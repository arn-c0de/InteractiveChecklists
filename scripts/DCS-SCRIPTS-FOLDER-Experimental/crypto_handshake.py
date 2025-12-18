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

logger = logging.getLogger(__name__)


class SecurityAuditLogger:
    """Logs security events in structured JSON format for analysis"""

    def __init__(self, audit_file: str = "security_audit.jsonl"):
        self.audit_file = Path(audit_file)
        self._lock = __import__('threading').Lock()
        self._ensure_secure_permissions()

    def _ensure_secure_permissions(self):
        """Ensure audit file exists and has secure permissions (best-effort).

        Notes:
        - Attempt to create the file with owner-only permissions when missing.
        - On platforms where POSIX permissions aren't meaningful (e.g., Windows) this is
          best-effort and errors are logged. We avoid silently proceeding if permission
          setting repeatedly fails.
        """
        try:
            # Ensure parent directory exists
            self.audit_file.parent.mkdir(parents=True, exist_ok=True)

            # If file does not exist, create it with secure permissions if possible
            if not self.audit_file.exists():
                try:
                    import stat
                    fd = os.open(str(self.audit_file), os.O_CREAT | os.O_WRONLY, stat.S_IRUSR | stat.S_IWUSR)
                    os.close(fd)
                except Exception:
                    # Fallback to touch if os.open with mode fails (platform differences)
                    try:
                        self.audit_file.touch()
                    except Exception as e:
                        logger.error(f"❌ Failed to create audit file: {e}")
                        return

            # Attempt to set secure permissions (best-effort)
            try:
                import stat
                self.audit_file.chmod(stat.S_IRUSR | stat.S_IWUSR)  # 0o600
            except Exception as e:
                # Log as error because audit logs are sensitive
                logger.error(f"❌ Could not set secure permissions on audit file: {e}")
        except Exception as e:
            logger.error(f"❌ Unexpected error ensuring audit file permissions: {e}")

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
                # Ensure file exists with secure permissions before writing
                if not self.audit_file.exists():
                    try:
                        # Try to create with 0600 mode
                        import stat
                        fd = os.open(str(self.audit_file), os.O_CREAT | os.O_WRONLY, stat.S_IRUSR | stat.S_IWUSR)
                        os.close(fd)
                    except Exception:
                        # Best-effort fallback
                        self.audit_file.touch()

                # Append the event
                with open(self.audit_file, 'a', encoding='utf-8') as f:
                    f.write(json.dumps(event) + '\n')

                # Verify permissions after write
                try:
                    current_mode = self.audit_file.stat().st_mode & 0o777
                    if current_mode != (0o600):
                        logger.error(f"❌ Audit log has insecure permissions: {oct(current_mode)}")
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
                 peer_public_key: bytes, aircraft: Optional[str] = None):
        self.session_id = session_id
        self.device_id = device_id
        self.session_key = session_key
        self.peer_public_key = peer_public_key
        self.aircraft = aircraft
        self.created_at = time.time()
        self.last_activity = time.time()
        # Whether the client completed KeyConfirm for this session
        self.confirmed = False
        self.confirmed_at = None
        # Counter for nonce generation (thread-safe)
        self._nonce_counter = 0
        self._nonce_lock = __import__('threading').Lock()
        from collections import deque
        self._received_nonces = deque(maxlen=1000)  # Auto-evicts oldest
        self._nonce_counter_min = 0  # Track minimum accepted counter

    def is_expired(self, timeout_seconds: int = 900) -> bool:
        """Check if session has expired (default 15 minutes - shorter for security)"""
        return (time.time() - self.last_activity) > timeout_seconds

    def update_activity(self):
        """Update last activity timestamp"""
        self.last_activity = time.time()

    def generate_nonce(self) -> bytes:
        """Generate counter-based nonce for this session (SERVER side, 0x01 prefix) with exhaustion protection and rekey warning."""
        with self._nonce_lock:
            self._nonce_counter += 1

            # Trigger re-key at 75% capacity (2^62 messages)
            if self._nonce_counter >= (2**64 * 0.75):
                logger.warning(f"⚠️ Nonce counter at 75% capacity for session {self.session_id[:8]}...")
                # Signal to application layer that re-keying is needed
                self._needs_rekey = True

            if self._nonce_counter >= 2**64:
                logger.critical(f"🚨 Nonce counter exhausted for session {self.session_id[:8]}!")
                # Instead of crashing, invalidate the session
                self._is_valid = False
                raise RuntimeError("Nonce exhausted - session invalidated")

            # Format: 0x01 (server) + 3 bytes reserved + 8 bytes counter
            return bytes([0x01, 0x00, 0x00, 0x00]) + self._nonce_counter.to_bytes(8, 'big')

    def validate_nonce(self, nonce: bytes, expected_sender: int = 0x00) -> bool:
        """Validate received nonce to prevent replay attacks and check sender id.

        Nonce format: [sender_id:1][reserved:3][counter:8]
        For messages coming from the client to the server, sender_id MUST be 0x00.
        Uses a sliding window with automatic eviction for memory safety.
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
            # Reject if too old (sliding window)
            if counter < self._nonce_counter_min:
                logger.warning(f"⚠️ Nonce too old: {counter} < {self._nonce_counter_min}")
                return False

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

            self._received_nonces.append(counter)

            # Update minimum periodically
            if len(self._received_nonces) >= 1000:
                self._nonce_counter_min = min(self._received_nonces)

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

        # SECURITY: Whitelist file integrity tracking
        self._whitelist_hash: Optional[str] = None  # SHA-256 hash of loaded whitelist
        self._whitelist_mtime: Optional[float] = None  # Last modification time of whitelist file
        self._whitelist_load_time: Optional[float] = None  # When whitelist was last loaded
        self._whitelist_last_check: float = 0  # Last time integrity was checked
        self._whitelist_check_interval: float = 60.0  # Check integrity every 60 seconds (cooldown)
        self._whitelist_lock = __import__('threading').Lock()

        # Rate limiting: IP -> (attempt_count, window_start_time)
        self.handshake_attempts: Dict[str, Tuple[int, float]] = {}
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
                    # Add to blacklist after repeated violations
                    self.add_to_blacklist(ip_address)
                    return True

                # Increment count
                self.handshake_attempts[ip_address] = (count + 1, window_start)
                return False
            else:
                # First attempt from this IP
                self.handshake_attempts[ip_address] = (1, current_time)
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

    def cleanup_rate_limits(self):
        """Remove expired rate limit entries (prevent memory leak)"""
        with self.rate_limit_lock:
            current_time = time.time()

            # Cleanup IP rate limits
            expired_ips = [
                ip for ip, (_, window_start) in self.handshake_attempts.items()
                if current_time - window_start > self.RATE_LIMIT_WINDOW * 2
            ]
            for ip in expired_ips:
                del self.handshake_attempts[ip]

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

            if expired_ips or expired_devices or expired_blacklist:
                logger.debug(f"🧹 Cleaned up {len(expired_ips)} IP limits, {len(expired_devices)} device limits, {len(expired_blacklist)} blacklist entries")

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

            # SECURITY: Validate deviceId format and length
            if not device_id or not isinstance(device_id, str):
                logger.warning(f"❌ Missing or invalid deviceId from {ip_address}")
                return {
                    "type": "Error",
                    "error": "HandshakeFailed",
                    "message": "deviceId is required and must be a string",
                    "timestamp": int(time.time() * 1000)
                }

            if len(device_id) > 128:
                logger.warning(f"❌ deviceId too long from {ip_address}: {len(device_id)} chars")
                return {
                    "type": "Error",
                    "error": "HandshakeFailed",
                    "message": "deviceId must not exceed 128 characters",
                    "timestamp": int(time.time() * 1000)
                }

            # Validate deviceId contains only safe characters (alphanumeric, dash, underscore)
            import re
            if not re.match(r'^[a-zA-Z0-9_-]+$', device_id):
                logger.warning(f"❌ deviceId contains invalid characters from {ip_address}")
                return {
                    "type": "Error",
                    "error": "HandshakeFailed",
                    "message": "deviceId must contain only alphanumeric characters, dash, or underscore",
                    "timestamp": int(time.time() * 1000)
                }

            # SECURITY: Validate deviceName length (prevent log injection)
            if not isinstance(device_name, str):
                device_name = 'Unknown'
            if len(device_name) > 128:
                device_name = device_name[:128]  # Truncate
            # Remove newlines and control characters from device name
            device_name = ''.join(char for char in device_name if char.isprintable())

            # SECURITY: Validate publicKey is valid Base64 and correct length
            if not client_public_key_b64 or not isinstance(client_public_key_b64, str):
                logger.warning(f"❌ Missing or invalid publicKey from {ip_address}")
                return {
                    "type": "Error",
                    "error": "HandshakeFailed",
                    "message": "publicKey is required and must be a string",
                    "timestamp": int(time.time() * 1000)
                }

            try:
                decoded_key = self._base64_decode(client_public_key_b64)
                # SECP256R1 DER-encoded public key should be around 91 bytes
                if len(decoded_key) < 50 or len(decoded_key) > 200:
                    raise ValueError(f"Invalid key length: {len(decoded_key)}")
            except Exception as e:
                logger.warning(f"❌ Invalid publicKey format from {ip_address}: {e}")
                return {
                    "type": "Error",
                    "error": "HandshakeFailed",
                    "message": "publicKey must be valid Base64-encoded DER key",
                    "timestamp": int(time.time() * 1000)
                }

            logger.info(f"📥 ClientHello from {device_name} ({device_id[:16]}...) @ {ip_address}")

            # SECURITY: Check IP blacklist first (prevent DoS from banned IPs)
            if self.is_ip_blacklisted(ip_address):
                return {
                    "type": "Error",
                    "error": "IPBlacklisted",
                    "message": "Your IP address has been temporarily blacklisted due to repeated violations.",
                    "timestamp": int(time.time() * 1000)
                }

            # SECURITY: IP-based rate limiting (prevent DoS attacks)
            if self.is_rate_limited(ip_address):
                logger.warning(f"🚫 Rate limit exceeded for {ip_address} - rejecting handshake")
                return {
                    "type": "Error",
                    "error": "RateLimitExceeded",
                    "message": f"Too many handshake attempts. Please wait {self.RATE_LIMIT_WINDOW}s and try again.",
                    "timestamp": int(time.time() * 1000)
                }

            # SECURITY: Device-ID-based rate limiting (stricter, prevents abuse even with IP rotation)
            if self.is_device_rate_limited(device_id):
                logger.warning(f"🚫 Device rate limit exceeded for {device_id[:16]}... - rejecting handshake")
                return {
                    "type": "Error",
                    "error": "DeviceRateLimitExceeded",
                    "message": f"Too many handshake attempts from this device. Please wait {self.RATE_LIMIT_WINDOW}s and try again.",
                    "timestamp": int(time.time() * 1000)
                }

            # SECURITY: Validate timestamp (prevent replay of old messages)
            server_time = time.time() * 1000  # milliseconds
            time_diff = abs(server_time - client_timestamp)
            MAX_TIMESTAMP_DRIFT_MS = 120000  # 120 seconds / 2 minutes (compromise between security and clock drift tolerance)

            if time_diff > MAX_TIMESTAMP_DRIFT_MS:
                logger.warning(f"❌ Timestamp too old/future: {time_diff/1000:.1f}s drift (max {MAX_TIMESTAMP_DRIFT_MS/1000}s)")
                return {
                    "type": "Error",
                    "error": "HandshakeFailed",
                    "message": f"Timestamp drift too large: {time_diff/1000:.1f}s (max {MAX_TIMESTAMP_DRIFT_MS/1000}s). Check device clock.",
                    "timestamp": int(server_time)
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
            MAX_TIMESTAMP_DRIFT_MS = 120000  # 120 seconds / 2 minutes (compromise between security and clock drift tolerance)

            if time_diff > MAX_TIMESTAMP_DRIFT_MS:
                logger.warning(f"❌ KeyConfirm timestamp too old/future: {time_diff/1000:.1f}s drift")
                return {
                    "type": "Error",
                    "error": "AuthFailed",
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

                # SECURITY AUDIT: Log HMAC verification failure
                get_audit_logger().log_event('hmac_verification_failed', 'high', {
                    'session_id': session_id[:8] + '...',
                    'device_id': session.device_id[:16] + '...' if session else 'Unknown',
                    'reason': 'HMAC mismatch - possible MITM or corrupted message'
                })

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
            logger.error(f"❌ Error handling KeyConfirm: {e}", exc_info=True)
            return {
                "type": "Error",
                "error": "KeyConfirmFailed",
                "message": str(e),
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

