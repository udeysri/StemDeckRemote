import Foundation

/// Downloads every song's stems in the background, one song at a time, as
/// soon as it appears in the library — so by the time the user taps a song,
/// it's likely already there. Opening a song directly jumps the line via
/// `downloadNow`, which starts (or attaches to) that song's download right
/// away rather than waiting behind others still queued.
///
/// A single shared instance because it's the one place that should ever be
/// writing stem files to disk — `PlayerView` and the library list both read
/// its `statuses` instead of downloading independently, so a song opened
/// mid-background-download is observed rather than re-fetched.
@MainActor
final class StemDownloadQueue: ObservableObject {
    static let shared = StemDownloadQueue()

    enum Status: Equatable {
        case notQueued
        case queued
        case downloading(progress: Double)
        case downloaded
        case failed(String)
    }

    @Published private(set) var statuses: [String: Status] = [:]

    private let store = StemFileStore()
    private var server: PairedServer?
    private var jobsByID: [String: Job] = [:]
    private var pendingJobIDs: [String] = []
    private var activeTasks: [String: Task<Void, Error>] = [:]
    private var isDraining = false

    private init() {}

    func configure(server: PairedServer) {
        self.server = server
    }

    func status(for jobID: String) -> Status {
        statuses[jobID] ?? .notQueued
    }

    /// Drops any tracked status for `jobID` — used when the user trashes a
    /// song (`TrashedSongStore`), so that if it's later restored it's
    /// re-evaluated from scratch (its files were deleted along with the
    /// status) rather than still reporting stale `.downloaded`.
    func forget(jobID: String) {
        statuses.removeValue(forKey: jobID)
    }

    /// Call after every library refresh. Songs already tracked (downloaded,
    /// queued, mid-download, or previously failed) are left alone — this
    /// only picks up ones that are new to the queue. Trashed songs are
    /// skipped entirely: the user explicitly removed them from the mobile
    /// library, so StemDeck still having the job shouldn't silently bring
    /// its stems back — only an explicit restore (or opening it directly)
    /// downloads it again.
    func syncLibrary(_ jobs: [Job]) {
        for job in jobs {
            jobsByID[job.id] = job
            guard statuses[job.id] == nil else { continue }
            guard !TrashedSongStore.shared.isTrashed(job.id) else { continue }
            if isFullyDownloaded(job) {
                statuses[job.id] = .downloaded
            } else {
                statuses[job.id] = .queued
                pendingJobIDs.append(job.id)
            }
        }
        drainQueueIfNeeded()
    }

    /// The user opened this song. Downloads it now, ahead of anything still
    /// waiting in the background queue, and waits for it to finish.
    func downloadNow(_ job: Job) async throws {
        jobsByID[job.id] = job
        pendingJobIDs.removeAll { $0 == job.id }
        try await performDownload(job)
    }

    func isFullyDownloaded(_ job: Job) -> Bool {
        let names = job.stemNames
        guard !names.isEmpty else { return false }
        return names.allSatisfy { store.exists(jobID: job.id, stem: $0) }
    }

    private func drainQueueIfNeeded() {
        guard !isDraining, !pendingJobIDs.isEmpty else { return }
        let nextID = pendingJobIDs.removeFirst()
        guard let job = jobsByID[nextID] else {
            drainQueueIfNeeded()
            return
        }
        isDraining = true
        Task {
            try? await performDownload(job)
            isDraining = false
            drainQueueIfNeeded()
        }
    }

    /// One in-flight `Task` per job — the background queue and a direct
    /// `downloadNow` call both funnel through here, so if the queue already
    /// reached this song, opening it just attaches to that same download
    /// instead of starting a duplicate one.
    private func performDownload(_ job: Job) async throws {
        if isFullyDownloaded(job) {
            statuses[job.id] = .downloaded
            return
        }
        if let existing = activeTasks[job.id] {
            try await existing.value
            return
        }
        guard let server else { return }
        let task = Task { try await self.runDownload(job: job, server: server) }
        activeTasks[job.id] = task
        defer { activeTasks[job.id] = nil }
        try await task.value
    }

    private func runDownload(job: Job, server: PairedServer) async throws {
        let jobID = job.id
        let store = store
        let missing = job.stemNames.filter { !store.exists(jobID: jobID, stem: $0) }
        guard !missing.isEmpty else {
            statuses[jobID] = .downloaded
            return
        }

        statuses[jobID] = .downloading(progress: 0)
        var completed = 0
        do {
            try await withThrowingTaskGroup(of: Void.self) { group in
                for name in missing {
                    group.addTask {
                        let client = StemDeckClient(server: server)
                        try await client.downloadStem(jobID: jobID, name: name, to: store.url(jobID: jobID, stem: name))
                    }
                }
                for try await _ in group {
                    completed += 1
                    self.statuses[jobID] = .downloading(progress: Double(completed) / Double(missing.count))
                }
            }
            if !store.peaksExist(jobID: jobID) {
                let client = StemDeckClient(server: server)
                if let data = try? await client.fetchPeaks(jobID: jobID) {
                    try? data.write(to: store.peaksURL(jobID: jobID))
                }
            }
            statuses[jobID] = .downloaded
        } catch {
            statuses[jobID] = .failed(Self.describe(error))
            throw error
        }
    }

    static func describe(_ error: Error) -> String {
        switch error {
        case StemDeckClient.ClientError.serverRefused(let detail):
            return detail
        case StemDeckClient.ClientError.certificateRejected:
            return "StemDeck's certificate no longer matches what this app trusted. Disconnect and pair again."
        default:
            return "Couldn't download stems. Check that StemDeck is still running and reachable on your Wi-Fi."
        }
    }
}
