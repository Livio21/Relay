package dev.relay.music.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.relay.music.model.Track
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

sealed interface RemoteTrackDownloadResult {
    data class Success(
        val documentUri: String,
        val mimeType: String,
        val sizeBytes: Long,
    ) : RemoteTrackDownloadResult

    data class Failure(val message: String) : RemoteTrackDownloadResult
}

/** Downloads a selected public source stream into Relay's managed downloads directory. */
class RemoteTrackDownloadClient(private val context: Context) {
    suspend fun download(
        track: Track,
        downloadUrl: String,
        rootUri: Uri,
        headers: Map<String, String> = emptyMap(),
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): RemoteTrackDownloadResult = withContext(Dispatchers.IO) {
        if (!downloadUrl.startsWith("https://")) return@withContext RemoteTrackDownloadResult.Failure("Download URL must use HTTPS.")
        var output: DocumentFile? = null
        try {
            val connection = openSecureConnection(downloadUrl, headers)
            try {
                if (connection.responseCode !in 200..299) error("Download returned HTTP ${connection.responseCode}.")
                val mimeType = connection.contentType?.substringBefore(';')?.lowercase().orEmpty()
                if (mimeType.isNotBlank() && !mimeType.startsWith("audio/") && mimeType != "application/octet-stream") {
                    error("Download did not return an audio file.")
                }
                val safeMimeType = mimeType.ifBlank { "audio/mpeg" }
                val totalBytes = connection.contentLengthLong
                if (totalBytes > MAX_DOWNLOAD_BYTES) error("Download is larger than Relay's 1 GB limit.")
                val partFile = RelayStorage.createDownload(context, rootUri, track.sourceId, track.id, track.title, safeMimeType)
                    ?: error("Relay downloads folder is unavailable.")
                output = partFile
                context.contentResolver.openOutputStream(partFile.uri, "w")?.use { destination ->
                    connection.inputStream.use { source ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = source.read(buffer)
                            if (read < 0) break
                            downloaded += read
                            if (downloaded > MAX_DOWNLOAD_BYTES) error("Download is larger than Relay's 1 GB limit.")
                            destination.write(buffer, 0, read)
                            onProgress(downloaded, totalBytes)
                        }
                        destination.flush()
                        val completed = RelayStorage.finishDownload(partFile, track.sourceId, track.id, track.title, safeMimeType)
                            ?: error("Could not finalize the downloaded file.")
                        output = null
                        RemoteTrackDownloadResult.Success(completed.uri.toString(), safeMimeType, downloaded)
                    }
                } ?: error("Could not create the downloaded file.")
            } finally {
                connection.disconnect()
            }
        } catch (error: Throwable) {
            output?.delete()
            if (error is CancellationException) throw error
            RemoteTrackDownloadResult.Failure(error.message ?: "Download failed.")
        }
    }

    private fun openSecureConnection(initialUrl: String, headers: Map<String, String>): HttpURLConnection {
        var url = URL(initialUrl)
        repeat(MAX_REDIRECTS + 1) { redirect ->
            require(url.protocol == "https") { "Download redirected away from HTTPS." }
            val connection = (url.openConnection() as? HttpURLConnection) ?: error("Download URL is invalid.")
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            when (connection.responseCode) {
                in 300..399 -> {
                    if (redirect == MAX_REDIRECTS) {
                        connection.disconnect()
                        error("Download redirected too many times.")
                    }
                    val location = connection.getHeaderField("Location") ?: run {
                        connection.disconnect()
                        error("Download redirect had no location.")
                    }
                    url = URL(url, location)
                    connection.disconnect()
                }
                else -> return connection
            }
        }
        error("Download redirected too many times.")
    }

    private companion object {
        const val BUFFER_SIZE = 32 * 1024
        const val MAX_DOWNLOAD_BYTES = 1_073_741_824L
        const val MAX_REDIRECTS = 3
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val USER_AGENT = "Relay/0.1 Android remote download"
    }
}
