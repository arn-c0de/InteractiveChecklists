package com.example.checklist_interactive.ui.maps.ui

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.runtime.Composable
import com.example.checklist_interactive.data.tactical.LocationRepository
import com.example.checklist_interactive.ui.maps.MapViewerState
import com.example.checklist_interactive.ui.maps.components.RadialMenu
import com.example.checklist_interactive.ui.maps.components.RadialMenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "MapRadialMenuDisplay"

/**
 * Displays the radial menu for marker interactions.
 * Shows options for Info, Edit, Navigate, and Delete (if not static).
 *
 * @param mapState The map viewer state containing radial menu state
 * @param locationRepository Repository for location/marker operations
 * @param scope Coroutine scope for async operations
 */
@Composable
fun MapRadialMenuDisplay(
    mapState: MapViewerState,
    locationRepository: LocationRepository?,
    scope: CoroutineScope
) {
    // Only show radial menu if visible and marker is selected
    if (!mapState.radialMenuVisible || mapState.radialMenuMarker == null) {
        return
    }

    Log.d(TAG, "Rendering RadialMenu at (${mapState.radialMenuX}, ${mapState.radialMenuY}) for marker ${mapState.radialMenuMarker?.name}")

    val items = buildRadialMenuItems(
        mapState = mapState,
        locationRepository = locationRepository,
        scope = scope
    )

    RadialMenu(
        centerX = mapState.radialMenuX,
        centerY = mapState.radialMenuY,
        onDismiss = { mapState.radialMenuVisible = false },
        items = items
    )
}

/**
 * Builds the list of radial menu items based on the selected marker.
 *
 * @param mapState The map viewer state
 * @param locationRepository Repository for location operations
 * @param scope Coroutine scope for async operations
 * @return List of RadialMenuItem to display
 */
private fun buildRadialMenuItems(
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
