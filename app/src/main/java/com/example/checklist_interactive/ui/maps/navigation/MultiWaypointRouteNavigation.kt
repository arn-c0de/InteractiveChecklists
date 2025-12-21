package com.example.checklist_interactive.ui.maps.navigation

import android.app.Application
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.checklist_interactive.R
import com.example.checklist_interactive.data.tactical.*
import com.example.checklist_interactive.ui.datapad.LocalDataPadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

/**
 * Multi-waypoint route navigation and creation
 * Handles complex routes with multiple waypoints, optimization, and visual display
 */

/**
 * ViewModel for multi-waypoint route creation and management
 */
class MultiWaypointRouteViewModel(
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

    init {
        loadRoutes()
        loadLocations()
    }

    /**
     * Generate multiple route candidates (combinations) and compute their total distances
     * Respects locked waypoints (keeps them at their positions) and provides up to `maxCandidates` best
     */
    fun generateRouteCandidates(maxCandidates: Int = 10) {
        val waypoints = _selectedWaypoints.value
        if (waypoints.size < 3) return

        viewModelScope.launch {
            val locked = _lockedWaypoints.value
            val lockedPositions = mutableMapOf<Int, Int>()
            val movable = mutableListOf<LocationEntity>()

            waypoints.forEachIndexed { idx, loc ->
                if (loc.id in locked) {
                    lockedPositions[loc.id] = idx
                } else {
                    movable.add(loc)
                }
            }

            if (movable.isEmpty()) {
                _routeCandidates.value = listOf(_selectedWaypoints.value to calculateTotalDistance(_selectedWaypoints.value))
                return@launch
            }

            // Generate permutations of movable waypoints
            val permutations = generatePermutations(movable)
            val candidates = mutableListOf<Pair<List<LocationEntity>, Double>>()

            for (perm in permutations) {
                val result = waypoints.toMutableList()
                var movableIdx = 0
                waypoints.forEachIndexed { idx, loc ->
                    if (loc.id !in locked) {
                        result[idx] = perm[movableIdx++]
                    }
                }
                val dist = calculateTotalDistance(result)
                candidates.add(result to dist)
            }

            // Sort by distance and take top N
            val sorted = candidates.sortedBy { it.second }.take(maxCandidates)
            _routeCandidates.value = sorted
        }
    }

    /**
     * Apply candidate at index `candidateIndex` (reorders current selected waypoints)
     */
    fun applyRouteCandidate(candidateIndex: Int) {
        val candidates = _routeCandidates.value
        if (candidateIndex in candidates.indices) {
            _selectedWaypoints.value = candidates[candidateIndex].first
        }
    }

    private fun loadLocations() {
        viewModelScope.launch {
            // getAllLocations returns a Flow, collect latest
            locationRepository.getAllLocations().collect { locations ->
                _availableLocations.value = locations
            }
        }
    }

    fun startRouteCreation() {
        _isCreatingRoute.value = true
        _selectedWaypoints.value = emptyList()
        _currentRouteName.value = getApplication<Application>().getString(
            R.string.route_new_name_template,
            System.currentTimeMillis() % 1000
        )
        _currentRouteId.value = null
    }

    fun startRouteEditing(routeId: Int) {
        viewModelScope.launch {
            // getRouteWithWaypoints is a suspend function returning a nullable RouteWithWaypoints
            val routeWithWaypoints = routeRepository.getRouteWithWaypoints(routeId)
            routeWithWaypoints?.let { rww ->
                _currentRouteId.value = routeId
                _currentRouteName.value = rww.route.name
                // Map WaypointWithLocation -> LocationEntity
                _selectedWaypoints.value = rww.waypoints.map { it.location }
                _isCreatingRoute.value = true
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
            // Runway DAO exposes a Flow; collect to obtain the current list
            runwayDao.getRunwaysByLocation(location.id ?: 0).collect { runways ->
                _waypointRunways.value = _waypointRunways.value + (location.id to runways)
            }
        }
    }

    private fun loadAllRunwaysForWaypoints() {
        viewModelScope.launch {
            val map = mutableMapOf<Int, List<RunwayEntity>>()
            _selectedWaypoints.value.forEach { loc ->
                runwayDao.getRunwaysByLocation(loc.id ?: 0).collect { runways ->
                    map[loc.id] = runways
                }
            }
            _waypointRunways.value = map
        }
    }

    /**
     * Persist a location (e.g., current player position) and add it as a waypoint
     */
    fun addWaypointFromCoordinates(name: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            val newLocation = LocationEntity(
                name = name,
                latitude = latitude,
                longitude = longitude,
                markerType = "WAYPOINT",
                description = "Added from current position",
                created = "",
                modified = ""
            )
            val id = locationRepository.saveLocation(newLocation)
            val insertedLocation = newLocation.copy(id = id.toInt())
            _selectedWaypoints.value = _selectedWaypoints.value + insertedLocation
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
            val locked = _lockedWaypoints.value
            val lockedPositions = mutableMapOf<Int, Int>()
            val movable = mutableListOf<LocationEntity>()

            waypoints.forEachIndexed { idx, loc ->
                if (loc.id in locked) {
                    lockedPositions[loc.id] = idx
                } else {
                    movable.add(loc)
                }
            }

            if (movable.isEmpty()) return@launch

            val optimizedMovable = if (movable.size <= 10) {
                findOptimalRouteExact(movable)
            } else {
                findOptimalRouteGreedy(movable)
            }

            val result = waypoints.toMutableList()
            var movableIdx = 0
            waypoints.forEachIndexed { idx, loc ->
                if (loc.id !in locked) {
                    result[idx] = optimizedMovable[movableIdx++]
                }
            }

            _selectedWaypoints.value = result
        }
    }

    /**
     * Find optimal route by checking all permutations (exact solution for TSP)
     * Only practical for up to ~10 waypoints
     */
    private fun findOptimalRouteExact(waypoints: List<LocationEntity>): List<LocationEntity> {
        var bestRoute = waypoints
        var bestDistance = calculateTotalDistance(waypoints)

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

        var current = remaining.removeAt(0)
        result.add(current)

        while (remaining.isNotEmpty()) {
            var nearestIndex = 0
            var nearestDistance = Double.MAX_VALUE

            remaining.forEachIndexed { idx, loc ->
                val dist = calculateDistance(
                    current.latitude, current.longitude,
                    loc.latitude, loc.longitude
                )
                if (dist < nearestDistance) {
                    nearestDistance = dist
                    nearestIndex = idx
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
            totalDistance += calculateDistance(
                waypoints[i].latitude, waypoints[i].longitude,
                waypoints[i + 1].latitude, waypoints[i + 1].longitude
            )
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
            val rest = list.subList(0, i) + list.subList(i + 1, list.size)
            val perms = generatePermutations(rest)
            perms.forEach { perm -> result.add(listOf(list[i]) + perm) }
        }
        return result
    }

    fun finishRouteCreation(onSuccess: (Int) -> Unit) {
        val waypoints = _selectedWaypoints.value
        if (waypoints.size < 2) {
            // Show error toast or message
            return
        }

        viewModelScope.launch {
            val routeId = _currentRouteId.value
            val locationIds = waypoints.map { it.id }

            if (routeId != null) {
                // Update existing route metadata and waypoints
                val existingRoute = routeRepository.getRouteById(routeId)
                existingRoute?.let { r ->
                    val updatedRoute = r.copy(name = _currentRouteName.value)
                    routeRepository.updateRoute(updatedRoute)
                    routeRepository.updateRouteWaypoints(routeId, locationIds)
                    onSuccess(routeId)
                }
            } else {
                // Create new route with waypoints
                val route = RouteEntity(
                    name = _currentRouteName.value,
                    description = "",
                    color = "#00A8FF",
                    created = "",
                    modified = ""
                )
                val newRouteId = routeRepository.saveRouteWithWaypoints(route, locationIds)
                onSuccess(newRouteId.toInt())
            }

            _isCreatingRoute.value = false
            _selectedWaypoints.value = emptyList()
            _currentRouteId.value = null
            loadRoutes()
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
 * Calculate distance between two coordinates in nautical miles
 */
fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371.0 // km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    val distanceKm = earthRadius * c
    return distanceKm * 0.539957 // Convert to nautical miles
}

/**
 * Custom overlay to draw permanent text labels on the map
 */
class RouteTextOverlay : Overlay() {
    data class TextLabel(val position: GeoPoint, val text: String)

    private val labels = mutableListOf<TextLabel>()
    private val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 32f
        isAntiAlias = true
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val backgroundPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#CC000000")
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

        labels.forEach { label ->
            val point = mapView.projection.toPixels(label.position, null)

            // Measure text bounds
            val bounds = Rect()
            textPaint.getTextBounds(label.text, 0, label.text.length, bounds)

            // Draw background rounded rectangle
            val padding = 8f
            val left = point.x - bounds.width() / 2f - padding
            val top = point.y.toFloat() - bounds.height() - padding
            val right = point.x + bounds.width() / 2f + padding
            val bottom = point.y.toFloat() + padding

            canvas.drawRoundRect(
                left, top, right, bottom,
                8f, 8f, backgroundPaint
            )

            // Draw text
            canvas.drawText(
                label.text,
                point.x.toFloat(),
                point.y.toFloat(),
                textPaint
            )
        }
    }
}

/**
 * Helper to draw route on map with distance/heading labels
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
        val (loc1, _, _) = waypoints[i]
        val (loc2, dist, hdg) = waypoints[i + 1]

        val midLat = (loc1.latitude + loc2.latitude) / 2
        val midLon = (loc1.longitude + loc2.longitude) / 2
        val midPoint = GeoPoint(midLat, midLon)

        val label = buildString {
            dist?.let { append("${String.format("%.1f", it)} NM") }
            if (dist != null && hdg != null) append(" / ")
            hdg?.let { append("${String.format("%.0f", it)}°") }
        }
        textOverlay.addLabel(midPoint, label)
    }

    mapView.overlays.add(textOverlay)
    mapView.invalidate()
}
