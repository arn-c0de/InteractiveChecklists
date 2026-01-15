# Responsive Design - Manual QA Checklist

## Test Device Matrix

### Priority 1: Critical Devices (Must Test)
| Device Profile | Width x Height (dp) | Orientation | Screen Density | Test Priority |
|---------------|---------------------|-------------|----------------|---------------|
| Small Phone | 360 x 640 | Portrait | mdpi-xhdpi | **HIGH** |
| Small Phone | 640 x 360 | Landscape | mdpi-xhdpi | **HIGH** |
| Standard Phone | 412 x 732 | Portrait | xxhdpi | **HIGH** |
| Standard Phone | 732 x 412 | Landscape | xxhdpi | **HIGH** |
| Small Tablet | 600 x 1024 | Portrait | xhdpi | **MEDIUM** |
| Small Tablet | 1024 x 600 | Landscape | xhdpi | **MEDIUM** |
| Large Tablet | 800 x 1280 | Portrait | xhdpi | **MEDIUM** |
| Large Tablet | 1280 x 800 | Landscape | xhdpi | **MEDIUM** |

### Priority 2: Extended Devices (Nice to Have)
| Device Profile | Width x Height (dp) | Orientation | Notes |
|---------------|---------------------|-------------|-------|
| Very Small Phone | 320 x 568 | Portrait | Minimum supported size |
| Large Phone/Phablet | 480 x 800 | Both | Growing segment |
| Foldable (folded) | 412 x 732 | Portrait | Like standard phone |
| Foldable (unfolded) | 673 x 841 | Landscape | Like small tablet |

## Testing Scenarios

### 1. Map Screen Tests

#### 1.1 Map Controls & Navigation
- [ ] **FAB Buttons** (FABOverlay.kt)
  - [ ] All FABs visible and accessible in portrait
  - [ ] All FABs visible and accessible in landscape
  - [ ] FABs don't overlap map navigation display
  - [ ] FABs don't overlap each other
  - [ ] Touch targets are minimum 48dp (easy to tap)
  - [ ] FAB positions persist when rotating device
  - [ ] Landscape positions work correctly on phones

#### 1.2 Map Navigation Display (MapNavigationDisplay.kt)
- [ ] **Display in Portrait**
  - [ ] Card width appropriate (not too wide on phones)
  - [ ] Card doesn't overlap FABs
  - [ ] Text is readable at all sizes
  - [ ] Close (X) button is accessible
  - [ ] Expand/collapse button works

- [ ] **Display in Landscape**
  - [ ] Card doesn't take excessive vertical space
  - [ ] All controls visible without scrolling
  - [ ] Text remains readable
  - [ ] Close button not hidden behind other UI

- [ ] **State Preservation**
  - [ ] Navigation target preserved on rotation
  - [ ] Expanded/collapsed state preserved on rotation
  - [ ] Selected runway preserved on rotation

#### 1.3 Flight Instruments (MapFlightInstruments.kt)
- [ ] **Portrait Mode**
  - [ ] Instruments visible and readable
  - [ ] No black overlay on tablets
  - [ ] Instruments scale appropriately

- [ ] **Landscape Mode (especially phones)**
  - [ ] Instruments don't block map navigation
  - [ ] Instruments don't block route line/markers
  - [ ] Proper spacing from screen edges
  - [ ] Click-through works when no data

#### 1.4 Map Overlays
- [ ] **Tactical Units Overlay** (TacticalUnitsMapOverlay.kt)
  - [ ] Markers scale appropriately on all devices
  - [ ] Labels readable without overlap
  - [ ] Labels don't overlap map controls
  - [ ] Touch targets adequate for selection

- [ ] **Airport Labels** (AirportMarkerLabelsOverlay.kt)
  - [ ] Label text size appropriate for screen
  - [ ] Labels don't clip at screen edges
  - [ ] Collision detection works

- [ ] **Airspace Circles** (AirspaceCirclesOverlay.kt)
  - [ ] Line widths scale correctly
  - [ ] Labels readable
  - [ ] Performance acceptable with multiple circles

- [ ] **AA Range Rings** (AARangeRingsOverlay.kt)
  - [ ] Ring size calculations correct
  - [ ] Labels positioned correctly
  - [ ] Readable on all device sizes

- [ ] **Drawing Overlay** (MapDrawingOverlay.kt)
  - [ ] Drawing tools accessible in all orientations
  - [ ] Line widths appropriate
  - [ ] Touch handling accurate on small screens

### 2. Dialogs & Pickers

#### 2.1 Military Symbol Picker (MilitarySymbolPicker.kt) ✓
- [ ] **Dialog Sizing**
  - [ ] Dialog fits screen on smallest phone (360dp)
  - [ ] Dialog not too small on tablets
  - [ ] Appropriate width for each device class
  - [ ] Maximizes height on landscape phones

- [ ] **Grid Layout**
  - [ ] 2-3 columns on small phones (< 360dp)
  - [ ] 3-4 columns on standard phones
  - [ ] 4-5 columns on tablets
  - [ ] Items scale appropriately
  - [ ] Touch targets minimum 48dp

- [ ] **Content**
  - [ ] Header text readable on all devices
  - [ ] Close button accessible (48dp touch target)
  - [ ] Category tabs scroll if needed
  - [ ] Affiliation chips fit in available space
  - [ ] Symbol icons clearly visible
  - [ ] Symbol names readable (not truncated)

