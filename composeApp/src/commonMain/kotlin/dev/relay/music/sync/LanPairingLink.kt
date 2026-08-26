package dev.relay.music.sync

/**
 * The payload a hosting device shares so a peer can find it: a QR code, an NSD
 * advertisement, or pasted text all reduce to this. The link only names where to
 * connect — trust still comes from pinned peer keys plus the user-compared code.
 */
data class LanPairingLink(
    val mode: Mode,
    val host: String,
    val port: Int,
) {
    enum class Mode { SYNC, PLAY }

    fun encode(): String = buildString {
        append(LINK_PREFIX)
        append("?mode=").append(mode.name.lowercase())
        append("&address=").append(host.trim()).append(':').append(port)
    }

    fun address(): String = "${host.trim()}:$port"

    companion object {
        const val LINK_PREFIX = "relay://lan/join"
        private val REGEX = Regex("""^relay://lan/join\?mode=(sync|play)&address=([0-9A-Za-z.\-]+):(\d{1,5})$""")

        /** Strict parser; anything malformed returns null rather than a best guess. */
        fun parse(raw: String?): LanPairingLink? {
            val trimmed = raw?.trim()?.takeIf { it.length <= 256 } ?: return null
            val match = REGEX.matchEntire(trimmed) ?: return null
            val mode = when (match.groupValues[1]) {
                "sync" -> Mode.SYNC
                else -> Mode.PLAY
            }
            val port = match.groupValues[3].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val host = match.groupValues[2]
            if (host.isBlank()) return null
            return LanPairingLink(mode, host, port)
        }
    }
}

/** One LAN device found through network discovery, ready to be joined. */
data class DiscoveredLanHost(
    val name: String,
    val host: String,
    val port: Int,
    val mode: LanPairingLink.Mode,
) {
    fun toLink(): LanPairingLink = LanPairingLink(mode, host, port)
}
