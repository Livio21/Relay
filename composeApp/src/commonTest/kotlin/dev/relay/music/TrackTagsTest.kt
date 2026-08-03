package dev.relay.music

import dev.relay.music.model.NumberTag
import dev.relay.music.model.TagOrigin
import dev.relay.music.model.TextTag
import dev.relay.music.model.TrackTagField
import dev.relay.music.model.TrackTags
import dev.relay.music.model.decodeTrackTags
import dev.relay.music.model.encodeTrackTags
import dev.relay.music.model.mergeRefresh
import dev.relay.music.model.normalized
import dev.relay.music.model.validate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackTagsTest {
    @Test
    fun normalizationBoundsAndDeduplicatesProviderData() {
        val tags = TrackTags(
            genres = listOf(TextTag(" Rock ", TagOrigin.PROVIDER), TextTag("rock", TagOrigin.SOURCE)),
            releaseDate = TextTag("not a date", TagOrigin.PROVIDER),
            bpm = NumberTag(900f, TagOrigin.PROVIDER),
        ).normalized()

        assertEquals(listOf("Rock"), tags.genres.map { it.value })
        assertNull(tags.releaseDate)
        assertNull(tags.bpm)
        assertNull(tags.validate())
    }

    @Test
    fun refreshPreservesReviewedValuesAndIntentionalEmptyFields() {
        val current = TrackTags(
            genres = listOf(TextTag("Post-punk", TagOrigin.USER)),
            moods = emptyList(),
            userControlledFields = setOf(TrackTagField.GENRES, TrackTagField.MOODS),
        )
        val refreshed = current.mergeRefresh(
            TrackTags(
                genres = listOf(TextTag("Rock", TagOrigin.PROVIDER)),
                moods = listOf(TextTag("Energetic", TagOrigin.PROVIDER)),
                instruments = listOf(TextTag("Guitar", TagOrigin.PROVIDER)),
            ),
        )

        assertEquals(listOf("Post-punk"), refreshed.genres.map { it.value })
        assertEquals(emptyList(), refreshed.moods)
        assertEquals(listOf("Guitar"), refreshed.instruments.map { it.value })
        assertEquals(refreshed, decodeTrackTags(encodeTrackTags(refreshed)))
    }
}
