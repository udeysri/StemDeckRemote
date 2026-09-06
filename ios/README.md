# StemDeck Remote (iOS)

Native SwiftUI companion app for [StemDeck](https://github.com/stemdeckapp/stemdeck) —
see the [repo root](../README.md) for what this is, and
[`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) for the full cross-platform
architecture writeup. Pairs with a StemDeck desktop instance on the LAN,
syncs its song library, downloads a song's stems to the phone, and plays
them back through a native per-stem mixer.

## Setup

The `.xcodeproj` is generated, not committed (see `project.yml`).

```sh
brew install xcodegen   # once
xcodegen generate        # run from this directory, where project.yml lives
open StemDeckRemote.xcodeproj
```

Run on the Simulator or a device from Xcode (⌘R). Re-run `xcodegen generate`
after adding/removing/renaming any source file.

## Trying it against a real StemDeck

1. On the desktop app, enable **"Make available on your network"** in
   Settings → Network. If it hasn't generated a certificate yet, StemDeck
   serves plain http; once it has, it serves https on port 8443 with a
   self-signed cert. Either way, Settings shows a QR code for the address.
2. In the iOS app, tap **Scan QR Code** and scan it, or enter the address
   manually (toggle HTTPS on/off to match what StemDeck is actually serving).
3. If the address is https, confirm the certificate fingerprint shown
   (StemDeck's cert is self-signed, so this is the app's one-time trust step,
   same idea as a browser's "connection is not private" warning). Plain http
   pairs immediately — nothing to trust.
4. The song list loads from `GET /api/jobs`. Pull to refresh.
5. Tap a song to open the mixer. First tap downloads its stems (WAV, one per
   stem — `GET /api/jobs/{id}/stems/{name}.wav`) to the app's Documents
   directory; later taps play instantly from that local cache. Each stem gets
   a play/pause-synced volume slider, mixed live via `AVAudioEngine`.

On the Simulator, `localhost`/`127.0.0.1` reaches a StemDeck instance running
on the same Mac directly. On a physical iPhone, both devices need to be on
the same Wi-Fi network.

## Sample songs (no StemDeck required)

The home screen's **Check Sample Songs** opens 3 bundled, Creative Commons
(CC BY)-licensed tracks — separated by StemDeck itself — so the stem console
can be tried without pairing to a live instance (handy for App Review). See
`StemDeckRemote/Pairing/SampleSongCatalog.swift` for the catalog and
`StemDeckRemote/SampleSongs/` for the bundled audio.

Stems are re-encoded to AAC (64kbps) to keep the app bundle small (~30MB for
all 3 full songs, 6 stems each, vs. ~615MB as the original lossless WAV).
The original WAV stems live in `sampleMusic-source/` in this directory
(gitignored, outside the Xcode target) in case they need reprocessing:

```sh
afconvert -f m4af -d aac -b 64000 -q 127 -s 2 <stem>.wav <stem>.m4a
```

## Not yet implemented

- Re-downloading a stem that failed to download (removing the song via
  swipe-to-delete and re-syncing works around it, since that clears its
  cached files and download status)
- Drag-to-reorder folders in Manage Folders (create/delete are implemented;
  there's no rename either — delete and recreate under the new name)
- On-device chord detection is fully implemented (`Chords/ChordAnalyzer.swift`
  and friends) but not yet wired into the console's CHORD readout — see
  `docs/ARCHITECTURE.md`
