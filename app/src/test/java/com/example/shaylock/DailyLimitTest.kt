package com.example.shaylock

import com.example.shaylock.core.DailyLimit
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the daily allowance model.
 */
class DailyLimitTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private val day1Noon = Instant.parse("2024-03-15T12:00:00Z").toEpochMilli()
    private val day1Evening = Instant.parse("2024-03-15T23:30:00Z").toEpochMilli()
    private val day2Noon = Instant.parse("2024-03-16T12:00:00Z").toEpochMilli()

    private val thirtyMinutes = 30L * 60L * 1000L

    @Test
    fun `start of day is local midnight`() {
        val start = DailyLimit.startOfDay(day1Noon, zone)
        assertEquals(Instant.parse("2024-03-15T00:00:00Z").toEpochMilli(), start)
    }

    @Test
    fun `a disabled limiter never blocks`() {
        val limit = DailyLimit(isEnabled = false, limitMs = thirtyMinutes, usedMs = thirtyMinutes)
        assertFalse(limit.isReached())
    }

    @Test
    fun `the allowance is reached when it is used up`() {
        val limit = DailyLimit(isEnabled = true, limitMs = thirtyMinutes, usedMs = thirtyMinutes)
        assertTrue(limit.isReached())
        assertEquals(0L, limit.remainingMs)
    }

    @Test
    fun `the allowance is not reached while there is time left`() {
        val limit = DailyLimit(isEnabled = true, limitMs = thirtyMinutes, usedMs = 1_799_000L)
        assertFalse(limit.isReached())
        assertEquals(1_000L, limit.remainingMs)
    }

    @Test
    fun `used fraction is clamped to one`() {
        val limit = DailyLimit(isEnabled = true, limitMs = thirtyMinutes, usedMs = 3_600_000L)
        assertEquals(1f, limit.usedFraction(), 0.0001f)
    }

    @Test
    fun `rolling inside the same day keeps the usage`() {
        val limit = DailyLimit(
            isEnabled = true,
            limitMs = thirtyMinutes,
            usedMs = 10_000L,
            dayStartWallClock = DailyLimit.startOfDay(day1Noon, zone),
        )
        val rolled = limit.rolledToDay(day1Evening, zone)
        assertEquals(10_000L, rolled.usedMs)
        assertEquals(DailyLimit.startOfDay(day1Noon, zone), rolled.dayStartWallClock)
    }

    @Test
    fun `rolling into the next day resets the usage but keeps the limit`() {
        val limit = DailyLimit(
            isEnabled = true,
            limitMs = thirtyMinutes,
            usedMs = thirtyMinutes,
            dayStartWallClock = DailyLimit.startOfDay(day1Noon, zone),
        )
        val rolled = limit.rolledToDay(day2Noon, zone)
        assertEquals(0L, rolled.usedMs)
        assertEquals(thirtyMinutes, rolled.limitMs)
        assertTrue(rolled.isEnabled)
        assertEquals(DailyLimit.startOfDay(day2Noon, zone), rolled.dayStartWallClock)
        assertFalse(rolled.isReached())
    }
}
