import Foundation

/// Chord-tone templates matched against a window's chroma vector the same
/// way StemDeck's own key detection matches a whole-song chroma against
/// major/minor scale profiles (`_correlate`/`_detect_key` in
/// `app/pipeline/analyze.py`) — same Pearson correlation, just against
/// chord-tone templates (which notes are *in* this chord) instead of the
/// corpus-derived 7-note scale profiles key detection uses.
enum ChordTemplates {
    private static let pitchNames = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]

    /// (chord-name suffix, [(semitones above root, weight)]). Root/third/
    /// fifth weighted heaviest; the seventh, where present, moderate.
    private static let qualities: [(suffix: String, tones: [(interval: Int, weight: Double)])] = [
        ("", [(0, 1.0), (4, 0.9), (7, 0.9)]),                    // major triad
        ("m", [(0, 1.0), (3, 0.9), (7, 0.9)]),                   // minor triad
        ("7", [(0, 1.0), (4, 0.85), (7, 0.85), (10, 0.7)]),      // dominant 7th
        ("maj7", [(0, 1.0), (4, 0.85), (7, 0.85), (11, 0.6)]),   // major 7th
        ("m7", [(0, 1.0), (3, 0.85), (7, 0.85), (10, 0.7)]),     // minor 7th
    ]

    /// One 12-element template per quality, root fixed at index 0 — rotated
    /// per candidate root at match time, same as `_correlate` rotates the
    /// chroma rather than the profile.
    private static let templates: [(suffix: String, profile: [Double])] = qualities.map { quality in
        var profile = [Double](repeating: 0, count: 12)
        for tone in quality.tones {
            profile[tone.interval] = tone.weight
        }
        return (quality.suffix, profile)
    }

    /// Best-matching chord label (e.g. "F#m7") for a summed chroma vector,
    /// or nil for a window with no discernible pitch content (silence, or a
    /// stem that was empty in that stretch).
    static func bestMatch(chroma: [Double]) -> String? {
        guard chroma.count == 12, chroma.contains(where: { $0 > 0 }) else { return nil }

        var best: (score: Double, label: String)?
        for root in 0..<12 {
            for template in templates {
                let score = correlate(template.profile, chroma, shift: root)
                if best == nil || score > best!.score {
                    best = (score, pitchNames[root] + template.suffix)
                }
            }
        }
        return best?.label
    }

    /// Pearson correlation between `profile` and `chroma` rotated by
    /// `shift` semitones — the exact math of `_correlate` in
    /// `app/pipeline/analyze.py`, ported unchanged.
    private static func correlate(_ profile: [Double], _ chroma: [Double], shift: Int) -> Double {
        let n = profile.count
        let rotated = (0..<n).map { chroma[($0 + shift) % n] }
        let meanP = profile.reduce(0, +) / Double(n)
        let meanC = rotated.reduce(0, +) / Double(n)
        let num = zip(profile, rotated).reduce(0.0) { $0 + ($1.0 - meanP) * ($1.1 - meanC) }
        let denomP = profile.reduce(0.0) { $0 + ($1 - meanP) * ($1 - meanP) }.squareRoot()
        let denomC = rotated.reduce(0.0) { $0 + ($1 - meanC) * ($1 - meanC) }.squareRoot()
        guard denomP > 0, denomC > 0 else { return 0 }
        return num / (denomP * denomC)
    }
}
