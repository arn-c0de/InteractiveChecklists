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
    
    @Query("SELECT * FROM locations WHERE map = :mapName ORDER BY name")
    fun getLocationsByMap(mapName: String): Flow<List<LocationEntity>>
    
    @Query("SELECT DISTINCT map FROM locations WHERE map IS NOT NULL ORDER BY map")
    fun getAllMaps(): Flow<List<String>>
    
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

/**
 * DAO for Runway operations
 */
@Dao
interface RunwayDao {
    @Query("SELECT * FROM runways WHERE location_id = :locationId ORDER BY name")
    fun getRunwaysByLocation(locationId: Int): Flow<List<RunwayEntity>>
    
    @Query("SELECT * FROM runways WHERE location_id = :locationId ORDER BY name")
    suspend fun getRunwaysByLocationSync(locationId: Int): List<RunwayEntity>
    
    @Query("SELECT * FROM runways WHERE id = :id")
    suspend fun getRunwayById(id: Int): RunwayEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRunway(runway: RunwayEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRunways(runways: List<RunwayEntity>)
    
    @Update
    suspend fun updateRunway(runway: RunwayEntity)
    
    @Delete
    suspend fun deleteRunway(runway: RunwayEntity)
    
    @Query("DELETE FROM runways WHERE id = :id")
    suspend fun deleteRunwayById(id: Int)
    
    @Query("DELETE FROM runways WHERE location_id = :locationId")
    suspend fun deleteRunwaysByLocation(locationId: Int)
}

/**
 * DAO for Service operations
 */
@Dao
interface ServiceDao {
    @Query("SELECT * FROM services WHERE location_id = :locationId")
    fun getServicesByLocation(locationId: Int): Flow<List<ServiceEntity>>
    
    @Query("SELECT * FROM services WHERE service_type = :type")
    fun getServicesByType(type: String): Flow<List<ServiceEntity>>
    
    @Query("SELECT * FROM services WHERE id = :id")
    suspend fun getServiceById(id: Int): ServiceEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertService(service: ServiceEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServices(services: List<ServiceEntity>)
    
    @Update
    suspend fun updateService(service: ServiceEntity)
    
    @Delete
    suspend fun deleteService(service: ServiceEntity)
    
    @Query("DELETE FROM services WHERE id = :id")
    suspend fun deleteServiceById(id: Int)
}

/**
 * DAO for Media operations
 */
@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE location_id = :locationId ORDER BY is_primary DESC, created_at DESC")
    fun getMediaByLocation(locationId: Int): Flow<List<MediaEntity>>
    
    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getMediaById(id: Int): MediaEntity?
    
    @Query("SELECT * FROM media WHERE location_id = :locationId AND is_primary = 1 LIMIT 1")
    suspend fun getPrimaryMediaForLocation(locationId: Int): MediaEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaList(mediaList: List<MediaEntity>)
    
    @Update
    suspend fun updateMedia(media: MediaEntity)
    
