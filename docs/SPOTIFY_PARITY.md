# Spotify parity — research and feasibility

_Research note, 2026-11-16. This is not a plan change and not a commitment.
The active plan is still Phase 10; feature work after v1.0 needs an owner
decision. Sources were read on 2026-11-16 and are listed at the end._

## 1. The short version

- Spotify's **player mechanics** are mostly matchable by Relay. Its **catalog,
  social graph and personalization data** are not.
- Relay already matches most playback, queue, offline, lyrics, library-search
  and backup behavior. The real gap is **discovery, playlist workflows,
  sharing and casting**.
- Every credible open-source "Spotify replacement" substitutes a **content
  backend** (YouTube Music, Jellyfin, Navidrome/Subsonic) instead of streaming
  Spotify. Relay already has that backend abstraction. The highest-leverage
  content move is a **self-hosted server source**, not a Spotify client.
- Suggested parity definition: **client parity for music the user can already
  access**, plus local/open-data discovery. Not catalog, Spotify-style social,
  cloud AI or cross-network hand-off. Social is cheaper through Last.fm than
  through a Relay account; a paired LAN circle covers in-room playback only
  (section 11).
- The only genuinely unreachable area is Spotify's catalog and its data-network
  effects (global recommendations, internet-scale social listening). Cars are a
  distribution problem, not a code problem.

## 2. What "parity" can mean

Spotify is three products in one: a licensed catalog, a personalization data
engine, and a music client. Only the third is a realistic target for Relay.

| Reading | What it would require | Verdict |
|---|---|---|
| **Catalog parity** — same songs, podcasts, audiobooks | Content licensing, DRM, a subscription business | Not possible. Spotify's API does not deliver audio streams [5]; playback needs Spotify's SDK and a Premium account. Reverse-engineered clients are unofficial and currently broken for new accounts [7]. |
| **Client parity** — the controls, library, playlists, discovery, lyrics and surfaces around the music | Engineering and design only | Possible in large part. This note measures it. |
| **Cloud/social parity** — Connect across networks, Jam, Blend, friend activity, public profiles | Accounts, a backend, population-scale listening data | Contradicts Relay's local-first, no-account design. A paired LAN circle and Last.fm-based friend activity are the feasible subsets (section 11); the rest is not recommended. |

Relay's fixed constraints (from `IMPLEMENTATION_PLAN.md` and `AGENTS.md`):
Android-first Compose Multiplatform, Media3 playback, signed source
extensions, no accounts, no Relay cloud, no telemetry, GitHub distribution for
v1, data-only customization packs, and a noncommercial license.

## 3. What Spotify ships (as of late 2026)

Grouped from Spotify's own announcements and help pages:

- **Playback**: background/lock-screen controls, gapless, crossfade, loudness
  normalization, equalizer with presets, sleep timer, playback speed for
  podcasts, five quality levels up to 24-bit/44.1 kHz lossless FLAC, per-network
  and download quality settings, offline downloads [1][27].
- **Library**: liked songs, album and artist pages, playlists with custom
  covers, local files, search, playlist folders (desktop), recently played.
- **Discovery**: Home, Made for You, Daily Mixes, Discover Weekly, Release
  Radar, track/artist radio and autoplay, Smart Shuffle, daylist (updates
  several times a day) [10], Wrapped (annual recap) [11], Enhance, AI DJ [13],
  AI Playlist [1], Mix transitions [1].
- **Playlists and social**: collaborative playlists, Jam (shared queue; guests
  add songs; host needs Premium; works in person or remotely) [8], Blend (shared
  taste, daily updates, up to 10 friends) [9], friend activity, follows, public
  profiles, share cards and shareable lyrics [3].
- **Lyrics**: line-synced lyrics with a preview, shareable; Musixmatch's
  distribution matrix lists Spotify as plain and line-synced, not word-by-word
  [3][4].
- **Other content**: podcasts, audiobooks (expanding to 180 countries by the
  end of 2026) [12], music videos and video podcasts in some markets.
- **Devices**: Spotify Connect hand-off and speaker control [2], Google Cast,
  Android Auto, CarPlay, Wear OS, TV, web player, desktop, widgets, voice
  search.
- **Business**: free ad-supported tier and paid tiers. Relay has no ads and no
  subscription, so this is deliberately different rather than missing.

## 4. What comparable open-source apps prove

| App | Stack / content model | Parity-relevant features | Lesson |
|---|---|---|---|
| **Metrolist** (14.5k stars, active) | Kotlin Multiplatform, Compose; YouTube Music | Android Auto and Android TV, Google Cast/FCast/DLNA/Sonos, podcast library, playlist import from Spotify/Apple/Deezer/Tidal/Amazon and 30+ app backups, multiple lyrics providers, AutoEQ import, Shazam recognition, LAN device connect, stats, sleep timer, crossfade, normalization [16][17] | Almost the whole Spotify client surface is achievable by a small OSS team — but it depends on a third-party content source and carries that source's ToS risk. |
| **OuterTune** (5.4k stars, unmaintained) | Kotlin, Compose; YouTube Music + local files | Local and remote in one queue, multiple queues, TTML/word-by-word lyrics, normalization, tempo/pitch, Android Auto, custom tag extractor [18] | Mixing local and remote music in one library is viable; an unmaintained fork still leaves a feature benchmark. |
| **Finamp** (4.6k stars) | Flutter; Jellyfin | Gapless, offline, transcoded streaming, lyrics, ReplayGain, Android Auto and CarPlay, server-side sonic analysis via AudioMuse [15] | The server-client model is the strongest "own library everywhere" story. It states plainly that Android Auto requires installing from Play. |
| **Feishin** (10k+ stars) | Electron; Navidrome/Jellyfin/Subsonic | Desktop player, server smart playlists, synced lyrics, scrobbling [19] | The desktop/server ecosystem already expects smart playlists and server-driven discovery. |
| **Spotube** (50k+ stars) | Kotlin Multiplatform; pluggable audio sources | Plugin architecture, time-synced lyrics, downloads, no telemetry [20] | A plugin/source model is a proven way to separate the client from the content source. It does not make Spotify catalog access legal. |

