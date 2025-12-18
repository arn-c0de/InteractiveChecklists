import time
from crypto_handshake import SessionManager


def test_generic_error_messages():
    """Test that detailed exceptions are not leaked to clients"""
    mgr = SessionManager()

    # Test ClientHello with invalid data that causes internal exception
    # This should return a generic error message
    result = mgr.handle_client_hello({
        "deviceId": "test_device_1234567890",
        "publicKey": "invalid_base64",
        "timestamp": int(time.time() * 1000)
    }, ("127.0.0.1", 12345))

    assert result["type"] == "Error"
    assert "internal error" in result["message"].lower()
    assert "invalid_base64" not in result["message"]  # Should not leak exception details


def test_key_confirm_generic_errors():
    """Test that KeyConfirm errors are generic"""
    mgr = SessionManager()

    # Test with invalid session ID that might cause internal errors
    result = mgr.handle_key_confirm({
        "sessionId": "invalid-session-id",
        "hmac": "dGVzdGluZzEyMzQ1Njc4OTAxMjM0NTY=",  # 32 bytes base64
        "timestamp": int(time.time() * 1000)
    })

    assert result["type"] == "Error"
    # Should be generic, not detailed
    assert "failed" in result["message"].lower()


def test_constant_time_delays():
    """Test that authentication failures have constant timing"""
    mgr = SessionManager()

    # Create a mock session for testing
    from crypto_handshake import SessionData
    session = SessionData("test_session", "test_device", b"k" * 32, b"pk")
    mgr.sessions["test_session"] = session

    start_time = time.time()
    result = mgr.handle_key_confirm({
        "sessionId": "test_session",
        "hmac": "dGVzdGluZzEyMzQ1Njc4OTAxMjM0NTY=",  # Wrong HMAC
        "timestamp": int(time.time() * 1000)
    })
    end_time = time.time()

    # Should have taken at least 100ms due to constant time delay
    elapsed = end_time - start_time
    assert elapsed >= 0.09  # Allow some tolerance
    assert result["error"] == "AuthFailed"