package com.stemdeck.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stemdeck.remote.library.LibraryScreen
import com.stemdeck.remote.networking.PairingStore
import com.stemdeck.remote.pairing.PairingScreen
import com.stemdeck.remote.playback.MiniPlayerView
import com.stemdeck.remote.playback.PlaybackCoordinator
import com.stemdeck.remote.playback.PlayerScreen
import com.stemdeck.remote.ui.theme.StemDeckRemoteTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The single Activity hosting the whole Compose UI tree — Android's
 * counterpart to iOS's `StemDeckRemoteApp` (`WindowGroup { ContentView() }`).
 * Root-level switch between the pairing flow and the library, plus the
 * mini player / full-screen console overlay, lives in [RootScreen] below,
 * mirroring `ContentView.swift` directly.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var pairingStore: PairingStore

    @Inject lateinit var playbackCoordinator: PlaybackCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StemDeckRemoteTheme {
                RootScreen(pairingStore = pairingStore, playback = playbackCoordinator)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun RootScreen(pairingStore: PairingStore, playback: PlaybackCoordinator) {
    val server by pairingStore.current.collectAsStateWithLifecycle()
    val job by playback.job.collectAsStateWithLifecycle()
    val viewModel by playback.viewModel.collectAsStateWithLifecycle()
    val isExpanded by playback.isExpanded.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        if (server != null) {
            LibraryScreen(server = server!!, coordinator = playback)
        } else {
            PairingScreen(coordinator = playback)
        }

        val currentViewModel = viewModel
        val currentJob = job
        if (currentViewModel != null && currentJob != null) {
            val isPlaying by currentViewModel.isPlaying.collectAsStateWithLifecycle()
            AnimatedVisibility(
                visible = isPlaying && !isExpanded,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(animationSpec = tween(250)) { it } + fadeIn(tween(250)),
                exit = slideOutVertically(animationSpec = tween(250)) { it } + fadeOut(tween(250)),
            ) {
                MiniPlayerView(
                    job = currentJob,
                    viewModel = currentViewModel,
                    onExpand = { playback.expand() },
                    onTogglePlay = { currentViewModel.togglePlayback() },
                )
            }

            if (isExpanded) {
                Dialog(
                    onDismissRequest = { playback.minimize() },
                    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                ) {
                    PlayerScreen(job = currentJob, viewModel = currentViewModel, coordinator = playback)
                }
            }
        }
    }
}
