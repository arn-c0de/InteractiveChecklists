# FAB Overlay System — Centralized FAB Management

## Overview

The FAB Overlay System provides centralized management for all Floating Action Buttons (FABs) in the application. All FABs are driven through a single component to ensure consistent presentation and simple configuration.

**✅ Fully migrated:**
- ✅ MapViewer (8 FABs)
- ✅ PdfViewer (4 FABs)
- ✅ MarkdownViewerScreen (3 FABs)
- ✅ InternalFileViewer (3 FABs)
- ✅ InternalFilesScreen (2 FABs)

## Architecture

### Components

1. **FABOverlay.kt** — central component
   - `FABConfig` — data class for FAB configuration
   - `FABOverlay` — Composable that renders all FABs
   - Predefined FAB sets for different screens:
     - `MapViewerFABs`
     - `QuickTabSwitcherFABs`
     - `MenuFABs`
     - `QuickAccessFABs`
     - `DataPadFABs`

2. **PreferencesManager.kt** — preferences management
   - `setFabSize(size: String)` — save FAB size ("small", "medium", "large")
   - `getFabSize(): String` — retrieve current FAB size
   - `getFabSizeDp(): Int` — get FAB size in dp (40, 56, 72)

3. **SettingsScreen.kt** — user-facing UI
   - FAB size selector (Small / Medium / Large)
   - Reset FAB positions

## FAB Sizes

| Size   | dp | Pixel (mdpi) | Use case |
|--------|----|--------------|----------|
| Small  | 40 | 40px         | Compact layouts, many FABs |
| Medium | 56 | 56px         | Standard Material3 size (default) |
| Large  | 72 | 72px         | Easier reachability, large displays |

## Usage

### 1. Import

```kotlin
import com.example.checklist_interactive.ui.common.FABOverlay
import com.example.checklist_interactive.ui.common.MapViewerFABs
```

### 2. Measure screen dimensions

```kotlin
val configuration = LocalConfiguration.current
val density = LocalDensity.current
val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
val fabMarginPx = with(density) { 12.dp.roundToPx() }
```

### 3. Use FABOverlay

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    // Your screen content...

    FABOverlay(
        prefsManager = prefsManager,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        marginPx = fabMarginPx,
        fabs = MapViewerFABs.create(
            onCenterOnPosition = { /* ... */ },
            onLayerSelection = { /* ... */ },
            // more callbacks...
            containerColorPrimary = MaterialTheme.colorScheme.primaryContainer,
            containerColorSecondary = MaterialTheme.colorScheme.secondaryContainer
        )
    )
}
```

### 4. Create a custom FAB configuration

```kotlin
val customFabs = listOf(
    FABConfig(
        id = "my_fab",  // unique id for position storage
        icon = Icons.Default.Add,
        contentDescription = "Add item",
        onClick = { /* Action */ },
        visible = true,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        defaultX = 0.9f,  // 90% from the left (0.0 - 1.0)
        defaultY = 0.9f,  // 90% from the top (0.0 - 1.0)
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

## Features

### Position persistence

FAB positions are saved to SharedPreferences automatically when a user long-presses and drags a FAB:

- **Long press** — enable FAB drag mode
- **Drag** — move FAB to a new location
- **Release** — position is saved automatically

### Reset positions

```kotlin
prefsManager.resetPdfViewerLayout()
```

This resets FAB positions to their defaults.

## MapViewer example

The MapViewer integration exposes eight FABs:

1. **Center on Position** — center map on aircraft
2. **Layers** — choose map layers
3. **Overlays** — configure overlays (compass, rings)
4. **Add Military Symbol** — add a military symbol
5. **Marker/Route Management** — manage markers and routes
6. **Screen Lock** — lock/unlock screen interaction
7. **Map Rotation** — toggle map rotation (North/HDG)
8. **Reset FAB Positions** — restore FAB positions to defaults

## Benefits

1. **Centralized management** — all FABs are defined in one place
2. **Consistent sizing** — single setting for FAB sizes
3. **Easy maintenance** — change in one place affects all screens
4. **Reusability** — predefined FAB sets for different screens
5. **User-friendly** — draggable FABs with automatic position saving
6. **Responsive** — adaptive to screen size

## Migration from hard-coded FABs

### Before

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

### After

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

## Future extensions

- Make FAB icon size configurable independently of FAB size
- Support different FAB shapes (round, square)
- Grouped FABs with animations
- FAB tooltips on hover (desktop)
- FAB badges for notifications

## See also

- [DraggableFab.kt](../../app/src/main/java/com/example/checklist_interactive/ui/common/DraggableFab.kt)
- [PreferencesManager.kt](../../app/src/main/java/com/example/checklist_interactive/data/prefs/PreferencesManager.kt)
- [SettingsScreen.kt](../../app/src/main/java/com/example/checklist_interactive/ui/settings/SettingsScreen.kt)

---
App Version: v1.0.25
Last Updated: 2026.01.19
---