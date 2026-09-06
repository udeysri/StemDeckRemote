import SwiftUI

/// A vertical fader with a segmented LED-style fill, the look of a hardware
/// channel strip's fader well (e.g. Logic Pro's mixer). Two fill styles:
/// - `centered: false` (master volume) — segments light from the bottom up.
/// - `centered: true` (speed, key) — the fader's rest position is the
///   middle of the track (representing "no change"), and segments light
///   from the center outward toward wherever the fader currently sits,
///   above or below.
struct VerticalLEDFader: View {
    let label: String
    let valueText: String
    let color: Color
    @Binding var value: Double
    let range: ClosedRange<Double>
    var centered: Bool = false
    /// Double-tapping the fader snaps it back to this value (e.g. 1.0 for
    /// Speed, 0 for Key — "no change"). Nil (the master volume fader) means
    /// no double-tap behavior at all — there's no equivalent "center" reset
    /// that would make sense for it.
    var doubleTapResetValue: Double? = nil

    private let segmentCount = 20
    private let segmentGap: CGFloat = 2

    var body: some View {
        VStack(spacing: 4) {
            Text(label)
                .font(ConsoleTheme.headlineFont(9))
                .foregroundStyle(ConsoleTheme.outline)

            GeometryReader { geo in
                let fraction = normalizedFraction
                ZStack(alignment: .bottom) {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(ConsoleTheme.recessedWell)
                    segments(fraction: fraction, size: geo.size)
                    thumb(fraction: fraction, size: geo.size)
                }
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { drag in
                            let clampedY = min(max(0, drag.location.y), geo.size.height)
                            let frac = 1 - (clampedY / geo.size.height)
                            value = range.lowerBound + frac * (range.upperBound - range.lowerBound)
                        }
                )
                .highPriorityGesture(
                    doubleTapResetValue.map { resetValue in
                        TapGesture(count: 2).onEnded { value = resetValue }
                    }
                )
            }

            Text(valueText)
                .font(ConsoleTheme.monoFont(10, weight: .medium))
                .foregroundStyle(ConsoleTheme.onSurfaceVariant)
        }
    }

    private var normalizedFraction: Double {
        guard range.upperBound > range.lowerBound else { return 0 }
        return (value - range.lowerBound) / (range.upperBound - range.lowerBound)
    }

    @ViewBuilder
    private func segments(fraction: Double, size: CGSize) -> some View {
        let segH = (size.height - CGFloat(segmentCount - 1) * segmentGap) / CGFloat(segmentCount)
        Canvas { context, canvasSize in
            for i in 0..<segmentCount {
                let bottom = Double(i) / Double(segmentCount)
                let top = Double(i + 1) / Double(segmentCount)
                let isLit: Bool = centered
                    ? isLitCentered(bottom: bottom, top: top, fraction: fraction)
                    : top <= fraction + 0.0001
                let y = canvasSize.height - CGFloat(i + 1) * segH - CGFloat(i) * segmentGap
                let rect = CGRect(x: 0, y: y, width: canvasSize.width, height: segH)
                context.fill(
                    Path(roundedRect: rect, cornerRadius: 2),
                    with: .color(isLit ? color : ConsoleTheme.outlineVariant.opacity(0.25))
                )
            }
        }
    }

    /// Lights the band between center and the current position — padded by
    /// half a segment so that sitting exactly at rest (fraction == 0.5, the
    /// common case for Speed/Key) still lights the segment straddling
    /// center, instead of showing zero color at all until the fader moves.
    private func isLitCentered(bottom: Double, top: Double, fraction: Double) -> Bool {
        let mid = 0.5
        let halfSegment = 0.5 / Double(segmentCount)
        let low = min(mid, fraction) - halfSegment
        let high = max(mid, fraction) + halfSegment
        return top > low && bottom < high
    }

    private func thumb(fraction: Double, size: CGSize) -> some View {
        Capsule()
            .fill(Color.white)
            .frame(width: size.width + 6, height: 5)
            .shadow(color: .black.opacity(0.6), radius: 1, y: 1)
            .offset(y: -CGFloat(fraction) * size.height + 2.5)
    }
}
