package com.example.checklist_interactive.ui.maps

import android.content.Context
import android.app.Application
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.MotionEvent
import android.graphics.Point
import android.graphics.Paint
import com.example.checklist_interactive.R
import com.example.checklist_interactive.ui.datapad.LocalDataPadManager
import com.example.checklist_interactive.ui.datapad.DataPadPopup
import com.example.checklist_interactive.ui.quickaccess.QuickAccessSheet
import com.example.checklist_interactive.ui.quickaccess.LocalQuickNoteManager
import com.example.checklist_interactive.ui.common.DraggableFab
import com.example.checklist_interactive.ui.common.FABOverlay
import com.example.checklist_interactive.ui.common.MapViewerFABs
import com.example.checklist_interactive.ui.maps.marker.*
import com.example.checklist_interactive.ui.maps.navigation.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * MapViewer - Aviation map display with live position tracking
 * 
 * Features:
 * - OpenStreetMap base layer
 * - Aviation overlays (sectional charts)
 * - Live position marker from DataPad
 * - Smooth position updates
 * - Map controls (center, zoom, layers)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapViewer(
    modifier: Modifier = Modifier,
    isScreenLocked: Boolean = false,
    onLockScreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val dataPadManager = LocalDataPadManager.current
    val quickNoteManager = LocalQuickNoteManager.current
    val flightData by dataPadManager.flightData.collectAsState()
    val isConnected by dataPadManager.isConnected.collectAsState()
    val datapadEnabled by dataPadManager.isEnabled.collectAsState()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var positionMarker by remember { mutableStateOf<Marker?>(null) }
    // Load previous map state from preferences
    val prefsManager = remember { com.example.checklist_interactive.data.prefs.PreferencesManager(context) }
    val savedCenter = remember { prefsManager.getMapCenter() }
    val savedZoom = remember { prefsManager.getMapZoom() }
    var autoCenter by remember { mutableStateOf(if (savedCenter != null) prefsManager.isMapAutoCenterEnabled() else true) }
    var showLayerDialog by remember { mutableStateOf(false) }
    var showOverlayDialog by remember { mutableStateOf(false) }
    var compassEnabled by remember { mutableStateOf(prefsManager.isMapOverlayCompassEnabled()) }
    var rangeRingsEnabled by remember { mutableStateOf(prefsManager.isMapOverlayRangeRingsEnabled()) }
    var rangeRingsMaxNm by remember { mutableStateOf(prefsManager.getMapOverlayRangeRingsMaxNm()) }
    var mgrsGridEnabled by remember { mutableStateOf(prefsManager.isMapOverlayMgrsGridEnabled()) }
    var compassOverlay by remember { mutableStateOf<org.osmdroid.views.overlay.Overlay?>(null) }
    var headingSpeedLineOverlay by remember { mutableStateOf<org.osmdroid.views.overlay.Overlay?>(null) }
    var rangeRingsOverlay by remember { mutableStateOf<org.osmdroid.views.overlay.Overlay?>(null) }
    var mgrsGridOverlay by remember { mutableStateOf<org.osmdroid.views.overlay.Overlay?>(null) }
    var showQuickAccess by remember { mutableStateOf(false) }
    var showDataPad by remember { mutableStateOf(false) }
    var showMarkerRouteManagement by remember { mutableStateOf(false) }
    var showRouteCreation by remember { mutableStateOf(false) }
    var showMilitarySymbolPicker by remember { mutableStateOf(false) }
    
    // Map rotation mode: 0 = North-up, 1 = HDG-up (follow aircraft heading)
    var mapRotationMode by remember { mutableStateOf(0) }

    // Active navigation target
    var activeNavigationTarget by remember { mutableStateOf<com.example.checklist_interactive.data.tactical.LocationEntity?>(null) }
    var navigationLine by remember { mutableStateOf<org.osmdroid.views.overlay.Polyline?>(null) }
    var navigationDistanceNm by remember { mutableStateOf<Double?>(null) }
    var navigationHeading by remember { mutableStateOf<Double?>(null) }

    // Runway approach mode
    var showRunwayApproach by remember { mutableStateOf(false) }
    var targetRunways by remember { mutableStateOf<List<com.example.checklist_interactive.data.tactical.RunwayEntity>>(emptyList()) }
    var runwayApproachLines by remember { mutableStateOf<List<org.osmdroid.views.overlay.Polyline>>(emptyList()) }
    var selectedRunwayIndex by remember { mutableStateOf<Int?>(null) }
    var originalAirportTarget by remember { mutableStateOf<com.example.checklist_interactive.data.tactical.LocationEntity?>(null) }
    var selectedRunwayHeading by remember { mutableStateOf<Double?>(null) }
    var finalApproachDistanceNm by remember { mutableStateOf(5.0) }
    var selectedRunway by remember { mutableStateOf<com.example.checklist_interactive.data.tactical.RunwayEntity?>(null) }

    // Traffic pattern mode
    var showTrafficPattern by remember { mutableStateOf(false) }
    var trafficPatternPolyline by remember { mutableStateOf<org.osmdroid.views.overlay.Polyline?>(null) }
    var trafficPatternLabelOverlay by remember { mutableStateOf<PatternLabelOverlay?>(null) }
    var patternSize by remember { mutableStateOf(PatternSize.NORMAL) }
    var patternDirection by remember { mutableStateOf(PatternDirection.LEFT_HAND) }
    var patternFinalDistanceNm by remember { mutableStateOf(1.0) } // Configurable final approach length
    // Collapsible details
    var showPatternDetails by remember { mutableStateOf(true) }
    // Collapsible navigation display
    var showNavigationDetails by remember { mutableStateOf(true) }

    // Military symbol placement state
    var pendingSymbolPlacement by remember { mutableStateOf<Pair<MilitarySymbol, SymbolAffiliation>?>(null) }

    // Pending move marker id (driven by MapActionBus)
    val pendingMoveMarkerIdFlow = MapActionBus.pendingMoveMarkerId
    val pendingMoveMarkerId by pendingMoveMarkerIdFlow.collectAsState(initial = null)

    var pendingMoveTargetName by remember { mutableStateOf<String?>(null) }
    
    // Store last valid player position to prevent reset during recompositions
    var lastValidPlayerPosition by remember { mutableStateOf<GeoPoint?>(null) }
    // Store last processed timestamp to prevent accepting old/cached data
    var lastProcessedTimestamp by remember { mutableStateOf<String?>(null) }

    // Selected location for marker details in management sheet
    var selectedLocation by remember { mutableStateOf<com.example.checklist_interactive.data.tactical.LocationEntity?>(null) }
    var selectedRunways by remember { mutableStateOf<List<com.example.checklist_interactive.data.tactical.RunwayEntity>>(emptyList()) }

    // Radial menu state
    var radialMenuVisible by remember { mutableStateOf(false) }
    var radialMenuX by remember { mutableStateOf(0) }
    var radialMenuY by remember { mutableStateOf(0) }
    var radialMenuMarker by remember { mutableStateOf<com.example.checklist_interactive.data.tactical.LocationEntity?>(null) }

    // Track last long-pressed marker to suppress immediately following click
    var lastLongPressedMarkerId by remember { mutableStateOf<Int?>(null) }
    var lastLongPressTime by remember { mutableStateOf(0L) }

    // Map from Marker object to LocationEntity to robustly find marker data
    val markerToLocation = remember { mutableMapOf<org.osmdroid.views.overlay.Marker, com.example.checklist_interactive.data.tactical.LocationEntity>() }

    // Create a coroutine scope for state updates from listeners
    val scope = rememberCoroutineScope()

    // Density for dp<->px conversions needed by effects below
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    // Initialize tactical database and repositories (async to avoid blocking UI)
    var tacticalDb by remember { mutableStateOf<com.example.checklist_interactive.data.tactical.TacticalDatabase?>(null) }
    var dbInitFailed by remember { mutableStateOf(false) }
    var dbInitError by remember { mutableStateOf<String?>(null) }
    val dbReady = tacticalDb != null

    // Initialize DB off the main thread to avoid long blocking operations during composition
    LaunchedEffect(Unit) {
        // Don't block UI - perform DB init on IO
        android.util.Log.d("MapViewer", "Starting TacticalDatabase initialization...")
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                android.util.Log.d("MapViewer", "Calling TacticalDatabase.getInstance()...")
                val db = kotlinx.coroutines.withTimeout(10000L) { // 10 second timeout
                    com.example.checklist_interactive.data.tactical.TacticalDatabase.getInstance(context, useExternalPath = false)
                }
                android.util.Log.d("MapViewer", "TacticalDatabase.getInstance() completed successfully")
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    tacticalDb = db
                    android.util.Log.d("MapViewer", "TacticalDatabase assigned to state variable - DB ready!")
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                android.util.Log.e("MapViewer", "TacticalDatabase initialization timed out after 10 seconds", e)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    dbInitFailed = true
                    dbInitError = "Zeitüberschreitung beim Laden der Datenbank"
                }
            } catch (e: Exception) {
                android.util.Log.e("MapViewer", "Failed to initialize TacticalDatabase", e)
                e.printStackTrace()
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    dbInitFailed = true
                    dbInitError = e.message ?: "Unbekannter Fehler"
                }
            }
        }
    }

    // Repositories and viewmodels are created when DB is available
    val locationRepository = remember(tacticalDb) { 
        tacticalDb?.let { 
            android.util.Log.d("MapViewer", "Creating LocationRepository from tacticalDb")
            com.example.checklist_interactive.data.tactical.LocationRepositoryImpl(it.locationDao()) 
        }.also {
            android.util.Log.d("MapViewer", "LocationRepository state: ${if (it != null) "READY" else "NULL"}")
        }
    }
    val routeRepository = remember(tacticalDb) { 
        tacticalDb?.let { 
            com.example.checklist_interactive.data.tactical.RouteRepositoryImpl(it.routeDao(), it.locationDao()) 
        }
    }
    val markerRouteViewModel = remember(tacticalDb) { if (locationRepository != null && routeRepository != null) MarkerRouteViewModel(locationRepository, routeRepository) else null }
    val routeCreationViewModel = remember(tacticalDb) { if (routeRepository != null && locationRepository != null && tacticalDb != null) MultiWaypointRouteViewModel(context.applicationContext as Application, routeRepository, locationRepository, tacticalDb!!.runwayDao()) else null }

    // Combined ready state: DB AND repositories must be initialized
    val repositoriesReady = dbReady && locationRepository != null && routeRepository != null

    // Track whether we've completed the initial route restoration to prevent overwriting saved state
    var routesRestored by remember { mutableStateOf(false) }
    var navigationRestored by remember { mutableStateOf(false) }

    // Load and restore visible routes from SharedPreferences when MarkerRouteViewModel becomes available
    LaunchedEffect(markerRouteViewModel) {
        // Reset flag each time the viewmodel instance changes so we don't accidentally
        // enable saving before restoration for a newly created viewmodel
        routesRestored = false

        // Only proceed when a non-null ViewModel exists
        val vm = markerRouteViewModel
        if (vm == null) {
            android.util.Log.d("MapViewer", "MarkerRouteViewModel not ready - deferring route restoration")
            return@LaunchedEffect
        }

        val prefs = context.getSharedPreferences("map_routes_prefs", android.content.Context.MODE_PRIVATE)
        val savedRouteIds = prefs.getStringSet("visible_route_ids", emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet() ?: emptySet()

        // Apply the saved route set to the ready ViewModel
        vm.setVisibleRoutes(savedRouteIds)
        android.util.Log.d("MapViewer", "Restored visible routes: $savedRouteIds")

        // Small delay to ensure collectors process the change before we re-enable saving
        kotlinx.coroutines.delay(50)
        routesRestored = true
    }

    // Restore solo navigation state from SharedPreferences
    LaunchedEffect(dbReady, locationRepository) {
        if (!dbReady || locationRepository == null) {
            android.util.Log.d("MapViewer", "Navigation restore deferred: dbReady=$dbReady, locationRepository=${locationRepository != null}")
            return@LaunchedEffect
        }
        navigationRestored = false

        val prefs = context.getSharedPreferences("map_navigation_prefs", android.content.Context.MODE_PRIVATE)
        
        // Restore active navigation target
        val navTargetId = prefs.getInt("active_nav_target_id", -999)
        android.util.Log.d("MapViewer", "Attempting to restore navigation target with ID: $navTargetId")
        
        if (navTargetId == -2 || navTargetId == -1) {
            // Special-case: Pattern (-2) and approach (-1) navigation use temporary targets and
            // must be reconstructed from the original airport ID and saved runway index.
            // Support both legacy 'pattern_airport_id' and newer 'nav_airport_id'.
            val patternAirportId = prefs.getInt("nav_airport_id", prefs.getInt("pattern_airport_id", -999))
            if (patternAirportId > 0) {
                try {
                    val airport = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        locationRepository.getLocationById(patternAirportId)
                    }
                    if (airport != null) {
                        // Set the active navigation target to the airport so downstream effects will
                        // load runways and then the appropriate effect (approach or pattern) can create the temporary target.
                        activeNavigationTarget = airport
                        // Make sure the navigation UI is shown so details & buttons appear immediately
                        showNavigationDetails = true
                        autoCenter = false
                        android.util.Log.d("MapViewer", "✅ Restored navigation airport: ${airport.name} (id=$patternAirportId) for special target id=$navTargetId")
                    } else {
                        android.util.Log.w("MapViewer", "⚠️ Navigation airport with ID $patternAirportId not found in database")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MapViewer", "❌ Failed to restore navigation airport", e)
                }
            } else {
                android.util.Log.w("MapViewer", "⚠️ No navigation airport id saved; cannot fully restore special navigation (id=$navTargetId)")
            }
        } else if (navTargetId > 0) {
            try {
                val target = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    locationRepository.getLocationById(navTargetId)
                }
                
                if (target != null) {
                    activeNavigationTarget = target
                    originalAirportTarget = target
                    // Make navigation UI visible immediately
                    showNavigationDetails = true
                    autoCenter = false
                    android.util.Log.d("MapViewer", "✅ Restored navigation target: ${target.name} (id=$navTargetId)")
                } else {
                    android.util.Log.w("MapViewer", "⚠️ Navigation target with ID $navTargetId not found in database")
                }
            } catch (e: Exception) {
                android.util.Log.e("MapViewer", "❌ Failed to restore navigation target", e)
            }
        } else {
            android.util.Log.d("MapViewer", "No active navigation target to restore (id=$navTargetId)")
        }

        // Restore runway approach mode
        showRunwayApproach = prefs.getBoolean("show_runway_approach", false)
        finalApproachDistanceNm = prefs.getFloat("final_approach_distance_nm", 5.0f).toDouble()
        
        val selectedRwyIdx = prefs.getInt("selected_runway_index", -1)
        if (selectedRwyIdx >= 0) {
            selectedRunwayIndex = selectedRwyIdx
            android.util.Log.d("MapViewer", "Restored selected runway index: $selectedRwyIdx")
            // Runway entity will be restored via the runway-loading effect when activeNavigationTarget is set
        }

        // Restore traffic pattern mode
        showTrafficPattern = prefs.getBoolean("show_traffic_pattern", false)
        patternSize = PatternSize.fromOrdinal(prefs.getInt("pattern_size_ordinal", PatternSize.NORMAL.ordinal))
        patternDirection = if (prefs.getBoolean("pattern_direction_left", true)) PatternDirection.LEFT_HAND else PatternDirection.RIGHT_HAND
        patternFinalDistanceNm = prefs.getFloat("pattern_final_distance_nm", 1.0f).toDouble()

        android.util.Log.d("MapViewer", "✅ Restored navigation modes: approach=$showRunwayApproach, pattern=$showTrafficPattern, patternSize=$patternSize, patternDir=$patternDirection")

        kotlinx.coroutines.delay(50)
        navigationRestored = true
    }

    // Load runways for the selected location from the DB (only when DB is ready)
    LaunchedEffect(selectedLocation?.id, dbReady) {
        val locId = selectedLocation?.id
        if (!dbReady) {
            selectedRunways = emptyList()
            return@LaunchedEffect
        }
        if (locId != null) {
            tacticalDb!!.runwayDao().getRunwaysByLocation(locId).collect { list ->
                selectedRunways = list
            }
        } else {
            selectedRunways = emptyList()
        }
    }

    // Observe visible routes and draw them on map
    val visibleRouteIds by (markerRouteViewModel?.visibleRouteIds?.collectAsState(initial = emptySet()) ?: remember { mutableStateOf(emptySet<Int>()) })
    // Also observe all routes so changes (e.g. color updates) trigger a redraw
    val allRoutesForRedraw by (markerRouteViewModel?.allRoutes?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList<com.example.checklist_interactive.data.tactical.RouteEntity>()) })

    // Save visible routes to SharedPreferences when they change (but only after initial restoration completes)
    LaunchedEffect(visibleRouteIds, routesRestored) {
        // Skip saving during initial restoration phase to prevent overwriting saved state
        if (!routesRestored) {
            android.util.Log.d("MapViewer", "Skipping save - routes not yet restored")
            return@LaunchedEffect
        }
        
        val prefs = context.getSharedPreferences("map_routes_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("visible_route_ids", visibleRouteIds.map { it.toString() }.toSet())
            .apply()
        android.util.Log.d("MapViewer", "Saved visible routes: $visibleRouteIds")
    }

    // Save navigation state when it changes (but only after initial restoration completes)
    LaunchedEffect(
        activeNavigationTarget?.id,
        showRunwayApproach,
        showTrafficPattern,
        selectedRunwayIndex,
        finalApproachDistanceNm,
        patternSize,
        patternDirection,
        patternFinalDistanceNm,
        navigationRestored
    ) {
        // Skip saving during initial restoration phase
        if (!navigationRestored) {
            android.util.Log.d("MapViewer", "Skipping navigation save - not yet restored")
            return@LaunchedEffect
        }

        try {
            val navPrefs = context.getSharedPreferences("map_navigation_prefs", android.content.Context.MODE_PRIVATE)
            navPrefs.edit().apply {
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
                // Save the original airport id used for pattern/approach navigation (if any)
                putInt("pattern_airport_id", originalAirportTarget?.id ?: -999)
                // Backwards-compatible key used for both pattern and approach restores
                putInt("nav_airport_id", originalAirportTarget?.id ?: -999)
                
                apply()
            }
            android.util.Log.d("MapViewer", "💾 Saved navigation state: target=${activeNavigationTarget?.name}, approach=$showRunwayApproach, pattern=$showTrafficPattern, patternSize=$patternSize")
        } catch (e: Exception) {
            android.util.Log.e("MapViewer", "Failed to save navigation state", e)
        }
    }
    LaunchedEffect(visibleRouteIds, mapView, allRoutesForRedraw) {
        val mv = mapView ?: return@LaunchedEffect
        
        // Remove all existing route overlays (Polylines, RouteTextOverlay and route markers)
        mv.overlays.removeAll { overlay ->
            overlay is org.osmdroid.views.overlay.Polyline ||
            overlay is RouteTextOverlay ||
            (overlay is org.osmdroid.views.overlay.Marker && overlay.id?.startsWith("route_") == true)
        }
        
        // Draw all visible routes
        visibleRouteIds.forEach { routeId ->
            val routeData = routeRepository?.getRouteWithWaypoints(routeId)
            routeData?.let { data ->
                val waypoints = data.waypoints.map { wpWithLoc ->
                    Triple(
                        wpWithLoc.location,
                        wpWithLoc.waypoint.distanceNm,
                        wpWithLoc.waypoint.headingMag
                    )
                }
                if (waypoints.size >= 2) {
                    // Parse route color or use default
                    val routeColor = try {
                        android.graphics.Color.parseColor(data.route.color)
                    } catch (e: Exception) {
                        android.graphics.Color.parseColor("#00A8FF") // Default blue
                    }

                    // Create polyline with route's color
                    val polyline = org.osmdroid.views.overlay.Polyline(mv).apply {
                        outlinePaint.color = routeColor
                        outlinePaint.strokeWidth = 8f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        val points = waypoints.map { (loc, _, _) ->
                            org.osmdroid.util.GeoPoint(loc.latitude, loc.longitude)
                        }
                        setPoints(points)
                        id = "route_polyline_$routeId"
                    }
                    mv.overlays.add(polyline)

                    // Create text overlay for distance/heading labels
                    val textOverlay = RouteTextOverlay()

                    // Add distance/heading labels on route segments
                    for (i in 0 until waypoints.size - 1) {
                        val (loc1, distNm, heading) = waypoints[i]  // Distance/heading FROM this waypoint to next
                        val (loc2, _, _) = waypoints[i + 1]

                        android.util.Log.d("MapViewer", "Segment $i to ${i+1}: distNm=$distNm, heading=$heading")

                        if (distNm != null && heading != null) {
                            // Calculate midpoint
                            val midLat = (loc1.latitude + loc2.latitude) / 2
                            val midLon = (loc1.longitude + loc2.longitude) / 2

                            // Add label to text overlay (show heading first, then distance)
                            val labelText = String.format("HDG %03.0f° 	• %.1f NM", heading, distNm)
                            android.util.Log.d("MapViewer", "Adding label: $labelText at ($midLat, $midLon)")
                            textOverlay.addLabel(org.osmdroid.util.GeoPoint(midLat, midLon), labelText)
                        }
                    }

                    android.util.Log.d("MapViewer", "Total labels in overlay: ${textOverlay.getLabelCount()}")
                    mv.overlays.add(textOverlay)
                }
            }
        }
        
        mv.invalidate()
    }

    // Track last programmatic map movement to avoid treating it as a user scroll
    val lastProgrammaticMove = remember { mutableStateOf(0L) }
    // Track last user touch time so we only disable auto-center on real user interactions
    val lastUserTouch = remember { mutableStateOf(0L) }
    // When user presses the center button but the MapView isn't attached yet, store the requested center
    val pendingCenter = remember { mutableStateOf<GeoPoint?>(null) }
    val TAG = "MapViewer"
    
    // Initialize osmdroid configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    }
    
    // Map theme helper
    val isDarkTheme = isSystemInDarkTheme()

    // Helper: create an airplane emoji bitmap drawable (no rotation - handled by Marker.rotation)
    fun createPlaneDrawable(ctx: Context, sizeDp: Float, color: Int): BitmapDrawable {
        val emoji = "\u2708\uFE0F"
        val metrics = ctx.resources.displayMetrics
        val sizePx = (sizeDp * metrics.density).toInt().coerceAtLeast(16)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = sizePx.toFloat()
            this.color = color
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val baseline = -paint.ascent()
        val width = sizePx
        val height = (baseline + paint.descent()).toInt().coerceAtLeast(sizePx)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawText(emoji, width / 2f, baseline, paint)
        return BitmapDrawable(ctx.resources, bitmap)
    }

    // Update live navigation line when flight data or target changes
    LaunchedEffect(flightData, activeNavigationTarget, mapRotationMode) {
        val data = flightData
        val target = activeNavigationTarget
        val map = mapView
        
        // If rotation mode is HDG-up, update map orientation to latest heading
        // Negate the heading so the player's heading direction always points up (north position on screen)
        if (mapRotationMode == 1 && data != null && map != null) {
            try {
                map.setMapOrientation(-Math.toDegrees(data.heading).toFloat()) 
                // Ensure the player is visually placed at the top-center so the heading line points to the top center
                try {
                    val playerPos = GeoPoint(data.latitude, data.longitude)
                    // Center on player then offset upward so player sits at top-center with some padding
                    map.controller.animateTo(playerPos)
                    // Use a small delay to allow controller to apply before scrolling (non-blocking)
                    kotlinx.coroutines.delay(30L)
                    val desiredTopDp = 72.dp
                    val desiredTopPx = with(density) { desiredTopDp.toPx() }
                    val offsetPx = ((map.height / 2f) - desiredTopPx).toInt()
                    // Scroll map so player moves from center to the desired top offset
                    map.scrollBy(0, -offsetPx)
                } catch (_: Throwable) {}
            } catch (_: Throwable) {}
        }

        if (data != null && target != null && map != null && data.latitude != 0.0 && data.longitude != 0.0) {
            val playerPos = GeoPoint(data.latitude, data.longitude)
            val targetPos = GeoPoint(target.latitude, target.longitude)
            
            // Calculate distance and heading
            val distanceMeters = playerPos.distanceToAsDouble(targetPos)
            val distanceNm = distanceMeters / 1852.0
            navigationDistanceNm = distanceNm
            
            // Calculate bearing/heading
            val lat1 = Math.toRadians(playerPos.latitude)
            val lat2 = Math.toRadians(targetPos.latitude)
            val lon1 = Math.toRadians(playerPos.longitude)
            val lon2 = Math.toRadians(targetPos.longitude)
            val dLon = lon2 - lon1
            val y = Math.sin(dLon) * Math.cos(lat2)
            val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
            val bearing = (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
            navigationHeading = bearing
            
            // Update or create red navigation line
            scope.launch {
                val line = navigationLine ?: org.osmdroid.views.overlay.Polyline(map).apply {
                    outlinePaint.color = android.graphics.Color.argb(128, 255, 0, 0) // 50% transparent red
                    outlinePaint.strokeWidth = 10f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    map.overlays.add(this)
                    navigationLine = this
                }
                line.setPoints(listOf(playerPos, targetPos))
                map.invalidate()
            }
        } else if (target == null && navigationLine != null) {
            // Remove navigation line if target cleared
            scope.launch {
                mapView?.overlays?.remove(navigationLine)
                navigationLine = null
                navigationDistanceNm = null
                navigationHeading = null
                mapView?.invalidate()
            }
        }
    }

    // Load runways for active navigation target
    LaunchedEffect(activeNavigationTarget) {
        val target = activeNavigationTarget
        // Only update original airport if it's a real location (not a temporary approach point)
        if (target != null && target.id > 0) {
            originalAirportTarget = target
            // Load runways from database
            val db = com.example.checklist_interactive.data.tactical.TacticalDatabase.getInstance(context, useExternalPath = false)
            db.runwayDao().getRunwaysByLocation(target.id).collect { runways ->
                targetRunways = runways
                
                // Restore selected runway if we have a saved index
                val savedIdx = selectedRunwayIndex
                if (savedIdx != null && savedIdx >= 0 && runways.isNotEmpty()) {
                    // Calculate which runway based on index (each runway has 2 directions)
                    val runwayIdx = savedIdx / 2
                    if (runwayIdx < runways.size) {
                        selectedRunway = runways[runwayIdx]
                        android.util.Log.d("MapViewer", "✅ Restored selected runway: ${selectedRunway?.name} (index=$savedIdx)")
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

    // Helper function to extract runway heading from runway name
    fun extractRunwayHeading(runwayName: String): Double? {
        // Parse runway name like "12/30", "09/27", "13L/31R"
        // Extract first number before "/" and multiply by 10
        val match = runwayName.trim().split("/").firstOrNull()?.trim()
        return match?.replace(Regex("[LCR]"), "")?.toIntOrNull()?.times(10)?.toDouble()
    }

    // Draw runway approach lines when enabled
    LaunchedEffect(showRunwayApproach, targetRunways, originalAirportTarget, mapView, finalApproachDistanceNm) {
        val map = mapView
        val target = originalAirportTarget

        // Remove existing approach lines
        runwayApproachLines.forEach { line ->
            map?.overlays?.remove(line)
        }
        runwayApproachLines = emptyList()

        if (showRunwayApproach && target != null && map != null && targetRunways.isNotEmpty()) {
            val newLines = mutableListOf<org.osmdroid.views.overlay.Polyline>()

            targetRunways.forEach { runway ->
                val heading = runway.headingDeg ?: extractRunwayHeading(runway.name) ?: 0.0
                val center = GeoPoint(target.latitude, target.longitude)

                // Calculate final approach distance in meters
                val distanceMeters = finalApproachDistanceNm * 1852.0

                // Direction 1: heading
                val rad1 = Math.toRadians(heading)
                val lat1 = Math.toRadians(center.latitude)
                val lon1 = Math.toRadians(center.longitude)
                val dLat1 = distanceMeters * Math.cos(rad1) / 6371000.0
                val dLon1 = distanceMeters * Math.sin(rad1) / (6371000.0 * Math.cos(lat1))
                val endLat1 = lat1 + dLat1
                val endLon1 = lon1 + dLon1
                val endpoint1 = GeoPoint(Math.toDegrees(endLat1), Math.toDegrees(endLon1))

                // Direction 2: opposite heading
                val heading2 = (heading + 180) % 360
                val rad2 = Math.toRadians(heading2)
                val dLat2 = distanceMeters * Math.cos(rad2) / 6371000.0
                val dLon2 = distanceMeters * Math.sin(rad2) / (6371000.0 * Math.cos(lat1))
                val endLat2 = lat1 + dLat2
                val endLon2 = lon1 + dLon2
                val endpoint2 = GeoPoint(Math.toDegrees(endLat2), Math.toDegrees(endLon2))

                // Create lines for both directions
                val line1 = org.osmdroid.views.overlay.Polyline(map).apply {
                    outlinePaint.color = android.graphics.Color.argb(128, 255, 255, 0) // 50% transparent yellow
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    setPoints(listOf(center, endpoint1))
                    map.overlays.add(this)
                }

                val line2 = org.osmdroid.views.overlay.Polyline(map).apply {
                    outlinePaint.color = android.graphics.Color.argb(128, 255, 255, 0) // 50% transparent yellow
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    setPoints(listOf(center, endpoint2))
                    map.overlays.add(this)
                }

                newLines.add(line1)
                newLines.add(line2)
            }

            runwayApproachLines = newLines
            map.invalidate()
        }
    }

    // Recalculate approach point when final approach distance changes
    LaunchedEffect(finalApproachDistanceNm, selectedRunwayIndex) {
        val index = selectedRunwayIndex
        val runway = selectedRunway
        val target = originalAirportTarget

        if (index != null && runway != null && target != null) {
            // Determine if it's direction 1 or direction 2
            val isDirection1 = (index % 2) == 0
            val baseHeading = runway.headingDeg ?: extractRunwayHeading(runway.name) ?: 0.0
            val heading = if (isDirection1) {
                baseHeading
            } else {
                (baseHeading + 180) % 360
            }

            // Recalculate approach endpoint with new distance
            val distanceMeters = finalApproachDistanceNm * 1852.0
            val rad = Math.toRadians(heading)
            val lat1 = Math.toRadians(target.latitude)
            val lon1 = Math.toRadians(target.longitude)
            val dLat = distanceMeters * Math.cos(rad) / 6371000.0
            val dLon = distanceMeters * Math.sin(rad) / (6371000.0 * Math.cos(lat1))
            val endLat = lat1 + dLat
            val endLon = lon1 + dLon
            val endpoint = GeoPoint(Math.toDegrees(endLat), Math.toDegrees(endLon))

            // Update navigation target with new approach point
            val approachTarget = target.copy(
                id = -1,
                name = "${target.name} RWY ${String.format("%02d", heading.toInt() / 10)}",
                latitude = endpoint.latitude,
                longitude = endpoint.longitude
            )
            activeNavigationTarget = approachTarget
            selectedRunwayHeading = heading
        }
    }

    // Generate and draw traffic pattern when enabled
    LaunchedEffect(showTrafficPattern, selectedRunway, mapView, patternSize, patternDirection, originalAirportTarget, patternFinalDistanceNm) {
        val mv = mapView ?: return@LaunchedEffect
        val runway = selectedRunway ?: return@LaunchedEffect
        val target = originalAirportTarget ?: return@LaunchedEffect
        
        if (showTrafficPattern) {
            // Remove old pattern overlays
            trafficPatternPolyline?.let { mv.overlays.remove(it) }
            trafficPatternLabelOverlay?.let { mv.overlays.remove(it) }
            
            // Extract runway heading from name or use provided heading
            val runwayHeading = runway.headingDeg ?: extractRunwayHeading(runway.name) ?: 0.0
            val runwayLengthMeters = (runway.lengthM?.toDouble() ?: runway.lengthFt?.toDouble()?.times(0.3048)) ?: 2000.0
            val runwayThreshold = GeoPoint(
                runway.touchdownStartLat ?: target.latitude,
                runway.touchdownStartLon ?: target.longitude
            )
            
            // Generate pattern points
            val patternPoints = TrafficPatternGenerator.generateTrafficPattern(
                runwayThreshold = runwayThreshold,
                runwayHeading = runwayHeading,
                runwayLengthMeters = runwayLengthMeters,
                patternSize = patternSize,
                direction = patternDirection,
                finalDistanceNm = patternFinalDistanceNm
            )
            
            // Create and add pattern polyline
            val polyline = TrafficPatternGenerator.createPatternPolyline(
                points = patternPoints,
                color = 0xFF00FF00.toInt(), // Green for pattern
                width = 5f
            )
            mv.overlays.add(polyline)
            trafficPatternPolyline = polyline
            
            // Create and add pattern labels with distance and heading information
            val labels = TrafficPatternGenerator.generatePatternLabels(
                points = patternPoints,
                direction = patternDirection,
                runwayHeading = runwayHeading
            )
            val labelOverlay = PatternLabelOverlay(labels)
            mv.overlays.add(labelOverlay)
            trafficPatternLabelOverlay = labelOverlay
            
            // Set navigation target to runway threshold (landing point)
            // This creates a red line from current position to the pattern landing point
            val patternTarget = target.copy(
                id = -2, // Special ID for pattern navigation
                name = "${target.name} PATTERN ${String.format("%02d", runwayHeading.toInt() / 10)}",
                latitude = runwayThreshold.latitude,
                longitude = runwayThreshold.longitude
            )
            activeNavigationTarget = patternTarget
            // Ensure UI appears when pattern is generated
            showNavigationDetails = true
            autoCenter = false
            
            mv.invalidate()
        } else {
            // Remove pattern overlays when disabled
            trafficPatternPolyline?.let { 
                mv.overlays.remove(it)
                trafficPatternPolyline = null
            }
            trafficPatternLabelOverlay?.let {
                mv.overlays.remove(it)
                trafficPatternLabelOverlay = null
            }
            // Clear navigation if it was pattern navigation (id = -2)
            if (activeNavigationTarget?.id == -2) {
                activeNavigationTarget = null
            }
            mv.invalidate()
        }
    }

    // Update position marker when flight data changes
    LaunchedEffect(flightData, autoCenter) {
        val data = flightData
        val marker = positionMarker
        val map = mapView

        Log.d(TAG, "LaunchedEffect triggered: data=$data, marker=$marker, map=$map, autoCenter=$autoCenter")

        if (data != null && marker != null && map != null) {
            val lat = data.latitude
            val lon = data.longitude

            Log.d(TAG, "Position data: lat=$lat, lon=$lon, timestamp=${data.timestamp}")

            // Validate timestamp to prevent old/cached data from resetting position
            val currentTimestamp = data.timestamp
            // local stable copy to avoid smart-cast / delegated-property issues
            val lastTs = lastProcessedTimestamp
            if (currentTimestamp != null && lastTs != null) {
                try {
                    val curInst = java.time.Instant.parse(currentTimestamp)
                    val lastInst = java.time.Instant.parse(lastTs)
                    if (!curInst.isAfter(lastInst)) {
                        Log.w(TAG, "⚠️ REJECTED OLD DATA: timestamp=$currentTimestamp (last=$lastTs) - preventing position reset!")
                        return@LaunchedEffect
                    }
                } catch (e: Exception) {
                    // Fallback to lexicographic comparison for ISO-8601; works for same-format timestamps
                    if (currentTimestamp <= lastTs) {
                        Log.w(TAG, "⚠️ REJECTED OLD DATA (fallback compare): timestamp=$currentTimestamp (last=$lastTs) - preventing position reset!")
                        return@LaunchedEffect
                    }
                }
            }

            if (lat != 0.0 && lon != 0.0) {
                // Update last processed timestamp if available
                if (currentTimestamp != null) lastProcessedTimestamp = currentTimestamp
                val newPosition = GeoPoint(lat, lon)
                Log.d(TAG, "✅ Accepted data with timestamp=$currentTimestamp")
                // Store as last valid position to prevent reset
                lastValidPlayerPosition = newPosition
                marker.position = newPosition
                // use center anchor so rotation pivots around icon center
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                // Use raw datapad heading for the player marker
                val rawHeading = data.heading.toFloat()
                // Convert raw heading to degrees for overlays/labels
                val headingDeg = Math.toDegrees(rawHeading.toDouble()).toFloat()
                // Adjust rotation: apply rotationOffset to compensate for emoji's default orientation
                // 45° = 135° (previous) - 90° (to compensate for emoji default orientation)
                val rotationOffset = 45f
                val computedRotation = if (mapRotationMode == 1) {
                    // HDG-up mode: constant rotation (map rotates, icon stays pointing up)
                    rotationOffset
                } else {
                    // North-up mode: rotate based on heading
                    (rotationOffset - headingDeg + 360f) % 360f
                }
                marker.rotation = computedRotation
                Log.d(TAG, "headingDeg=$headingDeg rotationOffset=$rotationOffset computedRotation=$computedRotation mapRotationMode=$mapRotationMode")
                try {
                    val color = if (isDarkTheme) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                    // rely on Marker.rotation for rotation; use unrotated bitmap
                    marker.icon = createPlaneDrawable(context, 28f, color)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                } catch (e: Exception) {
                    // ignore
                }

                // Update marker snippet with altitude and speed
                val altFt = (data.altitude * 3.28084).toInt()
                val speedSource = data.groundSpeed ?: data.trueAirspeed ?: data.indicatedAirspeed ?: 0.0
                val speedKts = (speedSource * 1.9438)
                val baseSnippet = context.getString(R.string.marker_snippet_fmt, altFt, speedKts.toInt(), Math.toDegrees(data.heading).toInt())
                marker.snippet = if (data.unitName.isNotBlank()) {
                    baseSnippet + "\n\n" + context.getString(R.string.marker_pilot_fmt, data.unitName)
                } else {
                    baseSnippet
                }
                marker.title = data.aircraft

                // Auto-center map on position if enabled
                if (autoCenter) {
                    Log.d(TAG, "Auto-centering enabled, animating to: $lat,$lon")
                    lastProgrammaticMove.value = System.currentTimeMillis()
                    map.controller.animateTo(newPosition)
                } else {
                    Log.d(TAG, "Auto-center is disabled, not animating")
                }

                // Update overlays (compass + heading line + range rings)
                try {
                    // compass overlay (fixed size, scales with zoom)
                    (compassOverlay as? CompassOverlay)?.let { co ->
                        co.center = GeoPoint(lat, lon)
                        co.heading = headingDeg
                    }
                    // heading speed line overlay (length scales with speed)
                    (headingSpeedLineOverlay as? HeadingSpeedLineOverlay)?.let { hsl ->
                        hsl.center = GeoPoint(lat, lon)
                        hsl.heading = headingDeg
                        hsl.speedKts = speedKts
                    }
                    // range rings center
                    (rangeRingsOverlay as? RangeRingsOverlay)?.let { rr ->
                        rr.center = GeoPoint(lat, lon)
                        rr.heading = headingDeg
                        // pass through speed for scaling the heading radial
                        rr.speedKts = speedKts
                    }
                } catch (e: Exception) {
                    // ignore
                }

                map.invalidate()

                // Persist marker position as last known if user hasn't moved map
                if (prefsManager.getMapCenter() == null) {
                    prefsManager.setMapCenter(lat, lon)
                    prefsManager.setMapZoom(map.zoomLevelDouble)
                }
            }
        }
    }
    
    // Compute layout metrics so map and FABs are bounded correctly (exclude TabBar height)
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val tabBarHeightPx = with(density) { 40.dp.roundToPx() } // matches TabBar height
    val effectiveScreenHeightPx = (screenHeightPx - tabBarHeightPx).coerceAtLeast(1)
    val fabSizePx = with(density) { 56.dp.roundToPx() }
    val fabMarginPx = with(density) { 12.dp.roundToPx() }

    // Determine initial center/zoom: prefer last valid player position, then saved values, fall back to latest flightData, then default
    val initialCenter: GeoPoint? = lastValidPlayerPosition
        ?: savedCenter?.let { GeoPoint(it.first, it.second) }
        ?: flightData?.let { if (it.latitude != 0.0 && it.longitude != 0.0) GeoPoint(it.latitude, it.longitude) else null }
    val initialZoom: Double = savedZoom ?: 8.0

    // Map theme / tile helpers (moved out so they are accessible from the layer dialog handler)
    val darkTile = org.osmdroid.tileprovider.tilesource.XYTileSource(
        "CartoDB.DarkMatter",
        0, 18, 256, ".png",
        arrayOf("https://basemaps.cartocdn.com/dark_all/")
    )
    // Restore tile source from prefs if present, otherwise follow system theme
    val savedTileId = remember { prefsManager.getMapTileSourceId() }
    fun tileSourceForId(id: String): org.osmdroid.tileprovider.tilesource.ITileSource {
        return when (id) {
            "OpenTopo" -> TileSourceFactory.OpenTopo
            "USGS_SAT" -> TileSourceFactory.USGS_SAT
            "MAPNIK" -> TileSourceFactory.MAPNIK
            "CartoDB.DarkMatter" -> darkTile
            else -> TileSourceFactory.MAPNIK
        }
    }
    val initialTileSource = savedTileId?.let { tileSourceForId(it) } ?: if (isDarkTheme) darkTile else TileSourceFactory.MAPNIK

    // createPlaneDrawable moved earlier to be visible to flight-data LaunchedEffect


    Box(modifier = modifier.fillMaxSize()) {

        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(initialTileSource)
                    setMultiTouchControls(true)

                    // Set padding so map content doesn't hide under the TabBar
                    setPadding(0, tabBarHeightPx, 0, 0)

                    // Set initial view using saved or fallback values (no animation)
                    controller.setZoom(initialZoom)
                    // Only set initial center if we don't have a last valid player position (prevents reset)
                    if (lastValidPlayerPosition == null && initialCenter != null) {
                        controller.setCenter(initialCenter)
                    } else if (lastValidPlayerPosition == null) {
                        controller.setCenter(GeoPoint(48.0, 11.0))
                    }

                    // Create position marker
                    val marker = Marker(this).apply {
                        title = context.getString(R.string.map_aircraft_position)
                        snippet = context.getString(R.string.map_waiting_for_data)
                        // anchor center for correct rotation and pivot
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        // Use last valid position if available, prevents reset
                        lastValidPlayerPosition?.let { position = it }

                        // initial plane icon
                        try {
                            val color = if (isDarkTheme) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                            icon = createPlaneDrawable(ctx, 28f, color)
                        } catch (e: Exception) {
                            // Use default marker
                        }
                    }

                    overlays.add(marker)
                    positionMarker = marker
                    
                    // Map click handler for symbol placement
                    val mapEventsReceiver = object : org.osmdroid.events.MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            Log.d(TAG, "singleTapConfirmedHelper called at $p, pendingMoveMarkerId=$pendingMoveMarkerId, pendingSymbolPlacement=${pendingSymbolPlacement != null}")
                            
                            p?.let { geoPoint ->
                                // 1) If a move is pending, use this tap to set new coords for that marker
                                if (pendingMoveMarkerId != null) {
                                    val moveId = pendingMoveMarkerId
                                    scope.launch {
                                        try {
                                            // Get current DB and create repository fresh to avoid stale captures
                                            val db = tacticalDb
                                            if (db == null) {
                                                Log.e(TAG, "TacticalDatabase is null, cannot move marker")
                                                return@launch
                                            }
                                            val repo = com.example.checklist_interactive.data.tactical.LocationRepositoryImpl(db.locationDao())
                                            val loc = repo.getLocationById(moveId!!)
                                            if (loc != null) {
                                                val updated = loc.copy(latitude = geoPoint.latitude, longitude = geoPoint.longitude)
                                                repo.updateLocation(updated)
                                                Log.d(TAG, "Moved marker id=$moveId to ${geoPoint.latitude},${geoPoint.longitude}")
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to move marker id=$moveId", e)
                                        } finally {
                                            MapActionBus.clear()
                                        }
                                    }
                                    return true
                                }

                                // 2) Normal symbol placement flow
                                val symbolPlacement = pendingSymbolPlacement
                                if (symbolPlacement != null) {
                                    val (symbol, affiliation) = symbolPlacement
                                    Log.d(TAG, "Placing military symbol: ${symbol.name} at ${geoPoint.latitude}, ${geoPoint.longitude}")

                                    // Convert Compose Color to hex string properly
                                    val colorHex = String.format("#%08X", (affiliation.color.value.toLong() and 0xFFFFFFFF))

                                    // Place military symbol at clicked location
                                    scope.launch {
                                        try {
                                            val newLocation = com.example.checklist_interactive.data.tactical.LocationEntity(
                                                name = "${symbol.name} - ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                                                latitude = geoPoint.latitude,
                                                longitude = geoPoint.longitude,
                                                markerType = "tactical_military",
                                                coalition = affiliation.name.lowercase(),
                                                symbolSet = symbol.symbolSet,
                                                symbolEntity = symbol.symbolEntity,
                                                symbolAffiliation = affiliation.name.lowercase(),
                                                symbolColor = colorHex,
                                                icon = "ic_mapicon_${symbol.id}",
                                                description = "Military symbol: ${symbol.name}"
                                            )

                                            // Get current DB and create repository fresh to avoid stale captures
                                            val db = tacticalDb
                                            if (db == null) {
                                                Log.e(TAG, "TacticalDatabase is null, cannot save marker")
                                                // Notify user that DB is not ready and leave the placement pending so they can try again
                                                try {
                                                    android.widget.Toast.makeText(context, "Kartendatenbank noch nicht bereit — bitte erneut tippen.", android.widget.Toast.LENGTH_SHORT).show()
                                                } catch (_: Throwable) {
                                                    // ignore if Toast can't be shown (tests / preview)
                                                }
                                                return@launch
                                            }

                                            val repo = com.example.checklist_interactive.data.tactical.LocationRepositoryImpl(db.locationDao())
                                            val insertedId = repo.saveLocation(newLocation)
                                            // Only clear the pending placement after save succeeds to avoid losing the user's intent
                                            pendingSymbolPlacement = null

                                            Log.d(TAG, "Successfully placed military symbol: ${symbol.name} at ${geoPoint.latitude}, ${geoPoint.longitude} (id=$insertedId)")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to place military symbol", e)
                                        }
                                    }
                                    return true
                                }
                            }
                            return false
                        }
                        
                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            return false
                        }
                    }
                    val mapEventsOverlay = org.osmdroid.views.overlay.MapEventsOverlay(mapEventsReceiver)
                    overlays.add(0, mapEventsOverlay) // Add at index 0 so it processes events first
                    
                    mapView = this

                    // Touch listener: detect long-press on markers and mark user touch to disable auto-center
                    try {
                        var downX = 0f
                        var downY = 0f
                        var moved = false
                        var isDown = false
                        var longPressJob: kotlinx.coroutines.Job? = null
                        val longPressTimeoutMs = 600L
                        val moveThresholdPx = with(density) { 10.dp.toPx() }

                        setOnTouchListener { _, ev ->
                            try {
                                when (ev.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        lastUserTouch.value = System.currentTimeMillis()
                                        prefsManager.setMapAutoCenter(false)
                                        scope.launch { autoCenter = false }

                                        downX = ev.x
                                        downY = ev.y
                                        moved = false
                                        isDown = true

                                        longPressJob?.cancel()
                                        longPressJob = scope.launch {
                                            kotlinx.coroutines.delay(longPressTimeoutMs)
                                            if (isDown && !moved) {
                                                try {
                                                    // Find nearest marker within radius
                                                    val touchX = ev.x.toInt()
                                                    val touchY = ev.y.toInt()
                                                    var nearestMarker: org.osmdroid.views.overlay.Marker? = null
                                                    var bestDist2 = Int.MAX_VALUE
                                                    val radiusPx = (with(density) { 40.dp.toPx() }).toInt()

                                                    for (o in overlays) {
                                                        if (o is org.osmdroid.views.overlay.Marker && o != positionMarker) {
                                                            val p = android.graphics.Point()
                                                            projection.toPixels(o.position, p)
                                                            val dx = p.x - touchX
                                                            val dy = p.y - touchY
                                                            val dist2 = dx * dx + dy * dy
                                                            Log.d(TAG, "marker candidate at screen=(${p.x},${p.y}) dx=$dx dy=$dy dist2=$dist2")
                                                            if (dist2 < bestDist2 && dist2 <= radiusPx * radiusPx) {
                                                                bestDist2 = dist2
                                                                nearestMarker = o
                                                            }
                                                        }
                                                    }

                                                    nearestMarker?.let { nm ->
                                                        // Prefer mapping lookup; fall back to relatedObject
                                                        val loc = markerToLocation[nm] ?: try { nm.relatedObject as? com.example.checklist_interactive.data.tactical.LocationEntity } catch (_: Throwable) { null }
                                                        
                                                        // Get marker position in screen coordinates
                                                        // Use the raw touch coordinates as they are already in the correct screen space
                                                        val screenX = touchX
                                                        val screenY = touchY
                                                        
                                                        // If the marker has an icon, adjust for its size so the menu appears centered
                                                        var iconWidth = 0
                                                        var iconHeight = 0
                                                        try {
                                                            val icon = nm.icon
                                                            if (icon != null) {
                                                                iconWidth = icon.intrinsicWidth.coerceAtLeast(0)
                                                                iconHeight = icon.intrinsicHeight.coerceAtLeast(0)
                                                                Log.d(TAG, "Icon dimensions: ${iconWidth}x${iconHeight}")
                                                            } else {
                                                                Log.d(TAG, "Marker has no icon, using default dimensions")
                                                                iconWidth = 64
                                                                iconHeight = 64
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e(TAG, "Error getting icon dimensions", e)
                                                            iconWidth = 64
                                                            iconHeight = 64
                                                        }
                                                        
                                                        // Get MapView position in window to convert touch coordinates to window coordinates
                                                        val mapLoc = IntArray(2)
                                                        this@apply.getLocationInWindow(mapLoc)
                                                        
                                                        // Convert MapView-local touch coordinates to window coordinates
                                                        val windowX = mapLoc[0] + screenX
                                                        val windowY = mapLoc[1] + screenY
                                                        
                                                        // Offset up slightly so menu doesn't overlap marker
                                                        val extraUpPx = with(density) { 40.dp.toPx().toInt() }
                                                        val adjY = windowY - extraUpPx
                                                        
                                                        Log.d(TAG, "Long-press on marker '${nm.title}': touch=($touchX,$touchY) mapLoc=(${mapLoc[0]},${mapLoc[1]}) window=($windowX,$windowY) adj=($windowX,$adjY) icon=${iconWidth}x${iconHeight}")
                                                        
                                                        if (loc != null) {
                                                            // Show radial menu at adjusted coordinates
                                                            scope.launch {
                                                                Log.d(TAG, "Setting radialMenu state: marker=${loc.name}, pos=($windowX,$adjY)")
                                                                radialMenuMarker = loc
                                                                radialMenuX = windowX
                                                                radialMenuY = adjY
                                                                radialMenuVisible = true
                                                                lastLongPressedMarkerId = loc.id
                                                                lastLongPressTime = System.currentTimeMillis()
                                                                Log.d(TAG, "RadialMenu state set: visible=$radialMenuVisible, marker=${radialMenuMarker?.name}")
                                                            }
                                                        } else {
                                                            Log.d(TAG, "Long-press found nearest marker but could not resolve LocationEntity")
                                                        }
                                                    }

                                                } catch (_: Exception) {
                                                }
                                            }
                                        }
                                    }

                                    MotionEvent.ACTION_MOVE -> {
                                        lastUserTouch.value = System.currentTimeMillis()
                                        if (kotlin.math.hypot((ev.x - downX).toDouble(), (ev.y - downY).toDouble()) > moveThresholdPx) {
                                            moved = true
                                            longPressJob?.cancel()
                                        }
                                    }

                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        isDown = false
                                        longPressJob?.cancel()
                                    }
                                }
                            } catch (_: Exception) {
                            }

                            // Let MapView handle the touch normally (click listeners will be invoked unless suppressed)
                            false
                        }
                    } catch (_: Exception) {
                    }

                    // Listen for user map interactions to persist center/zoom and disable auto-center when user moves map
                    try {
                        val mapListener = object : org.osmdroid.events.MapListener {
                            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                                val center = this@apply.mapCenter
                                val now = System.currentTimeMillis()

                                // If a real user touch happened recently, treat this as a user scroll
                                if (now - lastUserTouch.value < 700) {
                                    prefsManager.setMapCenter(center.latitude, center.longitude)
                                    prefsManager.setMapAutoCenter(false)
                                    Log.d(TAG, "User scroll detected — disabling auto-center (touch at ${now - lastUserTouch.value}ms)")
                                    // Use coroutine scope to properly update Compose state
                                    scope.launch {
                                        Log.d(TAG, "User scroll - disabling autoCenter via scope.launch")
                                        autoCenter = false
                                    }
                                    return true
                                }

                                // Otherwise, ignore non-user-initiated scrolls (from animateTo)
                                prefsManager.setMapCenter(center.latitude, center.longitude)
                                Log.d(TAG, "Ignoring non-user scroll at ${now - lastProgrammaticMove.value}ms")
                                return true
                            }

                            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                                prefsManager.setMapZoom(this@apply.zoomLevelDouble)
                                return true
                            }
                        }
                        addMapListener(mapListener)
                    } catch (e: Exception) {
                        // osmdroid map listener not available - ignore
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = with(density) { 0.dp })
        )

        // Update tile source when theme changes (applies immediately on theme toggle)
        // Only apply theme-based tile source when user hasn't explicitly chosen one
        LaunchedEffect(isDarkTheme, savedTileId) {
            if (savedTileId == null) {
                mapView?.setTileSource(if (isDarkTheme) darkTile else TileSourceFactory.MAPNIK)
            }
        }
        
        // Load and display markers from database
        LaunchedEffect(mapView, locationRepository) {
            if (mapView != null && locationRepository != null) {
                val locFlow = locationRepository.getAllLocations()
                locFlow.collect { markers ->
                    mapView?.let { mv ->
                        // Remove existing marker overlays (except position marker)
                        mv.overlays.removeAll { it is org.osmdroid.views.overlay.Marker && it != positionMarker }
                        // Clear mapping to avoid holding stale references
                        markerToLocation.clear()

                        // Add markers to map
                        // Prepare icons and marker payloads off main thread to avoid UI jank
                        data class PreparedMarker(val entity: com.example.checklist_interactive.data.tactical.LocationEntity, val icon: BitmapDrawable?)

                        val prepared = withContext(kotlinx.coroutines.Dispatchers.Default) {
                            markers.map { marker ->
                                var markerIcon: BitmapDrawable? = null
                                try {
                                    if (marker.symbolEntity.isNotEmpty()) {
                                        val resName = "ic_mapicon_${marker.symbolEntity}"
                                        val iconResId = context.resources.getIdentifier(resName, "drawable", context.packageName)
                                        if (iconResId != 0) {
                                            val drawable = ContextCompat.getDrawable(context, iconResId)?.mutate()
                                            drawable?.let { d ->
                                                val affiliationColor = when (marker.symbolAffiliation.lowercase()) {
                                                    "friendly" -> android.graphics.Color.parseColor("#00A8FF")
                                                    "hostile" -> android.graphics.Color.parseColor("#FF4444")
                                                    "neutral" -> android.graphics.Color.parseColor("#00FF00")
                                                    "unknown" -> android.graphics.Color.parseColor("#FFFF80")
                                                    else -> try { if (marker.symbolColor.isNotEmpty()) android.graphics.Color.parseColor(marker.symbolColor) else android.graphics.Color.parseColor("#FFFF80") } catch (_: Exception) { android.graphics.Color.parseColor("#FFFF80") }
                                                }
                                                val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
                                                val canvas = android.graphics.Canvas(bitmap)
                                                d.setBounds(0, 0, 64, 64)
                                                val colorFilter = android.graphics.PorterDuffColorFilter(affiliationColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
                                                d.colorFilter = colorFilter
                                                d.draw(canvas)
                                                markerIcon = BitmapDrawable(context.resources, bitmap)
                                            }
                                        }
                                    }
                                } catch (_: Exception) { }

                                try {
                                    if (markerIcon == null && marker.markerType == "airport") {
                                        val iconResId = context.resources.getIdentifier("ic_mapicon_static_airport", "drawable", context.packageName)
                                        if (iconResId != 0) {
                                            val drawable = ContextCompat.getDrawable(context, iconResId)?.mutate()
                                            drawable?.let { d ->
                                                val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
                                                val canvas = android.graphics.Canvas(bitmap)
                                                d.setBounds(0, 0, 64, 64)
                                                d.draw(canvas)
                                                markerIcon = BitmapDrawable(context.resources, bitmap)
                                            }
                                        }
                                    }
                                } catch (_: Exception) { }

                                try {
                                    if (markerIcon == null && marker.icon.isNotEmpty() && marker.icon != "default") {
                                        val iconName = if (marker.icon.startsWith("ic_mapicon_")) marker.icon else "ic_mapicon_${marker.icon}"
                                        var iconResId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
                                        if (iconResId == 0) iconResId = context.resources.getIdentifier(marker.icon, "drawable", context.packageName)
                                        if (iconResId != 0) {
                                            val drawable = ContextCompat.getDrawable(context, iconResId)?.mutate()
                                            drawable?.let { d ->
                                                val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
                                                val canvas = android.graphics.Canvas(bitmap)
                                                d.setBounds(0, 0, 64, 64)
                                                d.draw(canvas)
                                                markerIcon = BitmapDrawable(context.resources, bitmap)
                                            }
                                        }
                                    }
                                } catch (_: Exception) { }

                                if (markerIcon == null) {
                                    val bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(bitmap)
                                    val paint = android.graphics.Paint().apply { isAntiAlias = true; color = android.graphics.Color.RED; style = android.graphics.Paint.Style.FILL }
                                    val strokePaint = android.graphics.Paint().apply { isAntiAlias = true; color = android.graphics.Color.WHITE; style = android.graphics.Paint.Style.STROKE; strokeWidth = 4f }
                                    canvas.drawCircle(24f, 24f, 18f, paint)
                                    canvas.drawCircle(24f, 24f, 18f, strokePaint)
                                    val xPaint = android.graphics.Paint().apply { isAntiAlias = true; color = android.graphics.Color.WHITE; style = android.graphics.Paint.Style.STROKE; strokeWidth = 3f }
                                    canvas.drawLine(12f, 12f, 36f, 36f, xPaint)
                                    canvas.drawLine(36f, 12f, 12f, 36f, xPaint)
                                    markerIcon = BitmapDrawable(context.resources, bitmap)
                                }

                                PreparedMarker(marker, markerIcon)
                            }
                        }

                        // Apply prepared markers on main thread
                        prepared.forEach { p ->
                            val marker = p.entity
                            val osmMarker = org.osmdroid.views.overlay.Marker(mv).apply {
                                position = GeoPoint(marker.latitude, marker.longitude)
                                title = marker.name
                                snippet = buildString {
                                    append("Type: ${marker.markerType}")
                                    if (marker.coalition != null) append("\nCoalition: ${marker.coalition}")
                                    if (marker.markerType == "tactical_military") {
                                        if (marker.symbolEntity.isNotEmpty()) append("\nSymbol: ${marker.symbolEntity}")
                                        if (marker.symbolAffiliation.isNotEmpty()) append("\nAffiliation: ${marker.symbolAffiliation}")
                                    }
                                    if (marker.description.isNotEmpty()) append("\n${marker.description}")
                                }
                                setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)

                                try { this.setRelatedObject(marker) } catch (_: Throwable) { }
                                try { markerToLocation[this] = marker } catch (_: Throwable) { }

                                icon = p.icon
                            }

                            mv.overlays.add(osmMarker)

                            // Attach click listener
                            osmMarker.setOnMarkerClickListener(object : org.osmdroid.views.overlay.Marker.OnMarkerClickListener {
                                override fun onMarkerClick(markerView: org.osmdroid.views.overlay.Marker?, mapView: org.osmdroid.views.MapView?): Boolean {
                                    if (lastLongPressedMarkerId == marker.id && System.currentTimeMillis() - lastLongPressTime < 1000L) {
                                        lastLongPressedMarkerId = null
                                        return true
                                    }

                                    selectedLocation = marker
                                    showMarkerRouteManagement = true

                                    (context as? android.app.Activity)?.runOnUiThread {
                                        lastProgrammaticMove.value = System.currentTimeMillis()
                                        try { markerView?.let { mv2 -> mapView?.controller?.animateTo(mv2.position) }; mapView?.invalidate() } catch (_: Exception) {}
                                    }

                                    return true
                                }
                            })
                        }

                        mv.invalidate()
                        Log.d(TAG, "Loaded ${markers.size} markers onto map")
                    }
                }
            }
        }
        
        // Control overlay with centralized FABs
        // Shared trigger for resetting FAB positions (moved here so both the control buttons
        // and the DraggableFab instances can access it)
        var fabLayoutResetTrigger by remember { mutableStateOf(0) }

        // Use centralized FAB overlay system
        FABOverlay(
            prefsManager = prefsManager,
            screenWidthPx = screenWidthPx,
            screenHeightPx = effectiveScreenHeightPx,
            marginPx = fabMarginPx,
            fabs = MapViewerFABs.create(
                onCenterOnPosition = {
                    val data = flightData
                    if (data != null && data.latitude != 0.0 && data.longitude != 0.0) {
                        // We have a live position — try to center immediately on UI thread
                        (context as? android.app.Activity)?.runOnUiThread {
                            lastProgrammaticMove.value = System.currentTimeMillis()
                            mapView?.controller?.animateTo(GeoPoint(data.latitude, data.longitude))
                            mapView?.invalidate()
                        }
                        pendingCenter.value = null
                        autoCenter = true
                        prefsManager.setMapAutoCenter(true)
                        prefsManager.setMapCenter(data.latitude, data.longitude)
                        Log.d(TAG, "Center FAB pressed — centering to live position ${data.latitude},${data.longitude}, autoCenter=$autoCenter")
                    } else {
                        // No live position yet — try to fall back to last saved center, or remember to center later
                        val saved = prefsManager.getMapCenter()
                        if (saved != null) {
                            val gp = GeoPoint(saved.first, saved.second)
                            (context as? android.app.Activity)?.runOnUiThread {
                                lastProgrammaticMove.value = System.currentTimeMillis()
                                mapView?.controller?.animateTo(gp)
                                mapView?.invalidate()
                            }
                            autoCenter = true
                            prefsManager.setMapAutoCenter(true)
                            Log.d(TAG, "Center FAB pressed — centering to saved position ${saved.first},${saved.second}, autoCenter=$autoCenter")
                        } else {
                            // no center available — remember to center once we get a valid position
                            pendingCenter.value = null // will be set when new data arrives in the flight-data effect
                            autoCenter = true
                            prefsManager.setMapAutoCenter(true)
                            Log.d(TAG, "Center FAB pressed — no position available yet, will enable auto-centering for future updates, autoCenter=$autoCenter")
                        }
                    }
                },
                onLayerSelection = { showLayerDialog = true },
                onOverlaySelection = { showOverlayDialog = true },
                onAddMilitarySymbol = { if (repositoriesReady) showMilitarySymbolPicker = true },
                onMarkerRouteManagement = { if (repositoriesReady) showMarkerRouteManagement = true },
                onLockScreen = onLockScreen,
                onToggleMapRotation = {
                    mapRotationMode = (mapRotationMode + 1) % 2
                    if (mapRotationMode == 0) {
                        try { mapView?.setMapOrientation(0f) } catch (_: Throwable) {}
                    } else {
                        flightData?.let { d -> try { mapView?.setMapOrientation(-Math.toDegrees(d.heading).toFloat()) } catch (_: Throwable) {} }
                    }
                },
                onResetFabPositions = {
                    try {
                        prefsManager.resetFabPositions("map")
                    } catch (e: Exception) { android.util.Log.w(TAG, "Failed to reset FAB prefs: ${e.message}") }
                    // force re-read of saved positions for the DraggableFabs
                    try { fabLayoutResetTrigger = fabLayoutResetTrigger + 1 } catch (_: Throwable) {}
                    // quick feedback
                    android.widget.Toast.makeText(context, "FAB-Positionen zurückgesetzt", android.widget.Toast.LENGTH_SHORT).show()
                },

                onDataPadOpen = { if (datapadEnabled) showDataPad = true },
                onQuickAccessOpen = { showQuickAccess = true },
                isConnected = isConnected,
                isScreenLocked = isScreenLocked,
                mapRotationMode = mapRotationMode,
                repositoriesReady = repositoriesReady,
                pendingSymbolPlacement = pendingSymbolPlacement,
                datapadEnabled = datapadEnabled,
                containerColorConnected = MaterialTheme.colorScheme.primaryContainer,
                containerColorDisconnected = MaterialTheme.colorScheme.surfaceVariant,
                containerColorSecondary = MaterialTheme.colorScheme.secondaryContainer,
                containerColorTertiary = MaterialTheme.colorScheme.tertiaryContainer,
                containerColorPrimary = MaterialTheme.colorScheme.primaryContainer,
                containerColorSurface = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        // Active Navigation Display (top center)
        if (activeNavigationTarget != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .widthIn(max = 500.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .clickable { showNavigationDetails = !showNavigationDetails },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ROUTE TO: ${activeNavigationTarget?.name ?: ""}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                navigationDistanceNm?.let { dist ->
                                    Text(
                                        text = "${String.format("%.1f", dist)} NM",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                navigationHeading?.let { hdg ->
                                    Text(
                                        text = "HDG ${String.format("%03.0f", hdg)}°",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            // Show final runway heading when runway selected
                            selectedRunwayHeading?.let { rwyHdg ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = "FINAL:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = "RWY ${String.format("%03.0f", rwyHdg)}°",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    originalAirportTarget?.let { airport ->
                                        val airportPos = GeoPoint(airport.latitude, airport.longitude)
                                        flightData?.let { data ->
                                            if (data.latitude != 0.0 && data.longitude != 0.0) {
                                                val playerPos = GeoPoint(data.latitude, data.longitude)
                                                val distanceMeters = playerPos.distanceToAsDouble(airportPos)
                                                val distanceNm = distanceMeters / 1852.0
                                                Text(
                                                    text = "${String.format("%.1f", distanceNm)} NM",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Toggle expand/collapse button
                            IconButton(
                                onClick = { showNavigationDetails = !showNavigationDetails }
                            ) {
                                Icon(
                                    imageVector = if (showNavigationDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (showNavigationDetails) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            
                            // Land button (only show if target has runways)
                            if (targetRunways.isNotEmpty()) {
                                FilledTonalIconButton(
                                    onClick = {
                                        showRunwayApproach = !showRunwayApproach
                                        if (!showRunwayApproach) {
                                            selectedRunwayIndex = null
                                            selectedRunwayHeading = null
                                            selectedRunway = null
                                        }
                                    },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = if (showRunwayApproach)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (showRunwayApproach)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FlightLand,
                                        contentDescription = "Landing approach"
                                    )
                                }
                            }

                            // Cancel button
                            IconButton(
                                onClick = {
                                    activeNavigationTarget = null
                                    originalAirportTarget = null
                                    showRunwayApproach = false
                                    selectedRunwayIndex = null
                                    selectedRunwayHeading = null
                                    selectedRunway = null
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel navigation",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    // Collapsible details section
                    AnimatedVisibility(
                        visible = showNavigationDetails,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Runway selection (when approach mode active)
                            if (showRunwayApproach && targetRunways.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SELECT RUNWAY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )

                                // Final approach distance dropdown
                                var expanded by remember { mutableStateOf(false) }
                                val distances = listOf(2.5, 5.0, 10.0, 15.0, 25.0)

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Pattern button
                                    Button(
                                        onClick = { showTrafficPattern = !showTrafficPattern },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (showTrafficPattern) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                            contentColor = if (showTrafficPattern) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            text = "PATTERN",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Box {
                                        FilterChip(
                                            selected = false,
                                            onClick = { expanded = !expanded },
                                            label = {
                                                Text(
                                                    text = "${finalApproachDistanceNm.let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() }} NM",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            modifier = Modifier.height(28.dp)
                                        )

                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false }
                                        ) {
                                            distances.forEach { dist ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = "${if (dist == dist.toInt().toDouble()) dist.toInt().toString() else dist.toString()} NM Final",
                                                            style = MaterialTheme.typography.bodySmall
                                                        )
                                                    },
                                                    onClick = {
                                                        finalApproachDistanceNm = dist
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Pattern configuration (when pattern mode active)
                            if (showTrafficPattern) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "PATTERN CONFIGURATION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Pattern size selector
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Size:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    
                                    var sizeExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        FilterChip(
                                            selected = false,
                                            onClick = { sizeExpanded = !sizeExpanded },
                                            label = {
                                                Text(
                                                    text = patternSize.displayName,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = if (sizeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            modifier = Modifier.height(28.dp)
                                        )
                                        
                                        DropdownMenu(
                                            expanded = sizeExpanded,
                                            onDismissRequest = { sizeExpanded = false }
                                        ) {
                                            PatternSize.values().forEach { size ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(
                                                                text = size.displayName,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                text = "${size.downwindDistanceNm} NM • ${size.patternAltitudeFt} ft",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        patternSize = size
                                                        sizeExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Pattern direction selector
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Direction:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = patternDirection == PatternDirection.LEFT_HAND,
                                            onClick = { patternDirection = PatternDirection.LEFT_HAND },
                                            label = {
                                                Text(
                                                    text = "Left",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            modifier = Modifier.height(28.dp)
                                        )
                                        
                                        FilterChip(
                                            selected = patternDirection == PatternDirection.RIGHT_HAND,
                                            onClick = { patternDirection = PatternDirection.RIGHT_HAND },
                                            label = {
                                                Text(
                                                    text = "Right",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Final approach distance selector
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Final Length:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    
                                    var finalExpanded by remember { mutableStateOf(false) }
                                    val finalDistances = listOf(0.5, 1.0, 2.0, 3.0, 5.0, 7.0, 10.0)
                                    
                                    Box {
                                        FilterChip(
                                            selected = false,
                                            onClick = { finalExpanded = !finalExpanded },
                                            label = {
                                                Text(
                                                    text = "${if (patternFinalDistanceNm == patternFinalDistanceNm.toInt().toDouble()) patternFinalDistanceNm.toInt().toString() else patternFinalDistanceNm.toString()} NM",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = if (finalExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            modifier = Modifier.height(28.dp)
                                        )
                                        
                                        DropdownMenu(
                                            expanded = finalExpanded,
                                            onDismissRequest = { finalExpanded = false }
                                        ) {
                                            finalDistances.forEach { dist ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(
                                                                text = "${if (dist == dist.toInt().toDouble()) dist.toInt().toString() else dist.toString()} NM Final",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                text = when {
                                                                    dist <= 1.0 -> "Short - Quick pattern"
                                                                    dist <= 3.0 -> "Medium - Standard"
                                                                    dist <= 5.0 -> "Long - More time"
                                                                    else -> "Very Long - Training"
                                                                },
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        patternFinalDistanceNm = dist
                                                        finalExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Pattern details header with collapsible body
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Pattern Details",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(onClick = { showPatternDetails = !showPatternDetails }) {
                                        Icon(
                                            imageVector = if (showPatternDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (showPatternDetails) "Collapse" else "Expand"
                                        )
                                    }
                                }

                                AnimatedVisibility(
                                    visible = showPatternDetails,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val sizeScale = when (patternSize) {
                                                PatternSize.NORMAL -> 1.0
                                                PatternSize.MEDIUM -> 1.25
                                                PatternSize.LARGE -> 1.5
                                                PatternSize.VERY_LARGE -> 2.0
                                            }
                                            val turnMultiplier = if (patternDirection == PatternDirection.LEFT_HAND) -1 else 1
                                            val selectedRwy = selectedRunway
                                            val baseHdg = (selectedRwy?.headingDeg ?: extractRunwayHeading(selectedRwy?.name ?: "") ?: 0.0).toInt()

                                            Text(
                                                text = "• Departure: HDG ${String.format("%03d", baseHdg)}° • ${String.format("%.1f", 0.5 * sizeScale)} NM",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            val crosswindHdg = (baseHdg + (90 * turnMultiplier) + 360) % 360
                                            Text(
                                                text = "• Crosswind: HDG ${String.format("%03d", crosswindHdg)}° • ${String.format("%.1f", patternSize.downwindDistanceNm * sizeScale)} NM",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            val downwindHdg = (baseHdg + 180) % 360
                                            val downwindLengthNm = (selectedRwy?.lengthM?.toDouble() ?: 2000.0) / 1852.0 + patternFinalDistanceNm + (0.5 * sizeScale)
                                            Text(
                                                text = "• Downwind: HDG ${String.format("%03d", downwindHdg)}° • ${String.format("%.1f", downwindLengthNm)} NM",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            val baseHdgValue = (baseHdg + (270 * turnMultiplier) + 360) % 360
                                            val baseExtensionNm = (0.3 + (patternFinalDistanceNm * 0.2)) * sizeScale
                                            Text(
                                                text = "• Base: HDG ${String.format("%03d", baseHdgValue)}° • ${String.format("%.1f", patternSize.downwindDistanceNm * sizeScale)} NM (turn at ${String.format("%.1f", baseExtensionNm)} NM)",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            Text(
                                                text = "• Final: HDG ${String.format("%03d", baseHdg)}° • ${String.format("%.1f", patternFinalDistanceNm)} NM",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            Text(
                                                text = "Pattern Altitude: ${patternSize.patternAltitudeFt} ft AGL",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            targetRunways.forEachIndexed { index, runway ->
                                val baseHeading = runway.headingDeg ?: extractRunwayHeading(runway.name) ?: 0.0
                                val heading1 = baseHeading.toInt()
                                val heading2 = ((heading1 + 180) % 360)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Direction 1
                                    FilterChip(
                                        selected = selectedRunwayIndex == (index * 2),
                                        onClick = {
                                            // When switching from Direction 2 to Direction 1, flip pattern direction
                                            if (selectedRunwayIndex == (index * 2 + 1)) {
                                                patternDirection = if (patternDirection == PatternDirection.LEFT_HAND) {
                                                    PatternDirection.RIGHT_HAND
                                                } else {
                                                    PatternDirection.LEFT_HAND
                                                }
                                            }
                                            selectedRunwayIndex = index * 2
                                            selectedRunway = runway
                                            // Store the runway heading for display
                                            val calcHeading = runway.headingDeg ?: extractRunwayHeading(runway.name) ?: 0.0
                                            selectedRunwayHeading = calcHeading
                                            // Create route to approach endpoint
                                            val target = originalAirportTarget
                                            if (target != null) {
                                                val distanceMeters = finalApproachDistanceNm * 1852.0
                                                val rad = Math.toRadians(calcHeading)
                                                val lat1 = Math.toRadians(target.latitude)
                                                val lon1 = Math.toRadians(target.longitude)
                                                val dLat = distanceMeters * Math.cos(rad) / 6371000.0
                                                val dLon = distanceMeters * Math.sin(rad) / (6371000.0 * Math.cos(lat1))
                                                val endLat = lat1 + dLat
                                                val endLon = lon1 + dLon
                                                val endpoint = GeoPoint(Math.toDegrees(endLat), Math.toDegrees(endLon))

                                                // Update navigation to approach endpoint (red line will auto-update)
                                                // Create temporary target at endpoint
                                                val approachTarget = target.copy(
                                                    id = -1,
                                                    name = "${target.name} RWY ${String.format("%02d", heading1 / 10)}",
                                                    latitude = endpoint.latitude,
                                                    longitude = endpoint.longitude
                                                )
                                                activeNavigationTarget = approachTarget
                                                // Keep runway approach lines visible (don't set showRunwayApproach = false)
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = "RWY ${String.format("%02d", heading1 / 10)} (${heading1}°)",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            selectedContainerColor = MaterialTheme.colorScheme.primary
                                        )
                                    )

                                    // Direction 2
                                    FilterChip(
                                        selected = selectedRunwayIndex == (index * 2 + 1),
                                        onClick = {
                                            // When switching from Direction 1 to Direction 2, flip pattern direction
                                            if (selectedRunwayIndex == (index * 2)) {
                                                patternDirection = if (patternDirection == PatternDirection.LEFT_HAND) {
                                                    PatternDirection.RIGHT_HAND
                                                } else {
                                                    PatternDirection.LEFT_HAND
                                                }
                                            }
                                            selectedRunwayIndex = index * 2 + 1
                                            selectedRunway = runway
                                            // Store the opposite runway heading for display
                                            val calcHeading = runway.headingDeg ?: extractRunwayHeading(runway.name) ?: 0.0
                                            val oppositeHeading = (calcHeading + 180) % 360
                                            selectedRunwayHeading = oppositeHeading
                                            // Create route to opposite approach endpoint
                                            val target = originalAirportTarget
                                            if (target != null) {
                                                val distanceMeters = finalApproachDistanceNm * 1852.0
                                                val heading2Rad = Math.toRadians(oppositeHeading)
                                                val lat1 = Math.toRadians(target.latitude)
                                                val lon1 = Math.toRadians(target.longitude)
                                                val dLat = distanceMeters * Math.cos(heading2Rad) / 6371000.0
                                                val dLon = distanceMeters * Math.sin(heading2Rad) / (6371000.0 * Math.cos(lat1))
                                                val endLat = lat1 + dLat
                                                val endLon = lon1 + dLon
                                                val endpoint = GeoPoint(Math.toDegrees(endLat), Math.toDegrees(endLon))

                                                val approachTarget = target.copy(
                                                    id = -1,
                                                    name = "${target.name} RWY ${String.format("%02d", heading2 / 10)}",
                                                    latitude = endpoint.latitude,
                                                    longitude = endpoint.longitude
                                                )
                                                activeNavigationTarget = approachTarget
                                                // Keep runway approach lines visible (don't set showRunwayApproach = false)
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = "RWY ${String.format("%02d", heading2 / 10)} (${heading2}°)",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            selectedContainerColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                            }
                        }
                        }
                    }
                }
            }
        }

        // Connection status indicator (only show when DataPad enabled)
        // Also show DB loading indicator if DB is not ready
        if (dbInitFailed) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .clickable { 
                        // Retry DB init on click
                        dbInitFailed = false
                        dbInitError = null
                        scope.launch {
                            withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    android.util.Log.d(TAG, "Retrying TacticalDatabase initialization...")
                                    val db = kotlinx.coroutines.withTimeout(10000L) {
                                        com.example.checklist_interactive.data.tactical.TacticalDatabase.getInstance(context, useExternalPath = false)
                                    }
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        tacticalDb = db
                                        android.util.Log.d(TAG, "DB initialized successfully on retry")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e(TAG, "Retry failed", e)
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        dbInitFailed = true
                                        dbInitError = e.message ?: "Unbekannter Fehler"
                                    }
                                }
                            }
                        }
                    },
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⚠️ Kartendatenbank Fehler",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dbInitError ?: "Unbekannt",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Tippen zum erneuten Versuchen",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        } else if (!dbReady) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Kartendatenbank wird geladen...",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else if (datapadEnabled && !isConnected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = stringResource(R.string.map_no_datapad_connection),
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else if (datapadEnabled && (flightData?.latitude == 0.0 || flightData?.longitude == 0.0)) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = stringResource(R.string.map_waiting_for_valid_position),
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        
        // Auto-center indicator (clickable: toggle + persist)
        if (isConnected) {
            if (autoCenter) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .clickable {
                            autoCenter = false
                            prefsManager.setMapAutoCenter(false)
                        },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = stringResource(R.string.map_auto_center_enabled),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .clickable {
                            autoCenter = true
                            prefsManager.setMapAutoCenter(true)
                        },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = stringResource(R.string.map_auto_center_disabled),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // Move mode instruction banner
        if (pendingMoveMarkerId != null) {
            // Optionally resolve name for nicer text
            LaunchedEffect(pendingMoveMarkerId) {
                pendingMoveTargetName = try {
                    locationRepository?.getLocationById(pendingMoveMarkerId!!)?.name
                } catch (_: Exception) {
                    null
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .widthIn(max = 800.dp)
                    .clickable { /* do nothing - purely informational */ },
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Move marker: ${pendingMoveTargetName ?: pendingMoveMarkerId}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.width(12.dp))
                    TextButton(onClick = { MapActionBus.clear() }) {
                        Text("Cancel")
                    }
                }
            }
        }
        
        // Flight data HUD (bottom-left) - shows Altitude, Speed, Pitch, Bank, Mach live from DataPad
        if (datapadEnabled && flightData != null) {
            val fd = flightData!!
            val altFt = (fd.altitude * 3.28084).toInt()
            val speedSource = fd.groundSpeed ?: fd.trueAirspeed ?: fd.indicatedAirspeed
            val speedKts = speedSource?.let { (it * 1.9438).toInt() }
            val pitchDeg = Math.toDegrees(fd.pitch)
            val bankDeg = Math.toDegrees(fd.bank)
            val machVal = fd.mach

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    // leave extra bottom padding so it doesn't overlap bottom-center connection/HUD and FABs
                    .padding(start = 12.dp, bottom = 88.dp)
                    .padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "ALT ${altFt} ft", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)

                    Divider(modifier = Modifier.height(18.dp).width(1.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                    Text(text = "SPD ${speedKts ?: "--"} kt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)

                    Divider(modifier = Modifier.height(18.dp).width(1.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                    Text(text = "P ${String.format("%.1f", pitchDeg)}°", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "B ${String.format("%.1f", bankDeg)}°", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)

                    Divider(modifier = Modifier.height(18.dp).width(1.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                    Text(text = "Mach ${if (machVal != null) String.format("%.2f", machVal) else "--"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Small tip when map lock is disabled (top-right, smaller to avoid overlapping datapad HUD)
        if (!isScreenLocked) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = stringResource(R.string.map_tip_tap_lock),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                )
            }
        }
        
        // Removed old DraggableFab implementations for QuickNote and DataPad
        // These are now handled by the centralized FABOverlay system above
    }
    
        // Pending symbol placement indicator
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            if (pendingSymbolPlacement != null) {
                Surface(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .fillMaxWidth(0.9f),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tap on the map to place ${pendingSymbolPlacement?.first?.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(onClick = { pendingSymbolPlacement = null }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    
    // Disable auto-center when user manually moves the map
    // Also use this effect to perform any pending center once the MapView is available
    LaunchedEffect(mapView) {
        if (mapView != null) {
            // If user requested center earlier but MapView wasn't ready, honor it now
            pendingCenter.value?.let { gp ->
                lastProgrammaticMove.value = System.currentTimeMillis()
                (context as? android.app.Activity)?.runOnUiThread {
                    mapView?.controller?.animateTo(gp)
                    mapView?.invalidate()
                }
                Log.d(TAG, "Applied pending center: ${gp.latitude},${gp.longitude}")
                pendingCenter.value = null
            }

            // Apply persisted overlay preferences immediately (if not already present)
            mapView?.let { mv ->
                if (compassEnabled && compassOverlay == null) {
                    // Fixed-size compass ring
                    val co = CompassOverlay()
                    flightData?.let { d -> if (d.latitude != 0.0 && d.longitude != 0.0) co.center = GeoPoint(d.latitude, d.longitude) }
                    flightData?.let { d -> co.heading = Math.toDegrees(d.heading).toFloat() }
                    mv.overlays.add(co)
                    compassOverlay = co
                    
                    // Speed-based heading line
                    if (headingSpeedLineOverlay == null) {
                        val hsl = HeadingSpeedLineOverlay()
                        flightData?.let { d -> if (d.latitude != 0.0 && d.longitude != 0.0) hsl.center = GeoPoint(d.latitude, d.longitude) }
                        flightData?.let { d -> hsl.heading = Math.toDegrees(d.heading).toFloat() }
                        flightData?.let { d -> hsl.speedKts = ((d.groundSpeed ?: d.trueAirspeed ?: d.indicatedAirspeed ?: 0.0) * 1.9438) }
                        mv.overlays.add(hsl)
                        headingSpeedLineOverlay = hsl
                    }
                }
                if (rangeRingsEnabled && rangeRingsOverlay == null) {
                    val rr = RangeRingsOverlay()
                    rr.maxNm = rangeRingsMaxNm
                    rr.heading = flightData?.heading?.let { Math.toDegrees(it).toFloat() } ?: 0f
                    flightData?.let { d -> if (d.latitude != 0.0 && d.longitude != 0.0) rr.center = GeoPoint(d.latitude, d.longitude) }
                    mv.overlays.add(rr)
                    rangeRingsOverlay = rr
                }
                if (mgrsGridEnabled && mgrsGridOverlay == null) {
                    val mg = MgrsGridOverlay()
                    mv.overlays.add(mg)
                    mgrsGridOverlay = mg
                }
                mv.invalidate()
            }
        }

        // This is a simplified approach - in production you'd listen to map scroll events continuously
        while (isActive) {
            delay(5000)
            // Auto-center can be manually re-enabled via button
        }
    }
    
    // Layer selection dialog
    if (showLayerDialog) {
        LayerSelectionDialog(
            onDismiss = { showLayerDialog = false },
            onLayerSelected = { id ->
                if (id != null) {
                    val ts = tileSourceForId(id)
                    mapView?.setTileSource(ts)
                    prefsManager.setMapTileSourceId(id)
                } else {
                    // follow system theme
                    prefsManager.setMapTileSourceId(null)
                    mapView?.setTileSource(if (isDarkTheme) darkTile else TileSourceFactory.MAPNIK)
                }
                showLayerDialog = false
            }
        )
    }

    // Overlay selection dialog
    if (showOverlayDialog) {
        OverlaySelectionDialog(
            compassEnabled = compassEnabled,
            rangeRingsEnabled = rangeRingsEnabled,
            rangeRingsMaxNm = rangeRingsMaxNm,
            mgrsGridEnabled = mgrsGridEnabled,
            onDismiss = { showOverlayDialog = false },
            onToggleCompass = { enabled ->
                compassEnabled = enabled
                prefsManager.setMapOverlayCompassEnabled(enabled)
                // apply immediately
                mapView?.let { mv ->
                    // remove existing compass and heading line
                    compassOverlay?.let { mv.overlays.remove(it) }
                    compassOverlay = null
                    headingSpeedLineOverlay?.let { mv.overlays.remove(it) }
                    headingSpeedLineOverlay = null
                    if (enabled) {
                        // Fixed-size compass ring
                        val co = CompassOverlay()
                        flightData?.let { d -> if (d.latitude != 0.0 && d.longitude != 0.0) co.center = GeoPoint(d.latitude, d.longitude) }
                        flightData?.let { d -> co.heading = Math.toDegrees(d.heading).toFloat() }
                        mv.overlays.add(co)
                        compassOverlay = co
                        
                        // Speed-based heading line
                        val hsl = HeadingSpeedLineOverlay()
                        flightData?.let { d -> if (d.latitude != 0.0 && d.longitude != 0.0) hsl.center = GeoPoint(d.latitude, d.longitude) }
                        flightData?.let { d -> hsl.heading = Math.toDegrees(d.heading).toFloat() }
                        flightData?.let { d -> hsl.speedKts = ((d.groundSpeed ?: d.trueAirspeed ?: d.indicatedAirspeed ?: 0.0) * 1.9438) }
                        mv.overlays.add(hsl)
                        headingSpeedLineOverlay = hsl
                    }
                    mv.invalidate()
                }
            },
            onToggleRangeRings = { enabled ->
                rangeRingsEnabled = enabled
                prefsManager.setMapOverlayRangeRingsEnabled(enabled)
                mapView?.let { mv ->
                    rangeRingsOverlay?.let { mv.overlays.remove(it) }
                    rangeRingsOverlay = null
                    if (enabled) {
                        val rr = RangeRingsOverlay()
                        rr.maxNm = rangeRingsMaxNm
                        rr.heading = flightData?.heading?.let { Math.toDegrees(it).toFloat() } ?: 0f
                        flightData?.let { d -> if (d.latitude != 0.0 && d.longitude != 0.0) rr.center = GeoPoint(d.latitude, d.longitude) }
                        mv.overlays.add(rr)
                        rangeRingsOverlay = rr
                    }
                    mv.invalidate()
                }
            },
            onChangeRangeRingsMaxNm = { nm ->
                rangeRingsMaxNm = nm
                prefsManager.setMapOverlayRangeRingsMaxNm(nm)
                (rangeRingsOverlay as? RangeRingsOverlay)?.let { rr ->
                    rr.maxNm = nm
                    mapView?.invalidate()
                }
            },
            onToggleMgrsGrid = { enabled ->
                mgrsGridEnabled = enabled
                prefsManager.setMapOverlayMgrsGridEnabled(enabled)
                mapView?.let { mv ->
                    mgrsGridOverlay?.let { mv.overlays.remove(it) }
                    mgrsGridOverlay = null
                    if (enabled) {
                        val mg = MgrsGridOverlay()
                        mv.overlays.add(mg)
                        mgrsGridOverlay = mg
                    }
                    mv.invalidate()
                }
            }
        )
    }
    
    // Quick Access Bottom Sheet
    if (showQuickAccess && quickNoteManager != null) {
        QuickAccessSheet(
            onDismiss = { showQuickAccess = false },
            currentDocumentPath = "special://aviation_map",
            currentDocumentName = stringResource(R.string.map_aviation_map)
        )
    }
    // DataPad Popup
    if (showDataPad && datapadEnabled) {
        DataPadPopup(onDismiss = { showDataPad = false })
    }
    
    // Military Symbol Picker Dialog
    if (showMilitarySymbolPicker) {
        MilitarySymbolPickerDialog(
            onDismiss = {
                // Only close the dialog on dismiss; do not clear pending placement here
                showMilitarySymbolPicker = false
            },
            onSymbolSelected = { symbol, affiliation ->
                // Store selected symbol and close dialog; user will tap map to place it
                pendingSymbolPlacement = symbol to affiliation
                Log.d(TAG, "Pending military symbol selected: ${symbol.name} (${affiliation.name})")
                showMilitarySymbolPicker = false
            }
        )
    }
    
    // Radial menu
    if (radialMenuVisible && radialMenuMarker != null) {
        Log.d(TAG, "Rendering RadialMenu at ($radialMenuX, $radialMenuY) for marker ${radialMenuMarker?.name}")
        val items = mutableListOf<RadialMenuItem>().apply {
            add(RadialMenuItem(
                icon = Icons.Default.Info,
                label = "Info",
                onClick = {
                    selectedLocation = radialMenuMarker
                    showMarkerRouteManagement = true
                }
            ))

            add(RadialMenuItem(
                icon = Icons.Default.Edit,
                label = "Edit",
                onClick = {
                    selectedLocation = radialMenuMarker
                    showMarkerRouteManagement = true
                }
            ))

            add(RadialMenuItem(
                icon = Icons.Default.Navigation,
                label = "Navigate",
                onClick = {
                    radialMenuMarker?.let { marker ->
                        activeNavigationTarget = marker
                    }
                }
            ))

            // Only show Delete for non-static markers
            if (radialMenuMarker?.isStatic != 1) {
                add(RadialMenuItem(
                    icon = Icons.Default.Delete,
                    label = "Delete",
                    onClick = {
                        radialMenuMarker?.let { marker ->
                            scope.launch {
                                val repo = locationRepository ?: return@launch
                                repo.deleteLocation(marker.id)
                            }
                        }
                    }
                ))
            }
        }

        RadialMenu(
            centerX = radialMenuX,
            centerY = radialMenuY,
            onDismiss = { radialMenuVisible = false },
            items = items
        )
    }
    
    // Marker/Route Management Sheet (with integrated marker details)
    if (showMarkerRouteManagement) {
        markerRouteViewModel?.let { vm ->
            MarkerRouteManagementSheet(
                viewModel = vm,
                onDismiss = { 
                    showMarkerRouteManagement = false
                    selectedLocation = null
                },
                onMarkerClick = { marker ->
                    // Update selected location to show details in tab
                    selectedLocation = marker
                },
                onRouteClick = { route ->
                    // Load and display route on map
                    scope.launch {
                        val repo = routeRepository ?: return@launch
                        val routeData = repo.getRouteWithWaypoints(route.id)
                        routeData?.let { data ->
                            // Draw route on map
                            val waypoints = data.waypoints.map { wpWithLoc ->
                                Triple(
                                    wpWithLoc.location,
                                    wpWithLoc.waypoint.distanceNm,
                                    wpWithLoc.waypoint.headingMag
                                )
                            }

                            // Parse route color
                            val routeColor = try {
                                android.graphics.Color.parseColor(data.route.color)
                            } catch (e: Exception) {
                                android.graphics.Color.parseColor("#00A8FF") // Default blue
                            }

                            mapView?.let { mv ->
                                drawRouteOnMap(mv, waypoints, routeColor)
                                // Center on first waypoint
                                if (waypoints.isNotEmpty()) {
                                    mv.controller.animateTo(
                                        GeoPoint(waypoints[0].first.latitude, waypoints[0].first.longitude)
                                    )
                                }
                            }
                        }
                    }
                    showMarkerRouteManagement = false
                },
                onCreateRoute = {
                    showMarkerRouteManagement = false
                    routeCreationViewModel?.let { rvm ->
                        rvm.startRouteCreation()
                        showRouteCreation = true
                    }
                },
                onCenter = { location ->
                    // Center map on provided location
                    mapView?.let { mv ->
                        mv.controller.animateTo(GeoPoint(location.latitude, location.longitude))
                    }
                },
                selectedMarker = selectedLocation,
                selectedRunways = selectedRunways,
                onSetActiveRoute = { location ->
                    activeNavigationTarget = location
                    // Auto-center disabled so user can see the full route
                    autoCenter = false
                    // Optionally close the sheet
                    showMarkerRouteManagement = false
                },
                onEditRouteWaypoints = { routeId ->
                    // Open RouteCreationSheet in edit mode
                    routeCreationViewModel?.let { rvm ->
                        rvm.startRouteEditing(routeId)
                        showMarkerRouteManagement = false
                        showRouteCreation = true
                    }
                }
            )
        }
    }
    
    // Route Creation Sheet
    if (showRouteCreation) {
        routeCreationViewModel?.let { vm ->
            RouteCreationSheet(
                viewModel = vm,
                onDismiss = {
                    showRouteCreation = false
                    vm.cancelRouteCreation()
                },
                onWaypointClick = { location ->
                    // Center map on waypoint
                    mapView?.controller?.animateTo(GeoPoint(location.latitude, location.longitude))
                },
                onRouteFinished = { routeId ->
                    // Force refresh the route on the map
                    scope.launch {
                        val currentVisibleIds = markerRouteViewModel?.visibleRouteIds?.value ?: emptySet()
                        if (currentVisibleIds.contains(routeId)) {
                            // Route is already visible - toggle off and on to force redraw
                            markerRouteViewModel?.toggleRouteVisibility(routeId)
                            kotlinx.coroutines.delay(50)
                            markerRouteViewModel?.toggleRouteVisibility(routeId)
                        } else {
                            // Route is not visible - make it visible
                            markerRouteViewModel?.toggleRouteVisibility(routeId)
                        }
                    }
                }
            )
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            // Clear navigation line
            navigationLine?.let { line ->
                mapView?.overlays?.remove(line)
            }
            navigationLine = null

            // Clear runway approach lines
            runwayApproachLines.forEach { line ->
                mapView?.overlays?.remove(line)
            }
            runwayApproachLines = emptyList()
            
            // Save navigation state before disposing
            try {
                val navPrefs = context.getSharedPreferences("map_navigation_prefs", android.content.Context.MODE_PRIVATE)
                navPrefs.edit().apply {
                    // Save active navigation target
                    val targetId = activeNavigationTarget?.id ?: -999
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
                    
                    apply()
                }
                android.util.Log.d("MapViewer", "Saved navigation state: target=${activeNavigationTarget?.name}, approach=$showRunwayApproach, pattern=$showTrafficPattern")
            } catch (e: Exception) {
                android.util.Log.e("MapViewer", "Failed to save navigation state", e)
            }
            
            // Save current map center/zoom and tile preferences on dispose
            try {
                mapView?.let { mv ->
                    val center = mv.mapCenter
                    prefsManager.setMapCenter(center.latitude, center.longitude)
                    prefsManager.setMapZoom(mv.zoomLevelDouble)
                    prefsManager.setMapAutoCenter(autoCenter)
                    // persist overlay preferences and remove overlays
                    prefsManager.setMapOverlayCompassEnabled(compassEnabled)
                    prefsManager.setMapOverlayRangeRingsEnabled(rangeRingsEnabled)
                    prefsManager.setMapOverlayRangeRingsMaxNm(rangeRingsMaxNm)
                    try { compassOverlay?.let { mv.overlays.remove(it) } } catch (_: Exception) {}
                    try { headingSpeedLineOverlay?.let { mv.overlays.remove(it) } } catch (_: Exception) {}
                    try { rangeRingsOverlay?.let { mv.overlays.remove(it) } } catch (_: Exception) {}
                    // tile source id is persisted when the user explicitly selects a layer via the dialog.
                }
            } catch (e: Exception) {
                // ignore
            }
            mapView?.onDetach()
        }
    }
}

/**
 * Dialog for selecting map tile sources
 */
@Composable
private fun LayerSelectionDialog(
    onDismiss: () -> Unit,
    onLayerSelected: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_layers_dialog_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.map_select_layer),
                    style = MaterialTheme.typography.bodyMedium
                )

                // Follow system theme (auto dark/light)
                OutlinedButton(
                    onClick = { onLayerSelected(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.map_follow_system_theme))
                }
                
                // Standard OpenStreetMap
                OutlinedButton(
                    onClick = { onLayerSelected("MAPNIK") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.map_openstreetmap))
                }
                
                // Topographic
                OutlinedButton(
                    onClick = { onLayerSelected("OpenTopo") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.map_topographic))
                }
                
                // Satellite (if available)
                OutlinedButton(
                    onClick = { onLayerSelected("USGS_SAT") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.map_satellite))
                }

                // Dark themed map (CartoDB Dark Matter)
                OutlinedButton(
                    onClick = { onLayerSelected("CartoDB.DarkMatter") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.map_dark))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(R.string.map_note_aviation_charts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

/**
 * Dialog for selecting overlays (compass, range rings)
 */
@Composable
private fun OverlaySelectionDialog(
    compassEnabled: Boolean,
    rangeRingsEnabled: Boolean,
    rangeRingsMaxNm: Int,
    mgrsGridEnabled: Boolean,
    onDismiss: () -> Unit,
    onToggleCompass: (Boolean) -> Unit,
    onToggleRangeRings: (Boolean) -> Unit,
    onChangeRangeRingsMaxNm: (Int) -> Unit,
    onToggleMgrsGrid: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_overlays_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.map_compass), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.map_compass_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = compassEnabled, onCheckedChange = onToggleCompass)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.map_range_rings), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.map_range_rings_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = rangeRingsEnabled, onCheckedChange = onToggleRangeRings)
                }

                if (rangeRingsEnabled) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(stringResource(R.string.map_range_rings_max_label, rangeRingsMaxNm), style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = rangeRingsMaxNm.toFloat(),
                            onValueChange = { onChangeRangeRingsMaxNm(it.toInt()) },
                            valueRange = 1f..500f,
                            steps = 499,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(stringResource(R.string.map_range_rings_max_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("MGRS Grid", style = MaterialTheme.typography.bodyMedium)
                        Text("Military Grid Reference System overlay", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = mgrsGridEnabled, onCheckedChange = onToggleMgrsGrid)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}
