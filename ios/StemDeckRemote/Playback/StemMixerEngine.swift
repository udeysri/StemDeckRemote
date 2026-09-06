import Accelerate
import AVFoundation

/// One `AVAudioEngine` graph per song: an `AVAudioPlayerNode` per stem,
/// each feeding its own `AVAudioUnitTimePitch` (for speed/key controls)
/// before the engine's main mixer. A volume slider is just that node's
/// `.volume`. Play/pause/seek all funnel through `scheduleAll`, which
/// re-schedules every node from a given frame — `AVAudioPlayerNode` has no
/// built-in seek, so a seek is "stop, reschedule from the target frame,
/// resume if it was playing".
final class StemMixerEngine {
    private let engine = AVAudioEngine()
    private var nodes: [String: AVAudioPlayerNode] = [:]
    /// One time/pitch unit per stem, inserted between its player node and
    /// the mixer. All units are always kept in lockstep (`setRate`/
    /// `setPitch` apply to every stem at once) so speed/key changes can't
    /// drift the stems out of sync with each other.
    private var timePitchNodes: [String: AVAudioUnitTimePitch] = [:]
    private var files: [String: AVAudioFile] = [:]
    private var referenceStem: String?
    private var onFinished: (() -> Void)?

    /// Where the current schedule started, in seconds — `currentTime()` adds
    /// the reference node's elapsed render time to this.
    private var scheduleStartSeconds: TimeInterval = 0
    /// Set while paused (or freshly sought-and-not-playing) so `currentTime()`
    /// has an answer without a live render timeline to read.
    private var pausedAtSeconds: TimeInterval?

    private(set) var isPlaying = false
    private(set) var duration: TimeInterval = 0

    /// The level each stem's slider is actually set to, independent of
    /// whether it's currently silenced by mute/solo — so un-muting or
    /// clearing solo restores exactly where the fader was, not unity.
    private var faderLevels: [String: Float] = [:]
    private var mutedStems: Set<String> = []
    private var soloedStems: Set<String> = []

    /// Fires with a stem's current post-fader RMS level (0...1 linear) a
    /// few times a second while it's actually producing audio — silence
    /// (paused, or a muted/silent stretch) just means no calls, not zeros.
    /// Always called on the main thread. Drives the LED meter's "playing"
    /// animation; the meter falls back to showing the fader position when
    /// nothing's arrived recently (paused).
    var onStemLevel: ((String, Float) -> Void)?
    private var lastLevelEmit: [String: CFAbsoluteTime] = [:]
    /// ~24fps — plenty for a meter to look alive, far below what would
    /// actually cost anything.
    private let levelEmitInterval: CFAbsoluteTime = 1.0 / 24

    /// Bumped on every `scheduleAll` call. `AVAudioPlayerNode` fires a
    /// segment's completion handler both when it plays to the end AND when
    /// `.stop()` cancels it mid-flight (seeking does the latter on every
    /// node) — there is no way to tell those apart from the callback's own
    /// arguments. Each handler captures the generation it was scheduled
    /// under; if a newer schedule has since superseded it, the firing is
    /// the stale "cancelled by seek" case and is ignored instead of being
    /// treated as "song ended."
    private var scheduleGeneration = 0

    /// Builds the graph, starts the engine, and schedules every stem from
    /// the beginning (silent until `play()`). `onFinished` fires once, on
    /// the main thread, when the longest stem finishes playing.
    func load(stems: [(name: String, url: URL)], onFinished: (() -> Void)?) throws {
        stopAndReset()
        self.onFinished = onFinished

        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playback, mode: .default)
        try session.setActive(true)

        var longestName: String?
        var longestFrames: AVAudioFramePosition = -1
        for stem in stems {
            let file = try AVAudioFile(forReading: stem.url)
            files[stem.name] = file
            if file.length > longestFrames {
                longestFrames = file.length
                longestName = stem.name
            }
        }
        referenceStem = longestName
        if let name = longestName, let file = files[name] {
            duration = Double(file.length) / file.processingFormat.sampleRate
        }

        for (name, file) in files {
            let node = AVAudioPlayerNode()
            let timePitch = AVAudioUnitTimePitch()
            engine.attach(node)
            engine.attach(timePitch)
            engine.connect(node, to: timePitch, format: file.processingFormat)
            engine.connect(timePitch, to: engine.mainMixerNode, format: file.processingFormat)
            nodes[name] = node
            timePitchNodes[name] = timePitch
            faderLevels[name] = 1.0
            installLevelTap(on: timePitch, stem: name)
        }
        mutedStems.removeAll()
        soloedStems.removeAll()

