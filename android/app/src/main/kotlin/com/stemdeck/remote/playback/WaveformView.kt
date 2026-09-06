package com.stemdeck.remote.playback

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Renders one stem's pre-computed [min, max] peak pairs — `peaks.json`, the
 * same overview data the desktop app's own waveform draws from — as
 * vertical bars around the vertical center. Bars before [progress] (0...1
 * through playback) are drawn solid; the rest dim, so the waveform itself
 * doubles as a playhead.
 *
 * Peaks arrive as a fixed ~1500-point overview regardless of song length,
 * which is usually far more points than the view has pixels for, so this
 * groups them down to roughly one bar per 2dp of width rather than drawing
 * 1500 slivers.
 */
@Composable
fun WaveformView(
    peaks: List<List<Double>>,
    color: Color,
    progress: Double,
    modifier: Modifier = Modifier,
    /** Mark In / Mark Out positions, as a 0..1 fraction of the track — null when that marker isn't set. See `PlayerViewModel.markInTime`/`markOutTime`. */
    markInFraction: Double? = null,
    markOutFraction: Double? = null,
) {
    Canvas(modifier = modifier) {
        if (peaks.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas
        val midY = size.height / 2
        val barSpacing = 2f
        val barCount = maxOf(1, (size.width / barSpacing).toInt())
        val groupSize = maxOf(1, peaks.size / barCount)
        val barWidth = size.width / barCount
        val playedBars = (progress * barCount).toInt()

        // Loop-region shading first, so the waveform bars draw on top of it.
        if (markInFraction != null && markOutFraction != null) {
            val x0 = minOf(markInFraction, markOutFraction).toFloat() * size.width
            val x1 = maxOf(markInFraction, markOutFraction).toFloat() * size.width
            drawRect(color = ConsoleTheme.loopRegionFill, topLeft = Offset(x0, 0f), size = Size(x1 - x0, size.height))
        }

        var start = 0
        var bar = 0
        while (start < peaks.size && bar < barCount) {
            val end = minOf(start + groupSize, peaks.size)
            var minV = 0.0
            var maxV = 0.0
            for (i in start until end) {
                val point = peaks[i]
                if (point.size != 2) continue
                minV = minOf(minV, point[0])
                maxV = maxOf(maxV, point[1])
            }
            val yTop = midY - (maxV * midY).toFloat()
            val yBottom = midY - (minV * midY).toFloat()
            val top = minOf(yTop, yBottom)
            val height = maxOf(kotlin.math.abs(yBottom - yTop), 1.5f)
            drawRect(
                color = if (bar < playedBars) color else color.copy(alpha = color.alpha * 0.3f),
                topLeft = Offset(bar * barWidth, top),
                size = Size(maxOf(barWidth - 1f, 1f), height),
            )
            start = end
            bar += 1
        }

        markInFraction?.let { drawMarker(it, ConsoleTheme.markerIn) }
        markOutFraction?.let { drawMarker(it, ConsoleTheme.markerOut) }
    }
}

/** A thin vertical line plus a small flag triangle at the top — the standard "locator" look most DAWs use for mark in/out points. */
private fun DrawScope.drawMarker(fraction: Double, color: Color) {
    val x = fraction.toFloat() * size.width
    drawRect(color = color, topLeft = Offset(x - 1f, 0f), size = Size(2f, size.height))
    val flag = Path().apply {
        moveTo(x - 5f, 0f)
        lineTo(x + 5f, 0f)
        lineTo(x, 8f)
        close()
    }
    drawPath(flag, color = color)
}
