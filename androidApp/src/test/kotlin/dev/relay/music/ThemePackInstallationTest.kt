package dev.relay.music

import dev.relay.music.extension.ApiRange
import dev.relay.music.extension.EXTENSION_API_VERSION
import dev.relay.music.extension.ExtensionCatalogEntry
import dev.relay.music.extension.ExtensionKind
import dev.relay.music.extension.ThemeColors
import dev.relay.music.extension.ThemePack
import dev.relay.music.extension.asInstalled
import dev.relay.music.settings.RelaySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ThemePackInstallationTest {
    private val themeEntry = ExtensionCatalogEntry(
        id = "example.theme",
        name = "Example Theme",
        version = "2.0.0",
        kind = ExtensionKind.THEME_PACK,
        api = ApiRange(EXTENSION_API_VERSION, EXTENSION_API_VERSION),
        artifactUrl = "https://example.invalid/example-theme.json",
        artifactSha256 = "a".repeat(64),
        artifactSizeBytes = 1,
        permissions = emptySet(),
    )
    private val pack = ThemePack(
        id = themeEntry.id,
        name = themeEntry.name,
        colors = ThemeColors("#000000", "#111111", "#222222", "#FFFFFF", "#AAAAAA", "#00FF00", "#FF0000"),
    )

    @Test
    fun installUpdateAndRemoveKeepThemeDataAndRepositoryIdentityTogether() {
        val source = themeEntry.copy(id = "example.source", kind = ExtensionKind.SOURCE).asInstalled("repo")
        val installed = RelaySettings(installedExtensions = listOf(source))
            .withRepositoryThemePack("repo", themeEntry, pack)

        assertEquals(pack.id, installed.activeThemePackId)
        assertEquals("2.0.0", installed.installedExtensions.single { it.kind == ExtensionKind.THEME_PACK }.version)
        assertTrue(source in installed.installedExtensions)

        val removed = installed.withoutThemePack(pack.id)
        assertEquals(null, removed.activeThemePackId)
        assertTrue(removed.installedExtensions.none { it.kind == ExtensionKind.THEME_PACK })
        assertTrue(removed.themePacks.none { it.id == pack.id })
    }

    @Test
    fun anotherRepositoryCannotReplaceTheSameThemeIdentity() {
        val installed = RelaySettings().withRepositoryThemePack("repo-a", themeEntry, pack)
        assertFailsWith<IllegalArgumentException> {
            installed.withRepositoryThemePack("repo-b", themeEntry, pack)
        }
        assertFailsWith<IllegalArgumentException> {
            RelaySettings().withRepositoryThemePack("repo", themeEntry.copy(id = "other.theme"), pack)
        }
    }
}
