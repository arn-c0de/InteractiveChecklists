# AAA (Anti-Aircraft Artillery) Range Ring System - Complete Documentation

## Overview

The AAA system provides real-time visualization of anti-aircraft threat ranges on the tactical map. It automatically detects AA units from DCS World, matches them against a comprehensive database of AA systems, and displays color-coded range rings showing detection, engagement, and dead zones.

---

## System Architecture

### Data Flow Pipeline

```
DCS World
  ↓
Export.lua (Categorizes units by Type.level1)
  ↓
Android App Database (TacticalUnitEntity)
  ↓
TacticalUnitsRepository.getGroundUnits()
  ↓
AARangeDatabase.getSystemByUnitType() (Pattern matching)
  ↓
AARangeRingsOverlay (Rendering)
  ↓
Map Display
```

### Component Overview

| Component | File | Purpose |
|-----------|------|---------|
| **Data Export** | `Export.lua` | Extracts unit data from DCS World |
| **Data Model** | `TacticalEntities.kt` | Database entity for tactical units |
| **Repository** | `TacticalUnitsRepository.kt` | Data access layer for units |
| **AA Database** | `AARangeData.kt` | AA system specifications database |
| **Overlay** | `AARangeRingsOverlay.kt` | Renders range rings on map |
| **Orchestration** | `MapViewer.kt` | Manages overlay lifecycle |

---

## Part 1: Unit Detection & Categorization

### 1.1 DCS Export Script (Export.lua)

**Location**: `scripts/DCS-SCRIPTS-FOLDER-Experimental/Export.lua`

The Lua script runs inside DCS World and exports unit data every 0.01 seconds (10 Hz).

#### Categorization Logic

Units are categorized using DCS's `Type.level1` field:

```lua
if objData.Type and type(objData.Type) == 'table' then
    local level1 = objData.Type.level1 or 0
    local level3 = objData.Type.level3 or 0

    if level1 == 1 then
        if level3 == 1 then
            categoryName = 'aircraft'
        elseif level3 == 6 then
            categoryName = 'helicopter'
        end
    elseif level1 == 2 then
        categoryName = 'ground'  -- ★ ALL AA SYSTEMS ARE HERE
    elseif level1 == 3 then
        categoryName = 'ship'
    elseif level1 == 4 then
        categoryName = 'structure'
    elseif level1 == 5 then
        categoryName = 'weapon'
    end
end
```

**Key Points:**
- **All ground units** (tanks, APCs, SAMs, AAA, etc.) have `level1 == 2`
- No distinction between AA and non-AA at this stage
- Unit name and type are preserved for later AA detection
- Maximum tracking distance: 150 km (300,000 meters)
- Units sorted by priority: Aircraft/Helicopters first, then by distance

#### Exported Data Structure

Each unit is exported as JSON with these fields:

```json
{
  "dcsId": "123456789",
  "name": "SA-11 Buk SR 9S18M1",
  "type": "Buk 9S18M1 SR",
  "category": "ground",
  "coalition": "Enemies",
  "latitude": 42.123456,
  "longitude": 43.654321,
  "altitude": 123.45,
  "heading": 270.5,
  "speed": 0.0,
  "distance": 5432.1,
  "bearing": 180.5,
  "country": 0,
  "group": "SAM Group 1",
  "pilot": "",
  "health": 1.0
}
```

### 1.2 Database Storage (TacticalEntities.kt)

**Location**: `app/src/main/java/com/example/checklist_interactive/data/tactical/TacticalEntities.kt:505-565`

#### TacticalUnitEntity Schema

```kotlin
@Entity(tableName = "tactical_units")
data class TacticalUnitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "dcs_id")
    val dcsId: String,  // Unique DCS object ID

    val name: String,  // Unit display name (e.g., "SA-11 Buk SR 9S18M1")
    val type: String,  // Detailed type (e.g., "Buk 9S18M1 SR")
    val category: String,  // "aircraft", "helicopter", "ground", "ship", "structure", "weapon"
    val coalition: Int,  // 0=Neutral, 1=Red, 2=Blue

    val latitude: Double,
    val longitude: Double,
    val altitude: Double,

    val heading: Double? = null,  // 0-360 degrees
    val speed: Double? = null,  // m/s

    val distance: Double? = null,  // Distance to player (meters)
    val bearing: Double? = null,  // Bearing to unit (0-360 degrees)
    val health: Double? = null,  // Unit health (0.0-1.0)

    val country: Int? = null,  // DCS country code
    @ColumnInfo(name = "group_name")
    val groupName: String? = null,
    @ColumnInfo(name = "pilot_name")
    val pilotName: String? = null,

    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Int = 1,  // 1=currently visible, 0=contact lost

    @ColumnInfo(name = "is_hidden", defaultValue = "0")
    val isHidden: Int = 0,  // 1=hidden from map/list, 0=visible

    @ColumnInfo(name = "is_highlighted", defaultValue = "0")
    val isHighlighted: Int = 0,  // 1=highlighted on map (larger icon), 0=normal

    @ColumnInfo(name = "show_range_rings", defaultValue = "0")
    val showRangeRings: Int = 0,  // ★ 1=show AA range rings on map, 0=hide

    @ColumnInfo(name = "first_seen_at")
    val firstSeenAt: String,  // ISO 8601 timestamp

    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: String,  // ISO 8601 timestamp (last contact)

    @ColumnInfo(name = "last_update_at")
    val lastUpdateAt: String  // ISO 8601 timestamp (last position update)
)
```

