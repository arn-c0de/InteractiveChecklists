# Tactical Map System mit Routen-Funktionalität

Vollständiges System für taktische Karten mit Markern, Wegpunkten und Flugrouten mit automatischer Berechnung von Distanz und Kurs (Heading).

## 🏗️ Architektur

### Clean Architecture Pattern

```
┌─────────────────────────────────────────────────┐
│              UI / App Layer                      │
│         (Android Kotlin / Python GUI)            │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│            Repository Layer                      │
│   MarkerRepository / RouteRepository             │
│   - getAll(), add(), update(), delete()          │
│   - saveRouteWithWaypoints()                     │
└────────────────────┬────────────────────────────┘
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
┌───────────────────┐    ┌───────────────────────┐
│  LocalDataSource  │    │   Future: SyncService │
│  (Room / SQLite)  │    │   (UDP/TCP/Cloud)     │
│                   │    │                       │
│  map_data.db       │    │  - Sync between       │
│                   │    │    devices            │
│  - locations      │    │  - Conflict resolution│
│  - routes         │    │  - AES-GCM encryption │
│  - route_waypoints│    │                       │
│  - borders        │    │                       │
└───────────────────┘    └───────────────────────┘
```

## 📁 Dateistruktur

### Android App (Kotlin)

```
app/src/main/java/com/example/checklist_interactive/
├── data/
│   └── tactical/
│       ├── TacticalDatabase.kt          # Room Database
│       ├── TacticalEntities.kt          # Entity Klassen
│       ├── TacticalDaos.kt              # Data Access Objects
│       └── TacticalRepositories.kt      # Repository Implementierungen
└── ui/
    └── maps/
        ├── MapViewer.kt                 # Hauptkarte
        └── RouteCreationUI.kt           # Routen-Erstellung UI
```

### Python Tools

```
scripts/MapDatabaseTools/
├── core/
│   ├── markers_database.py              # Datenbank-Modul
│   └── marker_icons.py                  # Icon-System
└── route_manager.py                     # Routen-Management CLI
```

## 🗄️ Datenbank-Schema

### locations (Marker/Wegpunkte)

```sql
CREATE TABLE locations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    marker_type TEXT NOT NULL,           -- airport, waypoint, tactical_*, etc.
    coalition TEXT,                      -- BLUFOR, OPFOR, NEUTRAL
    tactical_symbol TEXT,                -- fighter, sam, infantry, etc.
    icon TEXT DEFAULT 'default',
    description TEXT,
    
    -- Airport-spezifisch
    icao TEXT,
    iata TEXT,
    elevation_m REAL,
    frequencies TEXT,                    -- JSON
    runways TEXT,                        -- JSON
    
    -- Taktisch-spezifisch
    threat_level INTEGER,                -- 1-5
    unit_type TEXT,
    strength INTEGER,
    
    -- Metadaten
    created TEXT NOT NULL,
    modified TEXT NOT NULL,
    tags TEXT,                           -- JSON
    metadata TEXT                        -- JSON
);
```

### routes (Flugrouten)

```sql
CREATE TABLE routes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    description TEXT,
    color TEXT DEFAULT '#00A8FF',
    created TEXT NOT NULL,
    modified TEXT NOT NULL
);
```

### route_waypoints (Routen-Wegpunkte mit Navigation)

```sql
CREATE TABLE route_waypoints (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    route_id INTEGER NOT NULL,
    location_id INTEGER NOT NULL,
    sequence INTEGER NOT NULL,           -- Reihenfolge (0, 1, 2, ...)
    distance_nm REAL,                    -- Distanz zum nächsten WP in NM
    heading_mag REAL,                    -- Magnetischer Kurs zum nächsten WP (0-360°)
    FOREIGN KEY (route_id) REFERENCES routes(id) ON DELETE CASCADE,
    FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE CASCADE
);
```

### borders (Kartenregionen)

```sql
CREATE TABLE borders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    points TEXT NOT NULL,                -- JSON: [[lat, lon], ...]
    description TEXT,
    color TEXT DEFAULT '#FF0000',
    created TEXT NOT NULL,
    modified TEXT NOT NULL
);
```

## 🚀 Verwendung

### Python: Routen erstellen

