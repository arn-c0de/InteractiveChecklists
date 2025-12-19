package com.example.checklist_interactive.ui.maps

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Simple bus for ad-hoc map actions such as moving a marker.
 */
object MapActionBus {
    /** Pending marker id to move; when non-null, MapViewer should enter "move" mode and wait for a tap. */
    val pendingMoveMarkerId = MutableStateFlow<Int?>(null)

    fun requestMove(markerId: Int) {
        pendingMoveMarkerId.value = markerId
    }

    fun clear() {
        pendingMoveMarkerId.value = null
    }
}
