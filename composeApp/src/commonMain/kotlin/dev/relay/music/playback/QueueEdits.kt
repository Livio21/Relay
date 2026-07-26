package dev.relay.music.playback

import dev.relay.music.model.Track

/** A queue plus the position that should stay current after an edit. */
data class QueueEdit(
    val queue: List<Track>,
    val currentIndex: Int,
) {
    /** True when the edit removed or replaced the track that was playing. */
    val restartsPlayback: Boolean
        get() = currentIndex < 0 || queue.isEmpty()
}

fun removeFromQueue(queue: List<Track>, currentIndex: Int, index: Int): QueueEdit? {
    if (index !in queue.indices) return null
    val next = queue.toMutableList().apply { removeAt(index) }
    val nextIndex = when {
        next.isEmpty() -> -1
        index < currentIndex -> currentIndex - 1
        else -> currentIndex.coerceAtMost(next.lastIndex)
    }
    return QueueEdit(next, nextIndex)
}

fun moveInQueue(queue: List<Track>, currentIndex: Int, from: Int, to: Int): QueueEdit? {
    if (from !in queue.indices || to !in queue.indices || from == to) return null
    val next = queue.toMutableList().apply { add(to, removeAt(from)) }
    val nextIndex = when {
        currentIndex < 0 -> currentIndex
        currentIndex == from -> to
        from < currentIndex && to >= currentIndex -> currentIndex - 1
        from > currentIndex && to <= currentIndex -> currentIndex + 1
        else -> currentIndex
    }
    return QueueEdit(next, nextIndex)
}

/** Inserts directly after the playing track, so it is the next thing heard. */
fun playNextInQueue(queue: List<Track>, currentIndex: Int, track: Track): QueueEdit {
    if (queue.isEmpty() || currentIndex !in queue.indices) return QueueEdit(listOf(track), 0)
    val next = queue.toMutableList().apply { add(currentIndex + 1, track) }
    return QueueEdit(next, currentIndex)
}

fun appendToQueue(queue: List<Track>, currentIndex: Int, track: Track): QueueEdit {
    if (queue.isEmpty() || currentIndex !in queue.indices) return QueueEdit(listOf(track), 0)
    return QueueEdit(queue + track, currentIndex)
}