**Key Fields for AA System:**
- `category`: Must be "ground" for AA detection
- `name`: Used for pattern matching against AA database (primary lookup)
- `type`: Fallback for pattern matching if name doesn't match
- `showRangeRings`: User toggle for individual unit range display

### 1.3 Repository Layer (TacticalUnitsRepository.kt)

**Location**: `app/src/main/java/com/example/checklist_interactive/data/tactical/TacticalUnitsRepository.kt:284-286`

#### Ground Units Query

```kotlin
/**
 * Get all ground units (potential AA units)
 */
fun getGroundUnits(): Flow<List<TacticalUnitEntity>> {
    return getUnitsByCategory("ground")
}
```

**Important**: This returns **ALL** ground units, not just AA systems. The AA detection happens in the next stage via pattern matching.

---

## Part 2: AA System Database & Pattern Matching

### 2.1 AA System Database (AARangeData.kt)

**Location**: `app/src/main/java/com/example/checklist_interactive/ui/maps/AARangeData.kt`

This file contains the complete specification database for all major AA systems in DCS World.

#### AASystemSpec Data Structure

```kotlin
data class AASystemSpec(
    val name: String,                    // System ID (e.g., "SA-11", "MIM-104")
    val displayName: String,             // Display name for UI (e.g., "SA-11 Gadfly (Buk-M1)")
    val detectionRangeKm: Double,        // Detection range in kilometers
    val engagementRangeKm: Double,       // Max engagement range in kilometers
    val minEngagementRangeKm: Double = 0.0, // Min engagement range (dead zone)
    val maxAltitudeM: Double,            // Max engagement altitude in meters
    val minAltitudeM: Double = 0.0,      // Min engagement altitude in meters
    val searchRadarRangeKm: Double? = null, // Search radar range (if different)
    val trackRadarRangeKm: Double? = null,  // Track radar range
    val category: String,                // "Long-range", "Medium-range", "Short-range", "MANPADS", "AAA"
    val notes: String = ""               // Additional notes
)
```

#### System Categories

| Category | Range | Example Systems |
|----------|-------|-----------------|
| **Long-range SAM** | 75-200 km | SA-10 (S-300PS), SA-20 (S-300PMU), MIM-104 Patriot |
| **Medium-range SAM** | 24-45 km | SA-11 (Buk), SA-17 (Buk-M2), SA-6 (Kub), MIM-23 HAWK |
| **Short-range SAM** | 5-12 km | SA-15 (Tor), SA-19 (Tunguska), SA-8 (Osa), Roland, Rapier |
| **MANPADS** | 4-6 km | SA-7, SA-9, SA-18 (Igla), FIM-92 Stinger, Mistral |
| **AAA** | 1-4 km | ZSU-23-4 Shilka, 2S6 Tunguska (guns), Gepard, M163 Vulcan |

#### Example: SA-11 Buk System

```kotlin
AASystemSpec(
    name = "SA-11",
    displayName = "SA-11 Gadfly (Buk-M1)",
    detectionRangeKm = 85.0,
    engagementRangeKm = 35.0,
    minEngagementRangeKm = 3.0,
    maxAltitudeM = 22000.0,
    minAltitudeM = 15.0,
    searchRadarRangeKm = 85.0,
    trackRadarRangeKm = 42.0,
    category = "Medium-range",
    notes = "Buk-M1 - Mobile medium-range SAM (9M38 missiles)"
)
```

### 2.2 Pattern Matching Logic

**Location**: `AARangeData.kt:394-444`

The `getSystemByUnitType()` function matches unit names/types against known AA systems using a mapping table and fuzzy matching.

#### Pattern Matching Algorithm

