"""
DataPad GUI - Live Flight Data Display
Visualizes DCS flight data received via UDP in a PySide6 GUI.
Matches the layout and functionality of the Kotlin DataPadPopup.
"""

import sys
import os
from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QPushButton, QScrollArea, QGroupBox, QGridLayout,
    QDialog, QLineEdit, QMessageBox, QFrame, QSplitter, QCheckBox
)
from PySide6.QtCore import Qt, QTimer, Signal, QObject, QUrl
from PySide6.QtGui import QFont, QPalette, QColor
from PySide6.QtWebEngineWidgets import QWebEngineView
from PySide6.QtWebEngineCore import QWebEngineSettings
from network.datapad_receiver import DataPadReceiver, FlightData
from core.markers_database import MarkersDatabase, Location
from gui.location_manager import LocationManagerWidget
from typing import Optional
import json
import os


CONFIG_FILE = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'datapad_config.json')

def load_config() -> dict:
    """Load config from file or return defaults"""
    defaults = {
        'senderIp': '192.168.178.100',
        'senderPort': None,  # Use same as port if not specified
        'useEcdh': False,
        'deviceName': 'Python DataPad',
        'port': 5010,
        'bindIp': '0.0.0.0'  # Default to all interfaces for ECDH compatibility
    }
    try:
        if os.path.exists(CONFIG_FILE):
            with open(CONFIG_FILE, 'r', encoding='utf-8') as f:
                return {**defaults, **json.load(f)}
    except Exception:
        pass
    return defaults

def save_config(config: dict):
    """Save config to file"""
    try:
        with open(CONFIG_FILE, 'w', encoding='utf-8') as f:
            json.dump(config, f, indent=2)
    except Exception:
        pass

class DataSignals(QObject):
    """Signals for thread-safe GUI updates"""
    data_updated = Signal(object)
    connection_changed = Signal(bool)


