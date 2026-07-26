package dev.relay.music.library

import android.content.Context
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.relay.music.settings.BackupSchedule
import java.util.concurrent.TimeUnit

class AutomaticBackupWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val dao = UserLibraryStore.database(applicationContext).userLibraryDao()
        val settings = dao.settingsSnapshot()?.asSettings() ?: return Result.success()
        val rootUri = settings.storageRootUri?.toUri() ?: return Result.success()
        val document = RelayStorage.createAutomaticBackup(
            applicationContext,
            rootUri,
            "relay-auto-${System.currentTimeMillis()}.relaybackup",
        ) ?: return Result.retry()
        return runCatching {
            applicationContext.contentResolver.openOutputStream(document.uri)?.use { RelayBackupArchive.write(it, dao) }
                ?: error("Could not create automatic backup.")
            RelayStorage.trimAutomaticBackups(applicationContext, rootUri, settings.autoBackupExpiryDays)
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}

object AutomaticBackupScheduler {
    private const val UNIQUE_WORK_NAME = "relay.automatic_backup"

    fun update(context: Context, schedule: BackupSchedule) {
        val workManager = WorkManager.getInstance(context)
        if (schedule == BackupSchedule.OFF) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val days = if (schedule == BackupSchedule.DAILY) 1L else 7L
        val request = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(days, TimeUnit.DAYS).build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
