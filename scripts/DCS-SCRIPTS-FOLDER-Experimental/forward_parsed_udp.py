#!/usr/bin/env python3
"""
forward_parsed_udp.py
Tail a JSON-lines file (player_aircraft_parsed.jsonl) and send each JSON object
as a single UDP datagram to a configured host:port.

Now with AES-GCM encryption for secure data transmission!

Usage examples:
  python forward_parsed_udp.py --host 192.168.178.100 --port 5010
  python forward_parsed_udp.py --once --send-existing

Requires: pip install cryptography

This script is safe to run outside DCS and avoids modifying any DCS files.
"""

from __future__ import annotations
import argparse
import os
import socket
import sys
import time
import json
import logging
import traceback
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.backends import default_backend

# Setup logger
logger = logging.getLogger(__name__)

# Import SessionManager for ECDH handshake
try:
    from crypto_handshake import SessionManager
    HANDSHAKE_AVAILABLE = True
except ImportError:
    HANDSHAKE_AVAILABLE = False
    sys.stderr.write("⚠️ Warning: crypto_handshake.py not found, ECDH mode unavailable\n")

DEFAULT_FILE = os.path.expanduser(r"~\Saved Games\DCS\Scripts\player_aircraft_parsed.jsonl")
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 5010

# Pre-Shared Key (32 bytes for AES-256) - MUST match Android app!
# Change this to your own random key in production!
# WARNING: This file ships with a default key for convenience — change it before using in production.
DEFAULT_PRE_SHARED_KEY = b'DCS_DataPad_Secret_Key_32BYTES!!'
PRE_SHARED_KEY = DEFAULT_PRE_SHARED_KEY

def check_psk_security():
    """Check if default PSK is used and require confirmation (only for PSK mode)"""
    if PRE_SHARED_KEY == DEFAULT_PRE_SHARED_KEY:
        sys.stderr.write("\n" + "="*70 + "\n")
        sys.stderr.write("⚠️  SECURITY WARNING: Using default PRE_SHARED_KEY!\n")
        sys.stderr.write("="*70 + "\n")
        sys.stderr.write("This is INSECURE for production use!\n")
        sys.stderr.write("Change PRE_SHARED_KEY in this file or use --use-handshake for ECDH.\n")
        sys.stderr.write("See docs/technical/AES_GCM_ENCRYPTION.md for details.\n")
        sys.stderr.write("="*70 + "\n")
        
        # Require explicit confirmation
        try:
            response = input("\nType 'ACCEPT' to continue with default key (insecure): ")
            if response.strip().upper() != 'ACCEPT':
                sys.stderr.write("\n❌ Aborted. Please configure a secure key or use ECDH mode.\n")
                sys.exit(1)
            sys.stderr.write("\n✅ Proceeding with default key (NOT RECOMMENDED)\n\n")
        except (EOFError, KeyboardInterrupt):
            sys.stderr.write("\n\n❌ Aborted by user.\n")
            sys.exit(1)

def check_bind_security(bind_ip: str):
    """Enhanced bind security check.

    - Disallow 0.0.0.0 unconditionally.
    - Warn (and require confirmation) for public IPs.
    - Recommend using 127.0.0.1 for maximum safety.
    """
    if bind_ip == '0.0.0.0':
        sys.stderr.write("\n" + "="*70 + "\n")
        sys.stderr.write("🚨 CRITICAL SECURITY WARNING: Binding to ALL network interfaces is BLOCKED!\n")
        sys.stderr.write("="*70 + "\n")
        sys.stderr.write("This exposes your server to:\n")
        sys.stderr.write("  - Internet (if PC has public IP)\n")
        sys.stderr.write("  - Local network (LAN)\n")
        sys.stderr.write("  - VPN connections\n")
        sys.stderr.write("  - ALL other network interfaces\n")
        sys.stderr.write("\n")
        sys.stderr.write("BINDING TO 0.0.0.0 IS NOT ALLOWED.\n")
        sys.stderr.write("Use --bind-ip 127.0.0.1 (localhost only) or a specific LAN IP.\n")
        sys.stderr.write("="*70 + "\n")
        sys.stderr.write("\n❌ Aborted. Binding to 0.0.0.0 is forbidden for security reasons.\n")
        sys.exit(1)

    # Try to determine if the provided bind_ip is a public IP address.
    try:
        import ipaddress
        addr = ipaddress.ip_address(bind_ip)
        if not (addr.is_private or addr.is_loopback):
            sys.stderr.write("\n" + "="*70 + "\n")
            sys.stderr.write(f"⚠️ WARNING: Binding to public IP {bind_ip}!\n")
            sys.stderr.write("This may expose the handshake listener to the internet or other untrusted networks.\n")
            sys.stderr.write("If you really intend to bind to this IP, type 'ACCEPT_PUBLIC' to continue.\n")
            sys.stderr.write("="*70 + "\n")
            try:
                response = input("Type 'ACCEPT_PUBLIC' to continue: ")
            except (EOFError, KeyboardInterrupt):
                response = ''
            if response.strip() != 'ACCEPT_PUBLIC':
                sys.stderr.write("\n❌ Aborted. Binding to public IP not confirmed.\n")
                sys.exit(1)
    except Exception:
        # Could not parse IP (e.g. hostname) — warn and proceed, but log warning
        logger.warning(f"⚠️ Could not determine if bind IP {bind_ip} is public; ensure this is intended")

    # Recommend localhost for maximum safety
    if bind_ip != '127.0.0.1':
        logger.warning(f"⚠️ Binding to {bind_ip} - consider using 127.0.0.1 for localhost only")

