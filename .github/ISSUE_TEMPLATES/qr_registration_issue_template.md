---
name: "QR Registration problem"
about: "Use this template to report QR registration issues (app → server). Include logs and environment details to speed up debugging."
title: "[QR Registration] Short description"
labels: qr-registration, datapad, needs-triage
assignees: ''
---

## Short summary
One-line description of the problem (e.g. "QR scan accepted but registration fails with `sendto failed: EPERM` on Android").

## Environment
- App version / build: (e.g. v1.0.25)
- Android device: (manufacturer, model)
- Android OS version: (e.g. 13)
- Server OS: (Windows 10/11, Linux)
- Server app / forwarder version: (from GUI or log)

## Network & Server
- Are both devices on the same Wi‑Fi (same subnet)? Yes / No
- Server bind IP: (e.g. 192.168.178.100)
- Server handshake port: (e.g. 5011)
- Server start command / options used (copy the command you used to start the server)

## QR payload (copy the JSON payload scanned)
```
{ "type": "datapad_registration", "version": "1.0", "token": "...", "server": "192.168.x.x", "port": 5011, "expires": 1736... }
```

## What you saw (app / server output)
- App logs (important): include the `adb logcat` snippet around the time you tried registration.
  - Example: `❌ Registration failed: sendto failed: EPERM (Operation not permitted)`
- Server logs: include relevant lines (server startup and handshake listener lines).

## Steps to reproduce
1. Start server (exact command)
2. Generate QR token / show QR code
3. Scan QR in app and press "Register"
4. What happens (error, timeout, no server response)

## Troubleshooting steps already tried
- Checked Android app permissions (`INTERNET`) and network access
- Disabled VPN / firewall / mobile data on the device
- Verified both devices on same Wi‑Fi and same subnet
- Confirmed server is listening on the configured IP:port (server log shows "Listening for handshakes on ...")
- Regenerated token and retried (token not expired / single-use)
- Rebooted server and device

## Attachments (please include if available)
- `adb logcat` (time range around the registration attempt)
- Server logs (forward_parsed_udp output)
- `authorized_devices.json` and `registration_tokens.json` (redact secrets if necessary)

> NOTE: Please redact any long-lived secret keys. Short logs and error messages are safe and helpful.


Thank you — your detailed report helps us reproduce and fix the issue faster! ✅
