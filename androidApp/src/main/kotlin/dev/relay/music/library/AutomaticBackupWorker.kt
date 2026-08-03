package dev.relay.music.library

import android.content.Context
import android.util.Log
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
        val rootUri = settings.storageRootUri?.toUri() ?: return finalFailure(
            "Automatic backup needs a Relay storage folder. Open Settings > Storage to choose one.",
        )
        val staged = RelayStorage.stageAutomaticBackup(
            applicationContext,
            rootUri,
            "relay-auto-${System.currentTimeMillis()}.relaybackup",
        ) ?: return retryOrReport(
            "Automatic backup could not access the Relay folder. Open Settings > Storage and grant access again.",
        )
        return runCatching {
            applicationContext.contentResolver.openOutputStream(staged.document.uri, "w")?.use { RelayBackupArchive.write(it, dao) }
                ?: error("Could not create automatic backup.")
            applicationContext.contentResolver.openInputStream(staged.document.uri)?.use(RelayBackupArchive::inspect)
                ?: error("Could not verify automatic backup.")
            RelayStorage.commitAutomaticBackup(staged) ?: error("Could not finish automatic backup.")
            runCatching { RelayStorage.trimAutomaticBackups(applicationContext, rootUri, settings.autoBackupExpiryDays) }
                .onFailure { Log.w(TAG, "Could not trim old automatic backups", it) }
        }.fold(
            onSuccess = {
                AutomaticBackupFailureStore.clear(applicationContext)
                Result.success()
            },
            onFailure = { error ->
                RelayStorage.discardAutomaticBackup(staged)
                Log.w(TAG, "Automatic backup failed", error)
                retryOrReport(
                    "Automatic backup failed. Open Settings > Storage and verify the Relay folder.",
                )
            },
        )
    }

    private fun retryOrReport(message: String): Result =
        if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else finalFailure(message)

    private fun finalFailure(message: String): Result {
        AutomaticBackupFailureStore.record(applicationContext, message)
        // Periodic work remains healthy; the persisted one-shot state reports this failed run.
        return Result.success()
    }

    private companion object {
        const val TAG = "RelayAutoBackup"
        const val MAX_RETRY_ATTEMPTS = 2
    }
}

object AutomaticBackupFailureStore {
    private const val PREFERENCES = "relay.automatic_backup.status"
    private const val MESSAGE = "pending_failure"

    fun record(context: Context, message: String) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.contains(MESSAGE)) preferences.edit().putString(MESSAGE, message).apply()
    }

    fun consume(context: Context): String? {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return preferences.getString(MESSAGE, null)?.also { preferences.edit().remove(MESSAGE).apply() }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().remove(MESSAGE).apply()
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
