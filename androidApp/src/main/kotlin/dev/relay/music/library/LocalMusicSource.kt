package dev.relay.music.library

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.relay.music.model.Track
import dev.relay.music.source.MusicSource
import java.io.File
import java.security.MessageDigest
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMusicSource(
    private val context: Context,
    private val storageRootUri: () -> Uri?,
) : MusicSource {
    override val id = "local"
    override val displayName = "Relay music"

    override suspend fun tracks(): List<Track> = withContext(Dispatchers.IO) {
        val rootUri = storageRootUri() ?: return@withContext emptyList()
        val musicDirectory = RelayStorage.musicDirectory(context, rootUri)
            ?: error("Relay storage folder is unavailable. Choose it again in Settings.")
        val downloadsDirectory = RelayStorage.downloadsDirectory(context, rootUri)
        listOfNotNull(musicDirectory, downloadsDirectory).flatMap(::documentTracks).sortedBy { it.title.lowercase() }
    }

    private fun documentTracks(root: DocumentFile): List<Track> {
        val pending = ArrayDeque<DocumentFile>().apply { add(root) }
        return buildList {
            while (pending.isNotEmpty()) {
                val item = pending.removeLast()
                if (item.isDirectory) {
                    item.listFiles().forEach(pending::add)
                } else if (item.name?.let(RelayStorage::isPartialDownloadName) == true) {
                    // An interrupted download is not music, whatever its extension says.
                } else if (item.type?.startsWith("audio/") == true || item.name.isAudioFileName()) {
                    documentTrack(item)?.let(::add)
                }
            }
        }.sortedBy { it.title.lowercase() }
    }

    private fun documentTrack(file: DocumentFile): Track? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, file.uri)
            val embeddedArtwork = retriever.embeddedPicture
            val artwork = embeddedArtwork?.let { embeddedArtworkUri(file, it) }
            Track(
                id = file.uri.toString(),
                sourceId = id,
                playbackUri = file.uri.toString(),
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty(),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                releaseDate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                artworkUri = artwork,
                artworkHue = embeddedArtwork?.let(::artworkHue),
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')?.toIntOrNull(),
                discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                    ?.substringBefore('/')?.toIntOrNull(),
                sourceRevision = "${file.lastModified()}:${file.length()}",
            )
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun embeddedArtworkUri(file: DocumentFile, bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val fingerprint = "${file.uri}:${file.lastModified()}:${file.length()}".toByteArray()
        val name = MessageDigest.getInstance("SHA-256").digest(fingerprint).joinToString("") { "%02x".format(it) }
        val artworkFile = File(context.cacheDir, "relay-artwork/$name")
        artworkFile.parentFile?.mkdirs()
        if (!artworkFile.isFile || artworkFile.length() != bytes.size.toLong()) {
            artworkFile.outputStream().use { it.write(bytes) }
        }
        return Uri.fromFile(artworkFile).toString()
    }

    /**
     * Sample local embedded art only; shuffle never downloads artwork to manufacture a hue.
     * ponytail: local scans recompute this small bitmap sample; add a hue table only if scans become expensive.
     */
    private fun artworkHue(bytes: ByteArray): Int? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 64 || bounds.outHeight / sample > 64) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            var x = 0.0
            var y = 0.0
            pixels.forEach { color ->
                val hsv = FloatArray(3)
                Color.colorToHSV(color, hsv)
                val weight = hsv[1] * hsv[2]
                x += cos(Math.toRadians(hsv[0].toDouble())) * weight
                y += sin(Math.toRadians(hsv[0].toDouble())) * weight
            }
            if (x == 0.0 && y == 0.0) null else ((Math.toDegrees(atan2(y, x)) + 360) % 360).roundToInt()
        } finally {
            bitmap.recycle()
        }
    }
}

private fun String?.isAudioFileName(): Boolean = this
    ?.substringAfterLast('.', missingDelimiterValue = "")
    ?.lowercase() in setOf("aac", "flac", "m4a", "mp3", "ogg", "opus", "wav")
