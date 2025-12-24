package com.example.checklist_interactive.data.tactical

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Entity representing a single point in the aircraft's flight path history
 * Used to render the flight path as a polyline on the map
 */
@Entity(
    tableName = "flight_path_points",
    indices = [androidx.room.Index(value = ["timestamp"])]
)
data class FlightPathPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** Timestamp when this position was recorded (milliseconds since epoch) */
    val timestamp: Long,
    
    /** Latitude in decimal degrees */
    val latitude: Double,
    
    /** Longitude in decimal degrees */
    val longitude: Double,
    
    /** Altitude in meters MSL */
    val altitudeMsl: Double,
    
    /** True heading in degrees (0-360) */
    val heading: Double,
    
    /** Ground speed in meters per second (optional) */
    val groundSpeed: Double? = null,
    
    /** Vertical speed in meters per second (optional, for future visualizations) */
    val verticalSpeed: Double? = null
)

/**
 * DAO for flight path operations
 */
@Dao
interface FlightPathDao {
    /**
     * Insert a new flight path point
     */
    @Insert
    suspend fun insertPoint(point: FlightPathPoint): Long
    
    /**
     * Get all flight path points ordered by timestamp (ascending)
     * Returns as Flow for reactive UI updates
     */
    @Query("SELECT * FROM flight_path_points ORDER BY timestamp ASC")
    fun getAllPointsFlow(): Flow<List<FlightPathPoint>>
    
    /**
     * Get all flight path points ordered by timestamp (non-Flow version for repository)
     */
    @Query("SELECT * FROM flight_path_points ORDER BY timestamp ASC")
    suspend fun getAllPoints(): List<FlightPathPoint>
    
    /**
     * Get the count of recorded points
     */
    @Query("SELECT COUNT(*) FROM flight_path_points")
    suspend fun getPointCount(): Int
    
    /**
     * Get the timestamp of the last recorded point
     * Returns null if no points exist
     */
    @Query("SELECT MAX(timestamp) FROM flight_path_points")
    suspend fun getLastRecordedTime(): Long?
    
    /**
     * Clear all flight path points (reset path)
     */
    @Query("DELETE FROM flight_path_points")
    suspend fun clearAllPoints()
    
    /**
     * Delete old points before a given timestamp (for cleanup/memory management)
     */
    @Query("DELETE FROM flight_path_points WHERE timestamp < :beforeTimestamp")
    suspend fun deletePointsBefore(beforeTimestamp: Long): Int
    
    /**
     * Get points within a time range
     */
    @Query("SELECT * FROM flight_path_points WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    suspend fun getPointsInRange(startTime: Long, endTime: Long): List<FlightPathPoint>
}
