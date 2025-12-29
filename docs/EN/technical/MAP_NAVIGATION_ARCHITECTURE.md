# Map Navigation Architecture Refactoring

## Overview
The map navigation system has been refactored into modular components for better organization and maintainability.

## File Structure

### 1. **MarkerRouteManagement.kt** (Main Coordinator)
**Purpose**: Central coordinator for marker and route management
**Contains**:
- `MarkerRouteViewModel`: Coordinates all marker and route operations
- `MarkerRouteManagementSheet`: Main UI sheet with tabs for Details/Markers/Routes
- `MarkerGroupsList`, `MarkerGroupItem`, `MarkerListItem`: Marker list components
- `RoutesList`, `RouteListItem`: Route list components  
- `LocationEditDialog`, `RouteEditDialog`: Edit dialogs for locations and routes

**Responsibilities**:
- Managing marker groups and categories
- Listing and filtering markers
- Listing and managing routes
- Coordinating between single-marker and multi-waypoint navigation

### 2. **SingleMarkerNavigation.kt** (Single Marker Operations)
**Purpose**: Handles individual marker navigation and details
**Contains**:
- `MarkerDetailsContent`: Complete marker details view with action buttons
- `MarkerDetailsHeader`: Name and coordinates display
- `MarkerDetailsInfoGrid`: Metadata grid display
- `MarkerDetailsFrequencies`: Frequency information display
- `MarkerDetailsRunways`: Runway cards display
- `MarkerDetailsActionButtons`: Set Route, Edit, Move, Center, Delete buttons
- `MarkerDeleteConfirmationDialog`: Deletion confirmation
- Helper functions for marker data display and database operations

**Responsibilities**:
- Displaying comprehensive marker information
- Single marker navigation ("Set Route" button)
- Quick actions (Edit, Move, Center, Delete)
- Runway and frequency information
- Marker metadata display

**Key Features**:
- **Set Route Button**: Primary navigation action to set a single marker as target
- Integrated edit functionality with full database field support
- Move marker capability for non-static markers
- Center map on marker
- Delete with confirmation

### 3. **MultiWaypointRouteNavigation.kt** (Multi-Point Routes)
**Purpose**: Handles complex routes with multiple waypoints
**Contains**:
- `MultiWaypointRouteViewModel`: Business logic for route creation and optimization
- `RouteTextOverlay`: Map overlay for route labels
- `drawRouteOnMap`: Helper function to visualize routes on map
- `calculateDistance`: Distance calculation between coordinates
- Route optimization algorithms (TSP solver)

**Responsibilities**:
- Creating routes with multiple waypoints
- Route optimization (shortest path calculation)
- Waypoint locking and reordering
- Route candidate generation
- Distance and heading calculations
- Visual route display on map

**Key Features**:
- **Route Optimization**: TSP solver for optimal waypoint ordering
- **Waypoint Locking**: Lock specific waypoints in place during optimization
- **Route Candidates**: Generate multiple route variations with distances
- **Greedy & Exact Algorithms**: Choose algorithm based on waypoint count
- Visual route display with distance/heading labels

### 4. **RouteCreationUI.kt** (UI Components)
**Purpose**: User interface for route creation
**Contains**:
- Type alias: `RouteCreationViewModel = MultiWaypointRouteViewModel`
- `RouteCreationSheet`: Main UI sheet for route creation/editing
- `RouteSegmentInfo`: Distance and heading display between waypoints
- `WaypointItem`: Individual waypoint card with actions
- UI components for waypoint management

**Responsibilities**:
- Route creation UI flow
- Waypoint list display and interaction
- Route name and properties editing
- Location picker for adding waypoints
- Visual feedback during route creation

## Data Flow

```
┌──────────────────────────────────────┐
│     MapViewer (Entry Point)          │
│  - Initializes all ViewModels        │
│  - Manages map state                 │
└────────────┬─────────────────────────┘
             │
             ├─────────────────────────────────────┐
             │                                     │
┌────────────▼──────────────────┐    ┌────────────▼──────────────────┐
│  MarkerRouteManagement.kt     │    │  SingleMarkerNavigation.kt    │
│  (Main Coordinator)            │    │  (Single Marker Ops)          │
│                                │    │                                │
│  • MarkerRouteViewModel        │    │  • MarkerDetailsContent       │
│  • Lists markers & routes      │───▶│  • "Set Route" button         │
│  • Manages visibility          │    │  • Edit/Delete/Center         │
└────────────┬──────────────────┘    └───────────────────────────────┘
             │
             │
┌────────────▼──────────────────┐
│  MultiWaypointRouteNav.kt     │
│  (Multi-Point Routes)          │
│                                │
│  • MultiWaypointRouteViewModel│
│  • Route optimization          │
│  • TSP algorithms              │
│  • Waypoint management         │
└────────────┬──────────────────┘
             │
             │
┌────────────▼──────────────────┐
│     RouteCreationUI.kt        │
│     (UI Components)            │
│                                │
│  • RouteCreationSheet         │
│  • WaypointItem               │
│  • UI controls                │
└───────────────────────────────┘
```

## Navigation Modes

### Mode 1: Single Marker Navigation
**Use Case**: Direct navigation to a specific point
**Flow**:
1. User selects marker from list or map
2. `MarkerDetailsContent` displays in sheet
3. User clicks "Set Route" button
4. Navigation line drawn from player to marker
5. Distance and heading calculated in real-time

**Files Involved**:
- `SingleMarkerNavigation.kt`: Marker details and Set Route button
- `MarkerRouteManagement.kt`: Marker selection and display
- `MapViewer.kt`: Navigation line rendering

