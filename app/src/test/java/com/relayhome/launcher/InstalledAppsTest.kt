package com.relayhome.launcher

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledAppsTest {
    @Test
    fun exclusionRules_keepRelayOut_butProviderPackagesDiscoverable() {
        val relayPackage = "com.relayhome.launcher"

        assertTrue(InstalledApps.isExcludedPackage(relayPackage, relayPackage))
        listOf(
            "com.nuvio.tv",
            "com.relaytube.beta",
            "com.relaytube.stable",
            "com.relaytube.fdroid",
            "app.smarttube.stable",
            "org.smarttube.beta"
        ).forEach { packageName ->
            assertTrue(
                "$packageName should remain discoverable in All Apps",
                !InstalledApps.isExcludedPackage(relayPackage, packageName)
            )
        }
        assertTrue(!InstalledApps.isExcludedPackage(relayPackage, "com.netflix.ninja"))
    }

    @Test
    fun exclusionRules_areExact_soRelayLikePackagesRemainDiscoverable() {
        val relayPackage = "com.relayhome.launcher"

        listOf(
            "com.relayhome.launcher.debug",
            "com.relaytube",
            "com.relaytube.evil"
        ).forEach { packageName ->
            assertTrue(!InstalledApps.isExcludedPackage(relayPackage, packageName))
        }
    }

    @Test
    fun cache_reusesSnapshot_untilInvalidated() {
        val cache = InstalledAppsDiscoveryCache<String>()
        val discoveries = AtomicInteger()
        val discover = {
            discoveries.incrementAndGet()
            listOf("snapshot")
        }

        assertEquals(listOf("snapshot"), cache.getOrDiscover(discover))
        assertEquals(listOf("snapshot"), cache.getOrDiscover(discover))
        assertEquals(1, discoveries.get())

        cache.invalidate()
        assertEquals(listOf("snapshot"), cache.getOrDiscover(discover))
        assertEquals(2, discoveries.get())
    }

    @Test
    fun staleDiscovery_afterInvalidation_cannotRepopulateCache() {
        val cache = InstalledAppsDiscoveryCache<String>()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val discoveries = AtomicInteger()
        var staleResult: List<String>? = null

        val thread = Thread {
            staleResult = cache.getOrDiscover {
                discoveries.incrementAndGet()
                started.countDown()
                assertTrue(release.await(5, TimeUnit.SECONDS))
                listOf("stale")
            }
        }
        thread.start()
        assertTrue(started.await(5, TimeUnit.SECONDS))

        cache.invalidate()
        release.countDown()
        thread.join(5_000)
        assertEquals(listOf("stale"), staleResult)

        val current = cache.getOrDiscover {
            discoveries.incrementAndGet()
            listOf("current")
        }
        assertEquals(listOf("current"), current)
        assertEquals(2, discoveries.get())
    }
}
