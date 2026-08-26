package dev.relay.music

import dev.relay.music.haptics.HapticAction
import dev.relay.music.haptics.HapticIntensity
import dev.relay.music.haptics.HapticPattern
import dev.relay.music.haptics.defaultPattern
import dev.relay.music.haptics.resolveHaptic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HapticsTest {
    @Test
    fun offIntensitySilencesEverythingAndDefaultsAreStable() {
        assertTrue(HapticAction.entries.all { resolveHaptic(it, null, HapticIntensity.OFF) == null })
        assertEquals(HapticPattern.CLICK, HapticAction.TRANSPORT_PRESS.defaultPattern())
        assertEquals(HapticPattern.TICK, HapticAction.TAB_CHANGE.defaultPattern())
        assertEquals(HapticPattern.SOFT, HapticAction.QUEUE_REORDER.defaultPattern())
        assertEquals(HapticPattern.DOUBLE_CLICK, HapticAction.FAVORITE.defaultPattern())
    }

    @Test
    fun intensityScalesAmplitudeButNeverChangesThePattern() {
        val subtle = resolveHaptic(HapticAction.TRANSPORT_PRESS, null, HapticIntensity.SUBTLE)
        val medium = resolveHaptic(HapticAction.TRANSPORT_PRESS, null, HapticIntensity.MEDIUM)
        val strong = resolveHaptic(HapticAction.TRANSPORT_PRESS, null, HapticIntensity.STRONG)
        requireNotNull(subtle)
        requireNotNull(medium)
        requireNotNull(strong)
        assertEquals(HapticPattern.CLICK, subtle.pattern)
        assertEquals(HapticPattern.CLICK, strong.pattern)
        assertTrue(subtle.amplitude < medium.amplitude && medium.amplitude < strong.amplitude)
        assertTrue(strong.amplitude in 1..255)
    }

    @Test
    fun themeOverrideReplacesTheDefaultAndNoneSilencesOneAction() {
        val overridden = resolveHaptic(HapticAction.SELECT_TRACK, HapticPattern.RISE, HapticIntensity.MEDIUM)
        assertEquals(HapticPattern.RISE, overridden?.pattern)

        assertNull(resolveHaptic(HapticAction.SELECT_TRACK, HapticPattern.NONE, HapticIntensity.STRONG))
        // Silencing one action leaves others untouched.
        val untouched = resolveHaptic(HapticAction.TRANSPORT_PRESS, null, HapticIntensity.STRONG)
        requireNotNull(untouched)
        assertEquals(HapticPattern.CLICK, untouched.pattern)
    }
}
