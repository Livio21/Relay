package dev.relay.music.extension

import dev.relay.music.model.Track
import kotlinx.coroutines.CancellationException

/**
 * Runs search/browse across installed source extensions and resolves selected tracks just in
 * time for playback or download. Owns the track → source mapping; persistence and UI state stay
 * with the host activity, which reports source failures through [onSourceFailure].
 */
class ExtensionSourceCoordinator(
    private val loader: AndroidExtensionLoader,
    private val storedSettings: (extensionId: String) -> Map<String, String>,
    private val onSourceFailure: suspend (InstalledExtension, Throwable) -> Unit,
) {
    private val origins = mutableMapOf<String, LoadedRelaySource>()

    suspend fun query(
        entries: List<Pair<InstalledExtension, ExtensionCatalogEntry>>,
        request: SourceBrowseRequest,
    ): List<ExtensionSourceResults> {
        if (!request.appendsResults) origins.clear()
        val results = mutableListOf<ExtensionSourceResults>()
        val sourceQuery = request.field.toSourceQuery(request.query)
        entries.forEach { (installed, entry) ->
            val sources = loader.load(entry).getOrElse { error ->
                if (error is CancellationException) throw error
                onSourceFailure(installed, error)
                emptyList()
            }.onEach { source ->
                runCatching { loader.applySettings(source, storedSettings(entry.id)) }
            }
            var failure: Throwable? = null
            for (source in sources) {
                try {
                    val listingId = request.listingId
                    val result = if (listingId != null) {
                        loader.browse(source, listingId, request.page)
                    } else {
                        loader.search(source, sourceQuery, request.page)
                    }
                    val listings = if (request.page == 1) loader.listings(source) else emptyList()
                    result.tracks.forEach { track -> origins[track.key] = source }
                    if (result.tracks.isNotEmpty() || listings.isNotEmpty()) {
                        results += result.copy(listings = listings)
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    failure = error
                    break
                }
            }
            failure?.let { onSourceFailure(installed, it) }
        }
        return results
    }

    fun isExtensionTrack(track: Track): Boolean = track.sourceId.startsWith("extension:")

    /** Loads the extension and returns the combined preference schema of its sources. */
    suspend fun settingDefinitions(entry: ExtensionCatalogEntry): Result<List<SourceSettingDefinition>> = runCatching {
        loader.load(entry).getOrThrow().flatMap { source -> loader.settingDefinitions(source) }.distinctBy { it.id }
    }

    /**
     * The loaded source that produced this track. Falls back to reloading the owning extension
     * when the origin is not in memory, which happens for anything persisted across a restart
     * (playlist entries, restored queues). `sourceId` is `extension:<extensionId>:<inSourceId>`.
     */
    private suspend fun sourceFor(
        track: Track,
        entries: List<Pair<InstalledExtension, ExtensionCatalogEntry>>,
    ): LoadedRelaySource {
        origins[track.key]?.let { return it }
        val parts = track.sourceId.split(':', limit = 3)
        require(parts.size == 3 && parts[0] == "extension") { "This track's source is not an extension." }
        val (_, extensionId, inSourceId) = parts
        val entry = entries.firstOrNull { it.second.id == extensionId }?.second
            ?: error("Install and enable the $extensionId extension to play this track.")
        val source = loader.load(entry).getOrThrow()
            .onEach { loaded -> runCatching { loader.applySettings(loaded, storedSettings(entry.id)) } }
            .firstOrNull { it.source.getId() == inSourceId }
            ?: error("The $extensionId extension no longer offers this source.")
        origins[track.key] = source
        return source
    }

    /**
     * Resolves the stream URL (when the search result carried none), missing artwork, and the
     * source's media headers. Fails with a user-readable message when the source is gone.
     */
    suspend fun preparePlayback(
        track: Track,
        entries: List<Pair<InstalledExtension, ExtensionCatalogEntry>> = emptyList(),
    ): Result<Track> = runCatching {
        if (track.playbackUri.isNotBlank() && origins[track.key] == null) return@runCatching track
        val source = sourceFor(track, entries)
        val streamUrl = track.playbackUri.ifBlank {
            loader.resolveStreamUrl(source, track.sourceTrackId(source))
                ?: error("The source could not resolve a stream for this track.")
        }
        registerMediaHeaders(source, streamUrl)
        val artworkUrl = track.artworkUri
            ?: runCatching { loader.resolveArtworkUrl(source, track.sourceTrackId(source)) }.getOrNull()
        track.copy(playbackUri = streamUrl, artworkUri = artworkUrl)
    }

    class ResolvedDownload(val url: String, val headers: Map<String, String>)

    suspend fun prepareDownload(
        track: Track,
        entries: List<Pair<InstalledExtension, ExtensionCatalogEntry>> = emptyList(),
    ): Result<ResolvedDownload> = runCatching {
        val source = sourceFor(track, entries)
        val trackId = track.sourceTrackId(source)
        val url = runCatching { loader.resolveDownloadUrl(source, trackId) }.getOrNull()
            ?: loader.resolveStreamUrl(source, trackId)
            ?: track.playbackUri.takeUnless { it.isBlank() || it.startsWith("$EXTENSION_STREAM_SCHEME://") }
            ?: error("The source could not resolve a download for this track.")
        ResolvedDownload(url, registerMediaHeaders(source, url))
    }

    private suspend fun registerMediaHeaders(source: LoadedRelaySource, url: String): Map<String, String> {
        val headers = runCatching { loader.mediaRequestHeaders(source) }.getOrDefault(emptyMap())
        ExtensionMediaHeaders.register(url, headers)
        return headers
    }

    private val Track.key: String
        get() = "$sourceId:$id"

    /** Track IDs are prefixed with the in-extension source ID during mapping; sources see their own ID. */
    private fun Track.sourceTrackId(source: LoadedRelaySource): String =
        id.removePrefix("${source.source.getId()}:")
}
