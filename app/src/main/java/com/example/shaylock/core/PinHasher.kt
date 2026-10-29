package com.example.shaylock.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Pure PIN hashing helpers, kept free of Android APIs so they can be unit tested on the JVM.
 *
 * PBKDF2-HMAC-SHA256 with a random 16-byte salt and 120 000 iterations makes a brute-force
 * attack on a 4-digit PIN expensive while staying fast enough (~100 ms) for a keypad tap.
 */
object PinHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    /** A cryptographically random salt for a new PIN. */
    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    /** Derives the salted hash of [pin]. */
    fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time comparison of [pin] against [expected]. */
    fun verify(pin: String, salt: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(hash(pin, salt), expected)

    /** Base64 encoding used by the preference store. */
    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    /** Base64 decoding used by the preference store. */
    fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    /** True when [pin] is a valid PIN: exactly [BlockPolicy.PIN_LENGTH] decimal digits. */
    fun isValidPin(pin: String): Boolean =
        pin.length == BlockPolicy.PIN_LENGTH && pin.all { it.isDigit() }
}
