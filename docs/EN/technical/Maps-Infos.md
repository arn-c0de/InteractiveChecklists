# Map Feature Analysis


## Core Components & Functionality

The map feature is composed of several key UI composables and ViewModels that work together to provide a rich user experience.

### 1. `MapViewer.kt`

This is the central component of the map feature. It is a full-screen composable that hosts the osmdroid `MapView`.

- **Map Rendering:** Uses `osmdroid` to display a base map. The tile source is configurable, with options for standard, topographic, satellite, and dark-themed maps. The selected tile source is persisted in `SharedPreferences`.
- **Live Data Integration:** Connects to a `DataPadManager` to receive live flight data (latitude, longitude, heading, speed, etc.). The player's aircraft is displayed as a rotating marker on the map.
- **Database Integration:** It loads all `LocationEntity` objects (markers) from a `TacticalDatabase` and displays them on the map. It dynamically loads appropriate icons for each marker based on its type (e.g., airport, tactical unit) and affiliation, including color-coding.
- **Custom Overlays:** Implements several custom `osmdroid` overlays to enhance situational awareness:
    - `CompassOverlay`: A compass rose centered on the player's position.
    - `HeadingSpeedLineOverlay`: A line indicating the player's current heading, with its length proportional to the aircraft's speed.
    - `RangeRingsOverlay`: Concentric circles at set nautical mile distances (e.g., 1, 5, 10 NM) for quick distance estimation.
    - `MgrsGridOverlay`: A Military Grid Reference System (MGRS) grid.
    - `RouteTextOverlay`: Displays text labels for heading and distance on route segments.
- **User Interaction:**
    - **Controls:** A set of `FloatingActionButton`s provides controls for centering the map, changing layers, toggling overlays, adding symbols, managing routes, and locking the screen.
    - **Marker Clicks:** Short-clicking a marker opens a details sheet. Long-pressing a marker opens a `RadialMenu` for quick actions.
    - **Auto-Center:** The map can automatically follow the player's aircraft. This feature is disabled when the user manually interacts with (pans or zooms) the map.
- **State Persistence:** The map's last known center, zoom level, enabled overlays, and tile source are saved to `SharedPreferences` and restored when the view is recreated.

### 2. `MarkerRouteManagement.kt`

This file provides a sophisticated `ModalBottomSheet` for all Create, Read, Update, and Delete (CRUD) operations on markers and routes.

- **`MarkerRouteViewModel`:** Manages the state for this feature. It loads all markers and routes from the database, groups markers by type (Airports, BLUFOR Units, etc.), and tracks the visibility of routes on the map.
- **Tabbed UI:** The sheet has a three-tab interface:
    1.  **Details:** Shows a detailed view of a selected marker, including all its database properties, associated runways, and frequencies. From here, a user can edit, delete, move, or set the marker as a navigation target.
    2.  **Markers:** A list of all markers, organized into collapsible groups. From here, users can view all markers or create a new route from an entire group.
    3.  **Routes:** A list of all saved routes. Users can toggle a route's visibility on the map, edit its properties (name, color), edit its waypoints, or delete it.
- **Dialogs:** Contains composables for `LocationEditDialog` (an exhaustive editor for every property of a `LocationEntity`) and `RouteEditDialog`.

### 3. `RouteCreationUI.kt`

This file is dedicated to the user experience of creating and editing a route.

- **`RouteCreationViewModel`:** Manages the state of the route being built. It holds the list of waypoints, the route name, and handles all business logic.
- **Interactive List:** Users build a route by adding waypoints to a list. The UI shows the sequence, with calculated heading and distance for each leg of the journey displayed between waypoints.
- **Waypoint Management:** Waypoints can be added, removed, and manually re-ordered.
- **Route Optimization (TSP):** A key feature is the "Plan Fastest Route" button. This triggers a Traveling Salesman Problem solver in the ViewModel to automatically re-order the waypoints for the shortest possible total distance. The algorithm respects "locked" waypoints, keeping them fixed in the sequence. It uses a brute-force permutation method for small routes and a greedy heuristic for larger ones.
- **Location Picker:** Waypoints can be added from a searchable list of all markers in the database or by creating a new waypoint at the player's current position.

### 4. `MapMarkerPopup.kt`

A specialized `ModalBottomSheet` that displays detailed information for a single selected map marker (`LocationEntity`). Its functionality is largely integrated into the "Details" tab of the `MarkerRouteManagementSheet`, but it can be used independently. It is resizable, has configurable opacity, and aggressively maintains a full-screen immersive mode.

### 5. `MilitarySymbolPicker.kt`

This component provides a `Dialog` for selecting a military symbol.

- **Dynamic Loading:** It cleverly discovers available symbols at runtime by scanning the project's drawable resources for files with the prefix `ic_mapicon_`. This makes adding new symbols as simple as adding a new, correctly named image file.
- **UI:** It allows the user to first select an affiliation (Friendly, Hostile, etc.) which sets the color, then pick a symbol from a categorized, scrollable grid.
- **Interaction:** Once a symbol is selected, the dialog closes, and the `MapViewer` enters a placement mode, waiting for the user to tap the map to place the new marker.

### 6. `RadialMenu.kt`

A clean, reusable composable that displays a circular menu of actions (e.g., "Info", "Edit", "Navigate", "Delete"). It appears with a pleasant animation at the location of a long-press event on the map, providing quick, context-sensitive actions for markers.

### 7. `MapActionBus.kt`

A simple singleton object that acts as an event bus for map-related actions that need to be decoupled from the main composables. Its primary use case is to facilitate moving a marker: an action in one component (e.g., `MarkerDetailsContent`) can set a `pendingMoveMarkerId`, and the `MapViewer` listens to this change to enter a "move mode".

## How It Works: A User Flow Example (Creating a Route)

1.  The user opens the `MapViewer`.
2.  The user opens the `MarkerRouteManagementSheet` and navigates to the "Routes" tab.
3.  They tap "Create New Route," which presents the `RouteCreationSheet`.
4.  Inside the `RouteCreationSheet`, the user taps "Add" to open the location picker dialog.
5.  They search for and select several existing locations (e.g., airports) from the database, which are added to the waypoint list.
6.  The `WaypointItem` for each new waypoint appears in the `LazyColumn`, and the `RouteSegmentInfo` between them shows the heading and distance for each leg.
7.  The user decides the order is inefficient. They tap "Plan Fastest Route." The `RouteCreationViewModel` runs its TSP algorithm, and the waypoints in the list re-order themselves to form the shortest path.
8.  The user names the route and taps "Finish Route." The `RouteCreationViewModel` saves the new `RouteEntity` and its associated `WaypointEntity`s to the database.
9.  The `RouteCreationSheet` closes. The new route is now visible in the `MarkerRouteManagementSheet`, and the user can toggle its visibility on the main map.


---
App Version: v1.0.25
Last Updated: 2026.01.19
---