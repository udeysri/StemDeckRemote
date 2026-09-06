import Foundation

/// The last successfully fetched library listing, persisted so the song
/// list is still usable when StemDeck isn't reachable — the whole point of
/// downloading stems locally is defeated if you also need Wi-Fi just to
/// find and open the song you already have. Playback itself (`PlayerView`)
/// was already fully offline-safe; this closes the one remaining gap,
/// which was `LibraryView` refusing to show anything without a live fetch.
enum LibraryCache {
    private static var url: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("library-cache.json")
    }

    static func save(_ jobs: [Job]) {
        guard let data = try? JSONEncoder().encode(jobs) else { return }
        try? data.write(to: url)
    }

    static func load() -> [Job]? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode([Job].self, from: data)
    }
}
