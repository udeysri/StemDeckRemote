package com.stemdeck.remote.playback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stemdeck.remote.playback.ConsoleTheme.color

/**
 * The mixer's bottom player: a big row split into a 2x2 transport button
 * grid (left) and three vertical faders (right), then a thin progress row
 * underneath. Direct port of the iOS side's `BottomPlayerArea`.
 */
@Composable
fun BottomPlayerArea(
    isPlaying: Boolean,
    isLooping: Boolean,
    currentTime: Double,
    duration: Double,
    playbackRate: Double,
    pitchSemitones: Int,
    masterVolume: Double,
    onToggle: () -> Unit,
    onToggleLoop: () -> Unit,
    onMarkIn: () -> Unit,
    onMarkOut: () -> Unit,
    onSeek: (Double) -> Unit,
    onRateChange: (Double) -> Unit,
    onPitchChange: (Double) -> Unit,
    onVolumeChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ButtonGrid(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                isPlaying = isPlaying,
                isLooping = isLooping,
                onToggle = onToggle,
                onToggleLoop = onToggleLoop,
                onMarkIn = onMarkIn,
                onMarkOut = onMarkOut,
            )
            androidx.compose.foundation.layout.Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.4f)))
            Faders(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                playbackRate = playbackRate,
                pitchSemitones = pitchSemitones,
                masterVolume = masterVolume,
                onRateChange = onRateChange,
                onPitchChange = onPitchChange,
                onVolumeChange = onVolumeChange,
            )
        }

        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.5f)))

        ProgressRow(
            currentTime = currentTime,
            duration = duration,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ButtonGrid(
    isPlaying: Boolean,
    isLooping: Boolean,
    onToggle: () -> Unit,
    onToggleLoop: () -> Unit,
    onMarkIn: () -> Unit,
    onMarkOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TransportButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                isActive = isPlaying,
                activeColor = ConsoleTheme.accent,
                onClick = onToggle,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            TransportButton(
                icon = Icons.Filled.Repeat,
                isActive = isLooping,
                activeColor = color(0xf2e07a),
                onClick = onToggleLoop,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TransportButton(icon = Icons.Filled.SkipNext, isActive = false, onClick = onMarkIn, modifier = Modifier.weight(1f).fillMaxHeight())
            TransportButton(icon = Icons.Filled.SkipPrevious, isActive = false, onClick = onMarkOut, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = ConsoleTheme.accent,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) activeColor else ConsoleTheme.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isActive) Color.Black.copy(alpha = 0.85f) else ConsoleTheme.onSurfaceVariant,
            modifier = Modifier.height(20.dp).aspectRatio(1f),
        )
    }
}

@Composable
private fun Faders(
    playbackRate: Double,
    pitchSemitones: Int,
    masterVolume: Double,
    onRateChange: (Double) -> Unit,
    onPitchChange: (Double) -> Unit,
    onVolumeChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.padding(vertical = 14.dp, horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        VerticalLEDFader(
            label = "SPEED",
            valueText = speedLabel(playbackRate),
            color = color(0x8ecae6),
            value = playbackRate,
            onValueChange = onRateChange,
            range = PlayerViewModel.SPEED_RANGE.first..PlayerViewModel.SPEED_RANGE.second,
            centered = true,
            doubleTapResetValue = 1.0,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        VerticalLEDFader(
            label = "KEY",
            valueText = pitchLabel(pitchSemitones),
            color = color(0xb8a7ea),
            value = pitchSemitones.toDouble(),
            onValueChange = onPitchChange,
            range = PlayerViewModel.PITCH_RANGE.first.toDouble()..PlayerViewModel.PITCH_RANGE.last.toDouble(),
            centered = true,
            doubleTapResetValue = 0.0,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        VerticalLEDFader(
            label = "VOL",
            valueText = "${Math.round(masterVolume * 100)}",
            color = color(0x98d8c8),
            value = masterVolume,
            onValueChange = onVolumeChange,
            range = 0.0..1.0,
            centered = false,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun ProgressRow(currentTime: Double, duration: Double, onSeek: (Double) -> Unit, modifier: Modifier = Modifier) {
    val fraction = if (duration > 0) (currentTime / duration).coerceIn(0.0, 1.0) else 0.0

    Row(modifier = modifier, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = formatTime(currentTime), style = ConsoleTheme.monoFont(10), color = ConsoleTheme.onSurfaceVariant, modifier = Modifier.width(32.dp))

        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(16.dp)
                .pointerInput(duration) {
                    detectTapGestures { offset -> if (duration > 0) onSeek((offset.x / size.width).toDouble().coerceIn(0.0, 1.0) * duration) }
                }
                .pointerInput(duration) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        if (duration > 0) onSeek((change.position.x / size.width).toDouble().coerceIn(0.0, 1.0) * duration)
                    }
                },
        ) {
            val barHeight = 4.dp.toPx()
            val y = (size.height - barHeight) / 2
            drawRoundRect(color = ConsoleTheme.recessedWell, topLeft = Offset(0f, y), size = Size(size.width, barHeight), cornerRadius = CornerRadius(barHeight / 2))
            drawRoundRect(color = color(0xf4a6c1), topLeft = Offset(0f, y), size = Size(size.width * fraction.toFloat(), barHeight), cornerRadius = CornerRadius(barHeight / 2))
        }

        Text(text = formatTime(duration), style = ConsoleTheme.monoFont(10), color = ConsoleTheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
    }
}

private fun speedLabel(rate: Double): String {
    var text = "%.2f".format(rate)
    while (text.endsWith("0")) text = text.dropLast(1)
    if (text.endsWith(".")) text = text.dropLast(1)
    return "${text}x"
}

private fun pitchLabel(semitones: Int): String = if (semitones == 0) "0" else "%+d".format(semitones)

private fun formatTime(time: Double): String {
    if (!time.isFinite() || time < 0) return "0:00"
    val total = Math.round(time).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}
