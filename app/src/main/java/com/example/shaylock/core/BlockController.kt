package com.example.shaylock.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Process-wide source of truth for the blocking state.
 *
 * The accessibility watchdog, the overlay service and the setup UI all live in the same process,
 * so a single object holding the state keeps them perfectly in sync without any IPC.
 *
 * The model is a *daily allowance*: the user picks how much time the blocked apps may be used
 * per day, the watchdog measures how much of that allowance is actually consumed and, once the
 * allowance is used up, the blocker activates until the next day.
 *
 * Every change to the allowance - including switching the limiter off - requires the PIN.
 *
 * State is persisted to [SharedPreferences] because the allowance, today's usage and an in-flight
 * unlock session must survive the app being killed.
 */
object BlockController {

    private const val TAG = "BlockController"
    private const val PREFS_NAME = "shaylock_blocker"

    private const val KEY_LIMIT_ENABLED = "limit_enabled"
    private const val KEY_LIMIT_MS = "limit_ms"
    private const val KEY_USED_MS = "used_ms"
    private const val KEY_DAY_START = "day_start"
    private const val KEY_USAGE_SINCE = "usage_since"
    private const val KEY_SESSION_END = "session_end"
    private const val KEY_PIN_EXTENSIONS_USED = "pin_extensions_used"

    /** How often the running foreground time is written to disk. */
    private const val CHECKPOINT_INTERVAL_MS = 5_000L

    /** Long lived scope for the internal clock ticker. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var prefs: SharedPreferences

    private val _limit = MutableStateFlow(DailyLimit())
    private val _sessionEndWallClock = MutableStateFlow(0L)
    private val _pinExtensionsUsed = MutableStateFlow(0)
    private val _now = MutableStateFlow(System.currentTimeMillis())

    /** Wall clock at which the current foreground session of a blocked app started; 0 = idle. */
    private val _usageSince = MutableStateFlow(0L)

    /** The app that was last reported as being in the foreground. */
    private val _foregroundPackage = MutableStateFlow<String?>(null)

    /** True while the lock screen covers the blocked app, so no usage may be counted. */
    private val _usageSuppressed = MutableStateFlow(false)

    /** The configured daily allowance together with today's committed usage. */
    val dailyLimit: StateFlow<DailyLimit> = _limit.asStateFlow()

    /** Time consumed today, including the currently running foreground session. */
    val usedTodayMs: StateFlow<Long> = combine(_limit, _usageSince, _now) { limit, since, now ->
        limit.usedMs + if (since > 0L) (now - since).coerceAtLeast(0L) else 0L
    }.stateIn(scope, SharingStarted.Eagerly, 0L)

    /** Milliseconds of today's allowance that are still available. */
    val limitRemainingMs: StateFlow<Long> = combine(_limit, usedTodayMs) { limit, used ->
        (limit.limitMs - used).coerceAtLeast(0L)
    }.stateIn(scope, SharingStarted.Eagerly, 0L)

    /** True when today's allowance is used up. */
    val isLimitReached: StateFlow<Boolean> = combine(_limit, usedTodayMs) { limit, used ->
        limit.isReached(used)
    }.stateIn(scope, SharingStarted.Eagerly, false)

    /** Milliseconds left in the PIN-approved unlock session; 0 when no session is running. */
    val sessionRemainingMs: StateFlow<Long> = combine(_sessionEndWallClock, _now) { end, now ->
        if (end <= 0L) 0L else (end - now).coerceAtLeast(0L)
    }.stateIn(scope, SharingStarted.Eagerly, 0L)

    /** Number of PIN-approved extensions used today. */
    val pinExtensionsUsedToday: StateFlow<Int> = _pinExtensionsUsed.asStateFlow()

