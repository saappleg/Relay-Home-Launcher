package com.relayhome.launcher

import android.content.Context

/** Per-provider presentation limits for Relay's combined Continue Watching rail. */
internal object ContinueWatchingLimits {
    private const val preferencesName = "relay_continue_watching"
    private const val prefix = "provider_limit_"
    const val defaultLimit = 8

    fun load(context: Context, profileScope: String = "default"): Map<Provider, Int> = Provider.entries.associateWith { provider ->
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val key = "${prefix}${profileScope.preferenceKey()}_${provider.name}"
        val saved = if (preferences.contains(key)) preferences.getInt(key, defaultLimit) else {
            val legacyKey = prefix + provider.name
            if (preferences.contains(legacyKey)) {
                val hasOtherScopedLimit = preferences.all.keys.any {
                    it.startsWith(prefix) && it.endsWith("_${provider.name}") && it != key && it != legacyKey
                }
                val legacyValue = preferences.getInt(legacyKey, defaultLimit)
                preferences.edit().apply {
                    if (!hasOtherScopedLimit) putInt(key, legacyValue)
                    remove(legacyKey)
                }.apply()
                if (hasOtherScopedLimit) defaultLimit else legacyValue
            } else defaultLimit
        }
        if (preferences.contains(key) && preferences.contains(prefix + provider.name)) {
            preferences.edit().remove(prefix + provider.name).apply()
        }
        saved.coerceIn(1, 24)
    }

    fun save(context: Context, provider: Provider, limit: Int, profileScope: String = "default") {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putInt("${prefix}${profileScope.preferenceKey()}_${provider.name}", limit.coerceIn(1, 24))
            .apply()
    }

    private fun String.preferenceKey(): String = replace(Regex("[^A-Za-z0-9_.-]"), "_")
}
