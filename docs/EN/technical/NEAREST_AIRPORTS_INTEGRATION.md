# Nearest Airports Feature - Integration Guide

## Overview

The Nearest Airports feature displays a sorted list of the nearest airports based on the current player position. The feature uses **only local database markers** (no online data queries) and calculates distance and bearing in real-time.

## Created Files

### 1. Utility Classes
- **`GeoUtils.kt`**: Geographic calculations (Haversine formula for distances and bearings)
- **`NearestAirportsData.kt`**: Data models and calculator for Nearest Airports

### 2. UI Components
- **`NearestAirportsOverlay.kt`**: Sidebar/Overlay UI with airport list

### 3. Configuration
- **`PreferencesManager.kt`**: Feature flag added (`isNearestAirportsEnabled()`)
- **`FABOverlay.kt`**: New FAB button configured
- **`strings.xml`**: Localized strings added

## Integration in MapViewer

### Step 1: Add State Variables

Add the following state variables to your MapViewer Composable:

```kotlin
// Nearest Airports state
var showNearestAirports by remember { mutableStateOf(false) }
var nearestAirports by remember { mutableStateOf<List<AirportWithDistance>>(emptyList()) }
val nearestAirportsEnabled = prefsManager.isNearestAirportsEnabled()
```

### Step 2: Load and Calculate Data

Add a LaunchedEffect to load airport data and calculate distances:

```kotlin
// Calculate nearest airports when player position changes
LaunchedEffect(flightData?.latitude, flightData?.longitude, showNearestAirports) {
    if (showNearestAirports && flightData != null) {
        // Get all airport locations from database
        val allLocations = tacticalDb.locationDao().getAllLocations().first()

        // Get runways for all airports
        val runwaysMap = allLocations
            .filter { it.markerType == "airport" }
            .associateWith { location ->
                tacticalDb.runwayDao().getRunwaysByLocationSync(location.id)
            }
            .mapKeys { it.key.id }

        // Calculate nearest airports
        nearestAirports = NearestAirportsCalculator.calculateNearestAirports(
            allLocations = allLocations,
            playerLat = flightData.latitude,
            playerLon = flightData.longitude,
            maxResults = 10,
            maxDistanceKm = null, // No limit, or set to e.g. 500.0
            runwaysMap = runwaysMap
        )
    }
}
```

### Step 3: Configure FAB Button

Update the `MapViewerFABs.create()` call:

```kotlin
val fabs = MapViewerFABs.create(
    onCenterOnPosition = { /* existing */ },
    onLayerSelection = { /* existing */ },
    onOverlaySelection = { /* existing */ },
    // ... other parameters ...
    onNearestAirportsOpen = { showNearestAirports = !showNearestAirports },
    nearestAirportsEnabled = nearestAirportsEnabled,
    // ... remaining parameters ...
)
```

### Step 4: Add Overlay to UI

Add the NearestAirportsOverlay component to the MapViewer UI:

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    // Existing map content
    // ...

    // Nearest Airports Overlay
    NearestAirportsOverlay(
        visible = showNearestAirports && nearestAirportsEnabled,
        airports = nearestAirports,
        useNauticalMiles = true, // or false for kilometers
        isLoading = nearestAirports.isEmpty() && showNearestAirports,
        onClose = { showNearestAirports = false },
        onAirportClick = { location ->
            // Open airport details popup
            selectedLocation = location
            showMarkerPopup = true
            showNearestAirports = false
        },
        onCenterOnAirport = { location ->
            // Center map on airport
            mapController.setCenter(GeoPoint(location.latitude, location.longitude))
        }
    )
}
```

## Feature Activation

The feature is **disabled** by default. To activate it:

### Option 1: Programmatically
```kotlin
prefsManager.setNearestAirportsEnabled(true)
```

### Option 2: Settings UI
Add a toggle in the Map Settings:

```kotlin
SwitchPreference(
    title = stringResource(R.string.nearest_airports_title),
    subtitle = "Show list of nearest airports sorted by distance",
    checked = prefsManager.isNearestAirportsEnabled(),
    onCheckedChange = { enabled ->
        prefsManager.setNearestAirportsEnabled(enabled)
    }
)
```

## Usage

1. **Enable the feature** in Settings
2. **Click the FAB button** (airplane landing icon) on the Map View
3. **Select an airport** from the list
   - Click on center map button: Centers map on airport
   - Click on card: Opens airport details popup

## How it Works

### Data Source
- Uses **only local `LocationEntity`** from the database
- Filters by `markerType == "airport"`
- No online data queries or external APIs

### Calculations
- **Distance**: Haversine formula (great circle distance)
- **Bearing**: Initial bearing from player to airport (0-360°)
- **Update**: On every player position change

### Performance
- Calculations are optimized and fast
- Lazy loading of runway data
- Debouncing through LaunchedEffect

## Customization

### Maximum Number of Airports
```kotlin
maxResults = 20 // Default: 10
```

### Maximum Distance
```kotlin
maxDistanceKm = 500.0 // Only airports within 500km
```

### Units
```kotlin
useNauticalMiles = false // Kilometers instead of nautical miles
```

## Testing

### Unit Tests für GeoUtils
Erstelle Tests in `GeoUtilsTest.kt`:

```kotlin
@Test
fun testCalculateDistance() {
    // Frankfurt (EDDF) to Munich (EDDM)
    val distance = GeoUtils.calculateDistance(
        lat1 = 50.0379, lon1 = 8.5622,  // EDDF
        lat2 = 48.3538, lon2 = 11.7861  // EDDM
    )
    assertEquals(299.0, distance, 1.0) // ~299 km
}

