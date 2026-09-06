package com.stemdeck.remote.chords

import kotlinx.serialization.Serializable

/**
 * The subset of `GET /api/jobs/{id}/stems/beats.json` this app cares about —
 * StemDeck's existing click-track beat grid (`app/pipeline/beatgrid.py`),
 * unmodified upstream. Only `beats` (second-offsets) is decoded; everything
 * else in that file (bars, detector, confidence, ...) is ignored.
 *
 * Fetching it is a plain read of something StemDeck already computes for
 * every job with the beat-grid stage — not a feature request of the
 * backend — so relying on it doesn't tie this app to a patched StemDeck.
 * It's still treated as optional: older jobs or a failed stage 404, and
 * [ChordAnalyzer] falls back to fixed-width windows.
 */
@Serializable
data class BeatGrid(val beats: List<Double>)
