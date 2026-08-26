# Changelog

All notable changes to Relay are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning follows [SemVer](https://semver.org/).

## [Unreleased]

### Added

- LAN pairing without typing addresses: hosting devices advertise their session on the local network (NSD) and render a QR join code; guests tap SCAN NETWORK to list nearby Relay sessions or SCAN QR to read the code. Both feed the existing pinned-key + pairing-code handshake — trust requirements are unchanged. New dependencies for this feature: ZXing core + embedded scanner (Apache-2.0).
- Sleep timer: end-of-track or fixed-duration options with a gentle fade-out before pausing.
- Lyrics: `.lrc` file import/export and an offset nudge control for timed lyrics.
- Haptic feedback system: distinct patterns for transport presses, track selection, seeking, queue edits, favorites, and tab changes; intensity setting (OFF/SUBTLE/MEDIUM/STRONG); themes can remap patterns per interaction.
- Comprehensive theme engine v2: selectable transport arrangements (text row / centered icons / spaced bar), independent button corner radius, uppercase-title and global type-scale typography options, index-rail visibility toggle, and per-action haptic mapping — all data-only pack fields with strict validation; v1 packs remain compatible.
- Theme pack background images: validated image import (JPEG/PNG/WebP, ≤2 MB) stored in Relay-owned storage and referenced by content-hash tokens.
- New built-in iPod Classic theme pack; Zune and Material 3 packs upgraded to demonstrate v2 fields.
- Wallpaper editor: full interactive canvas with real rendered composition — drag to move elements, corner-handle resize, center snapping, fine-tune steppers and anchor grid kept in sync, element layering, undo/redo, and explicit save/cancel replacing the per-tap dialog-picker form.

### Fixed

- Haptics were silent on many devices: UI feedback now drives the Vibrator directly with explicit amplitudes (honoring the intensity setting exactly) instead of the OEM-tuned touch-feedback primitives, and the missing VIBRATE permission was added.
- Archived tracks are now excluded from the library view as intended.
- Launcher icon and app label resources added (previously used the system default).
- Performance: library filtering/sorting computed once per data change instead of per recomposition; queue renders lazily; library rescans skip unchanged files; expanded-player artwork blur no longer recomputes every position tick.
- Accessibility: equalizer sliders and insight range tabs meet the 48dp touch-target minimum.
- Wallpaper preview no longer a gray wireframe: canvas color, artwork crop mode, live metadata, clock, and progress render as the wallpaper will.

### Changed

- Governance: split license introduced — PolyForm Noncommercial 1.0.0 for code, CC BY-NC-SA 4.0 for docs/assets — plus THIRD_PARTY_NOTICES, PRIVACY, CONTRIBUTING, FORKING guides, AGENTS entry point, and CI workflow.
- Internal: `MainActivity` decomposed into feature coordinators (sleep timer, lyrics, downloads, extension installation, Last.fm account, backups/restores) with centralized user messaging, a local diagnostics log, and Settings → Advanced diagnostics export.

## [1.0.0] - Unreleased

First public Android release. Highlights carried in from pre-release
development (device verification recorded in docs/RELEASE_CHECKLIST.md):

### Added

- Local library playback over Media3/MediaSession with background playback,
  system media controls, gapless queues, crossfade, ReplayGain loudness
  normalization, equalizer + bass boost, and shuffle profiles (including
  deterministic image-seeded and RAINBOW groupings).
- Source extensions installed from signed HTTPS catalogs (Mihon-style APK
  sources, Source API v2) with browse/search listings, lazy stream
  resolution, media headers, schema-driven source settings, offline
  downloads with storage limits/cleanup, and one-tap updates.
- Playlists with cross-source entries, universal add-to-playlist, queue
  editing (play next/reorder/remove), and process-death queue restoration
  including remote placeholders.
- Last.fm scrobbling with desktop-flow auth (Keystore-secured session),
  pending-scrobble persistence across network failures, and history import.
- MusicBrainz/Cover Art Archive metadata repair with review-only applies,
  health detection, artwork caching, and effective-metadata precedence.
- Timed LRC lyrics with karaoke overlay, timestamp-capture editor, plain
  fallback rendering, and local lyric editing.
- Insights view: plays/listening-time totals, top tracks/artists/albums,
  listening calendar, local profile with provenance/deduplication, and
  exportable album charts.
- Device sync: wired `.relaysync` bundles, LAN pairing/data/music transfer
  with pinned peer identities, and foreground-only PLAY TOGETHER sessions
  with drift display and explicit RESYNC.
- Customization: data-only theme packs (live recolor incl. Zune pack),
  bounded effects (grain/vignette/grayscale/duotone/blur), Glance player
  widget with three size buckets, album-art live wallpaper with filters,
  visualizers, and opt-in sound-reactive mode.
- Versioned backups/restores with transactional preflight, automatic
  backup worker, and settings/storage management.
