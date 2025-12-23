package com.example.checklist_interactive.ui.maps.components

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
import com.example.checklist_interactive.ui.maps.drawing.MapBrushType
import com.example.checklist_interactive.ui.maps.drawing.MapDrawingState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Radial menu type
 */
enum class RadialMenuType {
    MARKER,   // Menu for map markers
    DRAWING   // Menu for drawing tools
}

/**
 * Radial menu item data
 */
data class RadialMenuItem(
    val icon: ImageVector,
    val label: String,
    val color: Color? = null,
    val isActive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Unified radial menu that appears on long press
 * Can show marker menu or drawing tools menu
 */
@Composable
fun RadialMenu(
    centerX: Int,
    centerY: Int,
    onDismiss: () -> Unit,
    menuType: RadialMenuType = RadialMenuType.MARKER,
    items: List<RadialMenuItem> = listOf(
        RadialMenuItem(Icons.Default.Info, "Info") {},
        RadialMenuItem(Icons.Default.Edit, "Edit") {},
        RadialMenuItem(Icons.Default.Navigation, "Navigate") {},
        RadialMenuItem(Icons.Default.Delete, "Delete") {}
    ),
    // Drawing-specific parameters
    drawingState: MapDrawingState? = null,
    onDrawingStateChange: ((MapDrawingState) -> Unit)? = null,
    onBrushSelected: ((MapBrushType) -> Unit)? = null,
    onColorSelected: ((Color) -> Unit)? = null
) {
    android.util.Log.d("RadialMenu", "RadialMenu composing at ($centerX, $centerY) type=$menuType")
    var visible by remember { mutableStateOf(false) }
    var showBrushSubmenu by remember { mutableStateOf(false) }
    var showColorSubmenu by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        android.util.Log.d("RadialMenu", "LaunchedEffect triggered, setting visible to true")
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
            // Center point indicator (visual reference) - RED for debugging exact position
            Box(
                modifier = Modifier
                    .offset { IntOffset(centerX - 12, centerY - 12) }
                    .size(24.dp)
                    .scale(scale)
                    .alpha(alpha)
                    .background(
                        Color.Red.copy(alpha = 0.8f),
                        CircleShape
                    )
            )
            
            // Inner dot to show exact center pixel
            Box(
                modifier = Modifier
                    .offset { IntOffset(centerX - 2, centerY - 2) }
                    .size(4.dp)
                    .scale(scale)
                    .alpha(alpha)
                    .background(
                        Color.Yellow,
                        CircleShape
                    )
            )
            
            // Render based on menu type
            when (menuType) {
                RadialMenuType.MARKER -> {
                    // Standard marker menu
                    items.forEachIndexed { index, item ->
                        val angle = (2 * PI * index / items.size) - PI / 2 // Start at top
                        val radius = 80 // pixels from center
                        
                        val buttonX = centerX + (radius * cos(angle)).toInt()
                        val buttonY = centerY + (radius * sin(angle)).toInt()
                        
                        RadialMenuButton(
                            x = buttonX,
                            y = buttonY,
                            item = item,
                            scale = scale,
                            alpha = alpha,
                            onDismiss = onDismiss
                        )
                    }
                }
                
                RadialMenuType.DRAWING -> {
                    // Drawing tools menu
                    val radius = 90
                    val state = drawingState ?: return@Box
                    
                    // Left: Toggle Drawing Mode
                    RadialMenuButton(
                        x = centerX - radius,
                        y = centerY,
                        item = RadialMenuItem(
                            icon = if (state.isDrawingMode) Icons.Default.TouchApp else Icons.Default.Draw,
                            label = if (state.isDrawingMode) "Exit Draw" else "Draw Mode",
                            isActive = state.isDrawingMode,
                            onClick = {
                                onDrawingStateChange?.invoke(state.copy(isDrawingMode = !state.isDrawingMode))
                                onDismiss()
                            }
                        ),
                        scale = scale,
                        alpha = alpha,
                        onDismiss = onDismiss
                    )
                    
                    // Top: Brushes
                    RadialMenuButton(
                        x = centerX,
                        y = centerY - radius,
                        item = RadialMenuItem(
                            icon = Icons.Default.Brush,
                            label = "Brush",
                            isActive = !state.isEraseMode && state.isDrawingMode,
                            onClick = {
                                showBrushSubmenu = !showBrushSubmenu
                                showColorSubmenu = false
                            }
                        ),
                        scale = scale,
                        alpha = alpha,
                        onDismiss = { /* Don't dismiss on submenu toggle */ }
                    )
                    
                    // Right: Colors
                    RadialMenuButton(
                        x = centerX + radius,
                        y = centerY,
                        item = RadialMenuItem(
                            icon = Icons.Default.Palette,
                            label = "Color",
                            color = state.selectedColor,
                            onClick = {
                                showColorSubmenu = !showColorSubmenu
                                showBrushSubmenu = false
                            }
                        ),
                        scale = scale,
                        alpha = alpha,
                        onDismiss = { /* Don't dismiss on submenu toggle */ }
                    )
                    
                    // Bottom: Eraser
                    RadialMenuButton(
                        x = centerX,
                        y = centerY + radius,
                        item = RadialMenuItem(
                            icon = Icons.Default.AutoFixHigh,
                            label = "Eraser",
                            isActive = state.isEraseMode,
                            onClick = {
                                onDrawingStateChange?.invoke(state.copy(isEraseMode = !state.isEraseMode))
                                onDismiss()
                            }
                        ),
                        scale = scale,
                        alpha = alpha,
                        onDismiss = onDismiss
                    )
                    
                    // Brush submenu
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
                            
                            RadialMenuButton(
                                x = buttonX,
                                y = buttonY,
                                item = RadialMenuItem(
                                    icon = icon,
                                    label = brushType.name,
                                    isActive = state.brushType == brushType,
                                    onClick = {
                                        onBrushSelected?.invoke(brushType)
                                        onDismiss()
                                    }
                                ),
                                scale = scale,
                                alpha = alpha,
                                onDismiss = onDismiss
                            )
                        }
                    }
                    
                    // Color submenu
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
                            
                            RadialMenuButton(
                                x = buttonX,
                                y = buttonY,
                                item = RadialMenuItem(
                                    icon = Icons.Default.Circle,
                                    label = "Color",
                                    color = color,
                                    isActive = state.selectedColor == color,
                                    onClick = {
                                        onColorSelected?.invoke(color)
                                        onDismiss()
                                    }
                                ),
                                scale = scale,
                                alpha = alpha,
                                onDismiss = onDismiss
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadialMenuButton(
    x: Int,
    y: Int,
    item: RadialMenuItem,
    scale: Float,
    alpha: Float,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(x - 28, y - 28) } // Center the 56dp button
            .size(56.dp)
            .scale(scale)
            .alpha(alpha)
    ) {
        FloatingActionButton(
            onClick = {
                item.onClick()
                onDismiss()
            },
            containerColor = when {
                item.isActive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = when {
                item.isActive -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = item.color ?: LocalContentColor.current,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
