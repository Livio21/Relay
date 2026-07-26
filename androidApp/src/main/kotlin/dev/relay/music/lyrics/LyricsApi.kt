package dev.relay.music.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class LyricsApi : AutoCloseable {
    private val client = HttpClient(Android) {
        install(HttpTimeout) { requestTimeoutMillis = 15_000L }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun fetch(artist: String, title: String): Result<String> = runCatching {
        val response = client.get(API_URL) {
            // Slash is part of artist/title text, not a path separator (for example AC/DC).
            url { appendPathSegments(artist.trim(), title.trim(), encodeSlash = true) }
            headers { append(HttpHeaders.UserAgent, "Relay/0.1 (personal music player)") }
        }
        check(response.status.isSuccess()) { "Lyrics provider could not find this track." }
        response.body<JsonObject>()["lyrics"]?.jsonPrimitive?.content?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_LYRICS_LENGTH }
            ?: error("Lyrics provider returned no usable lyrics.")
    }

    override fun close() = client.close()

    private companion object {
        const val API_URL = "https://api.lyrics.ovh/v1"
        const val MAX_LYRICS_LENGTH = 50_000
    }
}
