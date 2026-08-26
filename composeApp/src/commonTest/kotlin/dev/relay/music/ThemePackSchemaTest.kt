package dev.relay.music

import dev.relay.music.extension.ThemeChrome
import dev.relay.music.extension.ThemeColors
import dev.relay.music.extension.ThemeHapticsMapping
import dev.relay.music.extension.ThemePack
import dev.relay.music.extension.ThemePresentation
import dev.relay.music.extension.ThemeTypography
import dev.relay.music.extension.validate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ThemePackSchemaTest {
    private val colors = ThemeColors(
        ink = "#050505", panel = "#101010", line = "#303030",
        paper = "#F1F1EC", muted = "#92928B", signal = "#4B88FF", danger = "#FF453A",
    )

    private fun pack(
        schemaVersion: Int = 2,
        chrome: ThemeChrome = ThemeChrome(),
        typography: ThemeTypography = ThemeTypography(),
        haptics: ThemeHapticsMapping = ThemeHapticsMapping(),
    ) = ThemePack(
        schemaVersion = schemaVersion,
        id = "test.pack",
        name = "Test Pack",
        colors = colors,
        presentation = ThemePresentation(chrome = chrome, typography = typography, haptics = haptics),
    )

    @Test
    fun v1PacksWithoutNewFieldsStayValid() {
        assertNull(pack(schemaVersion = 1).validate())
    }

    @Test
    fun v2FieldsValidateWithinBounds() {
        assertNull(
            pack(
                chrome = ThemeChrome(buttonRadiusDp = 12),
                typography = ThemeTypography(titleUppercase = true, typeScale = 0.9f),
                haptics = ThemeHapticsMapping(mapOf("TRANSPORT_PRESS" to "THUD", "SELECT_TRACK" to "NONE")),
            ).validate(),
        )
        // -1 means inherit the panel radius.
        assertNull(pack(chrome = ThemeChrome(buttonRadiusDp = -1)).validate())
    }

    @Test
    fun outOfBoundsAndUnknownNamesFailClosed() {
        assertEquals("Theme pack schema is unsupported.", pack(schemaVersion = 3)?.validate())
        assertEquals("Theme button radius is invalid.", pack(chrome = ThemeChrome(buttonRadiusDp = 30))?.validate())
        assertEquals("Theme type scale is invalid.", pack(typography = ThemeTypography(typeScale = 1.5f))?.validate())
        assertNotNull(pack(haptics = ThemeHapticsMapping(mapOf("NOT_AN_ACTION" to "CLICK"))).validate())
        assertNotNull(pack(haptics = ThemeHapticsMapping(mapOf("TRANSPORT_PRESS" to "EXPLODE"))).validate())
    }
}
