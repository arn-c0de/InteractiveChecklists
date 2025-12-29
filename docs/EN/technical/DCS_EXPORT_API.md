# DCS Export API Reference

## Overview
This document lists the DCS World Export Lua API functions used in the Export.lua script to collect telemetry data for the tactical kneepad application.

## Available DCS Export Functions

### Basic Aircraft Data
- **LoGetSelfData()** - Returns comprehensive aircraft state
  - Name, Type, UnitName, ID
  - LatLongAlt (Lat, Long, Alt)
  - Heading, Pitch, Bank
  - Coalition, Country, GroupName
  - Position (x, y, z in DCS coordinates)
  - Player flag (human control)

### Speed & Performance
- **LoGetIndicatedAirSpeed()** - IAS in m/s
- **LoGetTrueAirSpeed()** - TAS in m/s
- **LoGetGroundSpeed()** - Ground speed in m/s
- **LoGetVerticalVelocity()** - Vertical speed in m/s
- **LoGetMachNumber()** - Mach number
- **LoGetAccelerationUnits()** - G-force data

### Fuel
- **LoGetFuelWeight()** - Total fuel weight in kg
- **LoGetEngineInfo()** - Engine parameters (may include fuel flow)

### Navigation & Waypoints
- **LoGetRoute()** - Flight plan data
  - CurrentWaypoint (Name, number, distance, heading, eta)
  - GoToWaypointCount - total waypoints
  - Name - route name

### Weapons & Stores
- **LoGetPayloadInfo()** - Weapon loadout
  - Stations[] - array of weapon stations
    - weapon (displayName, level1)
    - count - munition count per station
  - CurrentWeapon - selected weapon
- **LoGetMasterArmState()** - Master arm status (boolean)

### Radar & Sensors
- **LoGetRadarInfo()** - Radar status (aircraft-specific)
  - mode, range, lock, trackCount
- **LoGetTWSInfo()** - TWS tracks (limited)
- **LoGetSightingSystemInfo()** - Targeting pod data

### Countermeasures
- **LoGetSnares()** - Chaff/Flare counts
  - chaff, flare counts
  - mode (AUTO/MANUAL)

### Avionics
- **LoGetControlPanel_Autopilot()** - Autopilot state
  - on, mode, fd (flight director)
- **LoGetTransponderInfo()** - Transponder (aircraft-specific)
  - code, mode, ident
- **LoGetRadioFrequencies()** - Radio frequencies
  - COM1, COM2, Guard, active

### System Status
- **LoGetMCPState()** - Master Caution Panel
  - MasterCaution, MasterWarning
  - faults[], alerts[]
- **LoGetEngineInfo()** - Engine parameters
  - RPM, Temperature, Fuel Flow

### Environment
- **LoGetVectorWindVelocity()** - Wind vector (x, z)
- **LoGetTemperature()** - Outside air temperature
- **LoGetPressure()** - Barometric pressure
- **LoGetAltitudeAboveSeaLevel()** - Altitude MSL
- **LoGetAltitudeAboveGroundLevel()** - Altitude AGL

### Time
- **LoGetModelTime()** - Simulation time in seconds (high precision)
- **os.date()** - System time for timestamps

## Data Structure Mapping

### FlightData.kt ← DCS API
```
aircraft          ← LoGetSelfData().Name
unitName          ← LoGetSelfData().UnitName
lat/long/alt      ← LoGetSelfData().LatLongAlt
heading/pitch/bank← LoGetSelfData().Heading/Pitch/Bank
pos (x,y,z)       ← LoGetSelfData().Position

groundSpeed       ← LoGetGroundSpeed()
indicatedAirspeed ← LoGetIndicatedAirSpeed()
trueAirspeed      ← LoGetTrueAirSpeed()
verticalSpeed     ← LoGetVerticalVelocity()
mach              ← LoGetMachNumber()

fuel              ← LoGetFuelWeight() + LoGetEngineInfo()
waypoint          ← LoGetRoute().CurrentWaypoint
flightPlan        ← LoGetRoute()
weapons           ← LoGetPayloadInfo() + LoGetMasterArmState()
rwr               ← (limited/placeholder - DCS doesn't expose RWR directly)
radar             ← LoGetRadarInfo()
countermeasures   ← LoGetSnares()
autopilot         ← LoGetControlPanel_Autopilot()
transponder       ← LoGetTransponderInfo()
radios            ← LoGetRadioFrequencies()
warnings          ← LoGetMCPState()
environment       ← LoGetVectorWindVelocity() + LoGetTemperature() + LoGetPressure()
```

## Limitations

### DCS API Restrictions
1. **RWR Data** - Not directly exposed by DCS Export API
   - Workaround: Use clickable cockpit scripting or DCS-BIOS for specific aircraft
2. **Weapon Details** - Some aircraft don't expose full payload info
3. **System Faults** - Limited to what MCP exposes (aircraft-specific)
4. **External Fuel** - Not separated from internal fuel by default
5. **Fuel Flow** - May not be available on all aircraft

### Aircraft-Specific Functions
Some functions only work with certain aircraft modules:
- Transponder functions (F/A-18, F-16, A-10C II)
- Radar info (fighters with radar)
- Autopilot state (advanced modules)

## Update Rate
- **UPDATE_INTERVAL = 0.2** seconds (5 Hz)
- Adjustable: lower = higher CPU usage, higher = lower latency
- Recommended: 0.1 - 0.5 seconds for tactical use

## JSON Output
All telemetry is written to:
```
~\Saved Games\DCS\Scripts\player_aircraft_parsed.jsonl
```

Each line is a complete JSON object matching the FlightData.kt structure.

## Testing Commands
To verify DCS API functions are working:
1. Enable DEBUG_DUMP_TABLES in Export.lua
2. Check `player_aircraft_debug.log` for raw API dumps
3. Monitor `player_aircraft_parsed.jsonl` for JSON output

## References
- [DCS World Export Lua Documentation](https://wiki.hoggitworld.com/view/DCS_Export_Script)
- [DCS-BIOS](https://github.com/DCS-Skunkworks/dcs-bios) - Advanced cockpit data extraction
- [Tacview Export](https://www.tacview.net/documentation/dcs/en/) - Similar export framework
