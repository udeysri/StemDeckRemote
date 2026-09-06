package com.stemdeck.remote.playback

/**
 * Merges every stem's [min, max] peaks into one overview waveform (min of
 * mins, max of maxes per point) — used for the single master waveform both
 * mixer views show, since neither wants to pick one arbitrary stem to
 * represent "the song."
 */
object PeaksMerger {
    fun combine(peaksByStem: Map<String, List<List<Double>>>): List<List<Double>> {
        val allPeaks = peaksByStem.values
        val length = allPeaks.maxOfOrNull { it.size } ?: return emptyList()
        if (length <= 0) return emptyList()
        val combined = Array(length) { doubleArrayOf(0.0, 0.0) }
        for (peaks in allPeaks) {
            for (i in 0 until minOf(length, peaks.size)) {
                val point = peaks[i]
                if (point.size != 2) continue
                combined[i][0] = minOf(combined[i][0], point[0])
                combined[i][1] = maxOf(combined[i][1], point[1])
            }
        }
        return combined.map { listOf(it[0], it[1]) }
    }
}