The common finding: **none of them stream Spotify**. They are clients to a
backend the user already has access to. Relay's signed source extensions are
the same architecture; the missing piece is content breadth (a self-hosted
server source) and the client-side discovery/library UX around it.

## 5. Relay today

Short inventory, from the tree at the time of writing:

- **Playback**: Media3/MediaSession, gapless, crossfade (0–4 s), ReplayGain
  attenuation, 5-band equalizer + bass boost, sleep timer, playback speed,
  queue editing, shuffle profiles, offline downloads with storage limits and
  eviction.
- **Library**: SAF-folder local library, favorites flag, hide/archive,
  search/sort, cross-source playlists, queue restoration, Last.fm scrobbling,
  MusicBrainz metadata repair, insights, backups.
- **Sources**: signed HTTPS catalogs, Source API v2, lazy stream resolution,
  per-source settings, downloads, FMA/ccMixter/Audius/Octave/Soulseek in the
  current catalog.
- **Surfaces**: system media card, Glance widget, live wallpaper, landscape
  cover flow, LAN pairing/data/music transfer, foreground-only play-together.
- **Customization**: data-only theme packs v2, wallpaper composition editor.
- **Not present**: home/discovery surface, radio/autoplay, playlist
  import/export, sharing beyond the album-chart export, casting, Android Auto,
  podcasts/audiobooks, localization. Album and artist browsing landed
  2026-11-16.

## 6. Gap matrix

Legend — Relay: **yes** / **partial** / **no**. Fits:
**A** = fits the current architecture, no backend or account required;
**B** = realistic with new machinery (new media type, casting layer, new
renderer), still local-first; **C** = conditional on a decision outside code
(Play distribution, iOS app, accounts, cloud); **D** = not realistic or not
worth it. Effort is for one developer familiar with the codebase:
S ≈ days, M ≈ 1–3 weeks, L ≈ 1–3 months, XL > 3 months; it excludes device
verification.

### Playback and audio

| Feature | Relay | Fits | Effort | Notes |
|---|---|---|---|---|
| Background, lock screen, Bluetooth controls | yes | — | — | Media3 media session |
| Gapless | yes | — | — | One `setMediaItems` queue |
| Crossfade | yes | — | S | Capped at 4 s; a longer range is a settings change, not new tech |
| Loudness normalization | yes | — | — | ReplayGain; attenuates, never boosts |
| Equaliser, presets, bass boost | yes | — | — | 5 bands |
| Sleep timer and playback speed | yes | — | — | Both in playback settings |
| Queue and shuffle/repeat | yes | — | — | Shuffle profiles exceed Spotify's single shuffle |
| Audio quality selector and lossless | partial | B | M | Local FLAC already plays; there is no per-network or per-download quality preference, and extension streams are source-defined |
| Smart Shuffle | no | A | M | Local similarity + co-play history; needs the discovery engine below |
| Automix / beat-matched transitions | no | D | — | Requires beat/energy analysis; low user value for the effort |

### Library and organization

| Feature | Relay | Fits | Effort | Notes |
|---|---|---|---|---|
| Liked Songs collection | partial | A | S | Favourites flag exists per track; no dedicated collection surface |
| Album pages | yes | — | — | Library ALBUMS mode and album detail; remote sources still need entity support for remote albums |
| Artist pages | yes | — | — | Library ARTISTS mode and artist detail; also a natural seed for artist radio |
| Playlists: create/rename/reorder/delete | yes | — | — | Cross-source entries |
| Playlist folders | no | A | S | The playlist model has no folder entity |
| Local files | yes | — | — | Core model |
| Library search | partial | A | S | Searches tracks; no album/artist result groups |
| Sort and filter | partial | A | S | Sort cycles exist; no filter chips |
| Recently played | no | A | S | History data exists in insights; no UI |
| Search within playlist, search history | no | A | S | Two small features |
| Download a playlist, bulk select | no | A | M | Per-track downloads exist |
| Playlist cover art | no | A | S | The playlist model has no artwork field |

### Discovery and recommendations

| Feature | Relay | Fits | Effort | Notes |
|---|---|---|---|---|
| Home surface | no | A | M | Recently played, mixes, continue listening; no network needed |
| Made-for-you mixes | no | A | M | From favorites, history, tags, co-play |
| Track/artist radio and autoplay | no | A | M | Last.fm similar-track/artist data plus local matching; opt-in and cached |
| daylist-style time-of-day playlist | no | A | M | Cheap heuristic once mixes exist; fun, not core |
| Discover Weekly / Release Radar | no | C | L | Requires population-scale taste data or release feeds; open-data approximations are weaker |
| Enhance / smart insertion | no | A | M | Depends on the mix engine |
| Related artists, artist bio, credits | no | A | S | Last.fm and MusicBrainz already integrated; rate-limited |
| Genre/mood browse | no | B | L | Needs bounded tags (plan §5.1.1); add them when discovery consumes them |
| Year recap ("Relay Wrapped") | partial | A | M | Insights compute the data [11]; needs a yearly aggregation and share image |
| AI DJ, AI playlists | no | D | — | Cloud LLM/TTS and editorial data; contrary to the no-account design |

