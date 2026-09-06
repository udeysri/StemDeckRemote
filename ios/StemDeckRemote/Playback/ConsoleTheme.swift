import SwiftUI

/// Visual language for the mixer screen: a dark hardware-console aesthetic
/// (matte chassis surfaces, recessed fader wells, chalky pastel channel
/// accents, tracked monospaced readouts) taken from a design brief handed
/// off for this screen. Colors below are that brief's tokens; JetBrains
/// Mono / Space Mono aren't bundled as app fonts, so system `.monospaced`
/// stands in for the same "tabular readout" feel.
enum ConsoleTheme {
    static let background = Color(hex: 0x121316)
    static let surfaceContainerLow = Color(hex: 0x1b1b1f)
    static let surfaceContainer = Color(hex: 0x1f1f23)
    static let surfaceContainerHigh = Color(hex: 0x292a2d)
    static let recessedWell = Color(hex: 0x0d0e10)
    static let onSurface = Color(hex: 0xe3e2e6)
    static let onSurfaceVariant = Color(hex: 0xc5c6ca)
    static let outline = Color(hex: 0x8f9194)
    static let outlineVariant = Color(hex: 0x45474a)
    /// The brief's soft-glowing LED / active-state accent.
    static let accent = Color(hex: 0x93d3c3)

    /// One pastel per stem, matching the brief's "chalky pastel channel
    /// accents" — StemDeck's 6 stems (vocals, drums, bass, guitar, piano,
    /// other); anything else falls back to `outline`.
    static func stemColor(_ stem: String) -> Color {
        switch stem {
        case "vocals": return Color(hex: 0xe5989b)
        case "drums": return Color(hex: 0x8ecae6)
        case "bass": return Color(hex: 0x98d8c8)
        case "guitar": return Color(hex: 0xf7c59f)
        case "piano": return Color(hex: 0xb8a7ea)
        case "other": return accent
        default: return outline
        }
    }

    static func monoFont(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        .system(size: size, weight: weight, design: .monospaced)
    }

    /// All-caps, wide-tracked mono label — the brief's "screenprinted panel
    /// header" treatment. Apply `.textCase(.uppercase)` at the call site if
    /// the source string isn't already uppercase.
    static func headlineFont(_ size: CGFloat = 11) -> Font {
        .system(size: size, weight: .bold, design: .monospaced)
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xff) / 255,
            green: Double((hex >> 8) & 0xff) / 255,
            blue: Double(hex & 0xff) / 255,
            opacity: 1
        )
    }
}

/// A small soft-glowing status lamp — the brief's "physical LED illumination."
struct LEDDot: View {
    var color: Color
    var size: CGFloat = 6

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: size, height: size)
            .shadow(color: color.opacity(0.9), radius: size * 0.7)
    }
}

/// The recessed-well look used for fader tracks and waveform pockets: a
/// dark inset panel with a hairline top highlight and outer stroke.
struct RecessedWell: ViewModifier {
    var cornerRadius: CGFloat = 8

    func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(ConsoleTheme.recessedWell)
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(Color.white.opacity(0.06), lineWidth: 1)
            )
    }
}

extension View {
    func recessedWell(cornerRadius: CGFloat = 8) -> some View {
        modifier(RecessedWell(cornerRadius: cornerRadius))
    }
}

/// A tactile square/rectangular push-key — used for the Solo/Mute buttons.
struct HardwareButtonStyle: ButtonStyle {
    var isEngaged: Bool
    var engagedColor: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(ConsoleTheme.headlineFont(10))
            .foregroundStyle(isEngaged ? Color.black.opacity(0.85) : ConsoleTheme.onSurfaceVariant)
            .frame(width: 26, height: 22)
            .background(
                RoundedRectangle(cornerRadius: 5)
                    .fill(isEngaged ? engagedColor : ConsoleTheme.surfaceContainerHigh)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 5)
                    .stroke(Color.black.opacity(0.6), lineWidth: 1)
            )
            .shadow(color: .black.opacity(configuration.isPressed ? 0 : 0.5), radius: 2, y: configuration.isPressed ? 0 : 2)
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
    }
}
