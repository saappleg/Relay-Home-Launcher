package com.relayhome.launcher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.relayhome.launcher.applicationContextSafely
import com.relayhome.launcher.ui.shared.MediaItem
import com.relayhome.launcher.ui.shared.contentKey
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

/** A local-only opinion about a title. No provider payload contains this value. */
internal enum class PersonalRating(val label: String, val storageValue: String, val badge: String) {
    LIKE("Like", "like", "Like"),
    LOVE("Love", "love", "Love"),
    NEUTRAL("Neutral", "neutral", "Neutral");

    companion object {
        fun fromStorage(value: String?): PersonalRating? =
            entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() }
    }
}

/**
 * Small, device-local ratings store keyed by Relay's existing [MediaItem.contentKey].
 *
 * Ratings deliberately live in their own DataStore. They are presentation preferences, not
 * provider metadata, and are never passed to Nuvio, TMDB, SmartTube, or any other handoff.
 */
internal object RelayRatingsStore {
    private const val schemaVersionKey = "_schema_version"
    private const val currentSchemaVersion = 1
    private const val ratingPrefix = "rating."
    private const val legacyPreferencesName = "relay_ratings"

    private val lock = Any()
    private val writeMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val snapshot = AtomicReference<Map<String, PersonalRating>>(emptyMap())
    private val _revision = MutableStateFlow(0L)
    private var initializedContext: Context? = null
    private var collectorJob: Job? = null
    private var migrationJob: Job? = null
    private var pendingWrites = 0
    private var lastPersistedSnapshot: Map<String, PersonalRating> = emptyMap()

    fun revision(context: Context): StateFlow<Long> {
        initialize(context)
        return _revision.asStateFlow()
    }

    fun load(context: Context, item: MediaItem): PersonalRating? = load(context, item.contentKey())

    fun load(context: Context, contentKey: String): PersonalRating? {
        initialize(context)
        return snapshot.get()[contentKey]
    }

    fun loadAll(context: Context): Map<String, PersonalRating> {
        initialize(context)
        return snapshot.get()
    }

    fun save(context: Context, item: MediaItem, rating: PersonalRating) =
        save(context, item.contentKey(), rating)

    fun save(context: Context, contentKey: String, rating: PersonalRating) {
        val normalizedKey = contentKey.trim()
        if (normalizedKey.isEmpty()) return
        initialize(context)
        synchronized(lock) {
            val optimistic = snapshot.get().toMutableMap().also { it[normalizedKey] = rating }.toMap()
            pendingWrites += 1
            snapshot.set(optimistic)
            _revision.value += 1
        }
        scope.launch {
            try {
                val persisted = writeMutex.withLock {
                    context.applicationContext.relayRatingsDataStore.updateData { current ->
                        current.toMutablePreferences().also {
                            it[ratingPreferenceKey(normalizedKey)] = rating.storageValue
                        }
                    }
                }
                synchronized(lock) {
                    val persistedSnapshot = ratingsFrom(persisted)
                    snapshot.set(persistedSnapshot)
                    lastPersistedSnapshot = persistedSnapshot
                    pendingWrites -= 1
                    _revision.value += 1
                }
            } catch (_: Throwable) {
                synchronized(lock) {
                    pendingWrites -= 1
                    if (pendingWrites == 0) snapshot.set(lastPersistedSnapshot)
                    _revision.value += 1
                }
            }
        }
    }

    fun clear(context: Context, item: MediaItem) = clear(context, item.contentKey())

    fun clear(context: Context, contentKey: String) {
        val normalizedKey = contentKey.trim()
        if (normalizedKey.isEmpty()) return
        initialize(context)
        synchronized(lock) {
            val optimistic = snapshot.get().toMutableMap().also { it.remove(normalizedKey) }.toMap()
            pendingWrites += 1
            snapshot.set(optimistic)
            _revision.value += 1
        }
        scope.launch {
            try {
                val persisted = writeMutex.withLock {
                    context.applicationContext.relayRatingsDataStore.updateData { current ->
                        current.toMutablePreferences().also { it.remove(ratingPreferenceKey(normalizedKey)) }
                    }
                }
                synchronized(lock) {
                    val persistedSnapshot = ratingsFrom(persisted)
                    snapshot.set(persistedSnapshot)
                    lastPersistedSnapshot = persistedSnapshot
                    pendingWrites -= 1
                    _revision.value += 1
                }
            } catch (_: Throwable) {
                synchronized(lock) {
                    pendingWrites -= 1
                    if (pendingWrites == 0) snapshot.set(lastPersistedSnapshot)
                    _revision.value += 1
                }
            }
        }
    }

    /** Suspends until migration and all queued writes have settled. */
    suspend fun awaitReady(context: Context) {
        initialize(context)
        migrationJob?.join()
        applicationContextSafely(context).relayRatingsDataStore.data.first()
        awaitIdleForTesting(context)
    }

