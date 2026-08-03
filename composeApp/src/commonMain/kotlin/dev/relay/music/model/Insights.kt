package dev.relay.music.model

import kotlin.math.abs

/** Time window an insights view covers. `days == null` means every recorded play. */
enum class InsightsRange(val days: Int?) {
    WEEK(7),
    MONTH(30),
    YEAR(365),
    ALL(null),
}

enum class AlbumChartMetric { PLAYS }

/** A saved, reproducible chart request. Generated images are deliberately not canonical data. */
data class AlbumChartSpec(
    val id: String,
    val range: InsightsRange,
    val metric: AlbumChartMetric = AlbumChartMetric.PLAYS,
    val limit: Int = 9,
    val createdAtEpochMs: Long,
)

fun AlbumChartSpec.validate(): String? = when {
    !id.matches(Regex("[a-zA-Z0-9_-]{1,64}")) -> "Chart ID is invalid."
    limit !in 1..16 -> "Chart size is invalid."
    createdAtEpochMs < 0 -> "Chart creation time is invalid."
    else -> null
}

/** Provenance stays local: imported or manual events are never candidates for re-scrobbling. */
enum class ListeningOrigin { LOCAL, LASTFM_IMPORT, MANUAL }

/** One profile exists per installation; a tracker association is only a non-secret username. */
data class LocalProfile(
    val displayName: String,
    val createdAtEpochMs: Long,
    val lastFmUsername: String? = null,
)

/**
 * One recorded play. The snapshot fields let a remote track still be attributed after its
 * extension is uninstalled; rows recorded before snapshots existed simply have none.
 */
data class ListeningEvent(
    val sourceId: String,
    val trackId: String,
    val playedAtEpochMs: Long,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val origin: ListeningOrigin = ListeningOrigin.LOCAL,
    /** Reviewed title/artist/album fingerprint when no stable source track ID exists. */
    val identityFingerprint: String? = null,
)

const val LISTENING_EVENT_DEDUP_WINDOW_MS = 60_000L

/**
 * Keeps only import candidates that are not already represented by the same effective recording
 * around the same timestamp. The returned events preserve their original provenance.
 */
fun newListeningEventsForImport(
    existing: List<ListeningEvent>,
    candidates: List<ListeningEvent>,
    timestampWindowMs: Long = LISTENING_EVENT_DEDUP_WINDOW_MS,
): List<ListeningEvent> {
    require(timestampWindowMs >= 0) { "Timestamp window cannot be negative." }
    val accepted = existing.toMutableList()
    return candidates.filter { candidate ->
        accepted.none { recorded -> recorded.matchesForDeduplication(candidate, timestampWindowMs) }
            .also { if (it) accepted += candidate }
    }
}

fun ListeningEvent.effectiveFingerprint(): String {
    identityFingerprint?.normalized()?.takeIf(String::isNotEmpty)?.let { return it }
    val metadata = listOf(title, artist, album).mapNotNull { it.normalized().takeIf(String::isNotEmpty) }
    return metadata.takeIf { it.isNotEmpty() }?.joinToString("\u0000")
        ?: "${sourceId.normalized()}\u0000${trackId.normalized()}"
}

private fun ListeningEvent.matchesForDeduplication(other: ListeningEvent, windowMs: Long): Boolean =
    effectiveFingerprint() == other.effectiveFingerprint() && abs(playedAtEpochMs - other.playedAtEpochMs) <= windowMs

private fun String?.normalized(): String =
    this.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")

data class InsightEntry(
    val label: String,
    val plays: Int,
    val estimatedMs: Long,
)

/** UTC day bucket used for a compact listening calendar without locale-dependent formatting. */
data class ListeningDay(
    val epochDay: Long,
    val plays: Int,
)

data class Insights(
    val plays: Int,
    val estimatedMs: Long,
    /** Plays whose track length was known; the rest cannot contribute to a time estimate. */
    val timedPlays: Int,
    val topTracks: List<InsightEntry>,
    val topArtists: List<InsightEntry>,
    val topAlbums: List<InsightEntry>,
    val listeningDays: List<ListeningDay>,
) {
    val untimedPlays: Int get() = plays - timedPlays
}

const val UNKNOWN_INSIGHT_LABEL = "Unknown"

/**
 * Aggregates plays in a window. Listening time is an estimate: Relay records when a track
 * started, not how long it was heard, so a play counts its full length and only when that
 * length is known. Missing artists and albums group under [UNKNOWN_INSIGHT_LABEL] rather than
 * being dropped, so the totals always add up.
 */
fun insightsFor(
    events: List<ListeningEvent>,
    range: InsightsRange,
    nowEpochMs: Long,
    limit: Int = 5,
): Insights {
    val cutoff = range.days?.let { nowEpochMs - it * DAY_MS } ?: Long.MIN_VALUE
    val inRange = events.filter { it.playedAtEpochMs in cutoff..nowEpochMs }
    return Insights(
        plays = inRange.size,
        estimatedMs = inRange.sumOf { it.durationMs ?: 0L },
        timedPlays = inRange.count { (it.durationMs ?: 0L) > 0 },
        topTracks = inRange
            .groupBy { "${it.sourceId}\u0000${it.trackId}" }
            .entries
            .map { (_, plays) -> plays.asEntry(plays.first().trackLabel()) }
            .ranked(limit),
        topArtists = inRange.topBy(limit) { it.artist },
        topAlbums = inRange.topBy(limit) { it.album },
        listeningDays = inRange
            .groupingBy { it.playedAtEpochMs / DAY_MS }
            .eachCount()
            .map { (day, plays) -> ListeningDay(day, plays) }
            .sortedBy { it.epochDay },
    )
}

fun albumChartEntries(events: List<ListeningEvent>, spec: AlbumChartSpec, nowEpochMs: Long): List<InsightEntry> =
    insightsFor(events, spec.range, nowEpochMs, spec.limit).topAlbums

private fun ListeningEvent.trackLabel(): String {
    val trackTitle = title?.trim()?.takeIf(String::isNotEmpty) ?: UNKNOWN_INSIGHT_LABEL
    val trackArtist = artist?.trim()?.takeIf(String::isNotEmpty) ?: return trackTitle
    return "$trackTitle — $trackArtist"
}

private fun List<ListeningEvent>.asEntry(label: String) =
    InsightEntry(label, size, sumOf { it.durationMs ?: 0L })

private fun List<ListeningEvent>.topBy(limit: Int, field: (ListeningEvent) -> String?): List<InsightEntry> =
    groupBy { field(it)?.trim()?.takeIf(String::isNotEmpty) ?: UNKNOWN_INSIGHT_LABEL }
        .map { (label, plays) -> plays.asEntry(label) }
        .ranked(limit)

/** Most played first; ties resolve alphabetically so the same data always renders the same way. */
private fun List<InsightEntry>.ranked(limit: Int) =
    sortedWith(compareByDescending<InsightEntry> { it.plays }.thenBy { it.label.lowercase() }).take(limit)

private const val DAY_MS = 24 * 60 * 60 * 1000L
