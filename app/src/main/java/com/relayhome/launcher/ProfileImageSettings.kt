package com.relayhome.launcher

import android.content.Context

internal object ProfileImageSettings {
    private const val preferencesName = "relay_profile"
    private const val imageUriKey = "custom_image_uri"

    fun load(context: Context, profileScope: String = "default"): String? {
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val scopedKey = "${imageUriKey}_${profileScope.replace(Regex("[^A-Za-z0-9_.-]"), "_")}"
        val scoped = preferences.getString(scopedKey, null)?.takeIf { it.isNotBlank() }
        if (scoped != null) {
            if (preferences.contains(imageUriKey)) preferences.edit().remove(imageUriKey).apply()
            return scoped
        }
        val legacy = preferences.getString(imageUriKey, null)?.takeIf { it.isNotBlank() }
        if (legacy == null) return null
        val hasOtherScopedImage = preferences.all.keys.any {
            it.startsWith("${imageUriKey}_") && it != scopedKey
        }
        val importLegacy = !preferences.contains(scopedKey) && !hasOtherScopedImage
        preferences.edit().apply {
            if (importLegacy) putString(scopedKey, legacy)
            remove(imageUriKey)
        }.apply()
        return legacy.takeIf { importLegacy }
    }

    fun save(context: Context, uri: String, profileScope: String = "default") {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString("${imageUriKey}_${profileScope.replace(Regex("[^A-Za-z0-9_.-]"), "_")}", uri)
            .apply()
    }

    fun clear(context: Context, profileScope: String = "default") {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .remove("${imageUriKey}_${profileScope.replace(Regex("[^A-Za-z0-9_.-]"), "_")}")
            .apply()
    }
}
