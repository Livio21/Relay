package dev.relay.music.sync

/** Network-safe playback intent; it never contains audio, a stream URL, or credentials. */
data class PlayTogetherCommand(
    val sourceId: String,
    val trackId: String,
    /** SHA-256 for local audio. Device-local IDs are only a fallback for remote sources. */
    val contentDigest: String? = null,
    val queueIndex: Int,
    val leaderPositionMs: Long,
    val targetLeaderElapsedMs: Long,
    val playing: Boolean,
)

data class PlayTogetherCorrection(val targetPositionMs: Long, val driftMs: Long, val needsVisibleResync: Boolean)

/** One local ping sample using the standard four timestamps (local send/receive, peer receive/send). */
data class ClockProbe(val localSentMs: Long, val peerReceivedMs: Long, val peerSentMs: Long, val localReceivedMs: Long)

data class ClockEstimate(val peerMinusLocalMs: Long, val roundTripMs: Long)

/** Uses the lowest-latency valid probe, which minimizes queueing noise on a local network. */
fun estimateClockOffset(probes: List<ClockProbe>): ClockEstimate? = probes
    .mapNotNull { probe ->
        val roundTrip = probe.localReceivedMs - probe.localSentMs - (probe.peerSentMs - probe.peerReceivedMs)
        if (roundTrip < 0) null else ClockEstimate(
            peerMinusLocalMs = ((probe.peerReceivedMs - probe.localSentMs) + (probe.peerSentMs - probe.localReceivedMs)) / 2,
            roundTripMs = roundTrip,
        )
    }
    .minByOrNull { it.roundTripMs }

/** Maps a leader command to one device's monotonic clock using the measured clock offset. */
fun playTogetherCorrection(
    command: PlayTogetherCommand,
    localElapsedMs: Long,
    localPlaybackPositionMs: Long,
    leaderMinusLocalMs: Long,
    visibleResyncThresholdMs: Long = 250L,
): PlayTogetherCorrection {
    require(visibleResyncThresholdMs >= 0)
    val targetLocalElapsed = command.targetLeaderElapsedMs - leaderMinusLocalMs
    val elapsedSinceTarget = (localElapsedMs - targetLocalElapsed).coerceAtLeast(0)
    val targetPosition = command.leaderPositionMs + if (command.playing) elapsedSinceTarget else 0L
    val drift = targetPosition - localPlaybackPositionMs.coerceAtLeast(0)
    return PlayTogetherCorrection(targetPosition.coerceAtLeast(0), drift, kotlin.math.abs(drift) >= visibleResyncThresholdMs)
}
