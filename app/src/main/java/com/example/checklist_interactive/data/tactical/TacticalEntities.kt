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
        Index(value = ["name"]),
        Index(value = ["icao"]),
        Index(value = ["country"]),
        Index(value = ["verified"]),
        Index(value = ["map"])
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
    
    @ColumnInfo(defaultValue = "'default'")
    val icon: String = "default",
    
    @ColumnInfo(defaultValue = "''")
    val description: String = "",
    
    // NATO Military Symbol fields
    @ColumnInfo(name = "symbol_set", defaultValue = "''")
    val symbolSet: String = "",  // e.g., "ground_unit", "equipment", "installation"
    
    @ColumnInfo(name = "symbol_entity", defaultValue = "''")
    val symbolEntity: String = "",  // e.g., "infantry", "armor", "artillery", "mortar", "missile"
    
    @ColumnInfo(name = "symbol_size", defaultValue = "''")
    val symbolSize: String = "",  // e.g., "squad", "platoon", "company", "battalion", "regiment"
    
    @ColumnInfo(name = "symbol_affiliation", defaultValue = "'unknown'")
    val symbolAffiliation: String = "unknown",  // "friendly", "hostile", "neutral", "unknown"
    
    @ColumnInfo(name = "symbol_color", defaultValue = "'#FFFF80'")
    val symbolColor: String = "#FFFF80",  // Background color based on affiliation
    
    @ColumnInfo(name = "symbol_modifier", defaultValue = "''")
    val symbolModifier: String = "",  // Additional symbol modifiers (JSON)
    
    // Static marker flag (for non-moving entities like airports, installations)
    @ColumnInfo(name = "is_static", defaultValue = "0")
    val isStatic: Int = 0,  // 0=dynamic/mobile, 1=static/fixed
    
    // Airport fields
    val icao: String? = null,
    val iata: String? = null,
    
    @ColumnInfo(name = "elevation_m")
    val elevationM: Double? = null,
    
    @ColumnInfo(name = "elevation_ft", defaultValue = "0")
    val elevationFt: Int? = null,
    
    val frequencies: String? = null,  // JSON
    val runways: String? = null,  // JSON (deprecated - use runways table)
    
    // Tactical fields
    @ColumnInfo(name = "threat_level")
    val threatLevel: Int? = null,
    
    @ColumnInfo(name = "unit_type")
    val unitType: String? = null,
    
    val strength: Int? = null,
    
    // Geography & admin
    val country: String? = null,
    val region: String? = null,
    val timezone: String? = null,  // IANA timezone
    
    // Source & verification
    val source: String? = null,
    
    @ColumnInfo(defaultValue = "0")
    val verified: Int? = 0,  // 0=no, 1=yes
    
    @ColumnInfo(name = "last_verified_at")
    val lastVerifiedAt: String? = null,
    
    // Geometry (GeoJSON for complex shapes)
    val geom: String? = null,
    
    // Elevation details
    @ColumnInfo(name = "elevation_source")
    val elevationSource: String? = null,
    
    @ColumnInfo(name = "elevation_accuracy_m")
    val elevationAccuracyM: Double? = null,
    
    // Metadata (legacy, kept for compatibility)
    val created: String = "",
    val modified: String = "",
    val tags: String? = null,  // JSON (deprecated - use tags table)
    val metadata: String? = null,  // JSON
    
    // Audit fields (new)
    @ColumnInfo(name = "created_at")
    val createdAt: String? = null,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: String? = null,
    
    @ColumnInfo(name = "deleted_at")
    val deletedAt: String? = null,  // Soft delete
    
    // DCS Map identifier (for filtering markers by map)
    @ColumnInfo(name = "map")
    val map: String? = null  // e.g., "Caucasus", "Syria", "Persian Gulf", "Nevada"
)

/**
 * Runway entity - airport runway details
 */
@Entity(
    tableName = "runways",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["location_id"])]
)
data class RunwayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int? = 0,
    
    @ColumnInfo(name = "location_id")
    val locationId: Int,
    
    val name: String,  // e.g., "09/27", "RWY 13L"
    
    @ColumnInfo(name = "length_m")
    val lengthM: Int? = null,
    
    @ColumnInfo(name = "length_ft")
    val lengthFt: Int? = null,
    
    @ColumnInfo(name = "width_m")
    val widthM: Int? = null,
    
    @ColumnInfo(name = "width_ft")
    val widthFt: Int? = null,
    
    val surface: String? = null,  // concrete, asphalt, grass, dirt
    
    @ColumnInfo(name = "heading_deg")
    val headingDeg: Double? = null,
    
    @ColumnInfo(name = "ils_frequency")
    val ilsFrequency: String? = null,
    
    @ColumnInfo(name = "has_lighting", defaultValue = "0")
    val hasLighting: Int? = 0,  // 0=no, 1=yes
    
    @ColumnInfo(name = "touchdown_start_lat")
    val touchdownStartLat: Double? = null,
    
    @ColumnInfo(name = "touchdown_start_lon")
    val touchdownStartLon: Double? = null,
    
    @ColumnInfo(name = "touchdown_end_lat")
    val touchdownEndLat: Double? = null,
    
    @ColumnInfo(name = "touchdown_end_lon")
    val touchdownEndLon: Double? = null,
    
    val notes: String? = null
)

/**
 * Service entity - airport/location services (fuel, maintenance, etc.)
 */
@Entity(
    tableName = "services",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["location_id"]),
        Index(value = ["service_type"])
    ]
)
data class ServiceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int? = 0,
    
    @ColumnInfo(name = "location_id")
    val locationId: Int,
    
    @ColumnInfo(name = "service_type")
    val serviceType: String,  // fuel, maintenance, arming, etc.
    
    @ColumnInfo(defaultValue = "1")
    val available: Int? = 1,  // 0=unavailable, 1=available
    val details: String? = null  // JSON details
)

