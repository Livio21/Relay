package dev.relay.music.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelayStorageTest {
    @Test
    fun downloadFileNameIsStableAndDoesNotUseUnsafeTitleCharacters() {
        val first = RelayStorage.downloadFileName("extension:fma", "fma:track/1", "A/B: Test?", "mp3")
        val second = RelayStorage.downloadFileName("extension:fma", "fma:track/1", "A/B: Test?", "mp3")

        assertEquals(first, second)
        assertTrue(first.startsWith("A_B_ Test_"))
        assertTrue(first.endsWith(".mp3"))
    }

    @Test
    fun interruptedDownloadsAreRecognisedEvenAfterTheProviderAppendsAnExtension() {
        // Storage providers append their own extension to the requested name, which is how a
        // temporary file ends up looking like playable audio.
        assertTrue(RelayStorage.isPartialDownloadName("track-abc123.mp3.part"))
        assertTrue(RelayStorage.isPartialDownloadName("track-abc123.mp3.part.mp3"))
        assertTrue(RelayStorage.isPartialDownloadName("track-abc123.mp3.part (1).mp3"))

        assertFalse(RelayStorage.isPartialDownloadName("track-abc123.mp3"))
        assertFalse(RelayStorage.isPartialDownloadName("Pearl Jam - Alive.flac"))
        assertFalse(RelayStorage.isPartialDownloadName("a party mix.mp3"))
    }

    @Test
    fun replacementKeepsCompletedFileAndUsesAPlayableTemporaryFinalName() {
        val completed = "Alive-abc123.mp3"

        assertFalse(RelayStorage.isStagedDownloadFor(completed, completed))
        assertTrue(RelayStorage.isStagedDownloadFor("$completed.part.mp3", completed))
        assertEquals(
            "Alive-abc123 (replacement 2).mp3",
            RelayStorage.availableCompletedDownloadName(
                completed,
                setOf(completed, "Alive-abc123 (replacement 1).mp3"),
            ),
        )
    }
}
