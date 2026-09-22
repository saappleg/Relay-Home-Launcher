package com.relayhome.launcher

import android.os.StrictMode
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug-only process safety nets. These policies deliberately use penaltyLog() without
 * penaltyDeath(): a violation should provide actionable evidence while leaving the app usable.
 *
 * Keep this installation in [RelayHomeApplication] so crash diagnostics and debug diagnostics
 * share the same process startup path. Release builds do not install either policy.
 */
internal object RelayDebugSafetyNets {
    private const val tag = "RelayDebugSafetyNets"
    private val installed = AtomicBoolean(false)

    fun install() {
        if (!BuildConfig.DEBUG || !installed.compareAndSet(false, true)) return

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectUnbufferedIo()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectActivityLeaks()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build()
        )
        Log.i(tag, "Debug StrictMode thread and VM policies installed")
    }
}
