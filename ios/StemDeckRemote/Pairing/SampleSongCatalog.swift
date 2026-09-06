import Foundation

/// The 3 songs bundled with the app (`StemDeckRemote/SampleSongs/`) so
/// people — App Review included — can explore the stem console without
/// pairing to a live StemDeck instance. Stems were separated from the
/// original tracks by StemDeck itself, then re-encoded to AAC (64kbps) to
/// keep the app bundle small; the original WAV stems live in
/// `sampleMusic-source/` at the repo root (gitignored, outside the Xcode
/// target) in case they ever need reprocessing at a different quality:
///
///   afconvert -f m4af -d aac -b 64000 -q 127 -s 2 <stem>.wav <stem>.m4a
///
/// Every track is Creative Commons–licensed (CC BY) via the Free Music
/// Archive, which requires attribution — `Entry.artist`/`source`/`license`
/// carry exactly what needs crediting, surfaced per-track in
/// `SampleSongsView`.
enum SampleSongCatalog {
    struct Entry: Identifiable {
        var id: String { job.id }
        let job: Job
        let artist: String
        let source: String
        let license: String
    }

    private static let stemNames = ["vocals", "drums", "bass", "guitar", "piano", "other"]

    static let all: [Entry] = [
        Entry(
            job: makeJob(id: "solomon", title: "Solomon", duration: 211.16, bpm: 83, key: "C# min", scale: "Natural Minor"),
            artist: "KETSA X THE CONSCIOUS COLLECTIVE",
            source: "Free Music Archive",
            license: "CC BY"
        ),
        Entry(
            job: makeJob(id: "summer-vibe", title: "Summer Vibe (Clean)", duration: 138.024, bpm: 117, key: "G maj", scale: "Major"),
            artist: "Creepzz",
            source: "Free Music Archive",
            license: "CC BY"
        ),
        Entry(
            job: makeJob(id: "no-data-centers", title: "NO DATA CENTERS (Protest Chong)", duration: 258.542458, bpm: 129, key: "F maj", scale: "Major"),
            artist: "NoKings XXX",
            source: "Free Music Archive",
            license: "CC BY"
        ),
    ]

    private static func makeJob(id: String, title: String, duration: Double, bpm: Int, key: String, scale: String) -> Job {
        Job(
            id: id,
            status: "done",
            title: title,
            duration: duration,
            thumbnail: nil,
            bpm: bpm,
            key: key,
            scale: scale,
            stems: stemNames.map { Job.Stem(name: $0, url: nil) },
            createdAt: 0
        )
    }

    /// Stem name -> its bundled file URL for one entry's job id, resolved
    /// from the `SampleSongs/<id>/` folder reference in the app bundle.
    static func stemURLs(forJobID jobID: String) -> [String: URL] {
        Dictionary(uniqueKeysWithValues: stemNames.compactMap { name -> (String, URL)? in
            guard let url = Bundle.main.url(forResource: name, withExtension: "m4a", subdirectory: "SampleSongs/\(jobID)") else { return nil }
            return (name, url)
        })
    }

    static func peaksURL(forJobID jobID: String) -> URL? {
        Bundle.main.url(forResource: "peaks", withExtension: "json", subdirectory: "SampleSongs/\(jobID)")
    }
}