### Mode 2: Multi-Waypoint Route Navigation
**Use Case**: Complex routes with multiple stops
**Flow**:
1. User opens Route Creation
2. Selects multiple waypoints
3. Can optimize route order (shortest path)
4. Can lock specific waypoints
5. Saves route to database
6. Route displayed on map with segments

**Files Involved**:
- `MultiWaypointRouteNavigation.kt`: Route logic and optimization
- `RouteCreationUI.kt`: Route creation UI
- `MarkerRouteManagement.kt`: Route list and visibility toggle
- `MapViewer.kt`: Route visualization

## Key Components

### ViewModels

#### MarkerRouteViewModel
```kotlin
class MarkerRouteViewModel(
    locationRepository: LocationRepository,
    routeRepository: RouteRepository
)
```
- Manages marker groups and categories
- Handles route visibility toggling
- Coordinates marker/route CRUD operations

#### MultiWaypointRouteViewModel  
```kotlin
class MultiWaypointRouteViewModel(
    application: Application,
    routeRepository: RouteRepository,
    locationRepository: LocationRepository,
    runwayDao: RunwayDao
)
```
- Waypoint selection and ordering
- Route optimization algorithms
- Route candidate generation
- Distance calculations

### UI Components

#### MarkerDetailsContent
Complete marker information display with:
- Header (name, coordinates)
- Info grid (metadata)
- Frequencies
- Runways
- Action buttons (Set Route, Edit, Move, Center, Delete)

#### RouteCreationSheet
Route building interface with:
- Waypoint list with drag-to-reorder
- Add waypoint from markers or current position
- Optimize button (TSP solver)
- Lock waypoints option
- Route name editing
- Save/Cancel actions

## Database Integration

### Repositories Used
- **LocationRepository**: CRUD operations for markers/locations
- **RouteRepository**: CRUD operations for routes
- **RunwayDao**: Runway data for airports

### Entities
- **LocationEntity**: Marker/waypoint data
- **RouteEntity**: Route metadata
- **RunwayEntity**: Runway information

## Optimization Algorithms

### Traveling Salesman Problem (TSP) Solvers

#### Exact Algorithm (≤10 waypoints)
- Generates all permutations
- Finds absolute shortest route
- Computationally expensive
- Guaranteed optimal solution

#### Greedy/Heuristic Algorithm (>10 waypoints)
- Nearest neighbor heuristic
- Fast approximation
- Good enough solution for large routes
- Scalable to many waypoints

#### Waypoint Locking
- User can lock specific waypoints in place
- Only unlocked waypoints are optimized
- Preserves must-visit-in-order constraints

## Benefits of Refactoring

1. **Separation of Concerns**
   - Single marker operations isolated
   - Multi-waypoint logic separated
   - UI decoupled from business logic

2. **Maintainability**
   - Each file has clear responsibility
   - Easier to locate and fix bugs
   - Clearer code organization

3. **Reusability**
   - Components can be used independently
   - ViewModels can be tested separately
   - UI components are composable

4. **Scalability**
   - Easy to add new navigation modes
   - Can extend route optimization algorithms
   - Clear extension points

## Usage Examples

### Setting a Single Marker Route
```kotlin
// In MapViewer or other screen
MarkerRouteManagementSheet(
    viewModel = markerRouteViewModel,
    onSetActiveRoute = { marker ->
        // Set active navigation target
        activeNavigationTarget = marker
    }
)
```

### Creating a Multi-Waypoint Route
```kotlin
// Open route creation
routeCreationViewModel.startRouteCreation()

// Add waypoints
routeCreationViewModel.addWaypoint(location1)
routeCreationViewModel.addWaypoint(location2)
routeCreationViewModel.addWaypoint(location3)

// Optimize
routeCreationViewModel.optimizeRouteOrder()

// Save
routeCreationViewModel.finishRouteCreation { routeId ->
    // Route saved with id: routeId
}
```

### Displaying a Route on Map
```kotlin
drawRouteOnMap(
    mapView = mapView,
    waypoints = listOf(
        Triple(location1, null, null),
        Triple(location2, distanceNm, headingDeg),
        Triple(location3, distanceNm2, headingDeg2)
    ),
    color = android.graphics.Color.parseColor("#00A8FF")
)
```

## Migration Notes

### For Existing Code
- `RouteCreationViewModel` is now an alias to `MultiWaypointRouteViewModel`
- `MarkerDetailsContent` moved from `MarkerRouteManagement.kt` to `SingleMarkerNavigation.kt`
- All imports should be updated to reference new files
- No API breaking changes - all functions maintain same signatures

### Backwards Compatibility
- Type aliases preserve old naming
- All existing function signatures unchanged
- Database operations remain identical
- UI component interfaces unchanged

## Future Enhancements

1. **Navigation Modes**
   - Add "Follow Route" mode with automatic waypoint progression
   - Voice guidance for waypoint approach
   - Off-route detection and rerouting

2. **Route Sharing**
   - Export routes to file
   - Import routes from other users
   - Cloud sync for routes

3. **Advanced Optimization**
   - Consider terrain and obstacles
   - Fuel-optimal routing
   - Time-based constraints

4. **UI Improvements**
   - Drag-and-drop waypoint reordering on map
   - Visual route editing
   - Split-screen map and route editor

## Testing Considerations

### Unit Tests
- Test route optimization algorithms with various waypoint counts
- Verify distance calculations
- Test waypoint locking logic
- Validate route candidate generation

### Integration Tests  
- Test marker selection → route creation flow
- Verify database persistence
- Test route visibility toggling
- Validate UI state management

### UI Tests
- Test marker details display
- Verify button actions
- Test route creation flow
- Validate waypoint management UI

---

**Last Updated**: December 21, 2025
**Version**: 1.0
**Authors**: Architecture refactoring by GitHub Copilot
