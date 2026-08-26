package dev.relay.music.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.relay.music.model.SleepTimer
import dev.relay.music.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Owns the sleep timer countdown and its pause action against the shared player engine. */
class SleepTimerController(
    private val engine: PlayerEngine,
    private val scope: CoroutineScope,
    private val trackKeyOf: (Track) -> String,
) {
    var current by mutableStateOf(SleepTimer.OFF)
        private set

    private var job: Job? = null

    fun select(option: SleepTimer) {
        job?.cancel()
        job = null
        current = option
        when (val durationMs = option.durationMs) {
            null -> if (option != SleepTimer.OFF) armEndOfTrack() else Unit
            else -> job = scope.launch {
                delay(durationMs)
                finish()
            }
        }
    }

    private fun armEndOfTrack() {
        // Fire when playback moves past the armed track (or the queue changes under it).
        val armedKey = engine.state.value.currentTrack?.let(trackKeyOf)
        job = scope.launch {
            engine.state.first { it.currentTrack?.let(trackKeyOf) != armedKey }
            finish()
        }
    }

    private suspend fun finish() {
        runCatching { engine.pause() }
        current = SleepTimer.OFF
    }
}
