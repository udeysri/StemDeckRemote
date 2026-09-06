package com.stemdeck.remote.playback

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * A thin vertical LED-segment readout of one stem's fader level. Segments
 * light bottom-up as the volume slider moves, colored in the stem's own
 * color, with the unlit remainder dimmed instead of hidden so "not at 100%"
 * reads at a glance. Muted always shows fully dark, independent of where the
 * fader itself is sitting — no signal, no LEDs, same as a real console.
 */
@Composable
fun StemLevelMeter(color: Color, level: Double, isMuted: Boolean, modifier: Modifier = Modifier) {
    val segmentCount = 14
    val segmentSpacing = 2f
    val animatedLevel by animateFloatAsState(targetValue = level.toFloat(), animationSpec = tween(200), label = "levelMeter")

    Canvas(modifier = modifier.fillMaxSize()) {
        val segmentHeight = (size.height - (segmentCount - 1) * segmentSpacing) / segmentCount
        val litCount = if (isMuted) 0 else Math.round(animatedLevel * segmentCount)
        for (index in 0 until segmentCount) {
            // Segments are drawn bottom-up: index 0 is the bottom row.
            val y = size.height - (index + 1) * segmentHeight - index * segmentSpacing
            val lit = index < litCount
            drawRoundRect(
                color = if (lit) color else ConsoleTheme.outlineVariant.copy(alpha = 0.3f),
                topLeft = Offset(0f, y),
                size = Size(size.width, segmentHeight),
                cornerRadius = CornerRadius(1.5f, 1.5f),
            )
        }
    }
}
