# Aviation Map Feature - Technical Documentation

## Overview

The Aviation Map feature integrates OpenStreetMap (OSM) into the tab system, providing real-time aircraft position tracking from the DataPad UDP stream. The map displays the aircraft's current position with live updates and supports multiple tile sources including topographic and satellite layers.

## Architecture

### Components

#### 1. MapViewer (UI Layer)
**Path:** `app/src/main/java/com/example/checklist_interactive/ui/maps/MapViewer.kt`

**Responsibilities:**
- Render OpenStreetMap using osmdroid library
- Display live aircraft position marker
- Handle map controls (center, zoom, layer selection)
- Auto-center on aircraft position
- Show connection status indicators

**Key Features:**
- **AndroidView Integration:** Wraps osmdroid MapView for Compose compatibility
- **Live Position Updates:** Subscribes to DataPad FlightData StateFlow
- **Marker Rotation:** Aircraft heading displayed via marker rotation
- **Info Display:** Shows altitude, speed, and heading in marker popup
- **Multiple Tile Sources:** OpenStreetMap, Topographic, Satellite (USGS)

#### 2. TabContent.MapTab (Data Layer)
**Path:** `app/src/main/java/com/example/checklist_interactive/data/tabs/TabManager.kt`

**Integration:**
- `TabContent` sealed class with `MapTab` and `DocumentTab` variants
- `openMapTab()` method creates or switches to map tab
- Synthetic FileInfo created for map tabs with special path `special://aviation_map`

### Data Flow

```
DataPadManager (UDP) -> FlightData StateFlow
                              ↓
                        MapViewer observes
                              ↓
                    Update Marker Position
                              ↓
                      Render on MapView
```

## Usage

### Opening the Aviation Map

**From File List Screen:**
1. Click the Map icon (🗺️) in the top bar
2. Map opens as a new tab

**From Code:**
```kotlin
val idx = tabManager.openMapTab()
tabManager.switchToTab(idx)
```

### Map Controls

- **Center on Aircraft:** FAB button (🎯) - centers map on current position
- **Layer Selection:** FAB button (🗂️) - choose between map tile sources
- **Auto-centering:** Automatically follows aircraft when enabled
- **Manual Navigation:** Pinch to zoom, drag to pan

### Connection Status Indicators

- **⚠ No DataPad connection:** Red warning when UDP stream is disconnected
- **ℹ Waiting for valid position:** Blue info when position is 0,0
- **🎯 Auto-centering:** Green indicator when following aircraft

## Configuration

### Tile Sources

The map supports multiple tile sources configured in `LayerSelectionDialog`:

1. **OpenStreetMap (Default):** `TileSourceFactory.MAPNIK`
   - Standard street map
   - Good for general navigation

2. **Topographic:** `TileSourceFactory.OpenTopo`
   - Terrain and elevation data
   - Useful for terrain awareness

3. **Satellite (USGS):** `TileSourceFactory.USGS_SAT`
   - Satellite imagery
   - Visual terrain recognition

### Custom Aviation Overlays

For aviation sectional charts, you can add custom tile sources:

```kotlin
// Example: OpenAIP aviation overlay
val aviationTileSource = object : OnlineTileSourceBase(
    "OpenAIP",
    1, 20, 256, ".png",
    arrayOf("https://map.openaip.net/geowebcache/service/tms/1.0.0/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl + 
            "openaip_basemap@EPSG%3A900913@png/" +
            "${MapTileIndex.getZoom(pMapTileIndex)}/" +
            "${MapTileIndex.getX(pMapTileIndex)}/" +
            "${MapTileIndex.getY(pMapTileIndex)}.png"
    }
}

mapView.overlays.add(TilesOverlay(aviationTileSource, context))
```

**Note:** OpenAIP and OpenFlightMaps may require API keys or have usage restrictions. Check their respective terms of service.

## Performance Optimizations

### Memory Management

- `DisposableEffect` cleans up MapView on component disposal
- Marker updates are debounced via `LaunchedEffect`
- Only active position marker is maintained

### Update Frequency

- Position updates triggered by DataPad FlightData changes
- Map invalidation only when position changes
- Auto-center animation uses `animateTo()` for smooth transitions

### Tile Caching

osmdroid automatically caches tiles in device storage:
- Cache location: `getExternalStorageDirectory() + "/osmdroid/tiles"`
- Configurable via `Configuration.getInstance()`

## Integration with Tab System

### Tab Type Detection

The `TabBar` and `QuickTabSwitcher` detect map tabs:

```kotlin
val icon = when {
    tabInfo.content is TabManager.TabContent.MapTab -> Icons.Default.Map
    fileExtension == "pdf" -> Icons.Default.PictureAsPdf
    // ... other types
}
```

### Content Rendering

`TabbedDocumentViewer` renders appropriate viewer:

```kotlin
when (tabInfo.content) {
    is TabManager.TabContent.MapTab -> MapViewer()
    is TabManager.TabContent.DocumentTab -> InternalFileViewer(...)
}
```

## Live Position Updates

### Position Data Format

From DataPad FlightData:
```kotlin
data class FlightData(
    @SerialName("lat") val latitude: Double = 0.0,
    @SerialName("long") val longitude: Double = 0.0,
    @SerialName("heading") val heading: Double = 0.0,
    @SerialName("alt") val altitude: Double = 0.0,
    @SerialName("groundSpeed") val groundSpeed: Double? = null
)
```

