# Default view — Metro panorama, Relay anatomy

_Design direction and implementation record, 2026-11-16. The first slice is
implemented; remaining items are listed in §0._

Interactive: [`docs/mockups/default-view-interactive.html`](mockups/default-view-interactive.html)
(PNG beside it; `?page=1` opens Library, `?menu=1` opens the section menu).
The earlier static `default-view.html` is superseded by the interactive one.

## 0. Implementation status

Shipped on 2026-11-16:

- Default palette, 48/200 section titles, 40/200 player title, 25/300 artist,
  Material icon set, and 48dp icon actions.
- Metro hub with a 64dp next-section **title-only** sliver and a full-screen
  section menu; the current title opens the menu, the next title navigates.
- Now Playing: a full-width, borderless square (`ContentScale.Fit`, no crop)
  with 16dp of breathing room below the hub; one media row (shuffle, previous,
  play, next, repeat); the expanded player is the Now Playing pager page, so a
  horizontal drag moves the whole view like the other sections. The artwork is
  fixed (no vertical scroll), and queue/lyrics/options open as full-width
  bottom drawers with an invisible reserved base. Opening one grows the panel
  and translates the whole player upward without reflowing it; the player may
  overflow its container and remain visible behind the frosted title strip.
  Drawer content crossfades between queue/lyrics/options and reserves bottom
  padding so the last row is never cropped.
- Library defaults to Albums; 3-up / 2-up / list density picker, no gutters,
  1dp art borders, current-album accent, artist sections with 2.5-card
  horizontal rails, and a small recently-played strip at the bottom.
- `RelayMarqueeText`: the universal one-line title/row primitive. It never
  changes height; overflowing text gets a right-edge fade and a looping scroll
  (static under reduced motion). Used in the player, track rows, album cards,
  playlists and section rows.
- Offscreen Compose screenshot tests render Now Playing, Library and the
  section menu into `composeApp/build/screenshots/`.

Also in this slice: the section menu blurs the underlying content, uses a
translucent overlay and slides/fades; Settings submenu back returns to the
Settings root; `CollectionHeader`/`CollectionDetail` is the shared album,
artist and playlist detail; the mini player uses a blurred artwork underlay.
Still pending: the remaining old-screen restyle (Insights, Settings, source
lists), ambient artwork parallax, a
bundled thin font, and the full Android device matrix. Android compile, unit
tests and debug assemble pass; the hub and section menu were smoke-tested on
device.

## 1. Direction

Metro in the bones, not a Windows Phone/Zune clone: a horizontal panorama
where the next section's title peeks at the edge, giant lowercase thin section titles, full-square
album art that is never cropped, border-only tile grids, circular transport
icons, high contrast, and frosted overlays. Relay's library, album/artist
pages, queue, lyrics and settings stay.

This is the **default theme**, not the theme engine's limit. Theme packs can
override every token and choose the host anatomy variants in
`THEME_ENGINE_SCOPE.md`.

## 2. Type and tokens

| Token | Value | Notes |
|---|---|---|
| Ink | `#050505` | Canvas |
| Panel | `#0E0E0E` | Rows, mini player base |
| Line | `#414141` | Page, tile and row borders; `LineStrong #6A6A6A` for control outlines if 3:1 is enforced |
| Paper | `#FBFBF5` | Primary text; 18:1 |
| Muted | `#C4C4BA` | Secondary text; ~11:1 |
| Accent | `#FF4F00` | Current item, progress, primary action |
| Accent ink | `#090909` | On the accent; ~5:1 |

Type:

- **Section title**: 48 / 200, lowercase, tight tracking; the giant title is
  the section menu trigger and bleeds with the panorama.
- **Track title**: 40 / 200.
- **Artist**: 25 / 300.
- **Content titles**: 15–16 / 600; 3-up tiles 13 / 600.
- **Metadata**: 9–10 mono; **section sublabels** 8 mono uppercase.
- Avoid thin weights below 25px; body and small text stay 400+.

Spacing uses 8 / 13 / 21 / 34 (Fibonacci, ≈φ). Now Playing targets a
61.8% stage / 38.2% information split; the first slice uses a full-width
borderless square with a 16dp top gap and flowing info, with the exact split
still pending.

### Text overflow

`RelayMarqueeText` is the universal one-line primitive. It measures its text
against the available width; fitting text stays static, while overflow gets a
right-edge fade and a slow loop (static under reduced motion). It never changes
row height and keeps the full string in semantics. It is applied to the player
title/artist/album, track rows, album cards, playlists, artist rows and queue
labels.

The thin face is a **host-bundled open font** (Inter Display Light or IBM Plex
Sans Light, SIL OFL); the prototype uses the system thin weight as a stand-in.
Theme packs never supply fonts. Fallback is the system sans at 200/300.

## 3. Panorama

- Six sections: `now playing`, `library`, `playlists`, `insights`, `sources`,
  `settings`.
- Each page is **100% of the viewport**. The next section's **content is never
  previewed**; only its title appears.