```kotlin
fun getSystemByUnitType(unitType: String): AASystemSpec? {
    val normalized = unitType.lowercase()

    // Step 1: Direct mapping lookup
    val mappings = mapOf(
        "s-300ps" to "SA-10",
        "s-300pmu" to "SA-20",
        "buk" to "SA-11",
        "tor" to "SA-15",
        "tunguska" to "SA-19",
        "patriot" to "MIM-104",
        "hawk" to "MIM-23",
        "shilka" to "ZSU-23-4",
        "gepard" to "Gepard",
        // ... 27 total mappings
    )

    // Try direct mapping
    mappings[normalized]?.let { systemName ->
        return systems.firstOrNull { it.name == systemName }
    }

    // Step 2: Partial match in mapping keys
    mappings.entries.firstOrNull { (key, _) ->
        normalized.contains(key) || key.contains(normalized)
    }?.let { (_, systemName) ->
        return systems.firstOrNull { it.name == systemName }
    }

    // Step 3: Fallback to name search
    return getSystemByName(unitType)
}
```

#### Example Matches

| Unit Name (from DCS) | Normalized | Matched Key | System ID | Display Name |
|---------------------|-----------|-------------|-----------|--------------|
| `SA-11 Buk SR 9S18M1` | `sa-11 buk sr 9s18m1` | `buk` | `SA-11` | SA-11 Gadfly (Buk-M1) |
| `Tor 9A331` | `tor 9a331` | `tor` | `SA-15` | SA-15 Gauntlet (Tor-M1) |
| `2S6 Tunguska` | `2s6 tunguska` | `tunguska` | `SA-19` | SA-19 Grison (Tunguska) |
| `Patriot STR` | `patriot str` | `patriot` | `MIM-104` | MIM-104 Patriot |
| `ZSU-23-4 Shilka` | `zsu-23-4 shilka` | `shilka` | `ZSU-23-4` | ZSU-23-4 Shilka |

**Non-AA Ground Units**: Units like tanks (M-1 Abrams, T-80), APCs, trucks, etc. do NOT match any patterns and are ignored (no range rings displayed).

---

## Part 3: Range Ring Rendering

### 3.1 Range Ring Configuration (AARangeData.kt)

**Location**: `AARangeData.kt:468-501`

#### Range Ring Types

For each AA system, three types of rings can be generated:

```kotlin
fun getRangeRingsForSystem(system: AASystemSpec): List<AARangeRing> {
    val rings = mutableListOf<AARangeRing>()

    // 1. DETECTION RANGE RING (outermost, dashed, light red)
    rings.add(AARangeRing(
        radiusKm = system.detectionRangeKm,
        color = Color.parseColor("#88FF4444"), // Light red, semi-transparent
        label = "${system.displayName} - Detection (${system.detectionRangeKm.toInt()} km)",
        strokeWidth = 2f,
        isDashed = true  // Dashed line for detection
    ))

    // 2. ENGAGEMENT RANGE RING (solid, bright red)
    rings.add(AARangeRing(
        radiusKm = system.engagementRangeKm,
        color = Color.parseColor("#DDFF0000"), // Bright red, more opaque
        label = "${system.displayName} - Max Range (${system.engagementRangeKm.toInt()} km)",
        strokeWidth = 4f,
        isDashed = false  // Solid line for engagement
    ))

    // 3. MINIMUM RANGE (dead zone, amber, dashed) - only if > 0.5 km
    if (system.minEngagementRangeKm > 0.5) {
        rings.add(AARangeRing(
            radiusKm = system.minEngagementRangeKm,
            color = Color.parseColor("#AAFFAA00"), // Amber, semi-transparent
            label = "${system.displayName} - Min Range (${system.minEngagementRangeKm} km)",
            strokeWidth = 2f,
            isDashed = true  // Dashed line for dead zone
        ))
    }

    return rings.sortedByDescending { it.radiusKm } // Largest first for drawing order
}
```

#### Visual Design

```
    ┌────────────────────────────────────┐
    │   Detection Range (dashed, light)  │ ← Outermost
    │  ┌──────────────────────────────┐  │
    │  │ Engagement Range (solid, red)│  │ ← Kill zone
    │  │    ┌──────────────────┐      │  │
    │  │    │ Min Range (amber)│      │  │ ← Dead zone (can't hit)
    │  │    │       ★ SAM      │      │  │ ← Unit position
    │  │    └──────────────────┘      │  │
    │  └──────────────────────────────┘  │
    └────────────────────────────────────┘

Legend:
★ = AA unit position
● = Aircraft (you)
```

### 3.2 Overlay Rendering (AARangeRingsOverlay.kt)

**Location**: `app/src/main/java/com/example/checklist_interactive/ui/maps/AARangeRingsOverlay.kt`

This overlay handles the actual drawing of range rings on the map.

#### Initialization

```kotlin
class AARangeRingsOverlay(
    private val isEnabled: () -> Boolean,  // Global AA range visibility
    private val getAAUnits: () -> List<TacticalUnitEntity>,  // Cached ground units
    private val getFillTransparency: () -> Float,  // User-configurable transparency
    private val getShowAllAARange: () -> Boolean,  // Show all AA ranges toggle
    private val getUnitRangeVisibility: (Int) -> Boolean  // Per-unit range toggle
) : Overlay()
```

#### Drawing Pipeline

