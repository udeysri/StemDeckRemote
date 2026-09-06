import Foundation
import MediaPlayer
import UIKit

/// Owns the currently-loaded song's `PlayerViewModel` at app scope, outside
/// any single screen's lifecycle — the stem console is now a modal you can
/// minimize, and minimizing must not stop playback. `PlayerView` no longer
/// creates or tears down its own view model; it just displays whatever this
/// coordinator is holding, and `ContentView` shows either the full-screen
/// console or a floating mini player from the same shared state depending on
/// `isExpanded`.
///
/// Also owns the iOS Control Center / lock-screen integration
/// (`MPNowPlayingInfoCenter` + `MPRemoteCommandCenter`), since it already has
/// the one long-lived handle on whatever's currently playing.
@MainActor
final class PlaybackCoordinator: ObservableObject {
    static let shared = PlaybackCoordinator()

    @Published private(set) var job: Job?
    @Published private(set) var server: PairedServer?
    @Published private(set) var viewModel: PlayerViewModel?
    /// Whether the full stem console is showing (`true`) or minimized to the
    /// mini player (`false`). Bound directly to `ContentView`'s
    /// `fullScreenCover`.
    @Published var isExpanded = false

    private var nowPlayingTask: Task<Void, Never>?
    private var artworkImage: UIImage?
    private var artworkURL: URL?

    private init() {
        setupRemoteCommands()
    }

    /// Starts a song if it isn't already the current one (switching songs
    /// tears the previous engine down first), then expands to the full
    /// console. Tapping the same song again while it's already loaded just
    /// re-expands without restarting anything.
    ///
    /// `localStemURLs`/`localPeaksURL` are set only for a bundled
    /// `SampleSongCatalog` entry — see `PlayerViewModel`'s doc comment.
    func play(job: Job, server: PairedServer, localStemURLs: [String: URL]? = nil, localPeaksURL: URL? = nil) {
        if self.job?.id != job.id {
            close()
            self.job = job
            self.server = server
            let newViewModel = PlayerViewModel(job: job, server: server, localStemURLs: localStemURLs, localPeaksURL: localPeaksURL)
            viewModel = newViewModel
            Task { await newViewModel.start() }
            loadArtworkIfNeeded(for: job)
            startNowPlayingUpdates()
        }
        isExpanded = true
    }

    func minimize() { isExpanded = false }
    func expand() { isExpanded = true }

    /// Fully tears the current song down — used when switching to a
    /// different song, not when merely minimizing.
    func close() {
        nowPlayingTask?.cancel()
        nowPlayingTask = nil
        viewModel?.stop()
        viewModel = nil
        job = nil
        server = nil
        isExpanded = false
        artworkImage = nil
        artworkURL = nil
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    private func loadArtworkIfNeeded(for job: Job) {
        artworkImage = nil
        artworkURL = nil
        guard let thumbnail = job.thumbnail, let url = URL(string: thumbnail) else { return }
        artworkURL = url
        Task { [weak self] in
            guard let (data, _) = try? await URLSession.shared.data(from: url),
                  let image = UIImage(data: data) else { return }
            guard let self, self.artworkURL == url else { return }
            self.artworkImage = image
            self.updateNowPlayingInfo()
        }
    }

    private func startNowPlayingUpdates() {
        nowPlayingTask?.cancel()
        nowPlayingTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self, self.viewModel != nil else { return }
                self.updateNowPlayingInfo()
                try? await Task.sleep(nanoseconds: 500_000_000)
            }
        }
    }

    private func updateNowPlayingInfo() {
        guard let viewModel, let job else { return }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: job.title ?? "Untitled",
            MPMediaItemPropertyArtist: "StemDeck",
            MPMediaItemPropertyPlaybackDuration: viewModel.duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: viewModel.currentTime,
            MPNowPlayingInfoPropertyPlaybackRate: viewModel.isPlaying ? viewModel.playbackRate : 0,
        ]
        if let artworkImage {
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: artworkImage.size) { _ in artworkImage }
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    /// Registered once for the app's lifetime. Handlers hop onto the main
    /// actor and return `.success` immediately rather than waiting for that
    /// hop — Control Center wants a prompt reply, and there's nothing here
    /// that can meaningfully fail.
    private func setupRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()

        center.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in
                guard let self, let viewModel = self.viewModel else { return }
                if !viewModel.isPlaying { viewModel.togglePlayback() }
                self.updateNowPlayingInfo()
            }
            return .success
        }
        center.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in
                guard let self, let viewModel = self.viewModel else { return }
                if viewModel.isPlaying { viewModel.togglePlayback() }
                self.updateNowPlayingInfo()
            }
            return .success
        }
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in
                guard let self, let viewModel = self.viewModel else { return }
                viewModel.togglePlayback()
                self.updateNowPlayingInfo()
            }
            return .success
        }
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            let position = event.positionTime
            Task { @MainActor in
                guard let self, let viewModel = self.viewModel else { return }
                viewModel.seek(to: position)
                self.updateNowPlayingInfo()
            }
            return .success
        }
        center.nextTrackCommand.isEnabled = false
        center.previousTrackCommand.isEnabled = false
    }
}
