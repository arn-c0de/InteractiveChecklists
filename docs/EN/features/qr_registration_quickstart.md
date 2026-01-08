# QR-Registration Quick Start Guide

## Quick Start (5 Minute Setup)

### 1. Prepare Server

```bash
# Change to DCS scripts folder
cd "C:\Users\<username>\Saved Games\DCS\Scripts\DCS-SCRIPTS-FOLDER-Experimental"

# Install QR code library (optional, but recommended)
pip install qrcode[pil]

# Test: Check registration system
python test_qr_registration.py
```

**Expected output:**
```
✅ ALL TESTS PASSED
```

### 2. Generate Registration Token

**Option A: With terminal QR code**
```bash
python registration_token.py generate --server-ip 192.168.1.100 --port 5010
```

**Option B: With image file**
```bash
python registration_token.py generate --server-ip 192.168.1.100 --port 5010 --output-qr qr.png
```

→ Open `qr.png` on a second screen or print it out

### 3. Start Server

```bash
python forward_parsed_udp.py --host 192.168.1.100 --port 5010
# Or skip the interactive QR prompt:
python forward_parsed_udp.py --host 192.168.1.100 --port 5010 --skip-qr-prompt
```

- On startup you'll see an interactive prompt: press **B** within 5 seconds to generate a QR token and wait for registration.
- If you don't press **B**, the server will start normally.

**Server is ready when:**
```
✅ Registration token system enabled
🔐 SessionManager initialized
📡 Listening on 192.168.1.100:5010 for handshake
```

### 4. Configure Android App

**Option A: Scan QR code (implemented)**
- DataPad Settings → "Scan QR code"
- Camera opens full-screen and detects QR codes
- Wait for confirmation "Device registered successfully" (app automatically sends DeviceRegistration)

**Option B: Manual (temporary solution)**

Extract data from QR code and enter:

```json
// Copy QR code content:
{"type":"datapad_registration","version":"1.0","token":"abc...","server":"192.168.1.100","port":5010,...}
```

In app settings:
- Server IP: `192.168.1.100`
- UDP Port: `5010`

Then in Kotlin code (temporary):

```kotlin
// In DataPadSettingsFragment or similar
lifecycleScope.launch {
    val qrData = """{"type":"datapad_registration",...}""" // From QR code

    val qrRegistrationManager = QrRegistrationManager(
        context = requireContext(),
        keyManager = KeyManager(requireContext())
    )

    val result = qrRegistrationManager.completeRegistration(
        qrData = qrData,
        deviceName = "My Tablet",
        dataPadManager = dataPadManager
    )

    when (result) {
        is RegistrationResult.Success -> {
            Toast.makeText(context, "Registered!", Toast.LENGTH_LONG).show()
            dataPadManager.performHandshake()
        }
        else -> {
            Toast.makeText(context, "Error: $result", Toast.LENGTH_LONG).show()
        }
    }
}
```

### 5. Verify

**Check server log:**
```
📥 Received DeviceRegistration from (192.168.1.x, 12345)
✅ Device registered: test_device_12345678 (My Tablet)
```

**Check authorized_devices.json:**
```bash
cat authorized_devices.json
```

Should contain (camelCase fields):
```json
{
  "devices": [
    {
      "deviceId": "test_device_12345678",
      "name": "My Tablet",
      "publicKey": "MFkwEw...",
      "permissions": ["receive", "send_commands"],
      "addedDate": "2026-01-08 15:30:00",
      "addedBy": "qr_registration",
      "registeredFromIp": "192.168.1.x"
    }
  ]
}
```

## Workflow Diagram

