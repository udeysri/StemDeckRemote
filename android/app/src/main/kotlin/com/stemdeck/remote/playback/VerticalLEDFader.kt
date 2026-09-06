package com.stemdeck.remote.playback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * A vertical fader with a segmented LED-style fill, the look of a hardware
 * channel strip's fader well (e.g. Logic Pro's mixer). Two fill styles:
 * - `centered = false` (master volume) — segments light from the bottom up.
 * - `centered = true` (speed, key) — the fader's rest position is the middle
 *   of the track (representing "no change"), and segments light from the
 *   center outward toward wherever the fader currently sits, above or below.
 */
@Composable
fun VerticalLEDFader(
    label: String,
    valueText: String,
    color: Color,
    value: Double,
    onValueChange: (Double) -> Unit,
    range: ClosedFloatingPointRange<Double>,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    /**
     * Double-tapping the fader snaps it back to this value (e.g. 1.0 for
     * Speed, 0 for Key — "no change"). Null (the master volume fader) means
     * no double-tap behavior at all.
     */
    doubleTapResetValue: Double? = null,
) {
    val segmentCount = 20
    val segmentGap = 2f

    Column(modifier = modifier, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = ConsoleTheme.headlineFont(9), color = ConsoleTheme.outline)

        val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0.0, 1.0)

        Canvas(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight()
                .pointerInput(range) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val clampedY = change.position.y.coerceIn(0f, size.height.toFloat())
                        val frac = 1 - (clampedY / size.height)
                        onValueChange(range.start + frac * (range.endInclusive - range.start))
                    }
                }
                .pointerInput(doubleTapResetValue) {
                    if (doubleTapResetValue != null) {
                        detectTapGestures(onDoubleTap = { onValueChange(doubleTapResetValue) })
                    }
                },
        ) {
            drawRoundRect(
                color = ConsoleTheme.recessedWell,
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            )

            val segH = (size.height - (segmentCount - 1) * segmentGap) / segmentCount
            for (i in 0 until segmentCount) {
                val bottom = i.toDouble() / segmentCount
                val top = (i + 1).toDouble() / segmentCount
                val isLit = if (centered) {
                    isLitCentered(bottom, top, fraction, segmentCount)
                } else {
                    top <= fraction + 0.0001
                }
                val y = size.height - (i + 1) * segH - i * segmentGap
                drawRoundRect(
                    color = if (isLit) color else ConsoleTheme.outlineVariant.copy(alpha = 0.25f),
                    topLeft = Offset(0f, y),
                    size = Size(size.width, segH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            }

            // Thumb: a thin white capsule at the current fader position.
            val thumbY = size.height - fraction.toFloat() * size.height
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(-3.dp.toPx(), thumbY - 2.5.dp.toPx()),
                size = Size(size.width + 6.dp.toPx(), 5.dp.toPx()),
                cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
            )
        }

        Text(text = valueText, style = ConsoleTheme.monoFont(10, androidx.compose.ui.text.font.FontWeight.Medium), color = ConsoleTheme.onSurfaceVariant)
    }
}

/**
 * Lights the band between center and the current position — padded by half
 * a segment so that sitting exactly at rest (fraction == 0.5, the common
 * case for Speed/Key) still lights the segment straddling center, instead of
 * showing zero color at all until the fader moves.
 */
private fun isLitCentered(bottom: Double, top: Double, fraction: Double, segmentCount: Int): Boolean {
    val mid = 0.5
    val halfSegment = 0.5 / segmentCount
    val low = min(mid, fraction) - halfSegment
    val high = max(mid, fraction) + halfSegment
    return top > low && bottom < high
}
