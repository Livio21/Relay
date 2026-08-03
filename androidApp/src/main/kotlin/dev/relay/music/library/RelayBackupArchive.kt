package dev.relay.music.library

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import dev.relay.music.extension.disabled
import dev.relay.music.model.ListeningOrigin
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

enum class RelayBackupSection(val wireName: String, val displayName: String) {
    LIBRARY("library", "Favorites and library flags"),
    HISTORY("history", "Listening history"),
    PLAYLISTS("playlists", "Playlists"),
    QUEUE("queue", "Playback queue"),
    METADATA("metadata", "Metadata corrections"),
    PROFILE("profile", "Profile and charts"),
    SETTINGS("settings", "Playback and backup settings"),
    EXTENSIONS("extensions", "Extension repositories and settings"),
    APPEARANCE("appearance", "Themes and wallpaper settings"),
    ;

    companion object {
        val all: Set<RelayBackupSection> = entries.toSet()

        fun fromWireName(value: String): RelayBackupSection? = entries.firstOrNull { it.wireName == value }
    }
}

data class RelayBackupContents(
    val backup: UserLibraryBackup,
    val sections: Set<RelayBackupSection>,
    val schemaVersion: Int,
) {
    fun restorePlan(
        selectedSections: Set<RelayBackupSection> = sections,
        currentSettings: RelaySettingsEntity? = null,
        installedAndroidPackages: Set<String> = emptySet(),
    ): RelayRestorePlan {
        require(selectedSections.isNotEmpty()) { "Select at least one backup section." }
        require(sections.containsAll(selectedSections)) { "The backup does not contain every selected section." }
        backup.validate()

        val archivedExtensions = if (RelayBackupSection.EXTENSIONS in selectedSections) {
            backup.settings?.installedExtensions().orEmpty()
        } else {
            emptyList()
        }
        val knownRepositoryIds = buildSet {
            currentSettings?.trustedRepositories()?.mapTo(this) { it.id }
            backup.settings?.takeIf { RelayBackupSection.EXTENSIONS in selectedSections }
                ?.trustedRepositories()?.mapTo(this) { it.id }
        }
        val missingRepositories = archivedExtensions.map { it.repositoryId }
            .filterNot(knownRepositoryIds::contains)
            .distinct()
            .sorted()
        val missingPlugins = archivedExtensions.mapNotNull { installed ->
            installed.androidPackageName
                ?.takeUnless(installedAndroidPackages::contains)
                ?.let { installed.extensionId }
        }.distinct().sorted()
        val trackers = backup.profile
            ?.takeIf { RelayBackupSection.PROFILE in selectedSections && !it.lastFmUsername.isNullOrBlank() }
            ?.let { listOf("Last.fm (${it.lastFmUsername})") }
            .orEmpty()
        val warnings = buildList {
            if (RelayBackupSection.EXTENSIONS in selectedSections && archivedExtensions.isNotEmpty()) {
                add("Restored extensions stay disabled until you review and enable them.")
                add("Repository signing identities will be restored only after you confirm this plan.")
            }
            if (trackers.isNotEmpty()) add("Tracker sessions are device-only and must be connected again.")
            if (RelayBackupSection.SETTINGS in selectedSections) add("This device's Relay storage folder is kept.")
        }
        return RelayRestorePlan(
            contents = this,
            selectedSections = selectedSections,
            missingRepositories = missingRepositories,
            missingPlugins = missingPlugins,
            trackersRequiringReconnect = trackers,
            warnings = warnings,
        )
    }
}

data class RelayRestorePlan(
    val contents: RelayBackupContents,
    val selectedSections: Set<RelayBackupSection>,
    val missingRepositories: List<String>,
    val missingPlugins: List<String>,
    val trackersRequiringReconnect: List<String>,
    val warnings: List<String>,
)

object RelayBackupArchive {
    private const val MANIFEST = "manifest.json"
    private const val LIBRARY = "library.json"
    private const val MAX_ENTRY_BYTES = 2 * 1024 * 1024

    suspend fun write(
        output: OutputStream,
        dao: UserLibraryDao,
        sections: Set<RelayBackupSection> = RelayBackupSection.all,
    ) = writeArchive(output, dao.snapshot(), BACKUP_KIND, sections)

    internal fun write(
        output: OutputStream,
        backup: UserLibraryBackup,
        sections: Set<RelayBackupSection> = RelayBackupSection.all,
    ) = writeArchive(output, backup, BACKUP_KIND, sections)

