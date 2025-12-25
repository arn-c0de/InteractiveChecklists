# Tactical Units Tracking System

## Übersicht

Das Tactical Units Tracking System exportiert alle sichtbaren Einheiten aus DCS World (Flugzeuge, Bodentruppen, Schiffe, etc.), sendet sie verschlüsselt an die Android-App und zeigt sie als Marker auf der Karte an.

## Komponenten

### 1. DCS Export (Export.lua)

**Funktion `collect_nearby_units()`** sammelt:
- Alle sichtbaren Einheiten aus `LoGetWorldObjects()`
- Position (lat/lon/alt), Heading, Speed
- Kategorie (aircraft, helicopter, ground, ship, structure)
- Coalition (0=neutral, 1=red, 2=blue)
- Entfernung und Peilung zum Spieler

**Integration:**
- Wird automatisch bei jedem Frame aufgerufen
- Daten werden im `nearbyUnits` Array zum FlightData JSON hinzugefügt
- Verschlüsselte Übertragung via UDP (ECDH + AES-GCM)

### 2. Android Datenbank

**TacticalUnitEntity:**
- Speichert aktuellen Status jeder Einheit
- `isActive` Flag: 1 = sichtbar, 0 = Sichtkontakt verloren
- Timestamps: `firstSeenAt`, `lastSeenAt`, `lastUpdateAt`

**TacticalUnitHistoryEntity:**
- Speichert Positionshistorie für Track-Replay
- Foreign Key zu TacticalUnitEntity
- Automatische Cascade-Deletion

**Migration v6 → v7:**
- Neue Tabellen `tactical_units` und `tactical_unit_history`
- Indizes für Performance (dcs_id, category, coalition, is_active)

### 3. DataPadManager Integration

**Funktion `processNearbyUnits()`:**
1. Vergleicht empfangene Units mit DB
2. **Neue Units:** INSERT + History-Eintrag
3. **Bekannte Units:** UPDATE Position + History-Eintrag
4. **Verlorene Units:** Markiert als `isActive=0` (bleiben in DB)

**Lifecycle:**
- Läuft automatisch bei jedem empfangenen FlightData Paket
- Nutzt Coroutines für DB-Operationen (non-blocking)

### 4. Repository & ViewModel

**TacticalUnitsRepository:**
- High-level API für Unit-Verwaltung
- Filter-System (Kategorie, Coalition, Active/Inactive)
- Statistiken (Anzahl pro Kategorie, Coalition)
- Cleanup-Funktionen (alte inactive Units, alte History)

**TacticalUnitsViewModel:**
- UI-State Management
- Reaktive Flows für Units-Liste
- Filter-Logik (Categories, Coalitions, Search)

### 5. UI Komponenten

**TacticalUnitsListScreen:**
- Liste aller tracked Units
- Suchfunktion (Name, Group)
- Filter-Dialog (Category, Coalition, Active/Inactive)
- Unit-Cards mit Status-Badges
- Statistiken-Anzeige

**FAB Button:**
- TrackChanges Icon (Radar-ähnlich)
- Position: defaultX=0.95f, defaultY=0.15f
- In MapViewerFAB Overlay integriert

## Verwendung

### Setup

1. **DCS:** 
   - Export.lua in `Saved Games/DCS/Scripts/` kopieren
   - forward_parsed_udp.py starten

2. **Android App:**
   - DataPad aktivieren (Settings)
   - ECDH Handshake durchführen
   - Tactical Units FAB öffnet Liste

### Workflow

1. **DCS sammelt Units** → Export.lua schreibt in JSON
2. **Python forwarded** → Verschlüsselte UDP-Pakete
3. **App empfängt** → DataPadManager verarbeitet
4. **DB speichert** → TacticalUnitEntity + History
5. **Map zeigt** → Marker auf Karte (TODO) via tactical icons enemy are red blöufor blue civilian yellow
6. **Liste zeigt** → TacticalUnitsListScreen