class SettingsDialog(QDialog):
    """Settings dialog for DataPad configuration"""
    
    def __init__(self, receiver: DataPadReceiver, parent=None):
        super().__init__(parent)
        self.receiver = receiver
        self.config = load_config()
        self.setWindowTitle("DataPad Settings")
        self.setModal(True)
        self.setup_ui()
        
    def setup_ui(self):
        layout = QVBoxLayout()
        
        # UDP Port
        port_layout = QHBoxLayout()
        port_layout.addWidget(QLabel("UDP Port:"))
        self.port_edit = QLineEdit(str(self.receiver.port))
        port_layout.addWidget(self.port_edit)
        layout.addLayout(port_layout)
        
        # Bind IP
        ip_layout = QHBoxLayout()
        ip_layout.addWidget(QLabel("Bind IP:"))
        self.ip_edit = QLineEdit(self.receiver.bind_ip)
        self.ip_edit.setPlaceholderText("0.0.0.0 (all interfaces)")
        ip_layout.addWidget(self.ip_edit)
        layout.addLayout(ip_layout)
        
        # Pre-Shared Key
        key_layout = QHBoxLayout()
        key_layout.addWidget(QLabel("Pre-Shared Key (32 chars):"))
        self.key_edit = QLineEdit(self.receiver.pre_shared_key.decode('utf-8'))
        self.key_edit.setEchoMode(QLineEdit.EchoMode.Password)
        key_layout.addWidget(self.key_edit)
        layout.addLayout(key_layout)
        
        # ECDH Section
        ecdh_group = QGroupBox("ECDH Handshake Settings")
        ecdh_layout = QVBoxLayout()
        
        # Load persistent device
        from network.ecdh_device import load_device, get_public_key_b64_from_pem
        persistent_device = load_device()
        
        # Enable ECDH checkbox
        self.ecdh_checkbox = QCheckBox("Enable ECDH Handshake")
        self.ecdh_checkbox.setChecked(self.config.get('useEcdh', False))
        self.ecdh_checkbox.toggled.connect(self._on_ecdh_toggled)
        ecdh_layout.addWidget(self.ecdh_checkbox)
        
        # Sender IP
        sender_ip_layout = QHBoxLayout()
        sender_ip_layout.addWidget(QLabel("Sender IP:"))
        self.sender_ip_edit = QLineEdit(self.config.get('senderIp', ''))
        self.sender_ip_edit.setPlaceholderText("e.g., 192.168.1.100")
        sender_ip_layout.addWidget(self.sender_ip_edit)
        ecdh_layout.addLayout(sender_ip_layout)
        
        # Sender Port (for same-PC setup)
        sender_port_layout = QHBoxLayout()
        sender_port_layout.addWidget(QLabel("Sender Handshake Port:"))
        self.sender_port_edit = QLineEdit(str(self.config.get('senderPort', '')))
        self.sender_port_edit.setPlaceholderText("Leave empty to use same as UDP Port")
        self.sender_port_edit.setToolTip("Use different port when sender and receiver are on same PC (e.g., 5011)")
        sender_port_layout.addWidget(self.sender_port_edit)
        ecdh_layout.addLayout(sender_port_layout)
        
        # Device ID (read-only, auto-loaded) with copy button
        device_id_layout = QHBoxLayout()
        device_id_layout.addWidget(QLabel("Device ID:"))
        device_id_value = persistent_device['deviceId'] if persistent_device else 'Auto-generated on first start'
        self.device_id_edit = QLineEdit(device_id_value)
        self.device_id_edit.setReadOnly(True)
        self.device_id_edit.setStyleSheet("color: #42A5F5; font-family: monospace;")
        self.device_id_edit.setToolTip(device_id_value)
        device_id_layout.addWidget(self.device_id_edit)
        copy_id_btn = QPushButton("Copy")
        copy_id_btn.setToolTip("Copy full Device ID to clipboard")
        copy_id_btn.clicked.connect(self._copy_device_id)
        device_id_layout.addWidget(copy_id_btn)
        ecdh_layout.addLayout(device_id_layout)
        
        # Public Key (read-only, for copy)
        if persistent_device:
            pubkey_layout = QHBoxLayout()
            pubkey_layout.addWidget(QLabel("Public Key:"))
            self.pubkey_b64 = get_public_key_b64_from_pem(persistent_device['privateKeyPem'])
            self.pubkey_label = QLineEdit(self.pubkey_b64)
            self.pubkey_label.setReadOnly(True)
            self.pubkey_label.setStyleSheet("font-family: monospace; font-size: 9pt;")
            pubkey_layout.addWidget(self.pubkey_label)
            copy_btn = QPushButton("Copy")
            copy_btn.clicked.connect(self._copy_pubkey)
            pubkey_layout.addWidget(copy_btn)
            ecdh_layout.addLayout(pubkey_layout)
        
        # Device Name
        device_name_layout = QHBoxLayout()
        device_name_layout.addWidget(QLabel("Device Name:"))
        self.device_name_edit = QLineEdit(self.config.get('deviceName', 'Python DataPad'))
        device_name_layout.addWidget(self.device_name_edit)
        ecdh_layout.addLayout(device_name_layout)
        
        # Info label for ECDH
        ecdh_info = QLabel("⚠️ Device must be in authorized_devices.json on sender")
        ecdh_info.setStyleSheet("color: orange; font-style: italic;")
        ecdh_layout.addWidget(ecdh_info)
        
        ecdh_group.setLayout(ecdh_layout)
        layout.addWidget(ecdh_group)
        
        # Update ECDH fields state
        self._on_ecdh_toggled(self.ecdh_checkbox.isChecked())
        
        # Info label
        info = QLabel("Changes require restart of the receiver")
        info.setStyleSheet("color: gray; font-style: italic;")
        layout.addWidget(info)
        
        # Buttons
        button_layout = QHBoxLayout()
        save_btn = QPushButton("Save && Restart")
        save_btn.clicked.connect(self.save_and_restart)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        button_layout.addWidget(cancel_btn)
        button_layout.addWidget(save_btn)
        layout.addLayout(button_layout)
        
        self.setLayout(layout)
    
    def _on_ecdh_toggled(self, checked: bool):
        """Enable/disable ECDH fields based on checkbox"""
        self.sender_ip_edit.setEnabled(checked)
        self.device_name_edit.setEnabled(checked)
    
    def _copy_pubkey(self):
        """Copy public key to clipboard"""
        from PySide6.QtWidgets import QApplication
        QApplication.clipboard().setText(self.pubkey_b64)
        QMessageBox.information(self, "Copied", "Public key copied to clipboard!")

    def _copy_device_id(self):
        """Copy device ID to clipboard"""
        from PySide6.QtWidgets import QApplication
        QApplication.clipboard().setText(self.device_id_edit.text())
        QMessageBox.information(self, "Copied", "Device ID copied to clipboard!")
        
    def save_and_restart(self):
        """Save settings and restart receiver"""
        try:
            import uuid
            from network.datapad_receiver import DataPadReceiver
            
            port = int(self.port_edit.text())
            if port < 1024 or port > 65535:
                QMessageBox.warning(self, "Invalid Port", "Port must be between 1024 and 65535")
                return
            
            key = self.key_edit.text()
            if len(key) != 32:
                QMessageBox.warning(self, "Invalid Key", "Pre-shared key must be exactly 32 characters")
                return
            
            # ECDH settings
            use_ecdh = self.ecdh_checkbox.isChecked()
            sender_ip = self.sender_ip_edit.text() if use_ecdh else None
            device_name = self.device_name_edit.text() if use_ecdh else "Python DataPad"
            
            if use_ecdh and not sender_ip:
                QMessageBox.warning(self, "Invalid Input", "Sender IP is required for ECDH mode")
                return
            
            # Load or create persistent device (device_id is auto-managed)
            from network.ecdh_device import get_or_create_device
            device = get_or_create_device()
            device_id = device['deviceId'] if use_ecdh else None
            
            # Save config
            self.config['useEcdh'] = use_ecdh
            self.config['senderIp'] = sender_ip or ''
            sender_port_text = self.sender_port_edit.text().strip()
            self.config['senderPort'] = int(sender_port_text) if sender_port_text else None
            self.config['deviceName'] = device_name
            self.config['port'] = port
            self.config['bindIp'] = self.ip_edit.text()
            save_config(self.config)
            
            # Stop old receiver
            self.receiver.stop()
            
            # For ECDH: need to bind to 0.0.0.0 to reach external sender
            bind_ip = self.ip_edit.text()
            allow_bind_all = False
            if use_ecdh:
                bind_ip = '0.0.0.0'
                allow_bind_all = True
                # Update config with correct bind IP
                self.config['bindIp'] = '0.0.0.0'
            
            # Create new receiver with updated settings
            new_receiver = DataPadReceiver(
                port=port,
                bind_ip=bind_ip,
                allow_bind_all=allow_bind_all,
                pre_shared_key=key,
                use_ecdh=use_ecdh,
                sender_ip=sender_ip,
                sender_port=self.config.get('senderPort'),
                device_id=device_id,
                device_name=device_name
            )
            
            # Copy callbacks
            new_receiver.data_callbacks = self.receiver.data_callbacks
            new_receiver.connection_callbacks = self.receiver.connection_callbacks
            
            # Replace receiver in parent
            if hasattr(self.parent(), 'receiver'):
                self.parent().receiver = new_receiver
            
            # Start new receiver
            new_receiver.start()
            
            if use_ecdh:
                from network.ecdh_device import get_public_key_b64_from_pem
                pubkey = get_public_key_b64_from_pem(device['privateKeyPem'])
                QMessageBox.information(
                    self,
                    "ECDH Enabled",
                    f"ECDH handshake enabled.\n\n"
                    f"Device ID: {device_id}\n"
                    f"Public Key: {pubkey[:32]}...\n\n"
                    f"⚠️ Add this device to authorized_devices.json on sender:\n{sender_ip}"
                )
            
            self.accept()
            
        except ValueError:
            QMessageBox.warning(self, "Invalid Input", "Port must be a number")


