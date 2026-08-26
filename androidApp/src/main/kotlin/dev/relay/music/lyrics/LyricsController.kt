package dev.relay.music.lyrics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.relay.music.model.Track
import dev.relay.music.platform.UserMessenger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the lyrics state for the currently displayed track: local load, manual save,
 * and network fetch. File import/export stay with the host (they need launchers);
 * they hand their content to [save].
 */
class LyricsController(
    private val scope: CoroutineScope,
    private val dao: dev.relay.music.library.UserLibraryDao,
    private val lyricsApi: LyricsApi,
    private val messenger: UserMessenger,
    private val trackKeyOf: (Track) -> String,
) {
    var text by mutableStateOf<String?>(null)
        private set
    var trackKey by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun load(track: Track) {
        val key = trackKeyOf(track)
        if (trackKey == key) return
        trackKey = key
        text = null
        message = null
        scope.launch {
            text = withContext(Dispatchers.IO) { dao.lyrics(track.sourceId, track.id)?.content }
        }
    }

    fun save(track: Track, content: String) {
        val key = trackKeyOf(track)
        trackKey = key
        text = content.trim().takeIf { it.isNotEmpty() }
        message = null
        scope.launch(Dispatchers.IO) {
            dao.saveLyrics(
                dev.relay.music.library.TrackLyricsEntity(
                    track.sourceId,
                    track.id,
                    content.trim(),
                    "local",
                    System.currentTimeMillis(),
                ),
            )
        }
    }

    fun fetch(track: Track) {
        if (track.title.isBlank() || track.artist.isBlank()) return
        val key = trackKeyOf(track)
        trackKey = key
        message = "Fetching lyrics…"
        scope.launch {
            lyricsApi.fetch(track.artist, track.title).fold(
                onSuccess = { content ->
                    if (trackKey != key) return@fold
                    save(track, content)
                    message = "Lyrics from Lyrics.ovh."
                },
                onFailure = { error ->
                    if (trackKey == key) message = error.message ?: "Could not fetch lyrics."
                    messenger.quietError("Lyrics fetch failed", error)
                },
            )
        }
    }
}
