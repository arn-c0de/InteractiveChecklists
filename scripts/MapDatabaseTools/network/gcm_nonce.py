"""
GCM nonce validation and sliding-window replay protection
"""
import threading
import logging

logger = logging.getLogger(__name__)


class GcmNonceManager:
    def __init__(self, window: int = 10000):
        self._lock = threading.Lock()
        self._seen = set()
        self._highest = 0
        self._window = window

    def validate_nonce(self, nonce: bytes, expected_sender: int) -> bool:
        """Validate a 12-byte GCM nonce.
        - Checks length
        - Checks sender id prefix (byte 0)
        - Sliding-window replay protection
        Returns True if valid, False otherwise.
        """
        if len(nonce) != 12:
            return False

        if nonce[0] != expected_sender:
            logger.warning(f"Invalid nonce sender id: {nonce[0]:#02x} expected {expected_sender:#02x}")
            return False

        counter = int.from_bytes(nonce[4:12], 'big')

        with self._lock:
            if self._highest and counter <= (self._highest - self._window):
                logger.warning(f"Stale nonce detected (too old): {counter} (highest: {self._highest})")
                return False

            if counter in self._seen:
                logger.warning(f"Replay attack detected! Nonce counter: {counter}")
                return False

            self._seen.add(counter)
            if counter > self._highest:
                self._highest = counter

            # cleanup old counters
            min_allowed = max(0, self._highest - self._window)
            to_remove = [n for n in self._seen if n < min_allowed]
            for n in to_remove:
                self._seen.remove(n)

        return True


# Default global manager instance
default_gcm_nonce_manager = GcmNonceManager()