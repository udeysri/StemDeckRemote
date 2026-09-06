package com.stemdeck.remote.playback

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stemdeck.remote.models.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val PREFS_NAME = "stemdeck_prefs"
private const val KEY_VIEW_MODE = "mixer_view_mode"

private fun loadViewMode(context: Context): MixerViewMode {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_VIEW_MODE, null)
    return if (raw == MixerViewMode.ADVANCED.name) MixerViewMode.ADVANCED else MixerViewMode.SIMPLE
}

private fun saveViewMode(context: Context, mode: MixerViewMode) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_VIEW_MODE, mode.name).apply()
}

/**
 * Picks Simple or Advanced mixer content and hosts the states that come
 * before the mixer is playable: waiting on the download queue, then
 * building the audio graph. Direct port of the iOS side's `PlayerView`.
 *
 * The view model (and [coordinator]) are owned by [PlaybackCoordinator], not
 * by this composable — this screen is shown inside a full-screen
 * [androidx.compose.ui.window.Dialog] that can be dismissed back to the mini
 * player while the song keeps playing, so nothing here starts or stops
 * playback based on its own presence on screen.
 */
@Composable
fun PlayerScreen(job: Job, viewModel: PlayerViewModel, coordinator: PlaybackCoordinator, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val systemVolume = rememberSystemVolumeController()
    var viewMode by remember { mutableStateOf(loadViewMode(context)) }
    val scope = rememberCoroutineScope()

    fun toggleViewMode() {
        viewMode = if (viewMode == MixerViewMode.SIMPLE) MixerViewMode.ADVANCED else MixerViewMode.SIMPLE
        saveViewMode(context, viewMode)
    }

    Column(modifier = modifier.fillMaxSize().background(ConsoleTheme.background)) {
        when (val s = state) {
            is PlayerViewModel.State.WaitingForStems -> WaitingContent(job, coordinator)
            is PlayerViewModel.State.LoadingEngine -> LoadingContent()
            is PlayerViewModel.State.Failed -> FailedContent(s.message, viewModel, scope)
            is PlayerViewModel.State.Ready -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val minWidthDp = 340.dp
                    val minHeightDp = 460.dp
                    if (maxWidth < minWidthDp || maxHeight < minHeightDp) {
                        TooSmallContent()
                    } else if (viewMode == MixerViewMode.SIMPLE) {
                        SimpleConsoleView(
                            job = job,
                            viewModel = viewModel,
                            systemVolume = systemVolume,
                            onBack = { coordinator.minimize() },
                            onToggleViewMode = { toggleViewMode() },
                        )
                    } else {
                        AdvancedConsoleView(
                            job = job,
                            viewModel = viewModel,
                            systemVolume = systemVolume,
                            onBack = { coordinator.minimize() },
                            onToggleViewMode = { toggleViewMode() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitingContent(job: Job, coordinator: PlaybackCoordinator) {
    val statuses by coordinator.stemDownloadQueue.statuses.collectAsStateWithLifecycle()
    val status = statuses[job.id] ?: StemDownloadQueue.Status.NotQueued

    Box(modifier = Modifier.fillMaxSize().background(ConsoleTheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            when (status) {
                is StemDownloadQueue.Status.Downloading -> {
                    LinearProgressIndicator(progress = { status.progress.toFloat() }, color = ConsoleTheme.accent, modifier = Modifier.padding(horizontal = 40.dp))
                    Text(text = "Downloading stems… ${Math.round(status.progress * 100)}%", style = ConsoleTheme.monoFont(13), color = ConsoleTheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                }
                is StemDownloadQueue.Status.Queued -> {
                    CircularProgressIndicator(color = ConsoleTheme.accent)
                    Text(text = "Queued to download…", style = ConsoleTheme.monoFont(13), color = ConsoleTheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                }
                else -> CircularProgressIndicator(color = ConsoleTheme.accent)
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize().background(ConsoleTheme.background), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ConsoleTheme.onSurface)
    }
}

@Composable
private fun FailedContent(message: String, viewModel: PlayerViewModel, scope: CoroutineScope) {
    Box(modifier = Modifier.fillMaxSize().background(ConsoleTheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(text = message, style = ConsoleTheme.monoFont(13), color = ConsoleTheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(onClick = { scope.launch { viewModel.start() } }, modifier = Modifier.padding(top = 12.dp)) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun TooSmallContent() {
    Box(modifier = Modifier.fillMaxSize().background(ConsoleTheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(text = "Make This Window Bigger", style = ConsoleTheme.headlineFont(14), color = ConsoleTheme.onSurface)
            Text(
                text = "The stem mixer needs more room to lay out its controls. Resize or maximize this window to keep playing.",
                style = ConsoleTheme.monoFont(12),
                color = ConsoleTheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
