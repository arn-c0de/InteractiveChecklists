#!/usr/bin/env python3
"""
crypto_handshake.py
ECDH handshake session manager for secure DataPad communication

This module handles:
- ECDH key exchange with Android clients
- Session key derivation using HKDF-SHA256
- Device authorization via whitelist
- Session management and lifecycle

Requires: pip install cryptography
"""

import json
import os
import time
import uuid
import logging
from typing import Dict, Optional, Tuple
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.backends import default_backend
import hmac as hmac_lib

logger = logging.getLogger(__name__)


class SessionData:
    """Information about an active session"""
    def __init__(self, session_id: str, device_id: str, session_key: bytes,
                 peer_public_key: bytes, aircraft: Optional[str] = None):
        self.session_id = session_id
        self.device_id = device_id
        self.session_key = session_key
        self.peer_public_key = peer_public_key
        self.aircraft = aircraft
        self.created_at = time.time()
        self.last_activity = time.time()
        # Counter for nonce generation (thread-safe)
        self._nonce_counter = 0
        self._nonce_lock = __import__('threading').Lock()
        self._received_nonces = set()

    def is_expired(self, timeout_seconds: int = 900) -> bool:
        """Check if session has expired (default 15 minutes - shorter for security)"""
        return (time.time() - self.last_activity) > timeout_seconds

    def update_activity(self):
        """Update last activity timestamp"""
        self.last_activity = time.time()

    def generate_nonce(self) -> bytes:
        """Generate counter-based nonce for this session (SERVER side, 0x01 prefix)"""
        with self._nonce_lock:
            self._nonce_counter += 1
            if self._nonce_counter >= 2**64:
                raise RuntimeError(f"Nonce counter exhausted for session {self.session_id} - re-key required!")
            # Format: 0x01 (server) + 3 bytes reserved + 8 bytes counter
            return bytes([0x01, 0x00, 0x00, 0x00]) + self._nonce_counter.to_bytes(8, 'big')

    def validate_nonce(self, nonce: bytes, expected_sender: int = 0x00) -> bool:
        """Validate received nonce to prevent replay attacks and check sender id.

        Nonce format: [sender_id:1][reserved:3][counter:8]
        For messages coming from the client to the server, sender_id MUST be 0x00.
        """
        if len(nonce) != 12:
            return False

        # Validate sender prefix to prevent messages from the wrong origin
        if nonce[0] != expected_sender:
            logger.warning(f"⚠️ Invalid nonce sender id: {nonce[0]:#02x} - expected {expected_sender:#02x} for session {self.session_id[:8]}...")
            return False

        # Extract counter (bytes 4-11)
        counter = int.from_bytes(nonce[4:12], 'big')

        with self._nonce_lock:
            if counter in self._received_nonces:
                logger.warning(f"⚠️ Replay attack detected in session {self.session_id[:8]}... ! Nonce counter: {counter}")
                return False
            self._received_nonces.add(counter)
            # Cleanup old nonces
            if len(self._received_nonces) > 10000:
                old_nonces = sorted(self._received_nonces)[:5000]
                self._received_nonces.difference_update(old_nonces)
        return True


class AuthorizedDevice:
    """Represents an authorized device from whitelist"""
    def __init__(self, device_id: str, name: str, public_key: str, 
                 permissions: list, added_date: str):
        self.device_id = device_id
        self.name = name
        self.public_key = public_key
        self.permissions = permissions
        self.added_date = added_date


