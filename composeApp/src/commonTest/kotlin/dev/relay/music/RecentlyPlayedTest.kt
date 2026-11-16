package dev.relay.music

import dev.relay.music.model.ListeningEvent
import dev.relay.music.model.Track
import dev.relay.music.model.recentlyPlayedTracks
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentlyPlayedTest {
    private val a = Track("a", "local", "a", "A", "Artist")
    private val b = Track("b", "local", "b", "B", "Artist")
    private val c = Track("c", "local", "c", "C", "Artist")

    private fun event(track: Track, at: Long) = ListeningEvent(
        sourceId = track.sourceId,
        trackId = track.id,
        playedAtEpochMs = at,
    )

    @Test
    fun newestFirstAndDeduplicated() {
        val result = recentlyPlayedTracks(
            events = listOf(event(a, 10), event(b, 30), event(a, 40), event(c, 20)),
            tracks = listOf(a, b, c),
        )
        assertEquals(listOf(a, b, c), result)
    }

    @Test
    fun resolvesOnlyTracksStillInLibrary() {
        val result = recentlyPlayedTracks(
            events = listOf(event(a, 10), ListeningEvent("remote", "gone", 20)),
            tracks = listOf(a),
        )
        assertEquals(listOf(a), result)
    }

    @Test
    fun respectsLimitAndOrdering() {
        val result = recentlyPlayedTracks(
            events = listOf(event(a, 10), event(b, 20), event(c, 30)),
            tracks = listOf(a, b, c),
            limit = 2,
        )
        assertEquals(listOf(c, b), result)
    }

    @Test
    fun blankOrZeroLimitReturnsEmpty() {
        assertEquals(emptyList(), recentlyPlayedTracks(emptyList(), listOf(a), limit = 0))
        assertEquals(emptyList(), recentlyPlayedTracks(listOf(event(a, 1)), listOf(a), limit = -1))
    }
}
