package dev.relay.music.library

import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Small, host-only crypto boundary for an explicitly verified Relay LAN pairing. */
internal object LanSyncCrypto {
    private const val AES_KEY_BYTES = 32
    private const val GCM_NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private val random = SecureRandom()

    fun pairingCode(firstPublicKey: ByteArray, secondPublicKey: ByteArray): String {
        val (first, second) = if (firstPublicKey.compareUnsigned(secondPublicKey) <= 0) {
            firstPublicKey to secondPublicKey
        } else {
            secondPublicKey to firstPublicKey
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("relay-lan-pair-v1".encodeToByteArray() + first + second)
        val code = ((digest[0].toInt() and 0xFF) shl 16 or
            (digest[1].toInt() and 0xFF) shl 8 or
            (digest[2].toInt() and 0xFF)) % 1_000_000
        return code.toString().padStart(6, '0')
    }

    fun newSessionKey(): SecretKey = SecretKeySpec(ByteArray(AES_KEY_BYTES).also(random::nextBytes), "AES")

    fun wrapSessionKey(key: SecretKey, recipient: PublicKey): ByteArray =
        Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding").run {
            init(Cipher.ENCRYPT_MODE, recipient)
            doFinal(key.encoded)
        }

    fun unwrapSessionKey(wrapped: ByteArray, recipient: PrivateKey): SecretKey =
        SecretKeySpec(
            Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding").run {
                init(Cipher.DECRYPT_MODE, recipient)
                doFinal(wrapped)
            }.also { require(it.size == AES_KEY_BYTES) { "Invalid Relay LAN session key." } },
            "AES",
        )

    fun verifySecret(expected: SecretKey, actual: ByteArray) {
        require(MessageDigest.isEqual(expected.encoded, actual)) { "Relay LAN identity proof failed." }
    }

    fun encrypt(key: SecretKey, plaintext: ByteArray, associatedData: ByteArray): LanEncryptedPayload {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData)
        return LanEncryptedPayload(cipher.iv, cipher.doFinal(plaintext))
    }

    fun decrypt(key: SecretKey, payload: LanEncryptedPayload, associatedData: ByteArray): ByteArray {
        require(payload.nonce.size == GCM_NONCE_BYTES) { "Invalid Relay LAN nonce." }
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, payload.nonce))
            updateAAD(associatedData)
            doFinal(payload.ciphertext)
        }
    }
}

internal data class LanEncryptedPayload(val nonce: ByteArray, val ciphertext: ByteArray)

private fun ByteArray.compareUnsigned(other: ByteArray): Int {
    for (index in 0 until minOf(size, other.size)) {
        val difference = (this[index].toInt() and 0xFF) - (other[index].toInt() and 0xFF)
        if (difference != 0) return difference
    }
    return size - other.size
}