**Phase 1: Pre-calculation** (lines 144-174)

```kotlin
data class RingCircle(
    val ring: AARangeRing,
    val radiusMeters: Double,
    val radiusPixels: Float,  // Screen pixels for this zoom level
    val labelAngle: Double,
    val labelScreenX: Float,
    val labelScreenY: Float
)

// Pre-calculate all ring geometry
val ringCircles = rangeRings.map { ring ->
    val radiusMeters = ring.radiusKm * 1000.0
    val edgeGeoPoint = GeoPoint(aaUnit.latitude, aaUnit.longitude)
        .destinationPoint(radiusMeters, 90.0) // Point to the east
    val edgeScreenPoint = mapView.projection.toPixels(edgeGeoPoint, null)
    val radiusPixels = abs(edgeScreenPoint.x - centerScreenPoint.x).toFloat()

    // Label positioning at 135° (top-right) at 90% of radius
    val labelAngle = 135.0
    val labelGeoPoint = GeoPoint(aaUnit.latitude, aaUnit.longitude)
        .destinationPoint(radiusMeters * 0.90, labelAngle)
    val labelScreenPoint = mapView.projection.toPixels(labelGeoPoint, null)

    RingCircle(ring, radiusMeters, radiusPixels, labelAngle,
               labelScreenPoint.x, labelScreenPoint.y)
}
```

**Phase 2: Draw Filled Rings with Masking** (lines 176-216)

Creates "donut" effect by drawing largest ring, then masking out smaller rings:

```kotlin
for (i in ringCircles.indices) {
    val circle = ringCircles[i]
    val layerId = canvas.saveLayer(null, null)

    // Draw filled circle
    fillPaint.color = adjustColorWithTransparency(circle.ring.color, transparency)
    fillPaint.xfermode = null
    canvas.drawCircle(centerX, centerY, circle.radiusPixels, fillPaint)

    // Mask out all smaller circles (create ring/donut)
    fillPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    for (j in (i + 1) until ringCircles.size) {
        val innerCircle = ringCircles[j]
        canvas.drawCircle(centerX, centerY, innerCircle.radiusPixels, fillPaint)
    }

    canvas.restoreToCount(layerId)
}
```

**Phase 3: Draw Borders** (lines 219-236)

```kotlin
for (circle in ringCircles.reversed()) {
    circlePaint.color = circle.ring.color
    circlePaint.strokeWidth = circle.ring.strokeWidth

    // Apply dash pattern if specified
    circlePaint.pathEffect = if (circle.ring.isDashed) {
        DashPathEffect(floatArrayOf(15f, 10f), 0f)  // 15px dash, 10px gap
    } else {
        null  // Solid line
    }

    canvas.drawCircle(centerX, centerY, circle.radiusPixels, circlePaint)
}
```

**Phase 4: Draw Labels** (lines 238-255, 261-310)

Two types of labels:

1. **Unit Label** (center): System name, range, altitude
2. **Ring Labels** (on rings): Distance markers

```kotlin
// Center unit label
drawUnitLabel(
    canvas,
    centerScreenPoint.x,
    centerScreenPoint.y,
    aaSystem,  // Contains displayName, engagementRangeKm, maxAltitudeM
    aaUnit
)

// Example output:
// ┌──────────────────────┐
// │ SA-11 Gadfly (Buk-M1)│
// │ Rng: 35km            │
// │ Alt: 22k             │
// └──────────────────────┘

// Ring distance labels (positioned at 135° on each ring)
for (circle in ringCircles) {
    drawRangeLabel(canvas, circle.labelScreenX, circle.labelScreenY, circle.ring)
}

// Example output: [85 km] [35 km] [3 km]
```

### 3.3 Performance Optimizations

**Viewport Culling** (lines 78-95)

```kotlin
// Get current map bounds
val boundingBox = mapView.boundingBox
val minLat = boundingBox.latSouth
val maxLat = boundingBox.latNorth
val minLon = boundingBox.lonWest
val maxLon = boundingBox.lonEast

// Skip units outside visible bounds (with buffer for range rings)
val bufferDegrees = 0.5 // ~50km latitude buffer
if (aaUnit.latitude < minLat - bufferDegrees ||
    aaUnit.latitude > maxLat + bufferDegrees ||
    aaUnit.longitude < minLon - bufferDegrees ||
    aaUnit.longitude > maxLon + bufferDegrees) {
    continue  // Skip this unit
}
```

**System Spec Caching** (lines 66-68, 119-132)

