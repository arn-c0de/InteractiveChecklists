[← Back to README](../README.md) | [Documentation index](docnavigation.md)

# Project Roadmap

This roadmap outlines planned features, priorities, and future directions for ChecklistInteractive. Items are grouped by area and priority for clarity and professional project management.

---

## 1. Core Features & Integration

### DCS (Digital Combat Simulator) Integration
- **Export Checklists to DCS (Lua):**
	- Enable exporting checklists as Lua scripts for use in DCS.
- **Two-way Data Injection (Future):**
	- Explore sending data from mobile to DCS for two-way workflows.

---

## 2. Collaboration & Sharing

### Flight Squad Collaboration
- **Drawings & Notes Sharing:**
	- Enable flight squad members to share drawings and notes with each other in real-time.
	- Implement via self-hosted backend server for privacy and control.
- **Backend Infrastructure:**
	- Design and deploy lightweight backend server architecture.
	- Support WebSocket or REST API for real-time synchronization.
	- Implement user authentication and authorization.
	- Enable room/session management for flight squads.
- **Data Synchronization:**
	- Real-time sync of annotations, sketches, and tactical notes.
	- Conflict resolution for concurrent edits.
	- Optional offline mode with sync on reconnection.
- **Privacy & Security:**
	- End-to-end encryption for shared content.
	- Self-hosted deployment options for maximum control.
	- Configurable visibility and access permissions per squad.

---

## 3. User Experience & Design

- **Complete UI/UX Design:**
	- Finalize and polish the full user interface, visual language, and interaction patterns.
- **Collaborative UI Elements:**
	- Visualize active squad members.
	- Show real-time cursors/annotations from other users.
	- Notification system for shared content updates.
 - **OSMMap Integration UI:**
	- Smooth map interaction for telemetry display.
    - Toggle layers, trails, and squad overlays easily. 

---

## 4. File Format & Data Support

- **Additional File Formats:**
	- Extend support beyond Markdown and PDF (e.g., EPUB, DOCX, ODT, others).
- **Export Shared Sessions:**
	- Allow exporting collaborative sessions with all annotations and notes.

---

## 5. Localization & Internationalization

- **Multilanguage Support:**
	- Add more String translation languages, currently only EN an DE.

---

## 6. Notes & Prioritization

- Features are prioritized based on user demand and compatibility with existing data flows.
- Each item should be broken down into implementation milestones and tracked as issues.
- Collaboration features require careful consideration of network architecture, security, and user privacy.
- Backend deployment should support containerization (Docker) for easy self-hosting.
