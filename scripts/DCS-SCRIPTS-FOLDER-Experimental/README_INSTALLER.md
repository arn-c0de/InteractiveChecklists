# DCS DataPad Server - Auto-Installer & Launcher

**Complete GUI application for easy installation, configuration, and management of the DCS Interactive Checklists DataPad Server**

## Features

### 🎯 Core Functionality
- **Automatic DCS Detection**: Automatically finds DCS World installations via Windows registry and common paths
- **Dependency Management**: Checks and installs required Python packages automatically
- **Device Authorization Manager**: Add, edit, remove authorized devices with full GUI support
- **Integrated Server Launcher**: Start/stop/restart the DCS DataPad server with one click
- **System Tray Integration**: Minimize to tray, control server from taskbar
- **Health Checks**: Comprehensive diagnostics for DCS, scripts, network, and dependencies
- **Real-time Logs**: View server output with filtering and export capabilities
- **QR Code Registration**: Generate registration tokens for easy device pairing

### 🧙 First-Time Setup Wizard
- Guided step-by-step setup process
- DCS installation detection
- Automatic dependency installation
- Server configuration with validation
- Professional and user-friendly experience

### 📊 Dashboard
- Server status (running/stopped)
- Active sessions counter
- Authorized devices count
- Real-time statistics

### 🔧 Configuration Management
- Network settings (bind IP, target IP, ports)
- Performance tuning (update interval)
- Security settings (Proof-of-Work protection)
- Save and load configurations

### 🔍 Health Checks & Diagnostics
- DCS installation validation
- Export scripts verification
- Python dependencies check
- Network connectivity test
- Firewall status check
- Authorized devices validation

### 📱 Device Management
- Add devices manually (Device ID + Public Key)
- Generate registration tokens with QR codes
- Remove devices with confirmation
- View device permissions and status
- Real-time device list updates

## Installation

