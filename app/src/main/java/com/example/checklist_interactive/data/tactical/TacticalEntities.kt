package com.example.checklist_interactive.data.tactical

import androidx.room.*

/**
 * Location/Marker entity - stores airports, waypoints, tactical units, etc.
 */
@Entity(
    tableName = "locations",
    indices = [
        Index(value = ["marker_type"]),
        Index(value = ["coalition"]),
        Index(value = ["latitude", "longitude"]),
        Index(value = ["name"])
    ]
)
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    val name: String,
    val latitude: Double,
    val longitude: Double,
    
    @ColumnInfo(name = "marker_type")
    val markerType: String,
    
    val coalition: String? = null,
    
    @ColumnInfo(name = "tactical_symbol")
    val tacticalSymbol: String? = null,
    
    val icon: String = "default",
    val description: String = "",
    
    // Airport fields
    val icao: String? = null,
    val iata: String? = null,
    
    @ColumnInfo(name = "elevation_m")
    val elevationM: Double? = null,
    
    val frequencies: String? = null,  // JSON
    val runways: String? = null,  // JSON
    
    // Tactical fields
    @ColumnInfo(name = "threat_level")
    val threatLevel: Int? = null,
    
    @ColumnInfo(name = "unit_type")
    val unitType: String? = null,
    
    val strength: Int? = null,
    
    // Metadata
    val created: String,
    val modified: String,
    val tags: String? = null,  // JSON
    val metadata: String? = null  // JSON
)

/**
 * Border/Region boundary entity
 */
@Entity(
    tableName = "borders",
    indices = [Index(value = ["name"])]
)
data class BorderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    val name: String,
    val points: String,  // JSON array of [lat, lon]
    val description: String = "",
    val color: String = "#FF0000",
    val created: String,
    val modified: String
)

/**
 * Route entity - flight route/path
 */
@Entity(
    tableName = "routes",
    indices = [Index(value = ["name"])]
)
data class RouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    val name: String,
    val description: String = "",
    val color: String = "#00A8FF",
    val created: String,
    val modified: String
)

/**
 * Route waypoint entity - links routes to locations with navigation data
 */
@Entity(
    tableName = "route_waypoints",
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["route_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["route_id"]),
        Index(value = ["route_id", "sequence"]),
        Index(value = ["location_id"])
    ]
)
data class RouteWaypointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "route_id")
    val routeId: Int,
    
    @ColumnInfo(name = "location_id")
    val locationId: Int,
    
    val sequence: Int,
    
    @ColumnInfo(name = "distance_nm")
    val distanceNm: Double? = null,
    
    @ColumnInfo(name = "heading_mag")
    val headingMag: Double? = null
)

/**
 * Route with waypoints - for queries
 */
data class RouteWithWaypoints(
    @Embedded val route: RouteEntity,
    
    @Relation(
        entity = RouteWaypointEntity::class,
        parentColumn = "id",
        entityColumn = "route_id"
    )
    val waypoints: List<WaypointWithLocation>
)

/**
 * Waypoint with location data - for nested queries
 */
data class WaypointWithLocation(
    @Embedded val waypoint: RouteWaypointEntity,
    
    @Relation(
        parentColumn = "location_id",
        entityColumn = "id"
    )
    val location: LocationEntity
)
