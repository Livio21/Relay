# Privacy

Relay is local-first. There are no accounts, no analytics, no telemetry,
no crash reporting, and no advertising SDKs. This page states exactly what
can leave your device and when.

## Stays on your device

- Your library index, listening history, favorites, playlists, queue,
  metadata overrides, lyrics, insights/charts, theme packs, wallpaper
  presets, and settings — all stored locally (Room database + Relay's
  storage folder).
- Backups (`.relaybackup`) and sync bundles (`.relaysync`) are created only
  where you choose to put them.
- The diagnostics log (Settings → Advanced) lives in app-private storage
  and is exported only when you choose to share it.

## Leaves your device only through actions you initiate

| Data | Destination | When |
| --- | --- | --- |
| Catalog requests (repository descriptors, signed indexes, extension APKs) | User-added HTTPS catalog hosts | You add/refresh a source repository or install/update an extension. |
| Search/browse/stream/download requests for extension tracks | The extension's own service hosts | You browse, play, or download a track from that extension. Stream URLs are resolved just-in-time and never persisted. |
| Now-playing updates and scrobbles of tagged tracks; history import you trigger | ws.audioscrobbler.com (Last.fm) | Only while Last.fm is connected. Session keys are held in Android Keystore-backed storage, excluded from backups, and removable by disconnecting. |
| Metadata lookup queries (title/artist/album), optional | musicbrainz.org / Cover Art Archive / artwork hosts | Manual metadata review, or the opt-in automatic lookup setting. Requests are rate-limited and cached locally. |

## Permissions

- **Audio/media access** — indexing and playing music from folders you pick.
- **RECORD_AUDIO** — requested only if you enable the sound-reactive live
  wallpaper. Relay reads low-resolution FFT energy bands of *its own audio
  session* via the platform Visualizer, on-device only. Audio is never
  recorded, stored, transmitted, or exported. Denying the permission only
  disables that visualizer mode.
- **Notifications** — showing playback controls while backgrounded.
- **Nearby-network access** — only during an explicit LAN device-sync
  session you start; pairing is pinned-key + user-compared code, and no
  listener runs when no session is active.

## Network security

Catalogs, artwork, and extension artifacts must be HTTPS. Plain HTTP inside
LAN sync sessions requires an explicit per-session exception and never
carries credentials beyond the session's own ephemeral keys.

## Uninstalling

Uninstalling removes app-private data (database, caches, Keystore-bound
session material). Files Relay wrote into its user-selected storage folder
(music, downloads, sync bundles, backups) remain until you delete them.
