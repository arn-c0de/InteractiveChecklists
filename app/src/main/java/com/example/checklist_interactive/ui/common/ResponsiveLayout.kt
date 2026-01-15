package com.example.checklist_interactive.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window size class utilities for responsive design across device form factors.
 *
 * Breakpoints based on Material Design 3 guidelines and common device sizes:
 * - Compact: Phones in portrait (< 600dp width)
 * - Medium: Large phones, small tablets (600-840dp width)
 * - Expanded: Tablets in landscape, large tablets (> 840dp width)
 */

/**
 * Window width size class
 */
enum class WindowWidthSizeClass {
    /** Width < 600dp - Typical phones in portrait */
    COMPACT,

    /** Width 600-840dp - Large phones, small tablets, phones in landscape */
    MEDIUM,

    /** Width > 840dp - Tablets in landscape, large tablets */
    EXPANDED
}

/**
 * Window height size class
 */
enum class WindowHeightSizeClass {
    /** Height < 480dp - Phones in landscape, very compact screens */
    COMPACT,

    /** Height 480-900dp - Most phones in portrait */
    MEDIUM,

    /** Height > 900dp - Tablets, tall phones */
    EXPANDED
}

/**
 * Device type classification
 */
enum class DeviceType {
    /** Small phone (< 360dp width) */
    PHONE_SMALL,

    /** Typical phone (360-600dp width) */
    PHONE,

    /** Large phone / phablet (420-600dp width in portrait) */
    PHONE_LARGE,

    /** Small tablet (600-840dp width) */
    TABLET_SMALL,

    /** Large tablet (> 840dp width) */
    TABLET_LARGE
}

/**
 * Orientation
 */
enum class ScreenOrientation {
    PORTRAIT,
    LANDSCAPE
}

/**
 * Complete window size information
 */
data class WindowSize(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
    val widthDp: Int,
    val heightDp: Int,
    val orientation: ScreenOrientation,
    val deviceType: DeviceType
) {
    /** Quick check if device is in compact width mode (typically phones) */
    val isCompact: Boolean get() = widthSizeClass == WindowWidthSizeClass.COMPACT

    /** Quick check if device is tablet-sized */
    val isTablet: Boolean get() = deviceType == DeviceType.TABLET_SMALL || deviceType == DeviceType.TABLET_LARGE

    /** Quick check if device is phone-sized */
    val isPhone: Boolean get() = !isTablet

    /** Quick check if in landscape orientation */
    val isLandscape: Boolean get() = orientation == ScreenOrientation.LANDSCAPE

    /** Quick check if in portrait orientation */
    val isPortrait: Boolean get() = orientation == ScreenOrientation.PORTRAIT

    /** Check if vertical space is limited (landscape on phones) */
    val isVerticallyConstrained: Boolean get() =
        heightSizeClass == WindowHeightSizeClass.COMPACT ||
        (isPhone && isLandscape)
}

/**
 * Get current window size information
 */
@Composable
fun rememberWindowSize(): WindowSize {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    return remember(configuration.screenWidthDp, configuration.screenHeightDp, configuration.orientation) {
        val widthDp = configuration.screenWidthDp
        val heightDp = configuration.screenHeightDp

        val widthSizeClass = when {
            widthDp < 600 -> WindowWidthSizeClass.COMPACT
            widthDp < 840 -> WindowWidthSizeClass.MEDIUM
            else -> WindowWidthSizeClass.EXPANDED
        }

        val heightSizeClass = when {
            heightDp < 480 -> WindowHeightSizeClass.COMPACT
            heightDp < 900 -> WindowHeightSizeClass.MEDIUM
            else -> WindowHeightSizeClass.EXPANDED
        }

        val orientation = if (widthDp > heightDp) {
            ScreenOrientation.LANDSCAPE
        } else {
            ScreenOrientation.PORTRAIT
        }

        val deviceType = when {
            widthDp < 360 -> DeviceType.PHONE_SMALL
            widthDp < 420 -> DeviceType.PHONE
            widthDp < 600 -> if (orientation == ScreenOrientation.PORTRAIT) {
                DeviceType.PHONE_LARGE
            } else {
                DeviceType.PHONE // landscape phone
            }
            widthDp < 840 -> DeviceType.TABLET_SMALL
            else -> DeviceType.TABLET_LARGE
        }

        WindowSize(
            widthSizeClass = widthSizeClass,
            heightSizeClass = heightSizeClass,
            widthDp = widthDp,
            heightDp = heightDp,
            orientation = orientation,
            deviceType = deviceType
        )
    }
}

/**
 * Responsive dimensions helper
 * Provides dimension values that adapt to device size
 */
