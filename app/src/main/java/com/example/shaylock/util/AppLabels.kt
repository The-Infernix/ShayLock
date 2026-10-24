package com.example.shaylock.util

import android.content.Context
import android.content.pm.PackageManager

/**
 * Resolves the user facing name of an installed package.
 */
object AppLabels {

    /** Returns the application label for [packageName], or the package name when unknown. */
    fun labelFor(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    } catch (e: Exception) {
        packageName
    }
}
