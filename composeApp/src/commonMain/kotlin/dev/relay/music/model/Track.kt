package dev.relay.music.model

data class Track(
    val id: String,
    val sourceId: String,
    val playbackUri: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumArtist: String? = null,
    /** Original release date when supplied by the local file or a trusted source. */
    val releaseDate: String? = null,
    val durationMs: Long? = null,
    val artworkUri: String? = null,
    /** Cached local artwork hue in degrees; null means artwork is unavailable or has no usable color. */
    val artworkHue: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val musicBrainzId: String? = null,
    /** Changes when the local file changes; used only to invalidate derived metadata. */
    val sourceRevision: String? = null,
    val musicBrainzReleaseId: String? = null,
)

data class TrackFlags(
    val hidden: Boolean = false,
    val pinned: Boolean = false,
    val archived: Boolean = false,
)