    private fun initialize(context: Context) {
        val appContext = applicationContextSafely(context)
        synchronized(lock) {
            if (initializedContext === appContext && collectorJob?.isActive == true) return
            initializedContext = appContext
            snapshot.set(emptyMap())
            lastPersistedSnapshot = emptyMap()
            collectorJob?.cancel()
            migrationJob?.cancel()
            collectorJob = scope.launch {
                appContext.relayRatingsDataStore.data
                    .catch {
                        // Keep the last good snapshot when DataStore is temporarily unreadable;
                        // an empty emission would make existing ratings disappear in the UI.
                    }
                    .collect { values ->
                        synchronized(lock) {
                            if (pendingWrites == 0) {
                                val persisted = ratingsFrom(values)
                                snapshot.set(persisted)
                                lastPersistedSnapshot = persisted
                                _revision.value += 1
                            }
                        }
                    }
            }
            migrationJob = scope.launch {
                writeMutex.withLock {
                    appContext.relayRatingsDataStore.updateData { current ->
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

    private fun ratingsFrom(values: Preferences): Map<String, PersonalRating> = values.asMap()
        .mapNotNull { (key, value) ->
            if (!key.name.startsWith(ratingPrefix)) return@mapNotNull null
            val contentKey = key.name.removePrefix(ratingPrefix)
            val rating = (value as? String)?.let(PersonalRating::fromStorage)
            if (contentKey.isBlank() || rating == null) null else contentKey to rating
        }
        .toMap()

    private fun ratingPreferenceKey(contentKey: String) =
        stringPreferencesKey(ratingPrefix + contentKey)

    /** Copies the legacy one-value-per-content-key SharedPreferences format exactly once. */
    private fun copyLegacyInto(destination: MutablePreferences, context: Context) {
        // A stale/corrupt legacy XML file should not block the ratings DataStore from opening.
        val legacy = runCatching {
            context.applicationContext.getSharedPreferences(legacyPreferencesName, Context.MODE_PRIVATE)
        }.getOrNull() ?: return
        safeAllKeys(legacy).forEach { legacyKey ->
            val rating = runCatching { legacy.getString(legacyKey, null) }
                .getOrNull()
                ?.let(PersonalRating::fromStorage)
                ?: return@forEach
            val destinationKey = stringPreferencesKey(ratingPrefix + legacyKey)
            if (!destination.contains(destinationKey)) destination[destinationKey] = rating.storageValue
        }
    }

    // Test-only hooks mirror RelaySettingsRepository's migration tests without exposing the
    // underlying DataStore to UI callers.
    internal suspend fun resetForTesting(context: Context) {
        val appContext = applicationContextSafely(context)
        awaitIdleForTestingIfInitialized()
        synchronized(lock) {
            collectorJob?.cancel()
            migrationJob?.cancel()
            collectorJob = null
            migrationJob = null
            initializedContext = null
            pendingWrites = 0
            snapshot.set(emptyMap())
            lastPersistedSnapshot = emptyMap()
            _revision.value += 1
        }
        appContext.relayRatingsDataStore.updateData { emptyPreferences() }
    }

    /** Drops only the in-memory observer so tests can verify a fresh-process reload. */
    internal suspend fun reloadForTesting() {
        awaitIdleForTestingIfInitialized()
        synchronized(lock) {
            collectorJob?.cancel()
            migrationJob?.cancel()
            collectorJob = null
            migrationJob = null
            initializedContext = null
            pendingWrites = 0
            snapshot.set(emptyMap())
            lastPersistedSnapshot = emptyMap()
            _revision.value += 1
        }
    }

    internal suspend fun awaitIdleForTesting(context: Context) {
        initialize(context)
        migrationJob?.join()
        awaitIdleForTestingIfInitialized()
        val values = applicationContextSafely(context).relayRatingsDataStore.data.first()
        synchronized(lock) {
            if (pendingWrites == 0) {
                val persisted = ratingsFrom(values)
                snapshot.set(persisted)
                lastPersistedSnapshot = persisted
            }
            _revision.value += 1
        }
    }

    internal suspend fun storedKeysForTesting(context: Context): Set<String> {
        awaitReady(context)
        return applicationContextSafely(context).relayRatingsDataStore.data.first().asMap().keys.map { it.name }.toSet()
    }

    private suspend fun awaitIdleForTestingIfInitialized() {
        while (true) {
            val idle = synchronized(lock) { pendingWrites == 0 }
            if (idle) return
            delay(10)
        }
    }
}

private val Context.relayRatingsDataStore by preferencesDataStore(name = "relay_ratings_data")

private fun safeAllKeys(preferences: SharedPreferences): Set<String> =
    runCatching { preferences.all.keys }.getOrDefault(emptySet())
