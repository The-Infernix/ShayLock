package com.example.shaylock

import com.example.shaylock.core.BlockPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the blocking policy that is shared by the watchdog and the overlay.
 */
class BlockPolicyTest {

    @Test
    fun `instagram packages are blocked`() {
        assertTrue(BlockPolicy.isBlockedPackage("com.instagram.android"))
        assertTrue(BlockPolicy.isBlockedPackage("com.instagram.lite"))
    }

    @Test
    fun `other packages are not blocked`() {
        assertFalse(BlockPolicy.isBlockedPackage("com.android.settings"))
        assertFalse(BlockPolicy.isBlockedPackage("com.example.shaylock"))
        assertFalse(BlockPolicy.isBlockedPackage(null))
        assertFalse(BlockPolicy.isBlockedPackage(""))
    }

    @Test
    fun `the primary package is part of the blocked set`() {
        assertTrue(BlockPolicy.BLOCKED_PACKAGES.contains(BlockPolicy.PRIMARY_PACKAGE))
    }

    @Test
    fun `the default allowance is one of the offered options`() {
        assertTrue(BlockPolicy.DAILY_LIMIT_OPTIONS.contains(BlockPolicy.DEFAULT_DAILY_LIMIT_MS))
    }

    @Test
    fun `pin extensions stop after three uses a day`() {
        assertTrue(BlockPolicy.canUsePinExtension(0))
        assertTrue(BlockPolicy.canUsePinExtension(2))
        assertFalse(BlockPolicy.canUsePinExtension(3))
        assertFalse(BlockPolicy.canUsePinExtension(4))
    }

    @Test
    fun `allowances are positive and strictly increasing`() {
        val options = BlockPolicy.DAILY_LIMIT_OPTIONS
        assertTrue(options.all { it > 0L })
        assertEquals(options.sorted(), options)
        assertEquals(options.distinct(), options)
    }
}
