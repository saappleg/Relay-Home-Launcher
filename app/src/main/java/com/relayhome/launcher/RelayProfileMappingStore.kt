package com.relayhome.launcher

import android.content.Context
import com.relayhome.launcher.data.RelaySettingsRepository
import java.text.Normalizer
import java.util.Locale

/** Maps a local Relay/Nuvio profile to an opaque RelayTube profile id. */
internal object RelayProfileMappingStore {
    fun get(context: Context, nuvioProfile: Int): String? =
        (RelaySettingsRepository.getManualProfileMapping(context, nuvioProfile)
            ?: RelaySettingsRepository.getResolvedProfileMapping(context, nuvioProfile))
            ?.let(::cleanOpaqueId)

    fun setManual(context: Context, nuvioProfile: Int, relayTubeProfileId: String?) {
        RelaySettingsRepository.saveManualProfileMapping(
            context,
            nuvioProfile,
            relayTubeProfileId?.let(::cleanOpaqueId)
        )
    }

    /** Stores a candidate only; resolve() promotes it after an exact profile-name match. */
    fun set(context: Context, nuvioProfile: Int, relayTubeProfileId: String) {
        if (relayTubeProfileId.isBlank()) {
            RelaySettingsRepository.clearProfileMapping(context, nuvioProfile)
        } else {
            RelaySettingsRepository.saveProfileMappingCandidate(
                context,
                nuvioProfile,
                cleanOpaqueId(relayTubeProfileId)
            )
        }
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

        // A user-selected pairing is authoritative. Automatic name matching is only a
        // fallback, so a refresh cannot silently undo a deliberate pairing from Settings.
        getManualMapping(context, nuvioProfile.index)?.let { return it }

        val matched = relayTubeProfiles
            .filter { cleanOpaqueId(it.id) != null && normalizeProfileName(it.name) == normalizedName }
            .singleOrNull()

        // An opaque id saved for a different generation of profiles must not override a new
        // exact name match. If there is no name match, leave the profile visibly unmapped rather
        // than guessing from list order or the selected RelayTube profile.
        if (matched == null) {
            if (shouldClearMapping(get(context, nuvioProfile.index))) {
                set(context, nuvioProfile.index, "")
            }
            return null
        }
        val resolvedId = cleanOpaqueId(matched.id) ?: return null
        if (shouldPersistMapping(get(context, nuvioProfile.index), resolvedId)) {
            saveResolved(context, nuvioProfile.index, resolvedId)
        }
        return resolvedId
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
            RelaySettingsRepository.saveResolvedProfileMapping(context, nuvioProfile, cleanId)
        }
    }

    private fun getManualMapping(context: Context, nuvioProfile: Int): String? =
        RelaySettingsRepository.getManualProfileMapping(context, nuvioProfile)?.let(::cleanOpaqueId)

    internal fun shouldPersistMapping(current: String?, desired: String): Boolean = current != desired

    internal fun shouldClearMapping(current: String?): Boolean = current != null

    private const val MAX_OPAQUE_ID_LENGTH = 128
}
