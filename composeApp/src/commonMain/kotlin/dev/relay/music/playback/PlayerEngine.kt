package dev.relay.music.playback

import dev.relay.music.model.Track
import kotlinx.coroutines.flow.StateFlow

enum class RepeatMode { OFF, ONE, ALL }

data class NowPlayingSnapshot(
    val trackKey: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkCacheKey: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAtEpochMs: Long = 0,
)

data class PlaybackState(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val error: String? = null,
) {
    val currentTrack: Track?
        get() = queue.getOrNull(currentIndex)
}

interface PlayerEngine {
    val state: StateFlow<PlaybackState>

    fun setQueue(
        tracks: List<Track>,
        startIndex: Int,
        playWhenReady: Boolean = true,
        startPositionMs: Long = 0,
    )
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    /** Jumps to a queue position, ignoring indexes outside the current queue. */
    fun seekToIndex(index: Int)
    fun skipNext()
    fun skipPrevious()
    fun setRepeatMode(mode: RepeatMode)
    fun setShuffleEnabled(enabled: Boolean)
    fun setPlaybackSpeed(speed: Float)
    fun release()
}