class DataPadGUI(QMainWindow):
    """Main GUI window for DataPad visualization"""
    
    def __init__(self):
        super().__init__()
        
        # Load config and create receiver with saved settings
        config = load_config()
        
        # Load persistent device for ECDH if enabled
        device_id = None
        if config.get('useEcdh', False):
            from network.ecdh_device import get_or_create_device
            device = get_or_create_device()
            device_id = device['deviceId']
        
        # For ECDH: need to bind to 0.0.0.0 to reach external sender
        bind_ip = config.get('bindIp', '127.0.0.1')
        allow_bind_all = False
        if config.get('useEcdh', False):
            bind_ip = '0.0.0.0'
            allow_bind_all = True
        
        self.receiver = DataPadReceiver(
            port=config.get('port', 5010),
            bind_ip=bind_ip,
            allow_bind_all=allow_bind_all,
            use_ecdh=config.get('useEcdh', False),
            sender_ip=config.get('senderIp') if config.get('useEcdh') else None,
            sender_port=config.get('senderPort'),
            device_id=device_id,
            device_name=config.get('deviceName', 'Python DataPad')
        )
        self.db = MarkersDatabase()
        self.signals = DataSignals()
        # Map behavior
        self.auto_center = True
        self.webview: QWebEngineView | None = None
        
        # Connect signals
        self.signals.data_updated.connect(self.on_data_updated)
        self.signals.connection_changed.connect(self.on_connection_changed)
        
        self.receiver.add_data_callback(lambda data: self.signals.data_updated.emit(data))
        self.receiver.add_connection_callback(lambda conn: self.signals.connection_changed.emit(conn))
        
        self.setup_ui()
        self.receiver.start()
        
        # Update timer for time since last update
        self.update_timer = QTimer()
        self.update_timer.timeout.connect(self.update_time_display)
        self.update_timer.start(1000)  # Update every second
        
    def setup_ui(self):
        """Setup the user interface"""
        self.setWindowTitle("DCS DataPad - Live Flight Data & Tactical Markers")
        self.setGeometry(100, 100, 1400, 900)
        
        # Main widget and split layout (left: 1/3 info, right: 2/3 map)
        main_widget = QWidget()
        self.setCentralWidget(main_widget)
        main_layout = QHBoxLayout(main_widget)

        # Left column - vertical splitter for FlightData (top) and LocationManager (bottom)
        left_splitter = QSplitter(Qt.Orientation.Vertical)
        
        # Top: FlightData panel
        flight_data_container = QWidget()
        flight_data_layout = QVBoxLayout(flight_data_container)
        flight_data_layout.setContentsMargins(0, 0, 0, 0)

        # Header with connection status and controls
        header = self.create_header()
        flight_data_layout.addWidget(header)

        # Scroll area for flight data
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)

        self.data_widget = QWidget()
        self.data_layout = QVBoxLayout(self.data_widget)
        scroll.setWidget(self.data_widget)

        flight_data_layout.addWidget(scroll)
        left_splitter.addWidget(flight_data_container)
        
        # Bottom: Location Manager panel
        self.location_manager = LocationManagerWidget(self.db)
        self.location_manager.location_selected.connect(self.on_location_selected)
        self.location_manager.location_added.connect(self.on_location_changed)
        self.location_manager.location_updated.connect(self.on_location_changed)
        self.location_manager.location_deleted.connect(self.on_location_changed)
        left_splitter.addWidget(self.location_manager)
        
        # Set initial splitter sizes (60% flight data, 40% locations)
        left_splitter.setSizes([600, 400])

        # Right column: embedded OSM map using Leaflet (map.html)
        self.webview = QWebEngineView()
        
        # Enable remote URL access for CDN resources
        self.webview.settings().setAttribute(QWebEngineSettings.WebAttribute.JavascriptEnabled, True)
        self.webview.settings().setAttribute(QWebEngineSettings.WebAttribute.LocalContentCanAccessRemoteUrls, True)
        
        # map.html is in parent directory
        map_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'map.html')
        if os.path.exists(map_path):
            self.webview.load(QUrl.fromLocalFile(map_path))
        else:
            # If map file missing, display placeholder
            html = "<html><body><h3>map.html not found</h3></body></html>"
            self.webview.setHtml(html)

        # Add left splitter and right map with stretch factors (1:2)
        main_layout.addWidget(left_splitter, 1)
        main_layout.addWidget(self.webview, 2)

        # Initial "No Data" display
        self.show_no_data()
        
        # Load markers on map after initial setup
        QTimer.singleShot(1000, self.refresh_map_markers)
        
    def create_header(self) -> QWidget:
        """Create header with connection status and controls"""
        header = QFrame()
        header.setFrameStyle(QFrame.Shape.StyledPanel)
        layout = QHBoxLayout(header)
        
        # Connection status
        self.status_label = QLabel("⚫ Disconnected")
        self.status_label.setStyleSheet("font-weight: bold; color: red;")
        layout.addWidget(self.status_label)
        
        # Time since update
        self.time_label = QLabel("--")
        self.time_label.setStyleSheet("color: gray;")
        layout.addWidget(self.time_label)
        
        layout.addStretch()
        
        # Settings button
        settings_btn = QPushButton("⚙ Settings")
        settings_btn.clicked.connect(self.show_settings)
        layout.addWidget(settings_btn)
        
        # Auto-center toggle
        self.auto_btn = QPushButton("🎯 Auto-center")
        self.auto_btn.setCheckable(True)
        self.auto_btn.setChecked(True)
        self.auto_btn.toggled.connect(self.toggle_auto_center)
        layout.addWidget(self.auto_btn)

        # Center on position button
        center_btn = QPushButton("📍 Center")
        center_btn.clicked.connect(self.center_on_position)
        layout.addWidget(center_btn)

        # Toggle button
        self.toggle_btn = QPushButton("⏸ Pause")
        self.toggle_btn.clicked.connect(self.toggle_receiver)
        layout.addWidget(self.toggle_btn)
        
        return header
        
    def show_settings(self):
        """Show settings dialog"""
        dialog = SettingsDialog(self.receiver, self)
        dialog.exec()
        
    def toggle_receiver(self):
        """Toggle receiver on/off"""
        if self.receiver.running:
            self.receiver.stop()
            self.toggle_btn.setText("▶ Resume")
        else:
            self.receiver.start()
            self.toggle_btn.setText("⏸ Pause")
    
    def update_time_display(self):
        """Update time since last update display"""
        if self.receiver.last_update_time:
            self.time_label.setText(f"Last update: {self.receiver.get_time_since_update()}")
        else:
            self.time_label.setText("No data received")
    
    def on_connection_changed(self, connected: bool):
        """Handle connection status change"""
        if connected:
            self.status_label.setText("🟢 Connected")
            self.status_label.setStyleSheet("font-weight: bold; color: green;")
        else:
            self.status_label.setText("⚫ Disconnected")
            self.status_label.setStyleSheet("font-weight: bold; color: red;")
    
    def on_data_updated(self, flight_data: FlightData):
        """Handle new flight data"""
        self.display_flight_data(flight_data)
        # Update marker on the embedded map (if available)
        try:
            self.update_map_marker(flight_data)
        except Exception:
            pass

    def toggle_auto_center(self, checked: bool):
        """Enable/disable auto-centering on the map"""
        self.auto_center = checked
        self.auto_btn.setText("🎯 Auto-center" if checked else "Auto-center OFF")

    def update_map_marker(self, data: FlightData):
        """Send JS to the map to update marker / center"""
        if not self.webview or data is None:
            return
        lat = float(data.latitude)
        lon = float(data.longitude)
        heading = float(data.heading)
        # Only update when we have valid coordinates
        if lat == 0.0 and lon == 0.0:
            return
        center_flag = 'true' if self.auto_center else 'false'
        js = f"setMarker({lat:.6f}, {lon:.6f}, {heading:.1f}, {center_flag});"
        try:
            self.webview.page().runJavaScript(js)
        except Exception:
            pass

    def center_on_position(self):
        """Center the map on the last received position"""
        fd = self.receiver.flight_data
        if not fd:
            return
        if fd.latitude == 0.0 and fd.longitude == 0.0:
            return
        js = f"setMarker({fd.latitude:.6f}, {fd.longitude:.6f}, {fd.heading:.1f}, true);"
        try:
            if self.webview:
                self.webview.page().runJavaScript(js)
        except Exception:
            pass
    
    def on_location_selected(self, location: Location):
        """Handle location selection from LocationManager"""
        if self.webview and location:
            # Center map on selected location
            js = f"centerMap({location.latitude:.6f}, {location.longitude:.6f});"
            try:
                self.webview.page().runJavaScript(js)
            except Exception:
                pass
    
    def on_location_changed(self, *args):
        """Handle location add/update/delete - refresh markers"""
        self.refresh_map_markers()
    
    def refresh_map_markers(self):
        """Load all markers from database and display on map"""
        if not self.webview:
            return
        
        try:
            from core.marker_icons import TacticalMarkerStyle
            import json
            
            locations = self.db.get_all_locations()
            markers_data = []
            
            for loc in locations:
                # Get icon config
                icon_config = TacticalMarkerStyle.get_leaflet_icon_config(
                    loc.marker_type,
                    loc.coalition or "",
                    loc.tactical_symbol
                )
                
                # Build marker info
                marker_info = {
                    'id': loc.id,
                    'lat': loc.latitude,
                    'lon': loc.longitude,
                    'name': loc.name,
                    'icon': icon_config,
                    'description': loc.description or "",
                    'type': loc.marker_type,
                    'coalition': loc.coalition
                }
                
                # Add type-specific info
                if loc.icao:
                    marker_info['icao'] = loc.icao
                if loc.runways:
                    marker_info['runways'] = len(loc.runways)
                if loc.threat_level:
                    marker_info['threat'] = loc.threat_level
                
                markers_data.append(marker_info)
            
            # Send to map as JSON
            markers_json = json.dumps(markers_data).replace("'", "\\'")
            js = f"updateMarkers('{markers_json}');"
            self.webview.page().runJavaScript(js)
            
        except Exception as e:
            print(f"Error refreshing markers: {e}")

    def show_no_data(self):
        """Display 'No Data' message"""
        # Clear existing widgets
        while self.data_layout.count():
            item = self.data_layout.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
        
        no_data = QLabel("No flight data received yet\n\nWaiting for UDP packets...")
        no_data.setAlignment(Qt.AlignmentFlag.AlignCenter)
        no_data.setStyleSheet("color: gray; font-size: 14pt; padding: 40px;")
        self.data_layout.addWidget(no_data)
    
    def display_flight_data(self, data: FlightData):
        """Display flight data in sections"""
        # Clear existing widgets
        while self.data_layout.count():
            item = self.data_layout.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
        
        # Aircraft & Pilot Section
        aircraft_group = self.create_section("Aircraft & Pilot")
        aircraft_layout = QGridLayout()
        self.add_data_row(aircraft_layout, 0, "Aircraft:", data.aircraft or "N/A")
        self.add_data_row(aircraft_layout, 1, "Unit Name:", data.unitName or "N/A")
        self.add_data_row(aircraft_layout, 2, "Coalition:", data.coalition or "N/A")
        self.add_data_row(aircraft_layout, 3, "Group:", data.group or "N/A")
        aircraft_group.setLayout(aircraft_layout)
        self.data_layout.addWidget(aircraft_group)
        
        # Flight Parameters Section
        flight_group = self.create_section("Flight Parameters")
        flight_layout = QGridLayout()
        self.add_data_row(flight_layout, 0, "Altitude:", f"{data.altitude:.1f} m ({self.meters_to_feet(data.altitude):.0f} ft)")
        self.add_data_row(flight_layout, 1, "Heading:", f"{data.heading:.1f}°")
        self.add_data_row(flight_layout, 2, "Pitch:", f"{data.pitch:.1f}°")
        self.add_data_row(flight_layout, 3, "Bank:", f"{data.bank:.1f}°")
        if data.groundSpeed:
            self.add_data_row(flight_layout, 4, "Ground Speed:", f"{data.groundSpeed:.1f} m/s ({self.mps_to_knots(data.groundSpeed):.1f} kt)")
        if data.indicatedAirspeed:
            self.add_data_row(flight_layout, 5, "IAS:", f"{data.indicatedAirspeed:.1f} m/s ({self.mps_to_knots(data.indicatedAirspeed):.1f} kt)")
        if data.verticalSpeed:
            self.add_data_row(flight_layout, 6, "Vertical Speed:", f"{data.verticalSpeed:.1f} m/s ({self.mps_to_fpm(data.verticalSpeed):.0f} fpm)")
        if data.mach:
            self.add_data_row(flight_layout, 7, "Mach:", f"{data.mach:.2f}")
        flight_group.setLayout(flight_layout)
        self.data_layout.addWidget(flight_group)
        
        # Position Section
        position_group = self.create_section("Position")
        position_layout = QGridLayout()
        self.add_data_row(position_layout, 0, "Latitude:", f"{data.latitude:.6f}°")
        self.add_data_row(position_layout, 1, "Longitude:", f"{data.longitude:.6f}°")
        position_group.setLayout(position_layout)
        self.data_layout.addWidget(position_group)
        
        # Fuel Section
        if data.fuel:
            fuel_group = self.create_section("Fuel")
            fuel_layout = QGridLayout()
            fuel = data.fuel
            if 'remaining' in fuel:
                self.add_data_row(fuel_layout, 0, "Remaining:", f"{fuel['remaining']:.0f} kg")
            if 'total' in fuel:
                self.add_data_row(fuel_layout, 1, "Total:", f"{fuel['total']:.0f} kg")
            if 'internal' in fuel:
                self.add_data_row(fuel_layout, 2, "Internal:", f"{fuel['internal']:.0f} kg")
            if 'external' in fuel:
                self.add_data_row(fuel_layout, 3, "External:", f"{fuel['external']:.0f} kg")
            fuel_group.setLayout(fuel_layout)
            self.data_layout.addWidget(fuel_group)
        
        # AoA & G-Load Section
        if data.angleOfAttack or data.gLoad:
            aoa_group = self.create_section("AoA & G-Load")
            aoa_layout = QGridLayout()
            if data.angleOfAttack:
                self.add_data_row(aoa_layout, 0, "Angle of Attack:", f"{data.angleOfAttack:.2f}°")
            if data.gLoad:
                if 'current' in data.gLoad:
                    self.add_data_row(aoa_layout, 1, "Current G:", f"{data.gLoad['current']:.2f}")
                if 'max' in data.gLoad:
                    self.add_data_row(aoa_layout, 2, "Max G:", f"{data.gLoad['max']:.2f}")
            aoa_group.setLayout(aoa_layout)
            self.data_layout.addWidget(aoa_group)
        
        # Engine Data Section
        if data.engines:
            engine_group = self.create_section("Engine Data")
            engine_layout = QGridLayout()
            eng = data.engines
            if 'throttle' in eng:
                self.add_data_row(engine_layout, 0, "Throttle:", f"{eng['throttle']:.1f}%")
            if 'rpm' in eng:
                rpm = eng['rpm']
                if isinstance(rpm, dict):
                    if 'left' in rpm:
                        self.add_data_row(engine_layout, 1, "RPM Left:", f"{rpm['left']:.1f}%")
                    if 'right' in rpm:
                        self.add_data_row(engine_layout, 2, "RPM Right:", f"{rpm['right']:.1f}%")
            if 'afterburner' in eng:
                self.add_status_row(engine_layout, 3, "Afterburner:", eng['afterburner'])
            engine_group.setLayout(engine_layout)
            self.data_layout.addWidget(engine_group)
        
        # Mechanical Section (Gear, Flaps, etc.)
        if data.mechanical:
            mech_group = self.create_section("Mechanical")
            mech_layout = QGridLayout()
            mech = data.mechanical
            if 'gear' in mech:
                gear = mech['gear']
                if isinstance(gear, dict):
                    if 'value' in gear:
                        self.add_data_row(mech_layout, 0, "Gear Position:", f"{gear['value']:.1f}")
            if 'flaps' in mech:
                self.add_data_row(mech_layout, 1, "Flaps:", f"{mech['flaps']:.1f}")
            if 'speedBrake' in mech:
                self.add_data_row(mech_layout, 2, "Speed Brake:", f"{mech['speedBrake']:.1f}")
            if 'hook' in mech:
                self.add_status_row(mech_layout, 3, "Hook:", mech['hook'])
            mech_group.setLayout(mech_layout)
            self.data_layout.addWidget(mech_group)
        
        # Weapons Section
        if data.weapons:
            weapons_group = self.create_section("Weapons")
            weapons_layout = QGridLayout()
            wpn = data.weapons
            if 'masterArm' in wpn:
                self.add_status_row(weapons_layout, 0, "Master Arm:", wpn['masterArm'])
            if 'selected' in wpn:
                self.add_data_row(weapons_layout, 1, "Selected:", wpn['selected'] or "N/A")
            if 'totalCount' in wpn:
                self.add_data_row(weapons_layout, 2, "Total Weapons:", str(wpn['totalCount']))
            weapons_group.setLayout(weapons_layout)
            self.data_layout.addWidget(weapons_group)
        
        # Countermeasures Section
        if data.countermeasures:
            cm_group = self.create_section("Countermeasures")
            cm_layout = QGridLayout()
            cm = data.countermeasures
            if 'chaffCount' in cm:
                self.add_data_row(cm_layout, 0, "Chaff:", str(cm['chaffCount']))
            if 'flareCount' in cm:
                self.add_data_row(cm_layout, 1, "Flares:", str(cm['flareCount']))
            cm_group.setLayout(cm_layout)
            self.data_layout.addWidget(cm_group)
        
        # RWR / Threats Section
        if data.rwr:
            rwr_group = self.create_section("RWR / Threats")
            rwr_layout = QGridLayout()
            rwr = data.rwr
            if 'threatsDetected' in rwr:
                self.add_data_row(rwr_layout, 0, "Threats Detected:", str(rwr['threatsDetected']))
            if 'contacts' in rwr and rwr['contacts']:
                for i, contact in enumerate(rwr['contacts'][:5]):  # Show max 5
                    threat_info = f"{contact.get('type', 'Unknown')} @ {contact.get('bearing', 0):.0f}°"
                    self.add_data_row(rwr_layout, i+1, f"Contact {i+1}:", threat_info)
            rwr_group.setLayout(rwr_layout)
            self.data_layout.addWidget(rwr_group)
        
        # Environment Section
        if data.environment:
            env_group = self.create_section("Environment")
            env_layout = QGridLayout()
            env = data.environment
            if 'windDirection' in env:
                self.add_data_row(env_layout, 0, "Wind Direction:", f"{env['windDirection']:.0f}°")
            if 'windSpeed' in env:
                self.add_data_row(env_layout, 1, "Wind Speed:", f"{env['windSpeed']:.1f} m/s")
            if 'temperature' in env:
                self.add_data_row(env_layout, 2, "Temperature:", f"{env['temperature']:.1f}°C ({self.celsius_to_fahrenheit(env['temperature']):.1f}°F)")
            if 'pressure' in env:
                self.add_data_row(env_layout, 3, "Pressure:", f"{env['pressure']:.1f} hPa ({self.hpa_to_inhg(env['pressure']):.2f} inHg)")
            env_group.setLayout(env_layout)
            self.data_layout.addWidget(env_group)
        
        # Metadata Section
        meta_group = self.create_section("Additional Info")
        meta_layout = QGridLayout()
        self.add_data_row(meta_layout, 0, "Unit ID:", data.unitID)
        if data.missionTime:
            self.add_data_row(meta_layout, 1, "Mission Time:", f"{data.missionTime:.1f}s")
        self.add_data_row(meta_layout, 2, "Timestamp:", data.timestamp)
        meta_group.setLayout(meta_layout)
        self.data_layout.addWidget(meta_group)
        
        self.data_layout.addStretch()
    
    def create_section(self, title: str) -> QGroupBox:
        """Create a collapsible section"""
        group = QGroupBox(title)
        group.setStyleSheet("""
            QGroupBox {
                font-weight: bold;
                border: 1px solid #cccccc;
                border-radius: 5px;
                margin-top: 10px;
                padding-top: 10px;
            }
            QGroupBox::title {
                subcontrol-origin: margin;
                left: 10px;
                padding: 0 5px 0 5px;
            }
        """)
        return group
    
    def add_data_row(self, layout: QGridLayout, row: int, label: str, value: str):
        """Add a data row to the layout"""
        label_widget = QLabel(label)
        label_widget.setStyleSheet("font-weight: bold;")
        value_widget = QLabel(str(value))
        layout.addWidget(label_widget, row, 0)
        layout.addWidget(value_widget, row, 1)
    
    def add_status_row(self, layout: QGridLayout, row: int, label: str, active: bool):
        """Add a status row with colored indicator"""
        label_widget = QLabel(label)
        label_widget.setStyleSheet("font-weight: bold;")
        status = "🟢 ON" if active else "⚫ OFF"
        color = "green" if active else "gray"
        value_widget = QLabel(status)
        value_widget.setStyleSheet(f"color: {color};")
        layout.addWidget(label_widget, row, 0)
        layout.addWidget(value_widget, row, 1)
    
    # Unit conversion helpers
    def mps_to_knots(self, mps: float) -> float:
        return mps * 1.9438444924406
    
    def mps_to_fpm(self, mps: float) -> float:
        return mps * 196.850393701
    
    def meters_to_feet(self, meters: float) -> float:
        return meters * 3.28084
    
    def celsius_to_fahrenheit(self, celsius: float) -> float:
        return celsius * 9.0 / 5.0 + 32.0
    
    def hpa_to_inhg(self, hpa: float) -> float:
        return hpa * 0.02953
    
    def closeEvent(self, event):
        """Clean up when closing"""
        self.receiver.stop()
        self.db.close()
        event.accept()


