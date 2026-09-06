package com.stemdeck.remote.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stemdeck.remote.models.Job

/**
 * The plain, casual alternative to the hardware-console mixer: one small
 * overall waveform and a fill-bar volume slider per stem (plus Mute/Solo) —
 * no per-stem waveform, no dB-precision readouts. Direct port of the iOS
 * side's `SimpleConsoleView`. Shares [BottomPlayerArea] with
 * [AdvancedConsoleView].
 */
@Composable
fun SimpleConsoleView(
    job: Job,
    viewModel: PlayerViewModel,
    systemVolume: SystemVolumeController,
    onBack: () -> Unit,
    onToggleViewMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val peaks by viewModel.peaks.collectAsStateWithLifecycle()
    val progress = viewModel.progress
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isLooping by viewModel.isLooping.collectAsStateWithLifecycle()
    val playbackRate by viewModel.playbackRate.collectAsStateWithLifecycle()
    val pitchSemitones by viewModel.pitchSemitones.collectAsStateWithLifecycle()
    val systemVolumeValue by systemVolume.volume.collectAsStateWithLifecycle()
    val markInTime by viewModel.markInTime.collectAsStateWithLifecycle()
    val markOutTime by viewModel.markOutTime.collectAsStateWithLifecycle()

    var scrubProgress by remember { mutableStateOf<Double?>(null) }
    val combinedPeaks = remember(peaks) { PeaksMerger.combine(peaks) }
    val markInFraction = markInTime?.takeIf { duration > 0 }?.let { it / duration }
    val markOutFraction = markOutTime?.takeIf { duration > 0 }?.let { it / duration }

    val headerHeight = 56.dp
    val waveformHeight = 80.dp
    val bottomPlayerHeight = 190.dp
    val contentPadding = 16.dp
    val minRowHeight = 44.dp
    val maxRowHeight = 60.dp
    val rowSpacing = 8.dp

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(ConsoleTheme.background)) {
        val rowCount = channels.size
        val fixedHeight = headerHeight + waveformHeight + bottomPlayerHeight + contentPadding
        val available = maxHeight - fixedHeight - contentPadding * 2
        val spacingTotal = rowSpacing * maxOf(0, rowCount - 1)
        val idealRowHeight = if (rowCount > 0) (available - spacingTotal) / rowCount else minRowHeight
        val rowHeight = idealRowHeight.coerceIn(minRowHeight, maxRowHeight)
        val fitsWithoutScrolling = idealRowHeight >= minRowHeight

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = contentPadding, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(icon = Icons.Filled.KeyboardArrowDown, onClick = onBack, modifier = Modifier.size(32.dp))
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(text = job.title ?: "Untitled", fontSize = 17.sp, color = Color.White, maxLines = 1)
                    job.subtitle?.let { Text(text = it, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f)) }
                }
                RoundIconButton(icon = Icons.Filled.GraphicEq, onClick = onToggleViewMode, modifier = Modifier.size(32.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(waveformHeight)
                    .padding(horizontal = contentPadding)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black),
            ) {
                WaveformView(
                    peaks = combinedPeaks,
                    color = Color.White,
                    progress = scrubProgress ?: progress,
                    markInFraction = markInFraction,
                    markOutFraction = markOutFraction,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(duration) {
                            detectTapGestures { offset -> if (duration > 0) viewModel.seek((offset.x / size.width).toDouble().coerceIn(0.0, 1.0) * duration) }
                        }
                        .pointerInput(duration) {
                            var lastFraction = 0.0
                            detectDragGestures(
                                onDragEnd = { scrubProgress = null; if (duration > 0) viewModel.seek(lastFraction * duration) },
                                onDragCancel = { scrubProgress = null },
                            ) { change, _ ->
                                change.consume()
                                lastFraction = (change.position.x / size.width).toDouble().coerceIn(0.0, 1.0)
                                scrubProgress = lastFraction
                            }
                        },
                )
            }

            val rows: @Composable () -> Unit = {
                channels.forEach { channel ->
                    SimpleChannelRow(
                        channel = channel,
                        rowHeight = rowHeight,
                        onVolumeChange = { viewModel.setVolume(it, channel.id) },
                        onSolo = { viewModel.toggleSolo(channel.id) },
                        onMute = { viewModel.toggleMute(channel.id) },
                        modifier = Modifier.fillMaxWidth().height(rowHeight).padding(bottom = rowSpacing),
                    )
                }
            }

            if (fitsWithoutScrolling) {
                Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding, vertical = 12.dp)) {
                    rows()
                    Spacer(modifier = Modifier.weight(1f))
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding, vertical = 12.dp)) {
                    items(channels, key = { it.id }) { channel ->
                        SimpleChannelRow(
                            channel = channel,
                            rowHeight = rowHeight,
                            onVolumeChange = { viewModel.setVolume(it, channel.id) },
                            onSolo = { viewModel.toggleSolo(channel.id) },
                            onMute = { viewModel.toggleMute(channel.id) },
                            modifier = Modifier.fillMaxWidth().height(rowHeight).padding(bottom = rowSpacing),
                        )
                    }
                }
            }

            BottomPlayerArea(
                isPlaying = isPlaying,
                isLooping = isLooping,
                isMarkInSet = markInTime != null,
                isMarkOutSet = markOutTime != null,
                currentTime = scrubProgress?.let { it * duration } ?: currentTime,
                duration = duration,
                playbackRate = playbackRate,
                pitchSemitones = pitchSemitones,
                masterVolume = systemVolumeValue.toDouble(),
                onToggle = { viewModel.togglePlayback() },
                onToggleLoop = { viewModel.toggleLoop() },
                onMarkIn = { viewModel.markIn() },
                onMarkOut = { viewModel.markOut() },
                onSeek = { viewModel.seek(it) },
                onRateChange = { viewModel.setPlaybackRate(it) },
                onPitchChange = { viewModel.setPitch(it) },
                onVolumeChange = { systemVolume.setVolume(it.toFloat()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomPlayerHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ConsoleTheme.surfaceContainerHigh)
                    .padding(horizontal = contentPadding)
                    .padding(bottom = contentPadding),
            )
        }
    }
}

