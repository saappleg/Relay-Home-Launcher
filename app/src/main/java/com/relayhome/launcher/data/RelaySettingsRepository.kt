package com.relayhome.launcher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.relayhome.launcher.ui.shared.Provider
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

private val Context.relaySettingsDataStore by preferencesDataStore(
    name = dataStoreName,
    produceMigrations = { context -> listOf(LegacySettingsMigration(context.applicationContext)) }
)

/**
 * Central storage for Relay Home's non-sensitive settings.
 *
 * Preferences DataStore is the source of truth. Existing facade methods remain synchronous: they
 * read an in-memory snapshot and schedule serialized DataStore writes on an IO scope. The
 * revision flow lets Compose re-read the snapshot after DataStore loads persisted values or
 * completes migration. No UI caller blocks on DataStore or legacy preference I/O.
 */
internal object RelaySettingsRepository {
    private data class Snapshot(val values: Preferences)

    private val lock = Any()
    private val writeMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val snapshot = AtomicReference(Snapshot(emptyPreferences()))
    private val _revision = MutableStateFlow(0L)
    private var initializedContext: Context? = null
    private var collectorJob: Job? = null
    private var migrationJob: Job? = null
    private var pendingWrites = 0

    /** Emits whenever the in-memory snapshot may have changed. */
    fun revision(context: Context): StateFlow<Long> {
        initialize(context)
        return _revision.asStateFlow()
    }

    fun loadEnabledProviders(context: Context, fallback: Set<Provider>): Set<Provider> {
        initialize(context)
        val saved = snapshot.get().values[stringSetPreferencesKey(enabledProvidersKey)]
            ?: return fallback
        return saved.mapNotNull { value -> Provider.entries.firstOrNull { it.name == value } }.toSet()
    }

    fun saveEnabledProviders(context: Context, providers: Set<Provider>) {
        updateSnapshotAndPersist(context) { it[stringSetPreferencesKey(enabledProvidersKey)] = providers.map { provider -> provider.name }.toSet() }
    }

    fun loadSearchProvider(context: Context): Provider {
        initialize(context)
        val stored = snapshot.get().values[stringPreferencesKey(searchProviderKey)]
        return Provider.entries.firstOrNull { it.name == stored } ?: Provider.NUVIO
    }

    fun saveSearchProvider(context: Context, provider: Provider) {
        updateSnapshotAndPersist(context) { it[stringPreferencesKey(searchProviderKey)] = provider.name }
    }

    fun loadDateFormat(context: Context, defaultName: String): String {
        initialize(context)
        return snapshot.get().values[stringPreferencesKey(dateFormatKey)] ?: defaultName
    }

    fun saveDateFormat(context: Context, valueName: String) {
        updateSnapshotAndPersist(context) { it[stringPreferencesKey(dateFormatKey)] = valueName }
    }

    fun loadProfileImageUri(context: Context): String? {
        initialize(context)
        return snapshot.get().values[stringPreferencesKey(profileImageUriKey)]
    }

    fun saveProfileImageUri(context: Context, uri: String) {
        updateSnapshotAndPersist(context) { it[stringPreferencesKey(profileImageUriKey)] = uri }
    }

    fun clearProfileImageUri(context: Context) {
        updateSnapshotAndPersist(context) { it.remove(stringPreferencesKey(profileImageUriKey)) }
    }

    fun loadContinueWatchingLimit(
        context: Context,
        provider: Provider,
        defaultLimit: Int,
        minimum: Int,
        maximum: Int
    ): Int {
        initialize(context)
        return (snapshot.get().values[intPreferencesKey(continueWatchingLimitKey(provider.name))]
            ?: defaultLimit).coerceIn(minimum, maximum)
    }

    fun saveContinueWatchingLimit(
        context: Context,
        provider: Provider,
        limit: Int,
        minimum: Int,
        maximum: Int
    ) {
        updateSnapshotAndPersist(context) {
            it[intPreferencesKey(continueWatchingLimitKey(provider.name))] = limit.coerceIn(minimum, maximum)
        }
    }

    fun getResolvedProfileMapping(context: Context, nuvioProfile: Int): String? {
        initialize(context)
        return snapshot.get().values[stringPreferencesKey(profileMappingKey(resolvedProfileMappingPrefix, nuvioProfile))]
    }

    fun saveProfileMappingCandidate(context: Context, nuvioProfile: Int, value: String?) {
        updateSnapshotAndPersist(context) {
            val key = stringPreferencesKey(profileMappingKey(candidateProfileMappingPrefix, nuvioProfile))
            if (value == null) it.remove(key) else it[key] = value
        }
    }

    fun clearProfileMapping(context: Context, nuvioProfile: Int) {
        updateSnapshotAndPersist(context) {
            it.remove(stringPreferencesKey(profileMappingKey(candidateProfileMappingPrefix, nuvioProfile)))
            it.remove(stringPreferencesKey(profileMappingKey(resolvedProfileMappingPrefix, nuvioProfile)))
            it.remove(stringPreferencesKey(profileMappingKey(legacyProfileMappingPrefix, nuvioProfile)))
        }
    }

