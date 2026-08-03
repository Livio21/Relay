package dev.relay.music

import dev.relay.music.model.DisplayTrackMetadata
import dev.relay.music.model.MetadataHealth
import dev.relay.music.model.MetadataHealthSignals
import dev.relay.music.model.MetadataIssue
import dev.relay.music.model.MetadataOverride
import dev.relay.music.model.Track
import dev.relay.music.model.TrackMetadataLayer
import dev.relay.music.model.metadataHealth
import dev.relay.music.model.metadataIssues
import dev.relay.music.model.resolveEffectiveMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetadataTest {
    private val embedded = Track(
        id = "track-id",
        sourceId = "local",
        playbackUri = "content://music/track-id",
        title = "Embedded title",
        artist = "Embedded artist",
        album = "Embedded album",
        albumArtist = "Embedded album artist",
        releaseDate = "1991",
        durationMs = 180_000,
        artworkUri = "content://art/embedded",
        artworkHue = 20,
        trackNumber = 1,
        discNumber = 1,
        musicBrainzId = "embedded-recording",
        sourceRevision = "source-revision",
        musicBrainzReleaseId = "embedded-release",
    )

    @Test
    fun resolverUsesOverrideThenTrustedThenEmbeddedWithoutChangingIdentity() {
        val effective = embedded.resolveEffectiveMetadata(
            userOverride = MetadataOverride(
                title = "User title",
                albumArtist = "User album artist",
                artworkUri = "content://art/user",
                trackNumber = 7,
                musicBrainzReleaseId = "user-release",
            ),
            trustedSource = TrackMetadataLayer(
                title = "Trusted title",
                artist = "Trusted artist",
                albumArtist = "Trusted album artist",
                releaseDate = "1992",
                durationMs = 181_000,
                artworkUri = "https://example.test/trusted.jpg",
                artworkHue = 200,
                trackNumber = 4,
                discNumber = 2,
                musicBrainzId = "trusted-recording",
                musicBrainzReleaseId = "trusted-release",
            ),
        )

        assertEquals("track-id", effective.track.id)
        assertEquals("local", effective.track.sourceId)
        assertEquals("content://music/track-id", effective.track.playbackUri)
        assertEquals("source-revision", effective.track.sourceRevision)
        assertEquals("User title", effective.track.title)
        assertEquals("Trusted artist", effective.track.artist)
        assertEquals("Embedded album", effective.track.album)
        assertEquals("User album artist", effective.track.albumArtist)
        assertEquals("1992", effective.track.releaseDate)
        assertEquals(181_000L, effective.track.durationMs)
        assertEquals("content://art/user", effective.track.artworkUri)
        assertNull(effective.track.artworkHue)
        assertEquals(7, effective.track.trackNumber)
        assertEquals(2, effective.track.discNumber)
        assertEquals("trusted-recording", effective.track.musicBrainzId)
        assertEquals("user-release", effective.track.musicBrainzReleaseId)
    }

    @Test
    fun displayFallbacksNeverBecomeCanonicalOrTrackerMetadata() {
        val effective = embedded.copy(title = "Unknown title", artist = "", album = null)
            .resolveEffectiveMetadata(
                displayFallback = DisplayTrackMetadata("DISPLAY TITLE", "DISPLAY ARTIST", "DISPLAY ALBUM"),
            )

        assertEquals("Unknown title", effective.track.title)
        assertEquals("", effective.track.artist)
        assertNull(effective.track.album)
        assertEquals("DISPLAY TITLE", effective.display.title)
        assertEquals("DISPLAY ARTIST", effective.display.artist)
        assertEquals("DISPLAY ALBUM", effective.display.album)
        assertFalse(effective.trackerEligible)
        assertTrue(embedded.resolveEffectiveMetadata().trackerEligible)
    }

    @Test
    fun healthUsesOnlyExplicitConservativeSignals() {
        val effective = embedded.copy(albumArtist = null).resolveEffectiveMetadata()
        val issues = effective.metadataIssues(
            MetadataHealthSignals(
                artworkReadable = false,
                hasAlbumGroupConflict = true,
                hasUnreviewedProviderSuggestion = true,
            ),
        )

        assertEquals(
            setOf(
                MetadataIssue.ALBUM_ARTIST,
                MetadataIssue.ARTWORK_UNREADABLE,
                MetadataIssue.ALBUM_GROUP_CONFLICT,
                MetadataIssue.UNREVIEWED_PROVIDER_SUGGESTION,
            ),
            issues,
        )
        assertEquals(MetadataHealth.NEEDS_REVIEW, effective.metadataHealth(MetadataHealthSignals(artworkReadable = false)))
    }

    @Test
    fun missingAlbumDoesNotAlsoClaimItsAlbumArtistOrUnreadableArtwork() {
        val effective = embedded.copy(album = null, albumArtist = null, artworkUri = null).resolveEffectiveMetadata()

        assertEquals(
            setOf(MetadataIssue.ALBUM, MetadataIssue.ARTWORK),
            effective.metadataIssues(MetadataHealthSignals(artworkReadable = false)),
        )
    }
}
