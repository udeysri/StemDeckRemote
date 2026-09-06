import Foundation

/// Merges every stem's [min, max] peaks into one overview waveform (min of
/// mins, max of maxes per point) — used for the single master waveform both
/// mixer views show now, since neither wants to pick one arbitrary stem to
/// represent "the song."
enum PeaksMerger {
    static func combine(_ peaksByStem: [String: [[Double]]]) -> [[Double]] {
        let allPeaks = Array(peaksByStem.values)
        guard let length = allPeaks.map(\.count).max(), length > 0 else { return [] }
        var combined = [[Double]](repeating: [0, 0], count: length)
        for peaks in allPeaks {
            for i in 0..<min(length, peaks.count) where peaks[i].count == 2 {
                combined[i][0] = min(combined[i][0], peaks[i][0])
                combined[i][1] = max(combined[i][1], peaks[i][1])
            }
        }
        return combined
    }
}
