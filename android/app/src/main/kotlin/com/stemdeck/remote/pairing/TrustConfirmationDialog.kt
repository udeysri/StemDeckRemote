package com.stemdeck.remote.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * The native equivalent of the browser's one-time "your connection is not
 * private" click-through: shown once per StemDeck server so the user can
 * confirm they recognize the machine before its certificate gets pinned.
 * Direct port of the iOS side's `TrustConfirmationSheet`.
 */
@Composable
fun TrustConfirmationDialog(host: String, port: Int, fingerprint: String, onTrust: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFFA000)) },
        title = { Text("Unverified Certificate") },
        text = {
            Column {
                Text("StemDeck at $host:$port is using a certificate it generated itself, so this app can't verify it against a trusted authority — the same reason a browser would show a warning here.")
                Text("Only continue if this is your own computer, on your own network.", modifier = Modifier.padding(top = 12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(androidx.compose.ui.graphics.Color(0xFF1F1F23), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Text("Certificate fingerprint", fontFamily = FontFamily.Default, color = Color.Gray, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    Text(fingerprint, fontFamily = FontFamily.Monospace, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = { Button(onClick = onTrust) { Text("Trust & Connect") } },
        dismissButton = { OutlinedButton(onClick = onCancel) { Text("Cancel") } },
    )
}
