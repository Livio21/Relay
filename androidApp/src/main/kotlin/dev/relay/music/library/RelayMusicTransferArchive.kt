package dev.relay.music.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/** Selected user music only. Remote caches, streams, and credentials never enter this archive. */
object RelayMusicTransferArchive {
    private const val MANIFEST = "manifest.json"
    private const val MAX_FILES = 64
    private const val MAX_FILE_BYTES = 2L * 1024 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 8L * 1024 * 1024 * 1024

    fun write(context: Context, uris: List<Uri>, output: OutputStream) {
        require(uris.size in 1..MAX_FILES) { "Select between 1 and $MAX_FILES files." }
        val items = uris.mapIndexed { index, uri -> scan(context.contentResolver, uri, index) }
        require(items.sumOf { it.sizeBytes } <= MAX_TOTAL_BYTES) { "Selected music is too large." }
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(JSONArray().apply { items.forEach { put(it.toJson()) } }.toString().encodeToByteArray())
            zip.closeEntry()
            items.forEach { item ->
                zip.putNextEntry(ZipEntry(item.entryName))
                context.contentResolver.openInputStream(requireNotNull(item.uri))?.use { input -> input.copyTo(zip) }
                    ?: error("Could not read selected music.")
                zip.closeEntry()
            }
        }
    }

    fun read(context: Context, input: InputStream, rootUri: Uri): Int {
        val music = RelayStorage.musicDirectory(context, rootUri) ?: error("Relay music folder is unavailable.")
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            val manifestEntry = zip.nextEntry ?: error("Music transfer has no manifest.")
            require(manifestEntry.name == MANIFEST) { "Invalid music transfer order." }
            val items = parseManifest(zip.readLimited(64 * 1024).decodeToString())
            var total = 0L
            items.forEach { item ->
                val entry = zip.nextEntry ?: error("Music transfer is incomplete.")
                require(entry.name == item.entryName) { "Music transfer entry mismatch." }
                val partial = music.createFile(item.mimeType, "${item.fileName}.part") ?: error("Could not create music file.")
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    var bytes = 0L
                    context.contentResolver.openOutputStream(partial.uri)?.use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            bytes += count
                            require(bytes <= item.sizeBytes && bytes <= MAX_FILE_BYTES) { "Music file exceeds its manifest size." }
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                        }
                    } ?: error("Could not write music file.")
                    require(bytes == item.sizeBytes && digest.digest().hex() == item.sha256) { "Music file checksum failed." }
                    total += bytes
                    require(total <= MAX_TOTAL_BYTES) { "Music transfer is too large." }
                    require(partial.renameTo(item.fileName)) { "Could not finalize music file." }
                } catch (error: Throwable) {
                    partial.delete()
                    throw error
                }
            }
            return items.size
        }
    }

    private fun scan(resolver: ContentResolver, uri: Uri, index: Int): Item {
        val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> cursor.moveToFirst().takeIf { it }?.let { cursor.getString(0) } }
            ?.replace(Regex("[^A-Za-z0-9._ -]"), "_")?.take(100)?.ifBlank { "track" } ?: "track"
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                size += count
                require(size <= MAX_FILE_BYTES) { "A selected music file is too large." }
                digest.update(buffer, 0, count)
            }
        } ?: error("Could not read selected music.")
        val mime = resolver.getType(uri)?.takeIf { it.startsWith("audio/") } ?: "audio/mpeg"
        val checksum = digest.digest().hex()
        return Item(uri, "audio/${index.toString().padStart(3, '0')}", safeFileName(name, checksum), mime, size, checksum)
    }

    private fun parseManifest(value: String): List<Item> {
        val json = JSONArray(value)
        require(json.length() in 1..MAX_FILES) { "Music transfer file count is invalid." }
        return List(json.length()) { index -> Item.fromJson(json.getJSONObject(index)) }.also { items ->
            require(items.map { it.entryName }.distinct().size == items.size) { "Music transfer contains duplicate entries." }
        }
    }

    private fun safeFileName(name: String, digest: String): String {
        val stem = name.substringBeforeLast('.', name).take(80).ifBlank { "track" }
        val extension = name.substringAfterLast('.', "mp3").lowercase().take(8)
        return "$stem-${digest.take(12)}.$extension"
    }

    private data class Item(val uri: Uri?, val entryName: String, val fileName: String, val mimeType: String, val sizeBytes: Long, val sha256: String) {
        fun toJson() = JSONObject().put("entry", entryName).put("name", fileName).put("mime", mimeType).put("size", sizeBytes).put("sha", sha256)
        companion object {
            fun fromJson(json: JSONObject) = Item(null, json.getString("entry"), json.getString("name"), json.getString("mime"), json.getLong("size"), json.getString("sha")).also {
                require(it.entryName.matches(Regex("audio/[0-9]{3}")) && it.fileName.length <= 128 && it.mimeType.startsWith("audio/") && it.sizeBytes in 1..MAX_FILE_BYTES && it.sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid music transfer manifest." }
            }
        }
    }
}

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

private fun InputStream.readLimited(limit: Int): ByteArray {
    val result = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = read(buffer)
        if (count < 0) return result.toByteArray()
        require(result.size() + count <= limit) { "Music transfer manifest is too large." }
        result.write(buffer, 0, count)
    }
}
