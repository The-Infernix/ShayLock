package com.example.shaylock.core

import java.time.ZoneId

/**
 * One protected app and its independent daily timer.
 *
 * [dailyLimit] owns the allowance and today's committed usage. The remaining fields are the
 * per-app override budget introduced by the multi-app refactor: each package gets its own
 * three five-minute unlocks and its own limit-change cooldown.
 */
data class TrackedApp(
    val packageName: String,
    val label: String,
    val dailyLimit: DailyLimit,
    val overridesUsed: Int = 0,
    val overrideSessionEndWallClock: Long = 0L,
    val limitChangesToday: Int = 0,
    val sortOrder: Int = 0,
) {

    /** The configured allowance in milliseconds. */
    val limitMs: Long get() = dailyLimit.limitMs

    /** Committed usage today, without a currently running foreground session. */
    val usedMs: Long get() = dailyLimit.usedMs

    /** Milliseconds of today's allowance that are still available. */
    val remainingMs: Long get() = dailyLimit.remainingMs

    /** Overrides left today for this app. */
    val overridesRemainingToday: Int
        get() = (BlockPolicy.MAX_OVERRIDES_PER_APP_PER_DAY - overridesUsed).coerceAtLeast(0)

    /** True when today's allowance is used up. */
    fun isLimitReached(used: Long = usedMs): Boolean = dailyLimit.isReached(used)

    /** Consumed fraction in `0f..1f`, clamped for the progress bar. */
    fun usedFraction(used: Long = usedMs): Float = dailyLimit.usedFraction(used)

    /** Milliseconds left in the app's running PIN override, 0 when none is active. */
    fun overrideRemainingMs(now: Long): Long =
        (overrideSessionEndWallClock - now).coerceAtLeast(0L)

    /** True while a PIN override is currently lifting this app's block. */
    fun isOverrideActive(now: Long): Boolean = overrideSessionEndWallClock > now

    /** True while the daily limit-change cooldown still allows an edit. */
    fun canChangeLimitToday(): Boolean =
        limitChangesToday < BlockPolicy.MAX_LIMIT_CHANGES_PER_APP_PER_DAY

    /**
     * Returns a copy that belongs to the calendar day containing [now].
     *
     * Usage, overrides, the override session and the limit-change cooldown are per calendar day,
     * so they are all cleared together while the allowance itself is preserved.
     */
    fun rolledToDay(now: Long, zone: ZoneId = ZoneId.systemDefault()): TrackedApp {
        val rolled = dailyLimit.rolledToDay(now, zone)
        if (rolled === dailyLimit) return this

        return copy(
            dailyLimit = rolled,
            overridesUsed = 0,
            overrideSessionEndWallClock = 0L,
            limitChangesToday = 0,
        )
    }
}

/**
 * UI-facing snapshot of [TrackedApp] for a single instant.
 *
 * The committed usage is merged with the live foreground session here, so every caller (dashboard
 * cards, overlay and progress bars) sees the same number.
 */
data class TrackedAppState(
    val app: TrackedApp,
    val usedMs: Long,
    val remainingMs: Long,
    val usedFraction: Float,
    val isLimitReached: Boolean,
    val isBlocked: Boolean,
    val overrideRemainingMs: Long,
    val overridesRemainingToday: Int,
    val canChangeLimit: Boolean,
    val isForeground: Boolean,
    val isBlockerEnabled: Boolean,
) {
    val packageName: String get() = app.packageName
    val label: String get() = app.label
    val limitMs: Long get() = app.limitMs
    val isOverrideActive: Boolean get() = overrideRemainingMs > 0L
}
