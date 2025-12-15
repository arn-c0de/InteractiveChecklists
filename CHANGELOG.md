# Changelog

All relevant changes are summarized here by version.

## [1.0.10] - 2025-12-16

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
