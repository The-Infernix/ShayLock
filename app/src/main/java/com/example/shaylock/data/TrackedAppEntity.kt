package com.example.shaylock.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.shaylock.core.DailyLimit
import com.example.shaylock.core.TrackedApp

/**
 * Persisted row for one protected app.
 *
 * All counters are stored per package name: the daily allowance, the committed usage, the
 * number of PIN overrides used today and the currently running override session. Keeping them
 * on the row (instead of in a shared settings blob) is what makes every timer independent.
 */
@Entity(tableName = "tracked_apps")
data class TrackedAppEntity(
    @PrimaryKey
    val packageName: String,
    val label: String,
    val dailyLimitMs: Long,
    val usedMs: Long,
    val dayStartWallClock: Long,
    val overridesUsed: Int,
    val overrideSessionEndWallClock: Long,
    val limitChangesUsed: Int,
    val sortOrder: Int,
)

/** Maps the database row to the immutable domain model used by the UI and the watchdog. */
fun TrackedAppEntity.toDomain(): TrackedApp = TrackedApp(
    packageName = packageName,
    label = label,
    dailyLimit = DailyLimit(
        // A tracked app is always limited; only the global blocker switch can disable blocking.
        isEnabled = true,
        limitMs = dailyLimitMs,
        usedMs = usedMs,
        dayStartWallClock = dayStartWallClock,
    ),
    overridesUsed = overridesUsed,
    overrideSessionEndWallClock = overrideSessionEndWallClock,
    limitChangesToday = limitChangesUsed,
    sortOrder = sortOrder,
)

/** Flattens the domain model back into its database representation. */
fun TrackedApp.toEntity(): TrackedAppEntity = TrackedAppEntity(
    packageName = packageName,
    label = label,
    dailyLimitMs = dailyLimit.limitMs,
    usedMs = dailyLimit.usedMs,
    dayStartWallClock = dailyLimit.dayStartWallClock,
    overridesUsed = overridesUsed,
    overrideSessionEndWallClock = overrideSessionEndWallClock,
    limitChangesUsed = limitChangesToday,
    sortOrder = sortOrder,
)
