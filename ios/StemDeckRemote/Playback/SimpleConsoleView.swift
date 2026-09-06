import SwiftUI

/// The plain, casual alternative to the hardware-console mixer: one small
/// overall waveform and a fill-bar volume slider per stem (plus Mute/Solo)
/// — no per-stem waveform, no dB-precision readouts, no BPM/key/chord chips
/// beyond a one-line subtitle. Shares the same `BottomPlayerArea` transport
/// as `AdvancedConsoleView` (Play/Loop/Mark controls, Speed/Key/Vol faders)
/// rather than a bare play button, so Volume lives there instead of as a
/// separate stem-list row. Toggled against `AdvancedConsoleView` from
/// `PlayerView`, persisted via `MixerViewMode`.
///
/// Row heights are computed from available space (see `body`) so all 6
/// stems fit on screen without scrolling wherever there's room for it —
/// portrait with a bit of headroom, and landscape's shorter screen, where
/// the fixed hardware-console row height used to overflow. Only once even
/// the minimum comfortable row height doesn't fit does the stem list
/// scroll, rather than ever clipping or squeezing rows below a usable tap
/// target.
struct SimpleConsoleView: View {
    let job: Job
    @ObservedObject var viewModel: PlayerViewModel
    /// Shared with `AdvancedConsoleView` via `PlayerView` — see
    /// `SystemVolumeController` for why "Volume" here means the phone's
    /// actual hardware volume rather than an internal digital gain.
    @ObservedObject var systemVolume: SystemVolumeController
    var onBack: (() -> Void)? = nil
    var onToggleViewMode: (() -> Void)? = nil

    @State private var scrubProgress: Double?

    /// See `AdvancedConsoleView`'s matching property — iPad's regular
    /// horizontal size class gets taller fill-bars and more padding instead
    /// of the phone layout just stretched across the extra width, leaving a
    /// gap under the last row.
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    private var isRegular: Bool { horizontalSizeClass == .regular }

    private var minRowHeight: CGFloat { isRegular ? 56 : 44 }
    private var maxRowHeight: CGFloat { isRegular ? 84 : 60 }
    private let rowSpacing: CGFloat = 8
    private var contentPadding: CGFloat { isRegular ? 24 : 16 }

    private var combinedPeaks: [[Double]] { PeaksMerger.combine(viewModel.peaks) }

    var body: some View {
        GeometryReader { geo in
            let rowCount = viewModel.channels.count
            let fixedHeight = headerHeight + waveformHeight + bottomPlayerHeight + contentPadding
            let available = geo.size.height - fixedHeight - contentPadding * 2
            let spacingTotal = rowSpacing * CGFloat(max(0, rowCount - 1))
            let idealRowHeight = (available - spacingTotal) / CGFloat(rowCount)
            let rowHeight = min(maxRowHeight, max(minRowHeight, idealRowHeight))
            let fitsWithoutScrolling = idealRowHeight >= minRowHeight

            VStack(spacing: 0) {
                header

                GeometryReader { waveGeo in
                    WaveformView(peaks: combinedPeaks, color: .white, progress: scrubProgress ?? viewModel.progress)
                        .contentShape(Rectangle())
                        .gesture(
                            DragGesture(minimumDistance: 0)
                                .onChanged { value in scrubProgress = fraction(value.location.x, waveGeo.size.width) }
                                .onEnded { value in
                                    viewModel.seek(to: fraction(value.location.x, waveGeo.size.width) * viewModel.duration)
                                    scrubProgress = nil
                                }
                        )
                }
                .frame(height: waveformHeight)
                .background(Color.black)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .padding(.horizontal, contentPadding)
                .padding(.top, 8)

                mixerRows(rowHeight: rowHeight, scrolls: !fitsWithoutScrolling)

                // Same transport used by the Advanced console — real
                // Play/Pause/Loop/Mark controls and Speed/Key/Vol faders,
                // not just a bare play button, so Simple isn't missing
                // controls Advanced has.
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
                .background(ConsoleTheme.surfaceContainerHigh)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .padding(.horizontal, contentPadding)
                .padding(.bottom, contentPadding)
            }
        }
        .background(ConsoleTheme.background)
    }

