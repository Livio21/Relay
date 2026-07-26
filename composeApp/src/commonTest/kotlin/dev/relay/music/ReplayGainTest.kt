package dev.relay.music

import dev.relay.music.playback.MIN_REPLAY_GAIN_VOLUME
import dev.relay.music.playback.parseReplayGainDb
import dev.relay.music.playback.replayGainVolume
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplayGainTest {
    @Test
    fun parsesTheTagFormatsPlayersActuallyWrite() {
        assertEquals(-7.5f, parseReplayGainDb("-7.50 dB"))
        assertEquals(-7.5f, parseReplayGainDb("-7.50dB"))
        assertEquals(3f, parseReplayGainDb("+3 dB"))
        assertEquals(0f, parseReplayGainDb("0"))
        assertEquals(-4.25f, parseReplayGainDb("  -4.25 DB "))
    }

    @Test
    fun rejectsMissingAndImplausibleTags() {
        assertNull(parseReplayGainDb(null))
        assertNull(parseReplayGainDb(""))
        assertNull(parseReplayGainDb("loud"))
        assertNull(parseReplayGainDb("-999 dB"))
        assertNull(parseReplayGainDb("40 dB"))
    }

    @Test
    fun attenuatesLoudTracksAndNeverBoostsQuietOnes() {
        // -6 dB is about half amplitude.
        assertTrue(abs(replayGainVolume(-6f) - 0.501f) < 0.01f)
        assertEquals(1f, replayGainVolume(0f))
        // A positive tag would mean turning up; Relay leaves it alone to avoid clipping.
        assertEquals(1f, replayGainVolume(6f))
        assertEquals(MIN_REPLAY_GAIN_VOLUME, replayGainVolume(-60f))
    }
}
