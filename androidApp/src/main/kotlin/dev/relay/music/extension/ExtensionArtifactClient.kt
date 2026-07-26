package dev.relay.music.extension

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ArtifactDownloadResult {
    data class Success(val file: File) : ArtifactDownloadResult
    data class Failure(val message: String) : ArtifactDownloadResult
}

/** Downloads a catalog artifact only after its catalog constraints can be enforced locally. */
class ExtensionArtifactClient(context: Context) {
    private val artifactDirectory = File(context.cacheDir, "relay/extension-artifacts")

    suspend fun download(
        entry: ExtensionCatalogEntry,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): ArtifactDownloadResult = withContext(Dispatchers.IO) {
        runCatching {
            require(entry.validate() == null) { entry.validate() ?: "Extension artifact is invalid." }
            artifactDirectory.mkdirs()
            require(artifactDirectory.isDirectory) { "Could not prepare extension cache." }
            val artifact = File(artifactDirectory, "${entry.id}-${entry.artifactSha256}.artifact")
            if (artifact.isFile && artifact.length() == entry.artifactSizeBytes && artifact.sha256() == entry.artifactSha256) {
                onProgress(entry.artifactSizeBytes, entry.artifactSizeBytes)
                return@withContext ArtifactDownloadResult.Success(artifact)
            }
            artifact.delete()

            val temporary = File(artifactDirectory, ".${entry.id}-${entry.artifactSha256}.part")
            temporary.delete()
            try {
                val connection = openHttps(entry.artifactUrl)
                try {
                    require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                        "Extension artifact returned HTTP ${connection.responseCode}."
                    }
                    require(connection.contentLengthLong < 0 || connection.contentLengthLong == entry.artifactSizeBytes) {
                        "Extension artifact size does not match its catalog."
                    }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var bytesRead = 0L
                    var lastReportedBytes = 0L
                    connection.inputStream.use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                bytesRead += count
                                require(bytesRead <= entry.artifactSizeBytes) { "Extension artifact is too large." }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                                if (bytesRead - lastReportedBytes >= PROGRESS_STEP_BYTES || bytesRead == entry.artifactSizeBytes) {
                                    onProgress(bytesRead, entry.artifactSizeBytes)
                                    lastReportedBytes = bytesRead
                                }
                            }
                        }
                    }
                    require(bytesRead == entry.artifactSizeBytes) { "Extension artifact size does not match its catalog." }
                    require(digest.digest().hex() == entry.artifactSha256) { "Extension artifact digest is invalid." }
                    require(temporary.renameTo(artifact)) { "Could not save extension artifact." }
                    ArtifactDownloadResult.Success(artifact)
                } finally {
                    connection.disconnect()
                }
            } finally {
                temporary.delete()
            }
        }.getOrElse { error -> ArtifactDownloadResult.Failure(error.message ?: "Could not download extension artifact.") }
    }
}

private fun openHttps(url: String): HttpURLConnection {
    var current = URL(url)
    repeat(MAX_REDIRECTS + 1) {
        require(current.protocol == "https" && current.host.isNotBlank() && current.userInfo == null) { "Extension artifact must use HTTPS." }
        val connection = (current.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = false
        }
        if (connection.responseCode !in 300..399) return connection
        current = checkedRedirectUrl(current, connection.getHeaderField("Location"))
        connection.disconnect()
    }
    error("Extension artifact redirected too many times.")
}

internal fun checkedRedirectUrl(current: URL, location: String?): URL {
    val next = URL(current, location?.takeIf { it.isNotBlank() } ?: error("Extension artifact redirect is invalid."))
    require(next.protocol == "https" && next.host.isNotBlank() && next.userInfo == null) { "Extension artifact redirect must use HTTPS." }
    return next
}

private fun File.sha256(): String = inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    digest.digest().hex()
}

internal fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).hex()

private fun ByteArray.hex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private const val MAX_REDIRECTS = 3
private const val PROGRESS_STEP_BYTES = 128 * 1024L
