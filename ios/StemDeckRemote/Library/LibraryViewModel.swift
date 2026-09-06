import Foundation

@MainActor
final class LibraryViewModel: ObservableObject {
    enum LoadState: Equatable {
        case idle
        case loading
        case loaded
        case unreachable
        case certificateChanged
        case refused(String)
    }

    @Published private(set) var songs: [Job] = []
    @Published private(set) var state: LoadState = .idle
    /// True when `songs` is the last cached listing rather than a live one
    /// — StemDeck couldn't be reached, but there's a known library to show
    /// (and already-downloaded songs in it still open and play fine).
    @Published private(set) var isOffline = false
    @Published var searchText: String = ""
    @Published var sortOption: SongSortOption = .dateAddedNewest

    /// What the list actually shows — `songs` filtered by `searchText` (a
    /// plain case-insensitive title substring match) and ordered by
    /// `sortOption`. Both are pure display transforms; the fetched `songs`
    /// underneath is untouched.
    var displayedSongs: [Job] {
        let matching = searchText.trimmingCharacters(in: .whitespaces).isEmpty
            ? songs
            : songs.filter { ($0.title ?? "").localizedCaseInsensitiveContains(searchText) }
        return sortOption.sort(matching)
    }

    private var client: StemDeckClient?
    private var server: PairedServer?

    /// Cache-first: shows the last known library immediately (as "offline"
    /// until `refresh()` proves otherwise) instead of a blank/loading
    /// screen while the live fetch is still in flight.
    func configure(with server: PairedServer) {
        StemDownloadQueue.shared.configure(server: server)
        guard server != self.server else { return }
        self.server = server
        self.client = StemDeckClient(server: server)
        if let cached = LibraryCache.load(), !cached.isEmpty {
            songs = cached
            isOffline = true
            state = .loaded
        } else {
            songs = []
            state = .idle
        }
    }

    func refresh() async {
        guard let client else { return }
        state = .loading
        do {
            let fetched = try await client.fetchLibrary()
                .filter(\.isAvailable)
                .sorted { ($0.createdAt ?? 0) > ($1.createdAt ?? 0) }
            songs = fetched
            isOffline = false
            state = .loaded
            LibraryCache.save(fetched)
            // As soon as songs are known, queue their stems for background
            // download (Netflix/Prime-style "downloading" / "queued" rows —
            // see StemDownloadQueue) instead of waiting for the user to open
            // each one.
            StemDownloadQueue.shared.syncLibrary(songs)
        } catch StemDeckClient.ClientError.certificateRejected {
            // Not a transient connectivity blip — needs an explicit re-pair,
            // so surface it even if a cache exists rather than masking it.
            state = .certificateChanged
        } catch {
            if let cached = LibraryCache.load(), !cached.isEmpty {
                songs = cached
                isOffline = true
                state = .loaded
                StemDownloadQueue.shared.syncLibrary(songs)
            } else if case StemDeckClient.ClientError.serverRefused(let detail) = error {
                state = .refused(detail)
            } else {
                state = .unreachable
            }
        }
    }
}
