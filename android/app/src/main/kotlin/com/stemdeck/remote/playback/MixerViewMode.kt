package com.stemdeck.remote.playback

/** Persisted per-device (not per-song) — whichever mode you last used opens by default next time. */
enum class MixerViewMode {
    /** Big waveform, big play button, mute/solo per stem, one master volume — no per-stem faders or readouts. */
    SIMPLE,

    /** The full hardware-console layout: per-stem waveform, level fader, dB readout, BPM/key/chord header. */
    ADVANCED,
}
