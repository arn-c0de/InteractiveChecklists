# Device Compatibility Matrix

## Supported Devices & Form Factors

### Overview
Checklist Interactive is designed to work across a wide range of Android devices, from compact phones to large tablets, in both portrait and landscape orientations.

### Minimum Requirements
- **Android Version:** 8.0 (API 26) or higher
- **Screen Size:** 320dp width minimum
- **Screen Density:** mdpi to xxxhdpi
- **RAM:** 2GB minimum, 4GB recommended
- **Storage:** 100MB minimum for app

## Device Classifications

### Compact Devices (< 600dp width)
**Primary Use Case:** Phones in portrait orientation

| Device Example | Width x Height | WindowSizeClass | Support Level |
|----------------|----------------|-----------------|---------------|
| Small Phone | 320-359dp | COMPACT | ⚠️ Limited |
| Standard Phone | 360-419dp | COMPACT | ✅ Full |
| Large Phone | 420-599dp | COMPACT | ✅ Full |

**Adaptations:**
- 2-3 column grids
- Bottom navigation (if applicable)
- Compact spacing and padding
- Smaller dialog sizes
- Reduced text sizes where appropriate
- Minimized vertical space for overlays

### Medium Devices (600-840dp width)
**Primary Use Case:** Large phones in landscape, small tablets

| Device Example | Width x Height | WindowSizeClass | Support Level |
|----------------|----------------|-----------------|---------------|
| Phone Landscape | 640-732dp | MEDIUM | ✅ Full |
| Small Tablet | 600-700dp | MEDIUM | ✅ Full |
| 7" Tablet | 600-840dp | MEDIUM | ✅ Full |

**Adaptations:**
- 3-4 column grids
- Navigation rail (if multi-screen app)
- Standard spacing and padding
- Medium dialog sizes
- Consider two-pane layouts (future)

### Expanded Devices (> 840dp width)
**Primary Use Case:** Tablets in landscape, large tablets

| Device Example | Width x Height | WindowSizeClass | Support Level |
|----------------|----------------|-----------------|---------------|
| 10" Tablet | 800-1000dp | EXPANDED | ✅ Full |
| 12" Tablet | 1000-1280dp | EXPANDED | ✅ Full |
| Chromebook | 1200dp+ | EXPANDED | ✅ Full* |

*Chromebook support may require additional testing and optimizations

**Adaptations:**
- 4-6 column grids
- Navigation rail or permanent drawer
- Generous spacing and padding
- Large dialog sizes
- Two-pane layouts where appropriate
- Increased maximum content widths

## Tested Devices

### Physical Device Testing

| Device | Model | Screen Size | Resolution | WindowSize | Status |
|--------|-------|-------------|------------|------------|--------|
| [To be filled] | [Model] | [Size] | [Resolution] | [Class] | [✅/⚠️/❌] |

### Emulator Testing

| Profile | Width x Height (dp) | Orientation | Density | Status |
|---------|---------------------|-------------|---------|--------|
| Phone - Small | 360 x 640 | Portrait | xxhdpi | ✅ |
| Phone - Small | 640 x 360 | Landscape | xxhdpi | ✅ |
| Phone - Standard | 412 x 732 | Portrait | xxhdpi | ✅ |
| Phone - Standard | 732 x 412 | Landscape | xxhdpi | ✅ |
| Phone - Large | 480 x 800 | Portrait | xxhdpi | ⏳ |
| Tablet - Small | 600 x 1024 | Portrait | xhdpi | ⏳ |
| Tablet - Small | 1024 x 600 | Landscape | xhdpi | ⏳ |
| Tablet - 10" | 800 x 1280 | Portrait | xhdpi | ⏳ |
| Tablet - 10" | 1280 x 800 | Landscape | xhdpi | ✅* |

*Primary development device

**Legend:**
- ✅ Fully tested and working
- ⏳ Pending testing
- ⚠️ Tested with known issues
- ❌ Not working / Not supported

## Feature Support Matrix

### Core Features

| Feature | Compact | Medium | Expanded | Notes |
|---------|---------|--------|----------|-------|
| Map Display | ✅ | ✅ | ✅ | Fully responsive |
| Map Navigation | ✅ | ✅ | ✅ | Reduced width on phones |
| Flight Instruments | ✅ | ✅ | ✅ | Scales appropriately |
| Military Symbol Picker | ✅ | ✅ | ✅ | Adaptive grid (2-6 columns) |
| Tactical Units List | ✅ | ✅ | ✅ | Standard list view |
| Map Overlays | ✅ | ✅ | ✅ | Scaled markers/labels |
| Drawing Tools | ✅ | ✅ | ✅ | Touch-optimized |
| FAB Controls | ✅ | ✅ | ✅ | Adaptive positioning |

### Advanced Features

| Feature | Compact | Medium | Expanded | Notes |
|---------|---------|--------|----------|-------|
| Multi-window | ⏳ | ⏳ | ⏳ | Requires testing |
| Foldable Support | ⏳ | ⏳ | ⏳ | Requires testing |
| Desktop Mode | ❌ | ⚠️ | ⏳ | Chromebook optimization |
| External Display | ❌ | ⏳ | ⏳ | Future consideration |

## Orientation Support

