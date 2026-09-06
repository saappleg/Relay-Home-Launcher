package com.relayhome.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.relayhome.launcher.ui.home.HomeAmbientBackdrop
import com.relayhome.launcher.ui.home.HomeAmbientFocus
import com.relayhome.launcher.ui.home.rememberWallpaperUriResolution
import com.relayhome.launcher.ui.shared.orbitalPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WallpaperBackdropAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val resourceUri = "android.resource://${context.packageName}/drawable/relay_ambient_grain"

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resolution_reloadsReadableUri_andFallsBackForRevokedUri() {
        var validResolutionComplete = false
        var validResolutionUri: String? = null
        var invalidResolutionComplete = false
        var invalidResolutionUri: String? = null

        composeRule.setContent {
            MaterialTheme {
                val valid = rememberWallpaperUriResolution(resourceUri)
                val invalid = rememberWallpaperUriResolution("content://com.example.revoked/document/wallpaper")
                LaunchedEffect(valid) {
                    if (valid.complete) {
                        validResolutionComplete = true
                        validResolutionUri = valid.resolvedUri
                    }
                }
                LaunchedEffect(invalid) {
                    if (invalid.complete) {
                        invalidResolutionComplete = true
                        invalidResolutionUri = invalid.resolvedUri
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertTrue(validResolutionComplete)
        assertEquals(resourceUri, validResolutionUri)
        assertTrue(invalidResolutionComplete)
        assertNull(invalidResolutionUri)
    }

    @Test
    fun customWallpaper_isSelectedAsBackdropAndRenderedThroughCoil() {
        composeRule.setContent {
            MaterialTheme {
                HomeAmbientBackdrop(
                    focus = HomeAmbientFocus(
                        key = "wallpaper:$resourceUri",
                        artworkUrl = resourceUri,
                        fallbackPalette = orbitalPalette
                    ),
                    onArtworkPalette = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home-ambient-backdrop").assertIsDisplayed()
        composeRule.onNodeWithTag("home-ambient-wallpaper").assertIsDisplayed()
    }
}
