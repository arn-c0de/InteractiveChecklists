#!/usr/bin/env python3
"""
DCS DataPad Server - Auto-Installer & Launcher
Complete GUI application for installation, configuration, device management, and server control

Features:
- Automatic DCS installation detection
- Dependency installation and validation
- Device authorization management (QR scan, manual entry)
- Server configuration with live validation
- Integrated launcher with system tray
- Health checks and diagnostics
- First-time setup wizard
- Log viewer with filtering

Tech Stack: Python 3.8+ | PyQt6 | cryptography | qrcode
"""

import sys
import os
import json
import logging
import subprocess
import winreg
import shutil
import tempfile
import threading
import time
from pathlib import Path
from typing import Optional, Dict, List, Tuple
from datetime import datetime

# PyQt6 imports
try:
    from PyQt6.QtWidgets import (
        QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
        QPushButton, QLabel, QLineEdit, QTextEdit, QTabWidget, QMessageBox,
        QFileDialog, QTableWidget, QTableWidgetItem, QHeaderView, QComboBox,
        QSpinBox, QCheckBox, QGroupBox, QFormLayout, QProgressBar, QDialog,
        QWizard, QWizardPage, QListWidget, QSplitter, QSystemTrayIcon, QMenu,
        QInputDialog, QTextBrowser, QScrollArea
    )
    from PyQt6.QtCore import Qt, QThread, pyqtSignal, QTimer, QSize
    from PyQt6.QtGui import QIcon, QFont, QAction, QTextCursor, QPixmap
except ImportError:
    print("ERROR: PyQt6 not installed!")
    print("Install with: pip install PyQt6")
    sys.exit(1)

