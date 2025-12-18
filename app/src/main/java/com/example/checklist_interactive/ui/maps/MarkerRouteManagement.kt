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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checklist_interactive.data.tactical.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

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
}

/**
 * Marker and Route Management Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkerRouteManagementSheet(
    viewModel: MarkerRouteViewModel,
    onDismiss: () -> Unit,
    onMarkerClick: (LocationEntity) -> Unit,
    onRouteClick: (RouteEntity) -> Unit,
    onCreateRoute: () -> Unit
) {
    val markerGroups by viewModel.markerGroups.collectAsState()
    val expandedGroups by viewModel.expandedGroups.collectAsState()
    val allRoutes by viewModel.allRoutes.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    
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
            
            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Markers (${markerGroups.values.sumOf { it.size }})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Routes (${allRoutes.size})") }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Content
            when (selectedTab) {
                0 -> MarkerGroupsList(
                    groups = markerGroups,
                    expandedGroups = expandedGroups,
                    onToggleGroup = { viewModel.toggleGroup(it) },
                    onMarkerClick = onMarkerClick,
                    onDeleteMarker = { viewModel.deleteMarker(it) },
                    onCreateRouteFromGroup = { groupName ->
                        viewModel.createRouteFromGroup(groupName) {
                            selectedTab = 1 // Switch to routes tab
                        }
                    }
                )
                1 -> RoutesList(
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
                    .clickable(onClick = onToggle)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = groupName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
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
                
                // Create route button
                if (markers.size >= 2) {
                    IconButton(onClick = onCreateRoute) {
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