### Playlists, sharing and social

| Feature | Relay | Fits | Effort | Notes |
|---|---|---|---|---|
| Collaborative playlists | no | C | L | Needs shared storage; a pairwise shared playlist over sync is feasible (section 11), a Spotify-style live collaboration is not |
| Jam / group session | partial | B | M | LAN play-together exists; a shared queue over the same secured socket is realistic (section 11), cross-network is not |
| Friend activity and now playing | no | A | S/M | Last.fm friends + per-friend recent tracks; poll while visible; attribution (section 11) |
| Taste comparison ("Blend-lite") | no | A | S/M | Last.fm top-artist overlap; Tasteometer is gone (section 11) |
| Blend (Spotify-style) | no | D | — | Continuous social recommendations need accounts; top-artist overlap is the feasible version (section 11) |
| Share track/album/playlist card | no | A | S | Reuse the chart renderer; Android share sheet; no hosted links |
| Share lyrics | no | A | S | Local card; no lyric redistribution beyond the user's own copy |
| Import playlists from other services | no | A | M | CSV (Exportify) [25], M3U, or read-only OAuth; match by metadata and report unmatched tracks |
| Export playlists | no | A | S | CSV/M3U; useful for leaving as well as arriving |
| Public profile and follows | no | A | S | Link out to Last.fm; Relay does not own the graph (section 11) |

### Lyrics and metadata

| Feature | Relay | Fits | Effort | Notes |
|---|---|---|---|---|
| Timed lyrics | yes | — | — | LRC, offset nudge, STAMP editor, import/export |
| Word-by-word lyrics | no | B | L | TTML support; Spotify itself is line-synced [4] |
| Network lyrics | no | A | S/M | LRCLib is a public synced-lyrics API [21]; user-initiated, cached, attributed |
| Metadata repair and overrides | yes | — | — | Relay is ahead of Spotify here |
| Song credits | no | B | M | MusicBrainz relationships; rate-limited |

### Devices and surfaces

| Feature | Relay | Fits | Effort | Notes |
|---|---|---|---|---|
| Widgets | yes | — | — | Glance, three size buckets |
| Android Auto | no | C | M | Technically close (`MediaSessionService`/`MediaBrowserService`); Android Auto requires Play distribution and review [14][15] |
| CarPlay | no | D | — | Needs an iOS app and Apple's approval |
| Wear OS and TV | no | C | L | Separate surfaces/apps; distribution and maintenance cost |
| Casting (Cast/DLNA/Sonos) | no | B | L | Media3 Cast exists [24]; local files need a loopback HTTP server; DLNA/FCast alternatives avoid Play Services |
| Cross-network hand-off | no | C | L | LAN play-together is the local analog; true Connect needs a Relay cloud |
| Desktop parity | partial | B | L | macOS local player only; no persistence, Last.fm, sync or extension UI |
| Web player and voice control | no | D | — | Different platforms/products; low value for a local-first app |
| Tablet/foldable layouts | partial | A | M | Compose can adapt; no current breakpoints |
| Localization | no | B | L | English-only today; broad reach, but not a Spotify-specific gap |

### Content and business

| Feature | Relay | Fits | Effort | Notes |
|---|---|---|---|---|
| Podcasts | no | B | L | RSS + Podcast Index [26]; new content type with per-episode progress and speed |
| Audiobooks | no | C | L | Local audiobooks could reuse the player with chapters/bookmarks; Spotify's catalog is licensed and impossible |
| Music videos / video podcasts | no | D | — | Different media pipeline and licensing |
| Ads / Premium subscription | n/a | — | — | Relay has neither by design; not a gap |
| Cross-device account sync | partial | C | L | Wired and LAN sync exist; cloud account sync does not and should not |

## 7. Where the gap actually is

Relay is not "50% of Spotify". It is close to complete on the parts that make
Spotify a **player**, and close to zero on the parts that make it a
**discovery and social service**:

- Playback and offline are effectively at parity, with a few ergonomic gaps.
- Library management now has tracks, playlists, **albums and artists**; the
  remaining gaps are discovery, playlist workflows, sharing and casting.
- Discovery is the largest block of missing surface, and most of it is
  buildable without a backend.
- Sharing/import is the easiest way to feel dramatically more complete: right
  now a user cannot bring a playlist in or send a track out.
- Content breadth is the real reason someone keeps Spotify. For Relay that
  means source extensions, not Spotify.
- Spotify-style social and cars are the least realistic and, for a local-first
  tool, the least valuable. A Relay-owned social graph is not realistic;
  Last.fm can supply a graph and friend activity (section 11), and a paired
  circle covers in-room playback.

## 8. What is realistic

### Tier 1 — close the everyday gaps (fits the architecture)

1. **Album and artist pages, library grouping** — done 2026-11-16
   (`LibraryBrowse.kt`, `BrowseScreens.kt`, Library browse modes, album/artist
   detail, `onPlayTracks`). Remote entities still need source API support.
2. **Home: recently played, continue listening, local mixes** (M). Uses
   existing history and favorites; no network, no new dependency.
3. **Track/artist radio and autoplay** (M). Last.fm similar-track/artist
   lookups plus local matching (the Last.fm key already exists for scrobbling).
   Opt-in, cached, with a "nothing similar" fallback.
4. **Playlist import and export** (M). M3U, CSV, and Spotify exports via
   Exportify [25]; match on normalized title/artist/album/duration and show
   unmatched tracks honestly. Treat every file as untrusted bounded input.
5. **Playlist workflow polish** (S each): download a playlist, search inside a
   playlist, folders, custom covers, bulk select.
