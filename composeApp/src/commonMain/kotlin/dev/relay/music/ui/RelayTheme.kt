package dev.relay.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import dev.relay.music.extension.ThemePack
import dev.relay.music.extension.ThemeFill
import dev.relay.music.extension.ThemeFont
import dev.relay.music.extension.ThemeIconSet
import dev.relay.music.extension.ThemeEffect
import dev.relay.music.extension.ThemeEffectKind
import dev.relay.music.extension.ThemeBackground
import dev.relay.music.extension.ThemeLibraryLayout
import dev.relay.music.extension.ThemePlayerLayout
import dev.relay.music.extension.ThemeTransportLayout
import dev.relay.music.extension.ThemeShadow
import dev.relay.music.extension.validate
import dev.relay.music.haptics.HapticAction
import dev.relay.music.haptics.HapticPattern
import coil3.compose.AsyncImage

/**
 * Snapshot-state palette: applying a Theme Pack mutates these and every composable reading
 * them recomposes. Defaults are Relay's fixed design tokens.
 */
object RelayColors {
    var Ink by mutableStateOf(DEFAULT_INK); internal set
    var Panel by mutableStateOf(DEFAULT_PANEL); internal set
    var Line by mutableStateOf(DEFAULT_LINE); internal set
    var Paper by mutableStateOf(DEFAULT_PAPER); internal set
    var Muted by mutableStateOf(DEFAULT_MUTED); internal set
    var Signal by mutableStateOf(DEFAULT_SIGNAL); internal set
    var Danger by mutableStateOf(DEFAULT_DANGER); internal set
}

/** Bounded presentation tokens. Theme packs select values; they never supply UI code. */
object RelayChrome {
    var borderWidth by mutableStateOf(1.dp); internal set
    var cornerRadius by mutableStateOf(0.dp); internal set
    /** Buttons may round differently from panels; -1 inherits the panel radius. */
    var buttonRadiusDpRaw by mutableStateOf(-1); internal set
    var fill by mutableStateOf(ThemeFill.OUTLINE); internal set
    var shadow by mutableStateOf(ThemeShadow.NONE); internal set
    var contentFont by mutableStateOf(ThemeFont.SANS); internal set
    var utilityFont by mutableStateOf(ThemeFont.MONO); internal set
    var iconSet by mutableStateOf(ThemeIconSet.TEXT); internal set
    var titleUppercase by mutableStateOf(false); internal set
    var typeScale by mutableStateOf(1f); internal set

    val buttonRadius: Dp
        get() = (if (buttonRadiusDpRaw < 0) cornerRadius.value else buttonRadiusDpRaw.toFloat()).dp
}

/** Per-action haptic remapping supplied by the active pack; null means use host defaults. */
object RelayHaptics {
    private var overrides by mutableStateOf(emptyMap<HapticAction, HapticPattern>())

    fun patternFor(action: HapticAction): HapticPattern? = overrides[action]

    internal fun replace(newOverrides: Map<HapticAction, HapticPattern>) {
        overrides = newOverrides
    }
}

/** Effects remain host-rendered, bounded data; Theme Packs cannot provide rendering code. */
object RelayPresentation {
    var libraryLayout by mutableStateOf(ThemeLibraryLayout.LIST); internal set
    var playerLayout by mutableStateOf(ThemePlayerLayout.STANDARD); internal set
    var transportLayout by mutableStateOf(ThemeTransportLayout.TEXT_ROW); internal set
    var showIndexRail by mutableStateOf(true); internal set
    var background by mutableStateOf(ThemeBackground.NONE); internal set
    var backgroundAsset by mutableStateOf<String?>(null); internal set
    var effects by mutableStateOf<List<ThemeEffect>>(emptyList()); internal set
    var activePackId by mutableStateOf<String?>(null); internal set

    /**
     * Host-supplied resolver turning a validated pack asset token into a renderable URI.
     * Shared code never touches storage itself. Settable by platform hosts only at startup.
     */
    var assetResolver: (packId: String?, token: String?) -> String?
        by mutableStateOf<(String?, String?) -> String?>({ _, _ -> null })
}

private val RelayChrome.shape
    get() = if (cornerRadius.value == 0f) RectangleShape else RoundedCornerShape(cornerRadius)

internal val RelayChrome.buttonShape
    get() = if (buttonRadius.value == 0f) RectangleShape else RoundedCornerShape(buttonRadius)

