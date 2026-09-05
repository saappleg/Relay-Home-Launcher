package com.relayhome.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Optional compatibility mode for TVs that refuse to retain a third-party HOME role.
 * The user must explicitly enable this service in Android Accessibility settings.
 */
class RelayAutoStartService : AccessibilityService() {
    private var lastLaunchAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in stockTvLaunchers) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunchAt < 2_000L) return
        lastLaunchAt = now
        val target = "${BuildConfig.APPLICATION_ID}/${MainActivity::class.java.name}"
        LauncherOverride.recordLocalEvent(
            this,
            LauncherDiagnosticEvent(
                timestampMs = System.currentTimeMillis(),
                operation = "accessibility_auto_start",
                strategy = LauncherOverrideStrategy.ACCESSIBILITY,
                phase = "activity",
                outcome = "started",
                cause = "Stock launcher window became active.",
                target = target,
                observedHome = packageName
            )
        )
        try {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
            )
            // startActivity() does not prove that Relay became the visible or resolved Home app.
            // Keep this explicitly unverified so diagnostics never overstate auto-start success.
            LauncherOverride.recordLocalEvent(
                this,
                LauncherDiagnosticEvent(
                    timestampMs = System.currentTimeMillis(),
                    operation = "accessibility_auto_start",
                    strategy = LauncherOverrideStrategy.ACCESSIBILITY,
                    phase = "activity",
                    outcome = "unverified",
                    cause = "Activity launch was accepted; visibility was not independently verified.",
                    target = target,
                    observedHome = packageName
                )
            )
        } catch (error: Throwable) {
            LauncherOverride.recordLocalEvent(
                this,
                LauncherDiagnosticEvent(
                    timestampMs = System.currentTimeMillis(),
                    operation = "accessibility_auto_start",
                    strategy = LauncherOverrideStrategy.ACCESSIBILITY,
                    phase = "activity",
                    outcome = "failure",
                    cause = error.message ?: error::class.java.simpleName,
                    target = target,
                    observedHome = packageName
                )
            )
            throw error
        }
    }

    override fun onInterrupt() = Unit

    private companion object {
        val stockTvLaunchers = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.android.tv.launcher",
            "com.amazon.tv.launcher"
        )
    }
}