6. **Share cards** (S). Track, album, playlist and lyrics images through the
   Android share sheet, reusing the chart renderer. No hosted links, no account.
7. **Network lyrics** (S/M). LRCLib lookup in the lyrics controller [21],
   user-initiated, cached, attributed; keep local LRC authoritative.
8. **Year recap** (M). Aggregate the existing insights per year and render a
   private share image.
9. **ListenBrainz scrobbling and similar-artist data** (S). A second tracker
   with no paid key, matching what Metrolist/OuterTune offer [22].
10. **Smart Shuffle-lite** (M). Once radio exists, insert one similar track
    every few queue positions, never repeating, never changing the queue
    without a visible action.

### Tier 2 — bigger bets, still local-first

11. **Self-hosted server source** (L). A Subsonic/OpenSubsonic extension
    covering Navidrome, Airsonic, Gonic, Nextcloud and similar servers [23].
    This is the biggest content unlock available to Relay: real remote
    libraries with search, browse, streaming, transcoding and downloads through
    the existing lazy resolver. Auth material belongs in Keystore.
12. **Casting** (L). Media3 Cast for Chromecast plus a DLNA/FCast path for
    local networks [24]. Local files need a loopback HTTP server; DRM content
    is out of scope.
13. **LAN Jam** (M). Extend play-together with a shared queue: guests add or
    reorder tracks over the same pinned, encrypted socket. Local network only;
    no accounts.
14. **Local audio analysis** (L, optional). BPM/key/energy tags or a small
    on-device analyzer would improve radio and transitions. Metadata-first is
    cheaper; do not block Tier 1 on this.
15. **Podcasts** (L). RSS and Podcast Index [26], new episode model, per-show
    settings, playback speed and skip. A separate content kind, not a track
    hack.
16. **Desktop parity** (L/XL). Shared library persistence, Last.fm, discovery
    and sync; the current desktop app is a local-file player.
17. **Localization** (L). Not Spotify parity, but the largest reach multiplier
    after the above.
18. **Android Auto** (M) — only if the distribution decision changes (see
    below).

### Tier 3 — not realistic or not worth it

- **Spotify catalog or playback.** The API does not expose audio; the Web
  Playback SDK plays through Spotify for Premium users; librespot is
  unofficial, Premium-only, explicitly "probably forbidden", and has been
  broken for accounts created after Spotify's DRM key change [5][6][7].
- **Cross-network Connect, Jam, Blend, friend activity, public profiles.**
  All require accounts, a backend and a social graph. Relay's LAN play-together
  is the honest local substitute.
- **Discover Weekly / Release Radar quality.** These depend on
  population-scale listening data and editorial curation. Last.fm/ListenBrainz
  similarity is useful but not equivalent.
- **AI DJ and prompted playlists.** Cloud models, TTS and editorial data;
  contradicts the no-account design and is mostly presentation.
- **Podcast/audiobook marketplaces.** Licensing; Spotify exclusives are
  undeliverable. Local/RSS content is the realistic version.
- **CarPlay, Wear OS, TV, web, voice.** Either needs a platform app and store
  distribution, or is a different product. Android Auto is the one worth
  revisiting if Relay ever ships on Play.
- **Beat-matched automix and guaranteed lossless streaming.** Analysis and
  source-dependent; low value against the cost.

## 9. Architecture implications

- **Discovery engine in `commonMain`.** A pure, testable module fed by
  favorites, listening history, queue history, metadata and cached Last.fm
  similarity. It should produce ranked track lists; no UI, no network calls in
  composables. Desktop gets it for free.
- **Album/artist entities.** Group local tracks first. Remote album/artist
  browse needs the source API to expose entities (the plan already lists this
  as a backlog item); do not fake it with string grouping across sources.
- **Bounded tags.** Radio and mixes are only as good as the metadata. The plan
  already specifies a bounded tag record (genre/mood/year/BPM); add it when
  discovery consumes it, not before.
- **Import as untrusted input.** Parse in shared code, bound size and counts,
  normalize text, match conservatively, and show matched/unmatched results
  before writing.
- **Share cards as a renderer, not a new service.** Reuse the existing chart
  export path; share bytes through the platform, never host them.
- **Network lyrics as a host integration, not an extension.** Same pattern as
  Last.fm/MusicBrainz: timeouts, bounded response, cache, attribution,
  user-initiated.
- **Casting needs a media source.** A loopback HTTP server with Range support
  for local files; extension streams already resolve to HTTPS URLs. Keep the
  player authoritative and treat the cast device as an output.
- **Server source fits the existing extension contract.** OpenSubsonic maps
  cleanly onto search/browse/lazy resolution/download; auth and transcoding
  are the new parts.
- **Jam reuses the LAN transport.** The current play-together socket is already
  authenticated and sequenced; a queue-request message is a protocol version
  bump, not a new stack.

## 10. Legal and platform constraints

- **Spotify API.** The `streaming` scope is for the Web Playback SDK and
  requires Premium; there is no endpoint that hands Relay audio [5]. The
  developer terms restrict use of the platform [6].
- **librespot.** Unofficial, Premium-only, and currently cannot obtain audio
  keys for accounts created after Spotify's DRM change; its own README says
  use is "probably forbidden" [7]. Not a foundation for a public app.
- **YouTube-based sources.** Metrolist explicitly warns that using it may
  violate YouTube's terms; OuterTune and Spotube depend on the same kind of
  third-party source and carry the same risk [16][18][20]. Relay's extension
  model can host such a source, but the official catalog should not ship one;
  responsibility and risk stay with the user who adds it.
- **Lyrics.** LRCLib is a public community database [21]; lyrics are
  copyrighted. Keep retrieval user-initiated, cache locally, attribute the
  source, and do not redistribute lyric text in shared cards beyond what the
  user already has.
