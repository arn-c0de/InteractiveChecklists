# Secure Fingerprinting Implementation

## Overview
This document describes the secure fingerprinting approach used for logging sensitive identifiers (device IDs, device names, session keys) in the application.

## Security Issue Fixed
**CodeQL Alert**: Use of a broken or weak cryptographic hashing algorithm on sensitive data

### Problem
The codebase was using SHA256 directly to create "fingerprints" of sensitive identifiers for logging purposes:
```python
# INSECURE - vulnerable to brute-force attacks
device_fp = hashlib.sha256(device_id.encode('utf-8')).hexdigest()[:8]
```

While SHA256 is secure for file integrity checking, it's **inappropriate for sensitive data** because:
1. **Too fast** - Attackers can brute-force original values from intercepted logs
2. **No key** - Anyone with the hash can attempt to reverse it
3. **Deterministic** - Same input always produces same output, enabling correlation attacks

## Solution: HMAC-Based Fingerprinting

### Implementation
We now use **HMAC-SHA256 with a random per-session key**:
```python
# SECURE - uses HMAC with random key
import hmac
import hashlib
import secrets

fp_key = secrets.token_bytes(32)  # Random 256-bit key per session
device_fp = hmac.new(fp_key, device_id.encode('utf-8'), hashlib.sha256).hexdigest()[:8]
```

### Why HMAC?
1. **Keyed hash** - Requires secret key, making brute-force attacks infeasible
2. **Random key per session** - Fingerprints change between runs, preventing correlation
3. **Industry standard** - HMAC-SHA256 is approved for cryptographic use (FIPS 197)
4. **Same debugging utility** - Still provides unique identifiers for troubleshooting

## Files Modified
1. [network/ecdh_client.py](../scripts/MapDatabaseTools/network/ecdh_client.py)
   - Device ID and name fingerprints (initialization)
   - Session key fingerprint (handshake)

2. [network/datapad_receiver.py](../scripts/MapDatabaseTools/network/datapad_receiver.py)
   - Device ID fingerprints (initialization and loading)

3. [run_datapad_ecdh.py](../scripts/MapDatabaseTools/run_datapad_ecdh.py)
   - Command-line device fingerprint display

4. [tools/generate_device_key.py](../scripts/MapDatabaseTools/tools/generate_device_key.py)
   - Device generation fingerprint display

5. [network/ecdh_device.py](../scripts/MapDatabaseTools/network/ecdh_device.py)
   - Device protection setup fingerprint

## Legitimate SHA256 Usage
**Note**: SHA256 is still used appropriately for:
- **File integrity checking** in [crypto_handshake.py](../scripts/DCS-SCRIPTS-FOLDER-Experimental/crypto_handshake.py)
- This is a valid use case - SHA256 is perfect for verifying file modifications

## Security Considerations
- **No key storage needed** - Random keys are ephemeral (per-session only)
- **Forward secrecy** - Old logs can't be correlated with new ones
- **Performance** - HMAC-SHA256 is just as fast as plain SHA256
- **Compliance** - Meets security best practices for sensitive data handling

## References
- HMAC (RFC 2104): https://tools.ietf.org/html/rfc2104
- NIST FIPS 198-1: https://csrc.nist.gov/publications/detail/fips/198/1/final
- OWASP: Don't use fast hashes for sensitive data

---
App Version: v1.0.25
Last Updated: 2026.01.19
---