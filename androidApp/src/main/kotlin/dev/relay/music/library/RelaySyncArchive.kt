package dev.relay.music.library

import dev.relay.music.model.ListeningEvent
import dev.relay.music.model.ListeningOrigin
import dev.relay.music.model.newListeningEventsForImport
import java.io.InputStream
import java.io.OutputStream
import org.json.JSONObject

/** Portable, data-only sync bundle. Audio, credentials, queues, and device storage are excluded. */
object RelaySyncArchive {
    suspend fun write(output: OutputStream, dao: UserLibraryDao) = RelayBackupArchive.writeSync(output, dao)

    fun read(input: InputStream): UserLibraryBackup = RelayBackupArchive.readSync(input)
}

internal data class SyncSnapshot(
    val favorites: List<FavoriteTrackEntity>,
    val history: List<ListeningHistoryEntity>,
    val flags: List<TrackFlagsEntity>,
    val playlists: List<PlaylistEntity>,
    val playlistEntries: List<PlaylistEntryEntity>,
    val metadataOverrides: List<MetadataOverrideEntity>,
    val profile: LocalProfileEntity?,
    val charts: List<AlbumChartSpecEntity>,
)

internal data class SyncPlaylistImport(
    val name: String,
    val createdAtEpochMs: Long,
    val entries: List<PlaylistEntryEntity>,
)

data class SyncConflictDraft(
    val section: String,
    val key: String,
    val description: String,
    val receivedPayload: String,
)

internal data class SyncMergePlan(
    val favoritesToAdd: List<FavoriteTrackEntity>,
    val historyToAdd: List<ListeningHistoryEntity>,
    val flagsToAdd: List<TrackFlagsEntity>,
    val metadataToAdd: List<MetadataOverrideEntity>,
    val chartsToAdd: List<AlbumChartSpecEntity>,
    val profileToAdd: LocalProfileEntity?,
    val playlistsToAdd: List<SyncPlaylistImport>,
    val conflicts: List<SyncConflictDraft>,
)

data class SyncImportResult(
    val favoritesAdded: Int,
    val historyAdded: Int,
    val flagsAdded: Int,
    val metadataAdded: Int,
    val chartsAdded: Int,
    val playlistsAdded: Int,
    val conflicts: List<SyncConflictDraft>,
) {
    val importedRecords: Int
        get() = favoritesAdded + historyAdded + flagsAdded + metadataAdded + chartsAdded + playlistsAdded
}

/** Builds a non-destructive merge: existing conflicting values win and are reported for review. */
internal fun syncMergePlan(existing: SyncSnapshot, incoming: UserLibraryBackup): SyncMergePlan {
    incoming.validate()
    val existingFavorites = existing.favorites.map { it.sourceId to it.trackId }.toSet()
    val existingFlags = existing.flags.associateBy { it.sourceId to it.trackId }
    val existingMetadata = existing.metadataOverrides.associateBy { it.sourceId to it.trackId }
    val existingCharts = existing.charts.associateBy { it.id }
    val conflicts = mutableListOf<SyncConflictDraft>()

    val flags = incoming.flags.filter { received ->
        val local = existingFlags[received.sourceId to received.trackId]
        when {
            local == null -> true
            local == received -> false
            else -> {
                conflicts += received.asConflictDraft()
                false
            }
        }
    }
    val metadata = incoming.metadataOverrides.filter { received ->
        val local = existingMetadata[received.sourceId to received.trackId]
        when {
            local == null -> true
            local == received -> false
            else -> {
                conflicts += received.asConflictDraft()
                false
            }
        }
    }
    val charts = incoming.albumChartSpecs.filter { received ->
        val local = existingCharts[received.id]
        when {
            local == null -> true
            local == received -> false
            else -> {
                conflicts += received.asConflictDraft()
                false
            }
        }
    }
    val profile = incoming.profile?.takeIf { received ->
        val local = existing.profile
        when {
            local == null -> true
            local == received -> false
            else -> {
                conflicts += received.asConflictDraft()
                false
            }
        }
    }
    val existingSignatures = existing.playlists.associate { playlist ->
        playlist.id to playlistSignature(playlist.name, existing.playlistEntries.filter { it.playlistId == playlist.id })
    }.values.toMutableSet()
    val usedNames = existing.playlists.map { it.name.lowercase() }.toMutableSet()
    val playlists = incoming.playlists.mapNotNull { received ->
        val entries = incoming.playlistEntries.filter { it.playlistId == received.id }.sortedBy { it.position }
        val signature = playlistSignature(received.name, entries)
        if (!existingSignatures.add(signature)) return@mapNotNull null
        val collides = !usedNames.add(received.name.lowercase())
        val name = uniqueSyncPlaylistName(received.name, usedNames, collides)
        // A received playlist is preserved as its own variant, so it is not a destructive conflict.
        SyncPlaylistImport(name, received.createdAtEpochMs, entries)
    }
    val history = newListeningEventsForImport(
        existing.history.map(ListeningHistoryEntity::asListeningEvent),
        incoming.history.map(ListeningHistoryEntity::asListeningEvent),
    ).map(ListeningEvent::asHistoryEntity)

    return SyncMergePlan(
        favoritesToAdd = incoming.favorites.filter { (it.sourceId to it.trackId) !in existingFavorites },
        historyToAdd = history,
        flagsToAdd = flags,
        metadataToAdd = metadata,
        chartsToAdd = charts,
        profileToAdd = profile,
        playlistsToAdd = playlists,
        conflicts = conflicts.distinctBy { it.section to it.key to it.receivedPayload },
    )
}

