# Tactical Units Tracking System

## Overview

The Tactical Units Tracking System exports all visible units from DCS World (aircraft, ground forces, ships, etc.), sends them encrypted to the Android app, and displays them as markers on the map.

## Components

### 1. DCS Export (Export.lua)

**Function `collect_nearby_units()`** collects:
- All visible units from `LoGetWorldObjects()`
- Position (lat/lon/alt), heading, speed
- Category (aircraft, helicopter, ground, ship, structure)
- Coalition (0 = neutral, 1 = red, 2 = blue)
- Distance and bearing to the player

**Integration:**
- Called automatically every frame
- Data is added to the `nearbyUnits` array inside the FlightData JSON
- Encrypted transmission via UDP (ECDH + AES-GCM)

### 2. Android Database

**TacticalUnitEntity:**
- Stores the current status of each unit
- `isActive` flag: 1 = visible, 0 = visual contact lost
- Timestamps: `firstSeenAt`, `lastSeenAt`, `lastUpdateAt`

**TacticalUnitHistoryEntity:**
- Stores position history for track replay
- Foreign key to `TacticalUnitEntity`
- Automatic cascade deletion

**Migration v6 → v7:**
- New tables `tactical_units` and `tactical_unit_history`
- Indexes for performance (dcs_id, category, coalition, is_active)

### 3. DataPadManager Integration

**Function `processNearbyUnits()`:**
1. Compare received units with the DB
2. **New units:** INSERT + history entry
3. **Known units:** UPDATE position + history entry
4. **Lost units:** Mark as `isActive = 0` (remain in DB)

**Lifecycle:**
- Runs automatically on every received FlightData packet
- Uses coroutines for DB operations (non-blocking)

### 4. Repository & ViewModel

**TacticalUnitsRepository:**
- High-level API for unit management
- Filtering system (category, coalition, active/inactive)
- Statistics (counts per category and coalition)
- Cleanup functions (old inactive units, old history)

**TacticalUnitsViewModel:**
- UI state management
- Reactive flows for the units list
- Filter logic (categories, coalitions, search)

### 5. UI Components

**TacticalUnitsListScreen:**
- List of all tracked units
- Search (name, group)
- Filter dialog (category, coalition, active/inactive)
- Unit cards with status badges
- Statistics display

**FAB Button:**
- TrackChanges icon (radar-like)
- Position: `defaultX=0.95f`, `defaultY=0.15f`
- Integrated in the MapViewer FAB overlay

## Usage

### Setup

1. **DCS:**
   - Copy `Export.lua` to `Saved Games/DCS/Scripts/`
   - Start `forward_parsed_udp.py`

2. **Android App:**
   - Enable DataPad (Settings)
   - Perform the ECDH handshake
   - Open the Tactical Units list via the FAB

### Workflow

1. **DCS collects units** → `Export.lua` writes JSON
2. **Python forwards** → encrypted UDP packets
3. **App receives** → DataPadManager processes the data
4. **DB stores** → `TacticalUnitEntity` + history entries
5. **Map shows** → markers on the map (via tactical icons: enemy = red, BLUFOR = blue, civilian = yellow)
6. **List shows** → `TacticalUnitsListScreen`

### Filter & Search

- **Categories:** aircraft, helicopter, ground, ship, structure
- **Coalitions:** Neutral, Red, Blue
- **Status:** Active (visible), Lost (visual contact lost)
- **Search:** by name or group

### Cleanup

- **Auto-Cleanup:** old inactive units (7 days)
- **History:** remove old history entries (14 days)
- **Manual:** "Delete All" in the UI

## Security

- **ECDH Key Exchange:** secure key agreement
- **AES-GCM Encryption:** all data encrypted
- **Device Authorization:** only authorized devices (see `authorized_devices.json`)
- **DoS Protection:** rate limiting (per IP, global)

## Performance

- **DB Indexes:** optimized for fast queries
- **Coroutines:** non-blocking DB operations
- **StateFlow:** reactive UI updates
- **History Limit:** configurable (default 14 days)

## TODO

1. ✅ Export.lua nearbyUnits collection
2. ✅ Extend tactical entities
3. ✅ Database migration v6 → v7
4. ✅ DataPadManager processing
5. ✅ Create repository
6. ✅ UI screen + ViewModel
7. ✅ Add FAB button
8. ✅ Map integration (markers on map) - **live tracking with automatic updates**
9. ⏳ Navigation integration (screen routing)
10. ⏳ Unit detail view (with history display)

## Next Steps

### Map Integration ✅ IMPLEMENTED

Units are now displayed **live automatically** as markers on the map:

**Features:**
- ✅ Live updates: markers move automatically with unit positions
- ✅ Auto-remove: inactive units disappear from the map immediately
- ✅ Coalition colors: Neutral (gray), Red (red), Blue (blue)
- ✅ Category icons: Aircraft, Helicopter, Ground, Ship
- ✅ Heading display: markers rotate to match unit heading
- ✅ Details on click: name, category, coalition, speed, altitude, group
- ✅ Toggle control: only shown when entity tracking is enabled

**Implementation:**
```kotlin
// In MapViewer.kt:
LaunchedEffect(mapState.mapView, tacticalUnitsRepository, dataPadManager.isEntityTrackingEnabled) {
    repo.getAllActiveUnits().collect { units ->
        // Update markers: new units → create markers, inactive units → remove markers
        // Existing units → update position + heading
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
            // Navigate to detail view or center map on the unit
        }
    )
}
```

## Troubleshooting

### Units are not shown
- Is DataPad enabled? (Settings)
- Was the ECDH handshake successful? (Connection status)
- Is DCS running and is `Export.lua` active?
- Is `forward_parsed_udp.py` running?

### Old units remain visible
- Run cleanup (Settings in `TacticalUnitsListScreen`)
- Or manually: "Delete All Units"

### Performance issues
- Run history cleanup
- Delete old inactive units
- Check database size

## License

Part of ChecklistInteractive — see the main LICENSE

