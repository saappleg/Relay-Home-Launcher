package com.relayhome.launcher

import android.content.Context

/** Per-provider presentation limits for Relay's combined Continue Watching rail. */
internal object ContinueWatchingLimits {
    private const val preferencesName = "relay_continue_watching"
    private const val prefix = "provider_limit_"
    const val defaultLimit = 8

    fun load(context: Context): Map<Provider, Int> = Provider.entries.associateWith { provider ->
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getInt(prefix + provider.name, defaultLimit)
            .coerceIn(1, 24)
    }

    fun save(context: Context, provider: Provider, limit: Int) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putInt(prefix + provider.name, limit.coerceIn(1, 24))
            .apply()
    }
}
