
<!-- Badges -->
<p align="left">
	<a href="https://github.com/arn-c0de/InteractiveChecklists"><img src="https://img.shields.io/badge/GitHub-arn--c0de%2FInteractiveChecklists-181717?logo=github" alt="GitHub" /></a>
	<a href="https://deepwiki.com/arn-c0de/InteractiveChecklists"><img src="https://img.shields.io/badge/DeepWiki-Project%20Docs-blueviolet?logo=book" alt="DeepWiki" /></a>
	<img src="https://img.shields.io/badge/version-1.0-blue.svg" alt="Version" />
	<img src="https://img.shields.io/badge/platform-Android-green.svg" alt="Platform" />
	<img src="https://img.shields.io/badge/built_with-Jetpack%20Compose-orange.svg" alt="Jetpack Compose" />
	<img src="https://img.shields.io/badge/license-CC--BY--NC--SA%204.0-lightgrey.svg" alt="License: CC BY-NC-SA 4.0" />
</p>

<div align="center">
	<a href="README.md">English</a> |
	<a href="/docs/ZH/README_zh.md">Chinese (Simplified)</a>
</div>

<div align="center">
	<a href="CHANGELOG.md">Changelog</a> |
	<a href="docs/EN/docnavigation.md">Documentation</a> |
	<a href="docs/EN/planning/roadmap.md">Roadmap</a> |
	<a href="SECURITY.md">Security Policy</a> |
	<a href="LICENSE">License</a> |
	<a href="COLLABORATORS.md">Collaborators</a>
</div>

<div align="center">
	<a href="THIRD_PARTY_LICENSES.md">Third-Party Licenses</a>
</div>

<p align="center">
	<a href="#screenshots">Screenshots</a> • <a href="#demo-videos">Demo Videos</a>
</p>



# InteractiveChecklists

InteractiveChecklists is an Android application for viewing and interacting with Markdown and PDF checklists. It is built with Jetpack Compose and follows an MVVM-style architecture.

> **Development status:** This repository is a development version and not an official release. The app is functional but under active development and may contain experimental features.

> **Note:** A preview APK for version 1.1 is planned. If you are not familiar with Android Studio or building apps from source, please wait for the official preview release to test the app.

> **Important — Fresh install required (v1.0.21)** ⚠️  
> The pre-packaged map database (`assets/databases/map_data.db`) was reset to **version 1** in **v1.0.21**. If you are upgrading from an earlier build, you **must fully uninstall the app (including app data)** and then install the new APK to avoid schema mismatch errors and potential data loss. Back up any local database files if you need to preserve custom markers.


**Table of Contents**

