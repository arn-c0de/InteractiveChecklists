package com.example.checklist_interactive.ui.maps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checklist_interactive.data.tactical.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.json.JSONObject

/**
 * ViewModel for marker and route management
 */
class MarkerRouteViewModel(
    private val locationRepository: LocationRepository,
    private val routeRepository: RouteRepository
) : ViewModel() {
    
    private val _allMarkers = MutableStateFlow<List<LocationEntity>>(emptyList())
    val allMarkers: StateFlow<List<LocationEntity>> = _allMarkers.asStateFlow()
    
    private val _allRoutes = MutableStateFlow<List<RouteEntity>>(emptyList())
    val allRoutes: StateFlow<List<RouteEntity>> = _allRoutes.asStateFlow()
    
    private val _markerGroups = MutableStateFlow<Map<String, List<LocationEntity>>>(emptyMap())
    val markerGroups: StateFlow<Map<String, List<LocationEntity>>> = _markerGroups.asStateFlow()
    
    private val _expandedGroups = MutableStateFlow<Set<String>>(emptySet())
    val expandedGroups: StateFlow<Set<String>> = _expandedGroups.asStateFlow()
    
    init {
        loadData()
    }
    
    fun loadData() {
        viewModelScope.launch {
            locationRepository.getAllLocations().collect { markers ->
                _allMarkers.value = markers
                groupMarkers(markers)
            }
        }
        
        viewModelScope.launch {
            routeRepository.getAllRoutes().collect { routes ->
                _allRoutes.value = routes
            }
        }
    }
    
    private fun groupMarkers(markers: List<LocationEntity>) {
        val groups = mutableMapOf<String, MutableList<LocationEntity>>()
        
        markers.forEach { marker ->
            // Group by marker type
            val groupKey = when {
                marker.markerType == "airport" -> "✈ Airports"
                marker.markerType == "waypoint" -> "📍 Waypoints"
                marker.markerType.startsWith("tactical_blufor") -> "🔵 BLUFOR Units"
                marker.markerType.startsWith("tactical_opfor") -> "🔴 OPFOR Units"
                marker.markerType.startsWith("tactical_neutral") -> "⚪ Neutral Units"
                marker.markerType == "target" -> "🎯 Targets"
                marker.markerType == "threat" -> "⚠ Threats"
                else -> "📌 Other"
            }
            
            groups.getOrPut(groupKey) { mutableListOf() }.add(marker)
        }
        
        _markerGroups.value = groups
    }
    
    fun toggleGroup(groupName: String) {
        _expandedGroups.value = if (_expandedGroups.value.contains(groupName)) {
            _expandedGroups.value - groupName
        } else {
            _expandedGroups.value + groupName
        }
    }
    
    fun createRouteFromGroup(groupName: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val markers = _markerGroups.value[groupName] ?: return@launch
            if (markers.size < 2) return@launch
            
            val route = RouteEntity(
                name = groupName.replace(Regex("^[^a-zA-Z]+"), ""),
                description = "Auto-generated from marker group",
                color = "#00A8FF",
                created = "",
                modified = ""
            )
            
            val locationIds = markers.map { it.id }
            routeRepository.saveRouteWithWaypoints(route, locationIds)
            loadData()
            onSuccess()
        }
    }
    
    fun deleteRoute(routeId: Int) {
        viewModelScope.launch {
            routeRepository.deleteRoute(routeId)
            loadData()
        }
    }
    
    fun deleteMarker(markerId: Int) {
        viewModelScope.launch {
            locationRepository.deleteLocation(markerId)
            loadData()
        }
    }
    
    fun updateMarker(location: LocationEntity) {
        viewModelScope.launch {
            locationRepository.updateLocation(location)
            loadData()
        }
    }
}

