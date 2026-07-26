# Relay implementation plan

This document is the execution contract for an implementation agent. Read it completely before editing the repository. Work through one phase at a time and stop at each phase gate.

## 1. Product definition

Relay is a personal, Android-first music player that can play local and remote libraries, report listening activity to Last.fm, and later support source and theme extensions. It must remain responsive, visually functional, and portable to iOS, macOS, Windows, and Linux without an Electron/WebView application shell.

The first usable release has one job: show music stored on an Android device, play it correctly in the background, and expose a clean boundary for later sources and Last.fm tracking.

Working identifiers, until the owner chooses final branding:

- Product name: `Relay`
- Android application ID: `dev.relay.music`
- Shared library namespace: `dev.relay.music.shared`
- Minimum Android SDK: 23
- Target/compile Android SDK: 36

Renaming is intentionally deferred until before the first signed build.

## 2. Fixed technical decisions

Do not reopen these choices during the initial implementation unless the build proves one is impossible.

| Concern | Decision |
|---|---|
| Shared language | Kotlin Multiplatform |
| Shared UI | Compose Multiplatform |
| Android audio | Media3 ExoPlayer in a `MediaSessionService` |
| Android system player surfaces | Media3 `MediaSession`; accept the system-rendered notification and lock-screen layout |
| Android app widgets | Jetpack Glance backed by the existing `MediaSession`; one responsive provider for supported home/keyguard hosts |
| Android live wallpaper | `WallpaperService` with a Canvas renderer first; optional guarded GPU filters later |
| Apple audio later | AVFoundation adapter |
| Desktop audio later | miniaudio adapter behind the shared player contract on macOS, Windows, and Linux |
| State and concurrency | Coroutines and `StateFlow` |
| HTTP | Ktor Client |
| Serialization | kotlinx.serialization |
| Database when persistence begins | Room 3 KMP |
| Images when artwork begins | Coil 3 Multiplatform |
| Dependency injection | Constructor parameters; no DI framework |
| Navigation | Plain screen state until a third screen exists |
| Extension loading | Mihon-style Android source APKs loaded through a small versioned source API; dynamic loading later on other platforms |
| Extension repositories | User-added HTTPS catalogs, commonly hosted from GitHub repositories; explicit trust and install/update confirmation |
| Theme extensions | Data-only theme tokens and assets, never downloaded executable UI code |
| Wallpaper customization | Versioned data-only presets; never downloaded executable shader or rendering code |
| Device sync later | Local-first paired devices: LAN peer sync and user-mediated wired/USB sync bundles; no Relay cloud or central account service |
| Listener identity later | A local Relay profile with optional Last.fm association; Last.fm remains a tracker, not Relay's account system |

### 2.1 Mihon-inspired host and extension architecture

Relay adopts Mihon's useful product boundaries, not its manga-specific classes or its final module count:

- The host app owns the canonical library, playback service, queue, database, settings, backup/restore, extension registry, and all UI.
- Built-in local music, Last.fm, and MusicBrainz implement the first host contracts. Remote libraries arrive later through validated source extensions; built-ins remain available without any marketplace.
- `Source extension` means a versioned remote music source. It enumerates/browses music and resolves streams. Most sources require no Relay-managed authentication; account settings are introduced only with the first real authenticated source.
- Tracking and metadata providers are host-owned integrations, grouped under Settings (for example Last.fm and MusicBrainz). They are not marketplace extensions in the initial architecture.
- `Theme Pack` and `Wallpaper Preset` mean data-only customization packages. They are separate from executable source extensions and never mutate Relay's database directly.
- Audio engines, decoders, DSP, and executable Compose UI are not downloadable plugins. They stay trusted host/platform code because they run in latency- and security-sensitive paths.
- The host converts every plugin response into shared Relay models, validates it, applies time/size limits, and persists only host-owned records.
- A repository is only a signed catalog. It never executes code, grants trust, installs, updates, enables, or removes a plugin without a user action.

Repository catalogs may be hosted as static files in GitHub repositories or any HTTPS host. A versioned repository descriptor identifies the repository and its signing key; its index entries contain at minimum plugin ID, kind, name, version, Relay API range, platform artifacts, download URL, SHA-256 digest, signing-certificate fingerprint, capabilities, permissions, and source/license links. Relay rejects duplicate repository IDs, conflicting signing identities, insecure URLs, incompatible API versions, digest mismatches, and silent signer changes.

Platform execution follows the existing portability boundary:

- Android source plugins are separately installed APKs. Relay verifies the package signer against the trusted repository entry, reads the APK's source metadata, then loads its `RelaySource`/`RelaySourceFactory` classes through an APK class loader, matching Mihon's source-extension model. A source makes its own API/site requests and returns normalized records to Relay. This grants trusted extension code the host process permissions, so Relay only loads APKs whose catalog and APK signer both passed verification; untrusted or incompatible APKs remain installed but disabled with a reason.
- Windows/Linux source extensions are child processes using versioned JSON-RPC over stdin/stdout, with a restricted working directory and no Relay credentials except scoped tokens explicitly granted when a source eventually needs authentication.
- iOS supports built-in/compiled sources and data-only packs. Relay does not promise downloaded executable source extensions where platform policy does not allow them.
- Theme and wallpaper packs are versioned, validated data/assets on every platform and never executable code.

The source-extension manager presents installed, available, update, incompatible, disabled, and untrusted states; supports add/remove repository, install/update/uninstall, enable/disable, permission review, signer trust review, and per-source settings only when a real source needs them; and refreshes catalogs without number badges unless a count materially helps an update action. Source repositories and installed IDs are backed up, but binaries are not.

Pinned stable toolchain for the initial scaffold:

- Kotlin: `2.4.10`
- Compose Multiplatform plugin: `1.11.1`
- Android Gradle Plugin: `9.1.0`
- Gradle wrapper: `9.3.1`
- Java toolchain: 17
- AndroidX Activity Compose: `1.13.0`
- Media3: `1.10.1`
- Ktor when introduced: `3.5.1`
- Room when introduced: `3.0.0`
- Coil when introduced: `3.5.0`

Use stable releases only. Do not replace a pinned version with an alpha, beta, RC, dynamic version, or `+` range. Do not add Ktor, Room, or Coil before the phase that uses it.

AGP 9 cannot combine `com.android.application` and the Kotlin Multiplatform plugin in one module. The Android launcher must therefore be separate from the shared KMP library.

## 3. Initial repository shape

Create only these Gradle modules initially:

```text
.
├── androidApp/                    # Android application and platform integrations
├── composeApp/                    # Shared KMP models, contracts, state, and UI
├── relay-source-api/               # Small Java ABI shared with trusted Android source APKs
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── README.md
└── IMPLEMENTATION_PLAN.md
```

`composeApp` targets:

- Android through `com.android.kotlin.multiplatform.library`
- JVM desktop so common code is continuously checked for desktop compatibility
- `iosArm64`
- `iosSimulatorArm64`

Do not create `iosApp`, backend, or additional `core`/`domain`/`data` modules yet. Add a launcher only when work on that platform actually starts. `relay-source-api` is the deliberate exception: it is a small, separately versioned Java ABI required for Android source APK compatibility.

Expected source layout after phases 1 and 2:

```text
composeApp/src/commonMain/kotlin/dev/relay/music/
├── App.kt
├── model/Track.kt
├── playback/PlayerEngine.kt
├── source/MusicSource.kt
└── ui/RelayTheme.kt

composeApp/src/commonTest/kotlin/dev/relay/music/
└── ContractTest.kt

androidApp/src/main/kotlin/dev/relay/music/
├── MainActivity.kt
├── library/LocalMusicSource.kt
└── playback/
    ├── AndroidPlayerEngine.kt
    └── PlaybackService.kt
```

Keep files cohesive. Do not create one file per trivial data class, repository wrappers, use-case classes, coordinators, factories, or empty package placeholders.

## 4. Core contracts

These contracts live in `commonMain` and contain no Android, Java, Apple, or desktop types.

### 4.1 Track

