[Home](../README.md) | [Documentation Navigation](docnavigation.md)

# Tab-System für Multi-Document Navigation

## Überblick

Das Tab-System ermöglicht das gleichzeitige Öffnen mehrerer Markdown- und PDF-Dateien mit komfortabler Navigation zwischen ihnen. Die Implementierung folgt modernen Android-Best-Practices mit Clean Architecture.

## Features

### ✅ Implementierte Features

1. **Multi-Tab-Unterstützung**
   - Bis zu 10 gleichzeitig geöffnete Dokumente
   - Automatische Verwaltung bei Tab-Limit (älteste Tabs werden geschlossen)
   - Persistierung über App-Neustarts

2. **Horizontale Wisch-Gesten**
   - Links/rechts wischen zum Wechseln zwischen Tabs
   - Flüssige Animationen mit `HorizontalPager`
   - Visuelle Rückmeldung während des Wischens

3. **Tab-Bar UI**
   - Scrollbare Tab-Leiste am oberen Bildschirmrand
   - Datei-Icons (PDF/MD) zur schnellen Erkennung
   - Schließen-Button auf jedem Tab
   - Long-Press zum Schließen
   - Aktiver Tab hervorgehoben

4. **Navigationshistorie**
   - Bis zu 20 zuletzt besuchte Dokumente
   - Schnellzugriff über QuickTabSwitcher
   - "Zuletzt verwendet"-Sektion

5. **Persistierung**
   - Automatisches Speichern geöffneter Tabs
   - Wiederherstellung nach App-Neustart
   - Speicherung der Seitenpositionen pro Tab

## Architektur

### Komponenten

#### 1. TabManager (Data Layer)
**Pfad:** `app/src/main/java/com/example/checklist_interactive/data/tabs/TabManager.kt`

**Verantwortlichkeiten:**
- State-Management für Tabs (StateFlow)
- Persistierung via SharedPreferences
- Navigation-History-Verwaltung
- Tab-Lifecycle (open, close, switch)

**Wichtige Methoden:**
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
**Pfad:** `app/src/main/java/com/example/checklist_interactive/ui/tabs/TabBar.kt`

**Komponenten:**
- `TabBar` - Horizontale Tab-Leiste
- `TabItem` - Einzelner Tab
- `TabbedDocumentViewer` - Kombination aus TabBar + HorizontalPager
- `CompactTabIndicator` - Kompakte Tab-Anzeige

**Features:**
- Material Design 3 Styling
- Responsive Layout
- Touch-Feedback
- Accessibility-Support

#### 3. QuickTabSwitcher (UI Layer)
**Pfad:** `app/src/main/java/com/example/checklist_interactive/ui/tabs/QuickTabSwitcher.kt`

**Komponenten:**
- `QuickTabSwitcherSheet` - Bottom Sheet für Tab-Übersicht
- `QuickTabSwitchFAB` - FAB zum Öffnen des Switchers

**Features:**
- "Zuletzt verwendet"-Sektion
- Liste aller offenen Tabs
- Aktiver Tab hervorgehoben

## Integration in MainActivity

### State-Management

```kotlin
// TabManager initialisieren
val tabManager = remember { TabManager(this@MainActivity) }
val openTabs by tabManager.openTabs.collectAsState()
val activeTabIndex by tabManager.activeTabIndex.collectAsState()
```

### Tab-Wiederherstellung

```kotlin
LaunchedEffect(Unit) {
    // Nach Datei-Import: Tabs wiederherstellen
    val allFiles = fileManager.getAllFilesGrouped().values.flatten()
    tabManager.restoreTabsFromPaths { path ->
        allFiles.find { it.path == path }
    }
    tabManager.loadHistoryFromPreferences()
}
```

### Dokument öffnen

