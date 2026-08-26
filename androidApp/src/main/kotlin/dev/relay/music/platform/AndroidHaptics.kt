package dev.relay.music.platform

import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import dev.relay.music.haptics.HapticAction
import dev.relay.music.haptics.HapticPattern
import dev.relay.music.haptics.Haptics
import dev.relay.music.haptics.ResolvedHaptic

/**
 * Plays resolved patterns through the strongest channels available, in order:
 * 1. Composed haptic primitives — the motor's native effects (what makes system UI feel crisp).
 * 2. Predefined hardware effects (EFFECT_CLICK family).
 * 3. Raw waveforms with physical-emulation usage.
 * 4. View.performHapticFeedback as the last fallback.
 * Amplitude/intensity selects primitive scale; OEM "touch feedback" damping never applies.
 */
class AndroidHaptics(
    private val view: View,
    private val resolve: (HapticAction) -> ResolvedHaptic?,
) : Haptics {

    override fun perform(action: HapticAction) {
        val resolved = resolve(action)
        if (resolved == null) {
            Log.d(TAG, "skip $action: unresolved (intensity off or pattern NONE)")
            return
        }
        val vibrator = vibrator()
        Log.d(
            TAG,
            "perform $action pattern=${resolved.pattern} amp=${resolved.amplitude} " +
                "vibrator=${vibrator != null}",
        )
        val attributes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_PHYSICAL_EMULATION)
        } else {
            null
        }
        // 1. Native primitives — sharpest, OEM-tuned.
        if (vibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
            )
        ) {
            composedFor(resolved.pattern, intensityScale(resolved.amplitude))?.let { effect ->
                runCatching { vibrate(vibrator, effect, attributes) }
                    .onFailure { Log.w(TAG, "primitive vibration failed", it) }
                return
            }
        }
        // 2. Predefined hardware effects.
        if (vibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            predefinedFor(resolved.pattern)?.let { id ->
                runCatching { vibrate(vibrator, VibrationEffect.createPredefined(id), attributes) }
                    .onFailure { Log.w(TAG, "predefined vibration failed", it) }
                return
            }
        }
        // 3. Raw waveform.
        if (vibrator != null && vibrator.hasAmplitudeControl()) {
            waveformFor(resolved)?.let { effect ->
                runCatching { vibrate(vibrator, effect, attributes) }
                    .onFailure { Log.w(TAG, "waveform vibration failed", it) }
                return
            }
        }
        // 4. View-system fallback.
        primitive(constantFor(resolved.pattern))
    }

    private fun primitive(constant: Int) {
        view.performHapticFeedback(constant)
    }

    private fun vibrate(vibrator: Vibrator, effect: VibrationEffect, attributes: android.os.VibrationAttributes?) {
        if (attributes != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vibrator.vibrate(effect, attributes)
        } else {
            vibrator.vibrate(effect)
        }
    }

    private fun intensityScale(amplitude: Int): Float = when {
        amplitude >= 250 -> 1.0f
        amplitude >= 180 -> 0.85f
        else -> 0.55f
    }

    private fun composedFor(pattern: HapticPattern, scale: Float): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val composition = VibrationEffect.startComposition()
        when (pattern) {
            HapticPattern.NONE -> return null
            HapticPattern.CLICK -> composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale)
            HapticPattern.TICK -> composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale)
            HapticPattern.THUD -> composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, scale)
            HapticPattern.SOFT -> composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale * 0.6f)
            HapticPattern.DOUBLE_CLICK -> {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale)
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale, 80)
            }
            HapticPattern.RISE -> composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, scale)
        }
        return composition.compose()
    }

    private fun predefinedFor(pattern: HapticPattern): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return when (pattern) {
            HapticPattern.TICK, HapticPattern.SOFT -> VibrationEffect.EFFECT_TICK
            HapticPattern.THUD, HapticPattern.DOUBLE_CLICK -> VibrationEffect.EFFECT_HEAVY_CLICK
            HapticPattern.CLICK, HapticPattern.RISE -> VibrationEffect.EFFECT_CLICK
            HapticPattern.NONE -> null
        }
    }

    private fun waveformFor(resolved: ResolvedHaptic): VibrationEffect? {
        val a = resolved.amplitude.coerceIn(1, 255)
        return when (resolved.pattern) {
            HapticPattern.NONE -> null
            HapticPattern.CLICK -> onePulse(24, a)
            HapticPattern.TICK -> onePulse(9, a)
            HapticPattern.THUD -> onePulse(48, a)
            HapticPattern.SOFT -> onePulse(12, (a / 2).coerceIn(1, 255))
            HapticPattern.DOUBLE_CLICK -> VibrationEffect.createWaveform(
                longArrayOf(0, 30, 70, 30),
                intArrayOf(0, a, 0, a),
                -1,
            )
            HapticPattern.RISE -> VibrationEffect.createWaveform(
                longArrayOf(0, 40, 40, 40, 60),
                intArrayOf(1, (a / 4).coerceAtLeast(1), a / 2, (3 * a / 4).coerceAtLeast(1), a),
                -1,
            )
        }
    }

    private fun onePulse(durationMs: Long, amplitude: Int): VibrationEffect =
        VibrationEffect.createWaveform(longArrayOf(0, durationMs), intArrayOf(0, amplitude), -1)

    private fun constantFor(pattern: HapticPattern): Int = when (pattern) {
        HapticPattern.THUD -> HapticFeedbackConstants.LONG_PRESS
        HapticPattern.TICK -> HapticFeedbackConstants.CLOCK_TICK
        HapticPattern.SOFT -> HapticFeedbackConstants.KEYBOARD_TAP
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
    }

    private fun vibrator(): Vibrator? {
        val context = view.context
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }

    private companion object {
        const val TAG = "RelayHaptics"
    }
}
