package com.stemdeck.remote.playback

import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * A [SimpleBasePlayer] facade over whatever [PlaybackCoordinator] currently
 * has loaded, so the system (lock screen, Bluetooth headset buttons,
 * Android Auto, the notification's transport controls) can control and
 * observe playback through the one standard `Player` interface — [PlaybackService]
 * hands this to a Media3 `MediaSession`.
 *
 * This is the Android equivalent of the iOS side's `PlaybackCoordinator`
 * wiring `MPNowPlayingInfoCenter`/`MPRemoteCommandCenter` by hand; Media3's
 * session framework wants an actual `Player`, so rather than duplicating
 * that hand-wiring, this class *is* one — a thin read/forward adapter, not
 * a second copy of the mixer's state. All real state lives in
 * [PlayerViewModel]/[StemMixerEngine]; this only ever reads it (for
 * [getState]) or forwards commands to it (`handleXxx`).
 */
class NowPlayingSessionPlayer(private val coordinator: PlaybackCoordinator) : SimpleBasePlayer(Looper.getMainLooper()) {

    override fun getState(): State {
        val job = coordinator.job.value
        val viewModel = coordinator.viewModel.value

        val commands = Player.Commands.Builder().addAllCommands().build()
        val builder = State.Builder()
            .setAvailableCommands(commands)
            .setPlaybackParameters(PlaybackParameters(viewModel?.playbackRate?.value?.toFloat() ?: 1f))

        if (job == null || viewModel == null) {
            return builder
                .setPlaybackState(Player.STATE_IDLE)
                .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()
        }

        val mediaMetadataBuilder = MediaMetadata.Builder()
            .setTitle(job.title ?: "Untitled")
            .setArtist("StemDeck")
        job.thumbnail?.let { thumbnail -> runCatching { mediaMetadataBuilder.setArtworkUri(Uri.parse(thumbnail)) } }
        val mediaMetadata = mediaMetadataBuilder.build()

        val mediaItemData = MediaItemData.Builder(job.id)
            .setMediaItem(MediaItem.Builder().setMediaId(job.id).setMediaMetadata(mediaMetadata).build())
            .setMediaMetadata(mediaMetadata)
            .setIsSeekable(true)
            .setDurationUs(Math.round(viewModel.duration.value * 1_000_000))
            .build()

        val playbackState = when (viewModel.state.value) {
            is PlayerViewModel.State.Ready -> Player.STATE_READY
            is PlayerViewModel.State.Failed -> Player.STATE_IDLE
            else -> Player.STATE_BUFFERING
        }

        return builder
            .setPlaylist(listOf(mediaItemData))
            .setCurrentMediaItemIndex(0)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(viewModel.isPlaying.value, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs(Math.round(viewModel.currentTime.value * 1000))
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val viewModel = coordinator.viewModel.value
        if (viewModel != null && viewModel.isPlaying.value != playWhenReady) viewModel.togglePlayback()
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        coordinator.viewModel.value?.seek(positionMs / 1000.0)
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        coordinator.viewModel.value?.stopPlayback()
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    /** Called whenever [PlaybackCoordinator]'s published state changes — see its periodic ticker, the direct equivalent of the iOS side's `startNowPlayingUpdates`. */
    fun refresh() = invalidateState()
}
