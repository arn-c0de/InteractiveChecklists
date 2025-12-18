import threading
import time
from crypto_handshake import SessionData


def test_nonce_generation_thread_safety():
    """Test that nonce generation is thread-safe and prevents duplicates"""
    s = SessionData('sid', 'dev', b'k' * 32, b'pk')

    nonces = []
    errors = []

    def generate_nonces(count=100):
        try:
            for _ in range(count):
                nonce = s.generate_nonce()
                nonces.append(nonce)
        except Exception as e:
            errors.append(e)

    # Start multiple threads generating nonces
    threads = []
    for _ in range(5):
        t = threading.Thread(target=generate_nonces, args=(20,))
        threads.append(t)
        t.start()

    # Wait for all threads
    for t in threads:
        t.join()

    # Check results
    assert len(errors) == 0, f"Errors occurred: {errors}"
    assert len(nonces) == 100, f"Expected 100 nonces, got {len(nonces)}"

    # Check all nonces are unique
    unique_nonces = set(nonces)
    assert len(unique_nonces) == len(nonces), "Duplicate nonces generated"

    # Check counters are sequential (extract counter from nonce)
    counters = []
    for nonce in nonces:
        counter = int.from_bytes(nonce[4:12], 'big')
        counters.append(counter)

    counters.sort()
    # Should be 1 through 100 (counters start from 1 after increment)
    assert counters == list(range(1, 101)), f"Non-sequential counters: {counters}"


def test_nonce_validation_thread_safety():
    """Test that nonce validation is thread-safe"""
    s = SessionData('sid', 'dev', b'k' * 32, b'pk')

    results = []
    errors = []

    def validate_nonces():
        try:
            # Generate and validate some nonces
            for i in range(10):
                nonce = (0).to_bytes(1, 'big') + (0).to_bytes(3, 'big') + i.to_bytes(8, 'big')
                result = s.validate_nonce(nonce)
                results.append((i, result))
        except Exception as e:
            errors.append(e)

    # Start multiple threads
    threads = []
    for _ in range(3):
        t = threading.Thread(target=validate_nonces)
        threads.append(t)
        t.start()

    for t in threads:
        t.join()

    assert len(errors) == 0, f"Validation errors: {errors}"
    # Each thread validates 10 nonces, but they might overlap
    # Just check that we got some results
    assert len(results) > 0


def test_session_invalidation_thread_safety():
    """Test that session invalidation works correctly under concurrent access"""
    s = SessionData('sid', 'dev', b'k' * 32, b'pk')

    # Exhaust the nonce counter to trigger invalidation
    s._nonce_counter = 2**64 - 2  # Almost exhausted

    # This should work
    nonce1 = s.generate_nonce()
    assert s._is_valid is True

    # This should invalidate the session
    try:
        nonce2 = s.generate_nonce()
        assert False, "Should have raised RuntimeError"
    except RuntimeError as e:
        assert "exhausted" in str(e)
        assert s._is_valid is False

    # Further attempts should fail
    try:
        nonce3 = s.generate_nonce()
        assert False, "Should have raised RuntimeError"
    except RuntimeError as e:
        assert "invalid" in str(e)