```
┌─────────────┐                    ┌─────────────┐
│   Server    │                    │Android App  │
│   (Python)  │                    │  (Kotlin)   │
└──────┬──────┘                    └──────┬──────┘
       │                                  │
       │ 1. Generate Token                │
       │    $ python registration_token.py│
       ├──────────────────────────────────┤
       │                                  │
       │ 2. Display QR Code               │
       │    [QR Image/ASCII]              │
       │                                  │
       │                                  │ 3. Scan QR Code
       │                                  │    QrRegistrationManager.parseQrCode()
       │                                  ├────────────────┐
       │                                  │                │
       │                                  │ 4. Parse Token │
       │                                  │    (validate)  │
       │                                  │◄───────────────┘
       │                                  │
       │◄─────────────────────────────────┤ 5. Send DeviceRegistration
       │  DeviceRegistration              │    {token, deviceId, publicKey}
       │  (UDP Plaintext)                 │
       │                                  │
       ├──────────────────┐               │
       │ 6. Verify Token  │               │
       │    Consume Token │               │
       │    Add to        │               │
       │    Whitelist     │               │
       │◄─────────────────┘               │
       │                                  │
       ├─────────────────────────────────►│ 7. RegistrationSuccess
       │  {deviceId, permissions}         │    or RegistrationError
       │  (UDP Plaintext)                 │
       │                                  │
       │                                  ├────────────────┐
       │                                  │ 8. Apply       │
       │                                  │    Settings    │
       │                                  │◄───────────────┘
       │                                  │
       │◄─────────────────────────────────┤ 9. Start Handshake
       │  ClientHello (ECDH)              │    (Normal Flow)
       │                                  │
       ├─────────────────────────────────►│ ServerHello
       │                                  │
       │◄─────────────────────────────────┤ KeyConfirm
       │                                  │
       ├─────────────────────────────────►│ Ack (ready)
       │                                  │
       │                                  │
       │ 10. Encrypted Flight Data        │
       ├═════════════════════════════════►│
       │    (AES-GCM)                     │
       │                                  │
```

## Common Problems

### Problem: "Registration token system unavailable"

**Cause:** `registration_token.py` not found

**Solution:**
```bash
# Check if file exists
ls registration_token.py

# If not, create it (see QR-Registration docs)
```

### Problem: "Registration token is invalid or expired"

**Cause:** Token expired or already used

**Solution:**
```bash
# Generate new token
python registration_token.py generate --server-ip 192.168.1.100 --port 5010

# Clean up old tokens
python registration_token.py cleanup
```

### Problem: "Invalid public key"

**Cause:** EC-Public-Key format invalid

**Solution:**
```kotlin
// Check KeyManager initialization
val keyManager = KeyManager(context)
val keyPair = keyManager.getOrCreateDeviceKeyPair()
val publicKey = keyManager.exportPublicKey()
Log.d("Debug", "Public key: $publicKey")
```

### Problem: Python crypto dependency error

**Cause:** Missing native dependencies for the `cryptography` package (e.g. `_cffi_backend`) or not installed in the active environment.

**Solution:**
```bash
# Install cryptography and cffi
pip install cryptography cffi
# Use a virtual environment to keep dependencies isolated
python -m venv .venv
.\.venv\Scripts\Activate.ps1  # PowerShell
pip install -r requirements.txt  # if provided
```

### Problem: Server doesn't receive DeviceRegistration

**Cause:** forward_parsed_udp.py not patched

**Solution:**
- DeviceRegistration routing is now handled by `forward_parsed_udp.py` (no manual patch required). Use the interactive prompt at startup (press 'B') to generate a token and wait for registration or use `--skip-qr-prompt` to skip.
- Add handler for `DeviceRegistration` message

## Token Management

### Show all active tokens
```bash
python registration_token.py list
```

### Verify token
```bash
python registration_token.py verify <token-id>
```

### Delete expired tokens
```bash
python registration_token.py cleanup
```

### Delete all tokens (restart)
```bash
rm registration_tokens.json
```

## Security Checklist

- ✅ Token has short validity (max. 10 minutes)
- ✅ Token is single-use
- ✅ Server validates EC-Public-Key
- ✅ Registration IP is logged
- ✅ Rate-limiting active
- ✅ Audit log is written

## Performance Tips

### Optimize QR code size

**Current:** ~200-300 bytes → QR Version 10-15

**Optimization (optional):**
```python
# Shorten token ID (from 32 to 16 bytes)
token_id = secrets.token_urlsafe(16)  # Instead of 32

# Payload size: ~150 bytes → QR Version 8
```

### Server Performance

**Standard:** Unlimited tokens

**For production:**
```python
# In registration_token.py: Limit max. active tokens
MAX_ACTIVE_TOKENS = 10

if len(manager.list_active_tokens()) >= MAX_ACTIVE_TOKENS:
    manager.cleanup_expired()
    if len(manager.list_active_tokens()) >= MAX_ACTIVE_TOKENS:
        raise Exception("Too many active tokens")
```

## Next Steps

1. ✅ QR scanner UI implemented (ZXing-based `QrCodeScannerScreen`)
2. ⏳ ML Kit remains optional (can be swapped in as an alternative)
3. ✅ Settings fragment already integrates the scan flow and registration
4. ⏳ End-to-end testing with real QR code (please test on target devices)

## Support

When experiencing problems:
1. Check server log: `forward_parsed_udp.py` output
2. Check security audit: `security_audit.jsonl`
3. Check token status: `python registration_token.py list`
4. Check Android log: `adb logcat | grep QrRegistration`
