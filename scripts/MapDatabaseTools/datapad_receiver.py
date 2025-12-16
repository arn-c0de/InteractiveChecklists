"""
DataPad UDP Receiver - Python Implementation
Receives encrypted UDP flight data from DCS and displays it in a GUI.
Matches the functionality of the Kotlin DataPadManager.
"""

import socket
import json
import struct
from datetime import datetime
from typing import Optional, Dict, Any
from dataclasses import dataclass, field
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.backends import default_backend
import threading
import time


# Default settings (matching Kotlin app)
DEFAULT_UDP_PORT = 5010
DEFAULT_BIND_IP = "0.0.0.0"  # Listen on all interfaces
DEFAULT_PRE_SHARED_KEY = "DCS_DataPad_Secret_Key_32BYTES!!"
BUFFER_SIZE = 4096
SOCKET_TIMEOUT = 1.0


@dataclass
class FlightData:
    """Flight data model matching the Kotlin FlightData class"""
    # Basic Identity
    aircraft: str = ""
    unitName: str = ""
    coalition: str = ""
    country: int = 0
    group: str = ""
    
    # Flight Parameters
    altitude: float = 0.0
    heading: float = 0.0
    pitch: float = 0.0
    bank: float = 0.0
    latitude: float = 0.0
    longitude: float = 0.0
    
    # Speed & Vertical
    groundSpeed: Optional[float] = None
    indicatedAirspeed: Optional[float] = None
    trueAirspeed: Optional[float] = None
    verticalSpeed: Optional[float] = None
    mach: Optional[float] = None
    
    # Fuel
    fuel: Optional[Dict[str, Any]] = None
    
    # Angle of Attack & G-Load
    angleOfAttack: Optional[float] = None
    gLoad: Optional[Dict[str, Any]] = None
    
    # Aircraft Mass
    aircraftMass: Optional[Dict[str, Any]] = None
    
    # Engine Data
    engines: Optional[Dict[str, Any]] = None
    
    # Flight Controls & Trim
    flightControls: Optional[Dict[str, Any]] = None
    
    # Mechanical (Gear, Flaps, etc.)
    mechanical: Optional[Dict[str, Any]] = None
    weightOnWheels: bool = False
    
    # Lights
    lights: Optional[Dict[str, Any]] = None
    
    # Mission Time
    missionTime: Optional[float] = None
    
    # Systems Status
    systems: Optional[Dict[str, Any]] = None
    
    # Nearby Units
    nearbyUnits: Optional[list] = None
    
    # Navigation & Waypoints
    waypoint: Optional[Dict[str, Any]] = None
    flightPlan: Optional[Dict[str, Any]] = None
    
    # Weapons & Stores
    weapons: Optional[Dict[str, Any]] = None
    
    # EW / RWR / Threats
    rwr: Optional[Dict[str, Any]] = None
    radar: Optional[Dict[str, Any]] = None
    countermeasures: Optional[Dict[str, Any]] = None
    
    # Avionics & Systems
    autopilot: Optional[Dict[str, Any]] = None
    transponder: Optional[Dict[str, Any]] = None
    radios: Optional[Dict[str, Any]] = None
    warnings: Optional[Dict[str, Any]] = None
    
    # Environmental
    environment: Optional[Dict[str, Any]] = None
    
    # Status Flags
    isHuman: bool = False
    born: bool = False
    aiOn: bool = False
    radarActive: bool = False
    jamming: bool = False
    irJamming: bool = False
    invisible: bool = False
    
    # Metadata
    timestamp: str = ""
    unitID: str = "N/A"
    luaVersion: str = ""
    streamerVersion: str = ""
    dataAge: Optional[float] = None
    updateRate: Optional[float] = None
    
    @classmethod
    def from_json(cls, data: Dict[str, Any]) -> 'FlightData':
        """Create FlightData from JSON dictionary"""
        return cls(
            aircraft=data.get('aircraft', ''),
            unitName=data.get('unitName', ''),
            coalition=data.get('coalition', ''),
            country=data.get('country', 0),
            group=data.get('group', ''),
            altitude=data.get('alt', 0.0),
            heading=data.get('heading', 0.0),
            pitch=data.get('pitch', 0.0),
            bank=data.get('bank', 0.0),
            latitude=data.get('lat', 0.0),
            longitude=data.get('long', 0.0),
            groundSpeed=data.get('groundSpeed'),
            indicatedAirspeed=data.get('indicatedAirspeed'),
            trueAirspeed=data.get('trueAirspeed'),
            verticalSpeed=data.get('verticalSpeed'),
            mach=data.get('mach'),
            fuel=data.get('fuel'),
            angleOfAttack=data.get('angleOfAttack'),
            gLoad=data.get('gLoad'),
            aircraftMass=data.get('aircraftMass'),
            engines=data.get('engines'),
            flightControls=data.get('flightControls'),
            mechanical=data.get('mechanical'),
            weightOnWheels=data.get('weightOnWheels', False),
            lights=data.get('lights'),
            missionTime=data.get('missionTime'),
            systems=data.get('systems'),
            nearbyUnits=data.get('nearbyUnits'),
            waypoint=data.get('waypoint'),
            flightPlan=data.get('flightPlan'),
            weapons=data.get('weapons'),
            rwr=data.get('rwr'),
            radar=data.get('radar'),
            countermeasures=data.get('countermeasures'),
            autopilot=data.get('autopilot'),
            transponder=data.get('transponder'),
            radios=data.get('radios'),
            warnings=data.get('warnings'),
            environment=data.get('environment'),
            isHuman=data.get('isHuman', False),
            born=data.get('born', False),
            aiOn=data.get('aiOn', False),
            radarActive=data.get('radarActive', False),
            jamming=data.get('jamming', False),
            irJamming=data.get('irJamming', False),
            invisible=data.get('invisible', False),
            timestamp=data.get('timestamp', ''),
            unitID=data.get('unitID', 'N/A'),
            luaVersion=data.get('lua_version', ''),
            streamerVersion=data.get('streamer_version', ''),
            dataAge=data.get('dataAge'),
            updateRate=data.get('updateRate')
        )