# Global nonce counter for SERVER side (0x01 prefix)
_nonce_counter = 0
_nonce_lock = __import__('threading').Lock()
_received_nonces = set()
# Highest counter observed from clients and sliding window for replay protection
_highest_counter = 0
_REPLAY_WINDOW = 1000  # keep recent counters within this window (reduced from 10000 for security)

# DoS protection: Global message rate limiting
_message_timestamps = []  # List of timestamps for received messages
_rate_limit_lock = __import__('threading').Lock()
_MAX_MESSAGES_PER_SECOND = 50  # Reduced from 100 to 50 for better DoS protection
_RATE_LIMIT_WINDOW_SECONDS = 1.0  # 1 second window

# DoS protection: Per-IP rate limiting (independent of SessionManager)
_ip_rate_limits = {}  # IP -> (message_count, window_start_time, violation_count)
_MAX_MESSAGES_PER_IP = 5  # Maximum 5 messages per IP per window (strict)
_IP_RATE_LIMIT_WINDOW = 10.0  # 10 second window
_IP_BAN_DURATION = 300.0  # 5 minutes ban for violators
_ip_bans = {}  # IP -> ban_until_timestamp

# DoS protection: Message size limits
_MAX_HANDSHAKE_MESSAGE_SIZE = 8192  # 8KB max for handshake messages (prevents large payload attacks)

# Maximum UDP payload (protocol limit). Subtract AEAD overhead when encrypting
# to ensure ciphertext fits into a single UDP datagram.
_UDP_MAX_PAYLOAD = 65507
_AES_GCM_OVERHEAD = 12 + 16  # nonce + tag
# Conservative plaintext maximum so encrypted packet stays under UDP limit
_MAX_DATA_MESSAGE_SIZE = _UDP_MAX_PAYLOAD - _AES_GCM_OVERHEAD


def validate_data_message(data: bytes, encrypt: bool = True) -> bool:
    """Validate data message size before sending.

    Returns: True if the data is within allowed limits, False otherwise.
    """
    if encrypt:
        allowed = _MAX_DATA_MESSAGE_SIZE
    else:
        allowed = _UDP_MAX_PAYLOAD

    if len(data) > allowed:
        logger.warning(f"⚠️ Oversized data message: {len(data)} bytes (max {allowed})")
        return False
    return True


def check_ip_banned(ip: str) -> bool:
    """Check if IP is currently banned.

    Returns True if banned, False otherwise.
    """
    with _rate_limit_lock:
        if ip in _ip_bans:
            ban_until = _ip_bans[ip]
            if time.time() < ban_until:
                return True
            else:
                # Ban expired, remove
                del _ip_bans[ip]
        return False

def ban_ip(ip: str):
    """Ban an IP address temporarily."""
    with _rate_limit_lock:
        ban_until = time.time() + _IP_BAN_DURATION
        _ip_bans[ip] = ban_until
        logger.warning(f"🚫 Banned IP {ip} for {_IP_BAN_DURATION/60:.1f} minutes due to rate limit violations")

def check_ip_rate_limit(ip: str) -> bool:
    """Check per-IP rate limit (strict, independent of SessionManager).

    Returns True if rate limit is OK, False if exceeded.
    """
    with _rate_limit_lock:
        current_time = time.time()

        if ip in _ip_rate_limits:
            count, window_start, violations = _ip_rate_limits[ip]

            # Check if window expired
            if current_time - window_start > _IP_RATE_LIMIT_WINDOW:
                # Reset window
                _ip_rate_limits[ip] = (1, current_time, violations)
                return True

            # Within window - check count
            if count >= _MAX_MESSAGES_PER_IP:
                logger.warning(f"⚠️ IP rate limit exceeded for {ip}: {count} messages in {current_time - window_start:.1f}s")
                # Increment violations and ban
                _ip_rate_limits[ip] = (count, window_start, violations + 1)
                ban_ip(ip)
                return False

            # Increment count
            _ip_rate_limits[ip] = (count + 1, window_start, violations)
            return True
        else:
            # First message from this IP
            _ip_rate_limits[ip] = (1, current_time, 0)
            return True

