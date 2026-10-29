package com.example.shaylock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.shaylock.core.BlockController
import com.example.shaylock.core.BlockPolicy
import com.example.shaylock.core.PermissionUtils
import com.example.shaylock.core.TrackedAppState
import com.example.shaylock.core.formatLimit
import com.example.shaylock.service.BlockerAccessibilityService
import com.example.shaylock.service.OverlayController
import com.example.shaylock.ui.PermissionUiState
import com.example.shaylock.ui.screens.AboutScreen
import com.example.shaylock.ui.screens.AppPickerScreen
import com.example.shaylock.ui.screens.PinDialog
import com.example.shaylock.ui.screens.PinSettingsDialog
import com.example.shaylock.ui.screens.SetupScreen
import com.example.shaylock.ui.theme.InkBottom
import com.example.shaylock.ui.theme.InkTop
import com.example.shaylock.ui.theme.ShayLockTheme

/**
 * The single activity of the app: it hosts the dashboard, the app picker and the about screen.
 *
 * Everything else runs in the background:
 *  - [com.example.shaylock.service.BlockerAccessibilityService] watches the foreground app and
 *    measures how much of each app's daily allowance is actually consumed.
 *  - [com.example.shaylock.service.OverlayService] draws the strict lock screen over any app
 *    whose allowance is used up.
 */
class MainActivity : ComponentActivity() {

