package dev.relay.music.playback

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.core.net.toUri
import dev.relay.music.BuildConfig
import dev.relay.music.extension.EXTENSION_STREAM_SCHEME
import dev.relay.music.extension.ExtensionMediaHeaders
import dev.relay.music.extension.ExtensionStreamResolver
import java.io.IOException
import kotlinx.coroutines.runBlocking
import dev.relay.music.lastfm.LastFmApi
import dev.relay.music.lastfm.LastFmStore
import dev.relay.music.lastfm.LastFmTracker
import dev.relay.music.lastfm.SessionKeyStore
import dev.relay.music.library.NowPlayingSnapshotEntity
import dev.relay.music.library.UserLibraryStore
import dev.relay.music.playback.parseReplayGainDb
import dev.relay.music.playback.replayGainVolume
import dev.relay.music.settings.RelaySettings
import dev.relay.music.widget.NowPlayingWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlin.math.pow

class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var audioEffects: NativeAudioEffects? = null
    private var audioSettings = RelaySettings()
    private var lastFmTracker: LastFmTracker? = null
    private val snapshotScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var fadeInStartedAtMs: Long? = null
    private var transitionFadePending = false
    /** Fade and ReplayGain both drive one player volume, so they are composed rather than set. */
    private var fadeVolume = 1f
    private var trackGainDb: Float? = null
    private var lastSnapshotTrackKey: String? = null
    private var lastSnapshotPlaying = false
    private var lastSnapshotWriteElapsedMs = 0L

    override fun onCreate() {
        super.onCreate()
        // Resolves `relay-extension://` placeholders to a fresh stream URL just before the track
        // loads, and attaches source-declared headers (Referer, Cookie, ...) to every request.
        // Media3 calls this on its loading thread, so blocking here is the intended pattern.
        val dataSourceFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, DefaultHttpDataSource.Factory()),
        ) { dataSpec ->
            if (dataSpec.uri.scheme == EXTENSION_STREAM_SCHEME) {
                val resolved = try {
                    runBlocking { ExtensionStreamResolver.resolve(this@PlaybackService, dataSpec.uri.toString()) }
                } catch (error: Throwable) {
                    throw IOException(error.message ?: "This track could not be resolved.", error)
                }
                dataSpec.withUri(resolved.url.toUri()).withAdditionalHeaders(resolved.headers)
            } else {
                val headers = ExtensionMediaHeaders.headersFor(dataSpec.uri.toString())
                if (headers.isEmpty()) dataSpec else dataSpec.withAdditionalHeaders(headers)
            }
        }
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply { setAudioAttributes(AudioAttributes.DEFAULT, true) }
        observePlaybackSettings()
        lastFmTracker = LastFmTracker(
            api = LastFmApi(BuildConfig.LASTFM_API_KEY, BuildConfig.LASTFM_SHARED_SECRET),
            sessionKeyStore = SessionKeyStore(this),
            database = LastFmStore.database(this),
        )
        player?.addListener(
            object : androidx.media3.common.Player.Listener {
                override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                    lastFmTracker?.onPlayerEvent(player)
                    writeNowPlayingSnapshot(player, events)
                }

                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    trackGainDb = null
                    transitionFadePending = true
                    if (this@PlaybackService.player?.isPlaying == true) startTrackFadeIn()
                }

                override fun onMetadata(metadata: Metadata) {
                    metadata.replayGainTrackGainDb()?.let { db ->
                        trackGainDb = db
                        applyVolume()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        if (transitionFadePending) startTrackFadeIn() else scheduleTrackFade()
                    } else {
                        mainHandler.removeCallbacks(trackFadeRunnable)
                        resetTrackVolume()
                    }
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    attachAudioEffects(audioSessionId)
                }
            },
        )
        attachAudioEffects(requireNotNull(player).audioSessionId)
        mediaSession = MediaSession.Builder(this, requireNotNull(player)).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DEBUG_SCROBBLE -> if (BuildConfig.DEBUG) {
                player?.let { currentPlayer -> lastFmTracker?.debugScrobble(currentPlayer) }
            }
            ACTION_WIDGET_PREVIOUS -> player?.seekToPreviousMediaItem()
            ACTION_WIDGET_PLAY_PAUSE -> player?.let { if (it.isPlaying) it.pause() else it.play() }
            ACTION_WIDGET_NEXT -> player?.seekToNextMediaItem()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        mediaSession?.release()
        lastFmTracker?.release()
        snapshotScope.cancel()
        audioEffects?.release()
        player?.release()
        mediaSession = null
        player = null
        lastFmTracker = null
        super.onDestroy()
    }

    private fun observePlaybackSettings() {
        snapshotScope.launch {
            UserLibraryStore.database(this@PlaybackService).userLibraryDao().settings().collect { entity ->
                val settings = entity?.asSettings() ?: RelaySettings()
                mainHandler.post {
                    audioSettings = settings
                    player?.setPlaybackSpeed(settings.playbackSpeed)
                    audioEffects?.apply(settings)
                    applyVolume()
                }
            }
        }
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        audioEffects?.release()
        audioEffects = NativeAudioEffects(audioSessionId).also { it.apply(audioSettings) }
    }

    private fun startTrackFadeIn() {
        if (player == null) return
        transitionFadePending = false
        if (audioSettings.fadeInMs <= 0) {
            resetTrackVolume()
            scheduleTrackFade()
            return
        }
        fadeInStartedAtMs = SystemClock.elapsedRealtime()
        fadeVolume = 0f
        applyVolume()
        scheduleTrackFade()
    }

    private fun resetTrackVolume() {
        fadeInStartedAtMs = null
        fadeVolume = 1f
        applyVolume()
    }

    /** ReplayGain only attenuates, so a track without tags keeps its original level. */
    private fun applyVolume() {
        val gain = if (audioSettings.loudnessNormalization) {
            trackGainDb?.let(::replayGainVolume) ?: 1f
        } else {
            1f
        }
        val highestBoostDb = audioSettings.equalizerBandLevels.maxOrNull()?.coerceAtLeast(0)?.div(100f) ?: 0f
        // ponytail: output headroom limits EQ boosts; add a real PCM limiter only if clipping is observed.
        val equalizerHeadroom = if (audioSettings.equalizerEnabled) 10.0.pow(-highestBoostDb / 20.0).toFloat() else 1f
        player?.volume = (fadeVolume * gain * equalizerHeadroom).coerceIn(0f, 1f)
    }

    private fun Metadata.replayGainTrackGainDb(): Float? {
        for (index in 0 until length()) {
            val raw = when (val entry = get(index)) {
                is TextInformationFrame ->
                    entry.values.firstOrNull().takeIf { entry.description.equals(REPLAY_GAIN_TAG, ignoreCase = true) }
                is VorbisComment -> entry.value.takeIf { entry.key.equals(REPLAY_GAIN_TAG, ignoreCase = true) }
                else -> null
            }
            parseReplayGainDb(raw)?.let { return it }
        }
        return null
    }

    private fun scheduleTrackFade() {
        mainHandler.removeCallbacks(trackFadeRunnable)
        mainHandler.post(trackFadeRunnable)
    }

    private val trackFadeRunnable = object : Runnable {
        override fun run() {
            val currentPlayer = player ?: return
            if (!currentPlayer.isPlaying) return
            val now = SystemClock.elapsedRealtime()
            val fadeInStartedAt = fadeInStartedAtMs
            if (fadeInStartedAt != null) {
                val progress = ((now - fadeInStartedAt).toFloat() / audioSettings.fadeInMs).coerceIn(0f, 1f)
                fadeVolume = progress
                applyVolume()
                if (progress >= 1f) fadeInStartedAtMs = null
            } else {
                val remainingMs = currentPlayer.duration.takeIf { it != C.TIME_UNSET }?.minus(currentPlayer.currentPosition)
                val fadeOutMs = audioSettings.fadeOutMs
                fadeVolume = if (fadeOutMs > 0 && remainingMs != null && remainingMs in 0..fadeOutMs.toLong()) {
                    remainingMs.toFloat() / fadeOutMs
                } else {
                    1f
                }
                applyVolume()
            }
            mainHandler.postDelayed(this, TRACK_FADE_TICK_MS)
        }
    }

    private fun writeNowPlayingSnapshot(
        currentPlayer: androidx.media3.common.Player,
        events: androidx.media3.common.Player.Events,
    ) {
        val item = currentPlayer.currentMediaItem
        val trackKey = item?.mediaId?.takeIf { it.isNotBlank() }
        val nowElapsedMs = android.os.SystemClock.elapsedRealtime()
        val immediate = trackKey != lastSnapshotTrackKey ||
            currentPlayer.isPlaying != lastSnapshotPlaying ||
            events.contains(androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY)
        if (!immediate && (!currentPlayer.isPlaying || nowElapsedMs - lastSnapshotWriteElapsedMs < 15_000L)) return

        lastSnapshotTrackKey = trackKey
        lastSnapshotPlaying = currentPlayer.isPlaying
        lastSnapshotWriteElapsedMs = nowElapsedMs
        val metadata = item?.mediaMetadata
        val snapshot = NowPlayingSnapshotEntity(
            trackKey = trackKey,
            title = metadata?.title?.toString()?.takeIf { it.isNotBlank() },
            artist = metadata?.artist?.toString()?.takeIf { it.isNotBlank() },
            album = metadata?.albumTitle?.toString()?.takeIf { it.isNotBlank() },
            artworkCacheKey = null,
            isPlaying = currentPlayer.isPlaying,
            positionMs = currentPlayer.currentPosition.coerceAtLeast(0),
            durationMs = currentPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        snapshotScope.launch {
            UserLibraryStore.database(this@PlaybackService).userLibraryDao().saveNowPlayingSnapshot(snapshot)
            NowPlayingWidget().updateAll(this@PlaybackService)
        }
    }

    companion object {
        const val ACTION_DEBUG_SCROBBLE = "dev.relay.music.action.DEBUG_SCROBBLE"
        const val ACTION_WIDGET_NEXT = "dev.relay.music.action.WIDGET_NEXT"
        const val ACTION_WIDGET_PLAY_PAUSE = "dev.relay.music.action.WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_PREVIOUS = "dev.relay.music.action.WIDGET_PREVIOUS"
        private const val TRACK_FADE_TICK_MS = 50L
        private const val REPLAY_GAIN_TAG = "replaygain_track_gain"
    }
}
