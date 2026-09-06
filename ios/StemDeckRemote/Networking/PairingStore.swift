import Foundation
import Combine

/// Persists the paired StemDeck server across launches and publishes changes
/// so the root view can switch between pairing and library UI.
final class PairingStore: ObservableObject {
    static let shared = PairingStore()

    private static let defaultsKey = "com.stemdeck.remote.pairedServer"

    @Published private(set) var current: PairedServer?

    private init() {
        current = Self.load()
    }

    func pair(scheme: String, host: String, port: Int, certFingerprint: String?) {
        let server = PairedServer(scheme: scheme, host: host, port: port, certFingerprint: certFingerprint, pairedAt: Date())
        current = server
        save(server)
    }

    func forget() {
        current = nil
        UserDefaults.standard.removeObject(forKey: Self.defaultsKey)
    }

    private func save(_ server: PairedServer) {
        guard let data = try? JSONEncoder().encode(server) else { return }
        UserDefaults.standard.set(data, forKey: Self.defaultsKey)
    }

    private static func load() -> PairedServer? {
        guard let data = UserDefaults.standard.data(forKey: defaultsKey) else { return nil }
        return try? JSONDecoder().decode(PairedServer.self, from: data)
    }
}
