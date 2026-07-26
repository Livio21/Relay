package dev.relay.music.library

import dev.relay.music.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaylistEntryMappingTest {
    private val track = Track(
        id = "demo:t1",
        sourceId = "extension:example.ext:demo",
        playbackUri = "https://example.invalid/stream",
        title = "Signal Test",
        artist = "Relay Demo",
        album = "Samples",
        durationMs = 3_000,
        artworkUri = "https://example.invalid/art.jpg",
    )

    @Test
    fun entrySnapshotKeepsDisplayMetadataButNeverInsecureArtwork() {
        val entry = track.asPlaylistEntry(playlistId = 7, position = 2)

        assertEquals(7, entry.playlistId)
        assertEquals(2, entry.position)
        assertEquals("demo:t1", entry.trackId)
        assertEquals("Signal Test", entry.title)
        assertEquals("Relay Demo", entry.artist)
        assertEquals(3_000, entry.durationMs)
        assertEquals("https://example.invalid/art.jpg", entry.artworkUri)
        assertNull(track.copy(artworkUri = "http://example.invalid/art.jpg").asPlaylistEntry(7, 0).artworkUri)
    }

    @Test
    fun queueSnapshotCarriesTheSameFieldsAndNeverAStreamUrl() {
        val entry = track.asQueueEntry(position = 3)

        assertEquals(3, entry.position)
        assertEquals("extension:example.ext:demo", entry.sourceId)
        assertEquals("demo:t1", entry.trackId)
        assertEquals("Signal Test", entry.title)
        assertEquals("Relay Demo", entry.artist)
        assertEquals("Samples", entry.album)
        assertEquals(3_000, entry.durationMs)
        assertEquals("https://example.invalid/art.jpg", entry.artworkUri)
        assertNull(track.copy(artworkUri = "http://example.invalid/art.jpg").asQueueEntry(0).artworkUri)
        // The resolved stream URL must not survive into storage.
        assertEquals(
            listOf("Signal Test", "Relay Demo", "Samples"),
            listOfNotNull(entry.title, entry.artist, entry.album),
        )
    }
}
