package dev.relay.music

import dev.relay.music.model.InsightsRange
import dev.relay.music.model.ListeningEvent
import dev.relay.music.model.UNKNOWN_INSIGHT_LABEL
import dev.relay.music.model.insightsFor
import kotlin.test.Test
import kotlin.test.assertEquals

class InsightsTest {
    private val now = 1_800_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private fun play(
        title: String,
        artist: String? = "Relay Demo",
        album: String? = "Samples",
        daysAgo: Long = 0,
        durationMs: Long? = 180_000,
        trackId: String = title,
    ) = ListeningEvent(
        sourceId = "local",
        trackId = trackId,
        playedAtEpochMs = now - daysAgo * day,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
    )

    @Test
    fun countsOnlyPlaysInsideTheSelectedWindow() {
        val events = listOf(play("A", daysAgo = 1), play("B", daysAgo = 10), play("C", daysAgo = 200))

        assertEquals(1, insightsFor(events, InsightsRange.WEEK, now).plays)
        assertEquals(2, insightsFor(events, InsightsRange.MONTH, now).plays)
        assertEquals(3, insightsFor(events, InsightsRange.YEAR, now).plays)
        assertEquals(3, insightsFor(events, InsightsRange.ALL, now).plays)
    }

    @Test
    fun ranksByPlayCountAndBreaksTiesAlphabetically() {
        val events = listOf(
            play("Wideband", artist = "Zeta", trackId = "1"),
            play("Wideband", artist = "Zeta", trackId = "1"),
            play("Signal", artist = "alpha", trackId = "2"),
            play("Night", artist = "beta", trackId = "3"),
        )

        val insights = insightsFor(events, InsightsRange.ALL, now)

        assertEquals("Wideband — Zeta", insights.topTracks.first().label)
        assertEquals(2, insights.topTracks.first().plays)
        // One play each: alphabetical, so the order is stable across runs.
        assertEquals(listOf("Night — beta", "Signal — alpha"), insights.topTracks.drop(1).map { it.label })
        assertEquals(listOf("Zeta", "alpha", "beta"), insights.topArtists.map { it.label })
    }

    @Test
    fun missingMetadataIsBucketedRatherThanDropped() {
        val events = listOf(
            play("A", artist = null, album = null),
            play("B", artist = "  ", album = ""),
            play("C", artist = "Relay Demo", album = "Samples"),
        )

        val insights = insightsFor(events, InsightsRange.ALL, now)

        assertEquals(3, insights.plays)
        assertEquals(2, insights.topArtists.first { it.label == UNKNOWN_INSIGHT_LABEL }.plays)
        assertEquals(3, insights.topArtists.sumOf { it.plays })
        assertEquals(3, insights.topAlbums.sumOf { it.plays })
    }

    @Test
    fun playsWithoutAKnownLengthAreCountedButNotTimed() {
        val events = listOf(
            play("A", durationMs = 120_000),
            play("B", durationMs = null),
            play("C", durationMs = 0),
        )

        val insights = insightsFor(events, InsightsRange.ALL, now)

        assertEquals(3, insights.plays)
        assertEquals(1, insights.timedPlays)
        assertEquals(2, insights.untimedPlays)
        assertEquals(120_000, insights.estimatedMs)
    }

    @Test
    fun futureTimestampsFromAClockChangeAreIgnored() {
        val events = listOf(play("A", daysAgo = -5), play("B", daysAgo = 1))

        assertEquals(1, insightsFor(events, InsightsRange.ALL, now).plays)
    }

    @Test
    fun calendarBucketsPlaysByUtcDay() {
        val events = listOf(play("A", daysAgo = 0), play("B", daysAgo = 0), play("C", daysAgo = 2))

        val calendar = insightsFor(events, InsightsRange.MONTH, now).listeningDays

        assertEquals(listOf(1, 2), calendar.map { it.plays })
        assertEquals(listOf((now / day) - 2, now / day), calendar.map { it.epochDay })
    }
}
