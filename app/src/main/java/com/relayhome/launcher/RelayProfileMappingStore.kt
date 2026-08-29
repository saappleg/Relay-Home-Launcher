package com.relayhome.launcher

import android.content.Context

/** Maps a local Relay/Nuvio profile to an opaque RelayTube profile id. */
internal object RelayProfileMappingStore {
    private const val preferencesName = "relay_profile_mappings"

    fun get(context: Context, nuvioProfile: Int): String? =
        preferences(context).getString("nuvio_$nuvioProfile", null)

    fun set(context: Context, nuvioProfile: Int, relayTubeProfileId: String) {
        preferences(context).edit().putString("nuvio_$nuvioProfile", relayTubeProfileId).apply()
    }

    fun resolve(
        context: Context,
        nuvioProfile: NuvioProfile,
        relayTubeProfiles: List<RelayTubeProfile>,
        allowSelectedFallback: Boolean
    ): String? {
        get(context, nuvioProfile.index)?.let { saved ->
            if (relayTubeProfiles.any { it.id == saved }) return saved
        }
        val normalizedName = nuvioProfile.name.trim().lowercase()
        val matched = relayTubeProfiles.firstOrNull { it.name.trim().lowercase() == normalizedName }
            ?: relayTubeProfiles.singleOrNull()
            ?: relayTubeProfiles.firstOrNull { allowSelectedFallback && it.selected }
        return matched?.id?.also { set(context, nuvioProfile.index, it) }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
}
