[Home](../README.md) | [Documentation Navigation](docnavigation.md)

# Tab System for Multi-Document Navigation

## Overview

The tab system enables opening multiple Markdown and PDF documents simultaneously with efficient navigation between them. The implementation follows modern Android best practices and a Clean Architecture approach.

## Features

### ✅ Implemented Features

1. **Multi-tab Support**
   - Up to 10 concurrently open documents
   - Automatic management when the tab limit is reached (oldest tabs are closed)
   - Persistence across app restarts

2. **Horizontal Swipe Gestures**
   - Swipe left/right to switch between tabs
   - Smooth animations using `HorizontalPager`
   - Visual feedback during swipe

3. **Tab Bar UI**
   - Scrollable tab bar at the top of the screen
   - File icons (PDF/MD) for quick recognition
   - Close button on each tab
   - Long-press to close
   - Active tab is highlighted

4. **Navigation History**
   - Up to 20 recently viewed documents
   - Quick access via QuickTabSwitcher
   - "Recently used" section

5. **Persistence**
   - Automatically save open tabs
   - Restore tabs after app restart
   - Save per-tab page positions

## Architecture

### Komponenten

#### 1. TabManager (Data Layer)
**Path:** `app/src/main/java/com/example/checklist_interactive/data/tabs/TabManager.kt`

**Responsibilities:**
- State-Management für Tabs (StateFlow)
- Persistence via SharedPreferences
- Navigation-History-Verwaltung
- Tab-Lifecycle (open, close, switch)

**Important Methods:**
```kotlin
fun openTab(fileInfo: FileInfo, pageNumber: Int = -1)
fun closeTab(index: Int)
fun switchToTab(index: Int)
fun getActiveTab(): TabInfo?
fun updateCurrentTabPage(pageNumber: Int)
fun navigateToPreviousInHistory(): TabInfo?
```

**State Flows:**
```kotlin
val openTabs: StateFlow<List<TabInfo>>
val activeTabIndex: StateFlow<Int>
val navigationHistory: StateFlow<List<String>>
```

#### 2. TabBar (UI Layer)
**Path:** `app/src/main/java/com/example/checklist_interactive/ui/tabs/TabBar.kt`

**Components:**
- `TabBar` - Horizontal tab bar
- `TabItem` - Single tab
- `TabbedDocumentViewer` - Combination of TabBar + HorizontalPager
- `CompactTabIndicator` - Compact tab indicator

**Features:**
- Material Design 3 Styling
- Responsive Layout
- Touch-Feedback
- Accessibility-Support

#### 3. QuickTabSwitcher (UI Layer)
**Path:** `app/src/main/java/com/example/checklist_interactive/ui/tabs/QuickTabSwitcher.kt`

**Components:**
- `QuickTabSwitcherSheet` - Bottom Sheet for the tab overview
- `QuickTabSwitchFAB` - FAB to open the switcher

**Features:**
- "Recently used" section
- Liste aller offenen Tabs
- Active tab highlighted

## Integration in MainActivity

### State-Management

```kotlin
// TabManager initialisieren
val tabManager = remember { TabManager(this@MainActivity) }
val openTabs by tabManager.openTabs.collectAsState()
val activeTabIndex by tabManager.activeTabIndex.collectAsState()
```

### Tab restoration

```kotlin
LaunchedEffect(Unit) {
    // After file import: restore tabs
    val allFiles = fileManager.getAllFilesGrouped().values.flatten()
    tabManager.restoreTabsFromPaths { path ->
        allFiles.find { it.path == path }
    }
    tabManager.loadHistoryFromPreferences()
}
```

### Opening a Document

```kotlin
// Open a file (create new tab or activate existing tab)
tabManager.openTab(fileInfo, pageNumber)
showFileList = false
```

### Tab schließen

```kotlin
// Einzelnen Tab schließen
tabManager.closeTab(index)

// Alle Tabs schließen
tabManager.closeAllTabs()
```

## Verwendung

### Für Endnutzer

1. **Open a Tab:**
   - Datei aus Liste auswählen
   - The file opens in a new tab
   - Existierende Tabs bleiben erhalten

2. **Zwischen Tabs wechseln:**
   - **Swipe:** Swipe left/right across the document
   - **Tab-Bar:** Auf Tab in oberer Leiste tippen
   - **Quick Switcher:** FAB drücken → Tab auswählen

3. **Tab schließen:**
   - X-Button auf Tab klicken
   - Long-Press auf Tab
   - Back button (closes the active tab)

4. **Schnellwechsel:**
   - Open Quick Tab Switcher
   - "Recently used" shows frequently used documents
   - Ein Tap zum Wechseln

### Für Entwickler

#### Add New Tab Features

```kotlin
// Example: tab groups
class TabManager {
    fun createTabGroup(tabs: List<TabInfo>, name: String) {
        // Implementation
    }
}
```

#### Custom Tab-Actions

```kotlin
// Example: duplicate tab
fun duplicateTab(index: Int) {
    val tab = openTabs.value.getOrNull(index) ?: return
    openTab(tab.fileInfo, tab.pageNumber)
}
```

## Persistence

### SharedPreferences-Keys

