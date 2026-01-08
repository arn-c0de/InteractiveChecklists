# QR-Code Registration System - README

## Overview

This system enables automatic registration of Android DataPad devices via QR code scan, without manual copying of keys or editing config files.

## New Files

### Python Components (scripts/DCS-SCRIPTS-FOLDER-Experimental/)

1. **registration_token.py** - Token management system
   - CLI tool for generating, verifying and managing registration tokens
   - Creates QR codes (ASCII or PNG)
   - Token lifecycle management (expiration, single-use)

2. **test_qr_registration.py** - Integration tests
   - Tests complete registration flow
   - Validation of token generation and verification
   - Error handling tests

### Kotlin Components (app/src/main/java/.../data/datapad/)

3. **QrRegistrationManager.kt** - Client-side registration logic
   - QR code parsing
   - DeviceRegistration message handling
   - Auto-configuration after successful registration

### Documentation (docs/EN/features/)

4. **qr_registration.md** - Complete documentation
5. **qr_registration_quickstart.md** - Quick start guide

## Modified Files

### crypto_handshake.py
```python
# Line ~710: SessionManager.__init__()
# + Registration token manager integration

# Line ~1200: New method handle_device_registration()
# Processes DeviceRegistration messages and adds devices to whitelist
```

### forward_parsed_udp.py (MANUAL PATCH REQUIRED!)
```python
# Line ~803 and ~1716: Extend message routing
# + Add DeviceRegistration message handler
# See qr_registration.md for exact code snippet
```

## Installation

### Python Dependencies

```bash
# Required for QR code generation (optional)
pip install qrcode[pil]

# Already present (from existing installation)
pip install cryptography
```

### Android Dependencies

```kotlin
// Add to build.gradle.kts:
implementation("com.google.mlkit:barcode-scanning:17.2.0")
```

## Usage

### Server-Side

**1. Generate token:**
```bash
cd scripts/DCS-SCRIPTS-FOLDER-Experimental
python registration_token.py generate --server-ip 192.168.1.100 --port 5010
```

**2. Display QR code:**
- ASCII QR code is displayed in terminal
- Optional: `--output-qr qr.png` for image file

**3. Start server:**
```bash
python forward_parsed_udp.py --host 192.168.1.100 --port 5010
```

### Client-Side (Android)

**1. Scan QR code:**
```kotlin
val qrRegistrationManager = QrRegistrationManager(
    context = context,
    keyManager = KeyManager(context)
)

// Parse QR code
val token = qrRegistrationManager.parseQrCode(qrData) ?: return

// Register device
val result = qrRegistrationManager.registerDevice(
    token = token,
    deviceName = "My Tablet"
)

when (result) {
    is RegistrationResult.Success -> {
        // Apply settings and start handshake
        qrRegistrationManager.applyTokenSettings(token, dataPadManager)
        dataPadManager.performHandshake()
    }
    is RegistrationResult.Error -> {
        Log.e(TAG, "Registration failed: ${result.message}")
    }
    is RegistrationResult.Timeout -> {
        Log.e(TAG, "Registration timeout")
    }
}
```

**2. Or complete flow:**
```kotlin
lifecycleScope.launch {
    val result = qrRegistrationManager.completeRegistration(
        qrData = scannedQrData,
        deviceName = "My Tablet",
        dataPadManager = dataPadManager
    )

    // Handle result...
}
```

## Security Features

### Token Security
- ✅ **Single-use**: Token is consumed after use
- ✅ **Time-limited**: Default 10 minutes validity
- ✅ **Cryptographically secure**: 32-byte SecureRandom token ID
- ✅ **Server-bound**: Token is bound to server IP

### Registration Validation
- ✅ **Public Key Validation**: EC-Curve-Check (SECP256R1)
- ✅ **Device-ID Format Check**: Alphanumeric, 8-128 characters
- ✅ **Duplicate Check**: Prevents multiple registration
- ✅ **IP Logging**: Registration IP is stored
- ✅ **Audit Logging**: All registrations in security_audit.jsonl

### Rate-Limiting
- Registration attempts are subject to existing rate limits
- Prevents token brute-force attacks