- **Android Auto.** Media apps must be uploaded to Play Console and reviewed
  before they appear in cars [14]; Finamp's FAQ says the same for its own app
  [15]. A GitHub-only APK is not a supported path.
- **Last.fm.** The API terms are non-commercial, require attribution and links,
  cap stored data at 100 MB and impose rate limits [31]. Donations or any
  monetization need review before shipping social features.
- **Podcasts/audiobooks.** RSS and user-owned files are fine; Spotify's
  exclusive and licensed catalog is not accessible.
- **Distribution.** The noncommercial license does not prevent a Play listing,
  but it does prevent F-Droid-style "free software" claims and changes the
  current GitHub-only release model. That is an owner decision, not a code one.

## 11. Social options: local identity, paired circle, Last.fm

The social column is not all-or-nothing. Relay already has most of a
serverless identity and transport; what it lacks is discovery and a reason to
use it.

### What already exists

- **Local profile**: display name in Insights, optional Last.fm association.
- **LAN identity**: Android Keystore RSA keypair, pinned peer public keys, a
  user-compared pairing code, RSA-OAEP session wrapping and sequenced AES-GCM
  frames (`library/LanSecureSocket.kt`, `LanSyncIdentityStore.kt`,
  `LanSyncCrypto.kt`).
- **Discovery and pairing**: NSD advertise/browse, QR join link and manual
  address (`sync/LanDiscovery.kt`, `sync/LanPairingLink.kt`).
- **Sessions**: `.relaysync` exchange, selected-music transfer, play-together,
  saved peers, unpair, foreground-only lifecycle.
- **Merge semantics**: transactional import and visible conflict records in
  `.relaysync`.

That is roughly an account system without an account: a device key, an explicit
trust list and a private transport. A "local profile ID" is the public-key
fingerprint plus the display name, not a login.

### A feasible paired-circle model

| Piece | What it is | Fits | Effort | Notes |
|---|---|---|---|---|
| Profile ID | display name + public-key fingerprint, per device at first | A | S/M | Not a login; tied to the Keystore key |
| Pairing | QR/short code, mutual pinning, saved peers, unpair | A | done | Already the LAN trust model |
| Presence / now playing | opt-in "listening now", visible to paired peers while the app or a session is open | A | M | LAN only; off by default; status-only mode |
| Shared queue / LAN Jam | guests add or reorder over the same secured socket | A | M | Host-authoritative; no accounts |
| Pairwise taste exchange ("Blend-lite") | explicit bounded top artists/tracks plus a recent window, merged locally into a playlist | A | M/L | No continuous upload; both sides accept |
| Shared playlist | pairwise sync of one playlist with deletion tombstones and visible conflicts | A | L | Reuses `.relaysync` merge/conflict rules; tombstones are the missing part |
| Share file / deep link | playlist or track card as a file, QR or bounded link | A | S | No hosting, no public URL |
| Relay-owned internet friend graph | cross-network discovery and identity | D | — | Needs rendezvous, NAT traversal and moderation; use Last.fm instead |

### What a local ID is not

- **Portable across reinstalls** unless a separate exportable key or recovery
  flow exists. Keystore keys are non-exportable by design; losing the app means
  re-pairing. That is a real product decision, not a bug.
- **A person across devices.** A phone and a desktop are two peers unless keys
  are delegated or grouped; a person-level identity is a larger design.
- **Discoverable.** There is no global namespace, search or follow graph. Peers
  come from a QR/code exchange, not from a directory.
- **Moderated.** Blocking and unpairing are the only recourse. Keep pairing
  explicit, presence opt-in, and never accept unsolicited connections.

### Where "local" stops

Any cross-network social feature needs one of: user-managed networking (port
forwarding, a VPN overlay such as WireGuard/Tailscale), a rendezvous/signaling
service, or a third-party network (Nostr relays, Matrix, a DHT). Each is
infrastructure by another name and carries metadata, moderation and dependency
costs. Nostr is the least account-like option because identity is a keypair,
but it is still a relay dependency, not local-first.

Soulseek is not a counter-example: it is a hybrid with central login, search and
chat servers plus peer-to-peer transfers. A Soulseek-style "account" implies a
server. Relay's LAN pairing is closer to AirDrop than to Soulseek.

### Last.fm as the social layer

Last.fm is the cheaper route to most of the social surface, and Relay already
has the hard parts: public reads need only the API key, the user session is
already stored, and `track.updateNowPlaying` is sent on playback start
(`lastfm/LastFmTracker.kt`). A friends surface needs no Relay account and no
backend. Last.fm has operated independently since May 2026 and says API
functionality continues [32].

What the API supports (checked 2026-11-16):

| Need | API | Notes |
|---|---|---|
| Friend list | `user.getFriends` [29] | No user auth; paginated, 50 per page |
| Now playing / recent | `user.getRecentTracks` [28] | `nowplaying="true"` on the current track; 200 per page; no auth |
| Taste overlap | `user.getTopArtists` | Public; use a 6- or 12-month period |
| Similar tracks / artists | `track.getSimilar` [30] | No auth; powers radio |
| Weekly charts | `user.getWeekly*Chart` | Optional; Relay's own recap is better |

Quirks found in testing:

- The documented `recenttracks` flag on `user.getFriends` did not return recent
  listening data; plan on one `getRecentTracks` call per friend, capped and
  cached.
- `Tasteometer.compare` returns "Invalid Method"; use top-artist overlap
  instead.
- Friends can hide recent listening in Last.fm's privacy settings [33]; the
  feed must degrade to "recent listening unavailable", not an error.