@Test
fun testCalculateBearing() {
    // North to South should be ~180°
    val bearing = GeoUtils.calculateBearing(
        lat1 = 50.0, lon1 = 10.0,
        lat2 = 49.0, lon2 = 10.0
    )
    assertEquals(180.0, bearing, 1.0)
}
```

## Accessibility

Alle UI-Elemente haben:
- `contentDescription` für Screen Reader
- Semantics für Focus-Navigation
- Tastatur-Support

## Lokalisierung

Alle Strings sind in `strings.xml` definiert:
- `nearest_airports_title`
- `nearest_airports_count`
- `nearest_airports_empty`
- `nearest_airports_distance_label`
- `nearest_airports_bearing_label`
- `nearest_airports_elevation`
- `fab_cd_nearest_airports`

## Troubleshooting

### Airports not displayed
- Check if feature is enabled: `prefsManager.isNearestAirportsEnabled()`
- Check if airports exist in database: `markerType == "airport"`
- Check player position: `flightData?.latitude` and `longitude` not null

### Performance Issues
- Reduce `maxResults`
- Set `maxDistanceKm` for filtering
- Debounce position updates

### FAB Button not visible
- Check `nearestAirportsEnabled` parameter
- Check FAB position (default: `defaultY = 0.20f`)
- Check FAB visibility (`visible = nearestAirportsEnabled`)

## Future Enhancements

Possible extensions (not implemented):
- Filter by runway length (e.g., only airports with >2000m)
- Filter by frequencies (Tower, Ground, etc.)
- Filter by services (Fuel, Arming, etc.)
- Sorting by other criteria (Alphabet, Elevation, etc.)
- Export list as Text/CSV
- Integration with navigation (Auto-route to nearest airport)

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   MapViewer                         │
│  ┌───────────────────────────────────────────────┐  │
│  │ FlightData (Player Position)                  │  │
│  └───────────────┬───────────────────────────────┘  │
│                  │                                   │
│                  ▼                                   │
│  ┌───────────────────────────────────────────────┐  │
│  │ NearestAirportsCalculator                     │  │
│  │  - calculateNearestAirports()                 │  │
│  │  - Uses GeoUtils for calculations             │  │
│  └───────────────┬───────────────────────────────┘  │
│                  │                                   │
│                  ▼                                   │
│  ┌───────────────────────────────────────────────┐  │
│  │ List<AirportWithDistance>                     │  │
│  │  - LocationEntity                             │  │
│  │  - distanceKm, distanceNm                     │  │
│  │  - bearing                                    │  │
│  │  - runways                                    │  │
│  └───────────────┬───────────────────────────────┘  │
│                  │                                   │
│                  ▼                                   │
│  ┌───────────────────────────────────────────────┐  │
│  │ NearestAirportsOverlay                        │  │
│  │  - Sidebar/Panel UI                           │  │
│  │  - Airport Cards with Details                 │  │
│  │  - Click Handlers                             │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

## License & Credits

This feature was developed for the Interactive Checklists project and uses:
- OpenStreetMap data (ODbL License)
- Room Database (Android Jetpack)
- Jetpack Compose (Material 3)