    /** Sync intentionally omits queue and settings: both are device-local until explicitly enabled later. */
    suspend fun writeSync(output: OutputStream, dao: UserLibraryDao) = writeArchive(
        output,
        dao.snapshot().copy(queueEntries = emptyList(), queueState = null, settings = null),
        SYNC_KIND,
        RelayBackupSection.all - setOf(
            RelayBackupSection.QUEUE,
            RelayBackupSection.SETTINGS,
            RelayBackupSection.EXTENSIONS,
            RelayBackupSection.APPEARANCE,
        ),
    )

    private suspend fun UserLibraryDao.snapshot() = UserLibraryBackup(
            favorites = favoriteSnapshot(),
            history = historySnapshot(),
            flags = flagsSnapshot(),
            playlists = playlistSnapshot(),
            playlistEntries = playlistEntrySnapshot(),
            queueEntries = queueEntries(),
            queueState = queueState(),
            metadataOverrides = metadataOverrides(),
            settings = settings().first(),
            profile = localProfile(),
            albumChartSpecs = albumChartSpecSnapshot(),
        )

    private fun writeArchive(
        output: OutputStream,
        backup: UserLibraryBackup,
        archiveKind: String,
        sections: Set<RelayBackupSection>,
    ) {
        require(sections.isNotEmpty()) { "Select at least one backup section." }
        require(RelayBackupSection.all.containsAll(sections)) { "Unsupported backup section." }
        val selectedBackup = backup.forSections(sections)
        selectedBackup.validate()
        val library = selectedBackup.toJson().toString().toByteArray(Charsets.UTF_8)
        val manifest = JSONObject()
            .put("schemaVersion", CURRENT_SCHEMA_VERSION)
            .put("kind", archiveKind)
            .put("sections", JSONObject().put(LIBRARY, sha256(library)))
            .put("backupSections", JSONArray(sections.sortedBy { it.ordinal }.map { it.wireName }))
            .toString().toByteArray(Charsets.UTF_8)
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST)); zip.write(manifest); zip.closeEntry()
            zip.putNextEntry(ZipEntry(LIBRARY)); zip.write(library); zip.closeEntry()
        }
    }

    fun inspect(input: InputStream): RelayBackupContents = readContents(input, BACKUP_KIND, allowLegacyBackup = true)

    fun read(input: InputStream): UserLibraryBackup = inspect(input).backup

    fun readSync(input: InputStream): UserLibraryBackup = readContents(input, SYNC_KIND, allowLegacyBackup = false).backup

    private fun readContents(input: InputStream, expectedKind: String, allowLegacyBackup: Boolean): RelayBackupContents {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(entry.name == MANIFEST || entry.name == LIBRARY) { "Unsupported backup section." }
                require(entry.size <= MAX_ENTRY_BYTES || entry.size == -1L) { "Backup section is too large." }
                require(entries.put(entry.name, zip.readBounded()) == null) { "Duplicate backup section." }
                zip.closeEntry()
            }
        }
        val manifest = JSONObject(entries[MANIFEST]?.toString(Charsets.UTF_8) ?: error("Missing backup manifest."))
        val schemaVersion = manifest.optInt("schemaVersion")
        require(schemaVersion in LEGACY_SCHEMA_VERSION..CURRENT_SCHEMA_VERSION) { "Unsupported backup version." }
        val kind = manifest.optString("kind").ifBlank { if (allowLegacyBackup) BACKUP_KIND else "" }
        require(kind == expectedKind) { "This is not a $expectedKind archive." }
        val library = entries[LIBRARY] ?: error("Missing library backup section.")
        val checksums = manifest.getJSONObject("sections")
        require(checksums.keys().asSequence().toSet() == setOf(LIBRARY)) { "Unsupported backup payload." }
        require(checksums.optString(LIBRARY) == sha256(library)) { "Backup checksum failed." }
        val sections = if (schemaVersion == LEGACY_SCHEMA_VERSION) {
            RelayBackupSection.all
        } else {
            val values = manifest.optJSONArray("backupSections") ?: error("Backup section list is missing.")
            buildSet {
                for (index in 0 until values.length()) {
                    val wireName = values.getString(index)
                    add(RelayBackupSection.fromWireName(wireName) ?: error("Unsupported backup section: $wireName"))
                }
            }.also { require(it.isNotEmpty() && it.size == values.length()) { "Backup section list is invalid." } }
        }
        val backup = JSONObject(library.toString(Charsets.UTF_8)).toBackup()
        backup.validate()
        return RelayBackupContents(backup, sections, schemaVersion)
    }

    private fun ZipInputStream.readBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            require(output.size() + count <= MAX_ENTRY_BYTES) { "Backup section is too large." }
            output.write(buffer, 0, count)
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private const val BACKUP_KIND = "backup"
    private const val SYNC_KIND = "sync"
    private const val LEGACY_SCHEMA_VERSION = 1
    private const val CURRENT_SCHEMA_VERSION = 2

    private fun UserLibraryBackup.toJson() = JSONObject()
        .put("favorites", JSONArray(favorites.map { JSONObject().put("s", it.sourceId).put("t", it.trackId) }))
        .put("history", JSONArray(history.map {
            JSONObject().put("s", it.sourceId).put("t", it.trackId).put("p", it.playedAtEpochMs)
                .put("title", it.title).put("artist", it.artist).put("album", it.album).put("d", it.durationMs)
                .put("origin", it.origin).put("fingerprint", it.identityFingerprint)
        }))
        .put("profile", profile?.let {
            JSONObject().put("name", it.displayName).put("created", it.createdAtEpochMs).put("lastfm", it.lastFmUsername)
        })
        .put("charts", JSONArray(albumChartSpecs.map { spec ->
            JSONObject().put("id", spec.id).put("range", spec.range).put("metric", spec.metric).put("limit", spec.limit).put("created", spec.createdAtEpochMs)
        }))
        .put("flags", JSONArray(flags.map { JSONObject().put("s", it.sourceId).put("t", it.trackId).put("h", it.hidden).put("p", it.pinned).put("a", it.archived) }))
        .put("playlists", JSONArray(playlists.map { JSONObject().put("i", it.id).put("n", it.name).put("c", it.createdAtEpochMs) }))
        .put("entries", JSONArray(playlistEntries.map {
            JSONObject().put("p", it.playlistId).put("o", it.position).put("s", it.sourceId).put("t", it.trackId)
                .put("title", it.title).put("artist", it.artist).put("album", it.album)
                .put("d", it.durationMs).put("art", it.artworkUri)
        }))
        .put("queue", JSONArray(queueEntries.map {
            JSONObject().put("o", it.position).put("s", it.sourceId).put("t", it.trackId)
                .put("title", it.title).put("artist", it.artist).put("album", it.album)
                .put("d", it.durationMs).put("art", it.artworkUri).put("albumArtist", it.albumArtist)
                .put("releaseDate", it.releaseDate).put("hue", it.artworkHue)
        }))
        .put("queueState", queueState?.let { JSONObject().put("i", it.currentIndex).put("p", it.positionMs) })
        .put("metadata", JSONArray(metadataOverrides.map { JSONObject().put("s", it.sourceId).put("t", it.trackId).put("title", it.title).put("artist", it.artist).put("album", it.album).put("albumArtist", it.albumArtist).put("artworkUri", it.artworkUri).put("musicBrainzId", it.musicBrainzId).put("trackNumber", it.trackNumber).put("discNumber", it.discNumber).put("musicBrainzReleaseId", it.musicBrainzReleaseId) }))
        .put("settings", settings?.let {
            JSONObject().put("v", it.schemaVersion).put("r", it.resumeQueue).put("speed", it.playbackSpeed)
                .put("fadeIn", it.fadeInMs).put("fadeOut", it.fadeOutMs).put("crossfade", it.crossfadeMs)
                .put("eq", it.equalizerEnabled).put("eqPreset", it.equalizerPreset).put("eqBands", it.equalizerBandsJson)
                .put("bass", it.bassBoostStrength).put("loud", it.loudnessNormalization).put("b", it.backupSchedule)
                .put("retention", it.backupRetention).put("e", it.autoBackupExpiryDays)
                .put("shGroup", it.shuffleGrouping).put("shSeed", it.shuffleSeed).put("shLabel", it.shuffleSeedLabel)
                .put("shProfiles", it.shuffleProfilesJson).put("shActive", it.activeShuffleProfileId)
                .put("themes", it.themePacksJson).put("themeActive", it.activeThemePackId)
                .put("lockMetadata", it.showLockscreenMetadata)
                .put("wallpaperFit", it.wallpaperArtworkFit).put("wallpaperBackground", it.wallpaperBackground)
                .put("wallpaperTitle", it.wallpaperShowTrackTitle)
                .put("wallpaperTitlePosition", it.wallpaperTitlePosition).put("wallpaperTitleSize", it.wallpaperTitleSize)
                .put("wallpaperEffect", it.wallpaperEffect).put("wallpaperEffectStrength", it.wallpaperEffectStrength)
                .put("wallpaperVisualizer", it.wallpaperVisualizer).put("wallpaperSoundReactive", it.wallpaperSoundReactive)
                .put("wallpaperPreset", it.wallpaperPresetJson)
                .put("x", JSONArray(it.trustedRepositories().map { repository ->
                    JSONObject().put("id", repository.id).put("name", repository.name).put("url", repository.indexUrl).put("key", repository.signingPublicKey).put("algorithm", repository.signingAlgorithm)
                }))
                .put("i", JSONArray(it.installedExtensions().toInstalledExtensionsJson()))
                .put("sourceSettings", JSONObject(it.sourceSettingsJson))
        })

    private fun JSONObject.toBackup(): UserLibraryBackup {
        require(has("queueState") && has("settings")) { "Backup is missing a required section." }
        return UserLibraryBackup(
            favorites = array("favorites").map { FavoriteTrackEntity(it.getString("s"), it.getString("t")) },
            history = array("history").map {
                ListeningHistoryEntity(
                    sourceId = it.getString("s"), trackId = it.getString("t"), playedAtEpochMs = it.getLong("p"),
                    title = it.nullable("title"), artist = it.nullable("artist"),
                    album = it.nullable("album"), durationMs = it.nullableLong("d"),
                    origin = it.optString("origin", ListeningOrigin.LOCAL.name).also { origin ->
                        require(ListeningOrigin.entries.any { it.name == origin }) { "Backup history has an invalid origin." }
                    },
                    identityFingerprint = it.nullable("fingerprint"),
                )
            },
            profile = optJSONObject("profile")?.let {
                LocalProfileEntity(
                    displayName = it.optString("name", "Relay"),
                    createdAtEpochMs = it.optLong("created", 0L),
                    lastFmUsername = it.nullable("lastfm"),
                )
            },
            albumChartSpecs = optJSONArray("charts")?.let { charts ->
                List(charts.length()) { index ->
                    charts.getJSONObject(index).let { chart ->
                        AlbumChartSpecEntity(chart.getString("id"), chart.getString("range"), chart.getString("metric"), chart.getInt("limit"), chart.getLong("created"))
                    }
                }
            } ?: emptyList(),
            flags = array("flags").map { TrackFlagsEntity(it.getString("s"), it.getString("t"), it.getBoolean("h"), it.getBoolean("p"), it.getBoolean("a")) },
            playlists = array("playlists").map { PlaylistEntity(it.getLong("i"), it.getString("n"), it.getLong("c")) },
            playlistEntries = array("entries").map {
                PlaylistEntryEntity(
                    it.getLong("p"), it.getInt("o"), it.getString("s"), it.getString("t"),
                    it.nullable("title"), it.nullable("artist"), it.nullable("album"),
                    it.nullableLong("d"), it.nullable("art"),
                )
            },
            queueEntries = array("queue").map {
                QueueEntryEntity(
                    it.getInt("o"), it.getString("s"), it.getString("t"),
                    it.nullable("title"), it.nullable("artist"), it.nullable("album"),
                    it.nullableLong("d"), it.nullable("art"), it.nullable("albumArtist"),
                    it.nullable("releaseDate"), if (it.isNull("hue")) null else it.optInt("hue", -1).takeIf { hue -> hue in 0..359 },
                )
            },
            queueState = optJSONObject("queueState")?.let { QueueStateEntity(currentIndex = it.getInt("i"), positionMs = it.getLong("p")) },
            metadataOverrides = array("metadata").map {
                MetadataOverrideEntity(
                    it.getString("s"), it.getString("t"), it.nullable("title"), it.nullable("artist"),
                    it.nullable("album"), it.nullable("albumArtist"), it.nullable("artworkUri"),
                    it.nullable("musicBrainzId"), it.nullableInt("trackNumber"), it.nullableInt("discNumber"),
                    it.nullable("musicBrainzReleaseId"),
                )
            },
            settings = optJSONObject("settings")?.let {
                RelaySettingsEntity(
                    schemaVersion = it.getInt("v"),
                    resumeQueue = it.getBoolean("r"),
                    playbackSpeed = it.optDouble("speed", 1.0).toFloat(),
                    fadeInMs = it.optInt("fadeIn", 0),
                    fadeOutMs = it.optInt("fadeOut", 0),
                    crossfadeMs = it.optInt("crossfade", 0),
                    equalizerEnabled = it.optBoolean("eq", false),
                    equalizerPreset = it.optString("eqPreset", "FLAT"),
                    equalizerBandsJson = it.optString("eqBands", "[0,0,0,0,0]"),
                    bassBoostStrength = it.optInt("bass", 0),
                    loudnessNormalization = it.optBoolean("loud", false),
                    shuffleGrouping = it.optString("shGroup", "NONE"),
                    shuffleSeed = if (it.isNull("shSeed")) null else it.optLong("shSeed"),
                    shuffleSeedLabel = it.nullable("shLabel"),
                    shuffleProfilesJson = it.optString("shProfiles", "[]"),
                    activeShuffleProfileId = it.optString("shActive", "default"),
                    themePacksJson = it.optString("themes", "[]"),
                    activeThemePackId = it.nullable("themeActive"),
                    showLockscreenMetadata = it.optBoolean("lockMetadata", false),
                    wallpaperArtworkFit = it.optString("wallpaperFit", "FILL"),
                    wallpaperBackground = it.optString("wallpaperBackground", "INK"),
                    wallpaperShowTrackTitle = it.optBoolean("wallpaperTitle", false),
                    wallpaperTitlePosition = it.optString("wallpaperTitlePosition", "BOTTOM_LEFT"),
                    wallpaperTitleSize = it.optString("wallpaperTitleSize", "NORMAL"),
                    wallpaperEffect = it.optString("wallpaperEffect", "NONE"),
                    wallpaperEffectStrength = it.optInt("wallpaperEffectStrength", 50),
                    wallpaperVisualizer = it.optString("wallpaperVisualizer", "OFF"),
                    wallpaperSoundReactive = it.optBoolean("wallpaperSoundReactive", false),
                    wallpaperPresetJson = it.nullable("wallpaperPreset"),
                    backupSchedule = it.optString("b", "OFF"),
                    backupRetention = it.optInt("retention", 3),
                    autoBackupExpiryDays = it.optInt("e", 30),
                    trustedRepositoriesJson = it.optJSONArray("x")?.toString() ?: "[]",
                    installedExtensionsJson = it.optJSONArray("i")?.toString() ?: "[]",
                    sourceSettingsJson = it.optJSONObject("sourceSettings")?.toString() ?: "{}",
                )
            },
        )
    }

    private fun JSONObject.array(name: String): List<JSONObject> = buildList {
        require(has(name)) { "Backup is missing $name." }
        val array = getJSONArray(name)
        for (index in 0 until array.length()) add(array.getJSONObject(index))
    }

    private fun JSONObject.nullable(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private fun JSONObject.nullableInt(name: String): Int? = if (isNull(name)) null else optInt(name).takeIf { it > 0 }

    private fun JSONObject.nullableLong(name: String): Long? = if (isNull(name)) null else optLong(name).takeIf { it > 0 }

}

