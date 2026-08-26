package dev.relay.music

import dev.relay.music.sync.LanPairingLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanPairingLinkTest {
    @Test
    fun encodesAndParsesRoundTrip() {
        val link = LanPairingLink(LanPairingLink.Mode.SYNC, "192.168.1.12", 38400)
        assertEquals(link, LanPairingLink.parse(link.encode()))
        val play = LanPairingLink(LanPairingLink.Mode.PLAY, "my-phone.local", 65535)
        assertEquals(play, LanPairingLink.parse(play.encode()))
    }

    @Test
    fun rejectsMalformedOrForeignPayloads() {
        assertNull(LanPairingLink.parse(null))
        assertNull(LanPairingLink.parse(""))
        assertNull(LanPairingLink.parse("https://example.com/evil"))
        assertNull(LanPairingLink.parse("relay://lan/join?mode=teleport&address=1.2.3.4:5"))
        assertNull(LanPairingLink.parse("relay://lan/join?mode=sync&address=1.2.3.4:99999"))
        assertNull(LanPairingLink.parse("relay://lan/join?mode=sync&address=1.2.3.4:0"))
        assertNull(LanPairingLink.parse("relay://lan/join?address=1.2.3.4:38400"))
        // Path traversal or whitespace tricks in the host never survive parsing.
        assertNull(LanPairingLink.parse("relay://lan/join?mode=sync&address=../etc:80"))
    }

    @Test
    fun addressJoinsForExistingTransportEntryPoints() {
        val link = LanPairingLink.parse(
            LanPairingLink(LanPairingLink.Mode.SYNC, "192.168.1.12", 38400).encode(),
        )
        requireNotNull(link)
        assertEquals("192.168.1.12:38400", link.address())
    }
}
