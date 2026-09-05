package com.relayhome.launcher.data

import android.content.Context
import android.content.SharedPreferences
import com.relayhome.launcher.Provider

/**
 * Central storage for Relay Home's non-sensitive settings.
 *
 * The app currently does not depend on androidx.datastore, so this is a small
 * DataStore-shaped repository backed by one SharedPreferences file. Keeping
 * the storage boundary here makes the eventual androidx DataStore swap local
 * to this file while preserving the synchronous facade APIs used by the app.
 */
internal object RelaySettingsRepository {
    private const val dataStoreName = "relay_settings_data"
    private const val schemaVersionKey = "_schema_version"
    private const val currentSchemaVersion = 1

    private const val enabledProvidersKey = "providers.enabled_names"
    private const val searchProviderKey = "search.default_provider"
    private const val dateFormatKey = "display.date_format"
    private const val profileImageUriKey = "profile.custom_image_uri"
    private const val continueWatchingLimitPrefix = "continue_watching.provider_limit_"
    private const val profileMappingPrefix = "profile_mapping."
    private const val resolvedProfileMappingPrefix = "resolved_nuvio_"
    private const val candidateProfileMappingPrefix = "candidate_nuvio_"
    private const val legacyProfileMappingPrefix = "nuvio_"

    private const val legacyProviderPreferencesName = "relay_provider_settings"
    private const val legacyEnabledProvidersKey = "enabled_provider_names"
    private const val legacySearchPreferencesName = "relay_search"
    private const val legacySearchProviderKey = "default_provider"
    private const val legacyDisplayPreferencesName = "relay_display_settings"
    private const val legacyDateFormatKey = "date_format"
    private const val legacyProfilePreferencesName = "relay_profile"
    private const val legacyProfileImageUriKey = "custom_image_uri"
    private const val legacyContinueWatchingPreferencesName = "relay_continue_watching"
    private const val legacyContinueWatchingLimitPrefix = "provider_limit_"
    private const val legacyProfileMappingPreferencesName = "relay_profile_mappings"

    fun loadEnabledProviders(context: Context, fallback: Set<Provider>): Set<Provider> {
        val saved = runCatching {
            preferences(context).getStringSet(enabledProvidersKey, null)
        }.getOrNull()
            ?: return fallback
        return saved.mapNotNull { value ->
            Provider.entries.firstOrNull { it.name == value }
        }.toSet()
    }

    fun saveEnabledProviders(context: Context, providers: Set<Provider>) {
        preferences(context).edit()
            .putStringSet(enabledProvidersKey, providers.map { it.name }.toSet())
            .apply()
    }

    fun loadSearchProvider(context: Context): Provider {
        val stored = runCatching {
            preferences(context).getString(searchProviderKey, null)
        }.getOrNull()
        return Provider.entries.firstOrNull { it.name == stored } ?: Provider.NUVIO
    }

    fun saveSearchProvider(context: Context, provider: Provider) {
        preferences(context).edit()
            .putString(searchProviderKey, provider.name)
            .apply()
    }

    fun loadDateFormat(context: Context, defaultName: String): String {
        return runCatching {
            preferences(context).getString(dateFormatKey, defaultName)
        }.getOrNull() ?: defaultName
    }

    fun saveDateFormat(context: Context, valueName: String) {
        preferences(context).edit()
            .putString(dateFormatKey, valueName)
            .apply()
    }

    fun loadProfileImageUri(context: Context): String? =
        runCatching { preferences(context).getString(profileImageUriKey, null) }.getOrNull()

    fun saveProfileImageUri(context: Context, uri: String) {
        preferences(context).edit()
            .putString(profileImageUriKey, uri)
            .apply()
    }

    fun clearProfileImageUri(context: Context) {
        preferences(context).edit()
            .remove(profileImageUriKey)
            .apply()
    }

    fun loadContinueWatchingLimit(
        context: Context,
        provider: Provider,
        defaultLimit: Int,
        minimum: Int,
        maximum: Int
    ): Int = runCatching {
        preferences(context).getInt(continueWatchingLimitKey(provider.name), defaultLimit)
    }.getOrDefault(defaultLimit)
        .coerceIn(minimum, maximum)

    fun saveContinueWatchingLimit(
        context: Context,
        provider: Provider,
        limit: Int,
        minimum: Int,
        maximum: Int
    ) {
        preferences(context).edit()
            .putInt(continueWatchingLimitKey(provider.name), limit.coerceIn(minimum, maximum))
            .apply()
    }

    fun getResolvedProfileMapping(context: Context, nuvioProfile: Int): String? =
        runCatching {
            preferences(context).getString(profileMappingKey(resolvedProfileMappingPrefix, nuvioProfile), null)
        }.getOrNull()

    fun saveProfileMappingCandidate(context: Context, nuvioProfile: Int, value: String?) {
        val editor = preferences(context).edit()
        if (value == null) {
            editor.remove(profileMappingKey(candidateProfileMappingPrefix, nuvioProfile))
        } else {
            editor.putString(profileMappingKey(candidateProfileMappingPrefix, nuvioProfile), value)
        }
        editor.apply()
    }

    fun clearProfileMapping(context: Context, nuvioProfile: Int) {
        preferences(context).edit()
            .remove(profileMappingKey(candidateProfileMappingPrefix, nuvioProfile))
            .remove(profileMappingKey(resolvedProfileMappingPrefix, nuvioProfile))
            .remove(profileMappingKey(legacyProfileMappingPrefix, nuvioProfile))
            .apply()
    }