    fun saveResolvedProfileMapping(context: Context, nuvioProfile: Int, value: String) {
        updateSnapshotAndPersist(context) {
            it[stringPreferencesKey(profileMappingKey(resolvedProfileMappingPrefix, nuvioProfile))] = value
            it.remove(stringPreferencesKey(profileMappingKey(candidateProfileMappingPrefix, nuvioProfile)))
            it.remove(stringPreferencesKey(profileMappingKey(legacyProfileMappingPrefix, nuvioProfile)))
        }
    }

    private fun initialize(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (initializedContext === appContext && collectorJob?.isActive == true) return
            initializedContext = appContext
            snapshot.set(Snapshot(emptyPreferences()))
            collectorJob?.cancel()
            migrationJob?.cancel()
            collectorJob = scope.launch {
                appContext.relaySettingsDataStore.data
                    .catch { emit(emptyPreferences()) }
                    .collect { values ->
                        synchronized(lock) {
                            // A migration emission can race an optimistic write. Never replace a
                            // newer local value with that stale emission.
                            if (pendingWrites == 0) {
                                snapshot.set(Snapshot(values))
                                _revision.value += 1
                            }
                        }
                    }
            }
            // DataMigration handles a normal cold start. This idempotent guard also covers a
            // store that was created by an older process but is missing its schema marker (for
            // example after corruption recovery or a test reset), and keeps the six-source copy
            // atomic through DataStore's updateData transaction.
            migrationJob = scope.launch {
                writeMutex.withLock {
                    appContext.relaySettingsDataStore.updateData { current ->
                        if ((current[intPreferencesKey(schemaVersionKey)] ?: 0) >= currentSchemaVersion) {
                            current
                        } else {
                            current.toMutablePreferences().also {
                                copyLegacyInto(it, appContext)
                                it[intPreferencesKey(schemaVersionKey)] = currentSchemaVersion
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateSnapshotAndPersist(
        context: Context,
        mutation: (MutablePreferences) -> Unit
    ) {
        initialize(context)
        val appContext = context.applicationContext
        val optimistic = snapshot.get().values.toMutablePreferences().also(mutation)
        synchronized(lock) {
            pendingWrites += 1
            snapshot.set(Snapshot(optimistic))
            _revision.value += 1
        }
        scope.launch {
            try {
                val persisted = writeMutex.withLock {
                    appContext.relaySettingsDataStore.updateData { current ->
                        current.toMutablePreferences().also(mutation)
                    }
                }
                synchronized(lock) {
                    snapshot.set(Snapshot(persisted))
                    pendingWrites -= 1
                    _revision.value += 1
                }
            } catch (_: Throwable) {
                synchronized(lock) {
                    pendingWrites -= 1
                    _revision.value += 1
                }
            }
        }
    }

    /** Suspends until DataStore has opened, migrated, and produced its current value. */
    suspend fun awaitReady(context: Context) {
        initialize(context)
        migrationJob?.join()
        val values = context.applicationContext.relaySettingsDataStore.data.first()
        synchronized(lock) {
            if (pendingWrites == 0) snapshot.set(Snapshot(values))
            _revision.value += 1
        }
    }

    // Test-only hooks keep instrumentation setup out of production facades. Tests call these
    // from runBlocking on a test thread, never from Compose or the Android main thread.
    internal suspend fun resetForTesting(context: Context) {
        val appContext = context.applicationContext
        while (true) {
            val idle = synchronized(lock) { pendingWrites == 0 }
            if (idle) break
            delay(10)
        }
        synchronized(lock) {
            collectorJob?.cancel()
            migrationJob?.cancel()
            collectorJob = null
            migrationJob = null
            initializedContext = null
            pendingWrites = 0
            snapshot.set(Snapshot(emptyPreferences()))
            _revision.value += 1
        }
        appContext.relaySettingsDataStore.updateData { emptyPreferences() }
    }

    internal suspend fun awaitIdleForTesting(context: Context) {
        initialize(context)
        while (true) {
            val idle = synchronized(lock) { pendingWrites == 0 }
            if (idle) break
            delay(10)
        }
        awaitReady(context)
    }

    internal suspend fun storedKeysForTesting(context: Context): Set<String> =
        context.applicationContext.relaySettingsDataStore.data.first().asMap().keys.map { it.name }.toSet()

    internal suspend fun putStringForTesting(context: Context, key: String, value: String) {
        context.applicationContext.relaySettingsDataStore.updateData { current ->
            current.toMutablePreferences().also { it[stringPreferencesKey(key)] = value }
        }
        awaitReady(context)
    }

    /** Used by the DataStore migration; kept internal so the migration remains a thin adapter. */
    internal fun copyLegacyInto(destination: MutablePreferences, context: Context) {
        copyExistingDestination(context.getSharedPreferences(dataStoreName, Context.MODE_PRIVATE), destination)
        copyLegacySource(context.getSharedPreferences(legacyProviderPreferencesName, Context.MODE_PRIVATE), destination)
        copyLegacySource(context.getSharedPreferences(legacySearchPreferencesName, Context.MODE_PRIVATE), destination)
        copyLegacySource(context.getSharedPreferences(legacyDisplayPreferencesName, Context.MODE_PRIVATE), destination)
        copyLegacySource(context.getSharedPreferences(legacyProfilePreferencesName, Context.MODE_PRIVATE), destination)
        copyLegacySource(context.getSharedPreferences(legacyContinueWatchingPreferencesName, Context.MODE_PRIVATE), destination)
        copyLegacySource(context.getSharedPreferences(legacyProfileMappingPreferencesName, Context.MODE_PRIVATE), destination)
    }

    private fun copyExistingDestination(source: SharedPreferences, destination: MutablePreferences) {
        source.getStringSetSafely(enabledProvidersKey)?.let { if (!destination.contains(stringSetPreferencesKey(enabledProvidersKey))) destination[stringSetPreferencesKey(enabledProvidersKey)] = it }
        source.getStringSafely(searchProviderKey)?.let { if (!destination.contains(stringPreferencesKey(searchProviderKey))) destination[stringPreferencesKey(searchProviderKey)] = it }
        source.getStringSafely(dateFormatKey)?.let { if (!destination.contains(stringPreferencesKey(dateFormatKey))) destination[stringPreferencesKey(dateFormatKey)] = it }
        source.getStringSafely(profileImageUriKey)?.let { if (!destination.contains(stringPreferencesKey(profileImageUriKey))) destination[stringPreferencesKey(profileImageUriKey)] = it }
        copyTypedDynamicKeys(source, destination, useLegacyNames = false)
        source.getIntSafely(schemaVersionKey)?.let { destination[intPreferencesKey(schemaVersionKey)] = it }
    }

    private fun copyLegacySource(source: SharedPreferences, destination: MutablePreferences) {
        source.getStringSetSafely(legacyEnabledProvidersKey)?.let { if (!destination.contains(stringSetPreferencesKey(enabledProvidersKey))) destination[stringSetPreferencesKey(enabledProvidersKey)] = it }
        source.getStringSafely(legacySearchProviderKey)?.let { if (!destination.contains(stringPreferencesKey(searchProviderKey))) destination[stringPreferencesKey(searchProviderKey)] = it }
        source.getStringSafely(legacyDateFormatKey)?.let { if (!destination.contains(stringPreferencesKey(dateFormatKey))) destination[stringPreferencesKey(dateFormatKey)] = it }
        source.getStringSafely(legacyProfileImageUriKey)?.let { if (!destination.contains(stringPreferencesKey(profileImageUriKey))) destination[stringPreferencesKey(profileImageUriKey)] = it }
        copyTypedDynamicKeys(source, destination, useLegacyNames = true)
    }

    private fun copyTypedDynamicKeys(source: SharedPreferences, destination: MutablePreferences, useLegacyNames: Boolean) {
        safeAllKeys(source).filter { it.startsWith(if (useLegacyNames) legacyContinueWatchingLimitPrefix else continueWatchingLimitPrefix) }.forEach { key ->
            source.getIntSafely(key)?.let { value ->
                val suffix = if (useLegacyNames) key.removePrefix(legacyContinueWatchingLimitPrefix) else key.removePrefix(continueWatchingLimitPrefix)
                val destinationKey = intPreferencesKey(continueWatchingLimitPrefix + suffix)
                if (!destination.contains(destinationKey)) destination[destinationKey] = value
            }
        }
        safeAllKeys(source).filter { key ->
            if (useLegacyNames) {
                key.startsWith(resolvedProfileMappingPrefix) || key.startsWith(candidateProfileMappingPrefix) || key.startsWith(legacyProfileMappingPrefix)
            } else key.startsWith(profileMappingPrefix)
        }.forEach { key ->
            source.getStringSafely(key)?.let { value ->
                val destinationKey = stringPreferencesKey(if (useLegacyNames) profileMappingPrefix + key else key)
                if (!destination.contains(destinationKey)) destination[destinationKey] = value
            }
        }
    }

    private fun continueWatchingLimitKey(providerName: String): String = continueWatchingLimitPrefix + providerName

    private fun profileMappingKey(kind: String, nuvioProfile: Int): String = profileMappingPrefix + kind + nuvioProfile
}

private class LegacySettingsMigration(private val context: Context) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        (currentData[intPreferencesKey(schemaVersionKey)] ?: 0) < currentSchemaVersion

    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().also {
        RelaySettingsRepository.copyLegacyInto(it, context)
        it[intPreferencesKey(schemaVersionKey)] = currentSchemaVersion
    }

    override suspend fun cleanUp() {
        // Legacy files are intentionally retained for downgrade compatibility and auditability.
    }
}

private fun SharedPreferences.getStringSafely(key: String): String? = runCatching { getString(key, null) }.getOrNull()
private fun SharedPreferences.getStringSetSafely(key: String): Set<String>? = runCatching { getStringSet(key, null)?.toSet() }.getOrNull()
private fun SharedPreferences.getIntSafely(key: String): Int? = runCatching { if (contains(key)) getInt(key, 0) else null }.getOrNull()
private fun safeAllKeys(preferences: SharedPreferences): Set<String> = runCatching { preferences.all.keys }.getOrDefault(emptySet())
