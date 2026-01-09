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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.combine
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.awaitCancellation
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
import com.example.checklist_interactive.ui.maps.drawing.*
import com.example.checklist_interactive.data.tactical.LocationEntity
import com.example.checklist_interactive.data.tactical.RunwayEntity
import com.example.checklist_interactive.ui.maps.ui.MapNavigationDisplay
import com.example.checklist_interactive.ui.maps.ui.MapRadialMenuDisplay
import com.example.checklist_interactive.ui.maps.ui.OverlaySelectionDialog
import com.example.checklist_interactive.ui.maps.MapFlightInstruments
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
// Helper: create an airplane emoji bitmap drawable (no rotation - handled by Marker.rotation)
private fun createPlaneDrawable(ctx: Context, sizeDp: Float, color: Int): BitmapDrawable {
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

private const val TAG = "MapViewer"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapViewer(
    modifier: Modifier = Modifier,
    isScreenLocked: Boolean = false,
    onLockScreen: () -> Unit = {},
    onTacticalUnitsOpen: () -> Unit = {}
) {
    val context = LocalContext.current
    val dataPadManager = LocalDataPadManager.current
    val quickNoteManager = LocalQuickNoteManager.current
    val flightData by dataPadManager.flightData.collectAsState()
    val isConnected by dataPadManager.isConnected.collectAsState()
    val datapadEnabled by dataPadManager.isEnabled.collectAsState()

    // State holder for all MapViewer state
    val mapState = rememberMapViewerState()
    
    // Load previous map state from preferences
    val prefsManager = remember { com.example.checklist_interactive.data.prefs.PreferencesManager(context) }
    val savedCenter = remember { prefsManager.getMapCenter() }
    val savedZoom = remember { prefsManager.getMapZoom() }
    
    // Initialize flight path interval from preferences
    LaunchedEffect(Unit) {
        mapState.flightPathIntervalSeconds = prefsManager.getFlightPathIntervalSeconds()
    }

    // Pending move marker id (driven by MapActionBus)
    val pendingMoveMarkerIdFlow = MapActionBus.pendingMoveMarkerId
    val pendingMoveMarkerId by pendingMoveMarkerIdFlow.collectAsState(initial = null)

    // Map from Marker object to LocationEntity to robustly find marker data
    val markerToLocation = remember { mutableMapOf<org.osmdroid.views.overlay.Marker, com.example.checklist_interactive.data.tactical.LocationEntity>() }

    // Create a coroutine scope for state updates from listeners
    val scope = rememberCoroutineScope()

    // Extract string resources for use in non-composable contexts (lambdas)
    val msgDbNotReady = stringResource(R.string.map_db_not_ready_retry)
    val msgFabPositionsReset = stringResource(R.string.fab_positions_reset)
    val msgDrawingSaved = stringResource(R.string.map_drawing_saved)

    // Density for dp<->px conversions needed by effects below
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    // DB ready state
    val dbReady = mapState.tacticalDb != null

    // Initialize DB off the main thread to avoid long blocking operations during composition
    LaunchedEffect(Unit) {
        mapState.initializeDatabase()
    }

    // Repositories and viewmodels are created when DB is available
    val locationRepository = remember(mapState.tacticalDb) { 
        mapState.tacticalDb?.let { 
            android.util.Log.d("MapViewer", "Creating LocationRepository from tacticalDb")
            com.example.checklist_interactive.data.tactical.LocationRepositoryImpl(it.locationDao()) 
        }.also {
            android.util.Log.d("MapViewer", "LocationRepository state: ${if (it != null) "READY" else "NULL"}")
        }
    }
    val routeRepository = remember(mapState.tacticalDb) { 
        mapState.tacticalDb?.let { 
            com.example.checklist_interactive.data.tactical.RouteRepositoryImpl(it.routeDao(), it.locationDao()) 
        }
    }
    val flightPathRepository = remember(mapState.tacticalDb) {
        mapState.tacticalDb?.let {
            com.example.checklist_interactive.ui.maps.route.FlightPathRepository(context, it.flightPathDao())
        }
    }
    val markerRouteViewModel = remember(mapState.tacticalDb) { if (locationRepository != null && routeRepository != null) MarkerRouteViewModel(locationRepository, routeRepository) else null }
    val routeCreationViewModel = remember(mapState.tacticalDb) { if (routeRepository != null && locationRepository != null && mapState.tacticalDb != null) MultiWaypointRouteViewModel(context.applicationContext as Application, routeRepository, locationRepository, mapState.tacticalDb!!.runwayDao()) else null }

    // Tactical Units Repository for live unit tracking
    val tacticalUnitsRepository = remember(mapState.tacticalDb) {
        mapState.tacticalDb?.let {
            android.util.Log.d("MapViewer", "Creating TacticalUnitsRepository")
            com.example.checklist_interactive.data.tactical.TacticalUnitsRepository(context)
        }.also {
            android.util.Log.d("MapViewer", "TacticalUnitsRepository state: ${if (it != null) "READY" else "NULL"}")
        }
    }

    // Combined ready state: DB AND repositories must be initialized
    val repositoriesReady = dbReady && locationRepository != null && routeRepository != null

    // Load and restore visible routes from SharedPreferences when MarkerRouteViewModel becomes available
    LaunchedEffect(markerRouteViewModel) {
        mapState.restoreVisibleRoutes(markerRouteViewModel)
    }

    // Restore solo navigation state from SharedPreferences
    LaunchedEffect(dbReady, locationRepository) {
        mapState.restoreNavigationState(locationRepository)
    }

    // Load runways for the selected location from the DB (only when DB is ready)
    LaunchedEffect(mapState.selectedLocation?.id, dbReady) {
        mapState.loadRunwaysForSelectedLocation()
    }

    // Listen for center map events from MapActionBus
    LaunchedEffect(mapState.mapView) {
        MapActionBus.centerMapEvent.collect { (latitude, longitude) ->
            mapState.mapView?.let { mv ->
                (context as? android.app.Activity)?.runOnUiThread {
                    try {
                        mv.controller?.animateTo(org.osmdroid.util.GeoPoint(latitude, longitude))
                        mv.invalidate()
                    } catch (_: Exception) { }
                }
            }
        }
    }

    // Manage airport labels overlay lifecycle
    LaunchedEffect(mapState.mapView, mapState.showMarkerLabels, locationRepository, mapState.tacticalDb) {
        mapState.mapView?.let { mv ->
            if (mapState.showMarkerLabels && locationRepository != null && mapState.tacticalDb != null) {
                // Add overlay if not present
                if (mapState.airportLabelsOverlay == null) {
                    val labelsOverlay = AirportMarkerLabelsOverlay(
                        context = context,
                        locationRepository = locationRepository,
                        database = mapState.tacticalDb!!,
                        getVisibleMaps = { prefsManager.getVisibleMaps().toList() },
                        isEnabled = { mapState.showMarkerLabels },
                        getMapView = { mapState.mapView }
                    )
                    mv.overlays.add(labelsOverlay)
                    mapState.airportLabelsOverlay = labelsOverlay
                    mv.invalidate()
                }
            } else {
                // Remove overlay if present
                mapState.airportLabelsOverlay?.let { overlay ->
                    overlay.cleanup()
                    mv.overlays.remove(overlay)
                    mapState.airportLabelsOverlay = null
                    mv.invalidate()
                }
            }
        }
    }

    // Manage airspace circles overlay lifecycle
    LaunchedEffect(mapState.mapView, mapState.showAirspaceCircles, mapState.enabledAirspaceClasses, mapState.originalAirportTarget, mapState.airspaceFillTransparency) {
        Log.d(TAG, "🌐 Airspace overlay lifecycle triggered - show=${mapState.showAirspaceCircles}, airport=${mapState.originalAirportTarget?.name}, enabled=${mapState.enabledAirspaceClasses.size} classes")
        mapState.mapView?.let { mv ->
            if (mapState.showAirspaceCircles && mapState.originalAirportTarget != null) {
                // Remove existing overlay if present (will be recreated with new settings)
                mapState.airspaceCirclesOverlay?.let { oldOverlay ->
                    Log.d(TAG, "🌐 Removing old airspace overlay")
                    mv.overlays.remove(oldOverlay)
                    mapState.airspaceCirclesOverlay = null
                }

                // Create new overlay with current settings
                Log.d(TAG, "🌐 Creating new airspace overlay for ${mapState.originalAirportTarget?.name}")
                val airspaceOverlay = AirspaceCirclesOverlay(
                    isEnabled = { mapState.showAirspaceCircles },
                    getTargetAirport = { mapState.originalAirportTarget },
                    getEnabledAirspaces = { mapState.enabledAirspaceClasses },
                    getFillTransparency = { mapState.airspaceFillTransparency }
                )
                // Add at position 0 so airspace rings are drawn first (in background)
                // This ensures all labels (tactical units + airports) appear on top
                mv.overlays.add(0, airspaceOverlay)
                mapState.airspaceCirclesOverlay = airspaceOverlay
                mv.invalidate()
            } else {
                // Remove overlay if present
                mapState.airspaceCirclesOverlay?.let { overlay ->
                    Log.d(TAG, "🌐 Disabling airspace - removing overlay")
                    mv.overlays.remove(overlay)
                    mapState.airspaceCirclesOverlay = null
                    mv.invalidate()
                }
            }
        }
    }

    // Listen for show tactical unit details events from MapActionBus
    LaunchedEffect(Unit) {
        MapActionBus.showTacticalUnitDetailsEvent.collect { tacticalUnit ->
            // Convert TacticalUnitEntity to LocationEntity for display
            val tempLocation = com.example.checklist_interactive.data.tactical.LocationEntity(
                id = 0, // Temporary, no DB ID
                name = "${tacticalUnit.name} (${tacticalUnit.category})",
                latitude = tacticalUnit.latitude,
                longitude = tacticalUnit.longitude,
                elevationM = tacticalUnit.altitude,
                markerType = "tactical_unit",
                description = buildString {
                    append("Coalition: ")
                    append(when (tacticalUnit.coalition) {
                        0 -> "Neutral"
                        1 -> "Red"
                        2 -> "Blue"
                        else -> "Unknown"
                    })
                    tacticalUnit.groupName?.let { append("\nGroup: $it") }
                    tacticalUnit.pilotName?.let { append("\nPilot: $it") }
                },
                isStatic = 1,
                source = "tactical_tracking",
                updatedAt = tacticalUnit.lastSeenAt,
                metadata = org.json.JSONObject().apply {
                    put("tactical_unit_id", tacticalUnit.id)
                    tacticalUnit.heading?.let { put("heading", it) }
                    put("is_highlighted", tacticalUnit.isHighlighted)
                }.toString()
            )
            
            // Show marker details popup
            mapState.selectedLocation = tempLocation
            mapState.showMarkerRouteManagement = true
        }
    }

    // Observe visible routes and draw them on map
    val visibleRouteIds by (markerRouteViewModel?.visibleRouteIds?.collectAsState(initial = emptySet()) ?: remember { mutableStateOf(emptySet<Int>()) })
    // Also observe all routes so changes (e.g. color updates) trigger a redraw
    val allRoutesForRedraw by (markerRouteViewModel?.allRoutes?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList<com.example.checklist_interactive.data.tactical.RouteEntity>()) })

    // Save visible routes to SharedPreferences when they change (but only after initial restoration completes)
    LaunchedEffect(visibleRouteIds, mapState.routesRestored) {
        mapState.saveVisibleRoutes(visibleRouteIds)
    }
    
    // Drawing state
    var drawingState by remember { mutableStateOf(MapDrawingState()) }
    var showDrawingToolPopup by remember { mutableStateOf(false) }
    val mapDrawings = remember { mutableStateListOf<MapDrawingStroke>() }
    var currentDrawingStroke by remember { mutableStateOf<MapDrawingStroke?>(null) }

    // Debouncing for drawing saves (prevents blocking UI on rapid drawing)
    var saveDrawingsJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    // Load drawings from database - triggers on every composition (when re-entering map)
    // This ensures drawings are always reloaded from DB when user returns to the map
    LaunchedEffect(Unit) {
        mapState.tacticalDb?.let { db ->
            try {
                // Use .first() to get initial data ONCE without subscribing to updates
                // This prevents re-triggering recomposition on every DB insert
                val entities = db.mapDrawingDao().getAllDrawings().first()
                val strokes = entities.map { MapDrawingStroke.fromEntity(it) }
                mapDrawings.clear()
                mapDrawings.addAll(strokes)
                android.util.Log.d("MapViewer", "Loaded ${strokes.size} drawings from database (initial load)")
            } catch (e: Exception) {
                android.util.Log.e("MapViewer", "Failed to load drawings", e)
            }
        } ?: run {
            // If DB not ready yet, wait for it
            snapshotFlow { mapState.tacticalDb }.collect { db ->
                if (db != null) {
                    try {
                        val entities = db.mapDrawingDao().getAllDrawings().first()
                        val strokes = entities.map { MapDrawingStroke.fromEntity(it) }
                        mapDrawings.clear()
                        mapDrawings.addAll(strokes)
                        android.util.Log.d("MapViewer", "Loaded ${strokes.size} drawings from database (after DB ready)")
                        strokes.forEach { stroke ->
                            android.util.Log.d("MapViewer", "  Drawing: id=${stroke.id}, points=${stroke.geoPoints.size}, color=${stroke.color}")
                            val samplePoints = stroke.geoPoints.take(3).map { "(lat=${it.latitude}, lon=${it.longitude})" }.joinToString(", ")
                            android.util.Log.d("MapViewer", "    Sample points: $samplePoints")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MapViewer", "Failed to load drawings", e)
                    }
                }
            }
        }
    }
    
    // Save drawings to database with debouncing (prevents UI blocking on rapid drawing)
    fun saveDrawings() {
        // Cancel any pending save job
        saveDrawingsJob?.cancel()

        // Schedule new save after debounce delay
        saveDrawingsJob = scope.launch {
            // Debounce delay: wait for user to stop drawing
            kotlinx.coroutines.delay(500)

            withContext(Dispatchers.IO) {
                mapState.tacticalDb?.let { db ->
                    try {
                        val timestamp = java.time.Instant.now().toString()
                        // Only save strokes that don't have an ID yet (new strokes)
                        val newStrokesIndices = mapDrawings.withIndex().filter { it.value.id == 0 }

                        if (newStrokesIndices.isNotEmpty()) {
                            // Batch insert for better performance
                            val entities = newStrokesIndices.map { (_, stroke) ->
                                stroke.toEntity(timestamp, timestamp)
                            }

                            // Use batch insert
                            db.mapDrawingDao().insertDrawings(entities)

                            // Get the inserted IDs by querying the last N inserted rows
                            // Note: This is a workaround since batch insert doesn't return IDs
                            // We'll update the strokes with IDs from a fresh query
                            withContext(Dispatchers.Main) {
                                try {
                                    val allFromDb = db.mapDrawingDao().getAllDrawings().first()
                                    // Match by comparing geopoints (crude but works for just-inserted items)
                                    newStrokesIndices.forEach { (index, stroke) ->
                                        val matchingEntity = allFromDb.find { entity ->
                                            // Simple match: same color and similar point count
                                            entity.color == stroke.color.value.toLong() &&
                                            stroke.id == 0 // Only update if not yet assigned
                                        }
                                        if (matchingEntity != null) {
                                            mapDrawings[index] = stroke.copy(id = matchingEntity.id)
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("MapViewer", "Could not update stroke IDs after batch insert", e)
                                }
                            }

                            android.util.Log.d("MapViewer", "Batch saved ${newStrokesIndices.size} new drawings (debounced)")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MapViewer", "Failed to save drawings", e)
                    }
                }
            }
        }
    }
    
    // Delete drawings from database (runs on IO dispatcher to avoid blocking UI)
    fun deleteDrawings(strokes: List<MapDrawingStroke>) {
        scope.launch(Dispatchers.IO) {
            mapState.tacticalDb?.let { db ->
                try {
                    // Only delete strokes that have an ID (already in DB)
                    val toDelete = strokes.filter { it.id != 0 }
                    toDelete.forEach { stroke ->
                        db.mapDrawingDao().deleteDrawingById(stroke.id)
                    }
                    android.util.Log.d("MapViewer", "Deleted ${toDelete.size} drawings from database")
                } catch (e: Exception) {
                    android.util.Log.e("MapViewer", "Failed to delete drawings", e)
                }
            }
        }
    }

    // Flight path recording: Monitor repository state and update UI
    LaunchedEffect(flightPathRepository) {
        flightPathRepository?.let { repo ->
            // Observe recording state
            repo.isRecordingEnabled.collect { enabled ->
                mapState.flightPathRecording = enabled
                android.util.Log.d("MapViewer", "Flight path recording: $enabled")
            }
        }
    }
    
    // Flight path: Monitor point count
    LaunchedEffect(flightPathRepository) {
        flightPathRepository?.pointCount?.collect { count ->
            mapState.flightPathPointCount = count
        }
    }
    
    // Flight path: Process incoming flight data for recording
    LaunchedEffect(flightPathRepository, flightData) {
        flightPathRepository?.processFlightData(flightData)
    }

    // Save map settings when they change
    LaunchedEffect(mapState.autoCenter) {
        prefsManager.setMapAutoCenter(mapState.autoCenter)
    }
    
    LaunchedEffect(mapState.rotationGestureEnabled) {
        prefsManager.setMapRotationGestureEnabled(mapState.rotationGestureEnabled)
    }
    
    LaunchedEffect(mapState.flightInstrumentsEnabled) {
        prefsManager.setMapOverlayFlightInstrumentsEnabled(mapState.flightInstrumentsEnabled)
    }
    
    LaunchedEffect(mapState.flightPathEnabled) {
        prefsManager.setFlightPathEnabled(mapState.flightPathEnabled)
    }


    // Save navigation state when it changes (but only after initial restoration completes)
    LaunchedEffect(
        mapState.activeNavigationTarget?.id,
        mapState.showRunwayApproach,
        mapState.showTrafficPattern,
        mapState.selectedRunwayIndex,
        mapState.finalApproachDistanceNm,
        mapState.patternSize,
        mapState.patternDirection,
        mapState.patternFinalDistanceNm,
        mapState.roundedPatternCorners,
        mapState.navigationRestored
    ) {
        mapState.saveNavigationState()
    }
    LaunchedEffect(visibleRouteIds, mapState.mapView, allRoutesForRedraw) {
        val mv = mapState.mapView ?: return@LaunchedEffect
        
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

    // Flight path rendering: Draw the recorded path as a polyline on the map
    LaunchedEffect(mapState.flightPathEnabled, flightPathRepository, mapState.mapView) {
        val mv = mapState.mapView ?: return@LaunchedEffect
        
        // Remove existing flight path overlay
        mapState.flightPathPolyline?.let { mv.overlays.remove(it) }
        mapState.flightPathPolyline = null
        
        if (mapState.flightPathEnabled && flightPathRepository != null) {
            // Load path points and create polyline
            scope.launch(Dispatchers.IO) {
                try {
                    val points = flightPathRepository.getPathAsGeoPoints()
                    withContext(Dispatchers.Main) {
                        if (points.size >= 2) {
                            val polyline = org.osmdroid.views.overlay.Polyline(mv).apply {
                                outlinePaint.color = android.graphics.Color.parseColor("#FF6B00") // Orange color
                                outlinePaint.strokeWidth = 6f
                                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                outlinePaint.alpha = 200 // Slightly transparent
                                setPoints(points)
                                id = "flight_path_polyline"
                            }
                            // Add polyline below markers but above base layers
                            mv.overlays.add(0, polyline)
                            mapState.flightPathPolyline = polyline
                            mv.invalidate()
                            android.util.Log.d("MapViewer", "Flight path rendered with ${points.size} points")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MapViewer", "Failed to render flight path: ${e.message}", e)
                }
            }
        } else {
            mv.invalidate()
        }
    }

    // Flight path: Update polyline when new points are added (reactive updates)
    LaunchedEffect(mapState.flightPathPointCount, mapState.flightPathEnabled) {
        val mv = mapState.mapView ?: return@LaunchedEffect
        
        if (mapState.flightPathEnabled && mapState.flightPathPointCount > 0 && flightPathRepository != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val points = flightPathRepository.getPathAsGeoPoints()
                    withContext(Dispatchers.Main) {
                        if (points.size >= 2) {
                            // Update existing polyline or create new one
                            val polyline = mapState.flightPathPolyline
                            if (polyline != null) {
                                polyline.setPoints(points)
                            } else {
                                val newPolyline = org.osmdroid.views.overlay.Polyline(mv).apply {
                                    outlinePaint.color = android.graphics.Color.parseColor("#FF6B00")
                                    outlinePaint.strokeWidth = 6f
                                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                    outlinePaint.alpha = 200
                                    setPoints(points)
                                    id = "flight_path_polyline"
                                }
                                mv.overlays.add(0, newPolyline)
                                mapState.flightPathPolyline = newPolyline
                            }
                            
                            // Add/update start point marker
                            if (mapState.flightPathStartMarker == null) {
                                val startMarker = Marker(mv).apply {
                                    position = points.first()
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = context.getString(R.string.map_flight_path_start)
                                    snippet = "Start Point"
                                    // Use generated plane bitmap drawable (green) so we don't rely on an external resource
                                    icon = createPlaneDrawable(context, 20f, android.graphics.Color.parseColor("#4CAF50"))
                                }
                                mv.overlays.add(startMarker)
                                mapState.flightPathStartMarker = startMarker
                            } else {
                                mapState.flightPathStartMarker?.position = points.first()
                            }
                            
                            mv.invalidate()
                        } else {
                            // Points exist in DB but < 2 - remove polyline
                            mapState.flightPathPolyline?.let { mv.overlays.remove(it) }
                            mapState.flightPathPolyline = null
                            mapState.flightPathStartMarker?.let { mv.overlays.remove(it) }
                            mapState.flightPathStartMarker = null
                            mv.invalidate()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MapViewer", "Failed to update flight path: ${e.message}", e)
                }
            }
        } else {
            // Path disabled or no points - remove polyline from map
            mapState.flightPathPolyline?.let { mv.overlays.remove(it) }
            mapState.flightPathPolyline = null
            mapState.flightPathStartMarker?.let { mv.overlays.remove(it) }
            mapState.flightPathStartMarker = null
            mv.invalidate()
            android.util.Log.d("MapViewer", "Flight path polyline removed (disabled or cleared)")
        }
    }


    // Track last programmatic map movement to avoid treating it as a user scroll
    val lastProgrammaticMove = remember { mutableStateOf(0L) }
    // Track last user touch time so we only disable auto-center on real user interactions
    val lastUserTouch = remember { mutableStateOf(0L) }
    // When user presses the center button but the MapView isn't attached yet, store the requested center
    val pendingCenter = remember { mutableStateOf<GeoPoint?>(null) }

    // Initialize osmdroid configuration
    LaunchedEffect(Unit) {
        mapState.initializeOsmdroidConfig()
    }
    
    // Map theme helper
    val isDarkTheme = isSystemInDarkTheme()

    // Create and cache the drawables for the plane icon
    val planeDrawableDark = remember(context) {
        createPlaneDrawable(context, 28f, android.graphics.Color.WHITE)
    }
    val planeDrawableLight = remember(context) {
        createPlaneDrawable(context, 28f, android.graphics.Color.BLACK)
    }

    // Update the marker icon whenever the theme changes
    LaunchedEffect(isDarkTheme, mapState.positionMarker) {
        mapState.positionMarker?.let { marker ->
            marker.icon = if (isDarkTheme) planeDrawableDark else planeDrawableLight
            mapState.mapView?.invalidate()
        }
    }
    
    // Separate effect for map rotation to prevent flicker from frequent data updates
    LaunchedEffect(mapState.mapRotationMode, flightData?.heading) {
        val map = mapState.mapView ?: return@LaunchedEffect
        val rotationMode = mapState.mapRotationMode
        val fd = flightData // local copy to allow safe smart-cast

        // When mode changes to North-up, reset orientation once
        if (rotationMode == 0) {
            try {
                map.setMapOrientation(0f)
                // Update pattern labels to stay upright
                mapState.trafficPatternLabelOverlay?.updateMapRotation(0f)
                map.invalidate()
            } catch (_: Throwable) {}
        }

        // In HDG-up mode, update rotation based on heading
        if (rotationMode == 1) {
            val heading = fd?.heading
            if (heading != null) {
                try {
                    val rotation = -Math.toDegrees(heading).toFloat()
                    map.setMapOrientation(rotation)
                    // Update pattern labels to stay upright
                    mapState.trafficPatternLabelOverlay?.updateMapRotation(rotation)
                    map.invalidate()
                } catch (_: Throwable) {}
            }
        }
    }

    // Update live navigation line when flight data or target changes
    // Include target position (lat/lon) to auto-update when marker moves
    LaunchedEffect(
        flightData,
        mapState.activeNavigationTarget,
        mapState.activeNavigationTarget?.latitude,
        mapState.activeNavigationTarget?.longitude
    ) {
        val data = flightData
        val target = mapState.activeNavigationTarget
        val map = mapState.mapView

        if (data != null && target != null && map != null && data.latitude != 0.0 && data.longitude != 0.0) {
            val playerPos = GeoPoint(data.latitude, data.longitude)
            val targetPos = GeoPoint(target.latitude, target.longitude)
            
            // Calculate distance and heading
            val distanceMeters = playerPos.distanceToAsDouble(targetPos)
            val distanceNm = distanceMeters / 1852.0
            mapState.navigationDistanceNm = distanceNm
            
            // Calculate bearing/heading
            val lat1 = Math.toRadians(playerPos.latitude)
            val lat2 = Math.toRadians(targetPos.latitude)
            val lon1 = Math.toRadians(playerPos.longitude)
            val lon2 = Math.toRadians(targetPos.longitude)
            val dLon = lon2 - lon1
            val y = Math.sin(dLon) * Math.cos(lat2)
            val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
            val bearing = (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
            mapState.navigationHeading = bearing
            
            // Update or create red navigation line
            scope.launch {
                val line = mapState.navigationLine ?: org.osmdroid.views.overlay.Polyline(map).apply {
                    outlinePaint.color = android.graphics.Color.argb(128, 255, 0, 0) // 50% transparent red
                    outlinePaint.strokeWidth = 10f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    map.overlays.add(this)
                    mapState.navigationLine = this
                }
                line.setPoints(listOf(playerPos, targetPos))
                map.invalidate()
            }
        } else if (target == null && mapState.navigationLine != null) {
            // Remove navigation line if target cleared
            scope.launch {
                mapState.mapView?.overlays?.remove(mapState.navigationLine)
                mapState.navigationLine = null
                mapState.navigationDistanceNm = null
                mapState.navigationHeading = null
                mapState.mapView?.invalidate()
            }
        }
    }

    // Load runways for active navigation target
    LaunchedEffect(mapState.activeNavigationTarget) {
        mapState.loadRunwaysForActiveTarget()
    }

    // Observe originalAirportTarget for live position updates (for non-tactical markers)
    LaunchedEffect(mapState.originalAirportTarget?.id, locationRepository) {
        val targetId = mapState.originalAirportTarget?.id
        val repo = locationRepository

        // Only observe if it's a real database marker (id > 0) and not being tracked as tactical unit
        if (targetId != null && targetId > 0 && repo != null && mapState.activeNavigationTacticalUnitId == null) {
            Log.d(TAG, "🔄 Starting live observation for location ID: $targetId")

            repo.observeLocationById(targetId).collect { updatedLocation ->
                if (updatedLocation != null) {
                    // Update originalAirportTarget with fresh data from database
                    mapState.originalAirportTarget = updatedLocation
                    Log.d(TAG, "🔄 Updated originalAirportTarget: ${updatedLocation.name} at (${updatedLocation.latitude}, ${updatedLocation.longitude})")
                }
            }
        }
    }

    // Live update navigation target when tracking a tactical unit
    LaunchedEffect(mapState.activeNavigationTacticalUnitId, tacticalUnitsRepository) {
        val tacticalUnitId = mapState.activeNavigationTacticalUnitId
        val repo = tacticalUnitsRepository

        if (tacticalUnitId != null && tacticalUnitId > 0 && repo != null) {
            Log.d(TAG, "🔄 Starting live tracking for tactical unit ID: $tacticalUnitId")

            // Observe the tactical unit by ID and update navigation target when it changes
            repo.getUnitByIdFlow(tacticalUnitId).collect { tacticalUnit ->
                if (tacticalUnit != null) {
                    // Update the navigation target with the new position
                    val updatedLocation = com.example.checklist_interactive.data.tactical.LocationEntity(
                        id = 0, // Temporary, no DB ID
                        name = "${tacticalUnit.name} (${tacticalUnit.category})",
                        latitude = tacticalUnit.latitude,
                        longitude = tacticalUnit.longitude,
                        elevationM = tacticalUnit.altitude,
                        markerType = "tactical_unit",
                        description = buildString {
                            append("Coalition: ")
                            append(when (tacticalUnit.coalition) {
                                0 -> "Neutral"
                                1 -> "Red"
                                2 -> "Blue"
                                else -> "Unknown"
                            })
                            tacticalUnit.groupName?.let { append("\nGroup: $it") }
                            tacticalUnit.pilotName?.let { append("\nPilot: $it") }
                        },
                        isStatic = 1,
                        source = "tactical_tracking",
                        metadata = org.json.JSONObject().apply {
                            put("tactical_unit_id", tacticalUnit.id)
                            tacticalUnit.heading?.let { put("heading", it) }
                        }.toString()
                    )

                    // Update navigation target (this will trigger the navigation line update)
                    mapState.activeNavigationTarget = updatedLocation
                    // Also update originalAirportTarget to trigger pattern recalculation
                    mapState.originalAirportTarget = updatedLocation
                    Log.d(TAG, "🔄 Updated navigation target position: ${tacticalUnit.name} at (${tacticalUnit.latitude}, ${tacticalUnit.longitude})")
                } else {
                    // Tactical unit no longer exists - clear navigation
                    Log.w(TAG, "⚠️ Tactical unit $tacticalUnitId no longer exists, clearing navigation")
                    mapState.activeNavigationTarget = null
                    mapState.activeNavigationTacticalUnitId = null
                }
            }
        }
    }

    // Helper function to extract runway heading from runway name
    fun extractRunwayHeading(runwayName: String): Double? {
        // Parse runway name like "12/30", "09/27", "13L/31R"
        // Extract first number before "/" and multiply by 10
        val match = runwayName.trim().split("/").firstOrNull()?.trim()
        return match?.replace(Regex("[LCR]"), "")?.toIntOrNull()?.times(10)?.toDouble()
    }

    // Draw runway approach lines when enabled (supports both runways and manual heading)
    // Include target position (lat/lon) to auto-update when marker moves
    LaunchedEffect(
        mapState.showRunwayApproach,
        mapState.targetRunways,
        mapState.originalAirportTarget,
        mapState.originalAirportTarget?.latitude,
        mapState.originalAirportTarget?.longitude,
        mapState.mapView,
        mapState.finalApproachDistanceNm,
        mapState.selectedRunwayHeading
    ) {
        val map = mapState.mapView
        val target = mapState.originalAirportTarget

        Log.d(TAG, "🛬 Approach Lines Effect: showApproach=${mapState.showRunwayApproach}, target=${target?.name}, map=${map != null}, runways=${mapState.targetRunways.size}, heading=${mapState.selectedRunwayHeading}")

        // Remove existing approach lines
        mapState.runwayApproachLines.forEach { line ->
            map?.overlays?.remove(line)
        }
        mapState.runwayApproachLines = emptyList()

        if (mapState.showRunwayApproach && target != null && map != null) {
            val newLines = mutableListOf<org.osmdroid.views.overlay.Polyline>()

            // Case 1: Runways are available - draw approach for each runway
            if (mapState.targetRunways.isNotEmpty()) {
                Log.d(TAG, "🛬 Drawing ${mapState.targetRunways.size} runway approach lines")
                mapState.targetRunways.forEach { runway ->
                val heading = runway.headingDeg ?: extractRunwayHeading(runway.name) ?: 0.0
                val center = GeoPoint(target.latitude, target.longitude)

                // Calculate final approach distance in meters
                val distanceMeters = mapState.finalApproachDistanceNm * 1852.0
                
                // Get runway length in meters
                val runwayLengthMeters = (runway.lengthM?.toDouble() ?: runway.lengthFt?.toDouble()?.times(0.3048)) ?: 2000.0
                val halfRunwayLength = runwayLengthMeters / 2.0

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

                // Calculate runway start/end points (half length in each direction from center)
                val dLatStart = halfRunwayLength * Math.cos(rad1) / 6371000.0
                val dLonStart = halfRunwayLength * Math.sin(rad1) / (6371000.0 * Math.cos(lat1))
                val runwayStart = GeoPoint(Math.toDegrees(lat1 + dLatStart), Math.toDegrees(lon1 + dLonStart))
                
                val dLatEnd = halfRunwayLength * Math.cos(rad2) / 6371000.0
                val dLonEnd = halfRunwayLength * Math.sin(rad2) / (6371000.0 * Math.cos(lat1))
                val runwayEnd = GeoPoint(Math.toDegrees(lat1 + dLatEnd), Math.toDegrees(lon1 + dLonEnd))

                // Calculate perpendicular heading (90 degrees to runway heading)
                val perpHeading = (heading + 90) % 360
                val perpRad = Math.toRadians(perpHeading)
                val perpDistance = 100.0 // 100 meters width for threshold markers
                
                // Helper function to calculate perpendicular line endpoints
                fun getPerpendicularPoints(point: GeoPoint): Pair<GeoPoint, GeoPoint> {
                    val latRad = Math.toRadians(point.latitude)
                    val lonRad = Math.toRadians(point.longitude)
                    
                    // One side
                    val dLat1 = perpDistance * Math.cos(perpRad) / 6371000.0
                    val dLon1 = perpDistance * Math.sin(perpRad) / (6371000.0 * Math.cos(latRad))
                    val side1 = GeoPoint(Math.toDegrees(latRad + dLat1), Math.toDegrees(lonRad + dLon1))
                    
                    // Other side (opposite direction)
                    val perpHeading2 = (perpHeading + 180) % 360
                    val perpRad2 = Math.toRadians(perpHeading2)
                    val dLat2 = perpDistance * Math.cos(perpRad2) / 6371000.0
                    val dLon2 = perpDistance * Math.sin(perpRad2) / (6371000.0 * Math.cos(latRad))
                    val side2 = GeoPoint(Math.toDegrees(latRad + dLat2), Math.toDegrees(lonRad + dLon2))
                    
                    return Pair(side1, side2)
                }

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
                
                // Add perpendicular threshold markers at runway start and end
                val (startSide1, startSide2) = getPerpendicularPoints(runwayStart)
                val thresholdLine1 = org.osmdroid.views.overlay.Polyline(map).apply {
                    outlinePaint.color = android.graphics.Color.argb(180, 255, 255, 0) // Slightly more opaque
                    outlinePaint.strokeWidth = 6f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    setPoints(listOf(startSide1, startSide2))
                    map.overlays.add(this)
                }
                newLines.add(thresholdLine1)
                
                val (endSide1, endSide2) = getPerpendicularPoints(runwayEnd)
                val thresholdLine2 = org.osmdroid.views.overlay.Polyline(map).apply {
                    outlinePaint.color = android.graphics.Color.argb(180, 255, 255, 0) // Slightly more opaque
                    outlinePaint.strokeWidth = 6f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    setPoints(listOf(endSide1, endSide2))
                    map.overlays.add(this)
                }
                newLines.add(thresholdLine2)
            }
            }
            // Case 2: No runways but manual heading is set - draw single approach line
            else if (mapState.selectedRunwayHeading != null) {
                Log.d(TAG, "🛬 Drawing manual approach line with heading ${mapState.selectedRunwayHeading}")
                val heading = mapState.selectedRunwayHeading!!
                val center = GeoPoint(target.latitude, target.longitude)
                val distanceMeters = mapState.finalApproachDistanceNm * 1852.0

                val rad = Math.toRadians(heading)
                val lat1 = Math.toRadians(center.latitude)
                val lon1 = Math.toRadians(center.longitude)
                val dLat = distanceMeters * Math.cos(rad) / 6371000.0
                val dLon = distanceMeters * Math.sin(rad) / (6371000.0 * Math.cos(lat1))
                val endLat = lat1 + dLat
                val endLon = lon1 + dLon
                val endpoint = GeoPoint(Math.toDegrees(endLat), Math.toDegrees(endLon))

                Log.d(TAG, "🛬 Approach line from ${center.latitude},${center.longitude} to ${endpoint.latitude},${endpoint.longitude}")

                val line = org.osmdroid.views.overlay.Polyline(map).apply {
                    outlinePaint.color = android.graphics.Color.argb(128, 255, 255, 0) // 50% transparent yellow
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    setPoints(listOf(center, endpoint))
                    map.overlays.add(this)
                }

                newLines.add(line)
                Log.d(TAG, "🛬 Approach line added to map, total lines: ${newLines.size}")
            } else {
                Log.d(TAG, "🛬 No runways and no manual heading - cannot draw approach")
            }

            if (newLines.isNotEmpty()) {
                mapState.runwayApproachLines = newLines
                map.invalidate()
                Log.d(TAG, "🛬 Map invalidated with ${newLines.size} approach lines")
            }
        } else {
            Log.d(TAG, "🛬 Conditions not met: showApproach=${mapState.showRunwayApproach}, target=${target != null}, map=${map != null}")
        }
    }

    // Recalculate approach point when final approach distance changes
    // Include target position (lat/lon) to auto-update when marker moves
    LaunchedEffect(
        mapState.finalApproachDistanceNm,
        mapState.selectedRunwayIndex,
        mapState.originalAirportTarget?.latitude,
        mapState.originalAirportTarget?.longitude
    ) {
        val index = mapState.selectedRunwayIndex
        val runway = mapState.selectedRunway
        val target = mapState.originalAirportTarget

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
            val distanceMeters = mapState.finalApproachDistanceNm * 1852.0
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
                name = target.name, // Keep original name, RWY indicator shown in UI
                latitude = endpoint.latitude,
                longitude = endpoint.longitude
            )
            mapState.activeNavigationTarget = approachTarget
            mapState.selectedRunwayHeading = heading
        }
    }

    // Generate and draw traffic pattern when enabled (supports both runways and manual heading)
    // Include target position (lat/lon), elevation, and metadata to auto-update when marker moves
    LaunchedEffect(
        mapState.showTrafficPattern,
        mapState.selectedRunway,
        mapState.mapView,
        mapState.patternSize,
        mapState.patternDirection,
        mapState.originalAirportTarget,
        mapState.originalAirportTarget?.latitude,
        mapState.originalAirportTarget?.longitude,
        mapState.originalAirportTarget?.elevationM,
        mapState.originalAirportTarget?.metadata,
        mapState.patternFinalDistanceNm,
        mapState.customPatternAltitudeAglFt,
        mapState.selectedRunwayHeading,
        mapState.roundedPatternCorners
    ) {
        val mv = mapState.mapView ?: return@LaunchedEffect
        val target = mapState.originalAirportTarget ?: return@LaunchedEffect
        
        Log.d(TAG, "✈️ Pattern Effect: show=${mapState.showTrafficPattern}, runway=${mapState.selectedRunway?.name}, heading=${mapState.selectedRunwayHeading}")
        
        if (mapState.showTrafficPattern) {
            // Remove old pattern overlays
            mapState.trafficPatternPolyline?.let { mv.overlays.remove(it) }
            mapState.trafficPatternLabelOverlay?.let { mv.overlays.remove(it) }
            
            // Determine heading and runway properties (runway or manual)
            val runway = mapState.selectedRunway
            val runwayHeading: Double
            val runwayLengthMeters: Double
            val runwayThreshold: GeoPoint
            val headingForPattern: Double
            
            if (runway != null) {
                Log.d(TAG, "✈️ Using runway ${runway.name} for pattern")
                // Case 1: Runway is available - use runway data
                runwayHeading = runway.headingDeg ?: extractRunwayHeading(runway.name) ?: 0.0
                runwayLengthMeters = (runway.lengthM?.toDouble() ?: runway.lengthFt?.toDouble()?.times(0.3048)) ?: 2000.0
                runwayThreshold = GeoPoint(
                    runway.touchdownStartLat ?: target.latitude,
                    runway.touchdownStartLon ?: target.longitude
                )
                // Generate pattern points (use selected runway index to decide which runway end is active)
                val isDirection1 = (mapState.selectedRunwayIndex ?: 0) % 2 == 0
                headingForPattern = if (isDirection1) runwayHeading else (runwayHeading + 180.0) % 360
            } else if (mapState.selectedRunwayHeading != null) {
                Log.d(TAG, "✈️ Using manual heading ${mapState.selectedRunwayHeading} for pattern")
                // Case 2: Manual heading is set (no runway) - use manual heading
                runwayHeading = mapState.selectedRunwayHeading!!
                runwayLengthMeters = 2000.0 // Default 2km runway length for tactical units
                runwayThreshold = GeoPoint(target.latitude, target.longitude)
                headingForPattern = runwayHeading
            } else {
                Log.d(TAG, "✈️ No runway and no manual heading - cannot draw pattern")
                // No heading available - cannot draw pattern
                return@LaunchedEffect
            }

            // Generate pattern directly with correct heading and direction
            // The headingForPattern already accounts for runway direction (07 vs 25)
            // The direction parameter handles LEFT_HAND vs RIGHT_HAND
            // No post-generation mirroring needed - the generator creates the pattern correctly
            Log.d(TAG, "✈️ Generating pattern: heading=$headingForPattern, size=${mapState.patternSize}, dir=${mapState.patternDirection}, rounded=${mapState.roundedPatternCorners}")
            
            // Use the new function that returns corner positions and headings for labels
            // Always use generateTrafficPatternWithCorners to get proper headings for labels
            val (patternPoints, cornerPositions, segmentHeadings) = TrafficPatternGenerator.generateTrafficPatternWithCorners(
                runwayThreshold = runwayThreshold,
                runwayHeading = headingForPattern,
                runwayLengthMeters = runwayLengthMeters,
                patternSize = mapState.patternSize,
                direction = mapState.patternDirection,
                finalDistanceNm = mapState.patternFinalDistanceNm,
                roundedCorners = mapState.roundedPatternCorners
            )
            Log.d(TAG, "✈️ Pattern generated with ${patternPoints.size} points, headings provided: ${segmentHeadings != null}")

            // Create and add pattern polyline
            val polyline = TrafficPatternGenerator.createPatternPolyline(
                points = patternPoints,
                color = 0xFF00FF00.toInt(), // Green for pattern
                width = 5f
            )
            mv.overlays.add(polyline)
            mapState.trafficPatternPolyline = polyline
            Log.d(TAG, "✈️ Pattern polyline added to map")

            // Create and add pattern labels with distance and heading information
            val runwayElevationFt = (target.elevationM?.times(3.28084))?.toInt() ?: 0
            val labels = TrafficPatternGenerator.generatePatternLabels(
                points = patternPoints,
                direction = mapState.patternDirection,
                runwayHeading = headingForPattern,
                patternSize = mapState.patternSize,
                runwayElevationFt = runwayElevationFt,
                customAltitudeAglFt = mapState.customPatternAltitudeAglFt,
                cornerPositions = cornerPositions,
                segmentHeadings = segmentHeadings
            )
            // Pass current map rotation so labels stay upright when map rotates
            val currentMapRotation = mv.mapOrientation
            val labelOverlay = PatternLabelOverlay(labels, currentMapRotation)
            mv.overlays.add(labelOverlay)
            mapState.trafficPatternLabelOverlay = labelOverlay
            
            // Set navigation target to runway threshold (landing point)
            // This creates a red line from current position to the pattern landing point
            val patternTarget = target.copy(
                id = -2, // Special ID for pattern navigation
                name = target.name, // Keep original name, PATTERN indicator shown in UI
                latitude = runwayThreshold.latitude,
                longitude = runwayThreshold.longitude
            )
            mapState.activeNavigationTarget = patternTarget
            // Respect the user's saved navigation panel state; do NOT force it open when restoring/generating a pattern
            // Persist pattern/navigation state (but do not change showNavigationDetails here)
            mapState.saveNavigationState()

            mv.invalidate()
        } else {
            // Remove pattern overlays when disabled
            mapState.trafficPatternPolyline?.let { 
                mv.overlays.remove(it)
                mapState.trafficPatternPolyline = null
            }
            mapState.trafficPatternLabelOverlay?.let {
                mv.overlays.remove(it)
                mapState.trafficPatternLabelOverlay = null
            }
            // Restore navigation to original target if pattern navigation was active (id = -2)
            if (mapState.activeNavigationTarget?.id == -2) {
                // If showRunwayApproach is still active, restore to approach point
                if (mapState.showRunwayApproach && mapState.originalAirportTarget != null && mapState.selectedRunwayHeading != null) {
                    // Recreate approach target with selected runway heading
                    val target = mapState.originalAirportTarget!!
                    val heading = mapState.selectedRunwayHeading!!
                    val distanceMeters = mapState.finalApproachDistanceNm * 1852.0
                    val rad = Math.toRadians(heading)
                    val lat1 = Math.toRadians(target.latitude)
                    val lon1 = Math.toRadians(target.longitude)
                    val dLat = distanceMeters * Math.cos(rad) / 6371000.0
                    val dLon = distanceMeters * Math.sin(rad) / (6371000.0 * Math.cos(lat1))
                    val endLat = lat1 + dLat
                    val endLon = lon1 + dLon
                    val endpoint = org.osmdroid.util.GeoPoint(Math.toDegrees(endLat), Math.toDegrees(endLon))
                    
                    // Extract real runway name from database (e.g., "09/27" -> "09" or "27")
                    // Only calculate from heading if runway name is not available
                    val runwayName = if (mapState.selectedRunway != null) {
                        val names = mapState.selectedRunway?.name?.split("/")?.map { it.trim() } ?: emptyList()
                        val isDirection1 = (mapState.selectedRunwayIndex ?: 0) % 2 == 0
                        names.getOrNull(if (isDirection1) 0 else 1) ?: (heading / 10).toInt().toString().padStart(2, '0')
                    } else {
                        (heading / 10).toInt().toString().padStart(2, '0')
                    }
                    
                    val approachTarget = target.copy(
                        id = -1,
                        name = "${target.name} RWY $runwayName",
                        latitude = endpoint.latitude,
                        longitude = endpoint.longitude
                    )
                    mapState.activeNavigationTarget = approachTarget
                } else {
                    // Otherwise restore to original airport target or clear if none
                    mapState.activeNavigationTarget = mapState.originalAirportTarget
                    // Only clear tactical unit ID if we're completely clearing navigation
                    if (mapState.originalAirportTarget == null) {
                        mapState.activeNavigationTacticalUnitId = null
                    }
                }
            }
            mv.invalidate()
        }
    }

    // Update position marker when flight data changes (throttled to prevent UI blocking)
    LaunchedEffect(Unit) {
        var lastUpdateTime = 0L
        val minUpdateIntervalMs = 20L // Allow up to 50 Hz updates (interpolation handles smoothing)
        
        // Position interpolation state
        var interpolationStartPos: GeoPoint? = null
        var interpolationTargetPos: GeoPoint? = null
        var interpolationStartTime = 0L
        var interpolationDuration = 0L
        var interpolationTargetHeading = 0f
        var interpolationStartHeading = 0f
        
        // Launch interpolation job that runs at higher frequency
        val interpolationJob = launch {
            val interpolationFps = 30 // 30 FPS for smooth animation
            val interpolationIntervalMs = 1000L / interpolationFps
            
            while (isActive) {
                delay(interpolationIntervalMs)
                
                val marker = mapState.positionMarker
                val map = mapState.mapView
                
                if (marker == null || map == null || interpolationStartPos == null || interpolationTargetPos == null) {
                    continue
                }
                
                val elapsed = System.currentTimeMillis() - interpolationStartTime
                if (elapsed >= interpolationDuration) {
                    // Interpolation complete, snap to target
                    marker.position = interpolationTargetPos!!
                    marker.rotation = interpolationTargetHeading
                    
                    // Clear interpolation state
                    interpolationStartPos = null
                    interpolationTargetPos = null
                    continue
                }
                
                // Linear interpolation factor (0.0 to 1.0)
                val t = elapsed.toFloat() / interpolationDuration.toFloat()
                
                // Interpolate position
                val startLat = interpolationStartPos!!.latitude
                val startLon = interpolationStartPos!!.longitude
                val targetLat = interpolationTargetPos!!.latitude
                val targetLon = interpolationTargetPos!!.longitude
                
                val interpLat = startLat + (targetLat - startLat) * t
                val interpLon = startLon + (targetLon - startLon) * t
                val interpPos = GeoPoint(interpLat, interpLon)
                
                // Interpolate heading (handle 360° wrap-around)
                var headingDelta = interpolationTargetHeading - interpolationStartHeading
                if (headingDelta > 180f) headingDelta -= 360f
                if (headingDelta < -180f) headingDelta += 360f
                val interpHeading = (interpolationStartHeading + headingDelta * t + 360f) % 360f
                
                // Update marker
                marker.position = interpPos
                marker.rotation = interpHeading
                
                // Auto-center if enabled
                if (mapState.autoCenter) {
                    lastProgrammaticMove.value = System.currentTimeMillis()
                    map.controller.setCenter(interpPos)
                }
                
                map.invalidate()
            }
        }
        
        snapshotFlow { flightData }
            .collect { data ->
                val marker = mapState.positionMarker
                val map = mapState.mapView
                
                if (data == null || marker == null || map == null) return@collect
                
                // Throttle updates
                val now = System.currentTimeMillis()
                if (now - lastUpdateTime < minUpdateIntervalMs) {
                    return@collect
                }
                val deltaTime = now - lastUpdateTime
                lastUpdateTime = now

                val lat = data.latitude
                val lon = data.longitude

                // Validate timestamp to prevent old/cached data from resetting position
                val currentTimestamp = data.timestamp
                val lastTs = mapState.lastProcessedTimestamp
                if (currentTimestamp != null && lastTs != null) {
                    try {
                        val curInst = java.time.Instant.parse(currentTimestamp)
                        val lastInst = java.time.Instant.parse(lastTs)
                        if (!curInst.isAfter(lastInst)) {
                            return@collect
                        }
                    } catch (e: Exception) {
                        if (currentTimestamp <= lastTs) {
                            return@collect
                        }
                    }
                }

                if (lat != 0.0 && lon != 0.0) {
                    // Update last processed timestamp if available
                    if (currentTimestamp != null) mapState.lastProcessedTimestamp = currentTimestamp
                    val newPosition = GeoPoint(lat, lon)
                    mapState.lastValidPlayerPosition = newPosition
                    
                    // Setup interpolation instead of direct position update
                    val currentPos = marker.position
                    if (currentPos != null) {
                        interpolationStartPos = currentPos
                        interpolationTargetPos = newPosition
                        interpolationStartTime = now
                        // Use measured delta time for interpolation duration
                        // Cap at 500ms for responsiveness (was 1000ms)
                        interpolationDuration = deltaTime.coerceIn(50L, 500L)
                        
                        // Setup heading interpolation
                        interpolationStartHeading = marker.rotation
                        val rawHeading = data.heading.toFloat()
                        val headingDeg = Math.toDegrees(rawHeading.toDouble()).toFloat()
                        val rotationOffset = 45f
                        interpolationTargetHeading = if (mapState.mapRotationMode == 1) {
                            rotationOffset
                        } else {
                            (rotationOffset - headingDeg + 360f) % 360f
                        }
                    } else {
                        // First position update - no interpolation
                        marker.position = newPosition
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        
                        // Use raw datapad heading for the player marker
                        val rawHeading = data.heading.toFloat()
                        val headingDeg = Math.toDegrees(rawHeading.toDouble()).toFloat()
                        val rotationOffset = 45f
                        val computedRotation = if (mapState.mapRotationMode == 1) {
                            rotationOffset
                        } else {
                            (rotationOffset - headingDeg + 360f) % 360f
                        }
                        marker.rotation = computedRotation
                    }
                    
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    
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
                    
                    // Use raw datapad heading for overlay updates
                    val rawHeading = data.heading.toFloat()
                    val headingDeg = Math.toDegrees(rawHeading.toDouble()).toFloat()

                    // NOTE: Auto-center is now handled by interpolation job above
                    // (no need to set center here as it would interfere with smooth interpolation)

                    // Update overlays efficiently (batch updates)
                    try {
                        (mapState.compassOverlay as? CompassOverlay)?.let { co ->
                            co.center = GeoPoint(lat, lon)
                            co.heading = headingDeg
                        }
                        (mapState.headingSpeedLineOverlay as? HeadingSpeedLineOverlay)?.let { hsl ->
                            hsl.center = GeoPoint(lat, lon)
                            hsl.heading = headingDeg
                            hsl.speedKts = speedKts
                        }
                        (mapState.rangeRingsOverlay as? RangeRingsOverlay)?.let { rr ->
                            rr.center = GeoPoint(lat, lon)
                            rr.heading = headingDeg
                            rr.speedKts = speedKts
                        }
                    } catch (e: Exception) {
                        // ignore
                    }

                    // Single invalidate call after all updates
                    map.postInvalidate()

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
    val initialCenter: GeoPoint? = mapState.lastValidPlayerPosition
        ?: savedCenter?.let { GeoPoint(it.first, it.second) }
        ?: flightData?.let { if (it.latitude != 0.0 && it.longitude != 0.0) GeoPoint(it.latitude, it.longitude) else null }
    val initialZoom: Double = savedZoom ?: 8.0

    // Map theme / tile helpers (moved out so they are accessible from the layer dialog handler)
    val darkTile = org.osmdroid.tileprovider.tilesource.XYTileSource(
        "CartoDB.DarkMatter",
        0, 18, 256, ".png",
        arrayOf("https://basemaps.cartocdn.com/dark_all/")
    )
    
    // OpenTopoMap tile source (online, not from assets)
    val openTopoTile = org.osmdroid.tileprovider.tilesource.XYTileSource(
        "OpenTopoMap",
        0, 17, 256, ".png",
        arrayOf(
            "https://a.tile.opentopomap.org/",
            "https://b.tile.opentopomap.org/",
            "https://c.tile.opentopomap.org/"
        )
    )
    
    // Esri World Imagery - reliable satellite imagery with correct tile ordering
    // Esri uses z/y/x format (not z/x/y like standard TMS)
    val esriSatellite = object : org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase(
        "EsriWorldImagery",
        0, 19, 256, ".jpg",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex)
            val x = org.osmdroid.util.MapTileIndex.getX(pMapTileIndex)
            val y = org.osmdroid.util.MapTileIndex.getY(pMapTileIndex)
            // Esri format: baseUrl + z/y/x + extension
            return baseUrl + zoom + "/" + y + "/" + x + mImageFilenameEnding
        }
    }
    
    // Restore tile source from prefs if present, otherwise follow system theme
    val savedTileId = remember { prefsManager.getMapTileSourceId() }
    fun tileSourceForId(id: String): org.osmdroid.tileprovider.tilesource.ITileSource {
        return when (id) {
            "OpenTopo" -> openTopoTile
            "USGS_SAT" -> esriSatellite // Redirect old USGS_SAT to Esri (better reliability)
            "EsriSat" -> esriSatellite
            "MAPNIK" -> TileSourceFactory.MAPNIK
            "CartoDB.DarkMatter" -> darkTile
            else -> TileSourceFactory.MAPNIK
        }
    }
    val initialTileSource = savedTileId?.let { tileSourceForId(it) } ?: if (isDarkTheme) darkTile else TileSourceFactory.MAPNIK

    Box(modifier = modifier.fillMaxSize()) {

        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(initialTileSource)
                    setMultiTouchControls(true)

                    // Set padding so map content doesn't hide under the TabBar
                    setPadding(0, tabBarHeightPx, 0, 0)

                    // Improve zoom performance by preventing white flash on tile load
                    overlayManager.tilesOverlay.setLoadingBackgroundColor(android.graphics.Color.TRANSPARENT)

                    // Set initial view using saved or fallback values (no animation)
                    controller.setZoom(initialZoom)
                    // Only set initial center if we don't have a last valid player position (prevents reset)
                    if (mapState.lastValidPlayerPosition == null && initialCenter != null) {
                        controller.setCenter(initialCenter)
                    } else if (mapState.lastValidPlayerPosition == null) {
                        controller.setCenter(GeoPoint(48.0, 11.0))
                    }

                    // Create position marker
                    val marker = Marker(this).apply {
                        title = context.getString(R.string.map_aircraft_position)
                        snippet = context.getString(R.string.map_waiting_for_data)
                        // anchor center for correct rotation and pivot
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        // Use last valid position if available, prevents reset
                        mapState.lastValidPlayerPosition?.let { position = it }

                        // initial plane icon
                        try {
                            icon = if (isDarkTheme) planeDrawableDark else planeDrawableLight
                        } catch (e: Exception) {
                            // Use default marker
                        }
                    }

                    overlays.add(marker)
                    mapState.positionMarker = marker
                    
                    // Map click handler for symbol placement
                    val mapEventsReceiver = object : org.osmdroid.events.MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            Log.d(TAG, "singleTapConfirmedHelper called at $p, pendingMoveMarkerId=$pendingMoveMarkerId, pendingSymbolPlacement=${mapState.pendingSymbolPlacement != null}")
                            
                            // If navigation details are open and the user taps on the map (beside the card),
                            // collapse the details to the compact info bar. Skip collapsing when a move
                            // or symbol placement is in progress as these taps are intentional.
                            if (mapState.showNavigationDetails && pendingMoveMarkerId == null && mapState.pendingSymbolPlacement == null) {
                                mapState.showNavigationDetails = false
                                mapState.saveNavigationState()
                            }

                            p?.let { geoPoint ->
                                // 1) If a move is pending, use this tap to set new coords for that marker
                                if (pendingMoveMarkerId != null) {
                                    val moveId = pendingMoveMarkerId
                                    scope.launch {
                                        try {
                                            // Get current DB and create repository fresh to avoid stale captures
                                            val db = mapState.tacticalDb
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
                                val symbolPlacement = mapState.pendingSymbolPlacement
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
                                            val db = mapState.tacticalDb
                                            if (db == null) {
                                                Log.e(TAG, "TacticalDatabase is null, cannot save marker")
                                                // Notify user that DB is not ready and leave the placement pending so they can try again
                                                try {
                                                    android.widget.Toast.makeText(context, msgDbNotReady, android.widget.Toast.LENGTH_SHORT).show()
                                                } catch (_: Throwable) {
                                                    // ignore if Toast can't be shown (tests / preview)
                                                }
                                                return@launch
                                            }

                                            val repo = com.example.checklist_interactive.data.tactical.LocationRepositoryImpl(db.locationDao())
                                            val insertedId = repo.saveLocation(newLocation)
                                            // Only clear the pending placement after save succeeds to avoid losing the user's intent
                                            mapState.pendingSymbolPlacement = null

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
                    
                    mapState.mapView = this

                    // Touch listener: detect long-press on markers, handle rotation, and mark user touch to disable auto-center
                    try {
                        var downX = 0f
                        var downY = 0f
                        var moved = false
                        var isDown = false
                        var longPressJob: kotlinx.coroutines.Job? = null
                        val longPressTimeoutMs = 600L
                        val moveThresholdPx = with(density) { 10.dp.toPx() }

                        // Variables for rotation
                        var lastAngle = 0f
                        var inRotationGesture = false

                        setOnTouchListener { _, ev ->
                            try {
                                when (ev.action and MotionEvent.ACTION_MASK) {
                                    MotionEvent.ACTION_DOWN -> {
                                        lastUserTouch.value = System.currentTimeMillis()
                                        prefsManager.setMapAutoCenter(false)
                                        scope.launch { mapState.autoCenter = false }

                                        downX = ev.x
                                        downY = ev.y
                                        moved = false
                                        isDown = true
                                        inRotationGesture = false

                                        longPressJob?.cancel()
                                        longPressJob = scope.launch {
                                            delay(longPressTimeoutMs)
                                            // CRITICAL: Check if still single-finger press before processing long press
                                            if (isDown && !moved && ev.pointerCount == 1 && !inRotationGesture) {
                                                try {
                                                    // Find nearest marker within radius
                                                    val touchX = ev.x.toInt()
                                                    val touchY = ev.y.toInt()
                                                    val rawX = ev.rawX.toInt()
                                                    val rawY = ev.rawY.toInt()
                                                    var nearestMarker: org.osmdroid.views.overlay.Marker? = null
                                                    var bestDist2 = Int.MAX_VALUE
                                                    val radiusPx = (with(density) { 30.dp.toPx() }).toInt() // Reduced from 40dp to 30dp
                                                    
                                                    Log.d(TAG, "Long-press detection: touch=($touchX,$touchY) radiusPx=$radiusPx overlays=${overlays.size}")
                                                    Log.d(TAG, "Excluded markers: posMarker=${mapState.positionMarker} startMarker=${mapState.flightPathStartMarker}")

                                                    for (o in overlays) {
                                                        // Exclude position marker and flight path start marker from long-press detection
                                                        if (o is org.osmdroid.views.overlay.Marker) {
                                                            val isExcluded = (o == mapState.positionMarker || o == mapState.flightPathStartMarker)
                                                            val p = android.graphics.Point()
                                                            projection.toPixels(o.position, p)
                                                            val dx = p.x - touchX
                                                            val dy = p.y - touchY
                                                            val dist2 = dx * dx + dy * dy
                                                            val distPx = kotlin.math.sqrt(dist2.toDouble()).toInt()
                                                            Log.d(TAG, "  marker '${o.title}' at screen=(${p.x},${p.y}) distPx=$distPx excluded=$isExcluded")
                                                            
                                                            if (!isExcluded && dist2 < bestDist2 && dist2 <= radiusPx * radiusPx) {
                                                                bestDist2 = dist2
                                                                nearestMarker = o
                                                                Log.d(TAG, "    -> NEW NEAREST (distPx=$distPx)")
                                                            }
                                                        }
                                                    }
                                                    
                                                    Log.d(TAG, "Long-press result: nearestMarker=${nearestMarker?.title} bestDistPx=${kotlin.math.sqrt(bestDist2.toDouble()).toInt()}")

                                                    val nm = nearestMarker
                                                    val loc = if (nm != null) markerToLocation[nm] ?: try { nm.relatedObject as? com.example.checklist_interactive.data.tactical.LocationEntity } catch (_: Throwable) { null } else null

                                                    if (nm != null && loc != null) {
                                                        // Marker found and is a real LocationEntity - show marker radial menu
                                                        // Use raw screen coords for popup placement (more reliable across window insets)
                                                        val screenX = rawX
                                                        val screenY = rawY
                                                        
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
                                                        
                                                        // Use raw screen coordinates (ev.rawX/rawY) for popup placement — these are already in screen/window space
                                                        val windowX = screenX
                                                        val windowY = screenY
                                                        // Debug: also log map/window/screen info for verification
                                                        val mapLoc = IntArray(2)
                                                        this@apply.getLocationInWindow(mapLoc)
                                                        val screenLoc = IntArray(2)
                                                        this@apply.getLocationOnScreen(screenLoc)
                                                        Log.d(TAG, "Popup placement: rawScreen=($screenX,$screenY) mapWindow=(${mapLoc[0]},${mapLoc[1]}) mapOnScreen=(${screenLoc[0]},${screenLoc[1]}) -> usedWindow=($windowX,$windowY)")
                                                        
                                                        // Offset up slightly so menu doesn't overlap marker
                                                        val extraUpPx = with(density) { 40.dp.toPx().toInt() }
                                                        val adjY = windowY - extraUpPx
                                                        
                                                        Log.d(TAG, "Long-press on marker '${nm.title}': touch=($touchX,$touchY) mapLoc=(${mapLoc[0]},${mapLoc[1]}) window=($windowX,$windowY) adj=($windowX,$adjY) icon=${iconWidth}x${iconHeight}")
                                                        
                                                        if (loc != null) {
                                                            // Show radial menu at adjusted coordinates
                                                            scope.launch {
                                                                Log.d(TAG, "Setting radialMenu state: marker=${loc.name}, pos=($windowX,$adjY)")
                                                                mapState.radialMenuMarker = loc
                                                                mapState.radialMenuX = windowX
                                                                mapState.radialMenuY = adjY
                                                                mapState.radialMenuVisible = true
                                                                mapState.radialMenuType = com.example.checklist_interactive.ui.maps.components.RadialMenuType.MARKER
                                                                mapState.lastLongPressedMarkerId = loc.id
                                                                mapState.lastLongPressTime = System.currentTimeMillis()
                                                                Log.d(TAG, "RadialMenu state set: visible=${mapState.radialMenuVisible}, marker=${mapState.radialMenuMarker?.name}")
                                                            }
                                                        } else {
                                                            Log.d(TAG, "Long-press found nearest marker but could not resolve LocationEntity")
                                                        }
                                                    } else {
                                                        // No real marker found - show drawing radial menu
                                                        Log.d(TAG, "Long-press: no LocationEntity, showing drawing radial menu")
                                                        val windowX = rawX
                                                        val windowY = rawY
                                                        scope.launch {
                                                            mapState.radialMenuMarker = null
                                                            mapState.radialMenuX = windowX
                                                            mapState.radialMenuY = windowY
                                                            mapState.radialMenuVisible = true
                                                            mapState.radialMenuType = com.example.checklist_interactive.ui.maps.components.RadialMenuType.DRAWING
                                                        }
                                                    }

                                                } catch (_: Exception) {
                                                }
                                            }
                                        }
                                    }
                                    MotionEvent.ACTION_POINTER_DOWN -> {
                                        if (ev.pointerCount == 2 && mapState.rotationGestureEnabled) {
                                            longPressJob?.cancel() // Cancel long press if a second finger goes down
                                            inRotationGesture = true
                                            val dx = ev.getX(0) - ev.getX(1)
                                            val dy = ev.getY(0) - ev.getY(1)
                                            lastAngle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                        }
                                    }
                                    MotionEvent.ACTION_MOVE -> {
                                        lastUserTouch.value = System.currentTimeMillis()
                                        if (inRotationGesture && ev.pointerCount >= 2 && mapState.rotationGestureEnabled) {
                                            val dx = ev.getX(0) - ev.getX(1)
                                            val dy = ev.getY(0) - ev.getY(1)
                                            val currentAngle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                            val deltaAngle = currentAngle - lastAngle

                                            val newOrientation = this@apply.mapOrientation + deltaAngle
                                            this@apply.mapOrientation = newOrientation
                                            // Update pattern labels to stay upright during manual rotation
                                            mapState.trafficPatternLabelOverlay?.updateMapRotation(newOrientation)
                                            this@apply.invalidate()

                                            lastAngle = currentAngle
                                        } else if (!inRotationGesture && ev.pointerCount == 1) {
                                            if (kotlin.math.hypot((ev.x - downX).toDouble(), (ev.y - downY).toDouble()) > moveThresholdPx) {
                                                moved = true
                                                longPressJob?.cancel()
                                            }
                                        }
                                    }

                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        isDown = false
                                        longPressJob?.cancel()
                                        inRotationGesture = false
                                    }
                                    MotionEvent.ACTION_POINTER_UP -> {
                                        inRotationGesture = false
                                    }
                                }
                            } catch (_: Exception) {
                            }

                            // Let MapView handle the touch normally for panning and zooming
                            false
                        }
                    } catch (_: Exception) {
                    }

                    // Listen for user map interactions to persist center/zoom and disable auto-center when user moves map
                        val mapListener = object : org.osmdroid.events.MapListener {
                            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                                val center = this@apply.mapCenter
                                val now = System.currentTimeMillis()

                                // If a real user touch happened recently, treat this as a user scroll
                                if (now - lastUserTouch.value < 700) {
                                    prefsManager.setMapCenter(center.latitude, center.longitude)
                                    prefsManager.setMapAutoCenter(false)
                                    // Use coroutine scope to properly update Compose state
                                    scope.launch {
                                        mapState.autoCenter = false
                                    }
                                    return true
                                }

                                // Otherwise, ignore non-user-initiated scrolls (from animateTo)
                                prefsManager.setMapCenter(center.latitude, center.longitude)
                                return true
                            }

                            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                                prefsManager.setMapZoom(this@apply.zoomLevelDouble)
                                return true
                            }
                        }
                        addMapListener(mapListener)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = with(density) { 0.dp }),
            update = { mapView ->
                    // MapView interactions immer aktiv lassen, damit RadialMenu auch im Drawing Mode funktioniert
                    mapView.setMultiTouchControls(true)
                    mapView.isClickable = true
            }
        )
        
        // Drawing overlay - ALWAYS renders strokes, input only when drawing mode active
        MapDrawingOverlay(
            mapView = mapState.mapView,
            drawingState = drawingState,
            strokes = mapDrawings,
            currentStroke = currentDrawingStroke,
            onStrokeComplete = { stroke ->
                mapDrawings.add(stroke)
                currentDrawingStroke = null
                // Auto-save after each stroke
                saveDrawings()
            },
            onStrokesErased = { erasedStrokes ->
                // Delete from database first
                deleteDrawings(erasedStrokes)
                // Then remove from UI list
                mapDrawings.removeAll(erasedStrokes.toSet())
            },
            onLongPress = { offset ->
                // Open drawing radial menu
                val windowOffset = IntArray(2)
                mapState.mapView?.getLocationInWindow(windowOffset)
                // Also log screen coords for debugging
                val screenOffset = IntArray(2)
                mapState.mapView?.getLocationOnScreen(screenOffset)
                mapState.radialMenuMarker = null
                mapState.radialMenuX = windowOffset[0] + offset.x.toInt()
                mapState.radialMenuY = windowOffset[1] + offset.y.toInt()
                Log.d(TAG, "Coord conversion (compose overlay): mapInWindow=(${windowOffset[0]},${windowOffset[1]}) mapOnScreen=(${screenOffset[0]},${screenOffset[1]}) offset=(${offset.x},${offset.y}) -> window=(${mapState.radialMenuX},${mapState.radialMenuY})")
                mapState.radialMenuVisible = true
                mapState.radialMenuType = com.example.checklist_interactive.ui.maps.components.RadialMenuType.DRAWING
            },
            modifier = Modifier.fillMaxSize()
        )

        // Update tile source when theme changes (applies immediately on theme toggle)
        // Only apply theme-based tile source when user hasn't explicitly chosen one
        LaunchedEffect(isDarkTheme, savedTileId) {
            if (savedTileId == null) {
                mapState.mapView?.setTileSource(if (isDarkTheme) darkTile else TileSourceFactory.MAPNIK)
            }
        }
        
        // Load and display markers from database
        LaunchedEffect(mapState.mapView, locationRepository) {
            if (mapState.mapView != null && locationRepository != null) {
                val locFlow = locationRepository.getAllLocations()
                locFlow.collect { markers ->
                    mapState.mapView?.let { mv ->
                        // Remove existing marker overlays (except position marker)
                        mv.overlays.removeAll { it is org.osmdroid.views.overlay.Marker && it != mapState.positionMarker }
                        // Clear mapping to avoid holding stale references
                        markerToLocation.clear()

                        // Filter markers by visible maps from preferences
                        val visibleMaps = prefsManager.getVisibleMaps()
                        val filteredMarkers = markers.filter { marker ->
                            val markerMap = marker.map?.takeIf { it.isNotEmpty() } ?: "Unknown"
                            visibleMaps.any { it.equals(markerMap, ignoreCase = true) }
                        }

                        // Add markers to map
                        // Prepare icons and marker payloads off main thread to avoid UI jank
                        data class PreparedMarker(val entity: com.example.checklist_interactive.data.tactical.LocationEntity, val icon: BitmapDrawable?)

                        val prepared = withContext(kotlinx.coroutines.Dispatchers.Default) {
                            filteredMarkers.map { marker ->
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
                                    if (mapState.lastLongPressedMarkerId == marker.id && System.currentTimeMillis() - mapState.lastLongPressTime < 1000L) {
                                        mapState.lastLongPressedMarkerId = null
                                        return true
                                    }

                                    mapState.selectedLocation = marker
                                    mapState.showMarkerRouteManagement = true

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

        // Load and display TACTICAL UNITS from database (live tracking)
        val isEntityTrackingEnabled by dataPadManager.isEntityTrackingEnabled.collectAsState()
        val showTacticalUnitsOnMap by dataPadManager.showTacticalUnitsOnMap.collectAsState()

        // Create tactical units overlay (persists across recomposition)
        val tacticalUnitsOverlay = remember(tacticalUnitsRepository) {
            tacticalUnitsRepository?.let { repo ->
                TacticalUnitsMapOverlay(
                    context = context,
                    repository = repo,
                    showLiveOnlyFlow = dataPadManager.tacticalUnitsShowLiveOnly,
                    showHiddenUnitsFlow = dataPadManager.tacticalUnitsShowHidden,
                    isEntityTrackingEnabledFlow = dataPadManager.isEntityTrackingEnabled,
                    updateIntervalSecondsFlow = dataPadManager.tacticalUnitsMapUpdateInterval,
                    getMapView = { mapState.mapView },
                    onUnitClick = { tacticalUnit ->
                        // Convert TacticalUnitEntity to temporary LocationEntity for navigation
                        val tempLocation = com.example.checklist_interactive.data.tactical.LocationEntity(
                            id = 0, // Temporary, no DB ID
                            name = "${tacticalUnit.name} (${tacticalUnit.category})",
                            latitude = tacticalUnit.latitude,
                            longitude = tacticalUnit.longitude,
                            elevationM = tacticalUnit.altitude,
                            markerType = "tactical_unit",
                            description = buildString {
                                append("Coalition: ")
                                append(when (tacticalUnit.coalition) {
                                    0 -> "Neutral"
                                    1 -> "Red"
                                    2 -> "Blue"
                                    else -> "Unknown"
                                })
                                tacticalUnit.groupName?.let { append("\nGroup: $it") }
                                tacticalUnit.pilotName?.let { append("\nPilot: $it") }
                            },
                            isStatic = 1,
                            source = "tactical_tracking",
                            updatedAt = tacticalUnit.lastSeenAt, // Add last seen timestamp
                            metadata = org.json.JSONObject().apply {
                                // Store tactical unit DB ID for live tracking
                                put("tactical_unit_id", tacticalUnit.id)
                                // Store heading for extraction by MapMarkerPopup
                                tacticalUnit.heading?.let { put("heading", it) }
                                // Store highlight status
                                put("is_highlighted", tacticalUnit.isHighlighted)
                            }.toString()
                        )

                        // Set selected location and show route management
                        mapState.selectedLocation = tempLocation
                        mapState.showMarkerRouteManagement = true

                        // Center on marker
                        mapState.mapView?.let { mv ->
                            (context as? android.app.Activity)?.runOnUiThread {
                                try {
                                    mv.controller?.animateTo(org.osmdroid.util.GeoPoint(tacticalUnit.latitude, tacticalUnit.longitude))
                                    mv.invalidate()
                                } catch (_: Exception) { }
                            }
                        }

                        Log.d(TAG, "📡 Tactical unit clicked: ${tacticalUnit.name}")
                    }
                )
            }
        }

        // Manage tactical units overlay lifecycle
        LaunchedEffect(mapState.mapView, tacticalUnitsRepository, isEntityTrackingEnabled, showTacticalUnitsOnMap, tacticalUnitsOverlay) {
            val mv = mapState.mapView
            val repo = tacticalUnitsRepository
            val overlay = tacticalUnitsOverlay

            Log.d(TAG, "📡 Settings changed: mapView=${mv != null}, repo=${repo != null}, overlay=${overlay != null}, entityTracking=$isEntityTrackingEnabled, showOnMap=$showTacticalUnitsOnMap")

            // Remove overlay if visibility is disabled
            if (!showTacticalUnitsOnMap || overlay == null) {
                if (mv != null && overlay != null) {
                    Log.d(TAG, "📡 Removing tactical units overlay from map")
                    mv.overlays.remove(overlay)
                    overlay.stopTracking()
                    mv.invalidate()
                }
                Log.d(TAG, "📡 Tactical units map visibility disabled - overlay hidden")
                return@LaunchedEffect // Exit early
            }

            // Only continue if all conditions are met
            if (mv == null) {
                Log.d(TAG, "📡 MapView not ready - waiting...")
                return@LaunchedEffect
            }

            try {
                Log.d(TAG, "📡 Adding tactical units overlay to map and starting tracking")

                // Add overlay to map if not already present
                // Add normally so tactical unit labels appear ABOVE airspace circles
                if (!mv.overlays.contains(overlay)) {
                    mv.overlays.add(overlay)
                    Log.d(TAG, "📡 Tactical units overlay added to map (above airspace)")
                }

                // Start tracking with this coroutine scope
                overlay.startTracking(this)

                // Cleanup on effect cancellation
                awaitCancellation()
            } catch (e: Exception) {
                Log.e(TAG, "📡 Error in tactical units overlay: ${e.message}", e)
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
                            mapState.mapView?.controller?.animateTo(GeoPoint(data.latitude, data.longitude))
                            mapState.mapView?.invalidate()
                        }
                        pendingCenter.value = null
                        mapState.autoCenter = true
                        prefsManager.setMapAutoCenter(true)
                        prefsManager.setMapCenter(data.latitude, data.longitude)
                        Log.d(TAG, "Center FAB pressed — centering to live position ${data.latitude},${data.longitude}, autoCenter=${mapState.autoCenter}")
                    } else {
                        // No live position yet — try to fall back to last saved center, or remember to center later
                        val saved = prefsManager.getMapCenter()
                        if (saved != null) {
                            val gp = GeoPoint(saved.first, saved.second)
                            (context as? android.app.Activity)?.runOnUiThread {
                                lastProgrammaticMove.value = System.currentTimeMillis()
                                mapState.mapView?.controller?.animateTo(gp)
                                mapState.mapView?.invalidate()
                            }
                            mapState.autoCenter = true
                            prefsManager.setMapAutoCenter(true)
                            Log.d(TAG, "Center FAB pressed — centering to saved position ${saved.first},${saved.second}, autoCenter=${mapState.autoCenter}")
                        } else {
                            // no center available — remember to center once we get a valid position
                            pendingCenter.value = null // will be set when new data arrives in the flight-data effect
                            mapState.autoCenter = true
                            prefsManager.setMapAutoCenter(true)
                            Log.d(TAG, "Center FAB pressed — no position available yet, will enable auto-centering for future updates, autoCenter=${mapState.autoCenter}")
                        }
                    }
                },
                onLayerSelection = { mapState.showLayerDialog = true },
                onOverlaySelection = { mapState.showOverlayDialog = true },
                onAddMilitarySymbol = { if (repositoriesReady) mapState.showMilitarySymbolPicker = true },
                onMarkerRouteManagement = { if (repositoriesReady) mapState.showMarkerRouteManagement = true },
                onLockScreen = onLockScreen,
                onToggleMapRotation = {
                    mapState.mapRotationMode = (mapState.mapRotationMode + 1) % 2
                    prefsManager.setMapRotationMode(mapState.mapRotationMode)
                    if (mapState.mapRotationMode == 0) {
                        try { mapState.mapView?.setMapOrientation(0f) } catch (_: Throwable) {}
                    } else {
                        flightData?.let { d -> try { mapState.mapView?.setMapOrientation(-Math.toDegrees(d.heading).toFloat()) } catch (_: Throwable) {} }
                    }
                },
                onToggleRotationGesture = {
                    mapState.rotationGestureEnabled = !mapState.rotationGestureEnabled
                    prefsManager.setMapRotationGestureEnabled(mapState.rotationGestureEnabled)
                    android.util.Log.d(TAG, "Rotation gesture toggled: enabled=${mapState.rotationGestureEnabled}")
                },
                onDrawingTools = { 
                    if (drawingState.isDrawingMode) {
                        // Deactivate drawing mode and close popup
                        drawingState = drawingState.copy(isDrawingMode = false, isEraseMode = false)
                        showDrawingToolPopup = false
                    } else {
                        // Activate drawing mode and show popup
                        drawingState = drawingState.copy(isDrawingMode = true)
                        showDrawingToolPopup = true
                    }
                },
                onResetFabPositions = {
                    try {
                        prefsManager.resetFabPositions("map")
                    } catch (e: Exception) { android.util.Log.w(TAG, "Failed to reset FAB prefs: ${e.message}") }
                    // force re-read of saved positions for the DraggableFabs
                    try { fabLayoutResetTrigger = fabLayoutResetTrigger + 1 } catch (_: Throwable) {}
                    // quick feedback
                    android.widget.Toast.makeText(context, msgFabPositionsReset, android.widget.Toast.LENGTH_SHORT).show()
                },

                onDataPadOpen = { if (datapadEnabled) mapState.showDataPad = true },
                onQuickAccessOpen = { mapState.showQuickAccess = true },
                onTacticalUnitsOpen = {
                    // Open MarkerRouteManagementSheet with Tactical Units tab (tab 3) selected
                    mapState.markerRouteManagementInitialTab = 3
                    mapState.showMarkerRouteManagement = true
                },
                isConnected = isConnected,
                isScreenLocked = isScreenLocked,
                mapRotationMode = mapState.mapRotationMode,
                rotationGestureEnabled = mapState.rotationGestureEnabled,
                isDrawingMode = drawingState.isDrawingMode,
                repositoriesReady = repositoriesReady,
                pendingSymbolPlacement = mapState.pendingSymbolPlacement,
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
        MapNavigationDisplay(
            flightData = flightData,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            activeNavigationTarget = mapState.activeNavigationTarget,
            navigationDistanceNm = mapState.navigationDistanceNm,
            navigationHeading = mapState.navigationHeading,
            showNavigationDetails = mapState.showNavigationDetails,
            onShowNavigationDetailsChange = { mapState.showNavigationDetails = it },
            selectedRunwayHeading = mapState.selectedRunwayHeading,
            originalAirportTarget = mapState.originalAirportTarget,
            targetRunways = mapState.targetRunways,
            showRunwayApproach = mapState.showRunwayApproach,
            onShowRunwayApproachChange = { mapState.showRunwayApproach = it },
            finalApproachDistanceNm = mapState.finalApproachDistanceNm,
            onFinalApproachDistanceNmChange = { mapState.finalApproachDistanceNm = it },
            showTrafficPattern = mapState.showTrafficPattern,
            onShowTrafficPatternChange = { mapState.showTrafficPattern = it },
            patternSize = mapState.patternSize,
            onPatternSizeChange = { mapState.patternSize = it },
            patternDirection = mapState.patternDirection,
            onPatternDirectionChange = { mapState.patternDirection = it },
            showPatternDetails = mapState.showPatternDetails,
            onShowPatternDetailsChange = { mapState.showPatternDetails = it },
            patternFinalDistanceNm = mapState.patternFinalDistanceNm,
            onPatternFinalDistanceNmChange = { mapState.patternFinalDistanceNm = it },
            roundedPatternCorners = mapState.roundedPatternCorners,
            onRoundedPatternCornersChange = { mapState.roundedPatternCorners = it },
            selectedRunwayIndex = mapState.selectedRunwayIndex,
            onSelectedRunwayIndexChange = { mapState.selectedRunwayIndex = it },
            selectedRunway = mapState.selectedRunway,
            onSelectedRunwayChange = { mapState.selectedRunway = it },
            onActiveNavigationTargetChange = {
                mapState.activeNavigationTarget = it
                // Clear tactical unit tracking when navigation is cleared
                if (it == null) {
                    mapState.activeNavigationTacticalUnitId = null
                }
            },
            onOriginalAirportTargetChange = { mapState.originalAirportTarget = it },
            onSelectedRunwayHeadingChange = { mapState.selectedRunwayHeading = it },
            customPatternAltitudeAglFt = mapState.customPatternAltitudeAglFt,
            onCustomPatternAltitudeAglFtChange = { mapState.customPatternAltitudeAglFt = it },
            patternAltitudeSmallToleranceFt = mapState.patternAltitudeSmallToleranceFt,
            onPatternAltitudeSmallToleranceFtChange = { mapState.patternAltitudeSmallToleranceFt = it },
            patternAltitudeWarningToleranceFt = mapState.patternAltitudeWarningToleranceFt,
            onPatternAltitudeWarningToleranceFtChange = { mapState.patternAltitudeWarningToleranceFt = it },
            saveNavigationState = { mapState.saveNavigationState() },
            // Manual landing pattern state
            enableManualLandingPattern = mapState.enableManualLandingPattern,
            onEnableManualLandingPatternChange = { mapState.enableManualLandingPattern = it },
            manualLandingHeading = mapState.manualLandingHeading,
            onManualLandingHeadingChange = { mapState.manualLandingHeading = it },
            showManualHeadingError = mapState.showManualHeadingError,
            onShowManualHeadingErrorChange = { mapState.showManualHeadingError = it },
            // Airspace display state
            showAirspaceCircles = mapState.showAirspaceCircles,
            onShowAirspaceCirclesChange = {
                Log.d(TAG, "🌐 State change requested: showAirspaceCircles = ${mapState.showAirspaceCircles} -> $it")
                mapState.showAirspaceCircles = it
            },
            enabledAirspaceClasses = mapState.enabledAirspaceClasses,
            onEnabledAirspaceClassesChange = { mapState.enabledAirspaceClasses = it },
            airspaceFillTransparency = mapState.airspaceFillTransparency,
            onAirspaceFillTransparencyChange = { mapState.airspaceFillTransparency = it }
        )

        // DB loading/error indicators only (DataPad status moved to FlightMiniStatusBar)
        if (mapState.dbInitFailed) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .clickable { 
                        // Retry DB init on click
                        mapState.dbInitFailed = false
                        mapState.dbInitError = null
                        scope.launch {
                            withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    android.util.Log.d(TAG, "Retrying TacticalDatabase initialization...")
                                    val db = kotlinx.coroutines.withTimeout(10000L) {
                                        com.example.checklist_interactive.data.tactical.TacticalDatabase.getInstance(context, useExternalPath = false)
                                    }
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        mapState.tacticalDb = db
                                        android.util.Log.d(TAG, "DB initialized successfully on retry")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e(TAG, "Retry failed", e)
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        mapState.dbInitFailed = true
                                        mapState.dbInitError = e.message ?: "Unbekannter Fehler"
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
                        text = mapState.dbInitError ?: "Unbekannt",
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
        }
        
        // Auto-center indicator (clickable: toggle + persist)
        if (isConnected) {
            if (mapState.autoCenter) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .clickable {
                            mapState.autoCenter = false
                            prefsManager.setMapAutoCenter(false)
                        },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = stringResource(R.string.map_auto_center_enabled),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            } else {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .clickable {
                            mapState.autoCenter = true
                            prefsManager.setMapAutoCenter(true)
                        },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = stringResource(R.string.map_auto_center_disabled),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Move mode instruction banner
        if (pendingMoveMarkerId != null) {
            // Optionally resolve name for nicer text
            LaunchedEffect(pendingMoveMarkerId) {
                mapState.resolvePendingMoveTargetName(pendingMoveMarkerId!!, locationRepository)
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
                    Text(text = "Move marker: ${mapState.pendingMoveTargetName ?: pendingMoveMarkerId}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.width(12.dp))
                    TextButton(onClick = { MapActionBus.clear() }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }

        // Drawing mode indicator (top-right, shows when drawing is active)
        if (drawingState.isDrawingMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (drawingState.isEraseMode) Icons.Default.Delete else Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (drawingState.isEraseMode) "Eraser Mode" else "Drawing Mode",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // Small tip when map lock is disabled (top-right, smaller to avoid overlapping datapad HUD)
        if (!isScreenLocked && !drawingState.isDrawingMode) {
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
        
        // Drawing Tool Popup
        if (showDrawingToolPopup) {
            MapDrawingToolPopup(
                state = drawingState,
                onStateChange = { newState ->
                    drawingState = newState
                },
                onDismiss = {
                    // Just close the popup, keep drawing mode active
                    showDrawingToolPopup = false
                },
                onClearAll = {
                    mapDrawings.clear()
                    scope.launch {
                        mapState.tacticalDb?.mapDrawingDao()?.deleteAllDrawings()
                    }
                },
                onSave = {
                    saveDrawings()
                    android.widget.Toast.makeText(context, msgDrawingSaved, android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }
        
        // Flight Instruments Overlay
        val fd = flightData
        val instrumentsDataAvailable = fd != null

        // Always show instruments when the overlay is enabled, even if we don't have flight data yet.
        if (mapState.flightInstrumentsEnabled) {
            // Derived fuel values: some streams omit `total` but provide `internal`/`external`; prefer remaining when available
            val fuelRemainingValue = fd?.fuel?.remaining ?: fd?.fuel?.internal ?: 0.0
            val fuelTotalValue = fd?.fuel?.total ?: run {
                val internal = fd?.fuel?.internal
                if (internal != null) internal + (fd?.fuel?.external ?: 0.0) else null
            }

            // Compute sensible gLoad value: prefer vertical axis (y), then z, else use vector magnitude
            val gLoadValue: Double? = fd?.gLoad?.let { g ->
                val y = g.y
                val z = g.z
                val x = g.x
                when {
                    kotlin.math.abs(y) >= 0.05 -> y
                    kotlin.math.abs(z) >= 0.05 -> z
                    else -> {
                        val mag = Math.sqrt(x * x + y * y + z * z)
                        if (mag > 0.05) mag else null
                    }
                }
            }

            MapFlightInstruments(
                pitch = if (fd != null) Math.toDegrees(fd.pitch) else 0.0, // Convert radians to degrees if available, otherwise placeholder
                bank = if (fd != null) Math.toDegrees(fd.bank) else 0.0,
                verticalSpeed = fd?.verticalSpeed,
                airspeed = fd?.indicatedAirspeed ?: fd?.trueAirspeed ?: fd?.groundSpeed,
                altitude = fd?.altitude,
                terrainElevation = fd?.terrainElevation,
                heading = if (fd != null) Math.toDegrees(fd.heading) else null,
                angleOfAttack = fd?.angleOfAttack,
                gLoad = gLoadValue,
                fuelRemaining = fuelRemainingValue,
                fuelTotal = fuelTotalValue,
                mach = fd?.mach,
                engineRpmLeft = fd?.engines?.rpm?.left,
                engineRpmRight = fd?.engines?.rpm?.right,
                windSpeed = fd?.environment?.windSpeed,
                windDirection = fd?.environment?.windDirection,
                flareCount = fd?.countermeasures?.flareCount,
                chaffCount = fd?.countermeasures?.chaffCount,
                enabled = mapState.flightInstrumentsEnabled,
                dataAvailable = instrumentsDataAvailable
            )
        }

        // Small debug overlay (visible in debug builds) to show state
        if (android.util.Log.isLoggable("MapViewer", android.util.Log.DEBUG)) {
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(8.dp), contentAlignment = Alignment.TopStart) {
                androidx.compose.material3.Surface(
                    tonalElevation = 4.dp,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "FI: enabled=${mapState.flightInstrumentsEnabled} fd=${fd != null}",
                        modifier = Modifier.padding(6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
    
        // Pending symbol placement indicator
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            if (mapState.pendingSymbolPlacement != null) {
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
                                text = "Tap on the map to place ${mapState.pendingSymbolPlacement?.first?.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(onClick = { mapState.pendingSymbolPlacement = null }) {
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
    LaunchedEffect(mapState.mapView) {
        if (mapState.mapView != null) {
            // If user requested center earlier but MapView wasn't ready, honor it now
            pendingCenter.value?.let { gp ->
                lastProgrammaticMove.value = System.currentTimeMillis()
                (context as? android.app.Activity)?.runOnUiThread {
                    mapState.mapView?.controller?.animateTo(gp)
                    mapState.mapView?.invalidate()
                }
                Log.d(TAG, "Applied pending center: ${gp.latitude},${gp.longitude}")
                pendingCenter.value = null
            }

            // Apply persisted overlay preferences immediately (if not already present)
            mapState.mapView?.let { mv ->
                if (mapState.compassEnabled && mapState.compassOverlay == null) {
                    // Fixed-size compass ring
                    val co = CompassOverlay()
                    flightData?.let { d -> if (d.latitude != 0.0 && d.longitude != 0.0) co.center = GeoPoint(d.latitude, d.longitude) }
                    flightData?.let { d -> co.heading = Math.toDegrees(d.heading).toFloat() }
                    mv.overlays.add(co)
                    mapState.compassOverlay = co
                    
                    // Speed-based heading line
                    if (mapState.headingSpeedLineOverlay == null) {
                        val hsl = HeadingSpeedLineOverlay()
                        flightData?.let { d -> if (d.latitude != 0.0 && d.longitude != 0.0) hsl.center = GeoPoint(d.latitude, d.longitude) }
                        flightData?.let { d -> hsl.heading = Math.toDegrees(d.heading).toFloat() }
                        flightData?.let { d -> hsl.speedKts = ((d.groundSpeed ?: d.trueAirspeed ?: d.indicatedAirspeed ?: 0.0) * 1.9438) }
                        mv.overlays.add(hsl)
                        mapState.headingSpeedLineOverlay = hsl
                    }
                }
                if (mapState.rangeRingsEnabled && mapState.rangeRingsOverlay == null) {
                    val rr = RangeRingsOverlay()
                    rr.maxNm = mapState.rangeRingsMaxNm
                    rr.heading = flightData?.heading?.let { Math.toDegrees(it).toFloat() } ?: 0f
                    flightData?.let { d -> if (d.latitude != 0.0 && d.longitude != 0.0) rr.center = GeoPoint(d.latitude, d.longitude) }
                    mv.overlays.add(rr)
                    mapState.rangeRingsOverlay = rr
                }
                if (mapState.mgrsGridEnabled && mapState.mgrsGridOverlay == null) {
                    val mg = MgrsGridOverlay()
                    mv.overlays.add(mg)
                    mapState.mgrsGridOverlay = mg
                }
                
                // Airport labels overlay
                if (mapState.showMarkerLabels && mapState.airportLabelsOverlay == null && locationRepository != null && mapState.tacticalDb != null) {
                    val labelsOverlay = AirportMarkerLabelsOverlay(
                        context = context,
                        locationRepository = locationRepository,
                        database = mapState.tacticalDb!!,
                        getVisibleMaps = { prefsManager.getVisibleMaps().toList() },
                        isEnabled = { mapState.showMarkerLabels },
                        getMapView = { mapState.mapView }
                    )
                    mv.overlays.add(labelsOverlay)
                    mapState.airportLabelsOverlay = labelsOverlay
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
    if (mapState.showLayerDialog) {
        LayerSelectionDialog(
            onDismiss = { mapState.showLayerDialog = false },
            onLayerSelected = { id ->
                if (id != null) {
                    val ts = tileSourceForId(id)
                    mapState.mapView?.setTileSource(ts)
                    prefsManager.setMapTileSourceId(id)
                } else {
                    // follow system theme
                    prefsManager.setMapTileSourceId(null)
                    mapState.mapView?.setTileSource(if (isDarkTheme) darkTile else TileSourceFactory.MAPNIK)
                }
                mapState.showLayerDialog = false
            }
        )
    }

    // Overlay selection dialog
    if (mapState.showOverlayDialog) {
        OverlaySelectionDialog(
            compassEnabled = mapState.compassEnabled,
            rangeRingsEnabled = mapState.rangeRingsEnabled,
            rangeRingsMaxNm = mapState.rangeRingsMaxNm,
            mgrsGridEnabled = mapState.mgrsGridEnabled,
            flightInstrumentsEnabled = mapState.flightInstrumentsEnabled,
            markerLabelsEnabled = mapState.showMarkerLabels,
            flightPathEnabled = mapState.flightPathEnabled,
            flightPathRecording = mapState.flightPathRecording,
            flightPathPointCount = mapState.flightPathPointCount,
            flightPathIntervalSeconds = mapState.flightPathIntervalSeconds,
            onDismiss = { mapState.showOverlayDialog = false },
            onToggleCompass = { enabled ->
                mapState.compassEnabled = enabled
                prefsManager.setMapOverlayCompassEnabled(enabled)
                // apply immediately
                mapState.mapView?.let { mv ->
                    // remove existing compass and heading line
                    mapState.compassOverlay?.let { mv.overlays.remove(it) }
                    mapState.compassOverlay = null
                    mapState.headingSpeedLineOverlay?.let { mv.overlays.remove(it) }
                    mapState.headingSpeedLineOverlay = null
                    if (enabled) {
                        // Fixed-size compass ring
                        val co = CompassOverlay()
                        flightData?.let { d -> if (d.latitude != 0.0 && d.longitude != 0.0) co.center = GeoPoint(d.latitude, d.longitude) }
                        flightData?.let { d -> co.heading = Math.toDegrees(d.heading).toFloat() }
                        mv.overlays.add(co)
                        mapState.compassOverlay = co
                        
                        // Speed-based heading line
                        val hsl = HeadingSpeedLineOverlay()
                        flightData?.let { d -> if (d.latitude != 0.0 && d.longitude != 0.0) hsl.center = GeoPoint(d.latitude, d.longitude) }
                        flightData?.let { d -> hsl.heading = Math.toDegrees(d.heading).toFloat() }
                        flightData?.let { d -> hsl.speedKts = ((d.groundSpeed ?: d.trueAirspeed ?: d.indicatedAirspeed ?: 0.0) * 1.9438) }
                        mv.overlays.add(hsl)
                        mapState.headingSpeedLineOverlay = hsl
                    }
                    mv.invalidate()
                }
            },
            onToggleRangeRings = { enabled ->
                mapState.rangeRingsEnabled = enabled
                prefsManager.setMapOverlayRangeRingsEnabled(enabled)
                mapState.mapView?.let { mv ->
                    mapState.rangeRingsOverlay?.let { mv.overlays.remove(it) }
                    mapState.rangeRingsOverlay = null
                    if (enabled) {
                        val rr = RangeRingsOverlay()
                        rr.maxNm = mapState.rangeRingsMaxNm
                        rr.heading = flightData?.heading?.let { Math.toDegrees(it).toFloat() } ?: 0f
                        flightData?.let { d -> if (d.latitude != 0.0 && d.longitude != 0.0) rr.center = GeoPoint(d.latitude, d.longitude) }
                        mv.overlays.add(rr)
                        mapState.rangeRingsOverlay = rr
                    }
                    mv.invalidate()
                }
            },
            onChangeRangeRingsMaxNm = { nm ->
                mapState.rangeRingsMaxNm = nm
                prefsManager.setMapOverlayRangeRingsMaxNm(nm)
                (mapState.rangeRingsOverlay as? RangeRingsOverlay)?.let { rr ->
                    rr.maxNm = nm
                    mapState.mapView?.invalidate()
                }
            },
            onToggleMgrsGrid = { enabled ->
                mapState.mgrsGridEnabled = enabled
                prefsManager.setMapOverlayMgrsGridEnabled(enabled)
                mapState.mapView?.let { mv ->
                    mapState.mgrsGridOverlay?.let { mv.overlays.remove(it) }
                    mapState.mgrsGridOverlay = null
                    if (enabled) {
                        val mg = MgrsGridOverlay()
                        mv.overlays.add(mg)
                        mapState.mgrsGridOverlay = mg
                    }
                    mv.invalidate()
                }
            },
            onToggleFlightInstruments = { enabled ->
                mapState.flightInstrumentsEnabled = enabled
                prefsManager.setMapOverlayFlightInstrumentsEnabled(enabled)
            },
            onToggleMarkerLabels = { enabled ->
                mapState.showMarkerLabels = enabled
                prefsManager.setMapMarkerLabelsEnabled(enabled)
            },
            onToggleFlightPath = { enabled ->
                mapState.flightPathEnabled = enabled
                prefsManager.setFlightPathEnabled(enabled)
            },
            onStartTracking = {
                scope.launch {
                    flightPathRepository?.setRecordingEnabled(true)
                }
            },
            onPauseTracking = {
                scope.launch {
                    flightPathRepository?.setRecordingEnabled(false)
                }
            },
            onClearFlightPath = {
                // Immediately remove polyline and start marker from map
                mapState.flightPathPolyline?.let { polyline ->
                    mapState.mapView?.overlays?.remove(polyline)
                }
                mapState.flightPathPolyline = null
                mapState.flightPathStartMarker?.let { marker ->
                    mapState.mapView?.overlays?.remove(marker)
                }
                mapState.flightPathStartMarker = null
                mapState.flightPathPointCount = 0
                mapState.mapView?.invalidate()
                
                // Clear database in background
                scope.launch {
                    flightPathRepository?.clearPath()
                    android.util.Log.d("MapViewer", "Flight path cleared - UI and DB reset")
                }
            },
            onChangeFlightPathInterval = { seconds ->
                mapState.flightPathIntervalSeconds = seconds
                prefsManager.setFlightPathIntervalSeconds(seconds)
            }
        )
    }
    
    // Quick Access Bottom Sheet
    if (mapState.showQuickAccess && quickNoteManager != null) {
        QuickAccessSheet(
            onDismiss = { mapState.showQuickAccess = false },
            currentDocumentPath = "special://aviation_map",
            currentDocumentName = stringResource(R.string.map_aviation_map)
        )
    }
    // DataPad Popup
    if (mapState.showDataPad && datapadEnabled) {
        DataPadPopup(onDismiss = { mapState.showDataPad = false })
    }
    
    // Military Symbol Picker Dialog
    if (mapState.showMilitarySymbolPicker) {
        MilitarySymbolPickerDialog(
            onDismiss = {
                // Only close the dialog on dismiss; do not clear pending placement here
                mapState.showMilitarySymbolPicker = false
            },
            onSymbolSelected = { symbol, affiliation ->
                // Store selected symbol and close dialog; user will tap map to place it
                mapState.pendingSymbolPlacement = symbol to affiliation
                Log.d(TAG, "Pending military symbol selected: ${symbol.name} (${affiliation.name})")
                mapState.showMilitarySymbolPicker = false
            }
        )
    }
    
    // Radial menu
    MapRadialMenuDisplay(
        mapState = mapState,
        locationRepository = locationRepository,
        scope = scope,
        drawingState = drawingState,
        onDrawingStateChange = { newState ->
            drawingState = newState
        }
    )
    
    // Marker/Route Management Sheet (with integrated marker details)
    if (mapState.showMarkerRouteManagement) {
        markerRouteViewModel?.let { vm ->
            MarkerRouteManagementSheet(
                viewModel = vm,
                onDismiss = {
                    mapState.showMarkerRouteManagement = false
                    mapState.selectedLocation = null
                    mapState.markerRouteManagementInitialTab = null
                },
                onMarkerClick = { marker ->
                    // Update selected location to show details in tab
                    mapState.selectedLocation = marker
                },
                initialTab = mapState.markerRouteManagementInitialTab,
                onCenterOnMap = { lat, lon ->
                    // Center map on the provided coordinates
                    mapState.mapView?.let { mv ->
                        mv.controller.animateTo(GeoPoint(lat, lon))
                    }
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

                            mapState.mapView?.let { mv ->
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
                    mapState.showMarkerRouteManagement = false
                },
                onCreateRoute = {
                    mapState.showMarkerRouteManagement = false
                    routeCreationViewModel?.let { rvm ->
                        rvm.startRouteCreation()
                        mapState.showRouteCreation = true
                    }
                },
                onCenter = { location ->
                    // Center map on provided location
                    mapState.mapView?.let { mv ->
                        mv.controller.animateTo(GeoPoint(location.latitude, location.longitude))
                    }
                },
                selectedMarker = mapState.selectedLocation,
                selectedRunways = mapState.selectedRunways,
                onSetActiveRoute = { location ->
                    mapState.activeNavigationTarget = location

                    // If this is a tactical unit, extract and store its ID for live tracking
                    if (location.source == "tactical_tracking") {
                        try {
                            val metadata = location.metadata?.let { org.json.JSONObject(it) }
                            val tacticalUnitId = metadata?.optInt("tactical_unit_id", -1)
                            if (tacticalUnitId != null && tacticalUnitId > 0) {
                                mapState.activeNavigationTacticalUnitId = tacticalUnitId
                                Log.d(TAG, "🎯 Set navigation to tactical unit ID: $tacticalUnitId")
                            } else {
                                mapState.activeNavigationTacticalUnitId = null
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to extract tactical_unit_id from metadata", e)
                            mapState.activeNavigationTacticalUnitId = null
                        }
                    } else {
                        mapState.activeNavigationTacticalUnitId = null
                    }

                    // Auto-center disabled so user can see the full route
                    mapState.autoCenter = false
                    // Optionally close the sheet
                    mapState.showMarkerRouteManagement = false
                },
                onEditRouteWaypoints = { routeId ->
                    // Open RouteCreationSheet in edit mode
                    routeCreationViewModel?.let { rvm ->
                        rvm.startRouteEditing(routeId)
                        mapState.showMarkerRouteManagement = false
                        mapState.showRouteCreation = true
                    }
                }
            )
        }
    }
    
    // Route Creation Sheet
    if (mapState.showRouteCreation) {
        routeCreationViewModel?.let { vm ->
            RouteCreationSheet(
                viewModel = vm,
                onDismiss = {
                    mapState.showRouteCreation = false
                    vm.cancelRouteCreation()
                },
                onWaypointClick = { location ->
                    // Center map on waypoint
                    mapState.mapView?.controller?.animateTo(GeoPoint(location.latitude, location.longitude))
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
            mapState.navigationLine?.let { line ->
                mapState.mapView?.overlays?.remove(line)
            }
            mapState.navigationLine = null

            // Clear runway approach lines
            mapState.runwayApproachLines.forEach { line ->
                mapState.mapView?.overlays?.remove(line)
            }
            mapState.runwayApproachLines = emptyList()
            
            // Save navigation state before disposing
            try {
                val navPrefs = context.getSharedPreferences("map_navigation_prefs", android.content.Context.MODE_PRIVATE)
                navPrefs.edit().apply {
                    // Save active navigation target
                    val targetId = mapState.activeNavigationTarget?.id ?: -999
                    putInt("active_nav_target_id", targetId)
                    
                    // Save runway approach state
                    putBoolean("show_runway_approach", mapState.showRunwayApproach)
                    putFloat("final_approach_distance_nm", mapState.finalApproachDistanceNm.toFloat())
                    putInt("selected_runway_index", mapState.selectedRunwayIndex ?: -1)
                    
                    // Save traffic pattern state
                    putBoolean("show_traffic_pattern", mapState.showTrafficPattern)
                    putInt("pattern_size_ordinal", mapState.patternSize.ordinal)
                    putBoolean("pattern_direction_left", mapState.patternDirection == PatternDirection.LEFT_HAND)
                    putFloat("pattern_final_distance_nm", mapState.patternFinalDistanceNm.toFloat())
                    putBoolean("rounded_pattern_corners", mapState.roundedPatternCorners)
                    
                    apply()
                }
                android.util.Log.d("MapViewer", "Saved navigation state: target=${mapState.activeNavigationTarget?.name}, approach=${mapState.showRunwayApproach}, pattern=${mapState.showTrafficPattern}")
            } catch (e: Exception) {
                android.util.Log.e("MapViewer", "Failed to save navigation state", e)
            }
            
            // Save current map center/zoom and tile preferences on dispose
            try {
                mapState.mapView?.let { mv ->
                    val center = mv.mapCenter
                    prefsManager.setMapCenter(center.latitude, center.longitude)
                    prefsManager.setMapZoom(mv.zoomLevelDouble)
                    prefsManager.setMapAutoCenter(mapState.autoCenter)
                    // persist overlay preferences and remove overlays
                    prefsManager.setMapOverlayCompassEnabled(mapState.compassEnabled)
                    prefsManager.setMapOverlayRangeRingsEnabled(mapState.rangeRingsEnabled)
                    prefsManager.setMapOverlayRangeRingsMaxNm(mapState.rangeRingsMaxNm)
                    try { mapState.compassOverlay?.let { mv.overlays.remove(it) } } catch (_: Exception) {}
                    try { mapState.headingSpeedLineOverlay?.let { mv.overlays.remove(it) } } catch (_: Exception) {}
                    try { mapState.rangeRingsOverlay?.let { mv.overlays.remove(it) } } catch (_: Exception) {}
                    // tile source id is persisted when the user explicitly selects a layer via the dialog.
                }
            } catch (e: Exception) {
                // ignore
            }
            mapState.mapView?.onDetach()
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
                
                // Satellite - Esri World Imagery (reliable, fast)
                OutlinedButton(
                    onClick = { onLayerSelected("EsriSat") },
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
