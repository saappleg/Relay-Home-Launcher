package com.relayhome.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayProfileMappingStoreTest {
    @Test
    fun resolvedMapping_isWrittenOnlyWhenTheOpaqueIdChanges() {
        assertFalse(RelayProfileMappingStore.shouldPersistMapping("relay-profile-1", "relay-profile-1"))
        assertTrue(RelayProfileMappingStore.shouldPersistMapping("relay-profile-1", "relay-profile-2"))
        assertTrue(RelayProfileMappingStore.shouldPersistMapping(null, "relay-profile-1"))
    }

    @Test
    fun unmatchedMapping_isClearedOnlyWhenAResolvedValueExists() {
        assertFalse(RelayProfileMappingStore.shouldClearMapping(null))
        assertTrue(RelayProfileMappingStore.shouldClearMapping("relay-profile-1"))
    }
}