### Marker Update Logic

```kotlin
LaunchedEffect(flightData) {
    val data = flightData
    if (data != null && lat != 0.0 && lon != 0.0) {
        val newPosition = GeoPoint(lat, lon)
        marker.position = newPosition
        marker.rotation = data.heading.toFloat()
        
        // Format display info
        val altFt = (data.altitude * 3.28084).toInt()
        val speedKts = (data.groundSpeed ?: 0.0) * 1.9438
        marker.snippet = "Alt: ${altFt}ft | Spd: ${speedKts.toInt()}kt"
        
        if (autoCenter) {
            map.controller.animateTo(newPosition)
        }
        
        map.invalidate()
    }
}
```

## Permissions

### Required Permissions (AndroidManifest.xml)

```xml
<!-- Internet for downloading map tiles -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Storage for tile caching -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

**Note:** For Android 13+ (API 33), consider scoped storage and update permission handling accordingly.

## Troubleshooting

### Map Tiles Not Loading

**Symptoms:** Gray tiles or no map display

**Solutions:**
1. Check internet connection
2. Verify tile source is accessible
3. Clear osmdroid cache: `Configuration.getInstance().osmdroidBasePath`
4. Check logcat for HTTP errors

### Position Not Updating

**Symptoms:** Marker stays at initial position

**Solutions:**
1. Verify DataPad connection is active (check status indicator)
2. Ensure latitude/longitude are not 0,0
3. Check DataPad UDP stream is sending valid coordinates
4. Verify FlightData StateFlow is emitting updates

### Auto-Center Not Working

**Symptoms:** Map doesn't follow aircraft

**Solutions:**
1. Click the center button (🎯) to re-enable auto-centering
2. Manual map interaction disables auto-center
3. Check `autoCenter` state variable is true

### Performance Issues

**Symptoms:** Laggy map or dropped frames

**Solutions:**
1. Reduce tile quality or zoom level
2. Clear tile cache to free memory
3. Disable auto-center for manual navigation
4. Check for excessive marker updates (add debouncing)

## Future Enhancements

### Planned Features

1. **Flight Path Trail:**
   - Draw historical position track
   - Color-coded by altitude or speed
   - Configurable trail length

2. **Waypoint Display:**
   - Show flight plan waypoints on map
   - Distance/bearing to next waypoint
   - ETA calculations

3. **Threat Overlay:**
   - Display RWR threats from DataPad
   - Show SAM/AAA ranges
   - Color-coded by threat level

4. **Offline Maps:**
   - Download regions for offline use
   - Manage offline tile storage
   - Fallback to cached tiles

5. **Multi-Aircraft Display:**
   - Show friendly aircraft positions
   - TCAS-style collision avoidance
   - Formation flying support

6. **Aviation Chart Layers:**
   - VFR sectional charts
   - IFR low/high altitude charts
   - Airspace boundaries
   - Airport markers and info

### Code Extensions

#### Adding Flight Path Trail

```kotlin
private val pathOverlay = Polyline().apply {
    outlinePaint.color = Color.BLUE
    outlinePaint.strokeWidth = 5f
}

// In update logic:
val points = pathOverlay.actualPoints.toMutableList()
points.add(newPosition)
if (points.size > 100) points.removeAt(0) // Keep last 100 points
pathOverlay.setPoints(points)
```

#### Custom Marker Icon

```kotlin
val aircraftDrawable = BitmapDrawable(
    resources,
    BitmapFactory.decodeResource(resources, R.drawable.aircraft_icon)
)
marker.icon = aircraftDrawable
```

## Dependencies

### Gradle Dependencies

```kotlin
implementation("org.osmdroid:osmdroid-android:6.1.20")
```

### Required Libraries

- **osmdroid-android:** OpenStreetMap library for Android
- **Jetpack Compose:** UI framework
- **kotlinx-coroutines:** Async operations
- **StateFlow:** Reactive state management

## Resources

### External Documentation

- [osmdroid GitHub](https://github.com/osmdroid/osmdroid)
- [osmdroid Wiki](https://github.com/osmdroid/osmdroid/wiki)
- [OpenStreetMap](https://www.openstreetmap.org/)
- [OpenAIP](https://www.openaip.net/)
- [OpenFlightMaps](https://www.openflightmaps.org/)

### Related Internal Docs

- [DataPad Feature](../features/DATAPAD_FEATURE.md)
- [Tab System](../features/TAB_SYSTEM.md)
- [DCS Export API](DCS_EXPORT_API.md)
- [AES GCM Encryption](AES_GCM_ENCRYPTION.md)

## Changelog

### Version 1.0.11 (2024-12-16)
- ✅ Initial aviation map implementation
- ✅ MapViewer with osmdroid integration
- ✅ Live position tracking from DataPad
- ✅ Map tab type in TabManager
- ✅ Multiple tile source support
- ✅ Auto-centering and manual controls
- ✅ Connection status indicators
- ✅ Integration with tab system

## License

Same license as the main project (see root LICENSE file).


---
App Version: v1.0.25
Last Updated: 2026.01.19
---