private fun TrackFlagsEntity.asConflictDraft() = SyncConflictDraft(
    section = "FLAGS",
    key = "$sourceId/$trackId",
    description = "FLAGS: $sourceId/$trackId",
    receivedPayload = JSONObject().put("sourceId", sourceId).put("trackId", trackId)
        .put("hidden", hidden).put("pinned", pinned).put("archived", archived).toString(),
)

private fun MetadataOverrideEntity.asConflictDraft() = SyncConflictDraft(
    section = "METADATA",
    key = "$sourceId/$trackId",
    description = "METADATA: $sourceId/$trackId",
    receivedPayload = JSONObject().put("sourceId", sourceId).put("trackId", trackId)
        .put("title", title).put("artist", artist).put("album", album).put("albumArtist", albumArtist)
        .put("artworkUri", artworkUri).put("musicBrainzId", musicBrainzId)
        .put("trackNumber", trackNumber).put("discNumber", discNumber)
        .put("musicBrainzReleaseId", musicBrainzReleaseId).toString(),
)

private fun AlbumChartSpecEntity.asConflictDraft() = SyncConflictDraft(
    section = "CHART",
    key = id,
    description = "CHART: $id",
    receivedPayload = JSONObject().put("id", id).put("range", range).put("metric", metric)
        .put("limit", limit).put("created", createdAtEpochMs).toString(),
)

private fun LocalProfileEntity.asConflictDraft() = SyncConflictDraft(
    section = "PROFILE",
    key = "local-profile",
    description = "PROFILE",
    receivedPayload = JSONObject().put("name", displayName).put("created", createdAtEpochMs).put("lastfm", lastFmUsername).toString(),
)

private fun playlistSignature(name: String, entries: List<PlaylistEntryEntity>): String = buildString {
    append(name.trim().lowercase())
    entries.sortedBy { it.position }.forEach { append('\u0000').append(it.sourceId).append('\u0000').append(it.trackId) }
}

private fun uniqueSyncPlaylistName(original: String, usedNames: MutableSet<String>, collided: Boolean): String {
    val base = original.trim().take(90).ifBlank { "Imported playlist" }
    if (!collided) return base
    var suffix = 1
    while (true) {
        val candidate = "$base (SYNC${if (suffix == 1) "" else " $suffix"})".take(100)
        if (usedNames.add(candidate.lowercase())) return candidate
        suffix++
    }
}

private fun ListeningHistoryEntity.asListeningEvent() = ListeningEvent(
    sourceId = sourceId,
    trackId = trackId,
    playedAtEpochMs = playedAtEpochMs,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    origin = ListeningOrigin.entries.firstOrNull { it.name == origin } ?: ListeningOrigin.LOCAL,
    identityFingerprint = identityFingerprint,
)

private fun ListeningEvent.asHistoryEntity() = ListeningHistoryEntity(
    sourceId = sourceId,
    trackId = trackId,
    playedAtEpochMs = playedAtEpochMs,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    origin = origin.name,
    identityFingerprint = identityFingerprint,
)
