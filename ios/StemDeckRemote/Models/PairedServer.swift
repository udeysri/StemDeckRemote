import Foundation

/// A StemDeck desktop instance this device has paired with.
///
/// `scheme` matters because a StemDeck instance only serves https when its
/// "Make available on your network" setting has generated a LAN certificate
/// (desktop app Settings → Network); until then, the address it advertises
/// is plain http, and the desktop backend accepts that from other devices on
/// purpose (`_secure_origin_required()` in `app/main.py` only enforces https
/// in standalone server mode, not the desktop shell). `certFingerprint` is
/// nil for an http pairing — there's no certificate to pin. When present,
/// it isn't secret (it's broadcast in every TLS handshake with the server),
/// so this whole struct is stored in UserDefaults rather than the Keychain.
struct PairedServer: Codable, Equatable {
    let scheme: String
    let host: String
    let port: Int
    let certFingerprint: String?
    let pairedAt: Date

    var isSecure: Bool { scheme == "https" }

    var baseURL: URL {
        URL(string: "\(scheme)://\(host):\(port)")!
    }

    var displayAddress: String { "\(host):\(port)" }

    /// Placeholder passed when playing a bundled sample song (see
    /// `SampleSongCatalog`), which never makes a network request — stems and
    /// peaks are read straight from the app bundle, so none of this struct's
    /// fields are ever actually used.
    static let sample = PairedServer(scheme: "sample", host: "bundled", port: 0, certFingerprint: nil, pairedAt: .distantPast)
}
