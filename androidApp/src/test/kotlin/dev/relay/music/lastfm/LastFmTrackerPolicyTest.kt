package dev.relay.music.lastfm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class LastFmTrackerPolicyTest {
    @Test
    fun explicitTransitionStartsANewOccurrenceEvenForTheSameQueueItem() {
        val occurrence = PlaybackOccurrence("local:42", 0)

        assertFalse(playbackOccurrenceChanged(occurrence, occurrence, explicitTransition = false))
        assertTrue(playbackOccurrenceChanged(occurrence, occurrence, explicitTransition = true))
        assertTrue(playbackOccurrenceChanged(occurrence, PlaybackOccurrence("local:42", 1), explicitTransition = false))
    }

    @Test
    fun onlyTemporaryFailuresStayQueued() {
        assertEquals(PendingFailureAction.RETAIN_AND_STOP, pendingFailureAction(LastFmResult.Kind.TEMPORARY))
        assertEquals(PendingFailureAction.INVALIDATE_AND_STOP, pendingFailureAction(LastFmResult.Kind.INVALID_SESSION))
        assertEquals(PendingFailureAction.DISCARD_AND_CONTINUE, pendingFailureAction(LastFmResult.Kind.PERMANENT))
    }
}
