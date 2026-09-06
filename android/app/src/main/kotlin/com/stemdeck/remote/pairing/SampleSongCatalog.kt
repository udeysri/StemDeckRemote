package com.stemdeck.remote.pairing

import com.stemdeck.remote.models.Job

/**
 * The 3 songs bundled with the app (`app/src/main/assets/SampleSongs/`) so
 * people can explore the stem console without pairing to a live StemDeck
 * instance. Stems were separated from the original tracks by StemDeck
 * itself, then re-encoded to AAC (64kbps) to keep the app small — the same
 * bundled files the iOS target ships, just copied verbatim into this app's
 * assets rather than re-encoded again.
 *
 * Every track is Creative Commons–licensed (CC BY) via the Free Music
 * Archive, which requires attribution — [Entry.artist]/[Entry.source]/
 * [Entry.license] carry exactly what needs crediting, surfaced per-track in
 * `SampleSongsScreen`.
 */
object SampleSongCatalog {
    data class Entry(val job: Job, val artist: String, val source: String, val license: String)

    private val stemNames = listOf("vocals", "drums", "bass", "guitar", "piano", "other")

    val all: List<Entry> = listOf(
        Entry(
            job = makeJob(id = "solomon", title = "Solomon", duration = 211.16, bpm = 83, key = "C# min", scale = "Natural Minor"),
            artist = "KETSA X THE CONSCIOUS COLLECTIVE",
            source = "Free Music Archive",
            license = "CC BY",
        ),
        Entry(
            job = makeJob(id = "summer-vibe", title = "Summer Vibe (Clean)", duration = 138.024, bpm = 117, key = "G maj", scale = "Major"),
            artist = "Creepzz",
            source = "Free Music Archive",
            license = "CC BY",
        ),
        Entry(
            job = makeJob(id = "no-data-centers", title = "NO DATA CENTERS (Protest Chong)", duration = 258.542458, bpm = 129, key = "F maj", scale = "Major"),
            artist = "NoKings XXX",
            source = "Free Music Archive",
            license = "CC BY",
        ),
    )

    private fun makeJob(id: String, title: String, duration: Double, bpm: Int, key: String, scale: String) = Job(
        id = id,
        status = "done",
        title = title,
        duration = duration,
        thumbnail = null,
        bpm = bpm,
        key = key,
        scale = scale,
        stems = stemNames.map { Job.Stem(name = it, url = null) },
        createdAt = 0.0,
    )

    /** Stem name -> its asset path for one entry's job id, resolved from the `SampleSongs/<id>/` folder in the app's assets. */
    fun stemAssetPaths(jobID: String): Map<String, String> =
        stemNames.associateWith { name -> "SampleSongs/$jobID/$name.m4a" }

    fun peaksAssetPath(jobID: String): String = "SampleSongs/$jobID/peaks.json"
}
