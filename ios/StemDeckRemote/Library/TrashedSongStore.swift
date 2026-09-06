import Foundation

/// Songs the user has removed from the mobile library view (swipe-to-delete
/// in `LibraryView`) — mobile-only, this never calls StemDeck to delete
/// anything server-side. Stores a snapshot of each song's own metadata at
/// the moment it was trashed (not just its `job_id`) so the Trash section
/// can still show its title/BPM/key/duration even if StemDeck later
/// deletes the job for real and it drops out of `GET /api/jobs` entirely —
/// in that case there's nothing further to reconcile: the trashed record
/// just sits there as a normal (if now server-orphaned) local record until
/// the user restores or re-trashes over it.
///
/// Deliberately untouched by library syncs, same reasoning as
/// `LibraryFolderStore`: a freshly synced job that's already trashed stays
/// trashed (excluded from the visible library by `LibraryView`, skipped by
/// `StemDownloadQueue.syncLibrary`'s background downloader) until the user
/// explicitly restores it.
@MainActor
final class TrashedSongStore: ObservableObject {
    static let shared = TrashedSongStore()

    struct Entry: Codable, Identifiable {
        let job: Job
        let trashedAt: Date
        var id: String { job.id }
    }

    @Published private(set) var entries: [Entry] = []

    private static var url: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("trashed-songs.json")
    }

    private init() {
        load()
    }

    func isTrashed(_ jobID: String) -> Bool {
        entries.contains { $0.job.id == jobID }
    }

    /// Records `job` as trashed — callers are responsible for actually
    /// deleting its local files (see `StemFileStore.deleteAll`) and
    /// forgetting its download status (see `StemDownloadQueue.forget`).
    func trash(_ job: Job) {
        entries.removeAll { $0.job.id == job.id }
        entries.insert(Entry(job: job, trashedAt: Date()), at: 0)
        persist()
    }

    func restore(_ jobID: String) {
        entries.removeAll { $0.job.id == jobID }
        persist()
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(entries) else { return }
        try? data.write(to: Self.url)
    }

    private func load() {
        guard let data = try? Data(contentsOf: Self.url),
              let decoded = try? JSONDecoder().decode([Entry].self, from: data)
        else { return }
        entries = decoded
    }
}
