[← Back to README](../../README.md) | [Documentation index](../docnavigation.md)

# Project Roadmap

This roadmap outlines the planned development phases and features for the app in the exact priority order requested. All items are listed sequentially, reflecting the intended implementation sequence. The focus is on iterative progression: completing earlier items before moving to the next.

---



### Map View Nearest Airport List
- Add a nearest airport list feature to the map view.
- Display airports sorted by distance from the player's current position.
- Include details such as airport name, ICAO code, distance, and bearing.
- Make the list easily accessible (e.g., via sidebar or overlay toggle).

---

### Map Database
- Develop and integrate a dedicated map database.
- Store geographical data (airports, waypoints, terrain, etc.) for fast offline/online access.
- Optimize for quick queries and low memory usage on mobile devices.

---

### Map Orders Icons and Airport Symbols
- Implement customizable order icons on the map.
- Add standard airport symbols with proper scaling and layering.
- Ensure icons are clear, distinguishable, and support different zoom levels.

---

### AA Icon Range Circles
- Add range circles around anti-aircraft (AA) icons.
- Display configurable threat ranges (e.g., engagement zones, detection radii).
- Support multiple circle layers (e.g., radar, missile range) with color coding.

---

### Handshake and AES Implementation with Security Tests
- Implement secure handshake protocol for connections.
- Integrate AES encryption for all data in transit.
- Conduct comprehensive security tests (penetration testing, vulnerability checks, encryption validation).

---

### Two-Way Telemetry and App Commands
- Enable full two-way telemetry between app and external systems (e.g., simulator).
- Implement command transmission from app to system.
- Add in-app command buttons and controls (e.g., for waypoints, modes, actions).
- Support customizable button layouts and reliable delivery with feedback.

---

### Online Sharing with Parsed Data (Location, Name, Plane Heading, Speed, Height)
- Implement online data sharing functionality.
- Parse and transmit key telemetry data in real-time:
  - Location (coordinates)
  - Aircraft name/type
  - Heading
  - Speed
  - Altitude/height
- Display received shared data clearly on the map and UI.

---

### Extreme Security Hardening
- Apply advanced security hardening across the entire app.
- Include measures such as:
  - Code obfuscation
  - Root/jailbreak detection
  - Secure storage of keys and data
  - Protection against reverse engineering and tampering
  - Regular security audits and updates

---

### Squad Mode via Internet
- Develop full squad mode with internet-based multiplayer collaboration.
- Enable real-time sharing of:
  - Positions and telemetry
  - Map annotations and orders
  - Player icons and status
- Include squad management (join/leave rooms, authentication, voice/text coordination hooks).
- Ensure low-latency synchronization and robust reconnection handling.

---

### Notes & General Guidelines

- Backend infrastructure (where needed) should prioritize privacy, encryption, and optional self-hosting.
- Regular testing on target devices and simulators is required at each milestone.