### Portrait Orientation
- **All Devices:** Fully supported
- **Phones:** Primary orientation
- **Tablets:** Fully functional

### Landscape Orientation
- **Phones (< 600dp width):**
  - ✅ Map display optimized for constrained height
  - ✅ Flight instruments positioned to avoid overlap
  - ✅ Dialogs maximize vertical space
  - ⚠️ Some content may require scrolling

- **Tablets (≥ 600dp width):**
  - ✅ Primary orientation for many use cases
  - ✅ Full feature parity with portrait
  - ✅ Enhanced layouts where appropriate

## Responsive Design Features

### Implemented
- [x] WindowSizeClass utilities for device detection
- [x] Responsive dimension scaling
- [x] Adaptive grid layouts (MilitarySymbolPicker)
- [x] Touch target enforcement (48dp minimum)
- [x] Dialog sizing by device class
- [x] Compact layouts for phones
- [x] Map overlay scaling (in progress)
- [x] State preservation across rotations (in progress)

### Pending Implementation
- [ ] Adaptive navigation (if multi-screen app)
- [ ] Two-pane layouts for tablets
- [ ] Foldable-specific optimizations
- [ ] Desktop mode optimizations
- [ ] Accessibility enhancements

## Known Issues & Limitations

### By Device Class

#### Compact Devices (Phones)
- **Very Small Phones (< 320dp):**
  - ⚠️ Not officially supported, may have layout issues
  - ⚠️ Symbol picker limited to 2 columns
  - ⚠️ Some dialogs may be cramped

- **Small Phones (320-359dp):**
  - ⚠️ Limited support
  - ⚠️ Symbol picker uses smallest grid size
  - ⚠️ Some text may be very small

- **Landscape Phones:**
  - ⚠️ Vertically constrained, some scrolling required
  - ⚠️ Flight instruments may cover more map area
  - ✅ All functionality available

#### Medium Devices
- **No major known issues**
- ✅ All features fully functional

#### Expanded Devices
- **No major known issues**
- ✅ All features fully functional
- ℹ️ Some components may show extra whitespace

### By Android Version

| Android Version | API Level | Support Level | Notes |
|-----------------|-----------|---------------|-------|
| 8.0 Oreo | 26 | ✅ Minimum | Minimum supported version |
| 9.0 Pie | 28 | ✅ Full | |
| 10 | 29 | ✅ Full | |
| 11 | 30 | ✅ Full | |
| 12 | 31 | ✅ Full | |
| 13 | 33 | ✅ Full | |
| 14 | 34 | ✅ Full | Latest tested |

## Testing Recommendations

### For Developers
1. **Primary Testing:**
   - Use emulator profiles listed above
   - Test both portrait and landscape
   - Verify state preservation on rotation

2. **Before Release:**
   - Test on at least 3 physical devices:
     - 1 compact phone
     - 1 medium device
     - 1 tablet
   - Run through full QA checklist
   - Verify all critical flows

3. **Continuous Testing:**
   - Screenshot tests for regressions
   - Automated UI tests for multiple configurations
   - Regular manual testing on new Android versions

### For QA Team
- Follow RESPONSIVE_DESIGN_QA.md checklist
- Use device matrix above as reference
- Report issues using provided template
- Verify fixes on affected device classes

## Accessibility

### Touch Targets
- ✅ Minimum 48dp x 48dp enforced
- ✅ FABs standard 56dp size
- ✅ Adequate spacing between tappable elements

### Font Scaling
- ⏳ Needs testing with Large/Largest system fonts
- ⏳ Verify no text overflow
- ⏳ Verify layouts don't break

### RTL Support
- ⏳ Pending RTL layout testing
- ⏳ Verify all layouts mirror correctly

## Performance Considerations

### By Device Class

| Device Class | Expected Performance | Notes |
|--------------|---------------------|-------|
| Compact (low-end) | 30+ fps | May struggle with many overlays |
| Compact (mid-range) | 60 fps | Smooth performance expected |
| Medium | 60 fps | Smooth performance |
| Expanded | 60 fps | Smooth performance |

### Optimization Strategies
- Lazy loading for lists and grids
- Efficient overlay rendering
- State preservation to avoid recomposition
- Appropriate use of remember() and derivedStateOf()

## Future Enhancements

### Planned
1. **Adaptive Navigation** (if multi-screen app)
   - Bottom navigation for phones
   - Navigation rail for tablets
   - Feature parity across navigation types

2. **Two-Pane Layouts**
   - Master-detail for tactical units on tablets
   - Side-by-side list and map views
   - Enhanced productivity on large screens

3. **Foldable Support**
   - Optimize for unfolded state
   - Handle fold/unfold transitions
   - Adaptive layouts for table-top mode

### Under Consideration
- Desktop mode optimizations
- External display support
- Multi-window enhancements
- ChromeOS-specific features

## Support & Feedback

### Reporting Device-Specific Issues
Please include:
- Device make and model
- Android version
- Screen size and density
- Orientation when issue occurs
- Screenshots if possible

### Testing Requests
If you have a specific device you'd like us to test, please reach out with:
- Device specifications
- Specific features to verify
- Any known issues to watch for

---

**Last Updated:** [To be filled with implementation date]
**Document Version:** 1.0
**App Version:** [Current version]


---
App Version: v1.0.25
Last Updated: 2026.01.19
---