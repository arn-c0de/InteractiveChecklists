package com.example.checklist_interactive.ui.maps.drawing

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Radial menu for drawing tools that appears on long press
 * - Top: Brushes submenu
 * - Right: Color selector
 * - Bottom: Eraser
 * - Left: Additional tools
 */
@Composable
fun DrawingRadialMenu(
    centerX: Int,
    centerY: Int,
    drawingState: MapDrawingState,
    onDismiss: () -> Unit,
    onBrushSelected: (MapBrushType) -> Unit,
    onColorSelected: (Color) -> Unit,
    onEraseToggle: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var showBrushSubmenu by remember { mutableStateOf(false) }
    var showColorSubmenu by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        visible = true
    }
    
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "alpha"
    )
    
    // Fullscreen popup to capture outside clicks
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, 0),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    onDismiss()
                }
        ) {
            // Center point indicator
            Box(
                modifier = Modifier
                    .offset { IntOffset(centerX - 8, centerY - 8) }
                    .size(16.dp)
                    .scale(scale)
                    .alpha(alpha)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        CircleShape
                    )
            )
            
            // Main radial buttons
            val radius = 90
            
            // Top: Brushes
            DrawingRadialButton(
                x = centerX,
                y = centerY - radius,
                icon = Icons.Default.Brush,
                label = "Brush",
                scale = scale,
                alpha = alpha,
                isActive = !drawingState.isEraseMode,
                onClick = {
                    showBrushSubmenu = !showBrushSubmenu
                    showColorSubmenu = false
                }
            )
            
            // Right: Colors
            DrawingRadialButton(
                x = centerX + radius,
                y = centerY,
                icon = Icons.Default.Palette,
                label = "Color",
                scale = scale,
                alpha = alpha,
                color = drawingState.selectedColor,
                onClick = {
                    showColorSubmenu = !showColorSubmenu
                    showBrushSubmenu = false
                }
            )
            
            // Bottom: Eraser
            DrawingRadialButton(
                x = centerX,
                y = centerY + radius,
                icon = Icons.Default.AutoFixHigh,
                label = "Eraser",
                scale = scale,
                alpha = alpha,
                isActive = drawingState.isEraseMode,
                onClick = {
                    onEraseToggle()
                    onDismiss()
                }
            )
            
            // Left: Undo (future feature)
            DrawingRadialButton(
                x = centerX - radius,
                y = centerY,
                icon = Icons.Default.Undo,
                label = "Undo",
                scale = scale,
                alpha = alpha,
                onClick = {
                    // TODO: Implement undo
                    onDismiss()
                }
            )
            
            // Brush submenu (appears above brushes button)
            if (showBrushSubmenu) {
                val brushSubmenuY = centerY - radius - 80
                val brushTypes = listOf(
                    MapBrushType.Pen to Icons.Default.Create,
                    MapBrushType.Marker to Icons.Default.HighlightAlt,
                    MapBrushType.Special to Icons.Default.Brush
                )
                
                brushTypes.forEachIndexed { index, (brushType, icon) ->
                    val angle = (2 * PI * index / brushTypes.size) - PI / 2
                    val subRadius = 60
                    val buttonX = centerX + (subRadius * cos(angle)).toInt()
                    val buttonY = brushSubmenuY + (subRadius * sin(angle)).toInt()
                    
                    DrawingRadialButton(
                        x = buttonX,
                        y = buttonY,
                        icon = icon,
                        label = brushType.name,
                        scale = scale,
                        alpha = alpha,
                        isActive = drawingState.brushType == brushType,
                        onClick = {
                            onBrushSelected(brushType)
                            onDismiss()
                        }
                    )
                }
            }
            
            // Color submenu (appears right of color button)
            if (showColorSubmenu) {
                val colorSubmenuX = centerX + radius + 80
                val colors = listOf(
                    Color.Black,
                    Color.Red,
                    Color.Blue,
                    Color.Green,
                    Color.Yellow,
                    Color.Magenta,
                    Color.Cyan,
                    Color.White
                )
                
                colors.forEachIndexed { index, color ->
                    val angle = (2 * PI * index / colors.size) - PI / 2
                    val subRadius = 60
                    val buttonX = colorSubmenuX + (subRadius * cos(angle)).toInt()
                    val buttonY = centerY + (subRadius * sin(angle)).toInt()
                    
                    DrawingRadialButton(
                        x = buttonX,
                        y = buttonY,
                        icon = Icons.Default.Circle,
                        label = "Color",
                        scale = scale,
                        alpha = alpha,
                        color = color,
                        isActive = drawingState.selectedColor == color,
                        onClick = {
                            onColorSelected(color)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawingRadialButton(
    x: Int,
    y: Int,
    icon: ImageVector,
    label: String,
    scale: Float,
    alpha: Float,
    isActive: Boolean = false,
    color: Color? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(x - 28, y - 28) }
            .size(56.dp)
            .scale(scale)
            .alpha(alpha)
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = when {
                isActive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = when {
                isActive -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color ?: LocalContentColor.current,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
