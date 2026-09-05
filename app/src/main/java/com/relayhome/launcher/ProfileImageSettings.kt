package com.relayhome.launcher

import android.content.Context
import com.relayhome.launcher.data.RelaySettingsRepository

internal object ProfileImageSettings {
    fun load(context: Context): String? =
        RelaySettingsRepository.loadProfileImageUri(context)
            ?.takeIf { it.isNotBlank() }

    fun save(context: Context, uri: String) {
        RelaySettingsRepository.saveProfileImageUri(context, uri)
    }

    fun clear(context: Context) {
        RelaySettingsRepository.clearProfileImageUri(context)
    }
}
