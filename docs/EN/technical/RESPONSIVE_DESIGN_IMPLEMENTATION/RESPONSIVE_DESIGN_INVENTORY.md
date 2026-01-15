# Responsive Design Implementation Inventory

## Current Status
The app has been primarily developed and tested on tablets in portrait orientation. This document tracks the components that require responsive design updates to work reliably across all device form factors.

## Device Target Matrix

### Target Devices
| Device Class | Width (dp) | Height (dp) | Priority | Notes |
|--------------|-----------|-------------|----------|-------|
| Small Phone | 360x640 | Portrait | HIGH | Minimum supported size |
| Small Phone | 640x360 | Landscape | HIGH | Very constrained vertical space |
| Typical Phone | 412x732 | Portrait | HIGH | Most common phone size |
| Typical Phone | 732x412 | Landscape | HIGH | Common usage pattern |
| Large Phone/Phablet | 480x800 | Portrait | MEDIUM | Growing segment |
| Small Tablet | 600x1024 | Portrait | MEDIUM | Small tablets, large phones |
| Small Tablet | 1024x600 | Landscape | MEDIUM | Common tablet orientation |
| Large Tablet | 800x1280 | Both | LOW | Well supported (primary dev device) |

## Component Inventory

### Priority 1: Core Navigation & Structure
Status: **Needs Implementation**

#### Components:
- [ ] **Main Navigation** (if exists)
  - Current: Unknown structure
  - Required: Bottom navigation for phones, navigation rail for tablets
  - File: Check MainActivity or main navigation composable

- [ ] **FABOverlay.kt** ✓ (Partially Complete)
  - Current: Uses configuration.screenWidthDp >= 600 for tablet detection
  - Status: Has responsive positioning logic
  - Improvements needed:
    - Use new WindowSize utilities instead of raw dp checks
    - Add landscape-specific positioning for phones
    - Verify touch target sizes meet minimum 48dp

### Priority 2: Pickers & Dialogs
Status: **Needs Updates**

#### Components:
- [ ] **MilitarySymbolPicker.kt**
  - Current: Fixed grid layout
  - Issues:
    - May not adapt columns for small screens
    - Dialog sizing may overflow on phones
    - Touch targets may be too small in compact mode
  - Required:
    - Adaptive grid columns (2 on phones, 3-4 on tablets)
    - Responsive tile sizes
    - Dialog width constraints per device size
    - Minimum touch target enforcement (48dp)

- [ ] **LocationEditDialog** (in marker package)
  - Check if dialog adapts to screen width
  - May need scroll for landscape phones

- [ ] **OverlaySelectionDialog.kt**
  - Verify dialog sizing on small screens
  - Check landscape compatibility

### Priority 3: Map Components
Status: **Needs Updates**

#### Map Core:
- [ ] **MapViewer.kt**
  - Current: Large file, likely has some responsive logic
  - Check: State preservation across orientation changes
  - Required:
    - Verify map state (center, zoom, selections) persists
    - Check control positioning in landscape
    - Verify bottom sheet sizing

- [ ] **MapNavigationDisplay.kt** ✓ (Just Updated)
  - Recent updates: Width limited to 320dp on phones, smaller text/icons
  - Status: Good foundation, monitor in testing

- [ ] **MapFlightInstruments.kt** ✓ (Just Updated)
  - Recent updates: Removed black overlay on tablets
  - Status: Has responsive sizing logic (isTablet checks)
  - Verify: Instrument layout in landscape phones

#### Map Overlays:
- [ ] **TacticalUnitsMapOverlay.kt**
  - Current: Unknown scaling behavior
  - Required:
    - Use rememberMapOverlayScale() for marker sizes
    - Scale label text based on screen size
    - Ensure labels don't overlap controls

- [ ] **AirportMarkerLabelsOverlay.kt**
  - Required:
    - Adaptive label sizes
    - Collision detection with screen edges
    - Responsive font sizes

- [ ] **AirspaceCirclesOverlay.kt**
  - Check: Line widths and label sizes scale appropriately

- [ ] **AARangeRingsOverlay.kt**
  - Check: Ring labels scale and remain readable

- [ ] **MapDrawingOverlay.kt**
  - Check: Drawing tools work in all orientations
  - Verify: Touch handling on small screens

