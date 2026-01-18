#!/usr/bin/env python3
"""
registration_token.py
Registration token system for QR-code based device onboarding

Generates time-limited registration tokens that allow devices to self-register
without manual key exchange.

Usage:
    python registration_token.py generate --server-ip 192.168.1.100 --port 5010
    python registration_token.py verify <token>
"""

import json
import time
import secrets
import hashlib
import base64
from typing import Optional, Dict
from pathlib import Path
import logging

logger = logging.getLogger(__name__)


class RegistrationToken:
    """Represents a time-limited device registration token"""
    
    def __init__(self, token_id: str, server_ip: str, port: int, 
                 expires_at: float, permissions: list = None):
        self.token_id = token_id
        self.server_ip = server_ip
        self.port = port
        self.expires_at = expires_at
        self.permissions = permissions or ["receive", "send_commands"]
        self.created_at = time.time()
        self.used = False
    
    def is_expired(self) -> bool:
        """Check if token has expired"""
        return time.time() > self.expires_at
    
    def is_valid(self) -> bool:
        """Check if token is still valid (not expired, not used)"""
        return not self.used and not self.is_expired()
    
    def mark_used(self):
        """Mark token as used (can only be used once)"""
        self.used = True
    
    def to_dict(self) -> dict:
        """Serialize token to dictionary"""
        return {
            'token_id': self.token_id,
            'server_ip': self.server_ip,
            'port': self.port,
            'expires_at': self.expires_at,
            'permissions': self.permissions,
            'created_at': self.created_at,
            'used': self.used
        }
    
    @classmethod
    def from_dict(cls, data: dict) -> 'RegistrationToken':
        """Deserialize token from dictionary"""
        token = cls(
            token_id=data['token_id'],
            server_ip=data['server_ip'],
            port=data['port'],
            expires_at=data['expires_at'],
            permissions=data.get('permissions', ["receive", "send_commands"])
        )
        token.created_at = data.get('created_at', time.time())
        token.used = data.get('used', False)
        return token
    
    def to_qr_payload(self) -> str:
        """Generate QR code payload (compact JSON)"""
        payload = {
            'type': 'datapad_registration',
            'version': '1.0',
            'token': self.token_id,
            'server': self.server_ip,
            'port': self.port,
            'expires': int(self.expires_at),
            'permissions': self.permissions
        }
        return json.dumps(payload, separators=(',', ':'))  # Compact JSON


class RegistrationTokenManager:
    """Manages registration tokens lifecycle"""
    
    def __init__(self, tokens_file: str = "registration_tokens.json"):
        self.tokens_file = Path(tokens_file)
        self.tokens: Dict[str, RegistrationToken] = {}
        self._load_tokens()
    
    def _load_tokens(self):
        """Load tokens from file"""
        if self.tokens_file.exists():
            try:
                with open(self.tokens_file, 'r') as f:
                    data = json.load(f)
                    for token_data in data.get('tokens', []):
                        token = RegistrationToken.from_dict(token_data)
                        self.tokens[token.token_id] = token
                logger.info(f"Loaded {len(self.tokens)} registration tokens")
            except Exception as e:
                logger.error(f"Failed to load tokens: {e}")
        else:
            logger.info("No tokens file found, starting fresh")
    
    def _save_tokens(self):
        """Save tokens to file"""
        try:
            data = {
                'tokens': [token.to_dict() for token in self.tokens.values()],
                'updated_at': time.time()
            }
            with open(self.tokens_file, 'w') as f:
                json.dump(data, f, indent=2)
        except Exception as e:
            logger.error(f"Failed to save tokens: {e}")
    
    def generate_token(self, server_ip: str, port: int, 
                      validity_minutes: int = 10,
                      permissions: list = None) -> RegistrationToken:
        """Generate a new registration token
        
        Args:
            server_ip: Server IP address
            port: Server UDP port
            validity_minutes: Token validity in minutes (default: 10)
            permissions: Device permissions (default: receive, send_commands)
        
        Returns:
            RegistrationToken object
        """
        # Generate cryptographically secure token ID
        token_id = secrets.token_urlsafe(32)
        
        # Calculate expiration time
        expires_at = time.time() + (validity_minutes * 60)
        
        # Create token
        token = RegistrationToken(
            token_id=token_id,
            server_ip=server_ip,
            port=port,
            expires_at=expires_at,
            permissions=permissions
        )
        
        # Store token
        self.tokens[token_id] = token
        self._save_tokens()
        
        logger.info(f"✅ Generated registration token (expires in {validity_minutes} min)")
        return token
    
    def verify_token(self, token_id: str) -> Optional[RegistrationToken]:
        """Verify and retrieve a token

        Args:
            token_id: Token ID to verify

        Returns:
            RegistrationToken if valid, None otherwise
        """
        # Reload tokens from file to pick up newly generated tokens
        # (Important when GUI generates tokens while server is running)
        self._load_tokens()

        token = self.tokens.get(token_id)

        if not token:
            logger.warning(f"❌ Unknown token: {token_id[:16]}...")
            return None

        if not token.is_valid():
            logger.warning(f"❌ Invalid token (expired={token.is_expired()}, used={token.used})")
            return None

        logger.info(f"✅ Token verified: {token_id[:16]}...")
        return token
    
    def consume_token(self, token_id: str) -> Optional[RegistrationToken]:
        """Consume a token (mark as used and return it)
        
        Args:
            token_id: Token ID to consume
        
        Returns:
            RegistrationToken if successfully consumed, None otherwise
        """
        token = self.verify_token(token_id)
        
        if token:
            token.mark_used()
            self._save_tokens()
            logger.info(f"✅ Token consumed: {token_id[:16]}...")
            return token
        
        return None
    
    def cleanup_expired(self) -> int:
        """Remove expired and used tokens
        
        Returns:
            Number of tokens removed
        """
        before_count = len(self.tokens)
        
        # Remove expired or used tokens
        self.tokens = {
            token_id: token
            for token_id, token in self.tokens.items()
            if token.is_valid()
        }
        
        removed_count = before_count - len(self.tokens)
        
        if removed_count > 0:
            self._save_tokens()
            logger.info(f"🗑️ Cleaned up {removed_count} expired/used tokens")
        
        return removed_count
    
    def list_active_tokens(self) -> list:
        """Get list of all active (valid) tokens"""
        return [
            token for token in self.tokens.values()
            if token.is_valid()
        ]


