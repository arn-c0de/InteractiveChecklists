package com.example.checklist_interactive.ui.maps.route

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.checklist_interactive.data.datapad.FlightData
import com.example.checklist_interactive.data.prefs.PreferencesManager
import com.example.checklist_interactive.data.tactical.FlightPathDao
import com.example.checklist_interactive.data.tactical.FlightPathPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

/**
 * Repository for managing flight path recording and retrieval
 * 
 * Features:
 * - Records aircraft position history from FlightData stream
 * - Intelligent sampling (only records when position changes significantly)
 * - Persists path to database for survival across app restarts
 * - Provides reactive Flow of path data for UI rendering
 * - Manages recording state (enabled/disabled) with persistence
 * 
 * Recording Logic:
 * - Configurable time interval: 1-60 seconds between samples (user adjustable)
 * - Minimum distance: 10 meters movement required
 * - Automatically filters stationary aircraft on ground
 */
class FlightPathRepository(
    private val context: Context,
    private val flightPathDao: FlightPathDao
) {
    companion object {
        private const val TAG = "FlightPathRepository"
        
        // Recording thresholds
        private const val MIN_DISTANCE_METERS = 10.0      // Only record if moved > 10m
        private const val MIN_ALTITUDE_CHANGE_METERS = 5.0 // Or altitude changed > 5m
        
        // DataStore for persisting recording state
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "flight_path_settings")
        private val KEY_RECORDING_ENABLED = booleanPreferencesKey("recording_enabled")
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefsManager = PreferencesManager(context)
    
    // Recording state (persisted)
    private val _isRecordingEnabled = MutableStateFlow(false)
    val isRecordingEnabled: StateFlow<Boolean> = _isRecordingEnabled.asStateFlow()
    
    // Last recorded position for change detection
    private var lastRecordedLat: Double? = null
    private var lastRecordedLon: Double? = null
    private var lastRecordedAlt: Double? = null
    private var lastRecordedTime: Long = 0L
    
    // Statistics
    private val _pointCount = MutableStateFlow(0)
    val pointCount: StateFlow<Int> = _pointCount.asStateFlow()
    
    // Path data as Flow for reactive UI updates
    val pathPoints: Flow<List<FlightPathPoint>> = flightPathDao.getAllPointsFlow()
    
    init {
        // Load persisted recording state
        scope.launch {
            context.dataStore.data
                .map { prefs -> prefs[KEY_RECORDING_ENABLED] ?: false }
                .collect { enabled ->
                    _isRecordingEnabled.value = enabled
                    Log.d(TAG, "Recording state loaded: $enabled")
                }
        }
        
        // Load point count
        scope.launch {
            val count = flightPathDao.getPointCount()
            _pointCount.value = count
            Log.d(TAG, "Loaded $count existing path points from database")
        }
    }
    
    /**
     * Enable or disable path recording
     * State is persisted to DataStore
     */
    suspend fun setRecordingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RECORDING_ENABLED] = enabled
        }
        _isRecordingEnabled.value = enabled
        Log.d(TAG, "Recording ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Process incoming flight data and record position if conditions are met
     * 
     * Recording conditions:
     * 1. Recording must be enabled
     * 2. Valid position data (lat/lon not zero)
     * 3. Minimum time interval elapsed OR significant movement detected
     * 
     * @param flightData Current flight data from DataPad
     */
    suspend fun processFlightData(flightData: FlightData?) {
        if (!_isRecordingEnabled.value) {
            // Log.v(TAG, "Recording disabled, skipping")
            return
        }
        if (flightData == null) {
            Log.v(TAG, "No flight data available")
            return
        }
        
        // Validate position data
        val lat = flightData.latitude
        val lon = flightData.longitude
        if (lat == 0.0 && lon == 0.0) {
            // Invalid position (aircraft not spawned or GPS not available)
            Log.v(TAG, "Invalid position (0,0), skipping")
            return
        }
        
        val now = System.currentTimeMillis()
        val timeSinceLastRecord = now - lastRecordedTime
        
        // Get user-configured interval (in seconds) and convert to milliseconds
        val intervalSeconds = prefsManager.getFlightPathIntervalSeconds()
        val minIntervalMs = intervalSeconds * 1000L
        
        // Calculate distance moved since last record (if we have a previous position)
        val shouldRecord = if (lastRecordedLat != null && lastRecordedLon != null) {
            // Check if minimum time interval has elapsed (only after first point)
            if (timeSinceLastRecord < minIntervalMs) {
                Log.v(TAG, "⏱️ Too soon: ${timeSinceLastRecord}ms < ${minIntervalMs}ms (interval: ${intervalSeconds}s)")
                return  // Too soon, skip this sample
            }
            
            val distanceMoved = calculateDistance(
                lastRecordedLat!!,
                lastRecordedLon!!,
                lat,
                lon
            )
            
            val altitudeChange = if (lastRecordedAlt != null) {
                kotlin.math.abs(flightData.altitude - lastRecordedAlt!!)
            } else {
                Double.MAX_VALUE  // First point, always record
            }
            
            val shouldRec = distanceMoved > MIN_DISTANCE_METERS || altitudeChange > MIN_ALTITUDE_CHANGE_METERS
            
            if (!shouldRec) {
                Log.d(TAG, "📍 Movement too small: dist=${distanceMoved.toInt()}m (min ${MIN_DISTANCE_METERS.toInt()}m), alt_change=${altitudeChange.toInt()}m (min ${MIN_ALTITUDE_CHANGE_METERS.toInt()}m)")
            }
            
            shouldRec
        } else {
            // First point, always record
            Log.d(TAG, "📍 First point - will record")
            true
        }
        
        if (shouldRecord) {
            // Create and insert the new point
            val point = FlightPathPoint(
                timestamp = now,
                latitude = lat,
                longitude = lon,
                altitudeMsl = flightData.altitude,
                heading = Math.toDegrees(flightData.heading),  // Convert radians to degrees
                groundSpeed = flightData.groundSpeed,
                verticalSpeed = flightData.verticalSpeed
            )
            
            try {
                flightPathDao.insertPoint(point)
                
                // Update tracking variables
                lastRecordedLat = lat
                lastRecordedLon = lon
                lastRecordedAlt = flightData.altitude
                lastRecordedTime = now
                
                // Update count
                _pointCount.value = flightPathDao.getPointCount()
                
                Log.i(TAG, "✅ RECORDED POINT #${_pointCount.value}: lat=${String.format("%.6f", lat)}, lon=${String.format("%.6f", lon)}, alt=${flightData.altitude.toInt()}m, hdg=${Math.toDegrees(flightData.heading).toInt()}°")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record flight path point: ${e.message}", e)
            }
        }
    }
    
    /**
     * Clear all recorded path points
     */
    suspend fun clearPath() {
        try {
            // Count points before deletion for logging
            val countBefore = flightPathDao.getPointCount()
            Log.d(TAG, "Attempting to clear $countBefore flight path points...")
            
            // Delete all points from database
            flightPathDao.clearAllPoints()
            
            // Verify deletion
            val countAfter = flightPathDao.getPointCount()
            Log.d(TAG, "Points after clearAllPoints: $countAfter")
            
            // Reset internal tracking state
            lastRecordedLat = null
            lastRecordedLon = null
            lastRecordedAlt = null
            lastRecordedTime = 0L
            _pointCount.value = 0
            
            Log.i(TAG, "✅ Flight path cleared successfully: deleted $countBefore points, remaining: $countAfter")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear flight path: ${e.message}", e)
            // Try to get accurate count even after error
            try {
                val actualCount = flightPathDao.getPointCount()
                _pointCount.value = actualCount
                Log.e(TAG, "Actual point count after failed clear: $actualCount")
            } catch (countError: Exception) {
                Log.e(TAG, "Could not get point count: ${countError.message}")
            }
        }
    }
    
    /**
     * Get all path points as a list of GeoPoints for map rendering
     */
    suspend fun getPathAsGeoPoints(): List<GeoPoint> {
        return try {
            flightPathDao.getAllPoints().map { point ->
                GeoPoint(point.latitude, point.longitude)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load path points: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Calculate distance between two lat/lon points using Haversine formula
     * Returns distance in meters
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusM = 6371000.0  // Earth radius in meters
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        
        return earthRadiusM * c
    }
    
    /**
     * Get statistics about the recorded path
     */
    suspend fun getPathStatistics(): PathStatistics {
        val points = flightPathDao.getAllPoints()
        if (points.isEmpty()) {
            return PathStatistics(0, 0.0, 0L)
        }
        
        var totalDistance = 0.0
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            totalDistance += calculateDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        }
        
        val duration = points.last().timestamp - points.first().timestamp
        
        return PathStatistics(
            pointCount = points.size,
            totalDistanceMeters = totalDistance,
            durationMillis = duration
        )
    }
    
    /**
     * Delete old path points to manage database size
     * Keeps only points from the last N hours
     */
    suspend fun cleanupOldPoints(keepLastHours: Int = 24) {
        val cutoffTime = System.currentTimeMillis() - (keepLastHours * 3600 * 1000L)
        try {
            val deletedCount = flightPathDao.deletePointsBefore(cutoffTime)
            if (deletedCount > 0) {
                _pointCount.value = flightPathDao.getPointCount()
                Log.d(TAG, "Cleaned up $deletedCount old path points (older than $keepLastHours hours)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old points: ${e.message}", e)
        }
    }
}

/**
 * Statistics about the recorded flight path
 */
data class PathStatistics(
    val pointCount: Int,
    val totalDistanceMeters: Double,
    val durationMillis: Long
) {
    val totalDistanceNm: Double get() = totalDistanceMeters / 1852.0
    val totalDistanceKm: Double get() = totalDistanceMeters / 1000.0
    val durationMinutes: Double get() = durationMillis / 60000.0
    val durationHours: Double get() = durationMillis / 3600000.0
}
