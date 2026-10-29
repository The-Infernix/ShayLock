package com.example.shaylock.service

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.shaylock.R
import com.example.shaylock.core.BlockController
import com.example.shaylock.ui.screens.OverlayScreen
import com.example.shaylock.ui.theme.ShayLockTheme

/**
 * Owns the actual full-screen lock window.
 *
 * The window is hosted in a [Dialog] because a dialog window is modal by default: touches that
 * land outside of it are *not* delivered to the app underneath, which is exactly what a strict
 * blocker needs - Instagram cannot be interacted with behind the lock screen.
 *
 * The host is shared so that both [OverlayService] (the normal path) and the accessibility
 * watchdog (fallback path) can raise the very same lock screen without duplicating the code.
 */
object OverlayWindowHost {

    private const val TAG = "OverlayWindowHost"

    private var dialog: Dialog? = null

    /** The lifecycle owner that keeps the ComposeView of the lock screen alive. */
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    /** Used to marshal window operations onto the main thread. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** True while the lock window is on screen. */
    val isShowing: Boolean
        get() = dialog?.isShowing == true

    /** Package currently covered by the lock window, or null when it is hidden. */
    @Volatile
    var currentTargetPackage: String? = null
        private set

    /** True while the visible lock window is the instagram.com-in-Chrome rule. */
    @Volatile
    var currentWebBlock: Boolean = false
        private set

    /**
     * Raises the lock screen over the current app.
     *
     * @param context context used to create the window; a service or application context works
     *   because `TYPE_APPLICATION_OVERLAY` does not require an activity token.
     * @param targetPackage the package being blocked, used for the title on the lock screen.
     * @param webBlock true for the optional instagram.com-in-Chrome rule.
     * @param onUnlocked invoked after a valid PIN was entered.
     */
    @Synchronized
    fun show(
        context: Context,
        targetPackage: String,
        webBlock: Boolean = false,
        onUnlocked: () -> Unit,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            // Window operations must happen on the main thread.
            mainHandler.post { show(context, targetPackage, webBlock, onUnlocked) }
            return
        }

        if (dialog != null) return

        val baseContext = context.applicationContext
        val lockDialog = Dialog(baseContext, R.style.Theme_ShayLock_Overlay)

        // Not dismissable: the only way out of the lock screen is the correct PIN.
        lockDialog.setCancelable(false)
        lockDialog.setCanceledOnTouchOutside(false)
        // Swallow the hardware / gesture back button.
        lockDialog.setOnKeyListener { _, _, _ -> true }

        // The window type must be set before the dialog is shown.
        lockDialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)

        // A ComposeView creates its window recomposer from the ViewTreeLifecycleOwner of its
        // view tree. A dialog has none, so the crash
        // "ViewTreeLifecycleOwner not found from androidx.compose.ui.platform.ComposeView"
        // would happen as soon as the view is attached. The owner is therefore attached to the
        // decor view *before* the ComposeView is added to it.
        val owner = OverlayLifecycleOwner()
        owner.onCreate()
        lockDialog.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(owner)
            decorView.setViewTreeSavedStateRegistryOwner(owner)
        }

        // Compose content hosted by a plain ComposeView inside the dialog.
        val composeView = ComposeView(lockDialog.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ShayLockTheme {
                    OverlayScreen(
                        targetPackage = targetPackage,
                        webBlock = webBlock,
                        onSubmitPin = { code -> BlockController.submitPin(targetPackage, code) },
                        onUnlocked = {
                            dismiss()
                            onUnlocked()
                        },
                    )
                }
            }
        }
        lockDialog.setContentView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        try {
            lockDialog.show()
        } catch (e: WindowManager.BadTokenException) {
            Log.e(TAG, "Unable to show overlay window", e)
            owner.onDestroy()
            return
        }

        lockDialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(Color.BLACK.toDrawable())
            setDimAmount(1f)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        }

        // Go truly full screen: draw behind the status/navigation bars and hide them.
        lockDialog.window?.let { window ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
                systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }

        dialog = lockDialog
        lifecycleOwner = owner
        currentTargetPackage = targetPackage
        currentWebBlock = webBlock
        Log.i(TAG, "Lock screen shown over $targetPackage (webBlock=$webBlock)")
    }

    /** Removes the lock window if it is on screen. */
    @Synchronized
    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }

        dialog?.let {
            try {
                it.dismiss()
            } catch (e: Exception) {
                Log.w(TAG, "Ignoring error while dismissing overlay", e)
            }
        }
        dialog = null
        currentTargetPackage = null
        currentWebBlock = false

        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }
}
