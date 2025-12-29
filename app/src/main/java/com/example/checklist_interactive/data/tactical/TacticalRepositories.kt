package com.example.checklist_interactive.data.tactical

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import kotlin.math.*

/**
 * Repository for location/marker operations
 * Follows clean architecture pattern with interface
 */
interface LocationRepository {
    fun getAllLocations(): Flow<List<LocationEntity>>
    fun getLocationsByType(type: String): Flow<List<LocationEntity>>
    fun getLocationsByCoalition(coalition: String): Flow<List<LocationEntity>>
    fun searchLocations(query: String): Flow<List<LocationEntity>>
    fun getAllMaps(): Flow<List<String>>
    suspend fun getLocationById(id: Int): LocationEntity?
    fun observeLocationById(id: Int): Flow<LocationEntity?>
    suspend fun saveLocation(location: LocationEntity): Long
    suspend fun updateLocation(location: LocationEntity)
    suspend fun deleteLocation(id: Int)
    suspend fun getNearbyLocations(latitude: Double, longitude: Double, radiusKm: Double): List<Pair<LocationEntity, Double>>
}

/**
 * Repository for route operations
 */
interface RouteRepository {
    fun getAllRoutes(): Flow<List<RouteEntity>>
    fun getAllRoutesWithWaypoints(): Flow<List<RouteWithWaypoints>>
    suspend fun getRouteById(id: Int): RouteEntity?
    suspend fun getRouteWithWaypoints(id: Int): RouteWithWaypoints?
    fun getRouteWaypointsWithLocations(routeId: Int): Flow<List<WaypointWithLocation>>
    suspend fun saveRoute(route: RouteEntity): Long
    suspend fun updateRoute(route: RouteEntity)
    suspend fun deleteRoute(id: Int)
    suspend fun saveRouteWithWaypoints(route: RouteEntity, locationIds: List<Int>): Long
    suspend fun updateRouteWaypoints(routeId: Int, locationIds: List<Int>)
}

/**
 * Repository for border operations
 */
interface BorderRepository {
    fun getAllBorders(): Flow<List<BorderEntity>>
    suspend fun getBorderById(id: Int): BorderEntity?
    suspend fun saveBorder(border: BorderEntity): Long
    suspend fun updateBorder(border: BorderEntity)
    suspend fun deleteBorder(id: Int)
}

/**
 * Navigation utilities for distance and bearing calculations
 */
object NavigationUtils {
    /**
     * Calculate distance between two points using Haversine formula
     * @return Distance in kilometers
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Earth radius in km
        
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(deltaLat / 2).pow(2) + 
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return R * c
    }
    
    /**
     * Calculate initial bearing from point 1 to point 2
     * @return Bearing in degrees (0-360)
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLon = Math.toRadians(lon2 - lon1)
        
        val x = sin(deltaLon) * cos(lat2Rad)
        val y = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLon)
        
        val bearing = Math.toDegrees(atan2(x, y))
        return (bearing + 360) % 360
    }
    
    /**
     * Convert kilometers to nautical miles
     */
    fun kmToNauticalMiles(km: Double): Double = km * 0.539957
    
    /**
     * Convert nautical miles to kilometers
     */
    fun nauticalMilesToKm(nm: Double): Double = nm / 0.539957
}

/**
 * Implementation of LocationRepository
 */
class LocationRepositoryImpl(
    private val locationDao: LocationDao
) : LocationRepository {
    
    override fun getAllLocations(): Flow<List<LocationEntity>> = 
        locationDao.getAllLocations()
    
    override fun getLocationsByType(type: String): Flow<List<LocationEntity>> =
        locationDao.getLocationsByType(type)
    
    override fun getLocationsByCoalition(coalition: String): Flow<List<LocationEntity>> =
        locationDao.getLocationsByCoalition(coalition)
    
    override fun searchLocations(query: String): Flow<List<LocationEntity>> =
        locationDao.searchLocations(query)
    
    override fun getAllMaps(): Flow<List<String>> =
        locationDao.getAllMaps()
    
    override suspend fun getLocationById(id: Int): LocationEntity? =
        locationDao.getLocationById(id)

    override fun observeLocationById(id: Int): Flow<LocationEntity?> =
        locationDao.observeLocationById(id)

    override suspend fun saveLocation(location: LocationEntity): Long {
        val now = Instant.now().toString()
        val newLocation = location.copy(
            created = if (location.created.isEmpty()) now else location.created,
            modified = now
        )
        return locationDao.insertLocation(newLocation)
    }
    
    override suspend fun updateLocation(location: LocationEntity) {
        val updated = location.copy(modified = Instant.now().toString())
        locationDao.updateLocation(updated)
    }
    
    override suspend fun deleteLocation(id: Int) {
        locationDao.deleteLocationById(id)
    }
    
    override suspend fun getNearbyLocations(
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): List<Pair<LocationEntity, Double>> {
        // Get all locations and filter by distance
        // Note: This is not optimal for large datasets - consider spatial index
        val allLocations = locationDao.getAllLocations()
        
        // Since Flow is async, we need to collect it first
        // For now, return empty list - implement proper async handling in real app
        return emptyList()
    }
}

/**
 * Implementation of RouteRepository
 */