## Testing

```bash
# Run integration tests
python test_qr_registration.py
```

**Expected output:**
```
🧪 QR REGISTRATION SYSTEM - INTEGRATION TESTS
======================================================================

TEST 1: Token Generation
✅ Token generated: ...
✅ QR payload valid

TEST 2: Token Validation
✅ Valid token verified
✅ Invalid token rejected
✅ Expired token rejected
✅ Used token rejected

TEST 3: Device Registration Flow
⚠️  Expected error: Invalid public key (dummy key)
✅ Registration validation working correctly

TEST 4: Error Handling
✅ Missing fields rejected
✅ Invalid token rejected

TEST 5: QR Payload Size
✅ Payload fits in QR Version 10 (57x57)
✅ Payload size acceptable for QR code

✅ ALL TESTS PASSED
```

## Troubleshooting

### "Registration token system unavailable"
- Check if `registration_token.py` is in the same directory as `crypto_handshake.py`

### "Invalid token"
- Token may have expired (default: 10 minutes)
- Token already used (single-use)
- Generate new token: `python registration_token.py generate ...`

### "Invalid public key"
- Is KeyManager correctly initialized?
- Public key is not an EC key or wrong curve

### DeviceRegistration messages not received
- forward_parsed_udp.py not yet patched!
- See qr_registration.md "Manual Patch Required"

## Message Protocol

### DeviceRegistration (Client → Server)
```json
{
  "type": "DeviceRegistration",
  "registrationToken": "<token-id>",
  "deviceId": "<device-id>",
  "deviceName": "My Tablet",
  "publicKey": "<base64-ec-public-key>",
  "timestamp": 1736349123456
}
```

### RegistrationSuccess (Server → Client)
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

### RegistrationError (Server → Client)
```json
{
  "type": "RegistrationError",
  "error": "InvalidToken",
  "message": "Registration token is invalid or expired",
  "timestamp": 1736349123456
}
```

## Token Management CLI

```bash
# Generate token
python registration_token.py generate --server-ip IP --port PORT [--validity MINUTES] [--output-qr FILE]

# List tokens
python registration_token.py list

# Verify token
python registration_token.py verify <token-id>

# Clean up expired tokens
python registration_token.py cleanup
```

## File Structure

```
scripts/DCS-SCRIPTS-FOLDER-Experimental/
├── registration_token.py          # Token management (NEW)
├── test_qr_registration.py        # Integration tests (NEW)
├── crypto_handshake.py            # + DeviceRegistration handler (MODIFIED)
├── forward_parsed_udp.py          # + Message routing (MANUAL PATCH)
├── registration_tokens.json       # Token storage (AUTO-GENERATED)
└── authorized_devices.json        # Device whitelist (UPDATED)

app/src/main/java/.../data/datapad/
├── QrRegistrationManager.kt       # Client registration (NEW)
├── KeyManager.kt                  # Existing
├── DataPadManager.kt              # Existing (updateServerIp, updateUdpPort used)
└── HandshakeMessages.kt           # Existing

docs/EN/features/
├── qr_registration.md             # Complete docs (NEW)
└── qr_registration_quickstart.md  # Quick start guide (NEW)
```

## Next Steps

### Minimal Implementation (functional):
1. ✅ Python server-side (done)
2. ✅ Kotlin client-side (done)
3. ⏳ Patch forward_parsed_udp.py (see qr_registration.md)
4. ⏳ Run tests: `python test_qr_registration.py`

### UI Integration (optional):
5. ⏳ Add ML Kit Barcode Scanner to build.gradle.kts
6. ⏳ Create QrScannerActivity
7. ⏳ Extend DataPadSettingsFragment (QR scan button)
8. ⏳ End-to-end testing with real QR code

## Support & Contribution

For questions or problems:
- See [qr_registration.md](../../docs/EN/features/qr_registration.md) for details
- See [qr_registration_quickstart.md](../../docs/EN/features/qr_registration_quickstart.md) for quick start

## License

Same as parent project (see [LICENSE](../../LICENSE))
