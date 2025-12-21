package com.example.checklist_interactive.ui.maps

import android.content.Context
import androidx.compose.runtime.*
import com.example.checklist_interactive.data.prefs.PreferencesManager
import com.example.checklist_interactive.data.tactical.LocationEntity
import com.example.checklist_interactive.data.tactical.RunwayEntity
import com.example.checklist_interactive.ui.maps.marker.MilitarySymbol
import com.example.checklist_interactive.ui.maps.marker.SymbolAffiliation
import com.example.checklist_interactive.ui.maps.navigation.PatternDirection
import com.example.checklist_interactive.ui.maps.navigation.PatternLabelOverlay
import com.example.checklist_interactive.ui.maps.navigation.PatternSize
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

/**
 * State holder class for MapViewer composable.
 * Bundles all UI state to reduce boilerplate and improve state management.
 */
@Stable
class MapViewerState(
    context: Context
) {
    private val prefsManager = PreferencesManager(context)
    
    // Map state
    var mapView by mutableStateOf<MapView?>(null)
    var positionMarker by mutableStateOf<Marker?>(null)
    
    // Map preferences
    private val savedCenter = prefsManager.getMapCenter()
    var autoCenter by mutableStateOf(if (savedCenter != null) prefsManager.isMapAutoCenterEnabled() else true)
    
    // Dialog states
    var showLayerDialog by mutableStateOf(false)
    var showOverlayDialog by mutableStateOf(false)
    var showQuickAccess by mutableStateOf(false)
    var showDataPad by mutableStateOf(false)
    var showMarkerRouteManagement by mutableStateOf(false)
    var showRouteCreation by mutableStateOf(false)
    var showMilitarySymbolPicker by mutableStateOf(false)
    
    // Overlay states
    var compassEnabled by mutableStateOf(prefsManager.isMapOverlayCompassEnabled())
    var rangeRingsEnabled by mutableStateOf(prefsManager.isMapOverlayRangeRingsEnabled())
    var rangeRingsMaxNm by mutableStateOf(prefsManager.getMapOverlayRangeRingsMaxNm())
    var mgrsGridEnabled by mutableStateOf(prefsManager.isMapOverlayMgrsGridEnabled())
    
    // Overlay instances
    var compassOverlay by mutableStateOf<Overlay?>(null)
    var headingSpeedLineOverlay by mutableStateOf<Overlay?>(null)
    var rangeRingsOverlay by mutableStateOf<Overlay?>(null)
    var mgrsGridOverlay by mutableStateOf<Overlay?>(null)
    
    // Map rotation: 0 = North-up, 1 = HDG-up (follow aircraft heading)
    var mapRotationMode by mutableStateOf(0)
    
    // Navigation state
    var activeNavigationTarget by mutableStateOf<LocationEntity?>(null)
    var navigationLine by mutableStateOf<Polyline?>(null)
    var navigationDistanceNm by mutableStateOf<Double?>(null)
    var navigationHeading by mutableStateOf<Double?>(null)
    var showNavigationDetails by mutableStateOf(true)
    
    // Runway approach state
    var showRunwayApproach by mutableStateOf(false)
    var targetRunways by mutableStateOf<List<RunwayEntity>>(emptyList())
    var runwayApproachLines by mutableStateOf<List<Polyline>>(emptyList())
    var selectedRunwayIndex by mutableStateOf<Int?>(null)
    var originalAirportTarget by mutableStateOf<LocationEntity?>(null)
    var selectedRunwayHeading by mutableStateOf<Double?>(null)
    var finalApproachDistanceNm by mutableStateOf(5.0)
    var selectedRunway by mutableStateOf<RunwayEntity?>(null)
    
    // Traffic pattern state
    var showTrafficPattern by mutableStateOf(false)
    var trafficPatternPolyline by mutableStateOf<Polyline?>(null)
    var trafficPatternLabelOverlay by mutableStateOf<PatternLabelOverlay?>(null)
    var patternSize by mutableStateOf(PatternSize.NORMAL)
    var patternDirection by mutableStateOf(PatternDirection.LEFT_HAND)
    var patternFinalDistanceNm by mutableStateOf(1.0)
    var showPatternDetails by mutableStateOf(true)
    
    // Military symbol placement state
    var pendingSymbolPlacement by mutableStateOf<Pair<MilitarySymbol, SymbolAffiliation>?>(null)
    var pendingMoveTargetName by mutableStateOf<String?>(null)
    
    // Position tracking
    var lastValidPlayerPosition by mutableStateOf<GeoPoint?>(null)
    var lastProcessedTimestamp by mutableStateOf<String?>(null)
    
    // Selected location state
    var selectedLocation by mutableStateOf<LocationEntity?>(null)
    var selectedRunways by mutableStateOf<List<RunwayEntity>>(emptyList())
    
    // Radial menu state
    var radialMenuVisible by mutableStateOf(false)
    var radialMenuX by mutableStateOf(0)
    var radialMenuY by mutableStateOf(0)
    var radialMenuMarker by mutableStateOf<LocationEntity?>(null)
    
    // Marker interaction tracking
    var lastLongPressedMarkerId by mutableStateOf<Int?>(null)
    var lastLongPressTime by mutableStateOf(0L)
    
    // Database state
    var tacticalDb by mutableStateOf<com.example.checklist_interactive.data.tactical.TacticalDatabase?>(null)
    var dbInitFailed by mutableStateOf(false)
    var dbInitError by mutableStateOf<String?>(null)
    
    // Restoration tracking
    var routesRestored by mutableStateOf(false)
    var navigationRestored by mutableStateOf(false)
}

/**
 * Remember a MapViewerState instance.
 */
@Composable
fun rememberMapViewerState(
    context: Context = androidx.compose.ui.platform.LocalContext.current
): MapViewerState {
    return remember(context) {
        MapViewerState(context)
    }
}
