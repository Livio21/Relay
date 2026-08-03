package dev.relay.music.wallpaper

import dev.relay.music.library.LocalArtworkCache
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlbumWallpaperServiceTest {
    @Test
    fun artworkCacheAcceptsOnlyFilesInsideRelayCache() {
        val root = createTempDirectory("relay-artwork-").toFile()
        val image = File(root, "cover.jpg").apply { writeBytes(byteArrayOf(1)) }
        val cache = LocalArtworkCache(root)
        assertEquals("cover.jpg", cache.cacheKey(image.toURI().toString()))
        assertEquals(image.canonicalFile, cache.resolve("cover.jpg"))
        assertEquals(image.canonicalFile, cache.resolve(image.toURI().toString()))
        assertNull(cache.resolve("https://example.com/cover.jpg"))
        assertNull(cache.resolve(File(root.parentFile, "outside.jpg").toURI().toString()))
    }

    @Test
    fun sampleSizeOnlyDownsamplesOversizedArt() {
        assertEquals(1, sampleSize(1000, 1000, 600, 600))
        assertEquals(4, sampleSize(4000, 4000, 600, 600))
    }
}