def main():
    import logging
    # Enable debug logging for ECDH handshake diagnostics
    logging.basicConfig(
        level=logging.DEBUG,
        format='%(levelname)s:%(name)s:%(message)s'
    )
    
    app = QApplication(sys.argv)
    
    # Set dark theme
    app.setStyle("Fusion")
    palette = QPalette()
    palette.setColor(QPalette.ColorRole.Window, QColor(53, 53, 53))
    palette.setColor(QPalette.ColorRole.WindowText, Qt.GlobalColor.white)
    palette.setColor(QPalette.ColorRole.Base, QColor(35, 35, 35))
    palette.setColor(QPalette.ColorRole.AlternateBase, QColor(53, 53, 53))
    palette.setColor(QPalette.ColorRole.ToolTipBase, Qt.GlobalColor.white)
    palette.setColor(QPalette.ColorRole.ToolTipText, Qt.GlobalColor.white)
    palette.setColor(QPalette.ColorRole.Text, Qt.GlobalColor.white)
    palette.setColor(QPalette.ColorRole.Button, QColor(53, 53, 53))
    palette.setColor(QPalette.ColorRole.ButtonText, Qt.GlobalColor.white)
    palette.setColor(QPalette.ColorRole.BrightText, Qt.GlobalColor.red)
    palette.setColor(QPalette.ColorRole.Link, QColor(42, 130, 218))
    palette.setColor(QPalette.ColorRole.Highlight, QColor(42, 130, 218))
    palette.setColor(QPalette.ColorRole.HighlightedText, Qt.GlobalColor.black)
    app.setPalette(palette)
    
    window = DataPadGUI()
    window.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