Start with the minimum fields that local playback and scrobbling require:

```kotlin
data class Track(
    val id: String,
    val sourceId: String,
    val playbackUri: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumArtist: String? = null,
    val durationMs: Long? = null,
    val artworkUri: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val musicBrainzId: String? = null,
)
```

Rules:

- `id` is stable within a source. The Android local source uses its MediaStore content ID.
- `sourceId` is initially `local`.
- `playbackUri` remains a string so the shared layer does not depend on platform URI types.
- Never derive title or artist from a filename for Last.fm submission. Display fallbacks are allowed, but incomplete metadata is not scrobbled.
- Do not add genre, mood, credits, lyrics, release editions, or arbitrary metadata maps until a real feature consumes them. Phase 5 introduces bounded, user-reviewable tags for insights and charts rather than an unstructured metadata blob.

### 4.2 Source boundary

The first contract deliberately supports only library enumeration:

```kotlin
interface MusicSource {
    val id: String
    val displayName: String
    suspend fun tracks(): List<Track>
}
```

Do not add search, authentication, browse pagination, capabilities, or stream resolution yet. Expand this contract only when a first source extension is implemented; that change will be guided by two real implementations rather than guesses.

### 4.3 Player boundary

Use immutable shared state:

```kotlin
enum class RepeatMode { OFF, ONE, ALL }

data class PlaybackState(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val error: String? = null,
) {
    val currentTrack: Track?
        get() = queue.getOrNull(currentIndex)
}

interface PlayerEngine {
    val state: StateFlow<PlaybackState>

    fun setQueue(tracks: List<Track>, startIndex: Int)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()
    fun setRepeatMode(mode: RepeatMode)
    fun setShuffleEnabled(enabled: Boolean)
    fun release()
}
```

Validate `startIndex` at the platform adapter boundary. An empty queue must result in an idle state rather than an exception.

Do not place ExoPlayer, `MediaItem`, Android `Uri`, coroutine scopes, or callbacks in this interface.

## 5. Android data and playback flow

```text
MediaStore
   │ query on Dispatchers.IO
   ▼
LocalMusicSource ── List<Track> ──► shared App UI
                                         │ user selects track
                                         ▼
                                AndroidPlayerEngine
                                         │ MediaController commands
                                         ▼
                                  PlaybackService
                                         │
                                         ▼
                                   Media3 ExoPlayer
```

### 5.1 Local library

`LocalMusicSource` lives in `androidApp` and implements the shared `MusicSource` interface.

Query `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` for:

- `_ID`
- `TITLE`
- `ARTIST`
- `ALBUM`
- `ALBUM_ID`
- `DURATION`
- `TRACK`
- `DISC_NUMBER` when available on the running API

Selection must require `IS_MUSIC != 0`. Sort by `TITLE COLLATE NOCASE ASC` initially.

Permissions:

- API 33+: `READ_MEDIA_AUDIO`
- API 23–32: `READ_EXTERNAL_STORAGE`
- The legacy permission must use `android:maxSdkVersion="32"` in the manifest.

Request the permission from `MainActivity` using the Activity Result API. Do not request broad file access and do not use `MANAGE_EXTERNAL_STORAGE`.

Run the query on `Dispatchers.IO`, close the cursor with `use`, ignore rows whose content URI cannot be constructed, and return an empty list with a user-actionable UI state when permission is absent.

### 5.2 Playback service

`PlaybackService` extends `MediaSessionService` and owns exactly one `ExoPlayer` and one `MediaSession`.

Required behavior:

- Construct both in `onCreate`.
- Return the session from `onGetSession`.
- Release player and session in `onDestroy`.
- Declare `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions.
- Declare the service with `foregroundServiceType="mediaPlayback"`.
- Include Media3 and platform media-session intent filters.
- Let Media3 create the playback notification; do not build a custom notification in this phase.

`AndroidPlayerEngine` owns the `MediaController` connection used by the activity. It maps Media3 callbacks into the shared `PlaybackState`. Position may be refreshed at 500 ms while playing and once after pause/seek. Stop the ticker when not needed.

When setting a queue:

- Convert each `Track` to one `MediaItem`.
- Set media ID to `${sourceId}:${id}`.
- Set URI from `playbackUri`.
- Set title, artist, album, and artwork in `MediaMetadata`.
- Set the complete list once, seek to `startIndex`, prepare, then play.

Do not implement custom decoding, crossfade, loudness analysis, equalization, queue persistence, or download caching in this phase.

### 5.3 Android external surfaces

All Android playback surfaces are clients of the one `PlaybackService`. The activity, system media card, app widget, and live wallpaper must never own their own player.

```text
                              ┌─ system media card / lock screen
PlaybackService + MediaSession├─ shared app UI
               │              └─ Glance player widget
               │ playback events
               ▼
       NowPlayingSnapshot ──────► WallpaperService renderer
               │
               └────────────────► widget process-restoration fallback
```

There are three distinct integrations:

1. **System media card:** implemented in Phase 2 by Media3. Android owns its layout. Relay supplies accurate title, artist, album, artwork, duration, playback state, and supported actions. Do not attempt to replace the system card with a custom notification layout.
2. **App-defined widget:** deferred until Phase 4A. Implement one responsive Glance widget that may be hosted on the home screen and, where the OS/device exposes a keyguard widget host, the lock screen. Availability and placement are controlled by the host. The widget is an additional surface, not a replacement for the system media card.
3. **Live wallpaper:** deferred until Phase 4A. Implement an Android `WallpaperService` that renders cached artwork and optional overlay elements. It observes playback but cannot control or outlive the playback service unnecessarily.

When Phase 4A begins, add this serializable shared snapshot in `commonMain`:

```kotlin
data class NowPlayingSnapshot(
    val trackKey: String? = null,       // "$sourceId:$trackId"
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkCacheKey: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAtEpochMs: Long = 0,
)
```

Persist the latest snapshot as a single Room row after Phase 4. Do not store a bitmap, raw artwork bytes, playback URI, credentials, or a complete queue in this row. Artwork lives in the normal image cache and is referenced by a stable cache key. Update the row on track transition, play, pause, seek, stop, artwork change, and at a coarse interval of no more than once every 15 seconds while playing. The in-app player may still update its own progress every 500 ms; external surfaces must not cause database or widget writes at that rate.

The snapshot is a restoration fallback, not a second source of playback truth. A visible live wallpaper may hold a `MediaController` connection for event delivery and smooth progress, then release it when hidden. A widget action creates a short-lived controller connection, sends the command to the existing session, and releases it. If a controller cannot connect, the action should fail safely and request a widget refresh; it must not instantiate `ExoPlayer`.

## 6. Visual system

The interface is a listening utility, not a streaming-service storefront. It should resemble a precise catalog index combined with transport controls.

### 6.1 Tokens

Use these six base colors:

| Token | Value | Use |
|---|---:|---|
| `Ink` | `#050505` | Main background |
| `Panel` | `#101010` | Fixed controls and selected rows |
| `Line` | `#303030` | Sparse one-pixel borders/dividers |
| `Paper` | `#F1F1EC` | Primary text and principal controls |
| `Muted` | `#92928B` | Metadata and inactive controls |
| `Signal` | `#4B88FF` | Current track, focus, progress, and add/connect actions |

Use `#FF453A` only for destructive/remove/error actions. Do not use gradients, shadows, translucent glass, dynamic Material color, or decorative background color.

### 6.2 Shape, type, and spacing

- Every component uses `RectangleShape` and zero corner radius.
- No elevation or shadows.
- Use the platform sans-serif for titles/body and monospace for durations, counters, bitrates, and source labels.
- Main title: 24sp, bold, uppercase, modest letter spacing.
- Track title: 15sp, medium.
- Metadata: 12sp, normal.
- Utility labels: 10–11sp, monospace, uppercase.
- Use a 4dp spacing grid.
- Interactive targets remain at least 48dp even when the visual mark is smaller.
- Dividers are 1dp and used between logical rows, not around every element.

### 6.3 Signature element: indexed playback rail

