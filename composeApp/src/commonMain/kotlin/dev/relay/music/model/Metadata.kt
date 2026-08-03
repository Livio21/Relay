package dev.relay.music.model

enum class MetadataHealth {
    COMPLETE,
    NEEDS_REVIEW,
}

enum class MetadataIssue {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    ARTWORK,
    ARTWORK_UNREADABLE,
    ALBUM_GROUP_CONFLICT,
    UNREVIEWED_PROVIDER_SUGGESTION,
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
    val musicBrainzReleaseId: String? = null,
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
    val durationMs: Long? = null,
)

/** Metadata supplied by a trusted source separately from the embedded/source [Track] snapshot. */
data class TrackMetadataLayer(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val releaseDate: String? = null,
    val durationMs: Long? = null,
    val artworkUri: String? = null,
    val artworkHue: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val musicBrainzId: String? = null,
    val musicBrainzReleaseId: String? = null,
)

data class DisplayTrackMetadata(
    val title: String = "Untitled track",
    val artist: String = "Unknown artist",
    val album: String = "Unknown album",
)

data class EffectiveTrackMetadata(
    val track: Track,
    val display: DisplayTrackMetadata,
) {
    val trackerEligible: Boolean
        get() = track.title.isMeaningfulMetadata() && track.artist.isMeaningfulMetadata()
}

/** Inputs that are known only after bounded local/provider checks; null means artwork was not checked. */
data class MetadataHealthSignals(
    val artworkReadable: Boolean? = null,
    val hasAlbumGroupConflict: Boolean = false,
    val hasUnreviewedProviderSuggestion: Boolean = false,
)

/**
 * Resolves user override -> trusted source -> embedded/source metadata. Display fallbacks are
 * returned separately and never replace canonical fields or the track's playback identity.
 */
fun Track.resolveEffectiveMetadata(
    userOverride: MetadataOverride? = null,
    trustedSource: TrackMetadataLayer? = null,
    displayFallback: DisplayTrackMetadata = DisplayTrackMetadata(),
): EffectiveTrackMetadata {
    val overrideArtwork = userOverride?.artworkUri.presentText()
    val trustedArtwork = trustedSource?.artworkUri.presentText()
    val embeddedArtwork = artworkUri.presentText()
    val (resolvedArtwork, resolvedArtworkHue) = when {
        overrideArtwork != null -> overrideArtwork to null
        trustedArtwork != null -> trustedArtwork to trustedSource?.artworkHue
        else -> embeddedArtwork to artworkHue.takeIf { embeddedArtwork != null }
    }
    val resolved = copy(
        title = firstText(userOverride?.title, trustedSource?.title, title).orEmpty(),
        artist = firstText(userOverride?.artist, trustedSource?.artist, artist).orEmpty(),
        album = firstText(userOverride?.album, trustedSource?.album, album),
        albumArtist = firstText(userOverride?.albumArtist, trustedSource?.albumArtist, albumArtist),
        releaseDate = trustedSource?.releaseDate.presentText() ?: releaseDate.presentText(),
        durationMs = trustedSource?.durationMs ?: durationMs,
        artworkUri = resolvedArtwork,
        artworkHue = resolvedArtworkHue,
        trackNumber = userOverride?.trackNumber ?: trustedSource?.trackNumber ?: trackNumber,
        discNumber = userOverride?.discNumber ?: trustedSource?.discNumber ?: discNumber,
        musicBrainzId = firstText(userOverride?.musicBrainzId, trustedSource?.musicBrainzId, musicBrainzId),
        musicBrainzReleaseId = firstText(
            userOverride?.musicBrainzReleaseId,
            trustedSource?.musicBrainzReleaseId,
            musicBrainzReleaseId,
        ),
    )
    return EffectiveTrackMetadata(
        track = resolved,
        display = DisplayTrackMetadata(
            title = resolved.title.takeIf { it.isMeaningfulMetadata() } ?: displayFallback.title,
            artist = resolved.artist.takeIf { it.isMeaningfulMetadata() } ?: displayFallback.artist,
            album = resolved.album.takeIf { it.isMeaningfulMetadata() } ?: displayFallback.album,
        ),
    )
}

fun Track.withMetadataOverride(override: MetadataOverride?): Track =
    resolveEffectiveMetadata(userOverride = override).track

fun EffectiveTrackMetadata.metadataIssues(
    signals: MetadataHealthSignals = MetadataHealthSignals(),
): Set<MetadataIssue> = buildSet {
    if (!track.title.isMeaningfulMetadata()) add(MetadataIssue.TITLE)
    if (!track.artist.isMeaningfulMetadata()) add(MetadataIssue.ARTIST)
    if (!track.album.isMeaningfulMetadata()) add(MetadataIssue.ALBUM)
    if (track.album.isMeaningfulMetadata() && !track.albumArtist.isMeaningfulMetadata()) {
        add(MetadataIssue.ALBUM_ARTIST)
    }
    if (track.artworkUri.isNullOrBlank()) {
        add(MetadataIssue.ARTWORK)
    } else if (signals.artworkReadable == false) {
        add(MetadataIssue.ARTWORK_UNREADABLE)
    }
    if (signals.hasAlbumGroupConflict) add(MetadataIssue.ALBUM_GROUP_CONFLICT)
    if (signals.hasUnreviewedProviderSuggestion) add(MetadataIssue.UNREVIEWED_PROVIDER_SUGGESTION)
}

fun String?.isMeaningfulMetadata(): Boolean =
    !isNullOrBlank() && trim().lowercase() !in GENERIC_METADATA

fun Track.metadataIssues(signals: MetadataHealthSignals = MetadataHealthSignals()): Set<MetadataIssue> =
    resolveEffectiveMetadata().metadataIssues(signals)

fun EffectiveTrackMetadata.metadataHealth(signals: MetadataHealthSignals = MetadataHealthSignals()): MetadataHealth =
    if (metadataIssues(signals).isEmpty()) MetadataHealth.COMPLETE else MetadataHealth.NEEDS_REVIEW

fun Track.metadataHealth(signals: MetadataHealthSignals = MetadataHealthSignals()): MetadataHealth =
    resolveEffectiveMetadata().metadataHealth(signals)

private fun firstText(first: String?, second: String?, third: String?): String? =
    first.presentText() ?: second.presentText() ?: third.presentText()

private fun String?.presentText(): String? = this?.takeIf { it.isNotBlank() }

private val GENERIC_METADATA = setOf(
    "<unknown>",
    "unknown",
    "unknown album",
    "unknown artist",
    "unknown title",
    "untitled",
    "untitled track",
)
