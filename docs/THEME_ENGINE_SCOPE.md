# Theme engine scope — extensive skins without executable code

_Research and scoping note, 2026-11-16. Not a plan change. The active plan is
still Phase 10; this describes what a post-v1 theme engine would need._

Visual references: [`theme-directions.html`](mockups/theme-directions.html) (five
filtered directions) and [`theme-studio.html`](mockups/theme-studio.html) (the
recipe editor); PNGs beside them. The proposed default anatomy is specified in
[`DEFAULT_VIEW.md`](DEFAULT_VIEW.md).

## 1. Short answer

The current engine is a **global skin**: one palette, one shape language, one
type scale, and a handful of layout switches. That is enough for the existing
Material 3, Zune and iPod Classic packs to change mood, but not enough to
approach the reference apps.

To get close to Zune, iPod, Apple Music/iTunes, Spotify and Google Play Music,
the host has to expose **component-level slots** and **host-rendered shells** —
navigation, player, home, list/card anatomy, artwork treatment, type roles and
motion. The iconic parts are orthogonal traits, so a pack can mix them (orange
pivot + click wheel + own photo). Theme packs still stay data-only; they choose
from bounded host variants instead of shipping UI code.

Realistically that buys **roughly 65–85% of each look**. Exact clones are not
realistic: the fonts (Segoe, Chicago, SF Pro, Circular, Roboto variants), the
brand marks and the proprietary motion/icons are not shippable, and copying
them is a licensing problem. The target is *evocation*, not impersonation.

The biggest structural gaps are:

1. a fixed pager instead of selectable navigation shells;
2. one player anatomy instead of player presentations;
3. no home/shelves surface;
4. per-screen, per-component shapes and type roles instead of globals;
5. light/dark system bars and artwork-derived backgrounds;
6. an authoring path: preview, editor, gallery, contrast validation.

## 2. What the engine exposes today

| Area | Current surface | Consequence |
|---|---|---|
| Colors | 7 global roles: ink, panel, line, paper, muted, signal, danger | No light/dark pair, surface levels, scrim, overlay, on-accent, selection, header/player/tab colors |
| Type | 3 font families (sans/serif/mono), 4 roles (title/track/meta/utility), global scale and uppercase | No weight/line-height per role, no display face, no numerals, no font catalog |
| Shape | Global border width, panel radius 0–24, button radius, fill (outline/panel), shadow (none/soft/hard) | No per-component radius/border/elevation, no artwork/sheet/nav/input shapes, no density |
| Layout | Library LIST/GRID; player STANDARD/COMPACT; transport TEXT_ROW/CENTERED_ICONS/SPACED_BAR; index rail on/off | No nav shell, home, shelves, split screen, cards, click wheel, sheet, header variants |
| Background | None, artwork bleed (fixed blur/alpha), packaged asset | No dominant-color or gradient derivation, no per-screen scrim, no light surfaces |
| Effects | Global grain, vignette, grayscale, duotone, blur | No per-component backdrop blur, artwork gradients, reflections or shadows |
| Artwork | Fixed 48/64/112dp squares; global radius | No size/shape/shadow/reflection/blur roles |
| Icons | TEXT or SYMBOLS, host glyph set | No line/filled icon sets, no wheel, no transport icon family |
| System | Status bar fixed dark; widget uses its own fixed palette | Light themes render with dark system bars; widget ignores the pack |
| Authoring | JSON import, apply, remove; 64 KB cap; schema v2 | No preview, editor, gallery or per-pack contrast validation |

The theme is applied through snapshot state in `RelayTheme.kt`; screens consume
`RelayColors`, `RelayType` and `RelayChrome`, but component anatomy (rows,
player, headings, tabs) is hardcoded. That is why more colors alone cannot make
Spotify or Apple Music.

## 3. What the reference looks actually need