- The next title is a **64px sliver at the right edge of the hub**, clipped by
  the screen. It is muted, 52/200, and is a button to the next section. This is
  the Windows Phone panorama cue: you see the start of the next heading, not a
  slice of the next screen.
- The current title is paper; the next-title sliver is muted. The current title
  plus chevron opens the section menu; the next sliver navigates forward. The
  title strip sits above the content and draws the current cover blurred behind
  a translucent ink scrim, so art moving up behind it stays legible.
- Swipe/drag is the existing HorizontalPager gesture, not a new navigation
  model. Snap to each page; no free-scrolling.
- There is **no fixed top bar**. Each page owns a hub: current title, next-title
  sliver, then a 48px sub row with breadcrumbs on the left and page actions on
  the right.
- Tapping the giant title opens the **section menu**. Once a page scrolls, a
  compact frosted header appears with the section name and the same menu
  trigger; the prototype includes it, and it is required in the implementation.
- The last page has no next-title sliver; it is not a dead strip.

### Now Playing

- Hub, then a body split 61.8 / 38.2.
- The album art is a **full-width square** (1:1), edge to edge with no border or
  side padding, rendered with `ContentScale.Fit` so it is never cropped. It is
  fixed: the player does not vertically scroll. The expanded player is the
  pager's Now Playing page, so a horizontal swipe drags the page itself. A 16dp
  gap separates art from the hub title; the header rule is removed on Now
  Playing. Drawers translate the content up by their measured height rather
  than scrolling it.
- 1px `Line` above and below the square; no side padding.
- Info: track 40/200, artist 25/300, meta 9 mono, 4px accent progress, times,
  circular transport row (48px, play 60px accent) and a secondary icon row.
- Parallax: the ambient copy and the square art translate at different rates as
  the pager moves. Keep it under ~6px on the square and ~10px ambient.

### Library

- Default **3-up** tile wall: no gutters, 1px borders, square art, 13px title,
  metadata hidden at this density. The current album gets a 2px accent inset
  border, not a "featured" label.
- View toggle cycles **3-up → 2-up → list**:
  - 3-up: 13px title, no meta.
  - 2-up: 15px title, meta visible.
  - list: 52px art, 68px row, 15px title, meta visible.
- 2-up and list scroll vertically; 3-up fits the demo's 12 albums.
- No gaps; the border is the separation. The grid is full page width.

### Mini player

73px, `Panel` with a 1px top border in the first slice (backdrop frost is
pending), 3px accent progress, 64px art flush left, 14px title, 48px icon
actions. Hidden on Now Playing; appears on the other sections.

### Sections

- Playlists: rows with 48px art, title, count; add/more icons.
- Insights: 76/200 big stat, 28/200 secondary stats, numbered top artists.
- Sources: 48px source tile, name, version/status.
- Settings: borderless 64dp text rows with thin lowercase labels and chevrons; no card chrome. Expanded choice and value rows use the same thin label/value/chevron rhythm; Album Charts, catalog/installed extension rows, extension detail and trusted repository rows are borderless too.

## 4. Section menu

- Opened by the giant section title; the underlying screen blurs and the menu
  slides/fades in.
- The current cover is full-bleed from the corners behind the menu: top 30%
  carries the cover and the local-profile signature (the current section is already
  highlighted in the list, so it is not repeated); the middle 60%
  is the lower section list; the bottom 10% is empty space with a translucent
  line-art tattoo.
- The list's dark background fades in at its top and out at its bottom so it
  never reads as a hard panel over the cover. The title strip uses the current
  cover blurred under a translucent dark gradient.
- Rows: icon, **28/200 lowercase label**, 8px uppercase sublabel, chevron;
  weighted rows; current section in accent. No close button.
- Six sections; tapping the top or bottom art outside the list closes the menu.
- This is a navigation menu, not a settings sheet; no nested content.

## 5. Frost

- Frosted surfaces: the section menu now blurs the underlying screen and uses
  a translucent overlay. The mini player is still a `Panel`; its frost is
  pending. The top title area stays flat so the thin headline keeps contrast.
- Use a dark translucent base plus blur; **opaque fallback** when blur is
  unavailable or reduced-transparency is requested.
- Never place small thin text directly over a busy blurred image without a
  solid or near-solid base.
- Performance cap: blur only these two surfaces; no per-row blur.

## 6. Icons

- Material Icons (Apache-2.0): play, pause, previous, next, shuffle, repeat,
  heart, queue, lyrics, more, search, grid/list/agenda, add, close, chevrons.
- 24 base, 28 transport, 32 play; 48dp targets; play 60. Circular outline for
  transport only; tiles, rows and overlays stay rectangular.
- Every icon-only action gets a semantic label.
- Add `THIRD_PARTY_NOTICES.md` attribution; icons live in a small host-owned
  set, not in theme packs.

## 7. Parallax

- Horizontal pager offset drives the Now Playing ambient/art translation and
  the next-title sliver. No gyroscope in the default.
