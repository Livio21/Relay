package dev.relay.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import dev.relay.music.extension.ThemePack
import dev.relay.music.extension.ThemeFill
import dev.relay.music.extension.ThemeFont
import dev.relay.music.extension.ThemeIconSet
import dev.relay.music.extension.ThemeShadow
import dev.relay.music.extension.validate

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
    var fill by mutableStateOf(ThemeFill.OUTLINE); internal set
    var shadow by mutableStateOf(ThemeShadow.NONE); internal set
    var contentFont by mutableStateOf(ThemeFont.SANS); internal set
    var utilityFont by mutableStateOf(ThemeFont.MONO); internal set
    var iconSet by mutableStateOf(ThemeIconSet.TEXT); internal set
}

private val RelayChrome.shape
    get() = if (cornerRadius.value == 0f) RectangleShape else RoundedCornerShape(cornerRadius)

fun Modifier.relayBorder(color: Color = RelayColors.Line): Modifier =
    if (RelayChrome.borderWidth.value == 0f) this else border(RelayChrome.borderWidth, color, RelayChrome.shape)

fun Modifier.relayChrome(fillColor: Color = RelayColors.Panel): Modifier =
    then(if (RelayChrome.fill == ThemeFill.PANEL) Modifier.background(fillColor, RelayChrome.shape) else Modifier)
        .then(
            when (RelayChrome.shadow) {
                ThemeShadow.NONE -> Modifier
                ThemeShadow.SOFT -> Modifier.shadow(4.dp, RelayChrome.shape)
                ThemeShadow.HARD -> Modifier.shadow(8.dp, RelayChrome.shape)
            },
        )

private fun ThemeFont.asFontFamily(): FontFamily = when (this) {
    ThemeFont.SANS -> FontFamily.SansSerif
    ThemeFont.SERIF -> FontFamily.Serif
    ThemeFont.MONO -> FontFamily.Monospace
}

/** Computed per read so text styles always carry the active palette. */
object RelayType {
    val Title: TextStyle
        get() = TextStyle(
            color = RelayColors.Paper,
            fontFamily = RelayChrome.contentFont.asFontFamily(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
    val Track: TextStyle
        get() = TextStyle(
            color = RelayColors.Paper,
            fontFamily = RelayChrome.contentFont.asFontFamily(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    val Metadata: TextStyle
        get() = TextStyle(
            color = RelayColors.Muted,
            fontFamily = RelayChrome.contentFont.asFontFamily(),
            fontSize = 12.sp,
        )
    val Utility: TextStyle
        get() = TextStyle(
            color = RelayColors.Muted,
            fontFamily = RelayChrome.utilityFont.asFontFamily(),
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
        )
}

/** Applies a validated data-only Theme Pack's colors; null restores Relay's defaults. */
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
        RelayChrome.fill = ThemeFill.OUTLINE
        RelayChrome.shadow = ThemeShadow.NONE
        RelayChrome.contentFont = ThemeFont.SANS
        RelayChrome.utilityFont = ThemeFont.MONO
        RelayChrome.iconSet = ThemeIconSet.TEXT
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
    RelayChrome.fill = pack.presentation.chrome.fill
    RelayChrome.shadow = pack.presentation.chrome.shadow
    RelayChrome.contentFont = pack.presentation.typography.contentFont
    RelayChrome.utilityFont = pack.presentation.typography.utilityFont
    RelayChrome.iconSet = pack.presentation.icons.set
}

fun String.asThemeColor(fallback: Color): Color = runCatching {
    Color(0xFF000000 or removePrefix("#").toLong(16))
}.getOrDefault(fallback)

@Composable
fun RelayTheme(content: @Composable () -> Unit) {
    content()
}

private val DEFAULT_INK = Color(0xFF050505)
private val DEFAULT_PANEL = Color(0xFF101010)
private val DEFAULT_LINE = Color(0xFF303030)
private val DEFAULT_PAPER = Color(0xFFF1F1EC)
private val DEFAULT_MUTED = Color(0xFF92928B)
private val DEFAULT_SIGNAL = Color(0xFF4B88FF)
private val DEFAULT_DANGER = Color(0xFFFF453A)
