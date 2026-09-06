package com.stemdeck.remote.networking

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Handles TLS trust for StemDeck's self-signed LAN certificate.
 *
 * StemDeck terminates HTTPS on the LAN with a certificate it generates
 * itself (see the desktop app's `app/core/tls_listener.py`) — there's no CA
 * a browser or an HTTP client would recognize. A browser shows a one-time
 * "not private" warning the user clicks through; this trust manager does
 * the same thing programmatically: trust-on-first-use.
 *
 * With [pinnedFingerprint] `== null` (during pairing) any presented cert is
 * accepted and reported via [onFingerprint], so the pairing flow can show it
 * to the user for confirmation. Once pinned, only a cert whose fingerprint
 * matches is trusted — a changed cert (rotation, or a different machine
 * answering on that address) is rejected rather than silently re-trusted.
 *
 * There's also no hostname to validate against: StemDeck's cert is issued
 * for whatever LAN address it happens to be serving on, not a name any CA
 * would sign for, so the paired [okhttp3.OkHttpClient] additionally installs
 * a hostname verifier that accepts anything — the fingerprint check above is
 * the actual security boundary, exactly as it is on the iOS side (which
 * gets the same effect for free by handing `URLCredential(trust:)` straight
 * to the challenge instead of falling through to default hostname checks).
 */
class PinnedTrustManager(@Volatile var pinnedFingerprint: String?) : X509TrustManager {
    var onFingerprint: ((String) -> Unit)? = null

    /**
     * Set when a request was refused because the presented cert didn't match
     * [pinnedFingerprint] — lets callers distinguish "server unreachable"
     * from "server's certificate changed, re-pair" after a failed request.
     */
    @Volatile var didRejectCertificate = false
        private set

    fun resetRejectionFlag() {
        didRejectCertificate = false
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("No certificate presented")
        val fingerprint = fingerprint(leaf)
        onFingerprint?.invoke(fingerprint)

        val pinned = pinnedFingerprint
        if (pinned != null && pinned != fingerprint) {
            didRejectCertificate = true
            throw CertificateException("Certificate fingerprint $fingerprint does not match pinned $pinned")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    companion object {
        /** SHA-256 of the leaf certificate's DER encoding, colon-separated uppercase hex (e.g. "AB:12:CD:..."). */
        fun fingerprint(certificate: X509Certificate): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            return digest.joinToString(":") { "%02X".format(it) }
        }

        fun sslSocketFactory(trustManager: X509TrustManager): SSLSocketFactory {
            val context = SSLContext.getInstance("TLS")
            context.init(null, arrayOf(trustManager), SecureRandom())
            return context.socketFactory
        }

        /** No CA-signed name exists for a self-signed LAN cert — see the class doc comment. */
        val acceptAllHostnames = HostnameVerifier { _, _ -> true }
    }
}
