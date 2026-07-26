package dev.relay.music.lastfm

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import dev.relay.music.lastfm.PendingScrobble
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Entity(tableName = "pending_scrobbles")
data class PendingScrobbleEntity(
    @PrimaryKey val id: String,
    val artist: String,
    val track: String,
    val album: String?,
    val durationMs: Long,
    val startedAtEpochSeconds: Long,
) {
    fun asPendingScrobble() = PendingScrobble(
        id = id,
        artist = artist,
        track = track,
        album = album,
        durationMs = durationMs,
        startedAtEpochSeconds = startedAtEpochSeconds,
    )
}

@Dao
interface PendingScrobbleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(scrobble: PendingScrobbleEntity)

    @Query("SELECT * FROM pending_scrobbles ORDER BY startedAtEpochSeconds ASC")
    suspend fun pending(): List<PendingScrobbleEntity>

    @Query("DELETE FROM pending_scrobbles WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [PendingScrobbleEntity::class], version = 1, exportSchema = false)
abstract class LastFmDatabase : RoomDatabase() {
    abstract fun pendingScrobbleDao(): PendingScrobbleDao
}

object LastFmStore {
    @Volatile
    private var database: LastFmDatabase? = null

    fun database(context: Context): LastFmDatabase = database ?: synchronized(this) {
        database ?: Room.databaseBuilder(
            context.applicationContext,
            LastFmDatabase::class.java,
            "lastfm.db",
        ).build().also { database = it }
    }
}

class SessionKeyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun read(): LastFmSession? {
        val username = decrypt(preferences.getString(USERNAME, null))
        val key = decrypt(preferences.getString(SESSION_KEY, null))
        return if (username == null || key == null) {
            clear()
            null
        } else {
            LastFmSession(username, key)
        }
    }

    fun save(session: LastFmSession) {
        preferences.edit {
            putString(USERNAME, encrypt(session.username))
            putString(SESSION_KEY, encrypt(session.key))
        }
    }

    fun clear() {
        preferences.edit { clear() }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String?): String? = runCatching {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        if (payload.size <= GCM_IV_SIZE_BYTES) return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(GCM_TAG_SIZE_BITS, payload.copyOfRange(0, GCM_IV_SIZE_BYTES)),
            )
        }
        cipher.doFinal(payload.copyOfRange(GCM_IV_SIZE_BYTES, payload.size)).toString(Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val GCM_IV_SIZE_BYTES = 12
        const val GCM_TAG_SIZE_BITS = 128
        const val KEY_ALIAS = "relay.lastfm.session"
        const val PREFERENCES_NAME = "lastfm_session"
        const val SESSION_KEY = "session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val USERNAME = "username"
    }
}
