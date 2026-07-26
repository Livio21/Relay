package dev.relay.music.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.relay.music.model.Track
import dev.relay.music.playback.PlaybackState
import dev.relay.music.playback.PlayerEngine
import dev.relay.music.playback.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executor

class AndroidPlayerEngine(context: Context) : PlayerEngine {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor(mainHandler::post)
    private val mutableState = MutableStateFlow(PlaybackState())
    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
    ).setListener(
        object : MediaController.Listener {
            override fun onDisconnected(controller: MediaController) {
                if (!released) {
                    mutableState.update {
                        it.copy(isPlaying = false, error = "Playback service disconnected.")
                    }
                }
            }
        },
    ).buildAsync()

    private var controller: MediaController? = null
    private var requestedQueue = emptyList<Track>()
    private var requestedIndex = -1
    private var requestedPositionMs = 0L
    private var restoreRequest: RestoreRequest? = null
    private var shouldPlay = false
    private var released = false

    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            mutableState.update {
                it.copy(isPlaying = false, error = "The selected file could not be played.")
            }
        }
    }

    private val positionTicker = object : Runnable {
        override fun run() {
            controller?.let(::publish)
            if (controller?.isPlaying == true) {
                mainHandler.postDelayed(this, POSITION_REFRESH_MS)
            }
        }
    }

    init {
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }
                    .onSuccess { connectedController ->
                        if (released) {
                            connectedController.release()
                        } else {
                            controller = connectedController
                            connectedController.addListener(playerListener)
                            if (requestedIndex in requestedQueue.indices) {
                                applyQueue(connectedController)
                            } else if (connectedController.mediaItemCount == 0) {
                                restoreRequest?.let { request ->
                                    setQueue(
                                        tracks = request.tracks,
                                        startIndex = request.startIndex,
                                        playWhenReady = false,
                                        startPositionMs = request.positionMs,
                                    )
                                }
                            } else {
                                adoptSessionQueue(connectedController)
                                publish(connectedController)
                            }
                        }
                    }
                    .onFailure {
                        if (!released) {
                            mutableState.update {
                                it.copy(error = "Could not connect to playback service.")
                            }
                        }
                    }
            },
            mainExecutor,
        )
    }

    override fun setQueue(
        tracks: List<Track>,
        startIndex: Int,
        playWhenReady: Boolean,
        startPositionMs: Long,
    ) {
        if (tracks.isEmpty()) {
            requestedQueue = emptyList()
            requestedIndex = -1
            requestedPositionMs = 0L
            shouldPlay = false
            controller?.apply {
                clearMediaItems()
                stop()
            }
            mutableState.value = PlaybackState()
            return
        }
        if (startIndex !in tracks.indices) {
            mutableState.update { it.copy(error = "The selected track is unavailable.") }
            return
        }

        requestedQueue = tracks.toList()
        requestedIndex = startIndex
        requestedPositionMs = startPositionMs.coerceAtLeast(0)
        restoreRequest = null
        shouldPlay = playWhenReady
        mutableState.value = PlaybackState(
            queue = requestedQueue,
            currentIndex = requestedIndex,
            isPlaying = playWhenReady,
            positionMs = requestedPositionMs,
            durationMs = requestedQueue[requestedIndex].durationMs ?: 0,
            shuffleEnabled = mutableState.value.shuffleEnabled,
        )
        controller?.let(::applyQueue)
    }

    override fun play() {
        shouldPlay = true
        controller?.play()
    }

    override fun pause() {
        shouldPlay = false
        controller?.pause()
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
        mutableState.update { it.copy(positionMs = positionMs.coerceAtLeast(0)) }
    }

    override fun seekToIndex(index: Int) {
        if (index !in mutableState.value.queue.indices) return
        requestedIndex = index
        requestedPositionMs = 0
        controller?.seekTo(index, 0)
        mutableState.update { it.copy(currentIndex = index, positionMs = 0) }
    }

    override fun skipNext() {
        controller?.seekToNextMediaItem()
    }

    override fun skipPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    override fun setRepeatMode(mode: RepeatMode) {
        mutableState.update { it.copy(repeatMode = mode) }
        controller?.setRepeatMode(
            when (mode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            },
        )
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        mutableState.update { it.copy(shuffleEnabled = enabled) }
        controller?.setShuffleModeEnabled(enabled)
    }

    override fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed.coerceIn(0.5f, 2f))
    }

    override fun release() {
        if (released) return
        released = true
        mainHandler.removeCallbacks(positionTicker)
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
    }

    fun restoreQueueIfIdle(tracks: List<Track>, startIndex: Int, positionMs: Long) {
        if (tracks.isEmpty() || startIndex !in tracks.indices) return
        val request = RestoreRequest(tracks, startIndex, positionMs.coerceAtLeast(0))
        val connectedController = controller
        if (connectedController == null) {
            restoreRequest = request
        } else if (connectedController.mediaItemCount == 0 && requestedQueue.isEmpty()) {
            setQueue(request.tracks, request.startIndex, playWhenReady = false, startPositionMs = request.positionMs)
        }
    }

    fun refreshMetadata(libraryTracks: List<Track>) {
        val replacements = libraryTracks.associateBy { "${it.sourceId}\u0000${it.id}" }
        val refreshedQueue = requestedQueue.map { track -> replacements["${track.sourceId}\u0000${track.id}"] ?: track }
        if (refreshedQueue == requestedQueue) return
        requestedQueue = refreshedQueue
        mutableState.update { it.copy(queue = refreshedQueue) }
        controller?.let { connectedController ->
            val position = connectedController.currentPosition.coerceAtLeast(0)
            val index = connectedController.currentMediaItemIndex.coerceIn(0, refreshedQueue.lastIndex)
            connectedController.setMediaItems(refreshedQueue.map(::mediaItem), index, position)
            if (connectedController.isPlaying) connectedController.play() else connectedController.pause()
        }
    }

    /** Rebuilds this engine's queue from the media session that outlived the activity. */
    private fun adoptSessionQueue(controller: MediaController) {
        if (requestedQueue.isNotEmpty() || controller.mediaItemCount == 0) return
        val adopted = (0 until controller.mediaItemCount).mapNotNull { index ->
            controller.getMediaItemAt(index).asTrack()
        }
        if (adopted.size != controller.mediaItemCount) return
        requestedQueue = adopted
        requestedIndex = controller.currentMediaItemIndex.coerceIn(0, adopted.lastIndex)
        requestedPositionMs = controller.currentPosition.coerceAtLeast(0)
        mutableState.value = PlaybackState(
            queue = adopted,
            currentIndex = requestedIndex,
            isPlaying = controller.isPlaying,
            positionMs = requestedPositionMs,
            durationMs = adopted[requestedIndex].durationMs ?: 0,
        )
    }

    private fun applyQueue(controller: MediaController) {
        controller.setMediaItems(requestedQueue.map(::mediaItem), requestedIndex, requestedPositionMs)
        controller.prepare()
        if (shouldPlay) controller.play() else controller.pause()
    }

    private fun publish(player: Player) {
        val previous = mutableState.value
        val currentIndex = player.currentMediaItemIndex.takeIf { it in previous.queue.indices } ?: -1
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 }
            ?: previous.queue.getOrNull(currentIndex)?.durationMs
            ?: 0
        mutableState.value = previous.copy(
            currentIndex = currentIndex,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0),
            repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            shuffleEnabled = player.shuffleModeEnabled,
            error = null,
        )
        mainHandler.removeCallbacks(positionTicker)
        if (player.isPlaying) mainHandler.postDelayed(positionTicker, POSITION_REFRESH_MS)
    }

    private fun mediaItem(track: Track): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setExtras(
                Bundle().apply {
                    putString(EXTRA_SOURCE_ID, track.sourceId)
                    putString(EXTRA_TRACK_ID, track.id)
                    track.durationMs?.let { putLong(EXTRA_DURATION_MS, it) }
                    track.albumArtist?.let { putString(EXTRA_ALBUM_ARTIST, it) }
                    track.releaseDate?.let { putString(EXTRA_RELEASE_DATE, it) }
                    track.artworkHue?.let { putInt(EXTRA_ARTWORK_HUE, it) }
                },
            )
            .apply {
                track.artworkUri?.takeIf { it.isNotBlank() }?.let { setArtworkUri(it.toUri()) }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId("${track.sourceId}:${track.id}")
            .setUri(track.playbackUri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun MediaItem.asTrack(): Track? {
        val extras = mediaMetadata.extras ?: return null
        val sourceId = extras.getString(EXTRA_SOURCE_ID) ?: return null
        val trackId = extras.getString(EXTRA_TRACK_ID) ?: return null
        return Track(
            id = trackId,
            sourceId = sourceId,
            playbackUri = localConfiguration?.uri?.toString().orEmpty(),
            title = mediaMetadata.title?.toString().orEmpty(),
            artist = mediaMetadata.artist?.toString().orEmpty(),
            album = mediaMetadata.albumTitle?.toString(),
            albumArtist = extras.getString(EXTRA_ALBUM_ARTIST),
            releaseDate = extras.getString(EXTRA_RELEASE_DATE),
            durationMs = extras.getLong(EXTRA_DURATION_MS).takeIf { it > 0 },
            artworkUri = mediaMetadata.artworkUri?.toString(),
            artworkHue = extras.getInt(EXTRA_ARTWORK_HUE, -1).takeIf { it in 0..359 },
        )
    }

    private data class RestoreRequest(
        val tracks: List<Track>,
        val startIndex: Int,
        val positionMs: Long,
    )

    private companion object {
        const val POSITION_REFRESH_MS = 500L
        const val EXTRA_SOURCE_ID = "relay.sourceId"
        const val EXTRA_TRACK_ID = "relay.trackId"
        const val EXTRA_DURATION_MS = "relay.durationMs"
        const val EXTRA_ALBUM_ARTIST = "relay.albumArtist"
        const val EXTRA_RELEASE_DATE = "relay.releaseDate"
        const val EXTRA_ARTWORK_HUE = "relay.artworkHue"
    }
}
