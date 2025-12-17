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

# Global nonce counter for SERVER side (0x01 prefix)
_nonce_counter = 0
_nonce_lock = __import__('threading').Lock()
_received_nonces = set()

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
    Returns True if nonce is valid (not replayed), False otherwise.
    """
    if len(nonce) != 12:
        return False

    # Extract counter (bytes 4-11)
    counter = int.from_bytes(nonce[4:12], 'big')

    if counter in _received_nonces:
        logger.warning(f"⚠️ Replay attack detected! Nonce counter: {counter}")
        return False

    _received_nonces.add(counter)

    # Cleanup old nonces to prevent memory leak
    if len(_received_nonces) > 10000:
        old_nonces = sorted(_received_nonces)[:5000]
        _received_nonces.difference_update(old_nonces)

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
                  verbose=False, show_env=False, encrypt=True, session_mgr: 'SessionManager' = None):
    """Tail a file and send only new JSON lines as UDP datagrams (do not send existing lines)."""
    if not os.path.exists(path):
        raise FileNotFoundError(path)
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    
    # For ECDH mode: create a UDP listener socket for handshake responses
    handshake_sock = None
    if session_mgr:
        handshake_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        handshake_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        handshake_sock.bind(('0.0.0.0', port))  # Listen on same port for handshake
        handshake_sock.settimeout(1.0)  # Non-blocking with timeout
        logger.info(f"🔐 Listening for handshakes on port {port}")

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
                    data, addr = handshake_sock.recvfrom(4096)
                    # Try to parse as handshake message
                    try:
                        msg_str = data.decode('utf-8')
                        msg = json.loads(msg_str)
                        if 'type' in msg and msg['type'] == 'ClientHello':
                            logger.info(f"📥 Received ClientHello from {addr}")
                            response = session_mgr.handle_client_hello(msg, addr)
                            response_json = json.dumps(response).encode('utf-8')
                            handshake_sock.sendto(response_json, addr)
                            logger.info(f"📤 Sent ServerHello to {addr}")
                        elif 'type' in msg and msg['type'] == 'KeyConfirm':
                            logger.info(f"📥 Received KeyConfirm from {addr}")
                            response = session_mgr.handle_key_confirm(msg)
                            response_json = json.dumps(response).encode('utf-8')
                            handshake_sock.sendto(response_json, addr)
                            if response.get('status') == 'ready':
                                logger.info(f"✅ Session established with device {msg.get('sessionId', 'unknown')[:8]}...")
                    except (json.JSONDecodeError, UnicodeDecodeError):
                        # Not a handshake message, ignore
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
                            obj = json.loads(jsonpart)
                            env = obj.get('environment', {})
                            temp = obj.get('temperature', env.get('temperature'))
                            pres = obj.get('pressure', env.get('pressure'))
                            wind = env.get('wind') or {'speed': env.get('windSpeed'), 'direction': env.get('windDirection')}
                            wspeed = wind.get('speed')
                            wdir = wind.get('direction')
                            print(f"{ts} SENT {host}:{port} temp={temp}C pres={pres} wind={wspeed}@{wdir}")
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
        if handshake_sock:
            handshake_sock.close()


# New feature: repeat the last line every X seconds
def repeat_last_line(path: str, host: str, port: int, interval=5.0, verbose=False, show_env=False, encrypt=True, 
                     session_mgr: 'SessionManager' = None):
    """Send the last line of the file as a UDP datagram every <interval> seconds."""
    if not os.path.exists(path):
        raise FileNotFoundError(path)
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    
    # For ECDH mode: create a UDP listener socket for handshake responses
    handshake_sock = None
    if session_mgr:
        handshake_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        handshake_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        handshake_sock.bind(('0.0.0.0', port))  # Listen on same port for handshake
        handshake_sock.settimeout(0.1)  # Non-blocking with short timeout
        logger.info(f"🔐 Listening for handshakes on port {port}")
    
    try:
        last_sent = None
        last_send_time = 0
        
        while True:
            # In ECDH mode: check for incoming handshake messages
            if handshake_sock:
                try:
                    data, addr = handshake_sock.recvfrom(4096)
                    # Try to decrypt and parse as handshake message
                    try:
                        # Handshake messages are encrypted with PSK
                        logger.info(f"📦 Received {len(data)} bytes from {addr}")
                        logger.info(f"📦 First 32 bytes (hex): {data[:32].hex()}")
                        
                        aesgcm = AESGCM(PRE_SHARED_KEY)
                        if len(data) >= 28:  # 12 (nonce) + 16 (tag) minimum
                            nonce = data[:12]
                            ciphertext = data[12:]
                            try:
                                decrypted = aesgcm.decrypt(nonce, ciphertext, None)
                                msg_str = decrypted.decode('utf-8')
                                logger.info(f"✅ Successfully decrypted message")
                            except Exception as decrypt_err:
                                logger.error(f"❌ Decryption failed: {decrypt_err}")
                                # Try plain text as fallback
                                logger.warning(f"Trying plain text fallback")
                                msg_str = data.decode('utf-8')
                        else:
                            # Try plain text (for debugging)
                            logger.warning(f"Received short packet ({len(data)} bytes), trying plain text")
                            msg_str = data.decode('utf-8')
                        
                        logger.info(f"📄 Full message: {msg_str}")
                        msg = json.loads(msg_str)
                        logger.info(f"📨 Parsed message type: {msg.get('type')} from {addr}")
                        logger.info(f"📊 Message keys: {list(msg.keys())}")
                        
                        if 'type' in msg and msg['type'] == 'ClientHello':
                            logger.info(f"📥 Received ClientHello from {addr}")
                            response = session_mgr.handle_client_hello(msg, addr)
                            response_json = json.dumps(response).encode('utf-8')
                            # Encrypt response with PSK
                            encrypted_response = encrypt_payload(response_json, PRE_SHARED_KEY)
                            handshake_sock.sendto(encrypted_response, addr)
                            logger.info(f"📤 Sent ServerHello to {addr}")
                        elif 'type' in msg and msg['type'] == 'KeyConfirm':
                            logger.info(f"📥 Received KeyConfirm from {addr}")
                            response = session_mgr.handle_key_confirm(msg)
                            response_json = json.dumps(response).encode('utf-8')
                            # Encrypt response with PSK
                            encrypted_response = encrypt_payload(response_json, PRE_SHARED_KEY)
                            handshake_sock.sendto(encrypted_response, addr)
                            if response.get('status') == 'ready':
                                logger.info(f"✅ Session established with device {msg.get('sessionId', 'unknown')[:8]}...")
                    except (json.JSONDecodeError, UnicodeDecodeError, Exception) as decode_err:
                        # Not a handshake message or decryption failed, ignore
                        logger.debug(f"Failed to parse handshake: {decode_err}")
                        pass
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
                                obj = json.loads(jsonpart)
                                env = obj.get('environment', {})
                                temp = obj.get('temperature', env.get('temperature'))
                                pres = obj.get('pressure', env.get('pressure'))
                                wind = env.get('wind') or {'speed': env.get('windSpeed'), 'direction': env.get('windDirection')}
                                wspeed = wind.get('speed')
                                wdir = wind.get('direction')
                                print(f"{ts} REPEAT {host}:{port} temp={temp}C pres={pres} wind={wspeed}@{wdir}")
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
        if handshake_sock:
            handshake_sock.close()


def main(argv=None):
    p = argparse.ArgumentParser(description='Forward parsed JSONL as UDP datagrams with optional ECDH handshake')
    p.add_argument('--file', '-f', default=DEFAULT_FILE, help='Path to JSONL file')
    p.add_argument('--host', default=DEFAULT_HOST, help='Destination host')
    p.add_argument('--port', '-p', type=int, default=DEFAULT_PORT, help='Destination port')
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
                           show_env=args.show_env, encrypt=encrypt, session_mgr=session_mgr)
        else:
            print(f"Forwarding {args.file} to {args.host}:{args.port} (forward only new lines) ({enc_status})")
            while True:
                try:
                    tail_and_send(args.file, args.host, args.port, send_existing=False, once=False, 
                                interval=args.interval, verbose=args.verbose, show_env=args.show_env, 
                                encrypt=encrypt, session_mgr=session_mgr)
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