| Look | Signature | What must be host-rendered | Not realistic |
|---|---|---|---|
| **Zune** | Black, orange, large art, artwork bleed, spaced text transport, pivot navigation | Pivot shell, big-art player, artwork-derived background, wide uppercase display type, motion preset | Segoe UI, Zune wordmark, exact twist transitions |
| **iPod Classic** | Silver/white, blue selection, split screen, click wheel, small now-playing | Light palette + light system bars, split shell, click-wheel control with rotation/center, compact list density | Chicago/Myriad fonts, Apple marks, exact wheel physics |
| **Apple Music / iTunes** | Light/dark, large rounded artwork, blurred/gradient artwork background, tabs/sidebar, now-playing sheet, icons | Bottom tabs/sidebar, now-playing sheet, rounded per-component shapes, backdrop blur/gradient, large artwork roles, icon family | SF Pro, SF Symbols, Apple marks, exact transitions; desktop iTunes needs a table/sidebar shell |
| **Spotify** | Near-black, green accent, rounded cards, home shelves, compact bar, bottom tabs/sidebar | Home/shelves, rounded cards, gradient header, tabs/sidebar, compact player, icon family | Circular font, Spotify marks, exact recommendation layout |
| **Google Play Music** | White/orange, Roboto, top tabs, blurred header, cards, now-playing bar | Tabs, header blur/tint, cards, per-role Material type, light system bars | Retired product; exact assets |

Fonts are the hardest line. The plan and the pack format deliberately exclude
font files and remote fonts. On Android the host has Roboto/Noto/monospace;
SF Pro, Circular, Segoe and Chicago are not available. A host-bundled,
open-licensed font catalog (for example Inter, IBM Plex, Roboto, a grotesque and
a slab) is the realistic route; each pack selects from that catalog. Bundling
licensed fonts is a separate legal/product decision.

## 4. The UI parts that must change

### 4.0 The trait model: iconic parts as composable slots

The references are not five separate UIs. Their memorable parts are **traits**
on the same axes, and the engine should let a pack mix them:

| Trait | Host-rendered options | Iconic use |
|---|---|---|
| Shell | Pager, bottom tabs, sidebar, pivot, split | Pivot → Zune; tabs → Apple/Spotify/Play; sidebar → iTunes; split → iPod |
| Library | List, cards, shelves, menu, grid | Menu → iPod; cards/shelves → Spotify/Play; list → Zune/Apple |
| Player | Bar, hero, sheet, split, wheel | Hero → Zune/Apple; wheel → iPod; bar → Spotify/Play; sheet → Apple |
| Artwork | Flat, rounded, bleed, stack | Bleed → Zune; rounded → Apple/Spotify; flat → iPod |
| Type voice | Grotesque, geometric, humanist, mono, serif; case + scale | Wide uppercase geometric → Zune; dense humanist → iPod; heavy grotesque → Spotify |
| Accent | Fixed, artwork-derived, duotone | Orange → Zune/Play; blue → iPod; green → Spotify; red/pink → Apple |
| Surface | Flat, hairline, soft, glass | Flat → Zune/iPod; soft/glass → Apple/Spotify |
| Background | Solid, art bleed, gradient, texture, photo | Bleed → Zune; gradient → Apple/Spotify; tint → Play |
| Motion | Cut, fade, slide, pivot, sheet | Pivot → Zune; cut → iPod; sheet → Apple |
| Icons | Text, line, filled | Text → Zune/iPod; line → Apple; filled → Spotify/Play |
| Density | Compact, regular, airy | Compact → iPod; airy → Zune |

A pack is a **recipe** on these axes plus the token details. The reference looks
are presets, not constraints: a user can take the orange pivot, swap in the
click wheel, and use their own photo background. That is the creativity the
current color-only engine cannot express.

A recipe can be expressed as data:

```json
{
  "extends": "orange-pivot",
  "traits": {
    "shell": "PIVOT",
    "library": "CARDS",
    "player": "WHEEL",
    "artwork": "BLEED",
    "type": { "voice": "GEOMETRIC", "case": "UPPER", "scale": 1.1 },
    "accent": { "mode": "ARTWORK", "fallback": "#FF4F00" },
    "surface": "FLAT",
    "background": "ART_BLEED",
    "motion": "PIVOT",
    "icons": "TEXT",
    "density": "REGULAR"
  }
}
```

