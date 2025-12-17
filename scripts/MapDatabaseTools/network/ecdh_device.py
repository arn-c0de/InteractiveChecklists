"""
Persistent ECDH device key management
Stores device_id and private key PEM to a local JSON file so keys persist across restarts.
"""
import os
import json
import base64
from typing import Optional, Tuple
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.backends import default_backend

DEFAULT_DEVICE_FILE = os.path.join(os.path.dirname(__file__), '..', 'ecdh_device.json')


def load_device(file_path: Optional[str] = None) -> Optional[dict]:
    """Load device info (device_id, private_key_pem) from file if exists"""
    if file_path is None:
        file_path = DEFAULT_DEVICE_FILE
    try:
        if not os.path.exists(file_path):
            return None
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        return data
    except Exception:
        return None


def save_device(device_id: str, private_key_pem: str, file_path: Optional[str] = None) -> None:
    """Save device info to file"""
    if file_path is None:
        file_path = DEFAULT_DEVICE_FILE
    data = {
        'deviceId': device_id,
        'privateKeyPem': private_key_pem
    }
    with open(file_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2)


def generate_device(device_id: Optional[str] = None, file_path: Optional[str] = None) -> dict:
    """Generate a new EC P-256 keypair and store it; returns dict with deviceId and keys"""
    # Generate device id if not provided
    if device_id is None:
        # device id: 32 hex chars
        device_id = os.urandom(16).hex()

    private_key = ec.generate_private_key(ec.SECP256R1(), default_backend())
    private_pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption()
    ).decode('utf-8')

    # Save
    save_device(device_id, private_pem, file_path)

    return {
        'deviceId': device_id,
        'privateKeyPem': private_pem
    }


def get_or_create_device(device_id: Optional[str] = None, file_path: Optional[str] = None) -> dict:
    """Load existing device or create and save a new one"""
    data = load_device(file_path)
    if data:
        return data
    return generate_device(device_id, file_path)


def get_public_key_b64_from_pem(private_pem: str) -> str:
    """Return base64-encoded DER SubjectPublicKeyInfo for the private key PEM"""
    private_key = serialization.load_pem_private_key(private_pem.encode('utf-8'), password=None, backend=default_backend())
    pub_der = private_key.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo
    )
    return base64.b64encode(pub_der).decode('utf-8')
