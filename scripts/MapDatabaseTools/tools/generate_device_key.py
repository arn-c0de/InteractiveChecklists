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
    print("Device stored at:", os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'ecdh_device.json')))
    # Show short non-sensitive prefix of device ID
    device_fp = dev['deviceId'][:8]
    print(f"Device ID (short): {device_fp}...")
    print(f"Public Key (Base64):\n{pubkey_b64}\n")
    print("If you want to use a specific deviceId, pass it when calling get_or_create_device(device_id=...)")