        engine.prepare()
        try engine.start()
        pausedAtSeconds = 0
        scheduleAll(fromFrame: 0)
    }

    private func scheduleAll(fromFrame startFrame: AVAudioFramePosition) {
        scheduleGeneration += 1
        let generation = scheduleGeneration

        for (name, node) in nodes {
            guard let file = files[name] else { continue }
            node.stop()
            let remaining = AVAudioFrameCount(max(0, file.length - startFrame))
            guard remaining > 0 else { continue }
            if name == referenceStem {
                node.scheduleSegment(
                    file, startingFrame: startFrame, frameCount: remaining, at: nil,
                    completionCallbackType: .dataPlayedBack
                ) { [weak self] _ in
                    DispatchQueue.main.async {
                        guard let self, generation == self.scheduleGeneration else { return }
                        self.handleFinished()
                    }
                }
            } else {
                node.scheduleSegment(file, startingFrame: startFrame, frameCount: remaining, at: nil)
            }
        }
        let sampleRate = files[referenceStem ?? ""]?.processingFormat.sampleRate ?? 44100
        scheduleStartSeconds = Double(startFrame) / sampleRate
    }

    /// Whole-song loop: when the reference stem reaches the end, restart
    /// every stem from frame 0 instead of stopping.
    var isLooping = false

    private func handleFinished() {
        scheduleAll(fromFrame: 0)
        if isLooping {
            // Restart immediately — don't tell the view model playback
            // stopped, since it didn't.
            pausedAtSeconds = nil
            play()
        } else {
            isPlaying = false
            pausedAtSeconds = 0
            onFinished?()
        }
    }

    /// Seeks every stem to `time` (clamped to the track), preserving whether
    /// playback was running.
    func seek(to time: TimeInterval) {
        let wasPlaying = isPlaying
        let clamped = min(max(0, time), duration)
        let sampleRate = files[referenceStem ?? ""]?.processingFormat.sampleRate ?? 44100
        let frame = AVAudioFramePosition(clamped * sampleRate)
        scheduleAll(fromFrame: frame)
        if wasPlaying {
            pausedAtSeconds = nil
            play()
        } else {
            pausedAtSeconds = clamped
        }
    }

    /// Current playback position. While paused this is frozen at the last
    /// known position; while playing it's read live off the reference node,
    /// since `AVAudioPlayerNode` has no direct "current position" query.
    func currentTime() -> TimeInterval {
        if let paused = pausedAtSeconds { return paused }
        guard let ref = nodes[referenceStem ?? ""],
              let nodeTime = ref.lastRenderTime,
              let playerTime = ref.playerTime(forNodeTime: nodeTime)
        else { return scheduleStartSeconds }
        return scheduleStartSeconds + Double(playerTime.sampleTime) / playerTime.sampleRate
    }

    func setVolume(_ volume: Float, for stem: String) {
        faderLevels[stem] = volume
        applyEffectiveVolume(for: stem)
    }

    func isMuted(_ stem: String) -> Bool { mutedStems.contains(stem) }
    func isSoloed(_ stem: String) -> Bool { soloedStems.contains(stem) }

    func toggleMute(_ stem: String) {
        if mutedStems.contains(stem) {
            mutedStems.remove(stem)
        } else {
            mutedStems.insert(stem)
        }
        applyEffectiveVolume(for: stem)
    }

    /// Multi-solo, like most DAWs: any number of stems can be soloed at
    /// once. While at least one is soloed, only soloed stems are audible;
    /// clearing the last solo returns every stem to its own mute state.
    func toggleSolo(_ stem: String) {
        if soloedStems.contains(stem) {
            soloedStems.remove(stem)
        } else {
            soloedStems.insert(stem)
        }
        for name in nodes.keys { applyEffectiveVolume(for: name) }
    }

    /// Playback speed, independent of pitch — 1.0 is normal. Applied to
    /// every stem's time/pitch unit at once.
    func setRate(_ rate: Float) {
        for node in timePitchNodes.values { node.rate = rate }
    }

    /// Pitch shift in semitones, independent of speed — 0 is unchanged.
    /// `AVAudioUnitTimePitch.pitch` is in cents (100 per semitone).
    func setPitch(semitones: Int) {
        let cents = Float(semitones) * 100
        for node in timePitchNodes.values { node.pitch = cents }
    }

    /// One RMS-per-buffer via `vDSP` — a single-pass sum-of-squares over
    /// ~1024 samples, sub-microsecond work. The tap callback runs on the
    /// audio render thread, so it does only that math and hops to main
    /// immediately after; no allocation, locking, or UI work happens there.
    private func installLevelTap(on node: AVAudioNode, stem: String) {
        let format = node.outputFormat(forBus: 0)
        node.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
            guard let channelData = buffer.floatChannelData, buffer.frameLength > 0 else { return }
            var rms: Float = 0
            vDSP_rmsqv(channelData[0], 1, &rms, vDSP_Length(buffer.frameLength))
            DispatchQueue.main.async {
                self?.emitStemLevel(stem, rms)
            }
        }
    }

    private func emitStemLevel(_ stem: String, _ rms: Float) {
        let now = CFAbsoluteTimeGetCurrent()
        if let last = lastLevelEmit[stem], now - last < levelEmitInterval { return }
        lastLevelEmit[stem] = now
        onStemLevel?(stem, rms)
    }

    private func applyEffectiveVolume(for stem: String) {
        let audible = soloedStems.isEmpty ? !mutedStems.contains(stem) : soloedStems.contains(stem)
        nodes[stem]?.volume = audible ? (faderLevels[stem] ?? 1.0) : 0
    }

    func play() {
        if !engine.isRunning { try? engine.start() }
        nodes.values.forEach { $0.play() }
        isPlaying = true
        pausedAtSeconds = nil
    }

    func pause() {
        pausedAtSeconds = currentTime()
        nodes.values.forEach { $0.pause() }
        isPlaying = false
    }

    func stopAndReset() {
        for node in nodes.values {
            node.stop()
            engine.detach(node)
        }
        for timePitch in timePitchNodes.values {
            timePitch.removeTap(onBus: 0)
            engine.detach(timePitch)
        }
        nodes.removeAll()
        timePitchNodes.removeAll()
        files.removeAll()
        faderLevels.removeAll()
        mutedStems.removeAll()
        soloedStems.removeAll()
        lastLevelEmit.removeAll()
        referenceStem = nil
        duration = 0
        scheduleStartSeconds = 0
        pausedAtSeconds = nil
        if engine.isRunning {
            engine.stop()
        }
        isPlaying = false
    }
}
