package com.example.shaylock.core

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.edit
import com.example.shaylock.data.PinStore
import com.example.shaylock.data.ShayLockDatabase
import com.example.shaylock.data.TrackedAppDao
import com.example.shaylock.data.TrackedAppEntity
import com.example.shaylock.data.toDomain
import com.example.shaylock.data.toEntity
import com.example.shaylock.util.AppLabels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide source of truth for the multi-app blocking state.
 *
 * The accessibility watchdog, the overlay service and the Compose UI all live in the same
 * process, so one object holding the state keeps them perfectly in sync without IPC.
 *
 * The model is per package: every protected app has its own [DailyLimit] (allowance + committed
 * usage), its own three five-minute PIN overrides and its own daily limit-change cooldown.
 *
 * Per-app rows are persisted to Room. The few genuinely global values (blocker switch, PIN hash
 * and the in-flight usage timer) live in [SharedPreferences] because they are read before the
 * database is opened.
 */
object BlockController {

    private const val TAG = "BlockController"
    private const val PREFS_NAME = "shaylock_blocker"

    /** Reuses the 1.x key so an existing install keeps its armed/disabled state. */
    private const val KEY_BLOCKER_ENABLED = "limit_enabled"
    private const val KEY_USAGE_SINCE = "usage_since"
    private const val KEY_FOREGROUND_PACKAGE = "foreground_package"
    private const val KEY_HAS_BEEN_ARMED = "has_been_armed_v2"
    private const val KEY_MIGRATED_LEGACY = "migrated_legacy_v2"

    /** Legacy 1.x keys, read once by the migration. */
    private const val LEGACY_KEY_LIMIT_MS = "limit_ms"
    private const val LEGACY_KEY_USED_MS = "used_ms"
    private const val LEGACY_KEY_DAY_START = "day_start"
    private const val LEGACY_KEY_SESSION_END = "session_end"
    private const val LEGACY_KEY_PIN_EXTENSIONS_USED = "pin_extensions_used"

    /** How often the running foreground time is written to disk. */
    private const val CHECKPOINT_INTERVAL_MS = 5_000L

    /** Long-lived scope for the internal clock ticker and database writes. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Serialises database snapshots so a slower write can never overwrite a newer one. */
    private val persistMutex = Mutex()

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var dao: TrackedAppDao
    private lateinit var pinStore: PinStore

    private val _apps = MutableStateFlow<List<TrackedApp>>(emptyList())
    private val _blockerEnabled = MutableStateFlow(false)
    private val _hasBeenArmed = MutableStateFlow(false)
    private val _usingDefaultPin = MutableStateFlow(true)
    private val _now = MutableStateFlow(System.currentTimeMillis())

    /** The package that was last reported as being in the foreground. */
    private val _foregroundPackage = MutableStateFlow<String?>(null)

    /** The package whose foreground time is currently being measured. */
    private val _countingPackage = MutableStateFlow<String?>(null)

    /** Wall clock at which the current measurement started; 0 = not counting. */
    private val _usageSince = MutableStateFlow(0L)

    /** True while the lock screen covers a protected app, so no usage may be counted. */
    private val _usageSuppressed = MutableStateFlow(false)

    @Volatile
    private var initialized = false

    /** All protected apps in display order. */
    val apps: StateFlow<List<TrackedApp>> = _apps.asStateFlow()

    /** True while the global blocker switch is on. */
    val isBlockerEnabled: StateFlow<Boolean> = _blockerEnabled.asStateFlow()

    /** True once the user has armed protection at least once. */
    val hasBeenArmed: StateFlow<Boolean> = _hasBeenArmed.asStateFlow()

    /** True while the legacy `2310` PIN is still in use. */
    val isUsingDefaultPin: StateFlow<Boolean> = _usingDefaultPin.asStateFlow()

    private data class SettingsSnapshot(
        val blockerEnabled: Boolean,
        val hasBeenArmed: Boolean,
    )

    private data class SessionSnapshot(
        val foregroundPackage: String?,
        val countingPackage: String?,
        val usageSinceWallClock: Long,
        val now: Long,
    )

