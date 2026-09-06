import Foundation
import OSLog

@MainActor
final class PlayerViewModel: ObservableObject {
    private static let logger = Logger(subsystem: "com.stemdeck.remote", category: "PlayerViewModel")
    struct StemChannel: Identifiable, Equatable {
        let id: String
        var volume: Double = 1.0
        var isMuted: Bool = false
        var isSoloed: Bool = false

        var levelLabel: String {
            guard volume > 0.0001 else { return "-\u{221e} dB" }
            let db = 20 * log10(volume)
            return String(format: "%+.1f dB", db)
        }
    }

    enum State: Equatable {
        case waitingForStems
        case loadingEngine
        case ready
        case failed(String)
    }

    @Published private(set) var channels: [StemChannel] = []
    @Published private(set) var isPlaying = false
    @Published private(set) var state: State = .waitingForStems
    @Published private(set) var currentTime: TimeInterval = 0
    @Published private(set) var peaks: [String: [[Double]]] = [:]
    @Published private(set) var playbackRate: Double = 1.0
    @Published private(set) var pitchSemitones: Int = 0
    @Published private(set) var isLooping = false
    /// Live post-fader RMS level per stem, 0...1, updated while playing —
    /// what makes the LED meters bounce with the music instead of just
    /// showing the fader position. Rows fall back to the fader position
    /// whenever a stem has no recent entry here (paused, or before the
    /// first buffer arrives).
    @Published private(set) var liveLevels: [String: Double] = [:]
    @Published private(set) var currentChord: String?
    @Published private(set) var isAnalyzingChords = false
    private(set) var duration: TimeInterval = 0
    private var chordEvents: [ChordEvent] = []
    private var chordTask: Task<Void, Never>?

    static let speedRange = 0.5...1.5
    static let pitchRange = -6...6

    var progress: Double {
        guard duration > 0 else { return 0 }
        return min(max(currentTime / duration, 0), 1)
    }

    private let job: Job
    private let server: PairedServer
    private let store = StemFileStore()
    private let engine = StemMixerEngine()
    private let downloadQueue = StemDownloadQueue.shared
    private var progressTask: Task<Void, Never>?

    /// Non-nil only for a bundled `SampleSongCatalog` entry: stem name ->
    /// file URL straight in the app bundle. When set, `start()` skips the
    /// download queue and the server-backed peaks fetch entirely — nothing
    /// here ever touches the network.
    private let localStemURLs: [String: URL]?
    private let localPeaksURL: URL?

    init(job: Job, server: PairedServer, localStemURLs: [String: URL]? = nil, localPeaksURL: URL? = nil) {
        self.job = job
        self.server = server
        self.localStemURLs = localStemURLs
        self.localPeaksURL = localPeaksURL
    }

    func start() async {
        state = .waitingForStems
        guard !job.stemNames.isEmpty else {
            state = .failed("This song has no stems available.")
            return
        }

        let stemURLs: [String: URL]
        if let localStemURLs {
            stemURLs = localStemURLs
            loadLocalPeaksIfNeeded()
        } else {
            do {
                try await downloadQueue.downloadNow(job)
            } catch {
                if case .failed(let message) = downloadQueue.status(for: job.id) {
                    state = .failed(message)
                } else {
                    state = .failed("Couldn't download stems.")
                }
                return
            }
            await loadPeaksIfNeeded()
            stemURLs = Dictionary(uniqueKeysWithValues: job.stemNames.map { ($0, store.url(jobID: job.id, stem: $0)) })
        }

        state = .loadingEngine

        do {
            let stems = job.stemNames.compactMap { name in stemURLs[name].map { (name: name, url: $0) } }
            try engine.load(stems: stems) { [weak self] in
                self?.isPlaying = false
                self?.currentTime = 0
                self?.liveLevels.removeAll()
            }
            engine.onStemLevel = { [weak self] stem, rms in
                self?.updateLiveLevel(stem: stem, rms: rms)
            }
            duration = engine.duration
            channels = job.stemNames.map { StemChannel(id: $0) }
            state = .ready
            startProgressUpdates()
        } catch {
            state = .failed("Couldn't load stems for playback: \(error.localizedDescription)")
        }
    }

