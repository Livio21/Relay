package dev.relay.music.metadata

import dev.relay.music.model.MetadataCandidate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MusicBrainzApiTest {
    @Test
    fun normalizedMetadataDurationAndOrdinalsRankDeterministically() {
        val target = target(title = "Beyoncé – Halo", artist = "Beyoncé", album = "I Am… Sasha Fierce")
        val exact = candidate(
            title = "Beyonce Halo",
            artist = "BEYONCE",
            album = "I Am Sasha Fierce",
            recordingId = "exact",
            durationMs = 261_000,
            trackNumber = 2,
            discNumber = 1,
        )
        val wrong = candidate(
            title = "Halo (Live)",
            artist = "Another Artist",
            album = "Live",
            recordingId = "wrong",
            durationMs = 320_000,
            trackNumber = 8,
            discNumber = 2,
        )

        val ranking = rankMetadataCandidates(target, listOf(wrong, exact))

        assertEquals("exact", ranking.candidates.first().recordingId)
        assertFalse(ranking.reviewRequired)
    }

    @Test
    fun existingRecordingAndReleaseIdentityOutrankTextOnlyMatch() {
        val target = target(recordingId = "recording-match", releaseId = "release-match")
        val identityMatch = candidate(
            title = "Track (album mix)",
            recordingId = "recording-match",
            releaseId = "release-match",
        )
        val textOnly = candidate(recordingId = "other-recording", releaseId = "other-release")

        assertEquals(
            "recording-match",
            rankMetadataCandidates(target, listOf(textOnly, identityMatch)).candidates.first().recordingId,
        )
    }

    @Test
    fun tiedProviderResultsRemainReviewOnlyWithStableOrdering() {
        val firstInput = candidate(recordingId = "z", releaseId = "release-z")
        val secondInput = candidate(recordingId = "a", releaseId = "release-a")

        val ranking = rankMetadataCandidates(target(), listOf(firstInput, secondInput))

        assertTrue(ranking.reviewRequired)
        assertEquals(listOf("a", "z"), ranking.candidates.map { it.recordingId })
    }

    @Test
    fun retryAfterSupportsSecondsAndHttpDatesWithinBound() {
        assertEquals(3_000L, parseRetryAfterMillis("3", nowEpochMs = 0))
        assertEquals(MAX_PROVIDER_RETRY_DELAY_MS, parseRetryAfterMillis("999", nowEpochMs = 0))
        assertEquals(null, parseRetryAfterMillis("not-a-date", nowEpochMs = 0))

        val date = "Sun, 06 Nov 1994 08:49:37 GMT"
        val epoch = ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        assertEquals(5_000L, parseRetryAfterMillis(date, nowEpochMs = epoch - 5_000))
    }

    @Test
    fun cacheKeysNormalizeTextAndUrlsButRetainMatchRelevantInputs() {
        val normalized = target(title = "Beyoncé - Halo", artist = "BEYONCÉ")
        val equivalent = target(title = "beyonce halo", artist = "beyonce")
        assertEquals(metadataSearchCacheKey(normalized), metadataSearchCacheKey(equivalent))
        assertNotEquals(
            metadataSearchCacheKey(normalized),
            metadataSearchCacheKey(normalized.copy(durationMs = normalized.durationMs?.plus(1))),
        )
        assertEquals(
            artworkCacheKey("https://EXAMPLE.com:443/art/../cover.jpg#preview"),
            artworkCacheKey("https://example.com/cover.jpg"),
        )
        assertNotEquals(
            artworkCacheKey("https://example.com/cover.jpg?size=250"),
            artworkCacheKey("https://example.com/cover.jpg?size=500"),
        )
    }

    @Test
    fun concurrentArtworkRequestsShareOneLoaderWithoutNetwork() {
        val deduplicator = ArtworkRequestDeduplicator()
        val calls = AtomicInteger()
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondThread = AtomicReference<Thread>()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<String> {
                deduplicator.run("cover") {
                    calls.incrementAndGet()
                    firstEntered.countDown()
                    assertTrue(releaseFirst.await(2, TimeUnit.SECONDS))
                    "cached-artwork"
                }
            }
            assertTrue(firstEntered.await(2, TimeUnit.SECONDS))
            val second = executor.submit<String> {
                secondThread.set(Thread.currentThread())
                deduplicator.run("cover") {
                    calls.incrementAndGet()
                    "unexpected-second-load"
                }
            }
            assertTrue(waitUntilWaiting(secondThread, 2_000))
            releaseFirst.countDown()

            assertEquals("cached-artwork", first.get(2, TimeUnit.SECONDS))
            assertEquals("cached-artwork", second.get(2, TimeUnit.SECONDS))
            assertEquals(1, calls.get())
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    private fun target(
        title: String = "Track",
        artist: String = "Artist",
        album: String? = "Album",
        recordingId: String? = null,
        releaseId: String? = null,
    ) = MetadataSearchTarget(
        title = title,
        artist = artist,
        album = album,
        durationMs = 260_000,
        trackNumber = 2,
        discNumber = 1,
        recordingId = recordingId,
        releaseId = releaseId,
    )

    private fun candidate(
        title: String = "Track",
        artist: String = "Artist",
        album: String? = "Album",
        recordingId: String,
        releaseId: String? = null,
        durationMs: Long? = 260_000,
        trackNumber: Int? = 2,
        discNumber: Int? = 1,
    ) = MetadataCandidate(
        title = title,
        artist = artist,
        album = album,
        albumArtist = artist,
        recordingId = recordingId,
        releaseId = releaseId,
        durationMs = durationMs,
        trackNumber = trackNumber,
        discNumber = discNumber,
    )

    private fun waitUntilWaiting(thread: AtomicReference<Thread>, timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            when (thread.get()?.state) {
                Thread.State.WAITING, Thread.State.TIMED_WAITING -> return true
                else -> Thread.yield()
            }
        }
        return false
    }
}
