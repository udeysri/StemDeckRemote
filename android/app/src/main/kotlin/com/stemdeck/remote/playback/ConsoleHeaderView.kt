package com.stemdeck.remote.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stemdeck.remote.models.Job

/**
 * Song title + the analysis readouts StemDeck already computed (BPM, key)
 * styled as the brief's LCD-style panel readouts. Direct port of the iOS
 * side's `ConsoleHeaderView`.
 */
@Composable
fun ConsoleHeaderView(
    job: Job,
    modifier: Modifier = Modifier,
    currentChord: String? = null,
    isAnalyzingChords: Boolean = false,
    onBack: (() -> Unit)? = null,
    onToggleViewMode: (() -> Unit)? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onBack != null) {
            RoundIconButton(icon = Icons.Filled.KeyboardArrowDown, onClick = onBack)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = "STEM CONSOLE", style = ConsoleTheme.headlineFont(10), color = ConsoleTheme.accent)
            Text(text = job.title ?: "Untitled", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ConsoleTheme.onSurface, maxLines = 1)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            job.bpm?.let { Readout(value = it.toString(), label = "BPM") }
            job.key?.let { key ->
                val suffix = if (job.scale?.lowercase()?.contains("minor") == true) "m" else ""
                Readout(value = key + suffix, label = "KEY")
            }
            if (currentChord != null) {
                Readout(value = currentChord, label = "CHORD", accent = true)
            } else if (isAnalyzingChords) {
                Readout(value = "…", label = "CHORD")
            }
        }

        if (onToggleViewMode != null) {
            RoundIconButton(icon = Icons.Filled.GraphicEq, onClick = onToggleViewMode)
        }
    }
}

@Composable
private fun Readout(value: String, label: String, accent: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 44.dp)
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .recessedWell(cornerRadius = 6),
    ) {
        Text(text = value, style = ConsoleTheme.monoFont(15, androidx.compose.ui.text.font.FontWeight.SemiBold), color = if (accent) ConsoleTheme.accent else ConsoleTheme.onSurface)
        Text(text = label, style = ConsoleTheme.headlineFont(8), color = ConsoleTheme.outline)
    }
}

@Composable
fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(ConsoleTheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ConsoleTheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}
