package com.example.checklist_interactive.ui.maps.marker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.example.checklist_interactive.R
import com.example.checklist_interactive.ui.common.ModalBottomSheetImmersiveMode
import kotlinx.coroutines.isActive
import com.example.checklist_interactive.ui.maps.navigation.MarkerDetailsContent
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
    
    private val _markerGroups = MutableStateFlow<Map<Int, List<LocationEntity>>>(emptyMap())
    val markerGroups: StateFlow<Map<Int, List<LocationEntity>>> = _markerGroups.asStateFlow()
    
    private val _expandedGroups = MutableStateFlow<Set<Int>>(emptySet())
    val expandedGroups: StateFlow<Set<Int>> = _expandedGroups.asStateFlow()
    
    private val _visibleRouteIds = MutableStateFlow<Set<Int>>(emptySet())
    val visibleRouteIds: StateFlow<Set<Int>> = _visibleRouteIds.asStateFlow()
    
    private val _availableMaps = MutableStateFlow<List<String>>(emptyList())
    val availableMaps: StateFlow<List<String>> = _availableMaps.asStateFlow()
    
    init {
        loadData()
        loadAvailableMaps()
    }
    
    private fun loadAvailableMaps() {
        viewModelScope.launch {
            locationRepository.getAllMaps().collect { maps ->
                _availableMaps.value = maps.filter { !it.isNullOrBlank() }
            }
        }
    }
    
    fun toggleRouteVisibility(routeId: Int) {
        _visibleRouteIds.value = if (_visibleRouteIds.value.contains(routeId)) {
            _visibleRouteIds.value - routeId
        } else {
            _visibleRouteIds.value + routeId
        }
    }

    fun setVisibleRoutes(routeIds: Set<Int>) {
        _visibleRouteIds.value = routeIds
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
        val groups = mutableMapOf<Int, MutableList<LocationEntity>>()
        
        markers.forEach { marker ->
            // Group by marker type
            val groupKey = when {
                marker.markerType == "airport" -> R.string.map_group_airports
                marker.markerType == "waypoint" -> R.string.map_group_waypoints
                marker.markerType.startsWith("tactical_blufor") -> R.string.map_group_blufor_units
                marker.markerType.startsWith("tactical_opfor") -> R.string.map_group_opfor_units
                marker.markerType.startsWith("tactical_neutral") -> R.string.map_group_neutral_units
                marker.markerType == "target" -> R.string.map_group_targets
                marker.markerType == "threat" -> R.string.map_group_threats
                else -> R.string.map_group_other
            }
            
            groups.getOrPut(groupKey) { mutableListOf() }.add(marker)
        }
        // Keep keys as resource IDs (Int). UI will resolve to human-readable strings.
        _markerGroups.value = groups
    }
    
    fun toggleGroup(groupKey: Int) {
        _expandedGroups.value = if (_expandedGroups.value.contains(groupKey)) {
            _expandedGroups.value - groupKey
        } else {
            _expandedGroups.value + groupKey
        }
    }
    
    fun createRouteFromGroup(groupKey: Int, groupLabel: String, description: String = "", onSuccess: () -> Unit) {
        viewModelScope.launch {
            val markers = _markerGroups.value[groupKey] ?: return@launch
            if (markers.size < 2) return@launch
            
            val route = RouteEntity(
                name = groupLabel.replace(Regex("^[^a-zA-Z]+"), ""),
                description = description,
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

    fun updateRoute(route: RouteEntity) {
        viewModelScope.launch {
            routeRepository.updateRoute(route)
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
    onCenter: (LocationEntity) -> Unit = {},
    selectedMarker: LocationEntity? = null,
    selectedRunways: List<RunwayEntity> = emptyList(),
    onSetActiveRoute: (LocationEntity) -> Unit,
    onEditRouteWaypoints: (Int) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val markerGroups by viewModel.markerGroups.collectAsState()
    val expandedGroups by viewModel.expandedGroups.collectAsState()
    val allRoutes by viewModel.allRoutes.collectAsState()
    val visibleRouteIds by viewModel.visibleRouteIds.collectAsState()
    // New tab order: 0=Details, 1=Markers (default), 2=Routes
    var selectedTab by remember { mutableStateOf(if (selectedMarker != null) 0 else 1) }
    val view = LocalView.current
    // Live search text for filtering markers
    var markerSearch by rememberSaveable { mutableStateOf("") }
    // Map filter for filtering by DCS map
    var selectedMapFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val availableMaps by viewModel.availableMaps.collectAsState()

    // Persisted sheet fraction + opacity like DataPadPopup
    val prefs = context.getSharedPreferences("map_objects_prefs", android.content.Context.MODE_PRIVATE)
    val KEY_SHEET_FRACTION = "map_objects_sheet_fraction"
    val savedFraction = prefs.getFloat(KEY_SHEET_FRACTION, 0.6f)
    val sheetMin = 0.25f
    val sheetMax = 0.95f
    var sheetFraction by rememberSaveable { mutableStateOf(savedFraction.coerceIn(sheetMin, sheetMax)) }

    val KEY_SHEET_OPACITY = "map_objects_sheet_opacity"
    val savedOpacity = prefs.getFloat(KEY_SHEET_OPACITY, 1.0f)
    var sheetOpacity by rememberSaveable { mutableStateOf(savedOpacity.coerceIn(0.25f, 1.0f)) }
    var showOpacitySlider by remember { mutableStateOf(false) }

    // Persist opacity when changed
    LaunchedEffect(sheetOpacity) {
        prefs.edit().putFloat(KEY_SHEET_OPACITY, sheetOpacity).apply()
    }

    // Update tab when selectedMarker changes -> show Details when a marker is selected
    LaunchedEffect(selectedMarker) {
        if (selectedMarker != null) {
            selectedTab = 0
        }
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val sheetHeightDp = (configuration.screenHeightDp.toFloat() * sheetFraction).dp

    // Apply immersive mode (consolidated helper)
    ModalBottomSheetImmersiveMode(isVisible = sheetState.isVisible)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = sheetOpacity)
    ) {

        Box(modifier = Modifier
            .fillMaxWidth()
            .height(sheetHeightDp)
            .padding(horizontal = 16.dp)
        ) {
            // Drag handle at top (drag vertically to resize and swipe down from the handle to dismiss)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(Unit) {
                        var dragAccum = 0f
                        detectDragGestures(
                            onDragStart = { dragAccum = 0f },
                            onDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset ->
                                // accumulate vertical displacement (positive = downward)
                                dragAccum += dragAmount.y
                                val screenPx = with(density) { configuration.screenHeightDp.dp.toPx() }
                                val fracDelta = dragAmount.y / screenPx
                                sheetFraction = (sheetFraction - fracDelta).coerceIn(sheetMin, sheetMax)
                            },
                            onDragEnd = {
                                // persist saved fraction
                                prefs.edit().putFloat(KEY_SHEET_FRACTION, sheetFraction).apply()
                                // If user swiped down sufficiently on the handle, dismiss the sheet
                                val threshold = with(density) { 64.dp.toPx() } // about 64dp downward to dismiss
                                if (dragAccum > threshold) {
                                    onDismiss()
                                }
                                dragAccum = 0f
                            },
                            onDragCancel = {
                                dragAccum = 0f
                            }
                        )
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(width = 64.dp, height = 6.dp)
                        .background(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small)
                )
            }

            // Main content
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.map_objects_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = { showOpacitySlider = !showOpacitySlider }, modifier = Modifier.padding(end = 8.dp)) {
                            Text(text = "${(sheetOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    }
                }

                AnimatedVisibility(visible = showOpacitySlider, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
                        Text(text = stringResource(R.string.map_objects_transparency_label, (sheetOpacity * 100).toInt()), style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = sheetOpacity,
                            onValueChange = { sheetOpacity = it },
                            valueRange = 0.25f..1.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            
            // Tabs (reordered: Details | Markers | Routes)
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_map_details)) },
                    enabled = selectedMarker != null
                )

                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.tab_map_markers_count, markerGroups.values.sumOf { it.size })) }
                )

                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.tab_map_routes_count, allRoutes.size)) }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Content
            val autoDesc = stringResource(R.string.map_route_auto_generated_description)

            when (selectedTab) {
                // New mapping: 0=Details, 1=Markers, 2=Routes
                0 -> {
                    if (selectedMarker != null) {
                        MarkerDetailsContent(
                            location = selectedMarker,
                            runways = selectedRunways,
                            onClose = { selectedTab = 1 },
                            onSetRoute = { location ->
                                android.util.Log.d("MarkerRouteManagement", "📡 onSetRoute received: id=${location.id}, source=${location.source}, name=${location.name}")
                                android.util.Log.d("MarkerRouteManagement", "🎯 About to call onSetActiveRoute...")
                                onSetActiveRoute(location)
                                android.util.Log.d("MarkerRouteManagement", "✅ onSetActiveRoute call completed")
                            },
                            onEdit = { updatedLocation ->
                                viewModel.updateMarker(updatedLocation)
                            },
                            onDelete = { markerId ->
                                viewModel.deleteMarker(markerId)
                                selectedTab = 1
                            },
                            onCenter = { loc ->
                                // delegate to parent sheet's onCenter behavior (only center the map)
                                onCenter(loc)
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.map_objects_no_marker_selected),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                1 -> {
                    Column {
                        OutlinedTextField(
                            value = markerSearch,
                            onValueChange = { markerSearch = it },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (markerSearch.isNotEmpty()) {
                                    IconButton(onClick = { markerSearch = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = null)
                                    }
                                }
                            },
                            placeholder = { Text(stringResource(R.string.map_search_markers_placeholder)) },
                            singleLine = true
                        )

                        // Map filter chips
                        if (availableMaps.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // "All Maps" chip
                                FilterChip(
                                    selected = selectedMapFilter == null,
                                    onClick = { selectedMapFilter = null },
                                    label = { Text(stringResource(R.string.map_filter_all_maps)) }
                                )
                                // Individual map chips
                                availableMaps.forEach { map ->
                                    FilterChip(
                                        selected = selectedMapFilter == map,
                                        onClick = { selectedMapFilter = if (selectedMapFilter == map) null else map },
                                        label = { Text(map) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        MarkerGroupsList(
                            groups = markerGroups,
                            expandedGroups = expandedGroups,
                            searchText = markerSearch,
                            selectedMap = selectedMapFilter,
                            onToggleGroup = { viewModel.toggleGroup(it) },
                            onMarkerClick = { marker ->
                                onMarkerClick(marker)
                                selectedTab = 0
                            },
                            onDeleteMarker = { viewModel.deleteMarker(it) },
                            onCreateRouteFromGroup = { groupKey, groupLabel ->
                                viewModel.createRouteFromGroup(
                                    groupKey,
                                    groupLabel,
                                    autoDesc
                                ) {
                                    selectedTab = 2
                                }
                            }
                        )
                    }
                }
                2 -> RoutesList(
                    routes = allRoutes,
                    visibleRouteIds = visibleRouteIds,
                    onRouteClick = onRouteClick,
                    onDeleteRoute = { viewModel.deleteRoute(it) },
                    onToggleVisibility = { viewModel.toggleRouteVisibility(it) },
                    onCreateRoute = onCreateRoute,
                    onEditRoute = { viewModel.updateRoute(it) },
                    onEditWaypoints = onEditRouteWaypoints
                )
            }
        }
    }
    }
}

/**
 * Markers organized by groups
 */
@Composable
fun MarkerGroupsList(
    groups: Map<Int, List<LocationEntity>>,
    expandedGroups: Set<Int>,
    searchText: String = "",
    selectedMap: String? = null,
    onToggleGroup: (Int) -> Unit,
    onMarkerClick: (LocationEntity) -> Unit,
    onDeleteMarker: (Int) -> Unit,
    onCreateRouteFromGroup: (Int, String) -> Unit
) {
    // Filter markers by search text and map selection
    val filteredGroups = groups.mapValues { (_, list) ->
        list.filter { marker ->
            // Map filter
            val matchesMap = selectedMap == null || marker.map == selectedMap
            
            // Search filter
            val matchesSearch = if (searchText.isBlank()) {
                true
            } else {
                val q = searchText.trim().lowercase()
                marker.name.lowercase().contains(q) ||
                (marker.icao?.lowercase()?.contains(q) ?: false) ||
                (marker.iata?.lowercase()?.contains(q) ?: false) ||
                (marker.description?.lowercase()?.contains(q) ?: false) ||
                (marker.tags?.lowercase()?.contains(q) ?: false)
            }
            
            matchesMap && matchesSearch
        }
    }.filterValues { it.isNotEmpty() }

    if (filteredGroups.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.map_objects_no_markers_yet),
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
        filteredGroups.forEach { (groupKey, markers) ->
            item(key = groupKey) {
                val groupLabel = stringResource(groupKey)
                MarkerGroupItem(
                    groupKey = groupKey,
                    groupLabel = groupLabel,
                    markers = markers,
                    // Auto-expand groups when a search is active so all matches are visible
                    isExpanded = if (searchText.isNotBlank()) true else expandedGroups.contains(groupKey),
                    onToggle = { onToggleGroup(groupKey) },
                    onMarkerClick = onMarkerClick,
                    onDeleteMarker = onDeleteMarker,
                    onCreateRoute = { onCreateRouteFromGroup(groupKey, groupLabel) }
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
    groupKey: Int,
    groupLabel: String,
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
                        contentDescription = if (isExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Make the title area toggle the group as well (but keep action icons free)
                Column(modifier = Modifier.weight(1f).clickable(onClick = onToggle)) {
                    Text(
                        text = groupLabel,
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
                            contentDescription = stringResource(R.string.map_create_route_from_group),
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
                    contentDescription = stringResource(R.string.action_delete),
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
    visibleRouteIds: Set<Int>,
    onRouteClick: (RouteEntity) -> Unit,
    onDeleteRoute: (Int) -> Unit,
    onToggleVisibility: (Int) -> Unit,
    onCreateRoute: () -> Unit,
    onEditRoute: (RouteEntity) -> Unit = {},
    onEditWaypoints: (Int) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Create new route button
        Button(
            onClick = onCreateRoute,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.map_create_new_route))
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (routes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.map_objects_no_routes_yet),
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
                        isVisible = visibleRouteIds.contains(route.id),
                        onClick = { onRouteClick(route) },
                        onDelete = { onDeleteRoute(route.id) },
                        onToggleVisibility = { onToggleVisibility(route.id) },
                        onEdit = { onEditRoute(it) },
                        onEditWaypoints = { onEditWaypoints(it) }
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
    isVisible: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleVisibility: () -> Unit,
    onEdit: (RouteEntity) -> Unit = {},
    onEditWaypoints: (Int) -> Unit = {}
) {
    var showEditDialog by remember { mutableStateOf(false) }

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

            // Visibility toggle
            Switch(
                checked = isVisible,
                onCheckedChange = { onToggleVisibility() },
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Color button (shows current color and opens full edit dialog)
            IconButton(onClick = { showEditDialog = true }) {
                val colorPreview = try {
                    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(route.color))
                } catch (e: Exception) {
                    androidx.compose.ui.graphics.Color.Gray
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colorPreview)
                )
            }

            // Edit waypoints button
            IconButton(onClick = { onEditWaypoints(route.id) }) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.map_edit_waypoints),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // Edit dialog for route properties (name/description/color)
    if (showEditDialog) {
        RouteEditDialog(
            route = route,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                showEditDialog = false
                // propagate updated route to caller (ViewModel will persist)
                onEdit(updated)
            }
        )
    }
}

// MarkerDetailsContent has been moved to SingleMarkerNavigation.kt
// Import and use from there

/**
 * Comprehensive location/marker edit dialog with all database fields
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationEditDialog(
    location: LocationEntity,
    runways: List<RunwayEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (LocationEntity) -> Unit,
    onSaveRunways: suspend (List<RunwayEntity>) -> Unit = {}
) {
    var name by remember { mutableStateOf(location.name) }
    var latitude by remember { mutableStateOf(location.latitude.toString()) }
    var longitude by remember { mutableStateOf(location.longitude.toString()) }
    var height by remember { mutableStateOf(location.elevationM?.toString() ?: "") }
    
    // Extract heading from metadata JSON (UDP tactical data)
    val initialHeading = remember(location.metadata) {
        location.metadata?.let { meta ->
            try {
                val obj = JSONObject(meta)
                val hdg = obj.optDouble("heading", Double.NaN)
                if (!hdg.isNaN()) hdg.toString() else ""
            } catch (_: Exception) {
                ""
            }
        } ?: ""
    }
    var heading by remember { mutableStateOf(initialHeading) }

    // Derived validation for numeric fields
    val latValid = latitude.toDoubleOrNull() != null
    val lonValid = longitude.toDoubleOrNull() != null
    val heightValid = height.isBlank() || height.toDoubleOrNull() != null
    val headingValid = heading.isBlank() || heading.toDoubleOrNull()?.let { it in 0.0..360.0 } ?: false
    var markerType by remember { mutableStateOf(location.markerType) }
    var coalition by remember { mutableStateOf(location.coalition ?: "") }
    var icon by remember { mutableStateOf(location.icon) }
    var description by remember { mutableStateOf(location.description) }

    // Editable runway state
    val editRunways = remember { mutableStateListOf<RunwayEntity>().apply { addAll(runways) } }
    val scope = rememberCoroutineScope()

    // Overall runways validity (used to enable Save)
    val runwaysValid by remember(editRunways) {
        derivedStateOf {
            editRunways.all { rw ->
                val lenOk = rw.lengthM == null || rw.lengthM >= 0
                val widOk = rw.widthM == null || rw.widthM >= 0
                val hdgOk = rw.headingDeg == null || (rw.headingDeg.isFinite() && rw.headingDeg >= 0.0 && rw.headingDeg < 360.0)
                val ilsOk = rw.ilsFrequency.isNullOrBlank() || (rw.ilsFrequency.toDoubleOrNull()?.let { it in 108.0..137.0 } ?: false)
                lenOk && widOk && hdgOk && ilsOk
            }
        }
    }    
    // NATO symbol fields
    var symbolSet by remember { mutableStateOf(location.symbolSet) }
    var symbolEntity by remember { mutableStateOf(location.symbolEntity) }
    var symbolSize by remember { mutableStateOf(location.symbolSize) }
    var symbolAffiliation by remember { mutableStateOf(location.symbolAffiliation) }
    var symbolColor by remember { mutableStateOf(location.symbolColor) }
    
    // Static marker flag
    var isStatic by remember { mutableStateOf(location.isStatic == 1) }
    // Show/hide airport fields; auto-expands when marker becomes static
    var showAirportFields by remember { mutableStateOf(isStatic) }
    LaunchedEffect(isStatic) { showAirportFields = isStatic }
    
    // Airport fields
    var icao by remember { mutableStateOf(location.icao ?: "") }
    var iata by remember { mutableStateOf(location.iata ?: "") }
    var elevationM by remember { mutableStateOf(location.elevationM?.toString() ?: "") }
    var frequencies by remember { mutableStateOf(location.frequencies ?: "") }
    
    // Tactical fields
    var threatLevel by remember { mutableStateOf(location.threatLevel?.toString() ?: "") }
    var unitType by remember { mutableStateOf(location.unitType ?: "") }
    var strength by remember { mutableStateOf(location.strength?.toString() ?: "") }

    // validation for numeric tactical fields
    val threatLevelInt = threatLevel.toIntOrNull()
    val threatLevelError = threatLevel.isNotBlank() && (threatLevelInt == null || threatLevelInt !in 0..10)
    val strengthInt = strength.toIntOrNull()
    val strengthError = strength.isNotBlank() && (strengthInt == null || strengthInt < 0)
    
    // Geography & admin
    var country by remember { mutableStateOf(location.country ?: "") }
    var region by remember { mutableStateOf(location.region ?: "") }
    var timezone by remember { mutableStateOf(location.timezone ?: "") }
    
    // Source & verification
    var source by remember { mutableStateOf(location.source ?: "") }
    var tags by remember { mutableStateOf(location.tags ?: "") }
    
    var showSymbolPicker by remember { mutableStateOf(false) }
    
    // Show symbol picker if requested
    if (showSymbolPicker) {
        MilitarySymbolPickerDialog(
            onDismiss = { showSymbolPicker = false },
            onSymbolSelected = { symbol, affiliation ->
                // Update icon and symbol fields
                icon = "ic_mapicon_${symbol.id}"
                symbolEntity = symbol.symbolEntity
                symbolSet = symbol.symbolSet
                symbolAffiliation = affiliation.name.lowercase()
                symbolColor = String.format("#%06X", 0xFFFFFF and affiliation.color.hashCode())
                showSymbolPicker = false
            }
        )
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.95f)
                .widthIn(min = 700.dp, max = 1100.dp),
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
                        text = stringResource(R.string.map_edit_marker_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, stringResource(R.string.action_close))
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
                    Text(stringResource(R.string.map_basic_information), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.map_name_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = latitude,
                            onValueChange = { latitude = it },
                            label = { Text(stringResource(R.string.map_latitude_label)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            value = longitude,
                            onValueChange = { longitude = it },
                            label = { Text(stringResource(R.string.map_longitude_label)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            value = height,
                            onValueChange = { height = it },
                            label = { Text("Height (m)") },
                            placeholder = { Text("0") },
                            modifier = Modifier.weight(0.8f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = !heightValid
                        )
                        OutlinedTextField(
                            value = heading,
                            onValueChange = { heading = it },
                            label = { Text(stringResource(R.string.heading_label)) },
                            placeholder = { Text("0-360") },
                            modifier = Modifier.weight(0.8f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = !headingValid
                        )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = markerType,
                            onValueChange = { markerType = it },
                            label = { Text(stringResource(R.string.map_type_label)) },
                            placeholder = { Text(stringResource(R.string.map_type_placeholder)) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = coalition,
                            onValueChange = { coalition = it },
                            label = { Text(stringResource(R.string.map_coalition_label)) },
                            placeholder = { Text(stringResource(R.string.map_coalition_placeholder)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.map_description_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                    
                    HorizontalDivider()
                    
                    // NATO Symbol section
                    Text(stringResource(R.string.map_nato_military_symbol_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    OutlinedTextField(
                        value = symbolEntity,
                        onValueChange = { symbolEntity = it },
                        label = { Text(stringResource(R.string.map_symbol_entity_label)) },
                        placeholder = { Text(stringResource(R.string.map_symbol_entity_placeholder)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = symbolSet,
                            onValueChange = { symbolSet = it },
                            label = { Text(stringResource(R.string.map_symbol_set_label)) },
                            placeholder = { Text(stringResource(R.string.map_symbol_set_placeholder)) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = symbolSize,
                            onValueChange = { symbolSize = it },
                            label = { Text(stringResource(R.string.map_size_label)) },
                            placeholder = { Text(stringResource(R.string.map_size_placeholder)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = symbolAffiliation,
                            onValueChange = { symbolAffiliation = it },
                            label = { Text(stringResource(R.string.map_affiliation_label)) },
                            placeholder = { Text(stringResource(R.string.map_affiliation_placeholder)) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = symbolColor,
                            onValueChange = { symbolColor = it },
                            label = { Text(stringResource(R.string.map_color_label)) },
                            placeholder = { Text(stringResource(R.string.map_color_placeholder)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // Icon selection with visual picker
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSymbolPicker = true },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.map_icon_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (icon.isNotEmpty() && icon != "default") icon else stringResource(R.string.map_icon_select_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (icon.isNotEmpty() && icon != "default") 
                                        MaterialTheme.colorScheme.onSurface 
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.map_icon_label),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    // Static marker checkbox
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isStatic,
                            onCheckedChange = { isStatic = it }
                        )
                        Text(
                            text = stringResource(R.string.map_static_marker_checkbox),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    
                    HorizontalDivider()

                    // Airport section (hidden by default unless marker is static)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.map_airport_fields_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { showAirportFields = !showAirportFields }) {
                            Icon(
                                imageVector = if (showAirportFields) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showAirportFields) stringResource(R.string.map_collapse_airport_fields_cd) else stringResource(R.string.map_expand_airport_fields_cd)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showAirportFields,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = icao,
                                    onValueChange = { icao = it.uppercase() },
                                    label = { Text(stringResource(R.string.map_icao_label)) },
                                    placeholder = { Text(stringResource(R.string.map_icao_placeholder)) },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = iata,
                                    onValueChange = { iata = it.uppercase() },
                                    label = { Text(stringResource(R.string.map_iata_label)) },
                                    placeholder = { Text(stringResource(R.string.map_iata_placeholder)) },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = elevationM,
                                    onValueChange = { elevationM = it.filter { ch -> ch.isDigit() } },
                                    label = { Text(stringResource(R.string.map_elevation_m_label)) },
                                    placeholder = { Text(stringResource(R.string.map_elevation_m_placeholder)) },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }

                            OutlinedTextField(
                                value = frequencies,
                                onValueChange = { frequencies = it },
                                label = { Text(stringResource(R.string.map_frequencies_json_label)) },
                                placeholder = { Text(stringResource(R.string.map_frequencies_json_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Runways (editable)
                            Text(stringResource(R.string.map_runways_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                editRunways.forEachIndexed { idx, rw ->
                                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                OutlinedTextField(
                                                    value = rw.name,
                                                    onValueChange = { v -> editRunways[idx] = rw.copy(name = v) },
                                                    label = { Text(stringResource(R.string.map_runway_name_label)) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(onClick = { editRunways.removeAt(idx) }) {
                                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.map_remove_runway_cd))
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = rw.lengthM?.toString() ?: "",
                                                    onValueChange = { v -> editRunways[idx] = rw.copy(lengthM = v.toIntOrNull()) },
                                                    label = { Text(stringResource(R.string.map_runway_length_m_label)) },
                                                    modifier = Modifier.weight(1f),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                                )

                                                OutlinedTextField(
                                                    value = rw.widthM?.toString() ?: "",
                                                    onValueChange = { v -> editRunways[idx] = rw.copy(widthM = v.toIntOrNull()) },
                                                    label = { Text(stringResource(R.string.map_runway_width_m_label)) },
                                                    modifier = Modifier.weight(1f),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = rw.surface ?: "",
                                                    onValueChange = { v -> editRunways[idx] = rw.copy(surface = v) },
                                                    label = { Text(stringResource(R.string.map_runway_surface_label)) },
                                                    modifier = Modifier.weight(1f)
                                                )

                                                Column(modifier = Modifier.weight(1f)) {
                                                    OutlinedTextField(
                                                        value = rw.ilsFrequency ?: "",
                                                        onValueChange = { v ->
                                                            // allow digits and a single dot, limit to two decimals
                                                            var filtered = v.filter { ch -> ch.isDigit() || ch == '.' }
                                                            val parts = filtered.split('.')
                                                            filtered = if (parts.size > 1) parts[0] + "." + parts[1].take(2) else parts[0]
                                                            editRunways[idx] = rw.copy(ilsFrequency = filtered)
                                                        },
                                                        label = { Text(stringResource(R.string.map_runway_ils_mhz_label)) },
                                                        placeholder = { Text(stringResource(R.string.map_frequency_placeholder)) },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                                    )
                                                    val ilsNum = rw.ilsFrequency?.toDoubleOrNull()
                                                    if (rw.ilsFrequency.isNullOrBlank()) {
                                                        Text(text = stringResource(R.string.map_runway_ils_range_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                                    } else if (ilsNum == null) {
                                                        Text(text = stringResource(R.string.map_runway_ils_invalid_format), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                                    } else if (ilsNum < 108.0 || ilsNum > 137.0) {
                                                        Text(text = stringResource(R.string.map_runway_ils_out_of_range), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                                    } else {
                                                        Text(text = stringResource(R.string.action_ok), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    OutlinedTextField(
                                                        value = rw.headingDeg?.toString() ?: "",
                                                        onValueChange = { v ->
                                                            // allow numeric input and decimal
                                                            val filtered = v.filter { ch -> ch.isDigit() || ch == '.' }
                                                            editRunways[idx] = rw.copy(headingDeg = filtered.toDoubleOrNull())
                                                        },
                                                        label = { Text(stringResource(R.string.map_runway_heading_deg_label)) },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                                    )
                                                    val headingErr = rw.headingDeg?.let { !(it.isFinite() && it >= 0.0 && it < 360.0) } ?: false
                                                    if (headingErr) {
                                                        Text(text = stringResource(R.string.map_runway_heading_invalid_range), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                                    } else {
                                                        Text(text = stringResource(R.string.map_runway_heading_range_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                                    }
                                                }

                                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(checked = (rw.hasLighting ?: 0) == 1, onCheckedChange = { checked -> editRunways[idx] = rw.copy(hasLighting = if (checked) 1 else 0) })
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(stringResource(R.string.map_runway_lighting_checkbox))
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                                                                            OutlinedTextField(
                                                                                                value = rw.notes ?: "",
                                                                                                onValueChange = { v -> editRunways[idx] = rw.copy(notes = v) },
                                                                                                label = { Text(stringResource(R.string.map_notes_label)) },
                                                                                                modifier = Modifier.fillMaxWidth(),
                                                                                                minLines = 1
                                                                                            )                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = {
                                        // Add a new (unsaved) runway
                                        val newRw = RunwayEntity(id = 0, locationId = location.id ?: 0, name = "New RWY")
                                        editRunways.add(newRw)
                                    }) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.map_add_runway_button))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    HorizontalDivider()

                    // Tactical section
                    Text(stringResource(R.string.map_tactical_fields_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = unitType,
                            onValueChange = { unitType = it },
                            label = { Text(stringResource(R.string.map_unit_type_label)) },
                            placeholder = { Text(stringResource(R.string.map_unit_type_placeholder)) },
                            modifier = Modifier.weight(1f)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = threatLevel,
                                onValueChange = { threatLevel = it.filter { ch -> ch.isDigit() } },
                                label = { Text(stringResource(R.string.map_threat_level_label)) },
                                placeholder = { Text(stringResource(R.string.map_threat_level_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Text(text = stringResource(R.string.map_threat_level_range_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            if (threatLevelError) {
                                Text(text = stringResource(R.string.map_threat_level_invalid_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = strength,
                                onValueChange = { strength = it.filter { ch -> ch.isDigit() } },
                                label = { Text(stringResource(R.string.map_strength_label)) },
                                placeholder = { Text(stringResource(R.string.map_strength_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Text(text = stringResource(R.string.map_strength_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            if (strengthError) {
                                Text(text = stringResource(R.string.map_strength_invalid_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    
                    HorizontalDivider()
                    
                    // Geography section
                    Text(stringResource(R.string.map_geography_admin_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = country,
                            onValueChange = { country = it },
                            label = { Text(stringResource(R.string.map_country_label)) },
                            placeholder = { Text(stringResource(R.string.map_country_placeholder)) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = region,
                            onValueChange = { region = it },
                            label = { Text(stringResource(R.string.map_region_label)) },
                            placeholder = { Text(stringResource(R.string.map_region_placeholder)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    OutlinedTextField(
                        value = timezone,
                        onValueChange = { timezone = it },
                        label = { Text(stringResource(R.string.map_timezone_label)) },
                        placeholder = { Text(stringResource(R.string.map_timezone_placeholder)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    HorizontalDivider()
                    
                    // Meta section
                    Text(stringResource(R.string.map_metadata_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    OutlinedTextField(
                        value = source,
                        onValueChange = { source = it },
                        label = { Text(stringResource(R.string.map_source_label)) },
                        placeholder = { Text(stringResource(R.string.map_source_placeholder)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text(stringResource(R.string.tags_title)) },
                        placeholder = { Text(stringResource(R.string.map_tags_placeholder)) },
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
                        Text(stringResource(R.string.action_cancel))
                    }
                    
                    Button(
                        onClick = {
                            // Validate and save
                            val lat = latitude.toDoubleOrNull() ?: location.latitude
                            val lon = longitude.toDoubleOrNull() ?: location.longitude
                            
                            // Update metadata JSON with heading
                            val updatedMetadata = try {
                                val existingMeta = location.metadata?.let { JSONObject(it) } ?: JSONObject()
                                heading.toDoubleOrNull()?.let { hdg ->
                                    existingMeta.put("heading", hdg)
                                } ?: existingMeta.remove("heading")
                                existingMeta.toString()
                            } catch (_: Exception) {
                                // If parsing fails, create new metadata with just heading
                                heading.toDoubleOrNull()?.let { hdg ->
                                    JSONObject().put("heading", hdg).toString()
                                } ?: location.metadata
                            }

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
                                isStatic = if (isStatic) 1 else 0,
                                icao = icao.takeIf { it.isNotBlank() },
                                iata = iata.takeIf { it.isNotBlank() },
                                elevationM = height.toDoubleOrNull() ?: elevationM.toDoubleOrNull(),
                                frequencies = frequencies.takeIf { it.isNotBlank() },
                                threatLevel = threatLevel.toIntOrNull(),
                                unitType = unitType.takeIf { it.isNotBlank() },
                                strength = strength.toIntOrNull(),
                                country = country.takeIf { it.isNotBlank() },
                                region = region.takeIf { it.isNotBlank() },
                                timezone = timezone.takeIf { it.isNotBlank() },
                                source = source.takeIf { it.isNotBlank() },
                                tags = tags.takeIf { it.isNotBlank() },
                                metadata = updatedMetadata
                            )

                            // Save runways first (suspend) then location
                            scope.launch {
                                try {
                                    onSaveRunways(editRunways.toList())
                                } catch (e: Exception) {
                                    android.util.Log.e("LocationEditDialog", "Failed to save runways", e)
                                }
                                onSave(updatedLocation)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = name.isNotBlank() && latValid && lonValid && heightValid && headingValid && !threatLevelError && !strengthError && runwaysValid
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}

/**
 * Route edit dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteEditDialog(
    route: RouteEntity,
    onDismiss: () -> Unit,
    onSave: (RouteEntity) -> Unit
) {
    var name by remember { mutableStateOf(route.name) }
    var description by remember { mutableStateOf(route.description) }
    var color by remember { mutableStateOf(route.color) }
    var colorDropdownExpanded by remember { mutableStateOf(false) }

    // Predefined color presets
    val colorPresets = listOf(
        stringResource(R.string.map_color_blue) to "#00A8FF",
        stringResource(R.string.map_color_red) to "#FF3B30",
        stringResource(R.string.map_color_green) to "#34C759",
        stringResource(R.string.map_color_yellow) to "#FFD60A",
        stringResource(R.string.map_color_orange) to "#FF9500",
        stringResource(R.string.map_color_purple) to "#AF52DE",
        stringResource(R.string.map_color_pink) to "#FF2D55",
        stringResource(R.string.map_color_cyan) to "#32ADE6",
        stringResource(R.string.map_color_teal) to "#5AC8FA",
        stringResource(R.string.map_color_indigo) to "#5856D6",
        stringResource(R.string.map_color_mint) to "#00C7BE",
        stringResource(R.string.map_color_brown) to "#A2845E",
        stringResource(R.string.map_color_gray) to "#8E8E93",
        stringResource(R.string.map_color_white) to "#FFFFFF",
        stringResource(R.string.map_color_black) to "#000000"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_edit_route_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.map_route_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.map_description_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                // Color dropdown
                ExposedDropdownMenuBox(
                    expanded = colorDropdownExpanded,
                    onExpandedChange = { colorDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = colorPresets.find { it.second == color }?.first ?: stringResource(R.string.map_color_custom),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.map_color_label)) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Color preview
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            try {
                                                androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(color))
                                            } catch (e: Exception) {
                                                androidx.compose.ui.graphics.Color.Gray
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = colorDropdownExpanded)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = colorDropdownExpanded,
                        onDismissRequest = { colorDropdownExpanded = false }
                    ) {
                        colorPresets.forEach { (colorName, colorHex) ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(colorHex))
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(colorName)
                                    }
                                },
                                onClick = {
                                    color = colorHex
                                    colorDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedRoute = route.copy(
                        name = name.takeIf { it.isNotBlank() } ?: route.name,
                        description = description,
                        color = color.takeIf { it.isNotBlank() } ?: route.color
                    )
                    onSave(updatedRoute)
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
