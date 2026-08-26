package dev.relay.music.extension

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/**
 * Stores validated background images for user-imported theme packs inside Relay-owned
 * storage. Packs reference images by opaque content-hash tokens, never by paths or URLs;
 * built-in and catalog-distributed packs cannot ship binaries.
 */
class ThemeAssetStore(private val context: Context) {

    fun import(packId: String, bytes: ByteArray): Result<String> = runCatching {
        require(packId.matches(Regex("[a-z0-9][a-z0-9._-]{0,127}"))) { "Theme pack ID is invalid." }
        require(bytes.size <= THEME_ASSET_MAX_BYTES) { "Background image is too large (max 2 MB)." }
        val extension = sniffImageExtension(bytes) ?: error("Background must be a JPEG, PNG, or WebP image.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..THEME_ASSET_MAX_DIMENSION && bounds.outHeight in 1..THEME_ASSET_MAX_DIMENSION) {
            "Background image must be at most ${THEME_ASSET_MAX_DIMENSION}px on each side."
        }
        val token = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
            .take(24) + ".$extension"
        val target = packDirectory(packId).resolve(token)
        if (!target.isFile || target.length() != bytes.size.toLong()) {
            target.parentFile?.mkdirs()
            target.outputStream().use { it.write(bytes) }
        }
        token
    }

    /** Resolves a validated pack token to a local file URI for rendering. */
    fun resolve(packId: String?, token: String?): String? {
        if (packId.isNullOrBlank() || token.isNullOrBlank()) return null
        if (!token.matches(Regex("[0-9a-f]{24}\\.(jpg|png|webp)"))) return null
        val file = packDirectory(packId).resolve(token)
        return if (file.isFile && file.length() in 1..THEME_ASSET_MAX_BYTES) {
            Uri.fromFile(file).toString()
        } else {
            null
        }
    }

    fun removeAll(packId: String) {
        if (!packId.matches(Regex("[a-z0-9][a-z0-9._-]{0,127}"))) return
        packDirectory(packId).deleteRecursively()
    }

    private fun packDirectory(packId: String): File = File(File(context.filesDir, "theme-assets"), packId)

    private fun sniffImageExtension(bytes: ByteArray): String? = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "jpg"
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "png"
        bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte() -> "webp"
        else -> null
    }

    private companion object {
        const val THEME_ASSET_MAX_BYTES = 2 * 1024 * 1024
        const val THEME_ASSET_MAX_DIMENSION = 2048
    }
}
