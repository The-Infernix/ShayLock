package com.example.shaylock.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.example.shaylock.core.BlockController
import com.example.shaylock.core.BlockPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The blocker watchdog.
 *
 * Responsibilities:
 *  1. Observe foreground window changes and detect when a blocked app (Instagram) is opened.
 *  2. Measure how much of the daily allowance is actually consumed - only the time the blocked
 *     app is really on screen is charged to the allowance.
 *  3. Raise the full-screen lock overlay over that app as soon as the allowance is used up.
 *  4. Swallow the HOME / BACK / APP-SWITCH key events while the overlay is on screen so the
 *     user cannot simply navigate around the lock.
 *
 * The service is declared with `BIND_ACCESSIBILITY_SERVICE` in the manifest and configured by
 * `res/xml/accessibility_service_config.xml`.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private var scope: CoroutineScope? = null

    /** The package that was last reported as being in the foreground. */
    private var foregroundPackage: String? = null

    /**
     * Windows that can appear over the blocked app without replacing it.
     *
     * Their events must never become the "foreground app" used for the block decision, and they
     * must not pause usage counting either: the blocked app is still behind them and the system
     * does not always deliver a new window-state event when they disappear. That combination used
     * to pause the timer (e.g. while watching reels) until the next Instagram UI change.
     */
    private val transientPackages = mutableSetOf("com.android.systemui")

    /** True while the screen-state receiver is registered. */
    private var screenReceiverRegistered = false

    /** True when blocking instagram.com in Chrome with no PIN bypass allowed. */
    private var permanentChromeWebBlock = false

    /**
     * Stops the allowance while the screen is off. Transient system-UI events no longer pause the
     * counter, so the keyguard must be handled explicitly.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen off - pausing usage counting")
                    BlockController.onForegroundAppChanged(null)
                }

                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT,
                -> resumeFromCurrentWindow()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        BlockController.initialize(applicationContext)

        // The keyboard is a separate window; its events must not end the blocked app's session.
        getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.forEach { transientPackages += it.packageName }

        registerScreenReceiver()

        Log.i(TAG, "Blocker watchdog connected")

        // The block has to appear the moment the allowance runs out, even if the user never
        // leaves the app, and it has to come back when the unlock session ends.
        scope?.launch {
            BlockController.isBlockingEffective.collect {
                evaluateBlocking(foregroundPackage)
            }
        }

        // Pick up the app that is already on screen when the service is (re)connected.
        resumeFromCurrentWindow()

        // If the allowance was already used up when the service was (re)connected, pick it up.
        evaluateBlocking(foregroundPackage)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // TYPE_WINDOWS_CHANGED fires for every window anywhere (status bar, keyboard, toasts) and
        // is not a reliable "this app came to the foreground" signal. Only a window-state change
        // means an app actually became the foreground window.
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // Ignore events produced by our own overlay window.
        if (packageName == this.packageName) return

        // System UI and the keyboard are transient windows drawn over the app: keep the real app
        // as the blocking target and keep charging the allowance. Do not rely on a later event to
        // resume counting - while watching reels no new window-state event may arrive.
        if (packageName in transientPackages) return

        BlockController.onForegroundAppChanged(packageName)

        if (packageName != foregroundPackage) {
            Log.d(TAG, "Foreground app -> $packageName")
            foregroundPackage = packageName
        }

        evaluateBlocking(packageName)
    }

    /** Registers the receiver that pauses counting while the screen is off. */
    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
    }

    /**
     * Reads the app that is actually on screen and resumes counting for it.
     *
     * Used after the screen turns back on and when the service reconnects, because neither case
     * guarantees a new accessibility window-state event.
     */
    private fun resumeFromCurrentWindow() {
        val current = rootInActiveWindow?.packageName?.toString() ?: return
        if (current == this.packageName || current in transientPackages) return

        Log.d(TAG, "Current foreground window -> $current")
        foregroundPackage = current
        BlockController.onForegroundAppChanged(current)
        evaluateBlocking(current)
    }

    /**
     * Filters the global key stream while the lock overlay is visible.
     *
     * Returning `true` consumes the event so the system never acts on it. The guard on
     * [OverlayService.isOverlayShowing] is what keeps the rest of the device usable: outside of a
     * block the keys are passed straight through.
     *
     * Note: only one accessibility service can hold the key-filtering capability at a time on a
     * given device, so this may be unavailable if the user has another key-filtering service
     * enabled. The watchdog re-arms the overlay whenever the blocked app returns to the
     * foreground, which is the fallback enforcement path.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (!OverlayService.isOverlayShowing) return false
        if (BlockController.isUnlockSessionActive()) return false

        return when (event.keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_APP_SWITCH,
            -> true // Consumed: the user cannot navigate away from the lock screen.

            else -> false
        }
    }

    /**
     * Re-evaluates the app that is currently on screen.
     *
     * Used when the overlay service is recreated after a process death: there may be no fresh
     * accessibility event, but the blocked app can already be visible.
     */
    fun recheckForeground() {
        resumeFromCurrentWindow()
    }

    override fun onInterrupt() {
        Log.i(TAG, "Blocker watchdog interrupted")
    }

    override fun onDestroy() {
        unregisterScreenReceiver()
        OverlayController.hide(this)
        scope?.cancel()
        scope = null
        instance = null
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        unregisterScreenReceiver()
        OverlayController.hide(this)
        instance = null
        return super.onUnbind(intent)
    }

    private fun isInstagramWebInChrome(): Boolean {
        if (foregroundPackage != "com.android.chrome" && foregroundPackage != "com.chrome.beta" && foregroundPackage != "com.chrome.dev" && foregroundPackage != "com.chrome.canary") return false
        val root = rootInActiveWindow ?: return false
        try {
            // Try common URL bar ids across Chrome variants
            val ids = listOf(
                "com.android.chrome:id/url_bar",
                "com.chrome.beta:id/url_bar",
                "com.chrome.dev:id/url_bar",
                "com.chrome.canary:id/url_bar",
                "org.chromium.chrome:id/url_bar",
            )
            for (id in ids) {
                val urlBars = root.findAccessibilityNodeInfosByViewId(id)
                if (!urlBars.isNullOrEmpty()) {
                    for (node in urlBars) {
                        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
                        if (text.contains("instagram.com", ignoreCase = true)) {
                            return true
                        }
                    }
                    break
                }
            }
            // Also scan all nodes for instagram.com text as fallback (covers incognito/custom UI)
            val allNodes = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
            val stack = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            stack.addLast(root)
            while (stack.isNotEmpty() && allNodes.size < 200) {
                val n = stack.removeLast()
                allNodes.add(n)
                for (i in 0 until n.childCount) {
                    val c = n.getChild(i) ?: continue
                    stack.addLast(c)
                }
            }
            for (node in allNodes) {
                val t = node.text?.toString().orEmpty()
                val d = node.contentDescription?.toString().orEmpty()
                if (t.contains("instagram.com", ignoreCase = true) || d.contains("instagram.com", ignoreCase = true)) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect Chrome URL bar", e)
        }
        return false
    }

    /**
     * Decides whether the currently visible app must be covered by the lock overlay.
     *
     * @param packageName the app reported in the foreground, or null when unknown.
     */
    private fun evaluateBlocking(packageName: String?) {
        val target = packageName ?: return

        val isChromeInstagramWeb = (target == "com.android.chrome" || target == "com.chrome.beta" || target == "com.chrome.dev" || target == "com.chrome.canary") && isInstagramWebInChrome()
        if (isChromeInstagramWeb) {
            permanentChromeWebBlock = true
            if (OverlayService.isOverlayShowing) {
                OverlayController.hide(this)
            }
            Log.i(TAG, "Blocking instagram.com in Chrome - permanent web block (no PIN)")
            OverlayController.show(this, BlockPolicy.PRIMARY_PACKAGE)
            BlockController.setUsageCountingSuppressed(true)
            return
        }

        if (permanentChromeWebBlock && target !in setOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary")) {
            permanentChromeWebBlock = false
        }

        val shouldBlock =
            BlockPolicy.isBlockedPackage(target) &&
                BlockController.isBlockingEffective.value

        if (shouldBlock) {
            if (!OverlayService.isOverlayShowing || permanentChromeWebBlock) {
                if (permanentChromeWebBlock) {
                    permanentChromeWebBlock = false
                    OverlayController.hide(this)
                }
                val used = BlockController.usedTodayMs.value
                val limit = BlockController.dailyLimit.value.limitMs
                Log.i(TAG, "Blocking $target - daily allowance used up ($used / $limit ms)")
                OverlayController.show(this, target)
            }
            // The user is looking at the lock screen, not at the app: stop charging the allowance.
            BlockController.setUsageCountingSuppressed(true)
        } else {
            if (OverlayService.isOverlayShowing && !permanentChromeWebBlock) {
                Log.i(TAG, "Releasing overlay")
                OverlayController.hide(this)
            } else if (OverlayService.isOverlayShowing && permanentChromeWebBlock && !isChromeInstagramWeb) {
                permanentChromeWebBlock = false
                Log.i(TAG, "Releasing overlay")
                OverlayController.hide(this)
            }
            BlockController.setUsageCountingSuppressed(false)
        }
    }

    companion object {
        private const val TAG = "BlockerA11y"

        @Volatile
        var instance: BlockerAccessibilityService? = null
            private set

        /** True once the system has connected the service and it is ready to watch. */
        val isRunning: Boolean
            get() = instance != null
    }
}
