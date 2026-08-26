package dev.relay.music.haptics

import androidx.compose.runtime.staticCompositionLocalOf

/** Interactions that can produce haptic feedback. */
enum class HapticAction {
    TRANSPORT_PRESS,
    SELECT_TRACK,
    SEEK_TICK,
    QUEUE_REORDER,
    FAVORITE,
    TAB_CHANGE,
}

/**
 * Named vibration shapes from the host's bounded catalog. Themes may map actions to these;
 * they never supply raw timing or amplitude data.
 */
enum class HapticPattern { NONE, CLICK, DOUBLE_CLICK, TICK, THUD, RISE, SOFT }

enum class HapticIntensity { OFF, SUBTLE, MEDIUM, STRONG }

/** A concrete feedback request after defaults, overrides, and intensity were applied. */
data class ResolvedHaptic(val pattern: HapticPattern, val amplitude: Int)

fun HapticAction.defaultPattern(): HapticPattern = when (this) {
    HapticAction.TRANSPORT_PRESS, HapticAction.SELECT_TRACK -> HapticPattern.CLICK
    HapticAction.SEEK_TICK, HapticAction.TAB_CHANGE -> HapticPattern.TICK
    HapticAction.QUEUE_REORDER -> HapticPattern.SOFT
    HapticAction.FAVORITE -> HapticPattern.DOUBLE_CLICK
}

private const val AMPLITUDE_SUBTLE = 112
private const val AMPLITUDE_MEDIUM = 180
private const val AMPLITUDE_STRONG = 255

fun HapticIntensity.amplitude(): Int = when (this) {
    HapticIntensity.OFF -> 0
    HapticIntensity.SUBTLE -> AMPLITUDE_SUBTLE
    HapticIntensity.MEDIUM -> AMPLITUDE_MEDIUM
    HapticIntensity.STRONG -> AMPLITUDE_STRONG
}

/**
 * Resolves one interaction into a concrete haptic, or null when feedback is off.
 * A themed override replaces the default pattern per action; NONE silences that action.
 */
fun resolveHaptic(
    action: HapticAction,
    overridePattern: HapticPattern?,
    intensity: HapticIntensity,
): ResolvedHaptic? {
    if (intensity == HapticIntensity.OFF) return null
    val pattern = overridePattern ?: action.defaultPattern()
    if (pattern == HapticPattern.NONE) return null
    val amplitude = intensity.amplitude()
    if (amplitude <= 0) return null
    return ResolvedHaptic(pattern, amplitude)
}

/** Platform haptic output. Shared UI calls this; hosts decide how (or whether) to vibrate. */
fun interface Haptics {
    fun perform(action: HapticAction)
}

object DisabledHaptics : Haptics {
    override fun perform(action: HapticAction) = Unit
}

/** Provided by the platform entry point; defaults to disabled so desktop needs no wiring. */
val LocalRelayHaptics = staticCompositionLocalOf<Haptics> { DisabledHaptics }
