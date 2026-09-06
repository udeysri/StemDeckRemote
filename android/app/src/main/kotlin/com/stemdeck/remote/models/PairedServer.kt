package com.stemdeck.remote.models

import kotlinx.serialization.Serializable

/**
 * A StemDeck desktop instance this device has paired with.
 *
 * `scheme` matters because a StemDeck instance only serves https when its
 * "Make available on your network" setting has generated a LAN certificate
 * (desktop app Settings → Network); until then, the address it advertises
 * is plain http, and the desktop backend accepts that from other devices on
 * purpose (`_secure_origin_required()` in `app/main.py` only enforces https
 * in standalone server mode, not the desktop shell). `certFingerprint` is
 * null for an http pairing — there's no certificate to pin. When present,
 * it isn't secret (it's broadcast in every TLS handshake with the server),
 * so this whole struct is stored in plain DataStore preferences rather than
 * `EncryptedSharedPreferences`.
 */
@Serializable
data class PairedServer(
    val scheme: String,
    val host: String,
    val port: Int,
    val certFingerprint: String? = null,
    val pairedAtEpochMillis: Long,
) {
    val isSecure: Boolean get() = scheme == "https"

    val baseUrl: String get() = "$scheme://$host:$port"

    val displayAddress: String get() = "$host:$port"

    companion object {
        /**
         * Placeholder passed when playing a bundled sample song (see
         * `SampleSongCatalog`), which never makes a network request — stems
         * and peaks are read straight from the app's assets, so none of this
         * class's fields are ever actually used.
         */
        val sample = PairedServer(scheme = "sample", host = "bundled", port = 0, certFingerprint = null, pairedAtEpochMillis = 0)
    }
}
