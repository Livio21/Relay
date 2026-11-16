package dev.relay.music.model

/**
 * Resolves listening events back to tracks that still exist in the library, newest first.
 * Snapshot-only or remote events without a current track are skipped rather than invented.
 * A track appears once, at its most recent play.
 */
fun recentlyPlayedTracks(
    events: List<ListeningEvent>,
    tracks: List<Track>,
    limit: Int = 6,
): List<Track> {
    if (limit <= 0) return emptyList()
    val byKey = tracks.associateBy { it.sourceId to it.id }
    return events
        .asSequence()
        .sortedByDescending { it.playedAtEpochMs }
        .mapNotNull { event -> byKey[event.sourceId to event.trackId] }
        .distinctBy { it.sourceId to it.id }
        .take(limit)
        .toList()
}
