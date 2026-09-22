package com.relayhome.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.relayhome.launcher.data.RelaySettingsRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelaySettingsRepositoryConcurrencyAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearStore() {
        runBlocking { RelaySettingsRepository.resetForTesting(context) }
        context.getSharedPreferences("relay_profile_mappings", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun restoreStore() {
        runBlocking { RelaySettingsRepository.resetForTesting(context) }
    }

    @Test
    fun concurrentProfileMappingWrites_preserveEveryDistinctKey() {
        val workers = 16
        val executor = Executors.newFixedThreadPool(workers)
        val start = CountDownLatch(1)
        val done = CountDownLatch(workers)

        repeat(workers) { profile ->
            executor.execute {
                try {
                    assert(start.await(5, TimeUnit.SECONDS))
                    RelaySettingsRepository.saveResolvedProfileMapping(
                        context,
                        profile,
                        "relay-profile-$profile"
                    )
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assert(done.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        runBlocking { RelaySettingsRepository.awaitIdleForTesting(context) }
        repeat(workers) { profile ->
            assertEquals("relay-profile-$profile", RelaySettingsRepository.getResolvedProfileMapping(context, profile))
        }
    }
}
