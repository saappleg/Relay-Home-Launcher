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

/** Keeps the complete Nuvio session encrypted with an Android Keystore key. */
internal object NuvioSessionStore {
    private const val preferencesName = "relay_nuvio_session"
    private const val tokenKey = "encrypted_access_token"
    private const val profileKey = "active_profile"
    private const val generationKey = "session_generation"
    private const val keyAlias = "relay_nuvio_session_key"
    private const val gcmIvBytes = 12
    private val storeLock = Any()

    fun load(context: Context): NuvioSession? = runCatching {
        val appContext = applicationContextSafely(context)
        val prefs = preferences(appContext)
        val payload = prefs.getString(tokenKey, null) ?: return null
        val parts = payload.split(':', limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        require(iv.size == gcmIvBytes)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        require(ciphertext.isNotEmpty())
        val plaintext = cipher(Cipher.DECRYPT_MODE, iv).doFinal(ciphertext).decodeToString()
        val session = decodeSession(plaintext)
        require(session.accessToken.isNotBlank())
        bindPersistence(appContext, session, prefs.getLong(generationKey, 0L))
        session
    }.getOrElse {
        clear(context)
        null
    }

    fun save(context: Context, session: NuvioSession) {
        require(session.accessToken.isNotBlank()) { "Nuvio session token cannot be blank." }
        val appContext = applicationContextSafely(context)
        val prefs = preferences(appContext)
        synchronized(storeLock) {
            val generation = prefs.getLong(generationKey, 0L) + 1L
            val saved = writeSharedPreferencesSafely(appContext, preferencesName) {
                it.putLong(generationKey, generation)
                    .putString(tokenKey, encrypt(session.tokenSnapshot()))
            }
            check(saved) { "Nuvio session could not be saved." }
            bindPersistence(appContext, session, generation)
        }
    }

    fun clear(context: Context) {
        val appContext = applicationContextSafely(context)
        synchronized(storeLock) {
            val prefs = preferences(appContext)
            writeSharedPreferencesSafely(appContext, preferencesName) {
                it.putLong(generationKey, prefs.getLong(generationKey, 0L) + 1L)
                    .remove(tokenKey)
                    .remove(profileKey)
            }
        }
    }

    fun loadProfile(context: Context): Int = readSharedPreferencesSafely(
        context, preferencesName, 1
    ) { it.getInt(profileKey, 1).coerceAtLeast(1) }

    fun saveProfile(context: Context, profileIndex: Int) {
        writeSharedPreferencesSafely(context, preferencesName) {
            it.putInt(profileKey, profileIndex.coerceAtLeast(1))
        }
    }

    private fun bindPersistence(context: Context, session: NuvioSession, generation: Long) {
        session.persistTokens = { snapshot ->
            // A late refresh from a signed-out or replaced session must not restore its token.
            runCatching {
                synchronized(storeLock) {
                    val prefs = preferences(context)
                    if (prefs.getLong(generationKey, 0L) == generation) {
                        writeSharedPreferencesSafely(context, preferencesName) {
                            it.putString(tokenKey, encrypt(snapshot))
                        }
                    }
                }
            }
        }
    }

    private fun decodeSession(plaintext: String): NuvioSession {
        val jsonSession = runCatching {
            val json = JSONObject(plaintext)
            val token = json.optString("access_token").takeIf { it.isNotBlank() } ?: return@runCatching null
            NuvioSession(
                accessToken = token,
                refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() && it != "null" },
                expiresAtEpochSeconds = json.optLong("expires_at", 0L).takeIf { it > 0L },
                accountId = json.optString("account_id")
            )
        }.getOrNull()
        // Older releases encrypted only the access token; continue accepting that payload.
        return jsonSession ?: NuvioSession(accessToken = plaintext.trim())
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

    private fun preferences(context: Context) =
        applicationContextSafely(context).getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    private fun cipher(mode: Int, iv: ByteArray? = null): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        if (mode == Cipher.ENCRYPT_MODE) init(mode, key())
        else init(mode, key(), GCMParameterSpec(128, requireNotNull(iv)))
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

internal fun NuvioSession.profileScope(profileIndex: Int): String {
    val identity = accountId.ifBlank { "unidentified:$accessToken" }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray())
        .take(12)
        .joinToString("") { byte -> "%02x".format(byte) }
    return "nuvio_${digest}_$profileIndex"
}
