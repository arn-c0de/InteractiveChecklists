# DataPad Feature

## Overview
DataPad is a live flight data display panel that receives real-time aircraft telemetry via UDP from DCS World through the `forward_parsed_udp.py` script.

**Phase 1 (experimental):** This implementation represents Phase 1 of DataPad. Future phases will expand telemetry coverage and add visual and security improvements, including live animated aircraft visualizations based on flight attitude, a dedicated DataPad UI redesign, and a planned migration to encrypted transport (TCP or UDP with AES encryption).

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

3. **Position**
   - Latitude/Longitude
   - DCS world coordinates (X, Y, Z)

4. **Systems Status**
   - Radar Active
   - Jamming
   - IR Jamming
   - AI On
   - Human control

5. **Additional Info**
   - Timestamp
   - Unit ID
   - Streamer version

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

## Data Format
Expected JSON format (one object per UDP datagram):
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
- [ ] Transition to encrypted transport (TCP or UDP) with AES-based encryption for secure telemetry streams
