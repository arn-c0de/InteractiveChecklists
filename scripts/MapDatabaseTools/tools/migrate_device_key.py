#!/usr/bin/env python3
"""
Migration tool for ECDH device keys
Converts unencrypted legacy device keys to encrypted format.

Run this tool if you have an existing ecdh_device.json file with plaintext keys.
The tool will automatically detect and migrate the file.
"""

import os
import sys
import json
import logging

# Add parent directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from network.ecdh_device import load_device, DEFAULT_DEVICE_FILE

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s'
)

logger = logging.getLogger(__name__)


def check_and_migrate_device(file_path: str = None) -> bool:
    """
    Check if device file exists and needs migration.
    If it's already encrypted or doesn't exist, does nothing.
    
    Returns True if migration was performed or not needed, False on error.
    """
    if file_path is None:
        file_path = DEFAULT_DEVICE_FILE
    
    if not os.path.exists(file_path):
        logger.info(f"✅ No device file found at {file_path}")
        logger.info("   No migration needed. A new encrypted key will be created on first use.")
        return True
    
    try:
        # Try to read the file
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        # Check format
        if 'encryptedPrivateKey' in data:
            logger.info("✅ Device file is already encrypted.")
            version = data.get('version', 1)
            method = data['encryptedPrivateKey'].get('method', 'unknown')
            logger.info(f"   Version: {version}, Method: {method}")
            return True
        
        elif 'privateKeyPem' in data:
            logger.warning("⚠️  Found UNENCRYPTED device file!")
            device_id = data.get('deviceId', 'unknown')
            logger.info(f"   Device ID: {device_id}")
            logger.info("\n" + "="*70)
            logger.info("MIGRATING TO ENCRYPTED STORAGE")
            logger.info("="*70)
            
            # Load using the new system - it will automatically migrate
            result = load_device(file_path)
            
            if result:
                logger.info("✅ Migration completed successfully!")
                logger.info(f"   Device ID: {result['deviceId']}")
                logger.info("\n⚠️  IMPORTANT: The old plaintext file has been replaced.")
                logger.info("   Keep a backup if needed (not recommended for production).")
                return True
            else:
                logger.error("❌ Migration failed!")
                return False
        
        else:
            logger.error("❌ Invalid device file format!")
            logger.error("   The file exists but has unknown structure.")
            return False
            
    except json.JSONDecodeError:
        logger.error(f"❌ Failed to parse {file_path} as JSON")
        return False
    except Exception as e:
        logger.error(f"❌ Error during migration: {e}")
        return False


def main():
    """Main entry point"""
    print("\n" + "="*70)
    print("🔐 ECDH DEVICE KEY MIGRATION TOOL")
    print("="*70)
    print("\nThis tool will check and migrate your ECDH device key")
    print("from unencrypted to encrypted storage.")
    print("\nOn Windows: Uses DPAPI (automatic, no password needed)")
    print("On macOS/Linux: Uses password-based encryption (you will be prompted)")
    print("="*70 + "\n")
    
    # Allow custom path via command line
    file_path = None
    if len(sys.argv) > 1:
        file_path = sys.argv[1]
        logger.info(f"Using custom device file: {file_path}")
    else:
        file_path = DEFAULT_DEVICE_FILE
        logger.info(f"Using default device file: {file_path}")
    
    print()
    
    # Perform migration
    success = check_and_migrate_device(file_path)
    
    print("\n" + "="*70)
    if success:
        print("✅ MIGRATION COMPLETED SUCCESSFULLY")
        print("\nYour device key is now securely encrypted.")
        print("\nNext steps:")
        print("  1. Test your application to ensure it can load the encrypted key")
        print("  2. On Windows: No password is needed (uses DPAPI)")
        print("  3. On macOS/Linux: You'll need to enter the password each time")
    else:
        print("❌ MIGRATION FAILED")
        print("\nPlease check the error messages above.")
        print("If you need help, contact the development team.")
    print("="*70 + "\n")
    
    return 0 if success else 1


if __name__ == '__main__':
    sys.exit(main())
