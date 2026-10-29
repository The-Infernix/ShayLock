package com.example.shaylock

import com.example.shaylock.core.BlockPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the hard-coded blocking rules shared by the watchdog and the UI.
 */
class BlockPolicyTest {

    @Test
    fun `legacy instagram packages are recognised`() {
        assertTrue(BlockPolicy.isInstagramPackage("com.instagram.android"))
        assertTrue(BlockPolicy.isInstagramPackage("com.instagram.lite"))
    }

    @Test
    fun `other packages are not instagram`() {
        assertFalse(BlockPolicy.isInstagramPackage("com.android.settings"))
        assertFalse(BlockPolicy.isInstagramPackage("com.example.shaylock"))
        assertFalse(BlockPolicy.isInstagramPackage(null))
        assertFalse(BlockPolicy.isInstagramPackage(""))
    }

    @Test
    fun `the default allowance is one of the offered options`() {
        assertTrue(BlockPolicy.DAILY_LIMIT_OPTIONS.contains(BlockPolicy.DEFAULT_DAILY_LIMIT_MS))
    }

    @Test
    fun `overrides stop after three uses per app per day`() {
        assertTrue(BlockPolicy.canOverride(0))
        assertTrue(BlockPolicy.canOverride(2))
        assertFalse(BlockPolicy.canOverride(3))
        assertFalse(BlockPolicy.canOverride(4))
        assertEquals(3, BlockPolicy.MAX_OVERRIDES_PER_APP_PER_DAY)
    }

    @Test
    fun `an override lasts five minutes`() {
        assertEquals(5L * 60L * 1000L, BlockPolicy.OVERRIDE_DURATION_MS)
    }

    @Test
    fun `an allowance can only be changed once per app per day`() {
        assertEquals(1, BlockPolicy.MAX_LIMIT_CHANGES_PER_APP_PER_DAY)
    }

    @Test
    fun `the legacy pin is a valid four digit pin`() {
        assertEquals(BlockPolicy.PIN_LENGTH, BlockPolicy.DEFAULT_PIN.length)
        assertTrue(BlockPolicy.DEFAULT_PIN.all { it.isDigit() })
    }

    @Test
    fun `allowances are positive and strictly increasing`() {
        val options = BlockPolicy.DAILY_LIMIT_OPTIONS
        assertTrue(options.all { it > 0L })
        assertEquals(options.sorted(), options)
        assertEquals(options.distinct(), options)
    }
}
