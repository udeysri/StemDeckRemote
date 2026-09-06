import SwiftUI

/// "Check Sample Songs" destination from the home screen: lets people
/// explore the stem console using 3 bundled, Creative Commons-licensed
/// songs without pairing to a live StemDeck instance — handy for a quick
/// look, or for App Review. See `SampleSongCatalog` for the bundled data
/// and the attribution each track requires.
struct SampleSongsView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List(SampleSongCatalog.all) { entry in
                Button {
                    play(entry)
                } label: {
                    SampleSongRow(entry: entry)
                }
                .buttonStyle(.plain)
            }
            .navigationTitle("Sample Songs")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .safeAreaInset(edge: .bottom) {
                Text("Licensed under Creative Commons (CC BY) via the Free Music Archive.")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity)
                    .background(.bar)
            }
        }
    }

    private func play(_ entry: SampleSongCatalog.Entry) {
        PlaybackCoordinator.shared.play(
            job: entry.job,
            server: .sample,
            localStemURLs: SampleSongCatalog.stemURLs(forJobID: entry.job.id),
            localPeaksURL: SampleSongCatalog.peaksURL(forJobID: entry.job.id)
        )
        dismiss()
    }
}

private struct SampleSongRow: View {
    let entry: SampleSongCatalog.Entry

    var body: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 6)
                .fill(Color(.secondarySystemBackground))
                .overlay(Image(systemName: "music.note").foregroundStyle(.secondary))
                .frame(width: 44, height: 44)

            VStack(alignment: .leading, spacing: 2) {
                Text(entry.job.title ?? "Untitled")
                    .font(.body)
                if let subtitle = entry.job.subtitle {
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                // CC BY's attribution requirement: title (above), artist,
                // source, and license, all in one place.
                Text("\(entry.artist) • \(entry.source) • \(entry.license)")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
            Spacer()
            if let duration = entry.job.formattedDuration {
                Text(duration)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
            }
        }
        .padding(.vertical, 4)
    }
}
