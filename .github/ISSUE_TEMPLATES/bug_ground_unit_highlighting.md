---
name: Bug Report - Ground Unit/Weapon Highlighting
about: Ground unit and weapon highlighting feature is currently broken and requires a fix.
title: "[BUG] Ground Unit & Weapon Highlighting Not Functioning"
labels: 'bug, map, ui'
assignees: ''
---

## Bug Description

The functionality responsible for highlighting ground units and weapons on the map is currently not working as intended. When a user attempts to highlight these entities (e.g., by selection or hovering), the visual feedback (highlight effect) does not appear or is inconsistent.

This issue impacts the user's ability to clearly identify selected or target ground units and weapons, leading to a degraded user experience, especially during mission planning or tactical overview.

## Steps to Reproduce

1.  Open the map view in the application.
2.  Attempt to select or interact with a ground unit or a weapon entity (e.g., an AA system, a tank, or an artillery piece).
3.  Observe that the expected highlighting effect (e.g., a colored border, increased opacity, or a special marker) does not appear or is visually incorrect.

## Expected Behavior

When a ground unit or weapon is selected or interacted with, a clear and consistent visual highlighting effect should be displayed to indicate its active state.

## Relevant Files (Potential Areas for Investigation)

-   `app/src/main/java/com/example/checklist_interactive/ui/maps/marker/MapMarkerPopup.kt`
-   `app/src/main/java/com/example/checklist_interactive/ui/maps/MapViewer.kt`

## Proposed Fix (High-Level)

The highlighting logic within `MapMarkerPopup.kt` and `MapViewer.kt` (or related components) needs to be investigated and debugged. This likely involves checking:
-   Event listeners for unit selection/hover.
-   Drawing logic for highlight effects (e.g., `Paint` properties, `Canvas` operations).
-   Interaction between different map layers or overlays that might interfere with rendering.

A thorough review and correction of the rendering pipeline for ground unit and weapon markers is required.
