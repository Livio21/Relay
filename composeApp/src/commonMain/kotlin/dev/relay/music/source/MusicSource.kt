package dev.relay.music.source

import dev.relay.music.model.Track

interface MusicSource {
    val id: String
    val displayName: String

    suspend fun tracks(): List<Track>
}
