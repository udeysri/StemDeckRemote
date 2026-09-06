package com.stemdeck.remote.pairing

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stemdeck.remote.models.PairedServer
import com.stemdeck.remote.playback.PlaybackCoordinator

/**
 * "Check Sample Songs" destination from the home screen: lets people
 * explore the stem console using 3 bundled, Creative Commons-licensed songs
 * without pairing to a live StemDeck instance. Direct port of the iOS
 * side's `SampleSongsView`.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SampleSongsScreen(coordinator: PlaybackCoordinator, onDismiss: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sample Songs") },
                actions = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Done") } },
            )
        },
        bottomBar = {
            Text(
                text = "Licensed under Creative Commons (CC BY) via the Free Music Archive.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(SampleSongCatalog.all) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val stemUris = SampleSongCatalog.stemAssetPaths(entry.job.id).mapValues { (_, path) -> Uri.parse("asset:///$path") }
                            coordinator.play(
                                job = entry.job,
                                server = PairedServer.sample,
                                localStemUris = stemUris,
                                localPeaksAssetPath = SampleSongCatalog.peaksAssetPath(entry.job.id),
                            )
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(text = entry.job.title ?: "Untitled")
                        entry.job.subtitle?.let { Text(text = it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(
                            text = "${entry.artist} • ${entry.source} • ${entry.license}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }

                    entry.job.formattedDuration?.let {
                        Text(text = it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
