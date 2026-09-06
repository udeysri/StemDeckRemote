import Foundation
import Security
import CryptoKit

/// Handles TLS trust for StemDeck's self-signed LAN certificate.
///
/// StemDeck terminates HTTPS on the LAN with a certificate it generates
/// itself (see the desktop app's `app/core/tls_listener.py`) — there's no CA
/// a browser or URLSession would recognize. A browser shows a one-time
/// "not private" warning the user clicks through; URLSession has no such UI,
/// so this delegate does the same thing natively: trust-on-first-use.
///
/// With `pinnedFingerprint == nil` (during pairing) any presented cert is
/// accepted and reported via `onFingerprint`, so the pairing flow can show it
/// to the user for confirmation. Once pinned, only a cert whose fingerprint
/// matches is trusted — a changed cert (rotation, or a different machine
/// answering on that address) is rejected rather than silently re-trusted.
final class PinnedTrustDelegate: NSObject, URLSessionDelegate {
    var pinnedFingerprint: String?
    var onFingerprint: ((String) -> Void)?

    /// Set when a request was refused because the presented cert didn't match
    /// `pinnedFingerprint` — lets callers distinguish "server unreachable"
    /// from "server's certificate changed, re-pair" after a failed request.
    private(set) var didRejectCertificate = false

    func resetRejectionFlag() {
        didRejectCertificate = false
    }

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let serverTrust = challenge.protectionSpace.serverTrust,
              let fingerprint = Self.fingerprint(of: serverTrust)
        else {
            completionHandler(.performDefaultHandling, nil)
            return
        }

        onFingerprint?(fingerprint)

        if pinnedFingerprint == nil || pinnedFingerprint == fingerprint {
            completionHandler(.useCredential, URLCredential(trust: serverTrust))
        } else {
            didRejectCertificate = true
            completionHandler(.cancelAuthenticationChallenge, nil)
        }
    }

    /// SHA-256 of the leaf certificate's DER encoding, formatted as
    /// colon-separated uppercase hex (e.g. "AB:12:CD:...").
    static func fingerprint(of trust: SecTrust) -> String? {
        guard let chain = SecTrustCopyCertificateChain(trust) as? [SecCertificate],
              let leaf = chain.first
        else { return nil }
        let data = SecCertificateCopyData(leaf) as Data
        let digest = SHA256.hash(data: data)
        return digest.map { String(format: "%02X", $0) }.joined(separator: ":")
    }
}
