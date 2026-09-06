import SwiftUI

/// A thin vertical LED-segment readout of one stem's fader level — replaces
/// the flat accent stripe that used to just be a static color swatch.
/// Segments light bottom-up as the volume slider moves, colored in the
/// stem's own color, with the unlit remainder dimmed instead of hidden so
/// "not at 100%" reads at a glance. Muted always shows fully dark,
/// independent of where the fader itself is sitting — no signal, no LEDs,
/// same as a real console.
struct StemLevelMeter: View {
    let color: Color
    let level: Double
    let isMuted: Bool

    private let segmentCount = 14
    private let segmentSpacing: CGFloat = 2

    var body: some View {
        GeometryReader { geo in
            let segmentHeight = (geo.size.height - CGFloat(segmentCount - 1) * segmentSpacing) / CGFloat(segmentCount)
            let litCount = isMuted ? 0 : Int((level * Double(segmentCount)).rounded())
            VStack(spacing: segmentSpacing) {
                ForEach((0..<segmentCount).reversed(), id: \.self) { index in
                    RoundedRectangle(cornerRadius: 1.5)
                        .fill(index < litCount ? color : ConsoleTheme.outlineVariant.opacity(0.3))
                        .frame(height: segmentHeight)
                }
            }
            .animation(.easeOut(duration: 0.2), value: litCount)
        }
    }
}
