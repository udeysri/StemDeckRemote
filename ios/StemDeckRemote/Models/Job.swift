import Foundation

/// Mirrors the JSON `GET /api/jobs` returns — one entry per completed job in
/// the StemDeck library (see `Job.to_state()` in the desktop app's
/// `app/core/models.py`). Only the fields the song list needs are decoded;
/// everything else in the payload is ignored.
struct Job: Codable, Identifiable, Hashable {
    let id: String
    let status: String
    let title: String?
    let duration: Double?
    let thumbnail: String?
    let bpm: Int?
    let key: String?
    let scale: String?
    let stems: [Stem]?
    let createdAt: Double?

    struct Stem: Codable, Hashable {
        let name: String?
        // Server-relative path, e.g. "/api/jobs/<id>/stems/vocals.wav"
        // (see runner.py's `job.stems` assignment). Not currently used —
        // downloads reconstruct the same path from `id` + stem name — but
        // decoded so a future server change to this shape doesn't silently
        // break `stems` decoding for the rest of the fields.
        let url: String?
    }

    enum CodingKeys: String, CodingKey {
        case id = "job_id"
        case status, title, duration, thumbnail, bpm, key, scale, stems
        case createdAt = "created_at"
    }

    var isAvailable: Bool { status == "done" }

    var formattedDuration: String? {
        guard let duration, duration.isFinite, duration >= 0 else { return nil }
        let total = Int(duration.rounded())
        return String(format: "%d:%02d", total / 60, total % 60)
    }

    var stemNames: [String] {
        (stems ?? []).compactMap(\.name)
    }

    var subtitle: String? {
        switch (bpm, key) {
        case let (bpm?, key?): return "\(bpm) BPM • \(key)"
        case let (bpm?, nil): return "\(bpm) BPM"
        case let (nil, key?): return key
        default: return nil
        }
    }
}
