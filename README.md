
<!-- Badges -->
<p align="left">
	<img src="https://img.shields.io/badge/version-1.0.0-blue.svg" alt="Version" />
	<img src="https://img.shields.io/badge/platform-Android-green.svg" alt="Platform" />
	<img src="https://img.shields.io/badge/built_with-Jetpack%20Compose-orange.svg" alt="Jetpack Compose" />
	<img src="https://img.shields.io/badge/license-CC--BY--NC--SA%204.0-lightgrey.svg" alt="License: CC BY-NC-SA 4.0" />
</p>

<div align="center">
	<a href="CHANGELOG.md">Changelog</a> |
	<a href="docs/docnavigation.md">Documentation</a> |
	<a href="SECURITY.md">Security Policy</a> |
	<a href="LICENSE">License</a>
</div>

# Checklist Interactive


Checklist Interactive is an Android application designed for viewing and interacting with markdown and PDF checklists. It's built with Jetpack Compose and follows an MVVM architecture.

## Features

-   **Unified File System**: The app manages files from both bundled assets and internal storage, presenting them in a single, hierarchical view.
-   **Multi-Tab Navigation**: Open multiple documents simultaneously with swipeable tabs, quick switching between recent files, and persistent tab state across app restarts.
-   **PDF Viewer**: A custom-built PDF viewer with support for annotations (drawing, highlighting, erasing), pinch-to-zoom, page-snapping, and color inversion.
-   **Interactive Markdown Checklists**: View and interact with markdown checklists, with support for collapsible sections and stateful checkboxes.
-   **Tagging System**: Assign multiple tags to files for easy filtering and organization.
-   **Data Persistence**: User preferences, annotations, shortcuts, tags, and open tabs are all saved locally.

## Architecture

The app is a single-activity application, with `MainActivity.kt` serving as the entry point. It uses a state-driven `when` block to switch between composable screens. The architecture is a variant of MVVM, with several manager classes handling business logic.

### Key Components

-   **`MainActivity.kt`**: The orchestrator of the entire app, handling navigation, permission handling, and startup file imports.
-   **`data/files/InternalFileManager.kt`**: Manages the unified file system, providing a consistent data source for the UI.
-   **`ui/files/InternalFilesScreen.kt`**: The main screen of the app, displaying the file list and the tagging system UI.
-   **`ui/checklist/PdfViewer.kt`**: A custom-built PDF viewer with advanced annotation features.
-   **`ui/checklist/MarkdownViewer.kt`**: A dual-mode viewer for interactive and plain markdown files.
-   **`data/tags/FileTagManager.kt`**: Manages the file tagging system.
-   **`data/prefs/PreferencesManager.kt`**: Handles user settings and preferences.

## How to build

This is a standard Android project. You can build it using Android Studio or by running `./gradlew assembleDebug` from the command line.



