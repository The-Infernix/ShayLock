package com.example.shaylock.core

/**
 * Single source of truth for the hard-coded blocking policy of this prototype.
 *
 * Everything the blocker knows about "what to block" and "how to unlock" lives here so the
 * accessibility watchdog, the overlay service and the setup UI can never drift apart.
 */
object BlockPolicy {

    /**
     * The hard-coded PIN that lifts the block and protects the app settings.
     *
     * PROTOTYPE LIMITATION: this value is compiled into the APK and can be recovered by anyone
     * who decompiles it. For a real product store a salted hash in the Android Keystore instead.
     */
    const val PIN: String = "2310"

    /** Number of digits the PIN must contain. */
    const val PIN_LENGTH: Int = 4

    /**
     * Pure check of a PIN entry.
     *
     * Kept free of any side effect so it can be unit tested: nothing here grants a session or
     * touches the persisted state.
     */
    fun isCorrectPin(code: String): Boolean =
        code.length == PIN_LENGTH && code == PIN

    /**
     * Packages that are blocked when a rule is active.
     *
     * [PRIMARY_PACKAGE] is Instagram proper; Instagram Lite is included because it is a
     * separate app id that would otherwise be an obvious bypass.
     */
    val BLOCKED_PACKAGES: Set<String> = setOf(
        "com.instagram.android",
        "com.instagram.lite",
    )

    /** The app the overlay is built around; used for the title shown on the lock screen. */
    const val PRIMARY_PACKAGE: String = "com.instagram.android"

    /**
     * How long a correct PIN keeps the blocked app accessible.
     *
     * Once the window elapses the watchdog re-arms the block automatically.
     */
    const val UNLOCK_SESSION_MS: Long = 5 * 60 * 1000L

    /**
     * How long the "Pause" action suspends the block.
     *
     * The pause is deliberately short and PIN-gated: it is an escape hatch, not a way to disable
     * protection for the rest of the day.
     */
    const val PAUSE_DURATION_MS: Long = 15 * 60 * 1000L

    /**
     * How many PIN-approved extensions (a 5-minute unlock or a 15-minute pause) are allowed
     * per day. Once all of them are used, the block cannot be lifted again until midnight.
     */
    const val MAX_PIN_EXTENSIONS_PER_DAY: Int = 3

    /** True while another PIN extension is still available today. */
    fun canUsePinExtension(usedToday: Int): Boolean =
        usedToday in 0 until MAX_PIN_EXTENSIONS_PER_DAY

    /** Convenience predicate used by the watchdog. */
    fun isBlockedPackage(packageName: String?): Boolean =
        packageName != null && BLOCKED_PACKAGES.contains(packageName)

    /**
     * Selectable daily allowances offered in the setup UI, in milliseconds.
     *
     * The chosen value is fixed: it stays exactly as selected until it is changed again with the
     * PIN. Only the usage counter is reset, and that happens automatically at local midnight.
     */
    val DAILY_LIMIT_OPTIONS: List<Long> = listOf(
        10 * 1000L, // Test option: the allowance runs out after 10 seconds.
        5 * 60 * 1000L,
        15 * 60 * 1000L,
        30 * 60 * 1000L,
        45 * 60 * 1000L,
        60 * 60 * 1000L,
    )

    /** The allowance that is offered by default when nothing is configured yet. */
    val DEFAULT_DAILY_LIMIT_MS: Long = 30 * 60 * 1000L
}
