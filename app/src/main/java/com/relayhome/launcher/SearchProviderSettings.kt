package com.relayhome.launcher

import android.content.Context
import com.relayhome.launcher.data.RelaySettingsRepository
import com.relayhome.launcher.ui.shared.Provider

internal object SearchProviderSettings {
    fun load(context: Context): Provider = RelaySettingsRepository.loadSearchProvider(context)

    fun save(context: Context, provider: Provider) {
        RelaySettingsRepository.saveSearchProvider(context, provider)
    }
}
