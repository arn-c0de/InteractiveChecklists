import time
import pytest
from crypto_handshake import SessionData


def test_nonce_window_invalidates_when_capacity_exceeded():
    # Use a small capacity for testability
    s = SessionData('sid','dev', b'k'*32, b'pk')
    s._NONCE_MAX_ENTRIES = 100
    s._NONCE_RETENTION_SECONDS = 3600

    # Fill window with unique counters
    for i in range(100):
        fake_nonce = (0).to_bytes(1,'big') + (0).to_bytes(3,'big') + i.to_bytes(8,'big')
        assert s.validate_nonce(fake_nonce) is True

    # Next unique nonce should trigger capacity exceeded -> invalidation
    new_nonce = (0).to_bytes(1,'big') + (0).to_bytes(3,'big') + (100).to_bytes(8,'big')
    assert s.validate_nonce(new_nonce) is False
    assert s._is_valid is False


def test_nonce_replay_detected():
    s = SessionData('sid','dev', b'k'*32, b'pk')
    n = 42
    nonce = (0).to_bytes(1,'big') + (0).to_bytes(3,'big') + n.to_bytes(8,'big')
    assert s.validate_nonce(nonce) is True
    # Replay same nonce
    assert s.validate_nonce(nonce) is False
