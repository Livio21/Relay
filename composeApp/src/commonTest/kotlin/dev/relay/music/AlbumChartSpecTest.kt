package dev.relay.music

import dev.relay.music.model.AlbumChartSpec
import dev.relay.music.model.InsightsRange
import dev.relay.music.model.ListeningEvent
import dev.relay.music.model.albumChartEntries
import kotlin.test.Test
import kotlin.test.assertEquals

class AlbumChartSpecTest {
    @Test
    fun sameSpecAndEventsProduceTheSameAlbumRanking() {
        val spec = AlbumChartSpec("chart_1", InsightsRange.ALL, limit = 2, createdAtEpochMs = 1)
        val events = listOf(
            ListeningEvent("local", "1", 100, title = "A", album = "Ten"),
            ListeningEvent("local", "2", 200, title = "B", album = "Ten"),
            ListeningEvent("local", "3", 300, title = "C", album = "Vs."),
        )
        assertEquals(listOf("Ten", "Vs."), albumChartEntries(events, spec, 1_000).map { it.label })
    }
}