class SessionManager:
    """Manages ECDH handshake and session lifecycle"""

    def __init__(self, authorized_devices_path: str = "authorized_devices.json",
                 aircraft_name: Optional[str] = None):
        self.sessions: Dict[str, SessionData] = {}  # session_id -> SessionData
        self.device_sessions: Dict[str, str] = {}  # device_id -> session_id (latest)
        self.authorized_devices: Dict[str, AuthorizedDevice] = {}
        self.authorized_devices_path = Path(authorized_devices_path)
        self.aircraft_name = aircraft_name
        self.last_cleanup = time.time()
        self._cleanup_thread = None
        self._stop_cleanup = False

        # Rate limiting: IP -> (attempt_count, window_start_time)
        self.handshake_attempts: Dict[str, Tuple[int, float]] = {}
        self.rate_limit_lock = __import__('threading').Lock()
        self.MAX_HANDSHAKE_ATTEMPTS = 5  # Max attempts per window
        self.RATE_LIMIT_WINDOW = 60.0  # Window in seconds (1 minute)
        
        # Generate server key pair (ephemeral, regenerated each run)
        self.server_private_key = ec.generate_private_key(ec.SECP256R1(), default_backend())
        self.server_public_key = self.server_private_key.public_key()
        
        logger.info("🔐 SessionManager initialized")
        logger.info(f"📂 Authorized devices file: {self.authorized_devices_path}")
        
        # Load authorized devices
        self.load_authorized_devices()
        
        # Start periodic cleanup thread
        self._start_cleanup_thread()
    
    def load_authorized_devices(self):
        """Load authorized devices from JSON file"""
        if not self.authorized_devices_path.exists():
            logger.warning(f"⚠️ No authorized devices file found at {self.authorized_devices_path}")
            logger.info("Creating empty authorized_devices.json template...")
            self._create_empty_whitelist()
            return
        
        try:
            with open(self.authorized_devices_path, 'r') as f:
                data = json.load(f)
            
            for device_data in data.get('devices', []):
                device = AuthorizedDevice(
                    device_id=device_data['deviceId'],
                    name=device_data['name'],
                    public_key=device_data['publicKey'],
                    permissions=device_data['permissions'],
                    added_date=device_data['addedDate']
                )
                self.authorized_devices[device.device_id] = device
            
            logger.info(f"✅ Loaded {len(self.authorized_devices)} authorized devices")
            for device in self.authorized_devices.values():
                logger.info(f"  - {device.name} ({device.device_id[:16]}...)")
        
        except Exception as e:
            logger.error(f"❌ Error loading authorized devices: {e}")
    
    def _create_empty_whitelist(self):
        """Create an empty whitelist template"""
        template = {
            "devices": [
                {
                    "deviceId": "example_device_id_32_chars_hex",
                    "name": "Example Device",
                    "publicKey": "base64_encoded_ec_public_key",
                    "permissions": ["receive", "send_commands"],
                    "addedDate": "2024-01-15T10:30:00Z",
                    "_comment": "Delete this example entry and add real devices"
                }
            ],
            "_instructions": {
                "howToAdd": "Run your Android app with ECDH enabled, check logs for Device ID, then add entry here",
                "permissions": ["receive: can receive flight data", "send_commands: can send commands to DCS"],
                "reloadHotkey": "The script reloads this file automatically when changed"
            }
        }
        
        try:
            with open(self.authorized_devices_path, 'w') as f:
                json.dump(template, f, indent=2)
            logger.info(f"📝 Created {self.authorized_devices_path} template")
        except Exception as e:
            logger.error(f"❌ Could not create template: {e}")
    
    def is_device_authorized(self, device_id: str) -> bool:
        """Check if device is in whitelist"""
        # Reload devices file to allow hot-reload
        self.load_authorized_devices()
        return device_id in self.authorized_devices
    
    def is_rate_limited(self, ip_address: str) -> bool:
        """Check if IP address is rate limited for handshake attempts"""
        with self.rate_limit_lock:
            current_time = time.time()

            if ip_address in self.handshake_attempts:
                count, window_start = self.handshake_attempts[ip_address]

                # Check if window has expired
                if current_time - window_start > self.RATE_LIMIT_WINDOW:
                    # Reset window
                    self.handshake_attempts[ip_address] = (1, current_time)
                    return False

                # Within window - check count
                if count >= self.MAX_HANDSHAKE_ATTEMPTS:
                    logger.warning(f"⚠️ Rate limit exceeded for {ip_address} ({count} attempts in {current_time - window_start:.1f}s)")
                    return True

                # Increment count
                self.handshake_attempts[ip_address] = (count + 1, window_start)
                return False
            else:
                # First attempt from this IP
                self.handshake_attempts[ip_address] = (1, current_time)
                return False

    def cleanup_rate_limits(self):
        """Remove expired rate limit entries (prevent memory leak)"""
        with self.rate_limit_lock:
            current_time = time.time()
            expired = [
                ip for ip, (_, window_start) in self.handshake_attempts.items()
                if current_time - window_start > self.RATE_LIMIT_WINDOW * 2
            ]
            for ip in expired:
                del self.handshake_attempts[ip]
            if expired:
                logger.debug(f"🧹 Cleaned up {len(expired)} expired rate limit entries")

    def handle_client_hello(self, message: dict, sender_address: Tuple[str, int]) -> dict:
        """
        Handle ClientHello message from Android app
        Returns ServerHello response or error
        """
        try:
            device_id = message.get('deviceId', '')
            device_name = message.get('deviceName', 'Unknown')
            client_public_key_b64 = message.get('publicKey', '')
            client_timestamp = message.get('timestamp', 0)
            ip_address = sender_address[0]

            logger.info(f"📥 ClientHello from {device_name} ({device_id[:16]}...) @ {ip_address}")

            # SECURITY: Rate limiting (prevent DoS attacks)
            if self.is_rate_limited(ip_address):
                logger.warning(f"🚫 Rate limit exceeded for {ip_address} - rejecting handshake")
                return {
                    "type": "Error",
                    "error": "RateLimitExceeded",
                    "message": f"Too many handshake attempts. Please wait {self.RATE_LIMIT_WINDOW}s and try again.",
                    "timestamp": int(time.time() * 1000)
                }

            # SECURITY: Validate timestamp (prevent replay of old messages)
            server_time = time.time() * 1000  # milliseconds
            time_diff = abs(server_time - client_timestamp)
            MAX_TIMESTAMP_DRIFT_MS = 300000  # 5 minutes

            if time_diff > MAX_TIMESTAMP_DRIFT_MS:
                logger.warning(f"❌ Timestamp too old/future: {time_diff/1000:.1f}s drift (max {MAX_TIMESTAMP_DRIFT_MS/1000}s)")
                return {
                    "type": "Error",
                    "error": "InvalidTimestamp",
                    "message": f"Timestamp drift too large: {time_diff/1000:.1f}s (max {MAX_TIMESTAMP_DRIFT_MS/1000}s). Check device clock.",
                    "timestamp": int(server_time)
                }

            # Check authorization
            if not self.is_device_authorized(device_id):
                logger.warning(f"❌ Unauthorized device: {device_id[:16]}... ({device_name})")
                logger.warning(f"   Add to {self.authorized_devices_path} to authorize")
                return {
                    "type": "Error",
                    "error": "Unauthorized",
                    "message": f"Device {device_id[:16]}... is not authorized. Add to authorized_devices.json on server.",
                    "timestamp": int(server_time)
                }

            logger.info(f"✅ Device authorized: {self.authorized_devices[device_id].name}")

            # SECURITY: Verify that the provided client public key matches the whitelist entry for this device
            authorized_device = self.authorized_devices.get(device_id)
            if authorized_device and authorized_device.public_key:
                if client_public_key_b64 != authorized_device.public_key:
                    logger.warning(f"❌ Client public key mismatch for device {device_id[:8]}... - potential impersonation attempt")
                    return {
                        "type": "Error",
                        "error": "InvalidClientPublicKey",
                        "message": "Client public key does not match authorized device entry",
                        "timestamp": int(server_time)
                    }

            # Parse client public key
            client_public_key = serialization.load_der_public_key(
                self._base64_decode(client_public_key_b64),
                backend=default_backend()
            )
            
            # Derive shared secret using ECDH
            shared_secret = self.server_private_key.exchange(
                ec.ECDH(), client_public_key
            )

            # Generate random salt for HKDF (32 bytes)
            salt = os.urandom(32)
            logger.info(f"🔑 Generated random salt for HKDF ({len(salt)} bytes)")

            # Derive session key using HKDF-SHA256 with random salt
            session_key = self._derive_session_key(shared_secret, salt)
            
            # Create session
            session_id = str(uuid.uuid4())
            session = SessionData(
                session_id=session_id,
                device_id=device_id,
                session_key=session_key,
                peer_public_key=client_public_key_b64.encode(),
                aircraft=self.aircraft_name
            )
            
            # Remove old session for this device (if exists)
            old_session_id = self.device_sessions.get(device_id)
            if old_session_id and old_session_id in self.sessions:
                logger.info(f"🗑️ Replacing old session {old_session_id[:8]}... for device {device_id[:16]}...")
                del self.sessions[old_session_id]
            
            self.sessions[session_id] = session
            self.device_sessions[device_id] = session_id
            
            logger.info(f"🔑 Session created: {session_id[:8]}... (key derived from ECDH)")
            
            # Export server public key
            server_public_key_b64 = self._base64_encode(
                self.server_public_key.public_bytes(
                    encoding=serialization.Encoding.DER,
                    format=serialization.PublicFormat.SubjectPublicKeyInfo
                )
            )
            
            # SECURITY: Add server HMAC for mutual authentication
            server_hmac = hmac_lib.new(
                session_key,
                f"server_{session_id}".encode(),
                'sha256'
            ).digest()
            
            return {
                "type": "ServerHello",
                "version": "1.0",
                "sessionId": session_id,
                "publicKey": server_public_key_b64,
                "timestamp": int(time.time() * 1000),
                "authorized": True,
                "aircraft": self.aircraft_name,
                "serverHmac": self._base64_encode(server_hmac),
                "salt": self._base64_encode(salt)  # Send random salt to client
            }
        
        except Exception as e:
            logger.error(f"❌ Error handling ClientHello: {e}", exc_info=True)
            return {
                "type": "Error",
                "error": "HandshakeFailed",
                "message": str(e),
                "timestamp": int(time.time() * 1000)
            }
    
    def handle_key_confirm(self, message: dict) -> dict:
        """
        Handle KeyConfirm message from Android app
        Verifies HMAC and returns Ack
        """
        try:
            session_id = message.get('sessionId', '')
            client_hmac_b64 = message.get('hmac', '')
            client_timestamp = message.get('timestamp', 0)

            # SECURITY: Validate timestamp
            server_time = time.time() * 1000
            time_diff = abs(server_time - client_timestamp)
            MAX_TIMESTAMP_DRIFT_MS = 300000  # 5 minutes

            if time_diff > MAX_TIMESTAMP_DRIFT_MS:
                logger.warning(f"❌ KeyConfirm timestamp too old/future: {time_diff/1000:.1f}s drift")
                return {
                    "type": "Error",
                    "error": "InvalidTimestamp",
                    "message": f"Timestamp drift too large: {time_diff/1000:.1f}s",
                    "timestamp": int(server_time)
                }

            # Find session
            session = self.sessions.get(session_id)
            if not session:
                logger.error(f"❌ Unknown session: {session_id[:8]}...")
                return {
                    "type": "Error",
                    "error": "InvalidSession",
                    "message": "Session not found or expired",
                    "timestamp": int(server_time)
                }

            # Verify HMAC
            expected_hmac = hmac_lib.new(
                session.session_key,
                session_id.encode(),
                'sha256'
            ).digest()
            
            client_hmac = self._base64_decode(client_hmac_b64)
            
            if not hmac_lib.compare_digest(expected_hmac, client_hmac):
                logger.error(f"❌ HMAC verification failed for session {session_id[:8]}...")
                return {
                    "type": "Error",
                    "error": "HMACVerificationFailed",
                    "message": "Key confirmation failed",
                    "timestamp": int(time.time() * 1000)
                }
            
            logger.info(f"✅ HMAC verified for session {session_id[:8]}...")
            session.update_activity()
            
            return {
                "type": "Ack",
                "sessionId": session_id,
                "status": "ready",
                "timestamp": int(time.time() * 1000),
                "message": "Handshake complete, session established"
            }
        
        except Exception as e:
            logger.error(f"❌ Error handling KeyConfirm: {e}", exc_info=True)
            return {
                "type": "Error",
                "error": "KeyConfirmFailed",
                "message": str(e),
                "timestamp": int(time.time() * 1000)
            }
    
    def get_session_by_id(self, session_id: str) -> Optional[SessionData]:
        """Get session by ID, returns None if not found or expired"""
        session = self.sessions.get(session_id)
        if session and not session.is_expired():
            session.update_activity()
            return session
        elif session:
            # Clean up expired session
            del self.sessions[session_id]
            logger.info(f"🗑️ Removed expired session: {session_id[:8]}...")
        return None
    
    def encrypt_with_session(self, data: bytes, session_id: str) -> Optional[bytes]:
        """Encrypt data with session key using counter-based nonce"""
        session = self.get_session_by_id(session_id)
        if not session:
            return None

        aesgcm = AESGCM(session.session_key)
        nonce = session.generate_nonce()  # Counter-based, no collision possible
        ciphertext = aesgcm.encrypt(nonce, data, None)
        return nonce + ciphertext

    def decrypt_with_session(self, encrypted_data: bytes, session_id: str) -> Optional[bytes]:
        """Decrypt data with session key and validate nonce"""
        session = self.get_session_by_id(session_id)
        if not session:
            return None

        if len(encrypted_data) < 28:  # 12 (nonce) + 16 (tag)
            return None

        nonce = encrypted_data[:12]
        ciphertext = encrypted_data[12:]

        # SECURITY: Validate nonce to prevent replay attacks
        if not session.validate_nonce(nonce):
            logger.error(f"❌ Replay attack detected - rejecting message for session {session_id[:8]}...")
            return None

        aesgcm = AESGCM(session.session_key)
        try:
            return aesgcm.decrypt(nonce, ciphertext, None)
        except Exception as e:
            logger.error(f"❌ Decryption failed: {e}")
            return None
    
    def cleanup_expired_sessions(self, max_age_seconds: int = 3600):
        """Remove expired sessions and rate limit entries"""
        expired = [
            sid for sid, session in self.sessions.items()
            if session.is_expired(max_age_seconds)
        ]
        for sid in expired:
            session = self.sessions[sid]
            # Remove from device mapping
            if session.device_id in self.device_sessions:
                if self.device_sessions[session.device_id] == sid:
                    del self.device_sessions[session.device_id]
            del self.sessions[sid]
            logger.info(f"🗑️ Cleaned up expired session: {sid[:8]}...")

        # Also cleanup rate limits
        self.cleanup_rate_limits()

        self.last_cleanup = time.time()
        if expired:
            logger.info(f"📊 Active sessions: {len(self.sessions)}, Devices: {len(self.device_sessions)}")
    
    def _start_cleanup_thread(self):
        """Start background thread for periodic session cleanup"""
        import threading
        
        def cleanup_worker():
            logger.info("🧹 Session cleanup thread started (runs every 60 seconds)")
            while not self._stop_cleanup:
                time.sleep(60)  # Run every 60 seconds
                if self._stop_cleanup:
                    break
                try:
                    self.cleanup_expired_sessions()
                except Exception as e:
                    logger.error(f"❌ Error in cleanup thread: {e}")
        
        self._cleanup_thread = threading.Thread(target=cleanup_worker, daemon=True)
        self._cleanup_thread.start()
    
    def stop_cleanup_thread(self):
        """Stop the cleanup thread (call when shutting down)"""
        self._stop_cleanup = True
        if self._cleanup_thread:
            self._cleanup_thread.join(timeout=2.0)
            logger.info("🧹 Session cleanup thread stopped")
    
    def _derive_session_key(self, shared_secret: bytes, salt: bytes) -> bytes:
        """Derive 256-bit AES key from ECDH shared secret using HKDF with random salt"""
        hkdf = HKDF(
            algorithm=hashes.SHA256(),
            length=32,  # 256 bits
            salt=salt,  # Use provided random salt (32 bytes)
            info=b'DataPad-Session-Key',
            backend=default_backend()
        )
        return hkdf.derive(shared_secret)
    
    @staticmethod
    def _base64_encode(data: bytes) -> str:
        """Base64 encode bytes to string"""
        import base64
        return base64.b64encode(data).decode('ascii')
    
    @staticmethod
    def _base64_decode(data: str) -> bytes:
        """Base64 decode string to bytes"""
        import base64
        return base64.b64decode(data)


if __name__ == '__main__':
    # Test/demo
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s [%(levelname)s] %(message)s'
    )
    
    print("🔐 ECDH SessionManager Test")
    print("=" * 50)
    
    manager = SessionManager(aircraft_name="F/A-18C_hornet")
    
    # Simulate ClientHello
    test_client_hello = {
        "type": "ClientHello",
        "deviceId": "test_device_id_1234567890abcdef",
        "deviceName": "Test Tablet",
        "publicKey": "base64_test_key",
        "timestamp": int(time.time() * 1000)
    }
    
    print(f"\n📊 Active sessions: {len(manager.sessions)}")
    print(f"📊 Authorized devices: {len(manager.authorized_devices)}")