def cleanup_rate_limits():
    """Cleanup expired rate limit entries to prevent memory leak."""
    with _rate_limit_lock:
        current_time = time.time()

        # Cleanup IP rate limits (keep for 2x window after last activity)
        expired_ips = [
            ip for ip, (_, window_start, _) in _ip_rate_limits.items()
            if current_time - window_start > _IP_RATE_LIMIT_WINDOW * 2
        ]
        for ip in expired_ips:
            del _ip_rate_limits[ip]

        # Cleanup expired bans
        expired_bans = [
            ip for ip, ban_until in _ip_bans.items()
            if current_time > ban_until + 3600  # Keep for 1 hour after expiry for logging
        ]
        for ip in expired_bans:
            del _ip_bans[ip]

def check_global_rate_limit() -> bool:
    """Check if global message rate limit is exceeded (DoS protection).

    Returns True if rate limit is OK, False if exceeded.
    """
    global _message_timestamps
    with _rate_limit_lock:
        current_time = time.time()

        # Remove timestamps older than the window
        cutoff_time = current_time - _RATE_LIMIT_WINDOW_SECONDS
        _message_timestamps = [ts for ts in _message_timestamps if ts > cutoff_time]

        # Check if we're over the limit
        if len(_message_timestamps) >= _MAX_MESSAGES_PER_SECOND:
            logger.warning(f"⚠️ Global message rate limit exceeded: {len(_message_timestamps)} messages in last {_RATE_LIMIT_WINDOW_SECONDS}s")
            return False

        # Record this message
        _message_timestamps.append(current_time)
        return True

def validate_handshake_message(data: bytes, addr: tuple) -> bool:
    """Early validation of handshake message before expensive parsing.

    This provides DoS protection by rejecting invalid messages early.

    Returns True if message passes validation, False otherwise.
    """
    ip = addr[0]

    # 1. Check IP ban status first (cheapest check)
    if check_ip_banned(ip):
        logger.debug(f"🚫 Rejected message from banned IP {ip}")
        return False

    # 2. Check message size (prevent large payload attacks)
    if len(data) > _MAX_HANDSHAKE_MESSAGE_SIZE:
        logger.warning(f"⚠️ Oversized handshake message from {ip}: {len(data)} bytes (max {_MAX_HANDSHAKE_MESSAGE_SIZE})")
        ban_ip(ip)
        return False

    if len(data) < 10:  # Minimum reasonable JSON size
        logger.debug(f"⚠️ Undersized message from {ip}: {len(data)} bytes")
        return False

    # 3. Check per-IP rate limit (before global to catch targeted attacks)
    if not check_ip_rate_limit(ip):
        return False

    # 4. Check global rate limit (prevent distributed floods)
    if not check_global_rate_limit():
        logger.warning(f"🚫 Dropping message from {ip} due to global rate limit")
        return False

    # 5. Basic format validation (check for JSON-like structure without full parsing)
    try:
        # Quick check: message should start with '{' and end with '}'
        data_str = data.decode('utf-8', errors='strict')
        stripped = data_str.strip()
        if not (stripped.startswith('{') and stripped.endswith('}')):
            logger.debug(f"⚠️ Invalid JSON format from {ip}")
            return False
    except UnicodeDecodeError:
        logger.warning(f"⚠️ Invalid UTF-8 from {ip}")
        return False

    return True


# Safe JSON parsing helper to limit size and nesting depth
def safe_json_parse(data: bytes | str, max_size: int = 8192, max_depth: int = 6) -> dict | None:
    """Safely parse JSON with size and depth limits.

    SECURITY: Size check happens BEFORE any parsing to prevent DoS attacks.
    Uses custom decoder with depth tracking to avoid JSON bombs.

    Returns a dict/list on success or None on failure.
    """
    try:
        # SECURITY: Check size BEFORE any processing (prevents memory exhaustion)
        if isinstance(data, bytes):
            if len(data) > max_size:
                logger.warning(f"⚠️ JSON too large: {len(data)} > {max_size} bytes")
                return None
            json_str = data.decode('utf-8', errors='strict')
        else:
            json_str = str(data)
            # Check encoded size
            encoded_size = len(json_str.encode('utf-8'))
            if encoded_size > max_size:
                logger.warning(f"⚠️ JSON too large: {encoded_size} > {max_size} bytes")
                return None

        # Custom JSON decoder with depth tracking (prevents CPU exhaustion from deep nesting)
        class DepthTrackingDecoder(json.JSONDecoder):
            def __init__(self, max_depth, *args, **kwargs):
                self.max_depth = max_depth
                super().__init__(*args, **kwargs)

            def decode(self, s, **kwargs):
                return self._decode_with_depth(s, 0)

            def _decode_with_depth(self, s, depth):
                if depth > self.max_depth:
                    raise ValueError(f"JSON nesting depth exceeds limit of {self.max_depth}")
                # Use the parent decoder but intercept object_hook to track depth
                original_object_hook = self.object_hook
                def depth_checking_hook(obj):
                    if depth + 1 > self.max_depth:
                        raise ValueError(f"JSON nesting depth exceeds limit of {self.max_depth}")
                    return original_object_hook(obj) if original_object_hook else obj

                self.object_hook = depth_checking_hook
                try:
                    result = super(DepthTrackingDecoder, self).decode(s)
                    return result
                finally:
                    self.object_hook = original_object_hook

        # Parse with depth-limited decoder
        decoder = DepthTrackingDecoder(max_depth)
        obj = decoder.decode(json_str)

        # Additional validation: ensure result is a dict or list (expected structures)
        if not isinstance(obj, (dict, list)):
            logger.warning("⚠️ JSON root must be object or array")
            return None

        return obj
    except (json.JSONDecodeError, ValueError, UnicodeDecodeError, RecursionError) as e:
        logger.warning(f"⚠️ Invalid or unsafe JSON: {e}")
        return None

