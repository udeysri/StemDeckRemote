import SwiftUI

/// Picks Simple or Advanced mixer content (both size themselves internally
/// for whatever orientation they're given) and hosts the states that come
/// before the mixer is playable: waiting on the download queue, then
/// building the audio graph.
///
/// The view model is owned by `PlaybackCoordinator`, not by this view — this
/// screen is presented as a modal that can be minimized to a mini player
/// while the song keeps playing, so nothing here starts or stops playback
/// based on its own appearance/disappearance.
struct PlayerView: View {
    let job: Job
    let server: PairedServer
    @ObservedObject var viewModel: PlayerViewModel
    @ObservedObject private var downloadQueue = StemDownloadQueue.shared
    @ObservedObject private var playback = PlaybackCoordinator.shared
    /// Live position while a finger is down on any waveform — shared across
    /// rows so scrubbing one row previews the seek on all of them at once.
    @State private var scrubProgress: Double?
    /// Persisted device-wide (not per-song) — whichever mode you last used
    /// opens by default next time.
    @AppStorage("stemdeck.mixerViewMode") private var viewModeRaw: String = MixerViewMode.simple.rawValue
    private var viewMode: MixerViewMode { MixerViewMode(rawValue: viewModeRaw) ?? .advanced }
    /// Owned here (not by Simple/Advanced individually) so switching view
    /// modes doesn't tear down and re-attach the hidden volume view, and
    /// so both modes agree on what "Volume" controls.
    @StateObject private var systemVolume = SystemVolumeController()

    /// iPadOS 26 removed the ability to lock an app to full screen —
    /// `UIRequiresFullScreen` still helps on older systems but is being
    /// phased out, and its replacement (`sizeRestrictions`) is only a
    /// best-effort hint the system can ignore. So the window can now shrink
    /// to sizes neither console layout was designed for; below this floor,
    /// show a placeholder instead of squeezing six stem rows and a full
    /// transport into a space too small for them to be usable.
    private static let minMixerWidth: CGFloat = 340
    private static let minMixerHeight: CGFloat = 460

    var body: some View {
        Group {
            switch viewModel.state {
            case .waitingForStems:
                waitingView.overlay(alignment: .topLeading) { minimizeButton }
            case .loadingEngine:
                ProgressView()
                    .tint(ConsoleTheme.onSurface)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(ConsoleTheme.background)
                    .overlay(alignment: .topLeading) { minimizeButton }
            case .failed(let message):
                failedView(message).overlay(alignment: .topLeading) { minimizeButton }
            case .ready:
                GeometryReader { geo in
                    if geo.size.width < Self.minMixerWidth || geo.size.height < Self.minMixerHeight {
                        tooSmallView.overlay(alignment: .topLeading) { minimizeButton }
                    } else if viewMode == .simple {
                        SimpleConsoleView(job: job, viewModel: viewModel, systemVolume: systemVolume, onBack: { playback.minimize() }, onToggleViewMode: toggleViewMode)
                    } else {
                        AdvancedConsoleView(job: job, viewModel: viewModel, scrubProgress: $scrubProgress, onBack: { playback.minimize() }, onToggleViewMode: toggleViewMode, systemVolume: systemVolume)
                    }
                }
            }
        }
        .background(ConsoleTheme.background)
        .background(
            HiddenSystemVolumeView(controller: systemVolume)
                .frame(width: 100, height: 44)
                .opacity(0.001)
                .allowsHitTesting(false)
        )
        .toolbar(.hidden, for: .navigationBar)
    }

    private var tooSmallView: some View {
        VStack(spacing: 12) {
            Image(systemName: "arrow.up.left.and.arrow.down.right")
                .font(.system(size: 32, weight: .medium))
                .foregroundStyle(ConsoleTheme.outline)
            Text("Make This Window Bigger")
                .font(ConsoleTheme.headlineFont(14))
                .foregroundStyle(ConsoleTheme.onSurface)
            Text("The stem mixer needs more room to lay out its controls. Resize or maximize this window to keep playing.")
                .font(ConsoleTheme.monoFont(12))
                .foregroundStyle(ConsoleTheme.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(ConsoleTheme.background)
    }

    private func toggleViewMode() {
        viewModeRaw = (viewMode == .simple ? MixerViewMode.advanced : .simple).rawValue
    }

    private var minimizeButton: some View {
        Button(action: { playback.minimize() }) {
            Image(systemName: "chevron.down")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(ConsoleTheme.onSurfaceVariant)
                .frame(width: 30, height: 30)
                .background(ConsoleTheme.surfaceContainerHigh)
                .clipShape(Circle())
        }
        .padding(16)
    }

    private var waitingView: some View {
        VStack(spacing: 16) {
            switch downloadQueue.status(for: job.id) {
            case .downloading(let progress):
                ProgressView(value: progress)
                    .tint(ConsoleTheme.accent)
                    .padding(.horizontal, 40)
                Text("Downloading stems… \(Int(progress * 100))%")
                    .font(ConsoleTheme.monoFont(13))
                    .foregroundStyle(ConsoleTheme.onSurfaceVariant)
            case .queued:
                ProgressView().tint(ConsoleTheme.accent)
                Text("Queued to download…")
                    .font(ConsoleTheme.monoFont(13))
                    .foregroundStyle(ConsoleTheme.onSurfaceVariant)
            default:
                ProgressView().tint(ConsoleTheme.accent)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(ConsoleTheme.background)
    }

    private func failedView(_ message: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 36))
                .foregroundStyle(ConsoleTheme.outline)
            Text(message)
                .font(ConsoleTheme.monoFont(13))
                .foregroundStyle(ConsoleTheme.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Button("Retry") { Task { await viewModel.start() } }
                .buttonStyle(.bordered)
                .tint(ConsoleTheme.accent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(ConsoleTheme.background)
    }
}
