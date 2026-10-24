package com.example.shaylock.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.shaylock.R
import com.example.shaylock.core.BlockController
import com.example.shaylock.core.PermissionUtils

/**
 * Periodic health check for the accessibility watchdog.
 *
 * Android does not tell an app when its accessibility service is disabled, so a 15-minute
 * WorkManager check is the reliable backstop. If a rule is armed but the service is off in
 * settings, a high-priority notification sends the user straight back to the settings screen.
 */
class ServiceHealthWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {

    override fun doWork(): Result {
        BlockController.initialize(applicationContext)

        // Nothing to protect, nothing to warn about.
        if (!BlockController.dailyLimit.value.isEnabled) return Result.success()

        val serviceEnabled =
            PermissionUtils.isAccessibilityServiceEnabledInSettings(applicationContext)
        if (serviceEnabled) {
            // Clear a warning the user has already fixed.
            NotificationManagerCompat.from(applicationContext).cancel(NOTIFICATION_ID)
        } else {
            notifyProtectionOff()
        }
        return Result.success()
    }

    private fun notifyProtectionOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Without notification permission the system cannot show the warning.
            return
        }

        createChannel()

        val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val settingsPendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            settingsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shaylock_mark)
            .setContentTitle(applicationContext.getString(R.string.notification_protection_off_title))
            .setContentText(applicationContext.getString(R.string.notification_protection_off_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(applicationContext.getString(R.string.notification_protection_off_text)),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(settingsPendingIntent)
            .addAction(
                0,
                applicationContext.getString(R.string.notification_protection_off_action),
                settingsPendingIntent,
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.notification_health_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description =
                applicationContext.getString(R.string.notification_health_channel_description)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "shaylock_service_health"
        const val NOTIFICATION_ID = 4720
    }
}
