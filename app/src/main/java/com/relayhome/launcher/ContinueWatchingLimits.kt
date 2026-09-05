package com.relayhome.launcher

import android.content.Context
import com.relayhome.launcher.data.RelaySettingsRepository

/** Per-provider presentation limits for Relay's combined Continue Watching rail. */
internal object ContinueWatchingLimits {
    const val defaultLimit = 8

    fun load(context: Context): Map<Provider, Int> = Provider.entries.associateWith { provider ->
        RelaySettingsRepository.loadContinueWatchingLimit(
            context = context,
            provider = provider,
            defaultLimit = defaultLimit,
            minimum = 1,
            maximum = 24
        )
    }

    fun save(context: Context, provider: Provider, limit: Int) {
        RelaySettingsRepository.saveContinueWatchingLimit(
            context = context,
            provider = provider,
            limit = limit,
            minimum = 1,
            maximum = 24
        )
    }
}
