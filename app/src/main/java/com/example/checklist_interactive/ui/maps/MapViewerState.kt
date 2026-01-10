package com.example.checklist_interactive.ui.maps

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import com.example.checklist_interactive.data.prefs.PreferencesManager
import com.example.checklist_interactive.data.tactical.LocationEntity
import com.example.checklist_interactive.data.tactical.LocationRepository
import com.example.checklist_interactive.data.tactical.RouteRepository
import com.example.checklist_interactive.data.tactical.RunwayEntity
import com.example.checklist_interactive.data.tactical.TacticalDatabase
import com.example.checklist_interactive.ui.maps.marker.MarkerRouteViewModel
import com.example.checklist_interactive.ui.maps.marker.MilitarySymbol
import com.example.checklist_interactive.ui.maps.marker.SymbolAffiliation
import com.example.checklist_interactive.ui.maps.navigation.PatternDirection
import com.example.checklist_interactive.ui.maps.navigation.PatternLabelOverlay
import com.example.checklist_interactive.ui.maps.navigation.PatternSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    private val context: Context
) {
    private val prefsManager = PreferencesManager(context)
    private val TAG = "MapViewerState"
    
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
    var markerRouteManagementInitialTab by mutableStateOf<Int?>(null)
    var showRouteCreation by mutableStateOf(false)
    var showMilitarySymbolPicker by mutableStateOf(false)
    
    // Overlay states
    var compassEnabled by mutableStateOf(prefsManager.isMapOverlayCompassEnabled())
    var rangeRingsEnabled by mutableStateOf(prefsManager.isMapOverlayRangeRingsEnabled())
    var rangeRingsMaxNm by mutableStateOf(prefsManager.getMapOverlayRangeRingsMaxNm())
    var mgrsGridEnabled by mutableStateOf(prefsManager.isMapOverlayMgrsGridEnabled())
    var countryBordersEnabled by mutableStateOf(prefsManager.isMapOverlayCountryBordersEnabled())
    var borderEpoch by mutableStateOf(
        BorderEpoch.values().getOrElse(prefsManager.getMapOverlayBorderEpoch()) { BorderEpoch.MODERN }
    )
    var flightInstrumentsEnabled by mutableStateOf(prefsManager.isMapOverlayFlightInstrumentsEnabled())
    var showMarkerLabels by mutableStateOf(prefsManager.isMapMarkerLabelsEnabled())
    
    // Flight path tracking state
    var flightPathEnabled by mutableStateOf(prefsManager.isFlightPathEnabled())
    var flightPathRecording by mutableStateOf(false)
    var flightPathPointCount by mutableStateOf(0)
    var flightPathIntervalSeconds by mutableStateOf(prefsManager.getFlightPathIntervalSeconds())
    var flightPathPolyline by mutableStateOf<Polyline?>(null)
    var flightPathStartMarker by mutableStateOf<Marker?>(null)
    
    // Overlay instances
    var compassOverlay by mutableStateOf<Overlay?>(null)
    var headingSpeedLineOverlay by mutableStateOf<Overlay?>(null)
    var rangeRingsOverlay by mutableStateOf<Overlay?>(null)
    var mgrsGridOverlay by mutableStateOf<Overlay?>(null)
    var countryBordersOverlay by mutableStateOf<Overlay?>(null)
    var airportLabelsOverlay by mutableStateOf<AirportMarkerLabelsOverlay?>(null)
    var airspaceCirclesOverlay by mutableStateOf<AirspaceCirclesOverlay?>(null)
    
    // Map rotation: 0 = North-up, 1 = HDG-up (follow aircraft heading)
    var mapRotationMode by mutableStateOf(prefsManager.getMapRotationMode())
    
    // 2-finger rotation gesture control
    var rotationGestureEnabled by mutableStateOf(prefsManager.isMapRotationGestureEnabled())
    
    // Navigation state
    var activeNavigationTarget by mutableStateOf<LocationEntity?>(null)
    var navigationLine by mutableStateOf<Polyline?>(null)
    var navigationDistanceNm by mutableStateOf<Double?>(null)
    var navigationHeading by mutableStateOf<Double?>(null)
    var showNavigationDetails by mutableStateOf(false)
    
    // Tactical unit tracking for live route updates
    var activeNavigationTacticalUnitId by mutableStateOf<Int?>(null) // DCS ID of tracked tactical unit
    
    // Runway approach state
    var showRunwayApproach by mutableStateOf(false)
    var targetRunways by mutableStateOf<List<RunwayEntity>>(emptyList())
    var runwayApproachLines by mutableStateOf<List<Polyline>>(emptyList())
    var selectedRunwayIndex by mutableStateOf<Int?>(null)
    var originalAirportTarget by mutableStateOf<LocationEntity?>(null) // For active navigation/pattern
    var airspaceTargets by mutableStateOf<Set<LocationEntity>>(emptySet()) // For showing multiple airspaces
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
    var roundedPatternCorners by mutableStateOf(false)
    var showPatternDetails by mutableStateOf(true)
    var customPatternAltitudeAglFt by mutableStateOf<Int?>(null) // Custom override for pattern altitude AGL

    // Manual landing pattern (for markers without runways - tactical units, carriers, etc.)
    var enableManualLandingPattern by mutableStateOf(false)
    var manualLandingHeading by mutableStateOf("")
    var showManualHeadingError by mutableStateOf(false)

    // Pattern altitude indicator thresholds (in feet)
    // Small tolerance (≈) and a larger warning tolerance (yellow) used for color coding
    var patternAltitudeSmallToleranceFt by mutableStateOf(50.0)
    var patternAltitudeWarningToleranceFt by mutableStateOf(500.0)
    
    // Airspace display state
    var showAirspaceCircles by mutableStateOf(false)
    var enabledAirspaceClasses by mutableStateOf(setOf("CLASS_D", "CLASS_C_CTR")) // Default to basic CTR classes
    var airspaceFillTransparency by mutableStateOf(0.10f) // Default to 10% opacity (very transparent)
    
    // Per-marker pattern state (marker ID -> PatternState)
    data class MarkerPatternState(
        var enabled: Boolean = false,
        var patternSize: PatternSize = PatternSize.NORMAL,
        var patternDirection: PatternDirection = PatternDirection.LEFT_HAND,
        var finalDistanceNm: Double = 1.0,
        var roundedCorners: Boolean = false,
        var customAltitudeAglFt: Int? = null,
        var selectedRunwayIndex: Int? = null,
        var selectedRunwayHeading: Double? = null,
        var manualHeading: String = "",
        var showManualHeadingError: Boolean = false
    )
    var markerPatterns by mutableStateOf<Map<Int, MarkerPatternState>>(emptyMap())
    var patternPolylines by mutableStateOf<Map<Int, Polyline>>(emptyMap())
    var patternLabelOverlays by mutableStateOf<Map<Int, PatternLabelOverlay>>(emptyMap())
    
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
    var radialMenuType by mutableStateOf(com.example.checklist_interactive.ui.maps.components.RadialMenuType.MARKER)
    
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

    // ============================================================================
    // BUSINESS LOGIC METHODS
    // Methods extracted from LaunchedEffect blocks for better separation of concerns
    // ============================================================================
    
    /**
     * Get or create pattern state for a marker
     */
    fun getPatternState(markerId: Int): MarkerPatternState {
        return markerPatterns[markerId] ?: MarkerPatternState().also {
            markerPatterns = markerPatterns + (markerId to it)
        }
    }
    
    /**
     * Update pattern state for a marker
     */
    fun updatePatternState(markerId: Int, update: (MarkerPatternState) -> MarkerPatternState) {
        val current = getPatternState(markerId)
        markerPatterns = markerPatterns + (markerId to update(current))
    }
    
    /**
     * Toggle pattern visibility for a marker
     */
    fun toggleMarkerPattern(markerId: Int): Boolean {
        val state = getPatternState(markerId)
        val newEnabled = !state.enabled
        updatePatternState(markerId) { it.copy(enabled = newEnabled) }
        saveMarkerPatternState(markerId)
        return newEnabled
    }
    
    /**
     * Clear pattern for a specific marker
     */
    fun clearMarkerPattern(markerId: Int) {
        markerPatterns = markerPatterns - markerId
        patternPolylines = patternPolylines - markerId
        patternLabelOverlays = patternLabelOverlays - markerId
        saveMarkerPatternState(markerId)
    }

    /**
     * Initialize the TacticalDatabase.
     * Should be called once when the MapViewer is composed.
     */
    suspend fun initializeDatabase() {
        Log.d(TAG, "Starting TacticalDatabase initialization...")
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Calling TacticalDatabase.getInstance()...")
                val db = withTimeout(10000L) { // 10 second timeout
                    TacticalDatabase.getInstance(context, useExternalPath = false)
                }
                Log.d(TAG, "TacticalDatabase.getInstance() completed successfully")
                withContext(Dispatchers.Main) {
                    tacticalDb = db
                    Log.d(TAG, "TacticalDatabase assigned to state variable - DB ready!")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "TacticalDatabase initialization timed out after 10 seconds", e)
                withContext(Dispatchers.Main) {
                    dbInitFailed = true
                    dbInitError = "Zeitüberschreitung beim Laden der Datenbank"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize TacticalDatabase", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    dbInitFailed = true
                    dbInitError = e.message ?: "Unbekannter Fehler"
                }
            }
        }
    }

    /**
     * Restore visible routes from SharedPreferences.
     * Should be called when MarkerRouteViewModel becomes available.
     */
    suspend fun restoreVisibleRoutes(markerRouteViewModel: MarkerRouteViewModel?) {
        // Reset flag each time the viewmodel instance changes
        routesRestored = false

        val vm = markerRouteViewModel
        if (vm == null) {
            Log.d(TAG, "MarkerRouteViewModel not ready - deferring route restoration")
            return
        }

        val prefs = context.getSharedPreferences("map_routes_prefs", Context.MODE_PRIVATE)
        val savedRouteIds = prefs.getStringSet("visible_route_ids", emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet() ?: emptySet()

        vm.setVisibleRoutes(savedRouteIds)
        Log.d(TAG, "Restored visible routes: $savedRouteIds")

        // Small delay to ensure collectors process the change before we re-enable saving
        delay(50)
        routesRestored = true
    }

    /**
     * Restore navigation state from SharedPreferences.
     * Should be called when database and locationRepository are ready.
     */
    suspend fun restoreNavigationState(locationRepository: LocationRepository?) {
        if (tacticalDb == null || locationRepository == null) {
            Log.d(TAG, "Navigation restore deferred: dbReady=${tacticalDb != null}, locationRepository=${locationRepository != null}")
            return
        }
        navigationRestored = false

        val prefs = context.getSharedPreferences("map_navigation_prefs", Context.MODE_PRIVATE)

        // Restore active navigation target
        val navTargetId = prefs.getInt("active_nav_target_id", -999)
        Log.d(TAG, "Attempting to restore navigation target with ID: $navTargetId")

        if (navTargetId == -2 || navTargetId == -1) {
            // Special-case: Pattern (-2) and approach (-1) navigation
            val patternAirportId = prefs.getInt("nav_airport_id", prefs.getInt("pattern_airport_id", -999))
            if (patternAirportId > 0) {
                try {
                    val airport = withContext(Dispatchers.IO) {
                        locationRepository.getLocationById(patternAirportId)
                    }
                    if (airport != null) {
                        activeNavigationTarget = airport
                        // Do not force the navigation panel open here; respect saved user preference
                        autoCenter = false
                        Log.d(TAG, "✅ Restored navigation airport: ${airport.name} (id=$patternAirportId) for special target id=$navTargetId")
                    } else {
                        Log.w(TAG, "⚠️ Navigation airport with ID $patternAirportId not found in database")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to restore navigation airport", e)
                }
            } else {
                Log.w(TAG, "⚠️ No navigation airport id saved; cannot fully restore special navigation (id=$navTargetId)")
            }
        } else if (navTargetId > 0) {
            try {
                val target = withContext(Dispatchers.IO) {
                    locationRepository.getLocationById(navTargetId)
                }

                if (target != null) {
                    activeNavigationTarget = target
                    originalAirportTarget = target
                    // Respect saved collapsed/expanded state of the navigation panel
                    autoCenter = false
                    Log.d(TAG, "✅ Restored navigation target: ${target.name} (id=$navTargetId)")
                } else {
                    Log.w(TAG, "⚠️ Navigation target with ID $navTargetId not found in database")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to restore navigation target", e)
            }
        } else {
            Log.d(TAG, "No active navigation target to restore (id=$navTargetId)")
        }

        // Restore runway approach mode
        showRunwayApproach = prefs.getBoolean("show_runway_approach", false)
        finalApproachDistanceNm = prefs.getFloat("final_approach_distance_nm", 5.0f).toDouble()

        // Restore whether the navigation details panel was expanded or collapsed
        showNavigationDetails = prefs.getBoolean("show_navigation_details", false)

        val selectedRwyIdx = prefs.getInt("selected_runway_index", -1)
        if (selectedRwyIdx >= 0) {
            selectedRunwayIndex = selectedRwyIdx
            Log.d(TAG, "Restored selected runway index: $selectedRwyIdx")
        }

        // Restore traffic pattern mode
        showTrafficPattern = prefs.getBoolean("show_traffic_pattern", false)
        patternFinalDistanceNm = prefs.getFloat("pattern_final_distance_nm", 1.0f).toDouble()
        patternSize = PatternSize.fromOrdinal(prefs.getInt("pattern_size_ordinal", PatternSize.NORMAL.ordinal))
        roundedPatternCorners = prefs.getBoolean("rounded_pattern_corners", false)
        patternDirection = if (prefs.getBoolean("pattern_direction_left", true)) PatternDirection.LEFT_HAND else PatternDirection.RIGHT_HAND

        // Restore pattern altitude thresholds
        patternAltitudeSmallToleranceFt = prefs.getFloat("pattern_alt_small_tolerance_ft", 50.0f).toDouble()
        patternAltitudeWarningToleranceFt = prefs.getFloat("pattern_alt_warning_tolerance_ft", 500.0f).toDouble()

        // Restore custom pattern altitude (AGL) if present (-1 means not set)
        val customAlt = prefs.getInt("custom_pattern_altitude_agl_ft", -1)
        customPatternAltitudeAglFt = if (customAlt < 0) null else customAlt
        
        // Restore tactical unit tracking ID for live route updates
        val trackedUnitId = prefs.getInt("active_navigation_tactical_unit_id", -999)
        if (trackedUnitId > 0) {
            activeNavigationTacticalUnitId = trackedUnitId
            Log.d(TAG, "🎯 Restored tactical unit tracking: ID=$trackedUnitId")
        } else {
            activeNavigationTacticalUnitId = null
        }
        
        // Restore airspace display state
        showAirspaceCircles = prefs.getBoolean("show_airspace_circles", false)
        enabledAirspaceClasses = prefs.getStringSet("enabled_airspace_classes", setOf("CLASS_D", "CLASS_C_CTR")) ?: setOf("CLASS_D", "CLASS_C_CTR")
        airspaceFillTransparency = prefs.getFloat("airspace_fill_transparency", 0.10f)
        
        // Restore airspace targets
        val savedAirspaceTargetIds = prefs.getStringSet("airspace_target_ids", emptySet()) ?: emptySet()
        if (savedAirspaceTargetIds.isNotEmpty()) {
            try {
                val restoredTargets = mutableSetOf<LocationEntity>()
                for (idStr in savedAirspaceTargetIds) {
                    val id = idStr.toIntOrNull() ?: continue
                    val location = withContext(Dispatchers.IO) {
                        locationRepository.getLocationById(id)
                    }
                    if (location != null) {
                        restoredTargets.add(location)
                        Log.d(TAG, "✅ Restored airspace target: ${location.name} (id=$id)")
                    } else {
                        Log.w(TAG, "⚠️ Airspace target with ID $id not found in database")
                    }
                }
                airspaceTargets = restoredTargets
                Log.d(TAG, "Restored ${airspaceTargets.size} airspace targets")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to restore airspace targets", e)
                airspaceTargets = emptySet()
            }
        }

        // Small delay before marking as restored
        delay(50)
        navigationRestored = true
    }

    /**
     * Save visible routes to SharedPreferences.
     */
    fun saveVisibleRoutes(visibleRouteIds: Set<Int>) {
        if (!routesRestored) {
            Log.d(TAG, "Skipping save - routes not yet restored")
            return
        }

        val prefs = context.getSharedPreferences("map_routes_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("visible_route_ids", visibleRouteIds.map { it.toString() }.toSet())
            .apply()
        Log.d(TAG, "Saved visible routes: $visibleRouteIds")
    }

    /**
     * Save navigation state to SharedPreferences.
     */
    fun saveNavigationState() {
        if (!navigationRestored) {
            Log.d(TAG, "Skipping navigation save - not yet restored")
            return
        }

        try {
            val prefs = context.getSharedPreferences("map_navigation_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                // Save active navigation target. Prefer pattern sentinel if a pattern is active or requested,
                // because activeNavigationTarget might be a temporary object and null during brief restore transitions.
                val targetId = when {
                    showTrafficPattern -> -2
                    activeNavigationTarget != null -> activeNavigationTarget!!.id
                    else -> -999
                }
                putInt("active_nav_target_id", targetId)

                // Save runway approach state
                putBoolean("show_runway_approach", showRunwayApproach)
                putFloat("final_approach_distance_nm", finalApproachDistanceNm.toFloat())
                putInt("selected_runway_index", selectedRunwayIndex ?: -1)

                // Save traffic pattern state
                putBoolean("show_traffic_pattern", showTrafficPattern)
                putInt("pattern_size_ordinal", patternSize.ordinal)
                putBoolean("pattern_direction_left", patternDirection == PatternDirection.LEFT_HAND)
                putFloat("pattern_final_distance_nm", patternFinalDistanceNm.toFloat())
                // Persist rounded corners preference
                putBoolean("rounded_pattern_corners", roundedPatternCorners)
                // Save pattern altitude thresholds
                putFloat("pattern_alt_small_tolerance_ft", patternAltitudeSmallToleranceFt.toFloat())
                putFloat("pattern_alt_warning_tolerance_ft", patternAltitudeWarningToleranceFt.toFloat())

                // Save custom pattern altitude AGL if set (remove key if null)
                if (customPatternAltitudeAglFt != null) {
                    putInt("custom_pattern_altitude_agl_ft", customPatternAltitudeAglFt!!)
                } else {
                    remove("custom_pattern_altitude_agl_ft")
                }

                // Save the original airport id used for pattern/approach navigation (if any)
                putInt("pattern_airport_id", originalAirportTarget?.id ?: -999)
                // Backwards-compatible key used for both pattern and approach restores
                putInt("nav_airport_id", originalAirportTarget?.id ?: -999)

                // Save whether the navigation details panel is expanded
                putBoolean("show_navigation_details", showNavigationDetails)
                
                // Save tactical unit tracking ID for live route updates
                putInt("active_navigation_tactical_unit_id", activeNavigationTacticalUnitId ?: -999)
                
                // Save airspace display state
                putBoolean("show_airspace_circles", showAirspaceCircles)
                putStringSet("enabled_airspace_classes", enabledAirspaceClasses)
                putFloat("airspace_fill_transparency", airspaceFillTransparency)
                
                // Save airspace target IDs
                val airspaceTargetIds = airspaceTargets.map { it.id.toString() }.toSet()
                putStringSet("airspace_target_ids", airspaceTargetIds)

                apply()
            }
            Log.d(TAG, "💾 Saved navigation state: target=${activeNavigationTarget?.name}, approach=$showRunwayApproach, pattern=$showTrafficPattern, patternSize=$patternSize, navDetailsExpanded=$showNavigationDetails")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save navigation state", e)
        }
    }
    
    /**
     * Save pattern state for a specific marker
     */
    fun saveMarkerPatternState(markerId: Int) {
        try {
            val prefs = context.getSharedPreferences("marker_patterns_prefs", Context.MODE_PRIVATE)
            val state = markerPatterns[markerId]
            
            prefs.edit().apply {
                if (state != null && state.enabled) {
                    // Save pattern state
                    putBoolean("marker_${markerId}_enabled", state.enabled)
                    putInt("marker_${markerId}_size", state.patternSize.ordinal)
                    putBoolean("marker_${markerId}_direction_left", state.patternDirection == PatternDirection.LEFT_HAND)
                    putFloat("marker_${markerId}_final_distance", state.finalDistanceNm.toFloat())
                    putBoolean("marker_${markerId}_rounded", state.roundedCorners)
                    if (state.customAltitudeAglFt != null) {
                        putInt("marker_${markerId}_custom_alt", state.customAltitudeAglFt!!)
                    } else {
                        remove("marker_${markerId}_custom_alt")
                    }
                    if (state.selectedRunwayIndex != null) {
                        putInt("marker_${markerId}_runway_idx", state.selectedRunwayIndex!!)
                    } else {
                        remove("marker_${markerId}_runway_idx")
                    }
                    if (state.selectedRunwayHeading != null) {
                        putFloat("marker_${markerId}_runway_hdg", state.selectedRunwayHeading!!.toFloat())
                    } else {
                        remove("marker_${markerId}_runway_hdg")
                    }
                    putString("marker_${markerId}_manual_hdg", state.manualHeading)
                } else {
                    // Clear all pattern state for this marker
                    remove("marker_${markerId}_enabled")
                    remove("marker_${markerId}_size")
                    remove("marker_${markerId}_direction_left")
                    remove("marker_${markerId}_final_distance")
                    remove("marker_${markerId}_rounded")
                    remove("marker_${markerId}_custom_alt")
                    remove("marker_${markerId}_runway_idx")
                    remove("marker_${markerId}_runway_hdg")
                    remove("marker_${markerId}_manual_hdg")
                }
                apply()
            }
            Log.d(TAG, "💾 Saved pattern state for marker $markerId: enabled=${state?.enabled}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save marker pattern state", e)
        }
    }
    
    /**
     * Load pattern state for a specific marker
     */
    fun loadMarkerPatternState(markerId: Int) {
        try {
            val prefs = context.getSharedPreferences("marker_patterns_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("marker_${markerId}_enabled", false)
            
            if (enabled) {
                val state = MarkerPatternState(
                    enabled = true,
                    patternSize = PatternSize.fromOrdinal(prefs.getInt("marker_${markerId}_size", PatternSize.NORMAL.ordinal)),
                    patternDirection = if (prefs.getBoolean("marker_${markerId}_direction_left", true)) PatternDirection.LEFT_HAND else PatternDirection.RIGHT_HAND,
                    finalDistanceNm = prefs.getFloat("marker_${markerId}_final_distance", 1.0f).toDouble(),
                    roundedCorners = prefs.getBoolean("marker_${markerId}_rounded", false),
                    customAltitudeAglFt = if (prefs.contains("marker_${markerId}_custom_alt")) prefs.getInt("marker_${markerId}_custom_alt", 1200) else null,
                    selectedRunwayIndex = if (prefs.contains("marker_${markerId}_runway_idx")) prefs.getInt("marker_${markerId}_runway_idx", -1) else null,
                    selectedRunwayHeading = if (prefs.contains("marker_${markerId}_runway_hdg")) prefs.getFloat("marker_${markerId}_runway_hdg", 0f).toDouble() else null,
                    manualHeading = prefs.getString("marker_${markerId}_manual_hdg", "") ?: ""
                )
                markerPatterns = markerPatterns + (markerId to state)
                Log.d(TAG, "✅ Loaded pattern state for marker $markerId: size=${state.patternSize}, direction=${state.patternDirection}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load marker pattern state for marker $markerId", e)
        }
    }
    
    /**
     * Load all marker pattern states from SharedPreferences
     */
    suspend fun loadAllMarkerPatternStates() {
        try {
            val prefs = context.getSharedPreferences("marker_patterns_prefs", Context.MODE_PRIVATE)
            val allKeys = prefs.all.keys
            val markerIds = allKeys.mapNotNull { key ->
                if (key.startsWith("marker_") && key.endsWith("_enabled")) {
                    key.removePrefix("marker_").removeSuffix("_enabled").toIntOrNull()
                } else null
            }.toSet()
            
            val loadedPatterns = mutableMapOf<Int, MarkerPatternState>()
            for (markerId in markerIds) {
                val enabled = prefs.getBoolean("marker_${markerId}_enabled", false)
                
                if (enabled) {
                    val state = MarkerPatternState(
                        enabled = true,
                        patternSize = PatternSize.fromOrdinal(prefs.getInt("marker_${markerId}_size", PatternSize.NORMAL.ordinal)),
                        patternDirection = if (prefs.getBoolean("marker_${markerId}_direction_left", true)) PatternDirection.LEFT_HAND else PatternDirection.RIGHT_HAND,
                        finalDistanceNm = prefs.getFloat("marker_${markerId}_final_distance", 1.0f).toDouble(),
                        roundedCorners = prefs.getBoolean("marker_${markerId}_rounded", false),
                        customAltitudeAglFt = if (prefs.contains("marker_${markerId}_custom_alt")) prefs.getInt("marker_${markerId}_custom_alt", 1200) else null,
                        selectedRunwayIndex = if (prefs.contains("marker_${markerId}_runway_idx")) prefs.getInt("marker_${markerId}_runway_idx", -1) else null,
                        selectedRunwayHeading = if (prefs.contains("marker_${markerId}_runway_hdg")) prefs.getFloat("marker_${markerId}_runway_hdg", 0f).toDouble() else null,
                        manualHeading = prefs.getString("marker_${markerId}_manual_hdg", "") ?: ""
                    )
                    loadedPatterns[markerId] = state
                    Log.d(TAG, "✅ Loaded pattern state for marker $markerId: size=${state.patternSize}, direction=${state.patternDirection}")
                }
            }
            
            // Update the entire map at once to trigger recomposition
            markerPatterns = loadedPatterns
            Log.d(TAG, "✅ Loaded ${loadedPatterns.size} marker patterns from preferences")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load all marker pattern states", e)
        }
    }

    /**
     * Load runways for the selected location.
     * Should be called when selected location changes.
     */
    suspend fun loadRunwaysForSelectedLocation() {
        val locId = selectedLocation?.id
        if (tacticalDb == null) {
            selectedRunways = emptyList()
            return
        }
        if (locId != null) {
            tacticalDb!!.runwayDao().getRunwaysByLocation(locId).collect { list ->
                selectedRunways = list
            }
        } else {
            selectedRunways = emptyList()
        }
    }

    /**
     * Load runways for the active navigation target.
     * Should be called when active navigation target changes.
     */
    suspend fun loadRunwaysForActiveTarget() {
        val target = activeNavigationTarget
        // Only update original airport if it's a real location (not a temporary approach point)
        if (target != null && target.id > 0) {
            originalAirportTarget = target
            // Load runways from database
            val db = TacticalDatabase.getInstance(context, useExternalPath = false)
            db.runwayDao().getRunwaysByLocation(target.id).collect { runways ->
                targetRunways = runways

                // Restore selected runway if we have a saved index
                val savedIdx = selectedRunwayIndex
                if (savedIdx != null && savedIdx >= 0 && runways.isNotEmpty()) {
                    // Calculate which runway based on index (each runway has 2 directions)
                    val runwayIdx = savedIdx / 2
                    if (runwayIdx < runways.size) {
                        selectedRunway = runways[runwayIdx]
                        Log.d(TAG, "✅ Restored selected runway: ${selectedRunway?.name} (index=$savedIdx)")
                    }
                }
            }
        } else if (target == null) {
            // Navigation cleared completely
            targetRunways = emptyList()
            originalAirportTarget = null
            showRunwayApproach = false
            selectedRunwayIndex = null
            selectedRunwayHeading = null
            selectedRunway = null
        }
        // If target.id == -1, it's an approach point - keep original airport and runways
    }

    /**
     * Helper function to extract runway heading from runway name.
     */
    fun extractRunwayHeading(runwayName: String): Double? {
        // Parse runway name like "12/30", "09/27", "13L/31R"
        // Extract first number before "/" and multiply by 10
        val match = runwayName.trim().split("/").firstOrNull()?.trim()
        return match?.replace(Regex("[LCR]"), "")?.toIntOrNull()?.times(10)?.toDouble()
    }

    /**
     * Initialize osmdroid configuration.
     */
    fun initializeOsmdroidConfig() {
        org.osmdroid.config.Configuration.getInstance().userAgentValue = context.packageName
        org.osmdroid.config.Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
        )
    }

    /**
     * Resolve and store the name for a pending move marker.
     */
    suspend fun resolvePendingMoveTargetName(markerId: Int, locationRepository: LocationRepository?) {
        pendingMoveTargetName = try {
            locationRepository?.getLocationById(markerId)?.name
        } catch (_: Exception) {
            null
        }
    }
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