def generate_nonce_server() -> bytes:
    """Generate counter-based nonce for SERVER (prevents collision).
    Format: [sender_id:1][reserved:3][counter:8] = 12 bytes
    Server uses sender_id = 0x01, Client uses 0x00
    """
    global _nonce_counter
    with _nonce_lock:
        _nonce_counter += 1
        if _nonce_counter >= 2**64:
            raise RuntimeError("Nonce counter exhausted - re-key required!")

        # Format: 0x01 (server) + 3 bytes reserved + 8 bytes counter
        return bytes([0x01, 0x00, 0x00, 0x00]) + _nonce_counter.to_bytes(8, 'big')

def validate_nonce_server(nonce: bytes) -> bool:
    """Validate received nonce to prevent replay attacks.

    Additional checks added:
      - ensure nonce prefix indicates a CLIENT message (0x00)
      - sliding-window replay protection with bounded memory

    Returns True if nonce is valid (not replayed), False otherwise.
    """
    if len(nonce) != 12:
        return False

    # Validate prefix: client messages must have sender_id = 0x00
    if nonce[0] != 0x00:
        logger.warning(f"⚠️ Invalid nonce sender id: {nonce[0]:#02x} - expected client (0x00)")
        return False

    # Extract counter (bytes 4-11)
    counter = int.from_bytes(nonce[4:12], 'big')

    with _nonce_lock:
        global _highest_counter
        # Reject counters that are far in the past (stale)
        if _highest_counter and counter <= (_highest_counter - _REPLAY_WINDOW):
            logger.warning(f"⚠️ Stale nonce detected (too old): {counter} (highest: {_highest_counter})")
            return False

        # Replay detection
        if counter in _received_nonces:
            logger.warning(f"⚠️ Replay attack detected! Nonce counter: {counter}")
            return False

        # Accept and record
        _received_nonces.add(counter)
        if counter > _highest_counter:
            _highest_counter = counter

        # Cleanup old nonces to prevent memory leak (keep window)
        min_allowed = max(0, _highest_counter - _REPLAY_WINDOW)
        old_nonces = [n for n in _received_nonces if n < min_allowed]
        for n in old_nonces:
            _received_nonces.remove(n)

    return True

def encrypt_payload(data: bytes, key: bytes = PRE_SHARED_KEY) -> bytes:
    """Encrypt data using AES-GCM with counter-based nonce (prevents collision).

    Returns: nonce (12 bytes) + ciphertext + tag (16 bytes)
    """
    aesgcm = AESGCM(key)
    nonce = generate_nonce_server()  # Counter-based, no collision possible
    ciphertext = aesgcm.encrypt(nonce, data, None)  # no additional authenticated data
    return nonce + ciphertext


def send_udp(payload: bytes, host: str, port: int, sock: socket.socket, encrypt: bool = True, 
             session_mgr: 'SessionManager' = None, device_id: str = None) -> bool:
    """Send UDP packet with optional encryption
    
    Args:
        payload: Data to send
        host: Destination host
        port: Destination port
        sock: UDP socket
        encrypt: Whether to encrypt (PSK or ECDH)
        session_mgr: SessionManager instance (for ECDH mode)
        device_id: Device ID to send to (for ECDH mode, looks up active session)
    """
    try:
        # Validate payload size before doing expensive encryption work
        if not validate_data_message(payload, encrypt=bool(session_mgr or encrypt)):
            return False

        if session_mgr and device_id:
            # ECDH mode: get session for device and encrypt with session key
            session_id = session_mgr.device_sessions.get(device_id)
            if not session_id:
                # No active session for this device
                return False
            
            encrypted = session_mgr.encrypt_with_session(payload, session_id)
            if not encrypted:
                # Session expired or encryption failed
                return False
            
            sock.sendto(encrypted, (host, port))
        elif encrypt:
            # PSK mode: use pre-shared key
            encrypted = encrypt_payload(payload)
            sock.sendto(encrypted, (host, port))
        else:
            # Unencrypted mode
            sock.sendto(payload, (host, port))
        return True
    except Exception as e:
        print(f"Send error: {e}", file=sys.stderr)
        return False


