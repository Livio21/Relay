package dev.relay.music.library

import kotlin.test.Test
import kotlin.test.assertFailsWith

class RelayBackupArchiveTest {
    @Test
    fun malformedQueueIsRejectedBeforeRestore() {
        val backup = UserLibraryBackup(
            favorites = emptyList(),
            history = emptyList(),
            flags = emptyList(),
            playlists = emptyList(),
            playlistEntries = emptyList(),
            queueEntries = listOf(QueueEntryEntity(position = 1, sourceId = "local", trackId = "1")),
            queueState = QueueStateEntity(currentIndex = 0, positionMs = 0),
            metadataOverrides = emptyList(),
            settings = null,
        )

        assertFailsWith<IllegalArgumentException> { backup.validate() }
    }
}
