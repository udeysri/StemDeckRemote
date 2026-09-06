import Foundation

/// Client-side song-folder organization: an ordered list of folders (order
/// is what "drag to reorder" reorders) and which job ID belongs to which
/// folder. A job with no entry in `assignments` is "Unassigned" — there's
/// no real folder to manage for that; it's just the default shown for
/// anything not explicitly filed away.
///
/// Deliberately untouched by library syncs (`LibraryViewModel.refresh()`):
/// StemDeck's API has no folder concept, so a freshly synced job is either
/// new (no assignment yet — shows up Unassigned automatically) or already
/// known (its assignment, if any, is exactly what the user set it to last
/// time) — nothing here needs to react to a refresh at all.
@MainActor
final class LibraryFolderStore: ObservableObject {
    static let shared = LibraryFolderStore()

    @Published private(set) var folders: [LibraryFolder] = []
    @Published private(set) var assignments: [String: UUID] = [:]

    private struct Snapshot: Codable {
        var folders: [LibraryFolder]
        var assignments: [String: UUID]
    }

    private static var url: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("library-folders.json")
    }

    private init() {
        load()
    }

    func folderID(forJobID jobID: String) -> UUID? {
        assignments[jobID]
    }

    func createFolder(name: String) {
        folders.append(LibraryFolder(name: name))
        persist()
    }

    func deleteFolders(at offsets: IndexSet) {
        let removedIDs = Set(offsets.map { folders[$0].id })
        folders.remove(atOffsets: offsets)
        // Songs filed under a deleted folder fall back to Unassigned rather
        // than pointing at a folder ID that no longer exists.
        assignments = assignments.filter { !removedIDs.contains($0.value) }
        persist()
    }

    func moveFolders(fromOffsets: IndexSet, toOffset: Int) {
        folders.move(fromOffsets: fromOffsets, toOffset: toOffset)
        persist()
    }

    /// `folderID` nil moves back to Unassigned.
    func assign(jobIDs: some Sequence<String>, toFolder folderID: UUID?) {
        for jobID in jobIDs {
            if let folderID {
                assignments[jobID] = folderID
            } else {
                assignments.removeValue(forKey: jobID)
            }
        }
        persist()
    }

    private func persist() {
        let snapshot = Snapshot(folders: folders, assignments: assignments)
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        try? data.write(to: Self.url)
    }

    private func load() {
        guard let data = try? Data(contentsOf: Self.url),
              let snapshot = try? JSONDecoder().decode(Snapshot.self, from: data)
        else { return }
        folders = snapshot.folders
        assignments = snapshot.assignments
    }
}
