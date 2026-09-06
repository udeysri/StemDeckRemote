import SwiftUI

/// The full hardware-console mixer. Header, master waveform, the six-stem
/// rack, and the bottom player are all one continuous panel now — a single
/// shared background and border with thin divider lines between sections,
/// rather than separate floating blocks — so the whole screen reads as one
/// DAW/rack unit rather than "header, then a rack below it."
///
/// - **One master waveform, at the top, is the only draggable one.** Every
///   stem's own waveform is purely visual (tinted by shared playback
///   progress), colored the same as the Simple view's stem pills.
/// - **Narrower stem rows**: left control column (icon/name/Solo/Mute, then
///   a full-width volume slider), right waveform column — shared by both
///   orientations, with dynamic row height and a scroll fallback for
///   landscape's shorter screen (`SimpleConsoleView` uses the same idea).
/// - **The bottom player** is a real transport, not just play/pause: Loop
///   (tints the master waveform to show the whole song is looping),
///   Play/Pause, Mark In/Out (placeholders for now), and three vertical
///   LED-style faders — Speed and Key rest in the middle and move up/down
///   from "no change," Volume fills from the bottom.
struct AdvancedConsoleView: View {
    let job: Job
    @ObservedObject var viewModel: PlayerViewModel
    @Binding var scrubProgress: Double?
    var onBack: (() -> Void)? = nil
    var onToggleViewMode: (() -> Void)? = nil
    /// The VOL fader drives the phone's actual hardware volume rather than
    /// an internal digital gain — see `SystemVolumeController`. Owned by
    /// `PlayerView` and shared with `SimpleConsoleView` so both views
    /// agree on what "volume" means regardless of which mode is active.
    @ObservedObject var systemVolume: SystemVolumeController

    /// iPad's regular horizontal size class gets taller rows, a wider
    /// control column, and more breathing room throughout — otherwise this
    /// rack just stretches the phone's proportions across a much bigger
    /// screen and leaves a big empty gap under the last row.
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    private var isRegular: Bool { horizontalSizeClass == .regular }

    private var minRowHeight: CGFloat { isRegular ? 84 : 64 }
    private var maxRowHeight: CGFloat { isRegular ? 130 : 88 }
    private var contentPadding: CGFloat { isRegular ? 24 : 16 }
    private var waveformHeight: CGFloat { isRegular ? 64 : 48 }
    private var headerHeight: CGFloat { isRegular ? 66 : 54 }
    private var bottomPlayerHeight: CGFloat { isRegular ? 240 : 190 }
    private var controlColumnWidth: CGFloat { isRegular ? 260 : 170 }

    private var combinedPeaks: [[Double]] { PeaksMerger.combine(viewModel.peaks) }

    var body: some View {
        GeometryReader { geo in
            let rowCount = max(1, viewModel.channels.count)
            let fixedHeight = headerHeight + waveformHeight + bottomPlayerHeight
            let available = geo.size.height - fixedHeight - contentPadding * 2
            let idealRowHeight = available / CGFloat(rowCount)
            let rowHeight = min(maxRowHeight, max(minRowHeight, idealRowHeight))
            let fitsWithoutScrolling = idealRowHeight >= minRowHeight

            VStack(spacing: 0) {
                ConsoleHeaderView(
                    job: job,
                    currentChord: viewModel.currentChord,
                    isAnalyzingChords: viewModel.isAnalyzingChords,
                    onBack: onBack,
                    onToggleViewMode: onToggleViewMode
                )
                .padding(.horizontal, contentPadding)
                .padding(.top, 10)
                .padding(.bottom, 8)
                .frame(height: headerHeight)

                divider

                masterWaveform
                    .frame(height: waveformHeight)
                    .padding(.horizontal, contentPadding)
                    .padding(.vertical, 8)

                divider

                channelRows(rowHeight: rowHeight, scrolls: !fitsWithoutScrolling)

                divider

                BottomPlayerArea(
                    isPlaying: viewModel.isPlaying,
                    isLooping: viewModel.isLooping,
                    currentTime: scrubProgress.map { $0 * viewModel.duration } ?? viewModel.currentTime,
                    duration: viewModel.duration,
                    playbackRate: viewModel.playbackRate,
                    pitchSemitones: viewModel.pitchSemitones,
                    masterVolume: Binding(
                        get: { Double(systemVolume.volume) },
                        set: { systemVolume.setVolume(Float($0)) }
                    ),
                    onToggle: { viewModel.togglePlayback() },
                    onToggleLoop: { viewModel.toggleLoop() },
                    onMarkIn: { viewModel.markIn() },
                    onMarkOut: { viewModel.markOut() },
                    onSeek: { viewModel.seek(to: $0) },
                    onRateChange: { viewModel.setPlaybackRate($0) },
                    onPitchChange: { viewModel.setPitch($0) }
                )
                .frame(height: bottomPlayerHeight)
            }
            .background(ConsoleTheme.surfaceContainerHigh)
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.black.opacity(0.5), lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .background(ConsoleTheme.background)
    }

    private var divider: some View {
        Rectangle().fill(Color.black.opacity(0.5)).frame(height: 1)
    }

    private var masterWaveform: some View {
        GeometryReader { geo in
            WaveformView(
                peaks: combinedPeaks,
                color: viewModel.isLooping ? Color(hex: 0xf2e07a) : ConsoleTheme.onSurface,
                progress: scrubProgress ?? viewModel.progress
            )
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in scrubProgress = fraction(value.location.x, geo.size.width) }
                    .onEnded { value in
                        viewModel.seek(to: fraction(value.location.x, geo.size.width) * viewModel.duration)
                        scrubProgress = nil
                    }
            )
        }
        .recessedWell(cornerRadius: 8)
    }

    /// All six rows as one continuous strip within the shared panel — no
    /// background/border of its own, just the rows and the dividers between
    /// them, so they read as slots in the same rack unit as everything else.
    @ViewBuilder
    private func channelRows(rowHeight: CGFloat, scrolls: Bool) -> some View {
        let rows = VStack(spacing: 0) {
            ForEach(Array(viewModel.channels.enumerated()), id: \.element.id) { index, channel in
                AdvancedChannelRow(
                    channel: channel,
                    peaks: viewModel.peaks[channel.id] ?? [],
                    progress: scrubProgress ?? viewModel.progress,
                    liveLevel: viewModel.liveLevels[channel.id],
                    rowHeight: rowHeight,
                    controlColumnWidth: controlColumnWidth,
                    onVolumeChange: { viewModel.setVolume($0, for: channel.id) },
                    onSolo: { viewModel.toggleSolo(channel.id) },
                    onMute: { viewModel.toggleMute(channel.id) }
                )
                if index < viewModel.channels.count - 1 {
                    Rectangle().fill(Color.black.opacity(0.3)).frame(height: 1)
                }
            }
        }

        if scrolls {
            ScrollView { rows }
        } else {
            rows
            Spacer(minLength: 0)
        }
    }

    private func fraction(_ x: CGFloat, _ width: CGFloat) -> Double {
        guard width > 0 else { return 0 }
        return Double(min(max(0, x), width) / width)
    }
}