    fun saveResolvedProfileMapping(context: Context, nuvioProfile: Int, value: String) {
        preferences(context).edit()
            .putString(profileMappingKey(resolvedProfileMappingPrefix, nuvioProfile), value)
            .remove(profileMappingKey(candidateProfileMappingPrefix, nuvioProfile))
            .remove(profileMappingKey(legacyProfileMappingPrefix, nuvioProfile))
            .apply()
    }

    private fun preferences(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        return appContext.getSharedPreferences(dataStoreName, Context.MODE_PRIVATE).also {
            migrateLegacySettings(appContext, it)
        }
    }

    /**
     * Migrates schema 0 (the six independent preference files) to schema 1.
     * The destination is populated only when its key is absent, and the
     * legacy stores are intentionally never edited or deleted. If commit()
     * fails, the version marker is not written and a later access retries the
     * migration safely.
     */
    private fun migrateLegacySettings(context: Context, destination: SharedPreferences) {
        val storedVersion = runCatching {
            destination.getInt(schemaVersionKey, 0)
        }.getOrDefault(0)

        // A newer schema owns the file; an older binary must not rewrite it.
        if (storedVersion >= currentSchemaVersion || storedVersion < 0) return
        if (storedVersion != 0) return

        val editor = destination.edit()
        val providerPreferences = context.getSharedPreferences(
            legacyProviderPreferencesName,
            Context.MODE_PRIVATE
        )
        migrateStringSet(
            source = providerPreferences,
            sourceKey = legacyEnabledProvidersKey,
            destination = destination,
            destinationKey = enabledProvidersKey,
            editor = editor
        )

        val searchPreferences = context.getSharedPreferences(
            legacySearchPreferencesName,
            Context.MODE_PRIVATE
        )
        migrateString(
            source = searchPreferences,
            sourceKey = legacySearchProviderKey,
            destination = destination,
            destinationKey = searchProviderKey,
            editor = editor
        )

        val displayPreferences = context.getSharedPreferences(
            legacyDisplayPreferencesName,
            Context.MODE_PRIVATE
        )
        migrateString(
            source = displayPreferences,
            sourceKey = legacyDateFormatKey,
            destination = destination,
            destinationKey = dateFormatKey,
            editor = editor
        )

        val profilePreferences = context.getSharedPreferences(
            legacyProfilePreferencesName,
            Context.MODE_PRIVATE
        )
        migrateString(
            source = profilePreferences,
            sourceKey = legacyProfileImageUriKey,
            destination = destination,
            destinationKey = profileImageUriKey,
            editor = editor
        )

        val continueWatchingPreferences = context.getSharedPreferences(
            legacyContinueWatchingPreferencesName,
            Context.MODE_PRIVATE
        )
        safeAllKeys(continueWatchingPreferences)
            .filter { it.startsWith(legacyContinueWatchingLimitPrefix) }
            .forEach { sourceKey ->
                migrateInt(
                    source = continueWatchingPreferences,
                    sourceKey = sourceKey,
                    destination = destination,
                    destinationKey = continueWatchingLimitPrefix +
                        sourceKey.removePrefix(legacyContinueWatchingLimitPrefix),
                    editor = editor
                )
            }

        val profileMappingPreferences = context.getSharedPreferences(
            legacyProfileMappingPreferencesName,
            Context.MODE_PRIVATE
        )
        safeAllKeys(profileMappingPreferences)
            .filter {
                it.startsWith(resolvedProfileMappingPrefix) ||
                    it.startsWith(candidateProfileMappingPrefix) ||
                    it.startsWith(legacyProfileMappingPrefix)
            }
            .forEach { sourceKey ->
                migrateString(
                    source = profileMappingPreferences,
                    sourceKey = sourceKey,
                    destination = destination,
                    destinationKey = profileMappingPrefix + sourceKey,
                    editor = editor
                )
            }

        // Put the marker in the same commit as the copied values. Legacy
        // preferences remain available for downgrade compatibility and audit.
        editor.putInt(schemaVersionKey, currentSchemaVersion).commit()
    }

    private fun migrateString(
        source: SharedPreferences,
        sourceKey: String,
        destination: SharedPreferences,
        destinationKey: String,
        editor: SharedPreferences.Editor
    ) {
        if (!source.contains(sourceKey) || destination.contains(destinationKey)) return
        runCatching { source.getString(sourceKey, null) }
            .getOrNull()
            ?.let { editor.putString(destinationKey, it) }
    }

    private fun migrateStringSet(
        source: SharedPreferences,
        sourceKey: String,
        destination: SharedPreferences,
        destinationKey: String,
        editor: SharedPreferences.Editor
    ) {
        if (!source.contains(sourceKey) || destination.contains(destinationKey)) return
        runCatching { source.getStringSet(sourceKey, null) }
            .getOrNull()
            ?.let { editor.putStringSet(destinationKey, it.toSet()) }
    }

    private fun migrateInt(
        source: SharedPreferences,
        sourceKey: String,
        destination: SharedPreferences,
        destinationKey: String,
        editor: SharedPreferences.Editor
    ) {
        if (!source.contains(sourceKey) || destination.contains(destinationKey)) return
        runCatching { source.getInt(sourceKey, 0) }
            .getOrNull()
            ?.let { editor.putInt(destinationKey, it) }
    }

    private fun safeAllKeys(preferences: SharedPreferences): Set<String> =
        runCatching { preferences.all.keys }.getOrDefault(emptySet())

    private fun continueWatchingLimitKey(providerName: String): String =
        continueWatchingLimitPrefix + providerName

    private fun profileMappingKey(kind: String, nuvioProfile: Int): String =
        profileMappingPrefix + kind + nuvioProfile
}