The memorable element is a 36dp-wide rail on the left of every track row:

- It displays the zero-padded queue/library index (`01`, `02`, …) in monospace.
- The active track index uses `Signal`.
- A 2dp vertical progress mark grows within the active rail while the track plays.
- The rail encodes real queue order and playback progress; it is not decoration.

Do not add waveforms, fake equalizer bars, animated gradients, or album-art backgrounds.

### 6.4 First screen

```text
┌──────────────────────────────────┐
│ RELAY                      LOCAL │
│ 128 TRACKS                 INDEX │
├────┬─────────────────────────────┤
│ 01 │ Track title                 │
│    │ Artist — Album        03:42 │
├────┼─────────────────────────────┤
│ 02 │ Track title                 │
│    │ Artist — Album        05:11 │
├────┴─────────────────────────────┤
│ CURRENT TRACK              01:24 │
│ Artist — Album             03:42 │
│ [PREV] [PAUSE] [NEXT]            │
└──────────────────────────────────┘
```

Screen rules:

- Header shows product name, active source, and track count.
- Library rows are the main content.
- The player is a rectangular bottom bar with a thin seek track.
- Use text transport controls (`PREV`, `PLAY`/`PAUSE`, `NEXT`) initially; this avoids an icon dependency and stays legible.
- Empty permission state: `Allow audio access to index music stored on this device.` Button: `ALLOW ACCESS`.
- Empty library state: `No music found in device storage.`
- Query failure state must state what failed and offer `TRY AGAIN`.
- All controls have semantic roles and accessibility labels.

## 7. Implementation phases

### Phase 0 — Reproducible scaffold

Goal: a current KMP/Compose build with separate shared and Android app modules.

Tasks:

1. Add `.gitignore` for Gradle, Kotlin, Android Studio, Xcode user data, signing files, `local.properties`, and build outputs.
2. Add `settings.gradle.kts` with `google()`, `mavenCentral()`, and `gradlePluginPortal()` as appropriate. Enable type-safe project accessors only if it does not create warnings with the pinned Gradle version.
3. Add `gradle/libs.versions.toml` with only Phase 0 dependencies and plugins.
4. Add a root `build.gradle.kts` that declares plugins with `apply false`.
5. Add `gradle.properties` with conservative JVM memory, AndroidX, Kotlin style, and Gradle caching. Do not add obsolete Android or Kotlin compatibility flags.
6. Add a Gradle 9.3.1 wrapper.
7. Configure `composeApp` as a Compose KMP library with Android, desktop JVM, and arm64 iOS targets.
8. Configure `androidApp` as an AGP 9 application depending on `composeApp`.
9. Add the smallest `App()` composable showing `RELAY` and an Android `MainActivity` that renders it.
10. Add `README.md` with prerequisites and exact run commands.

Phase gate:

- `./gradlew tasks` succeeds.
- `./gradlew :composeApp:allTests` succeeds.
- `./gradlew :androidApp:assembleDebug` succeeds when JDK 17 and Android SDK 36 are installed.
- No alpha/beta/RC dependencies are present.
- No generated build output is committed.

If the machine lacks Java or the Android SDK, create the files but do not claim verification. Record the exact missing command/tool and stop rather than installing system software without approval.

### Phase 1 — Shared contracts and visual shell

Goal: common code expresses tracks, sources, player state, and the real library/player layout without platform playback yet.

Tasks:

1. Implement `Track`, `MusicSource`, `PlaybackState`, `RepeatMode`, and `PlayerEngine` exactly as scoped above.
2. Implement `RelayTheme` with the fixed tokens, typography, shapes, and dark system-bar expectation.
3. Implement `App(tracks, playbackState, callbacks...)` as a stateless shared composable.
4. Render the header, indexed track list, permission/empty/error state, and bottom player.
5. Add a preview/sample state in debug or preview-only code, not production state.
6. Add one common test file covering:
   - invalid/empty current index returns no current track;
   - valid index returns the expected track;
   - duration formatting handles zero, under one hour, and over one hour.

Phase gate:

- Common tests pass.
- The Android app renders sample content without platform services.
- UI contains no rounded corners, shadows, gradients, fake visualizers, or extra navigation.
- TalkBack semantics identify every actionable element.

### Phase 2 — Android local playback vertical slice

Goal: load MediaStore music and play it through a background Media3 session.

Tasks:

1. Add stable Media3 dependencies to `androidApp` only.
2. Add manifest permissions and `PlaybackService` declaration.
3. Implement runtime audio permission handling.
4. Implement `LocalMusicSource` and map MediaStore rows into shared `Track` values.
5. Implement `PlaybackService`.
6. Implement `AndroidPlayerEngine` backed by a `MediaController`.
7. Replace sample data with the real local source.
8. Connect row selection, play/pause, previous/next, and seeking.
9. Handle controller connection failure and media errors in visible UI state.
10. Verify the Media3-provided notification and lock-screen card include title, artist, album, duration, and artwork when available.
11. Test on an API 33+ emulator/device with at least two tagged audio files.

Phase gate:

- App indexes tagged local tracks after permission is granted.
- Selecting a row starts that item and supplies the complete list as the queue.
- Playback continues when the activity is backgrounded and when the screen is locked.
- System notification and lock-screen media controls show correct track metadata/artwork and operate playback.
- Previous, next, pause, resume, and seek work.
- Revoking permission produces an actionable state rather than a crash.
- Returning to the activity does not create a second player.

### Phase 3 — Last.fm tracking

Goal: authenticate a personal Last.fm account, report now-playing, and persist eligible scrobbles until accepted.

Dependencies introduced here:

- Ktor Client 3.5.0 with platform engines
- kotlinx.serialization
- Okio only if needed explicitly for MD5; do not write a custom MD5 implementation
- Room 3.0.0 for the outgoing scrobble queue

Shared pieces:

- `LastFmClient`
- deterministic parameter sorter/signature builder
- `ScrobbleRule`
- persisted `PendingScrobble`
- `ScrobbleTracker` driven by playback events

Rules:

- A track is eligible only when its duration is greater than 30 seconds and listened time reaches `min(duration / 2, 4 minutes)`.
- Count actual elapsed playing time, not player position, so seeking cannot manufacture a scrobble.
- Capture the track-start Unix timestamp when playback actually begins.
- Send `track.updateNowPlaying` when a tagged track begins.
- Persist a pending scrobble before attempting the network request.
- Delete it only after Last.fm accepts it.
- Keep network errors and Last.fm temporary errors 11, 16, and 29 for retry on the next app start or track transition.
- Error 9 invalidates the stored session and asks the user to reconnect.
- Use an identifiable User-Agent.
- Never submit display fallbacks as artist/title metadata.

Authentication uses Last.fm's desktop flow:

1. Fetch an unauthorized token.
2. Open the Last.fm authorization URL in the platform browser.
3. User returns and selects `FINISH CONNECTION`.
4. Exchange the authorized token for a session key.
5. Store the session key using Android Keystore-backed storage; use Apple Keychain later.

The API key and shared secret come from untracked local build configuration. Never commit them.

Tests:

- Signature parameter ordering and a fixed known digest.
- 30-second boundary.
- Half-duration boundary.
- Four-minute cap.
- Paused time excluded.
- Seek does not advance listened time.
- Duplicate playback callbacks create one pending record.

Phase gate:

- Connect/disconnect works without collecting a Last.fm password.
- Now-playing appears for a valid tagged track.
- One eligible play creates exactly one accepted scrobble.
- A forced network failure leaves a pending row that sends later.

### Phase 4 — Local library persistence

Add Room entities only for user-owned data the operating system does not already own:

- favorites
- listening history
- hidden/pinned/archive flags
- manual playlists and ordered playlist entries
- queue snapshot and resume position
- pending scrobbles if not already added in Phase 3
- metadata overrides and provider IDs when Phase 5 begins

Do not copy the entire local catalog into Room. The configured local source (MediaStore initially, or a user-selected Storage Access Framework tree when enabled) remains the authority for local files. Use stable `(sourceId, trackId)` references in user data tables.

