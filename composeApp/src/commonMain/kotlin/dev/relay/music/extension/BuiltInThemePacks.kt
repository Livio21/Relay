package dev.relay.music.extension

/**
 * Packs shipped with Relay. Community packs use the same restricted data format.
 * Built-ins demonstrate the v2 schema surface: transport arrangements, button shapes,
 * type casing/scale, rail visibility, and haptic remapping.
 */
val builtInThemePacks = listOf(
    ThemePack(
        id = "relay.theme.material3",
        name = "Material 3 Dark",
        colors = ThemeColors(
            ink = "#141218",
            panel = "#1D1B20",
            line = "#49454F",
            paper = "#E6E1E5",
            muted = "#CAC4D0",
            signal = "#D0BCFF",
            danger = "#F2B8B5",
        ),
        presentation = ThemePresentation(
            typography = ThemeTypography(ThemeFont.SANS, ThemeFont.SANS),
            chrome = ThemeChrome(borderWidthDp = 1, cornerRadiusDp = 12, fill = ThemeFill.PANEL, shadow = ThemeShadow.SOFT),
            icons = ThemeIcons(ThemeIconSet.SYMBOLS),
            transportLayout = ThemeTransportLayout.TEXT_ROW,
            haptics = ThemeHapticsMapping(mapOf("TRANSPORT_PRESS" to "SOFT")),
        ),
    ),
    ThemePack(
        id = "relay.theme.zune",
        name = "Zune",
        colors = ThemeColors(
            ink = "#0B0B0B",
            panel = "#151515",
            line = "#454545",
            paper = "#F4F4F4",
            muted = "#A8A8A8",
            signal = "#FF4F00",
            danger = "#FF453A",
        ),
        presentation = ThemePresentation(
            background = ThemeBackground.ARTWORK_BLEED,
            effects = listOf(ThemeEffect(ThemeEffectKind.GRAIN, 0.2f), ThemeEffect(ThemeEffectKind.VIGNETTE, 0.3f)),
            typography = ThemeTypography(ThemeFont.SANS, ThemeFont.SANS, titleUppercase = true),
            chrome = ThemeChrome(borderWidthDp = 2, fill = ThemeFill.OUTLINE, shadow = ThemeShadow.NONE),
            icons = ThemeIcons(ThemeIconSet.TEXT),
            transportLayout = ThemeTransportLayout.SPACED_BAR,
            haptics = ThemeHapticsMapping(mapOf("TRANSPORT_PRESS" to "THUD", "SELECT_TRACK" to "CLICK")),
        ),
    ),
    ThemePack(
        id = "relay.theme.ipod-classic",
        name = "iPod Classic",
        colors = ThemeColors(
            ink = "#DCDDE0",
            panel = "#EDEEF0",
            line = "#AEB2B8",
            paper = "#1C1C1E",
            muted = "#5A5E66",
            signal = "#2C6FD6",
            danger = "#C0392B",
        ),
        presentation = ThemePresentation(
            libraryLayout = ThemeLibraryLayout.LIST,
            playerLayout = ThemePlayerLayout.STANDARD,
            background = ThemeBackground.NONE,
            effects = emptyList(),
            typography = ThemeTypography(ThemeFont.SANS, ThemeFont.MONO, titleUppercase = false, typeScale = 0.95f),
            chrome = ThemeChrome(
                borderWidthDp = 1,
                cornerRadiusDp = 6,
                buttonRadiusDp = 24,
                fill = ThemeFill.PANEL,
                shadow = ThemeShadow.SOFT,
            ),
            icons = ThemeIcons(ThemeIconSet.TEXT),
            transportLayout = ThemeTransportLayout.CENTERED_ICONS,
            showIndexRail = false,
            haptics = ThemeHapticsMapping(mapOf("SELECT_TRACK" to "TICK", "TRANSPORT_PRESS" to "CLICK")),
        ),
    ),
)

fun isBuiltInThemePack(id: String): Boolean = builtInThemePacks.any { it.id == id }

/** Bundled packs cannot be overridden by a downloaded document with the same ID. */
fun mergedThemePacks(packs: List<ThemePack>): List<ThemePack> =
    builtInThemePacks + packs.filterNot { pack -> isBuiltInThemePack(pack.id) }
