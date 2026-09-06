package com.stemdeck.remote.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stemdeck.remote.models.Job

/**
 * The full hardware-console mixer. Header, master waveform, the six-stem
 * rack, and the bottom player are all one continuous panel — a direct port
 * of the iOS side's `AdvancedConsoleView`, adapted from SwiftUI's
 * `GeometryReader`-driven dynamic row sizing to Compose's [BoxWithConstraints].
 *
 * Only the master waveform (top) is draggable/seekable; each stem's own
 * waveform is purely visual, tinted by shared playback progress.
 */
@Composable
fun AdvancedConsoleView(
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
    val liveLevels by viewModel.liveLevels.collectAsStateWithLifecycle()
    val currentChord by viewModel.currentChord.collectAsStateWithLifecycle()
    val isAnalyzingChords by viewModel.isAnalyzingChords.collectAsStateWithLifecycle()
    val systemVolumeValue by systemVolume.volume.collectAsStateWithLifecycle()

    var scrubProgress by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Double?>(null) }

    val combinedPeaks = androidx.compose.runtime.remember(peaks) { PeaksMerger.combine(peaks) }

    val headerHeight = 54.dp
    val waveformHeight = 48.dp
    val bottomPlayerHeight = 190.dp
    val controlColumnWidth = 170.dp
    val minRowHeight = 64.dp
    val maxRowHeight = 88.dp

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val rowCount = maxOf(1, channels.size)
        val available = maxHeight - headerHeight - waveformHeight - bottomPlayerHeight
        val idealRowHeight = available / rowCount
        val rowHeight = idealRowHeight.coerceIn(minRowHeight, maxRowHeight)
        val fitsWithoutScrolling = idealRowHeight >= minRowHeight

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ConsoleTheme.surfaceContainerHigh)
                .border(1.dp, Color.Black.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp)),
        ) {
            ConsoleHeaderView(
                job = job,
                currentChord = currentChord,
                isAnalyzingChords = isAnalyzingChords,
                onBack = onBack,
                onToggleViewMode = onToggleViewMode,
                modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Divider()

            Box(modifier = Modifier.fillMaxWidth().height(waveformHeight).padding(horizontal = 16.dp, vertical = 8.dp).recessedWell(8)) {
                WaveformView(
                    peaks = combinedPeaks,
                    color = if (isLooping) ConsoleTheme.color(0xf2e07a) else ConsoleTheme.onSurface,
                    progress = scrubProgress ?: progress,
                    modifier = Modifier
                        .fillMaxSize()
                        .seekableWaveform(duration, onScrub = { scrubProgress = it }, onSeek = { viewModel.seek(it) }),
                )
            }

            Divider()

            if (fitsWithoutScrolling) {
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    channels.forEachIndexed { index, channel ->
                        AdvancedChannelRow(
                            channel = channel,
                            peaks = peaks[channel.id] ?: emptyList(),
                            progress = scrubProgress ?: progress,
                            liveLevel = liveLevels[channel.id],
                            controlColumnWidth = controlColumnWidth,
                            onVolumeChange = { viewModel.setVolume(it, channel.id) },
                            onSolo = { viewModel.toggleSolo(channel.id) },
                            onMute = { viewModel.toggleMute(channel.id) },
                            modifier = Modifier.fillMaxWidth().height(rowHeight),
                        )
                        if (index < channels.size - 1) {
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.3f)))
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(channels, key = { it.id }) { channel ->
                        AdvancedChannelRow(
                            channel = channel,
                            peaks = peaks[channel.id] ?: emptyList(),
                            progress = scrubProgress ?: progress,
                            liveLevel = liveLevels[channel.id],
                            controlColumnWidth = controlColumnWidth,
                            onVolumeChange = { viewModel.setVolume(it, channel.id) },
                            onSolo = { viewModel.toggleSolo(channel.id) },
                            onMute = { viewModel.toggleMute(channel.id) },
                            modifier = Modifier.fillMaxWidth().height(rowHeight),
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.3f)))
                    }
                }
            }

            Divider()

            BottomPlayerArea(
                isPlaying = isPlaying,
                isLooping = isLooping,
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
                modifier = Modifier.fillMaxWidth().height(bottomPlayerHeight),
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.5f)))
}

/** One stem: left column is icon/name + Solo/Mute above a full-width volume slider; right column is that stem's waveform, tinted by shared playback progress but otherwise non-interactive. */
@Composable
private fun AdvancedChannelRow(
    channel: PlayerViewModel.StemChannel,
    peaks: List<List<Double>>,
    progress: Double,
    liveLevel: Double?,
    controlColumnWidth: Dp,
    onVolumeChange: (Double) -> Unit,
    onSolo: () -> Unit,
    onMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Live reading is post-fader already, on a dB scale; capping it at the
    // fader's own value guarantees the meter's peak height always tracks
    // the fader directly. Muting always wins.
    val effectiveLevel = when {
        channel.isMuted -> 0.0
        liveLevel != null -> minOf(liveLevel, channel.volume)
        else -> channel.volume
    }

    Row(modifier = modifier) {
        StemLevelMeter(
            color = StemIcon.color(channel.id),
            level = effectiveLevel,
            isMuted = channel.isMuted,
            modifier = Modifier.width(8.dp).fillMaxHeight().padding(vertical = 6.dp, horizontal = 2.dp),
        )

        Column(modifier = Modifier.width(controlColumnWidth).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ChannelLabel(stem = channel.id, isAudible = !channel.isMuted)
                SoloMuteButtons(isSoloed = channel.isSoloed, isMuted = channel.isMuted, onSolo = onSolo, onMute = onMute)
            }
            Slider(
                value = channel.volume.toFloat(),
                onValueChange = { onVolumeChange(it.toDouble()) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = StemIcon.color(channel.id), activeTrackColor = StemIcon.color(channel.id)),
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.4f)))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(6.dp)
                .background(ConsoleTheme.recessedWell),
        ) {
            WaveformView(
                peaks = peaks,
                color = StemIcon.color(channel.id),
                progress = progress,
                modifier = Modifier.fillMaxSize().alpha(if (channel.isMuted) 0.4f else 1f),
            )
        }
    }
}

/**
 * Tap-to-seek plus live drag-to-scrub over a waveform — the Compose
 * equivalent of the iOS side's single `DragGesture(minimumDistance: 0)`
 * (which fires on a plain tap too). Compose's `detectDragGestures` requires
 * clearing touch slop before it recognizes a drag at all, so a bare tap
 * needs its own `detectTapGestures` alongside it rather than falling out of
 * one combined gesture.
 */
private fun Modifier.seekableWaveform(duration: Double, onScrub: (Double?) -> Unit, onSeek: (Double) -> Unit): Modifier =
    this
        .pointerInput(duration) {
            detectTapGestures { offset ->
                if (duration > 0) onSeek((offset.x / size.width).toDouble().coerceIn(0.0, 1.0) * duration)
            }
        }
        .pointerInput(duration) {
            var lastFraction = 0.0
            detectDragGestures(
                onDragEnd = {
                    onScrub(null)
                    if (duration > 0) onSeek(lastFraction * duration)
                },
                onDragCancel = { onScrub(null) },
            ) { change, _ ->
                change.consume()
                lastFraction = (change.position.x / size.width).toDouble().coerceIn(0.0, 1.0)
                onScrub(lastFraction)
            }
        }
