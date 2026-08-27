package com.relayhome.launcher

import android.content.Context

internal object ProfileImageSettings {
    private const val preferencesName = "relay_profile"
    private const val imageUriKey = "custom_image_uri"

    fun load(context: Context): String? =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getString(imageUriKey, null)
            ?.takeIf { it.isNotBlank() }

    fun save(context: Context, uri: String) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(imageUriKey, uri)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .remove(imageUriKey)
            .apply()
    }
}
