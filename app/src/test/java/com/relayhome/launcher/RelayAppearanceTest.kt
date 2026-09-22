package com.relayhome.launcher

import com.relayhome.launcher.ui.shared.RelayAppearance
import com.relayhome.launcher.ui.shared.RelayPalette
import com.relayhome.launcher.ui.shared.relayPaletteForAppearance
import com.relayhome.launcher.ui.shared.relayPaletteFromSwatches
import com.relayhome.launcher.ui.shared.orbitalPalette
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RelayAppearanceTest {
    @Test
    fun storageValues_preserveExistingModesAndAddBackdropMode() {
        assertEquals(RelayAppearance.ORBITAL, RelayAppearance.fromStorage("orbital"))
        assertEquals(RelayAppearance.VIOLET, RelayAppearance.fromStorage("violet"))
        assertEquals(RelayAppearance.AUTOMATIC, RelayAppearance.fromStorage("automatic"))
        assertEquals(RelayAppearance.FROM_BACKDROP, RelayAppearance.fromStorage("from_backdrop"))
        assertEquals(RelayAppearance.ORBITAL, RelayAppearance.fromStorage(null))
        assertEquals(RelayAppearance.ORBITAL, RelayAppearance.fromStorage("removed-or-invalid"))
    }

    @Test
    fun fromBackdrop_withoutArtwork_fallsBackToOrbital() {
        assertEquals(
            orbitalPalette,
            relayPaletteForAppearance(
                appearance = RelayAppearance.FROM_BACKDROP,
                dynamicColorScheme = null,
                focusedArtworkPalette = null
            )
        )
    }

    @Test
    fun extractedPalette_becomesTheSharedPaletteOnlyForBackdropMode() {
        val extracted = relayPaletteFromSwatches(
            accentRgb = 0xFF2A9D8F.toInt(),
            backdropRgb = 0xFF264653.toInt(),
            glowRgb = 0xFFE9C46A.toInt()
        )
        requireNotNull(extracted)

        assertSame(
            extracted,
            relayPaletteForAppearance(RelayAppearance.FROM_BACKDROP, null, extracted)
        )
        assertEquals(orbitalPalette, relayPaletteForAppearance(RelayAppearance.ORBITAL, null, extracted))
        assertEquals(
            com.relayhome.launcher.ui.shared.violetPalette,
            relayPaletteForAppearance(RelayAppearance.VIOLET, null, extracted)
        )
        assertNotEquals(extracted, orbitalPalette)
    }

    @Test
    fun swatchConversion_requiresAnAccentAndBuildsAllThreePaletteRoles() {
        assertNull(relayPaletteFromSwatches(null, 0xFF112233.toInt(), 0xFF445566.toInt()))

        val palette: RelayPalette = requireNotNull(
            relayPaletteFromSwatches(
                accentRgb = 0xFF102030.toInt(),
                backdropRgb = 0xFF405060.toInt(),
                glowRgb = 0xFF708090.toInt()
            )
        )
        assertEquals(Color(0xFF102030.toInt()), palette.accent)
        assertEquals(Color(0xFF708090.toInt()).copy(alpha = .72f), palette.glow)
        assertEquals(Color(0xFF1B2228.toInt()), palette.backdrop)
    }
}
