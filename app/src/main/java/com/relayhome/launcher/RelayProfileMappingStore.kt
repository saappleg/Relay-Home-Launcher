package com.relayhome.launcher

import android.content.Context
import java.security.MessageDigest

/** Maps a local Relay/Nuvio profile to an opaque RelayTube profile id. */
internal object RelayProfileMappingStore {
    private const val preferencesName = "relay_profile_mappings"

    fun get(context: Context, accountId: String, nuvioProfile: Int): String? {
        if (accountId.isBlank()) return null
        return preferences(context).getString(mappingKey(accountId, nuvioProfile), null)
    }

    fun set(context: Context, accountId: String, nuvioProfile: Int, relayTubeProfileId: String) {
        if (accountId.isBlank() || relayTubeProfileId.isBlank()) return
        val preferences = preferences(context)
        val targetKey = mappingKey(accountId, nuvioProfile)
        val accountPrefix = accountMappingPrefix(accountId)
        preferences.edit().apply {
            preferences.all.keys
                .filter { it.startsWith(accountPrefix) && it != targetKey }
                .filter { preferences.getString(it, null) == relayTubeProfileId }
                .forEach { key -> remove(key) }
            putString(targetKey, relayTubeProfileId)
        }.apply()
    }

    fun resolve(
        context: Context,
        accountId: String,
        nuvioProfile: NuvioProfile,
        relayTubeProfiles: List<RelayTubeProfile>
    ): String? {
        if (accountId.isBlank()) return null
        val preferences = preferences(context)
        get(context, accountId, nuvioProfile.index)?.let { saved ->
            if (relayTubeProfiles.any { it.id == saved }) return saved
        }
        val normalizedName = nuvioProfile.name.trim().lowercase()
        val matched = relayTubeProfiles.singleOrNull { it.name.trim().lowercase() == normalizedName }
        val mappedElsewhere = matched?.id?.let { id ->
            preferences.all.keys.any { key ->
                key.startsWith(accountMappingPrefix(accountId)) && key != mappingKey(accountId, nuvioProfile.index) &&
                    preferences.getString(key, null) == id
            }
        } == true
        return matched?.id?.takeUnless { mappedElsewhere }?.also { set(context, accountId, nuvioProfile.index, it) }
    }

    private fun accountMappingPrefix(accountId: String): String = mappingKey(accountId, 0).substringBeforeLast('_') + "_"

    private fun mappingKey(accountId: String, nuvioProfile: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(accountId.toByteArray())
            .take(12)
            .joinToString("") { byte -> "%02x".format(byte) }
        return "account_${digest}_nuvio_$nuvioProfile"
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
}
