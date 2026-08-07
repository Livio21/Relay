package dev.relay.music.model

/** Oldest-first eviction candidate for offline download storage limits. */
data class OfflineDownloadEvictionEntry(
    val sourceId: String,
    val trackId: String,
    val sizeBytes: Long,
    val downloadedAtEpochMs: Long,
)

/**
 * Returns `(sourceId, trackId)` pairs to delete so total size is within [limitBytes].
 * Oldest downloads (by [OfflineDownloadEvictionEntry.downloadedAtEpochMs]) are removed first.
 */
fun offlineDownloadsToEvict(
    downloads: List<OfflineDownloadEvictionEntry>,
    limitBytes: Long,
): List<Pair<String, String>> {
    if (limitBytes <= 0) return emptyList()
    val sorted = downloads.sortedBy { it.downloadedAtEpochMs }
    var totalBytes = sorted.sumOf { it.sizeBytes }
    if (totalBytes <= limitBytes) return emptyList()
    val evict = mutableListOf<Pair<String, String>>()
    for (entry in sorted) {
        if (totalBytes <= limitBytes) break
        evict += entry.sourceId to entry.trackId
        totalBytes -= entry.sizeBytes
    }
    return evict
}
