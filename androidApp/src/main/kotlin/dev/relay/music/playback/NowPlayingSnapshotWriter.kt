package dev.relay.music.playback

internal const val SNAPSHOT_WRITE_INTERVAL_MS = 15_000L

internal class NowPlayingSnapshotWritePolicy(
    private val intervalMs: Long = SNAPSHOT_WRITE_INTERVAL_MS,
) {
    private var lastSnapshot: NowPlayingSnapshot? = null
    private var lastWriteElapsedMs: Long? = null

    init {
        require(intervalMs > 0)
    }

    fun shouldWrite(snapshot: NowPlayingSnapshot, elapsedMs: Long, force: Boolean = false): Boolean {
        val previous = lastSnapshot
        val immediate = force || previous == null || previous.externalFactsDifferFrom(snapshot)
        val periodic = snapshot.isPlaying && lastWriteElapsedMs?.let { elapsedMs - it >= intervalMs } == true
        if (!immediate && !periodic) return false
        lastSnapshot = snapshot
        lastWriteElapsedMs = elapsedMs
        return true
    }
}

internal fun nowPlayingSnapshot(
    trackKey: String?,
    title: String?,
    artist: String?,
    album: String?,
    artworkCacheKey: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    updatedAtEpochMs: Long,
): NowPlayingSnapshot {
    val duration = durationMs.coerceAtLeast(0)
    return NowPlayingSnapshot(
        trackKey = trackKey.cleanSnapshotText(),
        title = title.cleanSnapshotText(),
        artist = artist.cleanSnapshotText(),
        album = album.cleanSnapshotText(),
        artworkCacheKey = artworkCacheKey.cleanSnapshotText(),
        isPlaying = isPlaying,
        positionMs = positionMs.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration) else it },
        durationMs = duration,
        updatedAtEpochMs = updatedAtEpochMs.coerceAtLeast(0),
    )
}

internal fun externalSurfaceSnapshot(
    snapshot: NowPlayingSnapshot?,
    sessionPlaying: Boolean,
): NowPlayingSnapshot? = snapshot?.let { if (sessionPlaying) it else it.copy(isPlaying = false) }

private fun NowPlayingSnapshot.externalFactsDifferFrom(other: NowPlayingSnapshot): Boolean =
    trackKey != other.trackKey ||
        title != other.title ||
        artist != other.artist ||
        album != other.album ||
        artworkCacheKey != other.artworkCacheKey ||
        isPlaying != other.isPlaying ||
        durationMs != other.durationMs

private fun String?.cleanSnapshotText(): String? = this?.trim()?.takeIf(String::isNotEmpty)?.take(512)
