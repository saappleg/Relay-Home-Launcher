package com.relayhome.launcher

import android.content.Context
import com.relayhome.launcher.data.RelaySettingsRepository
import com.relayhome.launcher.ui.shared.Provider

/** Persists the provider tabs the household has deliberately activated for Relay Home. */
internal object ProviderSettingsStore {
    fun load(context: Context, fallback: Set<Provider>): Set<Provider> {
        return RelaySettingsRepository.loadEnabledProviders(context, fallback)
    }

    fun save(context: Context, providers: Set<Provider>) {
        RelaySettingsRepository.saveEnabledProviders(context, providers)
    }
}
