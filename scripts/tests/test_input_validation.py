import pytest
from crypto_handshake import SessionManager
import time


def test_client_hello_validation():
    """Test comprehensive input validation in handle_client_hello"""
    mgr = SessionManager()

    # Test invalid message type
    result = mgr.handle_client_hello("not a dict", ("127.0.0.1", 12345))
    assert result["type"] == "Error"
    assert "JSON object" in result["message"]

    # Test missing deviceId
    result = mgr.handle_client_hello({}, ("127.0.0.1", 12345))
    assert result["type"] == "Error"
    assert "deviceId is required" in result["message"]

    # Test invalid deviceId type
    result = mgr.handle_client_hello({"deviceId": 123}, ("127.0.0.1", 12345))
    assert result["type"] == "Error"
    assert "must be a string" in result["message"]

    # Test deviceId too short
    result = mgr.handle_client_hello({"deviceId": "short"}, ("127.0.0.1", 12345))
    assert result["type"] == "Error"
    assert "between 8 and 128" in result["message"]

    # Test deviceId too long
    long_id = "a" * 129
    result = mgr.handle_client_hello({"deviceId": long_id}, ("127.0.0.1", 12345))
    assert result["type"] == "Error"
    assert "between 8 and 128" in result["message"]

    # Test invalid deviceId characters
    result = mgr.handle_client_hello({"deviceId": "invalid@chars!"}, ("127.0.0.1", 12345))
    assert result["type"] == "Error"
    assert "only alphanumeric" in result["message"]

    # Test invalid publicKey
    result = mgr.handle_client_hello({
        "deviceId": "valid_device_id_123",
        "publicKey": "invalid"
    }, ("127.0.0.1", 12345))
    assert result["type"] == "Error"
    assert "Base64 string length invalid" in result["message"]

    # Test invalid timestamp
    result = mgr.handle_client_hello({
        "deviceId": "valid_device_id_123",
        "publicKey": "dGVzdA==",  # "test" in base64
        "timestamp": "not a number"
    }, ("127.0.0.1", 12345))
    assert result["type"] == "Error"
    assert "positive number" in result["message"]

    # Test timestamp from future
    future_time = (time.time() + 120) * 1000  # 2 minutes in future
    result = mgr.handle_client_hello({
        "deviceId": "valid_device_id_123",
        "publicKey": "dGVzdA==",  # "test" in base64
        "timestamp": future_time
    }, ("127.0.0.1", 12345))
    assert result["type"] == "Error"
    assert "future time" in result["message"]

    # Test timestamp too old
    old_time = (time.time() - 120) * 1000  # 2 minutes ago
    result = mgr.handle_client_hello({
        "deviceId": "valid_device_id_123",
        "publicKey": "dGVzdA==",  # "test" in base64
        "timestamp": old_time
    }, ("127.0.0.1", 12345))
    assert result["type"] == "Error"
    assert "too old" in result["message"]


def test_key_confirm_validation():
    """Test input validation in handle_key_confirm"""
    mgr = SessionManager()

    # Test invalid message type
    result = mgr.handle_key_confirm("not a dict")
    assert result["type"] == "Error"
    assert "JSON object" in result["message"]

    # Test missing sessionId
    result = mgr.handle_key_confirm({})
    assert result["type"] == "Error"
    assert "sessionId is required" in result["message"]

    # Test invalid sessionId length
    result = mgr.handle_key_confirm({"sessionId": "short"})
    assert result["type"] == "Error"
    assert "valid UUID" in result["message"]

    # Test missing HMAC
    result = mgr.handle_key_confirm({"sessionId": "12345678-1234-5678-9012-123456789012"})
    assert result["type"] == "Error"
    assert "hmac is required" in result["message"]

    # Test invalid HMAC length
    result = mgr.handle_key_confirm({
        "sessionId": "12345678-1234-5678-9012-123456789012",
        "hmac": "short"
    })
    assert result["type"] == "Error"
    assert "Base64-encoded SHA256 HMAC" in result["message"]

    # Test invalid timestamp
    result = mgr.handle_key_confirm({
        "sessionId": "12345678-1234-5678-9012-123456789012",
        "hmac": "dGVzdGluZzEyMzQ1Njc4OTAxMjM0NTY=",  # 32 bytes base64
        "timestamp": -1
    })
    assert result["type"] == "Error"
    assert "positive number" in result["message"]