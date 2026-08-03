package dev.relay.music.library

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.PrivateKey
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Android-keystore identity plus pinned peer keys. Pairing records are device-local and revocable. */
internal class LanSyncIdentityStore(context: Context) : LanSyncIdentity {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun publicKey(): PublicKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_DECRYPT)
                        .setKeySize(2048)
                        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                        .build(),
                )
                generateKeyPair()
            }
        }
        return requireNotNull(keyStore.getCertificate(KEY_ALIAS)?.publicKey) { "Relay LAN identity is unavailable." }
    }

    override fun privateKey(): PrivateKey = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        .getKey(KEY_ALIAS, null) as? PrivateKey ?: error("Relay LAN identity is unavailable.")

    fun peers(): List<LanSyncPeer> = runCatching {
        JSONArray(preferences.getString(PEERS, "[]")).let { items ->
            List(items.length()) { index ->
                items.getJSONObject(index).let { item ->
                    LanSyncPeer(item.getString("id"), item.getString("name"), item.getString("key"))
                }
            }
        }
    }.getOrDefault(emptyList())

    fun savePeer(peer: LanSyncPeer) {
        require(peer.id.matches(Regex("[A-F0-9]{12}"))) { "Relay LAN peer fingerprint is invalid." }
        require(peer.name.isNotBlank() && peer.name.length <= 64) { "Relay LAN peer name is invalid." }
        decodePublicKey(peer.publicKey)
        val updated = peers().filterNot { it.id == peer.id } + peer
        preferences.edit().putString(PEERS, JSONArray().apply {
            updated.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("key", it.publicKey)) }
        }.toString()).apply()
    }

    fun removePeer(id: String) {
        val updated = peers().filterNot { it.id == id }
        preferences.edit().putString(PEERS, JSONArray().apply {
            updated.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("key", it.publicKey)) }
        }.toString()).apply()
    }

    override fun fingerprint(key: PublicKey): String = key.encoded.sha256Hex().take(12)

    override fun encodePublicKey(key: PublicKey): String = Base64.encodeToString(key.encoded, Base64.NO_WRAP)

    override fun decodePublicKey(value: String): PublicKey = KeyFactory.getInstance("RSA").generatePublic(
        X509EncodedKeySpec(Base64.decode(value, Base64.NO_WRAP)),
    )

    private companion object {
        const val PREFERENCES = "relay_lan_sync"
        const val KEY_ALIAS = "relay_lan_identity"
        const val PEERS = "peers"
    }
}

internal data class LanSyncPeer(val id: String, val name: String, val publicKey: String)

private fun ByteArray.sha256Hex(): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { "%02X".format(it) }