fun Modifier.relayBorder(color: Color = RelayColors.Line): Modifier =
    if (RelayChrome.borderWidth.value == 0f) this else border(RelayChrome.borderWidth, color, RelayChrome.shape)

fun Modifier.relayButtonBorder(color: Color = RelayColors.Line): Modifier =
    if (RelayChrome.borderWidth.value == 0f) this else border(RelayChrome.borderWidth, color, RelayChrome.buttonShape)

fun Modifier.relayChrome(fillColor: Color = RelayColors.Panel): Modifier =
    then(if (RelayChrome.fill == ThemeFill.PANEL) Modifier.background(fillColor, RelayChrome.shape) else Modifier)
        .then(
            when (RelayChrome.shadow) {
                ThemeShadow.NONE -> Modifier
                ThemeShadow.SOFT -> Modifier.shadow(4.dp, RelayChrome.shape)
                ThemeShadow.HARD -> Modifier.shadow(8.dp, RelayChrome.shape)
            },
        )

fun Modifier.relayButtonChrome(fillColor: Color = RelayColors.Panel): Modifier =
    then(if (RelayChrome.fill == ThemeFill.PANEL) Modifier.background(fillColor, RelayChrome.buttonShape) else Modifier)
        .then(
            when (RelayChrome.shadow) {
                ThemeShadow.NONE -> Modifier
                ThemeShadow.SOFT -> Modifier.shadow(4.dp, RelayChrome.buttonShape)
                ThemeShadow.HARD -> Modifier.shadow(8.dp, RelayChrome.buttonShape)
            },
        )

private fun ThemeFont.asFontFamily(): FontFamily = when (this) {
    ThemeFont.SANS -> FontFamily.SansSerif
    ThemeFont.SERIF -> FontFamily.Serif
    ThemeFont.MONO -> FontFamily.Monospace
}

/** Computed per read so text styles always carry the active palette, scale, and case. */
object RelayType {
    private val scale: Float get() = RelayChrome.typeScale

    val Title: TextStyle
        get() = TextStyle(
            color = RelayColors.Paper,
            fontFamily = RelayChrome.contentFont.asFontFamily(),
            fontSize = (24f * scale).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (1.2f * scale).sp,
        )
    val Track: TextStyle
        get() = TextStyle(
            color = RelayColors.Paper,
            fontFamily = RelayChrome.contentFont.asFontFamily(),
            fontSize = (15f * scale).sp,
            fontWeight = FontWeight.Medium,
        )
    val Metadata: TextStyle
        get() = TextStyle(
            color = RelayColors.Muted,
            fontFamily = RelayChrome.contentFont.asFontFamily(),
            fontSize = (12f * scale).sp,
        )
    val Utility: TextStyle
        get() = TextStyle(
            color = RelayColors.Muted,
            fontFamily = RelayChrome.utilityFont.asFontFamily(),
            fontSize = (11f * scale).sp,
            letterSpacing = (0.5f * scale).sp,
        )
}

/**
 * Applies the active pack's title casing to display titles. Styles cannot transform text
 * cross-platform, so titled render sites wrap their strings with this.
 */
fun CharSequence.asThemedTitle(): String =
    if (RelayChrome.titleUppercase) toString().uppercase() else toString()

