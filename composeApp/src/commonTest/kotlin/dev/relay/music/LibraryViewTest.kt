package dev.relay.music

import dev.relay.music.model.Track
import dev.relay.music.model.TrackFlags
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryViewTest {
    private fun track(id: String, title: String, artist: String, album: String? = null) = Track(
        id = id,
        sourceId = "local",
        playbackUri = "content://media/$id",
        title = title,
        artist = artist,
        album = album,
    )

    private val library = listOf(
        track("1", "Wideband", "Relay Demo", "Samples"),
        track("2", "Night Transfer", "aurora", "Bside"),
        track("3", "Signal Test", "Relay Demo", null),
        track("4", "Hidden Track", "Ghost", "Ghosts"),
    )

    private val flags = mapOf(
        trackKey(library[3]) to TrackFlags(hidden = true),
        trackKey(library[2]) to TrackFlags(pinned = true),
    )

    @Test
    fun hidesHiddenTracksAndKeepsPinnedFirst() {
        val view = libraryView(library, flags)

        assertEquals(listOf("Signal Test", "Night Transfer", "Wideband"), view.map { it.title })
    }

    @Test
    fun searchMatchesTitleArtistAndAlbumCaseInsensitively() {
        assertEquals(listOf("Night Transfer"), libraryView(library, flags, query = "AURORA").map { it.title })
        assertEquals(listOf("Wideband"), libraryView(library, flags, query = "samples").map { it.title })
        assertEquals(listOf("Signal Test"), libraryView(library, flags, query = "signal").map { it.title })
        assertEquals(emptyList(), libraryView(library, flags, query = "nothing here").map { it.title })
        // A hidden track stays hidden even when it matches.
        assertEquals(emptyList(), libraryView(library, flags, query = "ghost").map { it.title })
    }

    @Test
    fun sortsByTheChosenFieldWithBlanksLast() {
        val noFlags = emptyMap<String, TrackFlags>()
        val visible = library.dropLast(1)

        assertEquals(
            listOf("Night Transfer", "Signal Test", "Wideband"),
            libraryView(visible, noFlags, sort = LibrarySort.TITLE).map { it.title },
        )
        assertEquals(
            listOf("aurora", "Relay Demo", "Relay Demo"),
            libraryView(visible, noFlags, sort = LibrarySort.ARTIST).map { it.artist },
        )
        assertEquals(
            listOf("Bside", "Samples", null),
            libraryView(visible, noFlags, sort = LibrarySort.ALBUM).map { it.album },
        )
    }
}
