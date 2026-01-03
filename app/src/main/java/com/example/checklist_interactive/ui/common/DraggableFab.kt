package com.example.checklist_interactive.ui.common

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import com.example.checklist_interactive.data.prefs.PreferencesManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Draggable FAB button that persists its position in SharedPreferences
 * Long-press to drag, tap to execute action
 * 
 * @param name Unique identifier for this FAB (used for persistence)
 * @param prefsManager PreferencesManager instance for saving positions
 * @param screenWidthPx Screen width in pixels
 * @param screenHeightPx Screen height in pixels
 * @param fabSizePx FAB size in pixels (default 56dp)
 * @param defaultX Default X position as percentage (0.0 - 1.0) of available space
 * @param defaultY Default Y position as percentage (0.0 - 1.0) of available space
 * @param visible Whether the FAB should be visible
 * @param onClick Action to perform on tap
 * @param content FAB icon content
 */
@Composable
fun DraggableFab(
    name: String,
    prefsManager: PreferencesManager,
    screenWidthPx: Int,
    screenHeightPx: Int,
    fabSizeDp: Int = 56,
    scope: String = "",
    defaultX: Float = 1.0f,
    defaultY: Float = 0.9f,
    isLandscape: Boolean = false,
    visible: Boolean = true,
    onClick: () -> Unit,
    containerColor: Color? = null,
    contentColor: Color? = null,
    content: @Composable () -> Unit,
    marginPx: Int = 0 // horizontal margin on both sides
) {
    if (!visible) return

    val coroutineScope = rememberCoroutineScope()

    val density = LocalDensity.current
    val fabSizePx = with(density) { fabSizeDp.dp.roundToPx() }

    // Calculate available area (screen minus FAB size and margins)
    val availableWidth = (screenWidthPx - fabSizePx - marginPx * 2).coerceAtLeast(1)
    val availableHeight = (screenHeightPx - fabSizePx - marginPx * 2).coerceAtLeast(1)

    // Add orientation suffix to position key for separate landscape/portrait positions
    val orientationSuffix = if (isLandscape) "_landscape" else "_portrait"
    val positionKey = name + orientationSuffix

    // Load and calculate position - recalculate when orientation changes
    val (offsetX, offsetY) = remember(isLandscape, availableWidth, availableHeight, name, scope, defaultX, defaultY) {
        // Load saved position or use defaults for current orientation
        val (savedXPercent, savedYPercent) = prefsManager.getFabPosition(
            if (scope.isBlank()) null else scope,
            positionKey,
            defaultX,
            defaultY
        )

        // Calculate pixel position from percentage
        val x = (marginPx + (savedXPercent * availableWidth)).coerceIn(marginPx.toFloat(), (marginPx + availableWidth).toFloat())
        val y = (marginPx + (savedYPercent * availableHeight)).coerceIn(marginPx.toFloat(), (marginPx + availableHeight).toFloat())

        mutableFloatStateOf(x) to mutableFloatStateOf(y)
    }

    // Convert to var for dragging
    var currentOffsetX by offsetX
    var currentOffsetY by offsetY

    // Clamp any positions that might be outside the available area (e.g., due to layout/padding changes)
    // and persist corrected values so old off-screen positions are fixed automatically.
    LaunchedEffect(positionKey, availableWidth, availableHeight) {
        val minX = marginPx.toFloat()
        val maxX = (marginPx + availableWidth).toFloat()
        val minY = marginPx.toFloat()
        val maxY = (marginPx + availableHeight).toFloat()

        val clampedX = currentOffsetX.coerceIn(minX, maxX)
        val clampedY = currentOffsetY.coerceIn(minY, maxY)
        if (clampedX != currentOffsetX || clampedY != currentOffsetY) {
            currentOffsetX = clampedX
            currentOffsetY = clampedY
            // Save corrected normalized values (relative to available area)
            val xPercent = if (availableWidth > 0) ((currentOffsetX - marginPx) / availableWidth) else 0f
            val yPercent = if (availableHeight > 0) ((currentOffsetY - marginPx) / availableHeight) else 0f
            coroutineScope.launch {
                try {
                    prefsManager.setFabPosition(if (scope.isBlank()) null else scope, positionKey, xPercent, yPercent)
                } catch (e: Exception) {
                    android.util.Log.w("DraggableFab", "Failed to persist clamped FAB position: ${e.message}")
                }
            }
        }
    }
    
    var isDragging by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }
    
    // Use provided colors when set to preserve previous look
    val localContainerColor = containerColor
    val localContentColor = contentColor

    if (localContainerColor == null && localContentColor == null) {
        FloatingActionButton(
            onClick = {
                if (!longPressTriggered) {
                    onClick()
                }
                longPressTriggered = false
            },
            modifier = Modifier
                .offset { IntOffset(currentOffsetX.roundToInt(), currentOffsetY.roundToInt()) }
                .zIndex(10f)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { _ ->
                            isDragging = true
                            longPressTriggered = true
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Update position (respect margins)
                            val minX = marginPx.toFloat()
                            val maxX = (marginPx + availableWidth).toFloat()
                            val minY = marginPx.toFloat()
                            val maxY = (marginPx + availableHeight).toFloat()
                            currentOffsetX = (currentOffsetX + dragAmount.x).coerceIn(minX, maxX)
                            currentOffsetY = (currentOffsetY + dragAmount.y).coerceIn(minY, maxY)
                        },
                        onDragEnd = {
                            if (isDragging) {
                                // Save position as percentage (normalized to available area excluding margins)
                                val xPercent = if (availableWidth > 0) ((currentOffsetX - marginPx) / availableWidth) else 0f
                                val yPercent = if (availableHeight > 0) ((currentOffsetY - marginPx) / availableHeight) else 0f

                                coroutineScope.launch {
                                    prefsManager.setFabPosition(if (scope.isBlank()) null else scope, positionKey, xPercent, yPercent)
                                }
                            }
                            isDragging = false
                            longPressTriggered = false
                        },
                        onDragCancel = {
                            isDragging = false
                            longPressTriggered = false
                        }
                    )
                }
        ) {
            content()
        }
    } else {
        FloatingActionButton(
            onClick = {
                if (!longPressTriggered) {
                    onClick()
                }
                longPressTriggered = false
            },
            modifier = Modifier
                .offset { IntOffset(currentOffsetX.roundToInt(), currentOffsetY.roundToInt()) }
                .size(fabSizeDp.dp)
                .zIndex(10f)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { _ ->
                            isDragging = true
                            longPressTriggered = true
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Update position (respect margins)
                            val minX = marginPx.toFloat()
                            val maxX = (marginPx + availableWidth).toFloat()
                            val minY = marginPx.toFloat()
                            val maxY = (marginPx + availableHeight).toFloat()
                            currentOffsetX = (currentOffsetX + dragAmount.x).coerceIn(minX, maxX)
                            currentOffsetY = (currentOffsetY + dragAmount.y).coerceIn(minY, maxY)
                        },
                        onDragEnd = {
                            if (isDragging) {
                                // Save position as percentage (normalized to available area excluding margins)
                                val xPercent = if (availableWidth > 0) ((currentOffsetX - marginPx) / availableWidth) else 0f
                                val yPercent = if (availableHeight > 0) ((currentOffsetY - marginPx) / availableHeight) else 0f

                                coroutineScope.launch {
                                    prefsManager.setFabPosition(if (scope.isBlank()) null else scope, positionKey, xPercent, yPercent)
                                }
                            }
                            isDragging = false
                            longPressTriggered = false
                        },
                        onDragCancel = {
                            isDragging = false
                            longPressTriggered = false
                        }
                    )
                },
            containerColor = localContainerColor ?: MaterialTheme.colorScheme.primary,
            contentColor = localContentColor ?: MaterialTheme.colorScheme.onPrimary
        ) {
            // Make the icon scale relative to FAB size so it remains visually balanced
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}
