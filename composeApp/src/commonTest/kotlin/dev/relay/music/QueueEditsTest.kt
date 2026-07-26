package dev.relay.music

import dev.relay.music.model.Track
import dev.relay.music.playback.appendToQueue
import dev.relay.music.playback.moveInQueue
import dev.relay.music.playback.playNextInQueue
import dev.relay.music.playback.removeFromQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueueEditsTest {
    private fun track(id: String) = Track(
        id = id,
        sourceId = "local",
        playbackUri = "content://media/$id",
        title = "Track $id",
        artist = "Artist",
    )

    private val queue = listOf("a", "b", "c", "d").map(::track)

    @Test
    fun removingBeforeTheCurrentTrackKeepsItPlaying() {
        val edit = removeFromQueue(queue, currentIndex = 2, index = 0)!!

        assertEquals(listOf("b", "c", "d"), edit.queue.map { it.id })
        assertEquals(1, edit.currentIndex)
        assertEquals(track("c"), edit.queue[edit.currentIndex])
    }

    @Test
    fun removingTheCurrentTrackAdvancesToTheNextOne() {
        val edit = removeFromQueue(queue, currentIndex = 1, index = 1)!!

        assertEquals(listOf("a", "c", "d"), edit.queue.map { it.id })
        assertEquals(track("c"), edit.queue[edit.currentIndex])
    }

    @Test
    fun removingTheLastRemainingTrackEmptiesTheQueue() {
        val edit = removeFromQueue(listOf(track("a")), currentIndex = 0, index = 0)!!

        assertTrue(edit.queue.isEmpty())
        assertTrue(edit.restartsPlayback)
        assertNull(removeFromQueue(queue, currentIndex = 0, index = 9))
    }

    @Test
    fun movingTracksKeepsTheSameTrackCurrent() {
        val moveCurrent = moveInQueue(queue, currentIndex = 1, from = 1, to = 3)!!
        assertEquals(listOf("a", "c", "d", "b"), moveCurrent.queue.map { it.id })
        assertEquals(track("b"), moveCurrent.queue[moveCurrent.currentIndex])

        val moveOverCurrent = moveInQueue(queue, currentIndex = 2, from = 0, to = 3)!!
        assertEquals(listOf("b", "c", "d", "a"), moveOverCurrent.queue.map { it.id })
        assertEquals(track("c"), moveOverCurrent.queue[moveOverCurrent.currentIndex])

        val moveUnderCurrent = moveInQueue(queue, currentIndex = 1, from = 3, to = 0)!!
        assertEquals(listOf("d", "a", "b", "c"), moveUnderCurrent.queue.map { it.id })
        assertEquals(track("b"), moveUnderCurrent.queue[moveUnderCurrent.currentIndex])

        assertNull(moveInQueue(queue, currentIndex = 0, from = 1, to = 1))
        assertNull(moveInQueue(queue, currentIndex = 0, from = 0, to = 9))
    }

    @Test
    fun playNextInsertsAfterTheCurrentTrackAndQueueAppends() {
        val next = playNextInQueue(queue, currentIndex = 1, track = track("x"))
        assertEquals(listOf("a", "b", "x", "c", "d"), next.queue.map { it.id })
        assertEquals(track("b"), next.queue[next.currentIndex])

        val appended = appendToQueue(queue, currentIndex = 1, track = track("x"))
        assertEquals(listOf("a", "b", "c", "d", "x"), appended.queue.map { it.id })
        assertEquals(1, appended.currentIndex)
    }

    @Test
    fun queueingIntoAnEmptyQueueStartsPlayback() {
        assertEquals(QueueOf("x", 0), playNextInQueue(emptyList(), -1, track("x")).let { QueueOf(it.queue.single().id, it.currentIndex) })
        assertEquals(QueueOf("x", 0), appendToQueue(emptyList(), -1, track("x")).let { QueueOf(it.queue.single().id, it.currentIndex) })
    }

    private data class QueueOf(val id: String, val index: Int)
}
