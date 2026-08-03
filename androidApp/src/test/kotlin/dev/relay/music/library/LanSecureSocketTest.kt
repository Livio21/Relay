package dev.relay.music.library

import java.net.ServerSocket
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import dev.relay.music.sync.RelayLanProtocol
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LanSecureSocketTest {
    @Test fun pairedPeersExchangeSequencedEncryptedFrames() {
        val server = ServerSocket(0)
        val hostFailure = AtomicReference<Throwable?>()
        val host = thread(name = "relay-lan-host") {
            runCatching {
                LanSecureSocket.host(server, RelayLanProtocol.DATA_SYNC_V1, TestIdentity(), emptyList(), { _, _ -> true }).use { session ->
                    assertContentEquals("from-client".encodeToByteArray(), session.receive("control", 128))
                    session.send("control", "from-host".encodeToByteArray(), 128)
                }
            }.onFailure(hostFailure::set)
        }
        LanSecureSocket.client("127.0.0.1", server.localPort, RelayLanProtocol.DATA_SYNC_V1, TestIdentity(), emptyList(), { _, _ -> true }).use { session ->
            session.send("control", "from-client".encodeToByteArray(), 128)
            assertContentEquals("from-host".encodeToByteArray(), session.receive("control", 128))
        }
        host.join(5_000)
        server.close()
        assertFalse(host.isAlive, "Host session did not finish")
        assertNull(hostFailure.get())
    }

    @Test fun clientCannotClaimAKeyWithoutItsPrivateKey() {
        val server = ServerSocket(0)
        val hostFailure = AtomicReference<Throwable?>()
        val host = thread(name = "relay-lan-impersonation-host") {
            runCatching {
                LanSecureSocket.host(server, RelayLanProtocol.DATA_SYNC_V1, TestIdentity(), emptyList(), { _, _ -> true }).close()
            }.onFailure(hostFailure::set)
        }
        val claimedIdentity = rsaKeyPair()
        val attackerIdentity = rsaKeyPair()
        val clientFailure = runCatching {
            LanSecureSocket.client(
                "127.0.0.1",
                server.localPort,
                RelayLanProtocol.DATA_SYNC_V1,
                TestIdentity(claimedIdentity, attackerIdentity.private),
                emptyList(),
                { _, _ -> true },
            ).close()
        }.exceptionOrNull()
        host.join(5_000)
        server.close()
        assertFalse(host.isAlive, "Host session did not finish")
        assertNotNull(clientFailure, "A client without the advertised private key completed pairing")
        assertNotNull(hostFailure.get(), "The host accepted an unproven client identity")
    }

    private class TestIdentity(
        private val pair: KeyPair = rsaKeyPair(),
        private val decryptionKey: PrivateKey = pair.private,
    ) : LanSyncIdentity {
        override fun publicKey(): PublicKey = pair.public
        override fun privateKey(): PrivateKey = decryptionKey
        override fun fingerprint(key: PublicKey): String = MessageDigest.getInstance("SHA-256").digest(key.encoded)
            .joinToString("") { "%02X".format(it) }.take(12)
        override fun encodePublicKey(key: PublicKey): String = Base64.getEncoder().encodeToString(key.encoded)
        override fun decodePublicKey(value: String): PublicKey = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(value)),
        )
    }

    private companion object {
        fun rsaKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }
}
