import SwiftUI

/// Renders one stem's pre-computed [min, max] peak pairs — `peaks.json`,
/// the same overview data the desktop app's own waveform draws from — as
/// vertical bars around the vertical center. Bars before `progress` (0...1
/// through playback) are drawn solid; the rest dim, so the waveform itself
/// doubles as a playhead.
///
/// Peaks arrive as a fixed ~1500-point overview regardless of song length,
/// which is usually far more points than the view has pixels for, so this
/// groups them down to roughly one bar per 2pt of width rather than drawing
/// 1500 slivers.
struct WaveformView: View {
    let peaks: [[Double]]
    let color: Color
    let progress: Double
    /// Mark In / Mark Out positions, as a 0...1 fraction of the track —
    /// `nil` when that marker isn't set. See `PlayerViewModel.markInTime`/
    /// `markOutTime`.
    var markInFraction: Double? = nil
    var markOutFraction: Double? = nil

    var body: some View {
        Canvas { context, size in
            guard !peaks.isEmpty, size.width > 0, size.height > 0 else { return }
            let midY = size.height / 2
            let barSpacing: CGFloat = 2
            let barCount = max(1, Int(size.width / barSpacing))
            let groupSize = max(1, peaks.count / barCount)
            let barWidth = size.width / CGFloat(barCount)
            let playedBars = Int(progress * Double(barCount))

            // Loop-region shading first, so the waveform bars draw on top of it.
            if let inFrac = markInFraction, let outFrac = markOutFraction {
                let x0 = CGFloat(min(inFrac, outFrac)) * size.width
                let x1 = CGFloat(max(inFrac, outFrac)) * size.width
                context.fill(Path(CGRect(x: x0, y: 0, width: x1 - x0, height: size.height)), with: .color(ConsoleTheme.loopRegionFill))
            }

            var start = 0
            var bar = 0
            while start < peaks.count && bar < barCount {
                let end = min(start + groupSize, peaks.count)
                var minV = 0.0
                var maxV = 0.0
                for point in peaks[start..<end] where point.count == 2 {
                    minV = Swift.min(minV, point[0])
                    maxV = Swift.max(maxV, point[1])
                }
                let yTop = midY - CGFloat(maxV) * midY
                let yBottom = midY - CGFloat(minV) * midY
                let rect = CGRect(
                    x: CGFloat(bar) * barWidth,
                    y: min(yTop, yBottom),
                    width: max(barWidth - 1, 1),
                    height: max(abs(yBottom - yTop), 1.5)
                )
                context.fill(Path(rect), with: .color(bar < playedBars ? color : color.opacity(0.3)))
                start = end
                bar += 1
            }

            if let inFrac = markInFraction { drawMarker(context: context, size: size, fraction: inFrac, color: ConsoleTheme.markerIn) }
            if let outFrac = markOutFraction { drawMarker(context: context, size: size, fraction: outFrac, color: ConsoleTheme.markerOut) }
        }
    }

    /// A thin vertical line plus a small flag triangle at the top — the
    /// standard "locator" look most DAWs use for mark in/out points.
    private func drawMarker(context: GraphicsContext, size: CGSize, fraction: Double, color: Color) {
        let x = CGFloat(fraction) * size.width
        context.fill(Path(CGRect(x: x - 1, y: 0, width: 2, height: size.height)), with: .color(color))
        var flag = Path()
        flag.move(to: CGPoint(x: x - 5, y: 0))
        flag.addLine(to: CGPoint(x: x + 5, y: 0))
        flag.addLine(to: CGPoint(x: x, y: 8))
        flag.closeSubpath()
        context.fill(flag, with: .color(color))
    }
}