/**
 * Marker and Route Management Sheet with integrated marker details
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkerRouteManagementSheet(
    viewModel: MarkerRouteViewModel,
    onDismiss: () -> Unit,
    onMarkerClick: (LocationEntity) -> Unit,
    onRouteClick: (RouteEntity) -> Unit,
    onCreateRoute: () -> Unit,
    selectedMarker: LocationEntity? = null,
    selectedRunways: List<RunwayEntity> = emptyList(),
    onSetActiveRoute: (LocationEntity) -> Unit = {}
) {
    val markerGroups by viewModel.markerGroups.collectAsState()
    val expandedGroups by viewModel.expandedGroups.collectAsState()
    val allRoutes by viewModel.allRoutes.collectAsState()
    // New tab order: 0=Details, 1=Markers (default), 2=Routes
    var selectedTab by remember { mutableStateOf(if (selectedMarker != null) 0 else 1) }
    val view = LocalView.current
    
    // Update tab when selectedMarker changes -> show Details when a marker is selected
    LaunchedEffect(selectedMarker) {
        if (selectedMarker != null) {
            selectedTab = 0
        }
    }
    
    // Force immersive fullscreen mode continuously while bottom sheet is shown
    LaunchedEffect(Unit) {
        val activity = view.context as? android.app.Activity
        val window = activity?.window

        val hideSystemUI = {
            // Hide for activity window (if available)
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
                val controller = WindowInsetsControllerCompat(it, it.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            // Also try to hide for the current view's window (covers dialog window from ModalBottomSheet)
            try {
                val viewWindow = (view.context as? android.app.Activity)?.window
                if (viewWindow != null) {
                    val controller = WindowCompat.getInsetsController(viewWindow, view)
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } catch (_: Throwable) {
            }

            // Try rootView as well in case the modal sheet uses a different attach point
            try {
                val rootWindow = (view.rootView.context as? android.app.Activity)?.window
                if (rootWindow != null) {
                    val controller = WindowCompat.getInsetsController(rootWindow, view.rootView)
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } catch (_: Throwable) {
            }
        }

        // Keep applying continuously to override ModalBottomSheet's behavior
        while (isActive) {
            hideSystemUI()
            kotlinx.coroutines.delay(750L)
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.8f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Map Objects",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close")
                }
            }
            
            // Tabs (reordered: Details | Markers | Routes)
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Details") },
                    enabled = selectedMarker != null
                )

                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Markers (${markerGroups.values.sumOf { it.size }})") }
                )

                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Routes (${allRoutes.size})") }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Content
            when (selectedTab) {
                // New mapping: 0=Details, 1=Markers, 2=Routes
                0 -> {
                    if (selectedMarker != null) {
                        MarkerDetailsContent(
                            location = selectedMarker,
                            runways = selectedRunways,
                            onClose = { selectedTab = 1 },
                            onSetRoute = onSetActiveRoute,
                            onEdit = { updatedLocation ->
                                viewModel.updateMarker(updatedLocation)
                            },
                            onDelete = { markerId ->
                                viewModel.deleteMarker(markerId)
                                selectedTab = 1
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No marker selected",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                1 -> MarkerGroupsList(
                    groups = markerGroups,
                    expandedGroups = expandedGroups,
                    onToggleGroup = { viewModel.toggleGroup(it) },
                    onMarkerClick = { marker ->
                        onMarkerClick(marker)
                        selectedTab = 0
                    },
                    onDeleteMarker = { viewModel.deleteMarker(it) },
                    onCreateRouteFromGroup = { groupName ->
                        viewModel.createRouteFromGroup(groupName) {
                            selectedTab = 2 // Switch to routes tab
                        }
                    }
                )
                2 -> RoutesList(
                    routes = allRoutes,
                    onRouteClick = onRouteClick,
                    onDeleteRoute = { viewModel.deleteRoute(it) },
                    onCreateRoute = onCreateRoute
                )
            }
        }
    }
}

/**
 * Markers organized by groups
 */
