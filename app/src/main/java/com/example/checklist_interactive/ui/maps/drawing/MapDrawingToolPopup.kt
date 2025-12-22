package com.example.checklist_interactive.ui.maps.drawing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Drawing tool popup for map - shows brush selection, colors, and controls
 */
@Composable
fun MapDrawingToolPopup(
    state: MapDrawingState,
    onStateChange: (MapDrawingState) -> Unit,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Use non-focusable popup so map touch events pass through to the drawing overlay
    // while the popup stays open. Back press will be handled explicitly below.
    Popup(
        alignment = Alignment.TopEnd,
        properties = PopupProperties(
            dismissOnBackPress = false, // we'll handle back press locally
            dismissOnClickOutside = false,
            focusable = false
        ),
        onDismissRequest = onDismiss
    ) {
        // Handle back press to close the popup even though Popup is non-focusable
        androidx.activity.compose.BackHandler(enabled = true) {
            onDismiss()
        }
        Surface(
            modifier = modifier
                .padding(16.dp)
                .width(280.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Drawing Tools",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.isDrawingMode) {
                            Text(
                                text = "✓ Drawing active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Divider()

                // Brush Type Selection
                Text(
                    text = "Brush Type",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pen
                    BrushTypeButton(
                        icon = Icons.Default.Create,
                        label = "Pen",
                        isSelected = state.brushType == MapBrushType.Pen && !state.isEraseMode,
                        onClick = {
                            onStateChange(state.copy(
                                brushType = MapBrushType.Pen,
                                isEraseMode = false
                            ))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Marker
                    BrushTypeButton(
                        icon = Icons.Default.Edit,
                        label = "Marker",
                        isSelected = state.brushType == MapBrushType.Marker && !state.isEraseMode,
                        onClick = {
                            onStateChange(state.copy(
                                brushType = MapBrushType.Marker,
                                isEraseMode = false
                            ))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Eraser
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BrushTypeButton(
                        icon = Icons.Default.Delete,
                        label = "Eraser",
                        isSelected = state.isEraseMode,
                        onClick = {
                            onStateChange(state.copy(isEraseMode = !state.isEraseMode))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }

                Divider()

                // Color Palette
                if (!state.isEraseMode) {
                    Text(
                        text = "Color",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    val colors = listOf(
                        Color.Red,
                        Color(0xFFFF6B00),  // Orange
                        Color(0xFFFFD700),  // Gold
                        Color.Green,
                        Color.Blue,
                        Color(0xFF9C27B0),  // Purple
                        Color.Black,
                        Color.White
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        colors.take(4).forEach { color ->
                            ColorButton(
                                color = color,
                                isSelected = state.selectedColor == color,
                                onClick = {
                                    onStateChange(state.copy(selectedColor = color))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        colors.drop(4).forEach { color ->
                            ColorButton(
                                color = color,
                                isSelected = state.selectedColor == color,
                                onClick = {
                                    onStateChange(state.copy(selectedColor = color))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Divider()

                // Stroke Width / Eraser Size
                if (state.isEraseMode) {
                    Text(
                        text = "Eraser Size",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            value = state.eraseRadius,
                            onValueChange = { onStateChange(state.copy(eraseRadius = it)) },
                            valueRange = 20f..100f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${state.eraseRadius.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(40.dp),
                            textAlign = TextAlign.End
                        )
                    }
                } else {
                    Text(
                        text = "Brush Size",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            value = state.strokeWidth,
                            onValueChange = { onStateChange(state.copy(strokeWidth = it)) },
                            valueRange = 2f..12f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${state.strokeWidth.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(40.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }

                Divider()

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Clear All
                    OutlinedButton(
                        onClick = onClearAll,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear All",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", fontSize = 12.sp)
                    }
                    
                    // Save
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = "Save",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Brush type selection button
 */
@Composable
private fun BrushTypeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface,
            contentColor = if (isSelected)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * Color selection button
 */
@Composable
private fun ColorButton(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Extension function for color luminance
private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}