    @Delete
    suspend fun deleteMedia(media: MediaEntity)
    
    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteMediaById(id: Int)
}

/**
 * DAO for Tag operations
 */
@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name")
    fun getAllTags(): Flow<List<TagEntity>>
    
    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getTagById(id: Int): TagEntity?
    
    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun getTagByName(name: String): TagEntity?
    
    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN location_tags lt ON t.id = lt.tag_id
        WHERE lt.location_id = :locationId
        ORDER BY t.name
    """)
    fun getTagsForLocation(locationId: Int): Flow<List<TagEntity>>
    
    @Query("""
        SELECT l.* FROM locations l
        INNER JOIN location_tags lt ON l.id = lt.location_id
        WHERE lt.tag_id = :tagId
        ORDER BY l.name
    """)
    fun getLocationsForTag(tagId: Int): Flow<List<LocationEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity): Long
    
    @Update
    suspend fun updateTag(tag: TagEntity)
    
    @Delete
    suspend fun deleteTag(tag: TagEntity)
    
    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTagById(id: Int)
    
    // Location-Tag associations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationTag(crossRef: LocationTagCrossRef)
    
    @Delete
    suspend fun deleteLocationTag(crossRef: LocationTagCrossRef)
    
    @Query("DELETE FROM location_tags WHERE location_id = :locationId")
    suspend fun deleteAllTagsForLocation(locationId: Int)
    
    @Query("DELETE FROM location_tags WHERE tag_id = :tagId")
    suspend fun deleteAllLocationsForTag(tagId: Int)
}

/**
 * DAO for Navaid operations
 */
@Dao
interface NavaidDao {
    @Query("SELECT * FROM navaids ORDER BY name")
    fun getAllNavaids(): Flow<List<NavaidEntity>>
    
    @Query("SELECT * FROM navaids WHERE location_id = :locationId")
    fun getNavaidsByLocation(locationId: Int): Flow<List<NavaidEntity>>
    
    @Query("SELECT * FROM navaids WHERE type = :type ORDER BY name")
    fun getNavaidsByType(type: String): Flow<List<NavaidEntity>>
    
    @Query("SELECT * FROM navaids WHERE ident = :ident")
    suspend fun getNavaidByIdent(ident: String): NavaidEntity?
    
    @Query("SELECT * FROM navaids WHERE id = :id")
    suspend fun getNavaidById(id: Int): NavaidEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNavaid(navaid: NavaidEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNavaids(navaids: List<NavaidEntity>)
    
    @Update
    suspend fun updateNavaid(navaid: NavaidEntity)
    
    @Delete
    suspend fun deleteNavaid(navaid: NavaidEntity)
    
    @Query("DELETE FROM navaids WHERE id = :id")
    suspend fun deleteNavaidById(id: Int)
}

/**
 * DAO for Map Drawing operations
 */
@Dao
interface MapDrawingDao {
    @Query("SELECT * FROM map_drawings ORDER BY created_at DESC")
    fun getAllDrawings(): Flow<List<MapDrawingEntity>>
    
    @Query("SELECT * FROM map_drawings WHERE id = :id")
    suspend fun getDrawingById(id: Int): MapDrawingEntity?
    
    @Query("SELECT * FROM map_drawings WHERE map_region = :region ORDER BY created_at DESC")
    fun getDrawingsByRegion(region: String): Flow<List<MapDrawingEntity>>
    
    @Query("""
        SELECT * FROM map_drawings 
        WHERE map_region IS NULL OR map_region = :region
        ORDER BY created_at DESC
    """)
    fun getAllDrawingsForRegion(region: String?): Flow<List<MapDrawingEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrawing(drawing: MapDrawingEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrawings(drawings: List<MapDrawingEntity>)
    
    @Update
    suspend fun updateDrawing(drawing: MapDrawingEntity)
    
    @Delete
    suspend fun deleteDrawing(drawing: MapDrawingEntity)
    
    @Query("DELETE FROM map_drawings WHERE id = :id")
    suspend fun deleteDrawingById(id: Int)
    
    @Query("DELETE FROM map_drawings")
    suspend fun deleteAllDrawings()
}

/**
 * DAO for Tactical Units operations
 */
@Dao
interface TacticalUnitsDao {
    // --- SELECT queries ---
    
    @Query("SELECT * FROM tactical_units ORDER BY last_seen_at DESC")
    fun getAllUnits(): Flow<List<TacticalUnitEntity>>
    
    @Query("SELECT * FROM tactical_units WHERE is_active = 1 ORDER BY last_seen_at DESC")
    fun getAllActiveUnits(): Flow<List<TacticalUnitEntity>>
    
    @Query("SELECT * FROM tactical_units WHERE is_active = 1 AND datetime(last_seen_at) > datetime('now', '-10 seconds') ORDER BY last_seen_at DESC")
    fun getLiveUnits(): Flow<List<TacticalUnitEntity>>
    
    @Query("SELECT * FROM tactical_units WHERE is_active = 0 ORDER BY last_seen_at DESC")
    fun getAllInactiveUnits(): Flow<List<TacticalUnitEntity>>
    
    @Query("SELECT * FROM tactical_units WHERE id = :id")
    suspend fun getUnitById(id: Int): TacticalUnitEntity?
    
    @Query("SELECT * FROM tactical_units WHERE dcs_id = :dcsId")
    suspend fun getUnitByDcsId(dcsId: String): TacticalUnitEntity?
    
    @Query("SELECT * FROM tactical_units WHERE category = :category ORDER BY last_seen_at DESC")
    fun getUnitsByCategory(category: String): Flow<List<TacticalUnitEntity>>
    
    @Query("SELECT * FROM tactical_units WHERE coalition = :coalition ORDER BY last_seen_at DESC")
    fun getUnitsByCoalition(coalition: Int): Flow<List<TacticalUnitEntity>>
    
    @Query("""
        SELECT * FROM tactical_units 
        WHERE category = :category AND coalition = :coalition 
        ORDER BY last_seen_at DESC
    """)
    fun getUnitsByCategoryAndCoalition(category: String, coalition: Int): Flow<List<TacticalUnitEntity>>
    
    @Query("""
        SELECT * FROM tactical_units 
        WHERE (name LIKE '%' || :query || '%' OR group_name LIKE '%' || :query || '%')
        ORDER BY last_seen_at DESC
    """)
    fun searchUnits(query: String): Flow<List<TacticalUnitEntity>>
    
    // --- Statistics ---
    
    @Query("SELECT COUNT(*) FROM tactical_units WHERE is_active = 1")
    suspend fun getActiveUnitCount(): Int
    
    @Query("SELECT COUNT(*) FROM tactical_units WHERE is_active = 1 AND category = :category")
    suspend fun getActiveUnitCountByCategory(category: String): Int
    
    @Query("SELECT COUNT(*) FROM tactical_units WHERE is_active = 1 AND coalition = :coalition")
    suspend fun getActiveUnitCountByCoalition(coalition: Int): Int
    
    // --- INSERT / UPDATE ---
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnit(unit: TacticalUnitEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnits(units: List<TacticalUnitEntity>)
    
    @Update
    suspend fun updateUnit(unit: TacticalUnitEntity)
    
    @Query("""
        UPDATE tactical_units
        SET latitude = :latitude, longitude = :longitude, altitude = :altitude,
            heading = :heading, speed = :speed, distance = :distance, bearing = :bearing,
            category = :category,
            is_active = 1, last_seen_at = :lastSeenAt, last_update_at = :lastUpdateAt
        WHERE dcs_id = :dcsId
    """)
    suspend fun updateUnitPosition(
        dcsId: String,
        latitude: Double,
        longitude: Double,
        altitude: Double,
        heading: Double?,
        speed: Double?,
        distance: Double?,
        bearing: Double?,
        category: String,
        lastSeenAt: String,
        lastUpdateAt: String
    ): Int
    
    @Query("UPDATE tactical_units SET is_active = 0 WHERE dcs_id = :dcsId")
    suspend fun markUnitInactive(dcsId: String)
    
    @Query("UPDATE tactical_units SET is_active = 0 WHERE is_active = 1")
    suspend fun markAllUnitsInactive()
    
    // --- DELETE ---
    
    @Delete
    suspend fun deleteUnit(unit: TacticalUnitEntity)
    
    @Query("DELETE FROM tactical_units WHERE id = :id")
    suspend fun deleteUnitById(id: Int)
    
    @Query("DELETE FROM tactical_units WHERE dcs_id = :dcsId")
    suspend fun deleteUnitByDcsId(dcsId: String)
    
    @Query("DELETE FROM tactical_units WHERE is_active = 0 AND last_seen_at < :cutoffTime")
    suspend fun deleteInactiveUnitsOlderThan(cutoffTime: String)

    @Query("DELETE FROM tactical_units WHERE is_active = 0")
    suspend fun deleteAllInactiveUnits()

    @Query("DELETE FROM tactical_units WHERE last_seen_at < :cutoffTime")
    suspend fun deleteUnitsOlderThan(cutoffTime: String)

    @Query("DELETE FROM tactical_units")
    suspend fun deleteAllUnits()
}

/**
 * DAO for Tactical Unit History operations
 */
@Dao
interface TacticalUnitHistoryDao {
    @Query("SELECT * FROM tactical_unit_history WHERE unit_id = :unitId ORDER BY timestamp DESC")
    fun getHistoryForUnit(unitId: Int): Flow<List<TacticalUnitHistoryEntity>>
    
    @Query("SELECT * FROM tactical_unit_history WHERE unit_id = :unitId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistoryForUnit(unitId: Int, limit: Int = 100): List<TacticalUnitHistoryEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: TacticalUnitHistoryEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistories(histories: List<TacticalUnitHistoryEntity>)
    
    @Query("DELETE FROM tactical_unit_history WHERE unit_id = :unitId")
    suspend fun deleteHistoryForUnit(unitId: Int)
    
    @Query("DELETE FROM tactical_unit_history WHERE timestamp < :cutoffTime")
    suspend fun deleteHistoryOlderThan(cutoffTime: String)
    
    @Query("DELETE FROM tactical_unit_history")
    suspend fun deleteAllHistory()
}

