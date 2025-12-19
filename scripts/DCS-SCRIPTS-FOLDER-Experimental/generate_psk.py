#!/usr/bin/env python3
"""
Generate Pre-Shared Key (PSK) for DataPad encryption
Nur für NON-ECDH Modus (nicht empfohlen - verwenden Sie stattdessen ECDH!)
"""

import secrets
import base64
import json
import os

def generate_psk():
    """Generate a secure 32-byte PSK"""
    psk_bytes = secrets.token_bytes(32)
    psk_b64 = base64.b64encode(psk_bytes).decode('utf-8')
    return psk_b64

def main():
    print("\n" + "="*60)
    print("Pre-Shared Key (PSK) Generator")
    print("⚠️  WARNUNG: ECDH ist sicherer! Verwenden Sie --use-handshake")
    print("="*60)

    psk_b64 = generate_psk()

    print(f"\n🔑 Generated PSK (Base64):")
    print(f"   {psk_b64}")

    print(f"\n📝 Verwendung:")
    print(f"   1. Server: export DATAPAD_PSK={psk_b64}")
    print(f"   2. Client: Setzen Sie in datapad_config.json:")
    print(f"      {{")
    print(f"        \"pre_shared_key\": \"{psk_b64}\",")
    print(f"        \"useEcdh\": false")
    print(f"      }}")

    # Offer to save
    config_file = "datapad_config.json"
    save = input(f"\n💾 Save to {config_file}? (y/n): ").strip().lower()

    if save == 'y':
        try:
            # Load existing config or create new
            config = {}
            if os.path.exists(config_file):
                with open(config_file, 'r') as f:
                    config = json.load(f)

            config['pre_shared_key'] = psk_b64
            config['useEcdh'] = False

            with open(config_file, 'w') as f:
                json.dump(config, f, indent=2)

            print(f"✅ Saved to {config_file}")
        except Exception as e:
            print(f"❌ Failed to save: {e}")

    print("\n⚠️  BESSER: Verwenden Sie ECDH statt PSK:")
    print("   Server: python forward_parsed_udp.py --use-handshake")
    print("   Client: Aktivieren Sie 'ECDH Handshake' in den Einstellungen")
    print("="*60 + "\n")

if __name__ == "__main__":
    main()