#### 4.1 Comprehensive settings

Add a typed, versioned `RelaySettings` model in shared code and persist one serialized settings snapshot in Room. Do not use an arbitrary string-key map. Expose a `StateFlow<RelaySettings>` through a small `SettingsStore`; the app and services observe the same values.

Settings are grouped by user task:

- `Library`: default sort/grouping, hidden content, indexing and metadata-repair behavior.
- `Playback`: autoplay, queue behavior, resume, shuffle/repeat defaults, crossfade/gapless options only after those features exist.
- `Audio`: quality, normalization, equalizer/preset, mono, speed, decoder diagnostics only after supported.
- `Sources`: built-in and installed source enablement, ordering, authentication, and per-source options.
- `Tracking`: Last.fm connection, scrobble behavior, privacy, and future host-owned tracking integrations.
- `Profile and insights`: local profile name, optional Last.fm username association, listening-data import policy, profile privacy, and which listening data participates in charts. This never stores or exports a Last.fm password/session key as profile data.
- `Metadata`: MusicBrainz/artwork-provider behavior, automatic lookup policy, confidence threshold, artwork quality, mobile-data policy, and cache controls.
- `Sources`: repositories, updates, installer behavior, permissions, and per-source settings only after a real authenticated source exists.
- `Appearance`: theme tokens, density, artwork visibility, and separate data-only Theme Packs.
- `Storage and offline`: cache/download locations, limits, cleanup, and scheduling after downloads exist.
- `Backup and restore`: manual export/import, automatic schedule, expiry, and included sections.
- `Privacy`: lock-screen metadata, history retention, network enrichment, diagnostics, and secret-export policy.
- `Advanced`: reindex library, clear caches, export diagnostics, and reset individual settings groups.

Only render settings for implemented capabilities. Do not fill screens with disabled future toggles. Search is added when the implemented setting count makes browsing materially difficult. Destructive resets require confirmation and state exactly which data they remove.

#### 4.2 Versioned backup and restore

Use a versioned `.relaybackup` archive with a small JSON manifest and checksummed section files. Shared code owns backup models, schema validation, migrations, and restore planning; platform code owns file pickers and archive I/O.

The selectable backup sections are:

- favorites, history, flags, playlists, ordered playlist entries, queue/resume state;
- local profile details, listening-event provenance, and saved chart specifications once Phase 9.1 exists;
- typed app settings;
- Last.fm/tracker associations and account labels, but not session keys by default;
- extension repository descriptors, trusted signing fingerprints, installed plugin IDs, enablement, and plugin settings, but never plugin binaries;
- metadata overrides, selected MusicBrainz IDs, user-reviewed insight tags, and user-selected/custom artwork once Phase 5 exists;
- wallpaper/theme presets once those phases exist.

Backups never include original local audio, remote downloads, disposable image/network caches, API shared secrets, Android Keystore material, or raw playback URLs. The first release does not export login/session secrets; after restore it lists trackers and sources that require reconnection. Optional credential export may be added only as a separately encrypted, passphrase-protected section with an audited cross-platform format.

Restore performs a complete preflight before writing: validate archive/version/checksums and bounded sizes, show included sections and missing plugins/providers, create a safety backup, then apply database changes in a transaction. User-library records merge by stable ID; settings replace only selected groups; unsupported future sections are skipped with a visible warning. A failed restore rolls back and leaves the original library usable.

After manual backup/restore is reliable, add automatic backups through the platform scheduler with a user-selected location, daily/weekly frequency, and a time-based expiry threshold. Manual and automatic backups use separate folders; Relay never expires manual backups. Automatic backup failure produces one actionable notice, not repeated toast spam.

Phase gate:

- Favorites, history, flags, playlists, queue, and resume position survive process death.
- Implemented settings survive restart and affect both activity and playback service consistently.
- A manual backup round-trip restores selected data and settings on a clean install.
- Corrupt, oversized, unsupported, and partially missing backups fail safely before mutation.
- Restore reports missing repositories/plugins/trackers and never silently restores credentials.
- Automatic retention never deletes files outside Relay's selected backup directory.

### Phase 4A — Android widgets and album-art live wallpaper (optional/deferred)

This phase may be scheduled any time after Phase 4 and does not block the first remote source. Keep it deferred until the Phase 2 system media controls and Phase 4 queue persistence are reliable.

#### 4A.1 Shared external-surface state

1. Add `NowPlayingSnapshot` exactly as specified in section 5.3 and a single-row Room entity keyed by a constant ID.
2. Add a `NowPlayingSnapshotStore` with only `observe()`, `read()`, and `write(snapshot)` operations. Keep Android Room types out of its common interface.
3. Have `PlaybackService`, not the activity, write snapshots from player/media-item events.
4. Create an `ArtworkCache` API that resolves `artworkCacheKey` to a local cached image. Remote URLs must be downloaded and validated by the app before an external surface consumes them.
5. On app/process restart, external surfaces display the last snapshot as paused/stale until they reconnect to the session. Never infer that old playback is still active solely from `isPlaying=true` in persisted data.

Tests:

- Snapshot mapping includes the correct stable track key and excludes playback URIs.
- A seek or pause produces one immediate snapshot update.
- Rapid position callbacks are throttled and never write more frequently than the configured coarse interval.
- A process-restored snapshot is treated as paused until session state confirms otherwise.

#### 4A.2 Player widget

1. Add the stable Jetpack Glance app-widget dependency to `androidApp` only. Choose and pin its stable version when this phase begins.
2. Implement `NowPlayingWidget` and `NowPlayingWidgetReceiver`; do not add a new Gradle module.
3. Declare the provider for `home_screen` and `keyguard`, but adapt content using `OPTION_APPWIDGET_HOST_CATEGORY`. Keyguard hosting is device/OS dependent, so absence of a keyguard host is supported behavior.
4. Set `updatePeriodMillis` to `0`. Refresh from playback/snapshot events, widget actions, app foreground changes, and restoration. Do not schedule a once-per-second or once-per-minute worker.
5. Support three responsive size buckets:
   - compact: artwork plus play/pause;
   - medium: artwork, title/artist, previous, play/pause, next;
   - expanded: medium content plus album, elapsed/duration text, and a coarse progress bar.
6. Keep the visual language rectangular, monochrome, and border-led. The host controls outer widget shape/padding where required; do not fight mandatory system treatment.
7. Route previous, play/pause, and next through a short-lived `MediaController` connected to `PlaybackService`. Use immutable `PendingIntent` values where platform intents are required.
8. Provide a configuration setting for `Show artwork and metadata on lock screen`; default it to artwork plus transport controls, with title/artist hidden until the user opts in.
9. When nothing has played, show `RELAY / NO ACTIVE TRACK` and make the body open the app.

Widget gate:

- Home-screen widget renders in all three size buckets and survives app-process death.
- On a device/OS with a keyguard widget host, the same provider renders a privacy-safe lock-screen layout. On unsupported devices, the system media card remains fully functional.
- Widget actions control the existing queue and do not create a second player or activity.
- Track/artwork changes trigger one event-driven refresh; there is no continuous widget update loop.
- TalkBack identifies artwork, metadata, and every actionable control.

#### 4A.3 Album-art live wallpaper

1. Add `AlbumWallpaperService : WallpaperService` and return a new `Engine` for every `onCreateEngine()` call. Multiple engines can exist for preview and active wallpaper; never keep renderer state in a single global engine.
2. Declare the service with `android.permission.BIND_WALLPAPER`, the wallpaper service intent, and required XML metadata. Launch the system live-wallpaper preview/picker for activation; never try to silently replace the user's wallpaper.
3. Start with a Canvas renderer. Decode artwork off the main thread, downsample it to the actual surface size, and retain only the current and next required render assets.
4. Draw only when the surface is valid and one of these events occurs: surface size change, visibility change to visible, new artwork, preset change, configuration change, or an active animated element needs a frame.
5. Stop frame callbacks, controller observation, and expensive work immediately in `onVisibilityChanged(false)` and clean them up in `onSurfaceDestroyed`/`onDestroy`.
6. The first release is deliberately static between track transitions: fill/crop the current album cover, draw a configurable background for letterboxing, and redraw only when state changes. This supplies the useful feature before adding an animation loop.
7. If no artwork exists, render the Relay `Ink` background with a small `NO ARTWORK` label. If the cached file is missing/corrupt, discard it and fall back without crashing.
8. Support home and lock wallpaper flags where the platform and user's wallpaper picker allow them. Do not promise separate lock/home live wallpapers on every device.
9. Add a settings action to open preview, an enable/disable metadata privacy setting, a battery-saver option that disables animation, and a `Reset preset` action.

