package com.stemdeck.remote.playback

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Per-stem [DefaultRenderersFactory] override that inserts a [RmsAudioProcessor]
 * into that stem's audio pipeline — the Android equivalent of the iOS side's
 * `installLevelTap` call in `StemMixerEngine.load`. One instance per stem
 * player (each reports its own level under its own stem name).
 *
 * `DefaultAudioProcessorChain`'s varargs constructor still installs Media3's
 * own silence-skipping and Sonic (speed/pitch) processors alongside ours —
 * only the *extra* processor list is being supplied here, not a replacement
 * for the default chain — so `PlaybackParameters(speed, pitch)` keeps
 * working exactly as it would without this override.
 */
class StemRenderersFactory(context: Context, private val onLevel: (Float) -> Unit) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
        DefaultAudioSink.Builder(context)
            .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(RmsAudioProcessor(onLevel)))
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
}
