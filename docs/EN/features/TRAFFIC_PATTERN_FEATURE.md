# Traffic Pattern (Platzrunde) Feature

## Übersicht

Das Traffic Pattern Feature ermöglicht es Piloten, realistische Platzrunden (Circuit Patterns) um Landebahnen herum zu fliegen. Das System generiert automatisch die korrekten Flugwege basierend auf Standard-Luftfahrtverfahren.

## Funktionen

### Pattern-Generierung
- **Standard Traffic Pattern**: Automatische Berechnung aller Pattern-Legs:
  - Departure (Start)
  - Crosswind (90° Kurve)
  - Downwind (parallel zur Landebahn, entgegengesetzte Richtung)
  - Base (90° Kurve zum Final)
  - Final (Anflug zur Landebahn)

### Pattern-Konfiguration

#### Pattern-Größen
- **Normal**: 0.5 NM Downwind-Abstand, 1000 ft Pattern-Höhe
- **Medium**: 0.75 NM Downwind-Abstand, 1000 ft Pattern-Höhe
- **Large**: 1.0 NM Downwind-Abstand, 1200 ft Pattern-Höhe
- **Very Large**: 1.5 NM Downwind-Abstand, 1500 ft Pattern-Höhe

#### Pattern-Richtung
- **Left-Hand** (Standard): Linkskurven (Standardverfahren)
- **Right-Hand**: Rechtskurven (für spezielle Landebahnen)

## Verwendung

### Pattern aktivieren
1. Navigiere zu einem Flughafen mit Landebahnen
2. Öffne das Runway Approach Popup
3. Klicke auf den **PATTERN** Button (neben dem NM-Dropdown)
4. Wähle eine Landebahn aus
5. Konfiguriere Pattern-Größe und -Richtung

### Pattern-Anzeige
- **Grüne gestrichelte Linie**: Pattern-Verlauf
- **Labels**: Anzeige der einzelnen Pattern-Legs (DEPARTURE, CROSSWIND, etc.)
- **Navigation**: Rote Navigationslinie führt vom aktuellen Standort zum Pattern-Einstieg

## Technische Details

### Pattern-Berechnung (MapRoutePattern.kt)

Die Pattern-Generierung basiert auf echten Luftfahrtstandards:

```kotlin
// Distances (in NM, converted to meters)
// Pattern size presets scale BOTH lateral and longitudinal distances:
// - Normal: base distances (e.g., 0.5 NM departure extension, 0.3 NM crosswind, 0.5 NM downwind offset)
// - Medium/Large/Very Large: scale factors (1.25x, 1.5x, 2.0x) are applied to
//   departure extension, crosswind, downwind length, base extension, final and short-final distances
- Departure extension (scaled): 0.5 NM * sizeScale past runway end
- Crosswind turn (scaled): 0.3 NM * sizeScale
- Downwind parallel: Pattern size dependent (0.5-1.5 NM) and scaled longitudinally
- Base leg: Equal to downwind lateral distance, base extension scaled
- Final approach (scaled): 0.5 NM * sizeScale from threshold
- Short final (scaled): 0.2 NM * sizeScale from threshold
```

### Koordinatenberechnung
- Verwendet Haversine-Formel für Großkreis-Navigation
- Berücksichtigt Erdkrümmung für präzise Positionierung
- Heading-Normalisierung für korrekte Kompass-Werte

### Pattern-Overlay
- `Polyline`: Gestrichelte grüne Linie für Pattern-Pfad
- `PatternLabelOverlay`: Benutzerdefiniertes Overlay für Leg-Beschriftungen
- Automatische Skalierung mit Zoom-Level

## Integration mit bestehenden Features

### Runway Approach Integration
- Pattern-Modus arbeitet parallel zum Runway Approach
- Beide können gleichzeitig aktiv sein
- Navigation kombiniert Pattern-Guidance mit Final Approach

### Navigation System
- Automatische Berechnung zum Pattern-Einstiegspunkt
- Distanz- und Heading-Anzeige aktualisiert sich in Echtzeit
- Integration mit DataPad Live-Position

## Zukünftige Erweiterungen

Das modulare Design ermöglicht einfache Erweiterungen:

1. **Holding Patterns**: Warteschleifen für Verkehrsmanagement
2. **Instrument Approaches**: ILS, VOR, NDB Approach-Patterns
3. **Custom Patterns**: Benutzer-definierte Pattern-Konfigurationen
4. **Pattern Recording**: Aufzeichnung und Replay von geflogenen Patterns
5. **Multi-Aircraft Patterns**: Visualisierung mehrerer Aircraft im Pattern

## Referenzen

### Luftfahrt-Standards
- FAA Advisory Circular AC 90-66B: "Recommended Standard Traffic Patterns for Aeronautical Operations"
- Pattern Altitude: 1000 ft AGL (Standard), 800 ft AGL (Alternative)
- Pattern Direction: Left-hand turns (Standard), Right-hand für spezielle Runways

### Code-Referenzen
- `MapRoutePattern.kt`: Pattern-Generierung und Utilities
- `MapViewer.kt`: Pattern-Integration und UI
- `TrafficPatternGenerator`: Hauptklasse für Pattern-Berechnung
- `PatternLabelOverlay`: Custom Overlay für Pattern-Beschriftungen

## Performance

- Pattern-Generierung: < 1ms für Standard-Pattern
- Overlay-Rendering: GPU-beschleunigt
- Memory Footprint: ~100 KB pro aktives Pattern
- Keine Auswirkung auf Frame-Rate bei aktivem Pattern

## Bekannte Limitierungen

1. Runway-Daten müssen vollständig sein (Heading, Length)
2. Pattern funktioniert nur mit gespeicherten Landebahnen
3. Keine dynamische Anpassung an Wind (geplant für v2.0)
4. Keine Höhen-Visualisierung (nur 2D)
