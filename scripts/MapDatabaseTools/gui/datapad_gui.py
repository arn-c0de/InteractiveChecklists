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
from core.markers_database import MarkersDatabase, Location, Border
from gui.location_manager import LocationManagerWidget
from gui.border_manager import BorderManagerWidget
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
        # Slightly smaller window for better assets visibility on smaller screens
        self.setGeometry(100, 100, 1200, 780)
        
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
        
        # Bottom: Unified Assets Manager (Markers & Borders) + hidden detailed panels
        from gui.assets_manager import AssetsManagerWidget
        self.assets_manager = AssetsManagerWidget(self.db)
        self.assets_manager.asset_selected.connect(self.on_asset_selected)
        self.assets_manager.asset_edited.connect(self.on_asset_changed)
        self.assets_manager.asset_deleted.connect(self.on_asset_deleted)
        self.assets_manager.draw_border_requested.connect(self.on_draw_border_requested)
        left_splitter.addWidget(self.assets_manager)

        # Keep detailed managers available (but placed in a collapsible area if needed)
        bottom_splitter = QSplitter(Qt.Orientation.Vertical)
        self.location_manager = LocationManagerWidget(self.db)
        self.location_manager.location_selected.connect(self.on_location_selected)
        self.location_manager.location_added.connect(self.on_location_changed)
        self.location_manager.location_updated.connect(self.on_location_changed)
        self.location_manager.location_deleted.connect(self.on_location_changed)
        bottom_splitter.addWidget(self.location_manager)

        self.border_manager = BorderManagerWidget(self.db)
        self.border_manager.border_selected.connect(self.on_border_selected)
        self.border_manager.border_added.connect(self.on_border_changed)
        self.border_manager.border_updated.connect(self.on_border_changed)
        self.border_manager.border_deleted.connect(self.on_border_deleted)
        self.border_manager.draw_border_requested.connect(self.on_draw_border_requested)
        self.border_manager.finish_drawing_requested.connect(self.on_finish_drawing_requested)
        bottom_splitter.addWidget(self.border_manager)

        bottom_splitter.setSizes([300, 300])
        left_splitter.addWidget(bottom_splitter)

        # Set initial splitter sizes (more space for assets at bottom)
        left_splitter.setSizes([450, 550])

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
        
        # Call marker/border refresh after the webview has loaded map.html
        if self.webview:
            # Ensure we refresh only after page load (avoid calling updateMarkers before JS is defined)
            self.webview.loadFinished.connect(self._on_map_load_finished)
            from PySide6.QtWebChannel import QWebChannel
            # For now, we'll poll for border drawing clicks via JavaScript callbacks
            # This is a simple implementation without WebChannel
            # Start polling for marker clicks once map loaded
            self._marker_click_timer = None
            self._popup_edit_timer = None
        
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

    def _on_map_load_finished(self, ok: bool):
        """Called when map.html finished loading - schedule marker/border refreshes"""
        if not ok:
            print("Warning: map.html failed to load in webview")
            return
        # small delay to allow page scripts to initialize
        QTimer.singleShot(200, self.refresh_map_markers)
        QTimer.singleShot(400, self.refresh_map_borders)

        # Start polling timers for marker clicks and popup edit requests
        if self._marker_click_timer is None:
            self._marker_click_timer = QTimer()
            self._marker_click_timer.timeout.connect(self.poll_marker_clicks)
            self._marker_click_timer.start(250)

        if self._popup_edit_timer is None:
            self._popup_edit_timer = QTimer()
            self._popup_edit_timer.timeout.connect(self.poll_popup_edit_requests)
            self._popup_edit_timer.start(500)

    def _call_js_when_ready(self, func_name: str, js: str, result_callback=None, retries: int = 6, delay: int = 500):
        """Call a JS function only when it's defined, retrying a few times if needed.

        Args:
            func_name: Name of the JS function to check (e.g., 'updateMarkers')
            js: The JS expression/string to execute (e.g., "updateMarkers('...');")
            result_callback: Optional Python callback to pass to runJavaScript
            retries: Number of retry attempts
            delay: Delay between retries in milliseconds
        """
        if not self.webview:
            return

        def _check_and_call(exists):
            try:
                if exists:
                    if result_callback:
                        self.webview.page().runJavaScript(js, result_callback)
                    else:
                        self.webview.page().runJavaScript(js)
                    setattr(self, f"_{func_name}_js_retries", 0)
                else:
                    cnt = getattr(self, f"_{func_name}_js_retries", 0) + 1
                    setattr(self, f"_{func_name}_js_retries", cnt)
                    if cnt <= retries:
                        # schedule another attempt
                        QTimer.singleShot(delay, lambda: self._call_js_when_ready(func_name, js, result_callback, retries, delay))
                    else:
                        print(f"{func_name} not available after retries; skipping call")
            except Exception as e:
                print(f"Error executing JS for {func_name}: {e}")

        # Ask the page whether the function exists
        try:
            self.webview.page().runJavaScript(f"typeof {func_name} === 'function'", _check_and_call)
        except Exception as e:
            print(f"Error checking for JS function {func_name}: {e}")        
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
            self._call_js_when_ready('setMarker', js)
        except Exception:
            pass

    def center_on_position(self):
        """Center the map on the last received position"""
        fd = self.receiver.flight_data
        if not fd:
            return
        if fd.latitude == 0.0 and fd.longitude == 0.0:
            return
        js = f"setMarker({fd.latitude:.6f}, {fd.longitude:.1f}, {fd.heading:.1f}, true);"
        try:
            if self.webview:
                self._call_js_when_ready('setMarker', js)
        except Exception:
            pass
    
    def on_location_selected(self, location: Location):
        """Handle location selection from LocationManager"""
        if self.webview and location:
            # Center map on selected location
            js = f"centerMap({location.latitude:.6f}, {location.longitude:.6f});"
            try:
                self._call_js_when_ready('centerMap', js)
            except Exception:
                pass

    def on_asset_selected(self, asset_tuple):
        """Handle selection from AssetsManager (marker or border)"""
        if not asset_tuple:
            return
        typ, obj = asset_tuple
        if typ == "marker":
            self.on_location_selected(obj)
        elif typ == "border":
            self.on_border_selected(obj)

    def on_asset_changed(self, asset_tuple):
        """Handle edited asset from AssetsManager - refresh relevant UI"""
        if not asset_tuple:
            return
        typ, obj = asset_tuple
        try:
            if typ == "marker":
                self.refresh_map_markers()
            elif typ == "border":
                self.refresh_map_borders()
            # Keep unified list in sync
            try:
                self.assets_manager.refresh_list()
            except Exception:
                pass
        except Exception as e:
            print(f"Error handling asset change: {e}")

    def on_asset_deleted(self, asset_tuple):
        """Handle deleted asset from AssetsManager - refresh UI and lists"""
        if not asset_tuple:
            return
        typ, obj = asset_tuple
        try:
            if typ == "marker":
                self.refresh_map_markers()
            elif typ == "border":
                self.refresh_map_borders()
            try:
                self.assets_manager.refresh_list()
            except Exception:
                pass
        except Exception as e:
            print(f"Error handling asset delete: {e}")

    def on_location_changed(self, *args):
        """Handle location add/update/delete - refresh markers"""
        self.refresh_map_markers()
    
    def on_border_selected(self, border: Border):
        """Handle border selection - zoom to border on map"""
        if self.webview and border and border.points:
            # Calculate center of border
            lats = [p[0] for p in border.points]
            lons = [p[1] for p in border.points]
            center_lat = sum(lats) / len(lats)
            center_lon = sum(lons) / len(lons)
            
            # Center map on border
            js = f"centerMap({center_lat:.6f}, {center_lon:.6f});"
            try:
                self._call_js_when_ready('centerMap', js)
            except Exception:
                pass
    
    def on_border_changed(self, *args):
        """Handle border add/update - refresh borders"""
        self.refresh_map_borders()
        # Also refresh unified list
        try:
            self.assets_manager.refresh_list()
        except Exception:
            pass
    
    def on_border_deleted(self, border_id: int):
        """Handle border deletion"""
        # Special case: border_id == -1 means clear drawing mode
        if border_id == -1:
            self.stop_border_drawing()
        else:
            self.refresh_map_borders()
    
    def on_draw_border_requested(self):
        """Start border drawing mode on map"""
        if not self.webview:
            return
        
        try:
            # Activate drawing mode in map
            js = "startBorderDrawing();"
            try:
                self._call_js_when_ready('startBorderDrawing', js)
            except Exception as e:
                print(f"Error starting border drawing (JS): {e}")
            
            # Start polling for clicks (simple implementation)
            self.border_click_timer = QTimer()
            self.border_click_timer.timeout.connect(self.poll_border_clicks)
            self.border_click_timer.start(200)  # Poll every 200ms
            
        except Exception as e:
            print(f"Error starting border drawing: {e}")

    def on_finish_drawing_requested(self):
        """User clicked Finish in Border Manager - draw closing line and finish"""
        if not self.webview:
            # If webview missing, just finish server-side
            try:
                self.border_manager.finish_drawing()
            except Exception as e:
                print(f"Error finishing border: {e}")
            return
        
        try:
            import json
            points = self.border_manager.current_drawing_points
            if not points or len(points) < 3:
                QMessageBox.warning(self, "Invalid Border", "Border must have at least 3 points.")
                return
            # Ask map to draw closing line/polygon for visual feedback
            pts_json = json.dumps(points)
            js = f"drawClosingLine({pts_json});"
            # Draw closing line briefly (guarded)
            try:
                self._call_js_when_ready('drawClosingLine', js)
            except Exception as e:
                print(f"Error drawing closing line (JS): {e}")
            # Delay slightly to allow user to see closing line, then finalize
            from PySide6.QtCore import QTimer
            QTimer.singleShot(250, lambda: self._finalize_finish_drawing())
        except Exception as e:
            print(f"Error requesting map draw for finish: {e}")
            # Fallback: just finish
            try:
                self.border_manager.finish_drawing()
            except Exception as e:
                print(f"Error finishing border: {e}")
    
    def stop_border_drawing(self):
        """Stop border drawing mode"""
        if hasattr(self, 'border_click_timer'):
            self.border_click_timer.stop()
        
        if self.webview:
            try:
                js = "stopBorderDrawing();"
                try:
                    self._call_js_when_ready('stopBorderDrawing', js)
                except Exception:
                    pass
            except Exception:
                pass
    
    def poll_border_clicks(self):
        """Poll for border drawing clicks from map"""
        if not self.border_manager.is_drawing:
            if hasattr(self, 'border_click_timer'):
                self.border_click_timer.stop()
            return
        
        if not self.webview:
            return
        
        try:
            # Get clicks from JavaScript (guarded)
            try:
                self._call_js_when_ready('getBorderClicks', "getBorderClicks();", result_callback=self.process_border_clicks)
            except Exception as e:
                print(f"Error polling border clicks (JS): {e}")
        except Exception as e:
            print(f"Error polling border clicks: {e}")

    def poll_marker_clicks(self):
        """Poll for marker click events from the map"""
        if not self.webview:
            return
        try:
            try:
                self._call_js_when_ready('getClickedMarker', "getClickedMarker();", result_callback=self.process_marker_click)
            except Exception as e:
                print(f"Error polling marker clicks (JS): {e}")
        except Exception as e:
            print(f"Error polling marker clicks: {e}")

    def process_marker_click(self, id_json: str):
        """Handle a marker click returned from JS (id as JSON string)"""
        if not id_json:
            return
        try:
            import json
            marker_id = json.loads(id_json)
            if not marker_id:
                return
            # Center map and select the location in the UI
            try:
                loc = self.db.get_location(marker_id)
                if loc:
                    # Center map on this location
                    self.on_location_selected(loc)
                    # Select it in the location manager list
                    try:
                        self.location_manager.select_location_by_id(marker_id)
                    except Exception:
                        pass
            except Exception as e:
                print(f"Error handling marker click: {e}")
        except Exception:
            pass

    def poll_popup_edit_requests(self):
        """Poll whether user clicked Edit in a popup (JS)"""
        if not self.webview:
            return
        try:
            try:
                self._call_js_when_ready('getPopupEditRequest', "getPopupEditRequest();", result_callback=self.process_popup_edit_request)
            except Exception as e:
                print(f"Error polling popup edit requests (JS): {e}")
        except Exception as e:
            print(f"Error polling popup edit requests: {e}")

    def process_popup_edit_request(self, id_json: str):
        """Open edit dialog for marker requested via popup"""
        if not id_json:
            return
        try:
            import json
            marker_id = json.loads(id_json)
            if not marker_id:
                return
            try:
                loc = self.db.get_location(marker_id)
                if loc:
                    # Open location edit dialog
                    from gui.location_manager import LocationEditDialog
                    dialog = LocationEditDialog(self.db, loc, None, self)
                    if dialog.exec() == dialog.DialogCode.Accepted:
                        try:
                            success = self.db.update_location(loc)
                            if success:
                                # Refresh map and lists
                                self.refresh_map_markers()
                                try:
                                    self.location_manager.refresh_list()
                                except Exception:
                                    pass
                            else:
                                print("Failed to update location from popup edit")
                        except Exception as e:
                            print(f"Error saving edited location: {e}")
            except Exception as e:
                print(f"Error opening edit dialog from popup: {e}")
        except Exception:
            pass
    
    def process_border_clicks(self, clicks_json: str):
        """Process border clicks received from map"""
        if not clicks_json or clicks_json == "[]":
            return
        
        try:
            import json
            clicks = json.loads(clicks_json)
            
            for click in clicks:
                lat = click['lat']
                lon = click['lon']
                should_close = click.get('shouldClose', False)
                
                if should_close:
                    # Close the polygon
                    if self.border_manager.finish_drawing():
                        self.stop_border_drawing()
                else:
                    # Add point to border manager
                    self.border_manager.add_point(lat, lon)
                    
                    # Keep finish button state in sync
                    try:
                        self.border_manager.update_finish_button_state()
                    except Exception:
                        pass
        except Exception as e:
            print(f"Error processing border clicks: {e}")

    def _finalize_finish_drawing(self):
        """Called after JS draws closing line to finalize saving on Python side"""
        try:
            result = self.border_manager.finish_drawing()
            if result:
                # Clear drawing on map
                self.stop_border_drawing()
                # Ensure assets list updated
                try:
                    self.assets_manager.refresh_list()
                except Exception:
                    pass
            else:
                # Re-enable finish button if user cancelled the dialog
                try:
                    self.border_manager.finish_btn.setEnabled(True)
                except Exception:
                    pass
        except Exception as e:
            print(f"Error finalizing finish drawing: {e}")    
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
                # Determine affiliation: prefer symbol_affiliation, fallback to coalition
                aff = getattr(loc, 'symbol_affiliation', None) or (loc.coalition or "")
                # Normalize legacy coalition names (BLUFOR/OPFOR/NEUTRAL)
                if isinstance(aff, str) and aff.upper() in ("BLUFOR", "OPFOR", "NEUTRAL"):
                    _map = {"BLUFOR": "friendly", "OPFOR": "hostile", "NEUTRAL": "neutral"}
                    affiliation = _map.get(aff.upper(), aff.lower())
                else:
                    affiliation = aff.lower() if isinstance(aff, str) and aff else 'unknown'

                # Prefer the new `symbol_entity`, then legacy `tactical_symbol` and `icon`
                symbol_entity = getattr(loc, 'symbol_entity', None) or (loc.tactical_symbol or "")

                # Try to get Android-compatible icon; if missing, fall back to a generic icon
                try:
                    if symbol_entity:
                        icon_config = TacticalMarkerStyle.get_leaflet_icon_config(symbol_entity, affiliation)
                    else:
                        # No symbol specified - generic icon using affiliation color
                        from core.marker_icons import get_affiliation_color
                        color = get_affiliation_color(affiliation)
                        icon_config = {'icon': 'info-sign', 'color': color, 'prefix': 'glyphicon'}
                except Exception:
                    # Symbol not available / failed to build - fallback to generic icon so marker is still visible
                    from core.marker_icons import get_affiliation_color
                    color = get_affiliation_color(affiliation)
                    icon_config = {'icon': 'info-sign', 'color': color, 'prefix': 'glyphicon'}

                # Ensure icon_config includes HTML fragment expected by map.html
                if not isinstance(icon_config, dict):
                    icon_config = {'icon': 'info-sign', 'color': '#FFFF80', 'prefix': 'glyphicon'}

                if 'html' not in icon_config or not icon_config.get('html'):
                    # construct simple colored square fallback
                    color = icon_config.get('color', '#FFFF80')
                    size = icon_config.get('iconSize', [28, 28])[0] if isinstance(icon_config.get('iconSize', None), list) else 28
                    icon_config['html'] = f'<div style="width:{size}px;height:{size}px;background:{color};border-radius:4px;border:1px solid rgba(0,0,0,0.1)"></div>'
                    icon_config['className'] = icon_config.get('className', 'custom-marker tactical-marker')
                    icon_config['iconSize'] = icon_config.get('iconSize', [size, size])
                    icon_config['iconAnchor'] = icon_config.get('iconAnchor', [size // 2, size // 2])
                    icon_config['popupAnchor'] = icon_config.get('popupAnchor', [0, -size // 2])

                # Debug: show keys and a short preview of html
                try:
                    print(f"Marker {loc.id} icon keys: {list(icon_config.keys())}; html_present={ 'html' in icon_config }")
                    if 'html' in icon_config:
                        print(f"Marker {loc.id} html preview: {str(icon_config['html'])[:80]}")
                except Exception:
                    pass

                # Build marker info
                marker_info = {
                    'id': loc.id,
                    'lat': loc.latitude,
                    'lon': loc.longitude,
                    'name': loc.name,
                    'icon': icon_config,
                    'description': loc.description or "",
                    'type': loc.marker_type,
                    'coalition': affiliation
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
            markers_json = json.dumps(markers_data)
            print(f"Refreshing markers: {len(markers_data)} entries")

            def _send_or_retry(exists):
                if exists:
                    # Pass JSON directly as JS object (no string wrapping to avoid escaping issues)
                    js = f"updateMarkers({markers_json});"
                    try:
                        self.webview.page().runJavaScript(js)
                    except Exception as e:
                        print(f"Error executing updateMarkers JS: {e}")
                    self._marker_js_retries = 0
                else:
                    self._marker_js_retries = getattr(self, '_marker_js_retries', 0) + 1
                    if self._marker_js_retries <= 6:
                        print(f"updateMarkers not defined yet; retrying ({self._marker_js_retries})")
                        QTimer.singleShot(500, self.refresh_map_markers)
                    else:
                        print("updateMarkers not available after retries; skipping marker update")

            self.webview.page().runJavaScript("typeof updateMarkers === 'function'", _send_or_retry)
            
        except Exception as e:
            print(f"Error refreshing markers: {e}")
    
    def refresh_map_borders(self):
        """Load all borders from database and display on map"""
        if not self.webview:
            return
        
        try:
            import json
            
            borders = self.db.get_all_borders()
            borders_data = []
            
            for border in borders:
                border_info = {
                    'id': border.id,
                    'name': border.name,
                    'points': border.points,
                    'description': border.description or "",
                    'color': border.color
                }
                borders_data.append(border_info)
            
            # Send to map as JSON (directly as JS object, no string wrapping)
            borders_json = json.dumps(borders_data)

            def _send_or_retry_b(exists):
                if exists:
                    js = f"updateBorders({borders_json});"
                    try:
                        self.webview.page().runJavaScript(js)
                    except Exception as e:
                        print(f"Error executing updateBorders JS: {e}")
                    self._borders_js_retries = 0
                else:
                    self._borders_js_retries = getattr(self, '_borders_js_retries', 0) + 1
                    if self._borders_js_retries <= 6:
                        print(f"updateBorders not defined yet; retrying ({self._borders_js_retries})")
                        QTimer.singleShot(500, self.refresh_map_borders)
                    else:
                        print("updateBorders not available after retries; skipping border update")

            self.webview.page().runJavaScript("typeof updateBorders === 'function'", _send_or_retry_b)
            
        except Exception as e:
            print(f"Error refreshing borders: {e}")

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
        # Prepare Environment group to be placed next to Aircraft if available
        env_group = None
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
        
        # Top row: Aircraft & Environment side-by-side (if Env present)
        top_row = QWidget()
        top_layout = QHBoxLayout(top_row)
        top_layout.setContentsMargins(0,0,0,0)
        top_layout.addWidget(aircraft_group, 2)
        if env_group:
            top_layout.addWidget(env_group, 1)
        self.data_layout.addWidget(top_row)
        
        # Flight Parameters and Position side-by-side
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
        
        position_group = self.create_section("Position")
        position_layout = QGridLayout()
        self.add_data_row(position_layout, 0, "Latitude:", f"{data.latitude:.6f}°")
        self.add_data_row(position_layout, 1, "Longitude:", f"{data.longitude:.6f}°")
        position_group.setLayout(position_layout)
        
        # Put flight and position side by side
        side_row = QWidget()
        side_layout = QHBoxLayout(side_row)
        side_layout.setContentsMargins(0, 0, 0, 0)
        side_layout.addWidget(flight_group, 2)
        side_layout.addWidget(position_group, 1)
        self.data_layout.addWidget(side_row)
        
        # Fuel Section (paired with AoA/G-Load when both exist)
        fuel_group = None
        aoa_group = None
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
        
        if fuel_group and aoa_group:
            row = QWidget()
            hl = QHBoxLayout(row)
            hl.setContentsMargins(0,0,0,0)
            hl.addWidget(fuel_group, 1)
            hl.addWidget(aoa_group, 1)
            self.data_layout.addWidget(row)
        else:
            if fuel_group:
                self.data_layout.addWidget(fuel_group)
            if aoa_group:
                self.data_layout.addWidget(aoa_group)
        
        # Engine Data and Mechanical side-by-side when both exist
        engine_group = None
        mech_group = None
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
        
        if engine_group and mech_group:
            row = QWidget()
            hl = QHBoxLayout(row)
            hl.setContentsMargins(0,0,0,0)
            hl.addWidget(engine_group, 1)
            hl.addWidget(mech_group, 1)
            self.data_layout.addWidget(row)
        else:
            if engine_group:
                self.data_layout.addWidget(engine_group)
            if mech_group:
                self.data_layout.addWidget(mech_group)
        
        # Weapons & Countermeasures (side-by-side if both present)
        weapons_group = None
        cm_group = None
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

        if data.countermeasures:
            cm_group = self.create_section("Countermeasures")
            cm_layout = QGridLayout()
            cm = data.countermeasures
            if 'chaffCount' in cm:
                self.add_data_row(cm_layout, 0, "Chaff:", str(cm['chaffCount']))
            if 'flareCount' in cm:
                self.add_data_row(cm_layout, 1, "Flares:", str(cm['flareCount']))
            cm_group.setLayout(cm_layout)

        if weapons_group and cm_group:
            row = QWidget()
            hl = QHBoxLayout(row)
            hl.setContentsMargins(0,0,0,0)
            hl.addWidget(weapons_group, 1)
            hl.addWidget(cm_group, 1)
            self.data_layout.addWidget(row)
        else:
            if weapons_group:
                self.data_layout.addWidget(weapons_group)
            if cm_group:
                self.data_layout.addWidget(cm_group)

        # RWR / Threats and Metadata side-by-side when both exist
        rwr_group = None
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

        # Metadata Section
        meta_group = self.create_section("Additional Info")
        meta_layout = QGridLayout()
        self.add_data_row(meta_layout, 0, "Unit ID:", data.unitID)
        if data.missionTime:
            self.add_data_row(meta_layout, 1, "Mission Time:", f"{data.missionTime:.1f}s")
        self.add_data_row(meta_layout, 2, "Timestamp:", data.timestamp)
        meta_group.setLayout(meta_layout)

        if rwr_group and meta_group:
            row = QWidget()
            hl = QHBoxLayout(row)
            hl.setContentsMargins(0,0,0,0)
            hl.addWidget(rwr_group, 1)
            hl.addWidget(meta_group, 1)
            self.data_layout.addWidget(row)
        else:
            if rwr_group:
                self.data_layout.addWidget(rwr_group)
            if meta_group:
                self.data_layout.addWidget(meta_group)

        self.data_layout.addStretch()
    
    def create_section(self, title: str) -> QGroupBox:
        """Create a collapsible section"""
        group = QGroupBox(title)
        group.setStyleSheet("""
            QGroupBox {
                font-weight: bold;
                font-size: 9pt;
                border: 1px solid #cccccc;
                border-radius: 5px;
                margin-top: 8px;
                padding-top: 8px;
            }
            QGroupBox::title {
                subcontrol-origin: margin;
                left: 8px;
                padding: 0 5px 0 5px;
            }
        """)
        return group
    
    def add_data_row(self, layout: QGridLayout, row: int, label: str, value: str):
        """Add a data row to the layout"""
        label_widget = QLabel(label)
        label_widget.setStyleSheet("font-weight: bold; font-size: 9pt;")
        value_widget = QLabel(str(value))
        value_widget.setStyleSheet("font-size: 9pt;")
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

    def add_status_row(self, layout: QGridLayout, row: int, label: str, active: bool):
        """Add a status row with colored indicator"""
        label_widget = QLabel(label)
        label_widget.setStyleSheet("font-weight: bold; font-size: 9pt;")
        status = "🟢 ON" if active else "⚫ OFF"
        color = "green" if active else "gray"
        value_widget = QLabel(status)
        value_widget.setStyleSheet(f"color: {color}; font-size: 9pt;")
        layout.addWidget(label_widget, row, 0)
        layout.addWidget(value_widget, row, 1)
    
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
