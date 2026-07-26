package dev.relay.music.library

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

object RelayBackupArchive {
    private const val MANIFEST = "manifest.json"
    private const val LIBRARY = "library.json"
    private const val MAX_ENTRY_BYTES = 2 * 1024 * 1024

    suspend fun write(output: OutputStream, dao: UserLibraryDao) {
        val backup = UserLibraryBackup(
            favorites = dao.favoriteSnapshot(),
            history = dao.historySnapshot(),
            flags = dao.flagsSnapshot(),
            playlists = dao.playlistSnapshot(),
            playlistEntries = dao.playlistEntrySnapshot(),
            queueEntries = dao.queueEntries(),
            queueState = dao.queueState(),
            metadataOverrides = dao.metadataOverrides(),
            settings = dao.settings().first(),
        )
        val library = backup.toJson().toString().toByteArray(Charsets.UTF_8)
        val manifest = JSONObject()
            .put("schemaVersion", 1)
            .put("sections", JSONObject().put(LIBRARY, sha256(library)))
            .toString().toByteArray(Charsets.UTF_8)
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST)); zip.write(manifest); zip.closeEntry()
            zip.putNextEntry(ZipEntry(LIBRARY)); zip.write(library); zip.closeEntry()
        }
    }

    fun read(input: InputStream): UserLibraryBackup {
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
        require(manifest.optInt("schemaVersion") == 1) { "Unsupported backup version." }
        val library = entries[LIBRARY] ?: error("Missing library backup section.")
        require(manifest.getJSONObject("sections").optString(LIBRARY) == sha256(library)) { "Backup checksum failed." }
        val backup = JSONObject(library.toString(Charsets.UTF_8)).toBackup()
        backup.validate()
        return backup
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

    private fun UserLibraryBackup.toJson() = JSONObject()
        .put("favorites", JSONArray(favorites.map { JSONObject().put("s", it.sourceId).put("t", it.trackId) }))
        .put("history", JSONArray(history.map {
            JSONObject().put("s", it.sourceId).put("t", it.trackId).put("p", it.playedAtEpochMs)
                .put("title", it.title).put("artist", it.artist).put("album", it.album).put("d", it.durationMs)
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
        .put("metadata", JSONArray(metadataOverrides.map { JSONObject().put("s", it.sourceId).put("t", it.trackId).put("title", it.title).put("artist", it.artist).put("album", it.album).put("albumArtist", it.albumArtist).put("artworkUri", it.artworkUri).put("musicBrainzId", it.musicBrainzId).put("trackNumber", it.trackNumber).put("discNumber", it.discNumber) }))
        .put("settings", settings?.let {
            JSONObject().put("v", it.schemaVersion).put("r", it.resumeQueue).put("speed", it.playbackSpeed)
                .put("fadeIn", it.fadeInMs).put("fadeOut", it.fadeOutMs)
                .put("eq", it.equalizerEnabled).put("eqPreset", it.equalizerPreset).put("eqBands", it.equalizerBandsJson)
                .put("bass", it.bassBoostStrength).put("loud", it.loudnessNormalization).put("b", it.backupSchedule).put("e", it.autoBackupExpiryDays)
                .put("shGroup", it.shuffleGrouping).put("shSeed", it.shuffleSeed).put("shLabel", it.shuffleSeedLabel)
                .put("shProfiles", it.shuffleProfilesJson).put("shActive", it.activeShuffleProfileId)
                .put("themes", it.themePacksJson).put("themeActive", it.activeThemePackId)
                .put("x", JSONArray(it.trustedRepositories().map { repository ->
                    JSONObject().put("id", repository.id).put("name", repository.name).put("url", repository.indexUrl).put("key", repository.signingPublicKey).put("algorithm", repository.signingAlgorithm)
                }))
                .put("i", JSONArray(it.installedExtensions().map { extension ->
                    JSONObject()
                        .put("repositoryId", extension.repositoryId)
                        .put("extensionId", extension.extensionId)
                        .put("version", extension.version)
                        .put("kind", extension.kind.name)
                        .put("enabled", extension.enabled)
                        .put("disabledReason", extension.disabledReason)
                        .put("permissions", JSONArray(extension.permissions.map { permission -> permission.name }))
                        .put("androidPackageName", extension.androidPackageName)
                        .put("androidSigningCertificateSha256", extension.androidSigningCertificateSha256)
                }))
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
                )
            },
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
                )
            },
            settings = optJSONObject("settings")?.let {
                RelaySettingsEntity(
                    schemaVersion = it.getInt("v"),
                    resumeQueue = it.getBoolean("r"),
                    playbackSpeed = it.optDouble("speed", 1.0).toFloat(),
                    fadeInMs = it.optInt("fadeIn", 0),
                    fadeOutMs = it.optInt("fadeOut", 0),
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
                    backupSchedule = it.optString("b", "OFF"),
                    autoBackupExpiryDays = it.optInt("e", 30),
                    trustedRepositoriesJson = it.optJSONArray("x")?.toString() ?: "[]",
                    installedExtensionsJson = it.optJSONArray("i")?.toString() ?: "[]",
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
