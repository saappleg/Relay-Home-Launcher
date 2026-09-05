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
        onResult: (Result<String>) -> Unit
    ) = runUserService(context, "set_relay_home", onResult) { shell ->
        shell.setRelayHome(
            stock?.packageName,
            stock?.activityName,
            disableStockLauncher && stock != null
        )
    }

    fun restoreStockLauncher(
        context: Context,
        stock: StockLauncherOverride,
        onResult: (Result<String>) -> Unit
    ) = runUserService(context, "restore_stock_launcher", onResult) { shell ->
        shell.restoreStockLauncher(stock.packageName, stock.activityName)
    }

    private fun runUserService(
        context: Context,
        operationName: String,
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
            onResult(Result.failure(IllegalStateException("Shizuku permission is not available.")))
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
        val finished = AtomicBoolean(false)
        lateinit var connection: ServiceConnection
        lateinit var timeout: Runnable

        fun finish(result: Result<String>) {
            if (!finished.compareAndSet(false, true)) return
            mainHandler.removeCallbacks(timeout)
            // The service is one-shot. Detach this callback immediately; daemon(false) also
            // prevents it from surviving the Relay process after an unexpected app exit.
            runCatching { Shizuku.unbindUserService(args, connection, false) }
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
            onResult(surfacedResult)
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
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mainHandler.removeCallbacks(timeout)
                if (finished.get()) return
                context.recordShizukuEvent(
                    operation = operationName,
                    phase = "service",
                    outcome = "success",
                    cause = "Shizuku user service connected."
                )
                val shell = IRelayHomeShell.Stub.asInterface(service)
                Thread {
                    val result = runCatching { operation(shell) }
                    mainHandler.post { finish(result) }
                }.start()
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
        }
        runCatching {
            Shizuku.bindUserService(args, connection)
            mainHandler.postDelayed(timeout, 8_000)
        }
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

    private fun Context.recordShizukuEvent(
        operation: String = "shizuku",
        phase: String,
        outcome: String,
        cause: String? = null
    ) {
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

    private fun failureMessage(error: Throwable): String = generateSequence(error) { it.cause }
        .mapNotNull { it.message?.takeIf(String::isNotBlank) }
        .joinToString("; ")
        .ifBlank { error::class.java.simpleName }
}
