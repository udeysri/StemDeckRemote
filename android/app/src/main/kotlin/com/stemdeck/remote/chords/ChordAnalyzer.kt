package com.stemdeck.remote.chords

import android.util.Log
import com.stemdeck.remote.models.Job
import com.stemdeck.remote.models.PairedServer
import com.stemdeck.remote.networking.StemDeckClient
import com.stemdeck.remote.playback.StemFileStore
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.yield
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Orchestrates on-device chord detection for one song: best-effort beat
 * grid, one chroma+template-match per beat window summed across the
 * harmonic stems, merged into ranges, cached to disk. Runs entirely off
 * already-downloaded stem files — no dependency on any StemDeck backend
 * change, since it only reads `beats.json`, which unmodified StemDeck
 * already serves for its own click track.
 *
 * A plain `object` with no shared mutable state, so it's safe to call from
 * any coroutine without extra synchronization — callers are responsible for
 * running it off the main dispatcher, since the analysis loop is synchronous
 * CPU work (see `PlayerViewModel`, which is where this would be wired in if
 * the chord readout is ever turned on — see that type's doc comment).
 */
object ChordAnalyzer {
    private const val TAG = "ChordAnalyzer"

    /** Harmonic instruments only — vocals are a monophonic melody, not chords, and drums are unpitched; both would just add noise. */
    private val harmonicStems = listOf("guitar", "piano", "bass", "other")

    /** Fallback window size when no beat grid is available. */
    private const val FALLBACK_WINDOW_SECONDS = 1.0

    /**
     * One stem's audio, decoded once and downmixed to mono — every window is
     * then a cheap array slice instead of a fresh disk seek+read. A typical
     * song is ~400 beats x 4 stems; reading per-window instead of once was
     * ~1600 individual file reads, easily the slowest part of this whole
     * feature.
     */
    private data class DecodedStem(val samples: FloatArray, val sampleRate: Double)

    suspend fun chords(job: Job, server: PairedServer, store: StemFileStore): List<ChordEvent> {
        val jobID = job.id

        if (store.chordsExist(jobID)) {
            try {
                val cached = Json.decodeFromString<List<ChordEvent>>(store.chordsFile(jobID).readText())
                Log.d(TAG, "chords[$jobID]: cache hit, ${cached.size} events")
                return cached
            } catch (e: Exception) {
                // Corrupt cache — fall through and recompute.
            }
        }

        val stems = harmonicStems.mapNotNull { name ->
            if (!store.exists(jobID, name)) return@mapNotNull null
            val decoded = try {
                readFullMonoWav(store.url(jobID, name))
            } catch (e: Exception) {
                null
            } ?: return@mapNotNull null
            decoded
        }
        if (stems.isEmpty()) {
            Log.w(TAG, "chords[$jobID]: no readable harmonic stems, skipping")
            return emptyList()
        }

        val grid = StemDeckClient(server).fetchBeatGrid(jobID)
        val duration = stems.maxOf { it.samples.size / it.sampleRate }
        val boundaries = windowBoundaries(grid?.beats, duration)
        Log.d(TAG, "chords[$jobID]: ${stems.size} stems, grid=${grid != null}, ${boundaries.size} boundaries, duration=${"%.1f".format(duration)}s")
        if (boundaries.size <= 1) return emptyList()

        val windows = ArrayList<Triple<Double, Double, String?>>(boundaries.size - 1)
        for (i in 0 until boundaries.size - 1) {
            val start = boundaries[i]
            val end = boundaries[i + 1]
            val combined = DoubleArray(12)
            for (stem in stems) {
                val slice = slice(stem, start, end) ?: continue
                val chroma = ChromaExtractor.chroma(slice, stem.sampleRate)
                for (c in 0 until 12) combined[c] += chroma[c]
            }
            windows.add(Triple(start, end, ChordTemplates.bestMatch(combined)))

            // Yield periodically so this CPU-bound loop can't monopolize the
            // dispatcher other coroutines (like concurrent stem downloads)
            // also run on.
            if (i % 20 == 0) yield()
        }

        val events = segment(windows)
        Log.d(TAG, "chords[$jobID]: analysis produced ${events.size} chord events")
        try {
            store.chordsFile(jobID).parentFile?.mkdirs()
            store.chordsFile(jobID).writeText(Json.encodeToString(events))
        } catch (e: Exception) {
            // Best-effort cache write.
        }
        return events
    }

    /**
     * With a usable beat grid: one boundary per detected beat, plus 0 and
     * the track's own duration as endpoints. Without one: fixed 1-second
     * boundaries. Either way the result is a plain list of time boundaries —
     * windows are the gaps between consecutive entries.
     */
    private fun windowBoundaries(beats: List<Double>?, duration: Double): List<Double> {
        if (beats != null && beats.size >= 4) {
            val sorted = beats.sorted().toMutableList()
            if (sorted.first() != 0.0) sorted.add(0, 0.0)
            if (duration > (sorted.lastOrNull() ?: 0.0)) sorted.add(duration)
            return sorted
        }
        if (duration <= 0) return emptyList()
        val times = mutableListOf<Double>()
        var t = 0.0
        while (t < duration) {
            times.add(t)
            t += FALLBACK_WINDOW_SECONDS
        }
        times.add(duration)
        return times
    }

    /** A `[start, end)` seconds range of an already-decoded stem, as a plain array copy — no I/O. */
    private fun slice(stem: DecodedStem, start: Double, end: Double): FloatArray? {
        val startIndex = maxOf(0, (start * stem.sampleRate).toInt())
        val endIndex = minOf(stem.samples.size, (end * stem.sampleRate).toInt())
        if (startIndex >= endIndex) return null
        return stem.samples.copyOfRange(startIndex, endIndex)
    }

    /**
     * Merges consecutive windows that landed on the same chord into one
     * range — a chord label shouldn't flicker every beat when the song
     * hasn't actually changed chords. Windows with no confident match
     * (silence, an empty stretch) are skipped rather than breaking a run,
     * tolerating a small gap before starting a fresh range.
     */
    private fun segment(windows: List<Triple<Double, Double, String?>>): List<ChordEvent> {
        val events = mutableListOf<ChordEvent>()
        for ((start, end, chord) in windows) {
            if (chord == null) continue
            val last = events.lastOrNull()
            if (last != null && last.chord == chord && start - last.end < 0.05) {
                events[events.size - 1] = last.copy(end = end)
            } else {
                events.add(ChordEvent(start, end, chord))
            }
        }
        return events
    }

    /**
     * Minimal PCM WAV reader: downloaded stems are always 16-bit PCM WAV
     * (see `StemDeckClient.downloadStem`), so this only needs to understand
     * the RIFF/`fmt `/`data` chunk layout, not general audio decoding — the
     * direct equivalent of the iOS side reading an `AVAudioFile` in full.
     */
    private fun readFullMonoWav(file: File): DecodedStem? {
        RandomAccessFile(file, "r").use { raf ->
            val riff = ByteArray(12)
            raf.readFully(riff)
            require(String(riff, 0, 4, Charsets.US_ASCII) == "RIFF" && String(riff, 8, 4, Charsets.US_ASCII) == "WAVE") { "Not a RIFF/WAVE file" }

            var channels = 1
            var sampleRate = 44100
            var bitsPerSample = 16
            var dataOffset = -1L
            var dataSize = 0L

            while (raf.filePointer < raf.length() - 8) {
                val chunkId = ByteArray(4).also { raf.readFully(it) }
                val chunkSize = readLeUInt32(raf)
                val id = String(chunkId, Charsets.US_ASCII)
                when (id) {
                    "fmt " -> {
                        val fmt = ByteArray(chunkSize.toInt()).also { raf.readFully(it) }
                        channels = leShort(fmt, 2)
                        sampleRate = leInt(fmt, 4)
                        bitsPerSample = leShort(fmt, 14)
                    }
                    "data" -> {
                        dataOffset = raf.filePointer
                        dataSize = chunkSize
                        raf.seek(raf.filePointer + chunkSize + (chunkSize % 2))
                    }
                    else -> raf.seek(raf.filePointer + chunkSize + (chunkSize % 2))
                }
            }
            if (dataOffset < 0 || bitsPerSample != 16) return null

            raf.seek(dataOffset)
            val bytes = ByteArray(dataSize.toInt())
            raf.readFully(bytes)

            val frameCount = bytes.size / 2 / channels
            val mono = FloatArray(frameCount)
            var byteIndex = 0
            for (frame in 0 until frameCount) {
                var sum = 0f
                for (ch in 0 until channels) {
                    val lo = bytes[byteIndex].toInt() and 0xFF
                    val hi = bytes[byteIndex + 1].toInt()
                    val sample = ((hi shl 8) or lo).toShort()
                    sum += sample / 32768f
                    byteIndex += 2
                }
                mono[frame] = sum / channels
            }
            return DecodedStem(mono, sampleRate.toDouble())
        }
    }

    private fun readLeUInt32(raf: RandomAccessFile): Long {
        val b = ByteArray(4)
        raf.readFully(b)
        return (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
            ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
    }

    private fun leShort(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8)

    private fun leInt(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8) or
            ((b[offset + 2].toInt() and 0xFF) shl 16) or ((b[offset + 3].toInt() and 0xFF) shl 24)
}