```kotlin
// Datei öffnen (neuer Tab oder existierenden Tab aktivieren)
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

1. **Tab öffnen:**
   - Datei aus Liste auswählen
   - Datei wird in neuem Tab geöffnet
   - Existierende Tabs bleiben erhalten

2. **Zwischen Tabs wechseln:**
   - **Wischen:** Links/rechts über Dokument wischen
   - **Tab-Bar:** Auf Tab in oberer Leiste tippen
   - **Quick Switcher:** FAB drücken → Tab auswählen

3. **Tab schließen:**
   - X-Button auf Tab klicken
   - Long-Press auf Tab
   - Zurück-Button (schließt aktiven Tab)

4. **Schnellwechsel:**
   - Quick-Tab-Switcher öffnen
   - "Zuletzt verwendet" zeigt häufig genutzte Dokumente
   - Ein Tap zum Wechseln

### Für Entwickler

#### Neue Tab-Features hinzufügen

```kotlin
// Beispiel: Tab-Gruppen
class TabManager {
    fun createTabGroup(tabs: List<TabInfo>, name: String) {
        // Implementation
    }
}
```

#### Custom Tab-Actions

```kotlin
// Beispiel: Tab duplizieren
fun duplicateTab(index: Int) {
    val tab = openTabs.value.getOrNull(index) ?: return
    openTab(tab.fileInfo, tab.pageNumber)
}
```

## Persistierung

### SharedPreferences-Keys

- `tab_paths` - Pipe-separated Liste der Dateipfade
- `tab_pages` - Pipe-separated Liste der Seitennummern
- `active_tab` - Index des aktiven Tabs
- `tab_history` - Pipe-separated Navigationshistorie

### Format-Beispiel

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
   - Dokumente werden on-demand geladen

2. **State-Management:**
   - StateFlow für reaktive Updates
   - remember() für UI-State

3. **Memory Management:**
   - Tab-Limit (10) verhindert Memory-Issues
   - Alte Tabs werden automatisch geschlossen

### UX

1. **Konsistenz:**
   - Material Design 3 Guidelines
   - Einheitliche Gesten über gesamte App

2. **Feedback:**
   - Visuelle Bestätigung bei Tab-Wechsel
   - Animationen für besseres Verständnis

3. **Accessibility:**
   - Content Descriptions für Icons
   - Touch-Targets mind. 48dp

## Erweiterungsmöglichkeiten

### Geplante Features

1. **Tab-Gruppen:**
   - Verwandte Dokumente gruppieren
   - Farb-Coding

2. **Tab-Pinning:**
   - Wichtige Tabs fixieren
   - Vor automatischem Schließen schützen

3. **Tab-Suche:**
   - Durchsuchen aller offenen Tabs
   - Filter nach Typ (MD/PDF)

4. **Keyboard-Shortcuts:**
   - Ctrl+Tab für Tab-Wechsel
   - Ctrl+W zum Schließen

### Code-Beispiele für Erweiterungen

#### Tab-Pinning

```kotlin
data class TabInfo(
    val fileInfo: FileInfo,
    val pageNumber: Int = -1,
    val isPinned: Boolean = false  // Neu
)

fun closeTab(index: Int) {
    val tab = _openTabs.value.getOrNull(index) ?: return
    if (tab.isPinned) return  // Nicht schließen wenn gepinnt
    // ... rest of implementation
}
```

#### Tab-Gruppen

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

### Problem: Tabs werden nicht wiederhergestellt

**Lösung:**
- Prüfen ob `restoreTabsFromPaths()` nach FileManager-Initialisierung aufgerufen wird
- Logs prüfen: `TabManager` gibt Debug-Informationen aus

### Problem: Swipe-Gesten funktionieren nicht

**Lösung:**
- Sicherstellen dass `TabbedDocumentViewer` verwendet wird
- Keine konfligierenden Gesture-Detector im Content

### Problem: Tab-Bar wird nicht angezeigt

**Lösung:**
- `TabBar` in Column() mit `TabbedDocumentViewer` kombinieren
- Prüfen ob `tabs.isEmpty()` - Bar wird nicht bei 0 Tabs angezeigt

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
    // Öffne 2 Tabs
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

## Ressourcen

### Relevante Dateien

- `MainActivity.kt` - Integration
- `TabManager.kt` - Datenlogik
- `TabBar.kt` - Tab-UI
- `QuickTabSwitcher.kt` - Quick-Access
- `InternalFileViewer.kt` - Dokument-Viewer

### Dependencies

```kotlin
// build.gradle.kts
implementation("androidx.compose.foundation:foundation:1.5.4")
implementation("androidx.compose.material3:material3:1.1.2")
```

### Weitere Dokumentation

- [CHECKLIST_FEATURE.md](CHECKLIST_FEATURE.md) - Checklist-System
- [TAG_SYSTEM.md](TAG_SYSTEM.md) - Tag-System
- [QUICKNOTES_ARCHITECTURE.md](QUICKNOTES_ARCHITECTURE.md) - QuickNotes

## Changelog

### Version 1.1.0 (2025-12-13)
- ✅ Initiale Tab-System-Implementierung
- ✅ TabManager mit Persistierung
- ✅ TabBar UI-Komponente
- ✅ HorizontalPager für Swipe-Gesten
- ✅ QuickTabSwitcher für schnellen Zugriff
- ✅ Navigation-History
- ✅ MainActivity-Integration

## Lizenz

Gleiche Lizenz wie Hauptprojekt.
