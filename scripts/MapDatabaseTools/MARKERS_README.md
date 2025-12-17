# Tactical Markers & Locations Database System

Modulares System für taktische Marker, Flughäfen und Wegpunkte mit SQLite-Datenbank.

## 📁 Dateien

- **markers_database.py** - SQLite-Datenbank mit vollständigem Schema für Locations
- **marker_icons.py** - Icon-System mit NATO-Style Symbologie (BLUFOR/OPFOR/NEUTRAL)
- **location_manager.py** - PySide6 UI-Widget für Location-Management
- **datapad_gui.py** - Hauptanwendung mit Split-View (FlightData + LocationManager + Map)
- **map.html** - Leaflet-Karte mit Marker-Unterstützung
- **markers.db** - SQLite-Datenbank (wird automatisch erstellt)

## 🚀 Features

### Marker-Typen
- **Airports** - Flughäfen mit ICAO/IATA, Landebahnen, Frequenzen, Elevation
- **Waypoints** - Navigationspunkte für Flugplanung
- **Tactical BLUFOR** - Freundliche militärische Einheiten
- **Tactical OPFOR** - Feindliche militärische Einheiten
- **Tactical NEUTRAL** - Neutrale Einheiten
- **POI** - Points of Interest
- **Threats** - Bedrohungen (SAM, AAA, etc.)
- **Targets** - Ziele

### Taktische Symbole
Unterstützte militärische Symbole (NATO APP-6 inspiriert):
- **Air**: Fighter, Bomber, Transport, Helicopter, UAV
- **Ground**: Infantry, Armor, Artillery, SAM, AAA, Radar
- **Naval**: Ship, Submarine
- **Support**: HQ, Supply

### Flughafen-Daten
- ICAO/IATA Codes
- Elevation
- Frequenzen (Tower, Ground, ATIS, etc.)
- Landebahnen mit:
  - Name (z.B. 09/27)
  - Länge und Breite
  - Heading
  - Oberfläche (concrete, asphalt, grass, dirt)
  - ILS-Verfügbarkeit

### Taktische Daten
- Coalition (BLUFOR/OPFOR/NEUTRAL)
- Threat Level (1-5)
- Unit Type
- Strength
- Tactical Symbol

## 💻 Verwendung

### DataPad GUI starten
```powershell
cd scripts\MapDatabaseTools
.\.venv\Scripts\Activate.ps1
python datapad_gui.py
```

### Nur Location Manager testen
```powershell
python location_manager.py
```

### Datenbank initialisieren mit Beispieldaten
```powershell
python markers_database.py
```

### Icons testen
```powershell
python marker_icons.py
```

## 🎨 UI-Layout

```
┌─────────────────────────────────────────────────────────────────┐
│  DCS DataPad - Live Flight Data & Tactical Markers             │
├──────────────────────┬──────────────────────────────────────────┤
│  Flight Data Panel   │                                          │
│  ┌────────────────┐  │                                          │
│  │ ⚫ Disconnected │  │                                          │
│  │ Last update: --│  │                                          │
│  │ [Settings]     │  │                                          │
│  └────────────────┘  │                                          │
│  ┌────────────────┐  │         Leaflet Map                      │
│  │ Aircraft Info  │  │    (OpenStreetMap + Markers)            │
│  │ Position Data  │  │                                          │
│  │ ...            │  │         🛩 Aircraft Position            │
│  └────────────────┘  │         ✈ Airports                      │
├──────────────────────┤         🚩 Waypoints                     │
│  Location Manager    │         🎯 Targets                       │
│  ┌────────────────┐  │         ⚠ Threats                       │
│  │ 📍 Locations   │  │                                          │
│  │ Filter: [All▼] │  │                                          │
│  │ Search: [___]  │  │                                          │
│  ├────────────────┤  │                                          │
│  │ ✈ Nellis AFB   │  │                                          │
│  │ 🚀 SA-10 Alpha │  │                                          │
│  │ 📍 IP Vegas    │  │                                          │
│  └────────────────┘  │                                          │
│  [➕Add][✏Edit]      │                                          │
│  [🗑Delete]          │                                          │
│  [Import][Export]    │                                          │
└──────────────────────┴──────────────────────────────────────────┘
```

## 🗃 Datenbank-Schema

### Locations Table
```sql
CREATE TABLE locations (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    marker_type TEXT NOT NULL,
    coalition TEXT,
    tactical_symbol TEXT,
    icon TEXT NOT NULL,
    description TEXT,
    
    -- Airport fields
    icao TEXT,
    iata TEXT,
    elevation_m REAL,
    frequencies TEXT (JSON),
    runways TEXT (JSON),
    
    -- Tactical fields
    threat_level INTEGER,
    unit_type TEXT,
    strength INTEGER,
    
    -- Metadata
    created TEXT NOT NULL,
    modified TEXT NOT NULL,
    tags TEXT (JSON),
    metadata TEXT (JSON)
);
```

