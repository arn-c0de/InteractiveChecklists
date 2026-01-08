# DCS DataPad Server - Easy Launcher

Simple batch scripts to install dependencies and run the server with an interactive configuration menu.

## 🚀 Quick Start

### First Time Setup

1. **Run installation** (creates venv and installs dependencies):
   ```
   install.bat
   ```

2. **Start the server**:
   ```
   run.bat
   ```

That's it! The configuration menu will guide you through selecting a server mode.

## 📋 Available Modes

The launcher provides 4 pre-configured modes:

### 1. ECDH with APP (with PoW protection) ⭐ Recommended for public networks
- Full security with Proof-of-Work DoS protection
- Adds 50-100ms latency per handshake
- Best for untrusted networks

### 2. ECDH with APP (standard)
- Full security without PoW
- Faster handshakes
- Good for trusted local networks

### 3. Map-Tools Mode
- Optimized for Map Database Tools
- Repeats last data every 500ms
- Uses separate handshake port (5011)

### 4. Custom Mode
- Enter your own command line parameters
- For advanced users

## ⚙️ Configuration Menu

Press `S` in the main menu to configure:

- **Bind IP Address**: Server listens on this IP (default: 192.168.178.100) - Must be a single valid IP
- **Target IP Addresses**: Client device IP(s) - Supports multiple formats:
  - Single IP: `192.168.178.132`
  - Multiple IPs (comma-separated): `192.168.178.132, 192.168.178.133`
  - Wildcard pattern: `192.168.178.*` (sends to all IPs in range .1 to .254)
- **Data Port**: UDP port for encrypted data (default: 5010)
- **Handshake Port**: Port for ECDH handshake (default: 5011)
- **Update Interval**: Data update frequency in milliseconds (default: 10ms)
- **Authorized Devices File**: Path to whitelist file (default: authorized_devices.json)

Settings are saved and remembered between sessions.

## 🔄 Auto-Selection

If no input is provided within 3 seconds, the **last used mode** is automatically selected.
This allows quick restarts without interaction.

## 📝 Manual Usage (Advanced)

You can still run the server manually without the launcher:

```bash
# Activate venv first
venv\Scripts\activate.bat

# Single target IP
python forward_parsed_udp.py --enable-pow --pow-difficulty 16 --interval 10 --host 192.168.178.132 --port 5010 --verbose --authorized-devices authorized_devices.json --bind-ip 192.168.178.100

# Multiple target IPs (separate --host for each)
python forward_parsed_udp.py --interval 10 --host 192.168.178.132 --host 192.168.178.133 --port 5010 --verbose --authorized-devices authorized_devices.json --bind-ip 192.168.178.100

# Wildcard pattern (expands to .1 through .254)
python forward_parsed_udp.py --interval 10 --host 192.168.178.* --port 5010 --verbose --authorized-devices authorized_devices.json --bind-ip 192.168.178.100
```

## 🔧 Updating Dependencies

Just run `install.bat` again to update all dependencies to the latest versions.

## 🆘 Troubleshooting

### "Python is not installed"
- Install Python 3.8+ from https://www.python.org/
- Make sure to check "Add Python to PATH" during installation

### "Virtual environment not found"
- Run `install.bat` first to create the virtual environment

### Server won't start
- Check that `forward_parsed_udp.py` exists in the current directory
- Verify your IP addresses in Settings menu
- Check `authorized_devices.json` exists and contains your device

### Port already in use
- Another instance may be running
- Change ports in Settings menu

### "getaddrinfo failed" error
- **Bind IP** must be a single valid IP address (not a wildcard like `192.168.178.*`)
- Wildcards are only supported in **Target IP** field
- Example: Bind IP = `192.168.178.100`, Target IP = `192.168.178.*`

### Multiple devices not receiving data
- Use comma-separated IPs in Target IP: `192.168.178.132, 192.168.178.133`
- Or use wildcard pattern: `192.168.178.*` (broadcasts to entire subnet)
- Bind IP must still be a single IP where the server listens

## 📂 Files

- `install.bat` - One-time setup script
- `run.bat` - Server launcher
- `config.py` - Configuration menu (Python)
- `server_config.json` - Saved configuration (auto-generated)
- `requirements.txt` - Python dependencies

## 🔒 Security Notes

- Always use ECDH mode (PSK has been removed)
- Keep `authorized_devices.json` secure
- Use PoW protection on public networks
- Bind to `127.0.0.1` for localhost-only (most secure)
- Bind to specific local IP (e.g., `192.168.x.x`) for LAN access
- Never bind to `0.0.0.0` unless you understand the security implications
