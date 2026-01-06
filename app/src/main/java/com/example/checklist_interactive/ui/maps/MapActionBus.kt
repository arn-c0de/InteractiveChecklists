package com.example.checklist_interactive.ui.maps

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Simple bus for ad-hoc map actions such as moving a marker.
 */
object MapActionBus {
    /** Pending marker id to move; when non-null, MapViewer should enter "move" mode and wait for a tap. */
    val pendingMoveMarkerId = MutableStateFlow<Int?>(null)

    /** Event to center map on specific coordinates */
    private val _centerMapEvent = MutableSharedFlow<Pair<Double, Double>>(replay = 0)
    val centerMapEvent = _centerMapEvent.asSharedFlow()

    /** Event to show tactical unit details */
    private val _showTacticalUnitDetailsEvent = MutableSharedFlow<com.example.checklist_interactive.data.tactical.TacticalUnitEntity>(replay = 0)
    val showTacticalUnitDetailsEvent = _showTacticalUnitDetailsEvent.asSharedFlow()

    fun requestMove(markerId: Int) {
        pendingMoveMarkerId.value = markerId
    }

    suspend fun centerMap(latitude: Double, longitude: Double) {
        _centerMapEvent.emit(Pair(latitude, longitude))
    }

    suspend fun showTacticalUnitDetails(unit: com.example.checklist_interactive.data.tactical.TacticalUnitEntity) {
        _showTacticalUnitDetailsEvent.emit(unit)
    }

    fun clear() {
        pendingMoveMarkerId.value = null
    }
}
