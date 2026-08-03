package dev.relay.music.library

import android.content.Context
import dev.relay.music.model.Track
import java.security.MessageDigest

/** Small local digest cache used only by explicit music transfer and play-together matching. */
internal class TrackContentDigestStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val preferences = context.applicationContext.getSharedPreferences("relay_track_digests", Context.MODE_PRIVATE)

    fun digest(track: Track): String? {
        if (track.sourceId != "local" || track.playbackUri.isBlank()) return null
        val revision = track.sourceRevision ?: ""
        preferences.getString(track.playbackUri, null)?.split('|', limit = 2)?.let { cached ->
            if (cached.size == 2 && cached[0] == revision) return cached[1]
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        resolver.openInputStream(android.net.Uri.parse(track.playbackUri))?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                size += count
                require(size <= MAX_BYTES) { "Track is too large to use in Relay Play Together." }
                digest.update(buffer, 0, count)
            }
        } ?: return null
        return digest.digest().joinToString("") { "%02x".format(it) }.also { value ->
            preferences.edit().putString(track.playbackUri, "$revision|$value").apply()
        }
    }

    fun find(tracks: List<Track>, contentDigest: String): Track? = tracks.firstOrNull { track ->
        track.sourceId == "local" && runCatching { digest(track) == contentDigest }.getOrDefault(false)
    }

    private companion object {
        const val MAX_BYTES = 2L * 1024 * 1024 * 1024
    }
}
