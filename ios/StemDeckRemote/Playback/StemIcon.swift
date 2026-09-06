import SwiftUI

/// Icon, color, and display name for the 6 stems StemDeck produces
/// (`STEM_NAMES` in the desktop app's `app/core/config.py`: vocals, drums,
/// bass, guitar, piano, other). Any unrecognized name — e.g. a future stem
/// StemDeck adds — falls back to a generic icon/color and its capitalized
/// name rather than failing.
enum StemIcon {
    static func systemName(for stem: String) -> String {
        switch stem {
        case "vocals": return "mic.fill"
        case "drums": return "drum.fill"
        case "bass": return "guitars"
        case "guitar": return "guitars"
        case "piano": return "pianokeys"
        case "other": return "music.note"
        default: return "waveform"
        }
    }

    static func color(for stem: String) -> Color {
        ConsoleTheme.stemColor(stem)
    }

    static func displayName(for stem: String) -> String {
        switch stem {
        case "vocals": return "Vocals"
        case "drums": return "Drums"
        case "bass": return "Bass"
        case "guitar": return "Guitar"
        case "piano": return "Piano"
        case "other": return "Other"
        default: return stem.capitalized
        }
    }
}
