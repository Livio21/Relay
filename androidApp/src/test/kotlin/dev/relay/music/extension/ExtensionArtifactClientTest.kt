package dev.relay.music.extension

import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    }
}
