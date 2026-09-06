import Foundation

/// Where downloaded stem files live on disk: `Documents/Stems/<jobID>/<stem>.wav`.
/// Stateless by design (pure path math + `FileManager` calls) so it's safe to
/// use from multiple concurrent download tasks without synchronization.
struct StemFileStore: Sendable {
    private var stemsRoot: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Stems", isDirectory: true)
    }

    func url(jobID: String, stem: String) -> URL {
        stemsRoot
            .appendingPathComponent(jobID, isDirectory: true)
            .appendingPathComponent("\(stem).wav")
    }

    func exists(jobID: String, stem: String) -> Bool {
        FileManager.default.fileExists(atPath: url(jobID: jobID, stem: stem).path)
    }

    func peaksURL(jobID: String) -> URL {
        stemsRoot.appendingPathComponent(jobID, isDirectory: true).appendingPathComponent("peaks.json")
    }

    func peaksExist(jobID: String) -> Bool {
        FileManager.default.fileExists(atPath: peaksURL(jobID: jobID).path)
    }

    /// Cache for `ChordAnalyzer`'s on-device chord detection — unlike
    /// `peaksURL`, nothing server-side produces this; it's written locally
    /// once analysis finishes so it's never recomputed for the same job.
    func chordsURL(jobID: String) -> URL {
        stemsRoot.appendingPathComponent(jobID, isDirectory: true).appendingPathComponent("chords.json")
    }

    func chordsExist(jobID: String) -> Bool {
        FileManager.default.fileExists(atPath: chordsURL(jobID: jobID).path)
    }

    /// Removes everything downloaded/cached for one job (stems, peaks,
    /// chords) — used when the user trashes a song from the mobile library,
    /// to actually reclaim the space rather than just hiding the row.
    func deleteAll(jobID: String) {
        let dir = stemsRoot.appendingPathComponent(jobID, isDirectory: true)
        try? FileManager.default.removeItem(at: dir)
    }
}
