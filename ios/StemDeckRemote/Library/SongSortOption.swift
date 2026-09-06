import Foundation

/// Client-side sort for the library list — purely a display transform over
/// `LibraryViewModel.songs`, no server involvement.
enum SongSortOption: String, CaseIterable, Identifiable {
    case dateAddedNewest = "Newest First"
    case dateAddedOldest = "Oldest First"
    case nameAscending = "Name (A–Z)"
    case nameDescending = "Name (Z–A)"
    case keyAscending = "Key (A–Z)"
    case bpmAscending = "BPM (Low–High)"
    case bpmDescending = "BPM (High–Low)"

    var id: String { rawValue }

    var systemImage: String {
        switch self {
        case .dateAddedNewest, .dateAddedOldest: return "clock"
        case .nameAscending, .nameDescending: return "textformat"
        case .keyAscending: return "music.quarternote.3"
        case .bpmAscending, .bpmDescending: return "metronome"
        }
    }

    func sort(_ jobs: [Job]) -> [Job] {
        switch self {
        case .dateAddedNewest:
            return jobs.sorted { ($0.createdAt ?? 0) > ($1.createdAt ?? 0) }
        case .dateAddedOldest:
            return jobs.sorted { ($0.createdAt ?? 0) < ($1.createdAt ?? 0) }
        case .nameAscending:
            return jobs.sorted { ($0.title ?? "").localizedCaseInsensitiveCompare($1.title ?? "") == .orderedAscending }
        case .nameDescending:
            return jobs.sorted { ($0.title ?? "").localizedCaseInsensitiveCompare($1.title ?? "") == .orderedDescending }
        case .keyAscending:
            // Songs with no detected key sort to the end regardless of direction.
            return jobs.sorted { lhs, rhs in
                switch (lhs.key, rhs.key) {
                case (nil, nil): return false
                case (nil, _): return false
                case (_, nil): return true
                case let (l?, r?): return l.localizedCaseInsensitiveCompare(r) == .orderedAscending
                }
            }
        case .bpmAscending:
            return jobs.sorted { lhs, rhs in
                switch (lhs.bpm, rhs.bpm) {
                case (nil, nil): return false
                case (nil, _): return false
                case (_, nil): return true
                case let (l?, r?): return l < r
                }
            }
        case .bpmDescending:
            return jobs.sorted { lhs, rhs in
                switch (lhs.bpm, rhs.bpm) {
                case (nil, nil): return false
                case (nil, _): return false
                case (_, nil): return true
                case let (l?, r?): return l > r
                }
            }
        }
    }
}
