package dev.relay.music.library

import android.content.Context
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import dev.relay.music.model.Track
import dev.relay.music.model.MetadataOverride
import dev.relay.music.model.ListeningOrigin
import dev.relay.music.model.AlbumChartMetric
import dev.relay.music.model.AlbumChartSpec
import dev.relay.music.model.validate
import dev.relay.music.playback.NowPlayingSnapshot
import dev.relay.music.playback.NowPlayingSnapshotStore
import dev.relay.music.playback.ShuffleGrouping
import dev.relay.music.playback.MissingShuffleValue
import dev.relay.music.playback.ShuffleProfile
import dev.relay.music.playback.MAX_CROSSFADE_MS
import dev.relay.music.settings.RelaySettings
import dev.relay.music.settings.RELAY_SETTINGS_SCHEMA_VERSION
import dev.relay.music.settings.EqualizerPreset
import dev.relay.music.settings.EQUALIZER_BAND_COUNT
import dev.relay.music.settings.EQUALIZER_MAX_LEVEL_MB
import dev.relay.music.settings.EQUALIZER_MIN_LEVEL_MB
import dev.relay.music.settings.normalizedEqualizerBands
import dev.relay.music.extension.RepositoryDescriptor
import dev.relay.music.extension.ApiRange
import dev.relay.music.extension.ExtensionCatalogEntry
import dev.relay.music.extension.ExtensionKind
import dev.relay.music.extension.ExtensionPermission
import dev.relay.music.extension.InstalledExtension
import dev.relay.music.extension.validate
import dev.relay.music.settings.BackupSchedule
import dev.relay.music.wallpaper.WallpaperArtworkFit
import dev.relay.music.wallpaper.ArtworkFilter
import dev.relay.music.wallpaper.WallpaperBackground
import dev.relay.music.wallpaper.WallpaperAnchor
import dev.relay.music.wallpaper.WallpaperCanvas
import dev.relay.music.wallpaper.WallpaperCanvasBackground
import dev.relay.music.wallpaper.WallpaperElement
import dev.relay.music.wallpaper.WallpaperElementLayout
import dev.relay.music.wallpaper.WallpaperEffect
import dev.relay.music.wallpaper.WallpaperFont
import dev.relay.music.wallpaper.WallpaperPreset
import dev.relay.music.wallpaper.WallpaperTitlePosition
import dev.relay.music.wallpaper.WallpaperTitleSize
import dev.relay.music.wallpaper.WallpaperVisualizer
import dev.relay.music.wallpaper.decodeWallpaperPreset
import dev.relay.music.wallpaper.encodeWallpaperPreset
import dev.relay.music.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

@Entity(tableName = "favorite_tracks", primaryKeys = ["sourceId", "trackId"])
data class FavoriteTrackEntity(
    val sourceId: String,
    val trackId: String,
)

@Entity(tableName = "listening_history")
data class ListeningHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val trackId: String,
    val playedAtEpochMs: Long,
    /** Display snapshot so a play stays attributable after its source is gone. */
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val origin: String = ListeningOrigin.LOCAL.name,
    val identityFingerprint: String? = null,
)

/** A singleton profile record. Last.fm session keys stay in encrypted platform storage. */
@Entity(tableName = "local_profile")
data class LocalProfileEntity(
    @PrimaryKey val id: Int = 0,
    val displayName: String = "Relay",
    val createdAtEpochMs: Long,
    val lastFmUsername: String? = null,
)

@Entity(tableName = "album_chart_specs")
data class AlbumChartSpecEntity(
    @PrimaryKey val id: String,
    val range: String,
    val metric: String,
    val limit: Int,
    val createdAtEpochMs: Long,
) {
    fun asSpec() = AlbumChartSpec(
        id = id,
        range = dev.relay.music.model.InsightsRange.entries.firstOrNull { it.name == range } ?: dev.relay.music.model.InsightsRange.MONTH,
        metric = AlbumChartMetric.entries.firstOrNull { it.name == metric } ?: AlbumChartMetric.PLAYS,
        limit = limit.coerceIn(1, 16),
        createdAtEpochMs = createdAtEpochMs.coerceAtLeast(0),
    )
}

@Entity(tableName = "track_flags", primaryKeys = ["sourceId", "trackId"])
data class TrackFlagsEntity(
    val sourceId: String,
    val trackId: String,
    val hidden: Boolean = false,
    val pinned: Boolean = false,
    val archived: Boolean = false,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "playlist_entries", primaryKeys = ["playlistId", "position"])
data class PlaylistEntryEntity(
    val playlistId: Long,
    val position: Int,
    val sourceId: String,
    val trackId: String,
    /** Display snapshot so entries from remote sources survive restarts. Never a stream URL. */
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val artworkUri: String? = null,
)

@Entity(tableName = "queue_entries")
data class QueueEntryEntity(
    @PrimaryKey val position: Int,
    val sourceId: String,
    val trackId: String,
    /** Display snapshot so a restored queue keeps remote tracks. Never a stream URL. */
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val artworkUri: String? = null,
    val albumArtist: String? = null,
    val releaseDate: String? = null,
    val artworkHue: Int? = null,
)

@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,
    val currentIndex: Int,
    val positionMs: Long,
)

internal const val NOW_PLAYING_SNAPSHOT_ID = 0

