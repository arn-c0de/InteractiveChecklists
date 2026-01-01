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
import hashlib
from typing import Dict, Optional, Tuple
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.backends import default_backend
import hmac as hmac_lib
import subprocess

logger = logging.getLogger(__name__)


class SecurityAuditLogger:
    """Logs security events in structured JSON format for analysis"""

    def __init__(self, audit_file: str = "security_audit.jsonl"):
        self.audit_file = Path(audit_file)
        self._lock = __import__('threading').Lock()
        # Encryption fallback: if secure permissions cannot be enforced, logs
        # will be encrypted using a key from AUDIT_LOG_KEY environment variable
        # (base64). These are set by _ensure_secure_permissions().
        self._encryption_enabled = False
        self._audit_key = None
        self._ensure_secure_permissions()

    def _try_apply_windows_acl(self, user_identifier: str) -> bool:
        """Attempt to set ACLs via icacls for the provided user identifier.
        Returns True if resulting ACL appears restrictive.
        """
        try:
            # Remove inheritance then grant read/write to specific user only
            cmds = [
                ['icacls', str(self.audit_file), '/inheritance:r'],
                ['icacls', str(self.audit_file), '/grant:r', f'"{user_identifier}:(R,W)"']
            ]
            for cmd in cmds:
                subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

            # Verify ACL contents - ensure common broad principals are not present
            out = subprocess.run(['icacls', str(self.audit_file)], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            acl_text = out.stdout.decode('utf-8', errors='ignore')
            insecure_principals = ['Everyone', 'Users', 'Authenticated Users']
            for p in insecure_principals:
                if p in acl_text:
                    logger.debug(f"Windows ACL still contains '{p}': {acl_text}")
                    return False
            return True
        except subprocess.CalledProcessError as e:
            logger.debug(f"icacls command failed: {e} - output: {getattr(e, 'output', None)}")
            return False
        except Exception as e:
            logger.debug(f"Unexpected error applying icacls: {e}")
            return False

    @staticmethod
    def _parse_audit_key(key_b64: str) -> Optional[bytes]:
        """Decode and validate base64 AUDIT_LOG_KEY. Return raw bytes or None if invalid."""
        import base64
        try:
            key = base64.b64decode(key_b64)
            if len(key) not in (16, 24, 32):
                logger.error(f"❌ AUDIT_LOG_KEY has invalid length: {len(key)} bytes (expected 32 preferred)")
                return None
            if len(key) != 32:
                logger.warning("⚠️ AUDIT_LOG_KEY is not 32 bytes; consider using a 32-byte key for AES-256")
            return key
        except Exception as e:
            logger.error(f"❌ Failed to decode AUDIT_LOG_KEY: {e}")
            return None

    def _ensure_secure_permissions(self):
        """Ensure audit file exists and has secure permissions.

        Behavior (strict/fail-safe):
        1. Try POSIX-style 0o600 permissions.
        2. If that fails on Windows, attempt a robust ACL fix using `icacls` (best-effort),
           trying several username formats and verifying the resulting ACL.
        3. If permissions cannot be made secure, require a valid encryption key via
           the `AUDIT_LOG_KEY` environment variable (base64, 32 bytes preferred) and enable
           on-disk encryption for audit entries.
        4. If neither permissions nor a valid key are available, raise RuntimeError
           to abort startup (fail-safe).
        """
        import base64
        import getpass
        import stat

        # Use helper instance/static methods defined on the class:
        # - self._try_apply_windows_acl(user_identifier)
        # - self._parse_audit_key(key_b64)
        # These exist as dedicated methods to improve testability and separation of concerns.

        try:
            # Ensure parent directory exists
            self.audit_file.parent.mkdir(parents=True, exist_ok=True)

            # If file does not exist, create it with secure permissions if possible
            if not self.audit_file.exists():
                try:
                    fd = os.open(str(self.audit_file), os.O_CREAT | os.O_WRONLY, stat.S_IRUSR | stat.S_IWUSR)
                    os.close(fd)
                except Exception:
                    # Fallback to touch if os.open with mode fails (platform differences)
                    try:
                        self.audit_file.touch()
                    except Exception as e:
                        logger.error(f"❌ Failed to create audit file: {e}")
                        raise RuntimeError("Could not create audit log file")

            # Try to set POSIX permissions (0o600)
            try:
                self.audit_file.chmod(stat.S_IRUSR | stat.S_IWUSR)  # 0o600
                current_mode = self.audit_file.stat().st_mode & 0o777
                if current_mode == 0o600:
                    self._encryption_enabled = False
                    self._audit_key = None
                    return
            except Exception as posix_err:
                logger.debug(f"POSIX chmod failed: {posix_err}")

            # If on Windows, attempt to set ACLs using icacls (robust sequence)
            if os.name == 'nt':
                user = getpass.getuser()
                # First attempt: plain username
                if self._try_apply_windows_acl(user):
                    logger.info(f"✅ Applied Windows ACLs for audit file: {self.audit_file} (user: {user})")
                    self._encryption_enabled = False
                    self._audit_key = None
                    return

                # Second attempt: try full whoami (DOMAIN\\User)
                try:
                    whoami = subprocess.run(['whoami'], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
                    whoami_name = whoami.stdout.decode('utf-8', errors='ignore').strip()
                    if whoami_name and self._try_apply_windows_acl(whoami_name):
                        logger.info(f"✅ Applied Windows ACLs for audit file: {self.audit_file} (whoami: {whoami_name})")
                        self._encryption_enabled = False
                        self._audit_key = None
                        return
                except Exception:
                    pass

                # As a best-effort fallback, try granting to Administrators and current user
                try:
                    admin_cmds = [
                        ['icacls', str(self.audit_file), '/grant:r', 'Administrators:(R,W)'],
                        ['icacls', str(self.audit_file), '/grant:r', f'"{user}:(R,W)"']
                    ]
                    for cmd in admin_cmds:
                        subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                    out = subprocess.run(['icacls', str(self.audit_file)], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                    if 'Everyone' not in out.stdout.decode('utf-8', errors='ignore'):
                        logger.info(f"✅ Applied relaxed Windows ACLs for audit file: {self.audit_file}")
                        self._encryption_enabled = False
                        self._audit_key = None
                        return
                except Exception as e:
                    logger.debug(f"Windows ACL attempts failed: {e}")

            # If we reached here, permissions were not made secure — attempt encryption fallback
            key_b64 = os.environ.get('AUDIT_LOG_KEY') or os.environ.get('AUDIT_LOG_ENCRYPT_KEY')
            if key_b64:
                key = self._parse_audit_key(key_b64)
                if key:
                    # Enable encryption for audit logs
                    self._audit_key = key
                    self._encryption_enabled = True
                    logger.warning("⚠️ Audit log permissions could not be enforced; encrypting audit log using AUDIT_LOG_KEY")
                    return

            # Nothing worked — instead of aborting, generate a random key and persist it if possible.
            # This provides an elegant fallback: audit entries remain encrypted even when ACLs cannot be fixed.
            try:
                generated_key = os.urandom(32)
                key_file = self.audit_file.with_suffix('.key')

                # Attempt to persist key atomically with restrictive permissions
                try:
                    import tempfile
                    tmp = key_file.with_suffix('.tmp')
                    # Write via os.open with 0o600 mode to ensure restrictive permissions
                    fd = os.open(str(tmp), os.O_WRONLY | os.O_CREAT | os.O_TRUNC, stat.S_IRUSR | stat.S_IWUSR)
                    with os.fdopen(fd, 'wb') as f:
                        f.write(generated_key)
                    # Replace atomically
                    os.replace(str(tmp), str(key_file))

                    # Try to set POSIX permissions on key file
                    try:
                        key_file.chmod(stat.S_IRUSR | stat.S_IWUSR)
                    except Exception:
                        logger.debug("Could not chmod key file; will attempt Windows ACLs if available")

                    # On Windows, try to restrict key file ACLs as well
                    if os.name == 'nt':
                        try:
                            user = __import__('getpass').getuser()
                            if self._try_apply_windows_acl(user) or self._try_apply_windows_acl(__import__('subprocess').run(['whoami'], stdout=__import__('subprocess').PIPE, check=True).stdout.decode().strip()):
                                logger.info(f"✅ Persisted audit key to {key_file} with restrictive ACLs")
                            else:
                                logger.warning(f"⚠️ Persisted audit key to {key_file} but could not enforce ACLs; please secure this file manually")
                        except Exception:
                            logger.warning(f"⚠️ Persisted audit key to {key_file} but failed to verify ACLs")

                    # Use persisted key
                    self._audit_key = generated_key
                    self._encryption_enabled = True
                    logger.warning("⚠️ Audit log permissions could not be enforced; generated and persisted an audit key")
                    return

                except Exception as persist_err:
                    logger.warning(f"⚠️ Could not persist generated audit key: {persist_err}; falling back to ephemeral in-memory key")
                    # Use ephemeral key in memory (won't survive restart) but keeps logs encrypted
                    self._audit_key = generated_key
                    self._encryption_enabled = True
                    logger.critical("⚠️ AUDIT LOG: using ephemeral audit key (not persisted). Set AUDIT_LOG_KEY to a stable Base64 key to persist across restarts")
                    return
            except Exception as e:
                logger.critical(f"🚨 SECURITY: Could not secure audit log file and failed to generate fallback key: {e}")
                raise RuntimeError("Insecure audit log configuration: secure permissions not set and no valid audit key available")
        except Exception as e:
            # Bubble up RuntimeError for caller to stop startup, log others
            if isinstance(e, RuntimeError):
                raise
            logger.error(f"❌ Unexpected error ensuring audit file permissions: {e}")
            raise RuntimeError("Failed to ensure audit log security")

    def log_event(self, event_type: str, severity: str, details: dict):
        """Log a security event in JSON format

        Args:
            event_type: Type of event (e.g., 'auth_failure', 'rate_limit', 'blacklist')
            severity: Severity level ('low', 'medium', 'high', 'critical')
            details: Additional details about the event
        """
        event = {
            'timestamp': time.time(),
            'timestamp_iso': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
            'event_type': event_type,
            'severity': severity,
            **details
        }

        with self._lock:
            try:
                # Ensure file exists with secure permissions or create it
                if not self.audit_file.exists():
                    try:
                        # Try to create with 0600 mode
                        import stat
                        fd = os.open(str(self.audit_file), os.O_CREAT | os.O_WRONLY, stat.S_IRUSR | stat.S_IWUSR)
                        os.close(fd)
                    except Exception:
                        # Best-effort fallback
                        self.audit_file.touch()

                # Prepare payload
                line = None
                if getattr(self, '_encryption_enabled', False) and self._audit_key is not None:
                    try:
                        # Encrypt JSON payload using AES-GCM
                        plaintext = json.dumps(event).encode('utf-8')
                        aesgcm = AESGCM(self._audit_key)
                        nonce = os.urandom(12)
                        ct = aesgcm.encrypt(nonce, plaintext, None)
                        import base64
                        payload_b64 = base64.b64encode(nonce + ct).decode('ascii')
                        line = json.dumps({'encrypted': True, 'payload': payload_b64}) + '\n'
                    except Exception as e:
                        logger.error(f"❌ Failed to encrypt audit event: {e}")
                        # As a last resort, fail-safe: do not write plaintext sensitive events
                        return
                else:
                    line = json.dumps(event) + '\n'

                # Append the event (encrypted or plaintext)
                with open(self.audit_file, 'a', encoding='utf-8') as f:
                    f.write(line)

                # Verify permissions after write (best-effort)
                try:
                    secure = True
                    # POSIX-style mode check where meaningful
                    try:
                        current_mode = self.audit_file.stat().st_mode & 0o777
                        if current_mode != 0o600:
                            secure = False
                    except Exception:
                        secure = False

                    # On Windows, use icacls to verify ACLs if available
                    if not secure and os.name == 'nt':
                        try:
                            out = subprocess.run(['icacls', str(self.audit_file)], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                            acl_text = out.stdout.decode('utf-8', errors='ignore')
                            if any(p in acl_text for p in ['Everyone', 'Users', 'Authenticated Users']):
                                secure = False
                            else:
                                secure = True
                        except Exception:
                            secure = False

                    if not secure and not getattr(self, '_encryption_enabled', False):
                        # If an encryption key is available, shift to encrypted append mode immediately
                        key_b64 = os.environ.get('AUDIT_LOG_KEY') or os.environ.get('AUDIT_LOG_ENCRYPT_KEY')
                        if key_b64:
                            key = None
                            try:
                                import base64
                                key = base64.b64decode(key_b64)
                            except Exception:
                                key = None
                            if key and len(key) in (16, 24, 32):
                                self._audit_key = key
                                self._encryption_enabled = True
                                logger.warning("⚠️ Audit file permissions found insecure at write time; switching to encrypted audit log using provided key")
                                # Avoid having written sensitive plaintext; we do NOT attempt to rewrite prior plaintext entries
                            else:
                                # No valid key — log critical problem for operator attention
                                try:
                                    current_mode = self.audit_file.stat().st_mode & 0o777
                                    logger.error(f"❌ Audit log has insecure permissions: {oct(current_mode)}")
                                except Exception:
                                    logger.error("❌ Audit log has insecure permissions and no valid AUDIT_LOG_KEY provided")
                except Exception as e:
                    logger.debug(f"Could not verify audit file permissions: {e}")
            except Exception as e:
                logger.error(f"❌ Failed to write security audit log: {e}")


# Global security audit logger
_audit_logger = None


def get_audit_logger() -> SecurityAuditLogger:
    """Get or create the global security audit logger"""
    global _audit_logger
    if _audit_logger is None:
        _audit_logger = SecurityAuditLogger()
    return _audit_logger


class SessionData:
    """Information about an active session"""
    def __init__(self, session_id: str, device_id: str, session_key: bytes,
                 peer_public_key: bytes, aircraft: Optional[str] = None,
                 entity_tracking_enabled: bool = False):
        self.session_id = session_id
        self.device_id = device_id
        self.session_key = session_key
        self.peer_public_key = peer_public_key
        self.aircraft = aircraft
        self.entity_tracking_enabled = entity_tracking_enabled  # Whether client wants tactical unit data
        self.created_at = time.time()
        self.last_activity = time.time()
        # Whether the client completed KeyConfirm for this session
        self.confirmed = False
        self.confirmed_at = None
        # Counter for nonce generation (thread-safe)
        self._nonce_counter = 0
        self._nonce_lock = __import__('threading').Lock()
        # Improved nonce replay tracking: map counter -> timestamp
        # Use a large sliding window and time-based eviction to avoid replay.
        self._received_nonces = {}  # type: Dict[int, float]
        # Configurable window parameters (sized for security/performance tradeoff)
        self._NONCE_MAX_ENTRIES = 100_000  # Sliding window capacity (recommended)
        self._NONCE_RETENTION_SECONDS = 3600.0  # Evict nonces older than 1 hour
        self._nonce_counter_min = 0  # Track minimum accepted counter
        # Session validity / rekey signaling
        self._needs_rekey = False
        self._is_valid = True
        # Track last received counter for monotonicity check
        self._last_received_counter = 0

    def is_expired(self, timeout_seconds: int = 900) -> bool:
        """Check if session has expired (default 15 minutes - shorter for security)"""
        return (time.time() - self.last_activity) > timeout_seconds

    def update_activity(self):
        """Update last activity timestamp"""
        self.last_activity = time.time()

    def generate_nonce(self) -> bytes:
        """Generate counter-based nonce for this session (SERVER side, 0x01 prefix) with exhaustion protection and rekey warning.

        SECURITY: All counter operations are atomic under lock to prevent race conditions.
        """
        with self._nonce_lock:
            # SECURITY: Check session validity FIRST, before any counter operations
            if not getattr(self, '_is_valid', True):
                raise RuntimeError("Session invalid - cannot generate nonce")

            # SECURITY: Check counter bounds BEFORE increment to prevent overflow
            if self._nonce_counter >= 2**64 - 1:  # Leave room for one more increment
                logger.critical(f"🚨 Nonce counter exhausted for session {self.session_id[:8]}!")
                self._is_valid = False
                raise RuntimeError("Nonce exhausted - session invalidated")

            # Atomic increment with overflow protection
            self._nonce_counter += 1

            # Trigger re-key at 75% capacity (warn early)
            if self._nonce_counter >= (2**64 * 0.75):
                logger.warning(f"⚠️ Nonce counter at 75% capacity for session {self.session_id[:8]}...")
                self._needs_rekey = True

            # Additional protection: if counter grows suspiciously high relative to received nonces
            # This could indicate abuse or counter manipulation
            received_count = len(self._received_nonces)
            if received_count > 0 and self._nonce_counter > received_count * 10:
                logger.warning(f"⚠️ Server nonce counter ({self._nonce_counter}) far exceeds received nonces ({received_count}) - possible abuse")
                # Don't invalidate immediately, but log for monitoring

            # Format: 0x01 (server) + 3 bytes reserved + 8 bytes counter
            return bytes([0x01, 0x00, 0x00, 0x00]) + self._nonce_counter.to_bytes(8, 'big')

    def validate_nonce(self, nonce: bytes, expected_sender: int = 0x00) -> bool:
        """Validate received nonce to prevent replay attacks and check sender id.

        SECURITY: All nonce validation operations are atomic under lock.
        Nonce format: [sender_id:1][reserved:3][counter:8]
        For messages coming from the client to the server, sender_id MUST be 0x00.
        """
        if len(nonce) != 12:
            return False

        # Validate sender prefix to prevent messages from the wrong origin
        if nonce[0] != expected_sender:
            logger.warning(f"⚠️ Invalid nonce sender id: {nonce[0]:#02x} - expected {expected_sender:#02x} for session {self.session_id[:8]}...")
            return False

        # Extract counter (bytes 4-11) - bounds check to prevent overflow
        try:
            counter = int.from_bytes(nonce[4:12], 'big')
            if counter >= 2**64:
                logger.warning(f"⚠️ Invalid nonce counter value: {counter}")
                return False
        except (ValueError, OverflowError):
            logger.warning("⚠️ Malformed nonce counter bytes")
            return False

        with self._nonce_lock:
            current_time = time.time()

            # SECURITY: Check session validity first
            if not getattr(self, '_is_valid', True):
                logger.warning(f"⚠️ Rejecting nonce for invalid session {self.session_id[:8]}...")
                return False

            # Reject if too old (sliding window lower bound)
            if counter < self._nonce_counter_min:
                logger.warning(f"⚠️ Nonce too old: {counter} < {self._nonce_counter_min}")
                return False

            # Check for duplicate (replay) - this is the critical check
            if counter in self._received_nonces:
                logger.warning(f"⚠️ Replay attack: {counter}")
                # SECURITY AUDIT: Log replay attack
                get_audit_logger().log_event('replay_attack_detected', 'critical', {
                    'session_id': self.session_id[:8] + '...',
                    'device_id': self.device_id[:16] + '...',
                    'nonce_counter': counter,
                    'reason': 'Duplicate nonce counter detected'
                })
                return False

            # SECURITY: Prevent nonce counter from going backwards (should be monotonically increasing)
            # This could indicate an attack or implementation bug
            if hasattr(self, '_last_received_counter') and counter < self._last_received_counter:
                logger.warning(f"⚠️ Nonce counter went backwards: {counter} < {self._last_received_counter}")
                return False

            # Accept and record timestamp
            self._received_nonces[counter] = current_time
            self._last_received_counter = counter

            # Evict old entries by age first (keeps window bounded for long-running sessions)
            if self._NONCE_RETENTION_SECONDS is not None:
                cutoff = current_time - self._NONCE_RETENTION_SECONDS
                old = [k for k, t in self._received_nonces.items() if t < cutoff]
                for k in old:
                    del self._received_nonces[k]

            # If we still exceed capacity, we consider this a potential abuse condition
            if len(self._received_nonces) > self._NONCE_MAX_ENTRIES:
                # SECURITY: Do not silently evict — invalidate session to force rekey
                logger.critical(f"🚨 Nonce replay window exceeded for session {self.session_id[:8]}: {len(self._received_nonces)} entries (max {self._NONCE_MAX_ENTRIES})")
                get_audit_logger().log_event('nonce_window_exceeded', 'high', {
                    'session_id': self.session_id[:8] + '...',
                    'device_id': self.device_id[:16] + '...',
                    'observed_count': len(self._received_nonces),
                    'nonce_window_max': self._NONCE_MAX_ENTRIES
                })
                # Invalidate session as fail-safe
                self._is_valid = False
                return False

            # Update minimum accepted counter for quick rejection
            if self._received_nonces:
                try:
                    self._nonce_counter_min = min(self._received_nonces)
                except (ValueError, TypeError):
                    # Fallback to conservative behavior
                    self._nonce_counter_min = 0

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


class ProofOfWorkChallenge:
    """
    Proof-of-Work challenge generator and verifier for DoS protection
    Requires client to find nonce such that SHA-256(challenge + nonce) has N leading zero bits
    """
    def __init__(self, difficulty: int = 16):
        """
        Initialize PoW challenge system
        
        Args:
            difficulty: Number of leading zero bits required (default 16 = ~65k hashes)
                       12 bits = ~4k hashes (~5-20ms)
                       16 bits = ~65k hashes (~50-100ms)
                       20 bits = ~1M hashes (~500-1000ms)
        """
        self.difficulty = difficulty
        self.challenges: Dict[str, Tuple[bytes, float]] = {}  # IP -> (challenge, timestamp)
        self._lock = __import__('threading').Lock()
        self.CHALLENGE_TIMEOUT = 300.0  # 5 minutes
        
    def generate_challenge(self, client_ip: str) -> str:
        """Generate a new PoW challenge for client"""
        challenge = os.urandom(32)  # 32 random bytes
        
        with self._lock:
            self.challenges[client_ip] = (challenge, time.time())
            # Cleanup old challenges
            self._cleanup_expired()
        
        import base64
        return base64.b64encode(challenge).decode('ascii')
    
    def verify_solution(self, client_ip: str, nonce: str) -> bool:
        """
        Verify client's PoW solution
        
        Args:
            client_ip: Client IP address
            nonce: Client's solution nonce (as string)
            
        Returns:
            True if solution is valid, False otherwise
        """
        with self._lock:
            if client_ip not in self.challenges:
                logger.warning(f"⚠️ PoW verification failed: no challenge for {client_ip}")
                return False
            
            challenge, timestamp = self.challenges[client_ip]
            
            # Check if challenge expired
            if time.time() - timestamp > self.CHALLENGE_TIMEOUT:
                logger.warning(f"⚠️ PoW verification failed: challenge expired for {client_ip}")
                del self.challenges[client_ip]
                return False
            
            # Verify solution
            try:
                digest = hashlib.sha256()
                digest.update(challenge)
                digest.update(nonce.encode('utf-8'))
                hash_result = digest.digest()
                
                # Count leading zero bits
                zero_bits = self._count_leading_zero_bits(hash_result)
                
                if zero_bits >= self.difficulty:
                    logger.info(f"✅ PoW verified for {client_ip} (difficulty: {self.difficulty}, found: {zero_bits})")
                    # Remove challenge after successful verification (one-time use)
                    del self.challenges[client_ip]
                    return True
                else:
                    logger.warning(f"⚠️ PoW verification failed: insufficient difficulty (need {self.difficulty}, got {zero_bits})")
                    return False
                    
            except Exception as e:
                logger.error(f"❌ PoW verification error: {e}")
                return False
    
    @staticmethod
    def _count_leading_zero_bits(data: bytes) -> int:
        """Count leading zero bits in byte array"""
        count = 0
        for byte in data:
            if byte == 0:
                count += 8
            else:
                # Count leading zeros in this byte
                b = byte
                while (b & 0x80) == 0:
                    count += 1
                    b = (b << 1) & 0xFF
                break
        return count
    
    def _cleanup_expired(self):
        """Remove expired challenges (called with lock held)"""
        current_time = time.time()
        expired = [ip for ip, (_, timestamp) in self.challenges.items() 
                   if current_time - timestamp > self.CHALLENGE_TIMEOUT]
        for ip in expired:
            del self.challenges[ip]


class SessionManager:
    """Manages ECDH handshake and session lifecycle"""

    def __init__(self, authorized_devices_path: str = "authorized_devices.json",
                 aircraft_name: Optional[str] = None,
                 enable_pow: bool = False,
                 pow_difficulty: int = 16):
        self.sessions: Dict[str, SessionData] = {}  # session_id -> SessionData
        self.device_sessions: Dict[str, str] = {}  # device_id -> session_id (latest)
        self.authorized_devices: Dict[str, AuthorizedDevice] = {}
        self.authorized_devices_path = Path(authorized_devices_path)
        self.aircraft_name = aircraft_name
        self.last_cleanup = time.time()
        self._cleanup_thread = None
        self._stop_cleanup = False

        # Proof-of-Work DoS protection (optional)
        self.enable_pow = enable_pow
        self.pow_challenge = ProofOfWorkChallenge(difficulty=pow_difficulty) if enable_pow else None
        if enable_pow:
            logger.info(f"🔐 Proof-of-Work enabled (difficulty: {pow_difficulty} bits)")

        # SECURITY: Whitelist file integrity tracking
        self._whitelist_hash: Optional[str] = None  # SHA-256 hash of loaded whitelist
        self._whitelist_mtime: Optional[float] = None  # Last modification time of whitelist file
        self._whitelist_load_time: Optional[float] = None  # When whitelist was last loaded
        self._whitelist_last_check: float = 0  # Last time integrity was checked
        self._whitelist_check_interval: float = 60.0  # Check integrity every 60 seconds (cooldown)
        self._whitelist_lock = __import__('threading').Lock()

        # Rate limiting: IP -> (attempt_count, window_start_time)
        self.handshake_attempts: Dict[str, Tuple[int, float, int]] = {}  # Now: (count, window_start, violations)
        # Device-ID based rate limiting: device_id -> (attempt_count, window_start_time, consecutive_violations)
        self.device_rate_limits: Dict[str, Tuple[int, float, int]] = {}
        # IP blacklist: IP -> (blacklist_until_timestamp, violation_count)
        self.ip_blacklist: Dict[str, Tuple[float, int]] = {}
        self.rate_limit_lock = __import__('threading').Lock()
        self.MAX_HANDSHAKE_ATTEMPTS = 5  # Max attempts per window
        self.RATE_LIMIT_WINDOW = 60.0  # Window in seconds (1 minute)
        self.MAX_DEVICE_ATTEMPTS = 3  # Max attempts per device per window (stricter)
        self.BLACKLIST_DURATION_BASE = 300.0  # 5 minutes base blacklist duration
        self.MAX_BLACKLIST_DURATION = 86400.0  # 24 hours max blacklist

        # Persistent IP blacklist file (stored next to authorized_devices file)
        self._blacklist_file = self.authorized_devices_path.with_name('ip_blacklist.json')

        # Generate server key pair (ephemeral, regenerated each run)
        self.server_private_key = ec.generate_private_key(ec.SECP256R1(), default_backend())
        self.server_public_key = self.server_private_key.public_key()

        logger.info("🔐 SessionManager initialized")
        logger.info(f"📂 Authorized devices file: {self.authorized_devices_path}")

        # Load authorized devices (SECURITY: Only loaded once at startup)
        self.load_authorized_devices()

        # Load persistent IP blacklist (if present)
        try:
            self._load_ip_blacklist()
        except Exception as e:
            logger.debug(f"Could not load IP blacklist: {e}")

        # Start periodic cleanup thread
        self._start_cleanup_thread()
    
    def load_authorized_devices(self):
        """Load authorized devices from JSON file with integrity tracking.

        SECURITY: This method should only be called once at startup or via explicit
        manual reload command. It does NOT automatically reload on every authorization check.
        """
        with self._whitelist_lock:
            try:
                # Read file content for hashing
                with open(self.authorized_devices_path, 'rb') as f:
                    file_content = f.read()

                # Compute SHA-256 hash for integrity verification
                file_hash = hashlib.sha256(file_content).hexdigest()

                # Get file modification time
                file_stat = os.stat(self.authorized_devices_path)
                file_mtime = file_stat.st_mtime

                # Parse JSON
                data = json.loads(file_content.decode('utf-8'))

                # Clear existing devices before loading
                self.authorized_devices.clear()

                for device_data in data.get('devices', []):
                    device = AuthorizedDevice(
                        device_id=device_data['deviceId'],
                        name=device_data['name'],
                        public_key=device_data['publicKey'],
                        permissions=device_data['permissions'],
                        added_date=device_data['addedDate']
                    )
                    self.authorized_devices[device.device_id] = device

                # Store integrity information
                self._whitelist_hash = file_hash
                self._whitelist_mtime = file_mtime
                self._whitelist_load_time = time.time()

                logger.info(f"✅ Loaded {len(self.authorized_devices)} authorized devices")
                logger.info(f"🔒 Whitelist file hash (SHA-256): {file_hash[:16]}...")
                for device in self.authorized_devices.values():
                    logger.info(f"  - {device.name} ({device.device_id[:16]}...)")

                # SECURITY AUDIT: Log whitelist load event
                get_audit_logger().log_event('whitelist_loaded', 'medium', {
                    'device_count': len(self.authorized_devices),
                    'file_hash': file_hash,
                    'file_mtime': file_mtime,
                    'load_time': self._whitelist_load_time
                })

            except FileNotFoundError:
                logger.warning(f"⚠️ No authorized devices file found at {self.authorized_devices_path}")
                logger.info("Creating empty authorized_devices.json template...")
                self._create_empty_whitelist()

            except json.JSONDecodeError as e:
                logger.error(f"❌ Invalid JSON in authorized devices file: {e}")

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
            # SECURITY: Create file with restricted permissions (owner read/write only)
            # On Windows, os.open with 0o600 may not work as expected, but we try anyway
            import stat
            fd = os.open(
                str(self.authorized_devices_path),
                os.O_CREAT | os.O_WRONLY | os.O_TRUNC,
                stat.S_IRUSR | stat.S_IWUSR  # 0o600: owner read/write only
            )
            with os.fdopen(fd, 'w') as f:
                json.dump(template, f, indent=2)
            logger.info(f"📝 Created {self.authorized_devices_path} template with secure permissions (0600)")
        except Exception as e:
            logger.error(f"❌ Could not create template: {e}")
    
    def is_device_authorized(self, device_id: str) -> bool:
        """Check if device is in whitelist.

        SECURITY FIX: Removed automatic reload to prevent privilege escalation.
        Whitelist is now only loaded once at startup. If file is modified after startup,
        it will be detected and logged, but NOT automatically reloaded.
        Administrator must restart the server to apply whitelist changes.
        """
        # Check for unauthorized file modifications (log but don't reload)
        self._check_whitelist_integrity()

        return device_id in self.authorized_devices

    def _check_whitelist_integrity(self):
        """Check if whitelist file has been modified since loading.

        SECURITY: Detects unauthorized modifications but does NOT automatically reload.
        Logs a critical security event if the file has been tampered with.

        Uses cooldown to avoid excessive file system checks (checks at most once per minute).
        """
        try:
            # Acquire lock to avoid race conditions between concurrent checks and updates
            with self._whitelist_lock:
                # Only check if we have a baseline hash
                if self._whitelist_hash is None or self._whitelist_mtime is None:
                    return

                # Cooldown: only check periodically to avoid excessive filesystem operations
                current_time = time.time()
                if current_time - self._whitelist_last_check < self._whitelist_check_interval:
                    return

                # Update last-check timestamp while holding the lock so other threads
                # won't repeat the expensive filesystem check concurrently
                self._whitelist_last_check = current_time

                # Get current file stats
                file_stat = os.stat(self.authorized_devices_path)
                current_mtime = file_stat.st_mtime

                # Quick check: has modification time changed?
                if current_mtime != self._whitelist_mtime:
                    # File has been modified - compute hash to verify
                    with open(self.authorized_devices_path, 'rb') as f:
                        file_content = f.read()
                    current_hash = hashlib.sha256(file_content).hexdigest()

                    if current_hash != self._whitelist_hash:
                        # File content has CHANGED - potential security issue!
                        logger.critical(f"🚨 SECURITY ALERT: Whitelist file has been MODIFIED since loading!")
                        logger.critical(f"   Original hash: {self._whitelist_hash[:16]}...")
                        logger.critical(f"   Current hash:  {current_hash[:16]}...")
                        logger.critical(f"   Original mtime: {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(self._whitelist_mtime))}")
                        logger.critical(f"   Current mtime:  {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(current_mtime))}")
                        logger.critical(f"   ⚠️  Changes will NOT take effect until server restart!")
                        logger.critical(f"   ⚠️  If this change was unauthorized, investigate immediately!")

                        # SECURITY AUDIT: Log critical event
                        get_audit_logger().log_event('whitelist_modified_after_load', 'critical', {
                            'original_hash': self._whitelist_hash,
                            'current_hash': current_hash,
                            'original_mtime': self._whitelist_mtime,
                            'current_mtime': current_mtime,
                            'loaded_at': self._whitelist_load_time,
                            'detected_at': time.time(),
                            'action': 'Change detected but NOT applied - restart required'
                        })

                        # Update our tracking to avoid repeated warnings (but don't reload!)
                        self._whitelist_hash = current_hash
                        self._whitelist_mtime = current_mtime

        except FileNotFoundError:
            logger.critical(f"🚨 SECURITY ALERT: Whitelist file has been DELETED!")
            get_audit_logger().log_event('whitelist_deleted', 'critical', {
                'original_hash': self._whitelist_hash,
                'detected_at': time.time()
            })
        except Exception as e:
            logger.debug(f"Error checking whitelist integrity: {e}")

    def _reload_authorized_devices_internal(self, force: bool = False, admin_token: str = None) -> bool:
        """
        Manually reload the authorized devices whitelist (INTERNAL - requires admin token).

        SECURITY: This method should only be called via a secure administrative interface.
        Requires a valid admin token (set in the ADMIN_RELOAD_TOKEN environment variable).

        Args:
            force: If True, reload even if file hasn't changed. If False, only reload if modified.
            admin_token: The admin token for authentication.

        Returns:
            True if whitelist was reloaded, False otherwise.
        """
        expected_token = os.environ.get('ADMIN_RELOAD_TOKEN')
        if not expected_token or admin_token != expected_token:
            logger.critical("🚨 Unauthorized whitelist reload attempt!")
            get_audit_logger().log_event('unauthorized_reload_attempt', 'critical', {
                'timestamp': time.time()
            })
            return False
        try:
            # Check if file has been modified
            file_stat = os.stat(self.authorized_devices_path)
            current_mtime = file_stat.st_mtime

            if not force and current_mtime == self._whitelist_mtime:
                logger.info("ℹ️  Whitelist file has not been modified, skipping reload")
                return False

            logger.warning("⚠️  MANUAL RELOAD of authorized devices whitelist requested")
            logger.warning(f"   This will apply changes from {self.authorized_devices_path}")
            logger.warning(f"   Current device count: {len(self.authorized_devices)}")

            # Reload the whitelist
            self.load_authorized_devices()

            logger.info(f"✅ Whitelist reloaded successfully")
            logger.info(f"   New device count: {len(self.authorized_devices)}")

            # SECURITY AUDIT: Log manual reload
            get_audit_logger().log_event('whitelist_manually_reloaded', 'high', {
                'device_count': len(self.authorized_devices),
                'file_hash': self._whitelist_hash,
                'reload_time': time.time(),
                'forced': force
            })

            return True

        except Exception as e:
            logger.error(f"❌ Failed to reload whitelist: {e}")
            return False

    def _load_ip_blacklist(self):
        """Load persisted IP blacklist from disk (only active bans are loaded)."""
        try:
            with self.rate_limit_lock:
                if not self._blacklist_file.exists():
                    return
                with open(self._blacklist_file, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                now = time.time()
                loaded = {}
                for entry in data:
                    try:
                        ip, until, count = entry
                        if float(until) > now:
                            loaded[ip] = (float(until), int(count))
                    except Exception:
                        continue
                self.ip_blacklist = loaded
                if loaded:
                    logger.info(f"🔒 Loaded {len(loaded)} active blacklisted IPs from {self._blacklist_file}")
        except Exception as e:
            logger.error(f"❌ Failed to load ip blacklist: {e}")

    def _save_ip_blacklist(self):
        """Persist current IP blacklist to disk (only active bans are saved)."""
        try:
            with self.rate_limit_lock:
                data = [[ip, until, count] for ip, (until, count) in self.ip_blacklist.items() if float(until) > time.time()]
                tmp = self._blacklist_file.with_suffix('.tmp')
                with open(tmp, 'w', encoding='utf-8') as f:
                    json.dump(data, f)
                # Atomic replace
                os.replace(str(tmp), str(self._blacklist_file))
        except Exception as e:
            logger.error(f"❌ Failed to save ip blacklist: {e}")

    def is_ip_blacklisted(self, ip_address: str) -> bool:
        """Check if IP is blacklisted and remove expired entries (persist changes)."""
        with self.rate_limit_lock:
            current_time = time.time()

            # Check if IP is currently blacklisted
            if ip_address in self.ip_blacklist:
                blacklist_until, violation_count = self.ip_blacklist[ip_address]
                if current_time < blacklist_until:
                    logger.warning(f"🚫 IP {ip_address} is blacklisted until {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(blacklist_until))}")
                    return True
                else:
                    # Blacklist expired, remove entry and persist change
                    del self.ip_blacklist[ip_address]
                    try:
                        self._save_ip_blacklist()
                    except Exception as e:
                        logger.debug(f"Failed to persist blacklist removal: {e}")
            return False

    def add_to_blacklist(self, ip_address: str):
        """Add IP to blacklist with exponential backoff and persist to disk"""
        with self.rate_limit_lock:
            current_time = time.time()

            if ip_address in self.ip_blacklist:
                _, violation_count = self.ip_blacklist[ip_address]
                violation_count += 1
            else:
                violation_count = 1

            # Exponential backoff: 5 min, 10 min, 20 min, ... up to 24 hours
            blacklist_duration = min(
                self.BLACKLIST_DURATION_BASE * (2 ** (violation_count - 1)),
                self.MAX_BLACKLIST_DURATION
            )
            blacklist_until = current_time + blacklist_duration

            self.ip_blacklist[ip_address] = (blacklist_until, violation_count)
            logger.warning(f"🚫 IP {ip_address} blacklisted for {blacklist_duration/60:.1f} minutes (violation #{violation_count})")

            # SECURITY AUDIT: Log blacklist event
            get_audit_logger().log_event('ip_blacklisted', 'high', {
                'ip': ip_address,
                'violation_count': violation_count,
                'blacklist_duration_seconds': blacklist_duration,
                'blacklist_until': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime(blacklist_until))
            })

            # Persist blacklist state to disk
            try:
                self._save_ip_blacklist()
            except Exception as e:
                logger.error(f"❌ Failed to persist ip blacklist: {e}")

    def is_combined_rate_limited(self, ip_address: str, device_id: str) -> bool:
        """Check combined IP + Device-ID rate limiting for enhanced security.

        This prevents attackers from bypassing limits by rotating IPs or device IDs.
        Uses adaptive rate limits based on violation history.
        """
        with self.rate_limit_lock:
            current_time = time.time()
            key = f"{ip_address}:{device_id}"  # Combined key

            if key in self.handshake_attempts:
                count, window_start, violations = self.handshake_attempts[key]

                # Adaptive rate limiting: stricter limits for repeat offenders
                adaptive_max_attempts = self.MAX_HANDSHAKE_ATTEMPTS
                if violations > 0:
                    # Reduce allowed attempts for violators (minimum 1)
                    adaptive_max_attempts = max(1, self.MAX_HANDSHAKE_ATTEMPTS // (2 ** violations))

                # Check if window has expired
                if current_time - window_start > self.RATE_LIMIT_WINDOW:
                    # Reset window but keep violation count
                    self.handshake_attempts[key] = (1, current_time, violations)
                    return False

                # Within window - check count
                if count >= adaptive_max_attempts:
                    logger.warning(f"⚠️ Combined rate limit exceeded for {ip_address}:{device_id[:8]}... "
                                 f"({count}/{adaptive_max_attempts} attempts in {current_time - window_start:.1f}s, "
                                 f"violations: {violations})")

                    # Increment violation count and add to blacklist
                    self.handshake_attempts[key] = (count, window_start, violations + 1)
                    self.add_to_blacklist(ip_address)

                    # SECURITY AUDIT: Log rate limit violation
                    get_audit_logger().log_event('rate_limit_violation', 'medium', {
                        'ip': ip_address,
                        'device_id': device_id[:16] + '...',
                        'attempts': count,
                        'max_attempts': adaptive_max_attempts,
                        'violations': violations + 1,
                        'window_seconds': current_time - window_start
                    })

                    return True

                # Increment count
                self.handshake_attempts[key] = (count + 1, window_start, violations)
                return False
            else:
                # First attempt from this IP+device combination
                self.handshake_attempts[key] = (1, current_time, 0)
                return False

    def is_device_rate_limited(self, device_id: str) -> bool:
        """Check if device ID is rate limited (stricter than IP-based)"""
        with self.rate_limit_lock:
            current_time = time.time()

            if device_id in self.device_rate_limits:
                count, window_start, violations = self.device_rate_limits[device_id]

                # Check if window has expired
                if current_time - window_start > self.RATE_LIMIT_WINDOW:
                    # Reset window (keep violation count for progressive penalties)
                    self.device_rate_limits[device_id] = (1, current_time, violations)
                    return False

                # Within window - check count
                if count >= self.MAX_DEVICE_ATTEMPTS:
                    logger.warning(f"⚠️ Device rate limit exceeded for {device_id[:16]}... ({count} attempts in {current_time - window_start:.1f}s)")
                    # Increment violation count
                    self.device_rate_limits[device_id] = (count, window_start, violations + 1)
                    return True

                # Increment count
                self.device_rate_limits[device_id] = (count + 1, window_start, violations)
                return False
            else:
                # First attempt from this device
                self.device_rate_limits[device_id] = (1, current_time, 0)
                return False
        """Check if device ID is rate limited (stricter than IP-based)"""
        with self.rate_limit_lock:
            current_time = time.time()

            if device_id in self.device_rate_limits:
                count, window_start, violations = self.device_rate_limits[device_id]

                # Check if window has expired
                if current_time - window_start > self.RATE_LIMIT_WINDOW:
                    # Reset window (keep violation count for progressive penalties)
                    self.device_rate_limits[device_id] = (1, current_time, violations)
                    return False

                # Within window - check count
                if count >= self.MAX_DEVICE_ATTEMPTS:
                    logger.warning(f"⚠️ Device rate limit exceeded for {device_id[:16]}... ({count} attempts in {current_time - window_start:.1f}s)")
                    # Increment violation count
                    self.device_rate_limits[device_id] = (count, window_start, violations + 1)
                    return True

                # Increment count
                self.device_rate_limits[device_id] = (count + 1, window_start, violations)
                return False
            else:
                # First attempt from this device
                self.device_rate_limits[device_id] = (1, current_time, 0)
                return False

    def cleanup_rate_limits(self):
        """Remove expired rate limit entries (prevent memory leak)"""
        with self.rate_limit_lock:
            current_time = time.time()

            # Cleanup IP rate limits (now combined IP:device keys)
            expired_combined = [
                key for key, (_, window_start, _) in self.handshake_attempts.items()
                if current_time - window_start > self.RATE_LIMIT_WINDOW * 2
            ]
            for key in expired_combined:
                del self.handshake_attempts[key]

            # Cleanup device rate limits
            expired_devices = [
                device_id for device_id, (_, window_start, _) in self.device_rate_limits.items()
                if current_time - window_start > self.RATE_LIMIT_WINDOW * 2
            ]
            for device_id in expired_devices:
                del self.device_rate_limits[device_id]

            # Cleanup expired blacklist entries
            expired_blacklist = [
                ip for ip, (blacklist_until, _) in self.ip_blacklist.items()
                if current_time > blacklist_until + 3600  # Keep for 1 hour after expiry for logging
            ]
            for ip in expired_blacklist:
                del self.ip_blacklist[ip]

            # Persist blacklist changes if any expired entries were removed
            if expired_blacklist:
                try:
                    self._save_ip_blacklist()
                except Exception as e:
                    logger.debug(f"Failed to persist blacklist cleanup: {e}")

            if expired_combined or expired_devices or expired_blacklist:
                logger.debug(f"🧹 Cleaned up {len(expired_combined)} combined limits, {len(expired_devices)} device limits, {len(expired_blacklist)} blacklist entries")

    def handle_client_hello(self, message: dict, sender_address: Tuple[str, int]) -> dict:
        """
        Handle ClientHello message from Android app
        Returns ServerHello response or error
        
        SECURITY NOTE: This message is transmitted in PLAINTEXT (standard for ECDH)
        - Passive observers can see: device ID, device name, public key
        - Mitigation: Device whitelist, HMAC verification, session timeouts
        - Flight data encrypted after handshake completes
        """
        try:
            # SECURITY: Validate message structure first
            if not isinstance(message, dict):
                logger.warning(f"❌ Invalid message type from {sender_address[0]}: {type(message)}")
                return {
                    "type": "Error",
                    "error": "HandshakeFailed",
                    "message": "Message must be a JSON object",
                    "timestamp": int(time.time() * 1000)
                }

            device_id = message.get('deviceId', '')
            device_name = message.get('deviceName', 'Unknown')
            client_public_key_b64 = message.get('publicKey', '')
            client_timestamp = message.get('timestamp', 0)
            entity_tracking_enabled = message.get('entityTrackingEnabled', False)  # NEW: Entity tracking preference
            ip_address = sender_address[0]

            # SECURITY: Comprehensive deviceId validation
            if not device_id or not isinstance(device_id, str):
                logger.warning(f"❌ Missing or invalid deviceId from {ip_address}")
                return {
                    "type": "Error",
                    "error": "HandshakeFailed",
                    "message": "deviceId is required and must be a string",
                    "timestamp": int(time.time() * 1000)
                }

            # Length limits (prevent DoS and buffer overflows)
            if len(device_id) < 8 or len(device_id) > 128:
                logger.warning(f"❌ deviceId length invalid from {ip_address}: {len(device_id)} chars (must be 8-128)")
                return {
                    "type": "Error",
                    "error": "HandshakeFailed",
                    "message": "deviceId must be between 8 and 128 characters",
                    "timestamp": int(time.time() * 1000)
                }

            # Strict character whitelist (prevent injection attacks)
            import re
            if not re.match(r'^[a-zA-Z0-9_-]+$', device_id):
                logger.warning(f"❌ deviceId contains invalid characters from {ip_address}: {repr(device_id)}")
                return {
                    "type": "Error",
                    "error": "HandshakeFailed",
                    "message": "deviceId must contain only alphanumeric characters, dash, or underscore",
                    "timestamp": int(time.time() * 1000)
                }

            # SECURITY: Comprehensive deviceName validation
            if not isinstance(device_name, str):
                device_name = 'Unknown'
            else:
                # Length limit and sanitization
                if len(device_name) > 64:  # Reduced from 128 for better UX
                    device_name = device_name[:64] + '...'
                # Remove control characters and normalize whitespace
                device_name = ''.join(char for char in device_name if char.isprintable())
                device_name = ' '.join(device_name.split())  # Normalize whitespace
                if not device_name.strip():
                    device_name = 'Unknown'

            # SECURITY: Comprehensive publicKey validation
            if not client_public_key_b64 or not isinstance(client_public_key_b64, str):
                logger.warning(f"❌ Missing or invalid publicKey from {ip_address}")
                return {
                    "type": "Error",
                    "error": "HandshakeFailed",
                    "message": "publicKey is required and must be a string",
                    "timestamp": int(time.time() * 1000)
                }

            # Length validation before Base64 decoding (prevent DoS)
            if len(client_public_key_b64) < 80 or len(client_public_key_b64) > 200:
                logger.warning(f"❌ publicKey Base64 length invalid from {ip_address}: {len(client_public_key_b64)} chars")
                return {
                    "type": "Error",
                    "error": "HandshakeFailed",
                    "message": "publicKey Base64 string length invalid",
                    "timestamp": int(time.time() * 1000)
                }

            # Validate Base64 format and decode
            try:
                decoded_key = self._base64_decode(client_public_key_b64)
                # SECP256R1 DER-encoded public key should be exactly 91 bytes (uncompressed)
                if len(decoded_key) != 91:
                    raise ValueError(f"DER key length must be 91 bytes, got {len(decoded_key)}")
            except Exception as e:
                logger.warning(f"❌ Invalid publicKey format from {ip_address}: {e}")
                return {
                    "type": "Error",
                    "error": "HandshakeFailed",
                    "message": "publicKey must be valid Base64-encoded SECP256R1 DER key",
                    "timestamp": int(time.time() * 1000)
                }

            # SECURITY: Stricter timestamp validation
            if not isinstance(client_timestamp, (int, float)) or client_timestamp <= 0:
                logger.warning(f"❌ Invalid timestamp type/value from {ip_address}: {client_timestamp}")
                return {
                    "type": "Error",
                    "error": "HandshakeFailed",
                    "message": "timestamp must be a positive number",
                    "timestamp": int(time.time() * 1000)
                }

            server_time = time.time() * 1000  # milliseconds
            time_diff = abs(server_time - client_timestamp)

            # Stricter time window: 60 seconds instead of 120 (reduced attack window)
            MAX_TIMESTAMP_DRIFT_MS = 60000  # 60 seconds

            # Additional check: prevent timestamps from far future (clock skew protection)
            if client_timestamp > server_time + MAX_TIMESTAMP_DRIFT_MS:
                logger.warning(f"❌ Timestamp from future: {time_diff/1000:.1f}s ahead")
                return {
                    "type": "Error",
                    "error": "HandshakeFailed",
                    "message": "Device clock appears to be set to future time",
                    "timestamp": int(server_time)
                }

            # Prevent timestamps that are too old (replay protection)
            if time_diff > MAX_TIMESTAMP_DRIFT_MS:
                logger.warning(f"❌ Timestamp too old: {time_diff/1000:.1f}s drift (max {MAX_TIMESTAMP_DRIFT_MS/1000}s)")
                return {
                    "type": "Error",
                    "error": "HandshakeFailed",
                    "message": f"Timestamp too old. Check device clock synchronization.",
                    "timestamp": int(server_time)
                }

            logger.info(f"📥 ClientHello from {device_name} ({device_id[:16]}...) @ {ip_address}")

            # SECURITY: Combined IP + Device-ID rate limiting (prevents IP rotation attacks)
            if self.is_combined_rate_limited(ip_address, device_id):
                logger.warning(f"🚫 Combined rate limit exceeded for {ip_address}:{device_id[:8]}... - rejecting handshake")
                return {
                    "type": "Error",
                    "error": "RateLimitExceeded",
                    "message": f"Too many handshake attempts. Please wait {self.RATE_LIMIT_WINDOW}s and try again.",
                    "timestamp": int(time.time() * 1000)
                }

            # Check authorization
            if not self.is_device_authorized(device_id):
                logger.warning(f"❌ Unauthorized device: {device_id[:16]}... ({device_name})")
                logger.warning(f"   Add to {self.authorized_devices_path} to authorize")

                # SECURITY AUDIT: Log unauthorized access attempt
                get_audit_logger().log_event('auth_failure_unauthorized', 'high', {
                    'device_id': device_id[:16] + '...',
                    'device_name': device_name,
                    'ip': ip_address,
                    'reason': 'Device not in whitelist'
                })

                # SECURITY: Constant time delay to prevent timing attacks on authorization
                time.sleep(0.05)  # 50ms delay on auth failure

                # Return a generic error message to clients to avoid leaking internal details
                return {
                    "type": "Error",
                    "error": "AuthorizationFailed",
                    "message": "Device is not authorized for this server.",
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

            # SECURITY: Validate ECDH public key properties
            try:
                if not isinstance(client_public_key, ec.EllipticCurvePublicKey):
                    raise ValueError("Public key must be an EC key")

                if not isinstance(client_public_key.curve, ec.SECP256R1):
                    raise ValueError("Only SECP256R1 curve is allowed")

                # Validate public key is not point at infinity
                public_numbers = client_public_key.public_numbers()
                if public_numbers.x == 0 and public_numbers.y == 0:
                    raise ValueError("Point at infinity")

            except Exception as e:
                logger.error(f"❌ Invalid EC public key for device {device_id[:8]}...: {e}")
                return {
                    "type": "Error",
                    "error": "InvalidPublicKey",
                    "message": f"Invalid EC public key: {e}",
                    "timestamp": int(server_time)
                }

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
                aircraft=self.aircraft_name,
                entity_tracking_enabled=entity_tracking_enabled  # Pass client's preference
            )
            
            # Remove old session for this device (if exists)
            old_session_id = self.device_sessions.get(device_id)
            if old_session_id and old_session_id in self.sessions:
                logger.info(f"🗑️ Replacing old session {old_session_id[:8]}... for device {device_id[:16]}...")
                del self.sessions[old_session_id]
            
            self.sessions[session_id] = session
            self.device_sessions[device_id] = session_id

            entity_status = "with entity tracking" if entity_tracking_enabled else "aircraft data only"
            logger.info(f"🔑 Session created: {session_id[:8]}... (key derived from ECDH, {entity_status})")

            # SECURITY AUDIT: Log successful session establishment
            get_audit_logger().log_event('session_established', 'low', {
                'session_id': session_id[:8] + '...',
                'device_id': device_id[:16] + '...',
                'device_name': self.authorized_devices[device_id].name if device_id in self.authorized_devices else 'Unknown',
                'ip': ip_address,
                'aircraft': self.aircraft_name
            })
            
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
            # SECURITY: Log detailed error internally but return generic message to client
            logger.error(f"❌ Error handling ClientHello: {e}", exc_info=True)
            return {
                "type": "Error",
                "error": "HandshakeFailed",
                "message": "Handshake failed due to an internal error. Please try again.",
                "timestamp": int(time.time() * 1000)
            }
    
    def handle_key_confirm(self, message: dict) -> dict:
        """
        Handle KeyConfirm message from Android app
        Verifies HMAC and returns Ack
        """
        try:
            # SECURITY: Validate message structure first
            if not isinstance(message, dict):
                logger.warning("❌ Invalid KeyConfirm message type")
                return {
                    "type": "Error",
                    "error": "AuthFailed",
                    "message": "Message must be a JSON object",
                    "timestamp": int(time.time() * 1000)
                }

            session_id = message.get('sessionId', '')
            client_hmac_b64 = message.get('hmac', '')
            client_timestamp = message.get('timestamp', 0)

            # SECURITY: Validate sessionId
            if not session_id or not isinstance(session_id, str):
                logger.warning("❌ Missing or invalid sessionId in KeyConfirm")
                return {
                    "type": "Error",
                    "error": "AuthFailed",
                    "message": "sessionId is required and must be a string",
                    "timestamp": int(time.time() * 1000)
                }

            if len(session_id) != 36:  # UUID4 length
                logger.warning(f"❌ Invalid sessionId length: {len(session_id)}")
                return {
                    "type": "Error",
                    "error": "AuthFailed",
                    "message": "sessionId must be a valid UUID",
                    "timestamp": int(time.time() * 1000)
                }

            # SECURITY: Validate HMAC
            if not client_hmac_b64 or not isinstance(client_hmac_b64, str):
                logger.warning("❌ Missing or invalid HMAC in KeyConfirm")
                return {
                    "type": "Error",
                    "error": "AuthFailed",
                    "message": "hmac is required and must be a string",
                    "timestamp": int(time.time() * 1000)
                }

            # Length validation for HMAC (SHA256 HMAC is 32 bytes -> 44 chars Base64)
            if len(client_hmac_b64) != 44:
                logger.warning(f"❌ Invalid HMAC length: {len(client_hmac_b64)}")
                return {
                    "type": "Error",
                    "error": "AuthFailed",
                    "message": "hmac must be valid Base64-encoded SHA256 HMAC",
                    "timestamp": int(time.time() * 1000)
                }

            # SECURITY: Stricter timestamp validation
            if not isinstance(client_timestamp, (int, float)) or client_timestamp <= 0:
                logger.warning(f"❌ Invalid timestamp in KeyConfirm: {client_timestamp}")
                return {
                    "type": "Error",
                    "error": "AuthFailed",
                    "message": "timestamp must be a positive number",
                    "timestamp": int(time.time() * 1000)
                }

            server_time = time.time() * 1000
            time_diff = abs(server_time - client_timestamp)
            MAX_TIMESTAMP_DRIFT_MS = 60000  # 60 seconds (stricter than ClientHello)

            # Prevent future timestamps (clock skew protection)
            if client_timestamp > server_time + MAX_TIMESTAMP_DRIFT_MS:
                logger.warning(f"❌ KeyConfirm timestamp from future: {time_diff/1000:.1f}s ahead")
                return {
                    "type": "Error",
                    "error": "AuthFailed",
                    "message": "Device clock appears to be set to future time",
                    "timestamp": int(server_time)
                }

            if time_diff > MAX_TIMESTAMP_DRIFT_MS:
                logger.warning(f"❌ KeyConfirm timestamp too old: {time_diff/1000:.1f}s drift")
                return {
                    "type": "Error",
                    "error": "AuthFailed",
                    "message": f"Timestamp too old. Maximum drift: {MAX_TIMESTAMP_DRIFT_MS/1000}s",
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

                # SECURITY AUDIT: Log HMAC verification failure
                get_audit_logger().log_event('hmac_verification_failed', 'high', {
                    'session_id': session_id[:8] + '...',
                    'device_id': session.device_id[:16] + '...' if session else 'Unknown',
                    'reason': 'HMAC mismatch - possible MITM or corrupted message'
                })

                # SECURITY: Constant time delay to prevent timing attacks on authentication
                time.sleep(0.1)  # 100ms delay on auth failure

                return {
                    "type": "Error",
                    "error": "AuthFailed",
                    "message": "Key confirmation failed",
                    "timestamp": int(time.time() * 1000)
                }
            
            logger.info(f"✅ HMAC verified for session {session_id[:8]}...")
            session.update_activity()

            # Mark session as confirmed and record confirmation time
            session.confirmed = True
            session.confirmed_at = time.time()
            get_audit_logger().log_event('session_confirmed', 'low', {
                'session_id': session_id[:8] + '...',
                'device_id': session.device_id[:16] + '...'
            })
            
            return {
                "type": "Ack",
                "sessionId": session_id,
                "status": "ready",
                "timestamp": int(time.time() * 1000),
                "message": "Handshake complete, session established"
            }
        
        except Exception as e:
            # SECURITY: Log detailed error internally but return generic message to client
            logger.error(f"❌ Error handling KeyConfirm: {e}", exc_info=True)
            return {
                "type": "Error",
                "error": "KeyConfirmFailed",
                "message": "Key confirmation failed due to an internal error. Please try again.",
                "timestamp": int(time.time() * 1000)
            }
    
    def get_session_by_id(self, session_id: str) -> Optional[SessionData]:
        """Get session by ID, returns None if not found, unconfirmed, or expired.

        Unconfirmed sessions are not usable for encryption and will be removed
        after a short timeout to avoid resource exhaustion by unauthenticated clients.
        """
        session = self.sessions.get(session_id)
        if not session:
            return None

        # Protect against unconfirmed sessions being used for encryption
        UNCONFIRMED_TIMEOUT = 60  # seconds
        if not getattr(session, 'confirmed', False):
            # If unconfirmed and expired, remove it
            if time.time() - session.created_at > UNCONFIRMED_TIMEOUT:
                # Remove mapping
                if session.device_id in self.device_sessions and self.device_sessions[session.device_id] == session_id:
                    del self.device_sessions[session.device_id]
                del self.sessions[session_id]
                logger.info(f"🗑️ Removed unconfirmed session due to timeout: {session_id[:8]}...")
                get_audit_logger().log_event('unconfirmed_session_removed', 'medium', {
                    'session_id': session_id[:8] + '...',
                    'device_id': session.device_id[:16] + '...',
                    'age_seconds': int(time.time() - session.created_at)
                })
                return None
            # Not yet confirmed but still within timeout — not usable
            logger.debug(f"⏳ Session {session_id[:8]}... not yet confirmed")
            return None

        # Confirmed session — check expiry as before
        if not session.is_expired():
            session.update_activity()
            return session
        else:
            # Clean up expired session
            if session.device_id in self.device_sessions and self.device_sessions[session.device_id] == session_id:
                del self.device_sessions[session.device_id]
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
    
    def cleanup_expired_sessions(self, max_age_seconds: int = 3600, unconfirmed_timeout_seconds: int = 60):
        """Remove expired sessions and rate limit entries.

        Unconfirmed sessions older than `unconfirmed_timeout_seconds` are removed to
        prevent memory exhaustion from unconfirmed handshakes.
        """
        expired = []
        now = time.time()
        for sid, session in list(self.sessions.items()):
            # Remove unconfirmed sessions older than timeout
            if not getattr(session, 'confirmed', False) and (now - session.created_at > unconfirmed_timeout_seconds):
                expired.append(sid)
                logger.info(f"🗑️ Unconfirmed session expired: {sid[:8]}... (age {int(now - session.created_at)}s)")
                get_audit_logger().log_event('unconfirmed_session_removed', 'medium', {
                    'session_id': sid[:8] + '...',
                    'device_id': session.device_id[:16] + '...',
                    'age_seconds': int(now - session.created_at)
                })
            elif session.is_expired(max_age_seconds):
                expired.append(sid)

        for sid in expired:
            session = self.sessions.get(sid)
            if not session:
                continue
            # Remove from device mapping
            if session.device_id in self.device_sessions:
                if self.device_sessions[session.device_id] == sid:
                    del self.device_sessions[session.device_id]
            del self.sessions[sid]
            logger.info(f"🗑️ Cleaned up session: {sid[:8]}...")

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