    /**
     * True when the blocked apps must currently be blocked.
     *
     * Blocking is effective when today's allowance is used up *and* no unlock session is running.
     */
    val isBlockingEffective: StateFlow<Boolean> =
        combine(isLimitReached, _sessionEndWallClock, _now) { reached, sessionEnd, now ->
            reached && (sessionEnd <= 0L || now >= sessionEnd)
        }.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        // Ticks once a second so countdowns stay accurate without every caller owning a timer.
        scope.launch {
            var lastCheckpoint = 0L
            while (true) {
                val now = System.currentTimeMillis()
                _now.value = now
                rolloverIfNeeded(now)
                if (_usageSince.value > 0L && now - lastCheckpoint >= CHECKPOINT_INTERVAL_MS) {
                    lastCheckpoint = now
                    commitUsage(now)
                }
                delay(1_000L)
            }
        }
    }

    /**
     * Loads persisted state. Safe to call repeatedly; only the first call has an effect.
     */
    fun initialize(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val now = System.currentTimeMillis()
        val stored = DailyLimit(
            isEnabled = prefs.getBoolean(KEY_LIMIT_ENABLED, false),
            limitMs = prefs.getLong(KEY_LIMIT_MS, 0L),
            usedMs = prefs.getLong(KEY_USED_MS, 0L),
            dayStartWallClock = prefs.getLong(KEY_DAY_START, 0L),
        )
        val rolled = stored.rolledToDay(now)
        _limit.value = rolled

        // The three PIN extensions are per calendar day, just like the usage counter.
        val storedExtensions = prefs.getInt(KEY_PIN_EXTENSIONS_USED, 0)
        _pinExtensionsUsed.value =
            if (stored.dayStartWallClock == rolled.dayStartWallClock) {
                storedExtensions.coerceIn(0, BlockPolicy.MAX_PIN_EXTENSIONS_PER_DAY)
            } else {
                0
            }

        // A running foreground session survives a process death: its start time was persisted.
        val storedSince = prefs.getLong(KEY_USAGE_SINCE, 0L)
        _usageSince.value =
            if (storedSince > 0L) maxOf(storedSince, rolled.dayStartWallClock) else 0L

        val storedSessionEnd = prefs.getLong(KEY_SESSION_END, 0L)
        if (storedSessionEnd in 1 until now) {
            // The session expired while the process was dead - the block is active again.
            _sessionEndWallClock.value = 0L
            prefs.edit { putLong(KEY_SESSION_END, 0L) }
        } else {
            _sessionEndWallClock.value = storedSessionEnd
        }

        // Save the day rollover (and the extension reset) even before the first usage event.
        persist()
    }

    // ----------------------------------------------------------------- usage tracking

    /**
     * Called by the watchdog whenever the foreground app changes.
     *
     * Only a blocked app that is really on screen consumes the allowance; the counter is paused
     * as soon as the user leaves the app and resumes when they come back.
     */
    fun onForegroundAppChanged(packageName: String?) {
        _foregroundPackage.value = packageName
        val now = _now.value
        if (shouldCountUsage(packageName)) startCounting(now) else stopCounting(now)
    }

    /**
     * Called when the lock screen covers (or uncovers) the blocked app.
     *
     * While the lock screen is up the user is not looking at the blocked app, so that time must
     * not be charged to the daily allowance.
     */
    fun setUsageCountingSuppressed(suppressed: Boolean) {
        if (_usageSuppressed.value == suppressed) return
        _usageSuppressed.value = suppressed
        val now = _now.value
        if (suppressed) {
            stopCounting(now)
        } else if (shouldCountUsage(_foregroundPackage.value)) {
            startCounting(now)
        }
    }

    private fun shouldCountUsage(packageName: String?): Boolean =
        _limit.value.isEnabled &&
            !_usageSuppressed.value &&
            BlockPolicy.isBlockedPackage(packageName)

    private fun startCounting(now: Long) {
        if (_usageSince.value > 0L) return
        _usageSince.value = now
        persist()
        Log.d(TAG, "Usage counting started (${_limit.value.usedMs} ms committed so far)")
    }

    private fun stopCounting(now: Long) {
        if (_usageSince.value <= 0L) return
        commitUsage(now)
        _usageSince.value = 0L
        persist()
        Log.d(TAG, "Usage counting paused (${_limit.value.usedMs} ms committed)")
    }

    /** Moves the running foreground time into the committed usage counter. */
    private fun commitUsage(now: Long) {
        val since = _usageSince.value
        if (since <= 0L) return
        val elapsed = (now - since).coerceAtLeast(0L)
        val limit = _limit.value
        _limit.value = limit.copy(usedMs = limit.usedMs + elapsed)
        _usageSince.value = now
        persist()
    }

    /** Resets the usage counter when the calendar day changed. */
    private fun rolloverIfNeeded(now: Long) {
        val current = _limit.value
        val rolled = current.rolledToDay(now)
        if (rolled === current) return

        _limit.value = rolled
        _pinExtensionsUsed.value = 0
        // A session that started before midnight only counts from the new day onwards.
        val since = _usageSince.value
        if (since > 0L) _usageSince.value = maxOf(since, rolled.dayStartWallClock)
        persist()
        Log.i(TAG, "New day - today's usage and PIN extension counters reset")
    }

    // ----------------------------------------------------------------- PIN gated actions

    /**
     * Checks the PIN without granting anything.
     *
     * Used by every setting that the user must not be able to change without the PIN.
     */
    fun authorize(pin: String): Boolean = matchesPin(pin)

    /**
     * Validates a PIN entry for the lock screen.
     *
     * @return true when the PIN matched, in which case a five-minute unlock session was started.
     */
    fun submitPin(code: String): Boolean {
        if (!matchesPin(code)) return false
        // A session is already running (a double tap or a stale overlay): never consume another.
        if (_sessionEndWallClock.value > System.currentTimeMillis()) return true
        if (!grantPinExtension(BlockPolicy.UNLOCK_SESSION_MS)) return false

        Log.i(TAG, "Correct PIN - unlock session granted")
        return true
    }

    private fun matchesPin(code: String): Boolean {
        val accepted = BlockPolicy.isCorrectPin(code)
        Log.i(TAG, "PIN check for ${code.length} digits -> ${if (accepted) "accepted" else "rejected"}")
        return accepted
    }

    /**
     * True when the daily allowance has already been configured at least once.
     *
     * The very first setup is deliberately free so the blocker can be armed without knowing the
     * PIN; every later change (including re-arming after switching it off) needs the PIN.
     */
    fun isPinRequiredToSetLimit(): Boolean =
        _limit.value.isEnabled || _limit.value.limitMs > 0L

    /**
     * Sets the daily allowance and switches the limiter on.
     *
     * The allowance stays exactly as set until it is changed again with the PIN.
     *
     * @param pin the PIN; null is only accepted while no allowance has been configured yet.
     * @return true when the allowance was applied (either first setup or a correct PIN).
     */
    fun applyDailyLimit(limitMs: Long, pin: String?): Boolean {
        val firstSetup = !isPinRequiredToSetLimit()
        if (!firstSetup && (pin == null || !authorize(pin))) return false

        val rolled = _limit.value.rolledToDay(_now.value)
        _limit.value = rolled.copy(isEnabled = true, limitMs = limitMs.coerceAtLeast(0L))
        persist()
        Log.i(TAG, "Daily allowance set to ${_limit.value.limitMs} ms (first setup=$firstSetup)")
        return true
    }

    /**
     * Switches the limiter on or off.
     *
     * Switching it off requires the PIN as well - there is no free way to disable the blocker.
     *
     * @return true when the PIN was correct and the limiter was switched.
     */
    fun setLimiterEnabled(enabled: Boolean, pin: String): Boolean {
        if (!authorize(pin)) return false

        if (enabled) {
            _limit.value = _limit.value.copy(isEnabled = true)
        } else {
            stopCounting(_now.value)
            _limit.value = _limit.value.copy(isEnabled = false)
            endSession()
        }
        persist()
        Log.i(TAG, "Limiter enabled=$enabled")
        return true
    }

    // ----------------------------------------------------------------- unlock session

    /** Starts the temporary unlock session that lifts the block for [durationMs]. */
    private fun grantUnlockSession(durationMs: Long) {
        val end = System.currentTimeMillis() + durationMs
        _sessionEndWallClock.value = end
        prefs.edit { putLong(KEY_SESSION_END, end) }
    }

    /**
     * Temporarily suspends blocking for [durationMs] after verifying the PIN.
     *
     * The pause reuses the unlock session, so usage keeps counting and the block returns as soon
     * as the session ends and the allowance is still exhausted.
     *
     * @return true when the PIN was correct and the pause was granted.
     */
    fun pauseFor(durationMs: Long, pin: String): Boolean {
        if (!authorize(pin)) return false
        if (_sessionEndWallClock.value > System.currentTimeMillis()) return true
        if (!grantPinExtension(durationMs)) return false

        Log.i(TAG, "Pause granted for $durationMs ms")
        return true
    }

    /**
     * Grants one of today's three PIN extensions and starts the session.
     *
     * Both the five-minute unlock on the lock screen and the fifteen-minute pause on the
     * dashboard consume one extension, so the PIN cannot be used to keep resetting the timer.
     */
    private fun grantPinExtension(durationMs: Long): Boolean {
        val used = _pinExtensionsUsed.value
        if (!BlockPolicy.canUsePinExtension(used)) {
            Log.i(TAG, "No PIN extensions left today")
            return false
        }

        _pinExtensionsUsed.value = used + 1
        grantUnlockSession(durationMs)
        persist()
        Log.i(
            TAG,
            "PIN extension granted (${_pinExtensionsUsed.value}/" +
                "${BlockPolicy.MAX_PIN_EXTENSIONS_PER_DAY} used today)",
        )
        return true
    }

    /** True when at least one of today's three PIN extensions is still available. */
    fun hasPinExtensionsLeft(): Boolean =
        BlockPolicy.canUsePinExtension(_pinExtensionsUsed.value)

    /** Immediately terminates any running unlock session, re-arming the block. */
    fun endSession() {
        _sessionEndWallClock.value = 0L
        prefs.edit { putLong(KEY_SESSION_END, 0L) }
    }

    /** Convenience snapshot for callers that do not observe flows. */
    fun isUnlockSessionActive(): Boolean = sessionRemainingMs.value > 0L

    private fun persist() {
        if (!::prefs.isInitialized) return
        val limit = _limit.value
        prefs.edit {
            putBoolean(KEY_LIMIT_ENABLED, limit.isEnabled)
            putLong(KEY_LIMIT_MS, limit.limitMs)
            putLong(KEY_USED_MS, limit.usedMs)
            putLong(KEY_DAY_START, limit.dayStartWallClock)
            putLong(KEY_USAGE_SINCE, _usageSince.value)
            putInt(KEY_PIN_EXTENSIONS_USED, _pinExtensionsUsed.value)
        }
    }
}
