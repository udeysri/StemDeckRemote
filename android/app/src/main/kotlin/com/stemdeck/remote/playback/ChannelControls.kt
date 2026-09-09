package com.stemdeck.remote.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Solo / Mute push-keys shared by both mixer layouts. */
@Composable
fun SoloMuteButtons(isSoloed: Boolean, isMuted: Boolean, onSolo: () -> Unit, onMute: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
        HardwareButton(label = "S", isEngaged = isSoloed, engagedColor = ConsoleTheme.color(0xf7c59f), onClick = onSolo)
        HardwareButton(label = "M", isEngaged = isMuted, engagedColor = ConsoleTheme.color(0xe5989b), onClick = onMute)
    }
}

@Composable
private fun HardwareButton(label: String, isEngaged: Boolean, engagedColor: Color, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .width(26.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (isEngaged) engagedColor else ConsoleTheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = ConsoleTheme.headlineFont(10), color = if (isEngaged) Color.Black.copy(alpha = 0.85f) else ConsoleTheme.onSurfaceVariant)
    }
}

/** The stem's icon + name label, dimmed when the channel is silenced by mute or by another stem's solo. */
@Composable
fun ChannelLabel(stem: String, isAudible: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        LEDDot(color = if (isAudible) StemIcon.color(stem) else ConsoleTheme.outlineVariant, size = 7)
        // Kept as a small monochrome glyph (not StemIcon.imageRes's full-color
        // artwork) on purpose: at 12dp its job is to dim/brighten with mute
        // state, which a single-color tint communicates and detailed art can't.
        Icon(StemIcon.icon(stem), contentDescription = null, tint = if (isAudible) ConsoleTheme.onSurfaceVariant else ConsoleTheme.outline, modifier = Modifier.size(12.dp))
        Text(
            text = StemIcon.displayName(stem).uppercase(),
            style = ConsoleTheme.headlineFont(11),
            color = if (isAudible) ConsoleTheme.onSurface else ConsoleTheme.outline,
            maxLines = 1,
        )
    }
}

/**
 * A recessed-track level fader with a colored fill and a dB readout —
 * built from Material3's [Slider], styled to read as a hardware fader well.
 */
@Composable
fun LevelFader(stem: String, levelLabel: String, volume: Double, onVolumeChange: (Double) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier = modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            Text(text = "LEVEL", style = ConsoleTheme.headlineFont(9), color = ConsoleTheme.outline)
            Text(text = levelLabel, style = ConsoleTheme.monoFont(11, androidx.compose.ui.text.font.FontWeight.Medium), color = ConsoleTheme.onSurfaceVariant)
        }
        Slider(
            value = volume.toFloat(),
            onValueChange = { onVolumeChange(it.toDouble()) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = StemIcon.color(stem), activeTrackColor = StemIcon.color(stem)),
        )
    }
}
