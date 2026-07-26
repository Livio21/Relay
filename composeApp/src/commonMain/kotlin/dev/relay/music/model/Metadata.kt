package dev.relay.music.model

enum class MetadataHealth {
    COMPLETE,
    NEEDS_REVIEW,
}

enum class MetadataIssue {
    TITLE,
    ARTIST,
    ALBUM,
    ARTWORK,
}

data class MetadataOverride(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val artworkUri: String? = null,
    val musicBrainzId: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
)

data class MetadataCandidate(
    val title: String,
    val artist: String,
    val album: String?,
    val albumArtist: String?,
    val recordingId: String,
    val releaseId: String? = null,
    val artworkUri: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
)

fun Track.withMetadataOverride(override: MetadataOverride?): Track {
    if (override == null) return this
    return copy(
        title = override.title ?: title,
        artist = override.artist ?: artist,
        album = override.album ?: album,
        albumArtist = override.albumArtist ?: albumArtist,
        artworkUri = override.artworkUri ?: artworkUri,
        musicBrainzId = override.musicBrainzId ?: musicBrainzId,
        trackNumber = override.trackNumber ?: trackNumber,
        discNumber = override.discNumber ?: discNumber,
    )
}

fun Track.metadataIssues(): Set<MetadataIssue> = buildSet {
    if (title.isGenericMetadata()) add(MetadataIssue.TITLE)
    if (artist.isGenericMetadata()) add(MetadataIssue.ARTIST)
    if (album.isNullOrBlank()) add(MetadataIssue.ALBUM)
    if (artworkUri.isNullOrBlank()) add(MetadataIssue.ARTWORK)
}

private fun String?.isGenericMetadata(): Boolean =
    isNullOrBlank() || trim().lowercase() in setOf("<unknown>", "unknown", "unknown artist", "untitled")

fun Track.metadataHealth(): MetadataHealth = if (metadataIssues().isEmpty()) {
    MetadataHealth.COMPLETE
} else {
    MetadataHealth.NEEDS_REVIEW
}
