package com.relayhome.launcher

import android.content.Context

internal object SearchProviderSettings {
    private const val preferencesName = "relay_search"
    private const val providerKey = "default_provider"

    fun load(context: Context): Provider {
        val stored = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getString(providerKey, null)
        return Provider.entries.firstOrNull { it.name == stored } ?: Provider.NUVIO
    }

    fun save(context: Context, provider: Provider) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(providerKey, provider.name)
            .apply()
    }
}