    private var permissionState by mutableStateOf(PermissionUiState())
    private var pinRequest by mutableStateOf<PinRequest?>(null)
    private var showPinSettings by mutableStateOf(false)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val permissionRecheck = Runnable { refreshPermissionState() }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            refreshPermissionState()
            if (!granted) {
                // Not fatal: the foreground service still runs, only the notification is hidden.
                android.util.Log.i(TAG, "Notifications denied - the blocker still works.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        BlockController.initialize(applicationContext)
        refreshPermissionState()

        setContent {
            ShayLockTheme {
                val appStates by BlockController.appStates.collectAsState()
                val blockerEnabled by BlockController.isBlockerEnabled.collectAsState()
                val isUsingDefaultPin by BlockController.isUsingDefaultPin.collectAsState()

                val navController = rememberNavController()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(InkTop, InkBottom))),
                ) {
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                containerColor = Color.Transparent,
                            ) { innerPadding ->
                                SetupScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    permissionState = permissionState,
                                    apps = appStates,
                                    blockerEnabled = blockerEnabled,
                                    isUsingDefaultPin = isUsingDefaultPin,
                                    onAddApps = { navController.navigate("add_apps") },
                                    onRequestSetLimit = { packageName, limitMs ->
                                        requestSetLimit(packageName, limitMs)
                                    },
                                    onRequestRemove = { packageName ->
                                        requestRemove(packageName)
                                    },
                                    onRequestUnlock = { packageName ->
                                        requestUnlock(packageName, appStates)
                                    },
                                    onResumeBlocking = { packageName ->
                                        BlockController.endOverride(packageName)
                                        toast("Blocking resumed")
                                    },
                                    onEnableProtection = { enableProtection() },
                                    onRequestDisable = { pinRequest = PinRequest.Disable },
                                    onManagePin = { showPinSettings = true },
                                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                                    onOpenOverlaySettings = { openOverlaySettings() },
                                    onRequestNotifications = { requestNotificationPermission() },
                                    onPreviewOverlay = { previewOverlay(appStates) },
                                    onOpenAbout = { navController.navigate("about") },
                                )
                            }
                        }

                        composable("add_apps") {
                            AppPickerScreen(
                                modifier = Modifier.fillMaxSize(),
                                trackedPackages = appStates.map { it.packageName }.toSet(),
                                onNavigateBack = { navController.popBackStack() },
                                onAddApps = { packageNames ->
                                    val added = BlockController.addApps(packageNames)
                                    if (added > 0) {
                                        toast(
                                            if (added == 1) "App protected"
                                            else "$added apps protected",
                                        )
                                    }
                                    navController.popBackStack()
                                },
                            )
                        }

                        composable("about") {
                            AboutScreen(
                                accessibilityEnabled = permissionState.accessibilityEnabled,
                                overlayGranted = permissionState.overlayGranted,
                                onNavigateBack = { navController.popBackStack() },
                                onFixPermissions = { openAccessibilitySettings() },
                            )
                        }
                    }

                    pinRequest?.let { request ->
                        PinDialog(
                            title = request.title,
                            message = request.message,
                            confirmLabel = "Confirm",
                            onDismiss = { pinRequest = null },
                            onConfirm = { pin -> confirmWithPin(request, pin) },
                        )
                    }

                    if (showPinSettings) {
                        PinSettingsDialog(
                            isUsingDefaultPin = isUsingDefaultPin,
                            onDismiss = { showPinSettings = false },
                            onVerifyCurrentPin = { pin -> BlockController.authorize(pin) },
                            onChangePin = { pin -> BlockController.setPinAfterAuthorization(pin) },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Both permissions are granted on separate system screens, so refresh on every resume.
        refreshPermissionState()
        schedulePermissionRechecks()
    }

    override fun onPause() {
        mainHandler.removeCallbacks(permissionRecheck)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshPermissionState()
    }

    private fun schedulePermissionRechecks() {
        mainHandler.removeCallbacks(permissionRecheck)
        listOf(300L, 1_200L, 3_000L).forEach { delay ->
            mainHandler.postDelayed(permissionRecheck, delay)
        }
    }

    /**
     * Enables protection. Arming is always free; only weakening it needs the PIN.
     */
    private fun enableProtection() {
        if (!BlockController.setBlockerEnabled(true)) {
            toast("Add at least one app before starting protection.")
            return
        }
        OverlayController.keepAlive(this)
        // A protected app may already be on screen: evaluate it without waiting for an event.
        BlockerAccessibilityService.instance?.recheckForeground()
        toast("Protection started")
    }

    /**
     * Sets an app's allowance. The first configuration is free; after arming, the change needs
     * the PIN and is limited to one change per app per day.
     */
    private fun requestSetLimit(packageName: String, limitMs: Long) {
        val current = BlockController.trackedApp(packageName)
        if (current == null) {
            toast("That app is no longer protected.")
            return
        }
        if (current.limitMs == limitMs) {
            toast("That allowance is already set.")
            return
        }

        if (!BlockController.isPinRequiredForSettings()) {
            if (BlockController.applyLimit(packageName, limitMs, pin = null)) {
                toast("Allowance updated")
                if (BlockController.isBlockerEnabled.value) OverlayController.keepAlive(this)
            }
            return
        }

        if (!BlockController.canChangeLimit(packageName)) {
            toast("An app's allowance can only be changed once per day. Try again tomorrow.")
            return
        }
        pinRequest = PinRequest.SetLimit(packageName, current.label, limitMs)
    }

    /**
     * Removes an app from the protected list. Free before the first arming, PIN-protected after.
     */
    private fun requestRemove(packageName: String) {
        val app = BlockController.trackedApp(packageName) ?: return
        if (BlockController.isPinRequiredForSettings()) {
            pinRequest = PinRequest.Remove(packageName, app.label)
        } else {
            removeApp(packageName, pin = null)
        }
    }

    private fun requestUnlock(packageName: String, appStates: List<TrackedAppState>) {
        val state = appStates.firstOrNull { it.packageName == packageName } ?: return
        if (state.overridesRemainingToday <= 0) {
            toast("No PIN unlocks left today for ${state.label}.")
            return
        }
        pinRequest = PinRequest.Unlock(
            packageName = packageName,
            label = state.label,
            unlocksLeft = state.overridesRemainingToday,
        )
    }

    /**
     * Runs the protected action after the PIN was entered.
     *
     * @return true when the PIN was correct, which lets the dialog close.
     */
    private fun confirmWithPin(request: PinRequest, code: String): Boolean {
        val accepted = when (request) {
            is PinRequest.Unlock -> {
                if (!BlockController.canOverride(request.packageName)) {
                    toast("No PIN unlocks left today for ${request.label}.")
                    return true
                }
                BlockController.submitPin(request.packageName, code)
            }

            is PinRequest.SetLimit -> {
                if (!BlockController.canChangeLimit(request.packageName)) {
                    toast("An app's allowance can only be changed once per day.")
                    return true
                }
                BlockController.applyLimit(request.packageName, request.limitMs, code)
            }

            is PinRequest.Remove -> removeApp(request.packageName, code)

            PinRequest.Disable -> BlockController.setBlockerEnabled(false, code)
        }
        android.util.Log.i(TAG, "PIN request=$request accepted=$accepted")

        if (accepted) {
            when (request) {
                is PinRequest.Unlock -> toast("${request.label} unlocked for five minutes")
                is PinRequest.SetLimit -> {
                    OverlayController.keepAlive(this)
                    toast("Allowance updated")
                }
                is PinRequest.Remove -> Unit // removeApp already reported the result
                PinRequest.Disable -> {
                    OverlayController.stop(this)
                    toast("Protection turned off")
                }
            }
        }
        return accepted
    }

    private fun removeApp(packageName: String, pin: String?): Boolean {
        if (!BlockController.removeApp(packageName, pin)) return false

        if (BlockController.apps.value.isEmpty()) {
            // Nothing left to protect: tear the foreground service down.
            OverlayController.stop(this)
        }
        toast("App removed")
        return true
    }

    private fun openAccessibilitySettings() {
        launchFirstAvailable(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    private fun openOverlaySettings() {
        val packageUri = "package:$packageName".toUri()
        launchFirstAvailable(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
        )
    }

    /**
     * Opens the first settings screen the device actually supports. Some OEMs ship only one of
     * these intents, or reject the package-specific variant.
     */
    private fun launchFirstAvailable(vararg intents: Intent) {
        for (intent in intents) {
            if (runCatching { startActivity(intent) }.isSuccess) return
        }
        android.util.Log.w(TAG, "No matching system settings screen is available")
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            refreshPermissionState()
        }
    }

    /** Raises the lock screen over a protected app so the flow can be verified end to end. */
    private fun previewOverlay(appStates: List<TrackedAppState>) {
        val target = appStates.firstOrNull()
        if (target == null) {
            toast("Add an app before previewing the lock screen.")
            return
        }
        if (!PermissionUtils.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        OverlayController.show(this, target.packageName, webBlock = false)
    }

    private fun refreshPermissionState() {
        val accessibilityToggleOn = PermissionUtils.isAccessibilityServiceEnabledInSettings(this)
        permissionState = PermissionUiState(
            accessibilityEnabled = accessibilityToggleOn &&
                PermissionUtils.isAccessibilityServiceRunning(),
            accessibilityToggleOn = accessibilityToggleOn,
            overlayGranted = PermissionUtils.canDrawOverlays(this),
            notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}

/**
 * The protected actions that need the PIN before they are applied.
 */
private sealed interface PinRequest {

    val title: String
    val message: String

    data class Unlock(
        val packageName: String,
        val label: String,
        val unlocksLeft: Int,
    ) : PinRequest {
        override val title: String = "Enter your PIN"
        override val message: String =
            "Unlock $label for five minutes. $unlocksLeft of " +
                "${BlockPolicy.MAX_OVERRIDES_PER_APP_PER_DAY} unlocks left today."
    }

    data class SetLimit(
        val packageName: String,
        val label: String,
        val limitMs: Long,
    ) : PinRequest {
        override val title: String = "Enter your PIN"
        override val message: String =
            "Change the daily allowance for $label to ${formatLimit(limitMs)}."
    }

    data class Remove(
        val packageName: String,
        val label: String,
    ) : PinRequest {
        override val title: String = "Enter your PIN"
        override val message: String =
            "Removing $label stops its timer and makes the app usable without limits."
    }

    data object Disable : PinRequest {
        override val title: String = "Enter your PIN"
        override val message: String =
            "Turning protection off needs your PIN."
    }
}
