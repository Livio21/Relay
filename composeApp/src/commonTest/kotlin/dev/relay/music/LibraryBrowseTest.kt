package dev.relay.music

import dev.relay.music.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryBrowseTest {
    private fun track(
        id: String,
        title: String,
        artist: String,
        album: String? = null,
        albumArtist: String? = null,
        disc: Int? = null,
        number: Int? = null,
        durationMs: Long? = null,
        artworkUri: String? = null,
        releaseDate: String? = null,
    ) = Track(
        id = id,
        sourceId = "local",
        playbackUri = "content://media/$id",
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        releaseDate = releaseDate,
        durationMs = durationMs,
        artworkUri = artworkUri,
        trackNumber = number,
        discNumber = disc,
    )

    @Test
    fun groupsAlbumsCaseInsensitivelyAndKeepsTheFirstSpelling() {
        val groups = albumGroups(
            listOf(
                track("1", "One", "A", "Samples", number = 2),
                track("2", "Two", "A", "samples", number = 1),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals("Samples", groups.single().title)
        assertEquals(listOf("Two", "One"), groups.single().tracks.map { it.title })
    }

    @Test
    fun ordersAlbumTracksByDiscThenTrackWithMissingNumbersLast() {
        val groups = albumGroups(
            listOf(
                track("a", "Zeta", "A", "Album", disc = 2, number = 1),
                track("b", "Beta", "A", "Album", disc = 1, number = 2),
                track("c", "Alpha", "A", "Album", disc = 1, number = 1),
                track("d", "Loose", "A", "Album"),
            ),
        )

        assertEquals(listOf("Alpha", "Beta", "Zeta", "Loose"), groups.single().tracks.map { it.title })
    }

    @Test
    fun tracksWithoutAnAlbumStayWithTheArtistButAreNotAlbums() {
        val tracks = listOf(
            track("1", "Album Track", "A", "Album"),
            track("2", "Loose Track", "A"),
        )

        assertEquals(listOf("Album"), albumGroups(tracks).map { it.title })
        val artist = artistGroups(tracks).single()
        assertEquals(2, artist.trackCount)
        assertEquals(1, artist.albumCount)
        assertEquals(listOf("Album Track", "Loose Track"), artist.tracks.map { it.title })
    }

    @Test
    fun groupsByAlbumArtistAndFallsBackToTheTrackArtist() {
        val groups = albumGroups(
            listOf(
                track("1", "One", "Boards of Canada", "Geogaddi"),
                track("2", "Two", "BOC", "Geogaddi", albumArtist = "Boards of Canada"),
                track("3", "Three", "Aphex Twin", "Selected Ambient Works"),
            ),
        )

        assertEquals(2, groups.size)
        assertEquals("Boards of Canada", groups.first { it.title == "Geogaddi" }.artist)
        assertEquals("Aphex Twin", groups.first { it.title == "Selected Ambient Works" }.artist)
    }

    @Test
    fun blankArtistsGroupUnderUnknownArtist() {
        val artist = artistGroups(
            listOf(
                track("1", "One", ""),
                track("2", "Two", "", album = null, albumArtist = ""),
            ),
        ).single()

        assertEquals("Unknown artist", artist.name)
        assertEquals(2, artist.trackCount)
        assertEquals(0, artist.albumCount)
    }

    @Test
    fun albumSummaryUsesEarliestYearAndFirstAvailableArtwork() {
        val groups = albumGroups(
            listOf(
                track("1", "One", "A", "Album", artworkUri = null, releaseDate = "2024-05-01"),
                track("2", "Two", "A", "Album", artworkUri = "art://b", releaseDate = "1998"),
                track("3", "Three", "A", "Album", artworkUri = "art://a", releaseDate = "2001"),
            ),
        )

        assertEquals("1998", groups.single().year)
        assertEquals("art://a", groups.single().artworkUri)
    }

    @Test
    fun sortsAlbumsAndArtistsCaseInsensitively() {
        val tracks = listOf(
            track("1", "One", "beta", "Zeta"),
            track("2", "Two", "Zulu", "alpha"),
        )

        assertEquals(listOf("alpha", "Zeta"), albumGroups(tracks).map { it.title })
        assertEquals(listOf("beta", "Zulu"), artistGroups(tracks).map { it.name })
    }

    @Test
    fun sumsKnownDurationsAndIgnoresMissingOnes() {
        val tracks = listOf(
            track("1", "One", "A", "Album", durationMs = 1_000),
            track("2", "Two", "A", "Album", durationMs = null),
            track("3", "Three", "A", "Album", durationMs = 2_500),
        )

        assertEquals(3_500, albumGroups(tracks).single().durationMs)
        assertEquals(3_500, artistGroups(tracks).single().durationMs)
    }

    @Test
    fun artistCarriesItsAlbumsAndAlbumKeysAreStableAcrossCasing() {
        val tracks = listOf(
            track("1", "One", "A", "Album"),
            track("2", "Two", "A", "album", number = 2),
        )

        val artist = artistGroups(tracks).single()
        assertEquals(1, artist.albumCount)
        assertEquals(1, albumGroups(tracks).size)
        assertTrue(artist.albums.single().key == albumGroups(tracks).single().key)
    }
}

class LibraryBrowseFilterTest {
    private fun track(id: String, artist: String, album: String) = Track(
        id = id,
        sourceId = "local",
        playbackUri = "content://media/$id",
        title = "Track $id",
        artist = artist,
        album = album,
    )

    private val albums = albumGroups(
        listOf(
            track("1", "Boards of Canada", "Geogaddi"),
            track("2", "Autechre", "Untilted"),
        ),
    )

    private val artists = artistGroups(
        listOf(
            track("1", "Boards of Canada", "Geogaddi"),
            track("2", "Autechre", "Untilted"),
        ),
    )

    @Test
    fun blankQueriesKeepEverything() {
        assertEquals(albums, filterAlbums(albums, "  "))
        assertEquals(artists, filterArtists(artists, ""))
    }

    @Test
    fun filtersAlbumsByTitleOrArtistCaseInsensitively() {
        assertEquals(listOf("Geogaddi"), filterAlbums(albums, "GEOGADDI").map { it.title })
        assertEquals(listOf("Untilted"), filterAlbums(albums, "autechre").map { it.title })
        assertEquals(emptyList(), filterAlbums(albums, "nothing"))
    }

    @Test
    fun filtersArtistsByNameOrAlbumTitle() {
        assertEquals(listOf("Boards of Canada"), filterArtists(artists, "boards").map { it.name })
        assertEquals(listOf("Autechre"), filterArtists(artists, "untilted").map { it.name })
        assertEquals(emptyList(), filterArtists(artists, "nothing"))
    }
}