```kotlin
// Cache AA system specs to avoid expensive lookups on every draw
private val aaSystemCache = mutableMapOf<String, AASystemSpec?>()

// In drawRangeRingsForUnit():
val cacheKey = "${aaUnit.name}:${aaUnit.type}"
val aaSystem = aaSystemCache.getOrPut(cacheKey) {
    AARangeDatabase.getSystemByUnitType(aaUnit.name)
        ?: AARangeDatabase.getSystemByUnitType(aaUnit.type)
}

if (aaSystem == null) {
    // Only log once per unique unit to avoid log spam
    if (lastLoggedMissingUnit != cacheKey) {
        Log.d(TAG, "No AA system spec found for unit: ${aaUnit.name}")
        lastLoggedMissingUnit = cacheKey
    }
    return  // Not an AA unit
}
```

---

## Part 4: MapViewer Integration

### 4.1 Overlay Lifecycle Management (MapViewer.kt)

**Location**: `app/src/main/java/com/example/checklist_interactive/ui/maps/MapViewer.kt:323-380`

#### Unit Data Caching

```kotlin
// Cache AA units to avoid expensive DB queries on every draw
val cachedAAUnits = remember { mutableStateOf<List<TacticalUnitEntity>>(emptyList()) }
val cachedUnitRangeVisibility = remember { mutableStateOf<Map<Int, Boolean>>(emptyMap()) }

// Manage AA Range Rings overlay lifecycle
LaunchedEffect(mapState.mapView, mapState.showAllAARange, mapState.aaRangeFillTransparency, tacticalUnitsRepository) {
    mapState.mapView?.let { mv ->
        if (tacticalUnitsRepository != null) {
            // Remove existing overlay if present
            mapState.aaRangeOverlay?.let { oldOverlay ->
                mv.overlays.remove(oldOverlay)
                mapState.aaRangeOverlay = null
            }

            // Start collecting ground units in background (non-blocking)
            launch {
                tacticalUnitsRepository.getGroundUnits().collect { units ->
                    cachedAAUnits.value = units  // Update cache

                    // Update visibility cache
                    val visibilityMap = units.associate {
                        it.id to (it.showRangeRings == 1)
                    }
                    cachedUnitRangeVisibility.value = visibilityMap
                }
            }

            // Create new overlay with cached data
            val aaRangeOverlay = AARangeRingsOverlay(
                isEnabled = { true },  // Always enabled, visibility controlled by settings
                getAAUnits = { cachedAAUnits.value },  // ★ No DB query!
                getFillTransparency = { mapState.aaRangeFillTransparency },
                getShowAllAARange = { mapState.showAllAARange },
                getUnitRangeVisibility = { unitId ->
                    cachedUnitRangeVisibility.value[unitId] ?: false  // ★ No DB query!
                }
            )

            // Add at position 1 (after airspace, before other overlays)
            mv.overlays.add(if (mv.overlays.size > 0) 1 else 0, aaRangeOverlay)
            mapState.aaRangeOverlay = aaRangeOverlay
            mv.invalidate()
        }
    }
}
```

#### Why Caching is Critical

- **Problem**: Overlay's `draw()` method is called 30-60 times per second
- **Without cache**: 30-60 DB queries per second = UI freeze
- **With cache**: DB query happens once, updates via Flow, overlay reads from memory

#### Overlay Z-Order

```
Layer 0: Airspace Circles (background)
Layer 1: AA Range Rings (middle) ← Positioned here
Layer 2+: Tactical Units, Flight Path, etc. (foreground)
```

### 4.2 User Controls

#### Global AA Range Toggle

```kotlin
mapState.showAllAARange = true/false  // Show all AA ranges at once
```

When enabled:
- All ground units are checked for AA system matches
- Range rings displayed for all matched AA systems

When disabled:
- Only units with `showRangeRings == 1` are displayed
- Allows selective display of specific threats

#### Per-Unit Range Toggle

```kotlin
// Toggle range rings for specific unit
tacticalUnitsRepository.toggleUnitRangeRings(unitId, showRange = true)

// This updates the TacticalUnitEntity.showRangeRings field
```

#### Transparency Control

```kotlin
mapState.aaRangeFillTransparency = 0.05f to 0.3f  // Adjustable range
```

Controls the opacity of filled ring areas (prevents map obscuring).

---

## Part 5: Data Flow Examples

### Example 1: SA-11 Buk Detection and Display

**Step 1: DCS Export** (Export.lua)
```json
{
  "dcsId": "16777987",
  "name": "SA-11 Buk SR 9S18M1",
  "type": "Buk 9S18M1 SR",
  "category": "ground",
  "coalition": 1,
  "latitude": 42.355896,
  "longitude": 43.321456,
  "altitude": 456.78,
  "heading": 270.0,
  "speed": 0.0,
  "distance": 12450.5,
  "bearing": 180.5,
  "health": 1.0
}
```

**Step 2: Database Storage** (TacticalUnitEntity)
```kotlin
TacticalUnitEntity(
    id = 123,
    dcsId = "16777987",
    name = "SA-11 Buk SR 9S18M1",
    type = "Buk 9S18M1 SR",
    category = "ground",
    coalition = 1,
    latitude = 42.355896,
    longitude = 43.321456,
    altitude = 456.78,
    showRangeRings = 0,  // Default: off
    isActive = 1,
    // ... other fields
)
```

