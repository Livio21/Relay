package dev.relay.music

import dev.relay.music.wallpaper.ArtworkFilter
import dev.relay.music.wallpaper.WallpaperAnchor
import dev.relay.music.wallpaper.WallpaperElement
import dev.relay.music.wallpaper.WallpaperElementLayout
import dev.relay.music.wallpaper.WallpaperPreset
import dev.relay.music.wallpaper.decodeWallpaperPreset
import dev.relay.music.wallpaper.encodeWallpaperPreset
import dev.relay.music.wallpaper.validationError
import dev.relay.music.wallpaper.wallpaperElementBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WallpaperPresetTest {
    @Test
    fun completeV1CompositionRoundTrips() {
        val preset = WallpaperPreset(
            elements = listOf(
                WallpaperElement.Artwork(), WallpaperElement.Title(), WallpaperElement.Artist(),
                WallpaperElement.Album(), WallpaperElement.Clock(), WallpaperElement.Progress(),
            ),
            filters = listOf(
                ArtworkFilter.Grayscale(), ArtworkFilter.Blur(), ArtworkFilter.Duotone(),
                ArtworkFilter.BrightnessContrast(), ArtworkFilter.Vignette(), ArtworkFilter.Grain(),
            ),
            showMetadata = true,
        )

        val decoded = decodeWallpaperPreset(encodeWallpaperPreset(preset))

        assertTrue(decoded.valid)
        assertEquals(preset, decoded.preset)
        assertNull(decoded.preset.validationError())
    }

    @Test
    fun unknownFutureKindsAreIgnoredWithAWarning() {
        val raw = encodeWallpaperPreset(WallpaperPreset())
            .replace("\"filters\":[]", "\"filters\":[{\"type\":\"future_shader\",\"source\":\"never execute\"}]")

        val decoded = decodeWallpaperPreset(raw)

        assertTrue(decoded.valid)
        assertTrue(decoded.preset.filters.isEmpty())
        assertTrue(decoded.warnings.single().contains("future_shader"))
    }

    @Test
    fun invalidKnownValuesUseTheSafeDefault() {
        val raw = encodeWallpaperPreset(WallpaperPreset()).replace("\"opacity\":1.0", "\"opacity\":9.0")

        val decoded = decodeWallpaperPreset(raw)

        assertFalse(decoded.valid)
        assertEquals(WallpaperPreset().elements, decoded.preset.elements)
        assertTrue(decoded.warnings.isNotEmpty())
    }

    @Test
    fun layoutMathRespectsNormalizedAnchor() {
        val bounds = wallpaperElementBounds(
            WallpaperElementLayout(x = 0.5f, y = 0.5f, width = 0.4f, height = 0.2f, anchor = WallpaperAnchor.CENTER),
            canvasWidth = 1000f,
            canvasHeight = 2000f,
        )

        assertEquals(300f, bounds.left)
        assertEquals(800f, bounds.top)
        assertEquals(400f, bounds.width)
        assertEquals(400f, bounds.height)
    }
}
