package dev.relay.music.lastfm

import okio.ByteString.Companion.encodeUtf8

enum class LastFmConnectionState {
    SETUP_REQUIRED,
    DISCONNECTED,
    AUTHORIZING,
    CONNECTED,
    ERROR,
}

data class PendingScrobble(
    val id: String,
    val artist: String,
    val track: String,
    val album: String?,
    val durationMs: Long,
    val startedAtEpochSeconds: Long,
)

fun pendingScrobbleId(mediaId: String, startedAtEpochSeconds: Long): String =
    "$mediaId:$startedAtEpochSeconds"

object ScrobbleRule {
    private const val MINIMUM_DURATION_MS = 30_000L
    private const val MAXIMUM_LISTEN_MS = 4 * 60_000L

    fun isEligible(durationMs: Long, listenedMs: Long): Boolean =
        durationMs > MINIMUM_DURATION_MS && listenedMs >= requiredListenMs(durationMs)

    fun requiredListenMs(durationMs: Long): Long =
        (durationMs / 2).coerceAtMost(MAXIMUM_LISTEN_MS)
}

class ScrobbleListenTimer {
    private var listenedMs = 0L
    private var playingSinceMs: Long? = null

    fun setPlaying(isPlaying: Boolean, elapsedRealtimeMs: Long) {
        val startedAt = playingSinceMs
        when {
            isPlaying && startedAt == null -> playingSinceMs = elapsedRealtimeMs
            !isPlaying && startedAt != null -> {
                listenedMs += (elapsedRealtimeMs - startedAt).coerceAtLeast(0)
                playingSinceMs = null
            }
        }
    }

    fun listenedMs(elapsedRealtimeMs: Long): Long =
        listenedMs + (playingSinceMs?.let { (elapsedRealtimeMs - it).coerceAtLeast(0) } ?: 0L)
}

fun lastFmSignature(parameters: Map<String, String>, sharedSecret: String): String =
    parameters.asSequence()
        .filter { (key, _) -> key != "format" && key != "callback" }
        .sortedBy { (key, _) -> key }
        .joinToString(separator = "") { (key, value) -> key + value }
        .plus(sharedSecret)
        .encodeUtf8()
        .md5()
        .hex()
