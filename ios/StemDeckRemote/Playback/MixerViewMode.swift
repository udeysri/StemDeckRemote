import Foundation

/// Persisted per-device (not per-song) — whichever mode you last used opens
/// by default next time, via `@AppStorage` in `PlayerView`.
enum MixerViewMode: String {
    /// Big waveform, big play button, mute/solo per stem, one master
    /// volume — no per-stem faders or readouts.
    case simple
    /// The full hardware-console layout: per-stem waveform, level fader,
    /// dB readout, BPM/key/chord header.
    case advanced
}
