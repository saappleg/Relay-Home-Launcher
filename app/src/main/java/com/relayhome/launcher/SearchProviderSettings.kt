package com.relayhome.launcher

import android.content.Context
import com.relayhome.launcher.data.RelaySettingsRepository

internal object SearchProviderSettings {
    fun load(context: Context): Provider = RelaySettingsRepository.loadSearchProvider(context)

    fun save(context: Context, provider: Provider) {
        RelaySettingsRepository.saveSearchProvider(context, provider)
    }
}
