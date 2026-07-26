package dev.relay.music

import dev.relay.music.extension.ThemePack
import dev.relay.music.extension.ThemeColors
import dev.relay.music.extension.builtInThemePacks
import dev.relay.music.extension.mergedThemePacks
import dev.relay.music.extension.validate
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.RelayChrome
import dev.relay.music.ui.applyThemePack
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuiltInThemePacksTest {
    @Test
    fun bundledThemesAreValidAndCannotBeOverridden() {
        assertEquals(setOf("relay.theme.material3", "relay.theme.zune"), builtInThemePacks.map { it.id }.toSet())
        assertTrue(builtInThemePacks.all { it.validate() == null })
        val replacement = ThemePack(
            id = "relay.theme.zune",
            name = "Replacement",
            colors = ThemeColors("#000000", "#000000", "#000000", "#FFFFFF", "#999999", "#FFFFFF", "#FF0000"),
        )
        assertEquals("Zune", mergedThemePacks(listOf(replacement)).first { it.id == replacement.id }.name)
    }

    @Test
    fun bundledPackChangesTheSharedPalette() {
        applyThemePack(builtInThemePacks.first { it.id == "relay.theme.zune" })
        try {
            assertEquals(Color(0xFFFF4F00), RelayColors.Signal)
            assertEquals(2.dp, RelayChrome.borderWidth)
        } finally {
            applyThemePack(null)
        }
    }
}
