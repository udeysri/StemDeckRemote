import Foundation
import Network

/// Detects — and pre-warms — iOS's "Local Network" permission (the dialog
/// gated on `NSLocalNetworkUsageDescription`). Apple has no direct request
/// API for it, and every known way to provoke the prompt early is
/// undocumented OS behavior that Apple has changed across iOS versions
/// without notice — this is a best-effort nudge, not a guarantee.
///
/// The mechanism: open a UDP "connection" to a private-network address.
/// Nothing needs to actually be listening there — the permission gate
/// triggers on the mere attempt to reach an address in RFC1918 space, which
/// is the same "connect to a device on your LAN" case pairing itself
/// exercises. (An earlier version of this used a throwaway Bonjour
/// advertise+browse instead; that stopped reliably triggering the prompt at
/// all on at least one recent iOS version, which is exactly the kind of
/// silent breakage to expect from an unofficial trick like this.)
///
/// Call `requestIfNeeded()` once at launch so the prompt — and the user's
/// response — happens well before the first real StemDeck connection
/// attempt, *if* the OS cooperates. Either way, `StemDeckClient`'s pairing
/// health-check now uses a long enough timeout (see `PairingView.connect`)
/// to survive the dialog showing up late instead of failing the first
/// pairing attempt — that fix does not depend on this file working at all.
@MainActor
final class LocalNetworkAuthorization: ObservableObject {
    enum Status {
        case unknown
        case granted
        case denied
    }

    static let shared = LocalNetworkAuthorization()

    @Published private(set) var status: Status = .unknown

    private var connection: NWConnection?
    private var didRequest = false

    private init() {}

    func requestIfNeeded() {
        guard !didRequest else { return }
        didRequest = true

        // 192.168.1.1 is a private-range address (the most common home
        // router IP, though it doesn't matter whether anything's actually
        // there) on an unlikely port — UDP so there's no handshake to hang
        // waiting on a reply that may never come.
        let connection = NWConnection(host: "192.168.1.1", port: 65330, using: .udp)
        self.connection = connection

        // Runs on the `.main` queue passed to start() below, but the
        // closure type isn't actor-isolated as far as the compiler's
        // concerned, so hop back to the MainActor explicitly before
        // touching `self`.
        connection.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                // Made it out of .preparing — the permission gate (if any)
                // has resolved in our favor.
                Task { @MainActor in self?.finish(granted: true) }
            case .failed(.dns(-65570)):
                // kDNSServiceErr_PolicyDenied: the OS's specific signal
                // that Local Network access was denied, as opposed to some
                // other failure that says nothing about the permission.
                Task { @MainActor in self?.finish(granted: false) }
            default:
                break
            }
        }
        connection.start(queue: .main)

        // Generous timeout: on a real device, the system dialog appearing
        // *and* the user responding to it can take much longer than you'd
        // expect for a process's first-ever local-network access. If we
        // still have no definitive answer by then, just clean up — leave
        // `status` as `.unknown` rather than guessing "denied", since a
        // false denied warning later is worse than no warning at all.
        DispatchQueue.main.asyncAfter(deadline: .now() + 20) { [weak self] in
            Task { @MainActor in
                guard let self, self.status == .unknown else { return }
                self.connection?.cancel()
                self.connection = nil
            }
        }
    }

    private func finish(granted: Bool) {
        status = granted ? .granted : .denied
        connection?.cancel()
        connection = nil
    }
}