    private val settingsSnapshot = combine(_blockerEnabled, _hasBeenArmed) { enabled, armed ->
        SettingsSnapshot(enabled, armed)
    }

    private val sessionSnapshot = combine(
        _foregroundPackage,
        _countingPackage,
        _usageSince,
        _now,
    ) { foreground, counting, since, now ->
        SessionSnapshot(foreground, counting, since, now)
    }

    /**
     * Live per-app state: committed usage merged with the currently running foreground session,
     * plus the block and override status at this instant.
     */
    val appStates: StateFlow<List<TrackedAppState>> = combine(
        _apps,
        settingsSnapshot,
        sessionSnapshot,
    ) { apps, settings, session ->
        apps.map { app ->
            val liveMs = if (session.countingPackage == app.packageName &&
                session.usageSinceWallClock > 0L
            ) {
                (session.now - session.usageSinceWallClock).coerceAtLeast(0L)
            } else {
                0L
            }
            val used = app.usedMs + liveMs
            val overrideRemaining = app.overrideRemainingMs(session.now)
            val reached = app.isLimitReached(used)

            TrackedAppState(
                app = app,
                usedMs = used,
                remainingMs = (app.limitMs - used).coerceAtLeast(0L),
                usedFraction = app.usedFraction(used),
                isLimitReached = reached,
                isBlocked = settings.blockerEnabled && reached && overrideRemaining <= 0L,
                overrideRemainingMs = overrideRemaining,
                overridesRemainingToday = app.overridesRemainingToday,
                canChangeLimit = !settings.hasBeenArmed || app.canChangeLimitToday(),
                isForeground = session.foregroundPackage == app.packageName,
                isBlockerEnabled = settings.blockerEnabled,
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Packages that must be covered by the strict lock screen right now. */
    val blockedPackages: StateFlow<Set<String>> = appStates
        .map { states -> states.filter { it.isBlocked }.map { it.packageName }.toSet() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return

        val application = context.applicationContext
        appContext = application
        prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        pinStore = PinStore(application)
        dao = ShayLockDatabase.get(application).trackedAppDao()

        val now = System.currentTimeMillis()

        // A handful of rows, read once at startup so the watchdog never observes empty state.
        val stored = runBlocking(Dispatchers.IO) { dao.getAll() }
        val migrated = migrateLegacyIfNeeded(application, stored, now)

        val loaded = (stored + listOfNotNull(migrated))
            .map { it.toDomain() }
            .sortedWith(compareBy<TrackedApp> { it.sortOrder }.thenBy { it.label })
            .map { it.rolledToDay(now) }
            .map { app ->
                if (app.overrideSessionEndWallClock in 1 until now) {
                    app.copy(overrideSessionEndWallClock = 0L)
                } else {
                    app
                }
            }

        _apps.value = loaded
        _blockerEnabled.value = prefs.getBoolean(KEY_BLOCKER_ENABLED, false)
        _hasBeenArmed.value = prefs.getBoolean(KEY_HAS_BEEN_ARMED, _blockerEnabled.value)
        _usingDefaultPin.value = pinStore.isUsingDefaultPin
        _now.value = now

        // A running foreground session survives process death: its start time was persisted.
        val storedForeground = prefs.getString(KEY_FOREGROUND_PACKAGE, null)
        val storedSince = prefs.getLong(KEY_USAGE_SINCE, 0L)
        if (_blockerEnabled.value &&
            storedForeground != null &&
            storedSince > 0L &&
            loaded.any { it.packageName == storedForeground && it.limitMs > 0L }
        ) {
            val dayStart = DailyLimit.startOfDay(now)
            _foregroundPackage.value = storedForeground
            _countingPackage.value = storedForeground
            _usageSince.value = maxOf(storedSince, dayStart)
        }

        if (migrated != null) {
            runBlocking(Dispatchers.IO) { dao.insertAll(listOf(migrated)) }
        }

        initialized = true
        persistApps()
        persistSession()
        startTicker()
        Log.i(TAG, "Initialized with ${loaded.size} tracked app(s)")
    }

    // ----------------------------------------------------------------- app management

    /** True when [packageName] is currently protected. */
    fun isTracked(packageName: String?): Boolean =
        packageName != null && _apps.value.any { it.packageName == packageName }

    /** True when at least one Instagram package is protected (used by the Chrome web block). */
    fun isInstagramTracked(): Boolean =
        _apps.value.any { BlockPolicy.isInstagramPackage(it.packageName) }

    /** A point-in-time snapshot of one app, or null when it is not tracked. */
    fun trackedApp(packageName: String): TrackedApp? =
        _apps.value.firstOrNull { it.packageName == packageName }

    /** Adds [packageNames] with the default allowance and returns how many were added. */
    fun addApps(packageNames: Collection<String>): Int {
        if (!initialized) return 0

        val existing = _apps.value.map { it.packageName }.toSet()
        val additions = packageNames
            .filter { it.isNotBlank() && it != appContext.packageName && it !in existing }
            .distinct()
        if (additions.isEmpty()) return 0

        val now = _now.value
        val dayStart = DailyLimit.startOfDay(now)
        var order = (_apps.value.maxOfOrNull { it.sortOrder } ?: -1) + 1

        val newApps = additions.map { packageName ->
            TrackedApp(
                packageName = packageName,
                label = AppLabels.labelFor(appContext, packageName),
                dailyLimit = DailyLimit(
                    isEnabled = true,
                    limitMs = BlockPolicy.DEFAULT_DAILY_LIMIT_MS,
                    usedMs = 0L,
                    dayStartWallClock = dayStart,
                ),
                sortOrder = order++,
            )
        }

        _apps.value = (_apps.value + newApps).sortedWith(compareBy { it.sortOrder })
        persistApps()
        Log.i(TAG, "Added ${newApps.size} app(s): ${newApps.joinToString { it.packageName }}")
        return newApps.size
    }

    /**
     * Removes [packageName] from the protected list.
     *
     * Removal weakens protection, so it needs the PIN once the blocker has been armed. Before the
     * first arming there is nothing to weaken and no PIN is required.
     */
    fun removeApp(packageName: String, pin: String? = null): Boolean {
        if (!initialized || !isTracked(packageName)) return false
        if (_hasBeenArmed.value && (pin == null || !authorize(pin))) return false

        _apps.value = _apps.value.filterNot { it.packageName == packageName }
        if (_countingPackage.value == packageName) {
            _countingPackage.value = null
            _usageSince.value = 0L
        }
        if (_foregroundPackage.value == packageName) {
            _foregroundPackage.value = null
        }
        persistApps()
        persistSession()
        Log.i(TAG, "Removed $packageName")
        return true
    }

    /**
     * Changes the daily allowance of [packageName].
     *
     * The first configuration is free. Afterwards the PIN is required and the per-app cooldown
     * (one change per day) must still be available.
     */
    fun applyLimit(packageName: String, limitMs: Long, pin: String?): Boolean {
        if (limitMs <= 0L) return false
        val app = trackedApp(packageName) ?: return false

        if (_hasBeenArmed.value) {
            if (pin == null || !authorize(pin)) return false
            if (!app.canChangeLimitToday()) {
                Log.i(TAG, "Limit change rejected for $packageName - cooldown exhausted")
                return false
            }
            updateApp(packageName) { current ->
                current.copy(
                    dailyLimit = current.dailyLimit.copy(isEnabled = true, limitMs = limitMs),
                    limitChangesToday = current.limitChangesToday + 1,
                )
            }
        } else {
            updateApp(packageName) { current ->
                current.copy(
                    dailyLimit = current.dailyLimit.copy(isEnabled = true, limitMs = limitMs),
                )
            }
        }

        Log.i(TAG, "Allowance for $packageName set to $limitMs ms")
        return true
    }

    /** True when [packageName] can still have its allowance changed today. */
    fun canChangeLimit(packageName: String): Boolean {
        val app = trackedApp(packageName) ?: return false
        return !_hasBeenArmed.value || app.canChangeLimitToday()
    }

    /** True once changing settings requires the PIN. */
    fun isPinRequiredForSettings(): Boolean = _hasBeenArmed.value

    /**
     * Switches the global blocker on or off.
     *
     * Enabling is always free (it only strengthens protection). Disabling needs the PIN and ends
     * every running override session.
     */
    fun setBlockerEnabled(enabled: Boolean, pin: String? = null): Boolean {
        if (!initialized) return false

        if (enabled) {
            if (_apps.value.none { it.limitMs > 0L }) return false
            _blockerEnabled.value = true
            if (!_hasBeenArmed.value) _hasBeenArmed.value = true
        } else {
            if (pin == null || !authorize(pin)) return false
            stopCounting(_now.value)
            _blockerEnabled.value = false
            clearAllOverrides()
        }

        persistSession()
        persistApps()
        Log.i(TAG, "Blocker enabled=$enabled")
        return true
    }

    // ----------------------------------------------------------------- usage tracking

    /**
     * Called by the watchdog whenever the foreground app changes.
     *
     * Only a tracked app that is really on screen consumes its own allowance; the counter is
     * paused as soon as the user leaves the app and resumes when they come back.
     */
    fun onForegroundAppChanged(packageName: String?) {
        if (!initialized) return

        _foregroundPackage.value = packageName
        val now = _now.value
        if (shouldCountUsage(packageName)) {
            startCounting(packageName!!, now)
        } else {
            stopCounting(now)
        }
        persistSession()
    }

    /**
     * Called when the lock screen covers (or uncovers) a protected app.
     *
     * While the lock screen is up the user is not looking at the app, so that time must not be
     * charged to the daily allowance.
     */
    fun setUsageCountingSuppressed(suppressed: Boolean) {
        if (!initialized || _usageSuppressed.value == suppressed) return

        _usageSuppressed.value = suppressed
        val now = _now.value
        if (suppressed) {
            stopCounting(now)
        } else if (shouldCountUsage(_foregroundPackage.value)) {
            startCounting(_foregroundPackage.value!!, now)
        }
    }

    /** Synchronous block decision used by the accessibility watchdog. */
    fun shouldBlock(packageName: String?): Boolean {
        if (packageName == null || !_blockerEnabled.value) return false

        val app = trackedApp(packageName) ?: return false
        val now = _now.value
        if (app.overrideSessionEndWallClock > now) return false

        val liveMs = if (_countingPackage.value == packageName && _usageSince.value > 0L) {
            (now - _usageSince.value).coerceAtLeast(0L)
        } else {
            0L
        }
        return app.isLimitReached(app.usedMs + liveMs)
    }

    /**
     * True when the optional instagram.com-in-Chrome block applies to [packageName].
     *
     * The web block deliberately ignores the remaining allowance (it exists to stop a trivial
     * bypass) but respects the same per-app override window.
     */
    fun isWebBlockActive(packageName: String): Boolean {
        if (!_blockerEnabled.value) return false
        val app = trackedApp(packageName) ?: return false
        return !app.isOverrideActive(_now.value)
    }

    /** True while any app is inside a PIN-granted override window. */
    fun isAnyOverrideActive(): Boolean {
        val now = _now.value
        return _apps.value.any { it.overrideSessionEndWallClock > now }
    }

    private fun shouldCountUsage(packageName: String?): Boolean =
        packageName != null &&
            _blockerEnabled.value &&
            !_usageSuppressed.value &&
            _apps.value.any { it.packageName == packageName && it.limitMs > 0L }

    private fun startCounting(packageName: String, now: Long) {
        if (_countingPackage.value == packageName && _usageSince.value > 0L) return
        if (_countingPackage.value != null && _countingPackage.value != packageName) {
            stopCounting(now)
        }
        _countingPackage.value = packageName
        _usageSince.value = now
        persistSession()
    }

    private fun stopCounting(now: Long) {
        val packageName = _countingPackage.value ?: return
        commitUsage(packageName, now)
        _countingPackage.value = null
        _usageSince.value = 0L
        persistSession()
    }

    /** Moves the running foreground time into the app's committed usage counter. */
    private fun commitUsage(packageName: String, now: Long) {
        val since = _usageSince.value
        if (since <= 0L) return
        val elapsed = (now - since).coerceAtLeast(0L)
        if (elapsed <= 0L) return

        updateApp(packageName) { app ->
            app.copy(
                dailyLimit = app.dailyLimit.copy(usedMs = app.dailyLimit.usedMs + elapsed),
            )
        }
    }

    private fun startTicker() {
        // Ticks once a second so countdowns stay accurate without every caller owning a timer.
        scope.launch {
            var lastCheckpoint = _now.value
            while (true) {
                val now = System.currentTimeMillis()
                _now.value = now
                rolloverIfNeeded(now)

                if (_countingPackage.value != null &&
                    now - lastCheckpoint >= CHECKPOINT_INTERVAL_MS
                ) {
                    lastCheckpoint = now
                    val packageName = _countingPackage.value
                    if (packageName != null) {
                        commitUsage(packageName, now)
                        _usageSince.value = now
                        persistSession()
                    }
                }
                delay(1_000L)
            }
        }
    }

    /** Resets every per-day counter when the calendar day changed. */
    private fun rolloverIfNeeded(now: Long) {
        val current = _apps.value
        val rolled = current.map { it.rolledToDay(now) }
        if (rolled == current) return

        _apps.value = rolled
        val dayStart = DailyLimit.startOfDay(now)
        if (_usageSince.value in 1 until dayStart) _usageSince.value = dayStart
        persistApps()
        persistSession()
        Log.i(TAG, "New day - usage, overrides and limit-change cooldowns reset")
    }

    // ----------------------------------------------------------------- PIN and overrides

    /** Checks the PIN without granting anything. */
    fun authorize(pin: String): Boolean {
        if (!::pinStore.isInitialized) return false
        val accepted = runCatching { pinStore.verify(pin) }.getOrDefault(false)
        Log.i(TAG, "PIN check for ${pin.length} digits -> ${if (accepted) "accepted" else "rejected"}")
        return accepted
    }

    /**
     * Validates a PIN entry on the lock screen and starts a five-minute override for one app.
     *
     * @return true when the PIN was correct and the app is (or already was) unlocked.
     */
    fun submitPin(packageName: String, code: String): Boolean {
        if (!authorize(code)) return false

        val app = trackedApp(packageName) ?: return false
        val now = _now.value
        if (app.overrideSessionEndWallClock > now) return true
        if (!BlockPolicy.canOverride(app.overridesUsed)) {
            Log.i(TAG, "No overrides left today for $packageName")
            return false
        }
        // Nothing to unlock if the app is not currently blocked.
        if (!shouldBlock(packageName)) return true

        updateApp(packageName) { current ->
            current.copy(
                overridesUsed = current.overridesUsed + 1,
                overrideSessionEndWallClock = now + BlockPolicy.OVERRIDE_DURATION_MS,
            )
        }
        Log.i(
            TAG,
            "Override granted for $packageName (" +
                "${app.overridesUsed + 1}/${BlockPolicy.MAX_OVERRIDES_PER_APP_PER_DAY} used today)",
        )
        return true
    }

    /** True when [packageName] still has an override available today. */
    fun canOverride(packageName: String): Boolean {
        val app = trackedApp(packageName) ?: return false
        return BlockPolicy.canOverride(app.overridesUsed)
    }

    /** Immediately ends the override for [packageName], re-arming its block. */
    fun endOverride(packageName: String) {
        updateApp(packageName) { app ->
            if (app.overrideSessionEndWallClock == 0L) app else app.copy(overrideSessionEndWallClock = 0L)
        }
    }

    /**
     * Stores a new PIN. The caller must already have verified the current PIN through
     * [authorize]; this keeps the expensive PBKDF2 check to a single pass per dialog.
     */
    fun setPinAfterAuthorization(newPin: String): Boolean {
        if (!::pinStore.isInitialized) return false
        if (!PinHasher.isValidPin(newPin)) return false

        val result = runCatching { pinStore.setPin(newPin) }
        result.onFailure { Log.e(TAG, "Unable to store the new PIN", it) }
        if (result.isSuccess) {
            _usingDefaultPin.value = false
            Log.i(TAG, "Custom PIN stored")
        }
        return result.isSuccess
    }

    /** Reset every per-day counter for testing. */
    fun debugResetDailyLimits() {
        if (!initialized) return
        val now = _now.value
        _apps.value = _apps.value.map {
            it.copy(
                overridesUsed = 0,
                overrideSessionEndWallClock = 0L,
                limitChangesToday = 0,
            )
        }
        if (_countingPackage.value != null) _usageSince.value = now
        persistApps()
        persistSession()
        Log.i(TAG, "Debug reset of overrides and limit-change cooldowns")
    }

    // ----------------------------------------------------------------- persistence

    private fun updateApp(packageName: String, transform: (TrackedApp) -> TrackedApp): TrackedApp? {
        val current = _apps.value
        val index = current.indexOfFirst { it.packageName == packageName }
        if (index < 0) return null

        val updated = transform(current[index])
        _apps.value = current.toMutableList().also { it[index] = updated }
        persistApps()
        return updated
    }

    private fun clearAllOverrides() {
        if (_apps.value.none { it.overrideSessionEndWallClock > 0L }) return
        _apps.value = _apps.value.map { it.copy(overrideSessionEndWallClock = 0L) }
        persistApps()
    }

    /** Writes the current app list to Room. Snapshots are serialised and taken inside the lock. */
    private fun persistApps() {
        if (!initialized) return
        scope.launch(Dispatchers.IO) {
            persistMutex.withLock {
                dao.replaceAll(_apps.value.map { it.toEntity() })
            }
        }
    }

    /** Writes the few global values to SharedPreferences. */
    private fun persistSession() {
        if (!initialized) return
        prefs.edit {
            putBoolean(KEY_BLOCKER_ENABLED, _blockerEnabled.value)
            putBoolean(KEY_HAS_BEEN_ARMED, _hasBeenArmed.value)
            putLong(KEY_USAGE_SINCE, _usageSince.value)
            val foreground = _foregroundPackage.value
            if (foreground == null) {
                remove(KEY_FOREGROUND_PACKAGE)
            } else {
                putString(KEY_FOREGROUND_PACKAGE, foreground)
            }
        }
    }

    /**
     * One-time import of the 1.x single-app configuration.
     *
     * The old install blocked Instagram and Instagram Lite with one shared timer. Only the
     * primary package is migrated so the usage counter is not double-counted; the user can add
     * Lite again from the app picker.
     */
    private fun migrateLegacyIfNeeded(
        context: Context,
        stored: List<TrackedAppEntity>,
        now: Long,
    ): TrackedAppEntity? {
        if (prefs.getBoolean(KEY_MIGRATED_LEGACY, false)) return null
        prefs.edit { putBoolean(KEY_MIGRATED_LEGACY, true) }

        if (stored.isNotEmpty()) return null

        val legacyLimitMs = prefs.getLong(LEGACY_KEY_LIMIT_MS, 0L)
        if (legacyLimitMs <= 0L) return null

        val packageName = BlockPolicy.INSTAGRAM_PACKAGES.firstOrNull { isInstalled(context, it) }
            ?: return null

        val dayStart = prefs.getLong(LEGACY_KEY_DAY_START, 0L)
            .takeIf { it > 0L }
            ?: DailyLimit.startOfDay(now)
        val usedMs = prefs.getLong(LEGACY_KEY_USED_MS, 0L)
        val sessionEnd = prefs.getLong(LEGACY_KEY_SESSION_END, 0L).takeIf { it > now } ?: 0L
        val overridesUsed = prefs.getInt(LEGACY_KEY_PIN_EXTENSIONS_USED, 0)
            .coerceIn(0, BlockPolicy.MAX_OVERRIDES_PER_APP_PER_DAY)

        val legacyEnabled = prefs.getBoolean(KEY_BLOCKER_ENABLED, false)
        prefs.edit { putBoolean(KEY_HAS_BEEN_ARMED, legacyEnabled) }

        Log.i(TAG, "Migrating the 1.x configuration to $packageName")
        return TrackedAppEntity(
            packageName = packageName,
            label = AppLabels.labelFor(context, packageName),
            dailyLimitMs = legacyLimitMs,
            usedMs = usedMs,
            dayStartWallClock = dayStart,
            overridesUsed = overridesUsed,
            overrideSessionEndWallClock = sessionEnd,
            limitChangesUsed = 0,
            sortOrder = 0,
        )
    }

    private fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    }.getOrElse { error ->
        if (error is PackageManager.NameNotFoundException) false else false
    }
}