    private func loadPeaksIfNeeded() async {
        let jobID = job.id
        if !store.peaksExist(jobID: jobID) {
            let client = StemDeckClient(server: server)
            if let data = try? await client.fetchPeaks(jobID: jobID) {
                try? data.write(to: store.peaksURL(jobID: jobID))
            }
        }
        guard let data = try? Data(contentsOf: store.peaksURL(jobID: jobID)) else { return }
        peaks = (try? JSONDecoder().decode([String: [[Double]]].self, from: data)) ?? [:]
    }

    private func loadLocalPeaksIfNeeded() {
        guard let localPeaksURL, let data = try? Data(contentsOf: localPeaksURL) else { return }
        peaks = (try? JSONDecoder().decode([String: [[Double]]].self, from: data)) ?? [:]
    }

    func togglePlayback() {
        if isPlaying {
            engine.pause()
            liveLevels.removeAll()
        } else {
            engine.play()
        }
        isPlaying = engine.isPlaying
    }

    /// RMS of real program material almost never reaches 0dBFS — that only
    /// happens for a constant full-scale wave, effectively clipping — so
    /// mapping all the way up to 0dB (as this used to) meant the top LEDs
    /// were essentially unreachable even at a 100% fader. -6dB is a much
    /// more realistic "meter's full" reference for loud passages.
    private static let meterFloorDb: Double = -40
    private static let meterCeilingDb: Double = -6

    private func updateLiveLevel(stem: String, rms: Float) {
        let db = Double(20 * log10(max(rms, 0.0001)))
        let normalized = (db - Self.meterFloorDb) / (Self.meterCeilingDb - Self.meterFloorDb)
        liveLevels[stem] = min(max(normalized, 0), 1)
    }

    func seek(to time: TimeInterval) {
        engine.seek(to: time)
        currentTime = engine.currentTime()
        isPlaying = engine.isPlaying
    }

    func setVolume(_ volume: Double, for stem: String) {
        engine.setVolume(Float(volume), for: stem)
        if let index = channels.firstIndex(where: { $0.id == stem }) {
            channels[index].volume = volume
        }
    }

    func toggleMute(_ stem: String) {
        engine.toggleMute(stem)
        if let index = channels.firstIndex(where: { $0.id == stem }) {
            channels[index].isMuted = engine.isMuted(stem)
        }
    }

    func toggleSolo(_ stem: String) {
        engine.toggleSolo(stem)
        for index in channels.indices {
            channels[index].isSoloed = engine.isSoloed(channels[index].id)
        }
    }

    func setPlaybackRate(_ rate: Double) {
        let clamped = min(max(Self.speedRange.lowerBound, rate), Self.speedRange.upperBound)
        engine.setRate(Float(clamped))
        playbackRate = clamped
    }

    func setPitch(_ value: Double) {
        let rounded = Int(value.rounded())
        let clamped = min(max(Self.pitchRange.lowerBound, rounded), Self.pitchRange.upperBound)
        guard clamped != pitchSemitones else { return }
        engine.setPitch(semitones: clamped)
        pitchSemitones = clamped
    }

    func toggleLoop() {
        isLooping.toggle()
        engine.isLooping = isLooping
    }

    func stopPlayback() {
        engine.pause()
        engine.seek(to: 0)
        isPlaying = false
        currentTime = 0
        liveLevels.removeAll()
    }

    func markIn() {}
    func markOut() {}

    private func startProgressUpdates() {
        progressTask?.cancel()
        progressTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                self.currentTime = self.engine.currentTime()
                self.updateCurrentChord()
                try? await Task.sleep(nanoseconds: 50_000_000)
            }
        }
    }

    private func updateCurrentChord() {
        currentChord = chordEvents.first { $0.start <= currentTime && currentTime < $0.end }?.chord
    }

    func stop() {
        progressTask?.cancel()
        chordTask?.cancel()
        isAnalyzingChords = false
        engine.stopAndReset()
        isPlaying = false
    }
}
