# QR-Code Based Device Registration

## Overview

This feature enables automatic registration of Android devices via QR code scan, without manual copying of keys or editing config files.

## Workflow

### 1. Server-Side (DCS/Python)

**Generate registration token:**
```bash
cd scripts/DCS-SCRIPTS-FOLDER-Experimental
python registration_token.py generate --server-ip 192.168.1.100 --port 5010 --validity 10
```

**Output:**
```
======================================================================
🔐 DATAPAD REGISTRATION TOKEN GENERATED
======================================================================

📱 Server: 192.168.1.100:5010
⏰ Expires: 2026-01-08 15:30:00
🔑 Token ID: <generated-token-id>

📋 QR Code Payload (156 bytes):
{"type":"datapad_registration","version":"1.0","token":"...","server":"192.168.1.100","port":5010,"expires":1736349000,"permissions":["receive","send_commands"]}

📱 QR CODE:
[ASCII QR Code will be displayed]

✅ Token ready! Scan with DataPad app to register device.
======================================================================
```

**Save QR code image (optional):**
```bash
python registration_token.py generate --server-ip 192.168.1.100 --port 5010 --output-qr registration_qr.png
```

### 2. Client-Side (Android App)

**In DataPad settings:**

1. Tap "Scan QR code" button
2. Camera opens automatically
3. Scan QR code from server
4. App shows confirmation dialog:
   - Server: 192.168.1.100:5010
   - Token valid until: 15:30:00
   - Confirm "Register"
5. App sends `DeviceRegistration` message
6. Server adds device to `authorized_devices.json`
7. Success message is displayed
8. Settings are automatically applied
9. Handshake is automatically initiated

## Architecture

### Python Components

**registration_token.py** - Token Management
- `RegistrationToken` - Token data class
- `RegistrationTokenManager` - Token lifecycle (generate, verify, cleanup)
- CLI interface for token management

**crypto_handshake.py** - Extended with:
- `handle_device_registration()` - Processes DeviceRegistration messages
- Token manager integration
- Automatic addition to authorized_devices.json

**forward_parsed_udp.py** - Extended with:
- DeviceRegistration message routing (built-in)
- Interactive registration mode at startup (press 'B' to generate QR token and wait for registration)
- `--skip-qr-prompt` flag to skip the interactive prompt

> Note: The previous "manual patch" has been applied. `forward_parsed_udp.py` now handles `DeviceRegistration` messages and can listen for registration at startup.

### Kotlin Components

**QrRegistrationManager.kt** - New class
- `RegistrationTokenPayload` - Token data from QR code
- `DeviceRegistration` - Request message
- `RegistrationResult` - Result sealed class
- `parseQrCode()` - Parse and validate QR code
- `registerDevice()` - Perform registration
- `completeRegistration()` - Complete flow (parse → register → apply settings)

## Security Features

### Token Security
- **Single-use**: Token is marked as consumed after use
- **Time-limited**: Default 10 minutes validity
- **Cryptographically secure**: 32-byte SecureRandom token ID
- **Server-bound**: Token is bound to server IP

### Registration Validation
- **Public Key Validation**: EC-Curve-Check (SECP256R1)
- **Device-ID Format Check**: Alphanumeric, 8-128 characters
- **Duplicate Check**: Prevents multiple registration of same device ID
- **IP Logging**: Registration IP is stored in authorized_devices.json
- **Audit Logging**: All registrations are logged in security_audit.jsonl

### Rate-Limiting
- Registration attempts are subject to existing rate limits
- Prevents token brute-force attacks

## Message Format

### DeviceRegistration (Android → Server)
```json
{
  "type": "DeviceRegistration",
  "registrationToken": "<token-from-qr>",
  "deviceId": "<device-id>",
  "deviceName": "My Tablet",
  "publicKey": "<base64-ec-public-key>",
  "timestamp": 1736349123456
}
```

### RegistrationSuccess (Server → Android)
```json
{
  "type": "RegistrationSuccess",
  "message": "Device successfully registered",
  "deviceId": "<device-id>",
  "deviceName": "My Tablet",
  "permissions": ["receive", "send_commands"],
  "timestamp": 1736349123456
}
```

### RegistrationError (Server → Android)
```json
{
  "type": "RegistrationError",
  "error": "InvalidToken",
  "message": "Registration token is invalid or expired",
  "timestamp": 1736349123456
}
```

## Token Management CLI

### List tokens
```bash
python registration_token.py list
```

### Verify token
```bash
python registration_token.py verify <token-id>
```

### Remove expired tokens
```bash
python registration_token.py cleanup
```

**Status: Applied**

The `DeviceRegistration` handler has been implemented and integrated into `forward_parsed_udp.py`. The server can optionally start in interactive registration mode (press 'B' at startup) or skip the prompt with `--skip-qr-prompt`.

