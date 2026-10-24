package com.example.shaylock.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Minimal [LifecycleOwner] + [SavedStateRegistryOwner] for the overlay window.
 *
 * A [android.app.Dialog] is not a lifecycle owner, but a ComposeView needs a
 * `ViewTreeLifecycleOwner` to create its window recomposer. Without one the app dies with
 * `IllegalStateException: ViewTreeLifecycleOwner not found from androidx.compose.ui.platform.ComposeView`.
 *
 * The owner is attached to the decor view of the dialog *before* the ComposeView is added to it,
 * so `findViewTreeLifecycleOwner()` finds it while the composition is being created.
 *
 * All methods must be called on the main thread.
 */
class OverlayLifecycleOwner : SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)

    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    /** Moves the owner into the resumed state so the composition may start drawing. */
    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    /**
     * Destroys the owner.
     *
     * The composition itself is disposed by the view composition strategy of the ComposeView.
     */
    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}
