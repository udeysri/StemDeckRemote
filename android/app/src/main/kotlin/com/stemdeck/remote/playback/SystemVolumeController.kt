package com.stemdeck.remote.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Bridges the VOL fader to the device's actual hardware media volume (the
 * `STREAM_MUSIC` stream) — the same level the physical volume buttons and
 * any system volume HUD control — instead of an internal digital gain.
 *
 * This is the direct Android equivalent of the iOS side's
 * `SystemVolumeController`, which has to reach into `MPVolumeView`'s private
 * `UISlider` because iOS has no public "just set system volume" API.
 * Android's [AudioManager.setStreamVolume] is exactly that public API, so
 * this needs none of the iOS side's view-hierarchy hackery — the whole
 * class is simpler as a direct result.
 *
 * Reads stay in sync with the hardware buttons (or Control Center-equivalent
 * volume changes elsewhere) via the system's `ACTION_VOLUME_CHANGED`
 * broadcast, the same "observe however it changes" reasoning as the iOS
 * side's `AVAudioSession.outputVolume` KVO.
 */
class SystemVolumeController(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    private val _volume = MutableStateFlow(currentFraction())
    val volume: StateFlow<Float> get() = _volume

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                _volume.value = currentFraction()
            }
        }
    }

    init {
        // RECEIVER_NOT_EXPORTED: this is a system broadcast we only need to
        // observe within our own process, and API 34+ requires every
        // dynamically-registered receiver to declare an export flag.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun currentFraction(): Float =
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume

    /** No `FLAG_SHOW_UI` — the fader itself is the volume UI, so the system's own volume HUD would be redundant (same effect as iOS driving the hidden slider directly rather than a system control). */
    fun setVolume(newValue: Float) {
        val index = Math.round(newValue.coerceIn(0f, 1f) * maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
        _volume.value = index.toFloat() / maxVolume
    }

    fun dispose() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Already unregistered.
        }
    }
}

@Composable
fun rememberSystemVolumeController(): SystemVolumeController {
    val context = LocalContext.current.applicationContext
    val controller = remember { SystemVolumeController(context) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { controller.dispose() }
    }
    return controller
}
