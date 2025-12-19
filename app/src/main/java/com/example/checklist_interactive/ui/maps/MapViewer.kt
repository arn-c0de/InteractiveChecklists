package com.example.checklist_interactive.ui.maps

import android.content.Context
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    
    // Initialize tactical database and repositories
    // Initialize tactical database and repositories
    val tacticalDb = remember { 
        com.example.checklist_interactive.data.tactical.TacticalDatabase.getInstance(context, useExternalPath = false)
    }
    val locationRepository = remember { 
        com.example.checklist_interactive.data.tactical.LocationRepositoryImpl(tacticalDb.locationDao())
    }
    val routeRepository = remember {
        com.example.checklist_interactive.data.tactical.RouteRepositoryImpl(tacticalDb.routeDao(), tacticalDb.locationDao())
    }
    val markerRouteViewModel = remember {
        MarkerRouteViewModel(locationRepository, routeRepository)
    }
    val routeCreationViewModel = remember {
        RouteCreationViewModel(routeRepository, locationRepository, tacticalDb.runwayDao())
    }

    // Load and restore visible routes from SharedPreferences
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("map_routes_prefs", android.content.Context.MODE_PRIVATE)
        val savedRouteIds = prefs.getStringSet("visible_route_ids", emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet() ?: emptySet()

        if (savedRouteIds.isNotEmpty()) {
            markerRouteViewModel.setVisibleRoutes(savedRouteIds)
        }
    }

    // Load runways for the selected location from the DB
    LaunchedEffect(selectedLocation?.id) {
        val locId = selectedLocation?.id
        if (locId != null) {
            tacticalDb.runwayDao().getRunwaysByLocation(locId).collect { list ->
                selectedRunways = list
            }
        } else {
            selectedRunways = emptyList()
        }
    }

    // Observe visible routes and draw them on map
    val visibleRouteIds by markerRouteViewModel.visibleRouteIds.collectAsState()

    // Save visible routes to SharedPreferences when they change
    LaunchedEffect(visibleRouteIds) {
        val prefs = context.getSharedPreferences("map_routes_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("visible_route_ids", visibleRouteIds.map { it.toString() }.toSet())
            .apply()
    }
    LaunchedEffect(visibleRouteIds, mapView) {
        val mv = mapView ?: return@LaunchedEffect
        
        // Remove all existing route overlays (Polylines, RouteTextOverlay and route markers)
        mv.overlays.removeAll { overlay ->
            overlay is org.osmdroid.views.overlay.Polyline ||
            overlay is RouteTextOverlay ||
            (overlay is org.osmdroid.views.overlay.Marker && overlay.id?.startsWith("route_") == true)
        }
        
        // Draw all visible routes
        visibleRouteIds.forEach { routeId ->
            val routeData = routeRepository.getRouteWithWaypoints(routeId)
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
                    outlinePaint.color = android.graphics.Color.RED
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
                            p?.let { geoPoint ->
                                // 1) If a move is pending, use this tap to set new coords for that marker
                                    if (pendingMoveMarkerId != null) {
                                        val moveId = pendingMoveMarkerId
                                        scope.launch {
                                            try {
                                                val loc = locationRepository.getLocationById(moveId!!)
                                                if (loc != null) {
                                                    val updated = loc.copy(latitude = geoPoint.latitude, longitude = geoPoint.longitude)
                                                    locationRepository.updateLocation(updated)
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
                                    pendingSymbolPlacement?.let { (symbol, affiliation) ->
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
                                                    symbolColor = String.format("#%06X", (0xFFFFFF and affiliation.color.hashCode())),
                                                    icon = "ic_mapicon_${symbol.id}",
                                                    description = "Military symbol: ${symbol.name}"
                                                )
                                                
                                                val insertedId = locationRepository.saveLocation(newLocation)
                                                Log.d(TAG, "Placed military symbol: ${symbol.name} at ${geoPoint.latitude}, ${geoPoint.longitude} (id=$insertedId)")
                                                
                                                // Clear pending placement and optionally set selectedLocation
                                                pendingSymbolPlacement = null
                                                // Optionally fetch the saved entity if needed
                                                // selectedLocation = locationRepository.getLocationById(insertedId.toInt())
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
                                                        val point = android.graphics.Point()
                                                        projection.toPixels(nm.position, point)
                                                        // Convert MapView-local pixels to window coordinates (use InWindow so Compose Popup positions match)
                                                        try {
                                                            val mapLoc = IntArray(2)
                                                            // Use getLocationInWindow so coords align with the Compose window origin (Popup uses window coordinates)
                                                            this@apply.getLocationInWindow(mapLoc)
                                                            // Base screen coordinates at the marker anchor (map window origin + projected point)
                                                            val rawScreenX = mapLoc[0] + point.x
                                                            val rawScreenY = mapLoc[1] + point.y

                                                            // If the marker has an icon, compute its visual center so the radial menu appears centered over the icon
                                                            val iconWidth = try { nm.icon?.intrinsicWidth ?: 0 } catch (_: Throwable) { 0 }
                                                            val iconHeight = try { nm.icon?.intrinsicHeight ?: 0 } catch (_: Throwable) { 0 }

                                                            // Adjust X to center horizontally on icon (anchor may be CENTER or CENTER/BOTTOM)
                                                            val adjX = rawScreenX - (iconWidth / 2)
                                                            // Adjust Y to the icon center; if anchor was BOTTOM the rawScreenY is at bottom of icon
                                                            // Subtract half the icon height to get to visual center, and add a small upward offset so the menu doesn't overlap the marker
                                                            val extraUpPx = with(density) { 6.dp.toPx().toInt() }
                                                            val adjY = rawScreenY - (iconHeight / 2) - extraUpPx

                                                            Log.d(TAG, "nearestMarker=${nm.title} locFound=${loc != null} mapLoc=(${mapLoc[0]},${mapLoc[1]}) point=(${point.x},${point.y}) raw=(${rawScreenX},${rawScreenY}) icon=(${iconWidth}x${iconHeight}) adj=(${adjX},${adjY})")

                                                            if (loc != null) {
                                                                // Show radial menu at adjusted coordinates (centered on marker icon)
                                                                scope.launch {
                                                                    radialMenuMarker = loc
                                                                    radialMenuX = adjX
                                                                    radialMenuY = adjY
                                                                    radialMenuVisible = true
                                                                    lastLongPressedMarkerId = loc.id
                                                                    lastLongPressTime = System.currentTimeMillis()
                                                                }
                                                            } else {
                                                                Log.d(TAG, "Long-press found nearest marker but could not resolve LocationEntity")
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e(TAG, "Failed to compute screen coords for marker", e)
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
        LaunchedEffect(mapView) {
            if (mapView != null) {
                locationRepository.getAllLocations().collect { markers ->
                    mapView?.let { mv ->
                        // Remove existing marker overlays (except position marker)
                        mv.overlays.removeAll { it is org.osmdroid.views.overlay.Marker && it != positionMarker }
                        // Clear mapping to avoid holding stale references
                        markerToLocation.clear()

                        // Add markers to map
                        markers.forEach { marker ->
                            val osmMarker = org.osmdroid.views.overlay.Marker(mv).apply {
                                position = GeoPoint(marker.latitude, marker.longitude)
                                title = marker.name
                                snippet = buildString {
                                    append("Type: ${marker.markerType}")
                                    if (marker.coalition != null) {
                                        append("\nCoalition: ${marker.coalition}")
                                    }
                                    if (marker.markerType == "tactical_military") {
                                        if (marker.symbolEntity.isNotEmpty()) {
                                            append("\nSymbol: ${marker.symbolEntity}")
                                        }
                                        if (marker.symbolAffiliation.isNotEmpty()) {
                                            append("\nAffiliation: ${marker.symbolAffiliation}")
                                        }
                                    }
                                    if (marker.description.isNotEmpty()) {
                                        append("\n${marker.description}")
                                    }
                                }
                                
                                // Set marker icon based on type
                                setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)

                                // Attach LocationEntity to marker and keep mapping so we can find it from touch coords reliably
                                try {
                                    this.setRelatedObject(marker)
                                } catch (_: Throwable) {
                                    // ignore if API not available
                                }
                                try {
                                    markerToLocation[this] = marker
                                } catch (_: Throwable) {
                                    // ignore mapping failures
                                }

                                // Different colors for different types
                                val markerColor = when {
                                    marker.markerType == "airport" -> android.graphics.Color.parseColor("#8B4513")
                                    marker.markerType == "waypoint" -> android.graphics.Color.parseColor("#FFA500")
                                    marker.markerType.startsWith("tactical_blufor") -> android.graphics.Color.parseColor("#00A8FF")
                                    marker.markerType.startsWith("tactical_opfor") -> android.graphics.Color.parseColor("#FF4444")
                                    marker.markerType == "target" -> android.graphics.Color.parseColor("#FF0000")
                                    marker.markerType == "threat" -> android.graphics.Color.parseColor("#FF0000")
                                    else -> android.graphics.Color.parseColor("#9370DB")
                                }
                                
                                // Create marker icon - priority: symbolEntity > airport default > icon field > NO FALLBACK
                                var markerIcon: BitmapDrawable? = null
                                try {
                                    // 1. Try symbolEntity first (for ALL markers with symbolEntity set)
                                    if (markerIcon == null && marker.symbolEntity.isNotEmpty()) {
                                        try {
                                            // Dynamic drawable lookup by resource name (ic_mapicon_<entity>)
                                            val resName = "ic_mapicon_${marker.symbolEntity}"
                                            val iconResId = context.resources.getIdentifier(resName, "drawable", context.packageName)
                                            if (iconResId != 0) {
                                                val drawable = ContextCompat.getDrawable(context, iconResId)?.mutate()
                                                drawable?.let { d ->
                                                    // Apply affiliation color to the icon
                                                    val affiliationColor = when (marker.symbolAffiliation.lowercase()) {
                                                        "friendly" -> android.graphics.Color.parseColor("#00A8FF")  // Blue
                                                        "hostile" -> android.graphics.Color.parseColor("#FF4444")    // Red
                                                        "neutral" -> android.graphics.Color.parseColor("#00FF00")    // Green
                                                        "unknown" -> android.graphics.Color.parseColor("#FFFF80")    // Yellow
                                                        else -> {
                                                            // Try to parse symbolColor if available
                                                            try {
                                                                if (marker.symbolColor.isNotEmpty()) {
                                                                    android.graphics.Color.parseColor(marker.symbolColor)
                                                                } else {
                                                                    android.graphics.Color.parseColor("#FFFF80") // Default yellow
                                                                }
                                                            } catch (e: Exception) {
                                                                android.graphics.Color.parseColor("#FFFF80") // Default yellow
                                                            }
                                                        }
                                                    }
                                                    
                                                    // Create bitmap and apply color filter to colorize the fill
                                                    val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
                                                    val canvas = android.graphics.Canvas(bitmap)
                                                    d.setBounds(0, 0, 64, 64)
                                                    
                                                    // Apply ColorFilter to replace the fill color with affiliation color
                                                    val colorFilter = android.graphics.PorterDuffColorFilter(
                                                        affiliationColor,
                                                        android.graphics.PorterDuff.Mode.SRC_ATOP
                                                    )
                                                    d.colorFilter = colorFilter
                                                    
                                                    d.draw(canvas)
                                                    markerIcon = BitmapDrawable(context.resources, bitmap)
                                                    Log.d(TAG, "Loaded icon for symbolEntity: ${marker.symbolEntity} with affiliation ${marker.symbolAffiliation} (color=${String.format("#%06X", 0xFFFFFF and affiliationColor)})")
                                                }
                                            } else {
                                                Log.w(TAG, "No drawable found for symbolEntity: ${marker.symbolEntity} (tried resName=$resName)")
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to load symbolEntity icon: ${marker.symbolEntity}", e)
                                        }
                                    }
                                    
                                    // 2. For airports, use static airport icon if no symbolEntity icon was loaded
                                    if (markerIcon == null && marker.markerType == "airport") {
                                        try {
                                            val iconResId = context.resources.getIdentifier("ic_mapicon_static_airport", "drawable", context.packageName)
                                            if (iconResId != 0) {
                                                val drawable = ContextCompat.getDrawable(context, iconResId)?.mutate()
                                                drawable?.let { d ->
                                                    val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
                                                    val canvas = android.graphics.Canvas(bitmap)
                                                    d.setBounds(0, 0, 64, 64)
                                                    d.draw(canvas)
                                                    markerIcon = BitmapDrawable(context.resources, bitmap)
                                                    Log.d(TAG, "Loaded default airport icon for: ${marker.name}")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to load airport icon", e)
                                        }
                                    }
                                    
                                    // 3. Try icon field if available (for custom icons) - add ic_mapicon_ prefix if not present
                                    if (markerIcon == null && marker.icon.isNotEmpty() && marker.icon != "default") {
                                        try {
                                            // Try with ic_mapicon_ prefix first if not already present
                                            val iconName = if (marker.icon.startsWith("ic_mapicon_")) marker.icon else "ic_mapicon_${marker.icon}"
                                            val iconResId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
                                            if (iconResId != 0) {
                                                val drawable = ContextCompat.getDrawable(context, iconResId)?.mutate()
                                                drawable?.let { d ->
                                                    // Don't use setTint() - render the icon as-is with its original colors
                                                    val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
                                                    val canvas = android.graphics.Canvas(bitmap)
                                                    d.setBounds(0, 0, 64, 64)
                                                    d.draw(canvas)
                                                    markerIcon = BitmapDrawable(context.resources, bitmap)
                                                    Log.d(TAG, "Loaded icon from icon field: ${marker.icon} (resolved to $iconName, resId=$iconResId)")
                                                }
                                            } else {
                                                // Try without prefix as fallback
                                                val iconResId2 = context.resources.getIdentifier(marker.icon, "drawable", context.packageName)
                                                if (iconResId2 != 0) {
                                                    val drawable = ContextCompat.getDrawable(context, iconResId2)?.mutate()
                                                    drawable?.let { d ->
                                                        val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
                                                        val canvas = android.graphics.Canvas(bitmap)
                                                        d.setBounds(0, 0, 64, 64)
                                                        d.draw(canvas)
                                                        markerIcon = BitmapDrawable(context.resources, bitmap)
                                                        Log.d(TAG, "Loaded icon from icon field (no prefix): ${marker.icon} (resId=$iconResId2)")
                                                    }
                                                } else {
                                                    Log.w(TAG, "No drawable found for icon field: ${marker.icon} (tried $iconName and ${marker.icon})")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to load icon from icon field: ${marker.icon}", e)
                                        }
                                    }
                                    
                                    // NO FALLBACK - if icon is not found, show error
                                    if (markerIcon == null) {
                                        Log.e(TAG, "FAILED TO LOAD ICON for marker: ${marker.name} (type=${marker.markerType}, symbolEntity=${marker.symbolEntity}, icon=${marker.icon})")
                                        // Create red error marker to make it obvious
                                        val bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
                                        val canvas = android.graphics.Canvas(bitmap)
                                        val paint = android.graphics.Paint().apply {
                                            isAntiAlias = true
                                            color = android.graphics.Color.RED
                                            style = android.graphics.Paint.Style.FILL
                                        }
                                        val strokePaint = android.graphics.Paint().apply {
                                            isAntiAlias = true
                                            color = android.graphics.Color.WHITE
                                            style = android.graphics.Paint.Style.STROKE
                                            strokeWidth = 4f
                                        }
                                        canvas.drawCircle(24f, 24f, 18f, paint)
                                        canvas.drawCircle(24f, 24f, 18f, strokePaint)
                                        // Draw X in the middle
                                        val xPaint = android.graphics.Paint().apply {
                                            isAntiAlias = true
                                            color = android.graphics.Color.WHITE
                                            style = android.graphics.Paint.Style.STROKE
                                            strokeWidth = 3f
                                        }
                                        canvas.drawLine(12f, 12f, 36f, 36f, xPaint)
                                        canvas.drawLine(36f, 12f, 12f, 36f, xPaint)
                                        markerIcon = BitmapDrawable(context.resources, bitmap)
                                    }
                                    
                                    // Set the icon
                                    icon = markerIcon
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to set marker icon for ${marker.name}", e)
                                    // Use default marker
                                }
                            }



                            mv.overlays.add(osmMarker)

                            // Attach click and long-press listeners to marker
                            osmMarker.setOnMarkerClickListener(object : org.osmdroid.views.overlay.Marker.OnMarkerClickListener {
                                override fun onMarkerClick(markerView: org.osmdroid.views.overlay.Marker?, mapView: org.osmdroid.views.MapView?): Boolean {
                                    // Suppress short click if it immediately followed a long-press on the same marker
                                    if (lastLongPressedMarkerId == marker.id && System.currentTimeMillis() - lastLongPressTime < 1000L) {
                                        lastLongPressedMarkerId = null
                                        return true
                                    }

                                    // Short click: show marker details
                                    selectedLocation = marker
                                    showMarkerRouteManagement = true

                                    (context as? android.app.Activity)?.runOnUiThread {
                                        lastProgrammaticMove.value = System.currentTimeMillis()
                                        try {
                                            markerView?.let { mv2 -> mapView?.controller?.animateTo(mv2.position) }
                                            mapView?.invalidate()
                                        } catch (_: Exception) {}
                                    }

                                    return true
                                }
                            })

                            // TODO: Implement long press listener for radial menu
                            // osmdroid doesn't have built-in long press support for markers
                            // Consider implementing using GestureDetector or custom touch handling
                        }
                        
                        mv.invalidate()
                        Log.d(TAG, "Loaded ${markers.size} markers onto map")
                    }
                }
            }
        }
        
        // Control overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Center on position button
            FloatingActionButton(
                onClick = {
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
                containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = stringResource(R.string.map_center_on_aircraft)
                )
            }
            
            // Layer selection button
            FloatingActionButton(
                onClick = { showLayerDialog = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = stringResource(R.string.map_layers)
                )
            }

            // Overlay selection button (compass, range rings)
            FloatingActionButton(
                onClick = { showOverlayDialog = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Flight,
                    contentDescription = stringResource(R.string.map_overlays)
                )
            }
            
            // Add Military Symbol button
            FloatingActionButton(
                onClick = { showMilitarySymbolPicker = true },
                containerColor = if (pendingSymbolPlacement != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Military Symbol"
                )
            }
            
            // Marker/Route management button
            FloatingActionButton(
                onClick = { showMarkerRouteManagement = true },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Markers & Routes"
                )
            }
            
            // Screen lock button - prevents tab swipe gestures
            FloatingActionButton(
                onClick = onLockScreen,
                containerColor = if (isScreenLocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    imageVector = if (isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isScreenLocked) stringResource(R.string.cd_unlock_screen) else stringResource(R.string.cd_lock_screen)
                )
            }

            // Map rotate button (North-up / HDG-up toggle)
            FloatingActionButton(
                onClick = {
                    mapRotationMode = (mapRotationMode + 1) % 2
                    if (mapRotationMode == 0) {
                        try { mapView?.setMapOrientation(0f) } catch (_: Throwable) {}
                    } else {
                        flightData?.let { d -> try { mapView?.setMapOrientation(-Math.toDegrees(d.heading).toFloat()) } catch (_: Throwable) {} }
                    }
                },
                containerColor = if (mapRotationMode == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    imageVector = if (mapRotationMode == 1) Icons.Default.Flight else Icons.Default.Explore,
                    contentDescription = "Toggle map rotation (North / HDG)"
                )
            }
        }

        // Active Navigation Display (top center)
        if (activeNavigationTarget != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .widthIn(max = 400.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
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
                    }

                    // Cancel button
                    IconButton(
                        onClick = {
                            activeNavigationTarget = null
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
        }

        // Connection status indicator (only show when DataPad enabled)
        if (datapadEnabled && !isConnected) {
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
                    locationRepository.getLocationById(pendingMoveMarkerId!!)?.name
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
        
        // Draggable FABs for QuickNote and DataPad
        // Use effective height excluding TabBar so FABs cannot be dragged under/above TabBar
        val prefsManager = remember { com.example.checklist_interactive.data.prefs.PreferencesManager(context) }
        val screenWidth = screenWidthPx
        val screenHeight = effectiveScreenHeightPx

        if (quickNoteManager != null) {
            DraggableFab(
                name = "map_quicknote_fab",
                prefsManager = prefsManager,
                screenWidthPx = screenWidth,
                screenHeightPx = screenHeight,
                fabSizePx = fabSizePx,
                defaultX = 0.85f,
                defaultY = 0.75f,
                visible = true,
                onClick = { showQuickAccess = true },
                content = { Icon(Icons.AutoMirrored.Filled.Note, contentDescription = stringResource(R.string.quick_notes_title)) },
                marginPx = fabMarginPx
            )
        }

        val datapadManager = LocalDataPadManager.current
        val datapadEnabled by datapadManager.isEnabled.collectAsState()

        DraggableFab(
            name = "map_datapad_fab",
            prefsManager = prefsManager,
            screenWidthPx = screenWidth,
            screenHeightPx = screenHeight,
            fabSizePx = fabSizePx,
            defaultX = 0.85f,
            defaultY = 0.85f,
            visible = datapadEnabled,
            onClick = { if (datapadEnabled) showDataPad = true },

            content = { Icon(Icons.Default.Flight, contentDescription = stringResource(R.string.datapad_title)) },
            marginPx = fabMarginPx
        )
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
                                locationRepository.deleteLocation(marker.id)
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
        MarkerRouteManagementSheet(
            viewModel = markerRouteViewModel,
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
                    val routeData = routeRepository.getRouteWithWaypoints(route.id)
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
                routeCreationViewModel.startRouteCreation()
                showRouteCreation = true
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
                routeCreationViewModel.startRouteEditing(routeId)
                showMarkerRouteManagement = false
                showRouteCreation = true
            }
        )
    }
    
    // Route Creation Sheet
    if (showRouteCreation) {
        RouteCreationSheet(
            viewModel = routeCreationViewModel,
            onDismiss = {
                showRouteCreation = false
                routeCreationViewModel.cancelRouteCreation()
            },
            onWaypointClick = { location ->
                // Center map on waypoint
                mapView?.controller?.animateTo(GeoPoint(location.latitude, location.longitude))
            },
            onRouteFinished = { routeId ->
                // Force refresh the route on the map
                scope.launch {
                    val currentVisibleIds = markerRouteViewModel.visibleRouteIds.value
                    if (currentVisibleIds.contains(routeId)) {
                        // Route is already visible - toggle off and on to force redraw
                        markerRouteViewModel.toggleRouteVisibility(routeId)
                        kotlinx.coroutines.delay(50)
                        markerRouteViewModel.toggleRouteVisibility(routeId)
                    } else {
                        // Route is not visible - make it visible
                        markerRouteViewModel.toggleRouteVisibility(routeId)
                    }
                }
            }
        )
    }
    
    DisposableEffect(Unit) {
        onDispose {
            // Clear navigation line
            navigationLine?.let { line ->
                mapView?.overlays?.remove(line)
            }
            navigationLine = null
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

/**
 * Fixed-size compass overlay drawn centered on given GeoPoint (scales with zoom, not speed)
 */
private class CompassOverlay : org.osmdroid.views.overlay.Overlay() {
    var center: GeoPoint? = null
    var heading: Float = 0f
    private val paint = Paint().apply {
        isAntiAlias = true
        // more transparent red for a subtler compass
        color = android.graphics.Color.argb(0x66, 0xFF, 0x44, 0x44)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        // slightly translucent labels
        color = android.graphics.Color.argb(0xCC, 0xFF, 0xFF, 0xFF)
        textSize = 32f
    }

    override fun draw(canvas: android.graphics.Canvas?, mapView: org.osmdroid.views.MapView?, shadow: Boolean) {
        if (shadow) return
        val mv = mapView ?: return
        val c = center ?: return
        val proj = mv.projection
        val centerPt = Point()
        proj.toPixels(c, centerPt)

        // Fixed radius based on zoom level (NOT speed)
        val zoomLevel = mv.zoomLevelDouble
        // Original base (40..240) scaled up ~2.2x (a bit smaller than before)
        val baseRadius = ((40f + (zoomLevel.toFloat() - 8f) * 20f) * 2.2f).coerceIn(88f, 528f)

        // outer circle
        val circlePaint = Paint(paint).apply { style = Paint.Style.STROKE; strokeWidth = paint.strokeWidth }
        canvas?.drawCircle(centerPt.x.toFloat(), centerPt.y.toFloat(), baseRadius, circlePaint)

        // Cardinal radial lines and labels (N, E, S, W)
        val cardinals = listOf(0, 90, 180, 270)
        val labelMap = mapOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")
        for (angle in cardinals) {
            val rad = Math.toRadians(angle.toDouble())
            val dx = (Math.sin(rad) * baseRadius).toFloat()
            val dy = (-Math.cos(rad) * baseRadius).toFloat()
            // full line across the circle
            canvas?.drawLine(centerPt.x - dx, centerPt.y - dy, centerPt.x + dx, centerPt.y + dy, paint)
            // label slightly beyond the ring
            val lx = centerPt.x + (dx * 1.12f)
            val ly = centerPt.y + (dy * 1.12f) + (textPaint.textSize / 3)
            canvas?.drawText(labelMap[angle] ?: "", lx, ly, textPaint)
        }

        // Degree ticks around outer ring
        // - Minor ticks every 5° (small)
        // - Major ticks every 30° (long)
        // - Numeric labels at 0°, 90°, 180°, 270°
        val smallTickPaint = Paint().apply { isAntiAlias = true; color = android.graphics.Color.WHITE; strokeWidth = 1f }
        val majorTickPaint = Paint().apply { isAntiAlias = true; color = android.graphics.Color.WHITE; strokeWidth = 2f }
        val labelSize = Paint(textPaint).apply { textSize = 18f }

        // Minor ticks (every 5° excluding the major tick positions)
        for (a in 0 until 360 step 5) {
            if (a % 30 == 0) continue // skip majors
            val rad = Math.toRadians(a.toDouble())
            val dx = (Math.sin(rad) * baseRadius).toFloat()
            val dy = (-Math.cos(rad) * baseRadius).toFloat()
            val innerX = centerPt.x + (dx * 0.985f)
            val innerY = centerPt.y + (dy * 0.985f)
            val outerX = centerPt.x + dx
            val outerY = centerPt.y + dy
            canvas?.drawLine(innerX, innerY, outerX, outerY, smallTickPaint)
        }

        // Major ticks and numeric labels (every 30°; label every 90°)
        for (a in 0 until 360 step 30) {
            val rad = Math.toRadians(a.toDouble())
            val dx = (Math.sin(rad) * baseRadius).toFloat()
            val dy = (-Math.cos(rad) * baseRadius).toFloat()
            val innerX = centerPt.x + (dx * 0.92f)
            val innerY = centerPt.y + (dy * 0.92f)
            val outerX = centerPt.x + dx
            val outerY = centerPt.y + dy
            canvas?.drawLine(innerX, innerY, outerX, outerY, majorTickPaint)

            if (a % 90 == 0) {
                // numeric degree label at 0°, 90°, 180°, 270°
                val lx = centerPt.x + (dx * 1.22f) - (labelSize.measureText("$a°") / 2)
                val ly = centerPt.y + (dy * 1.22f) + (labelSize.textSize / 2)
                canvas?.drawText("$a°", lx, ly, labelSize)
            }
        }

        // Heading indicator (just a small marker on the compass ring, NOT a line)
        val headingNorm = (((heading % 360) + 360) % 360).toInt()
        val radH = Math.toRadians(headingNorm.toDouble())
        val dxH = (Math.sin(radH) * baseRadius).toFloat()
        val dyH = (-Math.cos(radH) * baseRadius).toFloat()
        // Draw a small circle marker at the heading position on the ring
        val headingPaint = Paint().apply { isAntiAlias = true; color = android.graphics.Color.YELLOW; style = Paint.Style.FILL }
        // Draw a small circle marker at the heading position
        canvas?.drawCircle(centerPt.x + dxH, centerPt.y + dyH, 8f, headingPaint)
        // Outline for visibility
        val outlinePaint = Paint().apply { isAntiAlias = true; color = android.graphics.Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
        canvas?.drawCircle(centerPt.x + dxH, centerPt.y + dyH, 8f, outlinePaint)
        
        // Label with heading near the marker
        val label = "HDG ${headingNorm}°"
        val labelX = centerPt.x + (dxH * 1.25f)
        val labelY = centerPt.y + (dyH * 1.25f)
        val headingTextPaint = Paint(textPaint).apply { color = android.graphics.Color.YELLOW; textSize = 24f }
        // Draw shadow for readability
        canvas?.drawText(label, labelX + 2f, labelY + 2f, Paint(headingTextPaint).apply { color = android.graphics.Color.argb(0xCC, 0, 0, 0) })
        canvas?.drawText(label, labelX, labelY, headingTextPaint)
    }
}

/**
 * Heading speed line overlay: yellow line from center showing heading, length based on speed
 */
private class HeadingSpeedLineOverlay : org.osmdroid.views.overlay.Overlay() {
    var center: GeoPoint? = null
    var heading: Float = 0f
    var speedKts: Double = 0.0
    
    private val linePaint = Paint().apply {
        // Slightly transparent yellow for subtler visual
        color = android.graphics.Color.argb(0xCC, 0xFF, 0xFF, 0x00)
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        textSize = 30f
    }
    private val bgPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(0xCC, 0, 0, 0)
        style = Paint.Style.FILL
    }

    override fun draw(canvas: android.graphics.Canvas?, mapView: org.osmdroid.views.MapView?, shadow: Boolean) {
        if (shadow || canvas == null || mapView == null) return
        val c = center ?: return

        val screenPt = Point()
        val projection = mapView.projection
        projection.toPixels(c, screenPt)

        val cx = screenPt.x.toFloat()
        val cy = screenPt.y.toFloat()

        // Defensive speed handling to avoid NaN/Infinite lengths
        val safeSpeed = if (speedKts.isFinite()) speedKts.coerceIn(0.0, 1000.0) else 0.0
        val speedFactor = (safeSpeed / 300.0).coerceIn(0.0, 1.0)
        // Original was 50 + 150*factor, scale up by 2.5x (half of previous 5x), clamp to safe pixel range
        var lineLength = ((50f + 150f * speedFactor.toFloat()) * 2.5f).coerceIn(50f, 1000f)
        if (!lineLength.isFinite()) lineLength = 200f

        // Validate heading
        if (!heading.isFinite()) return
        val headingRad = Math.toRadians(heading.toDouble())
        var endX = cx + lineLength * Math.sin(headingRad).toFloat()
        var endY = cy - lineLength * Math.cos(headingRad).toFloat()

        // Defensive check: ensure endpoints are finite and within reasonable bounds
        if (!endX.isFinite() || !endY.isFinite()) {
            // fallback to capped length
            lineLength = 200f
            endX = cx + lineLength * Math.sin(headingRad).toFloat()
            endY = cy - lineLength * Math.cos(headingRad).toFloat()
        }

        // Draw the heading line
        canvas.drawLine(cx, cy, endX, endY, linePaint)

        // Draw speed label at the tip of the line (only if speed is finite)
        if (safeSpeed.isFinite()) {
            try {
                val speedText = String.format("%d kt", safeSpeed.toInt())
                val padding = 8f
                val textWidth = textPaint.measureText(speedText)
                // Position label slightly beyond the end point along the heading
                val labelOffset = 12f
                val labelX = endX + labelOffset * Math.sin(headingRad).toFloat()
                val labelY = endY - labelOffset * Math.cos(headingRad).toFloat()

                val rectLeft = labelX - padding
                val rectTop = labelY - textPaint.textSize
                val rectRight = labelX + textWidth + padding
                val rectBottom = labelY + (textPaint.textSize * 0.2f)

                canvas.drawRoundRect(android.graphics.RectF(rectLeft, rectTop, rectRight, rectBottom), 6f, 6f, bgPaint)
                // Draw text with small shadow
                textPaint.color = android.graphics.Color.WHITE
                canvas.drawText(speedText, labelX, labelY, Paint(textPaint).apply { color = android.graphics.Color.argb(0xCC, 0, 0, 0); textSize = textPaint.textSize })
                canvas.drawText(speedText, labelX, labelY, textPaint)
            } catch (_: Throwable) {
                // ignore drawing errors
            }
        }
    }
}

/**
 * Range rings overlay: concentric circles around center to estimate distances (1,2,5 NM)
 */
private class RangeRingsOverlay : org.osmdroid.views.overlay.Overlay() {
    var center: GeoPoint? = null
    // current heading (used to place exact heading label on outermost ring)
    var heading: Float = 0f
    // current speed in knots; used to scale the heading radial length
    var speedKts: Double = 0.0
    // max radius in NM; default 5
    var maxNm: Int = 5
    private val paint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(0x99, 0x22, 0x88, 0xFF)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        textSize = 22f
    }

    private fun generateDistancesMeters(): List<Double> {
        // Generate sequence 1,2,5,10,20,50,100,200,... up to maxNm
        val bases = listOf(1, 2, 5)
        val resultNm = mutableListOf<Int>()
        var multiplier = 1
        while (true) {
            var addedAny = false
            for (b in bases) {
                val nm = b * multiplier
                if (nm <= maxNm) {
                    resultNm.add(nm)
                    addedAny = true
                }
            }
            if (!addedAny) break
            multiplier *= 10
        }
        // ensure unique and sorted
        val finalNm = resultNm.distinct().sorted()
        return finalNm.map { it * 1852.0 }
    }

    override fun draw(canvas: android.graphics.Canvas?, mapView: org.osmdroid.views.MapView?, shadow: Boolean) {
        if (shadow) return
        val mv = mapView ?: return
        val c = center ?: return
        val proj = mv.projection
        val centerPt = Point()
        proj.toPixels(c, centerPt)

        val latRad = Math.toRadians(c.latitude)

        val distances = generateDistancesMeters()

        var outerRadiusPx = 0f
        distances.forEachIndexed { idx, meters ->
            // convert meters to degrees longitude delta at this latitude
            val deltaLon = meters / (111319.9 * Math.cos(latRad))
            val edge = GeoPoint(c.latitude, c.longitude + deltaLon)
            val edgePt = Point()
            proj.toPixels(edge, edgePt)
            val radiusPx = kotlin.math.hypot((edgePt.x - centerPt.x).toDouble(), (edgePt.y - centerPt.y).toDouble()).toFloat()
            canvas?.drawCircle(centerPt.x.toFloat(), centerPt.y.toFloat(), radiusPx, paint)
            // label at rightmost point
            val nm = (meters / 1852.0).toInt()
            val label = "%d NM".format(nm)
            canvas?.drawText(label, centerPt.x + radiusPx + 6f, centerPt.y.toFloat() - 6f - (idx * 18), textPaint)
            if (idx == distances.lastIndex) outerRadiusPx = radiusPx
        }

        if (outerRadiusPx > 0f) {
            // Draw cardinal radial lines through the outermost ring and label them
            val cardinals = listOf(0, 90, 180, 270)
            val labelMap = mapOf(0 to "N", 90 to "O", 180 to "S", 270 to "W")
            val tickPaint = Paint().apply { isAntiAlias = true; color = android.graphics.Color.argb(0xCC, 0xFF, 0xFF, 0xFF); strokeWidth = 2f }
            val smallText = Paint(textPaint).apply { textSize = 18f }

            for (angle in cardinals) {
                val rad = Math.toRadians(angle.toDouble())
                val dx = (Math.sin(rad) * outerRadiusPx).toFloat()
                val dy = (-Math.cos(rad) * outerRadiusPx).toFloat()
                canvas?.drawLine(centerPt.x - dx, centerPt.y - dy, centerPt.x + dx, centerPt.y + dy, tickPaint)
                val lx = centerPt.x + (dx * 1.05f) - (smallText.measureText(labelMap[angle] ?: "") / 2)
                val ly = centerPt.y + (dy * 1.05f) + (smallText.textSize / 2)
                canvas?.drawText(labelMap[angle] ?: "", lx, ly, smallText)
            }

            // Degree ticks around outer ring (every 30°, label every 60°)
            for (a in 0 until 360 step 30) {
                val rad = Math.toRadians(a.toDouble())
                val dx = (Math.sin(rad) * outerRadiusPx).toFloat()
                val dy = (-Math.cos(rad) * outerRadiusPx).toFloat()
                val innerX = centerPt.x + (dx * 0.97f)
                val innerY = centerPt.y + (dy * 0.97f)
                val outerX = centerPt.x + dx
                val outerY = centerPt.y + dy
                canvas?.drawLine(innerX, innerY, outerX, outerY, tickPaint)
                if (a % 60 == 0) {
                    val lab = "$a°"
                    val lx = centerPt.x + (dx * 1.08f) - (smallText.measureText(lab) / 2)
                    val ly = centerPt.y + (dy * 1.08f) + (smallText.textSize / 2)
                    canvas?.drawText(lab, lx, ly, smallText)
                }
            }

            // Draw exact heading label and heading radial line on the outermost ring (length scaled by speed)
            val headingNorm = (((heading % 360) + 360) % 360).toInt()
            val hRad = Math.toRadians(headingNorm.toDouble())

            // Scale heading radial by speed: small when stationary, longer with speed
            val minFactor = 0.05f
            val maxFactor = 1.0f
            val maxSpeed = 300.0 // knots
            var scaleFactor = (minFactor + ((speedKts.coerceIn(0.0, maxSpeed) / maxSpeed) * (maxFactor - minFactor))).toFloat()
            if (!scaleFactor.isFinite() || scaleFactor.isNaN()) scaleFactor = minFactor
            // Compute heading radial scaled by speed; cap it to a configurable maximum so it never becomes too long.
            // maxHeadingRatio defines the maximum fraction of the outer ring the heading radial may occupy.
            // Also clamp to an absolute pixel limit for very large rings.
            val headingRadiusRaw = outerRadiusPx * scaleFactor * 1f
            val minHeadingPx = 12f
            val maxHeadingRatio = 0.6f // at most 60% of outer ring
            val maxHeadingAbsPx = 300f // absolute cap in pixels
            val maxHeadingPx = kotlin.math.min(outerRadiusPx * maxHeadingRatio, maxHeadingAbsPx)
            val headingRadius = headingRadiusRaw.coerceIn(minHeadingPx, maxHeadingPx)

            val hx = centerPt.x + (Math.sin(hRad) * headingRadius).toFloat()
            val hy = centerPt.y + (-Math.cos(hRad) * headingRadius).toFloat()
            val headingLabel = "${headingNorm}°"
            val headingPaint = Paint(textPaint).apply { color = android.graphics.Color.YELLOW; textSize = textPaint.textSize + 2f }
            // Draw a highlighted radial for heading (slightly transparent)
            val headingLinePaint = Paint(paint).apply { color = android.graphics.Color.argb(0xCC, 0xFF, 0xFF, 0x00); strokeWidth = 4f }
            canvas?.drawLine(centerPt.x.toFloat(), centerPt.y.toFloat(), hx, hy, headingLinePaint)

            // Draw heading label
            canvas?.drawText(headingLabel, hx, hy - 6f, headingPaint)
        }
    }
}

/**
 * MGRS Grid overlay: draws Military Grid Reference System grid lines in red with transparency
 */
private class MgrsGridOverlay : org.osmdroid.views.overlay.Overlay() {
    private val gridPaint = Paint().apply {
        color = android.graphics.Color.argb(128, 255, 0, 0) // Red with 50% transparency
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = android.graphics.Color.argb(200, 255, 0, 0) // Red with 78% transparency
        textSize = 24f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val bgPaint = Paint().apply {
        color = android.graphics.Color.argb(180, 0, 0, 0) // Black background for text
        style = Paint.Style.FILL
    }

    override fun draw(canvas: android.graphics.Canvas?, mapView: org.osmdroid.views.MapView?, shadow: Boolean) {
        if (canvas == null || mapView == null || shadow) return

        val projection = mapView.projection
        val boundingBox = projection.boundingBox

        // Calculate grid spacing based on zoom level
        val zoom = mapView.zoomLevelDouble
        val gridSpacing = when {
            zoom >= 15 -> 0.001 // ~100m
            zoom >= 13 -> 0.01  // ~1km
            zoom >= 10 -> 0.1   // ~10km
            else -> 1.0         // ~100km
        }

        // Draw vertical lines (longitude)
        var lon = Math.floor(boundingBox.lonWest / gridSpacing) * gridSpacing
        while (lon <= boundingBox.lonEast) {
            val topPoint = projection.toPixels(GeoPoint(boundingBox.latNorth, lon), null)
            val bottomPoint = projection.toPixels(GeoPoint(boundingBox.latSouth, lon), null)
            canvas?.drawLine(
                topPoint.x.toFloat(),
                topPoint.y.toFloat(),
                bottomPoint.x.toFloat(),
                bottomPoint.y.toFloat(),
                gridPaint
            )

            // Draw label at top
            val label = formatMgrsCoordinate(lon, true)
            val textX = topPoint.x.toFloat()
            val textY = topPoint.y.toFloat() + 30f
            val textBounds = android.graphics.Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            canvas?.drawRect(
                textX - textBounds.width() / 2f - 4f,
                textY - textBounds.height() - 4f,
                textX + textBounds.width() / 2f + 4f,
                textY + 4f,
                bgPaint
            )
            canvas?.drawText(label, textX, textY, textPaint)

            lon += gridSpacing
        }

        // Draw horizontal lines (latitude)
        var lat = Math.floor(boundingBox.latSouth / gridSpacing) * gridSpacing
        while (lat <= boundingBox.latNorth) {
            val leftPoint = projection.toPixels(GeoPoint(lat, boundingBox.lonWest), null)
            val rightPoint = projection.toPixels(GeoPoint(lat, boundingBox.lonEast), null)
            canvas?.drawLine(
                leftPoint.x.toFloat(),
                leftPoint.y.toFloat(),
                rightPoint.x.toFloat(),
                rightPoint.y.toFloat(),
                gridPaint
            )

            // Draw label at left
            val label = formatMgrsCoordinate(lat, false)
            val textX = leftPoint.x.toFloat() + 50f
            val textY = leftPoint.y.toFloat()
            val textBounds = android.graphics.Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            canvas?.drawRect(
                textX - textBounds.width() / 2f - 4f,
                textY - textBounds.height() / 2f - 4f,
                textX + textBounds.width() / 2f + 4f,
                textY + textBounds.height() / 2f + 4f,
                bgPaint
            )
            canvas?.drawText(label, textX, textY + textBounds.height() / 2f, textPaint)

            lat += gridSpacing
        }
    }
    
    private fun formatMgrsCoordinate(value: Double, isLongitude: Boolean): String {
        val degrees = Math.abs(value).toInt()
        val minutes = ((Math.abs(value) - degrees) * 60).toInt()
        val direction = if (isLongitude) {
            if (value >= 0) "E" else "W"
        } else {
            if (value >= 0) "N" else "S"
        }
        return String.format("%d\u00b0%02d'%s", degrees, minutes, direction)
    }
}

