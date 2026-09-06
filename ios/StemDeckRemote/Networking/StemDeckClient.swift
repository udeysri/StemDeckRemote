import Foundation

/// Talks to one StemDeck desktop instance's REST API over the LAN. Scheme is
/// whatever the server actually advertises: plain http until "Make available
/// on your network" has generated a LAN certificate, https afterward. TLS
/// trust for the https case is handled by `PinnedTrustDelegate` — see that
/// type for why StemDeck's self-signed cert needs special handling at all.
/// `@unchecked Sendable`: everything mutable here (`delegate`'s pinning
/// state) is scoped to one instance, and callers making concurrent stem
/// downloads create one `StemDeckClient` per download rather than sharing —
/// see `PlayerViewModel.start()`.
final class StemDeckClient: @unchecked Sendable {
    enum ClientError: Error {
        case unreachable
        case certificateRejected
        /// Server explicitly refused the request and explained why — e.g.
        /// StemDeck's own "not available on the network" or "needs https"
        /// gate (`app/main.py`), which returns a human-readable body.
        case serverRefused(String)
        case invalidResponse(Int)
    }

    let baseURL: URL
    private let delegate: PinnedTrustDelegate?
    private let session: URLSession

    /// - Parameters:
    ///   - pinnedFingerprint: `nil` during pairing (any https cert is
    ///     accepted and reported via `onFingerprint`); the stored fingerprint
    ///     afterwards. Ignored entirely for plain http.
    ///   - timeout: A LAN request either resolves in well under a second or
    ///     the server genuinely isn't there — a long timeout made a
    ///     no-network state (no Wi-Fi, airplane mode) look hung rather than
    ///     failing fast into the offline-cache path
    ///     LibraryViewModel/PlayerViewModel already have — so 2s is the
    ///     right default for an already-paired server. The one exception is
    ///     pairing's own first health check: that request can be the exact
    ///     moment iOS shows its one-time "Local Network" permission dialog,
    ///     which pauses the request until the user responds — `PairingView`
    ///     passes a longer timeout there so a slow tap on "Allow" doesn't
    ///     make the very first connection attempt fail.
    init(scheme: String, host: String, port: Int, pinnedFingerprint: String?, timeout: TimeInterval = 2, onFingerprint: ((String) -> Void)? = nil) {
        self.baseURL = URL(string: "\(scheme)://\(host):\(port)")!

        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = timeout
        config.waitsForConnectivity = false
        // `.ephemeral` only means "don't persist to disk" — URLSession still
        // keeps an in-memory HTTP cache by default, and `GET /api/jobs`
        // ships with no Cache-Control/ETag/Last-Modified header for it to
        // key off, so CFNetwork can end up serving a stale in-memory
        // response to a repeated pull-to-refresh within the same app
        // session (surfaced as songs deleted on the desktop app still
        // showing up here after "syncing"). Disabling the cache outright
        // guarantees every request reflects StemDeck's actual current
        // state — this is a live-freshness fix only; `LibraryCache`'s
        // on-disk last-known-good snapshot (shown immediately, and as the
        // offline fallback) is a separate, deliberate mechanism and is
        // untouched by this.
        config.urlCache = nil
        config.requestCachePolicy = .reloadIgnoringLocalAndRemoteCacheData

        if scheme == "https" {
            let delegate = PinnedTrustDelegate()
            delegate.pinnedFingerprint = pinnedFingerprint
            delegate.onFingerprint = onFingerprint
            self.delegate = delegate
            self.session = URLSession(configuration: config, delegate: delegate, delegateQueue: nil)
        } else {
            self.delegate = nil
            self.session = URLSession(configuration: config)
        }
    }

    convenience init(server: PairedServer) {
        self.init(scheme: server.scheme, host: server.host, port: server.port, pinnedFingerprint: server.certFingerprint)
    }

    /// Confirms the server is reachable — used both to validate a freshly
    /// scanned/typed address during pairing and as a lightweight ping.
    func checkHealth() async throws {
        _ = try await get("api/health")
    }

    func fetchLibrary() async throws -> [Job] {
        let data = try await get("api/jobs")
        do {
            return try JSONDecoder().decode([Job].self, from: Self.sanitizingNonConformingFloats(data))
        } catch {
            throw ClientError.invalidResponse(-1)
        }
    }

