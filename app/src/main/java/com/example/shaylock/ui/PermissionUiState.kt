package com.example.shaylock.ui

/**
 * Snapshot of the system permissions the blocker depends on.
 *
 * Refreshed every time the setup screen resumes, because both permissions are granted in
 * separate system screens.
 */
data class PermissionUiState(
    /** The accessibility watchdog is switched on in settings and connected to the app. */
    val accessibilityEnabled: Boolean = false,
    /**
     * The service is listed as enabled in system settings. It can be true while the service is
     * still reconnecting after a process restart, which the UI shows as "Connecting".
     */
    val accessibilityToggleOn: Boolean = false,
    /** The app may draw over other apps (`SYSTEM_ALERT_WINDOW`). */
    val overlayGranted: Boolean = false,
    /** The app may post the foreground service notification (Android 13+). */
    val notificationsGranted: Boolean = false,
) {
    /** The permission is enabled but the system has not connected the service yet. */
    val accessibilityConnecting: Boolean
        get() = accessibilityToggleOn && !accessibilityEnabled

    /** Both mandatory permissions are fully in place, so the blocker can actually work. */
    val readyToBlock: Boolean
        get() = accessibilityEnabled && overlayGranted
}
