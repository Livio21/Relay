package dev.relay.music.metadata

import android.content.Context
import dev.relay.music.library.MetadataLookupEntity
import dev.relay.music.library.MetadataOverrideEntity
import dev.relay.music.library.UserLibraryDao
import dev.relay.music.model.MetadataCandidate
import dev.relay.music.model.MetadataOverride
import dev.relay.music.model.Track
import dev.relay.music.model.withMetadataOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Review-only metadata repair: resolves and caches artwork, persists user-confirmed overrides,
 * and serves MusicBrainz candidates through the host-owned lookup cache. UI state stays with
 * the caller; original audio files are never touched.
 */
class MetadataRepairCoordinator(
    private val context: Context,
    private val dao: UserLibraryDao,
    private val musicBrainzApi: MusicBrainzApi,
    private val appleSearchApi: AppleSearchApi,
) {
    class SavedOverride(val updatedTrack: Track, val artworkError: Throwable?)

    sealed interface SearchOutcome {
        data class Candidates(val candidates: List<MetadataCandidate>) : SearchOutcome
        data class Failure(val message: String) : SearchOutcome
    }

    suspend fun saveOverride(track: Track, override: MetadataOverride): SavedOverride {
        val artworkResult = withContext(Dispatchers.IO) {
            val primary = when (val artwork = override.artworkUri) {
                null -> Result.success(null)
                else -> when {
                    artwork.startsWith("https://", ignoreCase = true) -> runCatching { ArtworkCache.fetch(context, artwork) }
                    artwork.startsWith("http://", ignoreCase = true) -> Result.failure(IllegalArgumentException("Artwork must use HTTPS."))
                    else -> Result.success(artwork)
                }
            }
            primary.recoverCatching { error ->
                val fallbackUrl = appleSearchApi.artwork(
                    title = override.title ?: track.title,
                    artist = override.artist ?: track.artist,
                    album = override.album ?: track.album,
                ).getOrNull() ?: throw error
                ArtworkCache.fetch(context, fallbackUrl)
            }
        }
        val artworkUri = artworkResult.getOrNull()
        withContext(Dispatchers.IO) {
            dao.saveMetadataOverride(
                MetadataOverrideEntity(
                    sourceId = track.sourceId,
                    trackId = track.id,
                    title = override.title,
                    artist = override.artist,
                    album = override.album,
                    albumArtist = override.albumArtist,
                    artworkUri = artworkUri,
                    musicBrainzId = override.musicBrainzId,
                    trackNumber = override.trackNumber,
                    discNumber = override.discNumber,
                ),
            )
        }
        return SavedOverride(
            updatedTrack = track.withMetadataOverride(override.copy(artworkUri = artworkUri)),
            artworkError = artworkResult.exceptionOrNull(),
        )
    }

    /** Serves the cached lookup when the source revision and query still match; otherwise MusicBrainz. */
    suspend fun search(track: Track, title: String, artist: String): SearchOutcome {
        val fingerprint = metadataFingerprint(title, artist, track.album)
        val now = System.currentTimeMillis()
        val cached = withContext(Dispatchers.IO) {
            dao.metadataLookup(track.sourceId, track.id)
                ?.takeIf { it.sourceRevision == track.sourceRevision && it.queryFingerprint == fingerprint && it.expiresAtEpochMs > now }
                ?: dao.metadataLookupForQuery(fingerprint, now)
        }?.candidates()
        if (cached != null) return SearchOutcome.Candidates(cached)
        return musicBrainzApi.search(title, artist, track.album).fold(
            onSuccess = { candidates ->
                withContext(Dispatchers.IO) {
                    dao.saveMetadataLookup(
                        MetadataLookupEntity(
                            track.sourceId, track.id, track.sourceRevision, fingerprint,
                            candidates.toCacheJson(), System.currentTimeMillis() + METADATA_CACHE_MS,
                        ),
                    )
                }
                SearchOutcome.Candidates(candidates)
            },
            onFailure = { error -> SearchOutcome.Failure(error.message ?: "Could not search MusicBrainz.") },
        )
    }

    /** Marks the track's review as suppressed until its source revision changes. */
    suspend fun suppressReview(track: Track) = withContext(Dispatchers.IO) {
        val existing = dao.metadataLookup(track.sourceId, track.id)
        dao.saveMetadataLookup(
            (existing ?: MetadataLookupEntity(track.sourceId, track.id, track.sourceRevision, "", "[]", Long.MAX_VALUE))
                .copy(sourceRevision = track.sourceRevision, suppressed = true),
        )
    }

    private companion object {
        const val METADATA_CACHE_MS = 7 * 24 * 60 * 60 * 1000L
    }
}

private fun metadataFingerprint(title: String, artist: String, album: String?): String =
    "v3\u0000${title.trim()}\u0000${artist.trim()}\u0000${album.orEmpty().trim()}".lowercase()

private fun MetadataLookupEntity.candidates(): List<MetadataCandidate>? = runCatching {
    JSONArray(candidatesJson).let { array ->
        List(array.length()) { index ->
            array.getJSONObject(index).let { item ->
                MetadataCandidate(
                    title = item.getString("title"),
                    artist = item.getString("artist"),
                    album = item.optString("album").takeIf { it.isNotBlank() },
                    albumArtist = item.optString("albumArtist").takeIf { it.isNotBlank() },
                    recordingId = item.getString("recordingId"),
                    releaseId = item.optString("releaseId").takeIf { it.isNotBlank() },
                    artworkUri = item.optString("artworkUri").takeIf { it.isNotBlank() },
                    trackNumber = item.optInt("trackNumber").takeIf { it > 0 },
                    discNumber = item.optInt("discNumber").takeIf { it > 0 },
                )
            }
        }
    }
}.getOrNull()

private fun List<MetadataCandidate>.toCacheJson(): String = JSONArray(map { candidate ->
    JSONObject()
        .put("title", candidate.title)
        .put("artist", candidate.artist)
        .put("album", candidate.album)
        .put("albumArtist", candidate.albumArtist)
        .put("recordingId", candidate.recordingId)
        .put("releaseId", candidate.releaseId)
        .put("artworkUri", candidate.artworkUri)
        .put("trackNumber", candidate.trackNumber)
        .put("discNumber", candidate.discNumber)
}).toString()
