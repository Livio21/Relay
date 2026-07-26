package dev.relay.music.lastfm

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.relay.music.lastfm.PendingScrobble
import dev.relay.music.lastfm.ScrobbleListenTimer
import dev.relay.music.lastfm.ScrobbleRule
import dev.relay.music.lastfm.pendingScrobbleId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class LastFmTracker(
    private val api: LastFmApi,
    private val sessionKeyStore: SessionKeyStore,
    private val database: LastFmDatabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushMutex = Mutex()
    private var active: ActiveTrack? = null

    fun onPlayerEvent(player: Player) {
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val mediaItem = player.currentMediaItem
        val mediaId = mediaItem?.mediaId

        if (active?.mediaId != mediaId) {
            active?.let { finish(it, nowElapsedMs) }
            active = mediaItem?.asActiveTrack()?.also {
                it.durationMs = player.duration.validDuration()
            }
            flushPending()
        }

        active?.let { current ->
            current.durationMs = player.duration.validDuration() ?: current.durationMs
            val beganPlaying = player.isPlaying && !current.isPlaying
            current.timer.setPlaying(player.isPlaying, nowElapsedMs)
            current.isPlaying = player.isPlaying
            if (beganPlaying && current.startedAtEpochSeconds == null) {
                current.startedAtEpochSeconds = Clock.System.now().epochSeconds
                sendNowPlaying(current)
            }
        }
    }

    fun release() {
        active?.let { track ->
            track.timer.setPlaying(isPlaying = false, elapsedRealtimeMs = SystemClock.elapsedRealtime())
            pendingScrobble(track, SystemClock.elapsedRealtime())?.let { scrobble ->
                runBlocking(Dispatchers.IO) {
                    database.pendingScrobbleDao().insert(scrobble.asEntity())
                }
            }
        }
        active = null
        scope.cancel()
        api.close()
    }

    fun debugScrobble(player: Player) {
        val track = player.currentMediaItem?.asActiveTrack() ?: return
        val duration = player.duration.validDuration()?.takeIf { it > 30_000L } ?: return
        val timestamp = Clock.System.now().epochSeconds
        val scrobble = PendingScrobble(
            id = pendingScrobbleId("${track.mediaId}:debug", timestamp),
            artist = track.artist,
            track = track.title,
            album = track.album,
            durationMs = duration,
            startedAtEpochSeconds = timestamp,
        )
        scope.launch {
            database.pendingScrobbleDao().insert(scrobble.asEntity())
            flushPending()
        }
    }

    private fun finish(track: ActiveTrack, nowElapsedMs: Long) {
        track.timer.setPlaying(isPlaying = false, elapsedRealtimeMs = nowElapsedMs)
        val scrobble = pendingScrobble(track, nowElapsedMs) ?: return
        scope.launch {
            database.pendingScrobbleDao().insert(scrobble.asEntity())
            flushPending()
        }
    }

    private fun sendNowPlaying(track: ActiveTrack) {
        scope.launch {
            val session = sessionKeyStore.read() ?: return@launch
            when (
                val result = api.updateNowPlaying(
                    sessionKey = session.key,
                    artist = track.artist,
                    track = track.title,
                    album = track.album,
                    durationMs = track.durationMs ?: 0,
                )
            ) {
                is LastFmResult.Failure -> {
                    if (result.kind == LastFmResult.Kind.INVALID_SESSION) sessionKeyStore.clear()
                }
                is LastFmResult.Success -> Unit
            }
        }
    }

    private fun flushPending() {
        scope.launch {
            flushMutex.withLock {
                val session = sessionKeyStore.read() ?: return@withLock
                for (entity in database.pendingScrobbleDao().pending()) {
                    when (val result = api.scrobble(session.key, entity.asPendingScrobble())) {
                        is LastFmResult.Success -> database.pendingScrobbleDao().delete(entity.id)
                        is LastFmResult.Failure -> {
                            if (result.kind == LastFmResult.Kind.INVALID_SESSION) sessionKeyStore.clear()
                            return@withLock
                        }
                    }
                }
            }
        }
    }

    private fun PendingScrobble.asEntity() = PendingScrobbleEntity(
        id = id,
        artist = artist,
        track = track,
        album = album,
        durationMs = durationMs,
        startedAtEpochSeconds = startedAtEpochSeconds,
    )

    private fun pendingScrobble(track: ActiveTrack, nowElapsedMs: Long): PendingScrobble? {
        val startedAt = track.startedAtEpochSeconds ?: return null
        val duration = track.durationMs ?: return null
        if (!ScrobbleRule.isEligible(duration, track.timer.listenedMs(nowElapsedMs))) return null
        return PendingScrobble(
            id = pendingScrobbleId(track.mediaId, startedAt),
            artist = track.artist,
            track = track.title,
            album = track.album,
            durationMs = duration,
            startedAtEpochSeconds = startedAt,
        )
    }

    private fun MediaItem.asActiveTrack(): ActiveTrack? {
        val title = mediaMetadata.title?.toString()?.cleanMetadata() ?: return null
        val artist = mediaMetadata.artist?.toString()?.cleanMetadata() ?: return null
        return ActiveTrack(
            mediaId = mediaId,
            title = title,
            artist = artist,
            album = mediaMetadata.albumTitle?.toString()?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    private fun String.cleanMetadata(): String? =
        trim().takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }

    private fun Long.validDuration(): Long? =
        takeIf { it != C.TIME_UNSET && it > 0 }

    private class ActiveTrack(
        val mediaId: String,
        val title: String,
        val artist: String,
        val album: String?,
        val timer: ScrobbleListenTimer = ScrobbleListenTimer(),
        var durationMs: Long? = null,
        var isPlaying: Boolean = false,
        var startedAtEpochSeconds: Long? = null,
    )
}
