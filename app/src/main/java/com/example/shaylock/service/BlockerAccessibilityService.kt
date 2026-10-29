package com.example.shaylock.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
 *  1. Observe foreground window changes and detect when any protected app is opened.
 *  2. Measure how much of each app's allowance is actually consumed.
 *  3. Raise the full-screen lock overlay over that app as soon as its allowance is used up.
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
     * Windows that can appear over a protected app without replacing it.
     *
     * Their events must never become the "foreground app" used for the block decision, and they
     * must not pause usage counting either: the protected app is still behind them and the system
     * does not always deliver a new window-state event when they disappear. That combination used
     * to pause the timer (e.g. while watching reels) until the next UI change.
     */
    private val transientPackages = mutableSetOf("com.android.systemui")

    /** True while the screen-state receiver is registered. */
    private var screenReceiverRegistered = false

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

        // The keyboard is a separate window; its events must not end the protected app's session.
        getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.forEach { transientPackages += it.packageName }

        registerScreenReceiver()

        Log.i(TAG, "Blocker watchdog connected")

        // Re-evaluate whenever any app crosses its limit or an override starts/ends. Collecting
        // the derived package set (instead of the ticking state) keeps this cheap.
        scope?.launch {
            BlockController.blockedPackages.collect {
                evaluateBlocking(foregroundPackage)
            }
        }

        // Pick up the app that is already on screen when the service is (re)connected.
        resumeFromCurrentWindow()
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
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (!OverlayService.isOverlayShowing) return false
        if (BlockController.isAnyOverrideActive()) return false

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
     * accessibility event, but the protected app can already be visible.
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

    /**
     * Detects instagram.com in the Chrome URL bar.
     *
     * The web rule exists because the browser was an obvious bypass around the native app. It is
     * only armed while an Instagram package is actually tracked.
     */
    private fun isInstagramWebInChrome(): Boolean {
        if (foregroundPackage !in CHROME_PACKAGES) return false

        val root = rootInActiveWindow ?: return false
        try {
            // Try common URL bar ids across Chrome variants.
            val ids = CHROME_PACKAGES.flatMap { packageName ->
                listOf(
                    "$packageName:id/url_bar",
                    "org.chromium.chrome:id/url_bar",
                )
            }.distinct()

            for (id in ids) {
                val urlBars = root.findAccessibilityNodeInfosByViewId(id) ?: continue
                if (urlBars.isEmpty()) continue
                for (node in urlBars) {
                    val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
                    if (text.contains("instagram.com", ignoreCase = true)) return true
                }
                break
            }

            // Fallback: scan the visible tree (covers incognito and custom browser UIs).
            val stack = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            stack.addLast(root)
            var visited = 0
            while (stack.isNotEmpty() && visited < 200) {
                val node = stack.removeLast()
                visited++
                val text = node.text?.toString().orEmpty()
                val description = node.contentDescription?.toString().orEmpty()
                if (text.contains("instagram.com", ignoreCase = true) ||
                    description.contains("instagram.com", ignoreCase = true)
                ) {
                    return true
                }
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(stack::addLast)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect the Chrome URL bar", e)
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

        val webTarget = resolveWebTarget(target)
        if (webTarget != null && BlockController.isWebBlockActive(webTarget)) {
            showOverlay(webTarget, webBlock = true)
            BlockController.setUsageCountingSuppressed(true)
            return
        }

        if (BlockController.shouldBlock(target)) {
            showOverlay(target, webBlock = false)
            // The user is looking at the lock screen, not at the app: stop charging the allowance.
            BlockController.setUsageCountingSuppressed(true)
            val state = BlockController.appStates.value.firstOrNull { it.packageName == target }
            Log.i(
                TAG,
                "Blocking $target - daily allowance used up " +
                    "(${state?.usedMs ?: 0} / ${state?.limitMs ?: 0} ms)",
            )
        } else {
            hideOverlay()
            BlockController.setUsageCountingSuppressed(false)
        }
    }

    /** The Instagram package whose override budget the current Chrome tab should consume. */
    private fun resolveWebTarget(target: String): String? {
        if (target !in CHROME_PACKAGES) return null
        if (!BlockController.isInstagramTracked()) return null
        if (!isInstagramWebInChrome()) return null

        return BlockPolicy.INSTAGRAM_PACKAGES.firstOrNull { BlockController.isTracked(it) }
    }

    /**
     * Raises the overlay, or switches it when the visible target/rule changed underneath it.
     */
    private fun showOverlay(target: String, webBlock: Boolean) {
        if (!OverlayService.isOverlayShowing) {
            OverlayController.show(this, target, webBlock)
            return
        }

        if (OverlayWindowHost.currentTargetPackage != target ||
            OverlayWindowHost.currentWebBlock != webBlock
        ) {
            Log.i(TAG, "Switching lock screen to $target (webBlock=$webBlock)")
            OverlayWindowHost.dismiss()
            OverlayController.show(this, target, webBlock)
        }
    }

    private fun hideOverlay() {
        if (OverlayService.isOverlayShowing) {
            Log.i(TAG, "Releasing overlay")
            OverlayController.hide(this)
        }
    }

    companion object {
        private const val TAG = "BlockerA11y"

        private val CHROME_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
        )

        @Volatile
        var instance: BlockerAccessibilityService? = null
            private set

        /** True once the system has connected the service and it is ready to watch. */
        val isRunning: Boolean
            get() = instance != null
    }
}
