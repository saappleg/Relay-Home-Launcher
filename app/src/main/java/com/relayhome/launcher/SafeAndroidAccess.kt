package com.relayhome.launcher

import android.content.Context
import android.content.SharedPreferences

/**
 * Small fail-closed wrappers for framework-backed preference access.
 *
 * Android settings and provider bridges can be unavailable during process start or teardown on
 * some TV builds. A preference read must then use its caller-supplied default, and a failed write
 * must not turn a diagnostics or cache path into a process crash.
 */
internal inline fun <T> readSharedPreferencesSafely(
    context: Context,
    name: String,
    defaultValue: T,
    read: (SharedPreferences) -> T
): T = readSharedPreferencesResult(context, name, read).getOrDefault(defaultValue)

/**
 * Same boundary as [readSharedPreferencesSafely], but retains the failure signal for callers
 * whose current in-memory value is more useful than replacing it with a default.
 */
internal inline fun <T> readSharedPreferencesResult(
    context: Context,
    name: String,
    read: (SharedPreferences) -> T
): Result<T> = runCatching {
    read(applicationContextSafely(context).getSharedPreferences(name, Context.MODE_PRIVATE))
}

internal inline fun writeSharedPreferencesSafely(
    context: Context,
    name: String,
    edit: (SharedPreferences.Editor) -> Unit
): Boolean = runCatching {
    val preferences = applicationContextSafely(context).getSharedPreferences(name, Context.MODE_PRIVATE)
    preferences.edit().also(edit).apply()
    true
}.getOrDefault(false)

internal fun applicationContextSafely(context: Context): Context =
    runCatching { context.applicationContext }.getOrNull() ?: context