**Step 3: Repository Query** (TacticalUnitsRepository)
```kotlin
getGroundUnits()  // Returns all category == "ground" units
// → List containing SA-11, tanks, APCs, etc.
```

**Step 4: Pattern Matching** (AARangeDatabase)
```kotlin
getSystemByUnitType("SA-11 Buk SR 9S18M1")
// normalized: "sa-11 buk sr 9s18m1"
// matches key: "buk"
// returns: AASystemSpec(name = "SA-11", ...)
```

**Step 5: Range Ring Generation**
```kotlin
getRangeRingsForSystem(sa11System)
// Returns:
[
    AARangeRing(radiusKm=85.0, color=#88FF4444, isDashed=true, label="Detection"),
    AARangeRing(radiusKm=35.0, color=#DDFF0000, isDashed=false, label="Max Range"),
    AARangeRing(radiusKm=3.0, color=#AAFFAA00, isDashed=true, label="Min Range")
]
```

**Step 6: Rendering**
- Draws three concentric rings at unit position (42.355896, 43.321456)
- Detection ring: 85 km radius (dashed red)
- Engagement ring: 35 km radius (solid bright red)
- Dead zone: 3 km radius (dashed amber)
- Center label: "SA-11 Gadfly (Buk-M1) | Rng: 35km | Alt: 22k"

### Example 2: Non-AA Ground Unit (Ignored)

**Step 1: DCS Export**
```json
{
  "dcsId": "16777999",
  "name": "M-1 Abrams",
  "type": "M-1 Abrams",
  "category": "ground",
  "coalition": 2,
  ...
}
```

**Step 2-3**: Stored in DB, retrieved by `getGroundUnits()`

**Step 4: Pattern Matching**
```kotlin
getSystemByUnitType("M-1 Abrams")
// normalized: "m-1 abrams"
// NO match in mappings
// fallback: getSystemByName("M-1 Abrams") → null
// returns: null
```

**Step 5: Rendering**
```kotlin
if (aaSystem == null) {
    return  // Skip rendering, not an AA unit
}
```

**Result**: No range rings displayed for tank

---

## Part 6: AA System Database Reference

### Complete System List (37 systems)

#### Russian/Soviet Systems (14 systems)

| System ID | Display Name | Category | Det. Range | Eng. Range | Min Range | Max Alt | Notes |
|-----------|--------------|----------|------------|------------|-----------|---------|-------|
| SA-10 | SA-10 Grumble (S-300PS) | Long-range | 150 km | 75 km | 5 km | 25,000 m | S-300PS - Lethal long-range |
| SA-20 | SA-20 Gargoyle (S-300PMU) | Long-range | 200 km | 90 km | 5 km | 27,000 m | S-300PMU - Advanced |
| SA-23 | SA-23 Gladiator (S-300VM) | Long-range | 250 km | 200 km | 6 km | 30,000 m | ABM capable |
| SA-11 | SA-11 Gadfly (Buk-M1) | Medium-range | 85 km | 35 km | 3 km | 22,000 m | 9M38 missiles |
| SA-17 | SA-17 Grizzly (Buk-M2) | Medium-range | 120 km | 45 km | 3 km | 25,000 m | Upgraded Buk |
| SA-6 | SA-6 Gainful (Kub) | Medium-range | 75 km | 24 km | 4 km | 12,000 m | Older medium SAM |
| SA-15 | SA-15 Gauntlet (Tor-M1) | Short-range | 25 km | 12 km | 1.5 km | 6,000 m | Point defense |
| SA-19 | SA-19 Grison (Tunguska) | Short-range | 18 km | 8 km | 0.2 km | 3,500 m | Gun/missile SPAAG |
| SA-8 | SA-8 Gecko (Osa) | Short-range | 30 km | 10 km | 1.5 km | 5,000 m | Mobile short SAM |
| SA-13 | SA-13 Gopher (Strela-10) | Short-range | 10 km | 5 km | 0.8 km | 3,500 m | IR-guided |
| SA-9 | SA-9 Gaskin (Strela-1) | MANPADS | 8 km | 4.2 km | 0.8 km | 3,500 m | Mobile IR SAM |
| SA-18 | SA-18 Grouse (Igla) | MANPADS | 5.2 km | 5.2 km | 0.5 km | 3,500 m | Man-portable |
| SA-7 | SA-7 Grail (Strela-2) | MANPADS | 4.2 km | 4.2 km | 0.8 km | 2,300 m | First-gen MANPADS |
| ZSU-23-4 | ZSU-23-4 Shilka | AAA | 20 km | 2.5 km | 0 km | 1,500 m | 4x 23mm guns |
| 2S6 | 2S6 Tunguska (Gun) | AAA | 18 km | 4 km | 0 km | 3,500 m | 2x 30mm guns |

