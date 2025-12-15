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
    fabSizePx: Int = 56,
    defaultX: Float = 1.0f,
    defaultY: Float = 0.9f,
    visible: Boolean = true,
    onClick: () -> Unit,
    containerColor: Color? = null,
    contentColor: Color? = null,
    content: @Composable () -> Unit
) {
    if (!visible) return

    val coroutineScope = rememberCoroutineScope()
    
    // Load saved position or use defaults
    val (savedXPercent, savedYPercent) = remember(name) {
        prefsManager.getPdfViewerFabPosition(name, defaultX, defaultY)
    }
    
    // Calculate available area (screen minus FAB size)
    val availableWidth = (screenWidthPx - fabSizePx).coerceAtLeast(1)
    val availableHeight = (screenHeightPx - fabSizePx).coerceAtLeast(1)
    
    // Convert percentage to pixels
    var offsetX by remember(savedXPercent, availableWidth) {
        mutableFloatStateOf((savedXPercent * availableWidth).coerceIn(0f, availableWidth.toFloat()))
    }
    var offsetY by remember(savedYPercent, availableHeight) {
        mutableFloatStateOf((savedYPercent * availableHeight).coerceIn(0f, availableHeight.toFloat()))
    }

    // Clamp any positions that might be outside the available area (e.g., due to layout/padding changes)
    // and persist corrected values so old off-screen positions are fixed automatically.
    LaunchedEffect(name, availableWidth, availableHeight) {
        val clampedX = offsetX.coerceIn(0f, availableWidth.toFloat())
        val clampedY = offsetY.coerceIn(0f, availableHeight.toFloat())
        if (clampedX != offsetX || clampedY != offsetY) {
            offsetX = clampedX
            offsetY = clampedY
            // Save corrected normalized values
            val xPercent = if (availableWidth > 0) (offsetX / availableWidth) else 0f
            val yPercent = if (availableHeight > 0) (offsetY / availableHeight) else 0f
            coroutineScope.launch {
                try {
                    prefsManager.setPdfViewerFabPosition(name, xPercent, yPercent)
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
                            // Update position
                            offsetX = (offsetX + dragAmount.x).coerceIn(0f, availableWidth.toFloat())
                            offsetY = (offsetY + dragAmount.y).coerceIn(0f, availableHeight.toFloat())
                        },
                        onDragEnd = {
                            if (isDragging) {
                                // Save position as percentage
                                val xPercent = offsetX / availableWidth
                                val yPercent = offsetY / availableHeight

                                coroutineScope.launch {
                                    prefsManager.setPdfViewerFabPosition(name, xPercent, yPercent)
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
                .zIndex(10f)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { _ ->
                            isDragging = true
                            longPressTriggered = true
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Update position
                            offsetX = (offsetX + dragAmount.x).coerceIn(0f, availableWidth.toFloat())
                            offsetY = (offsetY + dragAmount.y).coerceIn(0f, availableHeight.toFloat())
                        },
                        onDragEnd = {
                            if (isDragging) {
                                // Save position as percentage
                                val xPercent = offsetX / availableWidth
                                val yPercent = offsetY / availableHeight

                                coroutineScope.launch {
                                    prefsManager.setPdfViewerFabPosition(name, xPercent, yPercent)
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
            content()
        }
    }
}
