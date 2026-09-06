package com.stemdeck.remote.library

import com.stemdeck.remote.models.Job

/** Client-side sort for the library list — purely a display transform, no server involvement. */
enum class SongSortOption(val label: String) {
    DATE_ADDED_NEWEST("Newest First"),
    DATE_ADDED_OLDEST("Oldest First"),
    NAME_ASCENDING("Name (A–Z)"),
    NAME_DESCENDING("Name (Z–A)"),
    KEY_ASCENDING("Key (A–Z)"),
    BPM_ASCENDING("BPM (Low–High)"),
    BPM_DESCENDING("BPM (High–Low)");

    fun sort(jobs: List<Job>): List<Job> = when (this) {
        DATE_ADDED_NEWEST -> jobs.sortedByDescending { it.createdAt ?: 0.0 }
        DATE_ADDED_OLDEST -> jobs.sortedBy { it.createdAt ?: 0.0 }
        NAME_ASCENDING -> jobs.sortedBy { (it.title ?: "").lowercase() }
        NAME_DESCENDING -> jobs.sortedByDescending { (it.title ?: "").lowercase() }
        // Songs with no detected key sort to the end regardless of direction.
        KEY_ASCENDING -> jobs.sortedWith(compareBy(nullsLast()) { it.key?.lowercase() })
        BPM_ASCENDING -> jobs.sortedWith(compareBy(nullsLast()) { it.bpm })
        BPM_DESCENDING -> jobs.sortedWith(compareByDescending(nullsFirst()) { it.bpm })
    }
}
