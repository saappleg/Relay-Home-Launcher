package com.relayhome.launcher

import android.content.Context
import android.os.Build
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-level crash evidence. This is deliberately synchronous and bounded because it runs
 * from an uncaught-exception thread while the process is shutting down. It never stores tokens,
 * URLs, or user media; only the exception and device/build context needed to diagnose a crash.
 */
internal object RelayCrashDiagnostics {
    private const val tag = "RelayCrashDiagnostics"
    private const val preferencesName = "relay_crash_diagnostics"
    private const val timestampKey = "timestamp_ms"
    private const val threadKey = "thread"
    private const val exceptionKey = "exception"
    private const val messageKey = "message"
    private const val stackTraceKey = "stack_trace"
    private const val deviceKey = "device"
    private const val osKey = "os"
    private const val maxTextLength = 16_000
    private val installed = AtomicBoolean(false)

    internal data class Record(
        val timestampMs: Long,
        val thread: String,
        val exception: String,
        val message: String,
        val stackTrace: String,
        val device: String,
        val os: String
    ) {
        fun formatForCopy(): String = buildString {
            appendLine("Relay Home crash diagnostics")
            appendLine("Time: $timestampMs")
            appendLine("Thread: $thread")
            appendLine("Exception: $exception")
            appendLine("Message: $message")
            appendLine("Device: $device")
            appendLine("OS: $os")
            appendLine()
            append(stackTrace)
        }
    }

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val applicationContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { record(applicationContext, thread, throwable) }
                .onFailure { Log.e(tag, "Could not persist uncaught exception diagnostics", it) }
            runCatching { previous?.uncaughtException(thread, throwable) }
                .onFailure { Log.e(tag, "Default uncaught-exception handler failed", it) }
        }
    }

    fun record(context: Context, thread: Thread, throwable: Throwable) {
        val now = System.currentTimeMillis()
        val stack = throwable.stackTraceToString().take(maxTextLength)
        runCatching {
            context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .edit()
                .putLong(timestampKey, now)
                .putString(threadKey, thread.name.take(160))
                .putString(exceptionKey, throwable::class.java.name.take(320))
                .putString(messageKey, throwable.message.orEmpty().take(2_000))
                .putString(stackTraceKey, stack)
                .putString(deviceKey, "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(320))
                .putString(osKey, "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                .commit()
        }.onFailure { Log.e(tag, "Could not write crash diagnostics", it) }
        Log.e(
            tag,
            "Uncaught exception on ${thread.name}; device=${Build.MANUFACTURER} ${Build.MODEL}, " +
                "os=Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}",
            throwable
        )
    }

    fun load(context: Context): Record? = runCatching {
        val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        if (!preferences.contains(timestampKey)) return@runCatching null
        Record(
            timestampMs = preferences.getLong(timestampKey, 0L),
            thread = preferences.getString(threadKey, "unknown") ?: "unknown",
            exception = preferences.getString(exceptionKey, "unknown") ?: "unknown",
            message = preferences.getString(messageKey, "") ?: "",
            stackTrace = preferences.getString(stackTraceKey, "") ?: "",
            device = preferences.getString(deviceKey, "unknown") ?: "unknown",
            os = preferences.getString(osKey, "unknown") ?: "unknown"
        )
    }.getOrNull()
}

class RelayHomeApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        RelayCrashDiagnostics.install(this)
    }
}
