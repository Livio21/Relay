package dev.relay.music.library

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import dev.relay.music.platform.UserMessenger
import dev.relay.music.settings.BackupSchedule
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backup and sync-bundle archive pipeline: writing, restore planning, transactional
 * restore with a safety snapshot, and sync merging. Dialogs and SAF launchers stay
 * with the host activity; failures surface through [UserMessenger] unchanged.
 */
class BackupRestoreCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val dao: UserLibraryDao,
    private val messenger: UserMessenger,
    private val onRestored: (restoredSchedule: BackupSchedule, trackersRequiringReconnect: List<String>) -> Unit,
) {
    /** Writes a manual backup into the Relay storage folder's backups/manual directory. */
    fun exportToStorage(rootUri: Uri, sections: Set<RelayBackupSection>, fileName: String = "relay-${System.currentTimeMillis()}.relaybackup") {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val document = RelayStorage.createManualBackup(context, rootUri, fileName)
                        ?: error("Relay storage folder is unavailable.")
                    context.contentResolver.openOutputStream(document.uri)?.use {
                        RelayBackupArchive.write(it, dao, sections)
                    } ?: error("Could not create the backup file.")
                }
            }
            messenger.info(
                if (result.isSuccess) "Manual backup saved in Relay/backups/manual." else "Could not save backup. Check Relay folder access in Settings.",
                longLength = true,
            )
        }
    }

    suspend fun writeTo(output: OutputStream, sections: Set<RelayBackupSection>) = withContext(Dispatchers.IO) {
        RelayBackupArchive.write(output, dao, sections)
    }

    suspend fun writeSyncTo(output: OutputStream) = withContext(Dispatchers.IO) {
        RelaySyncArchive.write(output, dao)
    }

    fun exportSyncToStorage(rootUri: Uri, fileName: String = "relay-${System.currentTimeMillis()}.relaysync") {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val document = RelayStorage.createSyncBundle(context, rootUri, fileName)
                        ?: error("Relay storage folder is unavailable.")
                    context.contentResolver.openOutputStream(document.uri)?.use { RelaySyncArchive.write(it, dao) }
                        ?: error("Could not create sync file.")
                }
            }
            messenger.info(
                if (result.isSuccess) "Sync file saved in Relay/sync." else "Could not save sync file. Choose the Relay folder again.",
                longLength = true,
            )
        }
    }

    /**
     * Builds the preflight restore plan: missing repositories/plugins and tracker
     * reconnects are reported before any mutation happens.
     */
    suspend fun buildRestorePlan(
        contents: RelayBackupContents,
        selectedSections: Set<RelayBackupSection>,
        isPackageInstalled: (String) -> Boolean,
    ): Result<RelayRestorePlan> = withContext(Dispatchers.IO) {
        runCatching {
            val packageNames = contents.backup.settings?.installedExtensions().orEmpty()
                .mapNotNull { it.androidPackageName }
                .toSet()
            contents.restorePlan(
                selectedSections = selectedSections,
                currentSettings = dao.settingsSnapshot(),
                installedAndroidPackages = packageNames.filterTo(mutableSetOf(), isPackageInstalled),
            )
        }
    }

    /**
     * Applies a reviewed plan: writes a safety snapshot first, then restores
     * transactionally. Existing data survives any failure.
     */
    fun performRestore(plan: RelayRestorePlan) {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    writeSafetySnapshot()
                    dao.restoreUserLibrary(plan)
                    dao.settingsSnapshot()?.asSettings()?.backupSchedule ?: BackupSchedule.OFF
                }
            }
            result.fold(
                onSuccess = { schedule ->
                    onRestored(schedule, plan.trackersRequiringReconnect)
                    val followUp = when {
                        plan.missingPlugins.isNotEmpty() -> " Reinstall missing extension packages before enabling them."
                        plan.missingRepositories.isNotEmpty() -> " Review missing extension repositories."
                        plan.trackersRequiringReconnect.isNotEmpty() -> " Reconnect your tracker in Settings."
                        else -> ""
                    }
                    messenger.info("Backup restored.$followUp", longLength = true)
                },
                onFailure = {
                    messenger.info("Backup could not be restored; existing data was kept.", longLength = true)
                },
            )
        }
    }

    private suspend fun writeSafetySnapshot() {
        val safetyBackup = AtomicFile(File(context.filesDir, "restore-safety/last.relaybackup"))
        safetyBackup.baseFile.parentFile?.mkdirs()
        val archiveBytes = ByteArrayOutputStream().also {
            RelayBackupArchive.write(it, dao)
        }.toByteArray()
        val output = safetyBackup.startWrite()
        try {
            output.write(archiveBytes)
            safetyBackup.finishWrite(output)
        } catch (error: Throwable) {
            safetyBackup.failWrite(output)
            throw error
        }
    }

    /** Merges an imported `.relaysync` bundle non-destructively; conflicts stay local. */
    fun mergeSyncFrom(input: InputStream, onMerged: () -> Unit) {
        scope.launch {
            val result = runCatching {
                val incoming = withContext(Dispatchers.IO) {
                    input.use(RelaySyncArchive::read)
                }
                withContext(Dispatchers.IO) { dao.mergeSync(incoming) }
            }
            if (result.isSuccess) onMerged()
            messenger.info(
                result.fold(
                    onSuccess = { merged ->
                        "Sync merged ${merged.importedRecords} records" +
                            if (merged.conflicts.isEmpty()) "." else "; ${merged.conflicts.size} conflicts kept local."
                    },
                    onFailure = { "Sync file is invalid or could not be merged." },
                ),
                longLength = true,
            )
        }
    }
}