/** One stem: its own fill-bar volume slider (dimmed while muted, but still draggable) plus Mute/Solo buttons sized to match the row. */
@Composable
private fun SimpleChannelRow(
    channel: PlayerViewModel.StemChannel,
    rowHeight: androidx.compose.ui.unit.Dp,
    onVolumeChange: (Double) -> Unit,
    onSolo: () -> Unit,
    onMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SimpleFillBar(
            stem = channel.id,
            value = channel.volume,
            onValueChange = onVolumeChange,
            dimmed = channel.isMuted,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        SquareButton(label = "M", active = channel.isMuted, activeColor = Color.Red, onClick = onMute, size = rowHeight)
        SquareButton(label = "S", active = channel.isSoloed, activeColor = Color.Blue, onClick = onSolo, size = rowHeight)
    }
}

/** A rounded fill-bar slider — drag anywhere on it to set the level. */
@Composable
private fun SimpleFillBar(stem: String, value: Double, onValueChange: (Double) -> Unit, dimmed: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(ConsoleTheme.surfaceContainerHigh)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onValueChange((change.position.x / size.width).toDouble().coerceIn(0.0, 1.0))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> onValueChange((offset.x / size.width).toDouble().coerceIn(0.0, 1.0)) }
            },
    ) {
        val fillWidth = maxWidth * value.toFloat()
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(maxOf(fillWidth, maxHeight))
                .clip(RoundedCornerShape(50))
                .background(StemIcon.color(stem)),
        )
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(StemIcon.icon(stem), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(text = StemIcon.displayName(stem), color = Color.White, fontSize = 15.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp).weight(1f))
        }
        if (dimmed) {
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)))
        }
    }
}

@Composable
private fun SquareButton(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(if (active) activeColor else ConsoleTheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White, fontSize = minOf(15f, size.value * 0.28f).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}
