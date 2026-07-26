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
import dev.relay.music.playback.NowPlayingSnapshot
import dev.relay.music.playback.ShuffleGrouping
import dev.relay.music.playback.MissingShuffleValue
import dev.relay.music.playback.ShuffleProfile
import dev.relay.music.settings.RelaySettings
import dev.relay.music.settings.EqualizerPreset
import dev.relay.music.settings.normalizedEqualizerBands
import dev.relay.music.extension.RepositoryDescriptor
import dev.relay.music.extension.InstalledExtension
import dev.relay.music.extension.validate
import dev.relay.music.settings.BackupSchedule
import dev.relay.music.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

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
)

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

@Entity(tableName = "now_playing_snapshot")
data class NowPlayingSnapshotEntity(
    @PrimaryKey val id: Int = 0,
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
) {
    fun asOverride() = MetadataOverride(
        title, artist, album, albumArtist, artworkUri, musicBrainzId, trackNumber, discNumber,
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
    val schemaVersion: Int = 8,
    val resumeQueue: Boolean = true,
    val playbackSpeed: Float = 1f,
    val fadeInMs: Int = 0,
    val fadeOutMs: Int = 0,
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
    val trustedRepositoriesJson: String = "[]",
    val installedExtensionsJson: String = "[]",
    val sourceSettingsJson: String = "{}",
    val themePacksJson: String = "[]",
    val activeThemePackId: String? = null,
) {
    fun asSettings() = RelaySettings(
        schemaVersion = schemaVersion,
        resumeQueue = resumeQueue,
        playbackSpeed = playbackSpeed.coerceIn(0.5f, 2f),
        fadeInMs = fadeInMs.coerceIn(0, 4_000),
        fadeOutMs = fadeOutMs.coerceIn(0, 4_000),
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
        trustedRepositories = trustedRepositories(),
        installedExtensions = installedExtensions(),
        sourceSettings = sourceSettings(),
        themePacks = dev.relay.music.extension.mergedThemePacks(themePacks()),
        activeThemePackId = activeThemePackId,
    )

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

/** Insecure artwork is dropped rather than persisted for a later unencrypted fetch. */
private fun Track.snapshotArtworkUri(): String? = artworkUri?.takeIf { !it.startsWith("http://") }

private fun List<RepositoryDescriptor>.toJson(): String = org.json.JSONArray(map {
    org.json.JSONObject().put("id", it.id).put("name", it.name).put("url", it.indexUrl).put("key", it.signingPublicKey).put("algorithm", it.signingAlgorithm)
}).toString()

private fun Map<String, Map<String, String>>.toSourceSettingsJson(): String =
    org.json.JSONObject(mapValues { (_, values) -> org.json.JSONObject(values) } as Map<*, *>).toString()

private fun List<InstalledExtension>.toInstalledExtensionsJson(): String = org.json.JSONArray(map { extension ->
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
    }).toString()

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

data class QueueSnapshot(
    val entries: List<QueueEntryEntity>,
    val currentIndex: Int,
    val positionMs: Long,
)

@Dao
interface UserLibraryDao {
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

    @Transaction
    suspend fun replaceQueue(tracks: List<Track>, currentIndex: Int, positionMs: Long) {
        clearQueue()
        insertQueue(tracks.mapIndexed { index, track -> track.asQueueEntry(index) })
        saveQueueState(
            QueueStateEntity(
                currentIndex = currentIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0)),
                positionMs = positionMs.coerceAtLeast(0),
            ),
        )
    }

    @Transaction
    suspend fun replaceUserLibrary(backup: UserLibraryBackup) {
        clearFavorites()
        clearHistory()
        clearFlags()
        clearPlaylistEntries()
        clearPlaylists()
        clearMetadataOverrides()
        clearQueue()
        restoreFavorites(backup.favorites)
        restoreHistory(backup.history)
        restoreFlags(backup.flags)
        restorePlaylists(backup.playlists)
        restorePlaylistEntries(backup.playlistEntries)
        restoreMetadataOverrides(backup.metadataOverrides)
        backup.queueEntries.takeIf { it.isNotEmpty() }?.let { insertQueue(it) }
        backup.queueState?.let { saveQueueState(it) }
        backup.settings?.let { settings ->
            saveSettings(settings.copy(storageRootUri = settingsSnapshot()?.storageRootUri))
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
)

internal fun UserLibraryBackup.validate() {
    require(favorites.map { it.sourceId to it.trackId }.distinct().size == favorites.size) { "Backup has duplicate favorites." }
    require(flags.map { it.sourceId to it.trackId }.distinct().size == flags.size) { "Backup has duplicate track flags." }
    require(playlists.map { it.id }.distinct().size == playlists.size) { "Backup has duplicate playlists." }
    require(playlistEntries.all { it.playlistId in playlists.map { playlist -> playlist.id } }) { "Backup has an entry for a missing playlist." }
    require(playlistEntries.map { it.playlistId to it.position }.distinct().size == playlistEntries.size) { "Backup has duplicate playlist positions." }
    require(queueEntries.map { it.position }.distinct().size == queueEntries.size) { "Backup has duplicate queue positions." }
    require(queueEntries.map { it.position }.sorted() == queueEntries.indices.toList()) { "Backup queue is invalid." }
    queueState?.let { state ->
        require(state.currentIndex in queueEntries.indices && state.positionMs >= 0) { "Backup queue state is invalid." }
    }
}

@Database(
    entities = [
        FavoriteTrackEntity::class,
        ListeningHistoryEntity::class,
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
    ],
    version = 23,
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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23).build().also { database = it }
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
}

class RoomSettingsStore(private val dao: UserLibraryDao) : SettingsStore {
    private val _settings = MutableStateFlow(RelaySettings())
    override val settings: StateFlow<RelaySettings> = _settings

    suspend fun load() {
        _settings.value = dao.settings().first()?.asSettings() ?: RelaySettings()
    }

    override suspend fun save(settings: RelaySettings) {
        dao.saveSettings(
            RelaySettingsEntity(
                schemaVersion = 8,
                resumeQueue = settings.resumeQueue,
                playbackSpeed = settings.playbackSpeed.coerceIn(0.5f, 2f),
                fadeInMs = settings.fadeInMs.coerceIn(0, 4_000),
                fadeOutMs = settings.fadeOutMs.coerceIn(0, 4_000),
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
                trustedRepositoriesJson = settings.trustedRepositories.toJson(),
                installedExtensionsJson = settings.installedExtensions.toInstalledExtensionsJson(),
                sourceSettingsJson = settings.sourceSettings.toSourceSettingsJson(),
                themePacksJson = org.json.JSONArray(
                    dev.relay.music.extension.mergedThemePacks(settings.themePacks)
                        .map(dev.relay.music.extension.ThemePackReader::toJson),
                ).toString(),
                activeThemePackId = settings.activeThemePackId,
            ),
        )
        _settings.value = settings.copy(themePacks = dev.relay.music.extension.mergedThemePacks(settings.themePacks))
    }
}