@Entity(tableName = "now_playing_snapshot")
data class NowPlayingSnapshotEntity(
    @PrimaryKey val id: Int = NOW_PLAYING_SNAPSHOT_ID,
    val trackKey: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val artworkCacheKey: String?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochMs: Long,
) {
    fun asSnapshot() = NowPlayingSnapshot(
        trackKey = trackKey,
        title = title,
        artist = artist,
        album = album,
        artworkCacheKey = artworkCacheKey,
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

internal fun NowPlayingSnapshot.asEntity() = NowPlayingSnapshotEntity(
    id = NOW_PLAYING_SNAPSHOT_ID,
    trackKey = trackKey,
    title = title,
    artist = artist,
    album = album,
    artworkCacheKey = artworkCacheKey,
    isPlaying = isPlaying,
    positionMs = positionMs,
    durationMs = durationMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

internal class RoomNowPlayingSnapshotStore(
    private val dao: UserLibraryDao,
) : NowPlayingSnapshotStore {
    override fun observe(): Flow<NowPlayingSnapshot?> = dao.observeNowPlayingSnapshot().map { it?.asSnapshot() }

    override suspend fun read(): NowPlayingSnapshot? = dao.nowPlayingSnapshot()?.asSnapshot()

    override suspend fun write(snapshot: NowPlayingSnapshot) = dao.saveNowPlayingSnapshot(snapshot.asEntity())
}

@Entity(tableName = "metadata_overrides", primaryKeys = ["sourceId", "trackId"])
data class MetadataOverrideEntity(
    val sourceId: String,
    val trackId: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val artworkUri: String?,
    val musicBrainzId: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val musicBrainzReleaseId: String? = null,
) {
    fun asOverride() = MetadataOverride(
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        artworkUri = artworkUri,
        musicBrainzId = musicBrainzId,
        trackNumber = trackNumber,
        discNumber = discNumber,
        musicBrainzReleaseId = musicBrainzReleaseId,
    )
}

@Entity(tableName = "metadata_lookup", primaryKeys = ["sourceId", "trackId"])
data class MetadataLookupEntity(
    val sourceId: String,
    val trackId: String,
    val sourceRevision: String?,
    val queryFingerprint: String,
    val candidatesJson: String,
    val expiresAtEpochMs: Long,
    val suppressed: Boolean = false,
)

@Entity(tableName = "relay_settings")
data class RelaySettingsEntity(
    @PrimaryKey val id: Int = 0,
    val schemaVersion: Int = RELAY_SETTINGS_SCHEMA_VERSION,
    val resumeQueue: Boolean = true,
    val playbackSpeed: Float = 1f,
    val fadeInMs: Int = 0,
    val fadeOutMs: Int = 0,
    val crossfadeMs: Int = 0,
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: String = EqualizerPreset.FLAT.name,
    val equalizerBandsJson: String = "[0,0,0,0,0]",
    val bassBoostStrength: Int = 0,
    val loudnessNormalization: Boolean = false,
    /** Legacy single-rule settings retained so existing installs migrate into a profile. */
    val shuffleGrouping: String = "NONE",
    val shuffleSeed: Long? = null,
    val shuffleSeedLabel: String? = null,
    val shuffleProfilesJson: String = "[]",
    val activeShuffleProfileId: String = "default",
    val storageRootUri: String? = null,
    val backupSchedule: String = BackupSchedule.OFF.name,
    val backupRetention: Int = 3,
    val autoBackupExpiryDays: Int = 30,
    val downloadStorageLimitGb: Int = 0,
    val downloadAutoCleanup: Boolean = false,
    val trustedRepositoriesJson: String = "[]",
    val installedExtensionsJson: String = "[]",
    val sourceSettingsJson: String = "{}",
    val themePacksJson: String = "[]",
    val activeThemePackId: String? = null,
    val wallpaperArtworkFit: String = WallpaperArtworkFit.FILL.name,
    val wallpaperBackground: String = WallpaperBackground.INK.name,
    val wallpaperShowTrackTitle: Boolean = false,
    val wallpaperTitlePosition: String = WallpaperTitlePosition.BOTTOM_LEFT.name,
    val wallpaperTitleSize: String = WallpaperTitleSize.NORMAL.name,
    val wallpaperEffect: String = WallpaperEffect.NONE.name,
    val wallpaperEffectStrength: Int = 50,
    val wallpaperVisualizer: String = WallpaperVisualizer.OFF.name,
    val wallpaperSoundReactive: Boolean = false,
    /** Full portable composition; null means migrate the legacy wallpaper columns above. */
    val wallpaperPresetJson: String? = null,
    val showLockscreenMetadata: Boolean = false,
) {
    fun asSettings() = RelaySettings(
        schemaVersion = RELAY_SETTINGS_SCHEMA_VERSION,
        resumeQueue = resumeQueue,
        playbackSpeed = playbackSpeed.coerceIn(0.5f, 2f),
        fadeInMs = fadeInMs.coerceIn(0, 4_000),
        fadeOutMs = fadeOutMs.coerceIn(0, 4_000),
        crossfadeMs = crossfadeMs.coerceIn(0, MAX_CROSSFADE_MS),
        equalizerEnabled = equalizerEnabled,
        equalizerPreset = EqualizerPreset.entries.firstOrNull { it.name == equalizerPreset } ?: EqualizerPreset.FLAT,
        equalizerBandLevels = equalizerBands(),
        bassBoostStrength = bassBoostStrength.coerceIn(0, 1_000),
        loudnessNormalization = loudnessNormalization,
        shuffleProfiles = shuffleProfiles().ifEmpty {
            listOf(
                ShuffleProfile(
                    rules = ShuffleGrouping.entries.firstOrNull { it.name == shuffleGrouping }?.let(::listOf).orEmpty(),
                    seed = shuffleSeed,
                    seedLabel = shuffleSeedLabel?.take(16),
                ),
            )
        },
        activeShuffleProfileId = activeShuffleProfileId,
        storageRootUri = storageRootUri,
        backupSchedule = BackupSchedule.entries.firstOrNull { it.name == backupSchedule } ?: BackupSchedule.OFF,
        autoBackupExpiryDays = autoBackupExpiryDays.coerceIn(7, 90),
        downloadStorageLimitGb = downloadStorageLimitGb.coerceIn(0, 100),
        downloadAutoCleanup = downloadAutoCleanup,
        trustedRepositories = trustedRepositories(),
        installedExtensions = installedExtensions(),
        sourceSettings = sourceSettings(),
        themePacks = dev.relay.music.extension.mergedThemePacks(themePacks()),
        activeThemePackId = activeThemePackId,
        showLockscreenMetadata = showLockscreenMetadata,
        wallpaperPreset = wallpaperPresetJson?.let(::decodeWallpaperPreset)?.preset ?: legacyWallpaperPreset(),
    )

    private fun legacyWallpaperPreset(): WallpaperPreset {
        val position = WallpaperTitlePosition.entries.firstOrNull { it.name == wallpaperTitlePosition } ?: WallpaperTitlePosition.BOTTOM_LEFT
        val titleLayout = when (position) {
            WallpaperTitlePosition.TOP_LEFT -> WallpaperElementLayout(0.08f, 0.08f, 0.84f, 0.08f, WallpaperAnchor.TOP_LEFT)
            WallpaperTitlePosition.TOP_CENTER -> WallpaperElementLayout(0.5f, 0.08f, 0.84f, 0.08f, WallpaperAnchor.TOP_CENTER)
            WallpaperTitlePosition.TOP_RIGHT -> WallpaperElementLayout(0.92f, 0.08f, 0.84f, 0.08f, WallpaperAnchor.TOP_RIGHT)
            WallpaperTitlePosition.CENTER -> WallpaperElementLayout(0.5f, 0.5f, 0.84f, 0.08f, WallpaperAnchor.CENTER)
            WallpaperTitlePosition.BOTTOM_LEFT -> WallpaperElementLayout(0.08f, 0.92f, 0.84f, 0.08f, WallpaperAnchor.BOTTOM_LEFT)
            WallpaperTitlePosition.BOTTOM_CENTER -> WallpaperElementLayout(0.5f, 0.92f, 0.84f, 0.08f, WallpaperAnchor.BOTTOM_CENTER)
            WallpaperTitlePosition.BOTTOM_RIGHT -> WallpaperElementLayout(0.92f, 0.92f, 0.84f, 0.08f, WallpaperAnchor.BOTTOM_RIGHT)
        }.copy(font = when (WallpaperTitleSize.entries.firstOrNull { it.name == wallpaperTitleSize }) {
            WallpaperTitleSize.SMALL -> WallpaperFont.BODY
            WallpaperTitleSize.LARGE -> WallpaperFont.DISPLAY
            else -> WallpaperFont.TITLE
        })
        val effect = WallpaperEffect.entries.firstOrNull { it.name == wallpaperEffect } ?: WallpaperEffect.NONE
        return WallpaperPreset(
            canvas = WallpaperCanvas(
                background = WallpaperCanvasBackground.SOLID,
                solidColorArgb = if (wallpaperBackground == WallpaperBackground.PAPER.name) 0xFFF3F0E8 else 0xFF101010,
                artworkFit = WallpaperArtworkFit.entries.firstOrNull { it.name == wallpaperArtworkFit } ?: WallpaperArtworkFit.FILL,
            ),
            elements = listOf(WallpaperElement.Artwork()) + if (wallpaperShowTrackTitle) listOf(WallpaperElement.Title(titleLayout)) else emptyList(),
            filters = if (effect == WallpaperEffect.AMBIENT_BLUR) {
                listOf(ArtworkFilter.Blur(wallpaperEffectStrength.coerceIn(0, 100) / 4f))
            } else emptyList(),
            visualizer = WallpaperVisualizer.entries.firstOrNull { it.name == wallpaperVisualizer } ?: WallpaperVisualizer.OFF,
            soundReactive = wallpaperSoundReactive,
            showMetadata = wallpaperShowTrackTitle,
            warnings = if (effect == WallpaperEffect.REFLECTION) listOf("Legacy reflection was removed because it is outside the safe preset schema.") else emptyList(),
        )
    }

    fun equalizerBands(): List<Int> = normalizedEqualizerBands(
        runCatching {
            org.json.JSONArray(equalizerBandsJson).let { values ->
                List(values.length()) { index -> values.optInt(index) }
            }
        }.getOrDefault(emptyList()),
    )

    fun shuffleProfiles(): List<ShuffleProfile> = runCatching {
        val values = org.json.JSONArray(shuffleProfilesJson)
        buildList {
            for (index in 0 until values.length()) {
                val item = values.getJSONObject(index)
                val id = item.optString("id").trim()
                if (id.isEmpty() || id.length > 64) continue
                val rules = item.optJSONArray("rules")?.let { ruleValues ->
                    buildList {
                        for (ruleIndex in 0 until ruleValues.length()) {
                            ShuffleGrouping.entries.firstOrNull { it.name == ruleValues.optString(ruleIndex) }?.let(::add)
                        }
                    }
                }.orEmpty()
                add(
                    ShuffleProfile(
                        id = id,
                        name = item.optString("name", "PROFILE").trim().take(32).ifBlank { "PROFILE" },
                        rules = rules.distinct().take(6),
                        missingValue = MissingShuffleValue.entries.firstOrNull { it.name == item.optString("missing") }
                            ?: MissingShuffleValue.LAST,
                        seed = if (item.isNull("seed")) null else item.optLong("seed"),
                        seedLabel = item.optString("label").takeIf { it.isNotBlank() }?.take(16),
                        seedSalt = item.optString("salt").trim().take(64),
                    ),
                )
            }
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    fun trustedRepositories(): List<RepositoryDescriptor> = org.json.JSONArray(trustedRepositoriesJson).let { entries ->
        buildList {
            for (index in 0 until entries.length()) {
                val entry = entries.getJSONObject(index)
                val descriptor = RepositoryDescriptor(
                    id = entry.getString("id"),
                    name = entry.getString("name"),
                    indexUrl = entry.getString("url"),
                    signingPublicKey = entry.getString("key"),
                    signingAlgorithm = entry.optString("algorithm", "ECDSA_P256_SHA256"),
                )
                require(descriptor.validate() == null) { "Invalid trusted repository." }
                add(descriptor)
            }
        }.distinctBy { it.id }
    }

    fun sourceSettings(): Map<String, Map<String, String>> = runCatching {
        org.json.JSONObject(sourceSettingsJson).let { extensions ->
            buildMap {
                extensions.keys().forEach { extensionId ->
                    val values = extensions.getJSONObject(extensionId)
                    put(extensionId, buildMap { values.keys().forEach { key -> put(key, values.getString(key)) } })
                }
            }
        }
    }.getOrDefault(emptyMap())

    fun themePacks(): List<dev.relay.music.extension.ThemePack> = runCatching {
        org.json.JSONArray(themePacksJson).let { packs ->
            buildList {
                for (index in 0 until packs.length()) {
                    dev.relay.music.extension.ThemePackReader.parse(packs.getString(index)).getOrNull()?.let(::add)
                }
            }
        }
    }.getOrDefault(emptyList()).distinctBy { it.id }

    fun installedExtensions(): List<InstalledExtension> = org.json.JSONArray(installedExtensionsJson).let { entries ->
        buildList {
            for (index in 0 until entries.length()) {
                val entry = entries.getJSONObject(index)
                val installed = InstalledExtension(
                    repositoryId = entry.getString("repositoryId"),
                    extensionId = entry.getString("extensionId"),
                    version = entry.getString("version"),
                    kind = dev.relay.music.extension.ExtensionKind.valueOf(entry.getString("kind")),
                    enabled = entry.getBoolean("enabled"),
                    disabledReason = entry.optString("disabledReason").ifBlank { null },
                    permissions = entry.getJSONArray("permissions").let { permissions ->
                        buildSet {
                            for (permissionIndex in 0 until permissions.length()) {
                                add(dev.relay.music.extension.ExtensionPermission.valueOf(permissions.getString(permissionIndex)))
                            }
                        }
                    },
                    androidPackageName = entry.optString("androidPackageName").ifBlank { null },
                    androidSigningCertificateSha256 = entry.optString("androidSigningCertificateSha256").ifBlank { null },
                    catalogSnapshot = entry.optJSONObject("catalogSnapshot")?.toCatalogSnapshot(),
                )
                require(installed.validate() == null) { "Invalid installed extension." }
                add(installed)
            }
        }.distinctBy { it.repositoryId to it.extensionId }
    }
}

internal fun Track.asPlaylistEntry(playlistId: Long, position: Int) = PlaylistEntryEntity(
    playlistId = playlistId,
    position = position,
    sourceId = sourceId,
    trackId = id,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    artworkUri = snapshotArtworkUri(),
)

internal fun Track.asHistoryEntry(playedAtEpochMs: Long) = ListeningHistoryEntity(
    sourceId = sourceId,
    trackId = id,
    playedAtEpochMs = playedAtEpochMs,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    origin = ListeningOrigin.LOCAL.name,
)

internal fun Track.asQueueEntry(position: Int) = QueueEntryEntity(
    position = position,
    sourceId = sourceId,
    trackId = id,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    artworkUri = snapshotArtworkUri(),
    albumArtist = albumArtist,
    releaseDate = releaseDate,
    artworkHue = artworkHue?.takeIf { it in 0..359 },
)

/** Remote artwork can carry short-lived credentials, so only host-local cache references persist. */
private fun Track.snapshotArtworkUri(): String? = artworkUri?.takeUnless {
    it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
}

private fun List<RepositoryDescriptor>.toJson(): String = org.json.JSONArray(map {
    org.json.JSONObject().put("id", it.id).put("name", it.name).put("url", it.indexUrl).put("key", it.signingPublicKey).put("algorithm", it.signingAlgorithm)
}).toString()

private fun Map<String, Map<String, String>>.toSourceSettingsJson(): String =
    org.json.JSONObject(mapValues { (_, values) -> org.json.JSONObject(values) } as Map<*, *>).toString()

internal fun List<InstalledExtension>.toInstalledExtensionsJson(): String = org.json.JSONArray(map { extension ->
    org.json.JSONObject()
        .put("repositoryId", extension.repositoryId)
        .put("extensionId", extension.extensionId)
        .put("version", extension.version)
        .put("kind", extension.kind.name)
        .put("enabled", extension.enabled)
        .put("disabledReason", extension.disabledReason)
        .put("permissions", org.json.JSONArray(extension.permissions.map { it.name }))
        .put("androidPackageName", extension.androidPackageName)
        .put("androidSigningCertificateSha256", extension.androidSigningCertificateSha256)
        .put("catalogSnapshot", extension.catalogSnapshot?.toJson())
    }).toString()

private fun ExtensionCatalogEntry.toJson() = org.json.JSONObject()
    .put("id", id)
    .put("name", name)
    .put("version", version)
    .put("kind", kind.name)
    .put("apiMinimum", api.minimum)
    .put("apiMaximum", api.maximum)
    .put("artifactUrl", artifactUrl)
    .put("artifactSha256", artifactSha256)
    .put("artifactSizeBytes", artifactSizeBytes)
    .put("permissions", org.json.JSONArray(permissions.map { it.name }))
    .put("androidPackageName", androidPackageName)
    .put("androidSigningCertificateSha256", androidSigningCertificateSha256)
    .put("supportUrl", supportUrl)

private fun org.json.JSONObject.toCatalogSnapshot() = ExtensionCatalogEntry(
    id = getString("id"),
    name = getString("name"),
    version = getString("version"),
    kind = ExtensionKind.valueOf(getString("kind")),
    api = ApiRange(getInt("apiMinimum"), getInt("apiMaximum")),
    artifactUrl = getString("artifactUrl"),
    artifactSha256 = getString("artifactSha256"),
    artifactSizeBytes = getLong("artifactSizeBytes"),
    permissions = getJSONArray("permissions").let { values ->
        buildSet {
            for (index in 0 until values.length()) add(ExtensionPermission.valueOf(values.getString(index)))
        }
    },
    androidPackageName = optString("androidPackageName").ifBlank { null },
    androidSigningCertificateSha256 = optString("androidSigningCertificateSha256").ifBlank { null },
    supportUrl = optString("supportUrl").ifBlank { null },
)

private fun List<ShuffleProfile>.toShuffleProfilesJson(): String = org.json.JSONArray(map { profile ->
    org.json.JSONObject()
        .put("id", profile.id)
        .put("name", profile.name)
        .put("rules", org.json.JSONArray(profile.rules.map { it.name }))
        .put("missing", profile.missingValue.name)
        .put("seed", profile.seed)
        .put("label", profile.seedLabel)
        .put("salt", profile.seedSalt)
}).toString()

@Entity(tableName = "track_lyrics", primaryKeys = ["sourceId", "trackId"])
data class TrackLyricsEntity(
    val sourceId: String,
    val trackId: String,
    val content: String,
    val source: String,
    val updatedAtEpochMs: Long,
)

/** Host-owned record of an explicit source download. The remote stream URL is never stored. */
@Entity(tableName = "offline_downloads", primaryKeys = ["sourceId", "trackId"])
data class OfflineDownloadEntity(
    val sourceId: String,
    val trackId: String,
    val documentUri: String,
    val mimeType: String,
    val sizeBytes: Long,
    val downloadedAtEpochMs: Long,
    /** Display name for the downloads list; null for rows written before titles were stored. */
    val title: String? = null,
)

/** Sync conflicts are device-local notices. Dismissing one never changes either library value. */
@Entity(tableName = "sync_conflicts")
data class SyncConflictEntity(
    @PrimaryKey val id: String,
    val description: String,
    val section: String = "",
    /** JSON for a reviewed received value. Older notice-only conflicts intentionally have none. */
    val receivedPayload: String? = null,
    val createdAtEpochMs: Long,
)

data class QueueSnapshot(
    val entries: List<QueueEntryEntity>,
    val currentIndex: Int,
    val positionMs: Long,
)

@Dao
interface UserLibraryDao {
    @Query("SELECT * FROM sync_conflicts ORDER BY createdAtEpochMs DESC")
    fun syncConflicts(): Flow<List<SyncConflictEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun saveSyncConflicts(conflicts: List<SyncConflictEntity>)

    @Query("DELETE FROM sync_conflicts WHERE id = :id")
    suspend fun deleteSyncConflict(id: String)

    @Query("SELECT * FROM sync_conflicts WHERE id = :id")
    suspend fun syncConflict(id: String): SyncConflictEntity?

    @Query("SELECT * FROM album_chart_specs ORDER BY createdAtEpochMs DESC")
    fun albumChartSpecs(): Flow<List<AlbumChartSpecEntity>>

    @Query("SELECT * FROM album_chart_specs ORDER BY id")
    suspend fun albumChartSpecSnapshot(): List<AlbumChartSpecEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAlbumChartSpec(spec: AlbumChartSpecEntity)

    @Query("DELETE FROM album_chart_specs WHERE id = :id")
    suspend fun deleteAlbumChartSpec(id: String)

    @Query("SELECT * FROM local_profile WHERE id = 0")
    suspend fun localProfile(): LocalProfileEntity?

    @Query("SELECT * FROM local_profile WHERE id = 0")
    fun observeLocalProfile(): Flow<LocalProfileEntity?>

    @Query("INSERT OR IGNORE INTO local_profile (id, displayName, createdAtEpochMs, lastFmUsername) VALUES (0, :displayName, :createdAtEpochMs, NULL)")
    suspend fun ensureLocalProfile(displayName: String, createdAtEpochMs: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLocalProfile(profile: LocalProfileEntity)

    @Query("SELECT * FROM favorite_tracks")
    fun favorites(): Flow<List<FavoriteTrackEntity>>

    @Query("SELECT * FROM favorite_tracks")
    suspend fun favoriteSnapshot(): List<FavoriteTrackEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tracks WHERE sourceId = :sourceId AND trackId = :trackId)")
    suspend fun isFavorite(sourceId: String, trackId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(favorite: FavoriteTrackEntity)

    @Query("DELETE FROM favorite_tracks WHERE sourceId = :sourceId AND trackId = :trackId")
    suspend fun removeFavorite(sourceId: String, trackId: String)

    @Insert
    suspend fun addHistory(entry: ListeningHistoryEntity)

    @Query("SELECT * FROM listening_history ORDER BY id")
    suspend fun historySnapshot(): List<ListeningHistoryEntity>

    @Query("DELETE FROM listening_history WHERE origin = :origin")
    suspend fun deleteHistoryByOrigin(origin: String)

    // ponytail: the newest 5000 plays cover every insight range; aggregate in SQL if a library
    // ever outgrows that.
    @Query("SELECT * FROM listening_history ORDER BY playedAtEpochMs DESC LIMIT 5000")
    fun recentHistory(): Flow<List<ListeningHistoryEntity>>

    @Query("SELECT * FROM track_flags")
    suspend fun flagsSnapshot(): List<TrackFlagsEntity>

    @Query("SELECT * FROM track_flags")
    fun flags(): Flow<List<TrackFlagsEntity>>

    @Query("SELECT * FROM track_flags WHERE sourceId = :sourceId AND trackId = :trackId")
    suspend fun trackFlags(sourceId: String, trackId: String): TrackFlagsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTrackFlags(flags: TrackFlagsEntity)

    @Insert
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY createdAtEpochMs DESC")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY id")
    suspend fun playlistSnapshot(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_entries ORDER BY playlistId, position")
    suspend fun playlistEntrySnapshot(): List<PlaylistEntryEntity>

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position")
    suspend fun playlistEntries(playlistId: Long): List<PlaylistEntryEntity>

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun clearPlaylistEntries(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaylistEntries(entries: List<PlaylistEntryEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_entries WHERE playlistId = :playlistId AND sourceId = :sourceId AND trackId = :trackId)")
    suspend fun playlistContains(playlistId: Long, sourceId: String, trackId: String): Boolean

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun lastPlaylistPosition(playlistId: Long): Int

    @Insert
    suspend fun addPlaylistEntry(entry: PlaylistEntryEntity)

    @Transaction
    suspend fun appendToPlaylist(playlistId: Long, track: Track): Boolean {
        if (playlistContains(playlistId, track.sourceId, track.id)) return false
        addPlaylistEntry(track.asPlaylistEntry(playlistId, lastPlaylistPosition(playlistId) + 1))
        return true
    }

    @Transaction
    suspend fun replacePlaylistEntries(playlistId: Long, tracks: List<Track>) {
        clearPlaylistEntries(playlistId)
        savePlaylistEntries(tracks.mapIndexed { index, track -> track.asPlaylistEntry(playlistId, index) })
    }

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistRow(playlistId: Long)

    @Transaction
    suspend fun deletePlaylist(playlistId: Long) {
        clearPlaylistEntries(playlistId)
        deletePlaylistRow(playlistId)
    }

    @Query("DELETE FROM queue_entries")
    suspend fun clearQueue()

    @Query("DELETE FROM queue_state")
    suspend fun clearQueueState()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueue(entries: List<QueueEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveQueueState(state: QueueStateEntity)

    @Query("SELECT * FROM queue_entries ORDER BY position")
    suspend fun queueEntries(): List<QueueEntryEntity>

    @Query("SELECT * FROM queue_state WHERE id = 0")
    suspend fun queueState(): QueueStateEntity?

    @Query("SELECT * FROM now_playing_snapshot WHERE id = 0")
    suspend fun nowPlayingSnapshot(): NowPlayingSnapshotEntity?

    @Query("SELECT * FROM now_playing_snapshot WHERE id = 0")
    fun observeNowPlayingSnapshot(): Flow<NowPlayingSnapshotEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNowPlayingSnapshot(snapshot: NowPlayingSnapshotEntity)

    @Query("SELECT * FROM metadata_overrides")
    suspend fun metadataOverrides(): List<MetadataOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMetadataOverride(override: MetadataOverrideEntity)

    @Query("SELECT * FROM metadata_lookup WHERE sourceId = :sourceId AND trackId = :trackId LIMIT 1")
    suspend fun metadataLookup(sourceId: String, trackId: String): MetadataLookupEntity?

    @Query("SELECT * FROM metadata_lookup")
    suspend fun metadataLookups(): List<MetadataLookupEntity>

    @Query("SELECT * FROM metadata_lookup WHERE queryFingerprint = :queryFingerprint AND expiresAtEpochMs > :now ORDER BY expiresAtEpochMs DESC LIMIT 1")
    suspend fun metadataLookupForQuery(queryFingerprint: String, now: Long): MetadataLookupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMetadataLookup(lookup: MetadataLookupEntity)

    @Query("SELECT * FROM relay_settings WHERE id = 0")
    fun settings(): Flow<RelaySettingsEntity?>

    @Query("SELECT * FROM relay_settings WHERE id = 0")
    suspend fun settingsSnapshot(): RelaySettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: RelaySettingsEntity)

    @Query("SELECT * FROM track_lyrics WHERE sourceId = :sourceId AND trackId = :trackId")
    suspend fun lyrics(sourceId: String, trackId: String): TrackLyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLyrics(lyrics: TrackLyricsEntity)

    @Query("SELECT * FROM offline_downloads")
    fun offlineDownloads(): Flow<List<OfflineDownloadEntity>>

    @Query("SELECT * FROM offline_downloads WHERE sourceId = :sourceId AND trackId = :trackId")
    suspend fun offlineDownload(sourceId: String, trackId: String): OfflineDownloadEntity?

    @Query("DELETE FROM offline_downloads WHERE sourceId = :sourceId AND trackId = :trackId")
    suspend fun deleteOfflineDownload(sourceId: String, trackId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOfflineDownload(download: OfflineDownloadEntity)

    @Query("DELETE FROM favorite_tracks")
    suspend fun clearFavorites()

    @Query("DELETE FROM listening_history")
    suspend fun clearHistory()

    @Query("DELETE FROM local_profile")
    suspend fun clearLocalProfile()

    @Query("DELETE FROM album_chart_specs")
    suspend fun clearAlbumChartSpecs()

    @Query("DELETE FROM track_flags")
    suspend fun clearFlags()

    @Query("DELETE FROM playlist_entries")
    suspend fun clearPlaylistEntries()

    @Query("DELETE FROM playlists")
    suspend fun clearPlaylists()

    @Query("DELETE FROM metadata_overrides")
    suspend fun clearMetadataOverrides()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreFavorites(entries: List<FavoriteTrackEntity>)

    @Insert
    suspend fun restoreHistory(entries: List<ListeningHistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreFlags(entries: List<TrackFlagsEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restorePlaylists(entries: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restorePlaylistEntries(entries: List<PlaylistEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreMetadataOverrides(entries: List<MetadataOverrideEntity>)

    /** Existing values win on conflict; the caller receives the conflicts for explicit review. */
    @Transaction
    suspend fun mergeSync(incoming: UserLibraryBackup): SyncImportResult {
        val plan = syncMergePlan(
            SyncSnapshot(
                favorites = favoriteSnapshot(),
                history = historySnapshot(),
                flags = flagsSnapshot(),
                playlists = playlistSnapshot(),
                playlistEntries = playlistEntrySnapshot(),
                metadataOverrides = metadataOverrides(),
                profile = localProfile(),
                charts = albumChartSpecSnapshot(),
            ),
            incoming,
        )
        plan.favoritesToAdd.forEach { addFavorite(it) }
        plan.historyToAdd.forEach { addHistory(it) }
        plan.flagsToAdd.forEach { saveTrackFlags(it) }
        plan.metadataToAdd.forEach { saveMetadataOverride(it) }
        plan.chartsToAdd.forEach { saveAlbumChartSpec(it) }
        plan.profileToAdd?.let { saveLocalProfile(it) }
        plan.playlistsToAdd.forEach { playlist ->
            val playlistId = createPlaylist(PlaylistEntity(name = playlist.name, createdAtEpochMs = playlist.createdAtEpochMs))
            savePlaylistEntries(playlist.entries.mapIndexed { index, entry -> entry.copy(playlistId = playlistId, position = index) })
        }
        saveSyncConflicts(
            plan.conflicts.map { conflict ->
                SyncConflictEntity(
                    id = UUID.nameUUIDFromBytes("${conflict.section}\u0000${conflict.key}\u0000${conflict.receivedPayload}".toByteArray(Charsets.UTF_8)).toString(),
                    description = conflict.description,
                    section = conflict.section,
                    receivedPayload = conflict.receivedPayload,
                    createdAtEpochMs = System.currentTimeMillis(),
                )
            },
        )
        return SyncImportResult(
            favoritesAdded = plan.favoritesToAdd.size,
            historyAdded = plan.historyToAdd.size,
            flagsAdded = plan.flagsToAdd.size,
            metadataAdded = plan.metadataToAdd.size,
            chartsAdded = plan.chartsToAdd.size,
            playlistsAdded = plan.playlistsToAdd.size,
            conflicts = plan.conflicts,
        )
    }

    @Transaction
    suspend fun resolveSyncConflict(id: String, useReceived: Boolean): Boolean {
        val conflict = syncConflict(id) ?: return false
        if (useReceived) {
            val received = conflict.receivedEntityOrNull() ?: return false
            when (received) {
                is TrackFlagsEntity -> saveTrackFlags(received)
                is MetadataOverrideEntity -> saveMetadataOverride(received)
                is AlbumChartSpecEntity -> saveAlbumChartSpec(received)
                is LocalProfileEntity -> saveLocalProfile(received)
                else -> return false
            }
        }
        deleteSyncConflict(id)
        return true
    }

    @Transaction
    suspend fun replaceQueue(tracks: List<Track>, currentIndex: Int, positionMs: Long) {
        clearQueue()
        clearQueueState()
        if (tracks.isEmpty()) return
        insertQueue(tracks.mapIndexed { index, track -> track.asQueueEntry(index) })
        saveQueueState(
            QueueStateEntity(
                currentIndex = currentIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0)),
                positionMs = positionMs.coerceAtLeast(0),
            ),
        )
    }

    /** Applies only the sections the user selected after the archive preflight completed. */
    @Transaction
    suspend fun restoreUserLibrary(plan: RelayRestorePlan) {
        val backup = plan.contents.backup
        val sections = plan.selectedSections
        require(sections.isNotEmpty() && plan.contents.sections.containsAll(sections)) {
            "The restore selection is invalid."
        }
        // This must remain before the first DELETE: corrupt/future content cannot partially mutate Room.
        backup.validate()

        if (RelayBackupSection.LIBRARY in sections) {
            clearFavorites()
            clearFlags()
            restoreFavorites(backup.favorites)
            restoreFlags(backup.flags)
        }
        if (RelayBackupSection.HISTORY in sections) {
            clearHistory()
            restoreHistory(backup.history)
        }
        if (RelayBackupSection.PLAYLISTS in sections) {
            clearPlaylistEntries()
            clearPlaylists()
            restorePlaylists(backup.playlists)
            restorePlaylistEntries(backup.playlistEntries)
        }
        if (RelayBackupSection.QUEUE in sections) {
            clearQueue()
            clearQueueState()
            backup.queueEntries.takeIf { it.isNotEmpty() }?.let { insertQueue(it) }
            backup.queueState?.let { saveQueueState(it) }
        }
        if (RelayBackupSection.METADATA in sections) {
            clearMetadataOverrides()
            restoreMetadataOverrides(backup.metadataOverrides)
        }
        if (RelayBackupSection.PROFILE in sections) {
            clearLocalProfile()
            clearAlbumChartSpecs()
            backup.profile?.let { saveLocalProfile(it) }
            backup.albumChartSpecs.forEach { saveAlbumChartSpec(it) }
        }

        val settingsSections = setOf(
            RelayBackupSection.SETTINGS,
            RelayBackupSection.EXTENSIONS,
            RelayBackupSection.APPEARANCE,
        )
        if (sections.any(settingsSections::contains)) {
            val current = settingsSnapshot() ?: RelaySettingsEntity()
            backup.settings?.let { incoming ->
                saveSettings(mergeRestoredSettings(current, incoming, sections))
            }
        }
    }
}

data class UserLibraryBackup(
    val favorites: List<FavoriteTrackEntity>,
    val history: List<ListeningHistoryEntity>,
    val flags: List<TrackFlagsEntity>,
    val playlists: List<PlaylistEntity>,
    val playlistEntries: List<PlaylistEntryEntity>,
    val queueEntries: List<QueueEntryEntity>,
    val queueState: QueueStateEntity?,
    val metadataOverrides: List<MetadataOverrideEntity>,
    val settings: RelaySettingsEntity?,
    val profile: LocalProfileEntity? = null,
    val albumChartSpecs: List<AlbumChartSpecEntity> = emptyList(),
)

private fun mergeRestoredSettings(
    current: RelaySettingsEntity,
    incoming: RelaySettingsEntity,
    sections: Set<RelayBackupSection>,
): RelaySettingsEntity {
    var merged = current
    if (RelayBackupSection.SETTINGS in sections) {
        merged = incoming.copy(
            id = 0,
            storageRootUri = current.storageRootUri,
            trustedRepositoriesJson = current.trustedRepositoriesJson,
            installedExtensionsJson = current.installedExtensionsJson,
            sourceSettingsJson = current.sourceSettingsJson,
            themePacksJson = current.themePacksJson,
            activeThemePackId = current.activeThemePackId,
            wallpaperArtworkFit = current.wallpaperArtworkFit,
            wallpaperBackground = current.wallpaperBackground,
            wallpaperShowTrackTitle = current.wallpaperShowTrackTitle,
            wallpaperTitlePosition = current.wallpaperTitlePosition,
            wallpaperTitleSize = current.wallpaperTitleSize,
            wallpaperEffect = current.wallpaperEffect,
            wallpaperEffectStrength = current.wallpaperEffectStrength,
            wallpaperVisualizer = current.wallpaperVisualizer,
            wallpaperSoundReactive = current.wallpaperSoundReactive,
            wallpaperPresetJson = current.wallpaperPresetJson,
            showLockscreenMetadata = current.showLockscreenMetadata,
        )
    }
    if (RelayBackupSection.EXTENSIONS in sections) {
        val safeExtensions = incoming.withRestoredExtensionsDisabled()
        merged = merged.copy(
            trustedRepositoriesJson = safeExtensions.trustedRepositoriesJson,
            installedExtensionsJson = safeExtensions.installedExtensionsJson,
            sourceSettingsJson = safeExtensions.sourceSettingsJson,
        )
    }
    if (RelayBackupSection.APPEARANCE in sections) {
        merged = merged.copy(
            themePacksJson = incoming.themePacksJson,
            activeThemePackId = incoming.activeThemePackId,
            wallpaperArtworkFit = incoming.wallpaperArtworkFit,
            wallpaperBackground = incoming.wallpaperBackground,
            wallpaperShowTrackTitle = incoming.wallpaperShowTrackTitle,
            wallpaperTitlePosition = incoming.wallpaperTitlePosition,
            wallpaperTitleSize = incoming.wallpaperTitleSize,
            wallpaperEffect = incoming.wallpaperEffect,
            wallpaperEffectStrength = incoming.wallpaperEffectStrength,
            wallpaperVisualizer = incoming.wallpaperVisualizer,
            wallpaperSoundReactive = incoming.wallpaperSoundReactive,
            wallpaperPresetJson = incoming.wallpaperPresetJson,
            showLockscreenMetadata = incoming.showLockscreenMetadata,
        )
    }
    return merged.copy(
        id = 0,
        schemaVersion = RELAY_SETTINGS_SCHEMA_VERSION,
        storageRootUri = current.storageRootUri,
    )
}

internal fun SyncConflictEntity.receivedEntityOrNull(): Any? {
    val json = receivedPayload?.let { org.json.JSONObject(it) } ?: return null
    return when (section) {
        "FLAGS" -> TrackFlagsEntity(
            sourceId = json.requiredText("sourceId"), trackId = json.requiredText("trackId"),
            hidden = json.getBoolean("hidden"), pinned = json.getBoolean("pinned"), archived = json.getBoolean("archived"),
        )
        "METADATA" -> MetadataOverrideEntity(
            sourceId = json.requiredText("sourceId"), trackId = json.requiredText("trackId"),
            title = json.nullableText("title"), artist = json.nullableText("artist"), album = json.nullableText("album"),
            albumArtist = json.nullableText("albumArtist"), artworkUri = json.nullableText("artworkUri"),
            musicBrainzId = json.nullableText("musicBrainzId"), trackNumber = json.nullablePositiveInt("trackNumber"),
            discNumber = json.nullablePositiveInt("discNumber"),
            musicBrainzReleaseId = json.nullableText("musicBrainzReleaseId"),
        )
        "CHART" -> AlbumChartSpecEntity(
            id = json.requiredText("id"), range = json.requiredText("range"), metric = json.requiredText("metric"),
            limit = json.getInt("limit"), createdAtEpochMs = json.getLong("created"),
        ).also { require(it.asSpec().validate() == null) { "Received chart is invalid." } }
        "PROFILE" -> LocalProfileEntity(
            displayName = json.requiredText("name").also { require(it.length <= 64) { "Received profile is invalid." } },
            createdAtEpochMs = json.getLong("created").also { require(it >= 0) { "Received profile is invalid." } },
            lastFmUsername = json.nullableText("lastfm")?.also { require(it.length <= 128) { "Received profile is invalid." } },
        )
        else -> null
    }
}

private fun org.json.JSONObject.requiredText(name: String): String = getString(name).trim().also {
    require(it.isNotEmpty() && it.length <= 512) { "Received sync value is invalid." }
}

private fun org.json.JSONObject.nullableText(name: String): String? =
    if (isNull(name)) null else optString(name).trim().takeIf { it.isNotEmpty() }?.also { require(it.length <= 512) { "Received sync value is invalid." } }

private fun org.json.JSONObject.nullablePositiveInt(name: String): Int? =
    if (isNull(name)) null else getInt(name).takeIf { it > 0 }

internal fun UserLibraryBackup.validate() {
    fun validId(value: String) = value.isNotBlank() && value.length <= 512 && '\u0000' !in value
    fun validText(value: String?, max: Int = 1_024) = value == null || value.length <= max && '\u0000' !in value

    require(favorites.all { validId(it.sourceId) && validId(it.trackId) }) { "Backup has an invalid favorite." }
    require(favorites.map { it.sourceId to it.trackId }.distinct().size == favorites.size) { "Backup has duplicate favorites." }
    require(flags.all { validId(it.sourceId) && validId(it.trackId) }) { "Backup has invalid track flags." }
    require(flags.map { it.sourceId to it.trackId }.distinct().size == flags.size) { "Backup has duplicate track flags." }
    require(playlists.all { it.id >= 0 && it.name.trim().length in 1..100 && it.createdAtEpochMs >= 0 }) { "Backup has an invalid playlist." }
    require(playlists.map { it.id }.distinct().size == playlists.size) { "Backup has duplicate playlists." }
    val playlistIds = playlists.mapTo(hashSetOf()) { it.id }
    require(playlistEntries.all {
        it.playlistId in playlistIds && it.position >= 0 && validId(it.sourceId) && validId(it.trackId) &&
            validText(it.title) && validText(it.artist) && validText(it.album) && validText(it.artworkUri, 4_096) &&
            (it.durationMs == null || it.durationMs > 0)
    }) { "Backup has an invalid playlist entry." }
    require(playlistEntries.map { it.playlistId to it.position }.distinct().size == playlistEntries.size) { "Backup has duplicate playlist positions." }
    require(queueEntries.all {
        validId(it.sourceId) && validId(it.trackId) && validText(it.title) && validText(it.artist) &&
            validText(it.album) && validText(it.albumArtist) && validText(it.releaseDate, 64) &&
            validText(it.artworkUri, 4_096) && (it.durationMs == null || it.durationMs > 0) &&
            (it.artworkHue == null || it.artworkHue in 0..359)
    }) { "Backup queue contains invalid metadata." }
    require(queueEntries.map { it.position }.distinct().size == queueEntries.size) { "Backup has duplicate queue positions." }
    require(queueEntries.map { it.position }.sorted() == queueEntries.indices.toList()) { "Backup queue is invalid." }
    queueState?.let { state ->
        require(state.currentIndex in queueEntries.indices && state.positionMs >= 0) { "Backup queue state is invalid." }
    }
    require(history.all {
        validId(it.sourceId) && validId(it.trackId) && it.playedAtEpochMs >= 0 &&
            it.origin in ListeningOrigin.entries.map(ListeningOrigin::name) && validText(it.title) &&
            validText(it.artist) && validText(it.album) && validText(it.identityFingerprint, 512) &&
            (it.durationMs == null || it.durationMs > 0)
    }) {
        "Backup history has invalid provenance."
    }
    require(metadataOverrides.map { it.sourceId to it.trackId }.distinct().size == metadataOverrides.size) { "Backup has duplicate metadata overrides." }
    require(metadataOverrides.all {
        validId(it.sourceId) && validId(it.trackId) && validText(it.title) && validText(it.artist) &&
            validText(it.album) && validText(it.albumArtist) && validText(it.artworkUri, 4_096) &&
            validText(it.musicBrainzId, 128) && (it.trackNumber == null || it.trackNumber in 1..999) &&
            (it.discNumber == null || it.discNumber in 1..999) && validText(it.musicBrainzReleaseId, 128)
    }) { "Backup has an invalid metadata override." }
    profile?.let {
        require(it.id == 0 && it.displayName.trim().length in 1..64 && it.createdAtEpochMs >= 0) { "Backup profile is invalid." }
        require(it.lastFmUsername == null || it.lastFmUsername.length <= 128) { "Backup profile is invalid." }
    }
    require(albumChartSpecs.all { it.asSpec().validate() == null }) { "Backup chart specification is invalid." }
    settings?.validateForRestore()
}

private fun RelaySettingsEntity.validateForRestore() {
    require(id == 0 && schemaVersion in 1..RELAY_SETTINGS_SCHEMA_VERSION) { "Backup settings schema is unsupported." }
    require(playbackSpeed.isFinite() && playbackSpeed in 0.5f..2f) { "Backup playback speed is invalid." }
    require(fadeInMs in 0..4_000 && fadeOutMs in 0..4_000 && crossfadeMs in 0..MAX_CROSSFADE_MS) { "Backup fade settings are invalid." }
    require(equalizerPreset in EqualizerPreset.entries.map(EqualizerPreset::name)) { "Backup equalizer preset is invalid." }
    val bands = org.json.JSONArray(equalizerBandsJson)
    require(bands.length() == EQUALIZER_BAND_COUNT && (0 until bands.length()).all {
        bands.getInt(it) in EQUALIZER_MIN_LEVEL_MB..EQUALIZER_MAX_LEVEL_MB
    }) { "Backup equalizer bands are invalid." }
    require(bassBoostStrength in 0..1_000) { "Backup bass boost is invalid." }
    require(backupSchedule in BackupSchedule.entries.map(BackupSchedule::name) && backupRetention in 1..100 && autoBackupExpiryDays in 7..90) {
        "Backup schedule is invalid."
    }
    require(downloadStorageLimitGb in 0..100) { "Backup download storage limit is invalid." }
    require(shuffleGrouping == "NONE" || shuffleGrouping in ShuffleGrouping.entries.map(ShuffleGrouping::name)) { "Backup shuffle setting is invalid." }
    require(activeShuffleProfileId.isNotBlank() && activeShuffleProfileId.length <= 64 && shuffleProfilesJson.length <= MAX_SETTINGS_JSON_CHARS) {
        "Backup shuffle profiles are invalid."
    }
    org.json.JSONArray(shuffleProfilesJson).also { profiles ->
        require(profiles.length() <= 32) { "Backup has too many shuffle profiles." }
        for (index in 0 until profiles.length()) {
            val profile = profiles.getJSONObject(index)
            require(profile.optString("id").trim().length in 1..64 && profile.optString("name", "PROFILE").length <= 32) {
                "Backup shuffle profile is invalid."
            }
            profile.optJSONArray("rules")?.let { rules ->
                require(rules.length() <= 6 && (0 until rules.length()).all { ruleIndex ->
                    ShuffleGrouping.entries.any { it.name == rules.getString(ruleIndex) }
                }) { "Backup shuffle rules are invalid." }
            }
        }
    }
    require(trustedRepositoriesJson.length <= MAX_SETTINGS_JSON_CHARS && trustedRepositories().size <= 32) { "Backup repositories are invalid." }
    require(installedExtensionsJson.length <= MAX_SETTINGS_JSON_CHARS && installedExtensions().size <= 64) { "Backup installed extensions are invalid." }
    validateSourceSettings(sourceSettingsJson)
    require(themePacksJson.length <= MAX_SETTINGS_JSON_CHARS) { "Backup theme packs are too large." }
    org.json.JSONArray(themePacksJson).also { packs ->
        require(packs.length() <= 32) { "Backup has too many theme packs." }
        for (index in 0 until packs.length()) {
            require(packs.getString(index).length <= 64 * 1024) { "Backup theme pack is too large." }
            dev.relay.music.extension.ThemePackReader.parse(packs.getString(index)).getOrThrow()
        }
    }
    require(activeThemePackId == null || activeThemePackId.length <= 128) { "Backup active theme is invalid." }
    require(WallpaperArtworkFit.entries.any { it.name == wallpaperArtworkFit } && WallpaperBackground.entries.any { it.name == wallpaperBackground }) {
        "Backup wallpaper canvas is invalid."
    }
    require(WallpaperTitlePosition.entries.any { it.name == wallpaperTitlePosition } && WallpaperTitleSize.entries.any { it.name == wallpaperTitleSize }) {
        "Backup wallpaper title is invalid."
    }
    require(WallpaperEffect.entries.any { it.name == wallpaperEffect } && wallpaperEffectStrength in 0..100 &&
        WallpaperVisualizer.entries.any { it.name == wallpaperVisualizer }) { "Backup wallpaper effect is invalid." }
    wallpaperPresetJson?.let { raw ->
        require(raw.length <= 64 * 1024) { "Backup wallpaper preset is too large." }
        require(decodeWallpaperPreset(raw).valid) { "Backup wallpaper preset is invalid." }
    }
}

private fun validateSourceSettings(raw: String) {
    require(raw.length <= MAX_SETTINGS_JSON_CHARS) { "Backup source settings are too large." }
    val extensions = org.json.JSONObject(raw)
    val extensionIds = extensions.keys().asSequence().toList()
    require(extensionIds.size <= 64 && extensionIds.all { it.length in 1..128 }) { "Backup source settings are invalid." }
    extensionIds.forEach { extensionId ->
        val values = extensions.getJSONObject(extensionId)
        val keys = values.keys().asSequence().toList()
        require(keys.size <= 64 && keys.all { it.length in 1..128 && values.getString(it).length <= 4_096 }) {
            "Backup source settings are invalid."
        }
    }
}

private const val MAX_SETTINGS_JSON_CHARS = 256 * 1024

@Database(
    entities = [
        FavoriteTrackEntity::class,
        ListeningHistoryEntity::class,
        LocalProfileEntity::class,
        AlbumChartSpecEntity::class,
        TrackFlagsEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        QueueEntryEntity::class,
        QueueStateEntity::class,
        NowPlayingSnapshotEntity::class,
        MetadataOverrideEntity::class,
        MetadataLookupEntity::class,
        RelaySettingsEntity::class,
        TrackLyricsEntity::class,
        OfflineDownloadEntity::class,
        SyncConflictEntity::class,
    ],
    version = 33,
    exportSchema = false,
)
abstract class UserLibraryDatabase : RoomDatabase() {
    abstract fun userLibraryDao(): UserLibraryDao
}

object UserLibraryStore {
    @Volatile
    private var database: UserLibraryDatabase? = null

    fun database(context: Context): UserLibraryDatabase = database ?: synchronized(this) {
        database ?: Room.databaseBuilder(
            context.applicationContext,
            UserLibraryDatabase::class.java,
            "user_library.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33).build().also { database = it }
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS track_flags (sourceId TEXT NOT NULL, trackId TEXT NOT NULL, hidden INTEGER NOT NULL, pinned INTEGER NOT NULL, archived INTEGER NOT NULL, PRIMARY KEY(sourceId, trackId))",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS playlists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS playlist_entries (playlistId INTEGER NOT NULL, position INTEGER NOT NULL, sourceId TEXT NOT NULL, trackId TEXT NOT NULL, PRIMARY KEY(playlistId, position))",
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS now_playing_snapshot (id INTEGER NOT NULL, trackKey TEXT, title TEXT, artist TEXT, album TEXT, artworkCacheKey TEXT, isPlaying INTEGER NOT NULL, positionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(id))",
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS metadata_overrides (sourceId TEXT NOT NULL, trackId TEXT NOT NULL, title TEXT, artist TEXT, album TEXT, albumArtist TEXT, artworkUri TEXT, musicBrainzId TEXT, PRIMARY KEY(sourceId, trackId))",
            )
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS relay_settings (id INTEGER NOT NULL, schemaVersion INTEGER NOT NULL, resumeQueue INTEGER NOT NULL, PRIMARY KEY(id))",
            )
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS track_lyrics (sourceId TEXT NOT NULL, trackId TEXT NOT NULL, content TEXT NOT NULL, source TEXT NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(sourceId, trackId))",
            )
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN storageRootUri TEXT")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN backupSchedule TEXT NOT NULL DEFAULT 'OFF'")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN backupRetention INTEGER NOT NULL DEFAULT 3")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN autoBackupExpiryDays INTEGER NOT NULL DEFAULT 30")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE metadata_overrides ADD COLUMN trackNumber INTEGER")
            connection.execSQL("ALTER TABLE metadata_overrides ADD COLUMN discNumber INTEGER")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS metadata_lookup (sourceId TEXT NOT NULL, trackId TEXT NOT NULL, sourceRevision TEXT, queryFingerprint TEXT NOT NULL, candidatesJson TEXT NOT NULL, expiresAtEpochMs INTEGER NOT NULL, suppressed INTEGER NOT NULL, PRIMARY KEY(sourceId, trackId))",
            )
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN trustedRepositoriesJson TEXT NOT NULL DEFAULT '[]'")
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN installedExtensionsJson TEXT NOT NULL DEFAULT '[]'")
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS offline_downloads (sourceId TEXT NOT NULL, trackId TEXT NOT NULL, documentUri TEXT NOT NULL, mimeType TEXT NOT NULL, sizeBytes INTEGER NOT NULL, downloadedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(sourceId, trackId))",
            )
        }
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN playbackSpeed REAL NOT NULL DEFAULT 1.0")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN fadeInMs INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN fadeOutMs INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN equalizerEnabled INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN equalizerPreset TEXT NOT NULL DEFAULT 'FLAT'")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN equalizerBandsJson TEXT NOT NULL DEFAULT '[0,0,0,0,0]'")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN bassBoostStrength INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN sourceSettingsJson TEXT NOT NULL DEFAULT '{}'")
        }
    }

    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE playlist_entries ADD COLUMN title TEXT")
            connection.execSQL("ALTER TABLE playlist_entries ADD COLUMN artist TEXT")
            connection.execSQL("ALTER TABLE playlist_entries ADD COLUMN album TEXT")
            connection.execSQL("ALTER TABLE playlist_entries ADD COLUMN durationMs INTEGER")
            connection.execSQL("ALTER TABLE playlist_entries ADD COLUMN artworkUri TEXT")
        }
    }

    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN shuffleGrouping TEXT NOT NULL DEFAULT 'NONE'")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN shuffleSeed INTEGER")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN shuffleSeedLabel TEXT")
        }
    }

    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN themePacksJson TEXT NOT NULL DEFAULT '[]'")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN activeThemePackId TEXT")
        }
    }

    private val MIGRATION_18_19 = object : Migration(18, 19) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE offline_downloads ADD COLUMN title TEXT")
        }
    }

    private val MIGRATION_19_20 = object : Migration(19, 20) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE queue_entries ADD COLUMN title TEXT")
            connection.execSQL("ALTER TABLE queue_entries ADD COLUMN artist TEXT")
            connection.execSQL("ALTER TABLE queue_entries ADD COLUMN album TEXT")
            connection.execSQL("ALTER TABLE queue_entries ADD COLUMN durationMs INTEGER")
            connection.execSQL("ALTER TABLE queue_entries ADD COLUMN artworkUri TEXT")
        }
    }

    private val MIGRATION_20_21 = object : Migration(20, 21) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN loudnessNormalization INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_21_22 = object : Migration(21, 22) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE listening_history ADD COLUMN title TEXT")
            connection.execSQL("ALTER TABLE listening_history ADD COLUMN artist TEXT")
            connection.execSQL("ALTER TABLE listening_history ADD COLUMN album TEXT")
            connection.execSQL("ALTER TABLE listening_history ADD COLUMN durationMs INTEGER")
        }
    }

    private val MIGRATION_22_23 = object : Migration(22, 23) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN shuffleProfilesJson TEXT NOT NULL DEFAULT '[]'")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN activeShuffleProfileId TEXT NOT NULL DEFAULT 'default'")
            connection.execSQL("ALTER TABLE queue_entries ADD COLUMN albumArtist TEXT")
            connection.execSQL("ALTER TABLE queue_entries ADD COLUMN releaseDate TEXT")
            connection.execSQL("ALTER TABLE queue_entries ADD COLUMN artworkHue INTEGER")
        }
    }

    private val MIGRATION_23_24 = object : Migration(23, 24) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN crossfadeMs INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_24_25 = object : Migration(24, 25) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE listening_history ADD COLUMN origin TEXT NOT NULL DEFAULT 'LOCAL'")
            connection.execSQL("ALTER TABLE listening_history ADD COLUMN identityFingerprint TEXT")
            connection.execSQL("CREATE TABLE IF NOT EXISTS local_profile (id INTEGER NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, lastFmUsername TEXT)")
        }
    }

    private val MIGRATION_25_26 = object : Migration(25, 26) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("CREATE TABLE IF NOT EXISTS album_chart_specs (id TEXT NOT NULL PRIMARY KEY, range TEXT NOT NULL, metric TEXT NOT NULL, limit INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL)")
        }
    }

    private val MIGRATION_26_27 = object : Migration(26, 27) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("CREATE TABLE IF NOT EXISTS sync_conflicts (id TEXT NOT NULL PRIMARY KEY, description TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL)")
        }
    }

    private val MIGRATION_27_28 = object : Migration(27, 28) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE sync_conflicts ADD COLUMN section TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE sync_conflicts ADD COLUMN receivedPayload TEXT")
        }
    }

    private val MIGRATION_28_29 = object : Migration(28, 29) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN wallpaperArtworkFit TEXT NOT NULL DEFAULT 'FILL'")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN wallpaperBackground TEXT NOT NULL DEFAULT 'INK'")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN wallpaperShowTrackTitle INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_29_30 = object : Migration(29, 30) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN wallpaperTitlePosition TEXT NOT NULL DEFAULT 'BOTTOM_LEFT'")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN wallpaperTitleSize TEXT NOT NULL DEFAULT 'NORMAL'")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN wallpaperEffect TEXT NOT NULL DEFAULT 'NONE'")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN wallpaperEffectStrength INTEGER NOT NULL DEFAULT 50")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN wallpaperVisualizer TEXT NOT NULL DEFAULT 'OFF'")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN wallpaperSoundReactive INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_30_31 = object : Migration(30, 31) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE metadata_overrides ADD COLUMN musicBrainzReleaseId TEXT")
        }
    }

    private val MIGRATION_31_32 = object : Migration(31, 32) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN wallpaperPresetJson TEXT")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN showLockscreenMetadata INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_32_33 = object : Migration(32, 33) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN downloadStorageLimitGb INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE relay_settings ADD COLUMN downloadAutoCleanup INTEGER NOT NULL DEFAULT 0")
        }
    }
}

