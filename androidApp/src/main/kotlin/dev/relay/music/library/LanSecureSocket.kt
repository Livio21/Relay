package dev.relay.music.library

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.SecretKey
import dev.relay.music.sync.RelayLanProtocol

/**
 * Authenticated, encrypted socket for one explicitly approved Relay LAN session.
 * Callers choose their own bounded message channels; neither pairing nor transport is implicit.
 */
internal class LanSecureSocket private constructor(
    private val socket: Socket,
    private val input: DataInputStream,
    private val output: DataOutputStream,
    private val key: SecretKey,
    private val protocol: String,
    private val sender: String,
) : AutoCloseable {
    private var sendSequence = 0L
    private var receiveSequence = 0L

    val peer: LanSyncPeer
        get() = checkedPeer
    private lateinit var checkedPeer: LanSyncPeer

    fun send(channel: String, value: ByteArray, maxBytes: Int) {
        require(value.size <= maxBytes) { "Relay LAN message is too large." }
        output.writeEncrypted(LanSyncCrypto.encrypt(key, value, aad(sender, channel, sendSequence++)), maxBytes)
    }

    fun receive(channel: String, maxBytes: Int): ByteArray = LanSyncCrypto.decrypt(
        key,
        input.readEncrypted(maxBytes),
        aad(if (sender == RelayLanProtocol.HOST) RelayLanProtocol.CLIENT else RelayLanProtocol.HOST, channel, receiveSequence++),
    )

    override fun close() = socket.close()

    private fun aad(from: String, channel: String, sequence: Long): ByteArray =
        RelayLanProtocol.authenticatedFrameContext(protocol, from, channel, sequence)

    private fun DataOutputStream.writeEncrypted(value: LanEncryptedPayload, maxBytes: Int) {
        val bytes = value.nonce + value.ciphertext
        require(bytes.size <= maxBytes + GCM_OVERHEAD_BYTES) { "Relay LAN message is too large." }
        writeInt(bytes.size)
        write(bytes)
        flush()
    }

    private fun DataInputStream.readEncrypted(maxBytes: Int): LanEncryptedPayload {
        val size = readInt()
        require(size in (GCM_NONCE_BYTES + 1)..(maxBytes + GCM_OVERHEAD_BYTES)) { "Invalid Relay LAN message size." }
        val bytes = ByteArray(size).also(::readFully)
        return LanEncryptedPayload(bytes.copyOfRange(0, GCM_NONCE_BYTES), bytes.copyOfRange(GCM_NONCE_BYTES, bytes.size))
    }

    companion object {
        private const val MAX_PUBLIC_KEY_CHARS = 8 * 1024
        private const val MAX_HANDSHAKE_BYTES = 16 * 1024 * 1024
        private const val GCM_NONCE_BYTES = 12
        private const val GCM_OVERHEAD_BYTES = GCM_NONCE_BYTES + 16
        private val READY = "READY".toByteArray(StandardCharsets.UTF_8)

        fun host(
            server: ServerSocket,
            protocol: String,
            identity: LanSyncIdentity,
            knownPeers: List<LanSyncPeer>,
            confirm: (LanSyncPeer, String) -> Boolean,
        ): LanSecureSocket = open(server.accept(), protocol, identity, knownPeers, confirm, isHost = true)

        fun client(
            host: String,
            port: Int,
            protocol: String,
            identity: LanSyncIdentity,
            knownPeers: List<LanSyncPeer>,
            confirm: (LanSyncPeer, String) -> Boolean,
        ): LanSecureSocket = open(Socket(host, port), protocol, identity, knownPeers, confirm, isHost = false)

        private fun open(
            socket: Socket,
            protocol: String,
            identity: LanSyncIdentity,
            knownPeers: List<LanSyncPeer>,
            confirm: (LanSyncPeer, String) -> Boolean,
            isHost: Boolean,
        ): LanSecureSocket = try {
            socket.soTimeout = 120_000
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())
            val localKey = identity.publicKey()
            val localHello = LanHello(identity.fingerprint(localKey), identity.encodePublicKey(localKey))
            val remoteHello = if (isHost) {
                val remote = input.readHello(protocol)
                output.writeHello(protocol, localHello)
                remote
            } else {
                output.writeHello(protocol, localHello)
                input.readHello(protocol)
            }
            val remoteKey = identity.decodePublicKey(remoteHello.publicKey)
            val peer = LanSyncPeer(remoteHello.id, "DEVICE ${remoteHello.id}", remoteHello.publicKey)
            require(identity.fingerprint(remoteKey) == peer.id) { "Relay LAN peer identity is invalid." }
            knownPeers.firstOrNull { it.id == peer.id }?.let {
                require(it.publicKey == peer.publicKey) { "Relay LAN peer identity changed." }
            }
            val code = LanSyncCrypto.pairingCode(localKey.encoded, remoteKey.encoded)
            require(confirm(peer, code)) { "Relay LAN pairing was not confirmed." }
            val key = if (isHost) {
                LanSyncCrypto.unwrapSessionKey(input.readBounded(), identity.privateKey()).also { sessionKey ->
                    val challenge = LanSyncCrypto.newSessionKey()
                    output.writeBounded(LanSyncCrypto.wrapSessionKey(challenge, remoteKey))
                    val proof = LanSyncCrypto.decrypt(sessionKey, input.readEncryptedHandshake(), handshakeContext(protocol, CLIENT_PROOF))
                    LanSyncCrypto.verifySecret(challenge, proof)
                    output.writeEncryptedHandshake(LanSyncCrypto.encrypt(sessionKey, READY, handshakeContext(protocol, HOST_READY)))
                }
            } else {
                LanSyncCrypto.newSessionKey().also { sessionKey ->
                    output.writeBounded(LanSyncCrypto.wrapSessionKey(sessionKey, remoteKey))
                    val challenge = LanSyncCrypto.unwrapSessionKey(input.readBounded(), identity.privateKey())
                    output.writeEncryptedHandshake(LanSyncCrypto.encrypt(sessionKey, challenge.encoded, handshakeContext(protocol, CLIENT_PROOF)))
                    require(
                        LanSyncCrypto.decrypt(sessionKey, input.readEncryptedHandshake(), handshakeContext(protocol, HOST_READY)).contentEquals(READY),
                    ) { "Relay LAN handshake failed." }
                }
            }
            LanSecureSocket(socket, input, output, key, protocol, if (isHost) RelayLanProtocol.HOST else RelayLanProtocol.CLIENT).also { it.checkedPeer = peer }
        } catch (error: Throwable) {
            socket.close()
            throw error
        }

        private fun DataOutputStream.writeHello(protocol: String, value: LanHello) {
            writeUTF(protocol)
            writeUTF(value.id)
            writeUTF(value.publicKey)
            flush()
        }

        private fun DataInputStream.readHello(protocol: String): LanHello {
            require(readUTF() == protocol) { "Unsupported Relay LAN protocol." }
            val id = readUTF()
            val key = readUTF()
            require(id.matches(Regex("[A-F0-9]{12}")) && key.length <= MAX_PUBLIC_KEY_CHARS) { "Invalid Relay LAN hello." }
            return LanHello(id, key)
        }

        private fun DataOutputStream.writeBounded(value: ByteArray) {
            require(value.size <= MAX_HANDSHAKE_BYTES) { "Relay LAN handshake is too large." }
            writeInt(value.size)
            write(value)
            flush()
        }

        private fun DataInputStream.readBounded(): ByteArray {
            val size = readInt()
            require(size in 1..MAX_HANDSHAKE_BYTES) { "Invalid Relay LAN handshake size." }
            return ByteArray(size).also(::readFully)
        }

        private fun DataOutputStream.writeEncryptedHandshake(value: LanEncryptedPayload) = writeBounded(value.nonce + value.ciphertext)

        private fun DataInputStream.readEncryptedHandshake(): LanEncryptedPayload = readBounded().let { bytes ->
            require(bytes.size > GCM_NONCE_BYTES) { "Invalid Relay LAN handshake." }
            LanEncryptedPayload(bytes.copyOfRange(0, GCM_NONCE_BYTES), bytes.copyOfRange(GCM_NONCE_BYTES, bytes.size))
        }

        private fun handshakeContext(protocol: String, stage: String): ByteArray =
            "$protocol:$stage".toByteArray(StandardCharsets.UTF_8)

        private data class LanHello(val id: String, val publicKey: String)

        private const val CLIENT_PROOF = "CLIENT_PROOF"
        private const val HOST_READY = "HOST_READY"
    }
}

/** Identity boundary keeps the transport testable without Android Keystore and allows a native desktop store later. */
internal interface LanSyncIdentity {
    fun publicKey(): PublicKey
    fun privateKey(): PrivateKey
    fun fingerprint(key: PublicKey): String
    fun encodePublicKey(key: PublicKey): String
    fun decodePublicKey(value: String): PublicKey
}
