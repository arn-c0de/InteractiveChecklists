package com.example.checklist_interactive.ui.maps

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
 * Radial menu item data
 */
data class RadialMenuItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

/**
 * Radial menu that appears around a marker on long press
 * Shows buttons in a circular arrangement
 */
@Composable
fun RadialMenu(
    centerX: Int,
    centerY: Int,
    onDismiss: () -> Unit,
    items: List<RadialMenuItem> = listOf(
        RadialMenuItem(Icons.Default.Info, "Info") {},
        RadialMenuItem(Icons.Default.Edit, "Edit") {},
        RadialMenuItem(Icons.Default.Navigation, "Navigate") {},
        RadialMenuItem(Icons.Default.Delete, "Delete") {}
    )
) {
    android.util.Log.d("RadialMenu", "RadialMenu composing at ($centerX, $centerY) with ${items.size} items")
    var visible by remember { mutableStateOf(false) }
    
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
            // Center point indicator (visual reference)
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
            
            // Radial menu buttons
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
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
