package com.stemdeck.remote.chords

import kotlinx.serialization.Serializable

/**
 * One span of the song where a single chord was detected. `PlayerViewModel`
 * looks up which event contains the current playback time to know what to
 * display.
 */
@Serializable
data class ChordEvent(val start: Double, val end: Double, val chord: String)
