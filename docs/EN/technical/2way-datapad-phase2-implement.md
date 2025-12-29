dispatch_command(msg['aircraft'], msg['command'], msg['value'])

# Summary: 2way-datapad-phase2-implement

**Goal:**
External apps can send cockpit commands to DCS over a secure channel (AES-GCM, ECDH). Commands are defined in separate Lua files and executed via Export.lua/dispatcher.lua.

**Flow:**
1. App → (encrypted) → Python forwarder → (UDP/TCP) → Export.lua → cockpit device.
2. Python forwarder decrypts, checks permissions (send_commands in authorized_devices.json), validates, and forwards commands.
3. Message format: JSON with aircraft, command, value, timestamp.
4. Security: ECDH handshake, AES-GCM, device whitelist, rate limiting, replay protection, explicit allow-list for critical commands.
5. Export.lua loads command definitions and executes them with performClickableAction.

**Checklist:**
- send_commands permission in authorized_devices.json
- Forwarder checks permissions, rate limiting, replay protection
- Test mode (--dry-run), allow-list, logging
- Integration test for encrypted commands

**Limitations:**
Only for player-controlled aircraft, some actions are system-protected, multiplayer may block export.

**Conclusion:**
Secure, auditable two-way command channel between app and DCS.