- Respect reduced motion: no translation; the panorama still swipes.
- One `graphicsLayer` translation per layer, driven by pager offset; no
  per-frame allocations; no continuous sensor.
- Optional tilt is a later toggle, not this default.

## 8. Sizes and ratio

| Surface | Art | Title |
|---|---|---|
| Now Playing stage | full width × 1:1, borderless, 16dp top gap | 40 |
| Library 3-up | ~130 | 13 |
| Library 2-up | ~195 | 15 |
| Library list | 52 | 15 |
| Mini player | 64 | 14 |
| Section title | — | 48 |
| Icons | 24 / 28 / 32 | — |

Vertical split: 61.8 / 38.2. Type growth: 40 / 25 / 16 / 13 / 9. Spacing:
8 / 13 / 21 / 34. Tiles are square at every density; no crop in the hero.

## 9. Implementation map

| Area | Files | Change |
|---|---|---|
| Tokens/font | `ui/RelayTheme.kt` | New palette and thin display roles — done; bundled font and `LineStrong` pending |
| Font asset | `composeResources` / Android assets | Bundle Inter Display Light or IBM Plex Sans Light + OFL notice — pending; system thin weight used |
| Icons | `ui/RelayIcons.kt` | Bounded Material vectors + Apache-2.0 notice — done |
| Panorama | `App.kt` + `MetroChrome.kt` | Full-width pager retained; clipped 64dp next-title sliver and next action — done |
| Hub/menu | `MetroChrome.kt`, `App.kt` | Frosted title strip, giant title trigger, next-title sliver, full-bleed 30/60/10 blurred section menu — done |
| Now Playing | `PlayerScreens.kt`, `App.kt` | Full-width borderless `Fit` art in the pager page, five-control media row, full-width queue/lyrics/options drawers — done; ambient artwork parallax and exact 61.8/38.2 split pending |
| Library | `BrowseScreens.kt`, `App.kt` | Album-first 3-up/2-up/list density, borderless titles with marquee, current accent, bottom recent strip, artist rails — done |
| Mini player | `PlayerScreens.kt` | 73px icon mini, 64px art, blurred artwork underlay — done |
| Frost | `App.kt`, `PlayerScreens.kt` | `Modifier.blur` for the section menu and a blurred artwork underlay for the mini — done |
| Navigation | `App.kt` | Section order, menu actions, back closes the menu, default Now Playing — done |
| Theme packs | `extension/ThemePack.kt` | New defaults load; v2 packs still load; new anatomy overrides pending |
| Type | `ui/RelayTheme.kt`, `App.kt` | Bundled Inter variable font, weights 100–900 — done |
| Tests | `commonTest`, `desktopTest`, `androidTest` | 127 shared tests, 62 Android unit tests, and offscreen screenshots for Now Playing, Library, Insights, Sources, the sections menu and Settings; full device matrix pending |

Compose's `HorizontalPager` already shows one full page, which is what this
design needs. The next-title sliver is an overlay inside the hub, clipped by
the screen; it must not shrink the page or render the next page's content. Use
a nested vertical scroll for 2-up/list. Do not fake the sliver with a screenshot
layer.

## 10. Order

1. Tokens, thin font, icons, spacing (M).
2. Panorama + hub + section menu (done); sticky compact header pending (M).
3. Now Playing contained art + circular transport (done); ambient/exact split pending (M).
4. Library 3-up/2-up/list and current accent (done).
5. Frost + parallax + reduced motion (M, device verification).
6. Restyle playlists/insights/sources/settings and album/artist pages (M).
7. Tablet/landscape: 3/4-up tiles and a wider next-title sliver, still title-only (M).

## 11. Risks

- **Long localized titles**: `now playing` fits at 52 with the 64px sliver; Albanian
  strings can be longer. Use a responsive clamp, allow one-line clipping with
  the full title in the menu, and test both locales.
- **Title sliver legibility**: the next title is clipped on purpose; it is not
  a tab and has no label. Use muted color, a wide enough 64px sliver, edge
  clipping, and `aria-label` so it is discoverable and accessible.
- **No content peek**: at rest the next section must show only its title. If the
  pager is mid-swipe, content moves naturally; do not add a persistent content
  preview.
- **Vertical scroll**: the giant title scrolls away; the compact sticky header
  is required, not optional.
- **Blur**: Android `RenderEffect` blur is API-dependent and expensive; test on
  low-end devices and keep the opaque fallback.
- **Thin font**: system thin weights differ; bundle one open face for
  consistency and licence it.
- **Circular transport**: a deliberate Metro exception to the rectangular
  system; still 48dp and high contrast.
- **Last page**: no next-title sliver; do not leave an empty page strip.
- **Menu focus**: focus trap, Escape, and screen-reader labels are part of the
  component, not a later polish.

## 12. Not doing

- No Windows Phone/Zune logos, exact proprietary motion or OS chrome.
- No gyroscope parallax by default.
- No per-pixel theming or arbitrary CSS/Compose.
- No cropping the hero square to fill the stage.
