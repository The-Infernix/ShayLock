package com.example.shaylock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.shaylock.MainActivity
import com.example.shaylock.R
import com.example.shaylock.core.BlockController

/**
 * Foreground service that owns the blocking session.
 *
 * It does three things:
 *  1. Keeps the process alive (and visible to the user through a low-key notification) while a
 *     blocking rule is armed.
 *  2. Raises and lowers the full-screen lock window via [OverlayWindowHost].
 *  3. Runs the 5 minute unlock session timer (driven by [BlockController]).
 *
 * A foreground service is used so that the lock screen can be raised while the app itself is in
 * the background, which is the whole point of a blocker.
 */
class OverlayService : Service() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // The service can be recreated after a process death without the Application object
        // having been recreated in the same order, so make the persisted state available first.
        BlockController.initialize(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The service is always started through startForegroundService(), so startForeground()
        // must be called within a few seconds - for *every* action, including the ones that
        // immediately tear the service down again. Otherwise the system kills the app with
        // "Context.startForegroundService() did not then call Service.startForeground()".
        try {
            promoteToForeground()
        } catch (e: Exception) {
            // Never let a broken notification take the whole blocker down: the overlay window
            // itself only needs the "display over other apps" permission.
            Log.e(TAG, "Unable to promote the service to the foreground", e)
        }

        when (intent?.action) {
            ACTION_SHOW -> {
                val target = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
                OverlayWindowHost.show(this, target) {
                    // Valid PIN: tear the whole service down, the session took over.
                    Log.i(TAG, "Unlocked, stopping overlay service")
                    stopEverything()
                }
            }

            ACTION_KEEP_ALIVE -> {
                Log.i(TAG, "Overlay service alive")
            }

            ACTION_HIDE -> {
                OverlayWindowHost.dismiss()
                stopEverything()
            }

            ACTION_STOP -> {
                OverlayWindowHost.dismiss()
                stopEverything()
            }

            else -> {
                // A null intent means Android recreated the service after killing the process.
                // Ask the watchdog to re-evaluate the app that is on screen right now; the next
                // accessibility event may otherwise only arrive after the user leaves the app.
                Log.i(TAG, "Service restarted by the system")
                BlockerAccessibilityService.instance?.recheckForeground()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        OverlayWindowHost.dismiss()
        instance = null
        super.onDestroy()
    }

    /** Removes the lock window and stops the foreground service. */
    fun stopEverything() {
        OverlayWindowHost.dismiss()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Promotes the service to the foreground so the lock screen may be drawn over other apps.
     *
     * On API 34+ the `specialUse` foreground service type is required and declared in the
     * manifest. On older releases the type flag is not understood by the platform, so the
     * two-argument overload is used to avoid an `IllegalArgumentException`.
     */
    private fun promoteToForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shaylock_mark)
            .setContentTitle(getString(R.string.notification_blocking_title))
            .setContentText(getString(R.string.notification_blocking_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "OverlayService"

        private const val CHANNEL_ID = "shaylock_blocking"
        private const val NOTIFICATION_ID = 4711

        const val ACTION_SHOW = "com.example.shaylock.action.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.example.shaylock.action.HIDE_OVERLAY"
        const val ACTION_KEEP_ALIVE = "com.example.shaylock.action.KEEP_ALIVE"
        const val ACTION_STOP = "com.example.shaylock.action.STOP_SERVICE"
        const val EXTRA_PACKAGE = "com.example.shaylock.extra.TARGET_PACKAGE"

        @Volatile
        var instance: OverlayService? = null
            private set

        /** True while the lock window is visible on screen. */
        val isOverlayShowing: Boolean
            get() = OverlayWindowHost.isShowing
    }
}
