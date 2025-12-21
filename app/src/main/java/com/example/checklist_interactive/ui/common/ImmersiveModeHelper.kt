package com.example.checklist_interactive.ui.common

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive


/**
 * Immersive mode helper for ModalBottomSheet and Dialog components
 * Provides reusable composables to hide system bars and maintain fullscreen immersive mode
 */

/**
 * Apply immersive mode to the current view (for activity window)
 * Call this in a LaunchedEffect to continuously apply while a sheet/dialog is visible
 */
@Composable
fun ApplyImmersiveModeToActivity(isVisible: Boolean) {
    val view = LocalView.current
    
    LaunchedEffect(isVisible) {
        if (!isVisible) return@LaunchedEffect
        
        val activity = view.context as? android.app.Activity
        val window = activity?.window ?: return@LaunchedEffect

        while (isActive && isVisible) {
            try {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } catch (_: Throwable) {
                // Ignore errors
            }
            delay(750L)
        }
    }
}

/**
 * Apply immersive mode to a dialog view (for ModalBottomSheet or Dialog window)
 * Handles both immediate application and continuous enforcement
 */
@Composable
fun ApplyImmersiveModeToDialog(isVisible: Boolean) {
    val dialogView = LocalView.current

    // Immediate application before first draw
    DisposableEffect(dialogView) {
        val dialogWindow = (dialogView.context as? android.app.Activity)?.window
        val controller = dialogWindow?.let { WindowCompat.getInsetsController(it, dialogView) }
        
        // Old-style flags for older APIs
        @Suppress("DEPRECATION")
        dialogView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        val preDrawListener = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                controller?.hide(WindowInsetsCompat.Type.systemBars())
                controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                dialogView.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        }
        dialogView.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                val vWindow = (v.context as? android.app.Activity)?.window
                val c = vWindow?.let { WindowCompat.getInsetsController(it, v) }
                c?.hide(WindowInsetsCompat.Type.systemBars())
                c?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                
                @Suppress("DEPRECATION")
                v.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }

            override fun onViewDetachedFromWindow(v: View) {}
        }
        dialogView.addOnAttachStateChangeListener(attachListener)
        
        onDispose {
            try {
                dialogView.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                dialogView.removeOnAttachStateChangeListener(attachListener)
            } catch (_: Throwable) {
                // Ignore cleanup errors
            }
        }
    }

    // Continuous enforcement while visible
    LaunchedEffect(dialogView, isVisible) {
        if (!isVisible) return@LaunchedEffect
        
        val dialogWindow = (dialogView.context as? android.app.Activity)?.window
        val dialogController = dialogWindow?.let { WindowCompat.getInsetsController(it, dialogView) }
        
        while (isActive && isVisible) {
            try {
                dialogController?.hide(WindowInsetsCompat.Type.systemBars())
                dialogController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } catch (_: Throwable) {
                // Ignore errors
            }
            delay(750L)
        }
    }
}

/**
 * Complete immersive mode setup for ModalBottomSheet
 * Combines both activity and dialog immersive mode application
 */
@Composable
fun ModalBottomSheetImmersiveMode(isVisible: Boolean) {
    ApplyImmersiveModeToActivity(isVisible)
    ApplyImmersiveModeToDialog(isVisible)
}
