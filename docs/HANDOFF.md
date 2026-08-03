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
- **Timed lyrics / karaoke view** (plan §9 item 6): lyrics render as an overlay on the artwork behind a dark gradient — current line bright, next line muted — and follow playback. Plain lyrics still render as plain text; nothing is guessed. Parser and cue lookup are pure (`model/Lyrics.kt`) and tested against the formats real files use (repeated timestamps per line, 1–3 digit fractions, `[offset:]`). The local editor can also insert the live playback timestamp in valid LRC format. No schema change — LRC is stored in the existing `track_lyrics.content`.
- **Landscape is a Cover Flow browser** (`CoverFlowScreens.kt`): rotating the Now Playing page gives full-bleed album covers turning in perspective with mirrored reflections, plus a transport row — no page heading, no docked mini player. Tap a cover to play it. Orientation is derived from `BoxWithConstraints`, so it stays platform-neutral.
- **The player engine adopts the media session's queue** when the activity is recreated (rotation, process restart while the service lives). Media items carry `relay.sourceId`/`relay.trackId` extras so the queue rebuilds exactly instead of showing an empty player.
- **Repository import accepts shorthand**: Settings → SOURCE REPOSITORIES takes `owner/repo` or a GitHub page URL and expands it to the raw `repository.json` (branch honoured from `/tree/<branch>`), with a PASTE button for the clipboard and the resolved URL shown before import. Expansion is a pure tested function (`extension/RepositoryShorthand.kt`); insecure or malformed input is rejected rather than upgraded, and the existing descriptor + signing-key review still gates trust.
- Room DB is at version 30. Migrations also cover local profiles/history provenance, chart specifications, wallpaper choices, and the existing persisted playback state.
- **Album-art live wallpaper** (Phase 4A.3) is build-verified: each `WallpaperService.Engine` owns its own bitmap and coroutine scope; it reads only Relay's validated `cacheDir/relay-artwork` file URIs from the persisted now-playing snapshot. Settings → WALLPAPER opens Android's system preview/picker; Relay never changes wallpaper silently. The preset persists artwork fit, background, title visibility/position/size, ambient-blur/reflection effect, strength, and bars/wave visualization. Sound reactivity is opt-in and requests `RECORD_AUDIO`; `PlaybackService` extracts only low-resolution FFT bands from Relay's own audio session, broadcasts them at most 30 Hz while the wallpaper is visible, and does not store/export audio. Visualizers use Canvas so they render consistently across wallpaper preview and active surfaces. Device verification remains: permission allow/deny, preview + active wallpaper, local embedded/cached art, no-art fallback, title privacy, track switch, effect/visualizer rendering, and app-process recreation.
- Publishing pipeline (user-approved, repo `github.com/Livio21/relay-extensions`): build APK → sha256+size into `index.json` → `scripts/sign-index.sh` → commit+push → `gh release create <tag> app-debug.apk`. Catalog currently serves FMA 0.2.2 and ccMixter 0.1.1 (`relay-extensions/ccmixter-source`). ccMixter's file host 403s without a `Referer` from its own site, so the source declares one via `getMediaRequestHeaders()` — the API returning JSON proves nothing about the media, so always fetch a real file URL before publishing (see `EXTENSION_AUTHORING.md` §1.4).
- Catalog entries may declare an optional HTTPS `supportUrl`. Relay validates it from the signed catalog and renders a host-owned SUPPORT button in extension details; add it to `index.json` and re-sign before publishing.

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

Phases 0–8 are done. Phase 9's ordered feature list stands at: **1 downloads/offline ✅**, 2 gapless/replay-gain/crossfade ◐ (Media3 gapless, ReplayGain and bounded Android crossfade are implemented; the crossfade still needs a physical-device transition check), 3 equalizer ✅, 4 search ✅ (library + cross-source; no full-text index yet — not needed at this size), 5 profile/insights/charts ◐ (profile/history import/calendar done; chart export UI remains), 6 timed lyrics + karaoke ◐ (LRC playback sync done; no timestamp-capture editor, and network lyric sources still return plain text only), 7 wallpaper preset editor ◐ (artwork/title/effect/visualizer controls persist; device visual checks remain), 8 data-only themes ✅, 9 spectrum/waveform ◐ (bars/wave wallpaper renderer only), 10 audio-reactive wallpaper ◐ (Android-only, permission-gated), 11 shuffle profiles ✅, 12 multi-device sync ✗. Phase 4A is in progress: the Glance widget exists and the live-wallpaper renderer/picker is ready for device verification.