`extends` is an editor preset, not inheritance at runtime; a saved pack is a
full recipe. Unknown traits or values reject the pack instead of silently
falling back.

### 4.1 Navigation shell and page heading

Today: one horizontal pager with a fixed six-page order and a fixed heading
(`previousTitle | title | nextTitle`, or `<- BACK`).

Needed: a host-rendered **shell** choice:

- pager (current);
- bottom tabs (Apple Music, Spotify, Play Music);
- sidebar (desktop iTunes/Spotify, tablets);
- pivot (Zune: category title, horizontal arrows, content);
- classic split (iPod: now-playing above, menu below, wheel at the bottom).

The heading also needs variants: plain title, large title, nav bar, pivot title,
and a back affordance that matches the shell. Themes should select a shell,
never draw one.

### 4.2 Library, home and card anatomy

Today: LIST or GRID with one row anatomy (4dp rail, 48dp art, title/meta,
duration, overflow).

Needed:

- row density and trailing actions (Apple list vs Spotify list vs iPod menu);
- artwork size/radius/presence per list template;
- card grids and home shelves (Spotify, Apple, Play);
- a Home surface with recently played, mixes and library shortcuts — the
  references assume one; Relay currently has none;
- classic split rows and cover flow (cover flow exists only as a landscape
  player).

### 4.3 Player presentations

Today: compact bar (64dp row, text transport) and an expanded player with three
transport arrangements; landscape cover flow.

Needed:

- compact bar variants (mini, full-width, floating rounded);
- big-art player (Zune, Apple full screen);
- split player (iPod);
- click wheel (iPod) — a new interactive host control with a rotating ring,
  center action and menu button;
- now-playing sheet (Apple/Spotify);
- transport icon families, progress styles (thin line, rounded bar, segmented,
  numeric), and artwork treatments.

### 4.4 Artwork and background

Today: fixed square artwork; global radius; artwork bleed with a fixed
blur/alpha; packaged background asset.

Needed: artwork roles (row, card, hero, player, sheet) with size, radius,
shadow, reflection and blur; background modes (solid, dominant color, linear
gradient, blurred artwork, packaged asset, pattern) with per-screen scrims and
contrast limits. Apple Music and Spotify depend on this more than on color
values; Zune depends on art bleed.

Gradients and heavy backdrop blur are a deliberate exception to Relay's flat
default. They would be opt-in, capped theme effects with a static fallback, and
the plan's "no gradients" rule needs a narrow amendment before shipping them.
The direction mockup uses them only to show what those references require.

### 4.5 Color roles

Today: 7 colors.

Needed: a semantic scheme, ideally light/dark pairs: canvas, surface,
surface-raised, surface-sunken, text, muted, border, accent, on-accent,
selection, progress, danger, scrim, overlay, player, header, tab bar, input.
Theme validation should reject combinations below contrast thresholds rather
than letting a pack ship an unreadable UI.

### 4.6 Typography

Today: 3 families, 4 styles, global scale/case.

Needed: a host font catalog and 5–6 roles (display, title, body, meta, utility,
numeric) with family, weight, size, line height, letter spacing and case per
role. Zune needs wide uppercase display; Apple needs sentence case and a
compact meta style; iPod needs a small, dense menu face; Spotify needs heavy
headings.

### 4.7 Shape, border and elevation

Today: one panel radius, one button radius, one border width, one elevation
choice.

Needed: per-component shape tokens — artwork, card, button, chip, input, list
row, sheet, tab bar, player, dialog — plus border width/color and elevation
per role. Rounded Apple/Spotify cards and flat Zune lines should coexist
without a global switch.

### 4.8 Icons and controls

Today: TEXT or SYMBOLS; text transport.

Needed: host icon families (line, filled, text/symbol), sizes, and a control
catalog: transport, tabs, overflow, back, search, wheel. No brand glyphs and no
pack-provided icon files; packs select from host-owned sets.

### 4.9 Motion

