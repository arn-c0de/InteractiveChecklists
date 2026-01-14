# AA Range Rings Implementation

This document briefly describes the implementation of the Anti-Aircraft (AA) range rings feature, which provides visual representations of AA system detection and engagement ranges on a map. The feature is primarily implemented through two Kotlin files: `AARangeData.kt` and `AARangeRingsOverlay.kt`.

## `AARangeData.kt`

This file serves as the data model and repository for AA system specifications and their corresponding range ring configurations.

### Key Components:

-   **`AASystemSpec` Data Class**: Defines the detailed attributes of an AA system, including:
    -   `name`, `displayName`
    -   `detectionRangeKm`
    -   `engagementRangeKm` (max) and `minEngagementRangeKm`
    -   `maxAltitudeM` and `minAltitudeM`
    -   `searchRadarRangeKm`, `trackRadarRangeKm`
    -   `category` (e.g., "Long-range", "AAA")
    -   `notes`
-   **`AARangeRing` Data Class**: Specifies the visual properties of a single range ring, such as:
    -   `radiusKm`
    -   `color`
    -   `label`
    -   `strokeWidth`
    -   `isDashed` (boolean)
-   **`AARangeDatabase` Object**: A singleton object that acts as a comprehensive database of various AA systems. It contains:
    -   A predefined list (`systems`) of `AASystemSpec` objects for Russian/Soviet, NATO/Western SAMs, and AAA.
    -   Utility functions to retrieve `AASystemSpec` entries by `name` (`getSystemByName`) or by DCS unit type (`getSystemByUnitType`).
    -   Functions to filter systems by category, list all categories, or retrieve all systems.
    -   A crucial function `getRangeRingsForSystem` that generates a list of `AARangeRing` objects (detection, max engagement, min engagement) for a given `AASystemSpec`, each with specific styling (colors, dashed/solid lines, labels).

## `AARangeRingsOverlay.kt`

This file implements a custom `Overlay` for the `osmdroid` map library, responsible for rendering the AA range rings dynamically on the map.

### Key Functionality:

-   **`AARangeRingsOverlay` Class**: Extends `org.osmdroid.views.overlay.Overlay`. Its constructor takes lambda functions to provide dynamic state (e.g., `isEnabled`, `getAAUnits`, `getFillTransparency`, `getShowAllAARange`, `getUnitRangeVisibility`).
-   **`draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean)` Method**: This is the core rendering logic.
    -   It checks if the overlay is enabled and if there are any AA units to draw.
    -   It iterates through each `TacticalUnitEntity` representing an AA unit.
    -   For each unit, it queries the `AARangeDatabase` (from `AARangeData.kt`) to get its `AASystemSpec`.
    -   It then retrieves the list of `AARangeRing` configurations for that specific system using `AARangeDatabase.getRangeRingsForSystem`.
    -   **Rendering Process**:
        1.  **Phase 1 (Fill Drawing)**: Uses `canvas.saveLayer` and `PorterDuffXfermode` (`DST_OUT`) to draw filled circles, creating a "donut" effect for overlapping rings with appropriate transparency.
        2.  **Phase 2 (Border Drawing)**: Draws the outlines of the rings using `Paint` objects, applying dashed or solid line styles as defined in `AARangeRing`.
        3.  **Phase 3 (Unit Label)**: Draws a central label for the AA unit, displaying its name, engagement range, and max altitude.
        4.  **Phase 4 (Range Labels)**: Draws smaller labels near each ring, indicating its radius (e.g., "75 km").
    -   Includes error handling and logging for robustness.

In essence, `AARangeData.kt` provides the static and dynamic data required for AA systems and their visual representation, while `AARangeRingsOverlay.kt` consumes this data to draw interactive and informative range rings directly onto the map interface.