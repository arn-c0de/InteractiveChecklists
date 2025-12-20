"""
Persistent ECDH device key management
Stores device_id and private key PEM to a local JSON file with encryption.

SECURITY:
- Windows: Uses DPAPI (Data Protection API) for automatic encryption
- macOS/Linux: Uses password-based encryption (prompts user on first access)
- Private keys are NEVER stored in plaintext
"""
import os
import sys
import json
import base64
import getpass
import logging
from typing import Optional, Tuple
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.backends import default_backend

logger = logging.getLogger(__name__)

DEFAULT_DEVICE_FILE = os.path.join(os.path.dirname(__file__), '..', 'ecdh_device.json')

# In-memory password cache (only for current session)
_password_cache = None


def _is_windows() -> bool:
    """Check if running on Windows"""
    return sys.platform == 'win32'


def _encrypt_windows_dpapi(data: bytes) -> bytes:
    """Encrypt data using Windows DPAPI"""
    try:
        import win32crypt
        encrypted = win32crypt.CryptProtectData(
            data,
            "ChecklistInteractive ECDH Device Key",
            None,  # optional entropy
            None,  # reserved
            None,  # prompt struct
            0      # flags
        )
        return encrypted
    except ImportError:
        raise RuntimeError("pywin32 is required for Windows DPAPI encryption. Install with: pip install pywin32")


def _decrypt_windows_dpapi(encrypted_data: bytes) -> bytes:
    """Decrypt data using Windows DPAPI"""
    try:
        import win32crypt
        _, decrypted = win32crypt.CryptUnprotectData(
            encrypted_data,
            None,  # optional entropy
            None,  # reserved
            None,  # prompt struct
            0      # flags
        )
        return decrypted
    except ImportError:
        raise RuntimeError("pywin32 is required for Windows DPAPI decryption. Install with: pip install pywin32")


def _get_password_for_encryption(device_id: str, force_new: bool = False) -> str:
    """
    Get password for encryption/decryption.
    On first use, prompts user and caches in memory.
    """
    global _password_cache
    
    if not force_new and _password_cache is not None:
        return _password_cache
    
    print("\n" + "="*70)
    print("🔐 ECDH DEVICE KEY PROTECTION")
    print("="*70)
    # Display only a short fingerprint of the device ID using HMAC for security
    import hmac
    import hashlib
    import secrets
    fp_key = secrets.token_bytes(32)
    device_fp = hmac.new(fp_key, device_id.encode('utf-8'), hashlib.sha256).hexdigest()[:8]
    print(f"Device ID: {device_fp}")
    print("\nYour private ECDH key needs to be protected with a password.")
    print("This password will be required each time you start the application.")
    print("\n⚠️  IMPORTANT: Choose a strong password and remember it!")
    print("   If you lose this password, you will need to generate a new device key.")
    print("="*70 + "\n")
    
    if force_new:
        while True:
            password = getpass.getpass("Enter NEW password for device key encryption: ")
            if len(password) < 8:
                print("❌ Password must be at least 8 characters long.")
                continue
            confirm = getpass.getpass("Confirm password: ")
            if password != confirm:
                print("❌ Passwords do not match. Try again.")
                continue
            break
    else:
        password = getpass.getpass("Enter password for device key: ")
    
    _password_cache = password
    return password


def _derive_key_from_password(password: str, salt: bytes) -> bytes:
    """Derive encryption key from password using PBKDF2"""
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=32,
        salt=salt,
        iterations=600000,  # NIST recommendation 2023
        backend=default_backend()
    )
    return kdf.derive(password.encode('utf-8'))


def _encrypt_with_password(data: bytes, password: str) -> dict:
    """Encrypt data with password-based encryption"""
    # Generate random salt
    salt = os.urandom(32)
    
    # Derive key from password
    key = _derive_key_from_password(password, salt)
    
    # Encrypt with AES-GCM
    aesgcm = AESGCM(key)
    nonce = os.urandom(12)
    ciphertext = aesgcm.encrypt(nonce, data, None)
    
    return {
        'salt': base64.b64encode(salt).decode('utf-8'),
        'nonce': base64.b64encode(nonce).decode('utf-8'),
        'ciphertext': base64.b64encode(ciphertext).decode('utf-8')
    }


def _decrypt_with_password(encrypted_data: dict, password: str) -> bytes:
    """Decrypt data with password-based encryption"""
    try:
        salt = base64.b64decode(encrypted_data['salt'])
        nonce = base64.b64decode(encrypted_data['nonce'])
        ciphertext = base64.b64decode(encrypted_data['ciphertext'])
        
        # Derive key from password
        key = _derive_key_from_password(password, salt)
        
        # Decrypt
        aesgcm = AESGCM(key)
        plaintext = aesgcm.decrypt(nonce, ciphertext, None)
        
        return plaintext
    except Exception as e:
        raise ValueError("Decryption failed. Incorrect password or corrupted data.") from e


def _encrypt_private_key(private_key_pem: str, device_id: str) -> dict:
    """
    Encrypt private key using platform-specific method.
    Returns dict with encrypted data and metadata.
    """
    data = private_key_pem.encode('utf-8')
    
    if _is_windows():
        try:
            encrypted = _encrypt_windows_dpapi(data)
            return {
                'method': 'dpapi',
                'data': base64.b64encode(encrypted).decode('utf-8')
            }
        except Exception as e:
            logger.warning(f"DPAPI encryption failed, falling back to password: {e}")
    
    # Fallback: password-based encryption
    password = _get_password_for_encryption(device_id, force_new=True)
    encrypted_data = _encrypt_with_password(data, password)
    return {
        'method': 'password',
        **encrypted_data
    }