class DataPadReceiver:
    """UDP receiver for encrypted flight data from DCS"""
    
    def __init__(self, port: int = DEFAULT_UDP_PORT, 
                 bind_ip: str = DEFAULT_BIND_IP,
                 pre_shared_key: str = DEFAULT_PRE_SHARED_KEY):
        self.port = port
        self.bind_ip = bind_ip
        self.pre_shared_key = pre_shared_key.encode('utf-8')
        
        self.socket: Optional[socket.socket] = None
        self.running = False
        self.receive_thread: Optional[threading.Thread] = None
        
        self.flight_data: Optional[FlightData] = None
        self.last_update_time: Optional[float] = None
        self.is_connected = False
        
        self.data_callbacks = []
        self.connection_callbacks = []
        
    def add_data_callback(self, callback):
        """Add callback to be called when new data arrives"""
        self.data_callbacks.append(callback)
        
    def add_connection_callback(self, callback):
        """Add callback to be called when connection status changes"""
        self.connection_callbacks.append(callback)
        
    def _notify_data_callbacks(self):
        """Notify all data callbacks"""
        for callback in self.data_callbacks:
            try:
                callback(self.flight_data)
            except Exception as e:
                print(f"Error in data callback: {e}")
                
    def _notify_connection_callbacks(self):
        """Notify all connection callbacks"""
        for callback in self.connection_callbacks:
            try:
                callback(self.is_connected)
            except Exception as e:
                print(f"Error in connection callback: {e}")
    
    def decrypt_payload(self, encrypted_data: bytes) -> Optional[bytes]:
        """
        Decrypt AES-GCM encrypted data
        Format: nonce (12 bytes) + ciphertext + tag (16 bytes)
        """
        try:
            if len(encrypted_data) < 28:  # 12 (nonce) + 16 (tag) minimum
                print(f"Encrypted data too short: {len(encrypted_data)} bytes")
                return None
            
            # Extract nonce (first 12 bytes)
            nonce = encrypted_data[:12]
            # Extract ciphertext + tag (remaining bytes)
            ciphertext = encrypted_data[12:]
            
            # Decrypt using AES-GCM
            aesgcm = AESGCM(self.pre_shared_key)
            plaintext = aesgcm.decrypt(nonce, ciphertext, None)
            
            return plaintext
            
        except Exception as e:
            print(f"Decryption failed: {e}")
            return None
    
    def get_local_ip(self) -> str:
        """Get the local IP address of this machine"""
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            local_ip = s.getsockname()[0]
            s.close()
            return local_ip
        except Exception:
            return "Unknown"
    
    def start(self):
        """Start listening for UDP packets"""
        if self.running:
            print("Receiver already running")
            return
            
        self.running = True
        self.receive_thread = threading.Thread(target=self._receive_loop, daemon=True)
        self.receive_thread.start()
        print(f"DataPad receiver started on {self.bind_ip}:{self.port}")
        print(f"Local IP: {self.get_local_ip()}")
    
    def stop(self):
        """Stop listening for UDP packets"""
        self.running = False
        if self.socket:
            self.socket.close()
        if self.receive_thread:
            self.receive_thread.join(timeout=2.0)
        self.is_connected = False
        self._notify_connection_callbacks()
        print("DataPad receiver stopped")
    
    def _receive_loop(self):
        """Main receive loop (runs in separate thread)"""
        try:
            # Create UDP socket
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.socket.settimeout(SOCKET_TIMEOUT)
            
            # Bind to address
            if self.bind_ip:
                self.socket.bind((self.bind_ip, self.port))
            else:
                self.socket.bind(('0.0.0.0', self.port))
            
            print(f"UDP socket opened on {self.bind_ip}:{self.port}")
            print(f"Waiting for encrypted UDP packets...")
            
            consecutive_timeouts = 0
            
            while self.running:
                try:
                    # Receive UDP packet
                    data, addr = self.socket.recvfrom(BUFFER_SIZE)
                    print(f"Received {len(data)} bytes from {addr}")
                    
                    # Try to decrypt
                    decrypted_data = self.decrypt_payload(data)
                    
                    if decrypted_data:
                        # Parse JSON
                        try:
                            json_data = json.loads(decrypted_data.decode('utf-8'))
                            self.flight_data = FlightData.from_json(json_data)
                            self.last_update_time = time.time()
                            
                            if not self.is_connected:
                                self.is_connected = True
                                self._notify_connection_callbacks()
                                print("Connected to DCS data stream")
                            
                            consecutive_timeouts = 0
                            self._notify_data_callbacks()
                            
                        except json.JSONDecodeError as e:
                            print(f"JSON decode error: {e}")
                    else:
                        print("Failed to decrypt data - check pre-shared key")
                
                except socket.timeout:
                    consecutive_timeouts += 1
                    # Consider disconnected after 5 consecutive timeouts (5 seconds)
                    if consecutive_timeouts >= 5 and self.is_connected:
                        self.is_connected = False
                        self._notify_connection_callbacks()
                        print("Disconnected - no data received")
                    continue
                    
                except Exception as e:
                    if self.running:
                        print(f"Error receiving data: {e}")
                    break
                    
        except Exception as e:
            print(f"Failed to start UDP socket: {e}")
        finally:
            if self.socket:
                self.socket.close()
    
    def get_time_since_update(self) -> str:
        """Get formatted time since last update"""
        if self.last_update_time is None:
            return "--"
        
        elapsed = time.time() - self.last_update_time
        if elapsed < 60:
            return f"{int(elapsed)}s ago"
        elif elapsed < 3600:
            return f"{int(elapsed / 60)}m ago"
        else:
            return f"{int(elapsed / 3600)}h ago"


if __name__ == "__main__":
    # Simple test without GUI
    receiver = DataPadReceiver()
    
    def on_data(flight_data):
        print(f"\n=== Flight Data Update ===")
        print(f"Aircraft: {flight_data.aircraft}")
        print(f"Altitude: {flight_data.altitude:.1f} m")
        print(f"Speed: {flight_data.groundSpeed:.1f} m/s" if flight_data.groundSpeed else "Speed: N/A")
        print(f"Heading: {flight_data.heading:.1f}°")
        print(f"Lat/Long: {flight_data.latitude:.6f}, {flight_data.longitude:.6f}")
    
    def on_connection(connected):
        status = "CONNECTED" if connected else "DISCONNECTED"
        print(f"\n*** Connection Status: {status} ***")
    
    receiver.add_data_callback(on_data)
    receiver.add_connection_callback(on_connection)
    
    receiver.start()
    
    try:
        print("\nPress Ctrl+C to stop...")
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nStopping...")
        receiver.stop()
