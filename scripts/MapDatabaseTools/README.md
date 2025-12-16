# DCS DataPad - Python UDP Receiver & Visualizer

A Python implementation of the DataPad feature that receives encrypted UDP flight data from DCS (Digital Combat Simulator) and displays it in a live GUI. This tool mirrors the functionality of the Kotlin Android app.

## Features

- **🔐 AES-GCM Encrypted UDP Reception**: Securely receives flight data using the same encryption as the Kotlin app
- **📊 Live Data Visualization**: Real-time display of comprehensive flight parameters (left panel)
- **🗺️ Embedded Map (OSM)**: Right-hand panel (2/3 width) shows a live OpenStreetMap with the aircraft position
- **🎨 Modern GUI**: Clean PySide6 interface with dark theme
- **⚙️ Configurable Settings**: Adjust UDP port, bind IP, and encryption key
- **📡 Connection Monitoring**: Visual feedback on connection status and data freshness
- **✅ Commercial-Friendly**: Uses LGPL libraries (PySide6) — compatible with CC BY-NC-SA 4.0 and commercial licensing

## Displayed Data

The GUI shows all available flight data including:

- **Aircraft & Pilot Info**: Aircraft type, unit name, coalition, group
- **Flight Parameters**: Altitude, speed (IAS/TAS/GS), heading, pitch, bank, vertical speed, Mach
- **Position**: Latitude, longitude coordinates
- **Fuel**: Total, remaining, internal/external tanks
- **Engine Data**: Throttle, RPM, afterburner status
- **AoA & G-Load**: Angle of attack, current/max G-forces
- **Mechanical**: Gear, flaps, speed brake, hook status
- **Weapons**: Master arm, selected weapon, total count
- **Countermeasures**: Chaff and flare counts
- **RWR/Threats**: Detected threats with bearing information
- **Environment**: Wind direction/speed, temperature, pressure
- **And more**: Mission time, metadata, timestamps

## Installation

### Prerequisites

- Python 3.8 or higher
- pip (Python package installer)

### Install Dependencies

```bash
cd scripts/MapDatabaseTools
pip install -r requirements.txt
```

This will install:
- `cryptography` - For AES-GCM decryption (Apache 2.0)
- `PySide6` - For the GUI (LGPL v3, commercial-friendly)
- `requests` - For downloading map assets (Apache 2.0)

## Configuration

### Default Settings

- **UDP Port**: 5010
- **Bind IP**: 0.0.0.0 (all network interfaces)
- **Pre-Shared Key**: `DCS_DataPad_Secret_Key_32BYTES!!`

### Changing Settings

1. Launch the GUI
2. Click the **⚙ Settings** button
3. Modify:
   - **UDP Port** (1024-65535)
   - **Bind IP** (leave empty for all interfaces, or specify IP like `192.168.1.100`)
   - **Pre-Shared Key** (must be exactly 32 characters for AES-256)
4. Click **Save & Restart**

**Important**: The pre-shared key must match the key configured in your DCS export script that sends the UDP data.

## Usage

### Running with GUI

```bash
python datapad_gui.py
```

This will:
1. Open the DataPad GUI window
2. Start listening for UDP packets on the configured port
3. Display flight data as it arrives
4. Show connection status and time since last update

### Running in Console Mode (No GUI)

For testing or debugging, you can run the receiver without GUI:

```bash
python datapad_receiver.py
```

This will print received data to the console.

## DCS Configuration

To send data from DCS to this tool, you need a Lua export script running in DCS that:

1. Collects flight data from the DCS API
2. Serializes it to JSON
3. Encrypts it using AES-GCM with the matching pre-shared key
4. Sends it via UDP to this tool's IP address and port

See the `scripts/DCS-SCRIPTS-FOLDER-Experimental/` directory for example DCS export scripts.

### Network Setup

**On the same machine:**
- Use `127.0.0.1` (localhost) in the DCS script
- Bind IP: `127.0.0.1` or `0.0.0.0`

**On different machines (network):**
- Use the Python tool machine's IP address in the DCS script
- Bind IP: Use the specific network interface IP or `0.0.0.0` for all
- Ensure firewall allows UDP traffic on the configured port

**Find your IP address:**
- Windows: `ipconfig`
- Linux/Mac: `ifconfig` or `ip addr`

The GUI displays your local IP address in the connection status.

## Encryption Details

The tool uses **AES-256-GCM** encryption to decrypt incoming UDP packets:

- **Algorithm**: AES-GCM (Galois/Counter Mode)
- **Key Size**: 256 bits (32 bytes)
- **Nonce Size**: 12 bytes (96 bits)
- **Tag Size**: 16 bytes (128 bits)