Today: fixed transitions; effects.

Needed: a bounded motion preset (none, fade, slide, pivot, sheet, cover-flow)
with reduced-motion fallback. Zune's pivot and Apple's sheet are part of the
look; they cannot come from color tokens.

### 4.10 System surfaces

Today: status bar style is hardcoded dark (`SystemBarStyle.dark`,
`MainActivity.kt:681`); the Glance widget uses a fixed palette; the live
wallpaper has its own preset.

Needed: derive status/navigation bar icon appearance from the theme surface;
let the widget read the theme palette; decide whether the wallpaper preset
inherits theme colors. Otherwise light themes look broken at the screen edges.

### 4.11 Authoring, preview and validation

Today: import JSON, apply, remove. No preview or editor.

Needed for "users can be really creative":

- schema v3 with defaults and migration from v1/v2;
- an in-app preview across representative screens before applying;
- a token editor (or at least a generated JSON form) with live preview;
- a gallery of built-in and community packs;
- validation: contrast, size/radius bounds, asset size, unknown fields;
- export and share; a public schema/README for authors.

Without this, extensive theming is a developer-only feature.

### 4.12 Theme Studio and the personal layer

Tokens alone do not make theming creative. The missing piece is an editor that
turns the trait catalog into something a person can use:

- **Live preview** across library, player and home; the preview uses the edited
  pack while the editor chrome stays Relay's neutral default.
- **Start from a preset, then swap traits**: orange pivot → wheel silver →
  glass red; every change is a bounded choice, never a CSS field.
- **Token controls**: accent swatches, artwork-derived accent, type voice/case/
  scale, surface, density, motion, icon set.
- **Personal layer**: local profile name and avatar, a validated background
  photo, "show greeting", and artwork-derived colors. These are local to the
  device and can be included in a pack or kept private.
- **Save, export, share**: JSON pack export; import into the existing signed
  trust flow for catalogs or a local file; gallery of community packs.
- **Validation while editing**: contrast warnings, target-size checks, effect
  caps, and a "preview on device" action.

The editor is a projection of the schema. It cannot create new anatomy; it can
only compose the host's traits. That keeps packs data-only and the app
maintainable.

## 5. Proposed schema v3 (sketch)

The recipe from 4.0 chooses the anatomy; the token sketch below styles it. A
v3 pack carries both, with defaults for anything omitted.

```json
{
  "schemaVersion": 3,
  "id": "author.pack",
  "name": "Pack name",
  "colors": {
    "light": { "canvas": "#FAFAFA", "surface": "#FFFFFF", "text": "#111111", "accent": "#FA2D48" },
    "dark":  { "canvas": "#121212", "surface": "#181818", "text": "#F5F5F5", "accent": "#1DB954" }
  },
  "type": {
    "display": { "font": "sans", "weight": 700, "size": 34, "case": "upper", "tracking": 2 },
    "title":   { "font": "sans", "weight": 700, "size": 24, "case": "sentence" },
    "body":    { "font": "sans", "weight": 400, "size": 15 },
    "meta":    { "font": "sans", "weight": 400, "size": 12 },
    "utility": { "font": "mono", "weight": 400, "size": 11, "case": "upper" }
  },
  "shape": {
    "artwork": { "radius": 12, "shadow": "soft" },
    "card":    { "radius": 8, "border": 0, "fill": "surface" },
    "button":  { "radius": 24, "fill": "accent" },
    "sheet":   { "radius": 18 }
  },
  "layout": {
    "shell": "BOTTOM_TABS",
    "library": "CARDS",
    "player": "MINI_BAR",
    "home": "SHELVES",
    "heading": "LARGE_TITLE"
  },
  "artwork": { "background": "BLURRED_ART", "scrim": 0.45, "dominant": true },
  "icons": "FILLED",
  "motion": "SHEET",
  "effects": [{ "kind": "GRAIN", "strength": 0.15 }],
  "assets": { "background": "token.png" }
}
```

