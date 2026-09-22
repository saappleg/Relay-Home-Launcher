package com.relayhome.launcher

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.relayhome.launcher.ui.home.HOME_AMBIENT_GRAIN_ALPHA
import com.relayhome.launcher.ui.shared.RelayAppearance
import com.relayhome.launcher.ui.shared.relayArtworkPalette
import com.relayhome.launcher.ui.shared.relayPaletteForAppearance
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeAmbientBackdropAndroidTest {
    @Test
    fun grainAsset_staysSmallAndWithinSubtleOpacityRange() {
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        BitmapFactory.decodeResource(resources, R.drawable.relay_ambient_grain, bounds)

        assertEquals(256, bounds.outWidth)
        assertEquals(256, bounds.outHeight)
        assertTrue(HOME_AMBIENT_GRAIN_ALPHA in 0.03f..0.06f)
    }

    @Test
    fun focusedArtwork_extractsPaletteThatPropagatesToBackdropAppearance() = runBlocking {
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFF2A9D8F.toInt())
        }
        val extracted = relayArtworkPalette(BitmapDrawable(resources, bitmap))

        assertNotNull(extracted)
        assertEquals(
            extracted,
            relayPaletteForAppearance(RelayAppearance.FROM_BACKDROP, null, extracted)
        )
    }
}
