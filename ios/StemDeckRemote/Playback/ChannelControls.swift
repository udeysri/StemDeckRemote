import SwiftUI

/// Solo / Mute push-keys shared by both mixer layouts.
struct SoloMuteButtons: View {
    let isSoloed: Bool
    let isMuted: Bool
    let onSolo: () -> Void
    let onMute: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Button("S", action: onSolo)
                .buttonStyle(HardwareButtonStyle(isEngaged: isSoloed, engagedColor: Color(hex: 0xf7c59f)))
            Button("M", action: onMute)
                .buttonStyle(HardwareButtonStyle(isEngaged: isMuted, engagedColor: Color(hex: 0xe5989b)))
        }
    }
}

/// The stem's icon + name label, dimmed when the channel is silenced by
/// mute or by another stem's solo.
struct ChannelLabel: View {
    let stem: String
    let isAudible: Bool

    var body: some View {
        HStack(spacing: 6) {
            LEDDot(color: isAudible ? StemIcon.color(for: stem) : ConsoleTheme.outlineVariant, size: 7)
            // Kept as a small monochrome SF Symbol (not StemIcon.imageName's
            // full-color artwork) on purpose: at size 12 its job is to
            // dim/brighten with mute state, which a single-color tint
            // communicates and detailed art can't.
            Image(systemName: StemIcon.systemName(for: stem))
                .font(.system(size: 12))
                .foregroundStyle(isAudible ? ConsoleTheme.onSurfaceVariant : ConsoleTheme.outline)
            Text(StemIcon.displayName(for: stem).uppercased())
                .font(ConsoleTheme.headlineFont(11))
                .foregroundStyle(isAudible ? ConsoleTheme.onSurface : ConsoleTheme.outline)
                .lineLimit(1)
        }
    }
}

/// A recessed-track level fader with a colored fill and a dB readout —
/// the brief's "Channel Faders" component, built from a plain `Slider`
/// (no custom drag handling) styled to read as a hardware fader well.
struct LevelFader: View {
    let stem: String
    let levelLabel: String
    @Binding var volume: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack {
                Text("LEVEL")
                    .font(ConsoleTheme.headlineFont(9))
                    .foregroundStyle(ConsoleTheme.outline)
                Spacer()
                Text(levelLabel)
                    .font(ConsoleTheme.monoFont(11, weight: .medium))
                    .foregroundStyle(ConsoleTheme.onSurfaceVariant)
            }
            Slider(value: $volume, in: 0...1)
                .tint(StemIcon.color(for: stem))
        }
    }
}
