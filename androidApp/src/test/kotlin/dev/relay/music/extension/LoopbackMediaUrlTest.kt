package dev.relay.music.extension

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** P2P sources stream from their own loopback server; every other cleartext URL stays rejected. */
class LoopbackMediaUrlTest {
    @Test
    fun httpsUrlsStayValid() {
        assertTrue("https://example.com/audio.mp3".isValidMediaUrl())
    }

    @Test
    fun loopbackHttpUrlsAreValid() {
        assertTrue("http://127.0.0.1:52341/s/17".isValidMediaUrl())
        assertTrue("http://localhost:8080/stream".isValidMediaUrl())
        assertTrue("http://127.0.0.1/s/17".isValidMediaUrl())
    }

    @Test
    fun otherCleartextHostsStayRejected() {
        assertFalse("http://192.168.1.10:5030/stream".isValidMediaUrl())
        assertFalse("http://localhost.evil.example/stream".isValidMediaUrl())
        assertFalse("http://user@127.0.0.1/stream".isValidMediaUrl())
        assertFalse("ftp://127.0.0.1/stream".isValidMediaUrl())
    }

    @Test
    fun loopbackDetectionIsHostExact() {
        assertTrue("http://127.0.0.1:1/x".isLoopbackHttpUrl())
        assertFalse("http://127.0.0.2:1/x".isLoopbackHttpUrl())
        assertFalse("http://127.0.0.1.example/x".isLoopbackHttpUrl())
        assertFalse("https://127.0.0.1/x".isLoopbackHttpUrl())
    }
}
