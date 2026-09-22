package com.relayhome.launcher

import com.relayhome.launcher.data.PersonalRating
import com.relayhome.launcher.ui.shared.MediaItem
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.ui.shared.contentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RelayRatingsModelTest {
    @Test
    fun storageValues_roundTripWithoutUnknownRatings() {
        PersonalRating.entries.forEach { rating ->
            assertEquals(rating, PersonalRating.fromStorage(rating.storageValue))
            assertEquals(rating, PersonalRating.fromStorage("  ${rating.storageValue.uppercase()}  "))
        }
        assertNull(PersonalRating.fromStorage(null))
        assertNull(PersonalRating.fromStorage("provider-rating"))
    }

    @Test
    fun contentKey_usesRelayStableProviderIdentifierWhenAvailable() {
        val byProviderId = MediaItem(
            title = "Changed display title",
            provider = Provider.NUVIO,
            progress = 0f,
            colors = emptyList(),
            artworkUrl = "",
            providerContentId = "movie-123"
        )
        val sameTitleDifferentProviderId = byProviderId.copy(providerContentId = "movie-456")
        val sameProviderIdUpdatedMetadata = byProviderId.copy(title = "Updated title", artworkUrl = "new-artwork")

        assertNotEquals(byProviderId.contentKey(), sameTitleDifferentProviderId.contentKey())
        assertEquals(byProviderId.contentKey(), sameProviderIdUpdatedMetadata.contentKey())
    }
}
