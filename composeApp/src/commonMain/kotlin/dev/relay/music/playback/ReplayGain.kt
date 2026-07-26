package dev.relay.music.playback

import kotlin.math.pow

/** Below this the track would be inaudible; treat anything lower as a bad tag. */
const val MIN_REPLAY_GAIN_VOLUME = 0.05f

/** ReplayGain tags look like `-7.50 dB`, `+3 dB`, or a bare number. */
fun parseReplayGainDb(value: String?): Float? {
    val cleaned = value?.trim()?.removeSuffix("dB")?.removeSuffix("DB")?.removeSuffix("db")?.trim() ?: return null
    val db = cleaned.removePrefix("+").toFloatOrNull() ?: return null
    return db.takeIf { it.isFinite() && it in -60f..20f }
}

/**
 * Linear volume for a ReplayGain value. Relay only attenuates: raising a quiet track above unity
 * would clip anything mastered near full scale, and the player has no headroom to give. Loud
 * tracks are brought down to match, which is the audible half of normalization.
 */
fun replayGainVolume(db: Float): Float =
    if (db >= 0f) 1f else 10.0.pow(db / 20.0).toFloat().coerceIn(MIN_REPLAY_GAIN_VOLUME, 1f)
