# Contributing to Relay

Thank you for helping make Relay better. Contributions are welcome from
anyone — human or AI-assisted — under the project's licenses: code reaches
this repository under PolyForm Noncommercial 1.0.0, documentation and other
creative assets under CC BY-NC-SA 4.0. By submitting a contribution you
agree to license it that way.

## Setup

Prerequisites: JDK 17, Android SDK Platform 36 + Build-Tools.

```sh
git clone <your-fork-url>
cd relay
export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home # adjust per machine
./gradlew :composeApp:desktopTest :androidApp:testDebugUnitTest :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Optional: add `LASTFM_API_KEY` / `LASTFM_SHARED_SECRET` to an untracked
`local.properties` to test Last.fm connect/scrobbling.

## Before you change code

Read `docs/PROJECT_GUIDE.md` (which layer owns what) and `AGENTS.md`
(conventions both humans and AI agents must follow). Key points:

- Shared UI lives in `composeApp` and stays platform-neutral; Android and
  desktop fulfil `RelayAppActions` intents.
- Room entity changes require a migration, a DB version bump, and matching
  backup-archive fields.
- No new dependency without naming the feature that needs it.
- Match the existing rectangular, border-led design system; keep touch
  targets ≥48dp and semantic labels present.

## Tests and checks

Run the smallest relevant check while iterating:

| Change | Check |
| --- | --- |
| Shared logic | `./gradlew :composeApp:desktopTest` |
| Android unit-testable logic | `./gradlew :androidApp:testDebugUnitTest` |
| Anything Android-side | `./gradlew :androidApp:assembleDebug` |
| Desktop engine/UI compile | `./gradlew :desktopApp:assembleDist` |

Non-trivial logic ships with one small test. Device-dependent behavior
(playback transitions, widgets, wallpaper, sync) needs a note describing
what was verified on hardware — do not claim device verification you did
not perform.

## Submitting

1. Keep pull requests focused; behavior-preserving refactors stay separate
   from behavior changes.
2. PR checklist:
   - [ ] tests pass locally (list what you ran);
   - [ ] Room changes include migration + version bump + backup fields;
   - [ ] no secrets, stream URLs, or user data persisted/logged;
   - [ ] UI follows the design system and accessibility minimums;
   - [ ] `CHANGELOG.md` updated under *Unreleased* when user-visible.
3. Describe what changed, why, and how you verified it.

## Bug reports

Include: device/Android version, Relay version, steps to reproduce, expected
vs actual behavior, and (when relevant) the exported diagnostics log from
Settings → Advanced. Never attach copyrighted media files.

## Security

Report vulnerabilities privately via GitHub security advisories for this
repository rather than public issues. Extension/catalog trust-boundary bugs
are especially welcome findings.