- [ ] **Scrolling**
  - [ ] Grid scrolls smoothly
  - [ ] Header remains visible while scrolling
  - [ ] No layout jumpiness

- [ ] **State Preservation**
  - [ ] Selected category preserved on rotation
  - [ ] Selected affiliation preserved on rotation

#### 2.2 Location Edit Dialog
- [ ] Dialog fits on screen in both orientations
- [ ] Form fields accessible without excessive scrolling
- [ ] Keyboard doesn't obscure input fields
- [ ] Touch targets adequate for all buttons
- [ ] State preserved on rotation

#### 2.3 Overlay Selection Dialog
- [ ] Dialog sized appropriately
- [ ] All overlay options visible
- [ ] Touch targets adequate
- [ ] Works in landscape

### 3. Tactical Units Screen

#### 3.1 List View (TacticalUnitsListScreen.kt)
- [ ] **Portrait Mode**
  - [ ] List items readable
  - [ ] Touch targets adequate
  - [ ] Action buttons accessible

- [ ] **Landscape Mode**
  - [ ] List fills available space appropriately
  - [ ] Items not too tall/short
  - [ ] Horizontal space used efficiently
  - [ ] Consider: two-pane layout for tablets?

- [ ] **List Items** (TacticalUnitsComponents.kt)
  - [ ] Text sizes appropriate
  - [ ] Icons scale correctly
  - [ ] Touch areas minimum 48dp
  - [ ] Spacing consistent

### 4. State Preservation (Critical!)

#### 4.1 Map State
- [ ] **Orientation Change**
  - [ ] Map center/zoom preserved
  - [ ] Selected marker preserved
  - [ ] Active navigation target preserved
  - [ ] Drawing overlay preserved
  - [ ] Visible overlays preserved

#### 4.2 Dialog State
- [ ] Open dialogs remain open (or gracefully close)
- [ ] Dialog content state preserved
- [ ] Selections within dialogs preserved

#### 4.3 Selection State
- [ ] Selected tactical unit preserved
- [ ] Selected symbol during placement preserved
- [ ] Filter selections preserved
- [ ] Panel expanded/collapsed states preserved

### 5. Accessibility

#### 5.1 Touch Targets
- [ ] All buttons minimum 48dp x 48dp
- [ ] FABs minimum 56dp (standard size)
- [ ] List items adequate height
- [ ] Grid items adequate size
- [ ] Spacing between tappable elements adequate

#### 5.2 Text Scaling
- [ ] App works with system font size set to "Large"
- [ ] App works with system font size set to "Largest"
- [ ] Text doesn't overflow containers
- [ ] Touch targets don't overlap

#### 5.3 Color & Contrast
- [ ] Affiliation colors distinguishable
- [ ] Labels readable on map background
- [ ] Dark theme support (if applicable)

### 6. Performance

#### 6.1 Smooth Performance
- [ ] No frame drops during rotation
- [ ] Smooth scrolling in grids/lists
- [ ] Map panning smooth with overlays
- [ ] No memory leaks on rotation

#### 6.2 Memory Usage
- [ ] No excessive memory allocation
- [ ] Large bitmaps handled appropriately
- [ ] Overlays don't cause OOM

## Testing Procedure

### For Each Device Configuration:

1. **Initial Setup**
   - Launch app
   - Note device profile and orientation
   - Check initial layout

2. **Navigation Flow**
   - Navigate to map screen
   - Open military symbol picker
   - Select a symbol
   - Place symbol on map
   - Open tactical units list
   - Return to map

3. **Rotation Test**
   - At each major screen, rotate device
   - Verify layout adapts
   - Verify state preserved
   - Check for visual glitches

4. **Interaction Test**
   - Test all major touch interactions
   - Verify touch targets adequate
   - Check for UI overlap issues

5. **Stress Test**
   - Add many markers/units
   - Enable all overlays
   - Verify performance
   - Check for layout issues with heavy content

## Issue Reporting Template

When logging issues, include:

```
**Device Profile:** [e.g., Small Phone 360x640]
**Orientation:** [Portrait/Landscape]
**Android Version:** [e.g., Android 12]
**Screen Density:** [e.g., xxhdpi]

**Component:** [e.g., MilitarySymbolPicker]
**Issue:** [Clear description]
**Expected:** [What should happen]
**Actual:** [What actually happens]
**Severity:** [Critical/High/Medium/Low]

**Screenshots:** [If available]
**Steps to Reproduce:**
1.
2.
3.
```

## Sign-off Criteria

Before release, verify:
- [ ] All **HIGH** priority devices tested
- [ ] All **Critical** issues resolved
- [ ] All **High** severity issues resolved or documented as known issues
- [ ] State preservation works correctly across all tested devices
- [ ] Touch targets meet minimum requirements
- [ ] No visual overlap or clipping issues
- [ ] Performance acceptable on lowest-spec test device

## Notes

### Known Limitations
- Document any known issues or limitations here
- Example: "Symbol picker may show only 2 columns on very small phones (< 320dp)"

### Future Improvements
- Document planned improvements for future releases
- Example: "Consider adaptive two-pane layout for tablets in landscape"
