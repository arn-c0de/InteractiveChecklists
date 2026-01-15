package com.example.checklist_interactive.ui.common

import android.content.Context
import android.content.res.Configuration

/**
 * Map overlay scaling utilities for non-Composable contexts
 *
 * These functions calculate appropriate scale factors for map overlays
 * (markers, labels, arrows) based on screen size and density.
 * Use these in Overlay classes that don't have access to Compose APIs.
 */

/**
 * Calculate map overlay scale factor based on screen configuration
 *
 * @param context Android context for accessing display metrics
 * @return Scale factor to apply to marker sizes, text sizes, and line widths
 */
fun calculateMapOverlayScale(context: Context): Float {
    val configuration = context.resources.configuration
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp

    // Determine if landscape based on dimensions
    val isLandscape = screenWidthDp > screenHeightDp

    // Determine device class
    val widthClass = when {
        screenWidthDp < 600 -> ScreenWidthClass.COMPACT
        screenWidthDp < 840 -> ScreenWidthClass.MEDIUM
        else -> ScreenWidthClass.EXPANDED
    }

    // Base scale by width class
    val baseScale = when (widthClass) {
        ScreenWidthClass.COMPACT -> when {
            screenWidthDp < 360 -> 0.7f  // Very small phones
            screenWidthDp < 420 -> 0.8f  // Small phones
            else -> 0.9f                  // Standard phones
        }
        ScreenWidthClass.MEDIUM -> 1.0f   // Tablets, large phones
        ScreenWidthClass.EXPANDED -> 1.1f // Large tablets
    }

    // Adjust for landscape orientation on phones (more horizontal space, less vertical)
    return if (isLandscape && widthClass == ScreenWidthClass.COMPACT) {
        baseScale * 0.85f  // Slightly smaller in landscape to fit more
    } else {
        baseScale
    }
}

/**
 * Get scaled marker radius
 * @param baseRadius Base radius in pixels (typically 16f)
 * @param context Android context
 * @return Scaled radius
 */
fun getScaledMarkerRadius(baseRadius: Float, context: Context): Float {
    return baseRadius * calculateMapOverlayScale(context)
}

/**
 * Get scaled text size for marker labels
 * @param baseTextSize Base text size in pixels (typically 18f for marker text)
 * @param context Android context
 * @return Scaled text size
 */
fun getScaledTextSize(baseTextSize: Float, context: Context): Float {
    return baseTextSize * calculateMapOverlayScale(context)
}

/**
 * Get scaled stroke width for lines and borders
 * @param baseStrokeWidth Base stroke width in pixels (typically 3f)
 * @param context Android context
 * @return Scaled stroke width
 */
fun getScaledStrokeWidth(baseStrokeWidth: Float, context: Context): Float {
    return baseStrokeWidth * calculateMapOverlayScale(context)
}

/**
 * Get scaled arrow size for heading indicators
 * @param baseArrowSize Base arrow size in pixels (typically 40f)
 * @param context Android context
 * @return Scaled arrow size
 */
fun getScaledArrowSize(baseArrowSize: Float, context: Context): Float {
    return baseArrowSize * calculateMapOverlayScale(context)
}

/**
 * Get scaled label offset (distance from marker)
 * @param baseOffset Base offset in pixels (typically 30f)
 * @param context Android context
 * @return Scaled offset
 */
fun getScaledLabelOffset(baseOffset: Float, context: Context): Float {
    return baseOffset * calculateMapOverlayScale(context)
}

/**
 * Screen width classification
 */
private enum class ScreenWidthClass {
    COMPACT,    // < 600dp
    MEDIUM,     // 600-840dp
    EXPANDED    // > 840dp
}

/**
 * Extension function to check if device is in landscape
 */
fun Context.isLandscape(): Boolean {
    return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

/**
 * Extension function to check if device is a tablet (>= 600dp width)
 */
fun Context.isTablet(): Boolean {
    return resources.configuration.screenWidthDp >= 600
}

/**
 * Extension function to check if device is a phone (< 600dp width)
 */
fun Context.isPhone(): Boolean {
    return !isTablet()
}

/**
 * Get display density scale factor
 * This is useful for converting dp to pixels
 */
fun Context.getDensityScale(): Float {
    return resources.displayMetrics.density
}

/**
 * Convert dp to pixels using device density
 */
fun Context.dpToPx(dp: Float): Float {
    return dp * getDensityScale()
}

/**
 * Convert pixels to dp using device density
 */
fun Context.pxToDp(px: Float): Float {
    return px / getDensityScale()
}
