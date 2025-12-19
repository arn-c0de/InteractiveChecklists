package com.example.checklist_interactive.ui.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import com.example.checklist_interactive.ui.datapad.LocalDataPadManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View
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
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.Overlay
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

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

    private val _currentRouteId = MutableStateFlow<Int?>(null)
    val currentRouteId: StateFlow<Int?> = _currentRouteId.asStateFlow()

    private val _allRoutes = MutableStateFlow<List<RouteEntity>>(emptyList())
    val allRoutes: StateFlow<List<RouteEntity>> = _allRoutes.asStateFlow()

    private val _availableLocations = MutableStateFlow<List<LocationEntity>>(emptyList())
    val availableLocations: StateFlow<List<LocationEntity>> = _availableLocations.asStateFlow()
    
    init {
        loadRoutes()
        loadLocations()
    }
    
    private fun loadLocations() {
        viewModelScope.launch {
            locationRepository.getAllLocations().collect { locations ->
                _availableLocations.value = locations
            }
        }
    }
    
    fun startRouteCreation() {
        _isCreatingRoute.value = true
        _selectedWaypoints.value = emptyList()
        _currentRouteName.value = "New Route ${System.currentTimeMillis() % 1000}"
        _currentRouteId.value = null
    }

    fun startRouteEditing(routeId: Int) {
        viewModelScope.launch {
            val routeData = routeRepository.getRouteWithWaypoints(routeId)
            routeData?.let { data ->
                _isCreatingRoute.value = true
                _currentRouteId.value = routeId
                _currentRouteName.value = data.route.name
                _selectedWaypoints.value = data.waypoints.map { it.location }
            }
        }
    }

    fun cancelRouteCreation() {
        _isCreatingRoute.value = false
        _selectedWaypoints.value = emptyList()
        _currentRouteId.value = null
    }
    
    fun addWaypoint(location: LocationEntity) {
        _selectedWaypoints.value = _selectedWaypoints.value + location
    }

    /**
     * Persist a location (e.g., current player position) and add it as a waypoint
     */
    fun addWaypointFromCoordinates(name: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            // Create a minimal LocationEntity and persist it
            val newLoc = LocationEntity(
                name = name,
                latitude = latitude,
                longitude = longitude,
                markerType = "waypoint",
                isStatic = 0,
                description = "Auto-saved player position"
            )
            val insertedId = locationRepository.saveLocation(newLoc).toInt()
            val saved = locationRepository.getLocationById(insertedId)
            if (saved != null) {
                _selectedWaypoints.value = _selectedWaypoints.value + saved
            }
        }
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
    
    fun finishRouteCreation(onSuccess: (Int) -> Unit) {
        val waypoints = _selectedWaypoints.value
        if (waypoints.size < 2) {
            // Need at least 2 waypoints
            return
        }

        viewModelScope.launch {
            val routeId = _currentRouteId.value
            val locationIds = waypoints.map { it.id }

            val finalRouteId = if (routeId != null) {
                // Edit mode - update existing route
                val existingRouteData = routeRepository.getRouteWithWaypoints(routeId)
                existingRouteData?.let { data ->
                    val updatedRoute = data.route.copy(
                        name = _currentRouteName.value.ifEmpty { data.route.name },
                        description = "Route with ${waypoints.size} waypoints"
                    )
                    routeRepository.updateRoute(updatedRoute)
                    routeRepository.updateRouteWaypoints(routeId, locationIds)
                }
                routeId
            } else {
                // Create mode - new route
                val route = RouteEntity(
                    name = _currentRouteName.value.ifEmpty { "Route ${System.currentTimeMillis()}" },
                    description = "Route with ${waypoints.size} waypoints",
                    color = "#00A8FF",
                    created = "",
                    modified = ""
                )
                routeRepository.saveRouteWithWaypoints(route, locationIds).toInt()
            }

            _isCreatingRoute.value = false
            _selectedWaypoints.value = emptyList()
            _currentRouteId.value = null
            loadRoutes()
            onSuccess(finalRouteId)
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
    onWaypointClick: (LocationEntity) -> Unit,
    onRouteFinished: (Int) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val selectedWaypoints by viewModel.selectedWaypoints.collectAsState()
    val routeName by viewModel.currentRouteName.collectAsState()
    val currentRouteId by viewModel.currentRouteId.collectAsState()
    val availableLocations by viewModel.availableLocations.collectAsState()
    var showNameDialog by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }

    val isEditMode = currentRouteId != null

    val view = LocalView.current

    // Persisted sheet fraction + opacity
    val prefs = context.getSharedPreferences("route_creation_prefs", android.content.Context.MODE_PRIVATE)
    val KEY_SHEET_FRACTION = "route_creation_sheet_fraction"
    val savedFraction = prefs.getFloat(KEY_SHEET_FRACTION, 0.7f)
    val sheetMin = 0.25f
    val sheetMax = 0.95f
    var sheetFraction by rememberSaveable { mutableStateOf(savedFraction.coerceIn(sheetMin, sheetMax)) }

    val KEY_SHEET_OPACITY = "route_creation_sheet_opacity"
    val savedOpacity = prefs.getFloat(KEY_SHEET_OPACITY, 1.0f)
    var sheetOpacity by rememberSaveable { mutableStateOf(savedOpacity.coerceIn(0.25f, 1.0f)) }
    var showOpacitySlider by remember { mutableStateOf(false) }

    // Persist opacity when changed
    LaunchedEffect(sheetOpacity) {
        prefs.edit().putFloat(KEY_SHEET_OPACITY, sheetOpacity).apply()
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val sheetHeightDp = (configuration.screenHeightDp.toFloat() * sheetFraction).dp

    // Force immersive fullscreen mode continuously while bottom sheet is shown
    LaunchedEffect(sheetState.currentValue) {
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

        // Keep applying occasionally to override ModalBottomSheet's behavior only while the sheet is visible
        if (sheetState.isVisible) {
            while (isActive && sheetState.isVisible) {
                hideSystemUI()
                kotlinx.coroutines.delay(750L)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = sheetOpacity),
        dragHandle = null // Disable default drag handle, use custom one only
    ) {
        // Try to hide the system UI inside the dialog window that hosts the sheet.
        val dialogView = LocalView.current

        // Immediately attempt to hide system UI before first draw to avoid flash.
        DisposableEffect(dialogView) {
            val dialogWindow = (dialogView.context as? android.app.Activity)?.window
            val controller = dialogWindow?.let { WindowCompat.getInsetsController(it, dialogView) }
            // Also set old-style flags for older API's
            @Suppress("DEPRECATION")
            dialogView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

            val preDrawListener = object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    controller?.hide(WindowInsetsCompat.Type.systemBars())
                    try {
                        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } catch (_: Throwable) {
                    }
                    dialogView.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            }
            dialogView.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            val attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    val vWindow = (v.context as? android.app.Activity)?.window
                    val c = vWindow?.let { WindowCompat.getInsetsController(it, v) }
                    c?.hide(WindowInsetsCompat.Type.systemBars())
                    try {
                        c?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } catch (_: Throwable) {
                    }
                    @Suppress("DEPRECATION")
                    v.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
                }

                override fun onViewDetachedFromWindow(v: View) {}
            }
            dialogView.addOnAttachStateChangeListener(attachListener)
            onDispose {
                try {
                    dialogView.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                    dialogView.removeOnAttachStateChangeListener(attachListener)
                } catch (_: Throwable) {
                }
            }
        }

        // Also keep ensuring hide while it's visible (loop for resilience).
        LaunchedEffect(dialogView, sheetState.isVisible) {
            if (sheetState.isVisible) {
                val dialogWindow = (dialogView.context as? android.app.Activity)?.window
                val dialogController = dialogWindow?.let { WindowCompat.getInsetsController(it, dialogView) }
                while (isActive && sheetState.isVisible) {
                    dialogController?.hide(WindowInsetsCompat.Type.systemBars())
                    kotlinx.coroutines.delay(750L)
                }
            }
        }

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
                    .align(Alignment.TopCenter)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val currentHeightPx = sheetHeightDp.toPx()
                            val screenHeightPx = configuration.screenHeightDp * density.density
                            val newHeightPx = (currentHeightPx - dragAmount.y).coerceIn(
                                screenHeightPx * sheetMin,
                                screenHeightPx * sheetMax
                            )
                            sheetFraction = (newHeightPx / screenHeightPx).coerceIn(sheetMin, sheetMax)
                            prefs.edit().putFloat(KEY_SHEET_FRACTION, sheetFraction).apply()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 28.dp)
            ) {
                // Opacity controls and header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isEditMode) "Edit Route" else "Create Route",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showNameDialog = true }) {
                            Icon(Icons.Default.Edit, "Edit route name", modifier = Modifier.size(20.dp))
                        }
                    }

                    Row {
                        IconButton(onClick = { showOpacitySlider = !showOpacitySlider }) {
                            Icon(
                                if (showOpacitySlider) Icons.Default.Close else Icons.Default.Settings,
                                contentDescription = "Toggle opacity"
                            )
                        }
                        IconButton(onClick = {
                            viewModel.cancelRouteCreation()
                            onDismiss()
                        }) {
                            Icon(Icons.Default.Close, "Cancel")
                        }
                    }
                }

                // Opacity slider
                if (showOpacitySlider) {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(
                            text = "Opacity: ${(sheetOpacity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = sheetOpacity,
                            onValueChange = { sheetOpacity = it },
                            valueRange = 0.25f..1.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Route name
                Text(
                    text = routeName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
                            Text("Add waypoints manually or tap locations on the map")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Add Waypoint button
                Button(
                    onClick = { showLocationPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Waypoint")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Waypoint list
                Text(
                    text = "Waypoints (${selectedWaypoints.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        userScrollEnabled = true
                    ) {
                        selectedWaypoints.forEachIndexed { index, waypoint ->
                            item(key = "waypoint_$index") {
                                Column {
                                    WaypointItem(
                                        waypoint = waypoint,
                                        index = index,
                                        totalCount = selectedWaypoints.size,
                                        onMoveUp = { viewModel.moveWaypointUp(index) },
                                        onMoveDown = { viewModel.moveWaypointDown(index) },
                                        onRemove = { viewModel.removeWaypoint(index) },
                                        onClick = { onWaypointClick(waypoint) }
                                    )

                                    // Show distance and heading to next waypoint
                                    if (index < selectedWaypoints.size - 1) {
                                        val nextWaypoint = selectedWaypoints[index + 1]
                                        val distanceKm = NavigationUtils.calculateDistance(
                                            waypoint.latitude, waypoint.longitude,
                                            nextWaypoint.latitude, nextWaypoint.longitude
                                        )
                                        val distanceNm = NavigationUtils.kmToNauticalMiles(distanceKm)
                                        val heading = NavigationUtils.calculateBearing(
                                            waypoint.latitude, waypoint.longitude,
                                            nextWaypoint.latitude, nextWaypoint.longitude
                                        )

                                        RouteSegmentInfo(
                                            distanceNm = distanceNm,
                                            heading = heading
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
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
                            viewModel.finishRouteCreation { routeId ->
                                onRouteFinished(routeId)
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
    
    // Location picker dialog
    if (showLocationPicker) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredLocations = remember(searchQuery, availableLocations) {
            availableLocations.filter { 
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.markerType.contains(searchQuery, ignoreCase = true)
            }
        }
        
        AlertDialog(
            onDismissRequest = { showLocationPicker = false },
            title = { Text("Add Waypoint") },
            text = {
                Column {
                    // Offer quick-add of the current player position (when available)
                    val dataPadManager = LocalDataPadManager.current
                    val flightData by dataPadManager.flightData.collectAsState()
                    val datapadEnabled by dataPadManager.isEnabled.collectAsState()

                    flightData?.let { flight ->
                        if (datapadEnabled && flight.latitude != 0.0 && flight.longitude != 0.0) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clickable {
                                        val label = if (flight.unitName.isNotBlank()) flight.unitName else "Player"
                                        viewModel.addWaypointFromCoordinates("$label (player)", flight.latitude, flight.longitude)
                                        showLocationPicker = false
                                    }
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MyLocation, contentDescription = null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Add current player position", fontWeight = FontWeight.Bold)
                                        Text("${String.format("%.4f", flight.latitude)}, ${String.format("%.4f", flight.longitude)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search locations") },
                        leadingIcon = { Icon(Icons.Default.Search, "Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        itemsIndexed(filteredLocations.take(50)) { _, location ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.addWaypoint(location)
                                        showLocationPicker = false
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = location.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${location.markerType} • ${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        if (filteredLocations.size > 50) {
                            item {
                                Text(
                                    text = "... and ${filteredLocations.size - 50} more. Refine your search.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLocationPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Route segment info between waypoints
 */
@Composable
fun RouteSegmentInfo(
    distanceNm: Double,
    heading: Double
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ArrowDownward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = String.format("%.1f NM", distanceNm),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "HDG ${String.format("%03.0f°", heading)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
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
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reorder indicator (not draggable - use arrows instead)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Reorder indicator",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Sequence number
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Waypoint info (clickable to center on map)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = waypoint.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${String.format("%.4f", waypoint.latitude)}, ${String.format("%.4f", waypoint.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Up/Down buttons (for manual reorder)
            Column {
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move up",
                        modifier = Modifier.size(18.dp)
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
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // Delete button (X)
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Custom overlay to draw permanent text labels on the map
 */
class RouteTextOverlay : Overlay() {
    data class TextLabel(val position: GeoPoint, val text: String)

    private val labels = mutableListOf<TextLabel>()
    private val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 36f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
    }

    private val backgroundPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#CC2C3E50")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun addLabel(position: GeoPoint, text: String) {
        labels.add(TextLabel(position, text))
    }

    fun clearLabels() {
        labels.clear()
    }

    fun getLabelCount(): Int = labels.size

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        android.util.Log.d("RouteTextOverlay", "Drawing ${labels.size} labels")

        val projection = mapView.projection
        val point = android.graphics.Point()

        for ((index, label) in labels.withIndex()) {
            android.util.Log.d("RouteTextOverlay", "Drawing label $index: ${label.text} at ${label.position}")
            projection.toPixels(label.position, point)

            // Measure text
            val bounds = Rect()
            textPaint.getTextBounds(label.text, 0, label.text.length, bounds)

            // Draw background with padding
            val padding = 12f
            val textHeight = bounds.height().toFloat()
            val textWidth = bounds.width().toFloat()

            val left = point.x - textWidth / 2f - padding
            val top = point.y - textHeight / 2f - padding
            val right = point.x + textWidth / 2f + padding
            val bottom = point.y + textHeight / 2f + padding

            canvas.drawRoundRect(
                left, top, right, bottom,
                8f, 8f,
                backgroundPaint
            )

            // Draw text centered
            canvas.drawText(
                label.text,
                point.x.toFloat(),
                point.y.toFloat() + textHeight / 2f - bounds.bottom,
                textPaint
            )
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
    // Clear previous route overlays (Polyline and RouteTextOverlay)
    mapView.overlays.removeAll { it is Polyline || it is RouteTextOverlay }

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

    // Create text overlay for distance/heading labels
    val textOverlay = RouteTextOverlay()

    // Add distance/heading labels on route segments
    for (i in 0 until waypoints.size - 1) {
        val (loc1, distNm, heading) = waypoints[i]  // Distance/heading FROM this waypoint to next
        val (loc2, _, _) = waypoints[i + 1]

        if (distNm != null && heading != null) {
            // Calculate midpoint
            val midLat = (loc1.latitude + loc2.latitude) / 2
            val midLon = (loc1.longitude + loc2.longitude) / 2

            // Add label to text overlay
            val labelText = String.format("%.1f NM @ %03.0f°", distNm, heading)
            textOverlay.addLabel(GeoPoint(midLat, midLon), labelText)
        }
    }

    mapView.overlays.add(textOverlay)
    mapView.invalidate()
}
