# StemDeck Remote (Android)

Native Kotlin/Jetpack Compose companion app for
[StemDeck](https://github.com/stemdeckapp/stemdeck) — see the
[repo root](../README.md) for what this is, and
[`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) for the full cross-platform
architecture writeup (the summary below is Android-specific detail; that doc
has the platform comparison). This is the Android counterpart to the
`ios/` (SwiftUI) target in this same repo — pairs with a StemDeck desktop
instance on the LAN, syncs its song library, downloads a song's stems to the
phone, and plays them back through a native per-stem mixer. Feature-for-
feature parity with the iOS app is the goal of this port; where the two
platforms have no equivalent primitive, the Android-native solution is
called out below and in code comments at the point it diverges.

## Setup

```sh
cd android
./gradlew assembleDebug   # or open the folder directly in Android Studio
```

Requires an Android SDK with `compileSdk 35` installed (Android Studio will
prompt for it). `local.properties` (gitignored) should point `sdk.dir` at
your SDK; Android Studio regenerates it automatically if missing.

Run on an emulator or device (minSdk 26 / Android 8.0+) from Android Studio,
or:

```sh
./gradlew installDebug
adb shell am start -n com.stemdeck.remote/.MainActivity
```

## Architecture

- **UI**: Jetpack Compose throughout, Material3 components for standard
  chrome (lists, dialogs, sheets, top bars) and fully custom Canvas-drawn
  composables for the mixer console itself (waveforms, LED faders/meters,
  hardware-style buttons) — the same split as the iOS side's SwiftUI: system
  controls for ordinary screens, hand-drawn views for the console's
  hardware-rack look (`ConsoleTheme.kt` ports the iOS brief's color/type
  tokens directly).
- **State management**: `StateFlow` everywhere a `@Published` property
  would be used on iOS; `ViewModel` (via Hilt's `@HiltViewModel`) for
  screen-scoped state, plain `@Singleton` classes for app-scoped state
  (`PlaybackCoordinator`, `StemDownloadQueue`, `PairingStore`, ...) — the
  direct match for the iOS side's `ObservableObject` singletons
  (`PlaybackCoordinator.shared`, etc).
- **DI**: Hilt. Every repository/store/coordinator is `@Singleton`
  constructor-injected; no manual service locator.
- **Networking**: OkHttp, with a hand-rolled `X509TrustManager`
  (`PinnedTrustManager`) implementing the same trust-on-first-use scheme as
  the iOS side's `PinnedTrustDelegate` for StemDeck's self-signed LAN
  certificate — SHA-256 fingerprint pinned after one-time user confirmation.
- **QR pairing**: CameraX + ML Kit's on-device barcode scanner (the iOS side
  uses `AVCaptureMetadataOutput` directly; ML Kit is the standard Android
  equivalent).
- **Playback engine** (`StemMixerEngine`): this is the one place the two
  platforms' native audio stacks genuinely don't have a shared primitive.
  iOS builds one `AVAudioEngine` graph with a player node per stem. Android
  has no single-engine equivalent for mixing several independently-decoded
  files with independent per-stem volume, so this uses **one Media3
  `ExoPlayer` per stem**, driven in lockstep:
  - Play/pause/seek issued to every player together; a lightweight
    once-a-second drift-correction pass re-seeks any stem that's drifted
    more than 80ms from the reference stem (insurance a single-engine graph
    doesn't need, since N independent `AudioTrack`s can drift a little over
    a long song).
  - Speed/pitch: `PlaybackParameters(speed, pitch)`, Media3's built-in Sonic
    time-stretch — applied identically to every stem so they can't drift
    out of tempo/key with each other, same reasoning as iOS keeping every
    `AVAudioUnitTimePitch` in lockstep.
  - Live per-stem RMS level (for the LED meters): a small custom
    `AudioProcessor` (`RmsAudioProcessor`) inserted into each stem's own
    render pipeline via a per-stem `RenderersFactory` override
    (`StemRenderersFactory`) — a transparent (non-modifying) tap, the direct
    equivalent of the iOS side's `AVAudioEngine` node tap
    (`installLevelTap`).
- **Background audio / lock screen**: a Media3 `MediaSessionService`
  (`PlaybackService`) publishing a `SimpleBasePlayer` facade
  (`NowPlayingSessionPlayer`) over `PlaybackCoordinator`'s state — the
  equivalent slot to iOS's `UIBackgroundModes: audio` +
  `MPNowPlayingInfoCenter`/`MPRemoteCommandCenter` wiring. Also owned by
  `PlaybackCoordinator`, which is the one long-lived handle on whatever's
  currently playing on both platforms.
- **Chord detection** (`chords/`): `ChordAnalyzer`/`ChromaExtractor`/
  `ChordTemplates`/`BeatGrid` are a straight port of the iOS side's
  on-device chord detection (FFT + chroma + chord-template correlation
  against StemDeck's own beat grid) — including a from-scratch radix-2 FFT
  in Kotlin, since Android has no `Accelerate`/`vDSP` equivalent. Like the
  iOS source it was ported from, **this is not wired into playback** —
  `PlayerViewModel.currentChord` exists and the analyzer is fully
  functional, but nothing currently calls `ChordAnalyzer.chords(...)`. This
  matches the current (pre-launch) state of the iOS app rather than being
  an Android omission.
- **Cleartext (http) support**: StemDeck serves plain http on the LAN until
  its LAN certificate has been generated. Android blocks cleartext traffic
  app-wide by default from API 28 on; `res/xml/network_security_config.xml`
  lifts that, the direct equivalent of the iOS side's
  `NSAllowsLocalNetworking` ATS exception.
- **No "Local Network" permission equivalent**: iOS gates LAN socket access
  behind a one-time system permission prompt (`LocalNetworkAuthorization` on
  that side). Android has no equivalent runtime permission for plain LAN
  connections — `INTERNET`/`ACCESS_NETWORK_STATE` (both install-time,
  non-prompting) are all a normal app needs — so there's nothing to port
  there.

## Sample songs (no StemDeck required)

Same 3 bundled Creative Commons (CC BY) tracks as the iOS target, copied
verbatim into `app/src/main/assets/SampleSongs/` (same AAC-in-`.m4a` stems
and `peaks.json` files — no re-encoding needed, Android's `MediaExtractor`/
ExoPlayer decode AAC natively). See `pairing/SampleSongCatalog.kt` for the
catalog.

## Not yet implemented

- Re-downloading a stem that failed to download (removing the song via
  swipe-to-delete and re-syncing works around it, since that clears its
  cached files and download status)
- Drag-to-reorder folders in Manage Folders (create/delete are implemented,
  matching the iOS app; Compose has no equally lightweight built-in for
  reordering that `List`'s native edit mode gives iOS for free) — nor is
  there a rename, on either platform
- Chord detection is implemented but not wired into the console's CHORD
  readout (see `docs/ARCHITECTURE.md` — matches the iOS app's current state)
