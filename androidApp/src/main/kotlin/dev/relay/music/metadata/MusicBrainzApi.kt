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
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class MetadataSearchTarget(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val recordingId: String?,
    val releaseId: String?,
)

internal data class RankedMetadataCandidate(
    val candidate: MetadataCandidate,
    val score: Int,
)

internal data class MetadataCandidateRanking(
    val ranked: List<RankedMetadataCandidate>,
    /** Close or weak matches need an especially visible review warning. All results remain review-only. */
    val reviewRequired: Boolean,
) {
    val candidates: List<MetadataCandidate>
        get() = ranked.map(RankedMetadataCandidate::candidate)
}

class MusicBrainzApi : AutoCloseable {
    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            connectTimeoutMillis = REQUEST_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    private val cache = ConcurrentHashMap<String, MetadataCandidateRanking>()
    private val requestMutex = Mutex()
    private var lastRequestAtMonotonicMs = 0L

    internal suspend fun search(
        target: MetadataSearchTarget,
        refresh: Boolean = false,
    ): Result<MetadataCandidateRanking> {
        val cleanTarget = target.sanitized()
        if (cleanTarget.title.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a track title to search."))
        }
        val cacheKey = metadataSearchCacheKey(cleanTarget)
        if (!refresh) cache[cacheKey]?.let { return Result.success(it) }

        return requestMutex.withLock {
            if (!refresh) cache[cacheKey]?.let { return@withLock Result.success(it) }
            runCatching { requestCandidates(cleanTarget) }.map { ranking ->
                cache[cacheKey] = ranking
                ranking
            }
        }
    }

    private suspend fun requestCandidates(target: MetadataSearchTarget): MetadataCandidateRanking {
        val query = buildString {
            append("recording:\"").append(target.title.escapedQueryValue()).append('"')
            if (target.artist.isNotEmpty()) append(" AND artist:\"").append(target.artist.escapedQueryValue()).append('"')
            if (!target.album.isNullOrEmpty()) append(" AND release:\"").append(target.album.escapedQueryValue()).append('"')
        }
        var serviceRetryCount = 0
        while (true) {
            awaitProviderTurn()
            val response = client.get(API_URL) {
                parameter("query", query)
                parameter("fmt", "json")
                parameter("inc", "releases+artist-credits+media")
                parameter("limit", MAX_RESULTS)
                headers { append(HttpHeaders.UserAgent, USER_AGENT) }
            }
            if (response.status.value == HTTP_SERVICE_UNAVAILABLE && serviceRetryCount < MAX_SERVICE_RETRIES) {
                val retryDelay = parseRetryAfterMillis(
                    response.headers[HttpHeaders.RetryAfter],
                    nowEpochMs = System.currentTimeMillis(),
                    maxDelayMs = MAX_PROVIDER_RETRY_DELAY_MS,
                ) ?: DEFAULT_SERVICE_RETRY_MS
                serviceRetryCount++
                delay(retryDelay)
                continue
            }
            check(response.status.isSuccess()) {
                if (response.status.value == HTTP_SERVICE_UNAVAILABLE) {
                    "MusicBrainz is temporarily unavailable. Try again shortly."
                } else {
                    "MusicBrainz is unavailable (${response.status.value})."
                }
            }
            return rankMetadataCandidates(target, response.body<JsonObject>().candidates())
        }
    }

    private suspend fun awaitProviderTurn() {
        val now = monotonicMs()
        val waitMs = MIN_REQUEST_INTERVAL_MS - (now - lastRequestAtMonotonicMs)
        if (waitMs > 0) delay(waitMs)
        lastRequestAtMonotonicMs = monotonicMs()
    }

    override fun close() = client.close()

    private fun JsonObject.candidates(): List<MetadataCandidate> =
        (get("recordings") as? JsonArray).orEmpty()
            .flatMap { value -> (value as? JsonObject)?.releaseCandidates().orEmpty() }
            .distinctBy { candidate ->
                listOf(
                    candidate.recordingId,
                    candidate.releaseId.orEmpty(),
                    candidate.discNumber?.toString().orEmpty(),
                    candidate.trackNumber?.toString().orEmpty(),
                ).joinToString("\u0000")
            }