**Packet Format:**
```
[12-byte nonce][encrypted data][16-byte authentication tag]
```

The decryption process:
1. Extract the 12-byte nonce from the start of the packet
2. Extract the ciphertext + tag (remaining bytes)
3. Use AESGCM to decrypt with the pre-shared key
4. Parse the resulting JSON data

This matches the encryption used in the Kotlin `DataPadManager.kt`.

## Troubleshooting

### No Data Received

1. **Check network connectivity**: Ensure DCS and this tool can communicate
2. **Verify IP address**: Confirm the DCS script is sending to the correct IP
3. **Check port**: Ensure the port matches in both DCS script and this tool
4. **Firewall**: Allow UDP traffic on the configured port
5. **Pre-shared key**: Must match exactly (32 characters) between sender and receiver

### Decryption Failed

- **Check pre-shared key**: Must be identical in DCS script and this tool
- **Key length**: Must be exactly 32 characters
- **Packet format**: Ensure DCS script uses correct nonce + ciphertext + tag format

### GUI Not Starting

- **Check PyQt6 installation**: Run `pip install --upgrade PyQt6`
- **Python version**: Ensure Python 3.8+
- **Display issues**: On Linux, ensure X11 or Wayland display is available

### Connection Shows "Disconnected"

- After 5 seconds without receiving data, status changes to disconnected
- Check that DCS is running and the export script is active
- Verify network path between DCS and this tool

## File Structure

```
MapDatabaseTools/
├── datapad_receiver.py    # Core UDP receiver with AES-GCM decryption
├── datapad_gui.py         # PyQt6 GUI for data visualization
├── requirements.txt       # Python dependencies
└── README.md             # This file
```

## Development

### Testing Without DCS

You can test the tool by sending encrypted UDP packets manually:

```python
import socket
import json
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
import os

# Prepare test data
data = {
    "aircraft": "F-16C_50",
    "alt": 5000.0,
    "heading": 270.0,
    "lat": 42.123456,
    "long": -83.654321,
    # ... more fields
}
json_data = json.dumps(data).encode('utf-8')

# Encrypt
key = b"DCS_DataPad_Secret_Key_32BYTES!!"
aesgcm = AESGCM(key)
nonce = os.urandom(12)
ciphertext = aesgcm.encrypt(nonce, json_data, None)

# Send
packet = nonce + ciphertext
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.sendto(packet, ("127.0.0.1", 5010))
sock.close()
```

### Extending the Tool

To add new features:

1. **Add fields to `FlightData`** in `datapad_receiver.py`
2. **Update `from_json()`** to parse new fields
3. **Add display sections** in `display_flight_data()` in `datapad_gui.py`

## Comparison with Kotlin App

| Feature | Kotlin App | Python Tool |
|---------|-----------|-------------|
| Platform | Android | Windows/Linux/Mac |
| UDP Reception | ✅ | ✅ |
| AES-GCM Decryption | ✅ | ✅ |
| Live Data Display | ✅ | ✅ |
| Settings UI | ✅ | ✅ |
| Connection Status | ✅ | ✅ |
| Persistent Settings | ✅ SharedPreferences | ❌ (runtime only) |
| Immersive Mode | ✅ (Android) | N/A |

## License

This tool is part of the **InteractiveChecklists** project and is licensed under **CC BY-NC-SA 4.0** (see root `LICENSE` file).

### Third-Party Dependencies

All Python dependencies are permissively licensed and compatible with both non-commercial and commercial use:

- **PySide6**: LGPL v3 (Qt Company) — allows commercial use via dynamic linking
- **cryptography**: Apache 2.0 / BSD
- **requests**: Apache 2.0
- **Leaflet.js**: BSD-2-Clause
- **Leaflet.RotatedMarker**: MIT

See `THIRD_PARTY_LICENSES.md` in the project root for full details.

**Commercial Use Note**: While this project is licensed under CC BY-NC-SA 4.0 (non-commercial), all underlying libraries are LGPL/Apache/MIT/BSD — so if you obtain commercial permission or create a derivative work, there are no GPL-style restrictions from dependencies.

## Related Files

- Android Implementation: `app/src/main/java/com/example/checklist_interactive/data/datapad/`
- DCS Export Scripts: `scripts/DCS-SCRIPTS-FOLDER-Experimental/`
- Documentation: `docs/features/DATAPAD_FEATURE.md`

## Support

For issues or questions, refer to the main project documentation or create an issue in the project repository.
