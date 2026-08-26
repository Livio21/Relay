package dev.relay.music

import dev.relay.music.wallpaper.WallpaperAnchor
import dev.relay.music.wallpaper.WallpaperElement
import dev.relay.music.wallpaper.WallpaperElementLayout
import dev.relay.music.wallpaper.formatClock
import dev.relay.music.wallpaper.moveElementLayoutBy
import dev.relay.music.wallpaper.resizeElementLayoutBy
import dev.relay.music.wallpaper.snapNormalized
import kotlin.test.Test
import kotlin.test.assertEquals

class WallpaperEditorMathTest {
    private val title = WallpaperElement.Title()

    @Test
    fun movesShiftTheAnchorPointAndClampToTheCanvas() {
        val moved = moveElementLayoutBy(title.layout, 0.05f, -0.03f)
        assertEquals(title.layout.x + 0.05f, moved.x)
        assertEquals(title.layout.y - 0.03f, moved.y)

        val clamped = moveElementLayoutBy(WallpaperElementLayout(x = 0.98f, y = 0.02f), 0.2f, -0.2f)
        assertEquals(1f, clamped.x)
        assertEquals(0f, clamped.y)
    }

    @Test
    fun resizesKeepTheModelMinimumsAndMaximums() {
        val grown = resizeElementLayoutBy(title.layout, 0.5f, 0.5f)
        assertEquals(1f, grown.width)
        assertEquals(0.58f, grown.height)

        val shrunk = resizeElementLayoutBy(grown, -3f, -3f)
        assertEquals(0.01f, shrunk.width)
        assertEquals(0.005f, shrunk.height)
    }

    @Test
    fun snappingPullsTowardTheCenterLineOnlyWhenClose() {
        assertEquals(0.5f, snapNormalized(0.51f))
        assertEquals(0.5f, snapNormalized(0.485f))
        assertEquals(0.44f, snapNormalized(0.44f))
        assertEquals(0.9f, snapNormalized(0.9f))
    }

    @Test
    fun clockFormatsMinutesOfTheDay() {
        // Epoch minute 360 is six hours in; 1439 is the last minute of a day.
        assertEquals("06:00", formatClock(360L * 60_000L))
        assertEquals("23:59", formatClock(1439L * 60_000L))
        assertEquals("00:00", formatClock(0))
    }
}