# Configure logging with UTF-8 encoding
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[
        logging.FileHandler('installer_gui.log', encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

# Set StreamHandler encoding to UTF-8 for console output
for handler in logging.getLogger().handlers:
    if isinstance(handler, logging.StreamHandler) and handler.stream == sys.stderr:
        handler.stream.reconfigure(encoding='utf-8')


class DCSPathDetector:
    """Detects DCS World installation paths on Windows"""

    COMMON_PATHS = [
        r"C:\Program Files\Eagle Dynamics\DCS World",
        r"C:\Program Files\Eagle Dynamics\DCS World OpenBeta",
        r"C:\Program Files (x86)\Steam\steamapps\common\DCSWorld",
        r"D:\DCS World",
        r"D:\Steam\steamapps\common\DCSWorld",
    ]

    SAVED_GAMES_PATHS = [
        Path.home() / "Saved Games" / "DCS",
        Path.home() / "Saved Games" / "DCS.openbeta",
    ]

    @staticmethod
    def detect_installation() -> List[str]:
        """Detect DCS installation paths using multiple methods"""
        found_paths = []

        # Method 1: Check registry
        try:
            registry_paths = DCSPathDetector._check_registry()
            found_paths.extend(registry_paths)
        except Exception as e:
            logger.debug(f"Registry check failed: {e}")

        # Method 2: Check common installation directories
        for path in DCSPathDetector.COMMON_PATHS:
            if os.path.exists(path) and os.path.isdir(path):
                # Verify it's actually DCS by checking for DCS.exe
                if os.path.exists(os.path.join(path, "bin", "DCS.exe")):
                    if path not in found_paths:
                        found_paths.append(path)

        return found_paths

    @staticmethod
    def _check_registry() -> List[str]:
        """Check Windows registry for DCS installations"""
        paths = []
        registry_keys = [
            (winreg.HKEY_CURRENT_USER, r"Software\Eagle Dynamics\DCS World"),
            (winreg.HKEY_CURRENT_USER, r"Software\Eagle Dynamics\DCS World OpenBeta"),
            (winreg.HKEY_LOCAL_MACHINE, r"SOFTWARE\Eagle Dynamics\DCS World"),
        ]

        for hkey, subkey in registry_keys:
            try:
                with winreg.OpenKey(hkey, subkey) as key:
                    path, _ = winreg.QueryValueEx(key, "Path")
                    if os.path.exists(path) and path not in paths:
                        paths.append(path)
            except FileNotFoundError:
                continue
            except Exception as e:
                logger.debug(f"Registry key {subkey} error: {e}")

        return paths

    @staticmethod
    def detect_saved_games() -> Optional[str]:
        """Detect DCS Saved Games folder (for Scripts placement)"""
        for path in DCSPathDetector.SAVED_GAMES_PATHS:
            if path.exists() and path.is_dir():
                scripts_dir = path / "Scripts"
                # Verify it's the right folder by checking for Export.lua
                if scripts_dir.exists() or (path / "Config").exists():
                    return str(path)
        return None


class DependencyChecker:
    """Checks and installs required dependencies"""

    REQUIRED_MODULES = [
        "cryptography",
        "qrcode"
    ]

    @staticmethod
    def check_python_version() -> Tuple[bool, str]:
        """Check if Python version is compatible (3.8+)"""
        version = sys.version_info
        if version.major >= 3 and version.minor >= 8:
            return True, f"Python {version.major}.{version.minor}.{version.micro}"
        return False, f"Python {version.major}.{version.minor}.{version.micro} (requires 3.8+)"

    @staticmethod
    def check_module(module_name: str) -> bool:
        """Check if a Python module is installed"""
        try:
            __import__(module_name)
            return True
        except ImportError:
            return False

    @staticmethod
    def install_module(module_name: str, progress_callback=None) -> Tuple[bool, str]:
        """Install a Python module using pip"""
        try:
            if progress_callback:
                progress_callback(f"Installing {module_name}...")

            result = subprocess.run(
                [sys.executable, "-m", "pip", "install", module_name],
                capture_output=True,
                text=True,
                timeout=120
            )

            if result.returncode == 0:
                return True, f"{module_name} installed successfully"
            else:
                return False, f"Failed to install {module_name}: {result.stderr}"
        except subprocess.TimeoutExpired:
            return False, f"Installation of {module_name} timed out"
        except Exception as e:
            return False, f"Error installing {module_name}: {str(e)}"

    @staticmethod
    def check_all_dependencies() -> Dict[str, bool]:
        """Check all required dependencies"""
        results = {}
        for module in DependencyChecker.REQUIRED_MODULES:
            results[module] = DependencyChecker.check_module(module)
        return results


class ServerProcess(QThread):
    """Background thread for running the DCS DataPad server"""

    output_received = pyqtSignal(str)
    error_received = pyqtSignal(str)
    process_started = pyqtSignal()
    process_stopped = pyqtSignal(int)

    def __init__(self, command: str, working_dir: str):
        super().__init__()
        self.command = command
        self.working_dir = working_dir
        self.process = None
        self._stop_requested = False

    def run(self):
        """Run the server process"""
        try:
            logger.info(f"Starting server with command: {self.command}")
            logger.info(f"Working directory: {self.working_dir}")

            # Set UTF-8 encoding for subprocess to handle emojis
            env = os.environ.copy()
            env['PYTHONIOENCODING'] = 'utf-8'
            env['PYTHONUTF8'] = '1'

            self.process = subprocess.Popen(
                self.command,
                shell=True,
                cwd=self.working_dir,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,  # Merge stderr into stdout
                text=True,
                bufsize=1,
                encoding='utf-8',
                errors='replace',
                env=env  # Pass UTF-8 environment
            )

            self.process_started.emit()
            logger.info(f"Server process started (PID: {self.process.pid})")

            # Read output in real-time
            output_lines = []
            for line in iter(self.process.stdout.readline, ''):
                if self._stop_requested:
                    break
                if line:
                    line_stripped = line.rstrip()
                    output_lines.append(line_stripped)
                    self.output_received.emit(line_stripped)
                    logger.info(f"[SERVER] {line_stripped}")

            # Wait for process to complete
            self.process.wait()
            exit_code = self.process.returncode

            # If process failed quickly, emit collected output as error
            if exit_code != 0 and output_lines:
                error_msg = "\n".join(output_lines[-10:])  # Last 10 lines
                self.error_received.emit(f"Server exited with code {exit_code}:\n{error_msg}")

            self.process_stopped.emit(exit_code)
            logger.info(f"Server process stopped (exit code: {exit_code})")

        except FileNotFoundError as e:
            error_msg = f"Server script not found: {e}"
            logger.error(error_msg)
            self.error_received.emit(error_msg)
            self.process_stopped.emit(-1)
        except Exception as e:
            error_msg = f"Server process error: {e}"
            logger.error(error_msg)
            self.error_received.emit(error_msg)
            self.process_stopped.emit(-1)

    def stop(self):
        """Stop the server process"""
        self._stop_requested = True
        if self.process and self.process.poll() is None:
            logger.info("Terminating server process...")
            self.process.terminate()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                logger.warning("Process did not terminate, killing...")
                self.process.kill()


class DeviceAuthManager:
    """Manages authorized devices (authorized_devices.json)"""

    def __init__(self, whitelist_path: str):
        self.whitelist_path = Path(whitelist_path)
        self.devices = []
        self.load_devices()

    def load_devices(self):
        """Load devices from whitelist file"""
        try:
            if self.whitelist_path.exists():
                with open(self.whitelist_path, 'r') as f:
                    data = json.load(f)
                    self.devices = data.get('devices', [])
                logger.info(f"Loaded {len(self.devices)} authorized devices")
            else:
                logger.warning(f"Whitelist file not found: {self.whitelist_path}")
                self.devices = []
        except Exception as e:
            logger.error(f"Error loading devices: {e}")
            self.devices = []

    def save_devices(self) -> bool:
        """Save devices to whitelist file"""
        try:
            # Create backup
            if self.whitelist_path.exists():
                backup_path = self.whitelist_path.with_suffix('.json.bak')
                shutil.copy2(self.whitelist_path, backup_path)

            # Ensure parent directory exists
            self.whitelist_path.parent.mkdir(parents=True, exist_ok=True)

            # Save with formatting
            data = {
                "devices": self.devices,
                "_lastModified": datetime.now().isoformat()
            }

            with open(self.whitelist_path, 'w') as f:
                json.dump(data, f, indent=2)

            logger.info(f"Saved {len(self.devices)} devices to {self.whitelist_path}")
            return True
        except Exception as e:
            logger.error(f"Error saving devices: {e}")
            return False

    def add_device(self, device_id: str, name: str, public_key: str,
                   permissions: List[str] = None) -> bool:
        """Add a new device to the whitelist"""
        # Check if device already exists
        for device in self.devices:
            if device['deviceId'] == device_id:
                logger.warning(f"Device {device_id} already exists")
                return False

        new_device = {
            "deviceId": device_id,
            "name": name,
            "publicKey": public_key,
            "permissions": permissions or ["receive", "send_commands"],
            "addedDate": datetime.now().isoformat(),
            "addedBy": "gui_installer"
        }

        self.devices.append(new_device)
        return self.save_devices()

    def remove_device(self, device_id: str) -> bool:
        """Remove a device from the whitelist"""
        original_count = len(self.devices)
        self.devices = [d for d in self.devices if d['deviceId'] != device_id]

        if len(self.devices) < original_count:
            return self.save_devices()
        return False

    def update_device(self, device_id: str, **kwargs) -> bool:
        """Update device properties"""
        for device in self.devices:
            if device['deviceId'] == device_id:
                device.update(kwargs)
                return self.save_devices()
        return False


class HealthChecker:
    """Performs system health checks"""

    @staticmethod
    def check_dcs_installation(dcs_path: str) -> Tuple[bool, str]:
        """Verify DCS installation"""
        if not os.path.exists(dcs_path):
            return False, "Path does not exist"

        dcs_exe = os.path.join(dcs_path, "bin", "DCS.exe")
        if not os.path.exists(dcs_exe):
            return False, "DCS.exe not found in bin folder"

        return True, "DCS installation verified"

    @staticmethod
    def check_export_scripts(saved_games_path: str) -> Tuple[bool, str]:
        """Check if export scripts are installed"""
        scripts_path = Path(saved_games_path) / "Scripts"
        export_lua = scripts_path / "Export.lua"

        if not scripts_path.exists():
            return False, "Scripts folder not found"

        if not export_lua.exists():
            return False, "Export.lua not installed"

        return True, "Export scripts installed"

    @staticmethod
    def check_network_connectivity(host: str, port: int) -> Tuple[bool, str]:
        """Test network connectivity"""
        import socket
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.settimeout(1)
            sock.bind((host, port))
            sock.close()
            return True, f"Port {port} available on {host}"
        except OSError as e:
            return False, f"Port {port} unavailable: {str(e)}"

    @staticmethod
    def check_firewall() -> Tuple[bool, str]:
        """Check Windows Firewall status (basic check)"""
        try:
            result = subprocess.run(
                ["netsh", "advfirewall", "show", "currentprofile"],
                capture_output=True,
                text=True,
                timeout=5
            )

            if "State" in result.stdout and "ON" in result.stdout:
                return True, "Windows Firewall is active (may need rules for DCS DataPad)"
            else:
                return True, "Windows Firewall status unclear"
        except Exception as e:
            return False, f"Could not check firewall: {str(e)}"


class SetupWizard(QWizard):
    """First-time setup wizard"""

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("DCS DataPad Server - First Time Setup")
        self.setWizardStyle(QWizard.WizardStyle.ModernStyle)
        self.setMinimumSize(700, 500)

        # Add pages
        self.addPage(WelcomePage())
        self.addPage(DCSDetectionPage())
        self.addPage(DependencyInstallPage())
        self.addPage(ServerConfigPage())
        self.addPage(CompletionPage())

        # Store configuration
        self.config = {}


class WelcomePage(QWizardPage):
    """Welcome page"""

    def __init__(self):
        super().__init__()
        self.setTitle("Welcome to DCS DataPad Server Setup")
        self.setSubTitle("This wizard will guide you through the installation and configuration process.")

        layout = QVBoxLayout()

        welcome_text = QLabel(
            "<h2>Welcome!</h2>"
            "<p>This setup wizard will help you:</p>"
            "<ul>"
            "<li>Detect your DCS World installation</li>"
            "<li>Install required Python dependencies</li>"
            "<li>Configure server settings</li>"
            "<li>Add your first authorized device</li>"
            "</ul>"
            "<p>Click <b>Next</b> to begin.</p>"
        )
        welcome_text.setWordWrap(True)
        layout.addWidget(welcome_text)

        self.setLayout(layout)


class DCSDetectionPage(QWizardPage):
    """DCS installation detection page"""

    def __init__(self):
        super().__init__()
        self.setTitle("DCS Installation Detection")
        self.setSubTitle("Detecting DCS World installation paths...")

        layout = QVBoxLayout()

        # Detection results
        self.result_label = QLabel("Searching for DCS installations...")
        layout.addWidget(self.result_label)

        self.path_list = QListWidget()
        layout.addWidget(self.path_list)

        # Manual path selection
        manual_group = QGroupBox("Manual Path Selection")
        manual_layout = QHBoxLayout()

        self.manual_path_edit = QLineEdit()
        manual_layout.addWidget(self.manual_path_edit)

        browse_btn = QPushButton("Browse...")
        browse_btn.clicked.connect(self.browse_dcs_path)
        manual_layout.addWidget(browse_btn)

        manual_group.setLayout(manual_layout)
        layout.addWidget(manual_group)

        self.setLayout(layout)

        # Register fields
        self.registerField("dcs_path*", self.manual_path_edit)

    def initializePage(self):
        """Run detection when page is shown"""
        self.detect_dcs()

    def detect_dcs(self):
        """Detect DCS installations"""
        paths = DCSPathDetector.detect_installation()

        if paths:
            self.result_label.setText(f"Found {len(paths)} DCS installation(s):")
            self.path_list.clear()
            for path in paths:
                self.path_list.addItem(path)
            # Auto-select first path
            if not self.manual_path_edit.text():
                self.manual_path_edit.setText(paths[0])
        else:
            self.result_label.setText("No DCS installations found automatically. Please select manually.")

    def browse_dcs_path(self):
        """Browse for DCS installation folder"""
        path = QFileDialog.getExistingDirectory(
            self,
            "Select DCS World Installation Folder",
            str(Path.home())
        )
        if path:
            self.manual_path_edit.setText(path)


class DependencyInstallPage(QWizardPage):
    """Dependency installation page"""

    def __init__(self):
        super().__init__()
        self.setTitle("Install Dependencies")
        self.setSubTitle("Checking and installing required Python packages...")

        layout = QVBoxLayout()

        self.status_label = QLabel("Checking dependencies...")
        layout.addWidget(self.status_label)

        self.progress_bar = QProgressBar()
        layout.addWidget(self.progress_bar)

        self.log_text = QTextEdit()
        self.log_text.setReadOnly(True)
        self.log_text.setMaximumHeight(200)
        layout.addWidget(self.log_text)

        self.setLayout(layout)

    def initializePage(self):
        """Check and install dependencies"""
        self.install_dependencies()

    def install_dependencies(self):
        """Check and install missing dependencies"""
        self.log_text.clear()
        self.log("Checking dependencies...\n")

        deps = DependencyChecker.check_all_dependencies()
        total = len(deps)
        installed = 0

        for i, (module, is_installed) in enumerate(deps.items()):
            self.progress_bar.setValue(int((i / total) * 100))

            if is_installed:
                self.log(f"✓ {module} is already installed\n")
                installed += 1
            else:
                self.log(f"✗ {module} not found, installing...\n")
                success, message = DependencyChecker.install_module(
                    module,
                    lambda msg: self.log(f"  {msg}\n")
                )

                if success:
                    self.log(f"✓ {message}\n")
                    installed += 1
                else:
                    self.log(f"✗ {message}\n")

        self.progress_bar.setValue(100)
        self.status_label.setText(f"Dependencies: {installed}/{total} installed")

        if installed == total:
            self.log("\n✓ All dependencies installed successfully!")
        else:
            self.log(f"\n⚠ Warning: {total - installed} dependencies failed to install")

    def log(self, message: str):
        """Add message to log"""
        self.log_text.append(message)
        self.log_text.moveCursor(QTextCursor.MoveOperation.End)
        QApplication.processEvents()


class ServerConfigPage(QWizardPage):
    """Server configuration page"""

    def __init__(self):
        super().__init__()
        self.setTitle("Server Configuration")
        self.setSubTitle("Configure network settings for the DCS DataPad server")

        layout = QFormLayout()

        # Bind IP
        self.bind_ip_edit = QLineEdit("192.168.1.100")
        layout.addRow("Server Bind IP:", self.bind_ip_edit)

        # Target IP
        self.target_ip_edit = QLineEdit("192.168.1.255")
        layout.addRow("Target IP (broadcast):", self.target_ip_edit)

        # Port
        self.port_spin = QSpinBox()
        self.port_spin.setRange(1024, 65535)
        self.port_spin.setValue(5010)
        layout.addRow("Data Port:", self.port_spin)

        # Handshake port
        self.handshake_port_spin = QSpinBox()
        self.handshake_port_spin.setRange(1024, 65535)
        self.handshake_port_spin.setValue(5011)
        layout.addRow("Handshake Port:", self.handshake_port_spin)

        # Update interval
        self.interval_spin = QSpinBox()
        self.interval_spin.setRange(10, 10000)
        self.interval_spin.setValue(100)
        self.interval_spin.setSuffix(" ms")
        layout.addRow("Update Interval:", self.interval_spin)

        # PoW settings
        self.pow_checkbox = QCheckBox()
        layout.addRow("Enable Proof-of-Work:", self.pow_checkbox)

        self.pow_difficulty_spin = QSpinBox()
        self.pow_difficulty_spin.setRange(8, 24)
        self.pow_difficulty_spin.setValue(16)
        self.pow_difficulty_spin.setEnabled(False)
        layout.addRow("PoW Difficulty (bits):", self.pow_difficulty_spin)

        self.pow_checkbox.toggled.connect(self.pow_difficulty_spin.setEnabled)

        self.setLayout(layout)

        # Register fields
        self.registerField("bind_ip", self.bind_ip_edit)
        self.registerField("target_ip", self.target_ip_edit)
        self.registerField("port", self.port_spin)
        self.registerField("handshake_port", self.handshake_port_spin)
        self.registerField("interval", self.interval_spin)
        self.registerField("enable_pow", self.pow_checkbox)
        self.registerField("pow_difficulty", self.pow_difficulty_spin)


class CompletionPage(QWizardPage):
    """Setup completion page"""

    def __init__(self):
        super().__init__()
        self.setTitle("Setup Complete!")
        self.setSubTitle("Your DCS DataPad Server is ready to use.")

        layout = QVBoxLayout()

        completion_text = QLabel(
            "<h2>Setup Complete!</h2>"
            "<p>Your DCS DataPad Server has been configured successfully.</p>"
            "<p>Next steps:</p>"
            "<ol>"
            "<li>Add authorized devices from the Device Management tab</li>"
            "<li>Click 'Start Server' to begin broadcasting flight data</li>"
            "<li>Connect your Android DataPad app</li>"
            "</ol>"
            "<p>Click <b>Finish</b> to open the main application.</p>"
        )
        completion_text.setWordWrap(True)
        layout.addWidget(completion_text)

        self.setLayout(layout)


class MainWindow(QMainWindow):
    """Main application window"""

    def __init__(self):
        super().__init__()
        self.setWindowTitle("DCS DataPad Server - Installer & Launcher")
        self.setMinimumSize(1000, 700)

        # Application state
        self.config_file = Path("server_config.json")
        self.config = self.load_config()
        self.server_process = None
        self.device_manager = None

        # Setup UI
        self.setup_ui()
        self.setup_system_tray()

        # Check if first run
        if not self.config_file.exists():
            self.show_setup_wizard()

    def load_config(self) -> dict:
        """Load configuration from file"""
        if self.config_file.exists():
            try:
                with open(self.config_file, 'r') as f:
                    return json.load(f)
            except Exception as e:
                logger.error(f"Error loading config: {e}")

        # Default configuration
        return {
            "dcs_path": "",
            "saved_games_path": "",
            "bind_ip": "192.168.1.100",
            "target_ip": "192.168.1.255",
            "port": 5010,
            "handshake_port": 5011,
            "interval": 100,
            "enable_pow": False,
            "pow_difficulty": 16,
            "authorized_devices_path": "authorized_devices.json"
        }

    def save_config(self):
        """Save configuration to file"""
        try:
            with open(self.config_file, 'w') as f:
                json.dump(self.config, f, indent=2)
            logger.info("Configuration saved")
        except Exception as e:
            logger.error(f"Error saving config: {e}")

    def setup_ui(self):
        """Setup main UI"""
        # Central widget with tabs
        self.tabs = QTabWidget()
        self.setCentralWidget(self.tabs)

        # Create tabs
        self.create_dashboard_tab()
        self.create_device_management_tab()
        self.create_server_config_tab()
        self.create_installation_settings_tab()
        self.create_health_check_tab()
        self.create_logs_tab()

        # Create menu bar
        self.create_menu_bar()

        # Create status bar
        self.statusBar().showMessage("Ready")

    def create_menu_bar(self):
        """Create menu bar"""
        menu_bar = self.menuBar()

        # File menu
        file_menu = menu_bar.addMenu("&File")

        run_wizard_action = QAction("Run Setup &Wizard", self)
        run_wizard_action.triggered.connect(self.show_setup_wizard)
        file_menu.addAction(run_wizard_action)

        file_menu.addSeparator()

        exit_action = QAction("E&xit", self)
        exit_action.triggered.connect(self.close)
        file_menu.addAction(exit_action)

        # Server menu
        server_menu = menu_bar.addMenu("&Server")

        start_action = QAction("&Start Server", self)
        start_action.triggered.connect(self.start_server)
        server_menu.addAction(start_action)

        stop_action = QAction("S&top Server", self)
        stop_action.triggered.connect(self.stop_server)
        server_menu.addAction(stop_action)

        server_menu.addSeparator()

        restart_action = QAction("&Restart Server", self)
        restart_action.triggered.connect(self.restart_server)
        server_menu.addAction(restart_action)

        # Help menu
        help_menu = menu_bar.addMenu("&Help")

        docs_action = QAction("&Documentation", self)
        docs_action.triggered.connect(self.show_documentation)
        help_menu.addAction(docs_action)

        about_action = QAction("&About", self)
        about_action.triggered.connect(self.show_about)
        help_menu.addAction(about_action)

    def create_dashboard_tab(self):
        """Create dashboard tab with server controls and status"""
        widget = QWidget()
        layout = QVBoxLayout()

        # Server status group
        status_group = QGroupBox("Server Status")
        status_layout = QVBoxLayout()

        self.status_label = QLabel("● Server Stopped")
        self.status_label.setStyleSheet("font-size: 16px; font-weight: bold; color: red;")
        status_layout.addWidget(self.status_label)

        self.uptime_label = QLabel("Uptime: --")
        status_layout.addWidget(self.uptime_label)

        status_group.setLayout(status_layout)
        layout.addWidget(status_group)

        # Control buttons
        control_group = QGroupBox("Server Control")
        control_layout = QHBoxLayout()

        self.start_btn = QPushButton("▶ Start Server")
        self.start_btn.setMinimumHeight(50)
        self.start_btn.setStyleSheet("font-size: 14px; font-weight: bold;")
        self.start_btn.clicked.connect(self.start_server)
        control_layout.addWidget(self.start_btn)

        self.stop_btn = QPushButton("⏹ Stop Server")
        self.stop_btn.setMinimumHeight(50)
        self.stop_btn.setStyleSheet("font-size: 14px; font-weight: bold;")
        self.stop_btn.setEnabled(False)
        self.stop_btn.clicked.connect(self.stop_server)
        control_layout.addWidget(self.stop_btn)

        control_group.setLayout(control_layout)
        layout.addWidget(control_group)

        # Quick stats
        stats_group = QGroupBox("Statistics")
        stats_layout = QFormLayout()

        self.active_sessions_label = QLabel("0")
        stats_layout.addRow("Active Sessions:", self.active_sessions_label)

        self.authorized_devices_label = QLabel("0")
        stats_layout.addRow("Authorized Devices:", self.authorized_devices_label)

        self.messages_sent_label = QLabel("0")
        stats_layout.addRow("Messages Sent:", self.messages_sent_label)

        stats_group.setLayout(stats_layout)
        layout.addWidget(stats_group)

        layout.addStretch()

        widget.setLayout(layout)
        self.tabs.addTab(widget, "Dashboard")

    def create_device_management_tab(self):
        """Create device management tab"""
        widget = QWidget()
        layout = QVBoxLayout()

        # Toolbar
        toolbar = QHBoxLayout()

        add_btn = QPushButton("➕ Add Device")
        add_btn.clicked.connect(self.add_device_dialog)
        toolbar.addWidget(add_btn)

        remove_btn = QPushButton("➖ Remove Device")
        remove_btn.clicked.connect(self.remove_selected_device)
        toolbar.addWidget(remove_btn)

        qr_btn = QPushButton("📷 Scan QR Code")
        qr_btn.clicked.connect(self.scan_qr_code)
        toolbar.addWidget(qr_btn)

        generate_token_btn = QPushButton("🎫 Generate Token")
        generate_token_btn.clicked.connect(self.generate_registration_token)
        toolbar.addWidget(generate_token_btn)

        toolbar.addStretch()

        refresh_btn = QPushButton("🔄 Refresh")
        refresh_btn.clicked.connect(self.refresh_device_list)
        toolbar.addWidget(refresh_btn)

        layout.addLayout(toolbar)

        # Device table
        self.device_table = QTableWidget()
        self.device_table.setColumnCount(5)
        self.device_table.setHorizontalHeaderLabels([
            "Device Name", "Device ID", "Permissions", "Added Date", "Status"
        ])
        self.device_table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        self.device_table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        layout.addWidget(self.device_table)

        widget.setLayout(layout)
        self.tabs.addTab(widget, "Device Management")

        # Load devices
        self.refresh_device_list()

    def create_server_config_tab(self):
        """Create server configuration tab"""
        widget = QWidget()
        layout = QVBoxLayout()

        # Configuration form
        form = QFormLayout()

        # Network settings
        network_group = QGroupBox("Network Settings")
        network_layout = QFormLayout()

        self.bind_ip_input = QLineEdit(self.config.get("bind_ip", ""))
        network_layout.addRow("Bind IP:", self.bind_ip_input)

        self.target_ip_input = QLineEdit(self.config.get("target_ip", ""))
        network_layout.addRow("Target IP:", self.target_ip_input)

        self.port_input = QSpinBox()
        self.port_input.setRange(1024, 65535)
        self.port_input.setValue(self.config.get("port", 5010))
        network_layout.addRow("Data Port:", self.port_input)

        self.handshake_port_input = QSpinBox()
        self.handshake_port_input.setRange(1024, 65535)
        self.handshake_port_input.setValue(self.config.get("handshake_port", 5011))
        network_layout.addRow("Handshake Port:", self.handshake_port_input)

        network_group.setLayout(network_layout)
        layout.addWidget(network_group)

        # Performance settings
        perf_group = QGroupBox("Performance Settings")
        perf_layout = QFormLayout()

        self.interval_input = QSpinBox()
        self.interval_input.setRange(10, 10000)
        self.interval_input.setValue(self.config.get("interval", 100))
        self.interval_input.setSuffix(" ms")
        perf_layout.addRow("Update Interval:", self.interval_input)

        perf_group.setLayout(perf_layout)
        layout.addWidget(perf_group)

        # Security settings
        security_group = QGroupBox("Security Settings")
        security_layout = QFormLayout()

        self.pow_checkbox = QCheckBox()
        self.pow_checkbox.setChecked(self.config.get("enable_pow", False))
        security_layout.addRow("Enable Proof-of-Work:", self.pow_checkbox)

        self.pow_difficulty_input = QSpinBox()
        self.pow_difficulty_input.setRange(8, 24)
        self.pow_difficulty_input.setValue(self.config.get("pow_difficulty", 16))
        self.pow_difficulty_input.setEnabled(self.pow_checkbox.isChecked())
        security_layout.addRow("PoW Difficulty:", self.pow_difficulty_input)

        self.pow_checkbox.toggled.connect(self.pow_difficulty_input.setEnabled)

        security_group.setLayout(security_layout)
        layout.addWidget(security_group)

        # Save button
        save_btn = QPushButton("💾 Save Configuration")
        save_btn.clicked.connect(self.save_server_config)
        save_btn.setMinimumHeight(40)
        layout.addWidget(save_btn)

        layout.addStretch()

        widget.setLayout(layout)
        self.tabs.addTab(widget, "Server Configuration")

    def create_installation_settings_tab(self):
        """Create installation and paths settings tab"""
        widget = QWidget()
        layout = QVBoxLayout()

        # DCS Installation paths
        dcs_group = QGroupBox("DCS World Installation")
        dcs_layout = QFormLayout()

        # DCS installation path
        dcs_path_layout = QHBoxLayout()
        self.dcs_path_input = QLineEdit(self.config.get("dcs_path", ""))
        dcs_path_layout.addWidget(self.dcs_path_input)

        dcs_browse_btn = QPushButton("Browse...")
        dcs_browse_btn.clicked.connect(self.browse_dcs_installation)
        dcs_path_layout.addWidget(dcs_browse_btn)

        dcs_detect_btn = QPushButton("Auto-Detect")
        dcs_detect_btn.clicked.connect(self.auto_detect_dcs)
        dcs_path_layout.addWidget(dcs_detect_btn)

        dcs_layout.addRow("DCS Installation Path:", dcs_path_layout)

        # Saved Games path
        saved_games_layout = QHBoxLayout()
        self.saved_games_input = QLineEdit(self.config.get("saved_games_path", ""))
        saved_games_layout.addWidget(self.saved_games_input)

        saved_games_browse_btn = QPushButton("Browse...")
        saved_games_browse_btn.clicked.connect(self.browse_saved_games)
        saved_games_layout.addWidget(saved_games_browse_btn)

        saved_games_detect_btn = QPushButton("Auto-Detect")
        saved_games_detect_btn.clicked.connect(self.auto_detect_saved_games)
        saved_games_layout.addWidget(saved_games_detect_btn)

        dcs_layout.addRow("Saved Games Path:", saved_games_layout)

        dcs_group.setLayout(dcs_layout)
        layout.addWidget(dcs_group)

        # Files and paths
        files_group = QGroupBox("Server Files")
        files_layout = QFormLayout()

        self.authorized_devices_input = QLineEdit(self.config.get("authorized_devices_path", "authorized_devices.json"))
        files_layout.addRow("Authorized Devices File:", self.authorized_devices_input)

        files_group.setLayout(files_layout)
        layout.addWidget(files_group)

        # Save button
        save_paths_btn = QPushButton("💾 Save Installation Settings")
        save_paths_btn.clicked.connect(self.save_installation_settings)
        save_paths_btn.setMinimumHeight(40)
        layout.addWidget(save_paths_btn)

        layout.addStretch()

        widget.setLayout(layout)
        self.tabs.addTab(widget, "Installation Settings")

    def browse_dcs_installation(self):
        """Browse for DCS installation folder"""
        path = QFileDialog.getExistingDirectory(
            self,
            "Select DCS World Installation Folder",
            self.dcs_path_input.text() or str(Path.home())
        )
        if path:
            self.dcs_path_input.setText(path)

    def auto_detect_dcs(self):
        """Auto-detect DCS installation"""
        paths = DCSPathDetector.detect_installation()
        if paths:
            self.dcs_path_input.setText(paths[0])
            QMessageBox.information(
                self,
                "DCS Detected",
                f"Found DCS installation at:\n{paths[0]}\n\n"
                f"Total installations found: {len(paths)}"
            )
        else:
            QMessageBox.warning(
                self,
                "Not Found",
                "Could not automatically detect DCS installation.\n\n"
                "Please select the folder manually."
            )

    def browse_saved_games(self):
        """Browse for Saved Games folder"""
        path = QFileDialog.getExistingDirectory(
            self,
            "Select DCS Saved Games Folder",
            self.saved_games_input.text() or str(Path.home() / "Saved Games")
        )
        if path:
            self.saved_games_input.setText(path)

    def auto_detect_saved_games(self):
        """Auto-detect Saved Games folder"""
        path = DCSPathDetector.detect_saved_games()
        if path:
            self.saved_games_input.setText(path)
            QMessageBox.information(
                self,
                "Saved Games Detected",
                f"Found DCS Saved Games folder at:\n{path}"
            )
        else:
            QMessageBox.warning(
                self,
                "Not Found",
                "Could not automatically detect Saved Games folder.\n\n"
                "Please select the folder manually."
            )

    def save_installation_settings(self):
        """Save installation settings"""
        self.config['dcs_path'] = self.dcs_path_input.text()
        self.config['saved_games_path'] = self.saved_games_input.text()
        self.config['authorized_devices_path'] = self.authorized_devices_input.text()

        self.save_config()
        QMessageBox.information(self, "Success", "Installation settings saved successfully!")

    def create_health_check_tab(self):
        """Create health check and diagnostics tab"""
        widget = QWidget()
        layout = QVBoxLayout()

        # Buttons toolbar
        toolbar = QHBoxLayout()

        run_btn = QPushButton("🔍 Run Health Checks")
        run_btn.clicked.connect(self.run_health_checks)
        run_btn.setMinimumHeight(40)
        toolbar.addWidget(run_btn)

        firewall_btn = QPushButton("🛡 Configure Windows Firewall")
        firewall_btn.clicked.connect(self.configure_windows_firewall)
        firewall_btn.setMinimumHeight(40)
        toolbar.addWidget(firewall_btn)

        layout.addLayout(toolbar)

        # Info label
        info_label = QLabel(
            "<b>Health Checks:</b> Verify system configuration and detect issues.<br>"
            "<b>Windows Firewall:</b> Optional - only needed if devices can't connect."
        )
        info_label.setWordWrap(True)
        info_label.setStyleSheet("padding: 10px; background-color: #f0f0f0; border-radius: 5px;")
        layout.addWidget(info_label)

        # Results table
        self.health_table = QTableWidget()
        self.health_table.setColumnCount(3)
        self.health_table.setHorizontalHeaderLabels(["Check", "Status", "Details"])
        self.health_table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        layout.addWidget(self.health_table)

        widget.setLayout(layout)
        self.tabs.addTab(widget, "Health Checks")

    def create_logs_tab(self):
        """Create logs viewer tab"""
        widget = QWidget()
        layout = QVBoxLayout()

        # Toolbar
        toolbar = QHBoxLayout()

        self.log_filter_combo = QComboBox()
        self.log_filter_combo.addItems(["All", "INFO", "WARNING", "ERROR", "DEBUG"])
        self.log_filter_combo.currentTextChanged.connect(self.filter_logs)
        toolbar.addWidget(QLabel("Filter:"))
        toolbar.addWidget(self.log_filter_combo)

        toolbar.addStretch()

        clear_btn = QPushButton("🗑 Clear Logs")
        clear_btn.clicked.connect(self.clear_logs)
        toolbar.addWidget(clear_btn)

        export_btn = QPushButton("💾 Export Logs")
        export_btn.clicked.connect(self.export_logs)
        toolbar.addWidget(export_btn)

        layout.addLayout(toolbar)

        # Log viewer
        self.log_viewer = QTextEdit()
        self.log_viewer.setReadOnly(True)
        self.log_viewer.setFont(QFont("Consolas", 9))
        layout.addWidget(self.log_viewer)

        widget.setLayout(layout)
        self.tabs.addTab(widget, "Logs")

    def setup_system_tray(self):
        """Setup system tray icon and menu"""
        # Create tray icon
        self.tray_icon = QSystemTrayIcon(self)

        # Create tray menu
        tray_menu = QMenu()

        show_action = QAction("Show Window", self)
        show_action.triggered.connect(self.show)
        tray_menu.addAction(show_action)

        tray_menu.addSeparator()

        start_action = QAction("Start Server", self)
        start_action.triggered.connect(self.start_server)
        tray_menu.addAction(start_action)

        stop_action = QAction("Stop Server", self)
        stop_action.triggered.connect(self.stop_server)
        tray_menu.addAction(stop_action)

        tray_menu.addSeparator()

        quit_action = QAction("Quit", self)
        quit_action.triggered.connect(self.quit_application)
        tray_menu.addAction(quit_action)

        self.tray_icon.setContextMenu(tray_menu)
        self.tray_icon.activated.connect(self.tray_icon_activated)
        self.tray_icon.show()

    def tray_icon_activated(self, reason):
        """Handle tray icon activation"""
        if reason == QSystemTrayIcon.ActivationReason.Trigger:
            self.show()
            self.activateWindow()

    # Server control methods
    def start_server(self):
        """Start the DCS DataPad server"""
        try:
            if self.server_process and self.server_process.isRunning():
                QMessageBox.warning(self, "Server Running", "Server is already running!")
                return

            # Validate configuration
            if not self.validate_server_config():
                return

            # Build command
            command = self.build_server_command()
            logger.info(f"Starting server: {command}")

            # Update UI to show starting state
            self.status_label.setText("● Server Starting...")
            self.status_label.setStyleSheet("font-size: 16px; font-weight: bold; color: orange;")
            self.start_btn.setEnabled(False)
            QApplication.processEvents()

            # Start server process
            self.server_process = ServerProcess(command, os.getcwd())
            self.server_process.output_received.connect(self.handle_server_output)
            self.server_process.error_received.connect(self.handle_server_error)
            self.server_process.process_started.connect(self.on_server_started)
            self.server_process.process_stopped.connect(self.on_server_stopped)
            self.server_process.start()

        except Exception as e:
            logger.error(f"Failed to start server: {e}")
            QMessageBox.critical(
                self,
                "Server Start Failed",
                f"Failed to start server:\n\n{str(e)}\n\n"
                "Check the logs for more details."
            )
            self.status_label.setText("● Server Stopped")
            self.status_label.setStyleSheet("font-size: 16px; font-weight: bold; color: red;")
            self.start_btn.setEnabled(True)

    def stop_server(self):
        """Stop the DCS DataPad server"""
        if self.server_process and self.server_process.isRunning():
            logger.info("Stopping server...")
            self.server_process.stop()
        else:
            QMessageBox.information(self, "Server Status", "Server is not running.")

    def restart_server(self):
        """Restart the server"""
        self.stop_server()
        QTimer.singleShot(2000, self.start_server)  # Wait 2 seconds before restarting

    def build_server_command(self) -> str:
        """Build the server command from configuration"""
        cmd_parts = [f'"{sys.executable}"', '"forward_parsed_udp.py"']

        # Add command line arguments
        cmd_parts.append(f'--host "{self.config.get("target_ip", "192.168.1.255")}"')
        cmd_parts.append(f'--port {self.config.get("port", 5010)}')
        cmd_parts.append(f'--bind-ip "{self.config.get("bind_ip", "192.168.1.100")}"')
        cmd_parts.append(f'--handshake-port {self.config.get("handshake_port", 5011)}')
        cmd_parts.append(f'--interval {self.config.get("interval", 100)}')
        cmd_parts.append(f'--authorized-devices "{self.config.get("authorized_devices_path", "authorized_devices.json")}"')
        cmd_parts.append("--verbose")

        # Skip interactive QR prompt (GUI handles token generation separately)
        cmd_parts.append("--skip-qr-prompt")

        if self.config.get('enable_pow', False):
            cmd_parts.append("--enable-pow")
            cmd_parts.append(f'--pow-difficulty {self.config.get("pow_difficulty", 16)}')

        command = " ".join(cmd_parts)
        logger.info(f"Built command: {command}")
        return command

    def validate_server_config(self) -> bool:
        """Validate server configuration before starting"""
        # Check if forward_parsed_udp.py exists
        script_path = Path("forward_parsed_udp.py")
        if not script_path.exists():
            QMessageBox.critical(
                self,
                "Missing Server Script",
                f"forward_parsed_udp.py not found!\n\n"
                f"Expected location: {script_path.absolute()}\n\n"
                "Please ensure all server scripts are in the current directory."
            )
            return False

        # Check Python executable
        if not os.path.exists(sys.executable):
            QMessageBox.critical(
                self,
                "Python Not Found",
                f"Python executable not found: {sys.executable}\n\n"
                "This should not happen. Please check your Python installation."
            )
            return False

        # Check authorized devices file
        devices_path = Path(self.config.get('authorized_devices_path', 'authorized_devices.json'))
        if not devices_path.exists():
            reply = QMessageBox.question(
                self,
                "No Authorized Devices",
                "No authorized devices file found. The server will not accept any connections.\n\n"
                "Do you want to create an empty whitelist and continue?",
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
            )
            if reply == QMessageBox.StandardButton.Yes:
                # Create empty whitelist
                self.device_manager = DeviceAuthManager(str(devices_path))
                self.device_manager.save_devices()
                logger.info(f"Created empty whitelist at: {devices_path}")
            else:
                return False

        # Validate network configuration
        try:
            bind_ip = self.config.get('bind_ip', '192.168.1.100')
            port = self.config.get('port', 5010)

            # Basic IP validation
            if not bind_ip or bind_ip.strip() == "":
                raise ValueError("Bind IP cannot be empty")

            if not (1024 <= port <= 65535):
                raise ValueError(f"Port {port} is out of valid range (1024-65535)")

        except ValueError as e:
            QMessageBox.warning(
                self,
                "Invalid Configuration",
                f"Server configuration is invalid:\n\n{str(e)}\n\n"
                "Please check your settings in the 'Server Configuration' tab."
            )
            return False

        return True

    def on_server_started(self):
        """Handle server started event"""
        self.status_label.setText("● Server Running")
        self.status_label.setStyleSheet("font-size: 16px; font-weight: bold; color: green;")
        self.start_btn.setEnabled(False)
        self.stop_btn.setEnabled(True)
        self.statusBar().showMessage("Server started successfully")
        logger.info("Server started successfully")

    def on_server_stopped(self, exit_code: int):
        """Handle server stopped event"""
        self.status_label.setText("● Server Stopped")
        self.status_label.setStyleSheet("font-size: 16px; font-weight: bold; color: red;")
        self.start_btn.setEnabled(True)
        self.stop_btn.setEnabled(False)

        if exit_code == 0:
            self.statusBar().showMessage("Server stopped normally")
        else:
            self.statusBar().showMessage(f"Server stopped with error (code: {exit_code})")
            QMessageBox.warning(
                self,
                "Server Error",
                f"Server process exited with code {exit_code}.\n\nCheck the logs for details."
            )

    def handle_server_output(self, line: str):
        """Handle server output"""
        self.log_viewer.append(line)
        self.log_viewer.moveCursor(QTextCursor.MoveOperation.End)

    def handle_server_error(self, error: str):
        """Handle server error"""
        self.log_viewer.append(f"<span style='color:red;'>ERROR: {error}</span>")
        self.log_viewer.moveCursor(QTextCursor.MoveOperation.End)

    # Device management methods
    def refresh_device_list(self):
        """Refresh the device list from file"""
        devices_path = Path(self.config.get('authorized_devices_path', 'authorized_devices.json'))
        self.device_manager = DeviceAuthManager(str(devices_path))

        self.device_table.setRowCount(0)

        for i, device in enumerate(self.device_manager.devices):
            self.device_table.insertRow(i)
            self.device_table.setItem(i, 0, QTableWidgetItem(device.get('name', 'Unknown')))
            self.device_table.setItem(i, 1, QTableWidgetItem(device.get('deviceId', '')[:24] + '...'))
            self.device_table.setItem(i, 2, QTableWidgetItem(', '.join(device.get('permissions', []))))
            self.device_table.setItem(i, 3, QTableWidgetItem(device.get('addedDate', 'Unknown')))
            self.device_table.setItem(i, 4, QTableWidgetItem("✓ Authorized"))

        self.authorized_devices_label.setText(str(len(self.device_manager.devices)))

    def add_device_dialog(self):
        """Show dialog to add a new device"""
        dialog = QDialog(self)
        dialog.setWindowTitle("Add Authorized Device")
        dialog.setMinimumWidth(500)

        layout = QFormLayout()

        name_edit = QLineEdit()
        layout.addRow("Device Name:", name_edit)

        device_id_edit = QLineEdit()
        layout.addRow("Device ID:", device_id_edit)

        public_key_edit = QTextEdit()
        public_key_edit.setMaximumHeight(100)
        layout.addRow("Public Key (Base64):", public_key_edit)

        receive_check = QCheckBox("Receive data")
        receive_check.setChecked(True)
        layout.addRow("", receive_check)

        send_check = QCheckBox("Send commands")
        send_check.setChecked(True)
        layout.addRow("", send_check)

        # Buttons
        button_layout = QHBoxLayout()
        add_btn = QPushButton("Add Device")
        cancel_btn = QPushButton("Cancel")

        button_layout.addWidget(add_btn)
        button_layout.addWidget(cancel_btn)

        layout.addRow(button_layout)

        dialog.setLayout(layout)

        def add_device():
            name = name_edit.text().strip()
            device_id = device_id_edit.text().strip()
            public_key = public_key_edit.toPlainText().strip()

            if not name or not device_id or not public_key:
                QMessageBox.warning(dialog, "Missing Fields", "All fields are required!")
                return

            permissions = []
            if receive_check.isChecked():
                permissions.append("receive")
            if send_check.isChecked():
                permissions.append("send_commands")

            if self.device_manager.add_device(device_id, name, public_key, permissions):
                QMessageBox.information(dialog, "Success", f"Device '{name}' added successfully!")
                dialog.accept()
                self.refresh_device_list()
            else:
                QMessageBox.warning(dialog, "Error", "Failed to add device. It may already exist.")

        add_btn.clicked.connect(add_device)
        cancel_btn.clicked.connect(dialog.reject)

        dialog.exec()

    def remove_selected_device(self):
        """Remove selected device from whitelist"""
        selected_rows = self.device_table.selectionModel().selectedRows()

        if not selected_rows:
            QMessageBox.information(self, "No Selection", "Please select a device to remove.")
            return

        row = selected_rows[0].row()
        device_id_item = self.device_table.item(row, 1)
        device_name = self.device_table.item(row, 0).text()

        if device_id_item:
            # Extract full device ID from manager
            device_id = self.device_manager.devices[row]['deviceId']

            reply = QMessageBox.question(
                self,
                "Confirm Removal",
                f"Are you sure you want to remove device '{device_name}'?\n\nThis action cannot be undone.",
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
            )

            if reply == QMessageBox.StandardButton.Yes:
                if self.device_manager.remove_device(device_id):
                    QMessageBox.information(self, "Success", f"Device '{device_name}' removed successfully!")
                    self.refresh_device_list()
                else:
                    QMessageBox.warning(self, "Error", "Failed to remove device.")

    def scan_qr_code(self):
        """Scan QR code for device registration (placeholder)"""
        QMessageBox.information(
            self,
            "QR Code Scanner",
            "QR Code scanning will be implemented in the next version.\n\n"
            "For now, please use 'Generate Token' to create a registration token."
        )

    def generate_registration_token(self):
        """Generate a registration token and display QR code"""
        try:
            # Check if server is running
            server_running = self.server_process and self.server_process.isRunning()

            if not server_running:
                reply = QMessageBox.question(
                    self,
                    "Server Not Running",
                    "The server must be running to accept device registrations.\n\n"
                    "Device registration requires:\n"
                    "• Server running on handshake port\n"
                    "• Network connectivity between device and server\n\n"
                    "Do you want to start the server now?",
                    QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
                )

                if reply == QMessageBox.StandardButton.Yes:
                    self.start_server()
                    # Give server time to start
                    QTimer.singleShot(2000, self._generate_token_after_server_start)
                    return
                else:
                    return

            self._do_generate_token()

        except Exception as e:
            logger.error(f"Error generating token: {e}")
            QMessageBox.critical(self, "Error", f"Failed to generate token:\n\n{str(e)}")

    def _generate_token_after_server_start(self):
        """Generate token after server has started"""
        if self.server_process and self.server_process.isRunning():
            self._do_generate_token()
        else:
            QMessageBox.warning(
                self,
                "Server Start Failed",
                "Server failed to start. Cannot generate registration token.\n\n"
                "Please check the logs and try starting the server manually."
            )

    def _do_generate_token(self):
        """Actually generate the token"""
        try:
            # Check if registration_token.py exists
            if not os.path.exists("registration_token.py"):
                QMessageBox.warning(
                    self,
                    "Registration System Unavailable",
                    "registration_token.py not found!\n\n"
                    "Please ensure all server scripts are installed."
                )
                return

            # Import registration token manager
            sys.path.insert(0, os.getcwd())
            from registration_token import RegistrationTokenManager

            token_manager = RegistrationTokenManager()

            # Get bind IP from config
            bind_ip = self.config.get('bind_ip', '192.168.1.100')
            port = self.config.get('handshake_port', 5011)

            # Generate token
            token = token_manager.generate_token(
                server_ip=bind_ip,
                port=port,
                permissions=["receive", "send_commands"],
                validity_minutes=10
            )

            if token:
                # Show token dialog with QR code
                self.show_token_dialog(token, bind_ip, port)
            else:
                QMessageBox.warning(self, "Error", "Failed to generate registration token.")

        except ImportError:
            QMessageBox.warning(
                self,
                "Registration System Error",
                "Could not import registration_token module.\n\n"
                "Please check that registration_token.py is installed correctly."
            )
        except Exception as e:
            logger.error(f"Error in _do_generate_token: {e}")
            QMessageBox.critical(self, "Error", f"Failed to generate token:\n\n{str(e)}")

    def show_token_dialog(self, token, server_ip: str, server_port: int):
        """Show dialog with registration token and QR code"""
        dialog = QDialog(self)
        dialog.setWindowTitle("Registration Token Generated")
        dialog.setMinimumSize(500, 700)

        layout = QVBoxLayout()

        # Instructions
        instructions = QLabel(
            "<h3>Device Registration Token</h3>"
            "<p>Scan this QR code with your Android DataPad app to register automatically.</p>"
            "<p><b>Steps:</b></p>"
            "<ol>"
            "<li>Open DataPad app on your Android device</li>"
            "<li>Go to Settings → Device Registration</li>"
            "<li>Scan this QR code</li>"
            "<li>Device will be automatically added to the authorized list</li>"
            "</ol>"
            "<p><b>Note:</b> This token is valid for 10 minutes and can only be used once.</p>"
        )
        instructions.setWordWrap(True)
        layout.addWidget(instructions)

        # Status label for registration feedback
        self.registration_status = QLabel("")
        self.registration_status.setStyleSheet("color: green; font-weight: bold;")
        layout.addWidget(self.registration_status)

        # QR Code (placeholder - will be implemented with qrcode library)
        try:
            import qrcode
            from io import BytesIO

            # Generate QR code
            qr_data = token.to_qr_payload()
            qr = qrcode.QRCode(version=1, box_size=10, border=4)
            qr.add_data(qr_data)
            qr.make(fit=True)

            img = qr.make_image(fill_color="black", back_color="white")

            # Convert to QPixmap
            buffer = BytesIO()
            img.save(buffer, format='PNG')
            buffer.seek(0)

            pixmap = QPixmap()
            pixmap.loadFromData(buffer.getvalue())

            qr_label = QLabel()
            qr_label.setPixmap(pixmap.scaled(400, 400, Qt.AspectRatioMode.KeepAspectRatio))
            qr_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
            layout.addWidget(qr_label)

        except ImportError:
            qr_placeholder = QLabel("QR Code generation requires 'qrcode' library.\nInstall with: pip install qrcode[pil]")
            qr_placeholder.setAlignment(Qt.AlignmentFlag.AlignCenter)
            qr_placeholder.setStyleSheet("border: 2px dashed gray; padding: 50px;")
            layout.addWidget(qr_placeholder)

        # Token details
        details_group = QGroupBox("Token Details")
        details_layout = QFormLayout()

        token_id_label = QLabel(token.token_id[:32] + "...")
        token_id_label.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        details_layout.addRow("Token ID:", token_id_label)

        details_layout.addRow("Server IP:", QLabel(server_ip))
        details_layout.addRow("Server Port:", QLabel(str(server_port)))

        # Convert Unix timestamp to datetime string
        from datetime import datetime
        expires_datetime = datetime.fromtimestamp(token.expires_at)
        details_layout.addRow("Expires:", QLabel(expires_datetime.strftime("%Y-%m-%d %H:%M:%S")))

        details_group.setLayout(details_layout)
        layout.addWidget(details_group)

        # Close button
        close_btn = QPushButton("Close")
        close_btn.clicked.connect(dialog.accept)
        layout.addWidget(close_btn)

        dialog.setLayout(layout)

        # Setup auto-refresh timer to detect when device registers
        refresh_timer = QTimer(dialog)
        device_count_before = len(self.device_manager.devices)
        check_count = 0
        max_checks = 300  # 300 * 2 sec = 10 minutes (token validity)

        def check_for_new_device():
            """Check if a new device was registered"""
            nonlocal check_count
            check_count += 1

            # Reload devices from file
            self.device_manager.load_devices()
            device_count_after = len(self.device_manager.devices)

            if device_count_after > device_count_before:
                # New device registered!
                new_devices = device_count_after - device_count_before
                self.registration_status.setText(f"✅ {new_devices} device(s) registered successfully!")
                self.registration_status.setStyleSheet("color: green; font-weight: bold; font-size: 14px;")
                refresh_timer.stop()
                # Refresh the main device table
                self.refresh_device_list()
                logger.info(f"Device registration detected: {new_devices} new device(s)")
                # Auto-close dialog after 2 seconds
                QTimer.singleShot(2000, dialog.accept)
            elif check_count >= max_checks:
                # Token expired
                self.registration_status.setText("⚠ Token expired (10 minutes). Generate a new token.")
                self.registration_status.setStyleSheet("color: orange; font-weight: bold; font-size: 12px;")
                refresh_timer.stop()
            else:
                # Update waiting status
                elapsed_sec = check_count * 2
                minutes_left = 10 - (elapsed_sec // 60)
                self.registration_status.setText(f"⏳ Waiting for device registration... ({minutes_left} min left)")
                self.registration_status.setStyleSheet("color: gray; font-size: 12px;")

        refresh_timer.timeout.connect(check_for_new_device)
        refresh_timer.start(2000)  # Check every 2 seconds

        # Initial status
        self.registration_status.setText("⏳ Waiting for device registration... (10 min left)")
        self.registration_status.setStyleSheet("color: gray; font-size: 12px;")

        # Show dialog
        result = dialog.exec()

        # Stop timer when dialog closes
        refresh_timer.stop()

    # Configuration methods
    def save_server_config(self):
        """Save server configuration"""
        self.config['bind_ip'] = self.bind_ip_input.text()
        self.config['target_ip'] = self.target_ip_input.text()
        self.config['port'] = self.port_input.value()
        self.config['handshake_port'] = self.handshake_port_input.value()
        self.config['interval'] = self.interval_input.value()
        self.config['enable_pow'] = self.pow_checkbox.isChecked()
        self.config['pow_difficulty'] = self.pow_difficulty_input.value()

        self.save_config()
        QMessageBox.information(self, "Success", "Configuration saved successfully!")

    # Health check methods
    def run_health_checks(self):
        """Run all health checks"""
        self.health_table.setRowCount(0)

        checks = [
            ("DCS Installation", self.check_dcs),
            ("Export Scripts", self.check_export_scripts),
            ("Python Dependencies", self.check_dependencies),
            ("Network Connectivity", self.check_network),
            ("Authorized Devices", self.check_devices),
            ("Windows Firewall", self.check_firewall)
        ]

        for i, (name, check_func) in enumerate(checks):
            self.health_table.insertRow(i)
            self.health_table.setItem(i, 0, QTableWidgetItem(name))

            success, message = check_func()

            status_item = QTableWidgetItem("✓ Pass" if success else "✗ Fail")
            status_item.setForeground(Qt.GlobalColor.green if success else Qt.GlobalColor.red)
            self.health_table.setItem(i, 1, status_item)

            self.health_table.setItem(i, 2, QTableWidgetItem(message))

    def check_dcs(self) -> Tuple[bool, str]:
        """Check DCS installation"""
        dcs_path = self.config.get('dcs_path', '')
        if not dcs_path:
            return False, "DCS path not configured"
        return HealthChecker.check_dcs_installation(dcs_path)

    def check_export_scripts(self) -> Tuple[bool, str]:
        """Check export scripts"""
        saved_games = self.config.get('saved_games_path', '')
        if not saved_games:
            return False, "Saved Games path not configured"
        return HealthChecker.check_export_scripts(saved_games)

    def check_dependencies(self) -> Tuple[bool, str]:
        """Check Python dependencies"""
        deps = DependencyChecker.check_all_dependencies()
        missing = [name for name, installed in deps.items() if not installed]

        if not missing:
            return True, "All dependencies installed"
        else:
            return False, f"Missing: {', '.join(missing)}"

    def check_network(self) -> Tuple[bool, str]:
        """Check network connectivity"""
        bind_ip = self.config.get('bind_ip', '192.168.1.100')
        port = self.config.get('port', 5010)
        return HealthChecker.check_network_connectivity(bind_ip, port)

    def check_devices(self) -> Tuple[bool, str]:
        """Check authorized devices"""
        devices_path = Path(self.config.get('authorized_devices_path', 'authorized_devices.json'))
        if not devices_path.exists():
            return False, "No authorized devices file found"

        try:
            with open(devices_path, 'r') as f:
                data = json.load(f)
                device_count = len(data.get('devices', []))

            if device_count == 0:
                return False, "No authorized devices configured"
            else:
                return True, f"{device_count} device(s) authorized"
        except Exception as e:
            return False, f"Error reading devices file: {str(e)}"

    def check_firewall(self) -> Tuple[bool, str]:
        """Check firewall status"""
        return HealthChecker.check_firewall()

    def configure_windows_firewall(self):
        """Configure Windows Firewall rules for DCS DataPad Server"""
        reply = QMessageBox.question(
            self,
            "Configure Windows Firewall",
            "This will add Windows Firewall rules to allow DCS DataPad Server traffic.\n\n"
            "<b>Rules to be added:</b>\n"
            "• Allow UDP incoming on data port (default: 5010)\n"
            "• Allow UDP incoming on handshake port (default: 5011)\n"
            "• Allow Python.exe through firewall\n\n"
            "<b>Note:</b> This requires Administrator privileges.\n\n"
            "Do you want to continue?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        )

        if reply == QMessageBox.StandardButton.No:
            return

        try:
            data_port = self.config.get('port', 5010)
            handshake_port = self.config.get('handshake_port', 5011)
            python_exe = sys.executable

            # Build netsh commands
            commands = [
                # Rule for data port
                f'netsh advfirewall firewall add rule name="DCS DataPad Server - Data Port" '
                f'dir=in action=allow protocol=UDP localport={data_port}',

                # Rule for handshake port
                f'netsh advfirewall firewall add rule name="DCS DataPad Server - Handshake Port" '
                f'dir=in action=allow protocol=UDP localport={handshake_port}',

                # Rule for Python executable
                f'netsh advfirewall firewall add rule name="DCS DataPad Server - Python" '
                f'dir=in action=allow program="{python_exe}" enable=yes'
            ]

            # Execute commands
            success_count = 0
            errors = []

            for cmd in commands:
                logger.info(f"Executing firewall command: {cmd}")
                result = subprocess.run(
                    cmd,
                    shell=True,
                    capture_output=True,
                    text=True,
                    timeout=10
                )

                if result.returncode == 0:
                    success_count += 1
                else:
                    error_msg = result.stderr or result.stdout
                    errors.append(error_msg)
                    logger.error(f"Firewall command failed: {error_msg}")

            # Show results
            if success_count == len(commands):
                QMessageBox.information(
                    self,
                    "Firewall Configured",
                    f"Successfully added {success_count} firewall rule(s).\n\n"
                    "DCS DataPad Server is now allowed through Windows Firewall.\n\n"
                    "Rules added:\n"
                    f"• Data port {data_port} (UDP)\n"
                    f"• Handshake port {handshake_port} (UDP)\n"
                    f"• Python executable"
                )
            elif success_count > 0:
                QMessageBox.warning(
                    self,
                    "Partial Success",
                    f"Added {success_count} out of {len(commands)} firewall rules.\n\n"
                    "Some rules may already exist or require administrator privileges.\n\n"
                    "Errors:\n" + "\n".join(errors[:3])
                )
            else:
                QMessageBox.critical(
                    self,
                    "Firewall Configuration Failed",
                    "Failed to add firewall rules.\n\n"
                    "<b>Possible causes:</b>\n"
                    "• Not running as Administrator\n"
                    "• Windows Firewall is disabled\n"
                    "• Third-party firewall is active\n\n"
                    "<b>Solution:</b>\n"
                    "Right-click the installer and select 'Run as Administrator'\n\n"
                    "Errors:\n" + "\n".join(errors[:3])
                )

        except subprocess.TimeoutExpired:
            QMessageBox.critical(
                self,
                "Timeout",
                "Firewall configuration timed out.\n\n"
                "Please try running the application as Administrator."
            )
        except Exception as e:
            logger.error(f"Error configuring firewall: {e}")
            QMessageBox.critical(
                self,
                "Error",
                f"Failed to configure firewall:\n\n{str(e)}\n\n"
                "You may need to configure firewall rules manually."
            )

    # Log viewer methods
    def filter_logs(self, filter_text: str):
        """Filter logs by level"""
        # TODO: Implement log filtering
        pass

    def clear_logs(self):
        """Clear the log viewer"""
        self.log_viewer.clear()

    def export_logs(self):
        """Export logs to file"""
        filename, _ = QFileDialog.getSaveFileName(
            self,
            "Export Logs",
            f"dcs_datapad_logs_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt",
            "Text Files (*.txt);;All Files (*)"
        )

        if filename:
            try:
                with open(filename, 'w') as f:
                    f.write(self.log_viewer.toPlainText())
                QMessageBox.information(self, "Success", f"Logs exported to:\n{filename}")
            except Exception as e:
                QMessageBox.critical(self, "Error", f"Failed to export logs:\n{str(e)}")

    # Wizard and dialogs
    def show_setup_wizard(self):
        """Show the first-time setup wizard"""
        wizard = SetupWizard(self)
        result = wizard.exec()

        if result == QWizard.DialogCode.Accepted:
            # Apply wizard configuration
            self.config['dcs_path'] = wizard.field("dcs_path")
            self.config['bind_ip'] = wizard.field("bind_ip")
            self.config['target_ip'] = wizard.field("target_ip")
            self.config['port'] = wizard.field("port")
            self.config['handshake_port'] = wizard.field("handshake_port")
            self.config['interval'] = wizard.field("interval")
            self.config['enable_pow'] = wizard.field("enable_pow")
            self.config['pow_difficulty'] = wizard.field("pow_difficulty")

            # Detect saved games path
            saved_games = DCSPathDetector.detect_saved_games()
            if saved_games:
                self.config['saved_games_path'] = saved_games

            self.save_config()

            # Refresh UI with new config
            self.bind_ip_input.setText(self.config['bind_ip'])
            self.target_ip_input.setText(self.config['target_ip'])
            self.port_input.setValue(self.config['port'])
            self.handshake_port_input.setValue(self.config['handshake_port'])
            self.interval_input.setValue(self.config['interval'])
            self.pow_checkbox.setChecked(self.config['enable_pow'])
            self.pow_difficulty_input.setValue(self.config['pow_difficulty'])

            QMessageBox.information(
                self,
                "Setup Complete",
                "Initial setup completed successfully!\n\n"
                "You can now add authorized devices and start the server."
            )

    def show_documentation(self):
        """Show documentation"""
        QMessageBox.information(
            self,
            "Documentation",
            "DCS DataPad Server Documentation\n\n"
            "For detailed documentation, please visit:\n"
            "https://github.com/arn-c0de/InteractiveChecklists\n\n"
            "Quick Start:\n"
            "1. Configure server settings in the 'Server Configuration' tab\n"
            "2. Add authorized devices in the 'Device Management' tab\n"
            "3. Click 'Start Server' to begin broadcasting\n"
            "4. Connect your Android DataPad app"
        )

    def show_about(self):
        """Show about dialog"""
        QMessageBox.about(
            self,
            "About DCS DataPad Server",
            "<h2>DCS DataPad Server</h2>"
            "<p>Version 1.0.0</p>"
            "<p>Auto-Installer & Launcher for DCS Interactive Checklists DataPad Server</p>"
            "<p><b>Features:</b></p>"
            "<ul>"
            "<li>Automatic DCS installation detection</li>"
            "<li>Dependency management</li>"
            "<li>Device authorization with QR code support</li>"
            "<li>Integrated server launcher</li>"
            "<li>Health checks and diagnostics</li>"
            "<li>Real-time log viewer</li>"
            "</ul>"
            "<p>Built with Python and PyQt6</p>"
            "<p>© 2024 arn-c0de</p>"
        )

    def quit_application(self):
        """Quit the application"""
        if self.server_process and self.server_process.isRunning():
            reply = QMessageBox.question(
                self,
                "Server Running",
                "The server is currently running. Are you sure you want to quit?",
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
            )

            if reply == QMessageBox.StandardButton.No:
                return

            self.stop_server()

        QApplication.quit()

    def closeEvent(self, event):
        """Handle window close event"""
        if self.server_process and self.server_process.isRunning():
            reply = QMessageBox.question(
                self,
                "Server Running",
                "The server is currently running.\n\n"
                "Do you want to minimize to system tray instead of closing?",
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No | QMessageBox.StandardButton.Cancel
            )

            if reply == QMessageBox.StandardButton.Yes:
                event.ignore()
                self.hide()
                self.tray_icon.showMessage(
                    "DCS DataPad Server",
                    "Application minimized to system tray. Server is still running.",
                    QSystemTrayIcon.MessageIcon.Information,
                    2000
                )
            elif reply == QMessageBox.StandardButton.No:
                self.stop_server()
                event.accept()
            else:
                event.ignore()
        else:
            event.accept()


def main():
    """Main entry point"""
    app = QApplication(sys.argv)
    app.setApplicationName("DCS DataPad Server")
    app.setOrganizationName("arn-c0de")

    # Set application style
    app.setStyle("Fusion")

    # Create and show main window
    window = MainWindow()
    window.show()

    sys.exit(app.exec())


if __name__ == '__main__':
    main()
