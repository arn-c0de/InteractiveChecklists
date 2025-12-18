import time
import pytest
from crypto_handshake import SessionManager


def test_combined_rate_limiting():
    """Test that combined IP + Device-ID rate limiting prevents abuse"""
    mgr = SessionManager()

    ip1 = "192.168.1.1"
    ip2 = "192.168.1.2"
    device1 = "device_1234567890abcdef"
    device2 = "device_fedcba0987654321"

    # Test normal operation (should not be rate limited)
    assert not mgr.is_combined_rate_limited(ip1, device1)
    assert not mgr.is_combined_rate_limited(ip1, device1)  # Second call

    # Exhaust the limit for IP1 + Device1 combination
    for _ in range(mgr.MAX_HANDSHAKE_ATTEMPTS - 1):  # -1 because we already made 2 calls
        assert not mgr.is_combined_rate_limited(ip1, device1)

    # Next call should be rate limited
    assert mgr.is_combined_rate_limited(ip1, device1)

    # Different IP with same device should not be limited (device-based limiting is separate)
    assert not mgr.is_combined_rate_limited(ip2, device1)

    # Same IP with different device should not be limited
    assert not mgr.is_combined_rate_limited(ip1, device2)


def test_adaptive_rate_limiting():
    """Test that rate limits become stricter after violations"""
    mgr = SessionManager()

    ip = "10.0.0.1"
    device = "test_device_1234567890"

    # First violation cycle
    for _ in range(mgr.MAX_HANDSHAKE_ATTEMPTS):
        assert not mgr.is_combined_rate_limited(ip, device)

    # This should trigger rate limiting and increment violations
    assert mgr.is_combined_rate_limited(ip, device)

    # Wait for window to reset
    time.sleep(mgr.RATE_LIMIT_WINDOW + 0.1)

    # Now the limit should be stricter (half of original)
    expected_stricter_limit = max(1, mgr.MAX_HANDSHAKE_ATTEMPTS // 2)
    for _ in range(expected_stricter_limit):
        assert not mgr.is_combined_rate_limited(ip, device)

    # Should be limited again
    assert mgr.is_combined_rate_limited(ip, device)


def test_rate_limit_cleanup():
    """Test that expired rate limit entries are cleaned up"""
    mgr = SessionManager()

    # Add some entries
    mgr.handshake_attempts["test_key"] = (1, time.time() - mgr.RATE_LIMIT_WINDOW * 3, 0)

    # Cleanup should remove expired entries
    mgr.cleanup_rate_limits()

    assert "test_key" not in mgr.handshake_attempts