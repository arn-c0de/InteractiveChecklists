#!/usr/bin/env python3
"""
test_qr_registration.py
Integration test for QR-code based device registration

Tests:
1. Token generation
2. Token validation
3. Device registration flow
4. Error handling (expired token, invalid format, etc.)
"""

import sys
import json
import time
import socket
from pathlib import Path

# Add scripts folder to path
sys.path.insert(0, str(Path(__file__).parent))

try:
    from registration_token import RegistrationTokenManager, RegistrationToken
    from crypto_handshake import SessionManager
except ImportError as e:
    print(f"❌ Import error: {e}")
    print("Make sure registration_token.py and crypto_handshake.py are in the same directory")
    sys.exit(1)


def test_token_generation():
    """Test token generation and serialization"""
    print("\n" + "="*70)
    print("TEST 1: Token Generation")
    print("="*70)
    
    manager = RegistrationTokenManager(tokens_file="test_tokens.json")
    
    # Generate token
    token = manager.generate_token(
        server_ip="192.168.1.100",
        port=5010,
        validity_minutes=10
    )
    
    print(f"✅ Token generated: {token.token_id[:32]}...")
    print(f"   Server: {token.server_ip}:{token.port}")
    print(f"   Expires in: {int((token.expires_at - time.time()) / 60)} minutes")
    
    # Test QR payload
    qr_payload = token.to_qr_payload()
    print(f"\n📱 QR Payload ({len(qr_payload)} bytes):")
    print(qr_payload)
    
    # Parse it back
    parsed = json.loads(qr_payload)
    assert parsed['type'] == 'datapad_registration'
    assert parsed['server'] == '192.168.1.100'
    assert parsed['port'] == 5010
    print("✅ QR payload valid")
    
    return token


def test_token_validation(token: RegistrationToken):
    """Test token validation logic"""
    print("\n" + "="*70)
    print("TEST 2: Token Validation")
    print("="*70)
    
    manager = RegistrationTokenManager(tokens_file="test_tokens.json")
    
    # Verify valid token (create fresh one for validation)
    fresh_token = manager.generate_token(
        server_ip="192.168.1.100",
        port=5010,
        validity_minutes=10
    )
    verified = manager.verify_token(fresh_token.token_id)
    assert verified is not None
    print("✅ Valid token verified")
    
    # Try to verify invalid token
    invalid = manager.verify_token("invalid_token_id")
    assert invalid is None
    print("✅ Invalid token rejected")
    
    # Test expiration (create expired token manually)
    expired_token = RegistrationToken(
        token_id="expired_test",
        server_ip="192.168.1.100",
        port=5010,
        expires_at=time.time() - 60  # Expired 1 minute ago
    )
    manager.tokens[expired_token.token_id] = expired_token
    manager._save_tokens()
    
    expired_verified = manager.verify_token(expired_token.token_id)
    assert expired_verified is None
    print("✅ Expired token rejected")
    
    # Test used token (use fresh_token for this test)
    fresh_token.mark_used()
    manager.tokens[fresh_token.token_id] = fresh_token
    manager._save_tokens()
    used_verified = manager.verify_token(fresh_token.token_id)
    assert used_verified is None
    print("✅ Used token rejected")
    
    # Create fresh token for next test
    fresh_token = manager.generate_token(
        server_ip="192.168.1.100",
        port=5010,
        validity_minutes=10
    )
    
    return fresh_token


def test_device_registration(token: RegistrationToken):
    """Test device registration with SessionManager"""
    print("\n" + "="*70)
    print("TEST 3: Device Registration Flow")
    print("="*70)
    
    # Generate a FRESH token for registration test (previous token was consumed)
    manager = RegistrationTokenManager(tokens_file="test_tokens.json")
    registration_token = manager.generate_token(
        server_ip="192.168.1.100",
        port=5010,
        validity_minutes=10
    )
    print(f"✅ Generated fresh token for registration test: {registration_token.token_id[:16]}...")
    
    # Create test authorized_devices.json
    test_auth_file = "test_authorized_devices.json"
    Path(test_auth_file).write_text(json.dumps({"devices": []}, indent=2))
    
    # Create SessionManager (will load same token file)
    session_mgr = SessionManager(
        authorized_devices_path=test_auth_file,
        aircraft_name="F/A-18C_hornet"
    )
    
    # IMPORTANT: Inject our token manager into session_mgr so it uses the same tokens
    session_mgr.token_manager = manager
    
    # Generate a REAL EC public key for testing    # Generate a REAL EC public key for testing
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.backends import default_backend
    import base64
    
    private_key = ec.generate_private_key(ec.SECP256R1(), default_backend())
    public_key = private_key.public_key()
    public_key_bytes = public_key.public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo
    )
    public_key_b64 = base64.b64encode(public_key_bytes).decode('utf-8')
    
    # Simulate device registration message
    device_registration = {
        "type": "DeviceRegistration",
        "registrationToken": registration_token.token_id,
        "deviceId": "test_device_12345678",
        "deviceName": "Test Tablet",
        "publicKey": public_key_b64,  # Real EC public key
        "timestamp": int(time.time() * 1000)
    }
    
    # Handle registration
    response = session_mgr.handle_device_registration(
        device_registration,
        ("127.0.0.1", 12345)
    )
    
    print(f"\n📥 Registration Response:")
    print(json.dumps(response, indent=2))
    
    # Check for success (should work now with real key)
    if response.get('type') == 'RegistrationSuccess':
        print("✅ Device registered successfully")
        
        # Verify device was added to whitelist
        with open(test_auth_file, 'r') as f:
            whitelist = json.load(f)
        
        devices = whitelist['devices']
        assert len(devices) == 1, f"Expected 1 device, got {len(devices)}"
        assert devices[0]['device_id'] == 'test_device_12345678', "Device ID mismatch"
        assert devices[0]['name'] == 'Test Tablet', "Device name mismatch"
        assert 'public_key' in devices[0], "Public key missing"
        assert 'added_by' in devices[0], "Added_by field missing"
        assert devices[0]['added_by'] == 'qr_registration', "Should be registered via QR"
        print("✅ Device added to whitelist with correct fields")
    elif response.get('type') == 'RegistrationError':
        print(f"❌ Registration failed: {response.get('error')} - {response.get('message')}")
        raise AssertionError(f"Registration should succeed with real EC key: {response.get('message')}")
    else:
        print(f"❌ Unexpected response type: {response.get('type')}")
        raise AssertionError(f"Unexpected response: {response}")
    
    # Cleanup
    Path(test_auth_file).unlink(missing_ok=True)
    Path(test_auth_file + '.bak').unlink(missing_ok=True)


