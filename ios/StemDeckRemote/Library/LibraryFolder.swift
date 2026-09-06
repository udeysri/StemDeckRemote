import Foundation

/// A user-created grouping in the song list (see `LibraryFolderStore`) —
/// purely a client-side organizational layer. StemDeck's own library has no
/// concept of folders, so this never round-trips to the server.
struct LibraryFolder: Codable, Identifiable, Equatable {
    let id: UUID
    var name: String

    init(id: UUID = UUID(), name: String) {
        self.id = id
        self.name = name
    }
}
