# Handoff — next work items

Read `docs/PROJECT_GUIDE.md` first, then this. This file is the execution brief for the next agent session. Work top-down; each task is independently shippable. Polish functionality first — visual design comes later.

## State as of 2026-07-25

Everything below is DONE, building, tested, and installed on the test device (CPH2649):

- **Source API v2** (`relay-source-api`, VERSION=2): paged `search(query, page)`, `getListings()`/`browse()`, lazy `resolveStreamUrl()`, media headers, schema-driven source settings. Extensions **extend `BaseRelaySource`** — the interface has NO default methods (desugared `$-CC` companions don't resolve across the APK boundary; never add interface defaults to the ABI).
- Extension UX: SOURCES/INSTALLED/AVAILABLE/UPDATES tabs, per-source browse with listing chips + LOAD MORE, one-tap updates, source settings form, INCOMPATIBLE state handling.
- App decomposed: `App.kt` is the nav shell; screens live in `PlayerScreens/LibraryScreens/SettingsScreens/MetadataScreens/PlaylistScreens/QueueScreens/ExtensionsScreens/UiKit.kt`. Android side has `ExtensionSourceCoordinator` and `MetadataRepairCoordinator`.
- Playlists at parity: detail screen, play/reorder/remove/rename/delete, cross-source entries with display snapshots (never stream URLs), universal ADD TO PLAYLIST picker, create+add in one step.
- Shuffle profiles (`playback/Shuffle.kt`): Fisher–Yates, image-seeded, grouped by ARTIST/ALBUM/TITLE. Host-side only; ExoPlayer shuffle stays off.
- Theme engine: `RelayColors` is snapshot-state; `applyThemePack()` recolors live. Import/apply/remove in Settings → THEME PACKS.
- Navigation: back stack only holds sub-screens (swipes replace); playlist detail, the queue, and the lyrics overlay are sub-screens; OS back = close sub-screens then confirm-exit. **Anything that takes over or covers the screen must live in `RelayDestination`, never in local `remember` state, or back cannot reach it.**
- **Lazy stream resolution** (`extension/ExtensionStreamUri.kt` + `extension/ExtensionStreamResolver.kt`): persisted extension tracks carry a `relay-extension://<extId>/<srcId>/<trackId>` placeholder; `PlaybackService`'s `ResolvingDataSource` rewrites it to a fresh URL + source headers just before load. Playlists queue every track — nothing is skipped for being remote. Resolver LRU is cleared whenever installed extensions or source settings change.
- **Queue screen** (`QueueScreens.kt`): reachable via QUEUE in the expanded player; play-from-index, reorder, remove, clear; PLAY NEXT / QUEUE actions on library rows and extension result rows. Pure edit helpers in `playback/QueueEdits.kt`.
- **Library search & sort** (`LibraryView.kt`): case-insensitive search over title/artist/album, sort TITLE/ARTIST/ALBUM with blanks last, pinned first, hidden excluded.
- **Offline downloads are real** (plan §9 item 1): a downloaded track now plays from storage instead of streaming. `ExtensionStreamResolver` checks `offline_downloads` before touching an extension, and the host swaps in the local URI wherever a queue is built. Settings → STORAGE lists downloads with sizes, per-item DELETE, and DELETE ALL (removes the file through SAF and the row).
- **`RelayAppContent` takes `(state, actions, modifier)`** — the 90-line manual forwarding layer in `RelayApp` is gone and the composable's 88 parameters with it. Adding a feature now touches the state/actions field and its usage, not four signatures.
- **Restored queues keep remote tracks**: `queue_entries` carries the same display snapshot as playlist entries, and `restoreQueueIfAvailable` rebuilds anything missing from the local library as a placeholder track (or its offline copy). Killing the app mid-playlist no longer drops the remote half of the queue.
- **Loudness normalization** (plan §9 item 2, partial): `PlaybackService` reads ReplayGain tags from ID3/Vorbis frames via `onMetadata` and composes fade × gain into one player volume (`applyVolume()` — never set `player.volume` directly again). Attenuates only, never boosts, so nothing clips. Setting lives under Settings → AUDIO → LOUDNESS, default off. Pure logic + tests in `playback/ReplayGain.kt`.
- **Gapless**: verified as working by default — `setQueue` hands ExoPlayer one `setMediaItems` list and nothing in the path defeats it. The only thing that does is a configured fade, which the fade settings now say out loud.
- **Insights view** (plan §9 item 5, partial): sixth pager page showing plays, estimated listening time, and top tracks/artists/albums over 7 days / 30 days / year / all time. `listening_history` rows now carry a display snapshot; older rows are resolved against the library at read time, and anything still unattributable is shown honestly as `Unknown` rather than dropped. Aggregation is pure (`model/Insights.kt`) and tested. Listening time is an estimate — Relay records when a track started, not how long it was heard — and the UI says so.
- **Partial downloads** can no longer masquerade as music: the scanner skips `.part` files, a startup sweep deletes ones left by interrupted transfers, and starting a download clears stale partials for that track.
- **Timed lyrics / karaoke view** (plan §9 item 6, partial): lyrics render as an overlay on the artwork behind a dark gradient — current line bright, next line muted — and follow playback. Plain lyrics still render as plain text; nothing is guessed. Parser and cue lookup are pure (`model/Lyrics.kt`) and tested against the formats real files use (repeated timestamps per line, 1–3 digit fractions, `[offset:]`). No schema change — LRC is stored in the existing `track_lyrics.content`.
- **Landscape is a Cover Flow browser** (`CoverFlowScreens.kt`): rotating the Now Playing page gives full-bleed album covers turning in perspective with mirrored reflections, plus a transport row — no page heading, no docked mini player. Tap a cover to play it. Orientation is derived from `BoxWithConstraints`, so it stays platform-neutral.
- **The player engine adopts the media session's queue** when the activity is recreated (rotation, process restart while the service lives). Media items carry `relay.sourceId`/`relay.trackId` extras so the queue rebuilds exactly instead of showing an empty player.
- **Repository import accepts shorthand**: Settings → SOURCE REPOSITORIES takes `owner/repo` or a GitHub page URL and expands it to the raw `repository.json` (branch honoured from `/tree/<branch>`), with a PASTE button for the clipboard and the resolved URL shown before import. Expansion is a pure tested function (`extension/RepositoryShorthand.kt`); insecure or malformed input is rejected rather than upgraded, and the existing descriptor + signing-key review still gates trust.
- Room DB at version 22. Migrations 15→22 added playlist snapshots, shuffle profile, theme packs, source settings, download titles, queue snapshots, loudness normalization, history snapshots.
- Publishing pipeline (user-approved, repo `github.com/Livio21/relay-extensions`): build APK → sha256+size into `index.json` → `scripts/sign-index.sh` → commit+push → `gh release create <tag> app-debug.apk`. Catalog currently serves FMA 0.2.2 and ccMixter 0.1.1 (`relay-extensions/ccmixter-source`). ccMixter's file host 403s without a `Referer` from its own site, so the source declares one via `getMediaRequestHeaders()` — the API returning JSON proves nothing about the media, so always fetch a real file URL before publishing (see `EXTENSION_AUTHORING.md` §1.4).

Build/verify (JDK: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home`):

```sh
./gradlew :composeApp:desktopTest :androidApp:testDebugUnitTest :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

## Conventions (do not break)

- No DI framework, constructor params only. No new dependencies without naming the feature that requires them.
- Shared UI is platform-neutral; Android/desktop fulfil `RelayAppActions` intents. New UI state goes in `RelayAppState`, new intents in `RelayAppActions` (with defaults so desktop keeps compiling).
- Every Room entity change needs a migration + DB version bump + backup archive read/write fields (`RelayBackupArchive.kt` — old backups must keep restoring).
- Never persist stream URLs, credentials, or secrets. Extension responses are untrusted bounded input.
- Non-trivial logic gets one small test (`composeApp/src/commonTest` or `androidApp/src/test`; real `org.json` is already a test dep).
- Design system: rectangular, 1dp `RelayColors.Line` borders, `RelayType` styles, text-button `TransportAction`, 48dp touch targets, semantic descriptions on everything actionable.

## Where this sits in IMPLEMENTATION_PLAN.md

Phases 0–8 are done. Phase 9's ordered feature list stands at: **1 downloads/offline ✅**, 2 gapless/replay-gain/crossfade (only fade in/out exists), 3 equalizer ✅, 4 search ✅ (library + cross-source; no full-text index yet — not needed at this size), 5 profile/insights/charts ◐ (insights done; local profile, Last.fm history import, listening calendar and exportable charts not started), 6 timed lyrics + karaoke ◐ (LRC playback sync done; no timestamp-capture editor, and network lyric sources still return plain text only), 7 wallpaper preset editor ✗, 8 data-only themes ✅, 9 spectrum/waveform ✗, 10 audio-reactive wallpaper ✗, 11 shuffle profiles ✅, 12 multi-device sync ✗. Phase 4A (widgets/live wallpaper) remains optional/deferred; the Glance widget exists minimally, the wallpaper service does not.

Do **not** edit IMPLEMENTATION_PLAN.md (agent protocol §9) — record progress here instead.

## Task 1 — Crossfade (the remaining half of plan §9 item 2)

**Not started, and deliberately not faked.** True crossfade overlaps the tail of one track with the head of the next, which a single `ExoPlayer` instance cannot do — its items play sequentially. Relay's existing FADE IN / FADE OUT settings fade to silence and back, which is a dip, not a crossfade; do not relabel them as one.

The real implementation needs two players:

1. Two `ExoPlayer` instances in `PlaybackService`, one active and one staged, each with the same `ResolvingDataSource` factory.
2. Schedule the staged player to prepare and start `crossfadeMs` before the active track ends (use the existing 50 ms fade ticker), ramping the outgoing player's volume down and the incoming one's up. Both must respect `applyVolume()`'s fade × ReplayGain composition.
3. `MediaSession` must keep pointing at whichever player is authoritative, and `LastFmTracker`/`writeNowPlayingSnapshot` currently assume one player — both take `Player` arguments already, but the swap point needs care so a scrobble is not double-counted or dropped at the boundary.
4. Gate behind a `crossfadeMs` setting, default 0 (off), and state in the UI that crossfade and gapless are mutually exclusive.

Budget this as a real feature, not a tweak. If it is not worth two players, say so and close the item.

## Task 2 — Theme effect rendering

`ThemePack` already carries validated `GRAIN / VIGNETTE / GRAYSCALE / DUOTONE / BLUR` with clamped strengths, and the settings screen lists them as "rendered in a later phase". Render them with a bounded Compose `Modifier`/Canvas overlay applied at the app root (`RelayTheme`), recomputed only when the pack or artwork changes — never per frame. Keep it data-only; no shaders from packs (plan §4A.4 rules apply, and the same composition will later back wallpaper presets).

## Task 3 — Rest of plan §9 item 5: profile, Last.fm import, calendar, charts

The INSIGHTS view exists (time ranges, totals, top tracks/artists/albums, `DATA SOURCE` label, honest unknown buckets). Still missing from plan §9.1:

1. **Local profile** — display name, creation date, optional Last.fm username association. Currently there is no profile record at all; `ListeningEvent` has no `origin` field yet.
2. **Provenance** — add `LOCAL` / `LASTFM_IMPORT` / `MANUAL` to each event before importing anything, plus the deduplication rule (normalized effective metadata within a bounded timestamp window). These are load-bearing for import correctness; read §9.1 before starting.
3. **Last.fm history import** — only after the tracker authorization exists (it does). Never send imported events back to Last.fm.
4. **Listening calendar** and **reproducible chart specs** with on-device share/export. Charts must use cached or embedded artwork only — chart generation must not bulk-fetch.

## Backlog after these (rough value order)

- Album/artist browse entities in the source API — additive `BaseRelaySource` methods + host screens; batch ABI churn into one version bump.
- RAINBOW shuffle — needs dominant-color extraction over cached artwork (shares work with theme effects).
- Extension install flow extraction from `MainActivity` (~220 lines welded to activity launchers — careful pass).
- Dead code: the `NowPlaying` composable in `PlayerScreens.kt` is never invoked — delete it.
- Optional follow-up to the state/actions flattening: group the still-flat `RelayAppState`/`RelayAppActions` fields into nested `LibraryUi`/`PlaybackUi`/`ExtensionUi`-style holders and hand each screen its own group. Lower value than the forwarding-layer removal that already landed — do it only if the flat lists start causing mistakes.
- Media3-native queue edits (`moveMediaItem`/`removeMediaItem`) instead of the whole-queue rebuild in `MainActivity.applyQueueEdit` — only if the re-prepare becomes noticeable.
- Download storage limits/cleanup scheduling (plan §4.1 "Storage and offline") — the manual list and DELETE ALL exist; automatic limits do not.
- Downloads for playlist tracks whose source is uninstalled still fail with a clear message; consider offering "install extension" from that toast.
- Desktop parity: desktop JSON-RPC extensions only handshake; desktop has no extension UI.
- Phase 4A (widget exists minimally; live wallpaper not started) — see IMPLEMENTATION_PLAN.md.

## Device/test notes

- Phone: CPH2649 over adb; `adb install -r`, then test flows manually. FMA extension 0.2.2 installed via the live catalog.
- Manual smoke test for the lazy-resolution path: add an FMA track to a playlist → force-stop Relay → reopen → play the playlist. It must stream without re-searching (the placeholder resolves in the service).
- If a new FMA release is needed: bump version in `relay-extensions`, follow the publish pipeline above (tag `fma-vX.Y.Z`). Publishing to GitHub requires the user's OK per action.
- Last.fm needs `LASTFM_API_KEY`/`LASTFM_SHARED_SECRET` in untracked `local.properties`.
