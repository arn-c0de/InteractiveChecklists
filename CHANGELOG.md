# Changelog


All relevant changes are summarized here by version.

## [1.0.20] - 2026-01-01

### Summary
- **Map & Navigation:** Multiple fixes and improvements — map rotation persistence and OpenTopoMap tile loading fixes; fix for rotation effect blocking; auto-rotation for traffic pattern labels; reduced navigation popup bottom bar size; HUD improvements (persist HUD altitude settings, expand VSI range to 15,000 fpm, display airspeed in kt); map drawing coordinate corruption fixed by forcing Locale.US; clear altitude warning input UX; vertical-speed +0 flicker fixed; improved entity batch logging and error visibility.
- **Tactical Units:** UI tweak — moved auto-sort toggle under visibility in the Stats card.
- **Security & Telemetry:** Implemented server key pinning (TOFU), PSK handshake manager, and optional Proof-of-Work support; documentation updated.
- **Docs & Planning:** Added dataflow docs/navigation, and a draft for Map 3D future plans; README and docs updated with demos and navigation improvements.
- **Other:** New/finished HUD attitude instrument and various minor fixes, performance and logging improvements.

## [1.0.19] - 2025-12-29

### Summary
- **Tactical Units:** Full streaming implementation with live markers, improved lifecycle management, and performance improvements. Added **"Live Units Only"** filter, search, clickable category filters, pilot info in list items, **"Last seen"** timestamps in marker popups, auto-cleanup of old units (15 min), click-to-navigate to markers (includes traffic pattern and carrier auto-calculation), improved heading/rotation and collision detection, and batched tactical unit exports for higher live export capacity.
- **Map & Navigation:** Added map filter UI, flight path tracking with persistent storage and overlay rendering, live-navigation UI improvements and fixes, two-finger rotation gesture toggle and persistence, radial/drawing menu enhancements, and drawing/performance fixes (debouncing, batch updates, redraw improvements).
- **DataPad & Forwarder:** Improved real-time processing (heartbeat and packet interarrival tracking), handshake toggle for entity tracking, trimming/export performance improvements (asynchronous trimming, reduced max JSON lines), and security/handshake updates.
- **Internationalization & Docs:** Added Simplified Chinese translations and documentation (`docs/ZH/*`), updated README (carrier demo video, contributors badge), and restructured translation folders.
- **Fixes & Improvements:** Various bug fixes and refactors (map instruments, HUD/VSI persistence, marker rendering/performance, stability and logging improvements).

### Thanks
- Contributors: Dawn, 张晓行 (Zhang XiaoXing)

## [1.0.18] - 2025-12-26

### Summary
- Tactical Units: Full streaming implementation for tactical units with live markers and improved lifecycle management. Added **"Live Units Only"** filter, search and clickable category filters, pilot info in list items, **"Last seen"** timestamps in marker popups, auto-cleanup for old units, and navigation-to-marker features including pattern and carrier auto-calculation.
- Map & Navigation: Added map filter UI, flight path tracking with persistent storage and overlay rendering, radial/drawing menu improvements, two-finger rotation gesture support, and multiple UX & performance fixes (debouncing, batch updates, flicker fixes).
- DataPad & Forwarder: Improved real-time processing (heartbeat, packet interarrival tracking), handshake toggle for entity tracking, export/trimming performance improvements, and batched tactical unit exports to increase live export capacity.
- Internationalization & Docs: Added Simplified Chinese translation and multiple Chinese documentation files (`README_zh.md`, `COLLABORATORS_zh.md`, `CONTRIBUTING_zh.md`, `docs/ZH/docnavigation.md`); updated README (videos, contributors badge) and documentation for tactical units and entity tracking.
- Fixes & Improvements: Marker heading/rotation and collision detection fixes, coalition mapping corrections, rendering performance improvements for tacmarkers, and miscellaneous UI and bug fixes.

### Thanks
- Contributors: Dawn, 张晓行 (Zhang XiaoXing) 

## [1.0.17] - 2025-12-25

### Summary
- Full Tactical Units Streaming Implementation - TACTICAL_UNITS_TRACKING.md

- Tactical Units: Added **"Live Units Only"** filter (shows units seen in the last 10s) and synchronized the filter between list and map; marker popups now include **"Last seen"** timestamps and refresh snippets with speed/altitude.
- Map & DataPad: `MapViewer` now uses a timer-based update loop for precise redraw intervals; `DataPadManager` adds `tacticalUnitsShowLiveOnly` StateFlow and persistence, and improves the tactical units map update interval handling.
- Fixes: Resolved compilation/import issues, fixed irregular marker update timing, ensured inactive units are removed promptly, and corrected coalition color mapping; improved logging for entity tracking.
- Docs: Updated entity tracking and tactical units documentation (README_ENTITY_TRACKING, TACTICAL_UNITS_TRACKING).
- Internationalization: Added Simplified Chinese translation (zh-CN) — thanks @Dawn3901.

