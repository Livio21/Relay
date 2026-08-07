package dev.relay.music

import dev.relay.music.extension.extensionStreamPlaceholder
import dev.relay.music.extension.parseExtensionStreamUri
import dev.relay.music.model.OfflineDownloadEvictionEntry
import dev.relay.music.model.Track
import dev.relay.music.model.offlineDownloadsToEvict
import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineDownloadTest {
    @Test
    fun placeholderRefRebuildsTheTrackIdentityUsedByDownloadRows() {
        val track = Track(
            id = "free-music-archive:track-42",
            sourceId = "extension:org.relay.extensions.fma:free-music-archive",
            playbackUri = "",
            title = "Signal Test",
            artist = "Relay Demo",
        )

        val ref = parseExtensionStreamUri(track.extensionStreamPlaceholder()!!)!!

        // The playback service looks up offline copies with exactly these values.
        assertEquals(track.sourceId, ref.trackSourceId)
        assertEquals(track.id, ref.hostTrackId)
    }

    @Test
    fun fileSizesReadInWholeUnits() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("512 B", formatFileSize(512))
        assertEquals("4 KB", formatFileSize(4_096))
        assertEquals("3.5 MB", formatFileSize(3_500_000))
        assertEquals("1.2 GB", formatFileSize(1_234_000_000))
        assertEquals("0 B", formatFileSize(-5))
    }

    @Test
    fun storageLimitEvictsOldestDownloadsFirst() {
        val limit = 100L
        val downloads = listOf(
            OfflineDownloadEvictionEntry("local", "a", 40, 1),
            OfflineDownloadEvictionEntry("local", "b", 40, 2),
            OfflineDownloadEvictionEntry("local", "c", 40, 3),
        )

        assertEquals(emptyList(), offlineDownloadsToEvict(downloads, 0))
        assertEquals(emptyList(), offlineDownloadsToEvict(downloads, 200))
        assertEquals(listOf("local" to "a"), offlineDownloadsToEvict(downloads, 100))
        assertEquals(listOf("local" to "a", "local" to "b"), offlineDownloadsToEvict(downloads, 50))
    }
}
