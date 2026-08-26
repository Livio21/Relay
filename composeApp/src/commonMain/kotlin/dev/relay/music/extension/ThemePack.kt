package dev.relay.music.extension

const val THEME_PACK_MAX_BYTES = 64 * 1024

/** Data-only theme package. It cannot carry Compose code, fonts, scripts, or shaders. */
data class ThemePack(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val colors: ThemeColors,
    val presentation: ThemePresentation = ThemePresentation(),
)

data class ThemeColors(
    val ink: String,
    val panel: String,
    val line: String,
    val paper: String,
    val muted: String,
    val signal: String,
    val danger: String,
)

/** Host-rendered options only; a pack never provides executable UI or shader code. */
data class ThemePresentation(
    val libraryLayout: ThemeLibraryLayout = ThemeLibraryLayout.LIST,
    val playerLayout: ThemePlayerLayout = ThemePlayerLayout.STANDARD,
    val background: ThemeBackground = ThemeBackground.NONE,
    val backgroundAsset: String? = null,
    val effects: List<ThemeEffect> = emptyList(),
    val typography: ThemeTypography = ThemeTypography(),
    val chrome: ThemeChrome = ThemeChrome(),
    val icons: ThemeIcons = ThemeIcons(),
    /** Arrangement of the expanded player's transport controls; actions never change. */
    val transportLayout: ThemeTransportLayout = ThemeTransportLayout.TEXT_ROW,
    /** The signature progress rail beside library rows; some mimicry looks better without it. */
    val showIndexRail: Boolean = true,
    /** Per-action haptic pattern remapping, keyed by HapticAction name. */
    val haptics: ThemeHapticsMapping = ThemeHapticsMapping(),
)

enum class ThemeLibraryLayout { LIST, GRID }

enum class ThemePlayerLayout { STANDARD, COMPACT }

enum class ThemeTransportLayout { TEXT_ROW, CENTERED_ICONS, SPACED_BAR }

enum class ThemeBackground { NONE, ARTWORK_BLEED, ASSET }

enum class ThemeFont { SANS, SERIF, MONO }

enum class ThemeFill { OUTLINE, PANEL }

enum class ThemeShadow { NONE, SOFT, HARD }

/** Uses Relay's built-in glyphs; a pack never supplies executable or remote icon assets. */
enum class ThemeIconSet { TEXT, SYMBOLS }

data class ThemeTypography(
    val contentFont: ThemeFont = ThemeFont.SANS,
    val utilityFont: ThemeFont = ThemeFont.MONO,
    /** Zune-style always-uppercase titles. */
    val titleUppercase: Boolean = false,
    /** Global text-size multiplier, 0.85–1.25. */
    val typeScale: Float = 1f,
)

data class ThemeChrome(
    val borderWidthDp: Int = 1,
    val cornerRadiusDp: Int = 0,
    /** Button corners may differ from panel corners; -1 inherits [cornerRadiusDp]. */
    val buttonRadiusDp: Int = -1,
    val fill: ThemeFill = ThemeFill.OUTLINE,
    val shadow: ThemeShadow = ThemeShadow.NONE,
)

data class ThemeIcons(
    val set: ThemeIconSet = ThemeIconSet.TEXT,
)

/**
 * Maps HapticAction names onto HapticPattern names from the host catalog. Unknown keys or
 * values invalidate the pack — authors get loud failures instead of silently ignored maps.
 */
data class ThemeHapticsMapping(
    val actions: Map<String, String> = emptyMap(),
)

data class ThemeEffect(
    val kind: ThemeEffectKind,
    val strength: Float = 1f,
)

enum class ThemeEffectKind { GRAIN, VIGNETTE, GRAYSCALE, DUOTONE, BLUR }

fun ThemePack.validate(): String? {
    presentation.haptics.actions.let { haptics ->
        if (haptics.size > 16) return "Theme pack maps too many haptic actions."
        if (haptics.keys.any { key -> dev.relay.music.haptics.HapticAction.entries.none { it.name == key } }) {
            return "Theme pack names an unknown haptic action."
        }
        if (haptics.values.any { value -> dev.relay.music.haptics.HapticPattern.entries.none { it.name == value } }) {
            return "Theme pack names an unknown haptic pattern."
        }
    }
    return when {
        schemaVersion !in 1..2 -> "Theme pack schema is unsupported."
        !id.matches(Regex("[a-z0-9][a-z0-9._-]{0,127}")) -> "Theme pack ID is invalid."
        name.isBlank() || name.length > 128 -> "Theme pack name is invalid."
        listOf(colors.ink, colors.panel, colors.line, colors.paper, colors.muted, colors.signal, colors.danger)
            .any { !it.matches(Regex("#[0-9A-Fa-f]{6}")) } -> "Theme pack colors must use #RRGGBB."
        presentation.background == ThemeBackground.ASSET && presentation.backgroundAsset.isNullOrBlank() -> "Theme background asset is missing."
        presentation.background != ThemeBackground.ASSET && presentation.backgroundAsset != null -> "Theme background asset is not applicable."
        presentation.backgroundAsset?.let { it.length > 256 || it.contains("..") || !it.matches(Regex("[A-Za-z0-9._/-]+")) } == true -> "Theme background asset is invalid."
        presentation.effects.size > 4 -> "Theme pack has too many effects."
        presentation.effects.any { it.strength !in 0f..1f } -> "Theme effect strength is invalid."
        presentation.chrome.borderWidthDp !in 0..3 -> "Theme border width is invalid."
        presentation.chrome.cornerRadiusDp !in 0..24 -> "Theme corner radius is invalid."
        presentation.chrome.buttonRadiusDp != -1 && presentation.chrome.buttonRadiusDp !in 0..24 -> "Theme button radius is invalid."
        presentation.typography.typeScale !in 0.85f..1.25f -> "Theme type scale is invalid."
        else -> null
    }
}
