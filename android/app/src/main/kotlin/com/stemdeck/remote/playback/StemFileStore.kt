package com.stemdeck.remote.playback

import android.content.Context
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where downloaded stem files live on disk: `filesDir/Stems/<jobID>/<stem>.wav`.
 * Stateless by design (pure path math + `File` calls) so it's safe to use
 * from multiple concurrent download coroutines without synchronization.
 */
@Singleton
class StemFileStore @Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext private val context: Context) {
    private val stemsRoot: File get() = File(context.filesDir, "Stems")

    fun url(jobID: String, stem: String): File = File(File(stemsRoot, jobID), "$stem.wav")

    fun exists(jobID: String, stem: String): Boolean = url(jobID, stem).exists()

    fun peaksFile(jobID: String): File = File(File(stemsRoot, jobID), "peaks.json")

    fun peaksExist(jobID: String): Boolean = peaksFile(jobID).exists()

    /**
     * Cache for `ChordAnalyzer`'s on-device chord detection — unlike
     * [peaksFile], nothing server-side produces this; it's written locally
     * once analysis finishes so it's never recomputed for the same job.
     */
    fun chordsFile(jobID: String): File = File(File(stemsRoot, jobID), "chords.json")

    fun chordsExist(jobID: String): Boolean = chordsFile(jobID).exists()

    /**
     * Removes everything downloaded/cached for one job (stems, peaks,
     * chords) — used when the user trashes a song from the mobile library,
     * to actually reclaim the space rather than just hiding the row.
     */
    fun deleteAll(jobID: String) {
        File(stemsRoot, jobID).deleteRecursively()
    }
}
