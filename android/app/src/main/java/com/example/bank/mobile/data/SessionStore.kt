package com.example.bank.mobile.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

private val Context.dataStore by preferencesDataStore(name = "session")

/** What the app remembers between launches. */
data class StoredSession(
    val serverUrl: String?,
    val refreshToken: String?,
    val fullName: String?,
    val email: String?,
    val hasPin: Boolean,
    val pinFailures: Int,
)

/**
 * Everything persisted on the phone:
 * - the refresh token, encrypted with a key that lives in the Android Keystore (it
 *   can't be read out of the device, not even by this app);
 * - the PIN, never stored itself: only a salted PBKDF2 hash, slow to brute-force;
 * - the server address, the user's name and email for the PIN screen.
 * The short-lived access token is never written to disk.
 */
class SessionStore(private val context: Context) {

    private object Keys {
        val SERVER = stringPreferencesKey("server_url")
        val REFRESH = stringPreferencesKey("refresh_token_enc")
        val NAME = stringPreferencesKey("full_name")
        val EMAIL = stringPreferencesKey("email")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_FAILURES = intPreferencesKey("pin_failures")
    }

    suspend fun load(): StoredSession {
        val p = context.dataStore.data.first()
        return StoredSession(
            serverUrl = p[Keys.SERVER],
            refreshToken = p[Keys.REFRESH]?.let { TokenCipher.decrypt(it) },
            fullName = p[Keys.NAME],
            email = p[Keys.EMAIL],
            hasPin = p[Keys.PIN_HASH] != null,
            pinFailures = p[Keys.PIN_FAILURES] ?: 0,
        )
    }

    suspend fun saveServer(url: String) = edit { it[Keys.SERVER] = url }

    suspend fun saveLogin(refreshToken: String, fullName: String, email: String) = edit {
        it[Keys.REFRESH] = TokenCipher.encrypt(refreshToken)
        it[Keys.NAME] = fullName
        it[Keys.EMAIL] = email
    }

    suspend fun saveRefreshToken(refreshToken: String) = edit { it[Keys.REFRESH] = TokenCipher.encrypt(refreshToken) }

    suspend fun savePin(pin: String) = withContext(Dispatchers.Default) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        edit {
            it[Keys.PIN_SALT] = Base64.encodeToString(salt, Base64.NO_WRAP)
            it[Keys.PIN_HASH] = Base64.encodeToString(hash, Base64.NO_WRAP)
            it[Keys.PIN_FAILURES] = 0
        }
    }

    /** Compares in constant time, so response timing reveals nothing about the PIN. */
    suspend fun checkPin(pin: String): Boolean = withContext(Dispatchers.Default) {
        val p = context.dataStore.data.first()
        val salt = p[Keys.PIN_SALT] ?: return@withContext false
        val expected = p[Keys.PIN_HASH] ?: return@withContext false
        val ok = MessageDigest.isEqual(
            pbkdf2(pin, Base64.decode(salt, Base64.NO_WRAP)),
            Base64.decode(expected, Base64.NO_WRAP),
        )
        edit { it[Keys.PIN_FAILURES] = if (ok) 0 else (it[Keys.PIN_FAILURES] ?: 0) + 1 }
        ok
    }

    /** Signs out on this phone: forgets tokens and PIN but keeps the server address. */
    suspend fun clearSession() = edit {
        it.remove(Keys.REFRESH)
        it.remove(Keys.NAME)
        it.remove(Keys.EMAIL)
        it.remove(Keys.PIN_HASH)
        it.remove(Keys.PIN_SALT)
        it.remove(Keys.PIN_FAILURES)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit): Preferences =
        context.dataStore.edit(block)

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120_000, 256))
            .encoded
}

/** AES-GCM with a non-exportable key kept in the Android Keystore. */
private object TokenCipher {
    private const val ALIAS = "lime_refresh_token_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    /** Null if the data can't be decrypted (e.g. the key was reset); the user then signs in again. */
    fun decrypt(encoded: String): String? = runCatching {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed.copyOfRange(0, 12)))
        }
        String(cipher.doFinal(sealed.copyOfRange(12, sealed.size)), Charsets.UTF_8)
    }.getOrNull()
}
