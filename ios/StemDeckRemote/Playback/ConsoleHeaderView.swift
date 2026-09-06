import SwiftUI

/// Song title + the analysis readouts StemDeck already computed (BPM, key)
/// styled as the brief's LCD-style panel readouts. Pan/meter/loop chips from
/// the mockup aren't backed by real data from the server, so they're left
/// out rather than faked.
struct ConsoleHeaderView: View {
    let job: Job
    var currentChord: String? = nil
    var isAnalyzingChords: Bool = false
    var onBack: (() -> Void)? = nil
    var onToggleViewMode: (() -> Void)? = nil

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            if let onBack {
                Button(action: onBack) {
                    Image(systemName: "chevron.down")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(ConsoleTheme.onSurfaceVariant)
                        .frame(width: 30, height: 30)
                        .background(ConsoleTheme.surfaceContainerHigh)
                        .clipShape(Circle())
                }
                .padding(.top, 2)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text("STEM CONSOLE")
                    .font(ConsoleTheme.headlineFont(10))
                    .foregroundStyle(ConsoleTheme.accent)
                Text(job.title ?? "Untitled")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(ConsoleTheme.onSurface)
                    .lineLimit(1)
            }

            Spacer()

            HStack(spacing: 8) {
                if let bpm = job.bpm {
                    readout(value: "\(bpm)", label: "BPM")
                }
                if let key = job.key {
                    readout(value: key + (job.scale?.lowercased().contains("minor") == true ? "m" : ""), label: "KEY")
                }
                if let currentChord {
                    readout(value: currentChord, label: "CHORD", accent: true)
                } else if isAnalyzingChords {
                    readout(value: "…", label: "CHORD")
                }
            }

            if let onToggleViewMode {
                Button(action: onToggleViewMode) {
                    Image(systemName: "waveform")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(ConsoleTheme.onSurfaceVariant)
                        .frame(width: 30, height: 30)
                        .background(ConsoleTheme.surfaceContainerHigh)
                        .clipShape(Circle())
                }
                .padding(.top, 2)
            }
        }
    }

    private func readout(value: String, label: String, accent: Bool = false) -> some View {
        VStack(spacing: 1) {
            Text(value)
                .font(ConsoleTheme.monoFont(15, weight: .semibold))
                .foregroundStyle(accent ? ConsoleTheme.accent : ConsoleTheme.onSurface)
            Text(label)
                .font(ConsoleTheme.headlineFont(8))
                .foregroundStyle(ConsoleTheme.outline)
        }
        .frame(minWidth: 44)
        .padding(.vertical, 4)
        .padding(.horizontal, 8)
        .recessedWell(cornerRadius: 6)
    }
}
