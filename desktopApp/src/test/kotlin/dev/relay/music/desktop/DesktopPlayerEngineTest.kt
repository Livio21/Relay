package dev.relay.music.desktop

import dev.relay.music.model.Track
import dev.relay.music.playback.RepeatMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopPlayerEngineTest {
    @Test
    fun completionAndManualNavigationRespectRepeatModes() {
        assertEquals(1, completionTarget(0, 2, RepeatMode.OFF))
        assertEquals(null, completionTarget(1, 2, RepeatMode.OFF))
        assertEquals(0, completionTarget(1, 2, RepeatMode.ALL))
        assertEquals(1, completionTarget(1, 2, RepeatMode.ONE))
        assertEquals(0, manualMoveTarget(1, 2, 1, wrap = true))
        assertEquals(null, manualMoveTarget(1, 2, 1, wrap = false))
    }

    @Test
    fun emptyQueueIsIdleAndSkippingWhilePausedStaysPaused() {
        val audio = FakeAudio()
        val engine = DesktopPlayerEngine { audio }
        val files = List(2) { File.createTempFile("relay-desktop-test-$it", ".mp3").apply { deleteOnExit() } }
        val tracks = files.mapIndexed { index, file ->
            Track("$index", "desktop-local", file.toURI().toString(), "Track $index", "Artist")
        }
        engine.setQueue(tracks, 0, playWhenReady = false)
        engine.skipNext()
        assertEquals(1, engine.state.value.currentIndex)
        assertFalse(engine.state.value.isPlaying)

        engine.setQueue(emptyList(), 0)
        assertTrue(engine.state.value.queue.isEmpty())
        assertFalse(engine.state.value.isPlaying)
        engine.release()
        assertTrue(audio.closed)
    }

    @Test
    fun completedTrackAdvancesAndRepeatOneRestarts() {
        val audio = FakeAudio()
        val engine = DesktopPlayerEngine { audio }
        val files = List(2) { File.createTempFile("relay-desktop-complete-$it", ".mp3").apply { deleteOnExit() } }
        val tracks = files.mapIndexed { index, file ->
            Track("$index", "desktop-local", file.toURI().toString(), "Track $index", "Artist")
        }
        engine.setQueue(tracks, 0, playWhenReady = true)
        audio.position = audio.duration
        engine.pollPlayback()
        assertEquals(1, engine.state.value.currentIndex)

        engine.setRepeatMode(RepeatMode.ONE)
        audio.position = audio.duration
        engine.pollPlayback()
        assertEquals(1, engine.state.value.currentIndex)
        assertTrue(engine.state.value.isPlaying)
        assertEquals(0, engine.state.value.positionMs)
        engine.release()
    }

    private class FakeAudio : DesktopAudio {
        var duration = 10_000L
        var position = 0L
        var closed = false

        override fun load(file: File): Boolean { position = 0; return true }
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) { position = positionMs }
        override fun positionMs(): Long = position
        override fun durationMs(): Long = duration
        override fun setPlaybackSpeed(speed: Float) = Unit
        override fun close() { closed = true }
    }
}