## [1.0.16] - 2025-12-23

### Summary
- Maps: Map rotation (two-finger gesture), overlay improvements, radial menu optimizations, improved drawing and navigation UI, flight path tracking with storage and overlay, performance and flicker fixes.
- Internationalization: Spanish translation and string extraction for DataPad and Map.
- Bugfixes: Various fixes for map interactions, radial menu, navigation, socket timeouts, database transactions, asynchronous trimming of export files.
- Miscellaneous: Contributor added, changelog updated, documentation revised.

## [1.0.15] - 2025-12-19

### Summary
- Security & CI: Added multi-language CodeQL support (Python + Kotlin), overhauled pre-push Git hooks for safer and faster checks, moved git-safety scripts into .githooks, and disabled the heavy secret-scan on pre-push in favor of CodeQL. Various CodeQL alerts were fixed across branches.
- Hardening & Privacy: Removed sensitive audit files from the repository, fixed sensitive/cleartext logging, enforced safer default binding behavior for the DataPad receiver (default to localhost), blocked binding to 0.0.0.0, and improved audit/log handling and session timeouts.
- Telemetry Security: Strengthened DataPad forwarder and handshake: added nonce and replay protection, per-client nonce management, public-key validation, whitelist/blacklist handling, rate-limit/auth improvements, and other encryption provider fixes.
- Maps & UX: Major map feature work — marker popups, route display and waypoint routing, MGRS support, new base icon sets (NATO Joint Military Symbols), map tools (compass, range/ruler), dynamic icon loading, and multiple visualization/route fixes (player icon rotation, map rotation, route coloring).
- MapDatabaseTools: Added JSON encryption and icon/visibility fixes to improve security and consistency between Python tools and the app.
- DataPad & Forwarder: Continued DataPad and forwarder stability and UX improvements (ECDH handshake phase 2, AES-GCM UDP flow, status visible in the flight mini-status bar, parser/forwarder fixes and robustness improvements).
- Misc & Fixes: Database and fresh-install fixes, full folder import support, multiple small bugfixes and translation updates (EN/DE), updates to roadmap and docs.

## [1.0.14] - 2025-12-18
### Added
- **Map improvements:** marker popups with detailed info, route display and waypoint routing, rotation-aware markers, MGRS support, map tools (compass, range/ruler, map tools UI), and new base icons/symbol sets.
- **Python MapDatabaseTools integration:** app and tools can now share the same database (shared .db integration) so markers and icons can be authored/loaded by the Python tools.
- **DataPad UI:** DataPad connection status is now visible in the flight mini-status bar.

### Changed
- **Maps & visualization:** marker editing and visualization improvements, routing and waypoint fixes, player icon rotation and map rotation handling, runway heading fixes, and dynamic icon loading for map icons.
- **Scripts:** runtime ECDH files (e.g., `authorized_devices.json`, `ip_blacklist.json`) are now ignored and template generation is relied upon at startup.

### Fixed
- **Forwarder / DataPad:** fixed empty-parse handling and several stability issues in the forwarder and DataPad integrations.
- **Database & install:** multiple DB fixes and fresh-install fixes to improve robustness.
- **Map bugs:** marker edit fixes, map view and routing display fixes, position timestamp validation and other stability fixes.
- **Misc:** removed sensitive audit files from the repository and other housekeeping fixes.

### Security
- **Hardened telemetry/forwarder:** added nonce and replay protection, per-client nonce management and nonce-prefix validation, public-key validation, stronger replay protection, audit-log fixes, whitelist/blacklist handling, auth for rate-limiting, session timeouts, encryption provider fixes, and reduced sensitive logging.

### Docs
- **Documentation updates:** roadmap and README updated with Map and DataPad details; MapDatabaseTools and DataPad docs improved.

## [1.0.13] - 2025-12-17
### Added
- **Map improvements:** marker popups, route info, marker rotation, and map tools; Python MapDatabaseTools integration so the app and tools can share the same DB.

### Fixed
- **DataPad / forwarder:** stability and parsing fixes, forwarder empty-parse handling, and DataPad status shown in the flight mini-status bar.