What this buys that a Relay circle cannot: friends who use Spotify or any other
scrobbler, an existing friend graph, cross-network reach, and recommendations
from listening data. What it cannot do: shared queue, synchronized playback,
collaborative playlists, or real-time presence. Last.fm is asynchronous and
poll-based.

Constraints:

- Non-commercial use only [31]. Relay's noncommercial license fits, but any
  monetization (including questions about donations) needs a commercial
  agreement or legal review.
- Credit Last.fm and link to Last.fm from views that use the data; the API
  terms require it [31].
- Maximum 100 MB of stored Last.fm data; cache bounded, respect HTTP caching
  headers, and do not mirror friends' histories [31].
- Rate limits and documented error 11/16/29 responses must be handled [28];
  poll only while the surface is visible, cap friends, back off and retry.
- The API key ships in the APK; without it, social and scrobbling are disabled
  (current behavior).
- Privacy: Last.fm data is third-party and public-ish. Make social opt-in, show
  attribution, respect hidden listening, and delete cached data on disconnect.

Recommendation: use Last.fm for friend activity, taste comparison and
recommendations, and keep the paired LAN path for in-room shared playback. Do
not build a Relay social graph. If privacy rules out Last.fm entirely, the
paired circle is the fallback, not an equivalent network.

### Nearby: presence with Last.fm enrichment

A nearby screen can show Relay devices on the same network and enrich a peer card
from that person's public Last.fm profile, so pairing needs less typing. It is
not a radar: NSD reports presence on the same LAN, not distance or direction,
and there is no signal-strength reading. A literal radar sweep would imply data
the platform does not provide.

Flow that keeps identity out of the beacon:

1. While the Nearby screen is open, an opt-in toggle advertises a nearby service
   (`_relay-nearby._tcp`) with protocol version, display name, public-key
   fingerprint and capability flags. No Last.fm username, real name or location.
2. The screen browses for the same service, caps the list and shows device cards.
   Beacons are unauthenticated, so names stay "unverified" until pairing.
3. Tapping a device uses the existing pairing code/QR handshake. Pairing proves
   the device key, not the Last.fm account.
4. After both sides confirm, the peer may share an optional profile card over
   the existing encrypted socket: display name, Last.fm username (if enabled)
   and whether now-playing may be shown.
5. The receiver fetches public Last.fm metadata on demand (profile, top artists,
   recent/now playing) and labels it "via Last.fm, shared by them". Account
   ownership is self-claimed; the UI must not imply verification.
6. Compatibility is top-artist overlap, computed locally. Cache bounded with a
   TTL, and clear it on unpair or disconnect.

Privacy and safety:

- Do not broadcast the Last.fm username in NSD. It links an online identity to a
  physical presence for anyone on the network.
- Fetch Last.fm only for a selected or paired peer, never for every beacon;
  respect rate limits and error 29, and cache within the 100 MB cap [31].
- Hidden recent listening degrades to profile and top artists only [33].
- Keep the pairing code as the security boundary; block/unpair; no auto-connect;
  no background scanning. Screen-scoped presence by default.
- Android 17 enforces local network access behind `ACCESS_LOCAL_NETWORK`; target
  36 is still opt-in, but plan the permission and runtime request before
  targeting 37 [34].
- Last.fm terms forbid using data to identify or contact users [31]. Show public
  music metadata only: no real names, contact details or location.

Feasibility:

| Piece | Fits | Effort | Notes |
|---|---|---|---|
| Presence beacon + browse | A | S/M | Reuses NSD; add a service type and lifecycle |
| Nearby list | A | S/M | Static list, not a distance radar |
| Pairing reuse | A | done | Existing code and QR flow |
| Profile exchange | A | S/M | New versioned message on the secured socket |
| Last.fm enrichment | A | S | Existing client; add public profile and top artists |
| Compatibility | A | S | Top-artist overlap |
| Privacy controls | A | M | Toggles, labels, cache clearing |

Verdict: worth doing as a small, opt-in Nearby surface; it makes pairing feel
lighter without building a directory. Keep it LAN-only and post-pairing for
Last.fm data.

### Proximity transports: hotspot, Wi-Fi Direct, NFC, Quick Share

The LAN path assumes a shared network. When there is no common Wi-Fi, the
options are not equivalent:

| Transport | No internet | Relay-to-Relay | Effort | Notes |
|---|---|---|---|---|
| Shared LAN (existing) | yes | yes | done | NSD + TCP; needs a network both devices already joined |
| Local-only hotspot | yes | yes | M | `LocalOnlyHotspot`; QR carries SSID/passphrase and host address; reuses the existing socket; Android 13+ needs `NEARBY_WIFI_DEVICES` [36] |
| Wi-Fi Direct | yes | yes | L | No manual join, 1:1 or group; older API and OEM variation; app-level pairing still needed [35] |
| Nearby Connections | yes | files/streams | M/L | Public Google Play Services API; encrypted, high bandwidth, file payloads; adds GMS and SDK analytics; iOS via Swift [37] |
| Quick Share today | yes | file export only | S | System share sheet only; no public protocol; receiver imports the file [38] |
| Quick Share SDK (H1 2027) | yes | files | wait | Announced in-app sheet, Tap to Share, end-to-end encryption, auto hotspot/Wi-Fi Direct, Android and iOS; not available now [38] |
| NFC | n/a | handshake / small token | M/L | Beam/P2P removed; HCE + reader can carry a profile or tiny playlist token, but it is OEM-finicky; QR is the default and NFC is only a shortcut [39] |

Recommendation for Relay:

