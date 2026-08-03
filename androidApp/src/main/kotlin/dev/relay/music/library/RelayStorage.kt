package dev.relay.music.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.security.MessageDigest

object RelayStorage {
    data class StagedAutomaticBackup internal constructor(
        val document: DocumentFile,
        internal val finalName: String,
    )

    private val folders = listOf("music", "downloads", "sync", "backups")

    fun prepare(context: Context, rootUri: Uri): Boolean {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return false
        if (!root.canRead() || !root.canWrite()) return false
        if (!folders.all { name ->
                root.findFile(name)?.isDirectory == true || root.createDirectory(name) != null
            }
        ) return false

        val backups = root.findFile("backups") ?: return false
        return listOf("manual", "automatic").all { name ->
            backups.findFile(name)?.isDirectory == true || backups.createDirectory(name) != null
        }
    }

    fun createManualBackup(context: Context, rootUri: Uri, name: String): DocumentFile? =
        createBackup(context, rootUri, "manual", name)

    fun createAutomaticBackup(context: Context, rootUri: Uri, name: String): DocumentFile? =
        createBackup(context, rootUri, "automatic", name)

    /** A staged name prevents interrupted writes from looking like usable automatic backups. */
    fun stageAutomaticBackup(context: Context, rootUri: Uri, finalName: String): StagedAutomaticBackup? {
        require(finalName.endsWith(".relaybackup")) { "Automatic backup name is invalid." }
        if (!prepare(context, rootUri)) return null
        val automatic = DocumentFile.fromTreeUri(context, rootUri)
            ?.findFile("backups")
            ?.findFile("automatic")
            ?.takeIf { it.isDirectory }
            ?: return null
        automatic.listFiles()
            .filter { it.name?.let { name -> name.startsWith("relay-auto-") && ".relaybackup.partial" in name } == true }
            .forEach { it.delete() }
        val document = automatic.createFile("application/zip", "$finalName.partial") ?: return null
        return StagedAutomaticBackup(document, finalName)
    }

    fun commitAutomaticBackup(staged: StagedAutomaticBackup): DocumentFile? {
        val parent = staged.document.parentFile ?: return null
        if (parent.listFiles().any { it.uri != staged.document.uri && it.name == staged.finalName }) return null
        return staged.document.takeIf { it.renameTo(staged.finalName) }
    }

    fun discardAutomaticBackup(staged: StagedAutomaticBackup) {
        staged.document.delete()
    }

    fun createSyncBundle(context: Context, rootUri: Uri, name: String): DocumentFile? {
        if (!prepare(context, rootUri)) return null
        val sync = DocumentFile.fromTreeUri(context, rootUri)?.findFile("sync")?.takeIf { it.isDirectory } ?: return null
        return sync.createFile("application/zip", name)
    }

    private fun createBackup(context: Context, rootUri: Uri, folder: String, name: String): DocumentFile? {
        if (!prepare(context, rootUri)) return null
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        val backups = root.findFile("backups")?.findFile(folder)?.takeIf { it.isDirectory } ?: return null
        return backups.createFile("application/zip", name)
    }

    fun trimAutomaticBackups(context: Context, rootUri: Uri, expiryDays: Int) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return
        val oldestAllowed = System.currentTimeMillis() - expiryDays.coerceIn(7, 90) * DAY_MS
        root.findFile("backups")
            ?.findFile("automatic")
            ?.takeIf { it.isDirectory }
            ?.listFiles()
            ?.filter {
                it.name?.let { name ->
                    name.startsWith("relay-auto-") && name.endsWith(".relaybackup") && it.lastModified() < oldestAllowed
                } == true
            }
            ?.forEach { it.delete() }
    }

    fun musicDirectory(context: Context, rootUri: Uri): DocumentFile? =
        DocumentFile.fromTreeUri(context, rootUri)?.findFile("music")?.takeIf { it.isDirectory }

    fun downloadsDirectory(context: Context, rootUri: Uri): DocumentFile? =
        DocumentFile.fromTreeUri(context, rootUri)?.findFile("downloads")?.takeIf { it.isDirectory }

    fun createDownload(context: Context, rootUri: Uri, sourceId: String, trackId: String, title: String, mimeType: String): DocumentFile? {
        if (!prepare(context, rootUri)) return null
        val directory = downloadsDirectory(context, rootUri) ?: return null
        val fileName = downloadFileName(sourceId, trackId, title, extensionForMimeType(mimeType))
        directory.listFiles().forEach { existing ->
            val name = existing.name ?: return@forEach
            if (isStagedDownloadFor(name, fileName)) existing.delete()
        }
        return directory.createFile(mimeType, "$fileName$PART_SUFFIX")
    }

    /**
     * A temporary download keeps the `.part` marker even though the storage provider appends its
     * own extension, so an interrupted transfer is never mistaken for playable music.
     */
    fun isPartialDownloadName(name: String): Boolean = PARTIAL_NAME.containsMatchIn(name)

    /** Deletes downloads interrupted by a crash or a killed process. Returns how many were removed. */
    fun deletePartialDownloads(context: Context, rootUri: Uri): Int {
        val directory = downloadsDirectory(context, rootUri) ?: return 0
        return directory.listFiles().count { file ->
            file.name?.let(::isPartialDownloadName) == true && file.delete()
        }
    }

    fun finishDownload(file: DocumentFile, sourceId: String, trackId: String, title: String, mimeType: String): DocumentFile? {
        val finalName = downloadFileName(sourceId, trackId, title, extensionForMimeType(mimeType))
        val occupiedNames = file.parentFile
            ?.listFiles()
            ?.asSequence()
            ?.filterNot { it.uri == file.uri }
            ?.mapNotNull { it.name }
            ?.toSet()
            .orEmpty()
        return if (file.renameTo(availableCompletedDownloadName(finalName, occupiedNames))) file else null
    }

    private const val PART_SUFFIX = ".part"

    /** `.part` at the end, or before the extension the provider appended (`.part.mp3`,
     *  `.part (1).mp3`) — but not inside an ordinary word like `mix.party.mp3`. */
    private val PARTIAL_NAME = Regex("""\.part($|[.\s(])""", RegexOption.IGNORE_CASE)

    internal fun isStagedDownloadFor(name: String, finalName: String): Boolean =
        name.startsWith(finalName) && isPartialDownloadName(name)

    internal fun availableCompletedDownloadName(finalName: String, occupiedNames: Set<String>): String {
        if (finalName !in occupiedNames) return finalName
        val dot = finalName.lastIndexOf('.')
        val stem = if (dot > 0) finalName.substring(0, dot) else finalName
        val extension = if (dot > 0) finalName.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = "$stem (replacement $index)$extension"
            if (candidate !in occupiedNames) return candidate
            index++
        }
    }

    internal fun downloadFileName(sourceId: String, trackId: String, title: String, extension: String): String {
        val identity = "$sourceId\u0000$trackId".toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(identity).joinToString("") { "%02x".format(it) }.take(12)
        val safeTitle = title.trim().replace(Regex("[^A-Za-z0-9._ -]"), "_").take(72).trim().ifBlank { "track" }
        return "$safeTitle-$digest.$extension"
    }

    private fun extensionForMimeType(mimeType: String): String = when (mimeType.lowercase()) {
        "audio/flac" -> "flac"
        "audio/ogg" -> "ogg"
        "audio/opus" -> "opus"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/mp4", "audio/aac" -> "m4a"
        else -> "mp3"
    }

    private const val DAY_MS = 24 * 60 * 60 * 1000L
}