@Composable
fun MarkerGroupsList(
    groups: Map<String, List<LocationEntity>>,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    onMarkerClick: (LocationEntity) -> Unit,
    onDeleteMarker: (Int) -> Unit,
    onCreateRouteFromGroup: (String) -> Unit
) {
    if (groups.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No markers yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groups.forEach { (groupName, markers) ->
            item(key = groupName) {
                MarkerGroupItem(
                    groupName = groupName,
                    markers = markers,
                    isExpanded = expandedGroups.contains(groupName),
                    onToggle = { onToggleGroup(groupName) },
                    onMarkerClick = onMarkerClick,
                    onDeleteMarker = onDeleteMarker,
                    onCreateRoute = { onCreateRouteFromGroup(groupName) }
                )
            }
        }
    }
}

/**
 * Individual marker group with expandable children
 */
@Composable
fun MarkerGroupItem(
    groupName: String,
    markers: List<LocationEntity>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onMarkerClick: (LocationEntity) -> Unit,
    onDeleteMarker: (Int) -> Unit,
    onCreateRoute: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Group header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Arrow button toggles the group (separate clickable so action icons aren't blocked)
                IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Make the title area toggle the group as well (but keep action icons free)
                Column(modifier = Modifier.weight(1f).clickable(onClick = onToggle)) {
                    Text(
                        text = groupName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Count badge
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ) {
                    Text(
                        text = "${markers.size}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Create route button (now reliably clickable)
                if (markers.size >= 2) {
                    IconButton(onClick = { onCreateRoute() }) {
                        Icon(
                            Icons.Default.AddRoad,
                            contentDescription = "Create route from group",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // Expandable marker list
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp, end = 12.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    markers.forEach { marker ->
                        MarkerListItem(
                            marker = marker,
                            onClick = { onMarkerClick(marker) },
                            onDelete = { onDeleteMarker(marker.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual marker in list
 */
@Composable
fun MarkerListItem(
    marker: LocationEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = marker.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${String.format("%.4f", marker.latitude)}, ${String.format("%.4f", marker.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Routes list
 */
@Composable
fun RoutesList(
    routes: List<RouteEntity>,
    onRouteClick: (RouteEntity) -> Unit,
    onDeleteRoute: (Int) -> Unit,
    onCreateRoute: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Create new route button
        Button(
            onClick = onCreateRoute,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create New Route")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (routes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No routes yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(routes, key = { it.id }) { route ->
                    RouteListItem(
                        route = route,
                        onClick = { onRouteClick(route) },
                        onDelete = { onDeleteRoute(route.id) }
                    )
                }
            }
        }
    }
}

/**
 * Individual route in list
 */
@Composable
fun RouteListItem(
    route: RouteEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Route icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Route,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = route.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (route.description.isNotEmpty()) {
                    Text(
                        text = route.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Marker details content (replaces the separate MapMarkerPopup)
 */
@Composable
fun MarkerDetailsContent(
    location: LocationEntity,
    runways: List<RunwayEntity>,
    onClose: () -> Unit,
    onSetRoute: (LocationEntity) -> Unit = {},
    onEdit: (LocationEntity) -> Unit = {},
    onDelete: (Int) -> Unit = {}
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // Header with location name and coordinates
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = location.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Info grid (two columns) — expanded to include tactical/admin metadata, source and tags
        val infoItems = remember(location, runways) {
            mutableListOf<Pair<String, String>>().apply {
                // Basic identifiers
                location.markerType?.takeIf { it.isNotEmpty() }?.let { add("Type" to it.replace('_', ' ')) }
                location.icao?.takeIf { it.isNotEmpty() }?.let { add("ICAO" to it) }
                location.iata?.takeIf { it.isNotEmpty() }?.let { add("IATA" to it) }

                // Geography / admin
                location.country?.takeIf { it.isNotEmpty() }?.let { add("Country" to it) }
                location.region?.takeIf { it.isNotEmpty() }?.let { add("Region" to it) }
                location.timezone?.takeIf { it.isNotEmpty() }?.let { add("Timezone" to it) }

                // Elevation
                location.elevationM?.let { add("Elevation" to "${it} m") }

                // Tactical / unit fields (if present)
                location.unitType?.takeIf { it.isNotEmpty() }?.let { add("Unit" to it) }
                location.threatLevel?.let { add("Threat" to it.toString()) }
                location.strength?.let { add("Strength" to it.toString()) }

                // Source and verification
                location.source?.takeIf { it.isNotEmpty() }?.let { add("Source" to it) }
                location.verified?.let { add("Verified" to if (it == 1) "yes" else "no") }
                location.lastVerifiedAt?.takeIf { it.isNotEmpty() }?.let { add("Verified at" to it) }

                // Tags (try JSON array, fallback to comma-separated string)
                location.tags?.takeIf { it.isNotEmpty() }?.let { rawTags ->
                    val parsed = try {
                        val arr = org.json.JSONArray(rawTags)
                        (0 until arr.length()).map { i -> arr.optString(i) }.filter { it.isNotEmpty() }
                    } catch (_: Exception) {
                        rawTags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    }
                    if (parsed.isNotEmpty()) add("Tags" to parsed.joinToString(", "))
                }

                // Runways count if provided by the caller
                if (runways.isNotEmpty()) add("Runways" to runways.size.toString())
            }
        }

        if (infoItems.isNotEmpty()) {
            Column {
                infoItems.chunked(2).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (k, v) ->
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (location.description.isNotEmpty()) {
            Text(text = location.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Frequencies (parsed JSON -> chips, fallback to raw text)
        val freqs = remember(location.frequencies) {
            try {
                val f = location.frequencies?.trim()
                if (f != null && f.startsWith("{")) {
                    val obj = JSONObject(f)
                    val keys = obj.keys().asSequence().toList()
                    keys.map { k -> k to obj.optString(k) }
                } else emptyList()
            } catch (_: Exception) {
                emptyList<Pair<String, String>>()
            }
        }

        if (freqs.isNotEmpty() || (!location.frequencies.isNullOrEmpty())) {
            Text(text = "Frequencies", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            if (freqs.isNotEmpty()) {
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    freqs.forEach { (k, v) ->
                        AssistChip(onClick = { /*noop*/ }, label = { Text(text = "$k: $v") })
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(text = location.frequencies ?: "", modifier = Modifier.padding(8.dp), fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Runways - compact cards list
        if (runways.isNotEmpty()) {
            Text(text = "Runways", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                runways.forEach { rw ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = rw.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                val length = rw.lengthM?.toString() ?: rw.lengthFt?.toString() ?: "?"
                                Text(text = "${length} m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(text = rw.surface ?: "unknown", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "HDG: ${rw.headingDeg?.let { String.format("%.0f°", it) } ?: "n/a"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                if (!rw.ilsFrequency.isNullOrEmpty()) {
                                    AssistChip(onClick = { /*noop*/ }, label = { Text(text = "ILS ${rw.ilsFrequency}") })
                                }

                                if (rw.hasLighting == 1) {
                                    AssistChip(onClick = { /*noop*/ }, label = { Text(text = "Lighting") })
                                }
                            }

                            if (!rw.notes.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = rw.notes ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Action buttons
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Set Route button (prominent)
            Button(
                onClick = { onSetRoute(location) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Flight, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Set Route")
            }
            
            // Edit and Delete buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Edit button
                OutlinedButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit")
                }
                
                // Delete button
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete")
                }
            }
            
            // Back button
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back to Markers")
            }
        }
    }
    
    // Edit dialog
    if (showEditDialog) {
        LocationEditDialog(
            location = location,
            onDismiss = { showEditDialog = false },
            onSave = { updatedLocation ->
                onEdit(updatedLocation)
                showEditDialog = false
            }
        )
    }
    
    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Marker") },
            text = { Text("Are you sure you want to delete '${location.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(location.id)
                        showDeleteConfirm = false
                        onClose()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Comprehensive location/marker edit dialog with all database fields
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationEditDialog(
    location: LocationEntity,
    onDismiss: () -> Unit,
    onSave: (LocationEntity) -> Unit
) {
    var name by remember { mutableStateOf(location.name) }
    var latitude by remember { mutableStateOf(location.latitude.toString()) }
    var longitude by remember { mutableStateOf(location.longitude.toString()) }
    var markerType by remember { mutableStateOf(location.markerType) }
    var coalition by remember { mutableStateOf(location.coalition ?: "") }
    var icon by remember { mutableStateOf(location.icon) }
    var description by remember { mutableStateOf(location.description) }
    
    // NATO symbol fields
    var symbolSet by remember { mutableStateOf(location.symbolSet) }
    var symbolEntity by remember { mutableStateOf(location.symbolEntity) }
    var symbolSize by remember { mutableStateOf(location.symbolSize) }
    var symbolAffiliation by remember { mutableStateOf(location.symbolAffiliation) }
    var symbolColor by remember { mutableStateOf(location.symbolColor) }
    
    // Airport fields
    var icao by remember { mutableStateOf(location.icao ?: "") }
    var iata by remember { mutableStateOf(location.iata ?: "") }
    var elevationM by remember { mutableStateOf(location.elevationM?.toString() ?: "") }
    var frequencies by remember { mutableStateOf(location.frequencies ?: "") }
    
    // Tactical fields
    var threatLevel by remember { mutableStateOf(location.threatLevel?.toString() ?: "") }
    var unitType by remember { mutableStateOf(location.unitType ?: "") }
    var strength by remember { mutableStateOf(location.strength?.toString() ?: "") }
    
    // Geography & admin
    var country by remember { mutableStateOf(location.country ?: "") }
    var region by remember { mutableStateOf(location.region ?: "") }
    var timezone by remember { mutableStateOf(location.timezone ?: "") }
    
    // Source & verification
    var source by remember { mutableStateOf(location.source ?: "") }
    var tags by remember { mutableStateOf(location.tags ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Marker",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Basic info section
                    Text("Basic Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = latitude,
                            onValueChange = { latitude = it },
                            label = { Text("Latitude *") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = longitude,
                            onValueChange = { longitude = it },
                            label = { Text("Longitude *") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = markerType,
                            onValueChange = { markerType = it },
                            label = { Text("Type *") },
                            placeholder = { Text("airport, waypoint, tactical_military, etc.") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = coalition,
                            onValueChange = { coalition = it },
                            label = { Text("Coalition") },
                            placeholder = { Text("blufor, opfor, neutral") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                    
                    HorizontalDivider()
                    
                    // NATO Symbol section
                    Text("NATO Military Symbol", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    OutlinedTextField(
                        value = symbolEntity,
                        onValueChange = { symbolEntity = it },
                        label = { Text("Symbol Entity") },
                        placeholder = { Text("equipment_mortar, aircraft_fighter, etc.") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = symbolSet,
                            onValueChange = { symbolSet = it },
                            label = { Text("Symbol Set") },
                            placeholder = { Text("ground_unit, equipment, etc.") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = symbolSize,
                            onValueChange = { symbolSize = it },
                            label = { Text("Size") },
                            placeholder = { Text("squad, platoon, etc.") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = symbolAffiliation,
                            onValueChange = { symbolAffiliation = it },
                            label = { Text("Affiliation") },
                            placeholder = { Text("friendly, hostile, neutral, unknown") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = symbolColor,
                            onValueChange = { symbolColor = it },
                            label = { Text("Color") },
                            placeholder = { Text("#00A8FF") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    OutlinedTextField(
                        value = icon,
                        onValueChange = { icon = it },
                        label = { Text("Icon") },
                        placeholder = { Text("ic_mapicon_...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    HorizontalDivider()
                    
                    // Airport section
                    Text("Airport Fields", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = icao,
                            onValueChange = { icao = it.uppercase() },
                            label = { Text("ICAO") },
                            placeholder = { Text("EDDF") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = iata,
                            onValueChange = { iata = it.uppercase() },
                            label = { Text("IATA") },
                            placeholder = { Text("FRA") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = elevationM,
                            onValueChange = { elevationM = it },
                            label = { Text("Elevation (m)") },
                            placeholder = { Text("100") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    OutlinedTextField(
                        value = frequencies,
                        onValueChange = { frequencies = it },
                        label = { Text("Frequencies (JSON)") },
                        placeholder = { Text("{\"tower\":\"118.5\", \"ground\":\"121.9\"}") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    
                    HorizontalDivider()
                    
                    // Tactical section
                    Text("Tactical Fields", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = unitType,
                            onValueChange = { unitType = it },
                            label = { Text("Unit Type") },
                            placeholder = { Text("Infantry, Armor, etc.") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = threatLevel,
                            onValueChange = { threatLevel = it },
                            label = { Text("Threat Level") },
                            placeholder = { Text("0-10") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = strength,
                            onValueChange = { strength = it },
                            label = { Text("Strength") },
                            placeholder = { Text("Number of units") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    HorizontalDivider()
                    
                    // Geography section
                    Text("Geography & Admin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = country,
                            onValueChange = { country = it },
                            label = { Text("Country") },
                            placeholder = { Text("Germany") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = region,
                            onValueChange = { region = it },
                            label = { Text("Region") },
                            placeholder = { Text("Hesse") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    OutlinedTextField(
                        value = timezone,
                        onValueChange = { timezone = it },
                        label = { Text("Timezone") },
                        placeholder = { Text("Europe/Berlin") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    HorizontalDivider()
                    
                    // Meta section
                    Text("Metadata", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    OutlinedTextField(
                        value = source,
                        onValueChange = { source = it },
                        label = { Text("Source") },
                        placeholder = { Text("OpenFlightMaps, user_created, etc.") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("Tags") },
                        placeholder = { Text("civil,military,public (comma-separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            // Validate and save
                            val lat = latitude.toDoubleOrNull() ?: location.latitude
                            val lon = longitude.toDoubleOrNull() ?: location.longitude
                            
                            val updatedLocation = location.copy(
                                name = name.takeIf { it.isNotBlank() } ?: location.name,
                                latitude = lat,
                                longitude = lon,
                                markerType = markerType.takeIf { it.isNotBlank() } ?: location.markerType,
                                coalition = coalition.takeIf { it.isNotBlank() },
                                icon = icon,
                                description = description,
                                symbolSet = symbolSet,
                                symbolEntity = symbolEntity,
                                symbolSize = symbolSize,
                                symbolAffiliation = symbolAffiliation,
                                symbolColor = symbolColor,
                                icao = icao.takeIf { it.isNotBlank() },
                                iata = iata.takeIf { it.isNotBlank() },
                                elevationM = elevationM.toDoubleOrNull(),
                                frequencies = frequencies.takeIf { it.isNotBlank() },
                                threatLevel = threatLevel.toIntOrNull(),
                                unitType = unitType.takeIf { it.isNotBlank() },
                                strength = strength.toIntOrNull(),
                                country = country.takeIf { it.isNotBlank() },
                                region = region.takeIf { it.isNotBlank() },
                                timezone = timezone.takeIf { it.isNotBlank() },
                                source = source.takeIf { it.isNotBlank() },
                                tags = tags.takeIf { it.isNotBlank() }
                            )
                            
                            onSave(updatedLocation)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = name.isNotBlank() && latitude.toDoubleOrNull() != null && longitude.toDoubleOrNull() != null
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
