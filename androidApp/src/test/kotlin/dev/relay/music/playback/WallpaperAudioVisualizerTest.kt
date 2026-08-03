package dev.relay.music.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WallpaperAudioVisualizerTest {
    @Test
    fun waveformLevelsStayNormalized() {
        assertEquals(0f, audioLevelFromWaveform(ByteArray(32) { 0x80.toByte() }))
        assertTrue(audioLevelFromWaveform(ByteArray(32)) > 0.9f)
        assertEquals(12, audioBandsFromWaveform(ByteArray(48)).size)
        assertEquals(0f, audioLevelFromFft(ByteArray(32)))
        assertTrue(audioLevelFromFft(ByteArray(32).also { it[2] = 64 }) > 0f)
    }
}
