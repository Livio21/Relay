package dev.relay.music.extension

import dev.relay.music.source.api.RelaySourceTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceResponseMappingTest {
    @Test
    fun tracksWithoutStreamUrlMapToUnresolvedPlayback() {
        val track = RelaySourceTrack("t1", null, "Signal Test", "Relay Demo", null, 3_000, null)
            .toTrack("example.extension", "demo")

        assertEquals("", track?.playbackUri)
        assertEquals("demo:t1", track?.id)
        assertEquals("extension:example.extension:demo", track?.sourceId)
    }

    @Test
    fun extendedSourceMetadataMapsWithoutBreakingOlderConstructors() {
        val track = RelaySourceTrack(
            "t2", null, "Signal Test", "Relay Demo", "Collection", "Relay Demo", "2024-05-01", 3_000, null,
        ).toTrack("example.extension", "demo")

        assertEquals("Relay Demo", track?.albumArtist)
        assertEquals("2024-05-01", track?.releaseDate)
    }

    @Test
    fun insecureOrOversizedStreamUrlsAreRejected() {
        assertNull(
            RelaySourceTrack("t1", "http://example.invalid/a.mp3", "Title", "Artist", null, null, null)
                .toTrack("example.extension", "demo"),
        )
        assertNull(
            RelaySourceTrack("t1", "https://example.invalid/${"a".repeat(8_192)}", "Title", "Artist", null, null, null)
                .toTrack("example.extension", "demo"),
        )
        assertNull(
            RelaySourceTrack("t1", "https://example.invalid/a.mp3", " ", "Artist", null, null, null)
                .toTrack("example.extension", "demo"),
        )
    }

    @Test
    fun mediaHeadersKeepOnlyTheAllowListWithoutInjection() {
        val headers = sanitizeMediaHeaders(
            mapOf(
                "Referer" to "https://example.invalid/",
                "User-Agent" to " CustomAgent/1.0 ",
                "Cookie" to "session=abc",
                "X-Forwarded-For" to "1.2.3.4",
                "Host" to "evil.invalid",
                "Accept" to "bad\r\nInjected: value",
                "Authorization" to "",
            ),
        )

        assertEquals(
            mapOf(
                "Referer" to "https://example.invalid/",
                "User-Agent" to "CustomAgent/1.0",
                "Cookie" to "session=abc",
            ),
            headers,
        )
    }
}