class RouteRepositoryImpl(
    private val routeDao: RouteDao,
    private val locationDao: LocationDao
) : RouteRepository {
    
    override fun getAllRoutes(): Flow<List<RouteEntity>> =
        routeDao.getAllRoutes()
    
    override fun getAllRoutesWithWaypoints(): Flow<List<RouteWithWaypoints>> =
        routeDao.getAllRoutesWithWaypoints()
    
    override suspend fun getRouteById(id: Int): RouteEntity? =
        routeDao.getRouteById(id)
    
    override suspend fun getRouteWithWaypoints(id: Int): RouteWithWaypoints? =
        routeDao.getRouteWithWaypoints(id)
    
    override fun getRouteWaypointsWithLocations(routeId: Int): Flow<List<WaypointWithLocation>> =
        routeDao.getRouteWaypointsWithLocations(routeId)
    
    override suspend fun saveRoute(route: RouteEntity): Long {
        val now = Instant.now().toString()
        val newRoute = route.copy(
            created = if (route.created.isEmpty()) now else route.created,
            modified = now
        )
        return routeDao.insertRoute(newRoute)
    }
    
    override suspend fun updateRoute(route: RouteEntity) {
        val updated = route.copy(modified = Instant.now().toString())
        routeDao.updateRoute(updated)
    }
    
    override suspend fun deleteRoute(id: Int) {
        routeDao.deleteRouteById(id)
    }
    
    override suspend fun saveRouteWithWaypoints(route: RouteEntity, locationIds: List<Int>): Long {
        val now = Instant.now().toString()
        val newRoute = route.copy(
            created = if (route.created.isEmpty()) now else route.created,
            modified = now
        )
        val routeId = routeDao.insertRoute(newRoute)
        
        // Create waypoints with distance and bearing calculations
        val waypoints = mutableListOf<RouteWaypointEntity>()
        for (i in locationIds.indices) {
            val locationId = locationIds[i]
            var distanceNm: Double? = null
            var headingMag: Double? = null
            
            // Calculate distance and bearing to next waypoint
            if (i < locationIds.size - 1) {
                val loc1 = locationDao.getLocationById(locationId)
                val loc2 = locationDao.getLocationById(locationIds[i + 1])
                
                if (loc1 != null && loc2 != null) {
                    val distanceKm = NavigationUtils.calculateDistance(
                        loc1.latitude, loc1.longitude,
                        loc2.latitude, loc2.longitude
                    )
                    distanceNm = NavigationUtils.kmToNauticalMiles(distanceKm)
                    headingMag = NavigationUtils.calculateBearing(
                        loc1.latitude, loc1.longitude,
                        loc2.latitude, loc2.longitude
                    )
                }
            }
            
            waypoints.add(
                RouteWaypointEntity(
                    routeId = routeId.toInt(),
                    locationId = locationId,
                    sequence = i,
                    distanceNm = distanceNm,
                    headingMag = headingMag
                )
            )
        }
        
        routeDao.insertWaypoints(waypoints)
        return routeId
    }
    
    override suspend fun updateRouteWaypoints(routeId: Int, locationIds: List<Int>) {
        // Delete existing waypoints
        routeDao.deleteRouteWaypoints(routeId)
        
        // Create new waypoints with calculations
        val waypoints = mutableListOf<RouteWaypointEntity>()
        for (i in locationIds.indices) {
            val locationId = locationIds[i]
            var distanceNm: Double? = null
            var headingMag: Double? = null
            
            if (i < locationIds.size - 1) {
                val loc1 = locationDao.getLocationById(locationId)
                val loc2 = locationDao.getLocationById(locationIds[i + 1])
                
                if (loc1 != null && loc2 != null) {
                    val distanceKm = NavigationUtils.calculateDistance(
                        loc1.latitude, loc1.longitude,
                        loc2.latitude, loc2.longitude
                    )
                    distanceNm = NavigationUtils.kmToNauticalMiles(distanceKm)
                    headingMag = NavigationUtils.calculateBearing(
                        loc1.latitude, loc1.longitude,
                        loc2.latitude, loc2.longitude
                    )
                }
            }
            
            waypoints.add(
                RouteWaypointEntity(
                    routeId = routeId,
                    locationId = locationId,
                    sequence = i,
                    distanceNm = distanceNm,
                    headingMag = headingMag
                )
            )
        }
        
        routeDao.insertWaypoints(waypoints)
        
        // Update route modified timestamp
        val route = routeDao.getRouteById(routeId)
        if (route != null) {
            routeDao.updateRoute(route.copy(modified = Instant.now().toString()))
        }
    }
}

/**
 * Implementation of BorderRepository
 */
class BorderRepositoryImpl(
    private val borderDao: BorderDao
) : BorderRepository {
    
    override fun getAllBorders(): Flow<List<BorderEntity>> =
        borderDao.getAllBorders()
    
    override suspend fun getBorderById(id: Int): BorderEntity? =
        borderDao.getBorderById(id)
    
    override suspend fun saveBorder(border: BorderEntity): Long {
        val now = Instant.now().toString()
        val newBorder = border.copy(
            created = if (border.created.isEmpty()) now else border.created,
            modified = now
        )
        return borderDao.insertBorder(newBorder)
    }
    
    override suspend fun updateBorder(border: BorderEntity) {
        val updated = border.copy(modified = Instant.now().toString())
        borderDao.updateBorder(updated)
    }
    
    override suspend fun deleteBorder(id: Int) {
        borderDao.deleteBorderById(id)
    }
}
