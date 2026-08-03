package dev.relay.music.sync

import android.os.SystemClock
import dev.relay.music.model.Track
import dev.relay.music.playback.AndroidPlayerEngine
import dev.relay.music.sync.PlayTogetherCommand
import dev.relay.music.sync.playTogetherCorrection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Applies a verified leader command through Relay's existing authoritative player only. */
internal class PlayTogetherCoordinator(
    private val player: AndroidPlayerEngine,
    private val resolveTrack: (PlayTogetherCommand) -> Track?,
    private val scope: CoroutineScope,
) {
    private var scheduledStart: Job? = null

    fun apply(
        command: PlayTogetherCommand,
        leaderMinusLocalMs: Long,
        resolvedTrack: Track? = resolveTrack(command),
        forceResync: Boolean = false,
    ): PlayTogetherApplyResult {
        val track = resolvedTrack ?: return PlayTogetherApplyResult.Unavailable
        scheduledStart?.cancel()
        val now = SystemClock.elapsedRealtime()
        val correction = playTogetherCorrection(command, now, player.state.value.positionMs, leaderMinusLocalMs)
        val current = player.state.value.currentTrack
        val sameTrack = current?.sourceId == track.sourceId && current.id == track.id
        if (sameTrack && command.playing && correction.needsVisibleResync && !forceResync) {
            return PlayTogetherApplyResult.ResyncRequired(correction.driftMs)
        }
        if (!sameTrack || forceResync) {
            player.setQueue(listOf(track), 0, playWhenReady = false, startPositionMs = correction.targetPositionMs)
        }
        if (command.playing) {
            val targetLocalElapsed = command.targetLeaderElapsedMs - leaderMinusLocalMs
            scheduledStart = scope.launch {
                delay((targetLocalElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                player.play()
            }
        } else {
            player.pause()
        }
        return PlayTogetherApplyResult.Applied(correction.driftMs, correction.needsVisibleResync)
    }

    fun leave() { scheduledStart?.cancel(); scheduledStart = null }
}

internal sealed interface PlayTogetherApplyResult {
    data object Unavailable : PlayTogetherApplyResult
    data class Applied(val driftMs: Long, val needsVisibleResync: Boolean) : PlayTogetherApplyResult
    data class ResyncRequired(val driftMs: Long) : PlayTogetherApplyResult
}
