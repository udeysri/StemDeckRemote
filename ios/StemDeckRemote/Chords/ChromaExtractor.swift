import Accelerate
import Foundation

/// Turns a window of mono PCM samples into a 12-element chroma vector
/// (relative energy per pitch class C, C#, D, ... B) via a single windowed
/// FFT — the same idea as StemDeck's own `librosa.feature.chroma_cqt`
/// (`app/pipeline/analyze.py`), traded down from a true Constant-Q Transform
/// to a plain FFT for a much simpler, still-fast-enough implementation:
/// this runs once per beat per stem in the background, not live.
enum ChromaExtractor {
    /// Chord tones live well within this range on guitar/piano/bass — no
    /// point spending bins on sub-bass rumble or high harmonics/hiss.
    private static let minHz: Float = 60
    private static let maxHz: Float = 5000

    static func chroma(samples: [Float], sampleRate: Double) -> [Double] {
        var chroma = [Double](repeating: 0, count: 12)
        guard samples.count >= 64 else { return chroma }

        let log2n = vDSP_Length(log2(Double(samples.count)).rounded(.down))
        let fftSize = 1 << Int(log2n)
        guard fftSize >= 64, let fftSetup = vDSP_create_fftsetup(log2n, FFTRadix(kFFTRadix2)) else {
            return chroma
        }
        defer { vDSP_destroy_fftsetup(fftSetup) }

        var window = [Float](repeating: 0, count: fftSize)
        vDSP_hann_window(&window, vDSP_Length(fftSize), Int32(vDSP_HANN_NORM))

        var real = [Float](repeating: 0, count: fftSize)
        vDSP_vmul(samples, 1, window, 1, &real, 1, vDSP_Length(fftSize))
        var imaginary = [Float](repeating: 0, count: fftSize)

        var magnitudes = [Float](repeating: 0, count: fftSize / 2)
        real.withUnsafeMutableBufferPointer { realPtr in
            imaginary.withUnsafeMutableBufferPointer { imagPtr in
                var split = DSPSplitComplex(realp: realPtr.baseAddress!, imagp: imagPtr.baseAddress!)
                vDSP_fft_zip(fftSetup, &split, 1, log2n, FFTDirection(FFT_FORWARD))
                magnitudes.withUnsafeMutableBufferPointer { magPtr in
                    vDSP_zvmags(&split, 1, magPtr.baseAddress!, 1, vDSP_Length(fftSize / 2))
                }
            }
        }

        let binHz = Float(sampleRate) / Float(fftSize)
        for bin in 1..<(fftSize / 2) {
            let freq = Float(bin) * binHz
            guard freq >= minHz, freq <= maxHz else { continue }
            let midi = 12 * log2f(freq / 440) + 69
            let pitchClass = ((Int(midi.rounded()) % 12) + 12) % 12
            chroma[pitchClass] += Double(magnitudes[bin])
        }
        return chroma
    }
}
