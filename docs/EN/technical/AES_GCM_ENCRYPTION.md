# AES-GCM Encryption for DataPad

## Overview
DataPad communication is protected using **ECDH (Elliptic Curve Diffie-Hellman)** key exchange with **AES-GCM** (Authenticated Encryption with Associated Data).

## Security Architecture

### ECDH Key Exchange (Primary Method)
DataPad uses ECDH for secure key agreement between the server and Android device:

1. **Perfect Forward Secrecy**: Each session uses unique ephemeral keys
2. **Mutual Authentication**: Both server and client verify each other's identity
3. **Device Authorization**: Server maintains a whitelist of authorized devices (`authorized_devices.json`)
4. **Session-based Encryption**: AES-GCM encryption uses session keys derived from ECDH

### Encryption Details
- **Algorithm**: AES-256-GCM
- **Key Derivation**: HKDF-SHA256 with random salt
- **Nonce**: Counter-based nonces (prevents collision and replay attacks)
- **Authentication**: HMAC-SHA256 for key confirmation
- **Replay Protection**: Sliding-window nonce validation

## Setup

For complete setup instructions, see:
- **[ECDH Usage Guide](ECDH_USAGE_GUIDE.md)** - Step-by-step setup and configuration

## Implementation

### Python side (`forward_parsed_udp.py`)
- ✅ ECDH handshake protocol
- ✅ AES-GCM encryption with session keys
- ✅ Counter-based nonce generation (server side: 0x01 prefix)
- ✅ Device authorization via `authorized_devices.json`
- ✅ HKDF-SHA256 for key derivation

### Android side (`DataPadManager.kt`, `EncryptionProvider.kt`)
- ✅ ECDH key pair generation and storage in Android KeyStore
- ✅ Handshake client (ClientHello → ServerHello → KeyConfirm → Ack)
- ✅ AES-GCM decryption using session keys
- ✅ Counter-based nonce validation (client side: 0x00 prefix)
- ✅ Replay attack protection

## Migration from Legacy PSK

**Note**: Legacy PSK mode has been removed. All connections now use ECDH.

If you were previously using PSK mode:
1. Update your server to the latest version
2. Generate device keys on Android (automatic on first launch)
3. Add your device ID to `authorized_devices.json` on the server
4. Restart both server and Android app

See [ECDH Usage Guide](ECDH_USAGE_GUIDE.md) for detailed migration instructions.


---
App Version: v1.0.25
Last Updated: 2026.01.19
---