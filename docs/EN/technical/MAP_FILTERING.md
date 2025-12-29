# Map Filtering Feature

## Overview
The Map Filtering feature allows users to organize and filter location markers by DCS map (e.g., "Caucasus", "Syria", "Persian Gulf", "Nevada"). This is essential for managing markers across different DCS theater maps.

## Database Schema

### LocationEntity - New Field
A new `map` column has been added to the `locations` table:

```kotlin
@ColumnInfo(name = "map")
val map: String? = null  // e.g., "Caucasus", "Syria", "Persian Gulf", "Nevada"
```

### Migration
- **Database Version**: Upgraded from 7 to 8
- **Migration**: `MIGRATION_7_8` adds the `map` column and creates an index for efficient filtering
- **Index**: `index_locations_map` on the `map` column for fast lookups

## Python Tools

### markers_database.py
The `Location` dataclass now includes:

```python
map: Optional[str] = None  # e.g., "Caucasus", "Syria", "Persian Gulf", "Nevada"
```

All `add_location()` and `update_location()` methods have been updated to handle the new field.

### add_caucasus_markers.py
All Caucasus airport markers now include `map="Caucasus"`:

```python
Location(
    name="Tbilisi Intl",
    # ... other fields ...
    source="OurAirports / DCS-World MAP",
    map="Caucasus"
)
```

## UI Components

### MapMarkerPopup
- Displays the map name in the marker information grid
- Shows as "Map: Caucasus" in the details section
- Localized string resource: `map_marker_map`

### SingleMarkerNavigation
- MarkerDetailsContent shows the map field in the info grid
- buildMarkerInfoItems() includes map in the displayed information

### String Resources
The following string resources have been added:
- **English** (`values/strings.xml`): `<string name="map_marker_map">Map</string>`
- **German** (`values-de/strings.xml`): `<string name="map_marker_map">Karte</string>`
- **Spanish** (`values-es/strings.xml`): `<string name="map_marker_map">Mapa</string>`

## DAO Methods

### LocationDao - New Queries

```kotlin
// Get all locations for a specific map
@Query("SELECT * FROM locations WHERE map = :mapName ORDER BY name")
fun getLocationsByMap(mapName: String): Flow<List<LocationEntity>>

// Get list of all available maps
@Query("SELECT DISTINCT map FROM locations WHERE map IS NOT NULL ORDER BY map")
fun getAllMaps(): Flow<List<String>>
```

## Usage Examples

### Filtering Markers by Map

```kotlin
// Get all Caucasus markers
val caucasusMarkers = locationDao.getLocationsByMap("Caucasus")
    .collectAsState(initial = emptyList())

// Get list of all available maps
val availableMaps = locationDao.getAllMaps()
    .collectAsState(initial = emptyList())
```

### Creating Markers for Different Maps

```kotlin
// Caucasus marker
val tbilisi = LocationEntity(
    name = "Tbilisi Intl",
    latitude = 41.668023,
    longitude = 44.952493,
    map = "Caucasus",
    // ... other fields
)

// Syria marker
val damascus = LocationEntity(
    name = "Damascus Intl",
    latitude = 33.411167,
    longitude = 36.515556,
    map = "Syria",
    // ... other fields
)
```

## Python Script Usage

When creating new location markers, always include the map field:

```python
from core.markers_database import MarkersDatabase, Location, MarkerType

db = MarkersDatabase()

# Add a location with map identifier
location = Location(
    name="Example Airport",
    latitude=42.0,
    longitude=43.0,
    marker_type=MarkerType.AIRPORT.value,
    map="Caucasus"  # Always specify the map
)

db.add_location(location)
```

## Future Enhancements

### Planned Features
1. **Map Selector UI**: Add a dropdown/filter in MapViewer to switch between maps
2. **Auto-Detection**: Detect current DCS map from mission data and auto-filter markers
3. **Multi-Map Support**: Show markers from multiple maps simultaneously
4. **Map-Specific Settings**: Store user preferences per map (zoom level, center position)
5. **Map Import/Export**: Batch import/export markers by map

### Integration Points
- **MapViewer**: Add map filter dropdown in toolbar
- **Marker Management**: Filter by map in marker list views
- **Route Planning**: Show only markers from the selected map when creating routes
- **DCS Integration**: Read current theater from DCS mission data

## Database Maintenance

### Re-indexing
The migration automatically creates the index. To manually rebuild:

```sql
CREATE INDEX IF NOT EXISTS index_locations_map ON locations(map);
```

### Data Migration
Existing markers without a map field will have `map = null`. These can be batch-updated:

```sql
-- Example: Set all Russian airports in specific region to Caucasus
UPDATE locations 
SET map = 'Caucasus' 
WHERE country = 'Russia' 
  AND latitude BETWEEN 41.0 AND 46.0 
  AND longitude BETWEEN 36.0 AND 50.0;
```

## Testing

### Verification Steps
1. **Database Migration**: Verify column exists and index is created
2. **Python Scripts**: Run `add_caucasus_markers.py` and verify all markers have `map="Caucasus"`
3. **UI Display**: Open marker popup and verify "Map: Caucasus" appears in info grid
4. **DAO Queries**: Test `getLocationsByMap()` and `getAllMaps()` methods
5. **Multi-Language**: Verify string resources in English, German, and Spanish

### Sample Test Queries

```kotlin
// Test filtering
val caucasusMarkers = db.locationDao().getLocationsByMap("Caucasus").first()
assert(caucasusMarkers.all { it.map == "Caucasus" })

// Test map list
val maps = db.locationDao().getAllMaps().first()
assert(maps.contains("Caucasus"))
```

## Implementation Status

✅ Database schema updated with `map` field  
✅ Migration 7→8 implemented  
✅ Python `Location` dataclass updated  
✅ Python `add_location()`/`update_location()` methods updated  
✅ `add_caucasus_markers.py` updated with map="Caucasus"  
✅ MapMarkerPopup UI displays map field  
✅ SingleMarkerNavigation displays map field  
✅ String resources added (EN/DE/ES)  
✅ LocationDao queries added (`getLocationsByMap`, `getAllMaps`)  
⏳ MapViewer filter UI (future enhancement)  
⏳ Auto-detection from DCS mission (future enhancement)

## Related Documentation
- [Database Structure](structure.md)
- [Marker System](../features/TAG_SYSTEM.md)
- [Aviation Maps](AVIATION_MAPS.md)
