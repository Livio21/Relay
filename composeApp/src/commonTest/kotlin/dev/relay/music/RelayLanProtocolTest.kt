package dev.relay.music

import dev.relay.music.sync.RelayLanProtocol
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class RelayLanProtocolTest {
    @Test fun authenticatedContextIsStableAndRejectsInvalidFrames() {
        assertContentEquals(
            "RELAY_LAN_SYNC_1:HOST:data:7".encodeToByteArray(),
            RelayLanProtocol.authenticatedFrameContext(RelayLanProtocol.DATA_SYNC_V1, RelayLanProtocol.HOST, "data", 7),
        )
        assertFailsWith<IllegalArgumentException> {
            RelayLanProtocol.authenticatedFrameContext("unknown", RelayLanProtocol.HOST, "data", 0)
        }
    }
}
