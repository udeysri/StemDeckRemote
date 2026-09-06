import SwiftUI

/// The native equivalent of the browser's one-time "your connection is not
/// private" click-through: shown once per StemDeck server so the user can
/// confirm they recognize the machine before its certificate gets pinned.
struct TrustConfirmationSheet: View {
    let host: String
    let port: Int
    let fingerprint: String
    let onTrust: () -> Void
    let onCancel: () -> Void

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 20) {
                Label("Unverified Certificate", systemImage: "exclamationmark.shield")
                    .font(.headline)
                    .foregroundStyle(.orange)

                Text("StemDeck at \(host):\(port) is using a certificate it generated itself, so this app can't verify it against a trusted authority — the same reason a browser would show a warning here.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                Text("Only continue if this is your own computer, on your own network.")
                    .font(.subheadline)

                VStack(alignment: .leading, spacing: 6) {
                    Text("Certificate fingerprint")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(fingerprint)
                        .font(.system(.footnote, design: .monospaced))
                        .textSelection(.enabled)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 10))

                Spacer()

                Button("Trust & Connect", action: onTrust)
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity)

                Button("Cancel", role: .cancel, action: onCancel)
                    .frame(maxWidth: .infinity)
            }
            .padding()
            .navigationTitle("Connect to StemDeck")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
