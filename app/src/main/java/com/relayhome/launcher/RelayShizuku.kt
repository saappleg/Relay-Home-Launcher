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

    // Shizuku answers permission requests outside Relay's Compose state. Keep a tiny observable
    // revision so Settings immediately changes from "Authorize" to "Enable" after approval.
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
        onResult: (Result<String>) -> Unit
    ) {
        if (!isReady()) {
            onResult(Result.failure(IllegalStateException("Shizuku permission is not available.")))
            return
        }
        val args = Shizuku.UserServiceArgs(
            ComponentName("com.relayhome.launcher", RelayShizukuService::class.java.name)
        )
            // Required by Shizuku's user-service API. Without it the service cannot start.
            .processNameSuffix("relay-home-shell")
            .tag("relay-home-launcher-override-v1")
        val finished = AtomicBoolean(false)
        val timeout = Runnable {
            if (finished.compareAndSet(false, true)) {
                onResult(Result.failure(IllegalStateException("Shizuku did not start Relay's service. Open Shizuku, confirm it is running, then allow Relay again.")))
            }
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mainHandler.removeCallbacks(timeout)
                val shell = IRelayHomeShell.Stub.asInterface(service)
                Thread {
                    val result = runCatching { shell.setLauncherEnabled(override.packageName, enabled) }
                    mainHandler.post {
                        if (finished.compareAndSet(false, true)) onResult(result)
                    }
                }.start()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                mainHandler.removeCallbacks(timeout)
                if (finished.compareAndSet(false, true)) {
                    onResult(Result.failure(IllegalStateException("Shizuku disconnected before Relay could apply the launcher override.")))
                }
            }
        }
        runCatching {
            Shizuku.bindUserService(args, connection)
            mainHandler.postDelayed(timeout, 8_000)
        }
            .onFailure { onResult(Result.failure(it)) }
    }
}
