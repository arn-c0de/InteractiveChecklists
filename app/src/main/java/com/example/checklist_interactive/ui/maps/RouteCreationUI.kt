package com.example.checklist_interactive.ui.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker as OsmMarker

/**
 * ViewModel for route creation and management
 */
class RouteCreationViewModel(
    private val routeRepository: RouteRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {
    
    private val _isCreatingRoute = MutableStateFlow(false)
    val isCreatingRoute: StateFlow<Boolean> = _isCreatingRoute.asStateFlow()
    
    private val _selectedWaypoints = MutableStateFlow<List<LocationEntity>>(emptyList())
    val selectedWaypoints: StateFlow<List<LocationEntity>> = _selectedWaypoints.asStateFlow()
    
    private val _currentRouteName = MutableStateFlow("")
    val currentRouteName: StateFlow<String> = _currentRouteName.asStateFlow()
    
    private val _allRoutes = MutableStateFlow<List<RouteEntity>>(emptyList())
    val allRoutes: StateFlow<List<RouteEntity>> = _allRoutes.asStateFlow()
    
    init {
        loadRoutes()
    }
    
    fun startRouteCreation() {
        _isCreatingRoute.value = true
        _selectedWaypoints.value = emptyList()
        _currentRouteName.value = "New Route ${System.currentTimeMillis() % 1000}"
    }
    
    fun cancelRouteCreation() {
        _isCreatingRoute.value = false
        _selectedWaypoints.value = emptyList()
    }
    
    fun addWaypoint(location: LocationEntity) {
        _selectedWaypoints.value = _selectedWaypoints.value + location
    }
    
    fun removeWaypoint(index: Int) {
        _selectedWaypoints.value = _selectedWaypoints.value.filterIndexed { i, _ -> i != index }
    }
    
    fun moveWaypointUp(index: Int) {
        if (index > 0) {
            val list = _selectedWaypoints.value.toMutableList()
            val temp = list[index]
            list[index] = list[index - 1]
            list[index - 1] = temp
            _selectedWaypoints.value = list
        }
    }
    
    fun moveWaypointDown(index: Int) {
        if (index < _selectedWaypoints.value.size - 1) {
            val list = _selectedWaypoints.value.toMutableList()
            val temp = list[index]
            list[index] = list[index + 1]
            list[index + 1] = temp
            _selectedWaypoints.value = list
        }
    }
    
    fun setRouteName(name: String) {
        _currentRouteName.value = name
    }
    
    fun finishRouteCreation(onSuccess: () -> Unit) {
        val waypoints = _selectedWaypoints.value
        if (waypoints.size < 2) {
            // Need at least 2 waypoints
            return
        }
        
        viewModelScope.launch {
            val route = RouteEntity(
                name = _currentRouteName.value.ifEmpty { "Route ${System.currentTimeMillis()}" },
                description = "Route with ${waypoints.size} waypoints",
                color = "#00A8FF",
                created = "",
                modified = ""
            )
            
            val locationIds = waypoints.map { it.id }
            routeRepository.saveRouteWithWaypoints(route, locationIds)
            
            _isCreatingRoute.value = false
            _selectedWaypoints.value = emptyList()
            loadRoutes()
            onSuccess()
        }
    }
    
    fun deleteRoute(routeId: Int) {
        viewModelScope.launch {
            routeRepository.deleteRoute(routeId)
            loadRoutes()
        }
    }
    
    private fun loadRoutes() {
        viewModelScope.launch {
            routeRepository.getAllRoutes().collect { routes ->
                _allRoutes.value = routes
            }
        }
    }
}

/**
 * Route creation bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteCreationSheet(
    viewModel: RouteCreationViewModel,
    onDismiss: () -> Unit,
    onWaypointClick: (LocationEntity) -> Unit
) {
    val selectedWaypoints by viewModel.selectedWaypoints.collectAsState()
    val routeName by viewModel.currentRouteName.collectAsState()
    var showNameDialog by remember { mutableStateOf(false) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.7f)
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
                    text = "Create Route",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Row {
                    IconButton(onClick = { showNameDialog = true }) {
                        Icon(Icons.Default.Edit, "Edit name")
                    }
                    IconButton(onClick = {
                        viewModel.cancelRouteCreation()
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                }
            }
            
            // Route name
            Text(
                text = routeName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Instructions
            if (selectedWaypoints.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text("Tap locations on the map to add waypoints to your route")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Waypoint list
            Text(
                text = "Waypoints (${selectedWaypoints.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(selectedWaypoints) { index, waypoint ->
                    WaypointItem(
                        waypoint = waypoint,
                        index = index,
                        totalCount = selectedWaypoints.size,
                        onMoveUp = { viewModel.moveWaypointUp(index) },
                        onMoveDown = { viewModel.moveWaypointDown(index) },
                        onRemove = { viewModel.removeWaypoint(index) },
                        onClick = { onWaypointClick(waypoint) }
                    )
                }
            }
            
            // Action buttons
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.cancelRouteCreation()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel")
                }
                
                Button(
                    onClick = {
                        viewModel.finishRouteCreation {
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selectedWaypoints.size >= 2
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Finish Route")
                }
            }
        }
    }
    
    // Name edit dialog
    if (showNameDialog) {
        var editName by remember { mutableStateOf(routeName) }
        
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Route Name") },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setRouteName(editName)
                    showNameDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Individual waypoint item in the list
 */
@Composable
fun WaypointItem(
    waypoint: LocationEntity,
    index: Int,
    totalCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit
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
            // Sequence number
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Waypoint info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = waypoint.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${String.format("%.4f", waypoint.latitude)}, ${String.format("%.4f", waypoint.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Action buttons
            Column {
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move up",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < totalCount - 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move down",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Helper to draw route on map
 */
fun drawRouteOnMap(
    mapView: MapView,
    waypoints: List<Triple<LocationEntity, Double?, Double?>>, // location, distance_nm, heading
    color: Int = android.graphics.Color.parseColor("#00A8FF")
) {
    // Clear previous route overlays (if any)
    mapView.overlays.removeAll { it is Polyline }
    
    if (waypoints.size < 2) return
    
    // Create polyline
    val polyline = Polyline(mapView).apply {
        outlinePaint.color = color
        outlinePaint.strokeWidth = 8f
        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
    }
    
    val points = waypoints.map { (loc, _, _) ->
        GeoPoint(loc.latitude, loc.longitude)
    }
    polyline.setPoints(points)
    
    mapView.overlays.add(polyline)
    
    // Add distance/heading labels on route segments
    for (i in 0 until waypoints.size - 1) {
        val (loc1, _, _) = waypoints[i]
        val (loc2, distNm, heading) = waypoints[i + 1]
        
        if (distNm != null && heading != null) {
            // Calculate midpoint
            val midLat = (loc1.latitude + loc2.latitude) / 2
            val midLon = (loc1.longitude + loc2.longitude) / 2
            
            // Create text marker
            val marker = OsmMarker(mapView).apply {
                position = GeoPoint(midLat, midLon)
                title = String.format("%.1f NM @ %03.0f°", distNm, heading)
                setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
            }
            
            mapView.overlays.add(marker)
        }
    }
    
    mapView.invalidate()
}
