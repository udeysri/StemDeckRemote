package com.stemdeck.remote.library

import android.content.Context
import com.stemdeck.remote.models.Job
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The last successfully fetched library listing, persisted so the song list
 * is still usable when StemDeck isn't reachable — the whole point of
 * downloading stems locally is defeated if you also need Wi-Fi just to find
 * and open the song you already have. Playback itself was already fully
 * offline-safe; this closes the one remaining gap, which was the library
 * screen refusing to show anything without a live fetch.
 */
@Singleton
class LibraryCache @Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext context: Context) {
    // allowSpecialFloatingPointValues: a `Job` decoded from StemDeck may
    // legitimately carry a NaN/Infinity float field (see StemDeckClient's
    // lenientJson) — round-tripping it through this cache needs the same
    // allowance on both the encode and decode side, or saving one would throw.
    private val json = Json { ignoreUnknownKeys = true; allowSpecialFloatingPointValues = true }
    private val file = File(context.filesDir, "library-cache.json")

    fun save(jobs: List<Job>) {
        try {
            file.writeText(json.encodeToString(jobs))
        } catch (e: Exception) {
            // Best-effort cache — a write failure just means the next launch
            // falls back to a live fetch instead of showing stale data.
        }
    }

    fun load(): List<Job>? {
        if (!file.exists()) return null
        return try {
            json.decodeFromString(file.readText())
        } catch (e: Exception) {
            null
        }
    }
}
