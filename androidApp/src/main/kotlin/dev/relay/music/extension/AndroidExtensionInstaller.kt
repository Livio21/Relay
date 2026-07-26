package dev.relay.music.extension

import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

sealed interface ExtensionInstallRequest {
    data class Ready(val sessionId: Int) : ExtensionInstallRequest
    data class Refused(val reason: String) : ExtensionInstallRequest
}

/** Prepares only a verified APK for Android's system-owned installer flow. */
class AndroidExtensionInstaller(private val context: Context) {
    private val verifier = AndroidExtensionVerifier(context)

    fun stage(entry: ExtensionCatalogEntry, artifact: File): ExtensionInstallRequest {
        entry.validate()?.let { return ExtensionInstallRequest.Refused(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            return ExtensionInstallRequest.Refused("Allow Relay to install trusted extension APKs in Android settings, then retry.")
        }
        if (!entry.isCompatible) {
            return ExtensionInstallRequest.Refused(
                "Requires extension API ${entry.api.minimum}-${entry.api.maximum}; Relay supports $EXTENSION_API_VERSION.",
            )
        }
        verifier.verifyArchive(entry, artifact)?.let { return ExtensionInstallRequest.Refused(it) }
        val installer = context.packageManager.packageInstaller
        val sessionId = runCatching {
            installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            })
        }.getOrElse { return ExtensionInstallRequest.Refused("Could not create Android install session.") }
        return runCatching {
            installer.openSession(sessionId).use { session ->
                artifact.inputStream().use { input ->
                    session.openWrite("base.apk", 0, artifact.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
            }
            ExtensionInstallRequest.Ready(sessionId)
        }.getOrElse {
            installer.abandonSession(sessionId)
            ExtensionInstallRequest.Refused("Could not stage extension APK.")
        }
    }

    fun commit(sessionId: Int, statusReceiver: IntentSender): String? = runCatching {
        context.packageManager.packageInstaller.openSession(sessionId).use { it.commit(statusReceiver) }
    }.exceptionOrNull()?.let { "Could not request Android installation." }
}