### Indices
- `idx_marker_type` - Schnelle Filterung nach Marker-Typ
- `idx_coalition` - Filterung nach Coalition
- `idx_location` - Räumliche Suche
- `idx_name` - Textsuche

## 📊 Beispiel-Daten

Die Datenbank wird automatisch mit Beispieldaten befüllt:

1. **Nellis Air Force Base** (KLSV)
   - BLUFOR Airport
   - 2 Landebahnen mit ILS
   - Tower/Ground/ATIS Frequenzen

2. **SA-10 Site Alpha**
   - OPFOR SAM-Stellung
   - Threat Level 5
   - S-300 System

3. **IP Vegas**
   - Waypoint für Navigation
   - Initial Point für Anflug

## 🔧 API

### MarkersDatabase

```python
from markers_database import MarkersDatabase, Location, Runway

# Datenbank öffnen
db = MarkersDatabase()

# Location hinzufügen
location = Location(
    name="Ramstein AB",
    latitude=49.4369,
    longitude=7.6003,
    marker_type="airport",
    coalition="BLUFOR",
    icao="ETAR",
    runways=[
        Runway(name="09/27", length_m=3200, width_m=45, 
               heading=90, surface="concrete", ils=True)
    ]
)
db.add_location(location)

# Locations suchen
all_airports = db.get_all_locations(marker_type="airport")
nearby = db.get_nearby_locations(49.0, 8.0, radius_km=100)
results = db.search_locations("Ramstein")

# Location bearbeiten
location.description = "Updated"
db.update_location(location)

# Location löschen
db.delete_location(location.id)

# Import/Export
db.export_to_json(Path("export.json"))
db.import_from_json(Path("import.json"))
```

### Location Manager Widget

```python
from location_manager import LocationManagerWidget

# Widget erstellen
manager = LocationManagerWidget(db)

# Signale verbinden
manager.location_selected.connect(lambda loc: print(f"Selected: {loc.name}"))
manager.location_added.connect(lambda loc: print(f"Added: {loc.name}"))
manager.location_updated.connect(lambda loc: print(f"Updated: {loc.name}"))
manager.location_deleted.connect(lambda id: print(f"Deleted ID: {id}"))
```

## 🎯 Icon-System

### Verfügbare Icons

**Airports:**
- `airport_military` - Militärischer Flughafen
- `airport_civilian` - Zivilflughafen
- `heliport` - Hubschrauberlandeplatz

**BLUFOR:**
- `blufor_fighter`, `blufor_bomber`, `blufor_transport`
- `blufor_helo`, `blufor_uav`
- `blufor_infantry`, `blufor_armor`, `blufor_artillery`

**OPFOR:**
- `opfor_fighter`, `opfor_bomber`, `opfor_helo`, `opfor_uav`
- `opfor_sam`, `opfor_aaa`, `opfor_radar`
- `opfor_armor`, `opfor_infantry`

**Special:**
- `target`, `threat`, `poi`
- `waypoint_nav`, `waypoint_ip`, `waypoint_target`

### Icon-Farben (NATO APP-6)
- BLUFOR: `#00A8FF` (Blau)
- OPFOR: `#FF4444` (Rot)
- NEUTRAL: `#00FF00` (Grün)
- UNKNOWN: `#FFFF00` (Gelb)
- Airport: `#8B4513` (Braun)
- Waypoint: `#FFA500` (Orange)

## 🔐 Sicherheit & Best Practices

1. **Datenbank-Location**: Die markers.db liegt im scripts/MapDatabaseTools Ordner
2. **Backup**: Export regelmäßig als JSON (`Export JSON` Button)
3. **Thread-Safety**: Datenbank verwendet `check_same_thread=False` für GUI
4. **Validierung**: Alle Eingaben werden validiert vor dem Speichern

## 📱 Android-Integration (Zukünftig)

Die Datenbank ist so designed, dass sie später in die Android-App integriert werden kann:

1. **Datenbank kopieren**:
   ```
   scripts/MapDatabaseTools/markers.db 
   -> app/src/main/assets/databases/markers.db
   ```

2. **Kotlin Data Classes** werden benötigt für:
   - `Location`
   - `Runway`
   - `MarkerType` enum
   - `TacticalSymbol` enum

3. **MapViewer.kt** erweitern um Marker aus Datenbank zu laden

4. **Synchronisation**: Python-GUI kann Datenbank vorbereiten, Android-App liest sie

## 🛠 Abhängigkeiten

```
PySide6
PySide6-WebEngine
cryptography
```

Installieren mit:
```powershell
pip install PySide6 PySide6-WebEngine cryptography
```

## 📝 Lizenz

Teil des ChecklistInteractive Projekts.
