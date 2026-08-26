package com.relayhome.launcher

import android.content.Context

/** Persists the provider tabs the household has deliberately activated for Relay Home. */
internal object ProviderSettingsStore {
    private const val preferencesName = "relay_provider_settings"
    private const val enabledKey = "enabled_provider_names"

    fun load(context: Context, fallback: Set<Provider>): Set<Provider> {
        val saved = context.applicationContext
            .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getStringSet(enabledKey, null)
            ?: return fallback
        return saved.mapNotNull { value -> Provider.entries.firstOrNull { it.name == value } }.toSet()
    }

    fun save(context: Context, providers: Set<Provider>) {
        context.applicationContext
            .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(enabledKey, providers.map { it.name }.toSet())
            .apply()
    }
}
