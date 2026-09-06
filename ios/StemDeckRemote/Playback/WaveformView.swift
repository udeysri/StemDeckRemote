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

    var body: some View {
        Canvas { context, size in
            guard !peaks.isEmpty, size.width > 0, size.height > 0 else { return }
            let midY = size.height / 2
            let barSpacing: CGFloat = 2
            let barCount = max(1, Int(size.width / barSpacing))
            let groupSize = max(1, peaks.count / barCount)
            let barWidth = size.width / CGFloat(barCount)
            let playedBars = Int(progress * Double(barCount))

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
        }
    }
}
