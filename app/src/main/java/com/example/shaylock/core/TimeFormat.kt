package com.example.shaylock.core

import java.util.concurrent.TimeUnit

/**
 * Human friendly duration formatting used by the setup screen and the lock screen.
 */
fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "0 min"

    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60
    val seconds = (millis % 60_000L) / 1000L

    return when {
        days > 0L -> "${days}d ${hours}h"
        hours > 0L -> if (minutes == 0L) pluralHours(hours) else "${pluralHours(hours)} $minutes min"
        minutes > 0L -> "$minutes min"
        // Sub-minute values are only used for the live session countdown.
        else -> "${seconds}s"
    }
}

/**
 * Formats a countdown as `MM:SS`, used by the unlock session indicator.
 */
fun formatCountdown(millis: Long): String {
    val safe = millis.coerceAtLeast(0L)
    val totalSeconds = safe / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

/**
 * Formats a daily allowance as a clean label such as `15 min`, `1 hour` or `2 hours 30 min`.
 */
fun formatLimit(millis: Long): String {
    if (millis <= 0L) return "0 min"

    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    if (totalMinutes < 1L) {
        val seconds = (millis / 1000L).coerceAtLeast(1L)
        return "$seconds s"
    }

    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return when {
        hours > 0L && minutes == 0L -> pluralHours(hours)
        hours > 0L -> "${pluralHours(hours)} $minutes min"
        else -> "$minutes min"
    }
}

/**
 * The allowance label used in the dashboard: a clean duration, or `Until stopped` when the
 * blocker has no active allowance.
 */
fun formatLimitOrUntilStopped(millis: Long): String =
    if (millis <= 0L) "Until stopped" else formatLimit(millis)

private fun pluralHours(hours: Long): String =
    if (hours == 1L) "1 hour" else "$hours hours"
