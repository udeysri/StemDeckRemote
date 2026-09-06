package com.stemdeck.remote.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * One song's mixer graph: one [ExoPlayer] per stem, driven in lockstep.
 *
 * This is the Android equivalent of the iOS side's `AVAudioEngine`-based
 * `StemMixerEngine` — but where iOS builds one engine graph with a player
 * node per stem, Android has no single-engine primitive that mixes several
 * independently-decoded files with independent per-stem volume, so this
 * uses **N synchronized `ExoPlayer` instances** instead, one per stem:
 * - Play/pause/seek are issued to every player together.
 * - Speed/pitch (`setSpeedAndPitch`) apply the same `PlaybackParameters` to
 *   every player, so they can never drift out of key/tempo with each other
 *   (same reasoning as the iOS side keeping every `AVAudioUnitTimePitch` in
 *   lockstep).
 * - A lightweight periodic drift-correction pass (every second, only while
 *   playing) re-seeks any stem whose position has drifted more than 80ms
 *   from the reference stem — cheap insurance against the small amount of
 *   independent clock drift multiple `ExoPlayer`/`AudioTrack` instances can
 *   accumulate over a long song, something a single-engine graph like
 *   iOS's doesn't need at all.
 * - Live per-stem RMS levels for the LED meters come from a small custom
 *   [RmsAudioProcessor] inserted into each stem's own audio pipeline via
 *   [StemRenderersFactory] — the direct equivalent of the iOS side's
 *   `installLevelTap`.
 *
 * One instance per loaded song (owned by [PlayerViewModel], exactly like
 * the iOS side), not a shared singleton.
 */
class StemMixerEngine(private val context: Context) {
    data class Stem(val name: String, val uri: Uri)

    private var players: Map<String, ExoPlayer> = emptyMap()
    private var referenceStem: String? = null

    /** The level each stem's slider is actually set to, independent of whether it's currently silenced by mute/solo — so un-muting or clearing solo restores exactly where the fader was, not unity. */
    private val faderLevels = mutableMapOf<String, Float>()
    private val mutedStems = mutableSetOf<String>()
    private val soloedStems = mutableSetOf<String>()

    private var onFinished: (() -> Unit)? = null

    /** Fires with a stem's current post-fader RMS level (0..1 linear) a few times a second while it's actually producing audio. Always invoked on the main thread. */
    var onStemLevel: ((String, Float) -> Unit)? = null
    private val lastLevelEmitNanos = ConcurrentHashMap<String, Long>()
    private val levelEmitIntervalNanos = (1_000_000_000L / 24) // ~24fps, same cadence as the iOS side.
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Whole-song loop: when the reference stem reaches the end, restart every stem from frame 0 instead of stopping. */
    var isLooping: Boolean = false

    var isPlaying: Boolean = false
        private set
    var durationMs: Long = 0
        private set

    private var currentRate = 1f
    private var currentPitchRatio = 1f

