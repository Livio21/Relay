package dev.relay.music

import dev.relay.music.model.Track
import dev.relay.music.model.TrackFlags

enum class LibrarySort { TITLE, ARTIST, ALBUM }

/**
 * What the library list shows: hidden tracks removed, an optional case-insensitive search over
 * title/artist/album, then pinned tracks first and the chosen field ordered with blanks last.
 */
fun libraryView(
    tracks: List<Track>,
    flags: Map<String, TrackFlags>,
    query: String = "",
    sort: LibrarySort = LibrarySort.TITLE,
): List<Track> {
    val needle = query.trim().lowercase()
    return tracks
        .asSequence()
        .filterNot { flags[trackKey(it)]?.hidden == true }
        .filter { needle.isEmpty() || it.matchesLibraryQuery(needle) }
        .sortedWith(
            compareByDescending<Track> { flags[trackKey(it)]?.pinned == true }
                .thenBy { it.sortField(sort).isEmpty() }
                .thenBy { it.sortField(sort) }
                .thenBy { it.title.trim().lowercase() },
        )
        .toList()
}

private fun Track.matchesLibraryQuery(needle: String): Boolean =
    title.contains(needle, ignoreCase = true) ||
        artist.contains(needle, ignoreCase = true) ||
        album?.contains(needle, ignoreCase = true) == true

private fun Track.sortField(sort: LibrarySort): String = when (sort) {
    LibrarySort.TITLE -> title
    LibrarySort.ARTIST -> albumArtist ?: artist
    LibrarySort.ALBUM -> album.orEmpty()
}.trim().lowercase()
