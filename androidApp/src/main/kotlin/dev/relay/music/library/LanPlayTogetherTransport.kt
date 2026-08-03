package dev.relay.music.library

import dev.relay.music.sync.PlayTogetherCommand
import dev.relay.music.sync.RelayLanProtocol
import java.net.ServerSocket
import org.json.JSONObject

/** Foreground-only leader-to-guest control stream; every frame is secured by [LanSecureSocket]. */
internal object LanPlayTogetherTransport {
    private const val PROTOCOL = RelayLanProtocol.PLAY_TOGETHER_V1
    private const val CONTROL = "control"
    private const val MAX_CONTROL_BYTES = 8 * 1024

    fun host(
        server: ServerSocket,
        identity: LanSyncIdentity,
        knownPeers: List<LanSyncPeer>,
        confirm: (LanSyncPeer, String) -> Boolean,
        active: () -> Boolean,
        command: () -> PlayTogetherCommand?,
        onConnected: (AutoCloseable) -> Unit,
        nowElapsedMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    ): LanSyncPeer = server.use { LanSecureSocket.host(it, PROTOCOL, identity, knownPeers, confirm) }.use { session ->
        onConnected(session)
        val probeSent = nowElapsedMs()
        session.send(CONTROL, JSONObject().put("type", "probe").put("sent", probeSent).toString().encodeToByteArray(), MAX_CONTROL_BYTES)
        val reply = JSONObject(session.receive(CONTROL, MAX_CONTROL_BYTES).decodeToString())
        require(reply.getString("type") == "probe") { "Invalid Relay Play Together clock probe." }
        val peerMinusLeader = ((reply.getLong("received") - probeSent) + (reply.getLong("sent") - nowElapsedMs())) / 2
        session.send(CONTROL, JSONObject().put("type", "offset").put("leaderMinusLocal", -peerMinusLeader).toString().encodeToByteArray(), MAX_CONTROL_BYTES)
        while (active()) {
            val next = command() ?: break
            session.send(CONTROL, encodeCommand(next), MAX_CONTROL_BYTES)
            if (!next.playing) break
            Thread.sleep(1_500)
        }
        session.peer
    }

    fun client(
        host: String,
        port: Int,
        identity: LanSyncIdentity,
        knownPeers: List<LanSyncPeer>,
        confirm: (LanSyncPeer, String) -> Boolean,
        active: () -> Boolean,
        onCommand: (PlayTogetherCommand, Long) -> Unit,
        onConnected: (AutoCloseable) -> Unit,
        nowElapsedMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    ): LanSyncPeer = LanSecureSocket.client(host, port, PROTOCOL, identity, knownPeers, confirm).use { session ->
        onConnected(session)
        val probe = JSONObject(session.receive(CONTROL, MAX_CONTROL_BYTES).decodeToString())
        require(probe.getString("type") == "probe") { "Invalid Relay Play Together clock probe." }
        val received = nowElapsedMs()
        session.send(CONTROL, JSONObject().put("type", "probe").put("received", received).put("sent", nowElapsedMs()).toString().encodeToByteArray(), MAX_CONTROL_BYTES)
        val offset = JSONObject(session.receive(CONTROL, MAX_CONTROL_BYTES).decodeToString())
        require(offset.getString("type") == "offset") { "Invalid Relay Play Together offset." }
        val leaderMinusLocal = offset.getLong("leaderMinusLocal")
        while (active()) {
            val frame = JSONObject(session.receive(CONTROL, MAX_CONTROL_BYTES).decodeToString())
            if (frame.optString("type") == "command") onCommand(decodeCommand(frame), leaderMinusLocal)
        }
        session.peer
    }

    private fun encodeCommand(command: PlayTogetherCommand): ByteArray = JSONObject()
        .put("type", "command")
        .put("sourceId", command.sourceId)
        .put("trackId", command.trackId)
        .putOpt("digest", command.contentDigest)
        .put("queueIndex", command.queueIndex)
        .put("position", command.leaderPositionMs)
        .put("target", command.targetLeaderElapsedMs)
        .put("playing", command.playing)
        .toString().encodeToByteArray()

    private fun decodeCommand(value: JSONObject): PlayTogetherCommand = PlayTogetherCommand(
        sourceId = value.getString("sourceId"),
        trackId = value.getString("trackId"),
        contentDigest = value.optString("digest").takeIf { it.matches(Regex("[0-9a-f]{64}")) },
        queueIndex = value.getInt("queueIndex"),
        leaderPositionMs = value.getLong("position").coerceAtLeast(0),
        targetLeaderElapsedMs = value.getLong("target"),
        playing = value.getBoolean("playing"),
    )
}
