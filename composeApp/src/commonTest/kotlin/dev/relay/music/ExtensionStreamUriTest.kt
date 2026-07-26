package dev.relay.music

import dev.relay.music.extension.ExtensionStreamRef
import dev.relay.music.extension.extensionStreamPlaceholder
import dev.relay.music.extension.extensionStreamUri
import dev.relay.music.extension.parseExtensionStreamUri
import dev.relay.music.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExtensionStreamUriTest {
    @Test
    fun roundTripsSegmentsThatContainUriSyntax() {
        val ref = ExtensionStreamRef(
            extensionId = "org.relay.extensions.fma",
            sourceId = "free-music-archive",
            trackId = "https://example.invalid/a b?c=1&d/2#x",
        )
        val uri = extensionStreamUri(ref.extensionId, ref.sourceId, ref.trackId)

        assertEquals(ref, parseExtensionStreamUri(uri))
        assertEquals(3, uri.removePrefix("relay-extension://").split('/').size)
    }

    @Test
    fun roundTripsNonAsciiTrackIds() {
        val uri = extensionStreamUri("ext", "src", "曲 — naïve")
        assertEquals(ExtensionStreamRef("ext", "src", "曲 — naïve"), parseExtensionStreamUri(uri))
    }

    @Test
    fun placeholderStripsTheInSourcePrefixSoSourcesSeeTheirOwnId() {
        val track = Track(
            id = "free-music-archive:track-42",
            sourceId = "extension:org.relay.extensions.fma:free-music-archive",
            playbackUri = "",
            title = "Signal Test",
            artist = "Relay Demo",
        )

        val ref = parseExtensionStreamUri(track.extensionStreamPlaceholder()!!)
        assertEquals(ExtensionStreamRef("org.relay.extensions.fma", "free-music-archive", "track-42"), ref)
    }

    @Test
    fun localTracksAndMalformedUrisHaveNoPlaceholder() {
        val local = Track(id = "42", sourceId = "local", playbackUri = "content://media/42", title = "T", artist = "A")

        assertNull(local.extensionStreamPlaceholder())
        assertNull(parseExtensionStreamUri("https://example.invalid/a.mp3"))
        assertNull(parseExtensionStreamUri("relay-extension://only/two"))
        assertNull(parseExtensionStreamUri("relay-extension://a/b/c/d"))
        assertNull(parseExtensionStreamUri("relay-extension://a//c"))
        assertNull(parseExtensionStreamUri("relay-extension://a/b/%ZZ"))
    }
}
