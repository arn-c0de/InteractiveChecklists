# DataPad Feature

## Overview
DataPad is a live flight data display panel that receives real-time aircraft telemetry via UDP from DCS World through the `forward_parsed_udp.py` script.

Phase 1 (experimental): This implementation represents Phase 1 of DataPad. Future phases will expand telemetry coverage and add visual and security improvements, including live animated aircraft visualizations based on flight attitude and a dedicated DataPad UI redesign. DataPad now supports AES-GCM encrypted UDP telemetry (AEAD) with a pre-shared key. See `docs/technical/AES_GCM_ENCRYPTION.md` for configuration and key setup.

## Architecture

### Components

#### 1. Data Layer (`data/datapad/`)
- **FlightData.kt**: Kotlin data classes that match the JSON structure from DCS
- **DataPadManager.kt**: Manages UDP socket reception on port 5010, parses JSON, and provides reactive state via StateFlow

#### 2. UI Layer (`ui/datapad/`)
- **DataPadPopup.kt**: Full-screen popup displaying live flight information
- **LocalDataPadManager.kt**: CompositionLocal for accessing DataPadManager in Compose UI

#### 3. Integration
- **MainActivity.kt**: Initializes DataPadManager and provides it via CompositionLocal
- **FlightMiniStatusBar.kt**: New flight icon button to open DataPad popup

## Features

### Connection Status
- Live connection indicator (green = receiving data, gray = waiting)
- Time since last update display (e.g., "5s ago", "2m ago")
- Auto-disconnect after 5 seconds of no data

### Flight Data Display
Organized in collapsible sections:

1. **Aircraft & Pilot**
   - Aircraft type
   - Pilot name
   - Coalition
   - Group

2. **Flight Parameters**
   - Altitude (meters)
   - Heading (degrees)
   - Pitch (degrees)
   - Bank (degrees)
   - **Angle of Attack (AoA)** — degrees (if available from draw arguments or aircraft module)
   - **G-Load** — X/Y/Z axes (G)
   - Ground Speed / Indicated Airspeed / True Airspeed
   - Vertical Speed (VSI)
   - Mach number
   - Flight Controls & Trim — elevator, aileron, rudder positions and trim values (if exposed)


3. **Position**
   - Latitude/Longitude
   - DCS world coordinates (X, Y, Z)

4. **Navigation & Waypoints**
   - Current waypoint name
   - Distance to waypoint
   - Bearing to waypoint
   - ETA (time/seconds)
   - Flight plan: current WP index / total waypoints
   - Route name

5. **Engine & Performance**
   - Engine RPM / N1, EGT, Throttle position, Afterburner state (where available)
   - Fuel flow (if exposed) and derived endurance

6. **Aircraft Mass**
   - Total mass, empty weight and payload mass (if exposed by module)

7. **Flight Controls / Trim**
   - Elevator/Aileron/Rudder positions and trims

8. **Gear & Configuration**
   - Gear positions (nose/left/right), Weight-on-Wheels (WoW), Flaps %, Speedbrake %, Hook state

9. **Lights & Canopy**
   - Landing/Taxi/Navigation/Strobe/Formation lights and canopy status

10. **Mission Time**
   - Mission clock / model time (seconds)

11. **Nearby Units / Ground Objects**
   - Nearby unit list (type, name, coalition, position, distance) for situational awareness

5. **Fuel**
   - Total fuel capacity
   - Remaining fuel (internal/external)
   - Fuel flow rate
   - Endurance (minutes remaining)

