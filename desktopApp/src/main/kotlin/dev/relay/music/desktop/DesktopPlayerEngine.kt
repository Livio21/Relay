package dev.relay.music.desktop

import dev.relay.music.model.Track
import dev.relay.music.playback.PlaybackState
import dev.relay.music.playback.PlayerEngine
import dev.relay.music.playback.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI

internal class DesktopPlayerEngine : PlayerEngine {
    private val audio = NativeAudio()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState

    init {
        scope.launch {
            while (true) {
                delay(500)
                val current = mutableState.value
                if (current.isPlaying) mutableState.value = current.copy(positionMs = audio.positionMs(), durationMs = audio.durationMs())
            }
        }
    }

    override fun setQueue(tracks: List<Track>, startIndex: Int, playWhenReady: Boolean, startPositionMs: Long) {
        if (startIndex !in tracks.indices) return
        val track = tracks[startIndex]
        val file = runCatching { File(URI(track.playbackUri)) }.getOrNull()
        if (file == null || !audio.load(file)) {
            mutableState.value = PlaybackState(error = "Could not open this audio file.")
            return
        }
        audio.seekTo(startPositionMs)
        if (playWhenReady) audio.play()
        mutableState.value = PlaybackState(tracks, startIndex, playWhenReady, startPositionMs, audio.durationMs())
    }

    override fun play() { audio.play(); mutableState.value = mutableState.value.copy(isPlaying = true) }
    override fun pause() { audio.pause(); mutableState.value = mutableState.value.copy(isPlaying = false) }
    override fun seekTo(positionMs: Long) { audio.seekTo(positionMs); mutableState.value = mutableState.value.copy(positionMs = positionMs) }
    override fun seekToIndex(index: Int) {
        val state = mutableState.value
        if (index in state.queue.indices) setQueue(state.queue, index, playWhenReady = state.isPlaying)
    }
    override fun skipNext() { move(1) }
    override fun skipPrevious() { move(-1) }
    override fun setRepeatMode(mode: RepeatMode) { mutableState.value = mutableState.value.copy(repeatMode = mode) }
    override fun setShuffleEnabled(enabled: Boolean) { mutableState.value = mutableState.value.copy(shuffleEnabled = enabled) }
    override fun setPlaybackSpeed(speed: Float) = Unit
    override fun release() { scope.cancel(); audio.close() }

    private fun move(delta: Int) {
        val state = mutableState.value
        val next = state.currentIndex + delta
        if (next in state.queue.indices) setQueue(state.queue, next)
    }
}
