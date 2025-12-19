"""
ECDH Client for DataPad Receiver
Implements client-side ECDH handshake to establish secure session with DCS sender
Compatible with crypto_handshake.py SessionManager protocol
"""

import json
import logging
import socket
import time
import base64
import hmac
from typing import Optional
from dataclasses import dataclass

from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives.asymmetric.ec import EllipticCurvePublicKey

# GCM nonce validation helper
try:
    from .gcm_nonce import default_gcm_nonce_manager
except Exception:
    from gcm_nonce import default_gcm_nonce_manager  # fallback for direct script run

logger = logging.getLogger(__name__)


@dataclass
class ECDHSession:
    """Active ECDH session with sender"""
    session_id: str
    session_key: bytes
    established_at: float
    last_activity: float
    
    def is_expired(self, timeout_seconds: int = 900) -> bool:
        """Check if session has expired (15 minutes default)"""
        return (time.time() - self.last_activity) > timeout_seconds
    
    def update_activity(self):
        """Update last activity timestamp"""
        self.last_activity = time.time()


class ECDHClient:
    """
    Client-side ECDH handshake implementation
    Initiates handshake with DCS sender to establish encrypted session
    Uses JSON-based protocol compatible with crypto_handshake.py SessionManager
    """
    
    def __init__(self, device_id: str, device_name: str = "Python DataPad", private_key_pem: Optional[str] = None, password: Optional[str] = None):
        self.device_id = device_id
        self.device_name = device_name
        
        # Load provided private key PEM OR generate new key
        if private_key_pem:
            try:
                password_bytes = password.encode('utf-8') if password else None
                self.private_key = serialization.load_pem_private_key(
                    private_key_pem.encode('utf-8'),
                    password=password_bytes,
                    backend=default_backend()
                )
            except (TypeError, ValueError) as e:
                logger.error(f"Failed to load private key, password may be incorrect: {e}")
                raise  # Re-raise to signal failure to the caller
            except Exception as e:
                logger.error(f"Failed to load provided private key PEM: {e}")
                self.private_key = ec.generate_private_key(ec.SECP256R1(), default_backend())
        else:
            self.private_key = ec.generate_private_key(ec.SECP256R1(), default_backend())

        self.public_key = self.private_key.public_key()
        
        # Active session
        self.session: Optional[ECDHSession] = None
        
        # Nonce counter for encryption
        self._nonce_counter = 0
        
        logger.info(f"🔐 ECDH Client initialized")
        logger.info(f"📱 Device ID: {self.device_id}")
        logger.info(f"📝 Device Name: {self.device_name}")
    
    def _generate_nonce(self) -> bytes:
        """Generate counter-based nonce for CLIENT (0x00 prefix) - used for DATA encryption with session key"""
        self._nonce_counter += 1
        if self._nonce_counter >= 2**64:
            raise RuntimeError("Nonce counter exhausted!")
        # Format: 0x00 (client) + 3 bytes reserved + 8 bytes counter
        return bytes([0x00, 0x00, 0x00, 0x00]) + self._nonce_counter.to_bytes(8, 'big')
    
    def perform_handshake(self, target_ip: str, target_port: int, timeout: float = 10.0, sock: Optional[socket.socket] = None) -> bool:
        """
        Perform ECDH handshake with DCS sender using JSON protocol
        If a socket is provided, it will be used (useful when already bound to port).
        """
        own_sock = False
        try:
            logger.info(f"🤝 Initiating ECDH handshake with {target_ip}:{target_port}")
            
            # Create or use provided socket
            if sock is None:
                sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                own_sock = True
            prev_timeout = sock.gettimeout()
            sock.settimeout(timeout)
            
            # Step 1: Send ClientHello (PLAINTEXT - no PSK encryption)
            client_hello = self._build_client_hello()
            logger.debug(f"📤 ClientHello: {client_hello}")
            
            sock.sendto(client_hello, (target_ip, target_port))
            logger.info(f"📤 Sent ClientHello ({len(client_hello)} bytes) [PLAINTEXT]")
            
            # Step 2: Receive ServerHello (PLAINTEXT)
            response, addr = sock.recvfrom(4096)
            logger.info(f"📥 Received ServerHello ({len(response)} bytes) from {addr} [PLAINTEXT]")
            
            server_hello = json.loads(response.decode('utf-8'))
            logger.info(f"📨 ServerHello type: {server_hello.get('type')}")
            
            if server_hello.get('type') == 'Error':
                error_msg = server_hello.get('message', 'Unknown error')
                logger.error(f"❌ Server error: {error_msg}")
                return False
            
            if server_hello.get('type') != 'ServerHello':
                logger.error(f"❌ Unexpected response type: {server_hello.get('type')}")
                return False
            
            # Step 3: Derive session key
            session_id = server_hello.get('sessionId')
            server_pubkey_b64 = server_hello.get('publicKey')
            salt_b64 = server_hello.get('salt')
            server_hmac_b64 = server_hello.get('serverHmac')
            
            if not all([session_id, server_pubkey_b64, salt_b64]):
                logger.error("❌ Missing required fields in ServerHello")
                return False
            
            # Deserialize server public key
            server_pubkey_der = base64.b64decode(server_pubkey_b64)
            server_public_key = serialization.load_der_public_key(
                server_pubkey_der,
                backend=default_backend()
            )

            # Validate server public key: must be EC P-256 and point must be on curve
            if not isinstance(server_public_key, EllipticCurvePublicKey):
                logger.error("Server public key is not an EC public key")
                return False

            curve_name = server_public_key.curve.name
            if curve_name != 'secp256r1':
                logger.error(f"Server public key uses unexpected curve: {curve_name}")
                return False

            # Validate point by reconstructing public numbers (will raise on invalid points)
            try:
                pubnums = server_public_key.public_numbers()
                # Reconstruct using known curve type
                ec.EllipticCurvePublicNumbers(pubnums.x, pubnums.y, ec.SECP256R1()).public_key(default_backend())
            except Exception as e:
                logger.error(f"Server public key failed point validation: {e}")
                return False
            
            # Perform ECDH
            shared_secret = self.private_key.exchange(ec.ECDH(), server_public_key)
            
            # Derive session key with salt from server
            salt = base64.b64decode(salt_b64)
            session_key = HKDF(
                algorithm=hashes.SHA256(),
                length=32,
                salt=salt,
                info=b'DataPad-Session-Key',
                backend=default_backend()
            ).derive(shared_secret)
            
            logger.info(f"🔑 Session key derived via ECDH+HKDF")
            logger.debug(f"   Session ID: {session_id}")
            logger.debug(f"   Shared secret (first 16 bytes hex): {shared_secret[:16].hex()}")
            logger.debug(f"   Salt (hex): {salt.hex()}")
            logger.debug(f"   Session key (first 16 bytes hex): {session_key[:16].hex()}")
            
            # Verify server HMAC
            if server_hmac_b64:
                hmac_data = f"server_{session_id}".encode()
                expected_hmac = hmac.new(
                    session_key,
                    hmac_data,
                    'sha256'
                ).digest()
                server_hmac = base64.b64decode(server_hmac_b64)
                
                logger.debug(f"   HMAC data: {hmac_data}")
                logger.debug(f"   Expected HMAC (first 16 bytes hex): {expected_hmac[:16].hex()}")
                logger.debug(f"   Server HMAC (first 16 bytes hex): {server_hmac[:16].hex()}")
                
                if server_hmac != expected_hmac:
                    logger.error("❌ Server HMAC verification failed!")
                    return False
                logger.info("✅ Server HMAC verified")
            
            # Step 4: Send KeyConfirm (PLAINTEXT)
            client_hmac = hmac.new(
                session_key,
                session_id.encode(),
                'sha256'
            ).digest()
            
            key_confirm = {
                "type": "KeyConfirm",
                "sessionId": session_id,
                "deviceId": self.device_id,
                "hmac": base64.b64encode(client_hmac).decode('utf-8'),
                "timestamp": int(time.time() * 1000)
            }
            
            key_confirm_bytes = json.dumps(key_confirm).encode('utf-8')
            sock.sendto(key_confirm_bytes, (target_ip, target_port))
            logger.info(f"📤 Sent KeyConfirm ({len(key_confirm_bytes)} bytes) [PLAINTEXT]")
            
            # Create session IMMEDIATELY after sending KeyConfirm
            # The DCS server doesn't send a separate "Ready" message - it starts sending encrypted data right away
            self.session = ECDHSession(
                session_id=session_id,
                session_key=session_key,
                established_at=time.time(),
                last_activity=time.time()
            )
            
            logger.info(f"✅ Handshake successful! Session established")
            logger.info(f"🔑 Session ID: {self.session.session_id[:16]}...")
            return True
                
        except socket.timeout:
            logger.error(f"❌ Handshake timeout - no response from {target_ip}:{target_port}")
            return False
        except Exception as e:
            logger.error(f"❌ Handshake error: {e}", exc_info=True)
            return False
        finally:
            try:
                if not own_sock:
                    # restore timeout
                    sock.settimeout(prev_timeout)
            except Exception:
                pass
            if own_sock:
                try:
                    sock.close()
                except Exception:
                    pass
    
    def _build_client_hello(self) -> bytes:
        """
        Build ClientHello JSON message
        """
        # Export client public key as DER
        pubkey_der = self.public_key.public_bytes(
            encoding=serialization.Encoding.DER,
            format=serialization.PublicFormat.SubjectPublicKeyInfo
        )
        pubkey_b64 = base64.b64encode(pubkey_der).decode('utf-8')
        
        client_hello = {
            "type": "ClientHello",
            "version": "1.0",
            "deviceId": self.device_id,
            "deviceName": self.device_name,
            "publicKey": pubkey_b64,
            "timestamp": int(time.time() * 1000)
        }
        
        return json.dumps(client_hello).encode('utf-8')
    
    def decrypt_payload(self, encrypted_data: bytes) -> Optional[bytes]:
        """
        Decrypt data using session key
        Format: nonce (12 bytes) + ciphertext + tag (16 bytes)
        """
        if not self.session:
            logger.error("No active session - cannot decrypt")
            return None
        
        try:
            if len(encrypted_data) < 28:  # 12 (nonce) + 16 (tag) minimum
                logger.error(f"Encrypted data too short: {len(encrypted_data)} bytes")
                return None
            
            # Extract nonce (first 12 bytes)
            nonce = encrypted_data[:12]
            # Extract ciphertext + tag (remaining bytes)
            ciphertext = encrypted_data[12:]
            
            # Decrypt using AES-GCM with session key
            aesgcm = AESGCM(self.session.session_key)
            plaintext = aesgcm.decrypt(nonce, ciphertext, None)
            
            # Update session activity
            self.session.update_activity()
            
            return plaintext
            
        except Exception as e:
            logger.error(f"Decryption failed: {e}")
            return None
    
    def is_session_valid(self) -> bool:
        """Check if session exists and is not expired"""
        if not self.session:
            return False
        return not self.session.is_expired()
    
    def close_session(self):
        """Close current session"""
        if self.session:
            logger.info(f"🔒 Closing session {self.session.session_id}")
            self.session = None
