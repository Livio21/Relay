package dev.relay.music.lastfm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class LastFmHistoryParserTest {
    @Test
    fun ignoresNowPlayingAndParsesAttributedScrobbles() {
        val page = parseRecentTracks(
            Json.parseToJsonElement(
                """{"recenttracks":{"@attr":{"totalPages":"2"},"track":[
                    {"@attr":{"nowplaying":"true"},"artist":{"#text":"Pearl Jam"},"name":"Alive"},
                    {"artist":{"#text":"Pearl Jam"},"name":"Black","album":{"#text":"Ten"},"mbid":"recording-1","date":{"uts":"1000"}}
                ]}}""",
            ).jsonObject,
        )

        assertEquals(2, page.totalPages)
        assertEquals(1, page.tracks.size)
        assertEquals("Pearl Jam", page.tracks.single().artist)
        assertEquals(1_000_000L, page.tracks.single().playedAtEpochMs)
    }
}
