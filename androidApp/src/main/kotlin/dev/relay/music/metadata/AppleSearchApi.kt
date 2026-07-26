package dev.relay.music.metadata

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Cover-art fallback only; MusicBrainz remains the source of canonical metadata and IDs. */
class AppleSearchApi : AutoCloseable {
    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            connectTimeoutMillis = REQUEST_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun artwork(title: String, artist: String, album: String?): Result<String?> = runCatching {
        require(title.isNotBlank()) { "Enter a track title to search." }
        val response = client.get(API_URL) {
            parameter("term", listOf(title, artist, album).filterNotNull().filter { it.isNotBlank() }.joinToString(" "))
            parameter("entity", "song")
            parameter("limit", 5)
            headers { append(HttpHeaders.UserAgent, USER_AGENT) }
        }
        check(response.status.isSuccess()) { "Apple artwork search is unavailable." }
        val results = response.body<JsonObject>()["results"] as? JsonArray
        val matches = results.orEmpty().mapNotNull { value ->
            (value as? JsonObject)?.string("artworkUrl100")?.replace("100x100bb", "600x600bb")
        }
        matches.firstOrNull()
    }

    override fun close() = client.close()

    private fun JsonObject.string(name: String): String? =
        get(name)?.jsonPrimitive?.content?.trim()?.takeIf { it.startsWith("https://") }

    private companion object {
        const val API_URL = "https://itunes.apple.com/search"
        const val REQUEST_TIMEOUT_MS = 15_000L
        const val USER_AGENT = "Relay/0.1 (personal music player)"
    }
}
