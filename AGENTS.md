# AGENTS.md — instructions for AI coding agents

Entry point for AI agents working on Relay. Humans start at README.md;
agents start here.

## Read order (before editing anything)

1. This file.
2. `docs/PROJECT_GUIDE.md` — module map, ownership table, change recipes.
3. `docs/HANDOFF.md` — current state, release track, conventions, backlog.
4. `IMPLEMENTATION_PLAN.md` — §2 fixed decisions, §8 quality rules,
   §9 agent execution protocol. **The active phase is Phase 10** (first
   public release); earlier phases are historical contract.
5. `docs/SOURCE_API.md` — only when touching extension/source code.

## Environment

```sh
export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home
./gradlew :composeApp:desktopTest :androidApp:testDebugUnitTest :androidApp:assembleDebug
```

`local.properties` supplies `LASTFM_API_KEY` / `LASTFM_SHARED_SECRET`
(untracked). Without them the app still builds; Last.fm connect shows an
error state.

## Hard conventions (violations get reverted)

- `composeApp` common code stays platform-neutral: no Android imports, no
  Room types, no network calls in composables.
- New UI state goes into `RelayAppState`; new intents go into
  `RelayAppActions` (give defaults so desktop keeps compiling). Platform
  entry points fulfil intents.
- No DI framework. Constructor parameters only.
- Every Room entity change needs: schema migration + DB version bump +
  matching fields in `RelayBackupArchive.kt` so old backups keep restoring.
- Never persist stream URLs, credentials, or secrets anywhere (DB, logs,
  backups, plugin messages).
- Treat catalogs, extension responses, metadata, artwork, backup archives,
  imported presets as untrusted bounded input; validate before use.
- Do not add dependencies without naming the feature that requires them.
- Design system: rectangular corners, 1dp `RelayColors.Line` borders,
  `RelayType` styles, text-button `TransportAction`, ≥48dp touch targets,
  semantic descriptions on everything actionable. No gradients/shadows/
  rounded corners.
- Non-trivial logic gets one small test (`composeApp/src/commonTest` or
  `androidApp/src/test`; real `org.json` is already a test dep).
- Run the smallest relevant Gradle check after each change. Never claim
  Android-device or iOS verification without actually running it there.

## Process rules

- Do not edit `IMPLEMENTATION_PLAN.md` unless your task explicitly authorizes
  a plan correction. Record progress in `docs/HANDOFF.md` instead.
- Never commit or push unless explicitly asked. Never commit secrets,
  keystores, `local.properties`, signing files, or user media paths.
- Preserve unrelated worktree changes.
- Report compactly: files changed, checks run + results, blockers, next gate.
