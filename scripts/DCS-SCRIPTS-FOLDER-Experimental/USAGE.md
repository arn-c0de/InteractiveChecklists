# DataPad Forward Script - Usage Guide

## 🚀 Quick Start Command

```bash
python forward_parsed_udp.py --repeat-last --interval 5 --host 192.168.178.131 --port 5010 --verbose --authorized-devices authorized_devices.json
```

**Features:**
- ✅ End-to-end encryption with ECDH key exchange
- ✅ Device whitelist authorization
- ✅ No pre-shared key needed
- ✅ Session-based security
- ✅ Mutual authentication

**Setup:**
1. Enable ECDH mode in Android app settings
2. Check app logs for Device ID (32-character hex string)
3. Add device to `authorized_devices.json`:
```json
{
  "devices": [
    {
      "deviceId": "your_device_id_from_app_logs",
      "name": "My Tablet",
      "publicKey": "base64_key_from_app_or_placeholder",
      "permissions": ["receive", "send_commands"],
      "addedDate": "2024-12-17T10:00:00Z"
    }
  ]
}
```
4. Run the command above

---

## 📋 Command Options

| Option | Description | Default |
|--------|-------------|---------|
| `--host` | Android device IP address | 127.0.0.1 |
| `--port` | UDP port | 5010 |
| `--interval` | Polling/repeat interval (seconds) | 0.2 |
| `--verbose` | Enable detailed logging | Off |
| `--repeat-last` | Repeat last line instead of tailing | Off |
| `--authorized-devices` | Path to device whitelist JSON | authorized_devices.json |
| `--aircraft` | Aircraft name for handshake | Auto-detect |
| `--no-encrypt` | Disable encryption (NOT RECOMMENDED) | Off |

---

## 🔧 Setup Steps

### 1. Install Dependencies
```bash
pip install cryptography
```

### 2. Copy Scripts to DCS
Copy these files to `%USERPROFILE%\Saved Games\DCS\Scripts\`:
- `forward_parsed_udp.py`
- `crypto_handshake.py`
- `authorized_devices.json`

### 3. Configure Android App
- Open DataPad settings
- Enable ECDH mode
- Note the Device ID shown in logs
- Set port to 5010

### 4. Authorize Device
Edit `authorized_devices.json` and add your device ID

### 5. Run Script
Use the quick start command from above

---

## 🐛 Troubleshooting

### "unrecognized arguments: --use-handshake"
**Solution:** This argument is no longer needed. Update to latest `forward_parsed_udp.py` from this directory

### "Device not authorized"
**Solution:** Add Device ID to `authorized_devices.json` whitelist

### "Handshake timeout"
**Checks:**
1. Android app has ECDH enabled
2. Device is on same network
3. Firewall allows UDP port 5010
4. IP address is correct

---

## 📝 Example: Full ECDH Setup

```bash
# 1. Navigate to DCS Scripts folder
cd "$env:USERPROFILE\Saved Games\DCS\Scripts"

# 2. Start script with ECDH
python forward_parsed_udp.py `
  --repeat-last `
  --interval 5 `
  --host 192.168.178.131 `
  --port 5010 `
  --verbose `
  --authorized-devices authorized_devices.json `
  --aircraft "F/A-18C_hornet"

# Expected output:
# 🔐 ECDH Handshake Mode ENABLED
# 🔐 SessionManager initialized
# 📂 Authorized devices file: authorized_devices.json
# ✅ Loaded 1 authorized device(s)
# 🔐 Listening for handshakes on port 5010
# 📥 ClientHello from My Tablet (abc123...) @ 192.168.178.131
# ✅ Device authorized: My Tablet
# 🔑 Session created: def456... (key derived from ECDH)
# ✅ HMAC verified for session def456...
# 🔒 Sending encrypted data to My Tablet
```

---

## 📚 Related Documentation

- [AES_GCM_ENCRYPTION.md](../../docs/technical/AES_GCM_ENCRYPTION.md) - Encryption details
- [ECDH_USAGE_GUIDE.md](../../docs/technical/ECDH_USAGE_GUIDE.md) - ECDH setup guide
- [SECURITY_FIXES_2024_12_17.md](../../docs/technical/SECURITY_FIXES_2024_12_17.md) - Security audit
- [encryption-todo.md](../../encryption-todo.md) - Known security status

---

**Last Updated:** December 17, 2024  
**Status:** ✅ Production Ready (ECDH Mode)
