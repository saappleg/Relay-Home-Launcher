package com.relayhome.launcher

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Keeps the Nuvio bearer token encrypted with an Android Keystore key.
 * The ciphertext survives normal app upgrades; the key never leaves the device.
 */
internal object NuvioSessionStore {
    private const val preferencesName = "relay_nuvio_session"
    private const val tokenKey = "encrypted_access_token"
    private const val profileKey = "active_profile"
    private const val keyAlias = "relay_nuvio_session_key"
    private const val gcmIvBytes = 12

    fun load(context: Context): NuvioSession? = runCatching {
        val payload = preferences(context).getString(tokenKey, null) ?: return null
        val parts = payload.split(':', limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        require(iv.size == gcmIvBytes)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        require(ciphertext.isNotEmpty())
        val plaintext = cipher(Cipher.DECRYPT_MODE, iv).doFinal(ciphertext)
        val stored = plaintext.decodeToString().trim()
        require(stored.isNotBlank())
        val session = if (stored.startsWith("{")) {
            val session = JSONObject(stored)
            NuvioSession(
                accessToken = session.getString("access_token"),
                refreshToken = session.optString("refresh_token").takeUnless { it.isBlank() || it == "null" },
                expiresAtEpochSeconds = session.optLong("expires_at", 0L).takeIf { it > 0L }
            )
        } else {
            // Migrate the original token-only payload without treating it as an expired session.
            NuvioSession(stored)
        }
        require(session.accessToken.isNotBlank())
        session
    }.getOrElse {
        // A corrupt/undecryptable token must not reset the nonsecret profile selection.
        preferences(context).edit().remove(tokenKey).apply()
        null
    }

    fun save(context: Context, session: NuvioSession) {
        require(session.accessToken.isNotBlank()) { "Nuvio session token cannot be blank." }
        val encryptor = cipher(Cipher.ENCRYPT_MODE)
        val plaintext = JSONObject()
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken)
            .put("expires_at", session.expiresAtEpochSeconds)
            .toString()
            .encodeToByteArray()
        val ciphertext = encryptor.doFinal(plaintext)
        val payload = "${Base64.encodeToString(encryptor.iv, Base64.NO_WRAP)}:${Base64.encodeToString(ciphertext, Base64.NO_WRAP)}"
        preferences(context).edit().putString(tokenKey, payload).apply()
    }

    fun clear(context: Context) {
        preferences(context).edit().remove(tokenKey).remove(profileKey).apply()
    }

    fun loadProfile(context: Context): Int = preferences(context).getInt(profileKey, 1)

    fun saveProfile(context: Context, profileIndex: Int) {
        preferences(context).edit().putInt(profileKey, profileIndex).apply()
    }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    private fun cipher(mode: Int, iv: ByteArray? = null): Cipher {
        val instance = Cipher.getInstance("AES/GCM/NoPadding")
        if (mode == Cipher.ENCRYPT_MODE) instance.init(mode, key())
        else instance.init(mode, key(), GCMParameterSpec(128, requireNotNull(iv)))
        return instance
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }
}
