package com.relayhome.launcher

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class RelayDateFormat(val label: String) {
    LOCAL("Local long date"),
    US("MM/DD/YYYY"),
    ISO("YYYY-MM-DD")
}

internal object DateFormatSettings {
    private const val preferencesName = "relay_display_settings"
    private const val dateFormatKey = "date_format"

    fun load(context: Context): RelayDateFormat = runCatching {
        RelayDateFormat.valueOf(
            context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .getString(dateFormatKey, RelayDateFormat.LOCAL.name)!!
        )
    }.getOrDefault(RelayDateFormat.LOCAL)

    fun save(context: Context, value: RelayDateFormat) {
        context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit().putString(dateFormatKey, value.name).apply()
    }
}

internal fun formatRelayDate(raw: String?, style: RelayDateFormat): String? = raw?.take(10)?.let {
    runCatching { formatRelayDate(LocalDate.parse(it), style) }.getOrDefault(raw)
}

internal fun formatRelayDate(date: LocalDate, style: RelayDateFormat): String = when (style) {
    RelayDateFormat.LOCAL -> date.format(DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.getDefault()))
    RelayDateFormat.US -> date.format(DateTimeFormatter.ofPattern("MM/dd/uuuu", Locale.US))
    RelayDateFormat.ISO -> date.toString()
}
