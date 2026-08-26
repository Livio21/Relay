package dev.relay.music.library

import android.content.Context
import android.widget.Toast
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dev.relay.music.model.OfflineDownloadEvictionEntry
import dev.relay.music.model.offlineDownloadsToEvict
import dev.relay.music.platform.UserMessenger
import dev.relay.music.settings.RelaySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Offline download management: deletion, storage limits, automatic cleanup. */
class DownloadsController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val dao: UserLibraryDao,
    private val currentSettings: () -> RelaySettings,
    private val modifySettings: suspend ((RelaySettings) -> RelaySettings) -> Unit,
    private val onLibraryChanged: () -> Unit,
    private val messenger: UserMessenger,
) {
    fun delete(sourceId: String, trackId: String) {
        scope.launch {
            val removed = withContext(Dispatchers.IO) {
                val entity = dao.offlineDownload(sourceId, trackId) ?: return@withContext false
                runCatching { DocumentFile.fromSingleUri(context, entity.documentUri.toUri())?.delete() }
                    .onFailure { messenger.quietError("Could not delete download file", it) }
                dao.deleteOfflineDownload(sourceId, trackId)
                true
            }
            if (removed) {
                onLibraryChanged()
                messenger.info("Download removed.")
            }
        }
    }

    fun deleteAll() {
        scope.launch {
            withContext(Dispatchers.IO) {
                dao.offlineDownloads().first().forEach { entity ->
                    runCatching { DocumentFile.fromSingleUri(context, entity.documentUri.toUri())?.delete() }
                        .onFailure { messenger.quietError("Could not delete download file", it) }
                    dao.deleteOfflineDownload(entity.sourceId, entity.trackId)
                }
            }
            onLibraryChanged()
            messenger.info("Downloads removed.")
        }
    }

    fun setStorageLimit(limitGb: Int) {
        scope.launch(Dispatchers.IO) {
            modifySettings { it.copy(downloadStorageLimitGb = limitGb) }
            val settings = currentSettings()
            if (settings.downloadAutoCleanup && limitGb > 0) {
                enforceStorageLimit(limitGb)
            }
        }
    }

    fun setAutoCleanup(enabled: Boolean) {
        scope.launch(Dispatchers.IO) {
            modifySettings { it.copy(downloadAutoCleanup = enabled) }
            val settings = currentSettings()
            if (enabled && settings.downloadStorageLimitGb > 0) {
                enforceStorageLimit(settings.downloadStorageLimitGb)
            }
        }
    }

    fun evictExceeded() {
        scope.launch {
            val limitGb = currentSettings().downloadStorageLimitGb
            if (limitGb <= 0) return@launch
            enforceStorageLimit(limitGb)
        }
    }

    /**
     * Deletes oldest downloads (by [OfflineDownloadEntity.downloadedAtEpochMs]) until total
     * storage is within [limitGb] GB. Called on limit/cleanup-toggle changes and after each
     * new download when auto-cleanup is enabled.
     */
    suspend fun enforceStorageLimit(limitGb: Int) {
        val limitBytes = limitGb * 1024L * 1024L * 1024L
        withContext(Dispatchers.IO) {
            val downloads = dao.offlineDownloads().first()
            val byKey = downloads.associateBy { it.sourceId to it.trackId }
            val toEvict = offlineDownloadsToEvict(
                downloads.map {
                    OfflineDownloadEvictionEntry(
                        sourceId = it.sourceId,
                        trackId = it.trackId,
                        sizeBytes = it.sizeBytes,
                        downloadedAtEpochMs = it.downloadedAtEpochMs,
                    )
                },
                limitBytes,
            )
            if (toEvict.isEmpty()) return@withContext
            for ((sourceId, trackId) in toEvict) {
                val download = byKey[sourceId to trackId] ?: continue
                runCatching {
                    DocumentFile.fromSingleUri(context, download.documentUri.toUri())?.delete()
                }.onFailure { messenger.quietError("Could not evict download file", it) }
                dao.deleteOfflineDownload(sourceId, trackId)
            }
            onLibraryChanged()
            withContext(Dispatchers.Main) {
                val evicted = toEvict.size
                Toast.makeText(
                    context,
                    "Removed $evicted download${if (evicted == 1) "" else "s"} to stay within limit.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}
