# Running Sender and Receiver on Same PC

When running both `forward_parsed_udp.py` (sender) and the DataPad GUI (receiver) on the same PC, they cannot both bind to the same port. This guide explains how to configure them to work together.

## The Problem

- **Sender** needs to listen on a port to receive ECDH handshake requests
- **Receiver** needs to listen on a port to receive encrypted data
- Windows doesn't allow two programs to bind to the same port

## The Solution

Use different ports for handshakes and data transmission:

1. **Sender** listens for handshakes on port `5011` and sends data to `127.0.0.1:5010`
2. **Receiver** listens for data on port `5010` and sends handshake to `127.0.0.1:5011`

## Quick Start (GUI Method - Recommended)

### Step 1: Configure the DataPad GUI

Edit `datapad_config.json`:
```json
{
  "senderIp": "127.0.0.1",
  "senderPort": 5011,
  "useEcdh": true,
  "deviceName": "Python DataPad",
  "port": 5010,
  "bindIp": "0.0.0.0"
}
```

Or use the GUI Settings dialog (Settings button in the app).

### Step 2: Start the Sender (DCS Scripts folder)

```powershell
cd "C:\Users\arn\Saved Games\DCS\Scripts"
python forward_parsed_udp.py --repeat-last --interval 3 --host 127.0.0.1 --port 5010 --handshake-port 5011 --verbose --use-handshake --authorized-devices authorized_devices.json
```

**Key parameters:**
- `--port 5010` - Send data to this port (where receiver listens)
- `--handshake-port 5011` - Listen for handshakes on this port
- `--host 127.0.0.1` - Send to localhost (same PC)

### Step 3: Start the DataPad GUI

Double-click `start_venv.bat` or run:
```powershell
cd "C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\scripts\MapDatabaseTools"
start_venv.bat
```

The GUI will automatically:
- Read configuration from `datapad_config.json`
- Start the ECDH receiver with correct parameters
- Display live flight data and map

## Command-Line Method (Advanced)

### Terminal 1: Start the Sender

```powershell
cd "C:\Users\arn\Saved Games\DCS\Scripts"
python forward_parsed_udp.py --repeat-last --interval 3 --host 127.0.0.1 --port 5010 --handshake-port 5011 --verbose --use-handshake --authorized-devices authorized_devices.json
```

### Terminal 2: Start the Receiver (CLI)

```powershell
cd "C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\scripts\MapDatabaseTools"
python run_datapad_ecdh.py --use-ecdh --sender-ip 127.0.0.1 --sender-port 5011 --port 5010 --allow-bind-all
```

**Key parameters:**
- `--port 5010` - Listen for data on this port
- `--sender-port 5011` - Send handshake to sender's handshake port
- `--sender-ip 127.0.0.1` - Sender is on localhost

## Network Diagram

```
┌─────────────────────────────────────┐
│       Same PC (127.0.0.1)           │
│                                     │
│  ┌──────────────────────┐          │
│  │  forward_parsed_udp  │          │
│  │  (Sender)            │          │
│  ├──────────────────────┤          │
│  │ Listen: 0.0.0.0:5011 │◄─────┐   │
│  │ (handshakes)         │      │   │
│  └──────────────────────┘      │   │
│           │                    │   │
│           │ Send data          │   │
│           ▼                    │   │
│  ┌──────────────────────┐     │   │
│  │  run_datapad_ecdh    │     │   │
│  │  (Receiver)          │     │   │
│  ├──────────────────────┤     │   │
│  │ Listen: 0.0.0.0:5010 │─────┘   │
│  │ (data)               │ Handshake│
│  └──────────────────────┘          │
└─────────────────────────────────────┘
```

## Android App Connection (No Changes Needed)

When connecting the Android app, it still uses the same configuration:
- Sender uses `--port 5010` (Android listens on 5010)
- Sender uses `--handshake-port 5010` (or omit, defaults to same as --port)
- Sender uses `--host <android-ip>` (Android's IP address)

The Android app doesn't need any changes - it binds to port 5010 for both handshakes and data like before.

## Troubleshooting

### "Address already in use" error
- Make sure no other program is using ports 5010 or 5011
- Check with: `netstat -ano | findstr :5010` and `netstat -ano | findstr :5011`

### "Ein Vorgang bezog sich auf ein Objekt, das kein Socket ist"
- This was the original error when both tried to use port 5010
- Verify you're using different ports with `--handshake-port 5011`

### Handshake fails
- Check that receiver's `--sender-port` matches sender's `--handshake-port`
- Verify both are using `127.0.0.1` or `192.168.178.100` consistently
- Ensure device is authorized in `authorized_devices.json`
