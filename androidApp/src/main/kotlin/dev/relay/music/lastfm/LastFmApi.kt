package dev.relay.music.lastfm

import dev.relay.music.lastfm.lastFmSignature
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

data class LastFmSession(
    val username: String,
    val key: String,
)

sealed interface LastFmResult<out T> {
    data class Success<T>(val value: T) : LastFmResult<T>
    data class Failure(val kind: Kind, val message: String) : LastFmResult<Nothing>

    enum class Kind {
        TEMPORARY,
        INVALID_SESSION,
        PERMANENT,
    }
}

class LastFmApi(
    private val apiKey: String,
    private val sharedSecret: String,
) : AutoCloseable {
    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            connectTimeoutMillis = REQUEST_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && sharedSecret.isNotBlank()

    suspend fun requestToken(): LastFmResult<String> = request(
        method = "auth.getToken",
        parameters = emptyMap(),
    ).map { response ->
        response.string("token")?.let { LastFmResult.Success(it) } ?: LastFmResult.Failure(
            LastFmResult.Kind.PERMANENT,
            "Last.fm did not return an authorization token.",
        )
    }

    fun authorizationUrl(token: String): String =
        "https://www.last.fm/api/auth/?api_key=$apiKey&token=$token"

    suspend fun createSession(token: String): LastFmResult<LastFmSession> = request(
        method = "auth.getSession",
        parameters = mapOf("token" to token),
    ).map { response ->
        val session = response["session"] as? JsonObject
            ?: return LastFmResult.Failure(
                LastFmResult.Kind.PERMANENT,
                "Last.fm did not return a session.",
            )
        val username = session.string("name")
        val key = session.string("key")
        if (username == null || key == null) {
            LastFmResult.Failure(LastFmResult.Kind.PERMANENT, "Last.fm returned an incomplete session.")
        } else {
            LastFmResult.Success(LastFmSession(username, key))
        }
    }

    suspend fun updateNowPlaying(
        sessionKey: String,
        artist: String,
        track: String,
        album: String?,
        durationMs: Long,
    ): LastFmResult<Unit> = request(
        method = "track.updateNowPlaying",
        parameters = buildMap {
            put("sk", sessionKey)
            put("artist", artist)
            put("track", track)
            album?.takeIf { it.isNotBlank() }?.let { put("album", it) }
            durationMs.takeIf { it > 0 }?.let { put("duration", (it / 1_000).toString()) }
        },
    ).map { LastFmResult.Success(Unit) }

    suspend fun scrobble(sessionKey: String, scrobble: PendingScrobble): LastFmResult<Unit> = request(
        method = "track.scrobble",
        parameters = buildMap {
            put("sk", sessionKey)
            put("artist", scrobble.artist)
            put("track", scrobble.track)
            put("timestamp", scrobble.startedAtEpochSeconds.toString())
            put("duration", (scrobble.durationMs / 1_000).toString())
            scrobble.album?.takeIf { it.isNotBlank() }?.let { put("album", it) }
        },
    ).map { LastFmResult.Success(Unit) }

    override fun close() {
        client.close()
    }

    private suspend fun request(
        method: String,
        parameters: Map<String, String>,
    ): LastFmResult<JsonObject> {
        if (!isConfigured) {
            return LastFmResult.Failure(LastFmResult.Kind.PERMANENT, "Last.fm API credentials are not configured.")
        }
        val signedParameters = parameters + mapOf(
            "api_key" to apiKey,
            "method" to method,
        )
        val requestParameters = signedParameters + mapOf(
            "api_sig" to lastFmSignature(signedParameters, sharedSecret),
            "format" to "json",
        )

        return runCatching {
            client.post(API_URL) {
                contentType(ContentType.Application.FormUrlEncoded)
                headers { append(HttpHeaders.UserAgent, USER_AGENT) }
                setBody(
                    FormDataContent(
                        Parameters.build {
                            requestParameters.forEach { (key, value) -> append(key, value) }
                        },
                    ),
                )
            }
        }.fold(
            onFailure = {
                LastFmResult.Failure(LastFmResult.Kind.TEMPORARY, "Could not reach Last.fm.")
            },
            onSuccess = { response ->
                val body = runCatching { response.body<JsonObject>() }.getOrNull()
                val errorCode = body?.string("error")?.toIntOrNull()
                if (errorCode != null) {
                    LastFmResult.Failure(errorKind(errorCode), body.string("message") ?: "Last.fm request failed.")
                } else if (!response.status.isSuccess() || body == null) {
                    LastFmResult.Failure(LastFmResult.Kind.TEMPORARY, "Last.fm is unavailable.")
                } else {
                    LastFmResult.Success(body)
                }
            },
        )
    }

    private fun errorKind(code: Int): LastFmResult.Kind = when (code) {
        9 -> LastFmResult.Kind.INVALID_SESSION
        11, 16, 29 -> LastFmResult.Kind.TEMPORARY
        else -> LastFmResult.Kind.PERMANENT
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    private inline fun <T, R> LastFmResult<T>.map(transform: (T) -> LastFmResult<R>): LastFmResult<R> =
        when (this) {
            is LastFmResult.Success -> transform(value)
            is LastFmResult.Failure -> this
        }

    private companion object {
        const val API_URL = "https://ws.audioscrobbler.com/2.0/"
        const val REQUEST_TIMEOUT_MS = 15_000L
        const val USER_AGENT = "Relay/0.1 (personal music player)"
    }
}
