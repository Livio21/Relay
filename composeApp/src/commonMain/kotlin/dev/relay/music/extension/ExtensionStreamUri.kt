package dev.relay.music.extension

import dev.relay.music.model.Track

const val EXTENSION_STREAM_SCHEME = "relay-extension"

/** Identifies one extension track well enough to re-resolve its stream after a restart. */
data class ExtensionStreamRef(
    val extensionId: String,
    val sourceId: String,
    val trackId: String,
) {
    /** The `Track.sourceId` these segments were derived from. */
    val trackSourceId: String get() = "extension:$extensionId:$sourceId"

    /** The `Track.id` these segments were derived from. */
    val hostTrackId: String get() = "$sourceId:$trackId"
}

/**
 * Placeholder URI stored in a queue or playlist instead of a real stream URL. The playback
 * service rewrites it to a fresh URL immediately before the track loads, so short-lived and
 * signed source URLs are never persisted.
 */
fun extensionStreamUri(extensionId: String, sourceId: String, trackId: String): String =
    "$EXTENSION_STREAM_SCHEME://${extensionId.encodeUriSegment()}/${sourceId.encodeUriSegment()}/${trackId.encodeUriSegment()}"

fun parseExtensionStreamUri(uri: String): ExtensionStreamRef? {
    val prefix = "$EXTENSION_STREAM_SCHEME://"
    if (!uri.startsWith(prefix)) return null
    val segments = uri.removePrefix(prefix).split('/')
    if (segments.size != 3) return null
    val decoded = segments.map { it.decodeUriSegment() ?: return null }
    if (decoded.any(String::isEmpty)) return null
    return ExtensionStreamRef(decoded[0], decoded[1], decoded[2])
}

/**
 * A placeholder for this track when it came from a source extension, else null.
 * `sourceId` format is `extension:<extensionId>:<inExtensionSourceId>`, and `id` is prefixed
 * with the in-extension source ID, which the source itself never sees.
 */
fun Track.extensionStreamPlaceholder(): String? {
    val parts = sourceId.split(':', limit = 3)
    if (parts.size != 3 || parts[0] != "extension") return null
    val (_, extensionId, inSourceId) = parts
    val trackId = id.removePrefix("$inSourceId:")
    if (extensionId.isEmpty() || inSourceId.isEmpty() || trackId.isEmpty()) return null
    return extensionStreamUri(extensionId, inSourceId, trackId)
}

private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

private fun String.encodeUriSegment(): String = buildString {
    this@encodeUriSegment.encodeToByteArray().forEach { byte ->
        val char = byte.toInt().toChar()
        if (byte >= 0 && char in UNRESERVED) {
            append(char)
        } else {
            append('%')
            append(HEX[(byte.toInt() shr 4) and 0xF])
            append(HEX[byte.toInt() and 0xF])
        }
    }
}

private fun String.decodeUriSegment(): String? {
    val bytes = ArrayList<Byte>(length)
    var index = 0
    while (index < length) {
        val char = this[index]
        when {
            char == '%' -> {
                if (index + 2 >= length) return null
                val value = substring(index + 1, index + 3).toIntOrNull(16) ?: return null
                bytes += value.toByte()
                index += 3
            }
            char.code in 1..127 -> {
                bytes += char.code.toByte()
                index += 1
            }
            else -> return null
        }
    }
    return runCatching { bytes.toByteArray().decodeToString() }.getOrNull()
}

private const val HEX = "0123456789ABCDEF"