6. **Weapons & Stores**
   - Master Arm status (SAFE/ARM)
   - Selected weapon
   - Weapon stations (station #, type, count)
   - Total munitions count

7. **Threats & EW**
   - RWR contacts (ID, type, bearing, priority, locked)
   - Total threats detected
   - Radar mode, range, lock status, track count
   - Countermeasures: Chaff/Flare counts, dispenser mode

8. **Avionics & Systems**
   - Autopilot enabled/mode/Flight Director
   - Transponder code & mode
   - Radio frequencies (COM1/COM2/Guard/Active)
   - Master Caution/Warning
   - System faults & alerts

9. **Environment**
   - Wind direction & speed
   - Temperature
   - Pressure
   - Visibility
   - Cloud layers

10. **Systems Status**
    - Radar Active
    - Jamming
    - IR Jamming
    - AI On
    - Human control

11. **Additional Info**
    - Timestamp
    - Unit ID
    - Streamer version
    - Data age (latency)
    - Update rate (Hz)

## Usage

### 1. Start DCS Streamer
Ensure the DCS export script is running in dcs /scripts/ folder and generating `player_aircraft_parsed.jsonl`

### 2. Forward UDP Data
Run the Python script to forward data to the app:
```bash
python forward_parsed_udp.py --host <ANDROID_DEVICE_IP> --port 5010
```

### 3. Open DataPad
- Tap the flight icon (✈️) button in the top status bar
- View live flight data as it streams in
- Connection status shows if data is being received

## Network Configuration

### Port
- Default UDP port: **5010**
- Configurable in `DataPadManager.UDP_PORT`

### Firewall
Ensure your firewall allows UDP traffic on port 5010:
```powershell
# Windows
New-NetFirewallRule -DisplayName "DCS DataPad" -Direction Inbound -Protocol UDP -LocalPort 5010 -Action Allow
```

### Android Network Permissions
- `INTERNET` permission is required in AndroidManifest.xml (already added)

## Security
- AES-GCM encryption for UDP telemetry is **enabled by default** in the Python forwarder (`forward_parsed_udp.py`).
- To temporarily disable encryption for debugging use `--no-encrypt` (not recommended in production).
- The Pre-Shared Key (32 bytes) **must match** on both Python and Android sides. See `docs/technical/AES_GCM_ENCRYPTION.md` for generation and configuration instructions.- The DataPad UI exposes a **Settings** dialog (⚙️) where you can change the UDP port, bind IP, and Pre-Shared Key. The dialog includes a **Reset Key** action which restores the default key — **you should change the key after resetting (do not use default in production)**.
## Data Format
Expected JSON format (one object per UDP datagram):

### Basic Example (Minimal)
```json
{
  "aircraft": "FA-18C_hornet",
  "unitName": "[RGS] FATBEE | FTBE",
  "coalition": "Enemies",
  "alt": 46.83,
  "heading": 4.43,
  "pitch": -0.007,
  "bank": -0.0002,
  "lat": 42.17,
  "long": 42.46,
  "pos": {"x": -285764.44, "y": 46.83, "z": 682926.49},
  "radarActive": true,
  "isHuman": true,
  "timestamp": "2025-12-15T19:52:22Z"
}
```

### Extended Example (Full Tactical Kneepad Data)
```json
{
  "aircraft": "FA-18C_hornet",
  "unitName": "[RGS] FATBEE | FTBE",
  "coalition": "Blue",
  "country": 2,
  "group": "Hornet Flight",
  "alt": 5486.3,
  "heading": 245.8,
  "pitch": 2.1,
  "bank": -5.3,
  "lat": 42.17,
  "long": 42.46,
  "pos": {"x": -285764.44, "y": 5486.3, "z": 682926.49},
  "groundSpeed": 420.5,
  "indicatedAirspeed": 385.2,
  "trueAirspeed": 425.8,
  "verticalSpeed": 12.3,
  "mach": 0.64,
  "fuel": {
    "total": 4800,
    "remaining": 3250,
    "internal": 2100,
    "external": 1150,
    "endurance": 45.2,
    "fuelFlow": 1.2
  },
  "waypoint": {
    "current": "WP03-TARGET",
    "distance": 38500,
    "bearing": 248.5,
    "eta": "2025-12-15T20:15:30Z",
    "etaSeconds": 342
  },
  "flightPlan": {
    "currentIndex": 2,
    "totalWaypoints": 5,
    "route": "BASE-IP-TGT-EGRESS-RTB"
  },
  "weapons": {
    "masterArm": true,
    "selected": "AIM-120C",
    "stations": [
      {"station": 2, "type": "AIM-120C", "count": 2},
      {"station": 8, "type": "AIM-120C", "count": 2},
      {"station": 3, "type": "AIM-9X", "count": 1},
      {"station": 7, "type": "AIM-9X", "count": 1}
    ],
    "totalCount": 6
  },
  "rwr": {
    "contacts": [
      {"id": "SA-6", "type": "SAM", "bearing": 315.0, "priority": 3, "locked": true},
      {"id": "MIG-29", "type": "AI", "bearing": 180.5, "priority": 2, "locked": false}
    ],
    "threatsDetected": 2
  },
  "angleOfAttack": 3.5,
  "gLoad": { "x": 1.0, "y": 0.0, "z": 1.0 },
  "engines": {
    "rpm": { "left": 98.5, "right": 98.5 },
    "egt": { "left": 560.0, "right": 558.0 },
    "throttle": 0.75,
    "afterburner": false
  },
  "aircraftMass": { "total": 17200, "empty": 12000, "payload": 4200 },
  "flightControls": { "pitch": 0.01, "roll": -0.02, "yaw": 0.0, "trimPitch": 0.0, "trimRoll": 0.0, "trimYaw": 0.0 },
  "mechanical": { "gear": { "nose": 1.0, "left": 1.0, "right": 1.0 }, "flaps": 0.25, "speedbrake": 0.0, "canopy": 0.0, "hook": 0.0 },
  "lights": { "landing": 0.0, "taxi": 0.0, "navigation": 1.0, "strobe": 0.0, "formation": 0.0 },
  "systems": { "electrical": "OK", "hydraulic": "OK", "apuOn": false, "generatorOn": true },
  "missionTime": 4523,
  "radar": {
    "mode": "RWS",
    "range": 80.0,
    "locked": false,
    "trackCount": 3,
    "tracks": [ { "id": 1, "range": 12000, "azimuth": 45.0, "elevation": 1.5, "locked": false } ]
  },
  "nearbyUnits": [ { "id": "U123", "name": "Tanker", "type": "KC-135", "coalition": "Blue", "distance": 3200 } ],
  "countermeasures": {
    "chaffCount": 60,
    "flareCount": 30,
    "dispenserMode": "AUTO"
  },
  "autopilot": {
    "enabled": true,
    "mode": "ALT HOLD",
    "flightDirector": true
  },
  "transponder": {
    "code": "2341",
    "mode": "ALT",
    "ident": false
  },
  "radios": {
    "com1": 251.5,
    "com2": 127.5,
    "guard": true,
    "activeFreq": 251.5
  },
  "warnings": {
    "masterCaution": false,
    "masterWarning": false,
    "faults": [],
    "alerts": ["LOW FUEL"]
  },
  "environment": {
    "windDirection": 270.0,
    "windSpeed": 15.5,
    "temperature": -12.3,
    "pressure": 1013.25,
    "visibility": 10000.0,
    "clouds": "SCT040"
  },
  "radarActive": true,
  "jamming": false,
  "irJamming": false,
  "isHuman": true,
  "timestamp": "2025-12-15T19:52:22Z",
  "unitID": "16777472",
  "lua_version": "1.2",
  "streamer_version": "2.0",
  "dataAge": 0.05,
  "updateRate": 20.0
}
```

## Complete Field Reference
A comprehensive list of fields the DataPad will receive from the UDP JSON stream (name · type · notes). Fields mirror the `FlightData.kt` data model.

### Basic Identity
- `aircraft` · string — aircraft type identifier
- `unitName` · string — pilot / unit display name
- `coalition` · string — coalition name (Blue/Enemies/etc.)
- `country` · int — country code
- `group` · string — group name

### Flight Parameters
- `alt` · number — altitude (meters)
- `heading` · number — heading (radians in-stream; converted to degrees in UI)
- `pitch` · number — pitch (radians)
- `bank` · number — bank/roll (radians)
- `angleOfAttack` · number? — AoA (degrees), when available from draw arguments or module
- `gLoad` · object? { `x`, `y`, `z` } — G-force in Gs on X/Y/Z axes
- `lat` · number — latitude (decimal degrees)
- `long` · number — longitude (decimal degrees)
- `pos` · object { `x`, `y`, `z` } — DCS world coordinates

### Engine Data
- `engines` · object? — engine telemetry
  - `rpm` · object? { `left`, `right` } — percent or RPM
  - `egt` · object? { `left`, `right` } — exhaust gas temperature (°C)
  - `throttle` · number? — throttle position (0.0–1.0)
  - `afterburner` · bool? — afterburner engaged

### Aircraft Mass
- `aircraftMass` · object? — { `total`, `empty`, `payload` } (kg)

### Flight Controls
- `flightControls` · object? — { `pitch`, `roll`, `yaw`, `trimPitch`, `trimRoll`, `trimYaw` }

### Mechanical / Configuration
- `mechanical` · object? — { `gear`, `flaps`, `speedbrake`, `canopy`, `hook`, `wheelBrake`, `noseGearSteeringEnabled` }
  - `gear` · object? { `nose`, `left`, `right` } — 0.0=up, 1.0=down
  - `weightOnWheels` · bool? — true if on ground

### Lights & Canopy
- `lights` · object? — { `landing`, `taxi`, `navigation`, `strobe`, `formation` }

### Systems
- `systems` · object? — { `electrical`, `hydraulic`, `apuOn`, `generatorOn` }

### Radar Tracks
- `radar.tracks` · list? — each track { `id`, `range`, `azimuth`, `elevation`, `locked` }

### Nearby Units
- `nearbyUnits` · list? — nearby objects with `{ id, name, type, coalition, distance }`

### Mission Time
- `missionTime` · number? — mission model time in seconds


### Speed & Vertical
- `groundSpeed` · number? — ground speed (units variable)
- `indicatedAirspeed` · number? — IAS
- `trueAirspeed` · number? — TAS
- `verticalSpeed` · number? — vertical speed (m/s)
- `mach` · number? — Mach number

### Fuel
- `fuel` · object? — fuel details
  - `total` · number — total capacity
  - `remaining` · number — remaining fuel
  - `internal` · number? — internal fuel
  - `external` · number? — external fuel
  - `endurance` · number? — minutes remaining
  - `fuelFlow` · number? — consumption rate

### Navigation & Waypoints
- `waypoint` · object? — current waypoint info
  - `current` · string? — waypoint name
  - `distance` · number? — distance to WP
  - `bearing` · number? — bearing to WP (degrees)
  - `eta` · string? — ISO timestamp or textual ETA
  - `etaSeconds` · number? — ETA in seconds
- `flightPlan` · object? — flight plan info
  - `currentIndex` · int? — current WP index
  - `totalWaypoints` · int? — total WPs
  - `route` · string? — route name

### Weapons & Stores
- `weapons` · object? — weapons summary
  - `masterArm` · bool — master arm state
  - `selected` · string? — selected weapon
  - `stations` · list? — station entries { `station`, `type`, `count` }
  - `totalCount` · int? — total munitions

### EW / RWR / Threats
- `rwr` · object? — RWR/contacts
  - `contacts` · list? — each contact: { `id`, `type`, `bearing`, `priority`, `locked` }
  - `threatsDetected` · int — total detected threats
- `radar` · object? — radar mode/status { `mode`, `range`, `locked`, `trackCount` }
- `countermeasures` · object? — { `chaffCount`, `flareCount`, `dispenserMode` }

### Avionics & Systems
- `autopilot` · object? — { `enabled`, `mode`, `flightDirector` }
- `transponder` · object? — { `code`, `mode`, `ident` }
- `radios` · object? — { `com1`, `com2`, `guard`, `activeFreq` }
- `warnings` · object? — { `masterCaution`, `masterWarning`, `faults`, `alerts` }

### Environment
- `environment` · object? — { `windDirection`, `windSpeed`, `temperature`, `pressure`, `visibility`, `clouds` }

### Existing Status Flags
- `isHuman` · bool — player-controlled
- `born` · bool — unit born/active flag
- `aiOn` · bool — AI control
- `radarActive` · bool — radar active
- `jamming` · bool — EW jamming
- `irJamming` · bool — IR jamming
- `invisible` · bool — map/world visibility flag

### Metadata
- `timestamp` · string — ISO timestamp for the sample
- `unitID` · string — unique unit identifier
- `lua_version` · string — exporter Lua version
- `streamer_version` · string — streamer/exporter version
- `dataAge` · number? — latency / age (seconds)
- `updateRate` · number? — reported update rate (Hz)

> Notes: Optional fields are suffixed with `?` above and may be absent depending on the exporter script or aircraft. Units can vary depending on DCS/exporter settings (e.g., wind speed m/s vs knots); the UI applies reasonable formatting/conversions.

## Technical Details

### Threading
- UDP reception runs on IO dispatcher (background thread)
- State updates trigger UI recomposition automatically via StateFlow

### Error Handling
- Socket timeout: 1 second (prevents blocking)
- Invalid JSON is logged but doesn't crash the app
- Connection auto-detects drops after 5 seconds of no data

### Resource Management
- DataPadManager starts on app launch
- Cleans up UDP socket on app disposal
- Minimal battery impact when not receiving data

## Future Enhancements
- [ ] Configurable UDP port in settings
- [ ] Data logging/recording
- [ ] Custom alert thresholds (altitude, speed, etc.)
- [ ] Multiple aircraft tracking
- [ ] Map integration with position overlay
- [ ] Live animated aircraft visualization showing orientation (pitch/roll/heading) and movement
- [ ] Dedicated DataPad UI/UX redesign and controls
- ✅ Implemented: AES-GCM encrypted UDP telemetry (see `docs/technical/AES_GCM_ENCRYPTION.md`) 