- [ ] **MapToolOverlays.kt**
  - Check: Tool selection UI adapts to screen size

### Priority 4: Tactical Units
Status: **Needs Updates**

#### Components:
- [ ] **TacticalUnitsListScreen.kt**
  - Check: List layout in landscape
  - Required:
    - Adaptive list item sizing
    - Two-pane layout for tablets in landscape?
    - Proper scroll behavior in constrained heights

- [ ] **TacticalUnitsComponents.kt**
  - Check: Individual component sizing
  - Verify: Touch targets on all list items

### Priority 5: Common Components
Status: **Needs Review**

#### Components:
- [ ] **DraggableFab.kt**
  - Check: Drag behavior in different screen sizes
  - Verify: Saved positions valid across orientations

- [ ] **All Dialogs**
  - Audit: Find all AlertDialog and Dialog usages
  - Required:
    - MaxWidth constraints
    - Scroll support for landscape
    - Adaptive padding

## Responsive Design Patterns to Apply

### 1. Window Size Detection
Replace all instances of:
```kotlin
configuration.screenWidthDp >= 600
```

With:
```kotlin
val windowSize = rememberWindowSize()
// Then use windowSize.isTablet, windowSize.isCompact, etc.
```

### 2. Adaptive Grids
For any LazyVerticalGrid or similar:
```kotlin
val columns = rememberAdaptiveGridColumns(
    compactColumns = 2,
    mediumColumns = 3,
    expandedColumns = 4
)
```

### 3. Responsive Dimensions
For spacing and sizing:
```kotlin
val dimensions = rememberResponsiveDimensions()
// Use dimensions.screenPadding, dimensions.contentPadding, etc.
```

### 4. Map Overlay Scaling
For map markers and overlays:
```kotlin
val overlayScale = rememberMapOverlayScale()
// Apply scale to marker sizes, label text, line widths
```

### 5. Touch Targets
Ensure all clickable elements:
```kotlin
.size(dimensions.minTouchTarget) // Minimum 48dp
```

## State Preservation Strategy

### Required for All Screens:
1. **ViewModel Usage**
   - Survives configuration changes automatically
   - Use SavedStateHandle for process death

2. **rememberSaveable for Compose State**
   - Use for local UI state that should survive rotation

3. **Map State**
   - Save: center lat/lon, zoom level, selected marker ID
   - Restore: reposition and reselect on configuration change

### Critical State to Preserve:
- [ ] Active navigation target
- [ ] Selected tactical unit
- [ ] Open dialog state
- [ ] Map center and zoom
- [ ] Selected military symbol (during placement)
- [ ] Drawing mode and current drawing
- [ ] Filter selections
- [ ] Expanded/collapsed panel states

## Testing Strategy

### Automated Testing
- [ ] Create UI test suite with Espresso/Compose Testing
- [ ] Test critical flows on each target device config
- [ ] Screenshot tests for regression detection

### Manual Testing Checklist
See RESPONSIVE_DESIGN_QA.md (to be created)

### Device Test Matrix
See DEVICE_COMPATIBILITY_MATRIX.md (to be created)

## Implementation Phases

### Phase 1: Foundation (COMPLETED)
- [x] Create WindowSizeClass utilities
- [x] Create ResponsiveLayout.kt

### Phase 2: High-Impact Components (CURRENT)
- [ ] Update MilitarySymbolPicker
- [ ] Update map overlays scaling
- [ ] Implement adaptive navigation (if needed)

### Phase 3: State Preservation
- [ ] Audit and fix orientation change handling
- [ ] Add SavedStateHandle where needed

### Phase 4: Testing & Documentation
- [ ] Create automated test suite
- [ ] Create QA checklist
- [ ] Update README
- [ ] Create device compatibility matrix

### Phase 5: Polish
- [ ] Accessibility audit (touch targets, font scaling)
- [ ] RTL layout verification
- [ ] Performance optimization for different devices

## Notes
- Current responsive logic uses `configuration.screenWidthDp >= 600` for tablet detection
- Recent updates to MapNavigationDisplay and MapFlightInstruments show good responsive patterns
- FABOverlay has landscape-specific positioning logic already
- Need to verify if main navigation exists and how it's structured
