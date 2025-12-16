
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
	<a href="CHANGELOG.md">Changelog</a> |
	<a href="docs/docnavigation.md">Documentation</a> |
	<a href="docs/planning/roadmap.md">Roadmap</a> |
	<a href="SECURITY.md">Security Policy</a> |
	<a href="LICENSE">License</a> |
	<a href="COLLABORATORS.md">Collaborators</a>
</div>

<div align="center">
	<a href="THIRD_PARTY_LICENSES.md">Third-Party Licenses</a>
</div>


# InteractiveChecklists

InteractiveChecklists is an Android application for viewing and interacting with Markdown and PDF checklists. It is built with Jetpack Compose and follows an MVVM-style architecture.

> **Development status:** This repository is a development version and not an official release. The app is functional but under active development and may contain experimental features.

> **Note:** A preview APK for version 1.1 is planned. If you are not familiar with Android Studio or building apps from source, please wait for the official preview release to test the app.


**Table of Contents**

- [Features](#features)
- [Screenshots](#screenshots)
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
- **Multi-Tab System:** Open multiple documents (MD/PDF) with a scrollable tab bar, quick tab switcher, swipe navigation, and tab persistence.
- **PDF Viewer:** PDF viewer with annotations (draw/highlight/erase), pinch-to-zoom, page snapping, and color inversion.
- **Interactive Markdown Checklists:** Stateful checkboxes and collapsible sections for interactive checklists.
- **Tagging System:** Assign tags to files for filtering and organization.
- **QuickNotes:** Persistent notes powered by Room, with search, autosave, and markdown support.
- **Data Persistence:** Stores user preferences, annotations, shortcuts, tags, and open tabs locally.
- **DataPad (experimental):** Live flight telemetry display (UDP) for DCS World. Streams aircraft telemetry to the app for realtime status and popup details — see `docs/features/DATAPAD_FEATURE.md` for full details and setup instructions.

## Experimental: DataPad (Live Flight Telemetry)

DataPad is an experimental feature that receives real-time aircraft telemetry from DCS World via UDP (default port **5010**). It is intended for advanced users and requires running the `forward_parsed_udp.py` script to forward telemetry to your device.

Key points:

- Default UDP port: **5010** (configurable in the app)
- Run the forwarder script and point it to your device IP and port (see `docs/features/DATAPAD_FEATURE.md`)
- Ensure your firewall allows UDP traffic on the chosen port
- Packets are AES-GCM encrypted by default; to disable for debugging use `--no-encrypt`. Ensure the pre-shared key matches the Android app (see `docs/technical/AES_GCM_ENCRYPTION.md`).

See `docs/features/DATAPAD_FEATURE.md` for full usage, configuration, and troubleshooting.

**Phase 1 (experimental)**: This release represents Phase 1 of DataPad. Future phases will expand telemetry coverage and add visual and security improvements, including live animated aircraft visualizations based on flight attitude, a dedicated DataPad UI redesign, and a planned migration to encrypted transport (TCP or UDP with AES encryption).

**Next up: 2way (experimental)** – enabling data flow from the App to DCS.

Planned enhancements:

- Additional telemetry (e.g., speed, vertical speed, fuel levels, systems statuses)
- Live animated aircraft visualization showing orientation (pitch/roll/heading) and movement
- Dedicated DataPad UI/UX design and controls

See `docs/features/DATAPAD_FEATURE.md` for full usage, configuration, and troubleshooting.

## Screenshots

<p align="center">
		<img src="images/1.0.6_FileExplorer.png" alt="File explorer - list of files and folders" width="360" />
		<img src="images/1.0.6_MD-Viewer.png" alt="Markdown viewer showing interactive checklists" width="360" />
		<img src="images/1.0.6_Pdf-Viewer.png" alt="PDF viewer with annotation tools" width="360" />
		<img src="images/1.0.6_Notes.png" alt="QuickNotes bottom sheet and editor" width="360" />
</p>


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

Planned features and long-term improvements are tracked in the [FUTURE_PLANS](docs/FUTURE_PLANS.md) document.


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
	- A: See the `docs/` folder or the [Documentation index](docs/docnavigation.md).


## Acknowledgements & Credits

Thanks to all contributors and to the Jetpack Compose and Android open-source ecosystems used in this project.


## License

This project is licensed under the terms in the `LICENSE` file (CC BY-NC-SA 4.0).