#### NATO/Western Systems (8 systems)

| System ID | Display Name | Category | Det. Range | Eng. Range | Min Range | Max Alt | Notes |
|-----------|--------------|----------|------------|------------|-----------|---------|-------|
| MIM-104 | MIM-104 Patriot | Long-range | 170 km | 100 km | 3 km | 24,000 m | Advanced long-range |
| NASAMS | NASAMS (AIM-120) | Medium-range | 120 km | 25 km | 1 km | 20,000 m | Uses AMRAAM |
| MIM-23 | MIM-23 HAWK | Medium-range | 100 km | 40 km | 2.5 km | 18,000 m | Legacy medium SAM |
| Roland | Roland SAM | Short-range | 18 km | 6.3 km | 0.5 km | 5,500 m | Franco-German |
| Rapier | Rapier FSC | Short-range | 15 km | 6.8 km | 0.5 km | 3,000 m | British |
| Avenger | Avenger (Stinger) | Short-range | 10 km | 4.8 km | 0.2 km | 3,800 m | Mobile Stinger |
| FIM-92 | FIM-92 Stinger | MANPADS | 4.8 km | 4.8 km | 0.2 km | 3,800 m | US MANPADS |
| Mistral | Mistral MANPADS | MANPADS | 6 km | 6 km | 0.5 km | 3,000 m | French MANPADS |
| Gepard | Gepard SPAAG | AAA | 15 km | 3.5 km | 0 km | 3,500 m | 2x 35mm guns |
| Vulcan | M163 Vulcan | AAA | 5 km | 1.2 km | 0 km | 1,200 m | 20mm Gatling |
| ZU-23 | ZU-23 (Towed) | AAA | 2.5 km | 2.5 km | 0 km | 1,500 m | Towed 23mm AAA |

### Pattern Mapping Reference

| DCS Unit Name Pattern | Mapping Key | System ID | Example Unit Names |
|----------------------|-------------|-----------|-------------------|
| Contains "S-300PS" | s-300ps | SA-10 | S-300PS 5P85C ln, S-300PS 40B6M tr |
| Contains "S-300PMU" | s-300pmu | SA-20 | S-300PMU1 ln, S-300PMU2 tr |
| Contains "SA-10" | sa-10 | SA-10 | SA-10 launcher |
| Contains "Buk" | buk | SA-11 | SA-11 Buk SR 9S18M1, Buk-M1-2 LN 9A310M1-2 |
| Contains "Tor" | tor | SA-15 | Tor 9A331, SA-15 Tor |
| Contains "Tunguska" | tunguska | SA-19 | 2S6 Tunguska, SA-19 Grison |
| Contains "Patriot" | patriot | MIM-104 | Patriot STR, Patriot ln, Patriot ECS |
| Contains "HAWK" | hawk | MIM-23 | Hawk pcp, Hawk sr, Hawk tr |
| Contains "Shilka" | shilka | ZSU-23-4 | ZSU-23-4 Shilka, AAA Shilka |
| Contains "Strela-10" | strela-10 | SA-13 | Strela-10M3, SA-13 Gopher |
| Contains "Igla" | igla | SA-18 | SA-18 Igla comm, Igla manpad |
| Contains "Gepard" | gepard | Gepard | Gepard, Flakpanzer Gepard |
| Contains "Vulcan" | vulcan | Vulcan | M163 Vulcan, Vulcan Air Defense System |

---

## Part 7: Configuration & User Settings

### 7.1 Global Settings (PreferencesManager)

```kotlin
// AA Range visibility
prefsManager.setShowAllAARange(enabled: Boolean)
prefsManager.getShowAllAARange(): Boolean

// Fill transparency (0.05 - 0.3)
prefsManager.setAARangeFillTransparency(value: Float)
prefsManager.getAARangeFillTransparency(): Float
```

### 7.2 Per-Unit Settings (TacticalUnitsRepository)

```kotlin
// Show range rings for specific unit
suspend fun toggleUnitRangeRings(unitId: Int, showRange: Boolean)

// Clear all unit range rings
suspend fun clearAllRangeRings()

// Example usage:
tacticalUnitsRepository.toggleUnitRangeRings(unitId = 123, showRange = true)
```

### 7.3 Display Priority Logic

```kotlin
// In AARangeRingsOverlay.kt:98-103
val showAllAA = getShowAllAARange()
val showThisUnit = getUnitRangeVisibility(aaUnit.id)

if (showAllAA || showThisUnit) {
    drawRangeRingsForUnit(canvas, mapView, aaUnit)
}
```

**Logic**:
- If "Show All AA" is ON → Display all detected AA systems
- If "Show All AA" is OFF → Only display units with `showRangeRings == 1`
- Per-unit toggle overrides global setting (allows selective display)

