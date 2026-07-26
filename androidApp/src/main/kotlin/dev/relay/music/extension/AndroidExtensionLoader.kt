package dev.relay.music.extension

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dalvik.system.DexClassLoader
import dev.relay.music.model.Track
import dev.relay.music.source.api.RelaySourceApi
import dev.relay.music.source.api.RelaySource
import dev.relay.music.source.api.RelaySourceFactory
import dev.relay.music.source.api.RelaySourcePage
import dev.relay.music.source.api.RelaySourceSetting
import dev.relay.music.source.api.RelaySourceTrack
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Loads source classes from a verified extension APK, following Mihon's source-API model. */
class AndroidExtensionLoader(
    private val context: Context,
    private val verifier: AndroidExtensionVerifier = AndroidExtensionVerifier(context),
) {
    suspend fun load(entry: ExtensionCatalogEntry): Result<List<LoadedRelaySource>> = try {
        Result.success(
            withTimeout(LOAD_TIMEOUT_MS) {
                withContext(Dispatchers.IO) { runInterruptible { loadBlocking(entry) } }
            },
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private fun loadBlocking(entry: ExtensionCatalogEntry): List<LoadedRelaySource> {
        require(entry.kind == ExtensionKind.SOURCE) { "Extension is not a source." }
        require(entry.validate() == null) { entry.validate() ?: "Extension catalog entry is invalid." }
        require(entry.isCompatible) { "Requires extension API ${entry.api.minimum}-${entry.api.maximum}; Relay supports $EXTENSION_API_VERSION." }
        verifier.verify(entry)?.let(::error)
        val applicationInfo = applicationInfo(entry.androidPackageName ?: error("Extension is not an Android APK."))
        val metadata = requireNotNull(applicationInfo.metaData) { "Extension source metadata is missing." }
        require(metadata.getInt(RelaySourceApi.METADATA_API_VERSION, 0) == RelaySourceApi.VERSION) {
            "Extension source API is incompatible."
        }
        metadata.getString(RelaySourceApi.METADATA_EXTENSION_ID)?.trim()?.takeIf(String::isNotEmpty)?.let { manifestId ->
            require(manifestId == entry.id) { "Extension manifest ID does not match its catalog entry." }
        }
        val entryClasses = metadata.getString(RelaySourceApi.METADATA_ENTRY_CLASS)
            ?.split(';')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()
        require(entryClasses.isNotEmpty() && entryClasses.size <= MAX_ENTRY_CLASSES) {
            "Extension has no source entry class."
        }

        val optimizedDirectory = File(context.codeCacheDir, "relay-extensions").apply { mkdirs() }
        val classLoader = RelayExtensionClassLoader(applicationInfo.sourceDir, optimizedDirectory.path, context.classLoader)
        return entryClasses.flatMap { className ->
            val resolvedName = if (className.startsWith('.')) "${entry.androidPackageName}$className" else className
            when (val instance = Class.forName(resolvedName, false, classLoader).getDeclaredConstructor().newInstance()) {
                is RelaySource -> listOf(instance)
                is RelaySourceFactory -> {
                    require(instance.getApiVersion() == RelaySourceApi.VERSION) { "Extension source factory API is incompatible." }
                    instance.createSources()
                }
                else -> error("Extension entry does not implement RelaySource or RelaySourceFactory.")
            }
        }.also { sources ->
            require(sources.isNotEmpty() && sources.size <= MAX_SOURCES) { "Extension returned an invalid number of sources." }
            require(sources.all { it.getId().isValidSourceId() && it.getName().isNotBlank() && it.getName().length <= 128 }) {
                "Extension source identity is invalid."
            }
            require(sources.map { it.getId() }.distinct().size == sources.size) { "Extension source IDs are duplicated." }
        }.map { source -> LoadedRelaySource(entry, source) }
    }

    /** Bounded, validated browse listings; an empty list means the source is search-only. */
    suspend fun listings(source: LoadedRelaySource): List<SourceListing> = withTimeout(LISTINGS_TIMEOUT_MS) {
        withContext(Dispatchers.IO) { runInterruptible { source.source.getListings() } }
    }.orEmpty().also { listings ->
        require(listings.size <= MAX_LISTINGS) { "Extension returned too many listings." }
        require(listings.all { it.getId().orEmpty().isValidListingId() && !it.getName().isNullOrBlank() && it.getName().length <= 64 }) {
            "Extension listing is invalid."
        }
        require(listings.map { it.getId() }.distinct().size == listings.size) { "Extension listing IDs are duplicated." }
    }.map { SourceListing(it.getId(), it.getName().trim()) }

    suspend fun search(source: LoadedRelaySource, query: String, page: Int): ExtensionSourceResults {
        require(query.length <= MAX_QUERY_LENGTH) { "Search query is too long." }
        require(page in 1..MAX_PAGE) { "Search page is out of range." }
        return pageResults(source, page) { source.source.search(query.trim(), page) }
    }

    suspend fun browse(source: LoadedRelaySource, listingId: String, page: Int): ExtensionSourceResults {
        require(listingId.isValidListingId()) { "Extension listing ID is invalid." }
        require(page in 1..MAX_PAGE) { "Browse page is out of range." }
        return pageResults(source, page) { source.source.browse(listingId, page) }
    }

    private suspend fun pageResults(
        source: LoadedRelaySource,
        page: Int,
        request: () -> RelaySourcePage,
    ): ExtensionSourceResults {
        val result = withTimeout(SEARCH_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { runInterruptible(block = request) }
        }
        require(result.getTracks().size <= MAX_TRACKS) { "Extension returned too many tracks." }
        return ExtensionSourceResults(
            extensionId = source.entry.id,
            extensionName = source.source.getName(),
            tracks = result.getTracks().mapNotNull { it.toTrack(source.entry.id, source.source.getId()) },
            page = page,
            hasNextPage = result.getHasNextPage(),
        )
    }

    /** Called just before playback or download for tracks whose search result had no stream URL. */
    suspend fun resolveStreamUrl(source: LoadedRelaySource, trackId: String): String? {
        require(trackId.isValidTrackId()) { "Extension track ID is invalid." }
        return withTimeout(STREAM_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { runInterruptible { source.source.resolveStreamUrl(trackId) } }
        }?.trim()?.takeIf(String::isValidMediaUrl)
    }

    suspend fun resolveArtworkUrl(source: LoadedRelaySource, trackId: String): String? {
        require(trackId.isValidTrackId()) { "Extension track ID is invalid." }
        return withTimeout(ARTWORK_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { runInterruptible { source.source.resolveArtworkUrl(trackId) } }
        }?.trim()?.takeIf(String::isValidMediaUrl)
    }

    suspend fun resolveDownloadUrl(source: LoadedRelaySource, trackId: String): String? {
        require(trackId.isValidTrackId()) { "Extension track ID is invalid." }
        return withTimeout(DOWNLOAD_URL_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { runInterruptible { source.source.resolveDownloadUrl(trackId) } }
        }?.trim()?.takeIf(String::isValidMediaUrl)
    }

    /** Allow-listed extra headers Relay attaches to this source's stream/artwork/download requests. */
    suspend fun mediaRequestHeaders(source: LoadedRelaySource): Map<String, String> = withTimeout(LISTINGS_TIMEOUT_MS) {
        withContext(Dispatchers.IO) { runInterruptible { source.source.getMediaRequestHeaders() } }
    }.orEmpty().let(::sanitizeMediaHeaders)

    /** Bounded, validated source preference schema; empty when the source declares none. */
    suspend fun settingDefinitions(source: LoadedRelaySource): List<SourceSettingDefinition> = withTimeout(LISTINGS_TIMEOUT_MS) {
        withContext(Dispatchers.IO) { runInterruptible { source.source.getSettings() } }
    }.orEmpty().also { settings ->
        require(settings.size <= MAX_SETTINGS) { "Extension declared too many settings." }
    }.map { setting ->
        SourceSettingDefinition(
            id = setting.getId().orEmpty(),
            label = setting.getLabel().orEmpty().trim(),
            type = when (setting.getType()) {
                RelaySourceSetting.Type.TEXT -> SourceSettingType.TEXT
                RelaySourceSetting.Type.TOGGLE -> SourceSettingType.TOGGLE
                RelaySourceSetting.Type.CHOICE, null -> SourceSettingType.CHOICE
            },
            defaultValue = setting.getDefaultValue().orEmpty(),
            choices = setting.getChoices().orEmpty().filterNotNull(),
        ).also { definition ->
            definition.validate()?.let(::error)
        }
    }.also { definitions ->
        require(definitions.map { it.id }.distinct().size == definitions.size) { "Extension setting IDs are duplicated." }
    }

    /** Hands sanitized stored preference values to the source. Unknown or invalid values are dropped. */
    suspend fun applySettings(source: LoadedRelaySource, values: Map<String, String>) {
        if (values.isEmpty()) return
        val sanitized = sanitizeSourceSettingValues(settingDefinitions(source), values)
        if (sanitized.isEmpty()) return
        withTimeout(LISTINGS_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { runInterruptible { source.source.applySettings(sanitized) } }
        }
    }

    private fun applicationInfo(packageName: String): ApplicationInfo =
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        }

    private companion object {
        const val MAX_ENTRY_CLASSES = 8
        const val MAX_SOURCES = 32
        const val MAX_TRACKS = 100
        const val MAX_LISTINGS = 24
        const val MAX_SETTINGS = 16
        const val MAX_PAGE = 1_000
        const val MAX_QUERY_LENGTH = 256
        const val LOAD_TIMEOUT_MS = 5_000L
        const val LISTINGS_TIMEOUT_MS = 5_000L
        const val SEARCH_TIMEOUT_MS = 10_000L
        const val STREAM_TIMEOUT_MS = 10_000L
        const val ARTWORK_TIMEOUT_MS = 5_000L
        const val DOWNLOAD_URL_TIMEOUT_MS = 10_000L
    }
}

data class LoadedRelaySource(
    val entry: ExtensionCatalogEntry,
    val source: RelaySource,
)

/** Header names a source may attach to its own media requests. Everything else is dropped. */
private val ALLOWED_MEDIA_HEADERS = setOf("user-agent", "referer", "origin", "cookie", "authorization", "accept")

internal fun sanitizeMediaHeaders(headers: Map<String?, String?>): Map<String, String> = headers.entries
    .asSequence()
    .mapNotNull { (name, value) ->
        val cleanName = name?.trim().orEmpty()
        val cleanValue = value?.trim().orEmpty()
        if (cleanName.lowercase() !in ALLOWED_MEDIA_HEADERS) return@mapNotNull null
        if (cleanValue.isEmpty() || cleanValue.length > 4_096 || cleanValue.any { it == '\r' || it == '\n' }) return@mapNotNull null
        cleanName to cleanValue
    }
    .take(8)
    .toMap()

internal fun RelaySourceTrack.toTrack(extensionId: String, sourceId: String): Track? {
    val id = getId()
    val streamUrl = getStreamUrl()?.trim().orEmpty()
    val title = getTitle()
    val artist = getArtist()
    val album = getAlbum()
    val albumArtist = getAlbumArtist()
    val releaseDate = getReleaseDate()
    val durationMs = getDurationMs()
    val artworkUrl = getArtworkUrl()
    if (!id.isValidTrackId() || title.isNullOrBlank() || artist.isNullOrBlank()) return null
    if (streamUrl.isNotEmpty() && !streamUrl.isValidMediaUrl()) return null
    if (
        title.length > 1_024 || artist.length > 1_024 || (album?.length ?: 0) > 1_024 ||
        (albumArtist?.length ?: 0) > 1_024 || (releaseDate?.length ?: 0) > 32 ||
        durationMs != null && durationMs !in 1..86_400_000L
    ) return null
    return Track(
        id = "$sourceId:$id",
        sourceId = "extension:$extensionId:$sourceId",
        // Empty means unresolved: the host calls resolveStreamUrl just before playback/download.
        playbackUri = streamUrl,
        title = title.trim(),
        artist = artist.trim(),
        album = album?.trim()?.takeIf(String::isNotEmpty),
        albumArtist = albumArtist?.trim()?.takeIf(String::isNotEmpty),
        releaseDate = releaseDate?.trim()?.takeIf(String::isNotEmpty)?.take(32),
        durationMs = durationMs,
        artworkUri = artworkUrl?.trim()?.takeIf(String::isValidMediaUrl),
    )
}

private fun String.isValidSourceId() = matches(Regex("[a-z0-9][a-z0-9._-]{0,127}"))

private fun String.isValidListingId() = matches(Regex("[a-z0-9][a-z0-9._-]{0,63}"))

private fun String?.isValidTrackId() = !isNullOrBlank() && length <= 512

internal fun String.isValidMediaUrl() = startsWith("https://") && length <= 8_192

/** Child-first for extension-owned dependencies, parent-first for Android/Kotlin and Relay's source API. */
private class RelayExtensionClassLoader(
    dexPath: String,
    optimizedDirectory: String,
    private val hostClassLoader: ClassLoader,
) : DexClassLoader(dexPath, optimizedDirectory, null, hostClassLoader) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(this) {
        findLoadedClass(name)?.let { return it }
        if (name.startsWith("java.") || name.startsWith("android.") || name.startsWith("kotlin.") || name.startsWith("dev.relay.music.source.api.")) {
            return hostClassLoader.loadClass(name)
        }
        runCatching { findClass(name) }.getOrElse { hostClassLoader.loadClass(name) }
    }
}
