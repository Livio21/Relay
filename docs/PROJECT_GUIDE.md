# Relay project guide

Read this file before changing code. It explains which layer owns a feature and which files form the main paths through the app.

## Map

```text
androidApp/MainActivity or desktopApp/Main
                 |
                 v
        RelayAppState + RelayAppActions
                 |
                 v
      composeApp/RelayApp + shared UI
                 |
                 v
        platform callback / PlayerEngine
                 |
                 +-- Android: Media3 service, Room, Android APIs
                 +-- Desktop: miniaudio native bridge and local files
```

`composeApp` must stay platform-neutral. Android and desktop code assemble a `RelayAppState`, receive user intents through `RelayAppActions`, then use their own services and storage to fulfil them.

## Directories

| Location | Owns |
| --- | --- |
| `composeApp/src/commonMain` | Shared UI, models, playback/source contracts, extension protocol and repository validation. Start at `RelayApp.kt`; `App.kt` is the navigation shell and each screen lives in its own file (`PlayerScreens.kt`, `LibraryScreens.kt`, `SettingsScreens.kt`, `MetadataScreens.kt`, `PlaylistScreens.kt`, `ExtensionsScreens.kt`, shared pieces in `UiKit.kt`). |
| `composeApp/src/commonMain/.../update` | Host-owned update matching for app, extensions, theme packs, and wallpaper presets. It classifies compatible upgrades, unknown version changes, downgrades, and incompatible candidates; callers still require user confirmation before installation. |
| `androidApp/src/main/kotlin/dev/relay/music/MainActivity.kt` | Android application coordinator: connects UI actions to services, storage, metadata, lyrics, and repository refresh. |
| `androidApp/.../playback` | Media3 playback service and Android `PlayerEngine`. |
| `androidApp/.../library` | Local SAF folder scanning, Room user-library database, backups, automatic backup worker, and Relay storage directories. |
| `androidApp/.../metadata`, `lyrics`, `lastfm` | Network integrations and Last.fm persistence/tracking. |
| `androidApp/.../extension` | Android-only signed catalog fetching, verification, APK source loading, and `ExtensionSourceCoordinator` (search/browse aggregation plus just-in-time stream/download resolution). Source authors: see `docs/SOURCE_API.md`. |
| `desktopApp/src/main/kotlin` | macOS desktop launcher, `DesktopPlayerEngine`, and native-library loading. |
| `desktopApp/src/main/cpp` | Narrow miniaudio/JNI bridge. Keep audio-callback work small and allocation-free. |
| `relay-extension-template` | Standalone desktop source-extension starter repository and static catalog/signing template. |
| `IMPLEMENTATION_PLAN.md` | Ordered work and phase gates. |

## Common change recipes

### Add a playback behavior

1. Add the cross-platform behavior to `composeApp/.../playback/PlayerEngine.kt` only when both platforms need it.
2. Implement it in `AndroidPlayerEngine` and `DesktopPlayerEngine`.
3. Add the UI intent to `RelayAppActions` and render it in the shared UI.
4. Keep Android media-session work in `PlaybackService`.

### Add or change a screen

1. Keep visual code in `composeApp`.
2. Add any rendered data to `RelayAppState`.
3. Add user intents to `RelayAppActions`.
4. Fulfil those intents in each platform entry point. Do not place Android APIs, Room calls, or network calls in a composable.

### Change library data or backups

1. Update Room entities/DAO/migrations in `androidApp/.../library/UserLibraryStorage.kt`.
2. Update `RelayBackupArchive.kt` if the record belongs in a backup.
3. Preserve the existing restore rule: validate first, then replace transactionally; never restore storage permissions or secrets.

### Add metadata or lyrics behavior

- Shared value types: `composeApp/.../model/Metadata.kt` and `Track.kt`.
- Android providers and caching: `androidApp/.../metadata` and `lyrics`.
- Metadata must be review-only until the user saves an override. Never edit original audio files as a side effect.

### Add an extension capability

1. Define only host-owned wire DTOs in `composeApp/.../extension/ExtensionProtocol.kt`.
2. Validate repository/catalog fields in `ExtensionRepository.kt`.
3. Android sources use the trusted Mihon-style `relay-source-api` class-loading boundary; desktop plugins remain child processes.
4. Use `relay-android-extension-template` to create a source extension; do not expose database handles, storage paths, secrets, or Media3 objects.

### Add an updatable component

1. Keep the installed version and catalog candidate in the platform-owned store.
2. Convert them to `InstalledComponent` and `AvailableComponent` with a stable `ComponentIdentity`.
3. Use `findComponentUpdates`; only `isActionable` candidates may show an update action.
4. Keep downloading, signature verification, and user confirmation in the platform installer. The shared updater never executes an update itself.

## Build and checks

Use JDK 17. On this Mac, Corretto is at:

```sh
export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home
```

```sh
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:desktopTest
./gradlew :desktopApp:packageDmg
```

`./gradlew :composeApp:allTests` also tries the iOS simulator target and requires Xcode command-line tools. Use `:composeApp:desktopTest` when Xcode is not configured.

## Current platform status

- Android is the primary working app.
- macOS has a packaged desktop build and local WAV/MP3/FLAC player path.
- iOS targets exist in shared code, but there is no iOS app launcher yet.
- Remote music sources remain extensions, not a built-in core source.

## Rules that prevent regressions

- Add a migration whenever a Room entity changes.
- Keep secrets in platform secure storage and out of backups/source control.
- Treat catalogs, extension messages, metadata, artwork, and backup archives as untrusted input.
- Run the smallest relevant check after a change; do not claim Android device or iOS verification without actually running it.
