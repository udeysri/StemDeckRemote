package com.stemdeck.remote.playback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stemdeck.remote.models.Job

/**
 * The YouTube-Music-style compact bar shown above the app's content whenever
 * a song is loaded but the full stem console has been minimized: thumbnail,
 * title, play/pause, and a thin progress line along the top edge. Tapping
 * anywhere but the play/pause button re-expands the console.
 */
@Composable
fun MiniPlayerView(job: Job, viewModel: PlayerViewModel, onExpand: () -> Unit, onTogglePlay: () -> Unit) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val progress = if (duration > 0) (currentTime / duration).coerceIn(0.0, 1.0) else 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surfaceContainerHigh)
            .clickable(onClick = onExpand),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            drawRect(color = Color.White.copy(alpha = 0.15f))
            drawRect(color = Color.White, topLeft = Offset.Zero, size = Size(size.width * progress.toFloat(), size.height))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(ConsoleTheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (job.thumbnail != null) {
                    AsyncImage(model = job.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)))
                } else {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                }
            }

            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text = job.title ?: "Untitled", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                job.subtitle?.let {
                    Text(text = it, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1)
                }
            }

            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
