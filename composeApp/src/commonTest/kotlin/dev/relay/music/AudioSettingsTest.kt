package dev.relay.music

import dev.relay.music.settings.EQUALIZER_BAND_COUNT
import dev.relay.music.settings.EQUALIZER_MAX_LEVEL_MB
import dev.relay.music.settings.EQUALIZER_MIN_LEVEL_MB
import dev.relay.music.settings.EqualizerPreset
import dev.relay.music.settings.equalizerPresetLevels
import dev.relay.music.settings.normalizedEqualizerBands
import kotlin.test.Test
import kotlin.test.assertEquals

class AudioSettingsTest {
    @Test
    fun presetsAndCustomBandsStayWithinThePortableFiveBandRange() {
        assertEquals(EQUALIZER_BAND_COUNT, equalizerPresetLevels(EqualizerPreset.VOCAL).size)
        assertEquals(
            listOf(EQUALIZER_MIN_LEVEL_MB, 0, EQUALIZER_MAX_LEVEL_MB, 0, 0),
            normalizedEqualizerBands(listOf(-9_999, 0, 9_999)),
        )
    }
}