Wallpaper gate:

- Preview and active engines can run simultaneously without sharing mutable bitmap ownership or crashing.
- Switching tracks replaces the artwork without restarting playback or the wallpaper service.
- A hidden wallpaper schedules no render frames and holds no `MediaController` connection.
- Rotation, density changes, process recreation, missing art, and corrupted cache entries render a safe fallback.
- Static mode performs no continuous redraw loop.
- The wallpaper never reads a remote URL, opens a source stream, or creates an audio player.

#### 4A.4 Versioned customization preset

Use a data model in `commonMain` so the editor, exportable chart renderer, desktop wallpaper export, and Android live wallpaper can eventually share composition choices:

```kotlin
@Serializable
data class WallpaperPreset(
    val schemaVersion: Int = 1,
    val canvas: WallpaperCanvas = WallpaperCanvas(),
    val elements: List<WallpaperElement> = listOf(WallpaperElement.Artwork()),
    val filters: List<ArtworkFilter> = emptyList(),
)
```

Version 1 limits:

- Elements: `Artwork`, `Title`, `Artist`, `Album`, `Clock`, and `Progress`.
- Element properties: normalized `x`/`y`/width/height, anchor, opacity, visibility target (`HOME`, `LOCK`, `BOTH`), font token, alignment, and z-order from list order.
- Canvas properties: solid color, artwork-derived average color, fit/fill crop mode, and horizontal page offset behavior.
- Filters: `Grayscale`, `Blur`, `Duotone`, `BrightnessContrast`, `Vignette`, and `Grain`, each with clamped numeric parameters.
- Validate the element count, filter count, enum values, numeric ranges, and schema version before saving or importing.
- Unknown future elements/filters are ignored with a visible warning rather than crashing or corrupting the saved preset.

Render filters into a cached intermediate bitmap only when artwork or the preset changes. Do not recompute static filters every frame. A guarded Android 13+ `RuntimeShader`/AGSL path may be added later for animated filters, but every effect must have a static fallback and imported presets must never contain executable AGSL source.

The first editor may be a simple ordered property form with a live preview. Free-form drag/resize, downloadable preset packs, animated shaders, audio-reactive effects, and third-party theme integration remain later work.

#### 4A.5 Portability boundary

Keep `NowPlayingSnapshot`, `WallpaperPreset`, validation, preset migration, layout math, and static image composition in shared code where platform APIs do not leak in. Keep these implementations platform-specific:

- Android: Media3 system surfaces, Glance/`RemoteViews`, `WallpaperService`, `Canvas`, and optional AGSL.
- Apple platforms later: system Now Playing metadata/commands plus WidgetKit/Live Activities where appropriate. Implement them as native Swift adapters and widget-extension targets; do not try to run Android widget or wallpaper code through KMP.
- Desktop later: native media-session integration and optional static wallpaper export or a dedicated desktop background adapter. Reuse the preset schema, not the Android service.

Do not weaken the shared model to pretend these surfaces are identical across operating systems. Share playback facts and composition data; adapt lifecycle, interaction, privacy, and rendering to each host platform.

### Phase 5 — Metadata health, search, and repair

Goal: detect incomplete local metadata, offer reliable candidates and artwork, and apply user-controlled corrections consistently across library management, playback surfaces, and tracking.

#### 5.1 Effective metadata and health

Keep source metadata immutable and add a host-owned `MetadataOverride` keyed by `(sourceId, trackId)`. Resolve every displayed/used field through one function with this precedence:

1. user-confirmed override;
2. trusted source metadata;
3. embedded/MediaStore metadata;
4. display-only fallback.

Last.fm and tracker plugins may use user-confirmed overrides, but never display-only filename/`Unknown artist` fallbacks. Library sorting, grouping, filtering, album pages, queue metadata, notification/lock-screen metadata, backup, and search all consume the same effective metadata resolver.

Create conservative health reasons rather than one vague `bad` flag:

- missing or generic title;
- missing/unknown artist;
- missing album or album artist;
- missing/unreadable artwork;
- conflicting album fields within an otherwise matching album group;
- provider match exists but has not been reviewed.

A missing album is `INCOMPLETE`, not automatically wrong: singles, live recordings, demos, and standalone files are valid. Detection never rewrites data or starts network work by itself.

#### 5.1.1 Insight tags

When the first listening-insight and chart features consume them, add a host-owned, versioned `TrackTags` record keyed by `(sourceId, trackId)`. Keep it bounded and explicit: normalized genres, moods, instruments, release year/date, BPM, musical key, and named contributor roles. Each value records whether it came from the source, a metadata provider, or the user; user values take precedence and are never overwritten by refreshes.

Do not add an arbitrary key/value metadata map, infer mood from audio, or fetch tags solely to decorate a chart. Tags are added through the same metadata-review flow, can be edited or removed manually, and are included in backup/sync as user-owned metadata. Effective metadata and tags remain separate: a tag never changes the original title, artist, or album unless the user applies a normal metadata override.

#### 5.2 User experience

- Show one persistent library banner, `Some tracks need metadata`, with `REVIEW`; do not emit a toast/notification per track.
- Mark affected rows with a small semantic `METADATA` action only when it helps the user locate the problem.
- The review screen shows the original values beside the proposed values and artwork, with `APPLY`, `EDIT`, `SKIP`, and `NOT A PROBLEM`.
- Manual search is always available and pre-fills the best known title/artist/album. The user can change the query or paste a MusicBrainz recording/release URL or MBID.
- Automatic lookup is an opt-in metadata setting. It may fetch suggestions in the background, but the first release never auto-applies a candidate. Add high-confidence auto-apply only after real-world false-match data supports a safe threshold.
- Bulk review may group tracks by the same proposed release, but applying a group still shows the affected fields and track list.

#### 5.3 Provider and matching boundary

Add a small `MetadataProvider` contract only after the built-in provider works. The initial provider uses MusicBrainz recording/release search and the Cover Art Archive. Send an identifiable versioned User-Agent, serialize requests to respect MusicBrainz's current one-request-per-second IP guidance, honor `Retry-After`/503 responses, and cache successful and negative results.

Candidate scoring uses normalized title, artist credit, album/release, duration tolerance, track/disc number, and existing MusicBrainz IDs. A title-only query can generate suggestions but cannot be silently accepted. Deduplicate candidates by recording MBID plus release MBID; keep edition differences visible when artwork or track lists differ.

Provider output is untrusted. Bound text/image sizes, reject non-HTTPS artwork after redirects, validate MIME type and decoded dimensions, and attribute MusicBrainz/Cover Art Archive in the metadata screen.

#### 5.4 Redundancy and artwork cache

Before any provider request or image download, check in this order:

1. user override and previously selected MusicBrainz IDs;
2. readable embedded/MediaStore artwork and metadata;
3. the local lookup cache for the stable track key plus source modification revision;
4. an existing normalized-query result shared by matching tracks;
5. the artwork cache by release/release-group MBID and requested size.

Store lookup status, provider, query fingerprint, response time, source revision, selected IDs, and negative-cache expiry. Re-run lookup when the source revision changes or the user explicitly refreshes. Download one suitable thumbnail for lists and one bounded larger image only when a detail/export surface needs it. Never download the same MBID/size concurrently; failed or corrupt artwork is removed and may be retried after backoff.

