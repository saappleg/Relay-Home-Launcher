package com.relayhome.launcher

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps the full Nuvio session encrypted with an Android Keystore key. */
internal object NuvioSessionStore {
    private const val preferencesName = "relay_nuvio_session"
    private const val tokenKey = "encrypted_access_token"
    private const val profileKey = "active_profile"
    private const val generationKey = "session_generation"
    private const val keyAlias = "relay_nuvio_session_key"
    private val storeLock = Any()

    fun load(context: Context): NuvioSession? = runCatching {
        val appContext = context.applicationContext
        val prefs = preferences(appContext)
        val payload = prefs.getString(tokenKey, null) ?: return null
        val parts = payload.split(':', limit = 2)
        require(parts.size == 2)
        val plaintext = cipher(Cipher.DECRYPT_MODE, Base64.decode(parts[0], Base64.NO_WRAP))
            .doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
            .decodeToString()
        val session = decodeSession(plaintext)
        require(session.accessToken.isNotBlank())
        bindPersistence(appContext, session, prefs.getLong(generationKey, 0L))
        session
    }.getOrElse {
        clear(context)
        null
    }

    fun save(context: Context, session: NuvioSession) {
        val appContext = context.applicationContext
        val prefs = preferences(appContext)
        synchronized(storeLock) {
            val generation = prefs.getLong(generationKey, 0L) + 1L
            prefs.edit()
                .putLong(generationKey, generation)
                .putString(tokenKey, encrypt(session.tokenSnapshot()))
                .apply()
            bindPersistence(appContext, session, generation)
        }
    }

    fun clear(context: Context) {
        val prefs = preferences(context)
        synchronized(storeLock) {
            prefs.edit()
                .putLong(generationKey, prefs.getLong(generationKey, 0L) + 1L)
                .remove(tokenKey)
                .remove(profileKey)
                .apply()
        }
    }

    fun loadProfile(context: Context): Int = preferences(context).getInt(profileKey, 1)

    fun saveProfile(context: Context, profileIndex: Int) {
        preferences(context).edit().putInt(profileKey, profileIndex).apply()
    }

    private fun bindPersistence(context: Context, session: NuvioSession, generation: Long) {
        session.persistTokens = { snapshot ->
            // A delayed response from a signed-out/previous session must not restore its token.
            runCatching {
                synchronized(storeLock) {
                    val prefs = preferences(context)
                    if (prefs.getLong(generationKey, 0L) == generation) {
                        prefs.edit().putString(tokenKey, encrypt(snapshot)).apply()
                    }
                }
            }
        }
    }

    private fun decodeSession(plaintext: String): NuvioSession {
        val jsonSession = runCatching {
            val json = JSONObject(plaintext)
            val accessToken = json.optString("access_token").takeIf { it.isNotBlank() } ?: return@runCatching null
            NuvioSession(
                accessToken = accessToken,
                refreshToken = json.optString("refresh_token"),
                expiresAtEpochSeconds = json.optLong("expires_at", 0L),
                accountId = json.optString("account_id")
            )
        }.getOrNull()
        // Older app versions encrypted only the access token. Keep those sessions readable; if
        // they have expired, NuvioApi will return its typed reauthentication-needed error.
        return jsonSession ?: NuvioSession(accessToken = plaintext)
    }

    private fun encrypt(snapshot: NuvioTokenSnapshot): String {
        val plaintext = JSONObject()
            .put("access_token", snapshot.accessToken)
            .put("refresh_token", snapshot.refreshToken)
            .put("expires_at", snapshot.expiresAtEpochSeconds)
            .put("account_id", snapshot.accountId)
            .toString()
            .encodeToByteArray()
        val encryptor = cipher(Cipher.ENCRYPT_MODE)
        val ciphertext = encryptor.doFinal(plaintext)
        return "${Base64.encodeToString(encryptor.iv, Base64.NO_WRAP)}:${Base64.encodeToString(ciphertext, Base64.NO_WRAP)}"
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
                    .build()
            )
        }.generateKey()
    }
}

internal fun NuvioSession.profileScope(profileIndex: Int): String {
    val identity = accountId.ifBlank { "unidentified:$accessToken" }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray())
        .take(12)
        .joinToString("") { byte -> "%02x".format(byte) }
    return "nuvio_${digest}_$profileIndex"
}