data class ResponsiveDimensions(
    /** Standard padding for screen edges */
    val screenPadding: Dp,

    /** Padding for content areas */
    val contentPadding: Dp,

    /** Spacing between elements */
    val elementSpacing: Dp,

    /** Minimum touch target size */
    val minTouchTarget: Dp,

    /** Icon size for actions */
    val iconSize: Dp,

    /** Icon size for navigation */
    val navIconSize: Dp,

    /** FAB size */
    val fabSize: Dp,

    /** Maximum content width for readability */
    val maxContentWidth: Dp,

    /** Dialog width */
    val dialogWidth: Dp,

    /** Bottom sheet peek height */
    val bottomSheetPeekHeight: Dp
)

/**
 * Get responsive dimensions based on window size
 */
@Composable
fun rememberResponsiveDimensions(windowSize: WindowSize = rememberWindowSize()): ResponsiveDimensions {
    return remember(windowSize) {
        when (windowSize.widthSizeClass) {
            WindowWidthSizeClass.COMPACT -> ResponsiveDimensions(
                screenPadding = 16.dp,
                contentPadding = 12.dp,
                elementSpacing = 8.dp,
                minTouchTarget = 48.dp,
                iconSize = 24.dp,
                navIconSize = 28.dp,
                fabSize = 56.dp,
                maxContentWidth = 600.dp,
                dialogWidth = when {
                    windowSize.widthDp < 360 -> 280.dp
                    else -> 320.dp
                },
                bottomSheetPeekHeight = 56.dp
            )
            WindowWidthSizeClass.MEDIUM -> ResponsiveDimensions(
                screenPadding = 24.dp,
                contentPadding = 16.dp,
                elementSpacing = 12.dp,
                minTouchTarget = 48.dp,
                iconSize = 24.dp,
                navIconSize = 28.dp,
                fabSize = 56.dp,
                maxContentWidth = 840.dp,
                dialogWidth = 400.dp,
                bottomSheetPeekHeight = 64.dp
            )
            WindowWidthSizeClass.EXPANDED -> ResponsiveDimensions(
                screenPadding = 32.dp,
                contentPadding = 24.dp,
                elementSpacing = 16.dp,
                minTouchTarget = 48.dp,
                iconSize = 28.dp,
                navIconSize = 32.dp,
                fabSize = 64.dp,
                maxContentWidth = 1200.dp,
                dialogWidth = 560.dp,
                bottomSheetPeekHeight = 72.dp
            )
        }
    }
}

/**
 * Adaptive grid columns based on window size
 */
@Composable
fun rememberAdaptiveGridColumns(
    compactColumns: Int = 2,
    mediumColumns: Int = 3,
    expandedColumns: Int = 4,
    windowSize: WindowSize = rememberWindowSize()
): Int {
    return remember(windowSize) {
        when (windowSize.widthSizeClass) {
            WindowWidthSizeClass.COMPACT -> compactColumns
            WindowWidthSizeClass.MEDIUM -> mediumColumns
            WindowWidthSizeClass.EXPANDED -> expandedColumns
        }
    }
}

/**
 * Check if device should use compact layout
 * Useful for switching between different layout strategies
 */
@Composable
fun shouldUseCompactLayout(windowSize: WindowSize = rememberWindowSize()): Boolean {
    return windowSize.isCompact || windowSize.isVerticallyConstrained
}

/**
 * Check if device should show navigation rail instead of bottom navigation
 */
@Composable
fun shouldUseNavigationRail(windowSize: WindowSize = rememberWindowSize()): Boolean {
    return windowSize.widthSizeClass >= WindowWidthSizeClass.MEDIUM
}

/**
 * Get appropriate number of columns for a grid based on available width
 */
fun calculateGridColumns(
    availableWidthDp: Int,
    minItemWidthDp: Int = 100,
    maxColumns: Int = 6
): Int {
    val columns = (availableWidthDp / minItemWidthDp).coerceAtLeast(1)
    return columns.coerceAtMost(maxColumns)
}

/**
 * Scale factor for map overlays based on screen density and size
 */
@Composable
fun rememberMapOverlayScale(windowSize: WindowSize = rememberWindowSize()): Float {
    val density = LocalDensity.current

    return remember(windowSize, density) {
        val baseScale = when (windowSize.widthSizeClass) {
            WindowWidthSizeClass.COMPACT -> when {
                windowSize.widthDp < 360 -> 0.7f  // Very small phones
                windowSize.widthDp < 420 -> 0.8f  // Small phones
                else -> 0.9f                       // Standard phones
            }
            WindowWidthSizeClass.MEDIUM -> 1.0f    // Tablets, large phones
            WindowWidthSizeClass.EXPANDED -> 1.1f  // Large tablets
        }

        // Further adjust for landscape orientation (more horizontal space)
        if (windowSize.isLandscape && windowSize.isPhone) {
            baseScale * 0.85f  // Slightly smaller in landscape to fit more
        } else {
            baseScale
        }
    }
}
