package com.stemdeck.remote.pairing

import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.stemdeck.remote.R
import com.stemdeck.remote.playback.PlaybackCoordinator

/**
 * The app's first-run / signed-out home screen: introduces StemDeck Remote
 * and offers the three ways in (QR pairing, manual address, sample songs).
 * Direct port of the iOS side's `PairingView`.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PairingScreen(coordinator: PlaybackCoordinator, viewModel: PairingViewModel = hiltViewModel()) {
    val isChecking by viewModel.isChecking.collectAsStateWithLifecycle()
    val pendingTrust by viewModel.pendingTrust.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var isScanning by rememberSaveable { mutableStateOf(false) }
    var isShowingManualConnect by rememberSaveable { mutableStateOf(false) }
    var isShowingSampleSongs by rememberSaveable { mutableStateOf(false) }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                Hero()
                Spacer(modifier = Modifier.height(32.dp))
                AboutSection()
                Spacer(modifier = Modifier.height(32.dp))
                Actions(
                    isChecking = isChecking,
                    onScanQr = {
                        if (cameraPermission.status.isGranted) isScanning = true else cameraPermission.launchPermissionRequest()
                    },
                    onManualConnect = { isShowingManualConnect = true },
                    onSampleSongs = { isShowingSampleSongs = true },
                )
            }
        }

        if (isScanning) {
            Dialog(onDismissRequest = { isScanning = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    QRScannerScreen(onCode = { code ->
                        isScanning = false
                        viewModel.handleScannedCode(code)
                    })
                    androidx.compose.material3.IconButton(
                        onClick = { isScanning = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
    }

    // The https path needs the manual-connect sheet out of the way so the
    // trust-confirmation dialog reads as its own step, not something
    // stacked on top of the sheet; the http path doesn't need this at all —
    // RootScreen swaps this whole screen out for the library the moment
    // pairing succeeds.
    androidx.compose.runtime.LaunchedEffect(pendingTrust) {
        if (pendingTrust != null) isShowingManualConnect = false
    }

    if (isShowingManualConnect) {
        ManualConnectSheet(
            isChecking = isChecking,
            onDismiss = { isShowingManualConnect = false },
            onConnect = { scheme, host, port ->
                viewModel.connect(scheme, host, port)
            },
        )
    }

    if (isShowingSampleSongs) {
        Dialog(onDismissRequest = { isShowingSampleSongs = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            SampleSongsScreen(coordinator = coordinator, onDismiss = { isShowingSampleSongs = false })
        }
    }

    pendingTrust?.let { pending ->
        com.stemdeck.remote.pairing.TrustConfirmationDialog(
            host = pending.host,
            port = pending.port,
            fingerprint = pending.fingerprint,
            onTrust = {
                viewModel.confirmTrust()
                isShowingManualConnect = false
            },
            onCancel = { viewModel.dismissPendingTrust() },
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Couldn't Connect") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { viewModel.dismissError() }) { Text("OK") } },
        )
    }
}

@Composable
private fun Hero() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Image(painter = painterResource(R.drawable.stemdeck_logo), contentDescription = null, modifier = Modifier.size(120.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("StemDeck Remote", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Your StemDeck library, remote in your pocket.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AboutSection() {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "StemDeck Remote is the companion app for StemDeck, the desktop app that separates songs into vocals, drums, bass, guitar, piano, and more.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Pair with StemDeck over your local network to sync your library, download stems to your phone, and mix them live on a per-stem console — no cables, no computer required once you've paired.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.clickableNoRipple { uriHandler.openUri("https://github.com/stemdeckapp/stemdeck") },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "stemdeckapp/stemdeck on GitHub",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

@Composable
private fun Actions(isChecking: Boolean, onScanQr: () -> Unit, onManualConnect: () -> Unit, onSampleSongs: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onScanQr, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan QR Code")
        }
        OutlinedButton(onClick = onManualConnect, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Connect with URL")
        }
        OutlinedButton(onClick = onSampleSongs, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Check Sample Songs")
        }
        Text(
            "Open StemDeck on your computer, go to Settings → Network, and scan the QR code shown there — or enter its address manually.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (isChecking) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Text("Connecting…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ManualConnectSheet(isChecking: Boolean, onDismiss: () -> Unit, onConnect: (String, String, Int) -> Unit) {
    var host by rememberSaveable { mutableStateOf("") }
    var portText by rememberSaveable { mutableStateOf("8443") }
    var useHttps by rememberSaveable { mutableStateOf(true) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Connect with URL", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host or IP (e.g. 192.168.1.20)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter(Char::isDigit) },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Use HTTPS")
                Switch(checked = useHttps, onCheckedChange = { useHttps = it })
            }
            Text(
                "Match the address shown in StemDeck's Settings → Network. If \"Make available on your network\" hasn't generated a certificate yet, StemDeck serves plain http — turn HTTPS off here to match.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        val port = portText.toIntOrNull() ?: if (useHttps) 8443 else 8000
                        onConnect(if (useHttps) "https" else "http", host, port)
                    },
                    enabled = host.trim().isNotEmpty() && !isChecking,
                    modifier = Modifier.weight(1f),
                ) { Text("Connect") }
            }
            if (isChecking) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text("Connecting…")
                }
            }
        }
    }
}