Applying artwork stores a stable host cache reference in `MetadataOverride`. Do not put bitmap bytes in Room. Do not overwrite embedded tags or original audio files in this phase. A later explicit `WRITE TAGS TO FILE` workflow must use a backup/temp-file/atomic-replace strategy and remain separate from normal library repair.

Tests:

- effective metadata precedence is identical for library, Media3, and Last.fm mapping;
- unknown/display fallbacks are detected and never submitted to trackers;
- title-only and ambiguous matches require review;
- duplicate tracks/queries produce one provider request and one artwork download;
- source revision invalidates stale lookup results without deleting user overrides;
- missing, corrupt, oversized, wrong-MIME, and redirected artwork fail safely;
- `SKIP` and `NOT A PROBLEM` suppress repeated prompts until the source revision changes.

Phase gate:

- A track with missing artist/album/artwork produces one actionable review suggestion.
- Manual search can select and apply title, artist, album, album artist, track/disc number, MusicBrainz IDs, and cover art.
- Corrected metadata immediately updates library organization, queue, system media surfaces, and eligible Last.fm submissions.
- Reopening the app performs no redundant metadata/artwork request for unchanged tracks.
- Provider outage leaves existing metadata/artwork usable and shows a retry action.
- Original audio files remain unchanged.

### Phase 6 — First remote source (deferred)

Defer a built-in Subsonic/Navidrome source. Relay's core remains local-first; a remote library is introduced later as a validated `SOURCE` extension. Do not implement remote-source settings, credentials, browsing, streaming, downloads, or a second built-in source before the extension boundary is ready.

When a first remote source is selected later, revise `MusicSource` using the needs proven by both local and remote implementations:

- capability declaration
- authentication state
- browse/search
- pagination
- stream resolution and optional download URL

Keep server URL, username/account label, protocol version, and non-secret capabilities in host settings. Store authentication material in platform secure storage under the source instance ID. Require HTTPS by default; a user may explicitly allow a private-network HTTP server only after a warning, and that exception is never inherited by another source.

Remote item IDs remain stable and namespaced by source instance. Resolve short-lived stream URLs just before playback and never persist authenticated URLs in the queue, history, backups, logs, or plugin messages. Map remote metadata through the same effective metadata resolver used by local tracks, and route playback through the existing `PlaybackService` and queue rather than creating a source-specific player.

Do not build a custom synchronization server, offline downloader, or background mirror in this phase.

Deferred gate:

- A user can add, test, edit, disable, and remove one remote source extension without exposing its credential in logs or backups.
- Browsing, search, pagination, artwork, queue insertion, playback, seeking, next/previous, and Last.fm mapping work through the existing host contracts.
- Restarting Relay restores stable queue/library references and resolves a fresh stream URL when playback resumes.
- Server timeout, expired authentication, malformed responses, and missing artwork leave the local library and player usable and show one actionable error.
- Local and remote implementations pass the same source-contract test suite.

### Phase 7 — Desktop application

Add `desktopApp` only now. Validate macOS first on the available development machine, then package and verify Windows and Linux before calling the phase cross-platform complete.

- Reuse shared UI, models, Room database, Last.fm client, and source logic.
- Implement a `PlayerEngine` using a narrow native wrapper around miniaudio.
- Statically link a pinned miniaudio revision; its ABI is not stable between releases.
- Built-in formats are WAV, MP3, and FLAC. Add other codec libraries only when a real library requires them.
- Keep network fetching and buffering above miniaudio; feed it through callbacks.
- Package a trimmed JVM runtime with the Compose desktop distribution.
- Add native media-key/session integration per operating system without putting those APIs in common code.
- Put the native library in architecture-specific application resources, validate that it loads on startup, and surface a useful diagnostic if its architecture or ABI is wrong.
- Add a native desktop command palette and documented keyboard shortcuts only after the local player is stable. It searches existing commands (transport, queue, search, lyrics, theme, and settings); it is not a second navigation hierarchy and does not require an online account.

Do not introduce PortAudio; miniaudio already covers its useful role. Do not bridge SoundFlow/.NET into Kotlin.

Phase gate:

- The desktop app browses local libraries using the shared source contracts. Remote-library support arrives only after its source extension is installed.
- WAV, MP3, and FLAC play through one queue with pause, seek, skip, repeat, shuffle, and Last.fm event parity with Android.
- Native handles and callback buffers survive repeated track changes and close cleanly on application exit.
- A packaged macOS build launches without a developer JDK. Equivalent Windows and Linux package gates must pass on those operating systems before advertising them as supported.
- Unsupported codec, missing native library, audio-device loss, and remote interruption fail without crashing or corrupting the queue.

### Phase 8 — Extension packaging and repositories

Only begin after the built-in local source, Last.fm tracker, and MusicBrainz metadata provider have proven their host contracts. The first remote source is delivered through this extension boundary rather than as a core feature. Do not create a generic plugin framework before then.

#### 8.1 Stable plugin API

Extract only the source operations already proven by the built-in local source and first remote source into the separately versioned `SOURCE` API. Android source APKs use `relay-source-api`: manifest metadata declares a source API version and one or more source/factory classes, which Relay instantiates after catalog and APK-signer verification. A stable Java ABI prevents a source author's Kotlin compiler version from becoming part of the compatibility contract. Relay refuses to enable an incompatible or malformed entry with a specific reason. Last.fm and MusicBrainz remain host-owned integrations.

Use host-owned wire DTOs and map them into Relay domain models after validation. A source extension may request operations and return data; it cannot receive a database handle, `MediaSession`, `Player`, unrestricted filesystem path, host API secret, or another source's credential. Source stream references are opaque and short-lived.

Data-only `THEME_PACK` and `WALLPAPER_PRESET` packages use bounded JSON/assets and a separate schema version. A Theme Pack may select host-provided list/grid and player layouts, an artwork or packaged-asset background, and bounded host-rendered effects such as grain, vignette, grayscale, duotone, and blur. It never supplies Compose code, native libraries, scripts, remote fonts, or shader source; a fully free-form composition belongs to a Wallpaper Preset instead.

#### 8.2 Repositories and marketplace

Call the user-facing area `Extensions`; its tabs are `INSTALLED`, `AVAILABLE`, and `UPDATES`, with repository management under Settings. Relay ships with no third-party repository enabled. The user adds a versioned HTTPS repository descriptor, commonly from a GitHub raw/Pages URL, reviews its origin and signing-key fingerprint, and explicitly trusts it.

Each repository index is signed by the descriptor key and contains the catalog fields from section 2.1. Cache the last verified index with `ETag`/`Last-Modified` support so installed extensions remain manageable offline. Catalog refresh never installs or enables anything. Merge entries by `(repositoryId, pluginId)`; do not let a similarly named plugin from another repository replace an installed identity.

Installation and update flow:

1. Show source repository, author/source link, version, compatibility, declared capabilities/permissions, signer, and change in permissions.
2. Download to a private temporary location with a strict size limit.
3. Verify HTTPS result, catalog signature, artifact SHA-256, expected plugin identity, and platform signer before invoking the platform installer/launcher.
4. Require confirmation for first install, new permissions, repository-key change, plugin-signer change, or downgrade. Never approve these through an automatic update setting.
5. Enable only after Relay can load the declared source class and validate its API version and returned source identities. Retain the prior working version/state when an update cannot start.

Automatic catalog checks may be enabled, but the first release keeps artifact installation user-initiated. Removing a repository does not silently uninstall its plugins; it marks them orphaned and explains that they will no longer receive updates. Uninstalling a plugin keeps host-owned library references/settings inactive until the user chooses to remove them, so reinstall and backup restore can reconnect by stable ID.

Expose source health as host-owned state: last successful catalog/source refresh, current availability, last actionable failure, and an explicit retry. Do not silently fail over between different sources. A later failover policy is allowed only for a user-approved equivalent source instance with the same stable track identity and no shared credentials.

#### 8.3 Platform execution and lifecycle

