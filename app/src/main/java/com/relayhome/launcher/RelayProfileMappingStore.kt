package com.relayhome.launcher

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/** Maps a local Relay/Nuvio profile to an opaque RelayTube profile id. */
internal object RelayProfileMappingStore {
    private const val preferencesName = "relay_profile_mappings"

    fun get(context: Context, nuvioProfile: Int): String? =
        preferences(context).getString(mappingKey(nuvioProfile), null)
            ?.let(::cleanOpaqueId)

    /** Stores a candidate only; resolve() promotes it after an exact profile-name match. */
    fun set(context: Context, nuvioProfile: Int, relayTubeProfileId: String) {
        val editor = preferences(context).edit()
        if (relayTubeProfileId.isBlank()) {
            editor.remove(candidateKey(nuvioProfile))
                .remove(mappingKey(nuvioProfile))
                .remove(legacyMappingKey(nuvioProfile))
        } else {
            cleanOpaqueId(relayTubeProfileId)?.let { editor.putString(candidateKey(nuvioProfile), it) }
                ?: editor.remove(candidateKey(nuvioProfile))
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
        val normalizedName = normalizeProfileName(nuvioProfile.name)
        if (normalizedName.isBlank()) return null
        val matched = relayTubeProfiles
            .filter { cleanOpaqueId(it.id) != null && normalizeProfileName(it.name) == normalizedName }
            .singleOrNull()

        // An opaque id saved for a different generation of profiles must not override a new
        // exact name match. If there is no name match, leave the profile visibly unmapped rather
        // than guessing from list order or the selected RelayTube profile.
        if (matched == null) {
            set(context, nuvioProfile.index, "")
            return null
        }
        return cleanOpaqueId(matched.id)?.also {
            saveResolved(context, nuvioProfile.index, it)
        }
    }

    private fun normalizeProfileName(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "")

    private fun cleanOpaqueId(value: String?): String? = value
        ?.trim()
        ?.takeIf { it.isNotBlank() && it.length <= MAX_OPAQUE_ID_LENGTH }
        ?.takeIf { id -> id.none { it.isWhitespace() || it.isISOControl() } }

    private fun saveResolved(context: Context, nuvioProfile: Int, relayTubeProfileId: String) {
        cleanOpaqueId(relayTubeProfileId)?.let { cleanId ->
            preferences(context).edit()
                .putString(mappingKey(nuvioProfile), cleanId)
                .remove(candidateKey(nuvioProfile))
                .remove(legacyMappingKey(nuvioProfile))
                .apply()
        }
    }

    private fun mappingKey(nuvioProfile: Int) = "resolved_nuvio_$nuvioProfile"

    private fun candidateKey(nuvioProfile: Int) = "candidate_nuvio_$nuvioProfile"

    private fun legacyMappingKey(nuvioProfile: Int) = "nuvio_$nuvioProfile"

    private fun preferences(context: Context) =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    private const val MAX_OPAQUE_ID_LENGTH = 128
}