/** Applies a validated data-only Theme Pack's tokens; null restores Relay's defaults. */
fun applyThemePack(pack: ThemePack?) {
    if (pack == null || pack.validate() != null) {
        RelayColors.Ink = DEFAULT_INK
        RelayColors.Panel = DEFAULT_PANEL
        RelayColors.Line = DEFAULT_LINE
        RelayColors.Paper = DEFAULT_PAPER
        RelayColors.Muted = DEFAULT_MUTED
        RelayColors.Signal = DEFAULT_SIGNAL
        RelayColors.Danger = DEFAULT_DANGER
        RelayChrome.borderWidth = 1.dp
        RelayChrome.cornerRadius = 0.dp
        RelayChrome.buttonRadiusDpRaw = -1
        RelayChrome.fill = ThemeFill.OUTLINE
        RelayChrome.shadow = ThemeShadow.NONE
        RelayChrome.contentFont = ThemeFont.SANS
        RelayChrome.utilityFont = ThemeFont.MONO
        RelayChrome.iconSet = ThemeIconSet.TEXT
        RelayChrome.titleUppercase = false
        RelayChrome.typeScale = 1f
        RelayHaptics.replace(emptyMap())
        RelayPresentation.libraryLayout = ThemeLibraryLayout.LIST
        RelayPresentation.playerLayout = ThemePlayerLayout.STANDARD
        RelayPresentation.transportLayout = ThemeTransportLayout.TEXT_ROW
        RelayPresentation.showIndexRail = true
        RelayPresentation.background = ThemeBackground.NONE
        RelayPresentation.backgroundAsset = null
        RelayPresentation.effects = emptyList()
        RelayPresentation.activePackId = null
        return
    }
    RelayColors.Ink = pack.colors.ink.asThemeColor(DEFAULT_INK)
    RelayColors.Panel = pack.colors.panel.asThemeColor(DEFAULT_PANEL)
    RelayColors.Line = pack.colors.line.asThemeColor(DEFAULT_LINE)
    RelayColors.Paper = pack.colors.paper.asThemeColor(DEFAULT_PAPER)
    RelayColors.Muted = pack.colors.muted.asThemeColor(DEFAULT_MUTED)
    RelayColors.Signal = pack.colors.signal.asThemeColor(DEFAULT_SIGNAL)
    RelayColors.Danger = pack.colors.danger.asThemeColor(DEFAULT_DANGER)
    RelayChrome.borderWidth = pack.presentation.chrome.borderWidthDp.dp
    RelayChrome.cornerRadius = pack.presentation.chrome.cornerRadiusDp.dp
    RelayChrome.buttonRadiusDpRaw = pack.presentation.chrome.buttonRadiusDp
    RelayChrome.fill = pack.presentation.chrome.fill
    RelayChrome.shadow = pack.presentation.chrome.shadow
    RelayChrome.contentFont = pack.presentation.typography.contentFont
    RelayChrome.utilityFont = pack.presentation.typography.utilityFont
    RelayChrome.iconSet = pack.presentation.icons.set
    RelayChrome.titleUppercase = pack.presentation.typography.titleUppercase
    RelayChrome.typeScale = pack.presentation.typography.typeScale.coerceIn(0.85f, 1.25f)
    RelayHaptics.replace(pack.presentation.haptics.actions.mapNotNull { (action, pattern) ->
        val actionEnum = HapticAction.entries.firstOrNull { it.name == action }
        val patternEnum = HapticPattern.entries.firstOrNull { it.name == pattern }
        if (actionEnum != null && patternEnum != null) actionEnum to patternEnum else null
    }.toMap())
    RelayPresentation.libraryLayout = pack.presentation.libraryLayout
    RelayPresentation.playerLayout = pack.presentation.playerLayout
    RelayPresentation.transportLayout = pack.presentation.transportLayout
    RelayPresentation.showIndexRail = pack.presentation.showIndexRail
    RelayPresentation.background = pack.presentation.background
    RelayPresentation.backgroundAsset = pack.presentation.backgroundAsset
    RelayPresentation.effects = pack.presentation.effects.toList()
    RelayPresentation.activePackId = pack.id
}

fun String.asThemeColor(fallback: Color): Color = runCatching {
    Color(0xFF000000 or removePrefix("#").toLong(16))
}.getOrDefault(fallback)

@Composable
fun RelayTheme(backgroundArtworkUri: String? = null, content: @Composable () -> Unit) {
    val effects = RelayPresentation.effects
    val colorFilter = remember(effects, RelayColors.Ink, RelayColors.Signal) {
        themeColorFilter(effects, RelayColors.Ink, RelayColors.Signal)
    }
    val blurStrength = effects.strength(ThemeEffectKind.BLUR)
    Box(modifier = Modifier.fillMaxSize().background(RelayColors.Ink)) {
        ThemeBackgroundLayer(backgroundArtworkUri, Modifier.matchParentSize())
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(if (blurStrength > 0f) Modifier.blur((blurStrength * 6f).dp) else Modifier)
                .themeColorFilter(colorFilter),
        ) {
            content()
        }
        ThemeEffectOverlay(effects, Modifier.matchParentSize())
    }
}

