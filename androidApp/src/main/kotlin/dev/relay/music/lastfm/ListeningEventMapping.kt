package dev.relay.music.lastfm

import dev.relay.music.library.ListeningHistoryEntity
import dev.relay.music.model.ListeningEvent
import dev.relay.music.model.ListeningOrigin
import dev.relay.music.model.effectiveFingerprint

const val LASTFM_IMPORT_SOURCE_ID = "lastfm"

internal fun ListeningHistoryEntity.asListeningEvent() = ListeningEvent(
    sourceId = sourceId,
    trackId = trackId,
    playedAtEpochMs = playedAtEpochMs,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    origin = ListeningOrigin.entries.firstOrNull { it.name == origin } ?: ListeningOrigin.LOCAL,
    identityFingerprint = identityFingerprint,
)

internal fun LastFmHistoryTrack.asImportedListeningEvent(): ListeningEvent {
    val event = ListeningEvent(
        sourceId = LASTFM_IMPORT_SOURCE_ID,
        trackId = recordingId ?: "$artist\u0000$title",
        playedAtEpochMs = playedAtEpochMs,
        title = title,
        artist = artist,
        album = album,
        origin = ListeningOrigin.LASTFM_IMPORT,
        identityFingerprint = recordingId,
    )
    return event.copy(identityFingerprint = event.identityFingerprint ?: event.effectiveFingerprint())
}

internal fun ListeningEvent.asHistoryEntry() = ListeningHistoryEntity(
    sourceId = sourceId,
    trackId = trackId,
    playedAtEpochMs = playedAtEpochMs,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    origin = origin.name,
    identityFingerprint = identityFingerprint?.take(512),
)