/**
 * Media entity - images, diagrams, PDFs
 */
@Entity(
    tableName = "media",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["location_id"])]
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int? = 0,
    
    @ColumnInfo(name = "location_id")
    val locationId: Int,
    
    val uri: String,
    val caption: String? = null,
    
    @ColumnInfo(name = "media_type")
    val mediaType: String? = null,  // image, diagram, pdf, video
    
    @ColumnInfo(name = "is_primary", defaultValue = "0")
    val isPrimary: Int? = 0,  // 0=no, 1=primary
    
    @ColumnInfo(name = "created_at")
    val createdAt: String? = null
)

/**
 * Tag entity - flexible categorization
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    val name: String  // e.g., "airbase", "FARP", "civilian"
)

/**
 * Location-Tag join entity (many-to-many)
 */
@Entity(
    tableName = "location_tags",
    primaryKeys = ["location_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["location_id"]),
        Index(value = ["tag_id"])
    ]
)
data class LocationTagCrossRef(
    @ColumnInfo(name = "location_id")
    val locationId: Int,
    
    @ColumnInfo(name = "tag_id")
    val tagId: Int
)

/**
 * Navaid entity - VOR, NDB, ILS, TACAN, DME
 */
@Entity(
    tableName = "navaids",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["location_id"]),
        Index(value = ["type"]),
        Index(value = ["ident"])
    ]
)
data class NavaidEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "location_id")
    val locationId: Int? = null,  // Optional link to location
    
    val name: String,
    val type: String,  // VOR, NDB, ILS, TACAN, DME, VORTAC
    val ident: String? = null,  // 3-letter identifier
    val frequency: String? = null,
    
    val latitude: Double,
    val longitude: Double,
    
    @ColumnInfo(name = "elevation_m")
    val elevationM: Double? = null,
    
    @ColumnInfo(name = "range_nm")
    val rangeNm: Double? = null,
    
    @ColumnInfo(name = "bearing_deg")
    val bearingDeg: Double? = null,
    
    val notes: String? = null
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

/**
 * Map drawing entity - stores freehand drawings on the map
 */
@Entity(
    tableName = "map_drawings",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["map_region"])
    ]
)
data class MapDrawingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "map_region")
    val mapRegion: String? = null,  // Optional region/area identifier for organizing drawings
    
    val color: Long,  // ARGB color value
    
    @ColumnInfo(name = "stroke_width")
    val strokeWidth: Float,
    
    @ColumnInfo(name = "brush_type")
    val brushType: String,  // "pen", "marker", "special"
    
    val points: String,  // JSON array of [lat, lon] coordinate pairs
    
    @ColumnInfo(name = "is_highlight", defaultValue = "0")
    val isHighlight: Int = 0,  // 0=no, 1=yes (for semi-transparent marker strokes)
    
    @ColumnInfo(name = "created_at")
    val createdAt: String,  // ISO 8601 timestamp
    
    @ColumnInfo(name = "modified_at")
    val modifiedAt: String  // ISO 8601 timestamp
)

/**
 * Tactical Unit entity - stores tracked units from DCS World
 * (aircraft, helicopters, ground units, ships, structures)
 */
@Entity(
    tableName = "tactical_units",
    indices = [
        Index(value = ["dcs_id"], unique = true),
        Index(value = ["category"]),
        Index(value = ["coalition"]),
        Index(value = ["is_active"]),
        Index(value = ["last_seen_at"])
    ]
)
data class TacticalUnitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "dcs_id")
    val dcsId: String,  // Unique DCS object ID
    
    val name: String,  // Unit display name (e.g., "MiG-29", "T-80")
    val type: String,  // Detailed type (e.g., "MiG-29A", "T-80BV")
    val category: String,  // aircraft, helicopter, ground, ship, structure, weapon
    val coalition: Int,  // 0=Neutral, 1=Red, 2=Blue
    
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    
    val heading: Double? = null,  // 0-360 degrees
    val speed: Double? = null,  // m/s
    
    val distance: Double? = null,  // Distance to player (meters)
    val bearing: Double? = null,  // Bearing to unit (0-360 degrees)

    val health: Double? = null,  // Unit health (0.0-1.0, 1.0 = full health)

    val country: Int? = null,  // DCS country code
    @ColumnInfo(name = "group_name")
    val groupName: String? = null,  // Group name
    @ColumnInfo(name = "pilot_name")
    val pilotName: String? = null,  // Pilot/unit name

    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Int = 1,  // 1=currently visible, 0=contact lost
    
    @ColumnInfo(name = "is_hidden", defaultValue = "0")
    val isHidden: Int = 0,  // 1=hidden from map/list, 0=visible (allows unhiding old units for review)
    
    @ColumnInfo(name = "first_seen_at")
    val firstSeenAt: String,  // ISO 8601 timestamp
    
    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: String,  // ISO 8601 timestamp (last contact)
    
    @ColumnInfo(name = "last_update_at")
    val lastUpdateAt: String  // ISO 8601 timestamp (last position update)
)

/**
 * Tactical Unit History entity - stores position history for track replay
 */
@Entity(
    tableName = "tactical_unit_history",
    foreignKeys = [
        ForeignKey(
            entity = TacticalUnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["unit_id"]),
        Index(value = ["timestamp"])
    ]
)
data class TacticalUnitHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "unit_id")
    val unitId: Int,  // Foreign key to TacticalUnitEntity
    
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    
    val heading: Double? = null,
    val speed: Double? = null,
    
    val timestamp: String  // ISO 8601 timestamp
)
