package dev.relay.music.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
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
import dev.relay.music.library.LocalArtworkCache
import dev.relay.music.library.RoomNowPlayingSnapshotStore
import dev.relay.music.library.UserLibraryStore
import dev.relay.music.playback.parseReplayGainDb
import dev.relay.music.playback.replayGainVolume
import dev.relay.music.playback.CrossfadeStage
import dev.relay.music.playback.crossfadeHandoffPositionMs
import dev.relay.music.playback.crossfadeStage
import dev.relay.music.playback.effectiveCrossfadeMs
import dev.relay.music.settings.RelaySettings
import dev.relay.music.widget.NowPlayingWidget
import dev.relay.music.wallpaper.AlbumWallpaperService
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.channels.Channel
import java.io.File
import kotlin.math.pow

class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    /** Not a MediaSession player: it only decodes the next item during a bounded overlap. */
    private var crossfadePreloader: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var audioEffects: NativeAudioEffects? = null
    private var wallpaperAudioVisualizer: WallpaperAudioVisualizer? = null
    private var audioSettings = RelaySettings()
    private var lastFmTracker: LastFmTracker? = null
    private var wallpaperVisibilityReceiverRegistered = false
    private val wallpaperVisibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateWallpaperVisualizerEnabled()
    }
    private val snapshotScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val snapshotWrites = Channel<NowPlayingSnapshot>(Channel.CONFLATED)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var fadeInStartedAtMs: Long? = null
    private var transitionFadePending = false
    /** Fade and ReplayGain both drive one player volume, so they are composed rather than set. */
    private var fadeVolume = 1f
    private var preloaderFadeVolume = 0f
    private var trackGainDb: Float? = null
    private var preloaderGainDb: Float? = null
    private var crossfadeTargetIndex = -1
    private var crossfadeStartedAtMs: Long? = null
    private var activeCrossfadeDurationMs = 0
    private val snapshotWritePolicy = NowPlayingSnapshotWritePolicy()
    private val snapshotStore by lazy {
        RoomNowPlayingSnapshotStore(UserLibraryStore.database(this).userLibraryDao())
    }
    private val artworkCache by lazy { LocalArtworkCache(File(cacheDir, "relay-artwork")) }
    private val snapshotTick = object : Runnable {
        override fun run() {
            val currentPlayer = player ?: return
            if (!currentPlayer.isPlaying) return
            writeNowPlayingSnapshot(currentPlayer)
            mainHandler.postDelayed(this, SNAPSHOT_WRITE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sessionPlaying = false
        snapshotScope.launch {
            for (snapshot in snapshotWrites) {
                snapshotStore.write(snapshot)
                NowPlayingWidget().updateAll(this@PlaybackService)
                sendBroadcast(Intent(AlbumWallpaperService.ACTION_SNAPSHOT_CHANGED).setPackage(packageName))
            }
        }
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(playbackDataSourceFactory()))
            .build()
            .apply { setAudioAttributes(AudioAttributes.DEFAULT, true) }
        registerWallpaperVisibilityReceiver()
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
                    writeNowPlayingSnapshot(
                        player,
                        force = events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                            events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
                            events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && player.playbackState != Player.STATE_READY,
                    )
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    this@PlaybackService.player?.let { lastFmTracker?.onMediaItemTransition(it, mediaItem) }
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                        this@PlaybackService.player?.let(::finishCrossfadeIfNeeded) == true
                    ) return
                    cancelCrossfade()
                    trackGainDb = null
                    transitionFadePending = true
                    if (this@PlaybackService.player?.isPlaying == true) startTrackFadeIn()
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION) cancelCrossfade()
                }

                override fun onMetadata(metadata: Metadata) {
                    metadata.replayGainTrackGainDb()?.let { db ->
                        trackGainDb = db
                        applyVolume()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    sessionPlaying = isPlaying
                    updateWallpaperVisualizerEnabled()
                    mainHandler.removeCallbacks(snapshotTick)
                    if (isPlaying) mainHandler.postDelayed(snapshotTick, SNAPSHOT_WRITE_INTERVAL_MS)
                    if (isPlaying) {
                        if (transitionFadePending) startTrackFadeIn() else scheduleTrackFade()
                    } else {
                        cancelCrossfade()
                        mainHandler.removeCallbacks(trackFadeRunnable)
                        resetTrackVolume()
                    }
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    attachAudioEffects(audioSessionId)
                    attachWallpaperAudioVisualizer(audioSessionId)
                }
            },
        )
        attachAudioEffects(requireNotNull(player).audioSessionId)
        attachWallpaperAudioVisualizer(requireNotNull(player).audioSessionId)
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
        sessionPlaying = false
        mainHandler.removeCallbacksAndMessages(null)
        releaseCrossfadePreloader()
        mediaSession?.release()
        lastFmTracker?.release()
        snapshotWrites.close()
        snapshotScope.cancel()
        audioEffects?.release()
        wallpaperAudioVisualizer?.release()
        if (wallpaperVisibilityReceiverRegistered) unregisterReceiver(wallpaperVisibilityReceiver)
        wallpaperVisibilityReceiverRegistered = false
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
                    crossfadePreloader?.setPlaybackSpeed(settings.playbackSpeed)
                    audioEffects?.apply(settings)
                    updateWallpaperVisualizerEnabled()
                    if (!canCrossfade()) cancelCrossfade()
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

    private fun attachWallpaperAudioVisualizer(audioSessionId: Int) {
        wallpaperAudioVisualizer?.release()
        wallpaperAudioVisualizer = WallpaperAudioVisualizer(this, audioSessionId).also { visualizer ->
            visualizer.setEnabled(shouldCaptureWallpaperAudio())
        }
    }

    private fun registerWallpaperVisibilityReceiver() {
        val filter = IntentFilter(AlbumWallpaperService.ACTION_VISIBILITY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wallpaperVisibilityReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(wallpaperVisibilityReceiver, filter)
        }
        wallpaperVisibilityReceiverRegistered = true
    }

    private fun shouldCaptureWallpaperAudio(): Boolean =
        player?.isPlaying == true && audioSettings.wallpaperPreset.soundReactive && AlbumWallpaperService.hasVisibleEngine()

    private fun updateWallpaperVisualizerEnabled() {
        wallpaperAudioVisualizer?.setEnabled(shouldCaptureWallpaperAudio())
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
        preloaderFadeVolume = 0f
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
        val preloaderGain = if (audioSettings.loudnessNormalization) preloaderGainDb?.let(::replayGainVolume) ?: 1f else 1f
        crossfadePreloader?.volume = (preloaderFadeVolume * preloaderGain * equalizerHeadroom).coerceIn(0f, 1f)
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
            if (updateCrossfade(currentPlayer, now)) {
                mainHandler.postDelayed(this, TRACK_FADE_TICK_MS)
                return
            }
            val fadeInStartedAt = fadeInStartedAtMs
            if (fadeInStartedAt != null) {
                val progress = ((now - fadeInStartedAt).toFloat() / audioSettings.fadeInMs).coerceIn(0f, 1f)
                fadeVolume = progress
                applyVolume()
                if (progress >= 1f) fadeInStartedAtMs = null
            } else {
                val remainingMs = currentPlayer.duration.takeIf { it != C.TIME_UNSET }?.minus(currentPlayer.currentPosition)
                // Crossfade owns the outgoing volume; applying both would raise the outgoing track
                // back to full volume at the start of the overlap.
                val fadeOutMs = if (canCrossfade()) 0 else audioSettings.fadeOutMs
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

    /** Resolves local and extension streams identically for the session player and its preloader. */
    private fun playbackDataSourceFactory() = ResolvingDataSource.Factory(
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

    private fun canCrossfade(): Boolean =
        audioSettings.crossfadeMs > 0 && !audioSettings.equalizerEnabled && audioSettings.bassBoostStrength == 0

    private fun updateCrossfade(currentPlayer: ExoPlayer, nowMs: Long): Boolean {
        if (!canCrossfade()) return false
        val durationMs = currentPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        val configuredDuration = effectiveCrossfadeMs(durationMs, audioSettings.crossfadeMs)
        val duration = activeCrossfadeDurationMs.takeIf { crossfadeStartedAtMs != null } ?: configuredDuration
        val remainingMs = durationMs?.minus(currentPlayer.currentPosition)
        when (crossfadeStage(remainingMs, duration)) {
            CrossfadeStage.NONE -> Unit
            CrossfadeStage.PRELOAD, CrossfadeStage.OVERLAP -> prepareCrossfadePreloader(currentPlayer)
        }
        if (crossfadeStage(remainingMs, duration) != CrossfadeStage.OVERLAP) return false
        val preloader = crossfadePreloader ?: return false
        if (crossfadeStartedAtMs == null && preloader.playbackState == Player.STATE_READY) {
            fadeInStartedAtMs = null
            fadeVolume = 1f
            preloaderFadeVolume = 0f
            crossfadeStartedAtMs = nowMs
            activeCrossfadeDurationMs = duration
            preloader.play()
        }
        val startedAt = crossfadeStartedAtMs ?: return false
        val progress = ((nowMs - startedAt).toFloat() / duration).coerceIn(0f, 1f)
        fadeVolume = 1f - progress
        preloaderFadeVolume = progress
        applyVolume()
        return true
    }

    private fun prepareCrossfadePreloader(currentPlayer: ExoPlayer) {
        if (crossfadePreloader != null) return
        val nextIndex = currentPlayer.nextMediaItemIndex
        if (nextIndex !in 0 until currentPlayer.mediaItemCount || nextIndex == currentPlayer.currentMediaItemIndex) return
        val nextItem = currentPlayer.getMediaItemAt(nextIndex)
        crossfadeTargetIndex = nextIndex
        preloaderGainDb = null
        crossfadePreloader = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(playbackDataSourceFactory()))
            .build()
            .apply {
                setAudioAttributes(AudioAttributes.DEFAULT, false)
                setPlaybackSpeed(audioSettings.playbackSpeed)
                volume = 0f
                addListener(object : Player.Listener {
                    override fun onMetadata(metadata: Metadata) {
                        metadata.replayGainTrackGainDb()?.let { gain ->
                            preloaderGainDb = gain
                            applyVolume()
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        cancelCrossfade()
                    }
                })
                setMediaItem(nextItem)
                prepare()
            }
    }

    private fun finishCrossfadeIfNeeded(currentPlayer: Player): Boolean {
        val startedAt = crossfadeStartedAtMs ?: return false
        if (currentPlayer.currentMediaItemIndex != crossfadeTargetIndex) return false
        val nextTrackGainDb = preloaderGainDb
        val handoffPositionMs = crossfadeHandoffPositionMs(
            SystemClock.elapsedRealtime() - startedAt,
            activeCrossfadeDurationMs,
        )
        releaseCrossfadePreloader()
        fadeVolume = 1f
        preloaderFadeVolume = 0f
        trackGainDb = nextTrackGainDb
        transitionFadePending = false
        applyVolume()
        if (handoffPositionMs > 0) currentPlayer.seekTo(handoffPositionMs)
        scheduleTrackFade()
        return true
    }

    private fun cancelCrossfade() {
        if (crossfadePreloader == null && crossfadeStartedAtMs == null) return
        releaseCrossfadePreloader()
        resetTrackVolume()
    }

    private fun releaseCrossfadePreloader() {
        crossfadePreloader?.release()
        crossfadePreloader = null
        crossfadeTargetIndex = -1
        crossfadeStartedAtMs = null
        activeCrossfadeDurationMs = 0
        preloaderGainDb = null
        preloaderFadeVolume = 0f
    }

    private fun writeNowPlayingSnapshot(currentPlayer: androidx.media3.common.Player, force: Boolean = false) {
        val item = currentPlayer.currentMediaItem
        val trackKey = item?.mediaId?.takeIf { it.isNotBlank() }
        val metadata = item?.mediaMetadata
        val snapshot = nowPlayingSnapshot(
            trackKey = trackKey,
            title = metadata?.title?.toString()?.takeIf { it.isNotBlank() },
            artist = metadata?.artist?.toString()?.takeIf { it.isNotBlank() },
            album = metadata?.albumTitle?.toString()?.takeIf { it.isNotBlank() },
            artworkCacheKey = artworkCache.cacheKey(metadata?.artworkUri?.toString()),
            isPlaying = currentPlayer.isPlaying,
            positionMs = currentPlayer.currentPosition.coerceAtLeast(0),
            durationMs = currentPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        if (snapshotWritePolicy.shouldWrite(snapshot, SystemClock.elapsedRealtime(), force)) snapshotWrites.trySend(snapshot)
    }

    companion object {
        @Volatile
        internal var sessionPlaying: Boolean = false
            private set

        const val ACTION_DEBUG_SCROBBLE = "dev.relay.music.action.DEBUG_SCROBBLE"
        const val ACTION_WIDGET_NEXT = "dev.relay.music.action.WIDGET_NEXT"
        const val ACTION_WIDGET_PLAY_PAUSE = "dev.relay.music.action.WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_PREVIOUS = "dev.relay.music.action.WIDGET_PREVIOUS"
        private const val TRACK_FADE_TICK_MS = 50L
        private const val REPLAY_GAIN_TAG = "replaygain_track_gain"
    }
}
