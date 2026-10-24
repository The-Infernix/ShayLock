package com.example.shaylock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import com.example.shaylock.core.BlockController
import com.example.shaylock.core.BlockPolicy
import com.example.shaylock.core.PermissionUtils
import com.example.shaylock.core.formatLimit
import com.example.shaylock.service.OverlayController
import com.example.shaylock.ui.PermissionUiState
import com.example.shaylock.ui.screens.PinDialog
import com.example.shaylock.ui.screens.SetupScreen
import com.example.shaylock.ui.theme.InkBottom
import com.example.shaylock.ui.theme.InkTop
import com.example.shaylock.ui.theme.ShayLockTheme

/**
 * The single activity of the app: it hosts the setup / dashboard screen.
 *
 * Everything else runs in the background:
 *  - [com.example.shaylock.service.BlockerAccessibilityService] watches the foreground app and
 *    measures how much of the daily allowance is actually consumed.
 *  - [com.example.shaylock.service.OverlayService] draws the strict lock screen.
 */
class MainActivity : ComponentActivity() {

    private var permissionState by mutableStateOf(PermissionUiState())
    private var selectedLimitMs by mutableLongStateOf(BlockPolicy.DEFAULT_DAILY_LIMIT_MS)
    private var pinRequest by mutableStateOf<PinRequest?>(null)

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
        selectedLimitMs = BlockController.dailyLimit.value.limitMs
            .takeIf { it > 0L }
            ?: BlockPolicy.DEFAULT_DAILY_LIMIT_MS
        refreshPermissionState()

        setContent {
            ShayLockTheme {
                val limit by BlockController.dailyLimit.collectAsState()
                val usedTodayMs by BlockController.usedTodayMs.collectAsState()
                val limitRemainingMs by BlockController.limitRemainingMs.collectAsState()
                val isBlockingEffective by BlockController.isBlockingEffective.collectAsState()
                val sessionRemainingMs by BlockController.sessionRemainingMs.collectAsState()
                val pinExtensionsUsedToday by
                    BlockController.pinExtensionsUsedToday.collectAsState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(InkTop, InkBottom))),
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                    ) { innerPadding ->
                        SetupScreen(
                            modifier = Modifier.padding(innerPadding),
                            permissionState = permissionState,
                            limit = limit,
                            usedTodayMs = usedTodayMs,
                            limitRemainingMs = limitRemainingMs,
                            isBlockingEffective = isBlockingEffective,
                            sessionRemainingMs = sessionRemainingMs,
                            pinExtensionsRemainingToday =
                                (BlockPolicy.MAX_PIN_EXTENSIONS_PER_DAY - pinExtensionsUsedToday)
                                    .coerceAtLeast(0),
                            selectedLimitMs = selectedLimitMs,
                            onSelectLimit = { selectedLimitMs = it },
                            onRequestSetLimit = { limitMs -> requestSetLimit(limitMs) },
                            onRequestDisable = { pinRequest = PinRequest.Disable },
                            onRequestPause = {
                                if (BlockController.hasPinExtensionsLeft()) {
                                    pinRequest = PinRequest.Pause(
                                        BlockPolicy.PAUSE_DURATION_MS,
                                    )
                                }
                            },
                            onResumeNow = { BlockController.endSession() },
                            onOpenAccessibilitySettings = { openAccessibilitySettings() },
                            onOpenOverlaySettings = { openOverlaySettings() },
                            onRequestNotifications = { requestNotificationPermission() },
                            onPreviewOverlay = { previewOverlay() },
                        )
                    }

                    val request = pinRequest
                    if (request != null) {
                        PinDialog(
                            title = request.title,
                            message = request.message,
                            confirmLabel = "Confirm",
                            onDismiss = { pinRequest = null },
                            onConfirm = { pin -> confirmWithPin(request, pin) },
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
     * Sets the first daily allowance without a PIN; later changes go through [PinRequest.SetLimit].
     */
    private fun requestSetLimit(limitMs: Long) {
        if (BlockController.isPinRequiredToSetLimit()) {
            pinRequest = PinRequest.SetLimit(limitMs)
            return
        }

        val accepted = BlockController.applyDailyLimit(limitMs, pin = null)
        android.util.Log.i(TAG, "Initial limit=$limitMs accepted=$accepted")
        if (accepted) {
            OverlayController.keepAlive(this)
        }
    }

    /**
     * Runs the protected action after the PIN was entered.
     *
     * @return true when the PIN was correct, which lets the dialog close.
     */
    private fun confirmWithPin(request: PinRequest, code: String): Boolean {
        val accepted = when (request) {
            is PinRequest.SetLimit -> BlockController.applyDailyLimit(request.limitMs, code)
            is PinRequest.Disable -> BlockController.setLimiterEnabled(false, code)
            is PinRequest.Pause -> BlockController.pauseFor(request.durationMs, code)
        }
        android.util.Log.i(TAG, "PIN request=$request accepted=$accepted")

        if (accepted) {
            when (request) {
                is PinRequest.SetLimit -> OverlayController.keepAlive(this)
                is PinRequest.Disable -> OverlayController.stop(this)
                is PinRequest.Pause -> Unit
            }
        }
        return accepted
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

    /** Raises the lock screen over this app so the flow can be verified without Instagram. */
    private fun previewOverlay() {
        if (!PermissionUtils.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        OverlayController.show(this, BlockPolicy.PRIMARY_PACKAGE)
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
            targetInstalled = PermissionUtils.isTargetInstalled(this),
        )
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

    data class SetLimit(val limitMs: Long) : PinRequest {
        override val title: String = "Enter your PIN"
        override val message: String =
            "Confirm the daily limit of ${formatLimit(limitMs)}."
    }

    data object Disable : PinRequest {
        override val title: String = "Enter your PIN"
        override val message: String =
            "Turning the blocker off needs your PIN."
    }

    data class Pause(val durationMs: Long) : PinRequest {
        override val title: String = "Enter your PIN"
        override val message: String =
            "Pausing for ${durationMs / 60_000} minutes needs your PIN."
    }
}
