# FAB Overlay System - Zentrale FAB-Verwaltung

## Überblick

Das FAB Overlay System bietet eine zentrale Verwaltung für alle Floating Action Buttons (FABs) in der Anwendung. Alle FABs werden über eine einzige Komponente gesteuert, was eine konsistente Darstellung und einfache Konfiguration ermöglicht.

**✅ Vollständig migriert:**
- ✅ MapViewer (8 FABs)
- ✅ PdfViewer (4 FABs)
- ✅ MarkdownViewerScreen (3 FABs)
- ✅ InternalFileViewer (3 FABs)
- ✅ InternalFilesScreen (2 FABs)

## Architektur

### Komponenten

1. **FABOverlay.kt** - Zentrale Komponente
   - `FABConfig` - Datenklasse für FAB-Konfiguration
   - `FABOverlay` - Composable zur Darstellung aller FABs
   - Vordefinierte FAB-Sets für verschiedene Screens:
     - `MapViewerFABs`
     - `QuickTabSwitcherFABs`
     - `MenuFABs`
     - `QuickAccessFABs`
     - `DataPadFABs`

2. **PreferencesManager.kt** - Einstellungsverwaltung
   - `setFabSize(size: String)` - FAB-Größe speichern ("small", "medium", "large")
   - `getFabSize(): String` - Aktuelle FAB-Größe abrufen
   - `getFabSizeDp(): Int` - FAB-Größe in dp (40, 56, 72)

3. **SettingsScreen.kt** - Benutzer-UI
   - FAB-Größenauswahl (Klein/Mittel/Groß)
   - FAB-Positionsreset

## FAB-Größen

| Größe  | dp | Pixel (mdpi) | Verwendung |
|--------|----|--------------| -----------|
| Small  | 40 | 40px         | Kompakte Ansicht, viele FABs |
| Medium | 56 | 56px         | Standard Material3 Größe (Default) |
| Large  | 72 | 72px         | Bessere Erreichbarkeit, größere Displays |

## Verwendung

### 1. Import

```kotlin
import com.example.checklist_interactive.ui.common.FABOverlay
import com.example.checklist_interactive.ui.common.MapViewerFABs
```

### 2. Screen Dimensions erfassen

```kotlin
val configuration = LocalConfiguration.current
val density = LocalDensity.current
val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
val fabMarginPx = with(density) { 12.dp.roundToPx() }
```

### 3. FABOverlay verwenden

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    // Ihr Screen Content...
    
    // FAB Overlay
    FABOverlay(
        prefsManager = prefsManager,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        marginPx = fabMarginPx,
        fabs = MapViewerFABs.create(
            onCenterOnPosition = { /* ... */ },
            onLayerSelection = { /* ... */ },
            // weitere Callbacks...
            containerColorPrimary = MaterialTheme.colorScheme.primaryContainer,
            containerColorSecondary = MaterialTheme.colorScheme.secondaryContainer,
            // weitere Farben...
        )
    )
}
```

### 4. Eigene FAB-Konfiguration erstellen

```kotlin
val customFabs = listOf(
    FABConfig(
        id = "my_fab",  // Eindeutige ID für Position-Speicherung
        icon = Icons.Default.Add,
        contentDescription = "Add item",
        onClick = { /* Action */ },
        visible = true,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        defaultX = 0.9f,  // 90% von rechts (0.0 - 1.0)
        defaultY = 0.9f,  // 90% von unten (0.0 - 1.0)
        enabled = true
    )
)

FABOverlay(
    prefsManager = prefsManager,
    screenWidthPx = screenWidthPx,
    screenHeightPx = screenHeightPx,
    fabs = customFabs
)
```

## Funktionen

### Position speichern

FAB-Positionen werden automatisch in SharedPreferences gespeichert, wenn der Benutzer einen FAB per Long-Press verschiebt:

- **Long Press** - FAB aktivieren zum Verschieben
- **Drag** - FAB an neue Position ziehen
- **Release** - Position wird automatisch gespeichert

### Position zurücksetzen

```kotlin
prefsManager.resetPdfViewerLayout()
```

Dies setzt alle FAB-Positionen auf ihre Standardwerte zurück.

## MapViewer Beispiel

Die vollständige MapViewer-Integration zeigt alle 8 FABs:

1. **Center on Position** - Zentriert Karte auf Flugzeugposition
2. **Layers** - Kartenebenen auswählen
3. **Overlays** - Overlays (Kompass, Ringe) konfigurieren
4. **Add Military Symbol** - Militärisches Symbol hinzufügen
5. **Marker/Route Management** - Marker und Routen verwalten
6. **Screen Lock** - Bildschirm sperren/entsperren
7. **Map Rotation** - Kartendrehung umschalten (Nord/HDG)
8. **Reset FAB Positions** - FAB-Positionen zurücksetzen

## Vorteile

1. **Zentrale Verwaltung** - Alle FABs an einem Ort definiert
2. **Konsistente Größe** - Eine Einstellung für alle FABs
3. **Einfache Wartung** - Änderungen an einem Ort durchführen
4. **Wiederverwendbarkeit** - Vordefinierte FAB-Sets für verschiedene Screens
5. **Benutzerfreundlich** - Verschiebbare FABs mit automatischer Positionsspeicherung
6. **Responsive** - Automatische Anpassung an Bildschirmgröße

## Migration von hartem Code

### Vorher

```kotlin
Column(modifier = Modifier.align(Alignment.TopEnd)) {
    FloatingActionButton(onClick = { /* ... */ }) {
        Icon(Icons.Default.Add, null)
    }
    FloatingActionButton(onClick = { /* ... */ }) {
        Icon(Icons.Default.Settings, null)
    }
}
```

### Nachher

```kotlin
FABOverlay(
    prefsManager = prefsManager,
    screenWidthPx = screenWidthPx,
    screenHeightPx = screenHeightPx,
    fabs = listOf(
        FABConfig("fab_add", Icons.Default.Add, "Add", onClick = { /* ... */ }),
        FABConfig("fab_settings", Icons.Default.Settings, "Settings", onClick = { /* ... */ })
    )
)
```

## Zukünftige Erweiterungen

- FAB-Icon-Größe unabhängig von FAB-Größe konfigurierbar
- FAB-Formen (rund, eckig) konfigurierbar
- FAB-Gruppen mit Animationen
- FAB-Tooltips beim Hover
- FAB-Badges für Benachrichtigungen

## Siehe auch

- [DraggableFab.kt](../../app/src/main/java/com/example/checklist_interactive/ui/common/DraggableFab.kt)
- [PreferencesManager.kt](../../app/src/main/java/com/example/checklist_interactive/data/prefs/PreferencesManager.kt)
- [SettingsScreen.kt](../../app/src/main/java/com/example/checklist_interactive/ui/settings/SettingsScreen.kt)
