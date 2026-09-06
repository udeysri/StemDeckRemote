package com.stemdeck.remote.networking

import com.stemdeck.remote.chords.BeatGrid
import com.stemdeck.remote.models.Job
import com.stemdeck.remote.models.PairedServer
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Talks to one StemDeck desktop instance's REST API over the LAN. Scheme is
 * whatever the server actually advertises: plain http until "Make available
 * on your network" has generated a LAN certificate, https afterward. TLS
 * trust for the https case is handled by [PinnedTrustManager] — see that
 * type for why StemDeck's self-signed cert needs special handling at all.
 *
 * A fresh [OkHttpClient] per instance (not a shared singleton) mirrors the
 * iOS side's one-`URLSession`-per-`StemDeckClient` design: callers making
 * concurrent stem downloads create one `StemDeckClient` per download rather
 * than sharing one — see `StemDownloadQueue.runDownload`.
 */
class StemDeckClient(
    private val scheme: String,
    private val host: String,
    private val port: Int,
    pinnedFingerprint: String?,
    /**
     * A LAN request either resolves in well under a second or the server
     * genuinely isn't there — a long timeout made a no-network state (no
     * Wi-Fi, airplane mode) look hung rather than failing fast into the
     * offline-cache path `LibraryViewModel`/`PlayerViewModel` already have —
     * so 2s is the right default for an already-paired server. The one
     * exception is pairing's own first health check, where `PairingScreen`
     * passes a longer timeout so a slow first connection doesn't fail
     * spuriously.
     */
    timeoutSeconds: Long = 2,
    private val onFingerprint: ((String) -> Unit)? = null,
) {
    sealed class ClientError(message: String) : IOException(message) {
        object Unreachable : ClientError("StemDeck is unreachable")
        object CertificateRejected : ClientError("Certificate was rejected")
        /**
         * Server explicitly refused the request and explained why — e.g.
         * StemDeck's own "not available on the network" or "needs https"
         * gate (`app/main.py`), which returns a human-readable body.
         */
        data class ServerRefused(val detail: String) : ClientError(detail)
        data class InvalidResponse(val code: Int) : ClientError("Invalid response (HTTP $code)")
    }

    val baseUrl: String = "$scheme://$host:$port"

    companion object {
        /**
         * StemDeck's `/api/jobs` (and other numeric-bearing) responses go
         * through plain `json.dumps` server-side, which — unlike strict
         * JSON — happily emits bare `NaN`/`Infinity`/`-Infinity` tokens for
         * a non-finite float field (e.g. a corrupted source file producing
         * a `+inf` peak level). `Json.Default` rejects those as invalid
         * syntax and fails the *entire* decode, not just that one field —
         * `allowSpecialFloatingPointValues` accepts them into `Double.NaN`/
         * `POSITIVE_INFINITY`/`NEGATIVE_INFINITY` instead, which `Job`'s
         * own downstream code (e.g. `formattedDuration`'s `isFinite` check)
         * already handles gracefully. Without this, one bad job's stray
         * field silently took down the whole library sync, misreported to
         * the user as being offline.
         */
        private val lenientJson = Json { ignoreUnknownKeys = true; allowSpecialFloatingPointValues = true }
    }

    private val trustManager: PinnedTrustManager? =
        if (scheme == "https") PinnedTrustManager(pinnedFingerprint).also { it.onFingerprint = onFingerprint } else null

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(timeoutSeconds * 4, TimeUnit.SECONDS) // generous ceiling for a whole download call
        .retryOnConnectionFailure(false)
        .apply {
            trustManager?.let { tm ->
                sslSocketFactory(PinnedTrustManager.sslSocketFactory(tm), tm)
                hostnameVerifier(PinnedTrustManager.acceptAllHostnames)
            }
        }
        .build()

    constructor(server: PairedServer) : this(
        scheme = server.scheme,
        host = server.host,
        port = server.port,
        pinnedFingerprint = server.certFingerprint,
    )

    /** Confirms the server is reachable — used both to validate a freshly scanned/typed address during pairing and as a lightweight ping. */
    suspend fun checkHealth() {
        get("api/health")
    }

    suspend fun fetchLibrary(): List<Job> {
        val body = get("api/jobs")
        return try {
            lenientJson.decodeFromString(body.decodeToString())
        } catch (e: Exception) {
            throw ClientError.InvalidResponse(-1)
        }
    }

    /**
     * Downloads one stem's WAV file to [destination], replacing anything
     * already there. WAV (not the transcoded `.mp3` variant) so all stems
     * decode to the same PCM format for [com.stemdeck.remote.playback.StemMixerEngine]
     * to mix sample-for-sample without resampling drift between tracks.
     */
    suspend fun downloadStem(jobID: String, name: String, destination: File) = withContext(Dispatchers.IO) {
        trustManager?.resetRejectionFlag()
        val request = Request.Builder().url("$baseUrl/api/jobs/$jobID/stems/$name.wav").build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val text = response.body?.string().orEmpty()
                    throw ClientError.ServerRefused(message(text) ?: "StemDeck returned an error (HTTP ${response.code}) for $name.")
                }
                destination.parentFile?.mkdirs()
                val tempFile = File.createTempFile("stem-", ".tmp", destination.parentFile)
                response.body?.byteStream()?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (destination.exists()) destination.delete()
                tempFile.renameTo(destination)
            }
        } catch (e: ClientError) {
            throw e
        } catch (e: Exception) {
            if (trustManager?.didRejectCertificate == true) throw ClientError.CertificateRejected
            throw ClientError.Unreachable
        }
    }

    /**
     * Pre-computed [min, max] waveform peaks for every stem in a job — the
     * same overview data (`_PEAK_POINTS` = 1500 points/stem, values already
     * normalized to [-1, 1]) the desktop app's own waveform renders from.
     * Non-fatal to omit: `WaveformView` just draws nothing without it.
     */
    suspend fun fetchPeaks(jobID: String): ByteArray = get("api/jobs/$jobID/stems/peaks.json")

    /**
     * StemDeck's click-track beat grid — see [BeatGrid]. `null` (never
     * throws) on any failure: an old job without this stage, a transient
     * network hiccup, or a malformed body all mean the same thing to
     * `ChordAnalyzer` — "no beat grid, use fixed windows instead."
     */
    suspend fun fetchBeatGrid(jobID: String): BeatGrid? = try {
        val data = get("api/jobs/$jobID/stems/beats.json")
        lenientJson.decodeFromString<BeatGrid>(data.decodeToString())
    } catch (e: Exception) {
        null
    }

    private suspend fun get(path: String): ByteArray = withContext(Dispatchers.IO) {
        trustManager?.resetRejectionFlag()
        val request = Request.Builder().url("$baseUrl/$path").build()
        try {
            client.newCall(request).execute().use { response: Response ->
                val bytes = response.body?.bytes() ?: ByteArray(0)
                if (!response.isSuccessful) {
                    throw ClientError.ServerRefused(message(bytes.decodeToString()) ?: "StemDeck returned an error (HTTP ${response.code}).")
                }
                bytes
            }
        } catch (e: ClientError) {
            throw e
        } catch (e: SSLHandshakeException) {
            if (trustManager?.didRejectCertificate == true) throw ClientError.CertificateRejected
            throw ClientError.Unreachable
        } catch (e: Exception) {
            if (trustManager?.didRejectCertificate == true) throw ClientError.CertificateRejected
            throw ClientError.Unreachable
        }
    }

    /**
     * StemDeck's error responses are either a FastAPI `{"detail": "..."}`
     * JSON body or, for the network/secure-origin gates in `app/main.py`, a
     * plain-text explanation. Either way, it's the message worth showing.
     */
    private fun message(text: String): String? {
        if (text.isBlank()) return null
        try {
            val element = Json.parseToJsonElement(text)
            val detail = element.jsonObject["detail"]?.jsonPrimitive?.content
            if (detail != null) return detail
        } catch (_: Exception) {
            // Not JSON — fall through to plain text below.
        }
        return text.trim().ifEmpty { null }
    }
}
