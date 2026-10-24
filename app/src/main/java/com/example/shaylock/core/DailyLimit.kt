package com.example.shaylock.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The daily screen-time allowance for the blocked apps.
 *
 * The model is intentionally simple: a fixed allowance per day ([limitMs]) and the time that has
 * already been consumed today ([usedMs]). The allowance itself never drifts - it stays exactly as
 * the user set it until it is changed again with the PIN. Only the *usage* counter is reset, and
 * that happens automatically at local midnight.
 *
 * Wall-clock timestamps are used (rather than [android.os.SystemClock.elapsedRealtime]) so the
 * state survives a reboot or a process death. A clock change shifts the counter by the same delta,
 * which is acceptable for a screen-time limiter.
 */
data class DailyLimit(
    /** Whether the daily limiter is switched on at all. */
    val isEnabled: Boolean = false,
    /** The allowance per day in milliseconds; 0 means "no limit configured". */
    val limitMs: Long = 0L,
    /** Time already consumed today; only the committed part, never the in-flight session. */
    val usedMs: Long = 0L,
    /** Wall-clock millis of the local midnight the usage belongs to. */
    val dayStartWallClock: Long = 0L,
) {

    /** Milliseconds of today's allowance that are still available. */
    val remainingMs: Long
        get() = (limitMs - usedMs).coerceAtLeast(0L)

    /** How much of today's allowance is consumed, as a fraction in `0f..1f`. */
    fun usedFraction(used: Long = usedMs): Float =
        if (limitMs <= 0L) 0f else (used.toFloat() / limitMs).coerceIn(0f, 1f)

    /**
     * True when today's allowance is used up.
     *
     * @param used the total usage to evaluate, including a currently running foreground session.
     */
    fun isReached(used: Long = usedMs): Boolean =
        isEnabled && limitMs > 0L && used >= limitMs

    /**
     * Returns a copy that belongs to the calendar day containing [now].
     *
     * When the day changed the usage counter is reset; the allowance itself is untouched.
     */
    fun rolledToDay(now: Long, zone: ZoneId = ZoneId.systemDefault()): DailyLimit {
        val dayStart = startOfDay(now, zone)
        if (dayStartWallClock == dayStart) return this
        return copy(usedMs = 0L, dayStartWallClock = dayStart)
    }

    companion object {
        /**
         * Wall-clock millis of the local midnight that starts the day containing [now].
         */
        fun startOfDay(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
                .toLocalDate()
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
    }
}
