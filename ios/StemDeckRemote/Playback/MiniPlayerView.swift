import SwiftUI

/// The YouTube-Music-style compact bar shown above the app's content
/// whenever a song is loaded but the full stem console has been minimized:
/// thumbnail, title, play/pause, and a thin progress line along the top
/// edge. Tapping anywhere but the play/pause button re-expands the console.
struct MiniPlayerView: View {
    let job: Job
    @ObservedObject var viewModel: PlayerViewModel
    let onExpand: () -> Void
    let onTogglePlay: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            progressLine

            HStack(spacing: 12) {
                thumbnail
                VStack(alignment: .leading, spacing: 1) {
                    Text(job.title ?? "Untitled")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    if let subtitle = job.subtitle {
                        Text(subtitle)
                            .font(.system(size: 11))
                            .foregroundStyle(.white.opacity(0.6))
                            .lineLimit(1)
                    }
                }
                Spacer()
                Button(action: onTogglePlay) {
                    Image(systemName: viewModel.isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
        }
        .background(ConsoleTheme.surfaceContainerHigh)
        .contentShape(Rectangle())
        .onTapGesture(perform: onExpand)
    }

    private var progressLine: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Rectangle().fill(Color.white.opacity(0.15))
                Rectangle()
                    .fill(Color.white)
                    .frame(width: geo.size.width * CGFloat(viewModel.progress))
            }
        }
        .frame(height: 2)
    }

    @ViewBuilder
    private var thumbnail: some View {
        let placeholder = RoundedRectangle(cornerRadius: 6)
            .fill(ConsoleTheme.surfaceContainer)
            .overlay(Image(systemName: "music.note").foregroundStyle(.white.opacity(0.6)))

        Group {
            if let thumb = job.thumbnail, let url = URL(string: thumb) {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().aspectRatio(contentMode: .fill)
                    } else {
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: 40, height: 40)
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }
}