- `tab_paths` - Pipe-separated Liste der Dateipfade
- `tab_pages` - Pipe-separated Liste der Seitennummern
- `active_tab` - Index des aktiven Tabs
- `tab_history` - Pipe-separated Navigationshistorie

### Format example

```
tab_paths: "asset://checklists/A320.md|/storage/manual.pdf"
tab_pages: "5|-1"
active_tab: 1
tab_history: "/storage/manual.pdf|asset://checklists/A320.md"
```

## Best Practices

### Performance

1. **Lazy Loading:**
   - HorizontalPager rendert nur sichtbare + benachbarte Seiten
   - Documents are loaded on-demand

2. **State-Management:**
   - StateFlow für reaktive Updates
   - remember() für UI-State

3. **Memory Management:**
   - Tab limit (10) prevents memory issues
   - Old tabs are automatically closed

### UX

1. **Consistency:**
   - Material Design 3 guidelines
   - Consistent gestures across the app

2. **Feedback:**
   - Visual confirmation when switching tabs
   - Animations to improve clarity

3. **Accessibility:**
   - Content descriptions for icons
   - Touch targets at least 48dp

## Extensibility

### Geplante Features

1. **Tab groups:**
   - Group related documents
   - Farb-Coding

2. **Tab pinning:**
   - Wichtige Tabs fixieren
   - Prevent from being closed automatically

3. **Tab-Suche:**
   - Search across all open tabs
   - Filter nach Typ (MD/PDF)

4. **Keyboard-Shortcuts:**
   - Ctrl+Tab für Tab-Wechsel
   - Ctrl+W zum Schließen

### Code examples for extensions

#### Tab pinning

```kotlin
data class TabInfo(
    val fileInfo: FileInfo,
    val pageNumber: Int = -1,
    val isPinned: Boolean = false  // New
)

fun closeTab(index: Int) {
    val tab = _openTabs.value.getOrNull(index) ?: return
    if (tab.isPinned) return  // Nicht schließen wenn gepinnt
    // ... rest of implementation
}
```

#### Tab groups

```kotlin
data class TabGroup(
    val name: String,
    val tabs: List<TabInfo>,
    val color: Color
)

private val _tabGroups = MutableStateFlow<List<TabGroup>>(emptyList())
val tabGroups: StateFlow<List<TabGroup>> = _tabGroups.asStateFlow()
```

## Troubleshooting

### Problem: Tabs are not restored

**Solution:**
- Ensure `restoreTabsFromPaths()` is called after FileManager initialization
- Check logs: `TabManager` emits debug information

### Problem: Swipe gestures are not working

**Solution:**
- Ensure `TabbedDocumentViewer` is used
- Verify there are no conflicting gesture detectors in the content

### Problem: Tab bar is not displayed

**Solution:**
- Combine `TabBar` with `TabbedDocumentViewer` in a Column()
- Check if `tabs.isEmpty()` — the bar is hidden when zero tabs

## Testing

### Unit Tests

```kotlin
@Test
fun testTabLimit() {
    val tabManager = TabManager(context)
    repeat(15) { i ->
        tabManager.openTab(createMockFileInfo("file$i"))
    }
    assertEquals(10, tabManager.openTabs.value.size)
}

@Test
fun testNavigationHistory() {
    val tabManager = TabManager(context)
    val file1 = createMockFileInfo("file1")
    val file2 = createMockFileInfo("file2")
    
    tabManager.openTab(file1)
    tabManager.openTab(file2)
    
    val previousTab = tabManager.navigateToPreviousInHistory()
    assertEquals(file1.path, previousTab?.fileInfo?.path)
}
```

### UI Tests

```kotlin
@Test
fun testTabSwitchBySwipe() {
    // Open 2 tabs
    onView(withId(R.id.file_list))
        .perform(click())
    
    // Swipe nach links
    onView(withId(R.id.pager))
        .perform(swipeLeft())
    
    // Prüfe ob Tab 2 aktiv
    onView(withText("Tab 2"))
        .check(matches(isDisplayed()))
}
```

## Resources

### Relevant Files

- `MainActivity.kt` - Integration
- `TabManager.kt` - Datenlogik
- `TabBar.kt` - Tab-UI
- `QuickTabSwitcher.kt` - Quick-Access
- `InternalFileViewer.kt` - Document viewer

### Dependencies

```kotlin
// build.gradle.kts
implementation("androidx.compose.foundation:foundation:1.5.4")
implementation("androidx.compose.material3:material3:1.1.2")
```

### Additional Documentation

- [CHECKLIST_FEATURE.md](CHECKLIST_FEATURE.md) - Checklist-System
- [TAG_SYSTEM.md](TAG_SYSTEM.md) - Tag-System
- [QUICKNOTES_ARCHITECTURE.md](QUICKNOTES_ARCHITECTURE.md) - QuickNotes

## Changelog

### Version 1.1.0 (2025-12-13)
- ✅ Initial tab system implementation
- ✅ TabManager with persistence
- ✅ TabBar UI-Komponente
- ✅ HorizontalPager für Swipe-Gesten
- ✅ QuickTabSwitcher für schnellen Zugriff
- ✅ Navigation-History
- ✅ MainActivity-Integration

## Lizenz

Same license as the main project.
