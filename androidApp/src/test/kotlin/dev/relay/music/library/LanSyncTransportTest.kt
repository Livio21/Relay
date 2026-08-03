package dev.relay.music.library

import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class LanSyncTransportTest {
    @Test fun exchangesDataAndSelectedMusicOverOnePairedSession() {
        val directory = Files.createTempDirectory("relay-lan-test").toFile()
        val music = File(directory, "selected.relaymusic").apply { writeBytes(ByteArray(2048) { it.toByte() }) }
        val server = ServerSocket(0)
        val hostFailure = AtomicReference<Throwable?>()
        val hostData = AtomicReference<ByteArray?>()
        val receivedMusic = AtomicReference<ByteArray?>()
        val host = thread(name = "relay-sync-host") {
            runCatching {
                LanSyncTransport.hostOnce(
                    server, TestIdentity(), emptyList(), { _, _ -> true }, "host-data".encodeToByteArray(),
                    onIncomingArchive = hostData::set,
                    temporaryDirectory = directory,
                    onIncomingMusicArchive = { archive -> receivedMusic.set(archive.readBytes()) },
                )
            }.onFailure(hostFailure::set)
        }
        val clientData = AtomicReference<ByteArray?>()
        LanSyncTransport.clientOnce(
            "127.0.0.1", server.localPort, TestIdentity(), emptyList(), { _, _ -> true }, "client-data".encodeToByteArray(),
            onIncomingArchive = clientData::set,
            outgoingMusicArchive = music,
            temporaryDirectory = directory,
        )
        host.join(5_000)
        server.close()
        assertFalse(host.isAlive, "Host session did not finish")
        assertNull(hostFailure.get())
        assertContentEquals("client-data".encodeToByteArray(), hostData.get())
        assertContentEquals("host-data".encodeToByteArray(), clientData.get())
        assertContentEquals(music.readBytes(), receivedMusic.get())
        directory.deleteRecursively()
    }

    private class TestIdentity : LanSyncIdentity {
        private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        override fun publicKey(): PublicKey = pair.public
        override fun privateKey(): PrivateKey = pair.private
        override fun fingerprint(key: PublicKey): String = MessageDigest.getInstance("SHA-256").digest(key.encoded)
            .joinToString("") { "%02X".format(it) }.take(12)
        override fun encodePublicKey(key: PublicKey): String = Base64.getEncoder().encodeToString(key.encoded)
        override fun decodePublicKey(value: String): PublicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(value)))
    }
}
