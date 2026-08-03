package dev.relay.music.library

import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteTrackDownloadClientTest {
    private val credentials = mapOf("Authorization" to "Bearer secret", "Cookie" to "session=secret")

    @Test
    fun extensionHeadersOnlySurviveSameOriginRedirects() {
        assertEquals(
            credentials,
            forwardedExtensionHeaders(
                credentials,
                URL("https://music.example/track"),
                URL("https://MUSIC.example:443/audio"),
            ),
        )
        val stripped = forwardedExtensionHeaders(
            credentials,
            URL("https://music.example/track"),
            URL("https://cdn.example/audio"),
        )
        assertTrue(stripped.isEmpty())
        assertTrue(
            forwardedExtensionHeaders(
                stripped,
                URL("https://cdn.example/audio"),
                URL("https://music.example/final"),
            ).isEmpty(),
        )
    }
}
