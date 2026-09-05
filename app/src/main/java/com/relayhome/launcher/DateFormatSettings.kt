package com.relayhome.launcher

import android.content.Context
import com.relayhome.launcher.data.RelaySettingsRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class RelayDateFormat(val label: String) {
    LOCAL("Local long date"),
    US("MM/DD/YYYY"),
    ISO("YYYY-MM-DD")
}

internal object DateFormatSettings {
    fun load(context: Context): RelayDateFormat = runCatching {
        RelayDateFormat.valueOf(
            RelaySettingsRepository.loadDateFormat(context, RelayDateFormat.LOCAL.name)
        )
    }.getOrDefault(RelayDateFormat.LOCAL)

    fun save(context: Context, value: RelayDateFormat) {
        RelaySettingsRepository.saveDateFormat(context, value.name)
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
