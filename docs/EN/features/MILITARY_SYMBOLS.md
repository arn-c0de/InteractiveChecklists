# Military Symbol Marker System

## Overview
The Military Symbol Marker System enables placing NATO military symbols (APP-6 compatible) on the map from both the Kotlin Android app and the Python MapDatabaseTools.

## Features

### Kotlin App (MapViewer)

1. **Symbol Selection Dialog**
   - FAB with a "+" icon opens the Military Symbol Picker
   - Grid layout for easy navigation
   - Categories: Ground Units, Equipment, Installations, Activities, Unit Size
   - Affiliation selection: Friendly (Blue), Hostile (Red), Neutral (Green), Unknown (Yellow)

2. **Symbol Placement**
   - After selecting a symbol: tap the map to place it
   - Automatically stored in the `tactical.locations` database
   - Stores position, symbol type, affiliation, and optional metadata

3. **Symbol Display**
   - Loads all symbols from the DB when the map initializes
   - Renders NATO icons with correct color based on affiliation
   - Supports various symbol types: Mortar, Missile, Military Police, Bridge/Engineering, etc.

### Database Schema Extensions

New fields added to `LocationEntity`:
- `symbol_set`: symbol category (e.g., "ground_unit", "equipment")
- `symbol_entity`: specific entity type (e.g., "infantry", "armor", "mortar")
- `symbol_size`: unit size (e.g., "squad", "regiment")
- `symbol_affiliation`: affiliation ("friendly", "hostile", "neutral", "unknown")
- `symbol_color`: background color based on affiliation
- `symbol_modifier`: additional modifiers (JSON)

These fields have sensible defaults to preserve backwards compatibility with older database versions.

### Python MapDatabaseTools Integration

1. **marker_icons.py updates**
   - `TacticalMarkerStyle.get_style()` accepts `symbol_affiliation` and related parameters
   - New supported symbols: mortar, missile, mp, bridge, engineer
   - Fully compatible with the Kotlin app symbol set

2. **markers_database.py updates**
   - `Location` dataclass extended with military symbol fields
   - Supports both new and legacy fields for backward compatibility

3. **location_manager.py updates**
   - Displays military symbols in lists with correct colors
   - Allows filtering by `symbol_affiliation` and other symbol fields

## Usage

### In the Kotlin App

1. Open the Map tab
2. Tap the FAB with the "+" icon to open the picker
3. Choose an affiliation (Friendly / Hostile / Neutral / Unknown)
4. Choose a category and symbol
5. Tap the desired position on the map to place the symbol
6. The symbol is saved to the database and will appear on map reloads

### In Python MapDatabaseTools

Symbols are automatically loaded from the shared database and displayed on Folium/Leaflet maps.

```python
from core.markers_database import Location, MarkersDatabase

db = MarkersDatabase("path/to/markers.db")

# Create a new military symbol
location = Location(
    name="Enemy SAM Site",
    latitude=42.5,
    longitude=41.7,
    marker_type="tactical_military",
    symbol_entity="sam",
    symbol_affiliation="hostile",
    symbol_color="#FF4444",
    description="SA-10 Battery"
)

db.add_location(location)
```

## Available Symbols

### Equipment
- Mortar
- Missile (Surface-to-Surface)
- SAM Site
- AAA
- Radar
- Tank

### Installations
- Headquarters
- Supply
- Bridge / Engineering
- Airfield
- Port

### Activities
- Military Police (MP)
- Medical
- Engineer
- Signal

### Unit Sizes
- Squad
- Platoon
- Company
- Battalion
- Regiment

## Icon Resources

Android Vector Drawables in `app/src/main/res/drawable/`:
- `ic_mapicon_mortar.xml`
- `ic_mapicon_missile.xml`
- `ic_mapicon_military_police.xml`
- `ic_mapicon_bridging.xml`
- `ic_mapicon_size_regiment.xml`
- `ic_mapicon_size_squad.xml`

Additional symbols can be added by converting NATO APP-6 SVGs to Android Vector Drawables.

## Compatibility

- **Android App**: Fully implemented
- **Python Tools**: Fully compatible
- **Database**: Shared SQLite database with new fields (defaults provided for backward compatibility)
- **Migration**: No migration required—the new fields have default values

## Future Extensions

- [ ] Additional NATO APP-6 symbols
- [ ] Symbol editing (change position, type)
- [ ] Symbol rotation / heading
- [ ] Unit size modifier display
- [ ] Mobility / status modifiers
- [ ] Combat effectiveness indicator


---
App Version: v1.0.25
Last Updated: 2026.01.19
---