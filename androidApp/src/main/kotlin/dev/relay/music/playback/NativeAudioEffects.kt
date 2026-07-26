package dev.relay.music.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import dev.relay.music.settings.RelaySettings

/** Android effects attached to Relay's one playback session. */
internal class NativeAudioEffects(private val audioSessionId: Int) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    fun apply(settings: RelaySettings) {
        applyEqualizer(settings)
        applyBassBoost(settings)
    }

    fun release() {
        equalizer?.release()
        bassBoost?.release()
        equalizer = null
        bassBoost = null
    }

    private fun applyEqualizer(settings: RelaySettings) {
        runCatching {
            val effect = equalizer ?: if (settings.equalizerEnabled) {
                Equalizer(0, audioSessionId).also { equalizer = it }
            } else {
                null
            } ?: return
            effect.enabled = settings.equalizerEnabled
            if (!settings.equalizerEnabled) return

            val range = effect.bandLevelRange
            val bandCount = effect.numberOfBands.toInt()
            if (bandCount <= 0) return
            // ponytail: portable five-band controls are linearly mapped; expose native bands if device-specific EQ becomes necessary.
            repeat(bandCount) { index ->
                val sourceIndex = if (bandCount == 1) 0 else index * settings.equalizerBandLevels.lastIndex / (bandCount - 1)
                val targetLevel = settings.equalizerBandLevels[sourceIndex].coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                effect.setBandLevel(index.toShort(), targetLevel)
            }
        }
    }

    private fun applyBassBoost(settings: RelaySettings) {
        runCatching {
            val enabled = settings.bassBoostStrength > 0
            val effect = bassBoost ?: if (enabled) {
                BassBoost(0, audioSessionId).also { bassBoost = it }
            } else {
                null
            } ?: return
            effect.enabled = enabled
            if (enabled) effect.setStrength(settings.bassBoostStrength.coerceIn(0, 1_000).toShort())
        }
    }
}
