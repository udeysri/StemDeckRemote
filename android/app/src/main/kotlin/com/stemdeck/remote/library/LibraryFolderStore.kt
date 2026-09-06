package com.stemdeck.remote.library

import android.content.Context
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client-side song-folder organization: an ordered list of folders (order is
 * what "drag to reorder" reorders) and which job ID belongs to which folder.
 * A job with no entry in [assignments] is "Unassigned" — there's no real
 * folder to manage for that; it's just the default shown for anything not
 * explicitly filed away.
 *
 * Deliberately untouched by library syncs (`LibraryViewModel.refresh()`):
 * StemDeck's API has no folder concept, so a freshly synced job is either
 * new (no assignment yet — shows up Unassigned automatically) or already
 * known (its assignment, if any, is exactly what the user set it to last
 * time) — nothing here needs to react to a refresh at all.
 */
@Singleton
class LibraryFolderStore @Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext context: Context) {
    @Serializable
    private data class Snapshot(
        val folders: List<LibraryFolder> = emptyList(),
        val assignments: Map<String, String> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "library-folders.json")

    private val _folders = MutableStateFlow<List<LibraryFolder>>(emptyList())
    val folders: StateFlow<List<LibraryFolder>> get() = _folders

    private val _assignments = MutableStateFlow<Map<String, String>>(emptyMap())
    val assignments: StateFlow<Map<String, String>> get() = _assignments

    init {
        load()
    }

    fun folderID(jobID: String): String? = _assignments.value[jobID]

    fun createFolder(name: String) {
        _folders.value = _folders.value + LibraryFolder(name = name)
        persist()
    }

    fun deleteFolders(ids: Set<String>) {
        _folders.value = _folders.value.filterNot { it.id in ids }
        // Songs filed under a deleted folder fall back to Unassigned rather
        // than pointing at a folder ID that no longer exists.
        _assignments.value = _assignments.value.filterNot { it.value in ids }
        persist()
    }

    fun moveFolder(fromIndex: Int, toIndex: Int) {
        val list = _folders.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _folders.value = list
        persist()
    }

    /** `folderID` null moves back to Unassigned. */
    fun assign(jobIDs: Collection<String>, folderID: String?) {
        val updated = _assignments.value.toMutableMap()
        for (jobID in jobIDs) {
            if (folderID != null) updated[jobID] = folderID else updated.remove(jobID)
        }
        _assignments.value = updated
        persist()
    }

    private fun persist() {
        try {
            file.writeText(json.encodeToString(Snapshot.serializer(), Snapshot(_folders.value, _assignments.value)))
        } catch (e: Exception) {
            // Best-effort persistence, same as LibraryCache.
        }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val snapshot = json.decodeFromString(Snapshot.serializer(), file.readText())
            _folders.value = snapshot.folders
            _assignments.value = snapshot.assignments
        } catch (e: Exception) {
            // Corrupt/unreadable snapshot — start from an empty state rather than crash.
        }
    }
}