- Android source plugins are separately installed APKs. Relay verifies the installed package certificate every time, reads explicit source metadata, then creates a child-first APK class loader whose parent owns Relay's source API. It instantiates only `RelaySource` or `RelaySourceFactory` entries and invokes their bounded browse/search calls on `Dispatchers.IO`. This matches Mihon’s source extension model. Since trusted source code executes in Relay's process, repositories and signer fingerprints are the security boundary: never load an APK that failed either verification, display the trust warning before installation, and retain a disabled reason on any loading failure. The system package installer owns install/uninstall confirmation.
- Desktop executable plugins are architecture-specific child processes using versioned JSON-RPC over stdin/stdout. Relay gives each process a private working/cache directory, a minimal environment, bounded messages, startup/request timeouts, cancellation, and forced termination after a failed graceful shutdown. Passing validation does not claim an operating-system security sandbox; document that limitation until a platform sandbox is implemented.
- iOS supports built-in/compiled source, tracker, and metadata implementations plus data-only packs. It does not download executable plugins. A later remote extension host must use the same untrusted network boundary and cannot bypass App Store/platform policy.

Disable a crashing, hanging, missing, signature-invalid, or API-incompatible plugin without blocking app startup or access to other sources. Playback already in progress may finish from a resolved host media item, but no new plugin calls occur after disable/uninstall.

#### 8.4 Source settings and authentication (deferred until needed)

Do not build source authentication forms before a real source needs them. When that happens, the source declares a bounded, schema-driven settings form using host-supported field types: text, number with range, boolean, single choice, action, and secret reference. Relay renders the form in its own design system, validates values, and stores non-secret values under `(sourceId, settingsSchemaVersion)`. Unknown field types are rejected rather than rendered as arbitrary UI.

Secret values live in platform secure storage scoped to source ID and account ID. Prefer system-browser OAuth/deep-link or device/code flows. A source cannot show an embedded login webview or read another account. The built-in Last.fm connection remains the reference tracking flow.

Repository descriptors, trusted fingerprints, installed source IDs, enablement, permissions, and non-secret source settings participate in Phase 4 backup/restore. Binaries, cached indexes/artifacts, and credentials do not. Restore first reports missing repositories/sources and reconnect-required accounts; it never fetches, installs, trusts, or logs in automatically.

Provide a small sample source extension plus a repository-index validator only after the contracts freeze. Samples contain no service-specific credentials. Do not create a public marketplace submission service in this phase; static user-added repositories are sufficient.

Tests:

- API negotiation accepts supported ranges and isolates unsupported or malformed plugins.
- Repository signature, artifact digest, package/process identity, signer continuity, URL scheme, redirect, size, and permission-change checks fail closed.
- Duplicate repositories/plugins, stale cached indexes, removed repositories, offline refresh, source class-load failures, timeout, cancellation, and oversize source pages have deterministic states.
- Source-settings migrations preserve supported values, discard invalid values visibly, and never expose secret material to backups or another source.
- Uninstall/reinstall and backup/restore reconnect host data only when repository ID, source ID, and trusted signer identity match.

Phase gate:

- A user can add a trusted GitHub-hosted test repository, browse compatible extensions, inspect permissions, install/update/disable/enable/uninstall a sample, and remove the repository with every trust transition visible.
- The sample source browses and plays through Relay's existing queue. Built-in Last.fm and MusicBrainz remain independently testable through their Settings submenus.
- A tampered index/artifact, silent signer change, incompatible API, maliciously large response, hung plugin, and crashed plugin are contained without corrupting host state or blocking startup/playback from other sources.
- Android proves trusted APK source loading and desktop proves child-process execution. Shared platform-compatibility tests mark executable catalog entries unsupported on iOS while leaving data-only packs importable; a native iOS UI gate waits for an actual iOS launcher phase.
- A backup round-trip restores repository/plugin configuration but installs no binary and restores no credential.

### Phase 9 — Feature expansion order

After the foundation is measured and stable, implement features in this order:

1. Remote source downloads and offline storage management.
2. Gapless verification, replay gain/loudness normalization, and crossfade.
3. Equalizer and audio presets.
4. Search across sources and local full-text indexing.
5. Local profile, listening insights, and exportable album charts.
6. Timed local lyrics and a focused karaoke view.
7. Wallpaper preset editor, bounded filters, and reusable chart composition elements.
8. Data-only custom themes distributed through the validated pack format, with a bounded live-preview editor.
9. Spectrum/waveform visualizations driven by real PCM data.
10. Audio-reactive wallpaper elements that consume the same bounded visualization data.
11. Deterministic normal/custom shuffle profiles.
12. Multi-device data, music, and synchronized-playback sessions with an explicit conflict model.

The Theme Pack editor previews only Relay's declared colors, typography tokens, list/grid/player layout choices, packaged or artwork-derived backgrounds, and bounded host-rendered effects. It never accepts CSS, Compose code, scripts, remote fonts, or shaders. A pack previews against representative Relay screens before it can be saved or imported.

Timed lyrics first support local `.lrc` content and manually saved timestamps. The karaoke view follows the current playback position, remains usable with plain lyrics, and never requires a lyric-provider account. Network lyric retrieval remains separately user initiated and cannot auto-publish or redistribute lyrics.

#### 9.1 Local profile, listening insights, and charts

Relay has one local profile per installation by default. It contains a display name, creation date, and optional Last.fm username association. A profile may import the user's Last.fm listening history only after the existing tracker authorization succeeds; it does not become a Last.fm login, copy Last.fm credentials, or require an online account. A user may instead enter/import listening events manually. Every event records its origin (`LOCAL`, `LASTFM_IMPORT`, `MANUAL`) and a stable track identity or its reviewed title/artist/album fingerprint.

The profile store deduplicates overlapping local scrobbles and Last.fm imports by normalized effective metadata plus a bounded timestamp window, retains origin/provenance, and never sends imported/manual events back to Last.fm automatically. The local playback history remains the primary source for Relay's own statistics. Removing a Last.fm association leaves local history intact and offers a separate choice to remove imported events.

Add a separate `INSIGHTS` view rather than putting counters into the main library. It contains time-range filters and focused views for total listening time, most-played tracks/albums/artists, listening calendar, genre/mood/instrument/tag breakdowns, trends, and a clear `DATA SOURCE` label. Empty, unknown, skipped, and manually corrected metadata must be represented honestly; do not manufacture precision from missing tags.

Album charts are user-created, reproducible views of a selected period, ranking metric, and filters (for example genre, mood, year, or a user tag). They may use already-cached or embedded artwork only; chart generation must not bulk-fetch metadata/artwork. Render an on-device share/export image using the existing bounded visual system, record its chart specification with the profile, and allow backup/sync of the specification without treating generated images as canonical data. A later embeddable chart may export a static image or self-contained document; it must not expose a private listening feed or require Relay hosting.

Tests cover profile/import deduplication, origin preservation, Last.fm disconnect behavior, correct time-range aggregation, unknown-tag buckets, chart-spec reproducibility, and no network call during chart rendering.

#### 9.2 Shuffle profiles

Implement shuffle only after queue persistence is reliable. A profile is selected before generating a queue; it never reorders tracks already playing without an explicit `RESHUFFLE` action.

- **Normal shuffle:** use an unbiased Fisher–Yates permutation, preserve the current item at the head of the remaining queue, and persist the generated order so resume/playback/service surfaces agree. Do not choose a random next item repeatedly; that creates bias and repeats.
- **Image-seeded shuffle:** the user chooses a local image. Relay hashes its original bytes plus an optional user salt, stores only the resulting seed/fingerprint in settings, and uses a small specified deterministic PRNG to generate the Fisher–Yates order. The image never leaves the device and the same library snapshot plus seed reproduces the same queue on another paired device.
- **Metadata shuffle:** profiles may order/group by normalized title, artist, release date, album artist, or cached album-art dominant hue, then shuffle inside each group with the persisted seed. `RAINBOW` orders art by hue around the color wheel; missing artwork/metadata goes in a final `UNKNOWN` group. The feature uses only data already held locally—it must not trigger metadata/artwork API calls.
- Profiles have a clear name, seed, ordered rules, and missing-value policy. They are versioned settings, included in backups, and sync as non-secret data. Selected images remain local unless the user explicitly includes them in a music/sync transfer.

