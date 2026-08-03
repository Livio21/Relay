package dev.relay.music.extension

import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.nio.file.Files
import dev.relay.music.extension.ApiRange
import dev.relay.music.extension.EXTENSION_API_VERSION
import dev.relay.music.extension.ExtensionCatalogEntry
import dev.relay.music.extension.ExtensionKind

class ExtensionArtifactClientTest {
    @Test
    fun sha256UsesLowercaseCatalogFormat() {
        assertEquals(
            "682fbae20f3428bcec4c117c57bea18d438c4758d972909b41dbe22884e0d6b8",
            sha256Hex("relay".encodeToByteArray()),
        )
    }

    @Test
    fun artifactRedirectsMustRemainHttps() {
        assertEquals(
            "https://objects.githubusercontent.com/release.apk",
            checkedRedirectUrl(URL("https://github.com/release"), "https://objects.githubusercontent.com/release.apk").toString(),
        )
        assertFailsWith<IllegalArgumentException> {
            checkedRedirectUrl(URL("https://github.com/release"), "http://example.invalid/release.apk")
        }
        assertFailsWith<IllegalArgumentException> {
            checkedRedirectUrl(URL("https://github.com/release"), "https://user@example.invalid/release.apk")
        }
        assertFailsWith<IllegalArgumentException> {
            checkedRedirectUrl(URL("https://github.com/release"), "https://example.invalid/release.apk#fragment")
        }
    }

    @Test
    fun stagedArtifactMustStillMatchTheCatalog() {
        val bytes = "relay artifact".encodeToByteArray()
        val file = Files.createTempFile("relay-artifact-", ".bin").toFile()
        try {
            file.writeBytes(bytes)
            val entry = ExtensionCatalogEntry(
                id = "example.source",
                name = "Example",
                version = "1.0.0",
                kind = ExtensionKind.SOURCE,
                api = ApiRange(EXTENSION_API_VERSION, EXTENSION_API_VERSION),
                artifactUrl = "https://example.invalid/source.apk",
                artifactSha256 = sha256Hex(bytes),
                artifactSizeBytes = bytes.size.toLong(),
                permissions = emptySet(),
            )
            assertEquals(null, artifactValidationError(entry, file))
            assertEquals("Extension artifact digest is invalid.", artifactValidationError(entry.copy(artifactSha256 = "0".repeat(64)), file))
            assertEquals("Extension artifact size does not match its catalog.", artifactValidationError(entry.copy(artifactSizeBytes = bytes.size + 1L), file))
        } finally {
            file.delete()
        }
    }
}