@Composable
private fun ThemeBackgroundLayer(artworkUri: String?, modifier: Modifier) {
    when (RelayPresentation.background) {
        ThemeBackground.NONE -> Unit
        ThemeBackground.ARTWORK_BLEED -> localThemeImage(artworkUri)?.let { model ->
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier.blur(36.dp).alpha(0.42f),
            )
            Box(modifier.background(RelayColors.Ink.copy(alpha = 0.58f)))
        }
        // Pack assets resolve through the host-owned store; a missing file falls back safely.
        ThemeBackground.ASSET -> {
            val model = RelayPresentation.assetResolver(RelayPresentation.activePackId, RelayPresentation.backgroundAsset)
            if (model != null && localThemeImage(model) != null) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier,
                )
            } else {
                Box(modifier.background(RelayColors.Panel))
            }
        }
    }
}

internal fun localThemeImage(value: String?): String? {
    val uri = value?.trim()?.takeIf { it.length in 1..4_096 } ?: return null
    return uri.takeIf {
        it.startsWith("content://", ignoreCase = true) ||
            it.startsWith("file://", ignoreCase = true) ||
            it.startsWith("android.resource://", ignoreCase = true)
    }
}

private fun Modifier.themeColorFilter(colorFilter: ColorFilter?): Modifier =
    if (colorFilter == null) this else drawWithContent {
        drawContext.canvas.withSaveLayer(
            Rect(0f, 0f, size.width, size.height),
            Paint().apply { this.colorFilter = colorFilter },
        ) { drawContent() }
    }

@Composable
private fun ThemeEffectOverlay(effects: List<ThemeEffect>, modifier: Modifier) {
    val grain = effects.strength(ThemeEffectKind.GRAIN)
    val vignette = effects.strength(ThemeEffectKind.VIGNETTE)
    if (grain <= 0f && vignette <= 0f) return
    Canvas(modifier = modifier) {
        if (vignette > 0f) {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Transparent, RelayColors.Ink.copy(alpha = vignette * 0.8f)),
                    center = center,
                    radius = maxOf(size.width, size.height) * 0.72f,
                ),
            )
        }
        if (grain > 0f) repeat(160) { index ->
            val x = ((index * 47 % 163) / 163f) * size.width
            val y = ((index * 89 % 191) / 191f) * size.height
            drawCircle(
                color = if (index % 2 == 0) RelayColors.Paper else RelayColors.Ink,
                radius = 0.5f + (index % 3) * 0.35f,
                center = androidx.compose.ui.geometry.Offset(x, y),
                alpha = grain * 0.12f,
            )
        }
    }
}

private fun List<ThemeEffect>.strength(kind: ThemeEffectKind): Float =
    fold(0f) { strongest, effect -> if (effect.kind == kind) maxOf(strongest, effect.strength) else strongest }

private fun themeColorFilter(effects: List<ThemeEffect>, low: Color, high: Color): ColorFilter? {
    val duotone = effects.strength(ThemeEffectKind.DUOTONE)
    val grayscale = effects.strength(ThemeEffectKind.GRAYSCALE)
    val matrix = when {
        duotone > 0f -> duotoneColorMatrix(low, high, duotone)
        grayscale > 0f -> ColorMatrix().also { it.setToSaturation(1f - grayscale) }
        else -> return null
    }
    return ColorFilter.colorMatrix(matrix)
}

private fun duotoneColorMatrix(low: Color, high: Color, strength: Float): ColorMatrix {
    val amount = strength.coerceIn(0f, 1f)
    fun row(lowChannel: Float, highChannel: Float, identity: Float): FloatArray {
        val delta = highChannel - lowChannel
        return floatArrayOf(
            identity * (1f - amount) + 0.2126f * delta * amount,
            0.7152f * delta * amount,
            0.0722f * delta * amount,
            0f,
            lowChannel * 255f * amount,
        )
    }
    return ColorMatrix(
        row(low.red, high.red, 1f) +
            row(low.green, high.green, 0f) +
            row(low.blue, high.blue, 0f) +
            floatArrayOf(0f, 0f, 0f, 1f, 0f),
    )
}

private val DEFAULT_INK = Color(0xFF050505)
private val DEFAULT_PANEL = Color(0xFF101010)
private val DEFAULT_LINE = Color(0xFF303030)
private val DEFAULT_PAPER = Color(0xFFF1F1EC)
private val DEFAULT_MUTED = Color(0xFF92928B)
private val DEFAULT_SIGNAL = Color(0xFF4B88FF)
private val DEFAULT_DANGER = Color(0xFFFF453A)
