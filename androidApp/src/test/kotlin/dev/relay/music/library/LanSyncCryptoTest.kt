package dev.relay.music.library

import java.security.KeyPairGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals

class LanSyncCryptoTest {
    @Test
    fun pairingCodeAndEncryptedPayloadArePeerStableAndTamperEvident() {
        val first = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val second = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertEquals(
            LanSyncCrypto.pairingCode(first.public.encoded, second.public.encoded),
            LanSyncCrypto.pairingCode(second.public.encoded, first.public.encoded),
        )

        val key = LanSyncCrypto.newSessionKey()
        val restored = LanSyncCrypto.unwrapSessionKey(LanSyncCrypto.wrapSessionKey(key, second.public), second.private)
        val encrypted = LanSyncCrypto.encrypt(restored, "relay sync".encodeToByteArray(), "v1".encodeToByteArray())
        assertEquals("relay sync", LanSyncCrypto.decrypt(key, encrypted, "v1".encodeToByteArray()).decodeToString())
        assertFails { LanSyncCrypto.decrypt(key, encrypted.copy(ciphertext = encrypted.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }), "v1".encodeToByteArray()) }
        assertNotEquals("000000", LanSyncCrypto.pairingCode(first.public.encoded, second.public.encoded))
    }

    @Test
    fun clientIdentityProofRejectsTampering() {
        val challenge = LanSyncCrypto.newSessionKey()
        LanSyncCrypto.verifySecret(challenge, challenge.encoded)
        val tampered = challenge.encoded.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFails { LanSyncCrypto.verifySecret(challenge, tampered) }
    }
}
