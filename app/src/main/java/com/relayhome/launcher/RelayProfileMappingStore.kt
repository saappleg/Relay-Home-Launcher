package com.relayhome.launcher

import android.content.Context
import java.util.Locale

/** Maps a local Relay/Nuvio profile to an opaque RelayTube profile id. */
internal object RelayProfileMappingStore {
    private const val preferencesName = "relay_profile_mappings"

    fun get(context: Context, nuvioProfile: Int): String? =
        preferences(context).getString(mappingKey(nuvioProfile), null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    /** Stores a candidate only; resolve() promotes it after an exact profile-name match. */
    fun set(context: Context, nuvioProfile: Int, relayTubeProfileId: String) {
        val editor = preferences(context).edit()
        if (relayTubeProfileId.isBlank()) {
            editor.remove(candidateKey(nuvioProfile))
                .remove(mappingKey(nuvioProfile))
                .remove(legacyMappingKey(nuvioProfile))
        } else {
            editor.putString(candidateKey(nuvioProfile), relayTubeProfileId.trim())
        }
        editor.apply()
    }

    @Suppress("UNUSED_PARAMETER")
    fun resolve(
        context: Context,
        nuvioProfile: NuvioProfile,
        relayTubeProfiles: List<RelayTubeProfile>,
        allowSelectedFallback: Boolean
    ): String? {
        val normalizedName = normalize(nuvioProfile.name)
        if (normalizedName.isBlank()) return null
        val matched = relayTubeProfiles.filter { normalize(it.name) == normalizedName }.singleOrNull()

        // An opaque id saved for a different generation of profiles must not override a new
        // exact name match. If there is no name match, leave the profile visibly unmapped rather
        // than guessing from list order or the selected RelayTube profile.
        if (matched == null) {
            set(context, nuvioProfile.index, "")
            return null
        }
        return matched.id.takeIf { it.isNotBlank() }?.also {
            saveResolved(context, nuvioProfile.index, it)
        }
    }

    private fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)

    private fun saveResolved(context: Context, nuvioProfile: Int, relayTubeProfileId: String) {
        preferences(context).edit()
            .putString(mappingKey(nuvioProfile), relayTubeProfileId)
            .remove(candidateKey(nuvioProfile))
            .remove(legacyMappingKey(nuvioProfile))
            .apply()
    }

    private fun mappingKey(nuvioProfile: Int) = "resolved_nuvio_$nuvioProfile"

    private fun candidateKey(nuvioProfile: Int) = "candidate_nuvio_$nuvioProfile"

    private fun legacyMappingKey(nuvioProfile: Int) = "nuvio_$nuvioProfile"

    private fun preferences(context: Context) =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
}
