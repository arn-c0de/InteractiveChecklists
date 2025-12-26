# Entity Tracking Feature

## Overview

The system now **automatically sends both files** (Aircraft Data + Entity Contacts) based on the toggle status in the Android app.

## Architecture

### DCS (Export.lua)
Writes two separate files in parallel:
- `player_aircraft_parsed.jsonl` — aircraft data (~1KB/update)
- `entity-contacts-parsed.jsonl` — tactical units (~30KB/update)

### Python Forwarder (forward_parsed_udp.py)
**A single command** is enough:
```bash
python forward_parsed_udp.py --interval 50 --host 192.168.178.132 --port 5010 --verbose --authorized-devices authorized_devices.json --bind-ip 192.168.178.100
```

**What happens automatically:**
1. The script detects both files automatically
2. During the handshake it receives the `entityTrackingEnabled` status from the app
3. **If toggle OFF:** only aircraft data is sent
4. **If toggle ON:** both files are sent in parallel

### Android App
- Toggle switch in the Tactical Units list
- Status is sent to the forwarder during the handshake
- Can be toggled on/off at any time (the next handshake applies the new status)

## Data Flow

```
DCS Export.lua
    ├─→ player_aircraft_parsed.jsonl (always)
    └─→ entity-contacts-parsed.jsonl (always)

Python Forwarder (reads both files)
    ├─→ Aircraft Data → ALL connected devices
    └─→ Entity Contacts → ONLY devices with entityTrackingEnabled=true

Android App
    ├─→ Receives Aircraft Data (always)
    └─→ Receives Entity Contacts (only when toggle is active)
```

## Benefits

✅ **One Python command** — no separate processes
✅ **Dynamically controllable** — toggle in the app is enough
✅ **Efficient** — entity data is only sent when needed
✅ **No bandwidth waste** — forwarder checks sessions before sending
✅ **Automatic detection** — forwarder finds `entity-contacts` file automatically

## Technical Details

### Handshake extension
```json
{
  "type": "ClientHello",
  "deviceId": "...",
  "deviceName": "...",
  "publicKey": "...",
  "entityTrackingEnabled": true  // NEW
}
```

### Session Management
Each session stores:
- `session_id` — unique session ID
- `device_id` — device ID
- `session_key` — encryption key
- **`entity_tracking_enabled`** — client's toggle status

### Forwarder send logic
```python
# Aircraft data: sent to ALL sessions
for device_id, session_id in device_sessions.items():
    send(aircraft_data, device_id)

# Entity contacts: ONLY if session.entity_tracking_enabled==True
for device_id, session_id in device_sessions.items():
    session = sessions[session_id]
    if session.entity_tracking_enabled:
        send(entity_data, device_id)
```

## Logging

The forwarder logs messages like:
```
🔑 Session created: abc123... (key derived from ECDH, with entity tracking)
📡 Entity contacts file detected: ...\entity-contacts-parsed.jsonl
📡 Sent entity data to abc123...
📡 Entity contacts sent to 1 device(s)
```

Or when toggle is off:
```
🔑 Session created: xyz789... (key derived from ECDH, aircraft data only)
```

## Migration

**Existing commands continue to work!**
- If `entityTrackingEnabled` is missing from the handshake → default: `false`
- Old apps without the toggle → do not receive entity data
- New apps with toggle off → do not receive entity data
- New apps with toggle on → receive entity data

No breaking changes! 🎉
