package com.stemdeck.remote.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * A transparent (non-modifying) [AudioProcessor] inserted into one stem's
 * ExoPlayer audio pipeline purely to measure its post-fader RMS level —
 * the Android equivalent of the iOS side's `AVAudioEngine` node tap
 * (`StemMixerEngine.installLevelTap`). Every buffer that passes through is
 * copied to the output completely unchanged; the RMS side-channel is a
 * side effect of reading it, not a transformation of the audio itself.
 *
 * Inserted downstream of the player's volume in the render pipeline —
 * `DefaultAudioSink` applies `Player.setVolume` as a plain linear gain
 * before handing buffers to the processor chain — so, same as the iOS tap,
 * [onLevel] already reflects the fader position, mute, and solo state.
 */
class RmsAudioProcessor(private val onLevel: (Float) -> Unit) : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Accept the format unchanged (mono or stereo 16-bit PCM is what
        // ExoPlayer's decoders produce by default) — this processor never
        // changes the format, only observes it.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            val shortBuffer = inputBuffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            var sumOfSquares = 0.0
            var count = 0
            while (shortBuffer.hasRemaining()) {
                val normalized = shortBuffer.get() / 32768.0
                sumOfSquares += normalized * normalized
                count++
            }
            if (count > 0) onLevel(sqrt(sumOfSquares / count).toFloat())
        }

        // Pass the buffer through completely unchanged.
        val output = replaceOutputBuffer(remaining)
        output.put(inputBuffer)
        output.flip()
    }
}
