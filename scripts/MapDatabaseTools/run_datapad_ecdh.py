#!/usr/bin/env python3
"""
DataPad Receiver with ECDH Support
Command-line launcher for testing ECDH handshake
"""

import sys
import os
import argparse
import logging
import hashlib

# Add parent directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from network.datapad_receiver import DataPadReceiver
import time

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)

def main():
    parser = argparse.ArgumentParser(description="DataPad Receiver with ECDH support")
    parser.add_argument('--port', type=int, default=5010, help='UDP port to listen on (default: 5010)')
    parser.add_argument('--bind-ip', default='127.0.0.1', help='IP address to bind to (default: 127.0.0.1)')
    parser.add_argument('--sender-ip', required=True, help='IP address of DCS sender (required for ECDH)')
    parser.add_argument('--sender-port', type=int, help='Port for ECDH handshake on sender (default: same as --port)')
    parser.add_argument('--device-id', help='Device ID for ECDH (auto-generated if not provided)')
    parser.add_argument('--device-name', default='Python DataPad', help='Device name for ECDH')
    parser.add_argument('--allow-bind-all', action='store_true',
                       help='Allow binding to 0.0.0.0 (all interfaces)')
    
    args = parser.parse_args()
    
    # Create receiver
    receiver = DataPadReceiver(
        port=args.port,
        bind_ip=args.bind_ip,
        show_sensitive=False,
        allow_bind_all=args.allow_bind_all,
        use_ecdh=True,
        sender_ip=args.sender_ip,
        sender_port=args.sender_port,
        device_id=args.device_id,
        device_name=args.device_name
    )
    
    # Callbacks for console output
    def on_data(flight_data):
        print(f"\n=== Flight Data Update ===")
        print(f"Aircraft: {flight_data.aircraft}")
        print(f"Altitude: {flight_data.altitude:.1f} m")
        if flight_data.groundSpeed:
            print(f"Speed: {flight_data.groundSpeed:.1f} m/s")
        print(f"Heading: {flight_data.heading:.1f}°")
        print(f"Position: [REDACTED]")
    
    def on_connection(connected):
        status = "CONNECTED" if connected else "DISCONNECTED"
        print(f"\n*** Connection Status: {status} ***")
    
    receiver.add_data_callback(on_data)
    receiver.add_connection_callback(on_connection)
    
    # Start receiver
    print("\n" + "="*60)
    print("DataPad Receiver with ECDH Support")
    print("="*60)
    
    print(f"🔐 ECDH Mode: ENABLED")
    print(f"📡 Sender IP: {args.sender_ip}")
    if args.sender_port:
        print(f"🔌 Sender Handshake Port: {args.sender_port}")
    device_name_fp = hashlib.sha256(args.device_name.encode('utf-8')).hexdigest()[:8] if args.device_name else 'N/A'
    print(f"📱 Device Name fingerprint: {device_name_fp}")
    if args.device_id:
        device_id_fp = hashlib.sha256(args.device_id.encode('utf-8')).hexdigest()[:8]
        print(f"🔑 Device ID fingerprint: {device_id_fp}")

    print(f"🌐 Listen Port: {args.port}")
    print(f"🔌 Bind IP: {args.bind_ip}")
    print("="*60 + "\n")
    
    receiver.start()
    
    try:
        print("Press Ctrl+C to stop...\n")
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n\nStopping...")
        receiver.stop()
        print("Goodbye!")


if __name__ == "__main__":
    main()
