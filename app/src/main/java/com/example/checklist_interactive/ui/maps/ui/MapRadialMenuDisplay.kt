package com.example.checklist_interactive.ui.maps.ui

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.checklist_interactive.data.tactical.LocationRepository
import com.example.checklist_interactive.ui.maps.MapViewerState
import com.example.checklist_interactive.ui.maps.components.RadialMenu
import com.example.checklist_interactive.ui.maps.components.RadialMenuItem
import com.example.checklist_interactive.ui.maps.components.RadialMenuType
import com.example.checklist_interactive.ui.maps.drawing.MapBrushType
import com.example.checklist_interactive.ui.maps.drawing.MapDrawingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "MapRadialMenuDisplay"

/**
 * Displays the unified radial menu for both marker and drawing interactions.
 *
 * @param mapState The map viewer state containing radial menu state
 * @param locationRepository Repository for location/marker operations
 * @param scope Coroutine scope for async operations
 * @param drawingState Current drawing state (for drawing menu)
 * @param onDrawingStateChange Callback to update drawing state
 */
@Composable
fun MapRadialMenuDisplay(
    mapState: MapViewerState,
    locationRepository: LocationRepository?,
    scope: CoroutineScope,
    drawingState: MapDrawingState? = null,
    onDrawingStateChange: ((MapDrawingState) -> Unit)? = null
) {
    // Only show radial menu if visible
    if (!mapState.radialMenuVisible) {
        return
    }

    Log.d(TAG, "Rendering RadialMenu at (${mapState.radialMenuX}, ${mapState.radialMenuY}) type=${mapState.radialMenuType}")

    when (mapState.radialMenuType) {
        RadialMenuType.MARKER -> {
            if (mapState.radialMenuMarker == null) return
            
            val items = buildMarkerMenuItems(
                mapState = mapState,
                locationRepository = locationRepository,
                scope = scope
            )

            RadialMenu(
                centerX = mapState.radialMenuX,
                centerY = mapState.radialMenuY,
                onDismiss = { mapState.radialMenuVisible = false },
                menuType = RadialMenuType.MARKER,
                items = items
            )
        }
        
        RadialMenuType.DRAWING -> {
            if (drawingState == null || onDrawingStateChange == null) return
            
            RadialMenu(
                centerX = mapState.radialMenuX,
                centerY = mapState.radialMenuY,
                onDismiss = { mapState.radialMenuVisible = false },
                menuType = RadialMenuType.DRAWING,
                drawingState = drawingState,
                onDrawingStateChange = onDrawingStateChange,
                onBrushSelected = { brushType ->
                    onDrawingStateChange(drawingState.copy(brushType = brushType, isEraseMode = false))
                },
                onColorSelected = { color ->
                    onDrawingStateChange(drawingState.copy(selectedColor = color))
                }
            )
        }
    }
}

/**
 * Builds the list of radial menu items for marker interactions.
 */
private fun buildMarkerMenuItems(
    mapState: MapViewerState,
    locationRepository: LocationRepository?,
    scope: CoroutineScope
): List<RadialMenuItem> {
    return mutableListOf<RadialMenuItem>().apply {
        // Info button
        add(RadialMenuItem(
            icon = Icons.Default.Info,
            label = "Info",
            onClick = {
                mapState.selectedLocation = mapState.radialMenuMarker
                mapState.showMarkerRouteManagement = true
            }
        ))

        // Edit button
        add(RadialMenuItem(
            icon = Icons.Default.Edit,
            label = "Edit",
            onClick = {
                mapState.selectedLocation = mapState.radialMenuMarker
                mapState.showMarkerRouteManagement = true
            }
        ))

        // Navigate button
        add(RadialMenuItem(
            icon = Icons.Default.Navigation,
            label = "Navigate",
            onClick = {
                mapState.radialMenuMarker?.let { marker ->
                    mapState.activeNavigationTarget = marker
                }
            }
        ))

        // Delete button (only for non-static markers)
        if (mapState.radialMenuMarker?.isStatic != 1) {
            add(RadialMenuItem(
                icon = Icons.Default.Delete,
                label = "Delete",
                onClick = {
                    mapState.radialMenuMarker?.let { marker ->
                        scope.launch {
                            val repo = locationRepository ?: return@launch
                            repo.deleteLocation(marker.id)
                        }
                    }
                }
            ))
        }
    }
}
