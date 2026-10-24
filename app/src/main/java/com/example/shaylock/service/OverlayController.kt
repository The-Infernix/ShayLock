package com.example.shaylock.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Thin facade used by the accessibility watchdog and the setup UI to drive [OverlayService].
 */
object OverlayController {

    private const val TAG = "OverlayController"

    /**
     * Raises the lock screen over the currently visible app.
     *
     * The service is normally already running as a foreground service (it is started when the
     * rule is armed), so this call only delivers a new intent to it.
     */
    fun show(context: Context, targetPackage: String) {
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission not granted - cannot block $targetPackage")
            return
        }
        if (OverlayService.isOverlayShowing) return

        val intent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW
            putExtra(OverlayService.EXTRA_PACKAGE, targetPackage)
        }

        if (start(context, intent)) return

        // The foreground service could not be started (Android 12+ background start restrictions).
        // The overlay window itself only needs the "display over other apps" permission, so it is
        // drawn straight from this context - the blocker must work even without the service.
        Log.w(TAG, "OverlayService unavailable - drawing the lock screen directly")
        OverlayWindowHost.show(context, targetPackage) { /* nothing to stop */ }
    }

    /** Lowers the lock screen. */
    fun hide(context: Context) {
        // When the service is already running it can be torn down directly. Going through an
        // intent would start a foreground service only to stop it again.
        val running = OverlayService.instance
        if (running != null) {
            running.stopEverything()
            return
        }

        val intent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_HIDE
        }
        start(context, intent)
    }

    /**
     * Starts the foreground service so that it is already alive when a block is needed.
     *
     * Called from the foreground (the setup screen) which keeps the foreground service start
     * restriction on Android 12+ satisfied.
     */
    fun keepAlive(context: Context) {
        val intent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_KEEP_ALIVE
        }
        start(context, intent)
    }

    /** Stops the foreground service completely. */
    fun stop(context: Context) {
        val running = OverlayService.instance
        if (running != null) {
            running.stopEverything()
            return
        }

        val intent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        start(context, intent)
    }

    /**
     * Starts the overlay service.
     *
     * @return true when the service was started (or was already running).
     */
    private fun start(context: Context, intent: Intent): Boolean {
        try {
            ContextCompat.startForegroundService(context, intent)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "startForegroundService rejected, falling back to startService", e)
        }
        try {
            context.startService(intent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start OverlayService", e)
            return false
        }
    }
}