private fun UserLibraryBackup.forSections(sections: Set<RelayBackupSection>) = copy(
    favorites = favorites.takeIf { RelayBackupSection.LIBRARY in sections }.orEmpty(),
    history = history.takeIf { RelayBackupSection.HISTORY in sections }.orEmpty(),
    flags = flags.takeIf { RelayBackupSection.LIBRARY in sections }.orEmpty(),
    playlists = playlists.takeIf { RelayBackupSection.PLAYLISTS in sections }.orEmpty(),
    playlistEntries = playlistEntries.takeIf { RelayBackupSection.PLAYLISTS in sections }.orEmpty(),
    queueEntries = queueEntries.takeIf { RelayBackupSection.QUEUE in sections }.orEmpty(),
    queueState = queueState.takeIf { RelayBackupSection.QUEUE in sections },
    metadataOverrides = metadataOverrides.takeIf { RelayBackupSection.METADATA in sections }.orEmpty(),
    settings = settings?.forSections(sections),
    profile = profile.takeIf { RelayBackupSection.PROFILE in sections },
    albumChartSpecs = albumChartSpecs.takeIf { RelayBackupSection.PROFILE in sections }.orEmpty(),
)

private fun RelaySettingsEntity.forSections(sections: Set<RelayBackupSection>): RelaySettingsEntity? {
    val includeSettings = RelayBackupSection.SETTINGS in sections
    val includeExtensions = RelayBackupSection.EXTENSIONS in sections
    val includeAppearance = RelayBackupSection.APPEARANCE in sections
    if (!includeSettings && !includeExtensions && !includeAppearance) return null

    val defaults = RelaySettingsEntity(schemaVersion = schemaVersion)
    return defaults.copy(
        resumeQueue = resumeQueue.takeIf { includeSettings } ?: defaults.resumeQueue,
        playbackSpeed = playbackSpeed.takeIf { includeSettings } ?: defaults.playbackSpeed,
        fadeInMs = fadeInMs.takeIf { includeSettings } ?: defaults.fadeInMs,
        fadeOutMs = fadeOutMs.takeIf { includeSettings } ?: defaults.fadeOutMs,
        crossfadeMs = crossfadeMs.takeIf { includeSettings } ?: defaults.crossfadeMs,
        equalizerEnabled = equalizerEnabled.takeIf { includeSettings } ?: defaults.equalizerEnabled,
        equalizerPreset = equalizerPreset.takeIf { includeSettings } ?: defaults.equalizerPreset,
        equalizerBandsJson = equalizerBandsJson.takeIf { includeSettings } ?: defaults.equalizerBandsJson,
        bassBoostStrength = bassBoostStrength.takeIf { includeSettings } ?: defaults.bassBoostStrength,
        loudnessNormalization = loudnessNormalization.takeIf { includeSettings } ?: defaults.loudnessNormalization,
        shuffleGrouping = shuffleGrouping.takeIf { includeSettings } ?: defaults.shuffleGrouping,
        shuffleSeed = shuffleSeed.takeIf { includeSettings },
        shuffleSeedLabel = shuffleSeedLabel.takeIf { includeSettings },
        shuffleProfilesJson = shuffleProfilesJson.takeIf { includeSettings } ?: defaults.shuffleProfilesJson,
        activeShuffleProfileId = activeShuffleProfileId.takeIf { includeSettings } ?: defaults.activeShuffleProfileId,
        backupSchedule = backupSchedule.takeIf { includeSettings } ?: defaults.backupSchedule,
        backupRetention = backupRetention.takeIf { includeSettings } ?: defaults.backupRetention,
        autoBackupExpiryDays = autoBackupExpiryDays.takeIf { includeSettings } ?: defaults.autoBackupExpiryDays,
        trustedRepositoriesJson = trustedRepositoriesJson.takeIf { includeExtensions } ?: defaults.trustedRepositoriesJson,
        installedExtensionsJson = installedExtensionsJson.takeIf { includeExtensions } ?: defaults.installedExtensionsJson,
        sourceSettingsJson = sourceSettingsJson.takeIf { includeExtensions } ?: defaults.sourceSettingsJson,
        themePacksJson = themePacksJson.takeIf { includeAppearance } ?: defaults.themePacksJson,
        activeThemePackId = activeThemePackId.takeIf { includeAppearance },
        showLockscreenMetadata = showLockscreenMetadata.takeIf { includeAppearance } ?: defaults.showLockscreenMetadata,
        wallpaperArtworkFit = wallpaperArtworkFit.takeIf { includeAppearance } ?: defaults.wallpaperArtworkFit,
        wallpaperBackground = wallpaperBackground.takeIf { includeAppearance } ?: defaults.wallpaperBackground,
        wallpaperShowTrackTitle = wallpaperShowTrackTitle.takeIf { includeAppearance } ?: defaults.wallpaperShowTrackTitle,
        wallpaperTitlePosition = wallpaperTitlePosition.takeIf { includeAppearance } ?: defaults.wallpaperTitlePosition,
        wallpaperTitleSize = wallpaperTitleSize.takeIf { includeAppearance } ?: defaults.wallpaperTitleSize,
        wallpaperEffect = wallpaperEffect.takeIf { includeAppearance } ?: defaults.wallpaperEffect,
        wallpaperEffectStrength = wallpaperEffectStrength.takeIf { includeAppearance } ?: defaults.wallpaperEffectStrength,
        wallpaperVisualizer = wallpaperVisualizer.takeIf { includeAppearance } ?: defaults.wallpaperVisualizer,
        wallpaperSoundReactive = wallpaperSoundReactive.takeIf { includeAppearance } ?: defaults.wallpaperSoundReactive,
        wallpaperPresetJson = wallpaperPresetJson.takeIf { includeAppearance },
    )
}

internal fun RelaySettingsEntity.withRestoredExtensionsDisabled(): RelaySettingsEntity = copy(
    installedExtensionsJson = installedExtensions()
        .map { it.disabled("Restored backup; review and enable manually.") }
        .toInstalledExtensionsJson(),
)
