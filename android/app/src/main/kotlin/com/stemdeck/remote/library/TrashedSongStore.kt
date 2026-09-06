package com.stemdeck.remote.library

import android.content.Context
import com.stemdeck.remote.models.Job
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TrashedSong(val job: Job, val trashedAtEpochMillis: Long)

/**
 * Songs the user has removed from the mobile library view (swipe-to-delete
 * in `LibraryScreen`) — mobile-only, this never calls StemDeck to delete
 * anything server-side. Stores a snapshot of each song's own metadata at
 * the moment it was trashed (not just its `job_id`) so the Trash section
 * can still show its title/BPM/key/duration even if StemDeck later deletes
 * the job for real and it drops out of `GET /api/jobs` entirely — in that
 * case there's nothing further to reconcile: the trashed record just sits
 * there as a normal (if now server-orphaned) local record until the user
 * restores or re-trashes over it.
 *
 * Deliberately untouched by library syncs, same reasoning as
 * [LibraryFolderStore]: a freshly synced job that's already trashed stays
 * trashed (excluded from the visible library by `LibraryScreen`, skipped by
 * [com.stemdeck.remote.playback.StemDownloadQueue.syncLibrary]'s background
 * downloader) until the user explicitly restores it. Direct Android
 * counterpart of the iOS side's `TrashedSongStore`.
 */
@Singleton
class TrashedSongStore @Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext context: Context) {
    // allowSpecialFloatingPointValues: a trashed `Job` snapshot may
    // legitimately carry a NaN/Infinity float field (see StemDeckClient's
    // lenientJson) — round-tripping it through this store needs the same
    // allowance on both the encode and decode side, or saving one would throw.
    private val json = Json { ignoreUnknownKeys = true; allowSpecialFloatingPointValues = true }
    private val file = File(context.filesDir, "trashed-songs.json")

    private val _entries = MutableStateFlow<List<TrashedSong>>(emptyList())
    val entries: StateFlow<List<TrashedSong>> get() = _entries

    init {
        load()
    }

    fun isTrashed(jobID: String): Boolean = _entries.value.any { it.job.id == jobID }

    /** Records `job` as trashed — callers are responsible for actually deleting its local files (see `StemFileStore.deleteAll`) and forgetting its download status (see `StemDownloadQueue.forget`). */
    fun trash(job: Job) {
        val withoutExisting = _entries.value.filterNot { it.job.id == job.id }
        _entries.value = listOf(TrashedSong(job, System.currentTimeMillis())) + withoutExisting
        persist()
    }

    fun restore(jobID: String) {
        _entries.value = _entries.value.filterNot { it.job.id == jobID }
        persist()
    }

    private fun persist() {
        try {
            file.writeText(json.encodeToString(_entries.value))
        } catch (e: Exception) {
            // Best-effort persistence, same as LibraryFolderStore/LibraryCache.
        }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            _entries.value = json.decodeFromString(file.readText())
        } catch (e: Exception) {
            // Corrupt/unreadable snapshot — start from an empty state rather than crash.
        }
    }
}