```python
from core.markers_database import MarkersDatabase, Location, Route

db = MarkersDatabase()

# Wegpunkte erstellen
wp1_id = db.add_location(Location(
    name="Start Point",
    latitude=36.0,
    longitude=-115.0,
    marker_type="waypoint"
))

wp2_id = db.add_location(Location(
    name="Target",
    latitude=36.5,
    longitude=-115.5,
    marker_type="target"
))

# Route mit automatischer Berechnung
route = Route(
    name="Mission Alpha",
    description="Training mission"
)

route_id = db.add_route(route, [wp1_id, wp2_id])

# Route abrufen mit Details
route_data = db.get_route_with_waypoints(route_id)
print(f"Total distance: {route_data['total_distance_nm']:.1f} NM")

for loc, wp in route_data['waypoints']:
    if wp.distance_nm and wp.heading_mag:
        print(f"{loc.name} → {wp.distance_nm:.1f} NM @ {wp.heading_mag:.0f}°")
```

### Python CLI Tool

```bash
# Beispiel-Route erstellen
python route_manager.py create-example

# Alle Routen auflisten
python route_manager.py list

# Route Details anzeigen
python route_manager.py show 1

# Route löschen
python route_manager.py delete 1
```

### Android Kotlin: Repository verwenden

```kotlin
// ViewModel oder Composable
val db = TacticalDatabase.getInstance(context, useExternalPath = true)
val routeRepo = RouteRepositoryImpl(db.routeDao(), db.locationDao())
val locationRepo = LocationRepositoryImpl(db.locationDao())

// Route erstellen
val route = RouteEntity(
    name = "Training Mission",
    description = "Nellis to Target Area",
    color = "#00A8FF",
    created = "",
    modified = ""
)

val locationIds = listOf(1, 2, 3, 4) // IDs der Wegpunkte

val routeId = routeRepo.saveRouteWithWaypoints(route, locationIds)

// Route mit Waypoints laden
val routeWithWaypoints = routeRepo.getRouteWithWaypoints(routeId)
routeWithWaypoints?.let { data ->
    data.waypoints.forEach { wpWithLoc ->
        val loc = wpWithLoc.location
        val wp = wpWithLoc.waypoint
        Log.d("Route", "${loc.name}: ${wp.distanceNm} NM @ ${wp.headingMag}°")
    }
}

// Alle Routen beobachten (Flow)
routeRepo.getAllRoutesWithWaypoints().collect { routes ->
    routes.forEach { route ->
        // UI aktualisieren
    }
}
```

### Android UI: Routen erstellen

In `MapViewer.kt`:

```kotlin
val viewModel = remember { RouteCreationViewModel(routeRepo, locationRepo) }
var showRouteCreation by remember { mutableStateOf(false) }

// Route-Button
FloatingActionButton(onClick = { 
    viewModel.startRouteCreation()
    showRouteCreation = true 
}) {
    Icon(Icons.Default.Route, "Create Route")
}

// Route creation sheet
if (showRouteCreation) {
    RouteCreationSheet(
        viewModel = viewModel,
        onDismiss = { showRouteCreation = false },
        onWaypointClick = { location ->
            // Karte zum Wegpunkt bewegen
            mapView?.controller?.animateTo(
                GeoPoint(location.latitude, location.longitude)
            )
        }
    )
}

// Karte: Wegpunkt hinzufügen bei Tap
mapView.setOnTouchListener { v, event ->
    if (event.action == MotionEvent.ACTION_UP && viewModel.isCreatingRoute.value) {
        val projection = mapView.projection
        val geoPoint = projection.fromPixels(event.x.toInt(), event.y.toInt())
        
        // Location in Nähe suchen oder neue erstellen
        // viewModel.addWaypoint(location)
    }
    false
}
```

## 📐 Navigations-Berechnungen

### Distanz (Haversine-Formel)

```kotlin
fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0 // Earth radius in km
    
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val deltaLat = Math.toRadians(lat2 - lat1)
    val deltaLon = Math.toRadians(lon2 - lon1)
    
    val a = sin(deltaLat / 2).pow(2) + 
            cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    
    return R * c // km
}
```

### Kurs/Heading (Initial Bearing)

```kotlin
fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val deltaLon = Math.toRadians(lon2 - lon1)
    
    val x = sin(deltaLon) * cos(lat2Rad)
    val y = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLon)
    
    val bearing = Math.toDegrees(atan2(x, y))
    return (bearing + 360) % 360 // 0-360°
}
```

### Konvertierung

