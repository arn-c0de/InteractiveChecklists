"""
GCM nonce validation and sliding-window replay protection
"""
import threading
import logging
import time
from typing import Dict, Tuple, Set

logger = logging.getLogger(__name__)


class ClientNonceState:
    """Per-client nonce validation state"""
    def __init__(self, window: int = 10000):
        self.seen: Set[int] = set()
        self.highest: int = 0
        self.window: int = window
        self.last_activity: float = time.time()
    
    def update_activity(self):
        """Update last activity timestamp"""
        self.last_activity = time.time()


class GcmNonceManager:
    """GCM nonce manager with per-client state for replay protection
    
    Each client is identified by their (IP, port) address tuple.
    This prevents nonce collisions between multiple clients.
    """
    def __init__(self, window: int = 10000, client_timeout: int = 600):
        """
        Args:
            window: Sliding window size for replay protection (default: 10000)
            client_timeout: Seconds of inactivity before client state is cleaned up (default: 600 = 10 minutes)
        """
        self._lock = threading.Lock()
        self._clients: Dict[Tuple[str, int], ClientNonceState] = {}
        self._window = window
        self._client_timeout = client_timeout
    
    def validate_nonce(self, nonce: bytes, expected_sender: int, client_addr: Tuple[str, int] = None) -> bool:
        """Validate a 12-byte GCM nonce with per-client state.
        
        Args:
            nonce: 12-byte nonce (format: [sender_id:1][reserved:3][counter:8])
            expected_sender: Expected sender ID (0x00 for client, 0x01 for server)
            client_addr: Optional (IP, port) tuple to identify client. If None, uses global state (legacy behavior)
        
        Returns:
            True if valid (not replayed), False otherwise.
        """
        if len(nonce) != 12:
            return False

        if nonce[0] != expected_sender:
            logger.warning(f"Invalid nonce sender id: {nonce[0]:#02x} expected {expected_sender:#02x}")
            return False

        counter = int.from_bytes(nonce[4:12], 'big')

        with self._lock:
            # Get or create client-specific state
            if client_addr is None:
                # Legacy mode: use single global state (key = None)
                client_addr = ("global", 0)
            
            if client_addr not in self._clients:
                self._clients[client_addr] = ClientNonceState(window=self._window)
                logger.debug(f"Created nonce state for new client {client_addr}")
            
            client_state = self._clients[client_addr]
            
            # Check if counter is too old (outside sliding window)
            if client_state.highest and counter <= (client_state.highest - client_state.window):
                logger.warning(f"Stale nonce from {client_addr}: counter {counter} (highest: {client_state.highest})")
                return False

            # Check for replay attack
            if counter in client_state.seen:
                logger.warning(f"Replay attack detected from {client_addr}! Nonce counter: {counter}")
                return False

            # Accept and record nonce
            client_state.seen.add(counter)
            if counter > client_state.highest:
                client_state.highest = counter
            
            # Update activity timestamp
            client_state.update_activity()

            # Cleanup old counters within this client's state
            min_allowed = max(0, client_state.highest - client_state.window)
            to_remove = [n for n in client_state.seen if n < min_allowed]
            for n in to_remove:
                client_state.seen.remove(n)

        return True
    
    def cleanup_inactive_clients(self):
        """Remove clients that have been inactive for longer than client_timeout
        
        This prevents memory leaks when clients disconnect without cleanup.
        Should be called periodically.
        """
        with self._lock:
            now = time.time()
            inactive_clients = []
            
            for addr, state in self._clients.items():
                if now - state.last_activity > self._client_timeout:
                    inactive_clients.append(addr)
            
            for addr in inactive_clients:
                del self._clients[addr]
                logger.debug(f"Cleaned up inactive client {addr}")
            
            if inactive_clients:
                logger.info(f"Removed {len(inactive_clients)} inactive client(s) from nonce manager")
    
    def get_client_count(self) -> int:
        """Get number of tracked clients"""
        with self._lock:
            return len(self._clients)
    
    def reset_client(self, client_addr: Tuple[str, int]):
        """Reset nonce state for a specific client
        
        Useful when a client reconnects or resets their counter.
        """
        with self._lock:
            if client_addr in self._clients:
                del self._clients[client_addr]
                logger.info(f"Reset nonce state for client {client_addr}")


# Default global manager instance
default_gcm_nonce_manager = GcmNonceManager()