    /// Downloads one stem's WAV file to `destination`, replacing anything
    /// already there. WAV (not the transcoded `.mp3` variant) so all stems
    /// decode to the same PCM format for `AVAudioEngine` to mix sample-for-
    /// -sample without resampling drift between tracks.
    func downloadStem(jobID: String, name: String, to destination: URL) async throws {
        delegate?.resetRejectionFlag()
        let url = baseURL.appendingPathComponent("api/jobs/\(jobID)/stems/\(name).wav")
        do {
            let (tempURL, response) = try await session.download(from: url)
            guard let http = response as? HTTPURLResponse else {
                throw ClientError.invalidResponse(-1)
            }
            guard (200..<300).contains(http.statusCode) else {
                let body = (try? Data(contentsOf: tempURL)) ?? Data()
                throw ClientError.serverRefused(Self.message(from: body) ?? "StemDeck returned an error (HTTP \(http.statusCode)) for \(name).")
            }
            try FileManager.default.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
            if FileManager.default.fileExists(atPath: destination.path) {
                try FileManager.default.removeItem(at: destination)
            }
            try FileManager.default.moveItem(at: tempURL, to: destination)
        } catch let error as ClientError {
            throw error
        } catch {
            if delegate?.didRejectCertificate == true {
                throw ClientError.certificateRejected
            }
            throw ClientError.unreachable
        }
    }

    /// Pre-computed [min, max] waveform peaks for every stem in a job — the
    /// same overview data (`_PEAK_POINTS` = 1500 points/stem, values already
    /// normalized to [-1, 1]) the desktop app's own waveform renders from.
    /// Non-fatal to omit: `WaveformView` just draws nothing without it.
    func fetchPeaks(jobID: String) async throws -> Data {
        try await get("api/jobs/\(jobID)/stems/peaks.json")
    }

    /// StemDeck's click-track beat grid — see `BeatGrid`. `nil` (never
    /// throws) on any failure: an old job without this stage, a transient
    /// network hiccup, or a malformed body all mean the same thing to
    /// `ChordAnalyzer` — "no beat grid, use fixed windows instead."
    func fetchBeatGrid(jobID: String) async -> BeatGrid? {
        guard let data = try? await get("api/jobs/\(jobID)/stems/beats.json") else { return nil }
        return try? JSONDecoder().decode(BeatGrid.self, from: Self.sanitizingNonConformingFloats(data))
    }

    private func get(_ path: String) async throws -> Data {
        delegate?.resetRejectionFlag()
        let url = baseURL.appendingPathComponent(path)
        do {
            let (data, response) = try await session.data(from: url)
            guard let http = response as? HTTPURLResponse else {
                throw ClientError.invalidResponse(-1)
            }
            guard (200..<300).contains(http.statusCode) else {
                throw ClientError.serverRefused(Self.message(from: data) ?? "StemDeck returned an error (HTTP \(http.statusCode)).")
            }
            return data
        } catch let error as ClientError {
            throw error
        } catch {
            if delegate?.didRejectCertificate == true {
                throw ClientError.certificateRejected
            }
            throw ClientError.unreachable
        }
    }

    /// StemDeck's error responses are either a FastAPI `{"detail": "..."}`
    /// JSON body or, for the network/secure-origin gates in `app/main.py`,
    /// a plain-text explanation. Either way, it's the message worth showing.
    private static func message(from data: Data) -> String? {
        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let detail = json["detail"] as? String {
            return detail
        }
        if let text = String(data: data, encoding: .utf8), !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return text
        }
        return nil
    }

    /// StemDeck's JSON responses go through plain Python `json.dumps`
    /// server-side, which — unlike strict JSON — happily emits a bare
    /// `NaN`/`Infinity`/`-Infinity` token for a non-finite float field
    /// (e.g. a corrupted source file producing a `+inf` peak level).
    /// `JSONDecoder` has no equivalent of kotlinx.serialization's
    /// `allowSpecialFloatingPointValues` to accept those, and — unlike a
    /// single malformed field failing just that key — it rejects the
    /// *entire* response as invalid JSON syntax, silently taking down the
    /// whole library sync (misreported to the user as being offline) over
    /// one bad job's stray field. Since these tokens can only legally
    /// appear unquoted immediately after a `:` in this API's JSON shape —
    /// never as object keys, never inside an already-quoted string value —
    /// rewriting that exact pattern to `null` is safe and narrowly scoped;
    /// the field then decodes as `nil`/`0` rather than the true (garbage)
    /// value, same as any other missing optional field, and this app never
    /// reads a raw `peak_db`/`dynamic_range` value anyway.
    private static func sanitizingNonConformingFloats(_ data: Data) -> Data {
        guard let text = String(data: data, encoding: .utf8),
              text.contains("NaN") || text.contains("Infinity")
        else { return data }
        guard let regex = try? NSRegularExpression(pattern: #"(:\s*)-?(?:NaN|Infinity)\b"#) else { return data }
        let range = NSRange(text.startIndex..., in: text)
        let sanitized = regex.stringByReplacingMatches(in: text, range: range, withTemplate: "$1null")
        return sanitized.data(using: .utf8) ?? data
    }
}
