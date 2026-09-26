package com.relayhome.launcher

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores only the Stremio auth key and account label, encrypted with an Android Keystore key. */
internal object StremioSessionStore {
    private const val preferencesName = "relay_stremio_session"
    private const val payloadKey = "encrypted_session"
    private const val generationKey = "session_generation"
    private const val keyAlias = "relay_stremio_session_key"
    private const val gcmIvBytes = 12
    private val storeLock = Any()

    fun load(context: Context): StremioSession? = runCatching {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val payload = prefs.getString(payloadKey, null) ?: return null
        val parts = payload.split(':', limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        require(iv.size == gcmIvBytes)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        require(ciphertext.isNotEmpty())
        val json = JSONObject(cipher(Cipher.DECRYPT_MODE, iv).doFinal(ciphertext).decodeToString())
        val session = StremioSession(
            authKey = json.optString("auth_key").trim(),
            accountId = json.optString("account_id").trim(),
            email = json.optString("email").trim()
        )
        require(session.authKey.isNotBlank())
        session
    }.getOrElse {
        clear(context)
        null
    }

    fun reserveSave(context: Context): Long {
        return reserveGeneration(context)
    }

    fun reserveClear(context: Context): Long {
        return reserveGeneration(context)
    }

    private fun reserveGeneration(context: Context): Long {
        val prefs = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        synchronized(storeLock) {
            val generation = prefs.getLong(generationKey, 0L) + 1L
            check(prefs.edit().putLong(generationKey, generation).commit()) { "Stremio account update could not be prepared." }
            return generation
        }
    }

    fun save(
        context: Context,
        session: StremioSession,
        generation: Long,
        afterSave: () -> Unit = {}
    ) {
        require(session.authKey.isNotBlank()) { "Stremio auth key cannot be blank." }
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val plaintext = JSONObject()
            .put("auth_key", session.authKey)
            .put("account_id", session.accountId)
            .put("email", session.email)
            .toString()
        synchronized(storeLock) {
            if (prefs.getLong(generationKey, 0L) != generation) return
            check(prefs.edit().putString(payloadKey, encrypt(plaintext)).commit()) {
                "Stremio account could not be saved."
            }
            afterSave()
        }
    }

    fun clear(context: Context) {
        val generation = reserveClear(context)
        clear(context, generation)
    }

    fun clear(context: Context, generation: Long, afterClear: () -> Unit = {}) {
        val prefs = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        synchronized(storeLock) {
            if (prefs.getLong(generationKey, 0L) != generation) return
            check(prefs.edit().remove(payloadKey).commit()) { "Stremio account could not be cleared." }
            afterClear()
        }
    }

    private fun encrypt(plaintext: String): String {
        val cipher = cipher(Cipher.ENCRYPT_MODE)
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return "${Base64.encodeToString(cipher.iv, Base64.NO_WRAP)}:${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"
    }

    private fun cipher(mode: Int, iv: ByteArray? = null): Cipher {
        val key = loadOrCreateKey()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            if (mode == Cipher.ENCRYPT_MODE) init(mode, key)
            else init(mode, key, GCMParameterSpec(128, requireNotNull(iv)))
        }
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }
}
