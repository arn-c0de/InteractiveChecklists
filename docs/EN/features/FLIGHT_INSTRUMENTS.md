# Flight Instruments Feature

## Overview
Real-time aviation flight instruments displayed as overlay on the map, showing live data from DataPad connection. Each instrument is individually draggable and positions are persisted.

## Current Instruments

### 1. Attitude Indicator (Artificial Horizon)
- **Display**: Classic blue/brown horizon with pitch ladder
- **Data**: 
  - Pitch angle (up/down)
  - Bank angle (roll left/right)
- **Features**:
  - 10-degree pitch ladder markings
  - Bank angle scale (-60° to +60°)
  - Fixed aircraft symbol (yellow)
  - Bank pointer indicator

### 2. Turn and Slip Indicator
- **Display**: Combined turn coordinator and slip ball
- **Data**:
  - Turn rate (degrees/second)
  - Slip/skid indicator
- **Features**:
  - Miniature aircraft symbol tilts with turn rate
  - Standard rate turn markers (L/R)
  - Ball-in-tube slip indicator
  - Reference marks for coordinated flight

## Architecture

### File Structure
- `MapFlightInstruments.kt` - Main instrument display and drag system
- `MapViewer.kt` - Integration and data binding
- `MapViewerState.kt` - State management
- `OverlaySelectionDialog.kt` - UI controls

### Components

#### MapFlightInstruments
Main composable that manages all instruments:
```kotlin
@Composable
fun MapFlightInstruments(
    pitch: Double,
    bank: Double,
    turnRate: Double,
    slip: Double,
    enabled: Boolean
)
```

#### DraggableInstrument
Wrapper that makes any instrument draggable:
- Handles drag gestures
- Persists positions to SharedPreferences
- Constrains movement to screen bounds

#### Individual Instruments
- `AttitudeIndicator()` - Pitch and bank display
- `TurnAndSlipIndicator()` - Turn rate and slip display

### Data Flow
```
DataPad (DCS Export) 
    ↓
FlightData State
    ↓
MapViewer
    ↓
MapFlightInstruments
    ↓
Individual Instruments
```

## Usage

### Enable/Disable
1. Open Map view
2. Tap Overlays button (layers icon)
3. Toggle "Flight Instruments"

### Reposition Instruments
- **Drag**: Touch and drag any instrument to move it
- **Auto-save**: Positions saved automatically
- **Persistence**: Positions restored on app restart

### Data Requirements
- DataPad connection active
- Valid pitch and bank data from DCS

## Configuration

### Preferences Storage
Positions stored in SharedPreferences:
- `attitude_x` - X position of attitude indicator
- `attitude_y` - Y position of attitude indicator
- `turnslip_x` - X position of turn/slip indicator
- `turnslip_y` - Y position of turn/slip indicator
- `map_overlay_flight_instruments` - Enabled state

### Default Positions
- Attitude Indicator: (16dp, 500dp) - Left side, bottom area
- Turn/Slip Indicator: (200dp, 500dp) - Right of attitude indicator

## Extension Points

### Adding New Instruments
1. Create new `@Composable` function in `MapFlightInstruments.kt`
2. Add Canvas-based drawing logic
3. Wrap in `DraggableInstrument()`
4. Add position prefs keys
5. Add to `MapFlightInstruments()` main composable

### Example Structure
```kotlin
@Composable
fun NewInstrument(
    data: Double,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(size).clip(CircleShape).background(Color.Black)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw instrument graphics
            }
        }
        Text("LABEL", fontSize = 10.sp, color = Color.White)
    }
}
```

### Future Instruments (Planned)
- **Altimeter** - Altitude display with multiple hands
- **Vertical Speed Indicator** - Rate of climb/descent
- **Airspeed Indicator** - IAS with speed tape
- **Heading Indicator** - Directional gyro
- **Mach Meter** - Mach number display
- **G-Meter** - G-force indicator

## Technical Details

### Drawing System
- Uses Compose Canvas API
- Hardware-accelerated rendering
- 60fps smooth updates
- Minimal memory footprint

### Performance
- Recomposition only on data changes
- No unnecessary allocations
- Efficient clipping and transforms
- Optimized for mobile devices

### Coordinates
- Screen coordinates (dp)
- Origin: Top-left corner
- Y-axis: Down positive
- X-axis: Right positive

## Known Limitations
1. **Turn Rate**: Currently placeholder (0.0) - needs calculation from heading delta
2. **Slip Data**: Not available in current DataPad protocol
3. **Screen Size**: Fixed default positions may need adjustment on small screens
4. **Occlusion**: Can overlap map controls if positioned poorly

## Future Enhancements
- [ ] Auto-layout presets (left panel, bottom panel, etc.)
- [ ] Size adjustment per instrument
- [ ] Opacity control
- [ ] Turn rate calculation from heading changes
- [ ] Slip estimation from accelerometer data
- [ ] Instrument clustering/grouping
- [ ] Lock/unlock individual instruments
- [ ] Reset to default positions button
- [ ] Import/export layout configurations


---
App Version: v1.0.25
Last Updated: 2026.01.19
---