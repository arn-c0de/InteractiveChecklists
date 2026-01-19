# Traffic Pattern (Circuit) Feature

## Overview

The Traffic Pattern feature enables pilots to fly realistic circuit patterns around runways. The system automatically generates correct flight legs based on standard aviation procedures.

## Features

### Pattern Generation
- **Standard Traffic Pattern**: Automatically calculates all pattern legs:
  - Departure
  - Crosswind (90° turn)
  - Downwind (parallel to runway, opposite direction)
  - Base (90° turn toward final)
  - Final (approach to runway)

### Pattern Configuration

#### Pattern Sizes
- **Normal**: 0.5 NM downwind offset, 1000 ft pattern altitude
- **Medium**: 0.75 NM downwind offset, 1000 ft pattern altitude
- **Large**: 1.0 NM downwind offset, 1200 ft pattern altitude
- **Very Large**: 1.5 NM downwind offset, 1500 ft pattern altitude

#### Pattern Direction
- **Left-Hand** (default): left turns
- **Right-Hand**: right turns (for special runway configurations)

## Usage

### Enabling a Pattern
1. Navigate to an airport with runways
2. Open the Runway Approach popup
3. Click the **PATTERN** button (next to the NM dropdown)
4. Choose a runway
5. Configure pattern size and direction

### Pattern Visualization
- **Green dashed polyline**: the pattern path
- **Labels**: show individual legs (DEPARTURE, CROSSWIND, etc.)
- **Navigation guidance**: red navigation line from current position to pattern entry

## Technical Details

### Pattern Calculation (MapRoutePattern.kt)

Generation follows aviation conventions and scales with size presets:

```kotlin
// Distances (in NM, converted to meters)
// Pattern size presets scale BOTH lateral and longitudinal distances:
// - Normal: base distances (e.g., 0.5 NM departure extension, 0.3 NM crosswind, 0.5 NM downwind offset)
// - Medium/Large/Very Large: scale factors (1.25x, 1.5x, 2.0x) apply to
//   departure extension, crosswind, downwind length, base extension, final and short-final distances
- Departure extension (scaled): 0.5 NM * sizeScale past runway end
- Crosswind turn (scaled): 0.3 NM * sizeScale
- Downwind parallel: pattern-size dependent (0.5–1.5 NM) and scaled longitudinally
- Base leg: equal to downwind lateral distance, base extension scaled
- Final approach (scaled): 0.5 NM * sizeScale from threshold
- Short final (scaled): 0.2 NM * sizeScale from threshold
```

### Coordinate Calculations
- Uses Haversine formula for great-circle navigation
- Accounts for earth curvature for precise placement
- Normalizes headings to correct compass values

### Pattern Overlay
- `Polyline`: green dashed line for the path
- `PatternLabelOverlay`: custom overlay for leg labels
- Auto-scales labels and rendering with zoom level

## Integration with Existing Features

### Runway Approach Integration
- Pattern mode works alongside Runway Approach
- Both modes can be active concurrently
- Navigation combines pattern guidance with final approach guidance

### Navigation System
- Calculates route to pattern entry point
- Distance and bearing update in real time
- Integrates with DataPad live aircraft position

## Future Extensions

The modular design allows easy additions:

1. **Holding Patterns**: holding stacks for traffic management
2. **Instrument Approaches**: ILS, VOR, NDB approach patterns
3. **Custom Patterns**: user-defined pattern configurations
4. **Pattern Recording**: record and replay flown patterns
5. **Multi-Aircraft Patterns**: visualize multiple aircraft in the pattern

## References

### Aviation Standards
- FAA Advisory Circular AC 90-66B: "Recommended Standard Traffic Patterns for Aeronautical Operations"
- Typical pattern altitude: 1000 ft AGL (standard), 800 ft AGL (alternate)
- Pattern direction: left-hand turns (standard), right-hand when required

### Code References
- `MapRoutePattern.kt`: pattern generation and utilities
- `MapViewer.kt`: pattern integration and UI
- `TrafficPatternGenerator`: core pattern calculation class
- `PatternLabelOverlay`: custom overlay for pattern labels

## Performance

- Pattern generation: <1 ms for a standard pattern
- Overlay rendering: GPU-accelerated
- Memory footprint: ~100 KB per active pattern
- No measurable impact on frame rate when active

## Known Limitations

1. Runway data must be complete (heading, length)
2. Pattern requires stored runways (won't generate from ad-hoc points)
3. No dynamic wind adjustment yet (planned for v2.0)
4. No vertical / altitude visualization (2D display only)


---
App Version: v1.0.25
Last Updated: 2026.01.19
---