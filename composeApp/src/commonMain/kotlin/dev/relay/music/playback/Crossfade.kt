package dev.relay.music.playback

const val MAX_CROSSFADE_MS = 4_000
private const val CROSSFADE_PRELOAD_LEAD_MS = 1_000L

enum class CrossfadeStage { NONE, PRELOAD, OVERLAP }

/** Caps an overlap so even short tracks retain an audible non-overlapped portion. */
fun effectiveCrossfadeMs(trackDurationMs: Long?, configuredMs: Int): Int {
    val duration = trackDurationMs ?: return 0
    return configuredMs.coerceIn(0, MAX_CROSSFADE_MS).coerceAtMost((duration / 2).toInt().coerceAtLeast(0))
}

fun crossfadeStage(remainingMs: Long?, crossfadeMs: Int): CrossfadeStage {
    val remaining = remainingMs ?: return CrossfadeStage.NONE
    if (crossfadeMs <= 0 || remaining < 0) return CrossfadeStage.NONE
    return when {
        remaining <= crossfadeMs -> CrossfadeStage.OVERLAP
        remaining <= crossfadeMs + CROSSFADE_PRELOAD_LEAD_MS -> CrossfadeStage.PRELOAD
        else -> CrossfadeStage.NONE
    }
}

fun crossfadeHandoffPositionMs(elapsedMs: Long, crossfadeMs: Int): Long =
    elapsedMs.coerceIn(0, crossfadeMs.toLong().coerceAtLeast(0))