    private var driftJob: Job? = null
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Builds the graph and prepares every stem (silent until [play]).
     * Suspends until every player has reported a known duration — the
     * Android equivalent of the iOS side's `load` synchronously reading
     * each `AVAudioFile`'s frame count before returning.
     */
    suspend fun load(stems: List<Stem>, onFinished: () -> Unit) {
        stopAndReset()
        this.onFinished = onFinished

        val built = LinkedHashMap<String, ExoPlayer>()
        for (stem in stems) {
            val renderersFactory = StemRenderersFactory(context) { level -> emitLevel(stem.name, level) }
            val player = ExoPlayer.Builder(context, renderersFactory).build()
            player.setMediaItem(MediaItem.fromUri(stem.uri))
            player.playWhenReady = false
            player.prepare()
            built[stem.name] = player
            faderLevels[stem.name] = 1f
        }
        players = built
        mutedStems.clear()
        soloedStems.clear()

        awaitAllReady(built.values)

        referenceStem = built.maxByOrNull { it.value.duration.coerceAtLeast(0) }?.key
        durationMs = built.values.maxOfOrNull { if (it.duration == C.TIME_UNSET) 0L else it.duration } ?: 0L

        for ((name, player) in built) {
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED && name == referenceStem) handleFinished()
                }
            })
        }
    }

    private suspend fun awaitAllReady(toAwait: Collection<ExoPlayer>) = suspendCancellableCoroutine { continuation ->
        val pending = toAwait.toMutableList()
        val listeners = mutableMapOf<ExoPlayer, Player.Listener>()

        fun finishIfDone() {
            if (pending.isEmpty() && continuation.isActive) continuation.resume(Unit)
        }

        if (pending.isEmpty()) {
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }

        for (player in toAwait) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
                        pending.remove(player)
                        listeners[player]?.let(player::removeListener)
                        finishIfDone()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    pending.remove(player)
                    listeners[player]?.let(player::removeListener)
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
            listeners[player] = listener
            player.addListener(listener)
        }
        continuation.invokeOnCancellation {
            for ((player, listener) in listeners) player.removeListener(listener)
        }
    }

    fun play() {
        players.values.forEach { it.play() }
        isPlaying = true
        startDriftCorrection()
    }

    fun pause() {
        players.values.forEach { it.pause() }
        isPlaying = false
        driftJob?.cancel()
    }

    /** Seeks every stem to [timeMs] (clamped to the track), preserving whether playback was running. */
    fun seek(timeMs: Long) {
        val clamped = timeMs.coerceIn(0, durationMs)
        players.values.forEach { it.seekTo(clamped) }
    }

    /** Read live off the reference player — the one ExoPlayer instance treated as this song's master clock. */
    fun currentPositionMs(): Long = players[referenceStem]?.currentPosition ?: 0L

    fun setVolume(stem: String, level: Float) {
        faderLevels[stem] = level
        applyEffectiveVolume(stem)
    }

    fun isMuted(stem: String): Boolean = mutedStems.contains(stem)
    fun isSoloed(stem: String): Boolean = soloedStems.contains(stem)

    fun toggleMute(stem: String) {
        if (!mutedStems.remove(stem)) mutedStems.add(stem)
        applyEffectiveVolume(stem)
    }

    /** Multi-solo, like most DAWs: any number of stems can be soloed at once. While at least one is soloed, only soloed stems are audible; clearing the last solo returns every stem to its own mute state. */
    fun toggleSolo(stem: String) {
        if (!soloedStems.remove(stem)) soloedStems.add(stem)
        players.keys.forEach { applyEffectiveVolume(it) }
    }

    /** Playback speed, independent of pitch — 1.0 is normal. Applied to every stem at once. */
    fun setRate(rate: Float) {
        currentRate = rate
        applyPlaybackParameters()
    }

    /** Pitch shift in semitones, independent of speed — 0 is unchanged. */
    fun setPitchSemitones(semitones: Int) {
        currentPitchRatio = 2.0.pow(semitones / 12.0).toFloat()
        applyPlaybackParameters()
    }

    private fun applyPlaybackParameters() {
        val params = PlaybackParameters(currentRate, currentPitchRatio)
        players.values.forEach { it.playbackParameters = params }
    }

    private fun applyEffectiveVolume(stem: String) {
        val audible = if (soloedStems.isEmpty()) !mutedStems.contains(stem) else soloedStems.contains(stem)
        players[stem]?.volume = if (audible) (faderLevels[stem] ?: 1f) else 0f
    }

    private fun startDriftCorrection() {
        driftJob?.cancel()
        driftJob = mainScope.launch {
            while (isActive) {
                delay(1000)
                correctDrift()
            }
        }
    }

    /** Cheap insurance against independent-clock drift across N `ExoPlayer`/`AudioTrack` instances — see the class doc comment. Not sample-accurate, just close enough that a long song doesn't audibly drift out of sync. */
    private fun correctDrift() {
        val ref = referenceStem?.let { players[it] } ?: return
        val refPosition = ref.currentPosition
        for ((name, player) in players) {
            if (name == referenceStem) continue
            if (abs(player.currentPosition - refPosition) > DRIFT_THRESHOLD_MS) {
                player.seekTo(refPosition)
            }
        }
    }

    private fun handleFinished() {
        if (isLooping) {
            players.values.forEach { it.seekTo(0) }
            play()
        } else {
            isPlaying = false
            driftJob?.cancel()
            players.values.forEach {
                it.playWhenReady = false
                it.seekTo(0)
            }
            onFinished?.invoke()
        }
    }

    private fun emitLevel(stem: String, rms: Float) {
        val now = System.nanoTime()
        val last = lastLevelEmitNanos[stem] ?: 0L
        if (now - last < levelEmitIntervalNanos) return
        lastLevelEmitNanos[stem] = now
        mainHandler.post { onStemLevel?.invoke(stem, rms) }
    }

    fun stopAndReset() {
        driftJob?.cancel()
        players.values.forEach { it.release() }
        players = emptyMap()
        faderLevels.clear()
        mutedStems.clear()
        soloedStems.clear()
        lastLevelEmitNanos.clear()
        referenceStem = null
        durationMs = 0
        isPlaying = false
    }

    private companion object {
        const val DRIFT_THRESHOLD_MS = 80L
    }
}
