#!/usr/bin/env python3
"""
Quick test of the encrypted ECDH device key storage
"""

import os
import sys
import logging

# Add package root (parent of tools/) to path so imports like `from network.ecdh_device` work
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Import module and helpers
import network.ecdh_device as ecdh_device
from network.ecdh_device import generate_device, load_device, get_or_create_device, DEFAULT_DEVICE_FILE

# Avoid interactive password prompt during automated tests by setting an in-memory test password (non-Windows)
# This is only for the test environment and not used in production.
try:
    ecdh_device._password_cache = os.environ.get('MAPDB_TEST_PW', 'test-password-123')
except Exception:
    pass

logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')
logger = logging.getLogger(__name__)

def test_encrypted_storage():
    """Test encrypted key storage"""
    print("\n" + "="*70)
    print("🧪 TESTING ENCRYPTED ECDH DEVICE KEY STORAGE")
    print("="*70 + "\n")
    
    test_file = os.path.join(os.path.dirname(DEFAULT_DEVICE_FILE), 'test_ecdh_device.json')
    
    # Clean up any existing test file
    if os.path.exists(test_file):
        os.remove(test_file)
        print("✓ Removed existing test file\n")
    
    # Test 1: Generate new device
    print("TEST 1: Generate new encrypted device key")
    print("-" * 70)
    device1 = generate_device(file_path=test_file)
    print(f"✓ Generated device ID: {device1['deviceId']}")
    print(f"✓ Private key (first 50 chars): {device1['privateKeyPem'][:50]}...")
    print()
    
    # Test 2: Load device
    print("TEST 2: Load encrypted device key")
    print("-" * 70)
    device2 = load_device(file_path=test_file)
    print(f"✓ Loaded device ID: {device2['deviceId']}")
    print(f"✓ Private key matches: {device1['privateKeyPem'] == device2['privateKeyPem']}")
    print()
    
    # Test 3: Verify encryption
    print("TEST 3: Verify file is encrypted")
    print("-" * 70)
    import json
    with open(test_file, 'r') as f:
        data = json.load(f)
    
    has_encrypted = 'encryptedPrivateKey' in data
    has_plaintext = 'privateKeyPem' in data
    version = data.get('version', 0)
    method = data.get('encryptedPrivateKey', {}).get('method', 'unknown')
    
    print(f"✓ Has encrypted key: {has_encrypted}")
    print(f"✓ Has plaintext key: {has_plaintext} (should be False!)")
    print(f"✓ Format version: {version}")
    print(f"✓ Encryption method: {method}")
    
    if has_plaintext:
        print("❌ ERROR: Private key is stored in plaintext!")
        return False
    else:
        print("✓ SUCCESS: Private key is encrypted!")
    
    print()
    
    # Test 4: Test get_or_create (should load existing)
    print("TEST 4: Test get_or_create_device")
    print("-" * 70)
    device3 = get_or_create_device(file_path=test_file)
    print(f"✓ Device ID matches: {device3['deviceId'] == device1['deviceId']}")
    print()

    # Test 5: generate_device_with_password
    print("TEST 5: Generate device with explicit password (non-interactive)")
    print("-" * 70)
    test_file2 = os.path.join(os.path.dirname(DEFAULT_DEVICE_FILE), 'test_ecdh_device_pw.json')
    if os.path.exists(test_file2):
        os.remove(test_file2)
    device_pw = ecdh_device.generate_device_with_password(password=os.environ.get('MAPDB_TEST_PW', 'test-password-123'), file_path=test_file2)
    print(f"✓ Generated device ID: {device_pw['deviceId']}")
    d2 = load_device(file_path=test_file2)
    print(f"✓ Loaded device ID: {d2['deviceId']}")
    os.remove(test_file2)
    print()

    # Test 6: reset_device
    print("TEST 6: Reset device (removes old and creates new)")
    print("-" * 70)
    test_file3 = os.path.join(os.path.dirname(DEFAULT_DEVICE_FILE), 'test_ecdh_device_reset.json')
    if os.path.exists(test_file3):
        os.remove(test_file3)
    d_old = generate_device(file_path=test_file3)
    d_new = ecdh_device.reset_device(password=os.environ.get('MAPDB_TEST_PW', 'test-password-123'), file_path=test_file3)
    print(f"✓ Old ID: {d_old['deviceId']} -> New ID: {d_new['deviceId']}")
    assert d_old['deviceId'] != d_new['deviceId']
    os.remove(test_file3)
    print()

    # Cleanup
    print("CLEANUP")
    print("-" * 70)
    os.remove(test_file)
    print("✓ Removed test file")
    print()
    
    print("="*70)
    print("✅ ALL TESTS PASSED!")
    print("="*70 + "\n")
    
    return True


if __name__ == '__main__':
    try:
        success = test_encrypted_storage()
        sys.exit(0 if success else 1)
    except Exception as e:
        print(f"\n❌ TEST FAILED: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