def _decrypt_private_key(encrypted_data: dict, device_id: str) -> str:
    """
    Decrypt private key using the method specified in the data.
    """
    method = encrypted_data.get('method', 'plaintext')
    
    if method == 'plaintext':
        # Legacy unencrypted format - migrate immediately
        logger.warning("⚠️  Found unencrypted private key! Migrating to encrypted storage...")
        return encrypted_data.get('data', '')
    
    elif method == 'dpapi':
        if not _is_windows():
            raise RuntimeError("Cannot decrypt DPAPI-encrypted key on non-Windows platform")
        encrypted_bytes = base64.b64decode(encrypted_data['data'])
        decrypted = _decrypt_windows_dpapi(encrypted_bytes)
        return decrypted.decode('utf-8')
    
    elif method == 'password':
        password = _get_password_for_encryption(device_id, force_new=False)
        decrypted = _decrypt_with_password(encrypted_data, password)
        return decrypted.decode('utf-8')
    
    else:
        raise ValueError(f"Unknown encryption method: {method}")


def load_device(file_path: Optional[str] = None) -> Optional[dict]:
    """
    Load device info (device_id, private_key_pem) from file if exists.
    Automatically decrypts the private key.
    Returns None if file doesn't exist.
    """
    if file_path is None:
        file_path = DEFAULT_DEVICE_FILE
    try:
        if not os.path.exists(file_path):
            return None
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        device_id = data.get('deviceId')
        if not device_id:
            logger.error("Invalid device file: missing deviceId")
            return None
        
        # Check if we have encrypted or legacy plaintext format
        if 'privateKeyPem' in data:
            # Legacy plaintext format - need to migrate
            logger.warning("⚠️  Found legacy unencrypted device file. Migrating to encrypted format...")
            private_key_pem = data['privateKeyPem']
            # Re-save with encryption
            save_device(device_id, private_key_pem, file_path)
            logger.info("✅ Device key migrated to encrypted storage")
        elif 'encryptedPrivateKey' in data:
            # Encrypted format - decrypt it
            try:
                private_key_pem = _decrypt_private_key(data['encryptedPrivateKey'], device_id)
            except Exception as e:
                logger.error(f"Failed to decrypt private key: {e}")
                raise
        else:
            logger.error("Invalid device file: missing private key data")
            return None
        
        return {
            'deviceId': device_id,
            'privateKeyPem': private_key_pem
        }
    except Exception as e:
        logger.error(f"Failed to load device: {e}")
        return None


def save_device(device_id: str, private_key_pem: str, file_path: Optional[str] = None) -> None:
    """
    Save device info to file with encrypted private key.
    Uses platform-specific encryption (DPAPI on Windows, password-based otherwise).
    """
    if file_path is None:
        file_path = DEFAULT_DEVICE_FILE
    
    # Encrypt the private key
    encrypted_private_key = _encrypt_private_key(private_key_pem, device_id)
    
    data = {
        'deviceId': device_id,
        'encryptedPrivateKey': encrypted_private_key,
        'version': 2  # Format version for future compatibility
    }
    
    with open(file_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2)
    
    logger.info(f"✅ Device key saved with {encrypted_private_key['method']} encryption")


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


def generate_device_with_password(device_id: Optional[str] = None, password: Optional[str] = None, file_path: Optional[str] = None) -> dict:
    """Generate a new EC P-256 keypair and store it without prompting for console password.

    If running on Windows and no password is provided, DPAPI is used.
    If a password is provided, password-based encryption (AES-GCM) is used.
    If no password is provided on non-Windows, falls back to the interactive path (which may prompt).
    Returns dict with deviceId and privateKeyPem.
    """
    global _password_cache

    # Generate device id if not provided
    if device_id is None:
        device_id = os.urandom(16).hex()

    private_key = ec.generate_private_key(ec.SECP256R1(), default_backend())
    private_pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption()
    ).decode('utf-8')

    if file_path is None:
        file_path = DEFAULT_DEVICE_FILE

    # Choose encryption method based on platform and provided password
    if _is_windows() and password is None:
        # Use DPAPI (no console prompt)
        encrypted_private_key = _encrypt_private_key(private_pem, device_id)
    elif password is not None:
        # Use password-based encryption without prompting
        encrypted_blob = _encrypt_with_password(private_pem.encode('utf-8'), password)
        encrypted_private_key = {'method': 'password', **encrypted_blob}
        # Cache password for this session so decryption won't ask again
        _password_cache = password
    else:
        # Fallback to interactive save which may prompt for password (keeps existing behavior)
        save_device(device_id, private_pem, file_path)
        return {
            'deviceId': device_id,
            'privateKeyPem': private_pem
        }

    data = {
        'deviceId': device_id,
        'encryptedPrivateKey': encrypted_private_key,
        'version': 2
    }

    with open(file_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2)

    logger.info(f"✅ Device key saved with {encrypted_private_key.get('method', 'unknown')} encryption")

    return {
        'deviceId': device_id,
        'privateKeyPem': private_pem
    }


def reset_device(password: Optional[str] = None, file_path: Optional[str] = None) -> dict:
    """Remove any existing device file and generate a fresh device.

    If password is provided it will be used for encrypting the new key (non-Windows).
    Returns the newly created device dict (deviceId, privateKeyPem).
    """
    # Remove old file if exists
    if file_path is None:
        file_path = DEFAULT_DEVICE_FILE

    try:
        if os.path.exists(file_path):
            os.remove(file_path)
    except Exception:
        logger.warning("Failed to remove existing device file; continuing to generate new one")

    return generate_device_with_password(device_id=None, password=password, file_path=file_path)


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
