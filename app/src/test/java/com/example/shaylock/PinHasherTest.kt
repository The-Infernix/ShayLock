package com.example.shaylock

import com.example.shaylock.core.BlockPolicy
import com.example.shaylock.core.PinHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the salted PBKDF2 PIN hashing used by the security store.
 */
class PinHasherTest {

    @Test
    fun `a pin must contain exactly four digits`() {
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("0000"))
        assertEquals(BlockPolicy.PIN_LENGTH, 4)

        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("12345"))
        assertFalse(PinHasher.isValidPin("12a4"))
        assertFalse(PinHasher.isValidPin(" 123"))
        assertFalse(PinHasher.isValidPin(""))
    }

    @Test
    fun `the correct pin verifies against its hash`() {
        val salt = PinHasher.newSalt()
        val expected = PinHasher.hash("2468", salt)

        assertTrue(PinHasher.verify("2468", salt, expected))
    }

    @Test
    fun `a wrong pin does not verify`() {
        val salt = PinHasher.newSalt()
        val expected = PinHasher.hash("2468", salt)

        assertFalse(PinHasher.verify("2469", salt, expected))
    }

    @Test
    fun `a different salt produces a different hash for the same pin`() {
        val first = PinHasher.hash("2468", PinHasher.newSalt())
        val second = PinHasher.hash("2468", PinHasher.newSalt())

        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun `base64 encoding round-trips the salt and hash bytes`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("1357", salt)

        assertEquals(salt.toList(), PinHasher.decode(PinHasher.encode(salt)).toList())
        assertEquals(hash.toList(), PinHasher.decode(PinHasher.encode(hash)).toList())
    }
}
