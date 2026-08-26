# Release checklist

Execution contract for shipping a Relay release. Work top-down; a release
tag is only cut when every box is checked against the **final** build.

## 1. Version and changelog

- [ ] `RELAY_VERSION` in `gradle.properties` set (no `-SNAPSHOT` suffix for the release).
- [ ] `CHANGELOG.md` has an entry for the version; *Unreleased* items folded in.
- [ ] `versionCode` derived by the build script matches `major*100000 + minor*1000 + patch*10`.
- [ ] Desktop `packageVersion` mirrors the same triple (`:desktopApp:assembleDist` builds).

## 2. Automated checks

- [ ] `./gradlew :composeApp:desktopTest` green.
- [ ] `./gradlew :androidApp:testDebugUnitTest` green.
- [ ] `./gradlew :androidApp:assembleDebug` and `:androidApp:assembleRelease` both succeed.
- [ ] CI workflow green on the tagged commit.

## 3. Device verification matrix (physical device, final build)

Run on the test device (CPH2649) with a realistic library. Check each item;
note failures as release blockers, fix, and re-run from the top of this list.

### Playback core

- [ ] Library indexes after permission grant; permission revocation shows an actionable state.
- [ ] Track select plays; queue supplies full list; PREV/PAUSE/NEXT/seek work.
- [ ] Background playback survives screen off and app backgrounding.
- [ ] System notification/lock-screen card shows correct metadata/artwork and controls playback.
- [ ] Gapless album playback verified (one `setMediaItems`, no per-track re-prepare).
- [ ] Crossfade transition hands over cleanly at the overlap; preloader released; ReplayGain carried through handoff.
- [ ] Crossfade correctly disabled while EQ/bass boost is active; settings copy says so.
- [ ] ReplayGain attenuates tagged tracks only when LOUDNESS setting is on.
- [ ] Equalizer presets + custom bands audibly apply; bass boost applies.
- [ ] Sleep timer: fixed duration pauses after countdown; END OF TRACK pauses without advancing; OFF cancels.
- [ ] Shuffle profiles (DEFAULT/ARTIST/ALBUM/TITLE/RAINBOW/image-seeded) produce stable queues; RESHUFFLE regenerates.
- [ ] Repeat modes cycle and behave (off/all/one).

### Queue, playlists, library

- [ ] Queue screen renders lazily with long queues (500+ items); play-from-index/reorder/remove/clear work.
- [ ] PLAY NEXT and QUEUE actions insert correctly.
- [ ] Process death mid-playlist restores queue incl. remote placeholders and offline copies.
- [ ] Playlists: create/rename/delete/reorder; cross-source entries play via lazy resolution.
- [ ] Search matches title/artist/album; sort cycles with blanks last; pinned first.
- [ ] Archived tracks no longer appear in the library view (regression check).
- [ ] Favorites toggle and flags survive restart.

### Extensions and downloads

- [ ] Catalog import via URL and `owner/repo` shorthand; descriptor + signing key review shown.
- [ ] Extension install → browse → search → stream; LOAD MORE pagination.
- [ ] Lazy stream resolution: force-stop mid-playlist → reopen → playlist streams without re-searching.
- [ ] Download completes into Relay/downloads; downloaded track plays offline (airplane mode).
- [ ] Storage limit + AUTO CLEANUP evict oldest-first; EVICT EXCEEDED works manually.
- [ ] Per-item DELETE removes file and row; partial `.part` files never appear as music.
- [ ] Update flow: one-tap update installs newer catalog entry.
- [ ] Tampered/oversized/incompatible entries rejected with visible reasons.

### Sync

- [ ] Wired `.relaysync` export/import round-trip between two clean installs.
- [ ] LAN pairing with pinned keys + pairing code; data exchange; selected-music transfer resumes and verifies digests.
- [ ] PLAY TOGETHER: host/guest join, drift display, RESYNC seek, explicit leave/pause-for-everyone.
- [ ] Unpair deletes peer credential; sessions cancel cleanly; battery/metered warnings show.

### Metadata, lyrics, insights

- [ ] Metadata health banner appears for incomplete tracks; review flow applies override end-to-end (library, media card, scrobble).
- [ ] SKIP / NOT A PROBLEM suppresses prompts until source revision changes.
- [ ] Lyrics overlay follows playback; STAMP editor inserts timestamps; offset ±100 ms shifts lines.
- [ ] `.lrc` import populates the editor/overlay; export writes a valid file.
- [ ] Insights totals/top lists/calendar render; Last.fm history import dedupes; chart export produces a shareable image.

### Surfaces

- [ ] Glance widget: all three size buckets render; transport controls work; refresh on track change; process-death restoration shows last snapshot paused.
- [ ] Live wallpaper: preview + activation via system picker; artwork/filters/title elements render; no-art fallback; track switch redraws; hidden wallpaper stops rendering.
- [ ] Sound-reactive visualizer: RECORD_AUDIO prompt only when enabling; deny = static wallpaper; ≤30 Hz updates; battery-saver disables animation.
- [ ] Landscape cover flow: rotation shows browser; tap plays; transport row present.
- [ ] Theme packs: import, live recolor, Zune grain/vignette effects visible, remove restores default.

### Backups and settings

- [ ] Manual backup round-trip on clean install (favorites/history/playlists/settings/extensions config).
- [ ] Corrupt/oversized backup fails safely before mutation.
- [ ] Automatic backup worker writes on schedule; expiry respected; failure notice actionable.
- [ ] Settings changes affect activity and playback service consistently.

### Performance spot checks

- [ ] ~2k-track library: scan time acceptable; scrolling smooth; memory stable.
- [ ] Second scan of unchanged library completes noticeably faster (revision cache).
- [ ] Expanded player position ticks do not visibly jank ambient artwork strips.

## 4. Build, sign, publish

- [ ] Create `keystore.properties` (untracked): `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
- [ ] `./gradlew :androidApp:assembleRelease` — APK signed with the release key.
- [ ] `apksigner verify --print-certs androidApp/build/outputs/apk/release/androidApp-release.apk`.
- [ ] `shasum -a 256 androidApp-release.apk > androidApp-release.apk.sha256`.
- [ ] Tag `v<version>` on the release commit.
- [ ] GitHub Release (draft): APK + checksums + changelog section + "verification matrix passed on <device/date>".
- [ ] Publish the release; confirm download link and checksum match.