def generate_qr_code_ascii(data: str, output_file: str = None) -> str:
    """Generate ASCII art QR code (fallback if qrcode package not available)
    
    Args:
        data: Data to encode in QR code
        output_file: Optional file to save QR code image
    
    Returns:
        ASCII representation of QR code or instructions
    """
    try:
        import qrcode
        
        # Create QR code
        qr = qrcode.QRCode(
            version=1,
            error_correction=qrcode.constants.ERROR_CORRECT_L,
            box_size=10,
            border=4,
        )
        qr.add_data(data)
        qr.make(fit=True)
        
        # Save to file if requested
        if output_file:
            img = qr.make_image(fill_color="black", back_color="white")
            img.save(output_file)
            logger.info(f"💾 QR code saved to: {output_file}")
        
        # Print ASCII version
        qr_ascii = qr.make_image(fill_color="black", back_color="white")
        
        # Return ASCII art (using terminal output)
        import io
        import sys
        
        f = io.StringIO()
        qr.print_ascii(out=f, invert=True)
        return f.getvalue()
        
    except ImportError:
        # Fallback: return instructions
        return f"""
⚠️  QR code library not installed. Install with:
    pip install qrcode[pil]

Or manually create QR code with this data:
{data}

You can use online QR generators like:
- https://www.qr-code-generator.com/
- https://www.qrcode-monkey.com/
"""


def main():
    """CLI interface for token management"""
    import argparse
    
    parser = argparse.ArgumentParser(
        description="DataPad Registration Token Manager"
    )
    subparsers = parser.add_subparsers(dest='command', help='Commands')
    
    # Generate token
    gen_parser = subparsers.add_parser('generate', help='Generate new registration token')
    gen_parser.add_argument('--server-ip', required=True, help='Server IP address')
    gen_parser.add_argument('--port', type=int, default=5010, help='Server port (default: 5010)')
    gen_parser.add_argument('--validity', type=int, default=10, 
                           help='Token validity in minutes (default: 10)')
    gen_parser.add_argument('--output-qr', help='Save QR code image to file')
    
    # Verify token
    verify_parser = subparsers.add_parser('verify', help='Verify a token')
    verify_parser.add_argument('token_id', help='Token ID to verify')
    
    # List tokens
    list_parser = subparsers.add_parser('list', help='List active tokens')
    
    # Cleanup
    cleanup_parser = subparsers.add_parser('cleanup', help='Remove expired tokens')
    
    args = parser.parse_args()
    
    # Setup logging
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s [%(levelname)s] %(message)s'
    )
    
    # Create manager
    manager = RegistrationTokenManager()
    
    if args.command == 'generate':
        # Generate token
        token = manager.generate_token(
            server_ip=args.server_ip,
            port=args.port,
            validity_minutes=args.validity
        )
        
        # Get QR payload
        qr_data = token.to_qr_payload()
        
        print("\n" + "=" * 70)
        print("🔐 DATAPAD REGISTRATION TOKEN GENERATED")
        print("=" * 70)
        print(f"\n📱 Server: {token.server_ip}:{token.port}")
        print(f"⏰ Expires: {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(token.expires_at))}")
        print(f"🔑 Token ID: {token.token_id}")
        print(f"\n📋 QR Code Payload ({len(qr_data)} bytes):")
        print(qr_data)
        
        # Generate QR code
        print("\n📱 QR CODE:")
        print(generate_qr_code_ascii(qr_data, args.output_qr))
        
        print("\n✅ Token ready! Scan with DataPad app to register device.")
        print("=" * 70)
    
    elif args.command == 'verify':
        # Verify token
        token = manager.verify_token(args.token_id)
        
        if token:
            print(f"\n✅ Token is VALID")
            print(f"   Server: {token.server_ip}:{token.port}")
            print(f"   Expires: {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(token.expires_at))}")
            print(f"   Permissions: {', '.join(token.permissions)}")
        else:
            print(f"\n❌ Token is INVALID or EXPIRED")
    
    elif args.command == 'list':
        # List active tokens
        active_tokens = manager.list_active_tokens()
        
        print(f"\n📋 Active Registration Tokens: {len(active_tokens)}")
        print("=" * 70)
        
        for token in active_tokens:
            expires_in = int((token.expires_at - time.time()) / 60)
            print(f"\n🔑 {token.token_id[:32]}...")
            print(f"   Server: {token.server_ip}:{token.port}")
            print(f"   Expires in: {expires_in} minutes")
            print(f"   Used: {'Yes' if token.used else 'No'}")
    
    elif args.command == 'cleanup':
        # Cleanup expired tokens
        removed = manager.cleanup_expired()
        print(f"\n🗑️  Removed {removed} expired/used tokens")
        print(f"📋 {len(manager.tokens)} active tokens remaining")
    
    else:
        parser.print_help()


if __name__ == '__main__':
    main()