- [Features](#features)
- [Screenshots](#screenshots)
- [Demo Videos](#demo-videos)
- [Installation](#installation)
- [System Requirements](#system-requirements)
- [How to Build & Run](#how-to-build--run)
- [Key Components](#key-components)
- [Contributing](#contributing)
- [Support & Contact](#support--contact)
- [FAQ](#faq)
- [Acknowledgements & Credits](#acknowledgements--credits)
- [License](#license)


## Features

- **Unified File System:** Manage files from bundled assets and internal storage in a single hierarchical view.
- **Multilanguage Support:** The app supports English, Spanish and German. You can switch the language in the Settings menu. All UI text is available in English.
- **Multi-Tab System:** Open multiple documents (MD/PDF) with a scrollable tab bar, quick tab switcher, swipe navigation, and tab persistence.
- **PDF Viewer:** PDF viewer with annotations (draw/highlight/erase), pinch-to-zoom, page snapping, and color inversion.
- **Interactive Markdown Checklists:** Stateful checkboxes and collapsible sections for interactive checklists.
- **Tagging System:** Assign tags to files for filtering and organization.
- **QuickNotes:** Persistent notes powered by Room, with search, autosave, and markdown support.
- **Data Persistence:** Stores user preferences, annotations, shortcuts, tags, and open tabs locally.
- **DataPad (experimental):** Live flight telemetry display (UDP) for DCS World. Streams aircraft telemetry to the app for realtime status and popup details — see [docs/EN/features/DATAPAD_FEATURE.md](docs/EN/features/DATAPAD_FEATURE.md) for full details and setup instructions.
- **Tactical Units Tracking (experimental):** Live tactical unit markers (aircraft, helicopter, ground, ship) on the map with real-time updates. Marker popups include **"Last seen"** timestamps and refresh snippets with speed/altitude. A **"Live Units Only"** filter (shows units seen in the last 10s) is synchronized between the list and the map. See [docs/EN/features/TACTICAL_UNITS_TRACKING.md](docs/EN/features/TACTICAL_UNITS_TRACKING.md) and [scripts/DCS-SCRIPTS-FOLDER-Experimental/README_ENTITY_TRACKING.md](scripts/DCS-SCRIPTS-FOLDER-Experimental/README_ENTITY_TRACKING.md) for setup and details.
- **Aviation Map (experimental):** OpenStreetMap-based map viewer with live aircraft position tracking from the DataPad stream. Adds a `MapViewer` tab showing aircraft position, heading, altitude and basic overlays — see [docs/EN/features/AVIATION_MAP_FEATURE.md](docs/EN/features/AVIATION_MAP_FEATURE.md) for details and configuration.
- **MapDatabaseTools (Python):** A collection of Python utilities for receiving, decrypting (AES-GCM), and visualizing DCS flight telemetry. Includes a PySide6 GUI with an embedded OpenStreetMap/Leaflet map for live aircraft tracking, a marker database, and helper scripts to manage map assets. See `scripts/MapDatabaseTools/README.md` for usage and configuration.

- **Supported maps (marker DB):**

| Map | Status | Notes |
| --- | --- | --- |
| Caucasus | Supported | Marker set available in DB |
| Marianas | Supported | Marker set available in DB |
| Germany (CW) | mostly Supported | Marker addition in progress |

## Experimental: DataPad (Live Flight Telemetry)

DataPad is an experimental feature that receives real-time aircraft telemetry from DCS World via UDP (default port **5010**). It is intended for advanced users and requires running the `forward_parsed_udp.py` script to forward telemetry to your device.

### Security Features (NEW - December 2025)

**✅ ECDH Handshake Mode** - Production-ready secure communication:
- **End-to-end encryption** with per-session AES-256-GCM keys
- **Device authentication** via whitelist (`authorized_devices.json`)
- **Forward secrecy** - compromising one session doesn't affect others
- **Replay attack protection** with counter-based nonces
- **Timestamp validation** (5-minute window)
- **Mutual authentication** (client ↔ server)

**🔒 Server Key Pinning (TOFU)** - Trust-On-First-Use server key pinning to detect man-in-the-middle attacks after the first successful connection (auto-pins the server key on first contact).

**🔑 PSK Handshake Manager (optional)** - Optional pre-shared key (PSK) handshake manager for compatibility and scripted deployments. See the docs for guidance on generating a 32-byte (256-bit) key and secure distribution.

**🛡️ Optional Proof-of-Work (PoW)** - Configurable anti-DoS protection for handshake requests; trade off handshake latency for robustness using `--enable-pow` and `--pow-difficulty`.

**Quick Start (Handshake & PoW):**
```bash
# Python: Enable handshake mode (ECDH + TOFU)
python forward_parsed_udp.py --interval 10 --host 192.168.178.132 --port 5010 --verbose --authorized-devices authorized_devices.json --bind-ip 192.168.178.100

# Python: Enable Proof-of-Work (anti-DoS)
python forward_parsed_udp.py --enable-pow --pow-difficulty 16 --interval 10 --host 192.168.178.132 --port 5010 --verbose --authorized-devices authorized_devices.json --bind-ip 192.168.178.100

# Python: Same-PC testing with handshake port
python forward_parsed_udp.py --repeat-last --interval 3 --host 127.0.0.1 --port 5010 --handshake-port 5011 --use-handshake --authorized-devices authorized_devices.json

# Android: Settings → DataPad → Enable "ECDH Handshake Mode" (optional: enable Server Key Pinning / configure Pre-Shared Key)
# Add your device ID to authorized_devices.json on the server
```

See [docs/EN/technical/ECDH_USAGE_GUIDE.md](docs/EN/technical/ECDH_USAGE_GUIDE.md) and [docs/EN/technical/DATA_FLOW_ANALYSIS.md](docs/EN/technical/DATA_FLOW_ANALYSIS.md) for complete setup instructions, PSK guidance, and PoW tuning and troubleshooting.

DataPad also supports receiving **entity contacts** (tactical units) exported from DCS. Enable **Entity Tracking** in the app to receive tactical units and display them as live markers (requires running the forwarder with entity tracking enabled). For details and setup instructions, see [scripts/DCS-SCRIPTS-FOLDER-Experimental/README_ENTITY_TRACKING.md](scripts/DCS-SCRIPTS-FOLDER-Experimental/README_ENTITY_TRACKING.md) and [docs/EN/features/TACTICAL_UNITS_TRACKING.md](docs/EN/features/TACTICAL_UNITS_TRACKING.md).

See [docs/EN/technical/ECDH_USAGE_GUIDE.md](docs/EN/technical/ECDH_USAGE_GUIDE.md) for complete setup instructions and [docs/EN/features/DATAPAD_FEATURE.md](docs/EN/features/DATAPAD_FEATURE.md) for full usage, configuration, and troubleshooting.

**Phase 1 (experimental)**: This release represents Phase 1 of DataPad. Future phases will expand telemetry coverage and add visual and security improvements, including live animated aircraft visualizations and a dedicated UI redesign.

**Next up:** 2-way communication (experimental) — enabling data flow from the app back to DCS.

Planned enhancements include additional telemetry (speed, vertical speed, fuel, systems), live animated aircraft visualizations, and UI/UX improvements.

## Screenshots

<p align="center">
		<img src="images/FileExplorer.png" alt="File explorer - list of files and folders" width="360" />
		<img src="images/md_viewer.png" alt="Markdown viewer showing interactive checklists" width="360" />
		<img src="images/pdf_viewer.png" alt="PDF viewer with annotation tools" width="360" />
		<img src="images/quick_notes.png" alt="QuickNotes bottom sheet and editor" width="360" />
		<img src="images/map_pattern_calculator.png" alt="Calculator for landing patterns" width="360" />
		<img src="images/map_tacmarker_live-data.png" alt="Full tactical live data Support" width="360" />
		<img src="images/map_flightpath.png" alt="Flight path overlay" width="360" />
		<img src="images/map-route.png" alt="Route lines overlay with labels" width="360" />
		<img src="images/routeplanner.png" alt="Route planner - line preview" width="360" />
		<img src="images/landingroute-planner.png" alt="Create Route sheet" width="360" />
		<img src="images/datapad.png" alt="DataPad live telemetry panel" width="360" />
		<img src="images/settings_menu.png" alt="Settings and preferences" width="360" />
</p>


<a name="demo-videos"></a>
## Demo Videos 🎬

<p align="center">
<a href="https://youtu.be/ecE6bdzyNwA"><img src="https://img.youtube.com/vi/ecE6bdzyNwA/0.jpg" alt="Carrier Landing Pattern – Live Tracking & Pattern Calculation Test (STATE App 1.0.19)" width="360" /></a> &nbsp;
<a href="https://youtu.be/V7vRuQvTFK8"><img src="https://img.youtube.com/vi/V7vRuQvTFK8/0.jpg" alt="Demo 1" width="360" /></a> &nbsp;
<a href="https://youtu.be/G5uiONmqxe0"><img src="https://img.youtube.com/vi/G5uiONmqxe0/0.jpg" alt="Demo 2" width="360" /></a>
</p>

<p align="center">
</p>

> 📝 **NOTE**  
> This is a test recording to evaluate recording performance, tablet capture workflow, resolution settings, and overall system stability during DCS gameplay. Mission content and pacing are deliberately simple and functional.

## Installation

Step-by-step instructions to get the project running locally.

1. Prerequisites
	 - Install Android Studio (Arctic Fox or later recommended).
	 - Install a compatible JDK (Java 11 or later recommended).
	 - Configure Android SDK and at least one emulator or use a physical device.

2. Clone the repository

```bash
git clone https://github.com/arn-c0de/InteractiveChecklists.git
cd InteractiveChecklists
```

3. Build with Gradle (command-line)

```bash
./gradlew assembleDebug
```

4. Open in Android Studio
	 - Open the `InteractiveChecklists` directory in Android Studio.
	 - Let Gradle sync and allow Android Studio to download any missing SDK components.
	 - Run the app on an emulator or connected device.


## System Requirements

- Supported OS: Windows, macOS, Linux (for development).
- Android Studio: Arctic Fox or newer recommended.
- JDK: Java 11+ recommended.
- Android SDK: API level corresponding to the project's `compileSdk` and `targetSdk` (see `build.gradle.kts`).


## How to Build & Run

- From Android Studio: Open the project, wait for Gradle to finish syncing, then select a target device and click **Run**.
- From the command line: `./gradlew assembleDebug` builds an APK; use `./gradlew installDebug` to install on a connected device.


## Key Components

- `MainActivity.kt`: App entry point and navigation orchestration.
- `data/files/InternalFileManager.kt`: Unified file management.
- `ui/files/InternalFilesScreen.kt`: File browser and tagging UI.
- `ui/checklist/MarkdownViewer.kt`: Interactive markdown checklist viewer.
- `ui/checklist/PdfViewer.kt`: PDF viewer and annotation tools.
- `data/quicknotes/QuickNoteManager.kt`: QuickNotes data layer.


## Contributing

We welcome contributions. For guidelines, issue workflow, and coding standards, see [COLLABORATORS.md](COLLABORATORS.md).

Quick contribution ideas:
- Improve documentation or add examples.
- Add or extend tests.
- Fix small UI/UX bugs or accessibility issues.

For larger or breaking changes, please open an issue first to discuss design and scope.


## Roadmap

Planned features and long-term improvements are tracked in the [Roadmap](docs/EN/planning/roadmap.md) document.


## Support & Contact

If you encounter issues or have questions:

- Open an issue in this repository.
- For security-sensitive issues, please follow the instructions in [SECURITY.md](SECURITY.md).
- For contribution coordination and discussions, see [COLLABORATORS.md](COLLABORATORS.md).


## FAQ

- Q: How do I run tests?
	- A: There are unit tests under `app/src/test`. Run them via `./gradlew test`.
- Q: What is the license?
	- A: This project is licensed under CC-BY-NC-SA 4.0. See the `LICENSE` file for details.
- Q: Where is the documentation?
	- A: See the `docs/` folder or the [Documentation index](docs/EN/docnavigation.md).


## Acknowledgements & Credits

Thanks to all contributors and to the Jetpack Compose and Android open-source ecosystems used in this project.

[![Contributors](https://contrib.rocks/image?repo=arn-c0de/InteractiveChecklists)](https://github.com/arn-c0de/InteractiveChecklists/graphs/contributors)



## License

This project is licensed under the terms in the `LICENSE` file (CC BY-NC-SA 4.0).