**Wired sync baseline** (Phase 9.3) is implemented and build-verified. Settings → DEVICE SYNC creates a checksummed, versioned `.relaysync` file in `Relay/sync/` (or a user-selected destination) and explicitly imports one. The bundle omits queue, device settings, music, stream URLs, credentials, APKs, and artwork caches. Import is transactional and non-destructive: favorites/history/charts are added, exact history duplicates are skipped, playlist name/content collisions become separately preserved `(... SYNC)` playlists, and conflicting flags/metadata/profile values are durable, visible records. For new conflicts, the user can explicitly keep local or apply the validated received flags, metadata, profile, or chart value; old notice-only records remain dismiss-only. Physical two-install round-trip remains required.

**LAN data, selected music, and play-together foundation** are now exposed in Settings → DEVICE SYNC and build-verified on Android. A visible host/join session uses an Android Keystore RSA identity, pinned peer public keys, a user-comparable pairing code, RSA-OAEP session-key wrapping, and sequenced AES-GCM frames. The same secured socket carries the bounded `.relaysync` exchange and an optional selected-music archive. Music transfer has a SHA-256 archive identity, resumes a matching cache partial, displays its prepared size, and still verifies every file before Relay indexes it. Waiting/active sessions have explicit cancel controls; saved peers are listed and can be unpaired; battery-saver and metered-network notices are visible. `PLAY TOGETHER` is foreground-only: the host sends logical local-track identity plus a SHA-256 content digest, timing and pause state; guests match their own local file, apply a clock probe, show drift, and require an explicit `RESYNC` for a visible seek. It never sends audio, stream URLs, source credentials, or extension binaries. The host closes the session after pausing or leaving. Automated tests cover the crypto tamper path, secure loopback pairing/frame exchange, a data-plus-selected-music loopback exchange, and a logical play-together command; physical two-device transfer/pairing/drift checks and desktop parity are still required before Phase 9.3 can pass its gate.

`composeApp/sync/RelayLanProtocol.kt` owns the versioned LAN protocol names and AES-GCM frame context. Android now consumes it, and the desktop adapter must consume it rather than copy protocol strings/AAD construction. The existing desktop player (`desktopApp`) has a native local-audio engine, but no sync identity store, transport, or UI wiring yet.

Do **not** edit IMPLEMENTATION_PLAN.md (agent protocol §9) — record progress here instead.

## Task 1 — Crossfade (the remaining half of plan §9 item 2)

**Implemented, pending physical-device verification.** `PlaybackService` retains one authoritative `MediaSession` player. Only during the final one-second lead window does it create a non-session `ExoPlayer` for the next queue item; it releases that preloader on handoff, skip, pause, errors and service shutdown. The 50 ms existing fade ticker ramps both players during the overlap, while the authoritative player remains the source of notification, queue, scrobble and now-playing state.

The user-facing `Crossfade` setting is persisted and defaults to off. It is capped at four seconds and at half of a short track. Native EQ and bass boost use the existing sequential fade instead: Android's effects are bound to the authoritative audio session and must not process only one half of an overlap. ReplayGain is read by the preloader and carried through handoff.

## Task 2 — Theme effect rendering

**Implemented.** `RelayTheme` renders bounded, host-owned `GRAIN` and `VIGNETTE` Canvas overlays, whole-app `GRAYSCALE` / `DUOTONE` color filters, and a bounded `BLUR` graphics layer. It has no animation loop, package-supplied shader, remote asset, or executable code. Effect state is copied only when a validated pack is applied; the Zune built-in pack now visibly renders its grain and vignette.

## Task 3 — Plan §9 item 5: profile, Last.fm import, calendar, charts

**Implemented, pending device verification.** INSIGHTS now has an editable, backupable local profile; explicit Last.fm history import (at most 1,000 historical scrobbles); provenance/deduplication; an unlink action that either preserves or, after a confirmation, removes only imported Last.fm events; time ranges, totals, rankings, and calendar; and saved reproducible album-chart specs. A saved chart can be rendered on-device as a private text-first PNG and sent through Android's share sheet. It makes no metadata/artwork calls. Artwork tiles remain deferred until the host has a reliable cached-artwork mapping for historical album records; never bulk-fetch while rendering a chart.

## Backlog after these (rough value order)

- Album/artist browse entities in the source API — additive `BaseRelaySource` methods + host screens; batch ABI churn into one version bump.
- RAINBOW shuffle — needs dominant-color extraction over cached artwork (shares work with theme effects).
- Extension install flow extraction from `MainActivity` (~220 lines welded to activity launchers — careful pass).
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
