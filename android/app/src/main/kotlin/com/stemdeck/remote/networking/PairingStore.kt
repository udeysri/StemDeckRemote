package com.stemdeck.remote.networking

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stemdeck.remote.models.PairedServer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val Context.pairingDataStore by preferencesDataStore(name = "stemdeck_pairing")

/**
 * Persists the paired StemDeck server across launches and publishes changes
 * so the root composable can switch between pairing and library UI — the
 * Android equivalent of iOS's `PairingStore` (`UserDefaults`-backed
 * `ObservableObject`), backed here by Jetpack DataStore instead.
 */
@Singleton
class PairingStore @Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("paired_server")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _current = MutableStateFlow<PairedServer?>(null)
    val current: StateFlow<PairedServer?> get() = _current

    init {
        scope.launch {
            _current.value = load()
        }
    }

    fun pair(scheme: String, host: String, port: Int, certFingerprint: String?) {
        val server = PairedServer(scheme, host, port, certFingerprint, System.currentTimeMillis())
        _current.value = server
        scope.launch { save(server) }
    }

    fun forget() {
        _current.value = null
        scope.launch {
            context.pairingDataStore.edit { it.remove(key) }
        }
    }

    private suspend fun save(server: PairedServer) {
        val encoded = json.encodeToString(PairedServer.serializer(), server)
        context.pairingDataStore.edit { it[key] = encoded }
    }

    private suspend fun load(): PairedServer? {
        val prefs = context.pairingDataStore.data.first()
        val raw = prefs[key] ?: return null
        return try {
            json.decodeFromString(PairedServer.serializer(), raw)
        } catch (e: Exception) {
            null
        }
    }
}