### Security
- **Hardened telemetry/forwarder:** added nonce and replay protection, per-client nonce management, public-key validation, auth for rate-limiting, session timeouts and audit-log fixes, whitelist/blacklist handling, improved encryption provider and JSON parse limits.

### Docs
- README and docs updates and small fixes.

## [1.0.12] - 2025-12-17
### Added
- **Multilanguage Support:** The app now supports multiple languages (English and German) via string resources. Language can be switched in the Settings menu. All UI text is available in English; German is also available for most screens.
- **ECDH Handshake:** Added ECDH (P-256) handshake for DataPad telemetry with session-based AES-256-GCM encryption, device whitelist, and mutual authentication. Provides forward secrecy and secure session keys.

### Changed
- Settings menu updated to allow language selection.

### Notes
- Initial implementation covers all main UI and navigation. Some advanced/experimental features may remain English-only.

## [1.0.11] - 2025-12-16
### Added
- **DataPad — AES-GCM encryption:** Implemented AES-GCM encrypted UDP telemetry for DataPad. Python forwarder (`forward_parsed_udp.py`) now encrypts packets by default and the app (`DataPadManager.kt`) performs AES-GCM decryption.
- **Aviation Map (experimental):** Integrated an OpenStreetMap-based `MapViewer` with live aircraft position tracking using DataPad. Adds a Map tab and documentation (`docs/features/AVIATION_MAP_FEATURE.md`).

### Changed
- **UI:** DataPad connection status now indicates encryption ("🔒 AES-GCM encrypted").

### Security
- Added guidance for generating and configuring a 32‑byte (256‑bit) pre-shared key and recommended using hex-formatted keys and secure distribution.

## [1.0.10] - 2025-12-15
### Added
- **DataPad (experimental):** Live UDP telemetry viewer (DataPadManager + DataPadPopup) to stream aircraft telemetry from DCS World (requires `forward_parsed_udp.py`) — Phase 1 implementation and docs. (commit: `0530b5e`, `ffc3ebd`)
- F-14B and AH-64 base checklists added
- Full folder import support
- PDF sharpening tools and auto-apply improvements
- Persist UI and tab state (pages, tabs, last position)

### Changed
- README and documentation updates (DataPad notes and roadmap)
- Performance and parser improvements (PDF parser, caching)
- UI/UX tweaks: improved swipe navigation, FAB behaviors, and search fixes

### Fixed
- Various bug fixes, including tab race condition, PDF page swipe issues, quicknote delete selection, and multiple small stability fixes


## [1.0.9] - 2025-12-14
### Added
- Standalone PDF outline parser and improved PDF outline caching for faster navigation.
- New PDF parser initialization and cross-reference cache for better performance.
- Added PDF sharpening tool (manual/auto)
- Tab and UI state now persist (pages, tabs, last position)
- Improved PDF zoom and swipe navigation
- New MarkdownParser implementation replaces commonmark
- Various bugfixes: FAB, search, paint tools, tab persistence, quicknote draw mode

### Fixed
- Fixed bug where PDF pages could get stuck while swiping.
- Fixed bug with quicknote manual delete selection.


## [1.0.8] - 2025-12-14
### Summary
- Quicknote and PDF navigation improvements, bug fixes, and UI tweaks
- Minor update in MainActivity.kt (init)

## [1.0.7] - 2025-12-14
### Changed
- Translated files to English (MainActivity, InternalFileManager, CategorizedFilesScreen, MarkdownViewer, PdfViewer, InternalFileViewer, InternalFilesScreen)
- LICENSE updated

## [1.0.6] - 2025-12-13
### Added
- **Multi-Tab System**: Open multiple MD/PDF files simultaneously
- **Tab Bar**: Scrollable tab bar with file icons, close buttons, and active tab highlighting
- **Swipe Navigation**: Horizontal swipe gestures to navigate between tabs
- **Quick Tab Switcher**: Bottom sheet showing recently used and all open tabs
- **Navigation History**: Track up to 20 recently viewed documents
- **Tab Persistence**: Restore open tabs and page positions after app restart
- **TabManager**: Clean architecture data layer with SharedPreferences persistence
- **Modular Tab Components**: Reusable TabBar, TabbedDocumentViewer, and QuickTabSwitcher composables

### Changed
- MainActivity now uses TabManager for multi-document support
- File opening creates/switches to tabs instead of replacing view
- Back button behavior updated to close individual tabs
- InternalFileViewer integrated with tab system

### Documentation
- Added comprehensive TAB_SYSTEM.md documentation
- Updated README.md with tab features
- Updated docnavigation.md with tab system reference