/// One stem: left column is icon/name + Solo/Mute above a full-width volume
/// slider; right column is that stem's waveform, tinted by shared playback
/// progress but otherwise non-interactive — only the master waveform seeks.
private struct AdvancedChannelRow: View {
    let channel: PlayerViewModel.StemChannel
    let peaks: [[Double]]
    let progress: Double
    /// Live post-fader RMS (0...1), or nil when there's nothing fresh to
    /// show (paused) — the meter falls back to the fader position then.
    let liveLevel: Double?
    let rowHeight: CGFloat
    let controlColumnWidth: CGFloat
    let onVolumeChange: (Double) -> Void
    let onSolo: () -> Void
    let onMute: () -> Void

    /// The live reading is post-fader already (the tap sits downstream of
    /// each stem's own volume node), but it's on a dB scale while the fader
    /// is linear — capping it at the fader's own value guarantees the
    /// meter's peak height always tracks the fader directly, rather than
    /// trusting the two scales to agree on their own. Muting always wins,
    /// independent of any live reading still in flight.
    private var effectiveLevel: Double {
        guard !channel.isMuted else { return 0 }
        guard let liveLevel else { return channel.volume }
        return min(liveLevel, channel.volume)
    }

    var body: some View {
        HStack(spacing: 0) {
            // A live LED level readout for this stem — bounces with the
            // actual audio while playing (via liveLevel), and falls back to
            // showing the fader position when paused, replacing what used
            // to be a flat, static color stripe either way.
            StemLevelMeter(color: StemIcon.color(for: channel.id), level: effectiveLevel, isMuted: channel.isMuted)
                .frame(width: 8)
                .padding(.vertical, 6)
                .padding(.leading, 2)

            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    ChannelLabel(stem: channel.id, isAudible: !channel.isMuted)
                    Spacer()
                    SoloMuteButtons(isSoloed: channel.isSoloed, isMuted: channel.isMuted, onSolo: onSolo, onMute: onMute)
                }
                Slider(value: Binding(get: { channel.volume }, set: onVolumeChange), in: 0...1)
                    .tint(StemIcon.color(for: channel.id))
                Spacer(minLength: 0)
            }
            .padding(8)
            .frame(width: controlColumnWidth)

            Rectangle().fill(Color.black.opacity(0.4)).frame(width: 1)

            // The waveform's recessed "display window" fill, inset within
            // the row rather than a separate box.
            WaveformView(peaks: peaks, color: StemIcon.color(for: channel.id), progress: progress)
                .opacity(channel.isMuted ? 0.4 : 1)
                .padding(6)
                .background(ConsoleTheme.recessedWell)
                .frame(maxWidth: .infinity)
        }
        .frame(height: rowHeight)
    }
}
