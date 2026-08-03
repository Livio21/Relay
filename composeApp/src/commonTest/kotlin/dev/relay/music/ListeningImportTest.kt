package dev.relay.music

import dev.relay.music.model.ListeningEvent
import dev.relay.music.model.ListeningOrigin
import dev.relay.music.model.newListeningEventsForImport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListeningImportTest {
    @Test
    fun importDeduplicationKeepsDistinctProvenanceAndLaterPlays() {
        val local = ListeningEvent(
            sourceId = "local",
            trackId = "1",
            playedAtEpochMs = 1_000_000,
            title = "Black",
            artist = "Pearl Jam",
            album = "Ten",
        )
        val importedOverlap = local.copy(
            sourceId = "lastfm",
            trackId = "black-pearl-jam",
            playedAtEpochMs = 1_030_000,
            origin = ListeningOrigin.LASTFM_IMPORT,
        )
        val importedLater = importedOverlap.copy(playedAtEpochMs = 1_061_000)

        val accepted = newListeningEventsForImport(listOf(local), listOf(importedOverlap, importedLater))

        assertEquals(listOf(importedLater), accepted)
        assertEquals(ListeningOrigin.LASTFM_IMPORT, accepted.single().origin)
        assertTrue(newListeningEventsForImport(emptyList(), listOf(importedOverlap)).single().origin == ListeningOrigin.LASTFM_IMPORT)
    }
}