def extract_json_from_line(line: str) -> str | None:
    # find first '{' and use from there; fallback to whole line
    idx = line.find('{')
    if idx == -1:
        s = line.strip()
        return s if s else None
    else:
        return line[idx:].strip()


def _is_same_file(f, path: str) -> bool:
    """Return True if file object ``f`` refers to the same underlying file as ``path``.

    Uses inode/device when available; on platforms where inode isn't meaningful,
    falls back to modification time and size.
    """
    try:
        st1 = os.fstat(f.fileno())
        st2 = os.stat(path)
    except Exception:
        return False

    # prefer device/inode when present
    if hasattr(st1, 'st_ino') and hasattr(st2, 'st_ino') and st1.st_ino and st2.st_ino:
        return (st1.st_ino == st2.st_ino) and (getattr(st1, 'st_dev', None) == getattr(st2, 'st_dev', None))

    # fallback to mtime+size comparison
    return (int(st1.st_mtime) == int(st2.st_mtime)) and (st1.st_size == st2.st_size)


def tail_and_send(path: str, host: str, port: int, send_existing=False, once=False, interval=0.2, 
                  verbose=False, show_env=False, encrypt=True, session_mgr: 'SessionManager' = None, handshake_port: int = None, bind_ip: str = '127.0.0.1'):
    """Tail a file and send only new JSON lines as UDP datagrams (do not send existing lines)."""
    if not os.path.exists(path):
        raise FileNotFoundError(path)
    
    # For ECDH mode: create a UDP socket bound to handshake_port for handshake, send data to destination port
    # For PSK mode: create unbound socket
    if session_mgr:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        # SECURITY: Default bind to localhost only for safety
        # Use handshake_port if specified, otherwise use data port
        listen_port = handshake_port if handshake_port is not None else port
        sock.bind((bind_ip, listen_port))  # Bind to specified IP (default: localhost)
        sock.settimeout(1.0)  # Non-blocking with timeout
        logger.info(f"🔐 Listening for handshakes on {bind_ip}:{listen_port}")
        if handshake_port is not None and handshake_port != port:
            logger.info(f"📤 Will send data to {host}:{port}")
        handshake_sock = sock  # Use same socket for handshake and data
    else:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        handshake_sock = None

    def open_for_tail(read_existing: bool = False):
        f = open(path, 'r', encoding='utf-8', errors='ignore')
        if not read_existing:
            f.seek(0, os.SEEK_END)
        return f

    f = open_for_tail()
    try:
        while True:
            # In ECDH mode: check for incoming handshake messages
            if handshake_sock:
                try:
                    data, addr = handshake_sock.recvfrom(65535)  # Max UDP size

                    # SECURITY: Early validation before expensive parsing (DoS protection)
                    if not validate_handshake_message(data, addr):
                        # Message rejected by validation (rate limit, size, format, etc.)
                        continue

                    # Try to parse as handshake message (PLAINTEXT - no PSK encryption)
                    try:
                        # Use safe parser with size and depth limits
                        msg = safe_json_parse(data, max_size=_MAX_HANDSHAKE_MESSAGE_SIZE)
                        if not msg:
                            # Parsing failed or message too large
                            continue

                        if 'type' in msg and msg['type'] == 'ClientHello':
                            logger.info(f"📥 Received ClientHello from {addr}")
                            response = session_mgr.handle_client_hello(msg, addr)
                            # Send response in PLAINTEXT (no PSK encryption)
                            response_json = json.dumps(response).encode('utf-8')
                            handshake_sock.sendto(response_json, addr)
                            logger.info(f"📤 Sent ServerHello to {addr}")
                        elif 'type' in msg and msg['type'] == 'KeyConfirm':
                            logger.info(f"📥 Received KeyConfirm from {addr}")
                            response = session_mgr.handle_key_confirm(msg)
                            # Send response in PLAINTEXT (no PSK encryption)
                            response_json = json.dumps(response).encode('utf-8')
                            handshake_sock.sendto(response_json, addr)
                            if response.get('status') == 'ready':
                                logger.info(f"✅ Session established with device {msg.get('sessionId', 'unknown')[:8]}...")
                    except Exception as decode_err:
                        # Not a handshake message or malformed, ignore
                        logger.debug(f"Failed to parse handshake: {decode_err}")
                        pass
                except socket.timeout:
                    # No handshake message, continue
                    pass
                except Exception as e:
                    logger.debug(f"Handshake check error: {e}")
            
            line = f.readline()
            if not line:
                try:
                    cur_pos = f.tell()
                    size = os.path.getsize(path)
                except OSError:
                    try:
                        f.close()
                    except Exception:
                        pass
                    f = open_for_tail(True)
                    if once:
                        break
                    time.sleep(interval)
                    continue

                if cur_pos > size:
                    try:
                        f.close()
                    except Exception:
                        pass
                    f = open_for_tail(True)
                    if once:
                        break
                    time.sleep(interval)
                    continue

                if not _is_same_file(f, path):
                    try:
                        f.close()
                    except Exception:
                        pass
                    f = open_for_tail(True)
                    if once:
                        break
                    time.sleep(interval)
                    continue

                if once:
                    break
                time.sleep(interval)
                continue

            jsonpart = extract_json_from_line(line)
            if not jsonpart:
                continue
            
            # Send to all devices with active sessions (ECDH mode) or broadcast (PSK mode)
            if session_mgr:
                # Send to each device with an active session
                sent_count = 0
                for device_id, session_id in list(session_mgr.device_sessions.items()):
                    # Get device info for logging
                    device = session_mgr.authorized_devices.get(device_id)
                    device_name = device.name if device else device_id[:8]
                    
                    if send_udp(jsonpart.encode('utf-8'), host, port, sock, encrypt=True, 
                               session_mgr=session_mgr, device_id=device_id):
                        sent_count += 1
                    else:
                        logger.warning(f"⚠️ Failed to send to {device_name} (session may have expired)")
                
                sent = sent_count > 0
            else:
                # PSK or unencrypted mode: send once
                sent = send_udp(jsonpart.encode('utf-8'), host, port, sock, encrypt=encrypt)
            if verbose or show_env:
                ts = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime())
                if sent:
                    if show_env:
                        try:
                            parsed = safe_json_parse(jsonpart, max_size=_MAX_DATA_MESSAGE_SIZE)
                            if parsed is not None:
                                env = parsed.get('environment', {})
                                temp = parsed.get('temperature', env.get('temperature'))
                                pres = parsed.get('pressure', env.get('pressure'))
                                wind = env.get('wind') or {'speed': env.get('windSpeed'), 'direction': env.get('windDirection')}
                                wspeed = wind.get('speed')
                                wdir = wind.get('direction')
                                print(f"{ts} SENT {host}:{port} temp={temp}C pres={pres} wind={wspeed}@{wdir}")
                            else:
                                print(f"{ts} SENT {host}:{port} {jsonpart}")
                        except Exception:
                            print(f"{ts} SENT {host}:{port} {jsonpart}")
                    else:
                        print(f"{ts} SENT {host}:{port} {jsonpart}")
                else:
                    print(f"{ts} ERROR {host}:{port} {jsonpart}")
    finally:
        try:
            f.close()
        except Exception:
            pass
        sock.close()
        # handshake_sock is same as sock in ECDH mode, don't close twice