---

## Part 8: Troubleshooting & Debugging

### Common Issues

#### Issue 1: Range rings not appearing

**Possible causes:**
1. Unit name doesn't match any pattern in `AARangeDatabase.getSystemByUnitType()`
2. Unit category is not "ground"
3. Unit is outside max tracking distance (150 km)
4. Global "Show All AA" is OFF and unit's `showRangeRings == 0`

**Debugging:**
```kotlin
// Check if unit matches AA database
val aaSystem = AARangeDatabase.getSystemByUnitType("SA-11 Buk SR 9S18M1")
if (aaSystem == null) {
    Log.d(TAG, "Unit not recognized as AA system")
}

// Check unit category
if (unit.category != "ground") {
    Log.d(TAG, "Unit is not ground category: ${unit.category}")
}
```

#### Issue 2: Performance issues / lag

**Possible causes:**
1. Too many AA units in range (> 50)
2. DB queries happening in draw loop (cache not working)
3. High map zoom level causing large ring radii

**Solutions:**
- Check that caching is working: `cachedAAUnits.value` should not trigger DB queries
- Verify viewport culling is working (check buffer calculations)
- Reduce transparency for better performance

#### Issue 3: Incorrect range rings

**Possible causes:**
1. Wrong system matched (e.g., "Buk" matched SA-17 instead of SA-11)
2. Outdated data in `AARangeDatabase`

**Solution:**
- Add more specific mapping entry in `getSystemByUnitType()`
- Check DCS unit name format has changed

### Debug Logging

Enable debug logging in `AARangeRingsOverlay.kt`:

```kotlin
private const val TAG = "AARangeRingsOverlay"

// Logs missing unit patterns (throttled to avoid spam)
if (aaSystem == null && lastLoggedMissingUnit != cacheKey) {
    Log.d(TAG, "No AA system spec found for unit: ${aaUnit.name} (type: ${aaUnit.type})")
    lastLoggedMissingUnit = cacheKey
}
```

---

## Part 9: Future Enhancements

### Potential Improvements

1. **FARP Override System**
   - Special handling for units at FARPs (Forward Arming and Refueling Points)
   - See Export.lua:321 for weapon override example
   - Could apply similar logic for AA units

2. **Exclusion Checks**
   - Filter out decoys/mock units
   - Handle units in specific states (destroyed, inactive radar)

3. **Threat Assessment**
   - Color-code by threat level (red = high threat, yellow = medium, green = low)
   - Factor in: distance, altitude, heading, radar state

4. **Multi-Layer Threat Zones**
   - Combine overlapping AA ranges into aggregate threat zones
   - Show "safe corridors" between coverage gaps

5. **Time-to-Kill Indicators**
   - Calculate engagement time based on missile flight time
   - Show countdown if player enters engagement range

6. **3D Altitude Visualization**
   - Show altitude coverage (vertical profile)
   - Indicate if player is above/below engagement envelope

7. **Dynamic Range Adjustments**
   - Adjust ranges based on:
     - Terrain (radar shadowing)
     - Weather (reduced detection in rain/clouds)
     - Electronic warfare (jamming reduces range)

8. **Historical Threat Tracking**
   - Show past AA unit positions
   - Track radar activation patterns
   - Predict patrol routes

---

## Summary

The AAA Range Ring System is a sophisticated multi-layer system that:

1. **Captures** unit data from DCS World at 10 Hz (Export.lua)
2. **Categorizes** units by DCS Type structure (level1 == 2 → ground)
3. **Stores** tactical units in SQLite database (TacticalUnitEntity)
4. **Queries** ground units via repository (category filter)
5. **Matches** unit names against comprehensive AA database (pattern matching)
6. **Generates** range rings (detection, engagement, dead zones)
7. **Renders** rings with advanced graphics (masking, transparency, labels)
8. **Optimizes** performance (caching, viewport culling, throttling)

### Key Architectural Decisions

- **No explicit AA flag**: Units are not marked as "AA" in database; detection happens via pattern matching at render time
- **Caching strategy**: Ground units cached in memory to avoid DB queries during rendering
- **Comprehensive database**: 37 AA systems with accurate real-world ranges
- **Flexible display**: Global toggle + per-unit toggles for maximum control
- **Performance-first**: Viewport culling, system spec caching, debounced updates

### File Dependencies

```
Export.lua (DCS)
    ↓
TacticalEntities.kt (Data Model)
    ↓
TacticalUnitsRepository.kt (Data Access)
    ↓
AARangeData.kt (AA Database) ←→ AARangeRingsOverlay.kt (Rendering)
    ↓
MapViewer.kt (Orchestration)
```

This documentation provides a complete understanding of how AA units are detected, classified, and displayed in the ChecklistInteractive tactical map system.


---
App Version: v1.0.25
Last Updated: 2026.01.19
---