```kotlin
fun kmToNauticalMiles(km: Double) = km * 0.539957
fun nauticalMilesToKm(nm: Double) = nm / 0.539957
```

## 🎨 UI Features

### Route Creation Sheet

- ✅ Wegpunkte auf Karte auswählen
- ✅ Reihenfolge per Drag & Drop ändern
- ✅ Wegpunkte hinzufügen/entfernen
- ✅ Live-Vorschau der Route auf Karte
- ✅ Automatische Distanz/Heading-Berechnung
- ✅ Route benennen und speichern

### Karten-Visualisierung

- ✅ Polyline zwischen Wegpunkten
- ✅ Distanz- und Heading-Labels auf Linien
- ✅ Nummerierte Wegpunkt-Marker
- ✅ Route-Farbcodierung

## 📱 Datenbank-Speicherort

### Android Internal (Standard)

```
/data/data/com.example.checklist_interactive/databases/map_data.db
```

### Android External (für Python-Tools)

```
/sdcard/Android/data/com.example_checklist_interactive/files/map_data.db
```

**Hinweis:** External Storage ermöglicht Zugriff durch Python-Tools via `adb pull` oder direktes Lesen bei Root-Zugriff.

### Python Default

```
scripts/MapDatabaseTools/markers.db
```

## 🔄 Zukünftige Sync-Funktionen

Das System ist vorbereitet für:

- **UDP/TCP Sync** zwischen Geräten
- **AES-GCM Verschlüsselung** für sichere Übertragung
- **Conflict Resolution** (Last-Write-Wins oder manuell)
- **Cloud Sync** (Firebase, AWS, etc.)
- **Version Control** für Marker und Routen

Siehe [marker_sync_architecture.md](../../../marker_sync_architecture.md) für Details.

## 🛠️ Dependencies

### Android (build.gradle.kts)

```kotlin
dependencies {
    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // OSMDroid für Karten
    implementation("org.osmdroid:osmdroid-android:6.1.18")
}
```

### Python (requirements.txt)

```
# Keine zusätzlichen Dependencies nötig (nur stdlib)
```

## 📝 Beispiel-Workflow

1. **Python: Datenbank vorbereiten**
   ```bash
   python route_manager.py create-example
   python route_manager.py list
   ```

2. **Android: Datenbank laden**
   ```bash
   adb push markers.db /sdcard/Android/data/com.example_checklist_interactive/files/map_data.db
   ```

3. **Android App: Route anzeigen und bearbeiten**
   - Karte öffnen
   - Route-Button tippen
   - Wegpunkte auswählen
   - Route finalisieren

4. **Android: Datenbank exportieren**
   ```bash
   adb pull /sdcard/Android/data/com.example_checklist_interactive/files/map_data.db
   ```

5. **Python: Routen analysieren**
   ```bash
   python route_manager.py show 1
   ```

## 🎯 Features Checklist

- [x] SQLite-Datenbank mit Routes-Schema
- [x] Python CRUD für Routen
- [x] Automatische Distanz/Heading-Berechnung
- [x] Android Room Database
- [x] Repository Pattern (Clean Architecture)
- [x] Route Creation UI (Android)
- [x] Polyline-Visualisierung auf Karte
- [x] Python CLI Tool
- [ ] Marker Creation UI (Android)
- [ ] Route Import/Export (GPX, KML)
- [ ] Multi-Device Sync
- [ ] Conflict Resolution UI
- [ ] Web-basiertes Admin-Interface

## 📚 Weitere Ressourcen

- [AVIATION_MAP_FEATURE.md](../../../docs/features/AVIATION_MAP_FEATURE.md) - Karten-Features
- [marker_sync_architecture.md](../../../marker_sync_architecture.md) - Sync-Architektur
- [structure.md](../../../docs/technical/structure.md) - App-Struktur

## 🐛 Troubleshooting

### Python: Datenbank gesperrt

```python
# Lösung: Verbindung schließen
with MarkersDatabase() as db:
    # ... operations
# Automatisches Close via context manager
```

### Android: Room Migration Fehler

```kotlin
// In TacticalDatabase.kt:
.fallbackToDestructiveMigration()  // Nur für Development!
```

### Koordinaten stimmen nicht

```
Überprüfe:
- Latitude: -90 bis +90
- Longitude: -180 bis +180
- Heading: 0 bis 360
```

---

**Version:** 1.0  
**Datum:** 2024-12-18  
**Autor:** System Architecture
