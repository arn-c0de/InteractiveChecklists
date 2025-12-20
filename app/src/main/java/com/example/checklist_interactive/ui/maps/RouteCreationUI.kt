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
import androidx.compose.ui.res.stringResource
import com.example.checklist_interactive.R
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
import android.app.Application
import androidx.lifecycle.AndroidViewModel

class RouteCreationViewModel(
    application: Application,
    private val routeRepository: RouteRepository,
    private val locationRepository: LocationRepository,
    private val runwayDao: RunwayDao
) : AndroidViewModel(application) {

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

    private val _waypointRunways = MutableStateFlow<Map<Int, List<RunwayEntity>>>(emptyMap())
    val waypointRunways: StateFlow<Map<Int, List<RunwayEntity>>> = _waypointRunways.asStateFlow()

    private val _lockedWaypoints = MutableStateFlow<Set<Int>>(emptySet())
    val lockedWaypoints: StateFlow<Set<Int>> = _lockedWaypoints.asStateFlow()

    // Route candidate options (multiple possible reorderings with distances)
    private val _routeCandidates = MutableStateFlow<List<Pair<List<LocationEntity>, Double>>>(emptyList())
    val routeCandidates: StateFlow<List<Pair<List<LocationEntity>, Double>>> = _routeCandidates.asStateFlow()

    /**
     * Generate multiple route candidates (combinations) and compute their total distances
     * Respects locked waypoints (keeps them at their positions) and provides up to `maxCandidates` best
     */
    fun generateRouteCandidates(maxCandidates: Int = 10) {
        val waypoints = _selectedWaypoints.value
        if (waypoints.size < 3) return

        viewModelScope.launch {
            val lockedIds = _lockedWaypoints.value

            // Separate locked and unlocked waypoints with their original indices
            val waypointsWithIndices = waypoints.mapIndexed { index, waypoint ->
                Triple(index, waypoint, lockedIds.contains(waypoint.id))
            }

            val locked = waypointsWithIndices.filter { it.third }
            val unlockedEntries = waypointsWithIndices.filter { !it.third }
            val unlocked = unlockedEntries.map { it.second }

            if (unlocked.isEmpty()) return@launch

            val candidateRoutes = mutableListOf<List<LocationEntity>>()

            if (unlocked.size <= 9) {
                // Exact permutations on unlocked subset
                val perms = generatePermutations(unlocked)
                perms.forEach { perm ->
                    // Reconstruct full route keeping locked positions
                    val result = MutableList<LocationEntity?>(waypoints.size) { null }
                    locked.forEach { (index, waypoint, _) -> result[index] = waypoint }
                    var uIndex = 0
                    for (i in result.indices) {
                        if (result[i] == null && uIndex < perm.size) {
                            result[i] = perm[uIndex]
                            uIndex++
                        }
                    }
                    candidateRoutes.add(result.filterNotNull())
                }
            } else {
                // Heuristic candidates for larger sizes: greedy starting at different start points and random shuffles
                // Greedy starting from each unlocked waypoint (limiting to maxCandidates)
                val starts = unlocked.take(maxCandidates)
                for (start in starts) {
                    val remaining = unlocked.toMutableList()
                    remaining.remove(start)
                    val result = mutableListOf<LocationEntity>()
                    result.add(start)
                    var current = start
                    while (remaining.isNotEmpty()) {
                        var nearestIndex = 0
                        var nearestDistance = Double.MAX_VALUE
                        for (i in remaining.indices) {
                            val d = NavigationUtils.calculateDistance(
                                current.latitude, current.longitude,
                                remaining[i].latitude, remaining[i].longitude
                            )
                            if (d < nearestDistance) {
                                nearestDistance = d
                                nearestIndex = i
                            }
                        }
                        current = remaining.removeAt(nearestIndex)
                        result.add(current)
                    }
                    // Reconstruct full route with locked positions
                    val resultFull = MutableList<LocationEntity?>(waypoints.size) { null }
                    locked.forEach { (index, waypoint, _) -> resultFull[index] = waypoint }
                    var uIndex = 0
                    for (i in resultFull.indices) {
                        if (resultFull[i] == null && uIndex < result.size) {
                            resultFull[i] = result[uIndex]
                            uIndex++
                        }
                    }
                    candidateRoutes.add(resultFull.filterNotNull())
                }

                // add a few random shuffles
                val random = java.util.Random(0)
                for (i in 0 until maxCandidates) {
                    val shuffled = unlocked.shuffled(random)
                    val resultFull = MutableList<LocationEntity?>(waypoints.size) { null }
                    locked.forEach { (index, waypoint, _) -> resultFull[index] = waypoint }
                    var uIndex = 0
                    for (j in resultFull.indices) {
                        if (resultFull[j] == null && uIndex < shuffled.size) {
                            resultFull[j] = shuffled[uIndex]
                            uIndex++
                        }
                    }
                    candidateRoutes.add(resultFull.filterNotNull())
                }
            }

            // Compute distances and keep best unique routes by distance
            val routeWithDistances = candidateRoutes
                .map { it to calculateTotalDistance(it) }
                .distinctBy { routeWithDistance ->
                    // Uniqueness by sequence of ids
                    routeWithDistance.first.joinToString(",") { it.id.toString() }
                }
                .sortedBy { it.second }
                .take(maxCandidates)

            _routeCandidates.value = routeWithDistances
        }
    }

    /**
     * Apply candidate at index `candidateIndex` (reorders current selected waypoints)
     */
    fun applyRouteCandidate(candidateIndex: Int) {
        val candidates = _routeCandidates.value
        if (candidateIndex in candidates.indices) {
            _selectedWaypoints.value = candidates[candidateIndex].first
            // clear candidates after apply to avoid stale UI
            _routeCandidates.value = emptyList()
        }
    }

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
        _currentRouteName.value = getApplication<Application>().getString(R.string.route_new_name_template, System.currentTimeMillis() % 1000)
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
                loadAllRunwaysForWaypoints()
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
        loadRunwaysForWaypoint(location)
    }

    private fun loadRunwaysForWaypoint(location: LocationEntity) {
        viewModelScope.launch {
            runwayDao.getRunwaysByLocation(location.id).collect { runways ->
                _waypointRunways.value = _waypointRunways.value + (location.id to runways)
            }
        }
    }

    private fun loadAllRunwaysForWaypoints() {
        viewModelScope.launch {
            val runwayMap = mutableMapOf<Int, List<RunwayEntity>>()
            _selectedWaypoints.value.forEach { waypoint ->
                runwayDao.getRunwaysByLocation(waypoint.id).collect { runways ->
                    runwayMap[waypoint.id] = runways
                }
            }
            _waypointRunways.value = runwayMap
        }
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
                description = getApplication<Application>().getString(R.string.route_autosaved_player_position_description)
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

    fun toggleWaypointLock(locationId: Int) {
        _lockedWaypoints.value = if (_lockedWaypoints.value.contains(locationId)) {
            _lockedWaypoints.value - locationId
        } else {
            _lockedWaypoints.value + locationId
        }
    }

    fun isWaypointLocked(locationId: Int): Boolean {
        return _lockedWaypoints.value.contains(locationId)
    }

    fun setRouteName(name: String) {
        _currentRouteName.value = name
    }

    /**
     * Optimize route order to find the shortest total distance (Traveling Salesman Problem)
     * Respects locked waypoints - they stay at their current positions
     */
    fun optimizeRouteOrder() {
        val waypoints = _selectedWaypoints.value
        if (waypoints.size < 3) return

        viewModelScope.launch {
            val lockedIds = _lockedWaypoints.value

            // Separate locked and unlocked waypoints with their original indices
            val waypointsWithIndices = waypoints.mapIndexed { index, waypoint ->
                Triple(index, waypoint, lockedIds.contains(waypoint.id))
            }

            val locked = waypointsWithIndices.filter { it.third }
            val unlocked = waypointsWithIndices.filter { !it.third }.map { it.second }

            // If all are locked or no unlocked waypoints, nothing to optimize
            if (unlocked.isEmpty()) return@launch

            // Optimize only the unlocked waypoints
            val optimizedUnlocked = if (unlocked.size <= 10) {
                findOptimalRouteExact(unlocked)
            } else {
                findOptimalRouteGreedy(unlocked)
            }

            // Reconstruct the route: keep locked waypoints at their positions,
            // fill unlocked positions with optimized order
            val result = MutableList<LocationEntity?>(waypoints.size) { null }

            // Place locked waypoints at their original positions
            locked.forEach { (index, waypoint, _) ->
                result[index] = waypoint
            }

            // Fill remaining positions with optimized unlocked waypoints
            var unlockedIndex = 0
            for (i in result.indices) {
                if (result[i] == null && unlockedIndex < optimizedUnlocked.size) {
                    result[i] = optimizedUnlocked[unlockedIndex]
                    unlockedIndex++
                }
            }

            _selectedWaypoints.value = result.filterNotNull()
        }
    }

    /**
     * Find optimal route by checking all permutations (exact solution for TSP)
     * Only practical for up to ~10 waypoints
     */
    private fun findOptimalRouteExact(waypoints: List<LocationEntity>): List<LocationEntity> {
        var bestRoute = waypoints
        var bestDistance = calculateTotalDistance(waypoints)

        // Generate all permutations and find the shortest
        val permutations = generatePermutations(waypoints)
        for (route in permutations) {
            val distance = calculateTotalDistance(route)
            if (distance < bestDistance) {
                bestDistance = distance
                bestRoute = route
            }
        }

        return bestRoute
    }

    /**
     * Find optimal route using nearest neighbor heuristic (approximate solution)
     * Works well for larger numbers of waypoints
     */
    private fun findOptimalRouteGreedy(waypoints: List<LocationEntity>): List<LocationEntity> {
        if (waypoints.isEmpty()) return waypoints

        val remaining = waypoints.toMutableList()
        val result = mutableListOf<LocationEntity>()

        // Start with the first waypoint
        var current = remaining.removeAt(0)
        result.add(current)

        // Always pick the nearest unvisited waypoint
        while (remaining.isNotEmpty()) {
            var nearestIndex = 0
            var nearestDistance = Double.MAX_VALUE

            for (i in remaining.indices) {
                val distance = NavigationUtils.calculateDistance(
                    current.latitude, current.longitude,
                    remaining[i].latitude, remaining[i].longitude
                )
                if (distance < nearestDistance) {
                    nearestDistance = distance
                    nearestIndex = i
                }
            }

            current = remaining.removeAt(nearestIndex)
            result.add(current)
        }

        return result
    }

    /**
     * Calculate total distance of a route in nautical miles
     */
    private fun calculateTotalDistance(waypoints: List<LocationEntity>): Double {
        if (waypoints.size < 2) return 0.0

        var totalDistance = 0.0
        for (i in 0 until waypoints.size - 1) {
            val distKm = NavigationUtils.calculateDistance(
                waypoints[i].latitude, waypoints[i].longitude,
                waypoints[i + 1].latitude, waypoints[i + 1].longitude
            )
            totalDistance += NavigationUtils.kmToNauticalMiles(distKm)
        }
        return totalDistance
    }

    /**
     * Generate all permutations of a list
     */
    private fun <T> generatePermutations(list: List<T>): List<List<T>> {
        if (list.size <= 1) return listOf(list)

        val result = mutableListOf<List<T>>()
        for (i in list.indices) {
            val current = list[i]
            val remaining = list.filterIndexed { index, _ -> index != i }
            val perms = generatePermutations(remaining)
            for (perm in perms) {
                result.add(listOf(current) + perm)
            }
        }
        return result
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
                        description = getApplication<Application>().getString(R.string.route_description_waypoints, waypoints.size)
                    )
                    routeRepository.updateRoute(updatedRoute)
                    routeRepository.updateRouteWaypoints(routeId, locationIds)
                }
                routeId
            } else {
                // Create mode - new route
                val route = RouteEntity(
                    name = _currentRouteName.value.ifEmpty { getApplication<Application>().getString(R.string.route_new_name_template, System.currentTimeMillis() % 1000) },
                    description = getApplication<Application>().getString(R.string.route_description_waypoints, waypoints.size),
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
    val waypointRunways by viewModel.waypointRunways.collectAsState()
    val lockedWaypoints by viewModel.lockedWaypoints.collectAsState()
    var showNameDialog by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var showCandidatesDialog by remember { mutableStateOf(false) }

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
        confirmValueChange = { newValue ->
            // Prevent the sheet from transitioning to Hidden while editing an existing route
            // Compare the string name to avoid referencing ModalBottomSheetValue directly
            if (isEditMode && newValue.toString() == "Hidden") {
                false
            } else {
                true
            }
        }
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
        onDismissRequest = {
            // Only allow dismiss via scrim/tap outside if NOT editing an existing route
            if (!isEditMode) {
                viewModel.cancelRouteCreation()
                onDismiss()
            }
            // If editing, ignore outside taps to prevent accidental loss of edits
        },
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
                        awaitPointerEventScope {
                            while (true) {
                                // Wait for an initial down event
                                val firstEvent = awaitPointerEvent()
                                val down = firstEvent.changes.firstOrNull() ?: continue
                                if (!down.pressed) continue

                                var lastY = down.position.y
                                val screenHeightPx = configuration.screenHeightDp * density.density

                                // Consume the down so other handlers don't steal it
                                down.consume()

                                // Track movement until pointer is released
                                var pointerStillDown = true
                                while (pointerStillDown) {
                                    val ev = awaitPointerEvent()
                                    val change = ev.changes.firstOrNull() ?: break

                                    val dy = change.position.y - lastY
                                    lastY = change.position.y

                                    val currentHeightPx = sheetHeightDp.toPx()
                                    val newHeightPx = (currentHeightPx - dy).coerceIn(
                                        screenHeightPx * sheetMin,
                                        screenHeightPx * sheetMax
                                    )

                                    sheetFraction = (newHeightPx / screenHeightPx).coerceIn(sheetMin, sheetMax)
                                    prefs.edit().putFloat(KEY_SHEET_FRACTION, sheetFraction).apply()

                                    // Consume movement so sheet doesn't snap back
                                    change.consume()

                                    if (!change.pressed) {
                                        pointerStillDown = false
                                    }
                                }
                            }
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
                            text = if (isEditMode) stringResource(R.string.route_edit_title) else stringResource(R.string.route_create_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showNameDialog = true }) {
                            Icon(Icons.Default.Edit, stringResource(R.string.route_edit_name_content_description), modifier = Modifier.size(20.dp))
                        }
                    }

                    Row {
                        IconButton(onClick = { showOpacitySlider = !showOpacitySlider }) {
                            Icon(
                                if (showOpacitySlider) Icons.Default.Close else Icons.Default.Settings,
                                contentDescription = stringResource(R.string.route_toggle_opacity_content_description)
                            )
                        }
                        IconButton(onClick = {
                            viewModel.cancelRouteCreation()
                            onDismiss()
                        }) {
                            Icon(Icons.Default.Close, stringResource(R.string.action_cancel))
                        }
                    }
                }

                // Opacity slider
                if (showOpacitySlider) {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(
                            text = stringResource(R.string.route_opacity_label, (sheetOpacity * 100).toInt()),
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
                            Text(stringResource(R.string.route_instructions_add_waypoints))
                        }
                    }
                } else if (selectedWaypoints.size >= 3) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 12.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Column {
                                Text(
                                    stringResource(R.string.route_instructions_lock_waypoints_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    stringResource(R.string.route_instructions_lock_waypoints_message),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Add Waypoint and Plan Fastest Route buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showLocationPicker = true },
                        modifier = Modifier.weight(0.25f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_add))
                    }

                    Button(
                        onClick = {
                            viewModel.generateRouteCandidates(maxCandidates = 8)
                            showCandidatesDialog = true
                        },
                        modifier = Modifier.weight(0.75f),
                        enabled = selectedWaypoints.size >= 3
                    ) {
                        Icon(Icons.Default.Route, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.route_plan_fastest_button))
                    }

                    // Route candidates dropdown (no modal) anchored near the Plan button
                    Box {
                        val candidates by viewModel.routeCandidates.collectAsState()

                        DropdownMenu(
                            expanded = showCandidatesDialog,
                            onDismissRequest = { showCandidatesDialog = false },
                        ) {
                            if (candidates.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.route_no_candidates_message)) },
                                    onClick = { showCandidatesDialog = false }
                                )
                            } else {
                                candidates.forEachIndexed { idx, pair ->
                                    val (routeList, distanceNm) = pair
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(text = stringResource(R.string.route_candidate_format, idx + 1, distanceNm), fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(text = routeList.joinToString(" → ") { it.name }.take(80), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        onClick = {
                                            viewModel.applyRouteCandidate(idx)
                                            showCandidatesDialog = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Waypoint list
                val totalDistanceNm = remember(selectedWaypoints) {
                    var total = 0.0
                    for (i in 0 until selectedWaypoints.size - 1) {
                        val distKm = NavigationUtils.calculateDistance(
                            selectedWaypoints[i].latitude, selectedWaypoints[i].longitude,
                            selectedWaypoints[i + 1].latitude, selectedWaypoints[i + 1].longitude
                        )
                        total += NavigationUtils.kmToNauticalMiles(distKm)
                    }
                    total
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.route_waypoints_label, selectedWaypoints.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.route_total_distance_format, totalDistanceNm),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Info about locked waypoints
                    if (lockedWaypoints.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.route_locked_waypoints_count, lockedWaypoints.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

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
                                        runways = waypointRunways[waypoint.id] ?: emptyList(),
                                        isLocked = lockedWaypoints.contains(waypoint.id),
                                        onMoveUp = { viewModel.moveWaypointUp(index) },
                                        onMoveDown = { viewModel.moveWaypointDown(index) },
                                        onRemove = { viewModel.removeWaypoint(index) },
                                        onClick = { onWaypointClick(waypoint) },
                                        onToggleLock = { viewModel.toggleWaypointLock(waypoint.id) }
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
                        Text(stringResource(R.string.action_cancel))
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
                        Text(stringResource(R.string.route_finish_button))
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
            title = { Text(stringResource(R.string.route_dialog_name_title)) },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text(stringResource(R.string.route_dialog_name_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setRouteName(editName)
                    showNameDialog = false
                }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
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
            title = { Text(stringResource(R.string.route_dialog_add_waypoint_title)) },
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
                                        Text(stringResource(R.string.route_add_current_player_position), fontWeight = FontWeight.Bold)
                                        Text(stringResource(R.string.route_player_position_coordinates_format, flight.latitude, flight.longitude), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(R.string.route_search_locations_label)) },
                        leadingIcon = { Icon(Icons.Default.Search, stringResource(R.string.action_search)) },
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
                                        text = "${location.markerType} • ${stringResource(R.string.route_player_position_coordinates_format, location.latitude, location.longitude)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        if (filteredLocations.size > 50) {
                            item {
                                Text(
                                    text = stringResource(R.string.route_search_refine_message, filteredLocations.size - 50),
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
                    Text(stringResource(R.string.action_cancel))
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
                text = stringResource(R.string.route_segment_hdg_distance_format, heading, distanceNm),
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
    runways: List<RunwayEntity> = emptyList(),
    isLocked: Boolean = false,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    onToggleLock: () -> Unit = {}
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
            // Lock/Unlock button
            IconButton(
                onClick = onToggleLock,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isLocked) stringResource(R.string.route_waypoint_locked_content_description) else stringResource(R.string.route_waypoint_unlocked_content_description),
                    tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Sequence number
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isLocked)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondary
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    color = if (isLocked)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSecondary,
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
                    text = stringResource(R.string.route_player_position_coordinates_format, waypoint.latitude, waypoint.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Property icons
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Runway icon if location has runways
                    if (runways.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Flight,
                                    contentDescription = stringResource(R.string.route_waypoint_runways_content_description, runways.size),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // Show ILS icon if any runway has ILS
                        if (runways.any { !it.ilsFrequency.isNullOrEmpty() }) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Sensors,
                                        contentDescription = stringResource(R.string.route_waypoint_ils_content_description),
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Marker type icon
                    when {
                        waypoint.markerType == "airport" -> {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.LocalAirport,
                                        contentDescription = stringResource(R.string.route_waypoint_airport_content_description),
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        waypoint.markerType == "waypoint" -> {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = CircleShape,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = stringResource(R.string.route_waypoint_waypoint_content_description),
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        waypoint.markerType.startsWith("tactical_") -> {
                            // Tactical unit icon with coalition color
                            val coalitionColor = when {
                                waypoint.markerType.contains("blufor") || waypoint.coalition?.contains("blufor", ignoreCase = true) == true ->
                                    Color(0xFF0080FF)
                                waypoint.markerType.contains("opfor") || waypoint.coalition?.contains("opfor", ignoreCase = true) == true ->
                                    Color(0xFFFF3030)
                                waypoint.markerType.contains("neutral") || waypoint.coalition?.contains("neutral", ignoreCase = true) == true ->
                                    Color(0xFFFFD700)
                                else -> MaterialTheme.colorScheme.outline
                            }

                            Surface(
                                color = coalitionColor.copy(alpha = 0.2f),
                                shape = CircleShape,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = stringResource(R.string.route_waypoint_tactical_unit_content_description),
                                        modifier = Modifier.size(14.dp),
                                        tint = coalitionColor
                                    )
                                }
                            }
                        }
                        waypoint.markerType == "target" -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.GpsFixed,
                                        contentDescription = stringResource(R.string.route_waypoint_target_content_description),
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    // Static marker icon
                    if (waypoint.isStatic == 1) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = stringResource(R.string.route_waypoint_static_content_description),
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
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
                        contentDescription = stringResource(R.string.action_move_up),
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
                        contentDescription = stringResource(R.string.action_move_down),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // Delete button (X)
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_remove),
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

            // Add label to text overlay (show heading first, then distance)
            val labelText = String.format("HDG %03.0f° 	• %.1f NM", heading, distNm)
            textOverlay.addLabel(GeoPoint(midLat, midLon), labelText)
        }
    }

    mapView.overlays.add(textOverlay)
    mapView.invalidate()
}
