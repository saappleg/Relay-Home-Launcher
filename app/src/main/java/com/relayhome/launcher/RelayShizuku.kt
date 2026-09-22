package com.relayhome.launcher

import android.content.Context
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

/** User-authorized, narrowly scoped bridge for the launcher role override. */
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

    fun requestAccess(context: Context? = null): String = runCatching {
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            context?.recordShizukuEvent(
                phase = "permission",
                outcome = "failure",
                cause = "Permission was previously denied."
            )
            "Relay's Shizuku permission was previously denied. Allow it in Shizuku, then try again."
        } else {
            Shizuku.requestPermission(permissionRequestCode)
            context?.recordShizukuEvent(
                phase = "permission",
                outcome = "unverified",
                cause = "Permission request sent; approval is verified by the next readiness check."
            )
            "Approve Relay in Shizuku. Relay will update automatically when access is granted."
        }
    }.getOrElse { error ->
        context?.recordShizukuEvent(
            phase = "permission",
            outcome = "failure",
            cause = failureMessage(error)
        )
        "Start Shizuku first, then try again."
    }

    fun setRelayHome(
        context: Context,
        stock: StockLauncherOverride?,
        disableStockLauncher: Boolean,
        onStillRunning: (() -> Unit)? = null,
        onResult: (Result<String>) -> Unit
    ) = runUserService(context, "set_relay_home", onStillRunning, onResult) { shell ->
        shell.setRelayHome(
            stock?.packageName,
            stock?.activityName,
            disableStockLauncher && stock != null
        )
    }

    fun restoreStockLauncher(
        context: Context,
        stock: StockLauncherOverride,
        onStillRunning: (() -> Unit)? = null,
        onResult: (Result<String>) -> Unit
    ) = runUserService(context, "restore_stock_launcher", onStillRunning, onResult) { shell ->
        shell.restoreStockLauncher(stock.packageName, stock.activityName)
    }

    private fun runUserService(
        context: Context,
        operationName: String,
        onStillRunning: (() -> Unit)?,
        onResult: (Result<String>) -> Unit,
        operation: (IRelayHomeShell) -> String
    ) {
        if (!isReady()) {
            context.recordShizukuEvent(
                operation = operationName,
                phase = "service",
                outcome = "failure",
                cause = "Shizuku permission or binder is not available."
            )
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
            ComponentName(BuildConfig.APPLICATION_ID, RelayShizukuService::class.java.name)
        )
            .processNameSuffix("relay-home-shell")
            .tag("relay-home-launcher-v3")
            // Shizuku reuses a user service when its tag and version match. Tie the version to
            // the APK so launcher-service changes cannot leave an older implementation running.
            .version(BuildConfig.VERSION_CODE)
            .daemon(false)
        val operationFinished = AtomicBoolean(false)
        val callbackDelivered = AtomicBoolean(false)
        val unbound = AtomicBoolean(false)
        val operationStarted = AtomicBoolean(false)
        lateinit var connection: ServiceConnection
        lateinit var timeout: Runnable
        lateinit var operationTimeout: Runnable

        fun unbindOnce() {
            if (unbound.compareAndSet(false, true)) {
                // bindUserService can partially bind before throwing, so clean up on every
                // terminal path, including startup timeout and bind failure.
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
                val surfacedResult = result.fold(
                    onSuccess = { raw ->
                        val message = LauncherOverride.recordServiceResult(context, raw)
                        if (message != null) Result.success(message) else Result.success(raw)
                    },
                    onFailure = { error ->
                        val message = LauncherOverride.recordServiceFailure(context, error.message)
                        if (message != null) Result.failure(IllegalStateException(message, error))
                        else Result.failure(error)
                    }
                )
                // A late binder callback must not crash the UI if Settings has been disposed.
                if (callbackDelivered.compareAndSet(false, true)) runCatching { onResult(surfacedResult) }
            }
        }

        timeout = Runnable {
            context.recordShizukuEvent(
                operation = operationName,
                phase = "service",
                outcome = "failure",
                cause = "Timed out waiting for the Shizuku user service."
            )
            finish(
                Result.failure(
                    IllegalStateException(
                        "Shizuku did not start Relay's service. Open Shizuku, confirm it is " +
                            "running, then allow Relay again."
                    )
                )
            )
        }
        operationTimeout = Runnable {
            if (!operationFinished.get()) runCatching { onStillRunning?.invoke() }
        }
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                runOnMain {
                    // A binder can arrive after timeout. Do not let that stale callback apply
                    // the launcher change once the operation has been reported as failed.
                    if (operationFinished.get()) {
                        unbindOnce()
                        return@runOnMain
                    }
                    mainHandler.removeCallbacks(timeout)
                    if (!operationStarted.compareAndSet(false, true)) return@runOnMain
                    mainHandler.postDelayed(operationTimeout, 12_000)
                    context.recordShizukuEvent(
                        operation = operationName,
                        phase = "service",
                        outcome = "success",
                        cause = "Shizuku user service connected."
                    )
                    runCatching {
                        val shell = IRelayHomeShell.Stub.asInterface(service)
                        Thread({
                            finish(runCatching { operation(shell) })
                        }, "relay-shizuku-$operationName").start()
                    }.onFailure { finish(Result.failure(it)) }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                context.recordShizukuEvent(
                    operation = operationName,
                    phase = "service",
                    outcome = "failure",
                    cause = "Shizuku disconnected before the operation completed."
                )
                finish(Result.failure(IllegalStateException("Shizuku disconnected before Relay could apply the launcher change.")))
            }

            override fun onBindingDied(name: ComponentName) {
                finish(Result.failure(IllegalStateException("Shizuku ended Relay's service binding before the operation completed.")))
            }

            override fun onNullBinding(name: ComponentName) {
                finish(Result.failure(IllegalStateException("Shizuku returned no service for the launcher override.")))
            }
        }

        runOnMain {
            if (operationFinished.get()) return@runOnMain
            // Schedule before binding so even a synchronous connection cannot beat timeout setup.
            mainHandler.postDelayed(timeout, 8_000)
            runCatching { Shizuku.bindUserService(args, connection) }
                .onFailure {
                    context.recordShizukuEvent(
                        operation = operationName,
                        phase = "service",
                        outcome = "failure",
                        cause = failureMessage(it)
                    )
                    finish(Result.failure(it))
                }
        }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    private fun deliverOnMain(callback: (Result<String>) -> Unit, result: Result<String>) {
        runOnMain { runCatching { callback(result) } }
    }

    private fun Context.recordShizukuEvent(
        operation: String = "shizuku",
        phase: String,
        outcome: String,
        cause: String? = null
    ) {
        runCatching {
            LauncherOverride.recordLocalEvent(
                this,
                LauncherDiagnosticEvent(
                    timestampMs = System.currentTimeMillis(),
                    operation = operation,
                    strategy = LauncherOverrideStrategy.SHIZUKU,
                    phase = phase,
                    outcome = outcome,
                    cause = cause
                )
            )
        }
    }

    private fun failureMessage(error: Throwable): String = generateSequence(error) { it.cause }
        .mapNotNull { it.message?.takeIf(String::isNotBlank) }
        .joinToString("; ")
        .ifBlank { error::class.java.simpleName }
}
