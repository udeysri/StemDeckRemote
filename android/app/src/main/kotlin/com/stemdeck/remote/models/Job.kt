package com.stemdeck.remote.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the JSON `GET /api/jobs` returns — one entry per completed job in
 * the StemDeck library (see `Job.to_state()` in the desktop app's
 * `app/core/models.py`). Only the fields the song list needs are decoded;
 * everything else in the payload is ignored (kotlinx.serialization ignores
 * unknown keys by default the way `JSONDecoder` does for a `Decodable` with
 * a fixed set of properties).
 */
@Serializable
data class Job(
    @SerialName("job_id") val id: String,
    val status: String,
    val title: String? = null,
    val duration: Double? = null,
    val thumbnail: String? = null,
    val bpm: Int? = null,
    val key: String? = null,
    val scale: String? = null,
    val stems: List<Stem>? = null,
    @SerialName("created_at") val createdAt: Double? = null,
) {
    @Serializable
    data class Stem(
        val name: String? = null,
        // Server-relative path, e.g. "/api/jobs/<id>/stems/vocals.wav" (see
        // runner.py's `job.stems` assignment). Not currently used —
        // downloads reconstruct the same path from `id` + stem name — but
        // decoded so a future server change to this shape doesn't silently
        // break `stems` decoding for the rest of the fields.
        val url: String? = null,
    )

    val isAvailable: Boolean get() = status == "done"

    val formattedDuration: String?
        get() {
            val d = duration ?: return null
            if (!d.isFinite() || d < 0) return null
            val total = Math.round(d).toInt()
            return "%d:%02d".format(total / 60, total % 60)
        }

    val stemNames: List<String>
        get() = (stems ?: emptyList()).mapNotNull { it.name }

    val subtitle: String?
        get() = when {
            bpm != null && key != null -> "$bpm BPM • $key"
            bpm != null -> "$bpm BPM"
            key != null -> key
            else -> null
        }
}
