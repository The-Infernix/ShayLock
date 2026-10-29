package com.example.shaylock.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** A launchable app that the user can add to ShayLock. */
data class InstalledApp(
    val packageName: String,
    val label: String,
)

/**
 * Reads the installed, launchable apps from the package manager.
 *
 * The manifest declares a `<queries>` entry for the launcher intent; without it Android 11+
 * would only return ShayLock itself.
 */
object AppCatalog {

    /** Returns every launchable app except ShayLock, sorted by label. */
    fun installedLaunchableApps(context: Context): List<InstalledApp> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        return resolved
            .mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                val packageName = activityInfo.packageName
                if (packageName == context.packageName) return@mapNotNull null

                InstalledApp(
                    packageName = packageName,
                    label = info.loadLabel(packageManager)?.toString()
                        ?.takeIf { it.isNotBlank() }
                        ?: packageName,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