def test_error_handling():
    """Test error handling scenarios"""
    print("\n" + "="*70)
    print("TEST 4: Error Handling")
    print("="*70)
    
    manager = RegistrationTokenManager(tokens_file="test_tokens.json")
    
    # Test 1: Missing fields
    print("\n📝 Test: Missing required fields")
    session_mgr = SessionManager(
        authorized_devices_path="test_authorized_devices.json",
        aircraft_name="F/A-18C_hornet"
    )
    
    incomplete_msg = {
        "type": "DeviceRegistration",
        "registrationToken": "some_token"
        # Missing deviceId, deviceName, publicKey
    }
    
    response = session_mgr.handle_device_registration(incomplete_msg, ("127.0.0.1", 12345))
    assert response['type'] == 'RegistrationError'
    assert response['error'] == 'InvalidRequest'
    print("✅ Missing fields rejected")
    
    # Test 2: Invalid token
    print("\n📝 Test: Invalid token")
    invalid_token_msg = {
        "type": "DeviceRegistration",
        "registrationToken": "invalid_token_that_does_not_exist",
        "deviceId": "test_device",
        "deviceName": "Test",
        "publicKey": "MFkwEw...",
        "timestamp": int(time.time() * 1000)
    }
    
    response = session_mgr.handle_device_registration(invalid_token_msg, ("127.0.0.1", 12345))
    assert response['type'] == 'RegistrationError'
    assert response['error'] == 'InvalidToken'
    print("✅ Invalid token rejected")
    
    # Cleanup
    Path("test_authorized_devices.json").unlink(missing_ok=True)


def test_qr_payload_size():
    """Test QR payload size constraints"""
    print("\n" + "="*70)
    print("TEST 5: QR Payload Size")
    print("="*70)
    
    manager = RegistrationTokenManager(tokens_file="test_tokens.json")
    
    # Generate token with maximum data
    token = manager.generate_token(
        server_ip="192.168.255.255",  # Max IP length
        port=65535,  # Max port
        validity_minutes=99999,  # Long expiry
        permissions=["receive", "send_commands", "admin", "debug"]  # Max permissions
    )
    
    qr_payload = token.to_qr_payload()
    payload_size = len(qr_payload)
    
    print(f"📏 QR Payload Size: {payload_size} bytes")
    print(f"   Token ID length: {len(token.token_id)} chars")
    
    # QR Code capacity limits:
    # - Version 1 (21x21): ~25 bytes
    # - Version 10 (57x57): ~271 bytes
    # - Version 20 (97x97): ~858 bytes
    # - Version 40 (177x177): ~2953 bytes
    
    if payload_size < 271:
        print("✅ Payload fits in QR Version 10 (57x57)")
    elif payload_size < 858:
        print("✅ Payload fits in QR Version 20 (97x97)")
    else:
        print("⚠️  Payload requires QR Version 20+ (large)")
    
    # Typical smartphone cameras scan up to Version 40 easily
    assert payload_size < 2000, "Payload too large for practical QR scanning"
    print("✅ Payload size acceptable for QR code")


def cleanup():
    """Cleanup test files"""
    print("\n" + "="*70)
    print("CLEANUP")
    print("="*70)
    
    test_files = [
        "test_tokens.json",
        "test_authorized_devices.json",
        "test_authorized_devices.json.bak",
        "security_audit.jsonl"
    ]
    
    for file in test_files:
        path = Path(file)
        if path.exists():
            path.unlink()
            print(f"🗑️  Removed {file}")
    
    print("✅ Cleanup complete")


def main():
    print("\n" + "="*70)
    print("🧪 QR REGISTRATION SYSTEM - INTEGRATION TESTS")
    print("="*70)
    
    try:
        # Run tests
        token = test_token_generation()
        token = test_token_validation(token)
        test_device_registration(token)
        test_error_handling()
        test_qr_payload_size()
        
        print("\n" + "="*70)
        print("✅ ALL TESTS PASSED")
        print("="*70)
        
    except AssertionError as e:
        print(f"\n❌ TEST FAILED: {e}")
        return 1
    except Exception as e:
        print(f"\n❌ UNEXPECTED ERROR: {e}")
        import traceback
        traceback.print_exc()
        return 1
    finally:
        cleanup()
    
    return 0


if __name__ == '__main__':
    sys.exit(main())
