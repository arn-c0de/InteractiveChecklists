package com.example.checklist_interactive.ui.maps

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.MotionEvent
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
    
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var positionMarker by remember { mutableStateOf<Marker?>(null) }
    // Load previous map state from preferences
    val prefsManager = remember { com.example.checklist_interactive.data.prefs.PreferencesManager(context) }
    val savedCenter = remember { prefsManager.getMapCenter() }
    val savedZoom = remember { prefsManager.getMapZoom() }
    var autoCenter by remember { mutableStateOf(if (savedCenter != null) prefsManager.isMapAutoCenterEnabled() else true) }
    var showLayerDialog by remember { mutableStateOf(false) }
    var showQuickAccess by remember { mutableStateOf(false) }
    var showDataPad by remember { mutableStateOf(false) }

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
    
    // Update position marker when flight data changes
    LaunchedEffect(flightData) {
        val data = flightData
        val marker = positionMarker
        val map = mapView
        
        if (data != null && marker != null && map != null) {
            val lat = data.latitude
            val lon = data.longitude
            
            if (lat != 0.0 && lon != 0.0) {
                val newPosition = GeoPoint(lat, lon)
                marker.position = newPosition
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                // Update marker rotation based on heading
                marker.rotation = data.heading.toFloat()

                // Update marker snippet with altitude and speed
                val altFt = (data.altitude * 3.28084).toInt()
                val speedKts = (data.groundSpeed ?: 0.0) * 1.9438
                marker.snippet = "Alt: ${altFt}ft | Spd: ${speedKts.toInt()}kt | Hdg: ${data.heading.toInt()}°"
                marker.title = data.aircraft

                // Auto-center map on position if enabled
                if (autoCenter) {
                    Log.d(TAG, "Auto-centering enabled, animating to: $lat,$lon")
                    lastProgrammaticMove.value = System.currentTimeMillis()
                    map.controller.animateTo(newPosition)
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
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val tabBarHeightPx = with(density) { 40.dp.roundToPx() } // matches TabBar height
    val effectiveScreenHeightPx = (screenHeightPx - tabBarHeightPx).coerceAtLeast(1)
    val fabSizePx = with(density) { 56.dp.roundToPx() }
    val fabMarginPx = with(density) { 12.dp.roundToPx() }

    // Determine initial center/zoom: prefer saved values, fall back to latest flightData, then default
    val initialCenter: GeoPoint? = savedCenter?.let { GeoPoint(it.first, it.second) }
        ?: flightData?.let { if (it.latitude != 0.0 && it.longitude != 0.0) GeoPoint(it.latitude, it.longitude) else null }
    val initialZoom: Double = savedZoom ?: 8.0

    // Map theme / tile helpers (moved out so they are accessible from the layer dialog handler)
    val isDarkTheme = isSystemInDarkTheme()
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
                    if (initialCenter != null) controller.setCenter(initialCenter) else controller.setCenter(GeoPoint(48.0, 11.0))

                    // Create position marker
                    val marker = Marker(this).apply {
                        title = "Aircraft Position"
                        snippet = "Waiting for data..."
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                        // Try to use a custom aircraft icon
                        try {
                            val drawable = ContextCompat.getDrawable(ctx, android.R.drawable.ic_menu_mylocation)
                            icon = drawable
                        } catch (e: Exception) {
                            // Use default marker
                        }
                    }

                    overlays.add(marker)
                    positionMarker = marker
                    mapView = this

                    // Touch listener: mark when real user touches the map so we don't disable auto-center from animations
                    try {
                        setOnTouchListener { _, ev ->
                            try {
                                if (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE) {
                                    lastUserTouch.value = System.currentTimeMillis()
                                    prefsManager.setMapAutoCenter(false)
                                    try {
                                        (ctx as? android.app.Activity)?.runOnUiThread {
                                            autoCenter = false
                                        }
                                    } catch (_: Exception) {
                                    }
                                }
                            } catch (_: Exception) {
                            }
                            // Let MapView handle the touch too
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
                                    try {
                                        (ctx as? android.app.Activity)?.runOnUiThread {
                                            autoCenter = false
                                        }
                                    } catch (_: Exception) {
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
                        Log.d(TAG, "Center FAB pressed — centering to live position ${data.latitude},${data.longitude}")
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
                            Log.d(TAG, "Center FAB pressed — centering to saved position ${saved.first},${saved.second}")
                        } else {
                            // no center available — remember to center once we get a valid position
                            pendingCenter.value = null // will be set when new data arrives in the flight-data effect
                            autoCenter = true
                            prefsManager.setMapAutoCenter(true)
                            Log.d(TAG, "Center FAB pressed — no position available yet, will enable auto-centering for future updates")
                        }
                    }
                },
                containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Center on aircraft"
                )
            }
            
            // Layer selection button
            FloatingActionButton(
                onClick = { showLayerDialog = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Map layers"
                )
            }
            
            // Screen lock button - prevents tab swipe gestures
            FloatingActionButton(
                onClick = onLockScreen,
                containerColor = if (isScreenLocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    imageVector = if (isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isScreenLocked) "Unlock screen" else "Lock screen"
                )
            }
        }
        
        // Connection status indicator
        if (!isConnected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "⚠ No DataPad connection - Map position unavailable",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else if (flightData?.latitude == 0.0 || flightData?.longitude == 0.0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "ℹ Waiting for valid position data...",
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
                        text = "🎯 Auto-centering (tap to disable)",
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
                        text = "Auto-center OFF (tap to enable)",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // Small tip when map lock is disabled (top-center, English, lowercase)
        if (!isScreenLocked) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "tip: tap lock to look around the map",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
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
                content = { Icon(Icons.Default.Note, contentDescription = "Quick Notes") },
                marginPx = fabMarginPx
            )
        }

        DraggableFab(
            name = "map_datapad_fab",
            prefsManager = prefsManager,
            screenWidthPx = screenWidth,
            screenHeightPx = screenHeight,
            fabSizePx = fabSizePx,
            defaultX = 0.85f,
            defaultY = 0.85f,
            visible = true,
            onClick = { showDataPad = true },
            content = { Icon(Icons.Default.Flight, contentDescription = "DataPad") },
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
    
    // Quick Access Bottom Sheet
    if (showQuickAccess && quickNoteManager != null) {
        QuickAccessSheet(
            onDismiss = { showQuickAccess = false },
            currentDocumentPath = "special://aviation_map",
            currentDocumentName = "Aviation Map",
            onOpenDocument = { _, _ -> }
        )
    }
    
    // DataPad Popup
    if (showDataPad) {
        DataPadPopup(onDismiss = { showDataPad = false })
    }
    
    DisposableEffect(Unit) {
        onDispose {
            // Save current map center/zoom and tile preferences on dispose
            try {
                mapView?.let { mv ->
                    val center = mv.mapCenter
                    prefsManager.setMapCenter(center.latitude, center.longitude)
                    prefsManager.setMapZoom(mv.zoomLevelDouble)
                    prefsManager.setMapAutoCenter(autoCenter)
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
        title = { Text("Map Layers") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Select a map layer:",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Follow system theme (auto dark/light)
                OutlinedButton(
                    onClick = { onLayerSelected(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Follow system theme")
                }
                
                // Standard OpenStreetMap
                OutlinedButton(
                    onClick = { onLayerSelected("MAPNIK") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("OpenStreetMap")
                }
                
                // Topographic
                OutlinedButton(
                    onClick = { onLayerSelected("OpenTopo") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Topographic")
                }
                
                // Satellite (if available)
                OutlinedButton(
                    onClick = { onLayerSelected("USGS_SAT") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Satellite (USGS)")
                }

                // Dark themed map (CartoDB Dark Matter)
                OutlinedButton(
                    onClick = { onLayerSelected("CartoDB.DarkMatter") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Dark map")
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Note: Aviation sectional charts require additional configuration. See docs/technical/AVIATION_MAPS.md",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
