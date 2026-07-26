package dev.relay.music

import dev.relay.music.model.Track
import dev.relay.music.playback.ShuffleGrouping
import dev.relay.music.playback.MissingShuffleValue
import dev.relay.music.playback.ShuffleProfile
import dev.relay.music.playback.shuffleSeedFromBytes
import dev.relay.music.playback.shuffledQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ShuffleTest {
    private fun track(
        id: Int,
        artist: String = "Artist",
        album: String? = "Album",
        artworkHue: Int? = null,
    ) = Track(
        id = "t$id",
        sourceId = "local",
        playbackUri = "content://media/$id",
        title = "Track $id",
        artist = artist,
        album = album,
        artworkHue = artworkHue,
    )

    private val queue = List(20) { track(it, artist = "Artist ${it % 4}") }

    @Test
    fun shuffleIsAPermutationThatKeepsTheCurrentTrackFirst() {
        val shuffled = shuffledQueue(queue, currentIndex = 7, profile = ShuffleProfile(seed = 42))

        assertEquals(queue[7], shuffled.first())
        assertEquals(queue.toSet(), shuffled.toSet())
        assertEquals(queue.size, shuffled.size)
    }

    @Test
    fun fixedSeedReproducesTheSameOrderAndDifferentSeedsDiverge() {
        val first = shuffledQueue(queue, 0, ShuffleProfile(seed = 42))
        val second = shuffledQueue(queue, 0, ShuffleProfile(seed = 42))
        val other = shuffledQueue(queue, 0, ShuffleProfile(seed = 43))

        assertEquals(first, second)
        assertNotEquals(first, other)
    }

    @Test
    fun groupedShuffleOrdersGroupsAlphabeticallyWithUnknownLast() {
        val mixed = listOf(
            track(1, artist = "Zeta"),
            track(2, artist = " "),
            track(3, artist = "alpha"),
            track(4, artist = "Zeta"),
            track(5, artist = "alpha"),
        )
        val shuffled = shuffledQueue(
            mixed,
            currentIndex = -1,
            profile = ShuffleProfile(rules = listOf(ShuffleGrouping.ARTIST), seed = 7),
        )

        val groups = shuffled.map { it.artist.trim().lowercase() }
        assertEquals(listOf("alpha", "alpha", "zeta", "zeta", ""), groups)
        assertEquals(mixed.toSet(), shuffled.toSet())
    }

    @Test
    fun seedFromImageBytesIsDeterministicWithAShortFingerprint() {
        val bytes = ByteArray(1_024) { (it % 251).toByte() }
        val first = shuffleSeedFromBytes(bytes)
        val second = shuffleSeedFromBytes(bytes)

        assertEquals(first.seed, second.seed)
        assertEquals(first.seedLabel, second.seedLabel)
        assertEquals(8, first.seedLabel?.length)
        assertNotEquals(first.seed, shuffleSeedFromBytes(bytes + 1).seed)
        assertTrue(first.seed != null)
    }

    @Test
    fun imageSaltChangesTheSeedAndRulesRespectTheirDeclaredOrder() {
        val image = ByteArray(32) { it.toByte() }
        assertNotEquals(shuffleSeedFromBytes(image, "one").seed, shuffleSeedFromBytes(image, "two").seed)
        val mixed = listOf(
            track(1, artist = "B", album = "A"),
            track(2, artist = "A", album = "Z"),
            track(3, artist = "A", album = "A"),
        )
        val shuffled = shuffledQueue(
            mixed,
            -1,
            ShuffleProfile(rules = listOf(ShuffleGrouping.ARTIST, ShuffleGrouping.ALBUM), seed = 3),
        )
        assertEquals(listOf("A", "A", "B"), shuffled.map { it.artist })
        assertEquals(listOf("A", "Z", "A"), shuffled.map { it.album })
    }

    @Test
    fun missingMetadataPolicyPlacesUnknownGroupsFirstOrLast() {
        val mixed = listOf(track(1, artist = ""), track(2, artist = "Alpha"))
        val first = shuffledQueue(mixed, -1, ShuffleProfile(
            rules = listOf(ShuffleGrouping.ARTIST),
            missingValue = MissingShuffleValue.FIRST,
            seed = 1,
        ))
        val last = shuffledQueue(mixed, -1, ShuffleProfile(
            rules = listOf(ShuffleGrouping.ARTIST),
            missingValue = MissingShuffleValue.LAST,
            seed = 1,
        ))
        assertEquals("", first.first().artist)
        assertEquals("Alpha", last.first().artist)
    }

    @Test
    fun rainbowOrdersCachedArtworkHueAndLeavesMissingArtworkLast() {
        val mixed = listOf(
            track(1, artworkHue = 240),
            track(2, artworkHue = null),
            track(3, artworkHue = 20),
        )
        val shuffled = shuffledQueue(
            mixed,
            -1,
            ShuffleProfile(rules = listOf(ShuffleGrouping.RAINBOW), seed = 1),
        )
        assertEquals(listOf(20, 240, null), shuffled.map { it.artworkHue })
    }

    @Test
    fun tinyQueuesAndMissingCurrentAreSafe() {
        assertEquals(emptyList(), shuffledQueue(emptyList(), -1, ShuffleProfile()))
        val single = listOf(track(1))
        assertEquals(single, shuffledQueue(single, 0, ShuffleProfile()))
        val noCurrent = shuffledQueue(queue, currentIndex = -1, profile = ShuffleProfile(seed = 1))
        assertEquals(queue.toSet(), noCurrent.toSet())
    }
}