    @ViewBuilder
    private func mixerRows(rowHeight: CGFloat, scrolls: Bool) -> some View {
        let rows = VStack(spacing: rowSpacing) {
            ForEach(viewModel.channels) { channel in
                SimpleChannelRow(
                    channel: channel,
                    rowHeight: rowHeight,
                    onVolumeChange: { viewModel.setVolume($0, for: channel.id) },
                    onSolo: { viewModel.toggleSolo(channel.id) },
                    onMute: { viewModel.toggleMute(channel.id) }
                )
            }
        }
        .padding(.horizontal, contentPadding)
        .padding(.top, 12)
        .padding(.bottom, contentPadding)

        if scrolls {
            ScrollView { rows }
        } else {
            rows
            Spacer(minLength: 0)
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            if let onBack {
                Button(action: onBack) {
                    Image(systemName: "chevron.down")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 32, height: 32)
                        .background(ConsoleTheme.surfaceContainerHigh)
                        .clipShape(Circle())
                }
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(job.title ?? "Untitled")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                if let subtitle = job.subtitle {
                    Text(subtitle)
                        .font(.system(size: 12))
                        .foregroundStyle(.white.opacity(0.6))
                }
            }
            Spacer()
            if let onToggleViewMode {
                Button(action: onToggleViewMode) {
                    Image(systemName: "waveform")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 32, height: 32)
                        .background(ConsoleTheme.surfaceContainerHigh)
                        .clipShape(Circle())
                }
            }
        }
        .padding(.horizontal, contentPadding)
        .padding(.top, 10)
        .padding(.bottom, 6)
        .frame(height: headerHeight)
    }

    private var headerHeight: CGFloat { isRegular ? 64 : 56 }
    private var waveformHeight: CGFloat { isRegular ? 120 : 80 }
    private var bottomPlayerHeight: CGFloat { isRegular ? 240 : 190 }

    private func fraction(_ x: CGFloat, _ width: CGFloat) -> Double {
        guard width > 0 else { return 0 }
        return Double(min(max(0, x), width) / width)
    }
}

/// A rounded fill-bar slider — drag anywhere on it to set the level. Shared
/// by the master volume row and every per-stem row, so "the stem bars work
/// the same as the volume bar" by construction rather than by convention.
private struct SimpleFillBar: View {
    let icon: String
    let label: String
    let color: Color
    @Binding var value: Double
    var dimmed: Bool = false
    var height: CGFloat = 56
    var valueLabel: String? = nil

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: height / 3).fill(ConsoleTheme.surfaceContainerHigh)
                RoundedRectangle(cornerRadius: height / 3)
                    .fill(color)
                    .frame(width: max(height, geo.size.width * CGFloat(value)))
                HStack {
                    Image(systemName: icon)
                        .foregroundStyle(.white)
                    Text(label)
                        .font(.system(size: min(16, height * 0.3), weight: .medium))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    Spacer()
                    if let valueLabel {
                        Text(valueLabel)
                            .font(.system(size: min(13, height * 0.24), design: .monospaced))
                            .foregroundStyle(.white.opacity(0.85))
                    }
                }
                .padding(.horizontal, 16)
            }
            .opacity(dimmed ? 0.45 : 1)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { drag in
                        value = min(max(0, drag.location.x / geo.size.width), 1)
                    }
            )
        }
        .frame(height: height)
    }
}

/// One stem: its own fill-bar volume slider (dimmed while muted, but still
/// draggable — muting and level are independent, same as Advanced) plus
/// Mute/Solo buttons sized to match the row.
private struct SimpleChannelRow: View {
    let channel: PlayerViewModel.StemChannel
    let rowHeight: CGFloat
    let onVolumeChange: (Double) -> Void
    let onSolo: () -> Void
    let onMute: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            SimpleFillBar(
                icon: StemIcon.systemName(for: channel.id),
                label: StemIcon.displayName(for: channel.id),
                color: StemIcon.color(for: channel.id),
                value: Binding(get: { channel.volume }, set: onVolumeChange),
                dimmed: channel.isMuted,
                height: rowHeight
            )

            squareButton("M", active: channel.isMuted, activeColor: .red, action: onMute)
            squareButton("S", active: channel.isSoloed, activeColor: .blue, action: onSolo)
        }
    }

    private func squareButton(_ label: String, active: Bool, activeColor: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: min(15, rowHeight * 0.28), weight: .bold))
                .foregroundStyle(.white)
                .frame(width: rowHeight, height: rowHeight)
                .background(active ? activeColor : ConsoleTheme.surfaceContainerHigh)
                .clipShape(RoundedRectangle(cornerRadius: rowHeight / 4))
        }
    }
}
