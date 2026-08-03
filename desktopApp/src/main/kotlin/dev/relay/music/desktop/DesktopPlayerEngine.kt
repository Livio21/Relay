package dev.relay.music.desktop

import dev.relay.music.model.Track
import dev.relay.music.playback.PlaybackState
import dev.relay.music.playback.PlayerEngine
import dev.relay.music.playback.RepeatMode
import java.io.File
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class DesktopPlayerEngine(
    audioFactory: () -> DesktopAudio = { NativeAudio() },
) : PlayerEngine {
    private val audio: DesktopAudio?
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState: MutableStateFlow<PlaybackState>
    override val state: StateFlow<PlaybackState>
        get() = mutableState

    init {
        val audioResult = runCatching(audioFactory)
        audio = audioResult.getOrNull()
        mutableState = MutableStateFlow(
            PlaybackState(error = audioResult.exceptionOrNull()?.usefulMessage()?.let { "Desktop audio is unavailable: $it" }),
        )
        scope.launch {
            while (true) {
                delay(500)
                pollPlayback()
            }
        }
    }

    @Synchronized
    override fun setQueue(tracks: List<Track>, startIndex: Int, playWhenReady: Boolean, startPositionMs: Long) {
        val previous = mutableState.value
        if (tracks.isEmpty() || startIndex !in tracks.indices) {
            audio?.pause()
            mutableState.value = PlaybackState(repeatMode = previous.repeatMode, shuffleEnabled = previous.shuffleEnabled)
            return
        }
        val backend = audio ?: run {
            mutableState.value = previous.copy(error = previous.error ?: "Desktop audio is unavailable.")
            return
        }
        val track = tracks[startIndex]
        val file = runCatching { File(URI(track.playbackUri)) }.getOrNull()?.takeIf(File::isFile)
        if (file == null || !backend.load(file)) {
            mutableState.value = previous.copy(isPlaying = false, error = "Could not open ${track.title}.")
            return
        }
        val duration = backend.durationMs().coerceAtLeast(0)
        val position = startPositionMs.coerceIn(0, duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        backend.seekTo(position)
        if (playWhenReady) backend.play() else backend.pause()
        mutableState.value = previous.copy(
            queue = tracks,
            currentIndex = startIndex,
            isPlaying = playWhenReady,
            positionMs = position,
            durationMs = duration,
            bufferedPositionMs = duration,
            error = null,
        )
    }

    @Synchronized
    override fun play() {
        if (mutableState.value.currentTrack == null || audio == null) return
        if (mutableState.value.durationMs > 0 && mutableState.value.positionMs >= mutableState.value.durationMs) {
            audio.seekTo(0)
        }
        audio.play()
        mutableState.value = mutableState.value.copy(isPlaying = true, positionMs = audio.positionMs())
    }

    @Synchronized
    override fun pause() {
        audio?.pause()
        mutableState.value = mutableState.value.copy(isPlaying = false, positionMs = audio?.positionMs() ?: 0)
    }

    @Synchronized
    override fun seekTo(positionMs: Long) {
        val state = mutableState.value
        if (state.currentTrack == null || audio == null) return
        val position = positionMs.coerceIn(0, state.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        audio.seekTo(position)
        mutableState.value = state.copy(positionMs = position)
    }

    @Synchronized
    override fun seekToIndex(index: Int) {
        val current = mutableState.value
        if (index in current.queue.indices) setQueue(current.queue, index, playWhenReady = current.isPlaying)
    }

    override fun skipNext() = move(1)
    override fun skipPrevious() = move(-1)

    @Synchronized
    override fun setRepeatMode(mode: RepeatMode) {
        mutableState.value = mutableState.value.copy(repeatMode = mode)
    }

    @Synchronized
    override fun setShuffleEnabled(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(shuffleEnabled = enabled)
    }

    @Synchronized
    override fun setPlaybackSpeed(speed: Float) {
        audio?.setPlaybackSpeed(speed)
    }

    @Synchronized
    override fun release() {
        scope.cancel()
        audio?.close()
    }

    @Synchronized
    internal fun pollPlayback() {
        val backend = audio ?: return
        val current = mutableState.value
        if (!current.isPlaying || current.currentTrack == null) return
        val duration = backend.durationMs().coerceAtLeast(0)
        val position = backend.positionMs().coerceAtLeast(0)
        if (duration > 0 && position >= duration) {
            val target = completionTarget(current.currentIndex, current.queue.size, current.repeatMode)
            if (target == null) {
                backend.pause()
                mutableState.value = current.copy(isPlaying = false, positionMs = duration, durationMs = duration)
            } else {
                setQueue(current.queue, target, playWhenReady = true)
            }
        } else {
            mutableState.value = current.copy(positionMs = position, durationMs = duration, bufferedPositionMs = duration)
        }
    }

    @Synchronized
    private fun move(delta: Int) {
        val current = mutableState.value
        val target = manualMoveTarget(current.currentIndex, current.queue.size, delta, current.repeatMode == RepeatMode.ALL) ?: return
        setQueue(current.queue, target, playWhenReady = current.isPlaying)
    }
}

internal fun completionTarget(currentIndex: Int, queueSize: Int, repeatMode: RepeatMode): Int? = when {
    currentIndex !in 0 until queueSize -> null
    repeatMode == RepeatMode.ONE -> currentIndex
    currentIndex + 1 < queueSize -> currentIndex + 1
    repeatMode == RepeatMode.ALL -> 0
    else -> null
}

internal fun manualMoveTarget(currentIndex: Int, queueSize: Int, delta: Int, wrap: Boolean): Int? {
    if (currentIndex !in 0 until queueSize || queueSize == 0) return null
    val target = currentIndex + delta
    if (target in 0 until queueSize) return target
    if (!wrap) return null
    return ((target % queueSize) + queueSize) % queueSize
}

private fun Throwable.usefulMessage(): String =
    generateSequence(this) { it.cause }.mapNotNull { it.message?.takeIf(String::isNotBlank) }.firstOrNull()
        ?: this::class.simpleName.orEmpty().ifBlank { "unknown native audio error" }