class RoomSettingsStore(private val dao: UserLibraryDao) : SettingsStore {
    private val _settings = MutableStateFlow(RelaySettings())
    override val settings: StateFlow<RelaySettings> = _settings

    suspend fun load() {
        _settings.value = dao.settings().first()?.asSettings() ?: RelaySettings()
    }

    override suspend fun save(settings: RelaySettings) {
        val wallpaperPresetJson = encodeWallpaperPreset(settings.wallpaperPreset)
        val title = settings.wallpaperPreset.elements.filterIsInstance<WallpaperElement.Title>().firstOrNull()
        val blur = settings.wallpaperPreset.filters.filterIsInstance<ArtworkFilter.Blur>().firstOrNull()
        dao.saveSettings(
            RelaySettingsEntity(
                schemaVersion = RELAY_SETTINGS_SCHEMA_VERSION,
                resumeQueue = settings.resumeQueue,
                playbackSpeed = settings.playbackSpeed.coerceIn(0.5f, 2f),
                fadeInMs = settings.fadeInMs.coerceIn(0, 4_000),
                fadeOutMs = settings.fadeOutMs.coerceIn(0, 4_000),
                crossfadeMs = settings.crossfadeMs.coerceIn(0, MAX_CROSSFADE_MS),
                equalizerEnabled = settings.equalizerEnabled,
                equalizerPreset = settings.equalizerPreset.name,
                equalizerBandsJson = org.json.JSONArray(normalizedEqualizerBands(settings.equalizerBandLevels)).toString(),
                bassBoostStrength = settings.bassBoostStrength.coerceIn(0, 1_000),
                loudnessNormalization = settings.loudnessNormalization,
                shuffleProfilesJson = settings.shuffleProfiles.toShuffleProfilesJson(),
                activeShuffleProfileId = settings.activeShuffleProfileId,
                storageRootUri = settings.storageRootUri,
                backupSchedule = settings.backupSchedule.name,
                autoBackupExpiryDays = settings.autoBackupExpiryDays.coerceIn(7, 90),
                downloadStorageLimitGb = settings.downloadStorageLimitGb.coerceIn(0, 100),
                downloadAutoCleanup = settings.downloadAutoCleanup,
                trustedRepositoriesJson = settings.trustedRepositories.toJson(),
                installedExtensionsJson = settings.installedExtensions.toInstalledExtensionsJson(),
                sourceSettingsJson = settings.sourceSettings.toSourceSettingsJson(),
                themePacksJson = org.json.JSONArray(
                    dev.relay.music.extension.mergedThemePacks(settings.themePacks)
                        .map(dev.relay.music.extension.ThemePackReader::toJson),
                ).toString(),
                activeThemePackId = settings.activeThemePackId,
                showLockscreenMetadata = settings.showLockscreenMetadata,
                wallpaperArtworkFit = settings.wallpaperPreset.canvas.artworkFit.name,
                wallpaperBackground = if (settings.wallpaperPreset.canvas.solidColorArgb == 0xFFF3F0E8L) WallpaperBackground.PAPER.name else WallpaperBackground.INK.name,
                wallpaperShowTrackTitle = title != null && settings.wallpaperPreset.showMetadata,
                wallpaperTitlePosition = title?.layout?.legacyPosition()?.name ?: WallpaperTitlePosition.BOTTOM_LEFT.name,
                wallpaperTitleSize = title?.layout?.font?.legacySize()?.name ?: WallpaperTitleSize.NORMAL.name,
                wallpaperEffect = if (blur != null) WallpaperEffect.AMBIENT_BLUR.name else WallpaperEffect.NONE.name,
                wallpaperEffectStrength = ((blur?.radius ?: 0f) * 4).toInt().coerceIn(0, 100),
                wallpaperVisualizer = settings.wallpaperPreset.visualizer.name,
                wallpaperSoundReactive = settings.wallpaperPreset.soundReactive,
                wallpaperPresetJson = wallpaperPresetJson,
            ),
        )
        _settings.value = settings.copy(themePacks = dev.relay.music.extension.mergedThemePacks(settings.themePacks))
    }
}

private fun WallpaperElementLayout.legacyPosition(): WallpaperTitlePosition = when {
    y < 0.34f && x < 0.34f -> WallpaperTitlePosition.TOP_LEFT
    y < 0.34f && x > 0.66f -> WallpaperTitlePosition.TOP_RIGHT
    y < 0.34f -> WallpaperTitlePosition.TOP_CENTER
    y > 0.66f && x < 0.34f -> WallpaperTitlePosition.BOTTOM_LEFT
    y > 0.66f && x > 0.66f -> WallpaperTitlePosition.BOTTOM_RIGHT
    y > 0.66f -> WallpaperTitlePosition.BOTTOM_CENTER
    else -> WallpaperTitlePosition.CENTER
}

private fun WallpaperFont.legacySize(): WallpaperTitleSize = when (this) {
    WallpaperFont.METADATA, WallpaperFont.BODY -> WallpaperTitleSize.SMALL
    WallpaperFont.TITLE -> WallpaperTitleSize.NORMAL
    WallpaperFont.DISPLAY -> WallpaperTitleSize.LARGE
}
