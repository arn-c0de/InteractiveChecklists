# AES-GCM Encryption for DataPad

## Overview
Communication between the Python script and the Android app is protected using **AES-GCM** (Authenticated Encryption with Associated Data).

## Implemented changes

### 1. Python script (`forward_parsed_udp.py`)
- ✅ AES-GCM encryption with a 256-bit pre-shared key
- ✅ Random 12-byte nonce per packet
- ✅ AEAD (authenticated encryption) provides integrity and authenticity
- ✅ Optional disable with the `--no-encrypt` flag

### 2. Android app (`DataPadManager.kt`)
- ✅ AES-GCM decryption using `javax.crypto`
- ✅ Automatic validation of packet authenticity
- ✅ Error handling for invalid packets

### 3. UI updates (`DataPadPopup.kt`)
- ✅ "🔒 AES-GCM encrypted" status indicator
- ✅ Updated command-line messages

## Installation

### Python side
```bash
pip install cryptography
```

### Android side
No additional dependencies required — uses `javax.crypto` (standard Android API).

> Notes: Follow the instructions in this document to generate and configure the pre-shared key securely on both sides.

## Usage

### Encrypted (recommended)
```bash
python forward_parsed_udp.py --host 192.168.178.100 --port 5010
```

### Unencrypted (not recommended)
```bash
python forward_parsed_udp.py --host 192.168.178.100 --port 5010 --no-encrypt
```

## Pre-Shared Key

**Important:** The pre-shared key **must** be identical on both sides. Prefer generating a random 32‑byte (256‑bit) key and use a hex string to avoid accidentally checking secrets into the repository.

Example: Generate a 32-byte key (hex):

```bash
python - <<'PY'
import os
print(os.urandom(32).hex())
PY
```

Configure in Python (safer: hex-to-bytes):

```python
# forward_parsed_udp.py
PRE_SHARED_KEY = bytes.fromhex('your_64_char_hex_key_here')
```

Configure in Android/Kotlin:

```kotlin
// Helper to convert hex string to byte array
private fun hexStringToByteArray(hex: String): ByteArray {
    val len = hex.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        i += 2
    }
    return data
}

// Use the hex key generated above
private val PRE_SHARED_KEY = hexStringToByteArray("your_64_char_hex_key_here")
```

### In production
- Distribute and store keys securely (out-of-band), rotate them as needed, and avoid checking secrets into the repository.

## Security properties

✅ **Confidentiality**: Data is encrypted using AES-256
✅ **Integrity**: GCM authentication tag prevents tampering
✅ **Authenticity**: Only clients with the correct key can decrypt
✅ **Replay protection**: Each packet uses a unique nonce

## Packet format

```
[12 Bytes Nonce][Encrypted JSON][16 Bytes GCM Tag]
```

Minimum packet size: 28 bytes (nonce + tag)

## Troubleshooting

### "Failed to decrypt packet - check Pre-Shared Key!"
- Ensure the pre-shared key is identical in both configurations
- Ensure the Python script has the `cryptography` package installed

### Python import errors
```bash
pip install --upgrade cryptography
```

## Notes

- Pylance may show unresolved-import warnings in VSCode if `cryptography` is not installed in the active Python environment
- Encryption is enabled by default — use `--no-encrypt` only for debugging
- The app shows "🔒 AES-GCM encrypted" in the connection status
