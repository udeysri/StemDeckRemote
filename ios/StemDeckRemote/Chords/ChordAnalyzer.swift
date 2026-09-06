import AVFoundation
import OSLog

/// Orchestrates on-device chord detection for one song: best-effort beat
/// grid, one chroma+template-match per beat window summed across the
/// harmonic stems, merged into ranges, cached to disk. Runs entirely off
/// already-downloaded stem files — no dependency on any StemDeck backend
/// change, since it only reads `beats.json`, which unmodified StemDeck
/// already serves for its own click track.
///
/// Not an actor/class: no shared mutable state, so it's safe to call from
/// a background `Task` without any isolation ceremony. Callers are
/// responsible for not running it on the main actor (see
/// `PlayerViewModel.start()`), since the analysis loop is synchronous CPU
/// work.
enum ChordAnalyzer {
    private static let logger = Logger(subsystem: "com.stemdeck.remote", category: "ChordAnalyzer")

    /// Harmonic instruments only — vocals are a monophonic melody, not
    /// chords, and drums are unpitched; both would just add noise.
    private static let harmonicStems = ["guitar", "piano", "bass", "other"]

    /// Fallback window size when no beat grid is available.
    private static let fallbackWindowSeconds: Double = 1.0

    /// One stem's audio, decoded once and downmixed to mono — every window
    /// is then a cheap array slice instead of a fresh disk seek+read. A
    /// typical song is ~400 beats x 4 stems; reading per-window instead of
    /// once was ~1600 individual file reads, easily the slowest part of
    /// this whole feature (and disk/CPU contention that made concurrent
    /// stem downloads feel slower, too).
    private struct DecodedStem {
        let samples: [Float]
        let sampleRate: Double
    }

    static func chords(for job: Job, server: PairedServer, store: StemFileStore) async -> [ChordEvent] {
        let jobID = job.id

        if store.chordsExist(jobID: jobID),
           let data = try? Data(contentsOf: store.chordsURL(jobID: jobID)),
           let cached = try? JSONDecoder().decode([ChordEvent].self, from: data) {
            logger.debug("chords[\(jobID)]: cache hit, \(cached.count) events")
            return cached
        }

        let stems = harmonicStems.compactMap { name -> DecodedStem? in
            guard store.exists(jobID: jobID, stem: name),
                  let file = try? AVAudioFile(forReading: store.url(jobID: jobID, stem: name)),
                  let samples = readFullMono(file: file)
            else { return nil }
            return DecodedStem(samples: samples, sampleRate: file.processingFormat.sampleRate)
        }
        guard !stems.isEmpty else {
            logger.warning("chords[\(jobID)]: no readable harmonic stems, skipping")
            return []
        }

        let grid = await StemDeckClient(server: server).fetchBeatGrid(jobID: jobID)
        let duration = stems.map { Double($0.samples.count) / $0.sampleRate }.max() ?? 0
        let boundaries = windowBoundaries(beats: grid?.beats, duration: duration)
        logger.debug("chords[\(jobID)]: \(stems.count) stems, grid=\(grid != nil), \(boundaries.count) boundaries, duration=\(duration, format: .fixed(precision: 1))s")
        guard boundaries.count > 1 else { return [] }

        var windows: [(start: Double, end: Double, chord: String?)] = []
        windows.reserveCapacity(boundaries.count - 1)
        for i in 0..<(boundaries.count - 1) {
            let start = boundaries[i]
            let end = boundaries[i + 1]
            var combined = [Double](repeating: 0, count: 12)
            for stem in stems {
                guard let slice = slice(stem, start: start, end: end) else { continue }
                let chroma = ChromaExtractor.chroma(samples: slice, sampleRate: stem.sampleRate)
                for i in 0..<12 { combined[i] += chroma[i] }
            }
            windows.append((start, end, ChordTemplates.bestMatch(chroma: combined)))

            // Yield periodically so this CPU-bound loop can't monopolize the
            // cooperative thread pool other Tasks (like concurrent stem
            // downloads) also run on.
            if i % 20 == 0 { await Task.yield() }
        }

        let events = segment(windows)
        logger.debug("chords[\(jobID)]: analysis produced \(events.count) chord events")
        if let data = try? JSONEncoder().encode(events) {
            try? FileManager.default.createDirectory(at: store.chordsURL(jobID: jobID).deletingLastPathComponent(), withIntermediateDirectories: true)
            try? data.write(to: store.chordsURL(jobID: jobID))
        }
        return events
    }

    /// With a usable beat grid: one boundary per detected beat, plus 0 and
    /// the track's own duration as endpoints. Without one: fixed 1-second
    /// boundaries. Either way the result is a plain list of time
    /// boundaries — windows are the gaps between consecutive entries.
    private static func windowBoundaries(beats: [Double]?, duration: Double) -> [Double] {
        if let beats, beats.count >= 4 {
            var sorted = beats.sorted()
            if sorted.first != 0 { sorted.insert(0, at: 0) }
            if duration > (sorted.last ?? 0) { sorted.append(duration) }
            return sorted
        }
        guard duration > 0 else { return [] }
        var times = stride(from: 0, to: duration, by: fallbackWindowSeconds).map { $0 }
        times.append(duration)
        return times
    }

    /// Decodes an entire stem file to mono float samples in one read.
    private static func readFullMono(file: AVAudioFile) -> [Float]? {
        let frameCount = AVAudioFrameCount(file.length)
        guard frameCount > 0, let buffer = AVAudioPCMBuffer(pcmFormat: file.processingFormat, frameCapacity: frameCount) else {
            return nil
        }
        file.framePosition = 0
        do {
            try file.read(into: buffer, frameCount: frameCount)
        } catch {
            return nil
        }
        guard let channelData = buffer.floatChannelData else { return nil }
        let channels = Int(buffer.format.channelCount)
        let frames = Int(buffer.frameLength)
        guard frames > 0 else { return nil }

        var mono = [Float](repeating: 0, count: frames)
        for channel in 0..<channels {
            let samples = channelData[channel]
            for i in 0..<frames { mono[i] += samples[i] }
        }
        if channels > 1 {
            let scale = 1.0 / Float(channels)
            for i in 0..<frames { mono[i] *= scale }
        }
        return mono
    }

    /// A `[start, end)` seconds range of an already-decoded stem, as a
    /// plain array slice — no I/O.
    private static func slice(_ stem: DecodedStem, start: Double, end: Double) -> [Float]? {
        let startIndex = max(0, Int(start * stem.sampleRate))
        let endIndex = min(stem.samples.count, Int(end * stem.sampleRate))
        guard startIndex < endIndex else { return nil }
        return Array(stem.samples[startIndex..<endIndex])
    }

    /// Merges consecutive windows that landed on the same chord into one
    /// range — a chord label shouldn't flicker every beat when the song
    /// hasn't actually changed chords. Windows with no confident match
    /// (silence, an empty stretch) are skipped rather than breaking a
    /// run, tolerating a small gap before starting a fresh range.
    private static func segment(_ windows: [(start: Double, end: Double, chord: String?)]) -> [ChordEvent] {
        var events: [ChordEvent] = []
        for window in windows {
            guard let chord = window.chord else { continue }
            if let last = events.indices.last, events[last].chord == chord, window.start - events[last].end < 0.05 {
                events[last] = ChordEvent(start: events[last].start, end: window.end, chord: chord)
            } else {
                events.append(ChordEvent(start: window.start, end: window.end, chord: chord))
            }
        }
        return events
    }
}
