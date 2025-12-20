#!/usr/bin/env python3
"""
Generate ECDH public key for a device ID
Use this to get the public key to add to authorized_devices.json
"""

import sys
import base64
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.backends import default_backend

import os
from network.ecdh_device import get_or_create_device, get_public_key_b64_from_pem

if __name__ == "__main__":
    # Create or load device
    dev = get_or_create_device()
    pubkey_b64 = get_public_key_b64_from_pem(dev['privateKeyPem'])
    import hmac
    import hashlib
    import secrets
    print("Device stored at:", os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'ecdh_device.json')))
    # Use HMAC for secure fingerprinting of sensitive device ID
    fp_key = secrets.token_bytes(32)
    device_fp = hmac.new(fp_key, dev['deviceId'].encode('utf-8'), hashlib.sha256).hexdigest()[:8]
    print(f"Device ID fingerprint: {device_fp}")
    print(f"Public Key (Base64):\n{pubkey_b64}\n")
    print("If you want to use a specific deviceId, pass it when calling get_or_create_device(device_id=...)")