### Prerequisites
- **Windows 10/11**
- **Python 3.8+** (Download from [python.org](https://www.python.org/downloads/))
- **DCS World** (Standalone or Steam version)

### Quick Start

1. **Install Python Dependencies**
   ```bash
   cd scripts/DCS-SCRIPTS-FOLDER-Experimental
   pip install -r requirements_gui.txt
   ```

2. **Launch the Application**
   ```bash
   python dcs_datapad_installer.py
   ```

3. **Follow the Setup Wizard**
   - The first-time wizard will guide you through:
     - DCS installation detection
     - Dependency verification
     - Server configuration
     - Initial setup completion

4. **Add Your First Device**
   - Go to **Device Management** tab
   - Click **"🎫 Generate Token"**
   - Scan the QR code with your Android DataPad app
   - Device will be automatically registered!

5. **Start the Server**
   - Go to **Dashboard** tab
   - Click **"▶ Start Server"**
   - Server is now broadcasting flight data!

## Usage Guide

### Starting the Server

1. Open the application
2. Go to the **Dashboard** tab
3. Click **"▶ Start Server"**
4. Server status will show **"● Server Running"** in green
5. View real-time logs in the **Logs** tab

### Adding Authorized Devices

**Method 1: QR Code Registration (Recommended)**
1. Go to **Device Management** tab
2. Click **"🎫 Generate Token"**
3. A QR code will be displayed
4. Scan with your Android DataPad app
5. Device will register automatically!

**Method 2: Manual Entry**
1. Go to **Device Management** tab
2. Click **"➕ Add Device"**
3. Enter:
   - **Device Name**: Friendly name (e.g., "My Tablet")
   - **Device ID**: From Android app settings
   - **Public Key**: Base64-encoded ECDH public key
   - **Permissions**: Choose receive/send_commands
4. Click **"Add Device"**

### Configuring the Server

1. Go to **Server Configuration** tab
2. Adjust settings:
   - **Bind IP**: IP address to bind to (e.g., `192.168.1.100`)
   - **Target IP**: Broadcast address (e.g., `192.168.1.255`)
   - **Data Port**: Default `5010`
   - **Handshake Port**: Default `5011`
   - **Update Interval**: 100ms recommended
   - **Enable PoW**: DoS protection (optional)
3. Click **"💾 Save Configuration"**
4. **Restart server** for changes to take effect

### Running Health Checks

1. Go to **Health Checks** tab
2. Click **"🔍 Run Health Checks"**
3. Review results:
   - ✓ Pass (green) = Everything OK
   - ✗ Fail (red) = Action required
4. Fix any issues and re-run checks

### Viewing Logs

1. Go to **Logs** tab
2. View real-time server output
3. Filter by level (INFO, WARNING, ERROR, DEBUG)
4. Export logs: Click **"💾 Export Logs"**

## System Tray Integration

When minimized, the application sits in the **system tray**:
- **Right-click tray icon** to access:
  - Show Window
  - Start Server
  - Stop Server
  - Quit

The server can run in the background while you play DCS!

## Keyboard Shortcuts

- **File Menu**
  - `Alt+F` → File menu
  - `Alt+S` → Server menu
  - `Alt+H` → Help menu

## Configuration Files

The application creates and manages these files:

```
scripts/DCS-SCRIPTS-FOLDER-Experimental/
├── server_config.json           # Server configuration
├── authorized_devices.json      # Authorized devices whitelist
├── registration_tokens.json     # Active registration tokens
├── installer_gui.log            # Application logs
└── security_audit.jsonl         # Security audit log
```

**Note**: Do not manually edit these files while the application is running!

## Troubleshooting

### "PyQt6 not installed" Error

**Solution**: Install dependencies
```bash
pip install -r requirements_gui.txt
```

### DCS Installation Not Detected

**Solution**: Use **Manual Path Selection**
1. In the setup wizard, click **"Browse..."**
2. Navigate to your DCS installation folder
3. Select the folder containing `bin\DCS.exe`

### Server Won't Start

**Possible causes:**
1. **No authorized devices**: Add at least one device first
2. **Port already in use**: Change port in Server Configuration
3. **Missing scripts**: Ensure `forward_parsed_udp.py` exists in current directory

**Solution**: Run **Health Checks** to identify the issue

### Network Connection Issues

1. Check **Windows Firewall** settings
2. Ensure correct **Bind IP** (use `0.0.0.0` to bind to all interfaces)
3. Verify **Target IP** matches your network (use broadcast address like `192.168.1.255`)
4. Run **Health Checks** → Network Connectivity

### QR Code Not Generating

**Solution**: Install QR code library
```bash
pip install qrcode[pil]
```

## Advanced Features

### Proof-of-Work (PoW) Protection

Enables DoS protection by requiring clients to solve a cryptographic challenge:
1. Go to **Server Configuration**
2. Enable **"Enable Proof-of-Work"**
3. Set **PoW Difficulty**: 12-16 recommended
4. Save and restart server

### Multiple Device Support

You can authorize multiple devices (tablets, phones, etc.):
- Each device needs a unique Device ID
- Generate separate registration tokens for each
- All authorized devices can connect simultaneously

### Custom Network Configurations

For advanced users:
- **Target IP wildcards**: Supports `192.168.178.*` for multi-subnet
- **Multiple Target IPs**: Comma-separated (e.g., `192.168.1.255,10.0.0.255`)
- **Handshake Port**: Separate port for device registration

## Building Standalone Executable (Optional)

To create a standalone `.exe` for distribution:

```bash
# Install PyInstaller
pip install pyinstaller

# Build executable
pyinstaller --onefile --windowed --name "DCS-DataPad-Installer" dcs_datapad_installer.py

# Output will be in dist/DCS-DataPad-Installer.exe
```

## Security Considerations

- **Device Whitelist**: Only authorized devices can connect
- **ECDH Encryption**: All flight data is encrypted end-to-end
- **Public Key Verification**: Prevents impersonation attacks
- **Session Timeouts**: Automatic cleanup of inactive sessions
- **Audit Logging**: All security events logged to `security_audit.jsonl`

## Support

- **GitHub Issues**: [Report bugs](https://github.com/arn-c0de/InteractiveChecklists/issues)
- **Documentation**: [Full docs](https://github.com/arn-c0de/InteractiveChecklists)
- **Video Tutorial**: Coming soon!

## License

Same as parent project - see [LICENSE](../../LICENSE)

## Credits

- Built with **PyQt6** for modern cross-platform GUI
- Integrates with existing DCS DataPad server infrastructure
- Part of the **DCS Interactive Checklists** project

---

**Version**: 1.0.0
**Author**: arn-c0de
**Last Updated**: January 2026