1. Keep the shared-LAN path.
2. Add a **local-only hotspot** fallback first: the host starts the hotspot, the
   QR carries its SSID/passphrase and address, the guest joins, and the existing
   `LanSecureSocket`/sync code runs unchanged. No Google dependency, more than
   two devices, and the least new code.
3. Add **Wi-Fi Direct** only if the join step is unacceptable and the 1:1 case
   justifies the OEM testing. It is not a drop-in replacement for the socket
   layer.
4. Treat **Nearby Connections** as an alternative only if Google Play Services
   is acceptable. It has the best offline UX but adds GMS/analytics and does not
   reuse Relay's protocol.
5. Treat NFC as an optional shortcut, not a transport: a small profile/contact
   card or a tiny playlist token can use HCE + reader, but QR delivers the same
   payload more reliably today. Revisit tap when the Quick Share SDK ships.
6. For sending files to non-Relay devices now, offer the system share sheet
   (Quick Share, Bluetooth, or another target). That is an export, not a Relay
   session.

Last.fm has no add-friend or follow endpoint: the social API is read-only for
friendship [29]. Contact sharing can carry a username or profile URL, but
adding the person remains manual on Last.fm. Real playlists go as an exported
file, not an inline link; only a handful of tracks fit a QR/NFC token.

Security: link-layer Wi-Fi security is not peer identity. Every transport still
pins keys and confirms a pairing code; hotspot passphrases are short-lived and
never logged; received files stay untrusted input and go through the existing
archive validation. No auto-accept, no background hotspot.

### Verdict

Last.fm is the pragmatic social layer: it reuses the existing tracker, gives
friend activity, taste comparison and recommendations, and reaches friends on
other music apps. The paired circle is the privacy-first fallback and the only
way to get in-room shared listening; it is not a social network. A Relay-owned
social graph is not worth building. If social is in scope, do Last.fm first and
treat the circle as a separate, smaller feature.

## 12. Proposed parity target

If the owner wants a concrete target, this is the one this research supports:

> **Relay matches Spotify's playback, library, lyrics and offline workflows
> for music the user can access; adds a local discovery layer (home, mixes,
> radio, recap) powered by Relay's own history and optional open APIs; and adds
> import/export, sharing, casting and a self-hosted server source so the
> library is portable and reachable. It does not attempt catalog, Spotify-style
> social or cloud-AI parity.**

Testable success criteria:

1. Every Spotify playback control except automix and Connect exists.
2. A new user with no account and no network gets a useful Home screen with
   recently played and at least one mix.
3. Starting radio from any track always produces a playable queue, with a
   visible reason when it cannot.
4. A Spotify playlist export imports with a matched/unmatched report.
5. A track, album, playlist and lyrics view can each produce a share image.
6. A year recap can be generated from local history.
7. One self-hosted server library can be browsed, searched, streamed and
   downloaded through the existing source contract.
8. At least one cast target (Chromecast or DLNA) plays local files.

If the paired circle is selected, add: two paired devices can see each other's
now-playing on the same network (off by default) and share a queue.

Explicit non-goals: Spotify catalog, accounts, a Relay-owned social graph,
cross-network hand-off, AI DJ, cars/wearables under the current distribution,
video. A paired LAN circle is the only social layer this target leaves open.

## 13. Decisions needed

1. **Distribution**: stay GitHub-only (no Android Auto), or add Play to unlock
   cars and possibly Wear OS?
2. **Cloud**: is any Relay-operated backend ever acceptable? If not,
   cross-network Connect, Blend and a Relay-owned social graph are out;
   Last.fm-based social and the paired LAN circle remain possible.
3. **Content scope**: music only, or podcasts/audiobooks/internet radio as
   additional source kinds?
4. **Sources**: should the official catalog host a self-hosted server source
   (Subsonic) and which one; and should it ever host YouTube/SoundCloud-style
   sources?
5. **Desktop**: parity investment, or keep macOS as a local player?
6. **Third-party APIs**: Last.fm is already used; are LRCLib, ListenBrainz and
   Podcast Index acceptable (network, some with free keys, no Relay account)?
7. **AI**: explicitly out, or optional bring-your-own-key later?
8. **Social**: use Last.fm for friend activity, taste comparison and
   recommendations, keep LAN play-together for in-room sessions (recommended);
   or build the paired circle instead? A Relay-owned social graph is not
   recommended; the circle is the privacy-first fallback.
9. **Nearby**: add the opt-in nearby screen with post-pairing Last.fm
   enrichment? If yes, should it live under Settings -> Device Sync or get its
   own surface?
10. **Proximity transport**: local-only hotspot first, Wi-Fi Direct only if the
    join step is unacceptable, and the Quick Share SDK when it ships? No custom
    NFC.

## Sources

All accessed 2026-11-16.

