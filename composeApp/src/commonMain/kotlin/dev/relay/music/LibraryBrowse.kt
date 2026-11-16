package dev.relay.music

import dev.relay.music.model.Track

/**
 * Album and artist browsing built from the tracks the library already shows.
 *
 * The caller passes the visible (and optionally searched) tracks, so hidden and archived tracks
 * never reach a group. Grouping is case-insensitive and keeps the first spelling it sees for the
 * display name. Tracks without an album stay in their artist but are not albums of their own.
 */
data class AlbumGroup(
    val key: String,
    val title: String,
    val artist: String,
    val year: String?,
    val artworkUri: String?,
    val tracks: List<Track>,
) {
    val trackCount: Int get() = tracks.size
    val durationMs: Long get() = tracks.sumOf { it.durationMs ?: 0L }
}

data class ArtistGroup(
    val key: String,
    val name: String,
    val albums: List<AlbumGroup>,
    val tracks: List<Track>,
) {
    val trackCount: Int get() = tracks.size
    val albumCount: Int get() = albums.size
    val durationMs: Long get() = tracks.sumOf { it.durationMs ?: 0L }
    val artworkUri: String?
        get() = albums.firstNotNullOfOrNull { it.artworkUri }
            ?: tracks.firstNotNullOfOrNull { it.artworkUri }
}

fun albumGroups(tracks: List<Track>): List<AlbumGroup> {
    val grouped = LinkedHashMap<String, MutableList<Track>>()
    for (track in tracks) {
        val title = track.album?.trim().orEmpty()
        if (title.isEmpty()) continue
        val artist = albumArtistName(track)
        val key = albumKey(title, artist)
        grouped.getOrPut(key) { mutableListOf() }.add(track)
    }
    return grouped.map { (key, albumTracks) ->
        val ordered = albumTracks.sortedWith(albumTrackOrder)
        val firstSeen = albumTracks.first()
        AlbumGroup(
            key = key,
            title = firstSeen.album?.trim().orEmpty(),
            artist = albumArtistName(firstSeen),
            year = ordered.mapNotNull(::releaseYear).minOrNull()?.toString(),
            artworkUri = ordered.firstNotNullOfOrNull { it.artworkUri },
            tracks = ordered,
        )
    }.sortedWith(compareBy({ it.title.lowercase() }, { it.artist.lowercase() }))
}

fun artistGroups(tracks: List<Track>): List<ArtistGroup> {
    val albumsByArtist = albumGroups(tracks).groupBy { it.artist.lowercase() }
    val grouped = LinkedHashMap<String, MutableList<Track>>()
    for (track in tracks) {
        grouped.getOrPut(albumArtistName(track).lowercase()) { mutableListOf() }.add(track)
    }
    return grouped.map { (key, artistTracks) ->
        ArtistGroup(
            key = key,
            name = albumArtistName(artistTracks.first()),
            albums = albumsByArtist[key].orEmpty(),
            tracks = artistTracks.sortedWith(artistTrackOrder),
        )
    }.sortedBy { it.name.lowercase() }
}

private val albumTrackOrder =
    compareBy<Track>({ it.trackNumber == null })
        .thenBy { it.discNumber ?: 1 }
        .thenBy { it.trackNumber ?: Int.MAX_VALUE }
        .thenBy { it.title.lowercase() }

private val artistTrackOrder =
    compareBy<Track>({ it.album.isNullOrBlank() })
        .thenBy { it.album.orEmpty().lowercase() }
        .thenBy { it.trackNumber == null }
        .thenBy { it.discNumber ?: 1 }
        .thenBy { it.trackNumber ?: Int.MAX_VALUE }
        .thenBy { it.title.lowercase() }

private fun albumKey(title: String, artist: String): String =
    "${artist.trim().lowercase()}\u0000${title.trim().lowercase()}"

private fun albumArtistName(track: Track): String =
    track.albumArtist?.trim()?.takeIf { it.isNotEmpty() }
        ?: track.artist.trim().takeIf { it.isNotEmpty() }
        ?: "Unknown artist"

private fun releaseYear(track: Track): Int? =
    track.releaseDate?.trim()?.take(4)?.toIntOrNull()?.takeIf { it in 1900..2100 }

/** Filters an existing album list by album title or artist; a blank query keeps everything. */
fun filterAlbums(albums: List<AlbumGroup>, query: String): List<AlbumGroup> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return albums
    return albums.filter {
        it.title.lowercase().contains(needle) || it.artist.lowercase().contains(needle)
    }
}

/** Filters an existing artist list by artist name or one of that artist's album titles. */
fun filterArtists(artists: List<ArtistGroup>, query: String): List<ArtistGroup> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return artists
    return artists.filter { artist ->
        artist.name.lowercase().contains(needle) ||
            artist.albums.any { it.title.lowercase().contains(needle) }
    }
}
