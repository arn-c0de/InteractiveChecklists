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
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.backends import default_backend

DEFAULT_FILE = os.path.expanduser(r"~\Saved Games\DCS\Scripts\player_aircraft_parsed.jsonl")
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 5010

# Pre-Shared Key (32 bytes for AES-256) - MUST match Android app!
# Change this to your own random key in production!
# WARNING: This file ships with a default key for convenience — change it before using in production.
DEFAULT_PRE_SHARED_KEY = b'DCS_DataPad_Secret_Key_32BYTES!!'
PRE_SHARED_KEY = DEFAULT_PRE_SHARED_KEY

# If the default key is still in use, warn the operator (printed to stderr)
if PRE_SHARED_KEY == DEFAULT_PRE_SHARED_KEY:
    sys.stderr.write("WARNING: Using default PRE_SHARED_KEY in forward_parsed_udp.py. Change this key before running in production. See docs/technical/AES_GCM_ENCRYPTION.md\n")

def encrypt_payload(data: bytes, key: bytes = PRE_SHARED_KEY) -> bytes:
    """Encrypt data using AES-GCM with a random 12-byte nonce.
    
    Returns: nonce (12 bytes) + ciphertext + tag (16 bytes)
    """
    aesgcm = AESGCM(key)
    nonce = os.urandom(12)  # 96-bit nonce recommended for GCM
    ciphertext = aesgcm.encrypt(nonce, data, None)  # no additional authenticated data
    return nonce + ciphertext


def send_udp(payload: bytes, host: str, port: int, sock: socket.socket, encrypt: bool = True) -> bool:
    try:
        if encrypt:
            encrypted = encrypt_payload(payload)
            sock.sendto(encrypted, (host, port))
        else:
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


def tail_and_send(path: str, host: str, port: int, send_existing=False, once=False, interval=0.2, verbose=False, show_env=False, encrypt=True):
    """Tail a file and send only new JSON lines as UDP datagrams (do not send existing lines)."""
    if not os.path.exists(path):
        raise FileNotFoundError(path)
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    def open_for_tail(read_existing: bool = False):
        f = open(path, 'r', encoding='utf-8', errors='ignore')
        if not read_existing:
            f.seek(0, os.SEEK_END)
        return f

    f = open_for_tail()
    try:
        while True:
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


# New feature: repeat the last line every X seconds
def repeat_last_line(path: str, host: str, port: int, interval=5.0, verbose=False, show_env=False, encrypt=True):
    """Send the last line of the file as a UDP datagram every <interval> seconds."""
    if not os.path.exists(path):
        raise FileNotFoundError(path)
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        last_sent = None
        while True:
            try:
                with open(path, 'r', encoding='utf-8', errors='ignore') as f:
                    lines = f.readlines()
                if not lines:
                    time.sleep(interval)
                    continue
                last_line = lines[-1]
                jsonpart = extract_json_from_line(last_line)
                if not jsonpart:
                    time.sleep(interval)
                    continue
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
                                print(f"{ts} REPEAT {host}:{port} temp={temp}C pres={pres} wind={wspeed}@{wdir}")
                            except Exception:
                                print(f"{ts} REPEAT {host}:{port} {jsonpart}")
                        else:
                            print(f"{ts} REPEAT {host}:{port} {jsonpart}")
                    else:
                        print(f"{ts} ERROR {host}:{port} {jsonpart}")
            except Exception as e:
                print(f"Error reading/sending: {e}", file=sys.stderr)
            time.sleep(interval)
    finally:
        sock.close()


def main(argv=None):
    p = argparse.ArgumentParser(description='Forward parsed JSONL as UDP datagrams')
    p.add_argument('--file', '-f', default=DEFAULT_FILE, help='Path to JSONL file')
    p.add_argument('--host', default=DEFAULT_HOST, help='Destination host')
    p.add_argument('--port', '-p', type=int, default=DEFAULT_PORT, help='Destination port')
    p.add_argument('--interval', type=float, default=0.2, help='Polling interval in seconds')
    p.add_argument('--verbose', '-v', action='store_true', help='Verbose output')
    p.add_argument('--repeat-last', action='store_true', help='Repeat the last line every <interval> seconds')
    p.add_argument('--show-env', action='store_true', help='Print temperature/pressure/wind when sending')
    p.add_argument('--no-encrypt', action='store_true', help='Disable AES-GCM encryption (not recommended)')
    args = p.parse_args(argv)

    encrypt = not args.no_encrypt
    enc_status = "🔒 AES-GCM encrypted" if encrypt else "⚠️ UNENCRYPTED"
    
    try:
        if args.repeat_last:
            print(f"Repeating last line every {args.interval} seconds from {args.file} to {args.host}:{args.port} ({enc_status})")
            repeat_last_line(args.file, args.host, args.port, interval=args.interval, verbose=args.verbose, show_env=args.show_env, encrypt=encrypt)
        else:
            print(f"Forwarding {args.file} to {args.host}:{args.port} (forward only new lines) ({enc_status})")
            while True:
                try:
                    tail_and_send(args.file, args.host, args.port, send_existing=False, once=False, interval=args.interval, verbose=args.verbose, show_env=args.show_env, encrypt=encrypt)
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
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
