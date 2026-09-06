package com.stemdeck.remote.playback

import android.content.Context
import android.net.Uri
import com.stemdeck.remote.models.Job
import com.stemdeck.remote.models.PairedServer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the currently-loaded song's [PlayerViewModel] at app scope, outside
 * any single screen's lifecycle — the stem console is a screen you can
 * minimize to a mini player without stopping playback, so nothing about its
 * lifecycle may own the mixer. Direct Android port of the iOS side's
 * `PlaybackCoordinator` (`ObservableObject` singleton); Hilt's `@Singleton`
 * plays the same "one instance for the app's lifetime" role `static let
 * shared` does there.
 *
 * Also the one place that knows how to build a [NowPlayingSessionPlayer] for
 * [PlaybackService] to hand to a Media3 `MediaSession` — the Android
 * equivalent of the iOS side also owning the `MPNowPlayingInfoCenter`/
 * `MPRemoteCommandCenter` wiring, since this is already the one long-lived
 * handle on whatever's currently playing.
 */
@Singleton
class PlaybackCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    val stemDownloadQueue: StemDownloadQueue,
    private val stemFileStore: StemFileStore,
) {
    private val _job = MutableStateFlow<Job?>(null)
    val job: StateFlow<Job?> get() = _job

    private val _server = MutableStateFlow<PairedServer?>(null)
    val server: StateFlow<PairedServer?> get() = _server

    private val _viewModel = MutableStateFlow<PlayerViewModel?>(null)
    val viewModel: StateFlow<PlayerViewModel?> get() = _viewModel

    /** Whether the full stem console is showing (`true`) or minimized to the mini player (`false`). */
    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> get() = _isExpanded

    val sessionPlayer: NowPlayingSessionPlayer by lazy { NowPlayingSessionPlayer(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var nowPlayingTicker: CoroutineJob? = null
    private var startJob: CoroutineJob? = null

    /**
     * Starts a song if it isn't already the current one (switching songs
     * tears the previous engine down first), then expands to the full
     * console. Tapping the same song again while it's already loaded just
     * re-expands without restarting anything.
     *
     * [localStemUris]/[localPeaksAssetPath] are set only for a bundled
     * `SampleSongCatalog` entry — see [PlayerViewModel]'s constructor doc
     * comment.
     */
    fun play(job: Job, server: PairedServer, localStemUris: Map<String, Uri>? = null, localPeaksAssetPath: String? = null) {
        if (_job.value?.id != job.id) {
            close()
            _job.value = job
            _server.value = server
            val newViewModel = PlayerViewModel(context, job, server, stemDownloadQueue, stemFileStore, localStemUris, localPeaksAssetPath)
            _viewModel.value = newViewModel
            startJob = scope.launch { newViewModel.start() }
            startNowPlayingUpdates()
        }
        _isExpanded.value = true
    }

    fun minimize() { _isExpanded.value = false }
    fun expand() { _isExpanded.value = true }
    fun setExpanded(value: Boolean) { _isExpanded.value = value }

    /** Fully tears the current song down — used when switching to a different song, not when merely minimizing. */
    fun close() {
        nowPlayingTicker?.cancel()
        nowPlayingTicker = null
        startJob?.cancel()
        _viewModel.value?.stop()
        _viewModel.value = null
        _job.value = null
        _server.value = null
        _isExpanded.value = false
        sessionPlayer.refresh()
    }

    private fun startNowPlayingUpdates() {
        nowPlayingTicker?.cancel()
        nowPlayingTicker = scope.launch {
            while (isActive && _viewModel.value != null) {
                sessionPlayer.refresh()
                delay(500)
            }
        }
    }
}
