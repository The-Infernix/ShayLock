package com.example.shaylock.core

/**
 * Shared, hard-coded limits of the blocking model.
 *
 * The set of protected apps is no longer part of this policy: it lives in Room and is configured
 * by the user. What remains here are the rules that must not be configurable from the UI.
 */
object BlockPolicy {

    /**
     * The legacy PIN. It is only used to seed [com.example.shaylock.data.PinStore] on first
     * launch so an existing 1.x install keeps working; users are asked to replace it.
     */
    const val DEFAULT_PIN: String = "2310"

    /** Number of digits the PIN must contain. */
    const val PIN_LENGTH: Int = 4

    /** A PIN override keeps one app usable for five minutes. */
    const val OVERRIDE_DURATION_MS: Long = 5 * 60 * 1000L

    /**
     * Strict cap on PIN overrides. The counter is independent per app and resets at midnight,
     * so using three overrides on Instagram does not consume TikTok's allowance.
     */
    const val MAX_OVERRIDES_PER_APP_PER_DAY: Int = 3

    /**
     * How often an app's daily allowance may be changed per calendar day once the blocker has
     * been armed. The very first configuration is free.
     */
    const val MAX_LIMIT_CHANGES_PER_APP_PER_DAY: Int = 1

    /** True while another override is available for an app that already used [used]. */
    fun canOverride(used: Int): Boolean =
        used in 0 until MAX_OVERRIDES_PER_APP_PER_DAY

    /**
     * Selectable daily allowances in milliseconds.
     *
     * The chosen value is fixed until it is changed again with the PIN. Only the usage counter
     * is reset, and that happens automatically at local midnight.
     */
    val DAILY_LIMIT_OPTIONS: List<Long> = listOf(
        10 * 1000L, // Test option: the allowance runs out after 10 seconds.
        5 * 60 * 1000L,
        15 * 60 * 1000L,
        30 * 60 * 1000L,
        45 * 60 * 1000L,
        60 * 60 * 1000L,
    )

    /** The allowance a newly added app starts with. */
    val DEFAULT_DAILY_LIMIT_MS: Long = 30 * 60 * 1000L

    /**
     * Packages that were hard-coded in 1.x. They are only referenced by the one-time migration
     * and by the optional instagram.com-in-Chrome web block.
     */
    val INSTAGRAM_PACKAGES: Set<String> = setOf(
        "com.instagram.android",
        "com.instagram.lite",
    )

    /** True when [packageName] belongs to one of the legacy Instagram apps. */
    fun isInstagramPackage(packageName: String?): Boolean =
        packageName != null && packageName in INSTAGRAM_PACKAGES
}
