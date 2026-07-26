package dev.relay.music

import dev.relay.music.model.Track
import dev.relay.music.model.MetadataOverride
import dev.relay.music.model.withMetadataOverride
import dev.relay.music.lastfm.ScrobbleRule
import dev.relay.music.lastfm.ScrobbleListenTimer
import dev.relay.music.lastfm.lastFmSignature
import dev.relay.music.lastfm.pendingScrobbleId
import dev.relay.music.playback.PlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContractTest {
    private val tracks = listOf(
        Track(
            id = "one",
            sourceId = "local",
            playbackUri = "content://sample/one",
            title = "First",
            artist = "Artist",
        ),
        Track(
            id = "two",
            sourceId = "local",
            playbackUri = "content://sample/two",
            title = "Second",
            artist = "Artist",
        ),
    )

    @Test
    fun currentTrackIsNullForEmptyOrInvalidQueues() {
        assertNull(PlaybackState().currentTrack)
        assertNull(PlaybackState(queue = tracks, currentIndex = -1).currentTrack)
        assertNull(PlaybackState(queue = tracks, currentIndex = tracks.size).currentTrack)
    }

    @Test
    fun currentTrackUsesTheQueueIndex() {
        assertEquals(tracks[1], PlaybackState(queue = tracks, currentIndex = 1).currentTrack)
    }

    @Test
    fun formatDurationHandlesZeroMinutesAndHours() {
        assertEquals("00:00", formatDuration(0))
        assertEquals("03:42", formatDuration(222_000))
        assertEquals("1:01:01", formatDuration(3_661_000))
    }

    @Test
    fun pagerDeltaUsesTheNearestCarouselStep() {
        // Expressed with the view constants so adding a page does not invalidate the rule:
        // the carousel always takes the shortest way round.
        assertEquals(1, pagerPageDelta(SETTINGS_VIEW, LIBRARY_VIEW))
        assertEquals(-1, pagerPageDelta(LIBRARY_VIEW, SETTINGS_VIEW))
        assertEquals(2, pagerPageDelta(NOW_PLAYING_VIEW, INSIGHTS_VIEW))
        assertEquals(0, pagerPageDelta(LIBRARY_VIEW, LIBRARY_VIEW))
    }

    @Test
    fun lastFmSignatureSortsParametersAndUsesTheKnownDigest() {
        assertEquals(
            "9ac306496295a8866c4a8673395540eb",
            lastFmSignature(
                mapOf(
                    "token" to "token",
                    "method" to "auth.getSession",
                    "api_key" to "key",
                    "format" to "json",
                ),
                "secret",
            ),
        )
    }

    @Test
    fun scrobbleRuleUsesDurationAndFourMinuteBoundaries() {
        assertEquals(false, ScrobbleRule.isEligible(30_000, 30_000))
        assertEquals(false, ScrobbleRule.isEligible(60_000, 29_999))
        assertEquals(true, ScrobbleRule.isEligible(60_000, 30_000))
        assertEquals(false, ScrobbleRule.isEligible(600_000, 239_999))
        assertEquals(true, ScrobbleRule.isEligible(600_000, 240_000))
    }

    @Test
    fun listenedTimeExcludesPausesAndIgnoresSeeks() {
        val timer = ScrobbleListenTimer()
        timer.setPlaying(isPlaying = true, elapsedRealtimeMs = 1_000)
        timer.setPlaying(isPlaying = false, elapsedRealtimeMs = 11_000)
        timer.setPlaying(isPlaying = true, elapsedRealtimeMs = 51_000)

        assertEquals(20_000, timer.listenedMs(61_000))
    }

    @Test
    fun duplicatePlaybackCallbacksUseTheSamePendingScrobbleId() {
        assertEquals(
            pendingScrobbleId("local:42", 1_700_000_000),
            pendingScrobbleId("local:42", 1_700_000_000),
        )
    }

    @Test
    fun confirmedMetadataOverridesSourceFieldsWithoutChangingPlaybackIdentity() {
        val corrected = tracks.first().withMetadataOverride(
            MetadataOverride(artist = "Correct artist", trackNumber = 4, discNumber = 2),
        )

        assertEquals("one", corrected.id)
        assertEquals("content://sample/one", corrected.playbackUri)
        assertEquals("Correct artist", corrected.artist)
        assertEquals(4, corrected.trackNumber)
        assertEquals(2, corrected.discNumber)
    }
}
