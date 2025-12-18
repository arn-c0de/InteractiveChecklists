package com.example.checklist_interactive.data.tactical

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Location/Marker operations
 */
@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY name")
    fun getAllLocations(): Flow<List<LocationEntity>>
    
    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getLocationById(id: Int): LocationEntity?
    
    @Query("SELECT * FROM locations WHERE marker_type = :type ORDER BY name")
    fun getLocationsByType(type: String): Flow<List<LocationEntity>>
    
    @Query("SELECT * FROM locations WHERE coalition = :coalition ORDER BY name")
    fun getLocationsByCoalition(coalition: String): Flow<List<LocationEntity>>
    
    @Query("SELECT * FROM locations WHERE name LIKE '%' || :query || '%' ORDER BY name")
    fun searchLocations(query: String): Flow<List<LocationEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationEntity): Long
    
    @Update
    suspend fun updateLocation(location: LocationEntity)
    
    @Delete
    suspend fun deleteLocation(location: LocationEntity)
    
    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun deleteLocationById(id: Int)
}

/**
 * DAO for Border operations
 */
@Dao
interface BorderDao {
    @Query("SELECT * FROM borders ORDER BY name")
    fun getAllBorders(): Flow<List<BorderEntity>>
    
    @Query("SELECT * FROM borders WHERE id = :id")
    suspend fun getBorderById(id: Int): BorderEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBorder(border: BorderEntity): Long
    
    @Update
    suspend fun updateBorder(border: BorderEntity)
    
    @Delete
    suspend fun deleteBorder(border: BorderEntity)
    
    @Query("DELETE FROM borders WHERE id = :id")
    suspend fun deleteBorderById(id: Int)
}

/**
 * DAO for Route operations
 */
@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY name")
    fun getAllRoutes(): Flow<List<RouteEntity>>
    
    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun getRouteById(id: Int): RouteEntity?
    
    @Transaction
    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun getRouteWithWaypoints(id: Int): RouteWithWaypoints?
    
    @Transaction
    @Query("SELECT * FROM routes ORDER BY name")
    fun getAllRoutesWithWaypoints(): Flow<List<RouteWithWaypoints>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity): Long
    
    @Update
    suspend fun updateRoute(route: RouteEntity)
    
    @Delete
    suspend fun deleteRoute(route: RouteEntity)
    
    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun deleteRouteById(id: Int)
    
    // Waypoint operations
    @Query("SELECT * FROM route_waypoints WHERE route_id = :routeId ORDER BY sequence")
    suspend fun getRouteWaypoints(routeId: Int): List<RouteWaypointEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoint(waypoint: RouteWaypointEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoints(waypoints: List<RouteWaypointEntity>)
    
    @Update
    suspend fun updateWaypoint(waypoint: RouteWaypointEntity)
    
    @Delete
    suspend fun deleteWaypoint(waypoint: RouteWaypointEntity)
    
    @Query("DELETE FROM route_waypoints WHERE route_id = :routeId")
    suspend fun deleteRouteWaypoints(routeId: Int)
    
    @Transaction
    @Query("""
        SELECT rw.id, rw.route_id, rw.location_id, rw.sequence, rw.distance_nm, rw.heading_mag
        FROM route_waypoints rw
        WHERE rw.route_id = :routeId
        ORDER BY rw.sequence
    """)
    fun getRouteWaypointsWithLocations(routeId: Int): Flow<List<WaypointWithLocation>>
}
