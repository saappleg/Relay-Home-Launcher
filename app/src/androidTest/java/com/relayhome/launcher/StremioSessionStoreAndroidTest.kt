package com.relayhome.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StremioSessionStoreAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = context.getSharedPreferences("relay_stremio_session", Context.MODE_PRIVATE)

    @Before
    fun clearStoredSession() {
        preferences.edit().clear().commit()
    }

    @After
    fun removeStoredSession() {
        StremioSessionStore.clear(context)
    }

    @Test
    fun saveAndLoad_roundTripsAnEncryptedSession() {
        val session = StremioSession("secret-auth-key", "stremio-user-7", "viewer@example.test")
        val generation = StremioSessionStore.reserveSave(context)

        StremioSessionStore.save(context, session, generation)

        val restored = StremioSessionStore.load(context)
        assertEquals(session.authKey, restored?.authKey)
        assertEquals(session.accountId, restored?.accountId)
        assertEquals(session.email, restored?.email)
        val encrypted = preferences.getString("encrypted_session", null).orEmpty()
        assertEquals(1, encrypted.count { it == ':' })
        assertTrue(!encrypted.contains(session.authKey))
        assertTrue(!encrypted.contains(session.email))
    }

    @Test
    fun delayedSave_cannotRestoreAnAccountAfterDisconnect() {
        val generation = StremioSessionStore.reserveSave(context)
        StremioSessionStore.clear(context)

        StremioSessionStore.save(context, StremioSession("stale-key", "user", ""), generation)

        assertNull(StremioSessionStore.load(context))
    }

    @Test
    fun delayedDisconnectClear_cannotDeleteANewAccount() {
        val disconnectGeneration = StremioSessionStore.reserveClear(context)
        val reconnectGeneration = StremioSessionStore.reserveSave(context)
        val reconnectedSession = StremioSession("new-account-key", "new-user", "new@example.test")
        var staleClearSideEffectRan = false

        StremioSessionStore.save(context, reconnectedSession, reconnectGeneration)
        StremioSessionStore.clear(context, disconnectGeneration) { staleClearSideEffectRan = true }

        assertFalse(staleClearSideEffectRan)
        assertEquals("new-account-key", StremioSessionStore.load(context)?.authKey)
    }

    @Test
    fun corruptEncryptedPayload_isDiscarded() {
        preferences.edit().putString("encrypted_session", "not-an-encrypted-session").commit()

        assertNull(StremioSessionStore.load(context))
        assertNull(preferences.getString("encrypted_session", null))
    }
}
