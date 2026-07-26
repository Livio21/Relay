package dev.relay.music.model

/** Time window an insights view covers. `days == null` means every recorded play. */
enum class InsightsRange(val days: Int?) {
    WEEK(7),
    MONTH(30),
    YEAR(365),
    ALL(null),
}

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
)

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
