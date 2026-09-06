import SwiftUI

struct ContentView: View {
    @ObservedObject private var store = PairingStore.shared
    @ObservedObject private var playback = PlaybackCoordinator.shared

    var body: some View {
        Group {
            if let server = store.current {
                LibraryView(server: server)
            } else {
                PairingView()
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if let job = playback.job, let viewModel = playback.viewModel, !playback.isExpanded {
                MiniPlayerContainer(job: job, viewModel: viewModel, onExpand: { playback.expand() })
            }
        }
        .animation(.easeInOut(duration: 0.25), value: playback.isExpanded)
        .task {
            // Pre-warm the OS's Local Network permission prompt at launch,
            // well before the first real connection attempt — see
            // LocalNetworkAuthorization's doc comment for why.
            LocalNetworkAuthorization.shared.requestIfNeeded()
        }
        .fullScreenCover(isPresented: Binding(
            get: { playback.isExpanded },
            set: { if !$0 { playback.minimize() } }
        )) {
            if let job = playback.job, let server = playback.server, let viewModel = playback.viewModel {
                PlayerView(job: job, server: server, viewModel: viewModel)
            }
        }
    }
}

/// Owns `@ObservedObject var viewModel` directly (rather than `ContentView`
/// just reading `playback.viewModel.isPlaying` inline) so this actually
/// reacts when playback starts/stops — `ContentView` only observes
/// `PlaybackCoordinator`'s own published properties, not properties nested
/// inside whatever `PlayerViewModel` it currently holds.
private struct MiniPlayerContainer: View {
    let job: Job
    @ObservedObject var viewModel: PlayerViewModel
    let onExpand: () -> Void

    var body: some View {
        Group {
            if viewModel.isPlaying {
                MiniPlayerView(
                    job: job,
                    viewModel: viewModel,
                    onExpand: onExpand,
                    onTogglePlay: { viewModel.togglePlayback() }
                )
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.25), value: viewModel.isPlaying)
    }
}
