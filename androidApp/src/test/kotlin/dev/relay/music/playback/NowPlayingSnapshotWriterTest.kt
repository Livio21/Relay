package dev.relay.music.playback

import dev.relay.music.library.NOW_PLAYING_SNAPSHOT_ID
import dev.relay.music.library.asEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingSnapshotWriterTest {
    @Test
    fun mappingUsesStableTrackKeyAndHasNoPlaybackUriField() {
        val snapshot = nowPlayingSnapshot(
            trackKey = "remote:track-42",
            title = "Title",
            artist = "Artist",
            album = "Album",
            artworkCacheKey = "cover.img",
            isPlaying = true,
            positionMs = 12_000,
            durationMs = 120_000,
            updatedAtEpochMs = 99,
        )

        assertEquals("remote:track-42", snapshot.trackKey)
        assertFalse(NowPlayingSnapshot::class.java.declaredFields.any { it.name == "playbackUri" })
        assertEquals(NOW_PLAYING_SNAPSHOT_ID, snapshot.asEntity().id)
        assertEquals(snapshot, snapshot.asEntity().asSnapshot())
    }

    @Test
    fun pauseAndSeekWriteImmediatelyWhileProgressIsThrottled() {
        val policy = NowPlayingSnapshotWritePolicy(intervalMs = 15_000)
        val playing = nowPlayingSnapshot(
            trackKey = "local:1",
            title = "Title",
            artist = "Artist",
            album = null,
            artworkCacheKey = null,
            isPlaying = true,
            positionMs = 0,
            durationMs = 60_000,
            updatedAtEpochMs = 1,
        )

        assertTrue(policy.shouldWrite(playing, elapsedMs = 1_000))
        assertFalse(policy.shouldWrite(playing.copy(positionMs = 14_999), elapsedMs = 15_999))
        assertTrue(policy.shouldWrite(playing.copy(positionMs = 15_000), elapsedMs = 16_000))
        assertTrue(policy.shouldWrite(playing.copy(isPlaying = false), elapsedMs = 16_001))
        assertTrue(policy.shouldWrite(playing.copy(isPlaying = false, positionMs = 30_000), elapsedMs = 16_002, force = true))
    }

    @Test
    fun restoredSnapshotIsPausedUntilTheSessionIsActive() {
        val persisted = NowPlayingSnapshot(trackKey = "local:1", isPlaying = true)

        assertEquals(false, externalSurfaceSnapshot(persisted, sessionPlaying = false)?.isPlaying)
        assertEquals(true, externalSurfaceSnapshot(persisted, sessionPlaying = true)?.isPlaying)
    }
}
