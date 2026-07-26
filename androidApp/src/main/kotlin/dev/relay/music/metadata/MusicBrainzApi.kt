package dev.relay.music.metadata

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import dev.relay.music.model.MetadataCandidate
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class MusicBrainzApi : AutoCloseable {
    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            connectTimeoutMillis = REQUEST_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    private val cache = mutableMapOf<String, List<MetadataCandidate>>()
    private val requestMutex = Mutex()
    private var lastRequestAtMs = 0L

    suspend fun search(title: String, artist: String, album: String?): Result<List<MetadataCandidate>> {
        val cleanTitle = title.trim().take(MAX_TEXT_LENGTH)
        val cleanArtist = artist.trim().take(MAX_TEXT_LENGTH)
        val cleanAlbum = album?.trim()?.take(MAX_TEXT_LENGTH).orEmpty()
        if (cleanTitle.isEmpty()) return Result.failure(IllegalArgumentException("Enter a track title to search."))
        val cacheKey = "$cleanTitle\u0000$cleanArtist\u0000$cleanAlbum".lowercase()
        cache[cacheKey]?.let { return Result.success(it) }

        return requestMutex.withLock {
            cache[cacheKey]?.let { return@withLock Result.success(it) }
            val waitMs = MIN_REQUEST_INTERVAL_MS - (System.currentTimeMillis() - lastRequestAtMs)
            if (waitMs > 0) delay(waitMs)
            lastRequestAtMs = System.currentTimeMillis()
            runCatching {
                val query = buildString {
                    append("recording:\"").append(cleanTitle.replace("\"", "")).append('"')
                    if (cleanArtist.isNotEmpty()) append(" AND artist:\"").append(cleanArtist.replace("\"", "")).append('"')
                    if (cleanAlbum.isNotEmpty()) append(" AND release:\"").append(cleanAlbum.replace("\"", "")).append('"')
                }
                val response = client.get(API_URL) {
                    parameter("query", query)
                    parameter("fmt", "json")
                    parameter("inc", "releases+artist-credits+media")
                    parameter("limit", MAX_RESULTS)
                    headers { append(HttpHeaders.UserAgent, USER_AGENT) }
                }
                check(response.status.isSuccess()) { "MusicBrainz is unavailable." }
                response.body<JsonObject>().candidates()
            }.map { candidates ->
                cache[cacheKey] = candidates
                candidates
            }
        }
    }

    override fun close() = client.close()

    private fun JsonObject.candidates(): List<MetadataCandidate> =
        (get("recordings") as? JsonArray).orEmpty().mapNotNull { value ->
            val recording = value as? JsonObject ?: return@mapNotNull null
            val title = recording.string("title") ?: return@mapNotNull null
            val artist = recording.artistName() ?: return@mapNotNull null
            val recordingId = recording.string("id") ?: return@mapNotNull null
            val release = (recording["releases"] as? JsonArray)?.firstOrNull() as? JsonObject
            val releaseId = release?.string("id")
            val releaseGroupId = (release?.get("release-group") as? JsonObject)?.string("id")
            val medium = (release?.get("media") as? JsonArray)?.firstOrNull() as? JsonObject
            val releaseTrack = (medium?.get("tracks") as? JsonArray)?.firstOrNull() as? JsonObject
            MetadataCandidate(
                title = title,
                artist = artist,
                album = release?.string("title"),
                albumArtist = release?.artistName(),
                recordingId = recordingId,
                releaseId = releaseId,
                artworkUri = releaseGroupId?.let { "https://coverartarchive.org/release-group/$it/front-250" }
                    ?: releaseId?.let { "https://coverartarchive.org/release/$it/front-250" },
                trackNumber = releaseTrack?.string("number")?.toIntOrNull(),
                discNumber = medium?.string("position")?.toIntOrNull(),
            )
        }.distinctBy { it.recordingId to it.releaseId }

    private fun JsonObject.artistName(): String? =
        (get("artist-credit") as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.string("name") ?: ((it as? JsonObject)?.get("artist") as? JsonObject)?.string("name") }
            ?.joinToString("")
            ?.takeIf { it.isNotBlank() }

    private fun JsonObject.string(name: String): String? =
        get(name)?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TEXT_LENGTH)

    private companion object {
        const val API_URL = "https://musicbrainz.org/ws/2/recording/"
        const val REQUEST_TIMEOUT_MS = 15_000L
        const val MIN_REQUEST_INTERVAL_MS = 1_000L
        const val MAX_RESULTS = 5
        const val MAX_TEXT_LENGTH = 512
        const val USER_AGENT = "Relay/0.1 (personal music player)"
    }
}

/** Android-only cache for provider artwork; original audio and image URLs are never modified. */
object ArtworkCache {
    fun fetch(context: Context, url: String): String {
        require(URL(url).protocol.equals("https", ignoreCase = true)) { "Artwork must use HTTPS." }
        val file = File(context.cacheDir, "relay-artwork/remote-${sha256(url)}.img")
        if (file.isFile && file.length() > 0) return Uri.fromFile(file).toString()

        val (bytes, contentType) = readHttps(URL(url))
        require(contentType?.startsWith("image/", ignoreCase = true) == true) { "Artwork is not an image." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= MAX_PIXELS) {
            "Artwork dimensions are not supported."
        }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.outputStream().use { it.write(bytes) }
        check(temporary.renameTo(file)) { "Could not cache artwork." }
        return Uri.fromFile(file).toString()
    }

    private fun readHttps(url: URL, redirectsRemaining: Int = MAX_REDIRECTS): Pair<ByteArray, String?> {
        require(url.protocol.equals("https", ignoreCase = true)) { "Artwork redirect must use HTTPS." }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = REQUEST_TIMEOUT_MS.toInt()
            readTimeout = REQUEST_TIMEOUT_MS.toInt()
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "image/*")
        }
        return try {
            when (val status = connection.responseCode) {
                in 300..399 -> {
                    require(redirectsRemaining > 0) { "Artwork redirected too many times." }
                    val location = connection.getHeaderField("Location") ?: error("Artwork redirect is missing a destination.")
                    readHttps(URL(url, location), redirectsRemaining - 1)
                }
                in 200..299 -> {
                    val length = connection.contentLength.toLong()
                    require(length <= MAX_BYTES) { "Artwork is too large." }
                    connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream(length.coerceAtLeast(0).toInt())
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= MAX_BYTES) { "Artwork is too large." }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray() to connection.contentType
                    }
                }
                else -> error("Artwork is unavailable ($status).")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private const val REQUEST_TIMEOUT_MS = 15_000L
    private const val MAX_REDIRECTS = 8
    private const val MAX_BYTES = 5 * 1024 * 1024
    private const val MAX_PIXELS = 16_000_000L
    private const val USER_AGENT = "Relay/0.1 (personal music player)"
}
