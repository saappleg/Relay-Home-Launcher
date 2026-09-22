package com.relayhome.launcher

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

/** User-authorized shell bridge. Relay exposes only launcher enable/disable, never a shell. */
internal object RelayShizuku {
    private const val permissionRequestCode = 7412
    private val mainHandler = Handler(Looper.getMainLooper())
    private val launcherOperationInFlight = AtomicBoolean(false)

    private var readinessRevision by mutableIntStateOf(0)
    val readinessRevisionForUi: Int get() = readinessRevision

    init {
        val refreshReadiness = {
            mainHandler.post { readinessRevision++ }
        }
        runCatching {
            Shizuku.addBinderReceivedListenerSticky { refreshReadiness() }
            Shizuku.addBinderDeadListener { refreshReadiness() }
            Shizuku.addRequestPermissionResultListener { _, _ -> refreshReadiness() }
        }
    }

    fun isReady(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestAccess(): String = runCatching {
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            "Relay's Shizuku permission was previously denied. Allow it in Shizuku, then try again."
        } else {
            Shizuku.requestPermission(permissionRequestCode)
            "Approve Relay in Shizuku. Relay will update automatically when access is granted."
        }
    }.getOrElse { "Start Shizuku first, then try again." }

    fun setStockLauncherEnabled(
        override: StockLauncherOverride,
        enabled: Boolean,
        onStillRunning: (() -> Unit)? = null,
        onResult: (Result<String>) -> Unit
    ) {
        if (!isReady()) {
            deliverOnMain(onResult, Result.failure(IllegalStateException("Shizuku permission is not available.")))
            return
        }
        if (!launcherOperationInFlight.compareAndSet(false, true)) {
            deliverOnMain(
                onResult,
                Result.failure(IllegalStateException("A launcher update is still running. Wait for it to finish before trying again."))
            )
            return
        }
        val args = Shizuku.UserServiceArgs(
            ComponentName("com.relayhome.launcher", RelayShizukuService::class.java.name)
        )
            .processNameSuffix("relay-home-shell")
            .tag("relay-home-launcher-override-v1")
        val callbackDelivered = AtomicBoolean(false)
        val operationFinished = AtomicBoolean(false)
        val unbound = AtomicBoolean(false)
        val operationStarted = AtomicBoolean(false)
        lateinit var connection: ServiceConnection
        lateinit var timeout: Runnable
        lateinit var operationTimeout: Runnable

        fun unbindOnce() {
            if (unbound.compareAndSet(false, true)) {
                // bindUserService may partially bind before throwing, so attempt cleanup on every
                // terminal path, including bind failures and startup timeouts.
                runCatching { Shizuku.unbindUserService(args, connection, true) }
            }
        }

        fun finish(result: Result<String>) {
            if (!operationFinished.compareAndSet(false, true)) return
            runOnMain {
                mainHandler.removeCallbacks(timeout)
                mainHandler.removeCallbacks(operationTimeout)
                unbindOnce()
                launcherOperationInFlight.set(false)
                if (callbackDelivered.compareAndSet(false, true)) onResult(result)
            }
        }

        timeout = Runnable {
            finish(
                Result.failure(
                    IllegalStateException(
                        "Shizuku did not start Relay's service. Open Shizuku, confirm it is running, then allow Relay again."
                    )
                )
            )
        }
        operationTimeout = Runnable {
            runOnMain {
                if (!operationFinished.get()) {
                    // The synchronous remote call may still be changing Android's Home
                    // components. Report the delay separately and keep its service bound until it returns.
                    onStillRunning?.invoke()
                }
            }
        }

        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                runOnMain {
                    // A connection can arrive after the startup timeout. Never let that late
                    // callback perform the requested launcher operation.
                    if (operationFinished.get()) {
                        unbindOnce()
                        return@runOnMain
                    }
                    mainHandler.removeCallbacks(timeout)
                    if (!operationStarted.compareAndSet(false, true)) return@runOnMain
                    mainHandler.postDelayed(operationTimeout, 12_000)

                    runCatching {
                        val shell = IRelayHomeShell.Stub.asInterface(service)
                        Thread {
                            finish(
                                runCatching {
                                    shell.setLauncherEnabled(override.packageName, override.activityName, enabled)
                                }
                            )
                        }.start()
                    }.onFailure { finish(Result.failure(it)) }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                finish(
                    Result.failure(
                        IllegalStateException("Shizuku disconnected before Relay could apply the launcher override.")
                    )
                )
            }

            override fun onBindingDied(name: ComponentName) {
                finish(
                    Result.failure(
                        IllegalStateException("Shizuku ended Relay's service binding before applying the launcher override.")
                    )
                )
            }

            override fun onNullBinding(name: ComponentName) {
                finish(
                    Result.failure(
                        IllegalStateException("Shizuku returned no service for the launcher override.")
                    )
                )
            }
        }

        runOnMain {
            if (operationFinished.get()) return@runOnMain
            // Schedule before binding. Both actions run on the main thread so a very fast
            // connection callback cannot remove a timeout that has not been posted yet.
            mainHandler.postDelayed(timeout, 8_000)
            runCatching { Shizuku.bindUserService(args, connection) }
                .onFailure { finish(Result.failure(it)) }
        }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    private fun deliverOnMain(callback: (Result<String>) -> Unit, result: Result<String>) {
        runOnMain { callback(result) }
    }
}
