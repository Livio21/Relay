package dev.relay.music.library

import java.io.File
import java.net.URI

/** Resolves only files already held in Relay's private artwork cache. */
internal class LocalArtworkCache(artworkDirectory: File) {
    private val root = runCatching { artworkDirectory.canonicalFile }.getOrNull()

    fun cacheKey(reference: String?): String? {
        val file = resolve(reference) ?: return null
        return runCatching { file.relativeTo(requireNotNull(root)).invariantSeparatorsPath }.getOrNull()
    }

    fun resolve(cacheKey: String?): File? {
        val value = cacheKey?.takeIf(String::isNotBlank) ?: return null
        val cacheRoot = root ?: return null
        val candidate = runCatching {
            if (value.startsWith("file:", ignoreCase = true)) File(URI(value)) else File(cacheRoot, value)
        }.getOrNull() ?: return null
        val file = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        return file.takeIf {
            it.isFile && (it == cacheRoot || it.path.startsWith(cacheRoot.path + File.separator))
        }
    }
}