Every value must be an enum or a clamped number. The host resolves fonts,
icons, colors, shapes and effects; a pack never supplies code, scripts, shaders,
remote fonts or brand assets. v2 packs keep applying with v3 defaults.

## 6. Per-look feasibility

| Look | Verdict | What gets close | What stays out |
|---|---|---|---|
| Zune | ~85% | black/orange, wide uppercase type, art bleed, big-art player, pivot shell, spaced transport | Segoe UI, Zune marks, exact pivot motion |
| iPod Classic | ~70% | silver/white, blue selection, split shell, click wheel, compact list, cover flow | Chicago/Myriad, Apple marks, exact wheel physics/click |
| Apple Music/iTunes | ~70% | light/dark, large rounded art, blurred/gradient background, tabs/sidebar, sheet, icon family | SF Pro, SF Symbols, Apple marks, exact transitions; iTunes desktop needs desktop shell |
| Spotify | ~65% | dark/green, rounded cards, home shelves, tabs/sidebar, compact player, gradient header | Circular font, Spotify marks, exact editorial layout |
| Google Play Music | ~75% | light/orange, Roboto, tabs, header tint, cards, bar | exact Material 1 assets; product is retired |
| Exact clone | 0% | — | fonts, logos, proprietary assets, motion; licensing |

Percentages are for the **visual language**, not pixel equality. A theme with
the right palette, type, shape and one signature layout gets most of the way;
the last 20–30% is proprietary assets and fonts.

## 7. Phases and effort

1. **Foundation** (M): semantic color scheme with light/dark, type roles/catalog,
   per-component shape/elevation, artwork roles, system bar appearance, pack
   contrast validation, preview harness, and a minimal token-only Studio. This
   makes every existing screen skinnable and Zune/Play directions close.
2. **Shells and player** (L): bottom tabs, sidebar, pivot, split; mini/full/sheet
   players; icon families. Unlocks Apple/Spotify/iPod structure.
3. **Home and cards** (L): Home surface, shelves, card templates, large-title
   header. Spotify/Apple/Play need this more than extra colors.
4. **iPod/Zune specials** (L): click wheel, split screen, pivot motion, art
   bleed/gradient pipeline.
5. **Authoring** (L): schema v3, full trait Studio, gallery, docs, import/export.
   Ship the token-only Studio with Phase 1 so people can start personalizing
   before the shells land.

Total is XL as one project; split across releases. A realistic "feature
complete" is a bounded catalog: 4–6 shells, 4–5 player presentations, 4 library
templates, 5–6 type roles, ~30 component tokens, an editor and a gallery — not
every pixel.

## 8. Rules and risks

- **Accessibility**: validate contrast per pack, keep 48dp targets, respect font
  scale, focus visibility and reduced motion. A theme must never remove labels
  or semantics. The current `Line` token is 1.54:1 on Ink; a strict 3:1
  non-text rule would require a theme-level border role, not the current token.
  Visual pill sizes (as in the Apple/Spotify directions) do not change hit
  targets; the host keeps a 48dp hit area around the visual control.
- **Performance**: blur, shadows and gradients are expensive on low-end devices;
  cap effects and provide a static fallback.
- **Maintenance**: every shell/slot is host code with cross-screen tests. Keep
  the catalog small; theme packs cannot add new anatomy.
- **Legal**: no brand logos, wordmarks or proprietary fonts; evocations only.
- **Migration**: v1/v2 packs must keep loading; unknown v3 fields are rejected
  loudly; defaults fill missing roles.
- **Testing**: golden screenshots per shell/player plus contrast checks and a
  device matrix; a pack that applies must not break touch targets or scroll.

## 9. Recommendation

Do the **foundation** first: semantic colors, type roles, per-component shape,
artwork/background roles and system bars. It makes the app genuinely skinnable,
unlocks the Zune and Play Music directions, and avoids spending months on
shells before the tokens are right. Then add shells/player variants, Home, the
iPod wheel, and finally the editor/preview.

Exact clones are not the goal; a recognizable, coherent look with Relay's
anatomy is. That is also the only version that stays maintainable and legal.
