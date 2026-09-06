# Architecture

This document is the cross-platform reference: what both apps do, why they're
built as two independent native codebases rather than one shared one, and
where their design deliberately diverges because the platforms themselves do.
For implementation-level detail on a specific platform, see
[`ios/README.md`](../ios/README.md) and [`android/README.md`](../android/README.md).

## The shape of the app, on both platforms

Every screen maps 1:1 across iOS and Android:

1. **Pairing** — first launch (or after disconnecting) shows a landing screen
   with three ways to connect to a StemDeck desktop instance on the LAN: scan
   its Settings → Network QR code, type its address manually, or open three
   bundled sample songs with no server at all. A self-signed LAN certificate
   (once StemDeck has generated one) is trusted the same way a browser
   handles "this connection is not private": shown once, pinned by
   fingerprint after the user confirms it.
2. **Library** — the paired server's song list (`GET /api/jobs`), with
   search, sort, client-side folders (a purely local organizational layer —
   StemDeck's own API has no concept of folders), and a background download
   queue that fetches every song's stems automatically, one at a time, so
   they're usually already local by the time a song is tapped open. Swiping
   a song away moves it to a local-only Trash — never a delete request to
   StemDeck itself — and it stays excluded from future syncs until restored.
3. **Player** — a per-stem hardware-console mixer (two view modes: a plain
   waveform-and-fader "Simple" console, and a full six-stem "Advanced" rack
   with per-stem waveforms, LED level meters, and dB-precise faders), plus a
   shared bottom transport for play/pause/loop, speed, and pitch. Can be
   minimized to a small persistent mini-player without stopping playback,
   and keeps playing in the background with lock-screen/notification
   transport controls.

## Why two independent native codebases, not one shared one

Nothing is shared between the two apps — not models, not networking, not
UI — by deliberate choice, not oversight. The most engineering-relevant
reason: each platform's native audio stack solves per-stem mixing
completely differently (see below), and that divergence would have fought a
shared-core architecture (à la Kotlin Multiplatform or a bridged C++ engine)
at exactly the point where each platform's own tools already do the best
job. Native-first also means the UI on each platform is built from that
platform's own idioms (SwiftUI vs. Jetpack Compose) rather than a
least-common-denominator abstraction over both.

The cost of that choice: the two apps only stay in sync by convention and
code review, not by a shared source of truth. If that ever becomes painful,
the right fix is a shared *contract* — e.g. an OpenAPI spec for StemDeck's
REST API that both clients are checked against — not shared implementation
code.

## Where the platforms diverge, and why

| Concern | iOS | Android |
|---|---|---|
| UI framework | SwiftUI | Jetpack Compose (Material3 for standard chrome, custom `Canvas` composables for the mixer console) |
| State management | `ObservableObject` + `@Published` | `StateFlow`, `ViewModel`s via Hilt |
| Dependency injection | Manual (`.shared` singletons) | Hilt |
| Networking | `URLSession` | OkHttp |
| TLS trust-on-first-use | `PinnedTrustDelegate` (custom `URLSessionDelegate`) | `PinnedTrustManager` (custom `X509TrustManager`) — same SHA-256 fingerprint-pinning scheme on both |
| QR pairing | `AVCaptureMetadataOutput` directly | CameraX + ML Kit's on-device barcode scanner |
| **Per-stem audio mixing** | One `AVAudioEngine` graph, one player node per stem | **No single-engine equivalent exists** — one Media3 `ExoPlayer` per stem, driven in lockstep with a periodic drift-correction pass (~80ms threshold) |
| Speed/pitch | `AVAudioUnitTimePitch` per stem, kept in lockstep | Media3's built-in Sonic time-stretch (`PlaybackParameters`), applied identically to every stem for the same reason |
| Live per-stem level metering | An `AVAudioEngine` node tap | A custom, transparent `AudioProcessor` inserted per-stem via a custom `RenderersFactory` |
| Background audio / lock screen | `MPNowPlayingInfoCenter` + `MPRemoteCommandCenter` | A Media3 `MediaSessionService` publishing a `SimpleBasePlayer` facade over the same app state |
| Cleartext (http) LAN support | `NSAllowsLocalNetworking` (ATS exception) | `res/xml/network_security_config.xml` (Android blocks cleartext app-wide by default from API 28+) |
| "Local Network" permission | A one-time system prompt (`LocalNetworkAuthorization`) | No equivalent exists — Android has no runtime permission gate for plain LAN sockets |
| Folder reordering | Native `List` edit mode (drag handles for free) | Not implemented — Compose has no equally lightweight built-in |

The **per-stem mixing engine** is the one place worth understanding in more
depth if you're touching either app's playback code: iOS's single-graph
approach and Android's N-synced-players approach are solving the exact same
problem (independent volume/mute/solo per stem, sample-tight sync, shared
speed/pitch) with fundamentally different primitives, because no shared one
exists on both platforms. See `StemMixerEngine` in each platform's source for
the full reasoning in code comments at the point of each decision.

## On-device chord detection (implemented, not yet wired in)

Both apps ship a complete on-device chord-detection pipeline — beat-grid-
aware windowing, an FFT → chroma-vector extraction, and chord-template
correlation matched against StemDeck's own key-detection math — including,
on Android, a from-scratch radix-2 FFT in Kotlin (iOS uses Accelerate/vDSP).
**Neither app currently calls it during playback.** `PlayerViewModel` on both
platforms has a `currentChord` property and the console header has a slot
ready for it; the analyzer itself just isn't invoked yet. This is a
pre-launch gap on both platforms, not a missing Android port of a working
iOS feature.

## Persistence

Everything client-side is intentionally simple, file-based state — no
database on either platform:

- **Paired server** (`PairingStore`): the last successfully paired
  host/port/scheme/certificate-fingerprint.
- **Library cache** (`LibraryCache`): the last successfully synced song
  list, so the library screen has something to show immediately (and works
  fully offline for already-downloaded songs) before a live refresh
  completes.
- **Folders** (`LibraryFolderStore`): user-created folders and which job ID
  is assigned to which — purely local; StemDeck's API has no concept of
  folders.
- **Trash** (`TrashedSongStore`): songs removed from the mobile library
  view. Stores a full snapshot of each song's metadata at the moment it was
  trashed (not just its ID), so the Trash section still shows a sensible
  title/BPM/key/duration even if StemDeck itself later deletes the job for
  real. Never calls StemDeck to delete anything — restoring un-hides the
  local record; if StemDeck no longer has the job either, there's nothing
  further to reconcile.
- **Downloaded stems** (`StemFileStore`): the actual WAV files, cached to
  disk per job ID so a song opened twice plays instantly the second time.

## Networking notes worth knowing

- StemDeck serves **plain http** until its "Make available on your network"
  setting has generated a certificate, and **https** (self-signed) after —
  both clients handle either transparently, matching whatever the paired
  address advertises.
- A LAN request either resolves in well under a second or the server
  genuinely isn't reachable — both clients use a short (~2s) timeout for
  routine requests so a real outage fails fast into each app's
  offline-cache path, with a longer timeout specifically for the first-ever
  pairing health check (which, on iOS, can coincide with the OS's one-time
  Local Network permission prompt).
- Both clients explicitly disable HTTP response caching end-to-end (no
  `URLCache` on iOS, no cache on OkHttp) — StemDeck's `/api/jobs` doesn't
  send cache-control headers, and a stale cached response previously could
  make deleted songs appear to "come back" after a sync.
- Both clients decode StemDeck's JSON leniently with respect to
  non-finite floats (`NaN`/`Infinity`/`-Infinity`) — Python's default JSON
  serializer emits these as bare, technically-invalid-JSON tokens for a
  non-finite field (e.g. a corrupted source file producing an infinite
  peak level), which a strict JSON parser otherwise rejects wholesale,
  taking down an entire library sync over one bad field in one job.

## Acknowledgments

Both apps are companions to **[StemDeck](https://github.com/stemdeckapp/stemdeck)**,
the desktop app that does the actual stem separation and analysis these
clients pair with, sync from, and play back. All credit for the separation
pipeline, the beat-grid/key-detection math both apps' chord detection is
matched against, and the REST API both clients speak belongs to that
project.
