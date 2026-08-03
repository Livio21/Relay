package dev.relay.music

import dev.relay.music.sync.PlayTogetherCommand
import dev.relay.music.sync.playTogetherCorrection
import dev.relay.music.sync.ClockProbe
import dev.relay.music.sync.estimateClockOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayTogetherTest {
    @Test fun commandStartsAtTheMappedFutureTime() {
        val command = PlayTogetherCommand("local", "1", queueIndex = 0, leaderPositionMs = 2_000, targetLeaderElapsedMs = 10_000, playing = true)
        assertEquals(2_000, playTogetherCorrection(command, 9_000, 0, 0).targetPositionMs)
        assertEquals(2_500, playTogetherCorrection(command, 10_500, 2_100, 0).targetPositionMs)
    }

    @Test fun lowestLatencyProbeDeterminesClockOffset() {
        val estimate = estimateClockOffset(listOf(
            ClockProbe(0, 120, 125, 250),
            ClockProbe(1_000, 1_164, 1_166, 1_110),
        ))!!
        assertEquals(110, estimate.peerMinusLocalMs)
        assertEquals(108, estimate.roundTripMs)
    }
}
