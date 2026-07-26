package dev.relay.music

import dev.relay.music.extension.ApiRange
import dev.relay.music.extension.AuthenticationMethod
import dev.relay.music.extension.EXTENSION_API_VERSION
import dev.relay.music.extension.ExtensionHandshake
import dev.relay.music.extension.ExtensionKind
import dev.relay.music.extension.ExtensionNegotiation
import dev.relay.music.extension.ExtensionCatalogEntry
import dev.relay.music.extension.RepositoryCatalog
import dev.relay.music.extension.RepositoryDescriptor
import dev.relay.music.extension.ThemeColors
import dev.relay.music.extension.ThemeBackground
import dev.relay.music.extension.ThemeEffect
import dev.relay.music.extension.ThemeEffectKind
import dev.relay.music.extension.ThemePresentation
import dev.relay.music.extension.ThemePack
import dev.relay.music.extension.negotiate
import dev.relay.music.extension.validate
import dev.relay.music.extension.asInstalled
import dev.relay.music.extension.disabled
import dev.relay.music.extension.SourceBrowseRequest
import dev.relay.music.extension.SourceSearchField
import dev.relay.music.extension.SourceSettingDefinition
import dev.relay.music.extension.SourceSettingType
import dev.relay.music.extension.sanitizeSourceSettingValues
import dev.relay.music.extension.isCompatible
import dev.relay.music.extension.toSourceQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ExtensionProtocolTest {
    @Test
    fun negotiationAcceptsOnlyTheHostApiRange() {
        val handshake = ExtensionHandshake(
            id = "example.source",
            version = "1.0.0",
            kind = ExtensionKind.SOURCE,
            api = ApiRange(EXTENSION_API_VERSION, EXTENSION_API_VERSION),
            capabilities = setOf("browse"),
            permissions = emptySet(),
            settingsSchemaVersion = 1,
            authentication = setOf(AuthenticationMethod.NONE),
        )

        assertEquals(EXTENSION_API_VERSION, assertIs<ExtensionNegotiation.Accepted>(handshake.negotiate()).apiVersion)
        assertIs<ExtensionNegotiation.Refused>(
            handshake.copy(api = ApiRange(EXTENSION_API_VERSION + 1, EXTENSION_API_VERSION + 1)).negotiate(),
        )
    }

    @Test
    fun browseRequestsAppendOnlyPastTheFirstPage() {
        assertEquals(false, SourceBrowseRequest(query = "signal").appendsResults)
        assertEquals(true, SourceBrowseRequest(query = "signal", page = 2).appendsResults)
    }

    @Test
    fun sourceSearchFieldsUseTheStableV1Grammar() {
        assertEquals("artist:Relay Demo", SourceSearchField.ARTIST.toSourceQuery(" Relay Demo "))
        assertEquals("Signal Test", SourceSearchField.ALL.toSourceQuery(" Signal Test "))
        assertEquals("", SourceSearchField.ALBUM.toSourceQuery(" "))
    }

    @Test
    fun repositoryCatalogRejectsMismatchedAndUnsafeEntries() {
        val descriptor = RepositoryDescriptor(
            id = "example.extensions",
            name = "Example Extensions",
            indexUrl = "https://example.invalid/index.json",
            signingPublicKey = "A".repeat(43) + "=",
        )
        val entry = ExtensionCatalogEntry(
            id = "example.source",
            name = "Example Source",
            version = "1.0.0",
            kind = ExtensionKind.SOURCE,
            api = ApiRange(EXTENSION_API_VERSION, EXTENSION_API_VERSION),
            artifactUrl = "https://example.invalid/example.bin",
            artifactSha256 = "b".repeat(64),
            artifactSizeBytes = 1,
            permissions = emptySet(),
        )

        assertNull(RepositoryCatalog(descriptor.id, listOf(entry)).validate(descriptor))
        assertEquals(
            "Extension artifact must use HTTPS.",
            RepositoryCatalog(descriptor.id, listOf(entry.copy(artifactUrl = "http://example.invalid/example.bin"))).validate(descriptor),
        )
        assertEquals(
            "Catalog repository ID does not match its descriptor.",
            RepositoryCatalog("other.extensions", listOf(entry)).validate(descriptor),
        )
        assertEquals(
            "Android extension package identity is incomplete.",
            RepositoryCatalog(descriptor.id, listOf(entry.copy(androidPackageName = "example.relay.source"))).validate(descriptor),
        )
        // An entry for a different Relay API is a valid catalog row that reads as incompatible;
        // it must never invalidate the whole repository catalog.
        val incompatible = entry.copy(api = ApiRange(EXTENSION_API_VERSION + 1, EXTENSION_API_VERSION + 1))
        assertNull(RepositoryCatalog(descriptor.id, listOf(incompatible)).validate(descriptor))
        assertEquals(false, incompatible.isCompatible)
        assertEquals(true, entry.isCompatible)
        assertNull(entry.asInstalled(descriptor.id).validate())
        assertNull(entry.asInstalled(descriptor.id, "Extension rejected the handshake.").validate())
        val disabled = entry.asInstalled(descriptor.id).disabled(" ")
        assertEquals("Extension failed to respond.", disabled.disabledReason)
        assertNull(disabled.validate())
    }

    @Test
    fun sourceSettingSchemaAndValuesAreBounded() {
        val choice = SourceSettingDefinition("page-size", "Results per page", SourceSettingType.CHOICE, "20", listOf("10", "20", "40"))
        val toggle = SourceSettingDefinition("only-short", "Only short samples", SourceSettingType.TOGGLE, "false")
        val text = SourceSettingDefinition("base-url", "Base URL", SourceSettingType.TEXT)

        assertNull(choice.validate())
        assertNull(toggle.validate())
        assertNull(text.validate())
        assertEquals("Source setting ID is invalid.", choice.copy(id = "Bad ID").validate())
        assertEquals("Source setting default is not a choice.", choice.copy(defaultValue = "15").validate())
        assertEquals("Source setting choices are invalid.", choice.copy(choices = emptyList()).validate())
        assertEquals("Source setting choices are not applicable.", toggle.copy(choices = listOf("x")).validate())

        assertEquals(
            mapOf("page-size" to "40", "only-short" to "true"),
            sanitizeSourceSettingValues(
                listOf(choice, toggle, text),
                mapOf(
                    "page-size" to "40",
                    "only-short" to "true",
                    "unknown" to "dropped",
                    "base-url" to "a".repeat(2_000),
                ),
            ),
        )
    }

    @Test
    fun dataOnlyThemePackRejectsUnsafeTokens() {
        val pack = ThemePack(
            id = "relay.theme.zune",
            name = "Zune",
            colors = ThemeColors("#0B0B0B", "#151515", "#454545", "#F4F4F4", "#A8A8A8", "#FF4F00", "#FF453A"),
        )

        assertNull(pack.validate())
        assertEquals("Theme pack colors must use #RRGGBB.", pack.copy(colors = pack.colors.copy(signal = "orange")).validate())
        assertNull(pack.copy(presentation = ThemePresentation(background = ThemeBackground.ARTWORK_BLEED, effects = listOf(ThemeEffect(ThemeEffectKind.GRAIN, 0.4f)))).validate())
        assertEquals("Theme background asset is missing.", pack.copy(presentation = ThemePresentation(background = ThemeBackground.ASSET)).validate())
    }
}
