package dev.relay.music

import dev.relay.music.playback.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatModeTest {
    @Test
    fun repeatControlCyclesQueueThenTrackThenOff() {
        assertEquals(RepeatMode.ALL, RepeatMode.OFF.next())
        assertEquals(RepeatMode.ONE, RepeatMode.ALL.next())
        assertEquals(RepeatMode.OFF, RepeatMode.ONE.next())
    }
}
