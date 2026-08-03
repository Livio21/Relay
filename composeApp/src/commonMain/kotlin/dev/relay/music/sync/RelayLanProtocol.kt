package dev.relay.music.sync

/** Versioned wire names shared by platform LAN adapters; values are part of Relay's compatibility contract. */
object RelayLanProtocol {
    const val DATA_SYNC_V1 = "RELAY_LAN_SYNC_1"
    const val PLAY_TOGETHER_V1 = "RELAY_PLAY_TOGETHER_1"
    const val HOST = "HOST"
    const val CLIENT = "CLIENT"

    fun authenticatedFrameContext(protocol: String, sender: String, channel: String, sequence: Long): ByteArray {
        require(protocol == DATA_SYNC_V1 || protocol == PLAY_TOGETHER_V1)
        require(sender == HOST || sender == CLIENT)
        require(channel.matches(Regex("[a-z-]{1,32}")) && sequence >= 0)
        return "$protocol:$sender:$channel:$sequence".encodeToByteArray()
    }
}
