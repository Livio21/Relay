package dev.relay.music.model

/** A track the user explicitly downloaded for offline playback. Host-owned; never a stream URL. */
data class OfflineDownload(
    val sourceId: String,
    val trackId: String,
    val title: String,
    val sizeBytes: Long,
)
