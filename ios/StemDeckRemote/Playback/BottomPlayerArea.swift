import SwiftUI

/// The mixer's bottom player: a big row split into a 2x2 transport button
/// grid (left) and three vertical faders (right), then a thin progress row
/// underneath. Connects directly to the channel rack above it — same panel,
/// no gap — so the whole screen reads as one DAW unit top to bottom.
struct BottomPlayerArea: View {
    let isPlaying: Bool
    let isLooping: Bool
    let currentTime: TimeInterval
    let duration: TimeInterval
    let playbackRate: Double
    let pitchSemitones: Int
    @Binding var masterVolume: Double
    let onToggle: () -> Void
    let onToggleLoop: () -> Void
    let onMarkIn: () -> Void
    let onMarkOut: () -> Void
    let onSeek: (Double) -> Void
    let onRateChange: (Double) -> Void
    let onPitchChange: (Double) -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                buttonGrid
                Rectangle().fill(Color.black.opacity(0.4)).frame(width: 1)
                faders
            }
            .frame(maxWidth: .infinity)

            Rectangle().fill(Color.black.opacity(0.5)).frame(height: 1)

            progressRow
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
        }
    }

    private var buttonGrid: some View {
        VStack(spacing: 10) {
            HStack(spacing: 10) {
                transportButton(
                    icon: isPlaying ? "pause.fill" : "play.fill",
                    isActive: isPlaying,
                    activeColor: ConsoleTheme.accent,
                    action: onToggle
                )
                transportButton(
                    icon: "repeat",
                    isActive: isLooping,
                    activeColor: Color(hex: 0xf2e07a),
                    action: onToggleLoop
                )
            }
            HStack(spacing: 10) {
                transportButton(icon: "arrow.right.to.line", isActive: false, action: onMarkIn)
                transportButton(icon: "arrow.left.to.line", isActive: false, action: onMarkOut)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func transportButton(icon: String, isActive: Bool, activeColor: Color = ConsoleTheme.accent, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(isActive ? Color.black.opacity(0.85) : ConsoleTheme.onSurfaceVariant)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(isActive ? activeColor : ConsoleTheme.surfaceContainer)
                .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }

    private var faders: some View {
        HStack(spacing: 18) {
            VerticalLEDFader(
                label: "SPEED",
                valueText: speedLabel,
                color: Color(hex: 0x8ecae6),
                value: Binding(get: { playbackRate }, set: onRateChange),
                range: PlayerViewModel.speedRange,
                centered: true,
                doubleTapResetValue: 1.0
            )
            VerticalLEDFader(
                label: "KEY",
                valueText: pitchLabel,
                color: Color(hex: 0xb8a7ea),
                value: Binding(get: { Double(pitchSemitones) }, set: onPitchChange),
                range: Double(PlayerViewModel.pitchRange.lowerBound)...Double(PlayerViewModel.pitchRange.upperBound),
                centered: true,
                doubleTapResetValue: 0
            )
            VerticalLEDFader(
                label: "VOL",
                valueText: volumeLabel,
                color: Color(hex: 0x98d8c8),
                value: $masterVolume,
                range: 0...1,
                centered: false
            )
        }
        .padding(.vertical, 14)
        .padding(.horizontal, 16)
        .frame(maxWidth: .infinity)
    }

    private var progressRow: some View {
        HStack(spacing: 10) {
            Text(formatted(currentTime))
                .font(ConsoleTheme.monoFont(10))
                .foregroundStyle(ConsoleTheme.onSurfaceVariant)
                .frame(width: 32, alignment: .leading)

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(ConsoleTheme.recessedWell)
                    Capsule()
                        .fill(Color(hex: 0xf4a6c1))
                        .frame(width: geo.size.width * CGFloat(fraction))
                }
                .frame(height: 4)
                .contentShape(Rectangle().inset(by: -10))
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onEnded { value in
                            let f = min(max(0, value.location.x / geo.size.width), 1)
                            onSeek(Double(f) * duration)
                        }
                )
            }
            .frame(height: 16)

            Text(formatted(duration))
                .font(ConsoleTheme.monoFont(10))
                .foregroundStyle(ConsoleTheme.onSurfaceVariant)
                .frame(width: 32, alignment: .trailing)
        }
    }

    private var fraction: Double {
        guard duration > 0 else { return 0 }
        return min(max(currentTime / duration, 0), 1)
    }

    private var speedLabel: String {
        var text = String(format: "%.2f", playbackRate)
        while text.hasSuffix("0") { text.removeLast() }
        if text.hasSuffix(".") { text.removeLast() }
        return text + "x"
    }

    private var pitchLabel: String {
        pitchSemitones == 0 ? "0" : String(format: "%+d", pitchSemitones)
    }

    private var volumeLabel: String {
        "\(Int((masterVolume * 100).rounded()))"
    }

    private func formatted(_ time: TimeInterval) -> String {
        guard time.isFinite, time >= 0 else { return "0:00" }
        let total = Int(time.rounded())
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}
