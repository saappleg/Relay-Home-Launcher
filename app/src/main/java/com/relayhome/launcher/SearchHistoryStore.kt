package com.relayhome.launcher

import android.content.Context

internal object SearchHistoryStore {
    private const val preferencesName = "relay_search_history"
    private const val keyPrefix = "recent_"
    private const val maximumEntries = 8

    fun load(context: Context, profileScope: String): List<String> =
        preferences(context).getString(key(profileScope), null)
            ?.split('\n')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.distinctBy { it.lowercase() }
            ?.take(maximumEntries)
            .orEmpty()

    fun add(context: Context, profileScope: String, query: String): List<String> {
        val cleanQuery = query.trim().replace(Regex("\\s+"), " ").take(120)
        if (cleanQuery.isBlank()) return load(context, profileScope)
        val next = (listOf(cleanQuery) + load(context, profileScope))
            .distinctBy { it.lowercase() }
            .take(maximumEntries)
        preferences(context).edit().putString(key(profileScope), next.joinToString("\n")).apply()
        return next
    }

    fun clear(context: Context, profileScope: String) {
        preferences(context).edit().remove(key(profileScope)).apply()
    }

    private fun key(profileScope: String) = "$keyPrefix${profileScope.replace(Regex("[^A-Za-z0-9_.-]"), "_")}"
    private fun preferences(context: Context) = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
}
