package com.relayhome.launcher

import com.relayhome.launcher.ui.RelayHomeApp
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.relayhome.launcher.ui.state.RelayHomeStateHolder
import com.relayhome.launcher.ui.state.RelayHomeSystemActions

class MainActivity : ComponentActivity(), RelayHomeSystemActions {
    private lateinit var relayHomeStateHolder: RelayHomeStateHolder

    /** Compatibility read for rememberInstalledApps until that composable can accept a flow. */
    val launcherStateRevision: Int
        get() = relayHomeStateHolder.launcherStateRevisionForLegacyCompose

    private val homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        relayHomeStateHolder.refreshLauncherState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        relayHomeStateHolder = ViewModelProvider(this)[RelayHomeStateHolder::class.java]
        setContent {
            RelayHomeApp(
                stateHolder = relayHomeStateHolder,
                systemActions = this@MainActivity
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            relayHomeStateHolder.onHomeIntent()
        }
    }

    override fun onResume() {
        super.onResume()
        InstalledApps.invalidateCache()
        relayHomeStateHolder.onResume()
    }

    override fun requestHomeRole() {
        val homeSettings = Intent(Settings.ACTION_HOME_SETTINGS)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                homeRoleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        // Some Android TV builds expose the Home intent but not the HOME role, and some expose
        // the role while already holding it. In both cases the system Home page is the reliable
        // next step instead of silently doing nothing.
        startActivity(if (homeSettings.resolveActivity(packageManager) != null) homeSettings else Intent(Settings.ACTION_SETTINGS))
    }

    override fun requestNotificationListenerAccess() {
        val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val detail = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(this, SmartTubeNowPlayingService::class.java).flattenToString()
            )
        } else {
            fallback
        }
        startActivity(if (detail.resolveActivity(packageManager) != null) detail else fallback)
    }

    override fun requestAutoStartAccessibility() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
