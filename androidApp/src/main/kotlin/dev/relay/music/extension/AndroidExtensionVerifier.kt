package dev.relay.music.extension

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** Verifies the installed APK identity before Relay ever binds to its exported service. */
class AndroidExtensionVerifier(context: Context) {
    private val packageManager = context.packageManager

    fun verify(entry: ExtensionCatalogEntry): String? {
        val packageName = entry.androidPackageName ?: return "Extension is not an Android APK."
        val expectedCertificate = entry.androidSigningCertificateSha256 ?: return "Android extension signer is missing."
        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= 28) {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return "Extension package is not installed."
        }
        return verifyPackageInfo(packageInfo, packageName, expectedCertificate)
    }

    fun verifyArchive(entry: ExtensionCatalogEntry, artifact: File): String? {
        val packageName = entry.androidPackageName ?: return "Extension is not an Android APK."
        val expectedCertificate = entry.androidSigningCertificateSha256 ?: return "Android extension signer is missing."
        if (!artifact.isFile) return "Extension artifact is missing."
        val packageInfo = if (Build.VERSION.SDK_INT >= 28) {
            packageManager.getPackageArchiveInfo(artifact.path, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(artifact.path, PackageManager.GET_SIGNATURES)
        } ?: return "Extension artifact is not a valid APK."
        return verifyPackageInfo(packageInfo, packageName, expectedCertificate)
    }

    private fun verifyPackageInfo(
        packageInfo: android.content.pm.PackageInfo,
        expectedPackageName: String,
        expectedCertificate: String,
    ): String? {
        if (packageInfo.packageName != expectedPackageName) return "Extension package does not match the trusted catalog."
        @Suppress("DEPRECATION")
        val signatures = if (Build.VERSION.SDK_INT >= 28) packageInfo.signingInfo?.apkContentsSigners.orEmpty() else packageInfo.signatures.orEmpty()
        return if (signatures.any { signature -> certificateSha256(signature.toByteArray()) == expectedCertificate }) {
            null
        } else {
            "Extension package signer does not match the trusted catalog."
        }
    }
}

internal fun certificateSha256(certificate: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(certificate).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