    private fun JsonObject.releaseCandidates(): List<MetadataCandidate> {
        val recordingTitle = string("title") ?: return emptyList()
        val recordingArtist = artistName() ?: return emptyList()
        val recordingId = string("id") ?: return emptyList()
        val recordingDurationMs = positiveLong("length")
        val releases = (get("releases") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        if (releases.isEmpty()) {
            return listOf(
                MetadataCandidate(
                    title = recordingTitle,
                    artist = recordingArtist,
                    album = null,
                    albumArtist = null,
                    recordingId = recordingId,
                    durationMs = recordingDurationMs,
                ),
            )
        }
        return releases.flatMap { release ->
            val releaseId = release.string("id")
            val releaseGroupId = (release["release-group"] as? JsonObject)?.string("id")
            val artworkUri = releaseGroupId?.let { "https://coverartarchive.org/release-group/$it/front-250" }
                ?: releaseId?.let { "https://coverartarchive.org/release/$it/front-250" }
            val media = (release["media"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val mediaOrNull: List<JsonObject?> = if (media.isEmpty()) listOf(null) else media
            mediaOrNull.flatMap { medium ->
                val matchingTracks = medium?.matchingTracks(recordingId, recordingTitle).orEmpty()
                val tracksOrNull: List<JsonObject?> = if (matchingTracks.isEmpty()) listOf(null) else matchingTracks
                tracksOrNull.map { releaseTrack ->
                    MetadataCandidate(
                        title = recordingTitle,
                        artist = recordingArtist,
                        album = release.string("title"),
                        albumArtist = release.artistName(),
                        recordingId = recordingId,
                        releaseId = releaseId,
                        artworkUri = artworkUri,
                        trackNumber = releaseTrack?.positiveInt("position") ?: releaseTrack?.numericText("number"),
                        discNumber = medium?.positiveInt("position"),
                        durationMs = releaseTrack?.positiveLong("length") ?: recordingDurationMs,
                    )
                }
            }
        }
    }

    private fun JsonObject.matchingTracks(recordingId: String, recordingTitle: String): List<JsonObject> {
        val tracks = (get("tracks") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val byIdentity = tracks.filter { track ->
            (track["recording"] as? JsonObject)?.string("id") == recordingId
        }
        if (byIdentity.isNotEmpty()) return byIdentity
        val normalizedTitle = normalizeMetadataText(recordingTitle)
        return tracks.filter { track -> normalizeMetadataText(track.string("title")) == normalizedTitle }
    }

    private fun JsonObject.artistName(): String? =
        (get("artist-credit") as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.string("name") ?: ((it as? JsonObject)?.get("artist") as? JsonObject)?.string("name") }
            ?.joinToString("")
            ?.takeIf { it.isNotBlank() }

    private fun JsonObject.string(name: String): String? =
        get(name)?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TEXT_LENGTH)

    private fun JsonObject.positiveLong(name: String): Long? =
        get(name)?.jsonPrimitive?.content?.toLongOrNull()?.takeIf { it > 0 }

    private fun JsonObject.positiveInt(name: String): Int? =
        get(name)?.jsonPrimitive?.content?.toIntOrNull()?.takeIf { it > 0 }

    private fun JsonObject.numericText(name: String): Int? =
        string(name)?.dropWhile { !it.isDigit() }?.takeWhile(Char::isDigit)?.toIntOrNull()?.takeIf { it > 0 }

    private fun MetadataSearchTarget.sanitized() = copy(
        title = title.trim().take(MAX_TEXT_LENGTH),
        artist = artist.trim().take(MAX_TEXT_LENGTH),
        album = album?.trim()?.take(MAX_TEXT_LENGTH)?.takeIf(String::isNotEmpty),
        durationMs = durationMs?.takeIf { it > 0 },
        trackNumber = trackNumber?.takeIf { it > 0 },
        discNumber = discNumber?.takeIf { it > 0 },
        recordingId = recordingId.normalizedMbid(),
        releaseId = releaseId.normalizedMbid(),
    )

    private fun String.escapedQueryValue(): String = replace("\\", "").replace("\"", "")

    private companion object {
        const val API_URL = "https://musicbrainz.org/ws/2/recording/"
        const val REQUEST_TIMEOUT_MS = 15_000L
        const val MIN_REQUEST_INTERVAL_MS = 1_000L
        const val MAX_RESULTS = 8
        const val MAX_TEXT_LENGTH = 512
        const val MAX_SERVICE_RETRIES = 1
        const val DEFAULT_SERVICE_RETRY_MS = 1_500L
        const val HTTP_SERVICE_UNAVAILABLE = 503
        const val USER_AGENT = "Relay/0.1 (personal music player)"
    }
}

internal fun rankMetadataCandidates(
    target: MetadataSearchTarget,
    candidates: List<MetadataCandidate>,
): MetadataCandidateRanking {
    val ranked = candidates.map { candidate ->
        RankedMetadataCandidate(candidate, metadataCandidateScore(target, candidate))
    }.sortedWith(
        compareByDescending<RankedMetadataCandidate> { it.score }
            .thenBy { normalizeMetadataText(it.candidate.title) }
            .thenBy { normalizeMetadataText(it.candidate.artist) }
            .thenBy { normalizeMetadataText(it.candidate.album) }
            .thenBy { it.candidate.recordingId.lowercase(Locale.ROOT) }
            .thenBy { it.candidate.releaseId.orEmpty().lowercase(Locale.ROOT) }
            .thenBy { it.candidate.discNumber ?: Int.MAX_VALUE }
            .thenBy { it.candidate.trackNumber ?: Int.MAX_VALUE },
    )
    val top = ranked.firstOrNull()
    val second = ranked.getOrNull(1)
    val confidenceFloor = if (normalizeMetadataText(target.artist).isEmpty()) TITLE_ONLY_CONFIDENCE_SCORE else TITLE_ARTIST_CONFIDENCE_SCORE
    val ambiguous = top != null && (
        top.score < confidenceFloor ||
            (second != null && top.score - second.score < MIN_CONFIDENCE_MARGIN)
        )
    return MetadataCandidateRanking(ranked, reviewRequired = ambiguous)
}

internal fun metadataCandidateScore(target: MetadataSearchTarget, candidate: MetadataCandidate): Int {
    var score = 0
    score += normalizedTextSimilarity(target.title, candidate.title) * TITLE_WEIGHT
    score += normalizedTextSimilarity(target.artist, candidate.artist) * ARTIST_WEIGHT
    if (!target.album.isNullOrBlank()) score += normalizedTextSimilarity(target.album, candidate.album) * ALBUM_WEIGHT

    if (!target.recordingId.isNullOrBlank()) {
        score += if (target.recordingId.equals(candidate.recordingId, ignoreCase = true)) RECORDING_ID_MATCH else RECORDING_ID_MISMATCH
    }
    if (!target.releaseId.isNullOrBlank()) {
        score += if (target.releaseId.equals(candidate.releaseId, ignoreCase = true)) RELEASE_ID_MATCH else RELEASE_ID_MISMATCH
    }
    score += durationScore(target.durationMs, candidate.durationMs)
    score += ordinalScore(target.trackNumber, candidate.trackNumber, TRACK_MATCH, TRACK_MISMATCH)
    score += ordinalScore(target.discNumber, candidate.discNumber, DISC_MATCH, DISC_MISMATCH)
    return score
}

internal fun metadataSearchCacheKey(target: MetadataSearchTarget): String = listOf(
    "v4",
    normalizeMetadataText(target.title),
    normalizeMetadataText(target.artist),
    normalizeMetadataText(target.album),
    target.durationMs?.takeIf { it > 0 }?.toString().orEmpty(),
    target.trackNumber?.takeIf { it > 0 }?.toString().orEmpty(),
    target.discNumber?.takeIf { it > 0 }?.toString().orEmpty(),
    target.recordingId.normalizedMbid().orEmpty(),
    target.releaseId.normalizedMbid().orEmpty(),
).joinToString("\u0000")

internal fun normalizeMetadataText(value: String?): String {
    if (value.isNullOrBlank()) return ""
    val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
    return buildString(decomposed.length) {
        var needsSpace = false
        decomposed.forEach { character ->
            when {
                Character.getType(character) == Character.NON_SPACING_MARK.toInt() -> Unit
                character.isLetterOrDigit() -> {
                    if (needsSpace && isNotEmpty()) append(' ')
                    append(character.lowercaseChar())
                    needsSpace = false
                }
                else -> needsSpace = true
            }
        }
    }.trim()
}

private fun normalizedTextSimilarity(first: String?, second: String?): Int {
    val left = normalizeMetadataText(first)
    val right = normalizeMetadataText(second)
    if (left.isEmpty() || right.isEmpty()) return 0
    if (left == right) return 100
    val longest = maxOf(left.length, right.length)
    val editScore = ((longest - levenshteinDistance(left, right)) * 100 / longest).coerceIn(0, 99)
    val leftTokens = left.split(' ').filterTo(linkedSetOf(), String::isNotEmpty)
    val rightTokens = right.split(' ').filterTo(linkedSetOf(), String::isNotEmpty)
    val union = leftTokens union rightTokens
    val tokenScore = if (union.isEmpty()) 0 else (leftTokens.intersect(rightTokens).size * 100 / union.size).coerceAtMost(99)
    return maxOf(editScore, tokenScore)
}

private fun levenshteinDistance(first: String, second: String): Int {
    var previous = IntArray(second.length + 1) { it }
    var current = IntArray(second.length + 1)
    first.forEachIndexed { firstIndex, firstCharacter ->
        current[0] = firstIndex + 1
        second.forEachIndexed { secondIndex, secondCharacter ->
            current[secondIndex + 1] = minOf(
                current[secondIndex] + 1,
                previous[secondIndex + 1] + 1,
                previous[secondIndex] + if (firstCharacter == secondCharacter) 0 else 1,
            )
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[second.length]
}

private fun durationScore(expected: Long?, actual: Long?): Int {
    if (expected == null || expected <= 0 || actual == null || actual <= 0) return 0
    return when (abs(expected - actual)) {
        in 0..2_000 -> DURATION_CLOSE
        in 2_001..5_000 -> DURATION_NEAR
        in 5_001..10_000 -> DURATION_LOOSE
        else -> DURATION_MISMATCH
    }
}

private fun ordinalScore(expected: Int?, actual: Int?, match: Int, mismatch: Int): Int = when {
    expected == null || expected <= 0 || actual == null || actual <= 0 -> 0
    expected == actual -> match
    else -> mismatch
}

internal fun parseRetryAfterMillis(
    value: String?,
    nowEpochMs: Long,
    maxDelayMs: Long = MAX_PROVIDER_RETRY_DELAY_MS,
): Long? {
    require(maxDelayMs >= 0)
    val clean = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    clean.toLongOrNull()?.takeIf { it >= 0 }?.let { seconds ->
        val boundedSeconds = seconds.coerceAtMost(maxDelayMs / 1_000 + 1)
        return (boundedSeconds * 1_000).coerceAtMost(maxDelayMs)
    }
    val date = runCatching {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("GMT")
        }.parse(clean)?.time
    }.getOrNull() ?: return null
    return (date - nowEpochMs).coerceAtLeast(0).coerceAtMost(maxDelayMs)
}

internal const val MAX_PROVIDER_RETRY_DELAY_MS = 10_000L

private const val TITLE_WEIGHT = 10
private const val ARTIST_WEIGHT = 8
private const val ALBUM_WEIGHT = 4
private const val RECORDING_ID_MATCH = 5_000
private const val RECORDING_ID_MISMATCH = -5_000
private const val RELEASE_ID_MATCH = 3_000
private const val RELEASE_ID_MISMATCH = -1_500
private const val DURATION_CLOSE = 300
private const val DURATION_NEAR = 200
private const val DURATION_LOOSE = 75
private const val DURATION_MISMATCH = -250
private const val TRACK_MATCH = 160
private const val TRACK_MISMATCH = -120
private const val DISC_MATCH = 120
private const val DISC_MISMATCH = -90
private const val TITLE_ONLY_CONFIDENCE_SCORE = 750
private const val TITLE_ARTIST_CONFIDENCE_SCORE = 1_450
private const val MIN_CONFIDENCE_MARGIN = 175

private fun String?.normalizedMbid(): String? = this?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotEmpty)

private fun monotonicMs(): Long = System.nanoTime() / 1_000_000L

/** Android-only cache for provider artwork; original audio and image URLs are never modified. */
object ArtworkCache {
    private val requests = ArtworkRequestDeduplicator()

    fun fetch(context: Context, url: String): String {
        val normalizedUrl = normalizedArtworkUrl(url)
        val cacheKey = artworkCacheKey(normalizedUrl)
        return requests.run(cacheKey) { fetchOnce(context, normalizedUrl, cacheKey) }
    }

    private fun fetchOnce(context: Context, url: String, cacheKey: String): String {
        val file = File(context.cacheDir, "relay-artwork/remote-$cacheKey.img")
        if (isValidCachedArtwork(file)) return Uri.fromFile(file).toString()
        if (file.exists()) file.delete()

        val (bytes, contentType) = readHttps(URL(url))
        require(contentType?.startsWith("image/", ignoreCase = true) == true) { "Artwork is not an image." }
        require(isValidArtworkBytes(bytes)) { "Artwork dimensions are not supported." }
        val directory = requireNotNull(file.parentFile)
        check(directory.isDirectory || directory.mkdirs()) { "Could not prepare artwork cache." }
        val temporary = File.createTempFile("${file.name}-", ".tmp", directory)
        try {
            temporary.outputStream().use { it.write(bytes) }
            require(isValidCachedArtwork(temporary)) { "Artwork cache validation failed." }
            check(!file.exists() || file.delete()) { "Could not replace cached artwork." }
            check(temporary.renameTo(file)) { "Could not cache artwork." }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
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
                    val length = connection.contentLengthLong
                    require(length <= MAX_BYTES) { "Artwork is too large." }
                    connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream(length.coerceIn(0, MAX_BYTES.toLong()).toInt())
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

    private fun isValidArtworkBytes(bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return validArtworkBounds(bounds)
    }

    private fun isValidCachedArtwork(file: File): Boolean {
        if (!file.isFile || file.length() !in 1..MAX_BYTES.toLong()) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        return validArtworkBounds(bounds)
    }

    private fun validArtworkBounds(bounds: BitmapFactory.Options): Boolean =
        bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= MAX_PIXELS

    private const val REQUEST_TIMEOUT_MS = 15_000L
    private const val MAX_REDIRECTS = 8
    private const val MAX_BYTES = 5 * 1024 * 1024
    private const val MAX_PIXELS = 16_000_000L
    private const val USER_AGENT = "Relay/0.1 (personal music player)"
}

internal fun artworkCacheKey(url: String): String = MessageDigest.getInstance("SHA-256")
    .digest(normalizedArtworkUrl(url).encodeToByteArray())
    .joinToString("") { "%02x".format(it) }

private fun normalizedArtworkUrl(value: String): String {
    val uri = URI(value).normalize()
    require(uri.scheme.equals("https", ignoreCase = true) && uri.host != null && uri.userInfo == null) { "Artwork must use HTTPS." }
    val host = uri.host.lowercase(Locale.ROOT).let { if (':' in it) "[$it]" else it }
    val port = uri.port.takeUnless { it == -1 || it == 443 }?.let { ":$it" }.orEmpty()
    val path = uri.rawPath?.takeIf(String::isNotEmpty) ?: "/"
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()
    return "https://$host$port$path$query"
}

/** Shares one synchronous load among callers for the same normalized artwork key. */
internal class ArtworkRequestDeduplicator {
    private val inFlight = ConcurrentHashMap<String, FutureTask<String>>()

    fun run(key: String, load: () -> String): String {
        val created = FutureTask<String> { load() }
        val task = inFlight.putIfAbsent(key, created) ?: created.also { it.run() }
        return try {
            task.get()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        } finally {
            if (task === created) inFlight.remove(key, created)
        }
    }
}
