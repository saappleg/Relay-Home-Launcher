package com.relayhome.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NuvioSessionStoreAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = context.getSharedPreferences("relay_nuvio_session", Context.MODE_PRIVATE)

    @Before
    fun clearSession() {
        preferences.edit().clear().commit()
    }

    @After
    fun restoreSession() {
        preferences.edit().clear().commit()
    }

    @Test
    fun saveAndLoad_roundTripsEncryptedSessionAndKeepsProfile() {
        val session = NuvioSession(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAtEpochSeconds = 1_900_000_000L
        )
        NuvioSessionStore.saveProfile(context, 3)
        NuvioSessionStore.save(context, session)

        assertEquals(session, NuvioSessionStore.load(context))
        assertEquals(3, NuvioSessionStore.loadProfile(context))
        val storedPayload = preferences.getString("encrypted_access_token", null).orEmpty()
        assertEquals(1, storedPayload.count { it == ':' })
    }

    @Test
    fun corruptPayload_clearsOnlyTokenAndPreservesProfileSelection() {
        NuvioSessionStore.saveProfile(context, 4)
        preferences.edit().putString("encrypted_access_token", "not-a-valid-payload").commit()

        assertNull(NuvioSessionStore.load(context))
        assertEquals(4, NuvioSessionStore.loadProfile(context))
        assertNull(preferences.getString("encrypted_access_token", null))
    }

    @Test
    fun legacyEncryptedPlainToken_loadsThroughUpgradeBranch() {
        NuvioSessionStore.saveProfile(context, 2)
        NuvioSessionStore.saveLegacyTokenForTest(context, "legacy-access-token")

        assertEquals(NuvioSession("legacy-access-token"), NuvioSessionStore.load(context))
        assertEquals(2, NuvioSessionStore.loadProfile(context))
    }
}