# New feature: repeat the last line every X seconds
def repeat_last_line(path: str, host: str, port: int, interval=5.0, verbose=False, show_env=False, encrypt=True, 
                     session_mgr: 'SessionManager' = None, handshake_port: int = None, bind_ip: str = '127.0.0.1'):
    """Send the last line of the file as a UDP datagram every <interval> seconds."""
    if not os.path.exists(path):
        raise FileNotFoundError(path)
    
    # For ECDH mode: create a UDP socket bound to handshake_port for handshake, send data to destination port
    # For PSK mode: create unbound socket
    if session_mgr:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        # SECURITY: Default bind to localhost only for safety
        # Use handshake_port if specified, otherwise use data port
        listen_port = handshake_port if handshake_port is not None else port
        sock.bind((bind_ip, listen_port))  # Bind to specified IP (default: localhost)
        sock.settimeout(0.1)  # Non-blocking with short timeout
        logger.info(f"🔐 Listening for handshakes on {bind_ip}:{listen_port}")
        if handshake_port is not None and handshake_port != port:
            logger.info(f"📤 Will send data to {host}:{port}")
        handshake_sock = sock  # Use same socket for handshake and data
    else:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        handshake_sock = None
    
    try:
        last_sent = None
        last_send_time = 0
        
        while True:
            # In ECDH mode: check for incoming handshake messages
            if handshake_sock:
                try:
                    data, addr = handshake_sock.recvfrom(65535)  # Max UDP size

                    # SECURITY: Early validation before expensive parsing (DoS protection)
                    if not validate_handshake_message(data, addr):
                        # Message rejected by validation (rate limit, size, format, etc.)
                        continue

                    # Parse handshake message (PLAINTEXT - no PSK encryption)
                    try:
                        logger.debug(f"📦 Received {len(data)} bytes from {addr}")

                        # Handshake messages are sent in PLAINTEXT for proper ECDH
                        msg = safe_json_parse(data, max_size=_MAX_HANDSHAKE_MESSAGE_SIZE)
                        if not msg:
                            logger.debug("Failed to parse handshake message or message too large")
                            continue

                        # SECURITY: Avoid logging full message contents; show a short preview/truncated
                        try:
                            preview = json.dumps(msg)
                            logger.debug(f"📄 Full message (truncated): {preview[:200]}")
                        except Exception:
                            logger.debug("📄 Full message: <unserializable>")

                        logger.info(f"📨 Received {msg.get('type', 'Unknown')} from {addr[0]}")
                        logger.debug(f"📊 Message keys: {list(msg.keys())}")

                        if 'type' in msg and msg['type'] == 'ClientHello':
                            logger.info(f"📥 Received ClientHello from {addr}")
                            response = session_mgr.handle_client_hello(msg, addr)
                            # Send response in PLAINTEXT (no PSK encryption)
                            response_json = json.dumps(response).encode('utf-8')
                            handshake_sock.sendto(response_json, addr)
                            logger.info(f"📤 Sent ServerHello to {addr}")
                        elif 'type' in msg and msg['type'] == 'KeyConfirm':
                            logger.info(f"📥 Received KeyConfirm from {addr}")
                            response = session_mgr.handle_key_confirm(msg)
                            # Send response in PLAINTEXT (no PSK encryption)
                            response_json = json.dumps(response).encode('utf-8')
                            handshake_sock.sendto(response_json, addr)
                            if response.get('status') == 'ready':
                                logger.info(f"✅ Session established with device {msg.get('sessionId', 'unknown')[:8]}...")
                    except Exception as decode_err:
                        # Not a handshake message or malformed, ignore
                        logger.debug(f"Failed to parse handshake: {decode_err}")
                        pass
                    except Exception as unexpected_err:
                        # Log unexpected errors for debugging
                        logger.warning(f"⚠️ Unexpected error parsing handshake: {unexpected_err}")
                except socket.timeout:
                    # No handshake message, continue
                    pass
                except Exception as e:
                    logger.debug(f"Handshake check error: {e}")
            
            # Send data at regular interval
            current_time = time.time()
            if current_time - last_send_time < interval:
                time.sleep(0.1)  # Short sleep to check handshakes frequently
                continue
            
            try:
                with open(path, 'r', encoding='utf-8', errors='ignore') as f:
                    lines = f.readlines()
                if not lines:
                    time.sleep(0.1)
                    continue
                last_line = lines[-1]
                jsonpart = extract_json_from_line(last_line)
                if not jsonpart:
                    time.sleep(0.1)
                    continue
                
                # Send to all devices with active sessions (ECDH mode) or broadcast (PSK mode)
                if session_mgr:
                    sent_count = 0
                    for device_id, session_id in list(session_mgr.device_sessions.items()):
                        # Get device info for logging
                        device = session_mgr.authorized_devices.get(device_id)
                        device_name = device.name if device else device_id[:8]
                        
                        if send_udp(jsonpart.encode('utf-8'), host, port, sock, encrypt=True, 
                                   session_mgr=session_mgr, device_id=device_id):
                            sent_count += 1
                        else:
                            logger.warning(f"⚠️ Failed to send to {device_name} (session may have expired)")
                    sent = sent_count > 0
                else:
                    sent = send_udp(jsonpart.encode('utf-8'), host, port, sock, encrypt=encrypt)
                
                last_send_time = current_time
                if verbose or show_env:
                    ts = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime())
                    if sent:
                        if show_env:
                            try:
                                parsed = safe_json_parse(jsonpart, max_size=_MAX_DATA_MESSAGE_SIZE)
                                if parsed is not None:
                                    env = parsed.get('environment', {})
                                    temp = parsed.get('temperature', env.get('temperature'))
                                    pres = parsed.get('pressure', env.get('pressure'))
                                    wind = parsed.get('wind') or {'speed': env.get('windSpeed'), 'direction': env.get('windDirection')}
                                    wspeed = wind.get('speed')
                                    wdir = wind.get('direction')
                                    print(f"{ts} REPEAT {host}:{port} temp={temp}C pres={pres} wind={wspeed}@{wdir}")
                                else:
                                    print(f"{ts} REPEAT {host}:{port} {jsonpart}")
                            except Exception:
                                print(f"{ts} REPEAT {host}:{port} {jsonpart}")
                        else:
                            print(f"{ts} REPEAT {host}:{port} {jsonpart}")
                    else:
                        print(f"{ts} ERROR {host}:{port} {jsonpart}")
            except Exception as e:
                print(f"Error reading/sending: {e}", file=sys.stderr)
    finally:
        sock.close()
        # handshake_sock is same as sock in ECDH mode, don't close twice


