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
import kotlin.math.sqrt

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
    marginPx: Int = 0, // horizontal margin on both sides
    allFabPositions: Map<String, Pair<Float, Float>> = emptyMap(),
    onPositionChanged: (Float, Float) -> Unit = { _, _ -> }
) {
    if (!visible) return

    val coroutineScope = rememberCoroutineScope()

    val density = LocalDensity.current
    val fabSizePx = with(density) { fabSizeDp.dp.roundToPx() }

    // Calculate available area (screen minus FAB size and margins)
    val availableWidth = (screenWidthPx - fabSizePx - marginPx * 2).coerceAtLeast(1)
    val availableHeight = (screenHeightPx - fabSizePx - marginPx * 2).coerceAtLeast(1)

    // Track last orientation to detect changes
    var lastWasLandscape by remember { mutableStateOf(isLandscape) }

    // Load saved position or use defaults (normalized to the available area excluding margins)
    val (savedXPercent, savedYPercent) = remember(name, scope) {
        prefsManager.getFabPosition(if (scope.isBlank()) null else scope, name, defaultX, defaultY)
    }

    // Convert percentage to pixels and offset by left margin
    var offsetX by remember(savedXPercent, availableWidth) {
        mutableStateOf((marginPx + (savedXPercent * availableWidth)).coerceIn(marginPx.toFloat(), (marginPx + availableWidth).toFloat()))
    }
    var offsetY by remember(savedYPercent, availableHeight) {
        mutableStateOf((marginPx + (savedYPercent * availableHeight)).coerceIn(marginPx.toFloat(), (marginPx + availableHeight).toFloat()))
    }

    // Report current position to overlay
    LaunchedEffect(offsetX, offsetY) {
        onPositionChanged(offsetX, offsetY)
    }

    // Function to check collision and adjust position if needed
    // Use centers for accurate collision and allow touch (no extra margin)
    fun adjustForCollisions(newX: Float, newY: Float): Pair<Float, Float> {
        var adjustedX = newX
        var adjustedY = newY
        val minDistance = fabSizePx.toFloat() // allow direct touching (no extra margin)

        val centerX = adjustedX + fabSizePx / 2f
        val centerY = adjustedY + fabSizePx / 2f

        allFabPositions.forEach { (otherId, otherPos) ->
            if (otherId != name) {
                val (otherX, otherY) = otherPos
                val otherCenterX = otherX + fabSizePx / 2f
                val otherCenterY = otherY + fabSizePx / 2f

                val dx = centerX - otherCenterX
                val dy = centerY - otherCenterY
                val distance = sqrt(dx * dx + dy * dy)

                if (distance < minDistance) {
                    if (distance == 0f) {
                        // Coincident positions: nudge horizontally by minDistance
                        adjustedX += minDistance
                    } else {
                        // Calculate repulsion vector based on center distance
                        val pushFactor = (minDistance - distance) / distance
                        val pushX = dx * pushFactor
                        val pushY = dy * pushFactor

                        adjustedX += pushX
                        adjustedY += pushY
                    }
                }
            }
        }

        // Clamp to screen bounds
        val minX = marginPx.toFloat()
        val maxX = (marginPx + availableWidth).toFloat()
        val minY = marginPx.toFloat()
        val maxY = (marginPx + availableHeight).toFloat()

        return Pair(
            adjustedX.coerceIn(minX, maxX),
            adjustedY.coerceIn(minY, maxY)
        )
    }

    // When orientation changes, reset to default position for that orientation
    LaunchedEffect(isLandscape) {
        android.util.Log.d("DraggableFAB", "$name: LaunchedEffect triggered - isLandscape=$isLandscape, lastWas=$lastWasLandscape")
        if (isLandscape != lastWasLandscape) {
            android.util.Log.d("DraggableFAB", "$name: ORIENTATION CHANGED! Resetting to defaults X=$defaultX Y=$defaultY")
            lastWasLandscape = isLandscape
            // Reset to default position for new orientation
            offsetX = (marginPx + (defaultX * availableWidth)).coerceIn(marginPx.toFloat(), (marginPx + availableWidth).toFloat())
            offsetY = (marginPx + (defaultY * availableHeight)).coerceIn(marginPx.toFloat(), (marginPx + availableHeight).toFloat())
            android.util.Log.d("DraggableFAB", "$name: New position: offsetX=$offsetX, offsetY=$offsetY")
        }
    }

    // Clamp any positions that might be outside the available area (e.g., due to layout/padding changes)
    // and persist corrected values so old off-screen positions are fixed automatically.
    LaunchedEffect(name, availableWidth, availableHeight) {
        val minX = marginPx.toFloat()
        val maxX = (marginPx + availableWidth).toFloat()
        val minY = marginPx.toFloat()
        val maxY = (marginPx + availableHeight).toFloat()

        val clampedX = offsetX.coerceIn(minX, maxX)
        val clampedY = offsetY.coerceIn(minY, maxY)
        if (clampedX != offsetX || clampedY != offsetY) {
            offsetX = clampedX
            offsetY = clampedY
            // Save corrected normalized values (relative to available area)
            val xPercent = if (availableWidth > 0) ((offsetX - marginPx) / availableWidth) else 0f
            val yPercent = if (availableHeight > 0) ((offsetY - marginPx) / availableHeight) else 0f
            coroutineScope.launch {
                try {
                    prefsManager.setFabPosition(if (scope.isBlank()) null else scope, name, xPercent, yPercent)
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
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .zIndex(10f)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { _ ->
                            isDragging = true
                            longPressTriggered = true
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Calculate new position with collision check
                            val newX = offsetX + dragAmount.x
                            val newY = offsetY + dragAmount.y
                            val (adjustedX, adjustedY) = adjustForCollisions(newX, newY)
                            offsetX = adjustedX
                            offsetY = adjustedY
                        },
                        onDragEnd = {
                            if (isDragging) {
                                // Save position as percentage (normalized to available area excluding margins)
                                val xPercent = if (availableWidth > 0) ((offsetX - marginPx) / availableWidth) else 0f
                                val yPercent = if (availableHeight > 0) ((offsetY - marginPx) / availableHeight) else 0f

                                coroutineScope.launch {
                                    prefsManager.setFabPosition(if (scope.isBlank()) null else scope, name, xPercent, yPercent)
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
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
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
                            // Calculate new position with collision check
                            val newX = offsetX + dragAmount.x
                            val newY = offsetY + dragAmount.y
                            val (adjustedX, adjustedY) = adjustForCollisions(newX, newY)
                            offsetX = adjustedX
                            offsetY = adjustedY
                        },
                        onDragEnd = {
                            if (isDragging) {
                                // Save position as percentage (normalized to available area excluding margins)
                                val xPercent = if (availableWidth > 0) ((offsetX - marginPx) / availableWidth) else 0f
                                val yPercent = if (availableHeight > 0) ((offsetY - marginPx) / availableHeight) else 0f

                                coroutineScope.launch {
                                    prefsManager.setFabPosition(if (scope.isBlank()) null else scope, name, xPercent, yPercent)
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