## [1.0.5] - 2025-12-13
- **PDF Outline Extractor | searching function**
**Commit:** `c0459fd` — 2025-12-13 — arn-c0de — checklists pdf updates

## [1.0.4] - 2025-12-12
### Added
- **Room Database integration** for QuickNotes with proper data persistence
- **Repository pattern** implementation for clean architecture and separation of concerns
- **Asynchronous operations** using Kotlin Coroutines for all database operations
- **Search functionality** for notes with real-time filtering by title and content
- **Automatic data migration** from SharedPreferences to Room Database
- **Type-safe database operations** with Room entities and DAOs
- **Flow-based reactive updates** for real-time UI synchronization
- **Comprehensive error handling** and logging throughout the QuickNotes system
- **Database versioning** and migration support
- **Optimized database queries** with indexed searches
- **Modern Material Design 3 UI** for QuickNotes with improved user experience
- **Horizontal scrollable note tabs** with FilterChips for better navigation
- **In-app search bar** for quick note filtering (appears when multiple notes exist)
- **Status indicators** showing save state and note count
- **Improved markdown editor** with syntax hints and clickable internal links
- **Quick text input field** for rapid note additions
- **Enhanced dialog designs** with icons and better UX
- **Smart UI behaviors** (auto-hide search when single note, disable delete on last note)

### Changed
- **QuickNoteManager refactored** to use Room Database instead of SharedPreferences
- **Data persistence layer** completely restructured using modern Android best practices
- **Database schema** now uses proper relational structure with entities
- **StateFlow emissions** now driven by Room's reactive Flow observers
- **All note operations** now execute asynchronously on background threads
- **QuickAccessSheet UI completely redesigned** with Material Design 3 components
- **Note tabs switched from buttons to FilterChips** for better visual hierarchy
- **Editor modes now use FilterChips** for cleaner mode switching
- **Removed redundant LinkedDocuments feature** - fully replaced by markdown links

### Improved
- **Performance** significantly improved with indexed database queries
- **Data integrity** enhanced with Room's compile-time SQL verification
- **Scalability** improved to handle thousands of notes efficiently
- **Code maintainability** enhanced through separation of concerns
- **Testing capability** improved with repository abstraction layer
- **UI responsiveness** with better state management and reactive updates
- **Visual consistency** across all note-taking components
- **Touch targets** optimized for mobile use
- **Code cleanliness** by removing ~500 lines of unused LinkedDocuments code
- **User workflow** streamlined with auto-save and better feedback

### Technical Details
- Added Room dependencies (version 2.6.1) and KSP compiler plugin
- Created `QuickNoteEntity`, `LinkedDocumentEntity` with TypeConverters
- Implemented `QuickNoteDao` with comprehensive query methods
- Built `QuickNoteDatabase` with singleton pattern
- Developed `QuickNoteRepository` with Result-based error handling
- Maintained backward compatibility with existing API surface
- Refactored UI to use LazyRow for horizontal scrolling performance
- Implemented conditional UI rendering based on note count
- Cleaned up imports and removed deprecated code paths
- Added proper spacing and alignment using Material Design guidelines
**Commit:** `093206d` — 2025-12-12 — arn-c0de — paint pdf fix

## [1.0.3] - 2025-12-12
### Added
- Notes FAB integration
- BackHandler
- Stable color for highlights while PDF color inverse
- Refactor assets | contribut/datasetlinks
- Further documentation updates

### Changed
- Various improvements and bugfixes
**Commit:** `e84f9cb` — 2025-12-12 — arn-c0de — docs changelog

## [1.0.2] - 2025-12-11
### Added
- Charts
- Data tag system
- Dynamic asset load
- Visibility aircraft settings fixes, only child folder visible
- Color inverse PDF function filter

### Changed
- Settings menu fix version number
- PDF flickering fixes / MD container format

### Fixed
- PDF view fixes
- Multi MD persistence fix
- MD checkbox fix
**Commit:** `e1a8eae` — 2025-12-11 — arn-c0de — color inverse pdf function filter

## [1.0.1] - 2025-12-11
### Added
- Software version visibility
- PDF text copy function
- Checkbox state counting
- Eraser fixes
- Dynamic re-render on zoom implemented

### Fixed
- PDF flickering fixes
- PDF chapter
- MD fixes
**Commit:** `939b262` — 2025-12-11 — arn-c0de — softwareversion visibility

## [1.0.0] - 2025-12-11
### Initial version
- Project initialization
- Basic PDF and Markdown viewer functionality
- Tagging system
- Data persistence
