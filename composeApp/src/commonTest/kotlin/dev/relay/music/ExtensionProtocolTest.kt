package dev.relay.music

import dev.relay.music.extension.ApiRange
import dev.relay.music.extension.AuthenticationMethod
import dev.relay.music.extension.EXTENSION_API_VERSION
import dev.relay.music.extension.ExtensionHandshake
import dev.relay.music.extension.ExtensionKind
import dev.relay.music.extension.ExtensionNegotiation
import dev.relay.music.extension.ExtensionCatalogEntry
import dev.relay.music.extension.ExtensionPermission
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
import dev.relay.music.extension.resolvedCatalogEntry
import dev.relay.music.extension.SourceBrowseRequest
import dev.relay.music.extension.SourceSearchField
import dev.relay.music.extension.SourceSettingDefinition
import dev.relay.music.extension.SourceSettingType
import dev.relay.music.extension.sanitizeSourceSettingValues
import dev.relay.music.extension.isCompatible
import dev.relay.music.extension.isSupportedOnIos
import dev.relay.music.extension.repositoryTrustError
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
        assertIs<ExtensionNegotiation.Refused>(handshake.copy(kind = ExtensionKind.THEME_PACK).negotiate())
        assertIs<ExtensionNegotiation.Refused>(handshake.copy(id = "Bad ID").negotiate())
        assertIs<ExtensionNegotiation.Refused>(handshake.copy(capabilities = setOf("browse", "BAD CAPABILITY")).negotiate())
        assertIs<ExtensionNegotiation.Refused>(handshake.copy(authentication = emptySet()).negotiate())
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
        assertNull(entry.copy(supportUrl = "https://example.invalid/support?source=relay").validate())
        assertEquals("Extension support link is invalid.", entry.copy(supportUrl = "http://example.invalid/support").validate())
        // An entry for a different Relay API is a valid catalog row that reads as incompatible;
        // it must never invalidate the whole repository catalog.
        val incompatible = entry.copy(api = ApiRange(EXTENSION_API_VERSION + 1, EXTENSION_API_VERSION + 1))
        assertNull(RepositoryCatalog(descriptor.id, listOf(incompatible)).validate(descriptor))
        assertEquals(false, incompatible.isCompatible)
        assertEquals(true, entry.isCompatible)
        assertNull(entry.asInstalled(descriptor.id).validate())
        val installed = entry.asInstalled(descriptor.id)
        assertEquals(entry, installed.resolvedCatalogEntry())
        assertEquals(
            "Installed extension catalog snapshot does not match its identity.",
            installed.copy(catalogSnapshot = entry.copy(version = "2.0.0")).validate(),
        )
        val legacy = installed.copy(catalogSnapshot = null)
        assertNull(legacy.resolvedCatalogEntry(listOf(entry.copy(version = "2.0.0"))))
        assertEquals(entry, legacy.resolvedCatalogEntry(listOf(entry.copy(version = "2.0.0"), entry)))
        assertNull(entry.asInstalled(descriptor.id, "Extension rejected the handshake.").validate())
        val disabled = entry.asInstalled(descriptor.id).disabled(" ")
        assertEquals("Extension failed to respond.", disabled.disabledReason)
        assertNull(disabled.validate())

        val theme = entry.copy(id = "example.theme", kind = ExtensionKind.THEME_PACK)
        assertNull(theme.validate())
        assertNull(theme.asInstalled(descriptor.id).validate())
        assertEquals(
            "Theme packs cannot request permissions.",
            theme.copy(permissions = setOf(ExtensionPermission.NETWORK)).validate(),
        )
        assertEquals(
            "Theme pack artifact is too large.",
            theme.copy(artifactSizeBytes = 64L * 1024 + 1).validate(),
        )
        assertEquals(
            "Theme packs cannot contain an Android package.",
            theme.copy(
                androidPackageName = "dev.relay.example.theme",
                androidSigningCertificateSha256 = "c".repeat(64),
            ).validate(),
        )
        assertEquals(false, entry.isSupportedOnIos)
        assertEquals(true, theme.isSupportedOnIos)
        assertEquals(false, theme.copy(api = ApiRange(EXTENSION_API_VERSION + 1, EXTENSION_API_VERSION + 1)).isSupportedOnIos)

        assertNull(repositoryTrustError(emptyList(), descriptor))
        assertEquals("Repository is already trusted.", repositoryTrustError(listOf(descriptor), descriptor))
        assertEquals(
            "Repository signing key changed. Remove the trusted repository before adding the new key.",
            repositoryTrustError(listOf(descriptor), descriptor.copy(signingPublicKey = "B".repeat(43) + "=")),
        )
        assertEquals(
            "Repository origin is already trusted under another ID.",
            repositoryTrustError(listOf(descriptor), descriptor.copy(id = "other.extensions")),
        )
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
