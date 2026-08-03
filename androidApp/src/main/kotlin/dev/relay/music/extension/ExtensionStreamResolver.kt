package dev.relay.music.extension

import android.content.Context
import dev.relay.music.library.UserLibraryStore
import kotlinx.coroutines.flow.first

/**
 * Turns a `relay-extension://` placeholder into a live stream URL plus the source's media
 * headers, immediately before playback. The playback service owns no activity state, so this
 * reads installed extensions from the database and reuses the catalog row verified at install.
 * Resolved URLs are short-lived and never persisted.
 */
object ExtensionStreamResolver {
    class Resolved(val url: String, val headers: Map<String, String>)

    private const val MAX_CACHED = 32
    private val cache = LinkedHashMap<String, Resolved>()

    suspend fun resolve(context: Context, placeholderUri: String): Resolved {
        cached(placeholderUri)?.let { return it }
        val ref = parseExtensionStreamUri(placeholderUri) ?: error("Track reference is invalid.")
        val dao = UserLibraryStore.database(context).userLibraryDao()
        // A downloaded copy always wins, so an offline track never re-streams.
        dao.offlineDownload(ref.trackSourceId, ref.hostTrackId)?.let { download ->
            return Resolved(download.documentUri, emptyMap())
        }
        val settings = dao.settings().first()?.asSettings()
            ?: error("Relay settings are unavailable.")
        val installed = settings.installedExtensions.firstOrNull {
            it.extensionId == ref.extensionId && it.enabled && it.kind == ExtensionKind.SOURCE
        } ?: error("Install and enable the ${ref.extensionId} extension to play this track.")
        val legacyCatalog = if (installed.catalogSnapshot == null) {
            settings.trustedRepositories.firstOrNull { it.id == installed.repositoryId }
                ?.let { RepositoryCatalogClient(context).cachedCatalog(it) }
        } else {
            null
        }
        val entry = installed.resolvedCatalogEntry(legacyCatalog?.extensions.orEmpty())
            ?: error("Trusted install details for ${ref.extensionId} are unavailable. Refresh its repository or reinstall the extension.")

        val loader = AndroidExtensionLoader(context)
        val source = loader.load(entry).getOrThrow()
            .onEach { loaded ->
                runCatching { loader.applySettings(loaded, settings.sourceSettings[entry.id].orEmpty()) }
            }
            .firstOrNull { it.source.getId() == ref.sourceId }
            ?: error("The ${ref.extensionId} extension no longer offers this source.")

        val url = loader.resolveStreamUrl(source, ref.trackId)
            ?: error("The source could not resolve a stream for this track.")
        val headers = runCatching { loader.mediaRequestHeaders(source) }.getOrDefault(emptyMap())
        ExtensionMediaHeaders.register(url, headers)
        return Resolved(url, headers).also { store(placeholderUri, it) }
    }

    @Synchronized
    private fun cached(placeholderUri: String): Resolved? = cache[placeholderUri]

    @Synchronized
    private fun store(placeholderUri: String, resolved: Resolved) {
        cache.remove(placeholderUri)
        cache[placeholderUri] = resolved
        while (cache.size > MAX_CACHED) cache.remove(cache.keys.first())
    }

    /** Dropped when an extension is disabled, updated, or uninstalled so stale URLs are not reused. */
    @Synchronized
    fun clearCache() = cache.clear()
}
