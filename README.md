# Relay

Android-first music player built with Kotlin Multiplatform and Compose Multiplatform.

Relay plays local libraries and remote sources through signed extensions,
scrobbles to Last.fm, repairs metadata with MusicBrainz, renders timed lyrics,
syncs between your own devices, and stays a local-first listening utility.

Start with [the project guide](docs/PROJECT_GUIDE.md) for the module map, runtime flow, and safe places to make changes. The ordered implementation work is in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md). AI agents should read [AGENTS.md](AGENTS.md) first.

## Download

Releases are published on the GitHub Releases page as a signed APK with
SHA-256 checksums. Download the APK and open it on the device; the system
installer handles the rest.

For a locally built debug APK:

```sh
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

A desktop build for macOS exists (`./gradlew :desktopApp:packageDmg`) but is
not yet part of a stable release.

## Contributing

Contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) for setup,
test commands, and review expectations. Forking guidance lives in
[FORKING.md](FORKING.md).

## Donations

Relay is free software for noncommercial use. If you want to support
development, donations are accepted and appreciated — they never unlock
features. Everything in Relay is available to everyone on equal terms.
Donation link: <https://github.com/Livio21> (Sponsors page).

## License

Split licensing:

- **Source code** — [PolyForm Noncommercial 1.0.0](LICENSE). Free to use,
  study, modify, and share for any noncommercial purpose, including accepting
  donations for your own work. Commercial use requires explicit permission.
- **Documentation, theme packs, artwork** —
  [CC BY-NC-SA 4.0](LICENSE-DOCS) (credit required, noncommercial,
  share-alike).
- Third-party dependencies keep their own licenses; see
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Forks must keep the license notices intact. Paywalling features of Relay or
of a fork violates the license; donations-based support does not.

## Prerequisites

- JDK 17
- Android SDK Platform 36 and Android SDK Build-Tools

## Build

```sh
./gradlew tasks
./gradlew :composeApp:allTests
./gradlew :androidApp:assembleDebug
./gradlew :desktopApp:packageDmg
```

Install the debug APK with:

```sh
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

## Last.fm

Create a Last.fm API application, then add its credentials to the untracked `local.properties` file:

```properties
LASTFM_API_KEY=your_api_key
LASTFM_SHARED_SECRET=your_shared_secret
```

Use `CONNECT LAST.FM` in Relay, approve access in the browser, then select `FINISH CONNECTION` in the app. The session key is encrypted with Android Keystore; pending scrobbles stay in the local database until Last.fm accepts them.

## Privacy

Relay has no telemetry and no accounts. See [PRIVACY.md](PRIVACY.md) for
exactly what leaves the device and when.
