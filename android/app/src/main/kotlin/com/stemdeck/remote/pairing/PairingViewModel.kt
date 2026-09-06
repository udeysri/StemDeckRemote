package com.stemdeck.remote.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stemdeck.remote.networking.PairingStore
import com.stemdeck.remote.networking.StemDeckClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Candidate https server awaiting trust confirmation, plus the fingerprint its certificate reported during the health-check probe. Plain http never reaches this state — there's no certificate to confirm. */
data class PendingTrust(val host: String, val port: Int, val fingerprint: String)

/**
 * Backs [PairingScreen] — the connect/pairing logic the iOS side keeps
 * inline in `PairingView` (`connect`/`handleScannedCode`), pulled out into a
 * proper `ViewModel` here since Compose screens don't own long-lived
 * mutable state the way a SwiftUI `View` struct's `@State` can.
 */
@HiltViewModel
class PairingViewModel @Inject constructor(private val pairingStore: PairingStore) : ViewModel() {
    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> get() = _isChecking

    private val _pendingTrust = MutableStateFlow<PendingTrust?>(null)
    val pendingTrust: StateFlow<PendingTrust?> get() = _pendingTrust

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> get() = _errorMessage

    fun dismissError() { _errorMessage.value = null }
    fun dismissPendingTrust() { _pendingTrust.value = null }

    fun handleScannedCode(code: String) {
        val uri = try {
            android.net.Uri.parse(code)
        } catch (e: Exception) {
            null
        }
        val host = uri?.host
        val scheme = uri?.scheme?.lowercase()
        if (uri == null || host.isNullOrEmpty() || (scheme != "http" && scheme != "https")) {
            _errorMessage.value = "That QR code isn't a StemDeck pairing code."
            return
        }
        val port = if (uri.port != -1) uri.port else if (scheme == "https") 8443 else 8000
        connect(scheme, host, port)
    }

    fun connect(scheme: String, host: String, port: Int) {
        val trimmedHost = host.trim()
        if (trimmedHost.isEmpty()) {
            _errorMessage.value = "Enter a valid host and port."
            return
        }

        _isChecking.value = true
        viewModelScope.launch {
            var reportedFingerprint: String? = null
            // Longer timeout than StemDeckClient's 2s default — this is the
            // first-ever connection attempt to this address, so give it
            // more room than an already-trusted server's routine requests.
            val client = StemDeckClient(scheme, trimmedHost, port, pinnedFingerprint = null, timeoutSeconds = 15) { fingerprint ->
                reportedFingerprint = fingerprint
            }
            try {
                client.checkHealth()
                _isChecking.value = false
                if (scheme == "https") {
                    val fingerprint = reportedFingerprint
                    if (fingerprint != null) {
                        _pendingTrust.value = PendingTrust(trimmedHost, port, fingerprint)
                    } else {
                        _errorMessage.value = "Connected, but StemDeck didn't present a certificate to verify."
                    }
                } else {
                    pairingStore.pair(scheme, trimmedHost, port, null)
                }
            } catch (e: StemDeckClient.ClientError.ServerRefused) {
                _isChecking.value = false
                _errorMessage.value = e.detail
            } catch (e: Exception) {
                _isChecking.value = false
                _errorMessage.value = "Couldn't reach StemDeck at $trimmedHost:$port over $scheme. Make sure it's running, \"Make available on your network\" is on, and your phone is on the same Wi-Fi."
            }
        }
    }

    fun confirmTrust() {
        val pending = _pendingTrust.value ?: return
        pairingStore.pair("https", pending.host, pending.port, pending.fingerprint)
        _pendingTrust.value = null
    }
}
