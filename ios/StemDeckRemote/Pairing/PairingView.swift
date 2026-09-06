import SwiftUI
import UIKit

/// Candidate https server awaiting trust confirmation, plus the fingerprint
/// its certificate reported during the health-check probe. Plain http never
/// reaches this state — there's no certificate to confirm.
private struct PendingTrust: Identifiable {
    let id = UUID()
    let host: String
    let port: Int
    let fingerprint: String
}

/// The app's first-run / signed-out home screen: introduces StemDeck Remote
/// and offers the three ways in (QR pairing, manual address, sample songs)
/// instead of dropping straight into a bare "scan a QR code" form.
struct PairingView: View {
    @ObservedObject private var store = PairingStore.shared
    @ObservedObject private var localNetwork = LocalNetworkAuthorization.shared

    @State private var manualHost = ""
    @State private var manualPort = "8443"
    @State private var manualUseHTTPS = true
    @State private var isScanning = false
    @State private var isChecking = false
    @State private var isShowingManualConnect = false
    @State private var isShowingSampleSongs = false
    @State private var pendingTrust: PendingTrust?
    @State private var errorMessage: String?

    private let stemDeckURL = URL(string: "https://github.com/stemdeckapp/stemdeck")!

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 32) {
                    hero
                    aboutSection
                    actions
                }
                .padding(24)
                .frame(maxWidth: 480)
                .frame(maxWidth: .infinity)
            }
            .toolbar(.hidden, for: .navigationBar)
        }
        .fullScreenCover(isPresented: $isScanning) {
            ZStack {
                QRScannerView { code in
                    isScanning = false
                    handleScannedCode(code)
                }
                .ignoresSafeArea()

                if localNetwork.status == .denied {
                    localNetworkWarningBanner
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                        .padding(.top, 8)
                }

                Button {
                    isScanning = false
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title)
                        .foregroundStyle(.white, .black.opacity(0.5))
                        .padding()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
            }
        }
        .sheet(isPresented: $isShowingManualConnect) {
            ManualConnectSheet(
                host: $manualHost,
                port: $manualPort,
                useHTTPS: $manualUseHTTPS,
                isChecking: isChecking,
                onConnect: {
                    let port = Int(manualPort) ?? (manualUseHTTPS ? 8443 : 8000)
                    connect(scheme: manualUseHTTPS ? "https" : "http", host: manualHost, port: port)
                }
            )
        }
        .sheet(isPresented: $isShowingSampleSongs) {
            SampleSongsView()
        }
        .sheet(item: $pendingTrust) { pending in
            TrustConfirmationSheet(
                host: pending.host,
                port: pending.port,
                fingerprint: pending.fingerprint,
                onTrust: {
                    store.pair(scheme: "https", host: pending.host, port: pending.port, certFingerprint: pending.fingerprint)
                    pendingTrust = nil
                },
                onCancel: { pendingTrust = nil }
            )
        }
        .alert("Couldn't Connect", isPresented: .constant(errorMessage != nil), presenting: errorMessage) { _ in
            Button("OK") { errorMessage = nil }
        } message: { message in
            Text(message)
        }
    }

    private var hero: some View {
        VStack(spacing: 16) {
            Image("StemDeck-Remote-Transparent")
                .resizable()
                .scaledToFit()
                .frame(width: 120, height: 120)
            Text("StemDeck Remote")
                .font(.largeTitle.bold())
            Text("Your StemDeck library, remote in your pocket.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(.top, 24)
    }

    private var aboutSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("StemDeck Remote is the companion app for **StemDeck**, the desktop app that separates songs into vocals, drums, bass, guitar, piano, and more.")
            Text("Pair with StemDeck over your local network to sync your library, download stems to your phone, and mix them live on a per-stem console — no cables, no computer required once you've paired.")
            Link(destination: stemDeckURL) {
                Label("stemdeckapp/stemdeck on GitHub", systemImage: "arrow.up.forward.square")
            }
            .font(.footnote)
        }
        .font(.subheadline)
        .foregroundStyle(.secondary)
        .fixedSize(horizontal: false, vertical: true)
    }

    private var actions: some View {
        VStack(spacing: 12) {
            Button {
                isScanning = true
            } label: {
                Label("Scan QR Code", systemImage: "qrcode.viewfinder")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)

            Button {
                isShowingManualConnect = true
            } label: {
                Label("Connect with URL", systemImage: "link")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .controlSize(.large)

            Button {
                isShowingSampleSongs = true
            } label: {
                Label("Check Sample Songs", systemImage: "play.square.stack")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
            .tint(.secondary)

            Text("Open StemDeck on your computer, go to Settings → Network, and scan the QR code shown there — or enter its address manually.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.top, 4)

            if isChecking {
                HStack(spacing: 8) {
                    ProgressView()
                    Text("Connecting…")
                }
                .font(.footnote)
                .foregroundStyle(.secondary)
                .padding(.top, 4)
            }
        }
    }

    private var localNetworkWarningBanner: some View {
        VStack(spacing: 8) {
            Label("Please enable Local Network access to sync with your local StemDeck.", systemImage: "wifi.exclamationmark")
                .font(.footnote)
                .foregroundStyle(.white)
            Button("Open Settings") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            .font(.footnote.bold())
        }
        .multilineTextAlignment(.center)
        .padding(12)
        .background(Color.black.opacity(0.75), in: RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal, 24)
    }

    private func handleScannedCode(_ code: String) {
        guard let url = URL(string: code),
              let host = url.host,
              let scheme = url.scheme?.lowercased(),
              scheme == "http" || scheme == "https"
        else {
            errorMessage = "That QR code isn't a StemDeck pairing code."
            return
        }
        let port = url.port ?? (scheme == "https" ? 8443 : 8000)
        connect(scheme: scheme, host: host, port: port)
    }

    private func connect(scheme: String, host: String, port: Int) {
        let trimmedHost = host.trimmingCharacters(in: .whitespaces)
        guard !trimmedHost.isEmpty, URL(string: "\(scheme)://\(trimmedHost):\(port)") != nil else {
            errorMessage = "Enter a valid host and port."
            return
        }

        isChecking = true
        Task {
            var reportedFingerprint: String?
            // Longer timeout than StemDeckClient's 2s default: this is the
            // first-ever connection attempt, which can be exactly when iOS
            // shows its one-time Local Network permission dialog — the
            // request pauses until the user responds, so a short timeout
            // here made the very first pairing attempt fail even when the
            // user went on to tap "Allow" (see LocalNetworkAuthorization).
            let client = StemDeckClient(scheme: scheme, host: trimmedHost, port: port, pinnedFingerprint: nil, timeout: 15) { fingerprint in
                reportedFingerprint = fingerprint
            }
            do {
                try await client.checkHealth()
                isChecking = false
                isShowingManualConnect = false
                if scheme == "https" {
                    if let fingerprint = reportedFingerprint {
                        pendingTrust = PendingTrust(host: trimmedHost, port: port, fingerprint: fingerprint)
                    } else {
                        errorMessage = "Connected, but StemDeck didn't present a certificate to verify."
                    }
                } else {
                    store.pair(scheme: scheme, host: trimmedHost, port: port, certFingerprint: nil)
                }
            } catch StemDeckClient.ClientError.serverRefused(let detail) {
                isChecking = false
                isShowingManualConnect = false
                errorMessage = detail
            } catch {
                isChecking = false
                isShowingManualConnect = false
                errorMessage = "Couldn't reach StemDeck at \(trimmedHost):\(port) over \(scheme). Make sure it's running, \"Make available on your network\" is on, and your iPhone is on the same Wi-Fi."
            }
        }
    }
}

/// The manual host/port entry, moved off the home screen into its own sheet
/// so the landing page can lead with "Scan QR Code" instead of a form.
private struct ManualConnectSheet: View {
    @Binding var host: String
    @Binding var port: String
    @Binding var useHTTPS: Bool
    var isChecking: Bool
    var onConnect: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Host or IP (e.g. 192.168.1.20)", text: $host)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                    TextField("Port", text: $port)
                        .keyboardType(.numberPad)
                    Toggle("Use HTTPS", isOn: $useHTTPS)
                } footer: {
                    Text("Match the address shown in StemDeck's Settings → Network. If \"Make available on your network\" hasn't generated a certificate yet, StemDeck serves plain http — turn HTTPS off here to match.")
                }

                if isChecking {
                    Section {
                        HStack {
                            ProgressView()
                            Text("Connecting…")
                        }
                    }
                }
            }
            .navigationTitle("Connect with URL")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Connect", action: onConnect)
                        .disabled(host.trimmingCharacters(in: .whitespaces).isEmpty || isChecking)
                }
            }
        }
        .presentationDetents([.medium])
    }
}
