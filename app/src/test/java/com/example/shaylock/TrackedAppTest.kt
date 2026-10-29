package com.example.shaylock

import com.example.shaylock.core.BlockPolicy
import com.example.shaylock.core.DailyLimit
import com.example.shaylock.core.TrackedApp
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the per-app timer model: overrides, limit-change cooldown and daily rollover.
 */
class TrackedAppTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val day1Noon = Instant.parse("2024-03-15T12:00:00Z").toEpochMilli()
    private val day1Evening = Instant.parse("2024-03-15T23:30:00Z").toEpochMilli()
    private val day2Noon = Instant.parse("2024-03-16T12:00:00Z").toEpochMilli()

    private val thirtyMinutes = 30L * 60L * 1000L

    private fun app(
        usedMs: Long = 0L,
        overridesUsed: Int = 0,
        overrideEnd: Long = 0L,
        limitChanges: Int = 0,
    ) = TrackedApp(
        packageName = "com.example.app",
        label = "Example",
        dailyLimit = DailyLimit(
            isEnabled = true,
            limitMs = thirtyMinutes,
            usedMs = usedMs,
            dayStartWallClock = DailyLimit.startOfDay(day1Noon, zone),
        ),
        overridesUsed = overridesUsed,
        overrideSessionEndWallClock = overrideEnd,
        limitChangesToday = limitChanges,
    )

    @Test
    fun `remaining overrides never go below zero`() {
        assertEquals(3, app(overridesUsed = 0).overridesRemainingToday)
        assertEquals(1, app(overridesUsed = 2).overridesRemainingToday)
        assertEquals(0, app(overridesUsed = 3).overridesRemainingToday)
        assertEquals(0, app(overridesUsed = 99).overridesRemainingToday)
    }

    @Test
    fun `an override is active only while its end is in the future`() {
        val active = app(overrideEnd = day1Noon + BlockPolicy.OVERRIDE_DURATION_MS)

        assertTrue(active.isOverrideActive(day1Noon))
        assertEquals(
            BlockPolicy.OVERRIDE_DURATION_MS,
            active.overrideRemainingMs(day1Noon),
        )
        assertFalse(active.isOverrideActive(active.overrideSessionEndWallClock))
        assertEquals(0L, active.overrideRemainingMs(active.overrideSessionEndWallClock + 1_000L))
    }

    @Test
    fun `the limit can be changed until the cap is reached`() {
        assertTrue(app(limitChanges = 0).canChangeLimitToday())
        assertFalse(app(limitChanges = BlockPolicy.MAX_LIMIT_CHANGES_PER_APP_PER_DAY).canChangeLimitToday())
    }

    @Test
    fun `the allowance is reached with live usage included`() {
        val tracked = app(usedMs = thirtyMinutes - 1_000L)

        assertFalse(tracked.isLimitReached())
        assertTrue(tracked.isLimitReached(used = thirtyMinutes))
        assertEquals(1f, tracked.usedFraction(used = thirtyMinutes), 0.0001f)
    }

    @Test
    fun `rolling inside the same day keeps overrides and cooldowns`() {
        val tracked = app(
            usedMs = 10_000L,
            overridesUsed = 2,
            overrideEnd = day1Evening + 1_000L,
            limitChanges = 1,
        )

        val rolled = tracked.rolledToDay(day1Evening, zone)

        assertSame(tracked, rolled)
        assertEquals(2, rolled.overridesUsed)
        assertEquals(1, rolled.limitChangesToday)
        assertEquals(10_000L, rolled.usedMs)
    }

    @Test
    fun `rolling into the next day resets per-day counters but keeps the allowance`() {
        val tracked = app(
            usedMs = thirtyMinutes,
            overridesUsed = 3,
            overrideEnd = day1Evening + 1_000L,
            limitChanges = 1,
        )

        val rolled = tracked.rolledToDay(day2Noon, zone)

        assertEquals(0L, rolled.usedMs)
        assertEquals(0, rolled.overridesUsed)
        assertEquals(0L, rolled.overrideSessionEndWallClock)
        assertEquals(0, rolled.limitChangesToday)
        assertEquals(thirtyMinutes, rolled.limitMs)
        assertEquals(DailyLimit.startOfDay(day2Noon, zone), rolled.dailyLimit.dayStartWallClock)
        assertFalse(rolled.isLimitReached())
        assertTrue(rolled.canChangeLimitToday())
        assertEquals(BlockPolicy.MAX_OVERRIDES_PER_APP_PER_DAY, rolled.overridesRemainingToday)
    }
}
