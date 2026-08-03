package dev.relay.music.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val TRACK_TAGS_SCHEMA_VERSION = 1
private const val MAX_TAG_VALUES = 16
private const val MAX_CONTRIBUTORS = 32
const val MAX_TRACK_TAGS_JSON_CHARS = 64 * 1024

@Serializable
enum class TagOrigin { SOURCE, PROVIDER, USER }

@Serializable
enum class TrackTagField { GENRES, MOODS, INSTRUMENTS, RELEASE_DATE, BPM, MUSICAL_KEY, CONTRIBUTORS }

@Serializable
data class TextTag(
    val value: String,
    val origin: TagOrigin,
)

@Serializable
data class NumberTag(
    val value: Float,
    val origin: TagOrigin,
)

@Serializable
data class ContributorTag(
    val name: String,
    val role: String,
    val origin: TagOrigin,
)

/** Bounded, reviewed insight metadata. It never changes a track's playback identity. */
@Serializable
data class TrackTags(
    val schemaVersion: Int = TRACK_TAGS_SCHEMA_VERSION,
    val genres: List<TextTag> = emptyList(),
    val moods: List<TextTag> = emptyList(),
    val instruments: List<TextTag> = emptyList(),
    val releaseDate: TextTag? = null,
    val bpm: NumberTag? = null,
    val musicalKey: TextTag? = null,
    val contributors: List<ContributorTag> = emptyList(),
    /** A reviewed field remains authoritative even when the user intentionally leaves it empty. */
    val userControlledFields: Set<TrackTagField> = emptySet(),
)

fun TrackTags.normalized(): TrackTags = copy(
    schemaVersion = TRACK_TAGS_SCHEMA_VERSION,
    genres = genres.normalizedTextTags(),
    moods = moods.normalizedTextTags(),
    instruments = instruments.normalizedTextTags(),
    releaseDate = releaseDate?.normalizedTextTag()?.takeIf { it.value.matches(RELEASE_DATE) },
    bpm = bpm?.takeIf { it.value.isFinite() && it.value in 20f..400f },
    musicalKey = musicalKey?.normalizedTextTag(16),
    contributors = contributors.mapNotNull { contributor ->
        val name = contributor.name.clean(128)
        val role = contributor.role.clean(32)
        if (name == null || role == null) null else contributor.copy(name = name, role = role)
    }.distinctBy { "${it.name.lowercase()}\u0000${it.role.lowercase()}" }.take(MAX_CONTRIBUTORS),
)

fun TrackTags.validate(): String? = when {
    schemaVersion != TRACK_TAGS_SCHEMA_VERSION -> "Track-tag schema is unsupported."
    genres.size > MAX_TAG_VALUES || moods.size > MAX_TAG_VALUES || instruments.size > MAX_TAG_VALUES -> "Too many track tags."
    contributors.size > MAX_CONTRIBUTORS -> "Too many contributors."
    normalized() != this -> "Track tags contain invalid or non-normalized values."
    else -> null
}

/** Refreshes provider/source fields without replacing anything the user has reviewed. */
fun TrackTags.mergeRefresh(incoming: TrackTags): TrackTags {
    val next = incoming.normalized()
    fun <T> keep(field: TrackTagField, current: T, refreshed: T): T =
        if (field in userControlledFields) current else refreshed
    return TrackTags(
        genres = keep(TrackTagField.GENRES, genres, next.genres),
        moods = keep(TrackTagField.MOODS, moods, next.moods),
        instruments = keep(TrackTagField.INSTRUMENTS, instruments, next.instruments),
        releaseDate = keep(TrackTagField.RELEASE_DATE, releaseDate, next.releaseDate),
        bpm = keep(TrackTagField.BPM, bpm, next.bpm),
        musicalKey = keep(TrackTagField.MUSICAL_KEY, musicalKey, next.musicalKey),
        contributors = keep(TrackTagField.CONTRIBUTORS, contributors, next.contributors),
        userControlledFields = userControlledFields,
    ).normalized()
}

fun encodeTrackTags(tags: TrackTags): String {
    val normalized = tags.normalized()
    require(normalized.validate() == null) { normalized.validate() ?: "Track tags are invalid." }
    return TRACK_TAGS_JSON.encodeToString(normalized).also {
        require(it.length <= MAX_TRACK_TAGS_JSON_CHARS) { "Track tags are too large." }
    }
}

fun decodeTrackTags(value: String): TrackTags {
    require(value.length <= MAX_TRACK_TAGS_JSON_CHARS) { "Track tags are too large." }
    return TRACK_TAGS_JSON.decodeFromString<TrackTags>(value).also {
        require(it.validate() == null) { it.validate() ?: "Track tags are invalid." }
    }
}

private fun List<TextTag>.normalizedTextTags(): List<TextTag> =
    mapNotNull(TextTag::normalizedTextTag)
        .distinctBy { it.value.lowercase() }
        .take(MAX_TAG_VALUES)

private fun TextTag.normalizedTextTag(maxLength: Int = 64): TextTag? =
    value.clean(maxLength)?.let { copy(value = it) }

private fun String.clean(maxLength: Int): String? =
    trim().replace(Regex("\\s+"), " ").take(maxLength).takeIf(String::isNotEmpty)

private val RELEASE_DATE = Regex("(?:[12]\\d{3})(?:-(?:0[1-9]|1[0-2])(?:-(?:0[1-9]|[12]\\d|3[01]))?)?")
private val TRACK_TAGS_JSON = Json { encodeDefaults = true }