def main(argv=None):
    p = argparse.ArgumentParser(description='Forward parsed JSONL as UDP datagrams with optional ECDH handshake')
    p.add_argument('--file', '-f', default=DEFAULT_FILE, help='Path to JSONL file')
    p.add_argument('--host', default=DEFAULT_HOST, help='Destination host')
    p.add_argument('--port', '-p', type=int, default=DEFAULT_PORT, help='Destination port')
    p.add_argument('--bind-ip', default='127.0.0.1', 
                   help='IP address to bind to for ECDH handshakes (default: 127.0.0.1 for localhost only). '
                        'Use 0.0.0.0 to bind to all interfaces (INSECURE - requires confirmation)')
    p.add_argument('--handshake-port', type=int, default=None, 
                   help='Port to listen for ECDH handshakes (default: same as --port). '
                        'Use different port when sender and receiver are on same PC.')
    p.add_argument('--interval', type=float, default=0.2, help='Polling interval in seconds')
    p.add_argument('--verbose', '-v', action='store_true', help='Verbose output')
    p.add_argument('--repeat-last', action='store_true', help='Repeat the last line every <interval> seconds')
    p.add_argument('--show-env', action='store_true', help='Print temperature/pressure/wind when sending')
    p.add_argument('--no-encrypt', action='store_true', help='Disable AES-GCM encryption (not recommended)')
    p.add_argument('--use-handshake', action='store_true', help='Enable ECDH handshake mode (requires crypto_handshake.py)')
    p.add_argument('--authorized-devices', default='authorized_devices.json', help='Path to authorized devices file')
    p.add_argument('--aircraft', default=None, help='Aircraft name to send in handshake')
    args = p.parse_args(argv)

    # Setup logging
    log_level = logging.INFO if args.verbose else logging.WARNING
    logging.basicConfig(
        level=log_level,
        format='%(asctime)s [%(levelname)s] %(message)s',
        datefmt='%Y-%m-%d %H:%M:%S'
    )
    
    # Check ECDH mode requirements
    if args.use_handshake:
        if not HANDSHAKE_AVAILABLE:
            print("❌ Error: --use-handshake requires crypto_handshake.py", file=sys.stderr)
            print("   Make sure crypto_handshake.py is in the same directory", file=sys.stderr)
            return 2
        print("🔐 ECDH Handshake Mode ENABLED")
        print(f"📂 Authorized devices: {args.authorized_devices}")
        
        # Security check: warn if binding to all interfaces
        check_bind_security(args.bind_ip)
        
        session_mgr = SessionManager(
            authorized_devices_path=args.authorized_devices,
            aircraft_name=args.aircraft
        )
    else:
        session_mgr = None
        # Only check PSK security if NOT using ECDH
        encrypt = not args.no_encrypt
        if encrypt:
            check_psk_security()

    encrypt = not args.no_encrypt
    enc_status = "🔒 ECDH-AES-GCM" if args.use_handshake else ("🔒 PSK-AES-GCM" if encrypt else "⚠️ UNENCRYPTED")
    
    try:
        if args.repeat_last:
            print(f"Repeating last line every {args.interval} seconds from {args.file} to {args.host}:{args.port} ({enc_status})")
            repeat_last_line(args.file, args.host, args.port, interval=args.interval, verbose=args.verbose, 
                           show_env=args.show_env, encrypt=encrypt, session_mgr=session_mgr, handshake_port=args.handshake_port, bind_ip=args.bind_ip)
        else:
            print(f"Forwarding {args.file} to {args.host}:{args.port} (forward only new lines) ({enc_status})")
            while True:
                try:
                    tail_and_send(args.file, args.host, args.port, send_existing=False, once=False, 
                                interval=args.interval, verbose=args.verbose, show_env=args.show_env, 
                                encrypt=encrypt, session_mgr=session_mgr, handshake_port=args.handshake_port, bind_ip=args.bind_ip)
                except KeyboardInterrupt:
                    raise
                except FileNotFoundError:
                    print(f"File not found: {args.file}")
                    time.sleep(5)
                    continue
                except Exception:
                    import traceback
                    print("Unhandled error in tail_and_send, restarting in 5s:", file=sys.stderr)
                    traceback.print_exc()
                    time.sleep(5)
                    continue
    except FileNotFoundError:
        print(f"File not found: {args.file}")
        return 2
    except KeyboardInterrupt:
        print("Interrupted")
    finally:
        # Cleanup session manager
        if session_mgr:
            session_mgr.stop_cleanup_thread()
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
