package com.stemdeck.remote.chords

import kotlin.math.sqrt

/**
 * Chord-tone templates matched against a window's chroma vector the same way
 * StemDeck's own key detection matches a whole-song chroma against
 * major/minor scale profiles (`_correlate`/`_detect_key` in
 * `app/pipeline/analyze.py`) — same Pearson correlation, just against
 * chord-tone templates (which notes are *in* this chord) instead of the
 * corpus-derived 7-note scale profiles key detection uses.
 */
object ChordTemplates {
    private val pitchNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** (chord-name suffix, [(semitones above root, weight)]). Root/third/fifth weighted heaviest; the seventh, where present, moderate. */
    private data class Quality(val suffix: String, val tones: List<Pair<Int, Double>>)

    private val qualities = listOf(
        Quality("", listOf(0 to 1.0, 4 to 0.9, 7 to 0.9)), // major triad
        Quality("m", listOf(0 to 1.0, 3 to 0.9, 7 to 0.9)), // minor triad
        Quality("7", listOf(0 to 1.0, 4 to 0.85, 7 to 0.85, 10 to 0.7)), // dominant 7th
        Quality("maj7", listOf(0 to 1.0, 4 to 0.85, 7 to 0.85, 11 to 0.6)), // major 7th
        Quality("m7", listOf(0 to 1.0, 3 to 0.85, 7 to 0.85, 10 to 0.7)), // minor 7th
    )

    /** One 12-element template per quality, root fixed at index 0 — rotated per candidate root at match time, same as `_correlate` rotates the chroma rather than the profile. */
    private val templates: List<Pair<String, DoubleArray>> = qualities.map { quality ->
        val profile = DoubleArray(12)
        for ((interval, weight) in quality.tones) profile[interval] = weight
        quality.suffix to profile
    }

    /** Best-matching chord label (e.g. "F#m7") for a summed chroma vector, or null for a window with no discernible pitch content (silence, or a stem that was empty in that stretch). */
    fun bestMatch(chroma: DoubleArray): String? {
        if (chroma.size != 12 || chroma.none { it > 0 }) return null

        var bestScore = Double.NEGATIVE_INFINITY
        var bestLabel: String? = null
        for (root in 0 until 12) {
            for ((suffix, profile) in templates) {
                val score = correlate(profile, chroma, root)
                if (score > bestScore) {
                    bestScore = score
                    bestLabel = pitchNames[root] + suffix
                }
            }
        }
        return bestLabel
    }

    /** Pearson correlation between `profile` and `chroma` rotated by `shift` semitones — the exact math of `_correlate` in `app/pipeline/analyze.py`, ported unchanged. */
    private fun correlate(profile: DoubleArray, chroma: DoubleArray, shift: Int): Double {
        val n = profile.size
        val rotated = DoubleArray(n) { chroma[(it + shift) % n] }
        val meanP = profile.sum() / n
        val meanC = rotated.sum() / n
        var num = 0.0
        var denomP = 0.0
        var denomC = 0.0
        for (i in 0 until n) {
            val dp = profile[i] - meanP
            val dc = rotated[i] - meanC
            num += dp * dc
            denomP += dp * dp
            denomC += dc * dc
        }
        val denom = sqrt(denomP) * sqrt(denomC)
        return if (denomP > 0 && denomC > 0) num / denom else 0.0
    }
}
