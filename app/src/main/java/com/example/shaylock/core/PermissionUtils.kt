package com.example.shaylock.core

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import com.example.shaylock.service.BlockerAccessibilityService

/**
 * Helpers for querying the two "special" system permissions the blocker needs.
 *
 * Android does not expose a single reliable API for either of them: OEM builds store the
 * accessibility component in slightly different formats and some of them lag behind when the
 * enabled-service list is read. Every check therefore combines the authoritative system API with
 * a parsed fallback from `Settings.Secure` / `AppOpsManager`.
 */
object PermissionUtils {

    /**
     * True only when our [BlockerAccessibilityService] is both switched on in system settings and
     * actually connected/running.
     *
     * A component can stay listed in the settings while the service is not connected (for example
     * right after a process restart). The UI treats that state as "connecting" instead of "ready",
     * so the blocker is never armed without a live watchdog.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean =
        isAccessibilityServiceEnabledInSettings(context) && isAccessibilityServiceRunning()

    /**
     * True when the service is listed as enabled in the system settings, regardless of whether the
     * service instance has finished connecting. Used by the dashboard to distinguish
     * "Action required" from "Connecting".
     */
    fun isAccessibilityServiceEnabledInSettings(context: Context): Boolean {
        val expected = ComponentName(context, BlockerAccessibilityService::class.java)

        // Primary source: the accessibility framework's own enabled-service list.
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (manager != null) {
            val enabledServices = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
            )
            if (enabledServices.any { info -> matchesServiceInfo(info, expected) }) {
                return true
            }
        }

        // Fallback for OEM builds that do not populate the list above reliably.
        val resolver = context.contentResolver
        val accessibilityEnabled = Settings.Secure.getInt(
            resolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        )
        if (accessibilityEnabled != 1) return false

        val enabledServices = Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        if (TextUtils.isEmpty(enabledServices)) return false

        // Components are normally separated by ':', a few OEM builds use ';'.
        return enabledServices
            .split(':', ';')
            .any { entry -> matchesFlattenedComponent(entry, expected) }
    }

    /** True when the accessibility service instance is currently connected. */
    fun isAccessibilityServiceRunning(): Boolean = BlockerAccessibilityService.isRunning

    /** True when the app is allowed to draw over other apps. */
    fun canDrawOverlays(context: Context): Boolean {
        if (Settings.canDrawOverlays(context)) return true

        // Fallback: some OEMs return a stale value from Settings but keep the app-op up to date.
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(),
                    context.packageName,
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /** True when [packageName] is actually installed on the device. */
    fun isPackageInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (e: Exception) {
        false
    }

    private fun matchesServiceInfo(
        info: AccessibilityServiceInfo,
        expected: ComponentName,
    ): Boolean {
        val serviceInfo = info.resolveInfo?.serviceInfo ?: return false
        return serviceInfo.packageName.equals(expected.packageName, ignoreCase = true) &&
            matchesClassName(serviceInfo.name, expected.className)
    }

    private fun matchesFlattenedComponent(entry: String, expected: ComponentName): Boolean {
        val flattened = entry.trim()
        if (flattened.isEmpty()) return false

        val parsed = ComponentName.unflattenFromString(flattened) ?: return false
        return parsed.packageName.equals(expected.packageName, ignoreCase = true) &&
            matchesClassName(parsed.className, expected.className)
    }

    /**
     * Compares fully-qualified, short (`.service.Foo`) and bare (`Foo`) class names.
     */
    private fun matchesClassName(actual: String, expected: String): Boolean {
        val actualName = actual.removePrefix(".").trim()
        val expectedName = expected.removePrefix(".").trim()

        if (actualName.equals(expectedName, ignoreCase = true)) return true
        if (actualName.endsWith(".$expectedName", ignoreCase = true)) return true
        return expectedName.endsWith(".$actualName", ignoreCase = true)
    }
}
