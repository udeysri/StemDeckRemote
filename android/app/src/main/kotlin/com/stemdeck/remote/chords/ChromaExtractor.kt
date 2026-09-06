package com.stemdeck.remote.chords

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.sin

/**
 * Turns a window of mono PCM samples into a 12-element chroma vector
 * (relative energy per pitch class C, C#, D, ... B) via a single windowed
 * FFT — the same idea as StemDeck's own `librosa.feature.chroma_cqt`
 * (`app/pipeline/analyze.py`), traded down from a true Constant-Q Transform
 * to a plain FFT for a much simpler, still-fast-enough implementation: this
 * runs once per beat per stem in the background, not live.
 *
 * The iOS side leans on Accelerate's `vDSP` FFT; there's no equivalent
 * system framework on Android, so this ships a small iterative radix-2
 * Cooley-Tukey FFT instead (in [fft] below) — same algorithm class, just a
 * plain-Kotlin implementation rather than a hardware-accelerated one.
 */
object ChromaExtractor {
    /** Chord tones live well within this range on guitar/piano/bass — no point spending bins on sub-bass rumble or high harmonics/hiss. */
    private const val MIN_HZ = 60.0
    private const val MAX_HZ = 5000.0

    fun chroma(samples: FloatArray, sampleRate: Double): DoubleArray {
        val chroma = DoubleArray(12)
        if (samples.size < 64) return chroma

        val log2n = kotlin.math.floor(ln(samples.size.toDouble()) / ln(2.0)).toInt()
        val fftSize = 1 shl log2n
        if (fftSize < 64) return chroma

        val real = DoubleArray(fftSize)
        val imag = DoubleArray(fftSize)
        for (i in 0 until fftSize) {
            // Hann window, matching vDSP_hann_window's normalized form.
            val w = 0.5 - 0.5 * cos(2.0 * PI * i / (fftSize - 1))
            real[i] = samples[i] * w
        }
        fft(real, imag)

        val binHz = sampleRate / fftSize
        for (bin in 1 until fftSize / 2) {
            val freq = bin * binHz
            if (freq < MIN_HZ || freq > MAX_HZ) continue
            // Magnitude-squared (matches vDSP_zvmags, which the iOS side sums per pitch class).
            val magnitude = real[bin] * real[bin] + imag[bin] * imag[bin]
            val midi = 12 * log2(freq / 440.0) + 69
            val pitchClass = ((Math.round(midi).toInt() % 12) + 12) % 12
            chroma[pitchClass] += magnitude
        }
        return chroma
    }

    /** In-place iterative radix-2 decimation-in-time FFT. `real`/`imag` length must be a power of two. */
    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wr = cos(angle)
            val wi = sin(angle)
            var i = 0
            while (i < n) {
                var curWr = 1.0
                var curWi = 0.0
                for (k in 0 until len / 2) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + len / 2] * curWr - imag[i + k + len / 2] * curWi
                    val vI = real[i + k + len / 2] * curWi + imag[i + k + len / 2] * curWr
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + len / 2] = uR - vR
                    imag[i + k + len / 2] = uI - vI
                    val nextWr = curWr * wr - curWi * wi
                    val nextWi = curWr * wi + curWi * wr
                    curWr = nextWr
                    curWi = nextWi
                }
                i += len
            }
            len = len shl 1
        }
    }
}
