package com.example.shaylock

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.shaylock.core.BlockController
import com.example.shaylock.service.ServiceHealthWorker
import java.util.concurrent.TimeUnit

/**
 * Initialises the shared blocking state as early as possible.
 *
 * The application object is created before any service or activity, so the accessibility
 * watchdog always sees a consistent, persisted state. It also schedules the periodic watchdog
 * health check that survives process death and reboots.
 */
class ShayLockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BlockController.initialize(this)
        scheduleServiceHealthCheck()
    }

    private fun scheduleServiceHealthCheck() {
        val request = PeriodicWorkRequestBuilder<ServiceHealthWorker>(
            15,
            TimeUnit.MINUTES,
        ).build()

        runCatching {
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                HEALTH_CHECK_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }.onFailure { error ->
            Log.w("ShayLockApp", "Unable to schedule the accessibility health check", error)
        }
    }

    private companion object {
        const val HEALTH_CHECK_WORK = "shaylock_service_health"
    }
}
