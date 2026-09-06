package com.stemdeck.remote.playback

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Publishes [PlaybackCoordinator]'s currently-loaded song as a Media3
 * `MediaSession` — background playback, the lock-screen/notification
 * transport, Bluetooth headset buttons, and Android Auto all flow through
 * this one standard mechanism. The direct Android counterpart of the iOS
 * side's `UIBackgroundModes: audio` + `PlaybackCoordinator`'s
 * `MPNowPlayingInfoCenter`/`MPRemoteCommandCenter` wiring.
 *
 * Holds no playback state of its own — [PlaybackCoordinator] is the single
 * source of truth (and is what survives this service being torn down and
 * recreated by the system), so this only wires that state's
 * [NowPlayingSessionPlayer] facade into a session.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var coordinator: PlaybackCoordinator

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, coordinator.sessionPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** No more sessions to serve and nothing playing — let the system reclaim the service rather than keeping it (and its notification) alive. */
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player == null || (!player.playWhenReady || player.mediaItemCount == 0)) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // The session player is owned by PlaybackCoordinator (an app-scoped
        // singleton that outlives this service) — release only the session
        // itself, not the player underneath it.
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
