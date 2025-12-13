> **⚠️ EARLY DEVELOPMENT NOTICE**  
> This project is in a **very early stage of development** and is currently **NOT ready for production use**.  
> It is intended for **development and testing purposes only**.  
> Features may be incomplete, unstable, or subject to significant changes.  
> Use at your own risk.

<!-- Badges -->
<p align="left">
	<img src="https://img.shields.io/badge/version-1.0.6-blue.svg" alt="Version" />
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


#  InteractiveChecklists

InteractiveChecklists is an Android application designed for viewing and interacting with markdown and PDF checklists. It's built with Jetpack Compose and follows an MVVM architecture.

## Features

-   **Unified File System**: The app manages files from both bundled assets and internal storage, presenting them in a single, hierarchical view.
-   **Multi-Tab System**: Open multiple documents (MD/PDF) simultaneously. Includes a scrollable Tab Bar with file icons, close buttons, active tab highlighting, swipe navigation, a Quick Tab Switcher bottom sheet for rapid switching, navigation history, and tab persistence across restarts.
-   **PDF Viewer**: A custom-built PDF viewer with support for annotations (drawing, highlighting, erasing), pinch-to-zoom, page-snapping, and color inversion.
-   **Interactive Markdown Checklists**: View and interact with markdown checklists, with support for collapsible sections and stateful checkboxes.
-   **Tagging System**: Assign multiple tags to files for easy filtering and organization.
-   **Data Persistence**: User preferences, annotations, shortcuts, tags, and open tabs are all saved locally.
 -   **QuickNotes**: In-app persistent notes using Room database and repository pattern, with migration from SharedPreferences, search, autosave, markdown editor, clickable internal links, and a compact Quick Access sheet.

## Architecture

## Screenshots

<p align="center">
	<img src="images/1.0.6_FileExplorer.png" alt="File explorer - list of files and folders" width="360" />
	<img src="images/1.0.6_MD-Viewer.png" alt="Markdown viewer showing interactive checklists" width="360" />
	<img src="images/1.0.6_Pdf-Viewer.png" alt="PDF viewer with annotation tools" width="360" />
	<img src="images/1.0.6_Notes.png" alt="QuickNotes bottom sheet and editor" width="360" />
</p>

The app is a single-activity application, with `MainActivity.kt` serving as the entry point. It uses a state-driven `when` block to switch between composable screens. The architecture is a variant of MVVM, with several manager classes handling business logic.

### Key Components

-   **`MainActivity.kt`**: The orchestrator of the entire app, handling navigation, permission handling, and startup file imports.
-   **`data/files/InternalFileManager.kt`**: Manages the unified file system, providing a consistent data source for the UI.
-   **`ui/files/InternalFilesScreen.kt`**: The main screen of the app, displaying the file list and the tagging system UI.
-   **`ui/checklist/PdfViewer.kt`**: A custom-built PDF viewer with advanced annotation features.
-   **`ui/checklist/MarkdownViewer.kt`**: A dual-mode viewer for interactive and plain markdown files.
-   **`data/tags/FileTagManager.kt`**: Manages the file tagging system.
-   **`data/prefs/PreferencesManager.kt`**: Handles user settings and preferences.
 -   **`data/tabs/TabManager.kt`**: Manages open tabs, history, persistence, and exposes APIs to open/close/switch between tabs.
 -   **`ui/tabs/TabBar.kt`** and **`ui/tabs/QuickTabSwitcher.kt`**: Composable UI for the tab bar and the quick tab switcher sheet.
 -   **`data/quicknotes/QuickNoteManager.kt`** and **`ui/quickaccess/QuickAccessSheet.kt`**: The QuickNotes manager and UI.

## How to build

This is a standard Android project. You can build it using Android Studio or by running `./gradlew assembleDebug` from the command line.