Tests cover permutation/no duplicates, current-track preservation, fixed-seed reproducibility across platforms, unchanged queue without `RESHUFFLE`, stable metadata ordering, and missing-art handling.

#### 9.3 Local-first multi-device sync and synchronized playback

Relay syncs between explicitly paired personal devices. It has no hosted Relay account, cloud relay, third-party credential forwarding, or automatic internet exposure.

**Scope and identity**

- Sync user-owned records: local-profile details, listening events and their provenance, favorites, flags, playlists/entries, queue references, metadata overrides/tags, lyrics, settings (with device-local exclusions), extension repository/configuration records, and data-only theme/wallpaper/shuffle/chart profiles.
- Audio transfer is a separate explicit action. It transfers user-selected local files into the receiving device's configured `music/` folder; it never copies remote cache files, authenticated stream URLs, downloaded extension binaries, API keys, session keys, or platform-keystore material.
- File identity is a SHA-256 content digest plus size. Source URIs and MediaStore/document IDs are device-local references and are remapped only after a received file is indexed.

**Transports**

- **Wireless:** an opt-in local-network listener is active only during a visible sync session. Devices pair through a QR code or verified short code, pin each peer's public identity, and use authenticated encrypted transport. Discovery, pairing, transfer, and diagnostics must work with manually entered local addresses when multicast discovery is unavailable.
- **Wired:** create/read a versioned `.relaysync` bundle on user-selected USB/removable storage or a mounted device folder. This is the portable baseline across Android, desktop, and platforms that do not expose direct USB peer links.
- iOS sync is foreground/user-initiated unless platform background execution proves reliable. Android and desktop may offer a user-enabled local-network sync session; neither starts an unbounded background listener.

**Transfer, conflicts, and safety**

- Exchange a bounded manifest first, compare version vectors and content digests, then transfer only missing chunks with checksums, resumable temporary files, and atomic final moves. Verify a complete file before indexing it.
- Every mutable synced record has a stable UUID, modification timestamp/version, and deletion tombstone. Independent edits produce a visible conflict record rather than silent last-writer-wins data loss; playlists preserve both entry variants until the user resolves them. Playback position/queue remains device-local by default, with an explicit `SYNC QUEUE` option.
- Pairing records are revocable. Unpairing stops future sessions and deletes the peer credential, but never deletes the user's music or library records. Rate/size limits, explicit storage estimates, battery/network warnings, cancellation, and partial-transfer cleanup are required.
- A sync restore/import follows the same preflight, validation, rollback, and missing-plugin reporting rules as backup restore. It never enables, installs, trusts, or logs in an extension automatically.

**Synchronized playback sessions**

- A paired device may start or join an explicit, visible `PLAY TOGETHER` session. One device is the session leader and broadcasts only a logical track identity, queue position, target monotonic start time, paused/playing state, and bounded correction messages. It never relays audio, shares a stream URL, or forwards a source credential.
- The session screen always shows the leader, each joined/unavailable peer, the resolved track identity, measured drift, and the last correction. `RESYNC`, `PAUSE FOR EVERYONE`, leader transfer, and leave are explicit user actions; no device is silently moved between roles or seeks without a visible correction decision.
- Each participant independently resolves the same track: local files match by SHA-256 content digest; remote music matches only when the same installed source can resolve it for that device. A device without a compatible local copy/source stays in the session as unavailable instead of playing a different recording.
- Before starting, peers measure clock offset and network jitter with repeated local probes. Relay keeps a per-device output-delay calibration setting and schedules a future start. During playback it uses infrequent position checks and small bounded rate correction where a platform supports it; otherwise it performs a visible, user-approved resync seek. Never create a second player: every device uses its existing `PlayerEngine`/media session.
- Exact zero-delay playback cannot be guaranteed across independent speakers, Bluetooth stacks, network jitter, and audio hardware. The goal is a calibrated, perceptually aligned session with measured offset/error shown to the user; Relay reports when a peer cannot meet the selected tolerance rather than pretending it is synchronized.
- A session is foreground/user-initiated, expires when the leader leaves or pauses it, and has no background Internet listener. Leader change, seek, queue change, and disconnect are explicit actions. Music/data transfer remains separate from playback synchronization.

Sync gate:

- Android and desktop pair over a local network without an internet connection, verify peer identity, and merge non-conflicting records.
- A wired `.relaysync` bundle round-trip works between two clean installs.
- A selected audio transfer resumes after interruption, validates its digest, appears only after indexing, and plays through the existing queue.
- A synchronized-playback session starts the same local or independently resolved remote track on compatible Android/desktop peers, applies a calibrated offset, and reports measured drift/unavailable peers.
- Conflicting playlist/metadata edits are visible and resolvable; queue/credentials are not silently copied.
- Revoked peers, malformed/oversized manifests, altered chunks, expired pairing material, and unavailable local storage fail without corrupting either library.

Optional Phase 4A remains deferred and may be scheduled after Phase 4 without changing this order. Do not start it implicitly while implementing another phase.

Do not implement immersive audio, smart playlists, recommendations, yearly recaps, achievements, voice search, typo tolerance, or automatic mood recognition before the underlying library/history data exists.

## 8. Quality and safety rules

- Inspect all callers before changing a shared contract.
- Prefer existing platform capabilities over custom implementations.
- No dependency may be added without naming the feature that requires it.
- No secrets, API keys, keystores, signing files, local paths, or user media enter git.
- Network calls always have timeouts and execute off the UI thread.
- Check host-owned metadata, query, negative-result, and artwork caches before calling an external metadata API or downloading artwork.
- Treat repository catalogs, plugin messages, remote metadata, artwork, stream references, backup archives, and imported presets as untrusted bounded input.
- Never run downloaded plugin code in Relay's process, and never accept executable code in a theme or wallpaper pack.
- Never mutate original audio tags/files as a side effect of metadata repair; a future explicit write-to-file action requires recoverable replacement.
- Backup restore always completes validation and a restore plan before mutation, then applies database changes transactionally.
- Render settings only for implemented behavior and store secrets through platform secure storage, not the typed settings snapshot.
- Cursors, responses, controllers, players, sessions, and native handles are always released.
- Never block or allocate heavily in an audio callback.
- Accessibility is required: adequate contrast, 48dp targets, semantic labels, and usable system font scaling.
- Errors are visible and actionable; do not swallow exceptions into logs alone.
- A phase with non-trivial logic adds the smallest runnable test that would fail if that logic broke.
- Preserve unrelated user changes in the worktree.
- Do not commit or push unless explicitly requested.

## 9. Agent execution protocol

For a `gpt-5.6-terra` implementation agent:

1. Read this entire document and inspect the repository before editing.
2. Work only on the phase named in the task message.
3. Follow the fixed decisions and exact scope; do not implement later-phase features “while here.”
4. Keep optional Phase 4A deferred unless the task message explicitly selects it.
5. Reuse existing code and patterns before adding files or dependencies.
6. Use `apply_patch` for manual file edits.
7. Run the phase's checks. If the environment lacks a tool, report the exact missing prerequisite and still perform non-tool-dependent checks.
8. Inspect `git diff --check`, `git status --short`, and the final diff before reporting completion.
9. Do not alter `IMPLEMENTATION_PLAN.md` unless the task explicitly requests a plan correction.
10. Return a compact report containing:
   - files changed;
   - checks run and their results;
   - unresolved blocker, if any;
   - the next phase gate, without beginning that phase.

## 10. Definition of the first milestone

Milestone 1 is complete only when Phases 0–3 pass their gates on a real Android device or emulator:

- Local tagged music is indexed.
- Playback survives activity backgrounding.
- System controls work.
- Queue controls and seeking work.
- The UI matches the fixed functional design system.
- Last.fm authentication works.
- Eligible playback is scrobbled exactly once and survives a temporary network failure.

Everything else remains deferred until this vertical slice is reliable.
