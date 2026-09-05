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

    fun requestAccess(): String = runCatching {
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            "Relay's Shizuku permission was previously denied. Allow it in Shizuku, then try again."
        } else {
            Shizuku.requestPermission(permissionRequestCode)
            "Approve Relay in Shizuku. Relay will update automatically when access is granted."
        }
    }.getOrElse { "Start Shizuku first, then try again." }

    fun setRelayHome(
        stock: StockLauncherOverride?,
        disableStockLauncher: Boolean,
        onResult: (Result<String>) -> Unit
    ) = runUserService(onResult) { shell ->
        shell.setRelayHome(
            stock?.packageName,
            stock?.activityName,
            disableStockLauncher && stock != null
        )
    }

    fun restoreStockLauncher(
        stock: StockLauncherOverride,
        onResult: (Result<String>) -> Unit
    ) = runUserService(onResult) { shell ->
        shell.restoreStockLauncher(stock.packageName, stock.activityName)
    }

    private fun runUserService(
        onResult: (Result<String>) -> Unit,
        operation: (IRelayHomeShell) -> String
    ) {
        if (!isReady()) {
            onResult(Result.failure(IllegalStateException("Shizuku permission is not available.")))
            return
        }
        val args = Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, RelayShizukuService::class.java.name)
        )
            .processNameSuffix("relay-home-shell")
            .tag("relay-home-launcher-v2")
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
            onResult(result)
        }

        timeout = Runnable {
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
                val shell = IRelayHomeShell.Stub.asInterface(service)
                Thread {
                    val result = runCatching { operation(shell) }
                    mainHandler.post { finish(result) }
                }.start()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                finish(Result.failure(IllegalStateException("Shizuku disconnected before Relay could apply the launcher change.")))
            }
        }
        runCatching {
            Shizuku.bindUserService(args, connection)
            mainHandler.postDelayed(timeout, 8_000)
        }
            .onFailure { finish(Result.failure(it)) }
    }
}
