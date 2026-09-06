package com.relayhome.launcher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.relayhome.launcher.ui.shared.HomeRow
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.ui.shared.AppIconShape
import com.relayhome.launcher.ui.shared.AppSortOrder
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
private const val currentSchemaVersion = 7

private const val enabledProvidersKey = "providers.enabled_names"
private const val searchProviderKey = "search.default_provider"
private const val dateFormatKey = "display.date_format"
private const val profileImageUriKey = "profile.custom_image_uri"
private const val wallpaperImageUriKey = "home.wallpaper_image_uri"
private const val hiddenHomeRowsKey = "home.hidden_rows"
private const val minimalHomeEnabledKey = "home.minimal_enabled"
private const val weatherCityKey = "weather.city"
private const val showHomeClockKey = "weather.show_home_clock"
private const val temperatureUnitKey = "weather.temperature_unit"
private const val heroCapKey = "home.hero.cap"
private const val heroNuvioKey = "home.hero.include_nuvio"
private const val heroContinueWatchingKey = "home.hero.include_continue_watching"
private const val heroSubscriptionsKey = "home.hero.include_subscriptions"
private const val heroNowPlayingKey = "home.hero.include_now_playing"
private const val heroAutoRotateKey = "home.hero.auto_rotate"
private const val heroAutoRotateIntervalSecondsKey = "home.hero.auto_rotate_interval_seconds"
private const val hiddenAppPackagesKey = "apps.hidden_packages"
private const val appSortOrderKey = "apps.sort_order"
private const val appIconShapeKey = "apps.icon_shape"
private const val appLastUsedPrefix = "apps.last_used."
private const val appInstalledAtPrefix = "apps.installed_at."
private const val tmdbApiKey = "data_sources.tmdb_api_key"
private const val omdbApiKey = "data_sources.omdb_api_key"
private const val fanartApiKey = "data_sources.fanart_api_key"
private const val tvdbApiKey = "data_sources.tvdb_api_key"
private const val continueWatchingLimitPrefix = "continue_watching.provider_limit_"
private const val profileMappingPrefix = "profile_mapping."
private const val manualProfileMappingPrefix = "manual_nuvio_"
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

    fun loadWallpaperImageUri(context: Context): String? {
        initialize(context)
        return snapshot.get().values[stringPreferencesKey(wallpaperImageUriKey)]
    }

    fun saveWallpaperImageUri(context: Context, uri: String?) {
        updateSnapshotAndPersist(context) {
            val key = stringPreferencesKey(wallpaperImageUriKey)
            if (uri.isNullOrBlank()) it.remove(key) else it[key] = uri
        }
    }

    fun loadHiddenHomeRows(context: Context): Set<HomeRow> {
        initialize(context)
        val stored = snapshot.get().values[stringSetPreferencesKey(hiddenHomeRowsKey)]
            ?: return emptySet()
        return stored.mapNotNull { value -> HomeRow.entries.firstOrNull { it.name == value } }.toSet()
    }

    fun saveHiddenHomeRows(context: Context, hiddenRows: Set<HomeRow>) {
        // Persist the key even when the set is empty. An empty set is an intentional initialized
        // value, not a signal to fall back to a future default.
        updateSnapshotAndPersist(context) {
            it[stringSetPreferencesKey(hiddenHomeRowsKey)] = hiddenRows.map { row -> row.name }.toSet()
        }
    }

    fun loadMinimalHomeEnabled(context: Context): Boolean {
        initialize(context)
        return snapshot.get().values[booleanPreferencesKey(minimalHomeEnabledKey)] ?: false
    }

    fun saveMinimalHomeEnabled(context: Context, enabled: Boolean) {
        updateSnapshotAndPersist(context) {
            it[booleanPreferencesKey(minimalHomeEnabledKey)] = enabled
        }
    }

    fun loadWeatherCity(context: Context): String {
        initialize(context)
        return snapshot.get().values[stringPreferencesKey(weatherCityKey)].orEmpty()
    }

    fun saveWeatherCity(context: Context, city: String) {
        updateSnapshotAndPersist(context) {
            it[stringPreferencesKey(weatherCityKey)] = city
        }
    }

    fun loadShowHomeClock(context: Context): Boolean {
        initialize(context)
        return snapshot.get().values[booleanPreferencesKey(showHomeClockKey)] ?: false
    }

    fun saveShowHomeClock(context: Context, enabled: Boolean) {
        updateSnapshotAndPersist(context) {
            it[booleanPreferencesKey(showHomeClockKey)] = enabled
        }
    }

    fun loadTemperatureUnit(context: Context): String? {
        initialize(context)
        return snapshot.get().values[stringPreferencesKey(temperatureUnitKey)]
    }

    fun saveTemperatureUnit(context: Context, unit: String) {
        updateSnapshotAndPersist(context) {
            it[stringPreferencesKey(temperatureUnitKey)] = unit
        }
    }

    fun loadHeroItemCap(context: Context): Int {
        initialize(context)
        return (snapshot.get().values[intPreferencesKey(heroCapKey)] ?: 4).coerceIn(1, 8)
    }

    fun saveHeroItemCap(context: Context, cap: Int) {
        updateSnapshotAndPersist(context) { it[intPreferencesKey(heroCapKey)] = cap.coerceIn(1, 8) }
    }

    fun loadHeroSourceEnabled(context: Context, key: String, default: Boolean = true): Boolean {
        initialize(context)
        return snapshot.get().values[booleanPreferencesKey(key)] ?: default
    }

    fun saveHeroSourceEnabled(context: Context, key: String, enabled: Boolean) {
        updateSnapshotAndPersist(context) { it[booleanPreferencesKey(key)] = enabled }
    }

    fun loadHeroAutoRotate(context: Context): Boolean {
        initialize(context)
        return snapshot.get().values[booleanPreferencesKey(heroAutoRotateKey)] ?: true
    }

    fun saveHeroAutoRotate(context: Context, enabled: Boolean) {
        updateSnapshotAndPersist(context) { it[booleanPreferencesKey(heroAutoRotateKey)] = enabled }
    }

    fun loadHeroAutoRotateIntervalSeconds(context: Context): Int {
        initialize(context)
        return (snapshot.get().values[intPreferencesKey(heroAutoRotateIntervalSecondsKey)] ?: 11)
            .coerceIn(3, 30)
    }

    fun saveHeroAutoRotateIntervalSeconds(context: Context, seconds: Int) {
        updateSnapshotAndPersist(context) {
            it[intPreferencesKey(heroAutoRotateIntervalSecondsKey)] = seconds.coerceIn(3, 30)
        }
    }

    fun loadHiddenAppPackages(context: Context): Set<String> {
        initialize(context)
        return snapshot.get().values[stringSetPreferencesKey(hiddenAppPackagesKey)].orEmpty()
    }

    fun saveHiddenAppPackages(context: Context, packages: Set<String>) {
        updateSnapshotAndPersist(context) {
            it[stringSetPreferencesKey(hiddenAppPackagesKey)] = packages
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        }
    }

    fun loadAppSortOrder(context: Context): AppSortOrder {
        initialize(context)
        return AppSortOrder.fromStorage(snapshot.get().values[stringPreferencesKey(appSortOrderKey)])
    }

    fun saveAppSortOrder(context: Context, order: AppSortOrder) {
        updateSnapshotAndPersist(context) {
            it[stringPreferencesKey(appSortOrderKey)] = order.storageValue
        }
    }

    fun loadAppIconShape(context: Context): AppIconShape {
        initialize(context)
        return AppIconShape.fromStorage(snapshot.get().values[stringPreferencesKey(appIconShapeKey)])
    }

    fun saveAppIconShape(context: Context, shape: AppIconShape) {
        updateSnapshotAndPersist(context) {
            it[stringPreferencesKey(appIconShapeKey)] = shape.storageValue
        }
    }

    /** Small, non-sensitive launch metadata used only to order the local All Apps grid. */
    fun loadAppLastUsed(context: Context): Map<String, Long> {
        initialize(context)
        return loadLongMetadata(appLastUsedPrefix)
    }

    fun loadAppInstalledAt(context: Context): Map<String, Long> {
        initialize(context)
        return loadLongMetadata(appInstalledAtPrefix)
    }

    fun recordAppLaunched(context: Context, packageName: String, timestampMs: Long = System.currentTimeMillis()) {
        val normalized = packageName.trim()
        if (normalized.isEmpty()) return
        updateSnapshotAndPersist(context) {
            it[longPreferencesKey(appLastUsedPrefix + normalized)] = timestampMs.coerceAtLeast(0L)
        }
    }

    fun recordDiscoveredAppPackages(
        context: Context,
        packageNames: Set<String>,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        val normalized = packageNames.map(String::trim).filter(String::isNotEmpty).toSet()
        if (normalized.isEmpty()) return
        updateSnapshotAndPersist(context) {
            normalized.forEach { packageName ->
                val key = longPreferencesKey(appInstalledAtPrefix + packageName)
                if (it[key] == null) it[key] = timestampMs.coerceAtLeast(0L)
            }
        }
    }

    private fun loadLongMetadata(prefix: String): Map<String, Long> = snapshot.get().values.asMap()
        .keys
        .mapNotNull { key ->
            key.name.takeIf { it.startsWith(prefix) }
                ?.removePrefix(prefix)
                ?.takeIf { it.isNotBlank() }
                ?.let { packageName ->
                    snapshot.get().values[longPreferencesKey(key.name)]?.let { packageName to it }
                }
        }
        .toMap()

    /** Returns only a syntactically valid user key; malformed persisted values fail closed. */
    fun loadTmdbApiKey(context: Context): String? {
        initialize(context)
        return snapshot.get().values[stringPreferencesKey(tmdbApiKey)]
            ?.trim()
            ?.takeIf(RelayMetadataApiKeyRules::isValidTmdb)
    }

    /** Returns false without writing when the caller supplies an invalid key. */
    fun saveTmdbApiKey(context: Context, value: String): Boolean {
        if (!RelayMetadataApiKeyValidationHook.validate(MetadataKeyService.TMDB, value).isValid) return false
        val normalized = value.trim()
        updateSnapshotAndPersist(context) {
            it[stringPreferencesKey(tmdbApiKey)] = normalized
        }
        return true
    }

    fun clearTmdbApiKey(context: Context) {
        updateSnapshotAndPersist(context) { it.remove(stringPreferencesKey(tmdbApiKey)) }
    }

    /** Returns only a syntactically valid user key; malformed persisted values fail closed. */
    fun loadOmdbApiKey(context: Context): String? {
        initialize(context)
        return snapshot.get().values[stringPreferencesKey(omdbApiKey)]
            ?.trim()
            ?.takeIf(RelayMetadataApiKeyRules::isValidOmdb)
    }

    /** Returns false without writing when the caller supplies an invalid key. */
    fun saveOmdbApiKey(context: Context, value: String): Boolean {
        if (!RelayMetadataApiKeyValidationHook.validate(MetadataKeyService.OMDB, value).isValid) return false
        val normalized = value.trim()
        updateSnapshotAndPersist(context) {
            it[stringPreferencesKey(omdbApiKey)] = normalized
        }
        return true
    }

    fun clearOmdbApiKey(context: Context) {
        updateSnapshotAndPersist(context) { it.remove(stringPreferencesKey(omdbApiKey)) }
    }

    fun loadAdditionalMetadataApiKey(context: Context, service: MetadataKeyService): String? {
        initialize(context)
        val key = when (service) {
            MetadataKeyService.FANART -> fanartApiKey
            MetadataKeyService.TVDB -> tvdbApiKey
            else -> return null
        }
        return snapshot.get().values[stringPreferencesKey(key)]
    }

    fun saveAdditionalMetadataApiKey(context: Context, service: MetadataKeyService, value: String): Boolean {
        if (!RelayMetadataApiKeyValidationHook.validate(service, value).isValid) return false
        val key = when (service) {
            MetadataKeyService.FANART -> fanartApiKey
            MetadataKeyService.TVDB -> tvdbApiKey
            else -> return false
        }
        updateSnapshotAndPersist(context) { it[stringPreferencesKey(key)] = value.trim() }
        return true
    }

    fun clearAdditionalMetadataApiKey(context: Context, service: MetadataKeyService) {
        val key = when (service) {
            MetadataKeyService.FANART -> fanartApiKey
            MetadataKeyService.TVDB -> tvdbApiKey
            else -> return
        }
        updateSnapshotAndPersist(context) { it.remove(stringPreferencesKey(key)) }
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

    fun getManualProfileMapping(context: Context, nuvioProfile: Int): String? {
        initialize(context)
        return snapshot.get().values[stringPreferencesKey(profileMappingKey(manualProfileMappingPrefix, nuvioProfile))]
    }

    fun saveManualProfileMapping(context: Context, nuvioProfile: Int, value: String?) {
        updateSnapshotAndPersist(context) {
            val key = stringPreferencesKey(profileMappingKey(manualProfileMappingPrefix, nuvioProfile))
            if (value.isNullOrBlank()) it.remove(key) else it[key] = value
        }
    }

    fun saveProfileMappingCandidate(context: Context, nuvioProfile: Int, value: String?) {
        updateSnapshotAndPersist(context) {
            val key = stringPreferencesKey(profileMappingKey(candidateProfileMappingPrefix, nuvioProfile))
            if (value == null) it.remove(key) else it[key] = value
        }
    }

    fun clearProfileMapping(context: Context, nuvioProfile: Int) {
        updateSnapshotAndPersist(context) {
            it.remove(stringPreferencesKey(profileMappingKey(manualProfileMappingPrefix, nuvioProfile)))
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

    /** Reloads the in-memory facade from the existing DataStore without deleting user settings. */
    internal suspend fun reloadForTesting(context: Context) {
        awaitIdleForTesting(context)
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
        source.getStringSetSafely(hiddenHomeRowsKey)?.let { if (!destination.contains(stringSetPreferencesKey(hiddenHomeRowsKey))) destination[stringSetPreferencesKey(hiddenHomeRowsKey)] = it }
        source.getBooleanSafely(minimalHomeEnabledKey)?.let { if (!destination.contains(booleanPreferencesKey(minimalHomeEnabledKey))) destination[booleanPreferencesKey(minimalHomeEnabledKey)] = it }
        source.getStringSafely(weatherCityKey)?.let { if (!destination.contains(stringPreferencesKey(weatherCityKey))) destination[stringPreferencesKey(weatherCityKey)] = it }
        source.getBooleanSafely(showHomeClockKey)?.let { if (!destination.contains(booleanPreferencesKey(showHomeClockKey))) destination[booleanPreferencesKey(showHomeClockKey)] = it }
        source.getStringSetSafely(hiddenAppPackagesKey)?.let { if (!destination.contains(stringSetPreferencesKey(hiddenAppPackagesKey))) destination[stringSetPreferencesKey(hiddenAppPackagesKey)] = it }
        source.getStringSafely(appSortOrderKey)?.let { if (!destination.contains(stringPreferencesKey(appSortOrderKey))) destination[stringPreferencesKey(appSortOrderKey)] = it }
        source.getStringSafely(appIconShapeKey)?.let { if (!destination.contains(stringPreferencesKey(appIconShapeKey))) destination[stringPreferencesKey(appIconShapeKey)] = it }
        source.getStringSafely(tmdbApiKey)?.trim()?.takeIf(RelayMetadataApiKeyRules::isValidTmdb)?.let {
            if (!destination.contains(stringPreferencesKey(tmdbApiKey))) destination[stringPreferencesKey(tmdbApiKey)] = it
        }
        source.getStringSafely(omdbApiKey)?.trim()?.takeIf(RelayMetadataApiKeyRules::isValidOmdb)?.let {
            if (!destination.contains(stringPreferencesKey(omdbApiKey))) destination[stringPreferencesKey(omdbApiKey)] = it
        }
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
private fun SharedPreferences.getBooleanSafely(key: String): Boolean? = runCatching { if (contains(key)) getBoolean(key, false) else null }.getOrNull()
private fun safeAllKeys(preferences: SharedPreferences): Set<String> = runCatching { preferences.all.keys }.getOrDefault(emptySet())

/** The two metadata services whose keys can be configured by the user. */
internal enum class MetadataKeyService {
    TMDB,
    OMDB,
    FANART,
    TVDB
}

/** Result deliberately contains no candidate value, so accidental logging cannot print a key. */
internal class MetadataKeyValidationResult private constructor(
    val isValid: Boolean,
    val errorMessage: String?
) {
    override fun toString(): String = if (isValid) "valid" else "invalid"

    companion object {
        fun valid() = MetadataKeyValidationResult(true, null)
        fun invalid(message: String) = MetadataKeyValidationResult(false, message)
    }
}

internal fun interface MetadataKeyValidationHook {
    fun validate(service: MetadataKeyService, rawValue: String): MetadataKeyValidationResult
}

/** Conservative formats accepted by the public TMDB v3 and OMDb API-key forms. */
internal object RelayMetadataApiKeyRules {
    private val tmdbPattern = Regex("^[A-Za-z0-9]{32}$")
    private val omdbPattern = Regex("^[A-Za-z0-9]{8}$")
    private val additionalPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{7,127}$")

    fun isValidTmdb(value: String): Boolean = tmdbPattern.matches(value.trim())
    fun isValidOmdb(value: String): Boolean = omdbPattern.matches(value.trim())
    fun isValidAdditional(value: String): Boolean = additionalPattern.matches(value.trim())
}

/**
 * Offline, bounded validation used before a key can reach DataStore.
 *
 * Provider validity cannot be proven without sending the secret to that provider. Relay therefore
 * intentionally does not perform a remote probe while saving: it would make saving dependent on
 * network availability, expose the key during an unrelated settings action, and require an OMDb
 * network client that is outside this scoped change. Exact provider formats plus the input bound
 * are the strongest safe no-network validation available here. The repository repeats this hook
 * even when the UI has already run it, so every caller fails closed before persistence.
 */
internal object RelayMetadataApiKeyValidationHook : MetadataKeyValidationHook {
    private const val MAX_CANDIDATE_LENGTH = 128

    override fun validate(service: MetadataKeyService, rawValue: String): MetadataKeyValidationResult {
        if (rawValue.length > MAX_CANDIDATE_LENGTH) {
            return MetadataKeyValidationResult.invalid("That key is too long.")
        }

        val normalized = rawValue.trim()
        if (normalized.isEmpty()) {
            return MetadataKeyValidationResult.invalid("Enter a key before saving.")
        }

        return when (service) {
            MetadataKeyService.TMDB -> if (RelayMetadataApiKeyRules.isValidTmdb(normalized)) {
                MetadataKeyValidationResult.valid()
            } else {
                MetadataKeyValidationResult.invalid("Enter the 32-character TMDB API key from your account.")
            }
            MetadataKeyService.OMDB -> if (RelayMetadataApiKeyRules.isValidOmdb(normalized)) {
                MetadataKeyValidationResult.valid()
            } else {
                MetadataKeyValidationResult.invalid("Enter the 8-character OMDb API key from your account.")
            }
            MetadataKeyService.FANART, MetadataKeyService.TVDB -> if (RelayMetadataApiKeyRules.isValidAdditional(normalized)) {
                MetadataKeyValidationResult.valid()
            } else {
                MetadataKeyValidationResult.invalid("Enter a valid ${if (service == MetadataKeyService.FANART) "Fanart.tv" else "TheTVDB"} API key.")
            }
        }
    }
}
