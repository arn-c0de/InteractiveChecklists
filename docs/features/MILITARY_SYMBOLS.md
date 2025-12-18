# Military Symbol Marker System

## Übersicht
Das Military Symbol Marker System ermöglicht das Platzieren von NATO-Symbolen (APP-6 kompatibel) auf der Karte sowohl in der Kotlin App als auch in den Python MapDatabaseTools.

## Features

### Kotlin App (MapViewer)

1. **Symbol-Auswahl Dialog**
   - FAB Button mit "+" Symbol öffnet den Military Symbol Picker
   - Grid-Layout zur einfachen Navigation
   - Kategorien: Ground Units, Equipment, Installations, Activities, Unit Size
   - Affiliation-Auswahl: Friendly (Blue), Hostile (Red), Neutral (Green), Unknown (Yellow)

2. **Symbol-Platzierung**
   - Nach Auswahl: Klick auf Karte platziert Symbol
   - Automatische Speicherung in `tactical.locations` Datenbank
   - Position, Symbol-Typ, Affiliation werden gespeichert

3. **Symbol-Anzeige**
   - Lädt alle Symbols beim Map-Start aus DB
   - Zeigt NATO-Icons mit korrekter Farbe (basierend auf Affiliation)
   - Unterstützt verschiedene Symbol-Typen: Mortar, Missile, Military Police, Bridging, etc.

### Datenbank-Schema Erweiterungen

Neue Felder in `LocationEntity`:
- `symbol_set`: Symbol-Kategorie (z.B. "ground_unit", "equipment")
- `symbol_entity`: Einheitentyp (z.B. "infantry", "armor", "mortar")
- `symbol_size`: Einheitengröße (z.B. "squad", "regiment")
- `symbol_affiliation`: Zugehörigkeit ("friendly", "hostile", "neutral", "unknown")
- `symbol_color`: Hintergrundfarbe basierend auf Affiliation
- `symbol_modifier`: Zusätzliche Modifikatoren (JSON)

### Python MapDatabaseTools Integration

1. **marker_icons.py Updates**
   - `TacticalMarkerStyle.get_style()` unterstützt `symbol_affiliation` Parameter
   - Neue Military Symbols: mortar, missile, mp, bridge, engineer
   - Kompatibel mit Kotlin App Symbolen

2. **markers_database.py Updates**
   - `Location` Dataclass erweitert mit Military Symbol Feldern
   - Unterstützt neue und legacy Felder (Rückwärtskompatibilität)

3. **location_manager.py Updates**
   - Zeigt Military Symbols in Liste mit korrekter Farbe
   - Filtert nach `symbol_affiliation` für neue Symbols

## Verwendung

### In der Kotlin App

1. Öffne Map Tab
2. Klicke auf FAB Button mit "+" Symbol
3. Wähle Affiliation (Friendly/Hostile/Neutral/Unknown)
4. Wähle Kategorie und Symbol
5. Klicke auf gewünschte Position auf der Karte
6. Symbol wird platziert und in DB gespeichert

### In Python MapDatabaseTools

Symbols werden automatisch aus der shared Database geladen und auf der Folium/Leaflet Karte angezeigt.

```python
from core.markers_database import Location, MarkersDatabase

db = MarkersDatabase("path/to/markers.db")

# Neues Military Symbol erstellen
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

## Verfügbare Symbole

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
- Bridge/Engineering
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

Weitere Symbole können durch Konvertierung der NATO APP-6 SVGs hinzugefügt werden.

## Kompatibilität

- **Android App**: Vollständig implementiert
- **Python Tools**: Vollständig kompatibel
- **Database**: Shared SQLite Database mit neuen Feldern (mit Defaults für Rückwärtskompatibilität)
- **Migration**: Keine Migration erforderlich - neue Felder haben Default-Werte

## Zukünftige Erweiterungen

- [ ] Weitere NATO APP-6 Symbole
- [ ] Symbol-Bearbeitung (Position, Typ ändern)
- [ ] Symbol-Rotation/Heading
- [ ] Unit Size Modifier-Anzeige
- [ ] Mobility/Status Modifier
- [ ] Combat Effectiveness Indicator