1. Spotify Newsroom — [Lossless listening arrives on Spotify Premium](https://newsroom.spotify.com/2025-09-10/lossless-listening-arrives-on-spotify-premium-with-a-richer-more-detailed-listening-experience/) (2025-09-10; quality levels, 24-bit/44.1 kHz FLAC, DJ/Jam/AI Playlist/Mix/daylist links).
2. Spotify Support — [Spotify Connect](https://support.spotify.com/us/article/spotify-connect/) (device hand-off, same-Wi-Fi first connect, lossless switching).
3. Spotify Support — [View lyrics](https://support.spotify.com/us/article/lyrics/) (lyrics preview, share lyrics).
4. Musixmatch Pro — [Where will my lyrics be distributed?](https://support.musixmatch.com/pro/en/articles/175741-where-will-my-lyrics-be-distributed) (Spotify: plain + line-synced, not word-by-word).
5. Spotify for Developers — [Scopes](https://developer.spotify.com/documentation/web-api/concepts/scopes) (`streaming` scope, Web Playback SDK, Premium).
6. Spotify for Developers — [Developer Terms](https://developer.spotify.com/terms) (version 10, effective 2025-05-15).
7. librespot — [README](https://github.com/librespot-org/librespot) (Premium-only, DRM key change, "probably forbidden").
8. Spotify Support — [Start or join a Jam](https://support.spotify.com/us/article/jam/) (shared queue, guests add songs, QR/link, host Premium).
9. Spotify Support — [Social recommendations in playlists / Blend](https://support.spotify.com/us/article/social-recommendations-in-playlists/) (daily updates, up to 10 friends).
10. Spotify Newsroom — [daylist expands into new markets](https://newsroom.spotify.com/2024-09-04/daylist-new-languages-expanding-worldwide/) (updates several times a day, share cards).
11. Spotify Newsroom — [Wrapped methodology](https://newsroom.spotify.com/tag/wrapped-methodology/) (how listening rankings are computed).
12. Billboard — [Spotify audiobooks expand to 180 countries](https://www.billboard.com/pro/spotify-audiobooks-service-expand-180-countries-2026/) (2026-10-06).
13. Yahoo Tech — [Spotify's AI DJ gets new voices and personalities](https://tech.yahoo.com/audio/articles/spotify-ai-dj-gets-voices-140000051.html) (2026-11; secondary source).
14. Android Developers — [Distribute to cars](https://developer.android.com/training/cars/distribute) (media apps must be uploaded to Play Console and reviewed).
15. Finamp — [README](https://github.com/finamp-app/finamp) (Android Auto requires Play Store install; CarPlay; server model).
16. Metrolist — [README](https://github.com/MetrolistGroup/Metrolist) (features; content-source disclaimer).
17. Metrolist — [changelog 14.0.0](https://github.com/MetrolistGroup/Metrolist/blob/main/docs/changelog.md) (2026; Android Auto, casting, imports, podcasts, lyrics providers, AutoEQ).
18. OuterTune — [README](https://github.com/OuterTune/OuterTune/tree/lite) (local + YouTube Music, multiple queues, TTML, Android Auto; unmaintained).
19. Feishin — [README](https://github.com/jeffvli/feishin) (Navidrome/Jellyfin/Subsonic, smart playlists, lyrics, scrobbling).
20. Spotube — [README](https://github.com/team-spotube/spotube) (plugin architecture, KMP, time-synced lyrics, downloads).
21. LRCLib — [API documentation](https://lrclib.net/docs) (public synced-lyrics lookup).
22. ListenBrainz — [API documentation](https://listenbrainz.readthedocs.io/en/latest/users/api/) (recommendations API, 1 request/second, user token).
23. OpenSubsonic — [API documentation](https://opensubsonic.github.io/) (Subsonic-compatible browsing, search, lyrics, podcasts, transcoding, sonic similarity).
24. Android Developers — [Media3 Cast](https://developer.android.com/media/media3/cast) (Cast integration for Media3).
25. Exportify — [exportify.net](https://exportify.net/) (CSV export of Spotify playlists).
26. Podcast Index — [API client documentation](https://www.npmjs.com/package/podcastindex) (free API credentials; endpoint coverage).
27. Spotify Support — [Equalizer](https://support.spotify.com/us/article/equalizer/) (presets and band adjustment; unavailable when using Connect).

28. Last.fm API — [user.getRecentTracks](https://www.last.fm/api/show/user.getRecentTracks) (now-playing attribute, pagination, errors).
29. Last.fm API — [user.getFriends](https://www.last.fm/api/show/user.getFriends) (friend list, pagination, no user auth).
30. Last.fm API — [track.getSimilar](https://www.last.fm/api/show/track.getSimilar) (similar tracks from listening data, no auth).
31. Last.fm — [API Terms of Service](https://www.last.fm/api/tos) (non-commercial use, attribution, 100 MB cap, caching, rate limits).
32. Last.fm Support — [Last.fm is now independent](https://support.last.fm/t/last-fm-is-now-independent/118591) (May 2026; API continues).
33. Last.fm Support — [Private profiles and hidden recent listening](https://support.last.fm/t/private-profiles/2050) (community support thread; privacy setting).

34. Android Developers — [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission) (Android 16 opt-in, Android 17 enforcement, NsdManager and LAN sockets).

35. Android Developers — [Create P2P connections with Wi-Fi Direct](https://developer.android.com/develop/connectivity/wifi/wifi-direct) (API, permissions, group lifecycle).
36. Android Developers — [Use a local-only hotspot](https://developer.android.com/develop/connectivity/wifi/localonlyhotspot) (no-internet AP, NEARBY_WIFI_DEVICES on Android 13+).
37. Google for Developers — [Nearby Connections overview](https://developers.google.com/nearby/connections/overview) (offline P2P, encryption, byte/file/stream payloads, GMS).
38. Quick Share SDK announcement (secondary) — [heise](https://www.heise.de/en/news/Peer-to-peer-sharing-Google-brings-Quick-Share-to-Android-and-iOS-apps-11510100.html) and [SamMobile](https://www.sammobile.com/news/quick-share-come-third-party-android-iphone-apps/) (H1 2027, in-app sheet, Tap to Share, E2E, hotspot/Wi-Fi Direct).
39. AOSP — [NFC: removed support for P2P communication](https://android.googlesource.com/platform/packages/apps/Nfc/+/b3bbdf6000e2aa20f5b309fa043dbb9349d13f03) (Android Beam/NFC P2P removal).

Secondary sources are marked as such in the list. Product features can
change by market and plan.
