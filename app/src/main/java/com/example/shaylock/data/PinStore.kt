package com.example.shaylock.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.shaylock.core.BlockPolicy
import com.example.shaylock.core.PinHasher

/**
 * Stores the user-defined PIN as a salted PBKDF2 hash.
 *
 * The PIN itself is never written to disk. A fresh random salt is generated every time the PIN
 * is changed, so two devices that happen to choose the same PIN still store different hashes.
 *
 * For backwards compatibility with ShayLock 1.x the well-known PIN `2310` is seeded on first
 * launch, but [isUsingDefaultPin] stays true so the dashboard can nag the user to replace it.
 */
class PinStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        ensureSeeded()
    }

    /** True while the legacy `2310` PIN is still in use. */
    val isUsingDefaultPin: Boolean
        get() = !prefs.getBoolean(KEY_CUSTOM_PIN, false)

    /** Verifies [pin] against the stored salt and hash in constant time. */
    fun verify(pin: String): Boolean {
        if (!PinHasher.isValidPin(pin)) return false

        val salt = prefs.getString(KEY_SALT, null)
            ?.let { runCatching { PinHasher.decode(it) }.getOrNull() }
            ?: return false
        val expected = prefs.getString(KEY_HASH, null)
            ?.let { runCatching { PinHasher.decode(it) }.getOrNull() }
            ?: return false

        return PinHasher.verify(pin, salt, expected)
    }

    /** Replaces the PIN with [newPin] and marks it as user-defined. */
    fun setPin(newPin: String) {
        require(PinHasher.isValidPin(newPin)) { "The PIN must contain ${BlockPolicy.PIN_LENGTH} digits" }

        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(newPin, salt)
        prefs.edit {
            putString(KEY_SALT, PinHasher.encode(salt))
            putString(KEY_HASH, PinHasher.encode(hash))
            putBoolean(KEY_CUSTOM_PIN, true)
        }
    }

    private fun ensureSeeded() {
        if (prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)) return

        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(BlockPolicy.DEFAULT_PIN, salt)
        prefs.edit {
            putString(KEY_SALT, PinHasher.encode(salt))
            putString(KEY_HASH, PinHasher.encode(hash))
            putBoolean(KEY_CUSTOM_PIN, false)
        }
    }

    private companion object {
        const val PREFS_NAME = "shaylock_security"
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
        const val KEY_CUSTOM_PIN = "pin_is_custom"
    }
}
