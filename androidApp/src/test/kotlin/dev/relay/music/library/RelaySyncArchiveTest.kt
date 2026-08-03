package dev.relay.music.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RelaySyncArchiveTest {
    @Test
    fun receivedFlagConflictRestoresTheReceivedValue() {
        val received = SyncConflictEntity(
            id = "flag", description = "FLAGS: local/track", section = "FLAGS",
            receivedPayload = """{"sourceId":"local","trackId":"track","hidden":true,"pinned":false,"archived":true}""",
            createdAtEpochMs = 0,
        ).receivedEntityOrNull()
        assertEquals(TrackFlagsEntity("local", "track", hidden = true, pinned = false, archived = true), assertIs<TrackFlagsEntity>(received))
    }

    @Test
    fun mergePlanKeepsLocalConflictsAndImportsNewDataWithoutDuplicatingHistory() {
        val localHistory = ListeningHistoryEntity(
            sourceId = "local", trackId = "track", playedAtEpochMs = 1_000,
            title = "Track", artist = "Artist",
        )
        val incoming = UserLibraryBackup(
            favorites = listOf(FavoriteTrackEntity("remote", "favorite")),
            history = listOf(localHistory.copy(id = 0)),
            flags = listOf(TrackFlagsEntity("local", "track", hidden = true)),
            playlists = listOf(PlaylistEntity(id = 4, name = "Road", createdAtEpochMs = 2_000)),
            playlistEntries = listOf(PlaylistEntryEntity(4, 0, "remote", "favorite")),
            queueEntries = emptyList(),
            queueState = null,
            metadataOverrides = listOf(MetadataOverrideEntity("local", "track", title = "Remote", artist = null, album = null, albumArtist = null, artworkUri = null, musicBrainzId = null, trackNumber = null, discNumber = null)),
            settings = null,
        )
        val plan = syncMergePlan(
            SyncSnapshot(
                favorites = emptyList(),
                history = listOf(localHistory),
                flags = listOf(TrackFlagsEntity("local", "track", hidden = false)),
                playlists = listOf(PlaylistEntity(id = 1, name = "Road", createdAtEpochMs = 1)),
                playlistEntries = listOf(PlaylistEntryEntity(1, 0, "local", "other")),
                metadataOverrides = listOf(MetadataOverrideEntity("local", "track", title = "Local", artist = null, album = null, albumArtist = null, artworkUri = null, musicBrainzId = null, trackNumber = null, discNumber = null)),
                profile = null,
                charts = emptyList(),
            ),
            incoming,
        )

        assertEquals(1, plan.favoritesToAdd.size)
        assertTrue(plan.historyToAdd.isEmpty())
        assertTrue(plan.flagsToAdd.isEmpty())
        assertTrue(plan.metadataToAdd.isEmpty())
        assertEquals("Road (SYNC)", plan.playlistsToAdd.single().name)
        assertTrue(plan.conflicts.any { it.description.startsWith("FLAGS:") && it.receivedPayload.contains("hidden") })
        assertTrue(plan.conflicts.any { it.description.startsWith("METADATA:") && it.receivedPayload.contains("Remote") })
    }
}
