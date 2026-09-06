import Foundation

/// One span of the song where a single chord was detected. `PlayerViewModel`
/// looks up which event contains `currentTime` to know what to display.
struct ChordEvent: Codable, Equatable {
    let start: Double
    let end: Double
    let chord: String
}
