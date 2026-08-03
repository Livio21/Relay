package dev.relay.music

import dev.relay.music.playback.CrossfadeStage
import dev.relay.music.playback.crossfadeHandoffPositionMs
import dev.relay.music.playback.crossfadeStage
import dev.relay.music.playback.effectiveCrossfadeMs
import kotlin.test.Test
import kotlin.test.assertEquals

class CrossfadeTest {
    @Test
    fun capsShortTracksAndSeparatesPreloadFromOverlap() {
        assertEquals(0, effectiveCrossfadeMs(null, 2_000))
        assertEquals(1_500, effectiveCrossfadeMs(3_000, 2_000))
        assertEquals(CrossfadeStage.NONE, crossfadeStage(3_001, 2_000))
        assertEquals(CrossfadeStage.PRELOAD, crossfadeStage(3_000, 2_000))
        assertEquals(CrossfadeStage.OVERLAP, crossfadeStage(2_000, 2_000))
        assertEquals(2_000L, crossfadeHandoffPositionMs(2_500, 2_000))
    }
}