If you need to reproduce the manual changes, see the commit history for the exact diff.

## Dependencies

### Python
```bash
pip install qrcode[pil]  # For QR code generation (optional, fallback available)
pip install cryptography  # Already present
```

### Android
- QR code scanner library (e.g. ZXing, ML Kit)
- Add to build.gradle.kts:
```kotlin
implementation("com.google.mlkit:barcode-scanning:17.2.0")
```

## UI Integration

The QR scanner UI is implemented inside the app using ZXing and a Compose `QrCodeScannerScreen` composable (full-screen dialog). ML Kit remains an option for future improvements.

Recommended integration:

- Add a "Scan QR code" action in DataPad Settings that opens `QrCodeScannerScreen`.
- The scanner opens full-screen, filters for QR codes only, and automatically returns the scanned payload to the registration flow.

Example flow (already implemented in the project):

1. User taps **Scan QR Code** in settings
2. `QrCodeScannerScreen` opens in a full-screen dialog
3. QR is scanned; app validates `datapad_registration` payload
4. App calls `QrRegistrationManager.completeRegistration(...)`
5. On success, settings are applied and handshake starts

The code lives in:
- `app/src/main/java/.../ui/datapad/QrCodeScannerComposable.kt` (ZXing-based scanner)
- `app/src/main/java/.../data/datapad/QrRegistrationManager.kt`

You can keep ML Kit as an alternative scanner by swapping the composable implementation if desired.

        lifecycleScope.launch {
            val result = qrRegistrationManager.completeRegistration(
                qrData = qrData,
                deviceName = deviceNamePreference.text,
                dataPadManager = dataPadManager
            )

            hideProgressDialog()

            when (result) {
                is RegistrationResult.Success -> {
                    showSuccessDialog(
                        "Device registered successfully!\n\n" +
                        "Device: ${result.deviceName}\n" +
                        "Permissions: ${result.permissions.joinToString()}"
                    )
                    // Trigger handshake
                    dataPadManager.performHandshake()
                }
                is RegistrationResult.Error -> {
                    showErrorDialog("Registration failed: ${result.message}")
                }
                is RegistrationResult.Timeout -> {
                    showErrorDialog("Registration timeout. Please check server connection.")
                }
            }
        }
    }
}
```

## Testing

### Server Test
```bash
# Terminal 1: Start server
python forward_parsed_udp.py --host 192.168.1.100 --port 5010

# Terminal 2: Generate token
python registration_token.py generate --server-ip 192.168.1.100 --port 5010
```

### Client Test (without QR scanner)
```kotlin
// In DataPadSettingsFragment or test activity
lifecycleScope.launch {
    val testQrData = """
        {"type":"datapad_registration","version":"1.0","token":"test-token-id","server":"192.168.1.100","port":5010,"expires":9999999999,"permissions":["receive","send_commands"]}
    """.trimIndent()

    val result = qrRegistrationManager.completeRegistration(
        qrData = testQrData,
        deviceName = "Test Device",
        dataPadManager = dataPadManager
    )

    Log.d("Test", "Registration result: $result")
}
```

## Benefits

✅ **User-friendly**: One scan instead of manual configuration
✅ **Secure**: Time-limited, single-use tokens
✅ **Fast**: Complete registration flow < 5 seconds
✅ **Error-resistant**: Validation on both client and server
✅ **Audit trail**: All registrations are logged
✅ **Offline-capable**: Token can be generated in advance

## Next Steps

1. ✅ Python server-side implemented
2. ✅ Kotlin client-side implemented
3. ✅ Patch forward_parsed_udp.py (implemented) — `DeviceRegistration` routing added and interactive registration support included
4. ✅ QR scanner integrated (ZXing) — `QrCodeScannerScreen` composable in `QrCodeScannerComposable.kt`
5. ✅ UI integration: settings include a scan flow that completes registration and triggers handshake
6. ⏳ Optional: Add a dedicated `QrScannerActivity` (if you prefer Activity-based flow rather than composable dialog)
7. ⏳ Testing with real QR code (recommended)

## Troubleshooting

### Token not recognized
- Check server log: `security_audit.jsonl`
- Check token validity: `python registration_token.py verify <token>`
- Check timestamp synchronization (client/server)

### Registration fails
- Check network connectivity
- Check firewall rules (Port 5010 UDP)
- Check server log: `forward_parsed_udp.py` output
- If you see a Python error like `ModuleNotFoundError: No module named '_cffi_backend'`, ensure `cryptography` and `cffi` are installed in your environment:

```bash
pip install cryptography cffi
```

Use a virtual environment (`python -m venv .venv` + `source .venv/bin/activate` or PowerShell equivalent) to avoid system conflicts.

### Device already registered
- Check authorized_devices.json
- Remove device ID from whitelist if necessary
- Or: Reset device key: `keyManager.resetDeviceKey()`
