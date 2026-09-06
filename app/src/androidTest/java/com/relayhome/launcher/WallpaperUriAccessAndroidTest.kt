package com.relayhome.launcher

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.relayhome.launcher.data.RelaySettingsRepository
import com.relayhome.launcher.ui.home.WallpaperUriAccess
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WallpaperUriAccessAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resourceUri = "android.resource://${context.packageName}/drawable/relay_ambient_grain"

    @Before
    fun setUp() = runBlocking {
        RelaySettingsRepository.resetForTesting(context)
    }

    @After
    fun tearDown() = runBlocking {
        RelaySettingsRepository.resetForTesting(context)
    }

    @Test
    fun savedWallpaper_survivesRepositoryReload() = runBlocking {
        RelaySettingsRepository.awaitReady(context)
        RelaySettingsRepository.saveWallpaperImageUri(context, resourceUri)
        RelaySettingsRepository.awaitIdleForTesting(context)
        assertEquals(resourceUri, RelaySettingsRepository.loadWallpaperImageUri(context))

        RelaySettingsRepository.reloadForTesting(context)

        assertEquals(resourceUri, RelaySettingsRepository.loadWallpaperImageUri(context))
    }

    @Test
    fun pickerResult_requiresDurableReadableImage_andRejectsPermissionFailure() {
        assertEquals(resourceUri, WallpaperUriAccess.acceptedPickerUri(context, resourceUri))
        assertNull(
            WallpaperUriAccess.acceptedPickerUri(
                context,
                "content://com.example.revoked/document/wallpaper"
            )
        )
        assertNull(WallpaperUriAccess.acceptedPickerUri(context, "https://example.com/wallpaper.jpg"))
        assertFalse(
            WallpaperUriAccess.hasDurableReadAccess(
                context,
                Uri.parse("content://com.example.revoked/document/wallpaper")
            )
        )
    }
}
