package dev.relay.music.extension

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.relay.music.platform.UserMessenger
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The extension installation pipeline: artifact download, PackageInstaller staging,
 * session bookkeeping, and post-install verification/loading. Dialogs and activity-result
 * launchers stay with the host activity; this class never shows one.
 */
class ExtensionInstallCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val installer: AndroidExtensionInstaller,
    private val loader: AndroidExtensionLoader,
    private val artifactClient: ExtensionArtifactClient,
    private val messenger: UserMessenger,
    private val statusAction: String,
    private val trustedCatalogEntry: (RepositoryDescriptor, ExtensionCatalogEntry) -> Boolean,
    private val persistReplaced: suspend (InstalledExtension) -> Unit,
    private val onSourcesInvalidated: () -> Unit,
) {
    var downloadProgress by mutableStateOf<ExtensionDownloadProgress?>(null)
        private set

    private val pendingInstalls = mutableMapOf<Int, Pair<RepositoryDescriptor, ExtensionCatalogEntry>>()

    fun isTrusted(repository: RepositoryDescriptor, entry: ExtensionCatalogEntry): Boolean =
        trustedCatalogEntry(repository, entry)

    fun isBusy(): Boolean = downloadProgress != null

    /** Downloads the verified artifact and stages it; theme packs are handed back whole. */
    fun downloadAndStage(repository: RepositoryDescriptor, entry: ExtensionCatalogEntry, onThemePackReady: (File) -> Unit) {
        if (isBusy() || !isTrusted(repository, entry)) return
        scope.launch {
            downloadProgress = ExtensionDownloadProgress(entry.id, entry.name, 0, entry.artifactSizeBytes)
            when (val download = artifactClient.download(entry) { downloaded, total ->
                runOnUiThread { downloadProgress = ExtensionDownloadProgress(entry.id, entry.name, downloaded, total) }
            }) {
                is ArtifactDownloadResult.Failure ->
                    messenger.error(download.message)
                is ArtifactDownloadResult.Success -> when (entry.kind) {
                    ExtensionKind.THEME_PACK -> onThemePackReady(download.file)
                    ExtensionKind.SOURCE -> when (val request = installer.stage(entry, download.file)) {
                        is ExtensionInstallRequest.Refused -> messenger.error(request.reason)
                        is ExtensionInstallRequest.Ready -> commit(request.sessionId, repository, entry)
                    }
                }
            }
            downloadProgress = null
        }
    }

    private fun commit(sessionId: Int, repository: RepositoryDescriptor, entry: ExtensionCatalogEntry) {
        val statusIntent = Intent(statusAction).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)
        pendingInstalls[sessionId] = repository to entry
        installer.commit(sessionId, pendingIntent.intentSender)?.let { error ->
            pendingInstalls.remove(sessionId)
            messenger.error(error)
        }
    }

    /**
     * Handles a PackageInstaller status broadcast. Returns the user-confirmation intent for
     * the activity to launch, or null when the flow finished here.
     */
    fun handleStatus(intent: Intent): Intent? {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val pending = pendingInstalls[sessionId] ?: return null
        return when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmation ?: run {
                    pendingInstalls.remove(sessionId)
                    messenger.error("Android installer did not provide a confirmation action.")
                    null
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                pendingInstalls.remove(sessionId)
                enableInstalled(pending)
                null
            }
            else -> {
                pendingInstalls.remove(sessionId)
                messenger.error(
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        ?: "Extension installation was cancelled.",
                )
                null
            }
        }
    }

    private fun enableInstalled(pending: Pair<RepositoryDescriptor, ExtensionCatalogEntry>) {
        scope.launch {
            val result = loader.load(pending.second)
            val failureMessage = result.exceptionOrNull()?.message
            val disabledReason = when {
                failureMessage != null -> failureMessage.take(240)
                result.isFailure -> "Extension verification failed."
                else -> null
            }
            val installed = pending.second.asInstalled(pending.first.id, disabledReason)
            persistReplaced(installed)
            if (result.isSuccess) {
                messenger.success("Extension installed and enabled.")
            } else {
                messenger.error("Extension was installed but not enabled: ${result.exceptionOrNull()?.message ?: "verification failed."}")
            }
        }
    }

    /** Enables/disables an installed source, verifying it loads before trusting the toggle. */
    suspend fun setEnabled(
        installed: InstalledExtension,
        enabled: Boolean,
        repositoryEntries: List<ExtensionCatalogEntry>,
    ): InstalledExtension {
        val updated = if (enabled) {
            val entry = installed.resolvedCatalogEntry(repositoryEntries)
            when {
                entry == null -> installed.disabled("Trusted install details are unavailable. Refresh its repository or reinstall the extension.")
                else -> {
                    val failure = loader.load(entry).exceptionOrNull()?.message
                    installed.copy(
                        enabled = failure == null,
                        disabledReason = failure?.take(240),
                        catalogSnapshot = entry,
                    )
                }
            }
        } else {
            installed.disabled("Disabled by user.")
        }
        persistReplaced(updated)
        onSourcesInvalidated()
        return updated
    }

    private fun runOnUiThread(block: () -> Unit) {
        if (context is android.app.Activity) context.runOnUiThread(block) else block()
    }
}