### Filter & Suche

- **Kategorien:** aircraft, helicopter, ground, ship, structure
- **Coalitions:** Neutral, Red, Blue
- **Status:** Active (sichtbar), Lost (Sichtkontakt verloren)
- **Suche:** Nach Name oder Gruppe

### Cleanup

- **Auto-Cleanup:** Alte inactive Units (7 Tage)
- **History:** Alte Einträge (14 Tage)
- **Manuell:** "Delete All" in UI

## Sicherheit

- **ECDH Key Exchange:** Sichere Schlüsselvereinbarung
- **AES-GCM Encryption:** Alle Daten verschlüsselt
- **Device Authorization:** Nur authorized devices (authorized_devices.json)
- **DoS Protection:** Rate Limiting (per IP, global)

## Performance

- **DB Indizes:** Optimiert für schnelle Queries
- **Coroutines:** Non-blocking DB Operations
- **StateFlow:** Reaktive UI Updates
- **History Limit:** Konfigurierbar (Standard 14 Tage)

## TODO

1. ✅ Export.lua nearbyUnits Collection
2. ✅ TacticalEntities erweitern
3. ✅ Database Migration v6→v7
4. ✅ DataPadManager Processing
5. ✅ Repository erstellen
6. ✅ UI Screen + ViewModel
7. ✅ FAB Button hinzufügen
8. ✅ Map Integration (Marker auf Karte) - **Live tracking mit automatischen Updates**
9. ⏳ Navigation Integration (Screen Routing)
10. ⏳ Unit Detail View (mit History-Anzeige)

## Nächste Schritte

### Map Integration ✅ IMPLEMENTIERT

Die Units werden jetzt **automatisch live** als Marker auf der Karte angezeigt:

**Features:**
- ✅ Live-Updates: Marker bewegen sich automatisch mit Unit-Positionen
- ✅ Auto-Remove: Inactive Units verschwinden sofort von der Karte
- ✅ Coalition-Farben: Neutral (grau), Red (rot), Blue (blau)
- ✅ Kategorie-Icons: Aircraft, Helicopter, Ground, Ship
- ✅ Heading-Anzeige: Marker rotieren entsprechend Unit-Richtung
- ✅ Details beim Click: Name, Kategorie, Coalition, Speed, Altitude, Group
- ✅ Toggle-Control: Nur angezeigt wenn Entity Tracking aktiviert ist

**Implementierung:**
```kotlin
// In MapViewer.kt:
LaunchedEffect(mapState.mapView, tacticalUnitsRepository, dataPadManager.isEntityTrackingEnabled) {
    repo.getAllActiveUnits().collect { units ->
        // Update markers: Neue Units → neue Marker, Inactive Units → Marker entfernen
        // Bestehende Units → Position + Heading aktualisieren
    }
}
```

### Navigation Integration

```kotlin
// In Navigation Graph:
composable("tactical_units") {
    val viewModel: TacticalUnitsViewModel = viewModel(
        factory = TacticalUnitsViewModelFactory(
            TacticalUnitsRepository(LocalContext.current)
        )
    )
    TacticalUnitsListScreen(
        viewModel = viewModel,
        onNavigateBack = { navController.popBackStack() },
        onUnitClick = { unit ->
            // Navigate to detail view or center map on unit
        }
    )
}
```

## Fehlerbehebung

### Units werden nicht angezeigt
- DataPad aktiviert? (Settings)
- ECDH Handshake erfolgreich? (Connection Status)
- DCS läuft und Export.lua aktiv?
- forward_parsed_udp.py läuft?

### Alte Units bleiben sichtbar
- Cleanup durchführen (Settings in TacticalUnitsListScreen)
- Oder manuell: "Delete All Units"

### Performance-Probleme
- History-Cleanup durchführen
- Alte inactive Units löschen
- DB-Größe prüfen

## Lizenz

Teil von ChecklistInteractive - Siehe Haupt-LICENSE
