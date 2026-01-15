package com.example.checklist_interactive.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import android.content.res.Configuration
import com.example.checklist_interactive.R
import com.example.checklist_interactive.data.prefs.PreferencesManager

/**
 * FAB configuration data class for centralized button management
 */
data class FABConfig(
    val id: String,
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val visible: Boolean = true,
    val containerColor: Color? = null,
    val contentColor: Color? = null,
    val defaultX: Float = 1.0f,
    val defaultY: Float = 0.9f,
    val defaultLandscapeX: Float? = null, // Optional landscape position
    val defaultLandscapeY: Float? = null, // Optional landscape position
    val enabled: Boolean = true,
    // Optional namespace to keep positions per-screen (e.g., "map", "pdf", "markdown")
    val scope: String = ""
)

/**
 * Central FAB Overlay component that renders all FAB buttons for a screen
 * This provides a single place to manage all floating action buttons across the app
 * 
 * @param prefsManager PreferencesManager for loading FAB size and positions
 * @param fabs List of FAB configurations to display
 * @param screenWidthPx Screen width in pixels
 * @param screenHeightPx Screen height in pixels
 * @param marginPx Horizontal margin on both sides
 */
@Composable
fun FABOverlay(
    prefsManager: PreferencesManager,
    fabs: List<FABConfig>,
    marginPx: Int = 0,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Use BoxWithConstraints to get the true available size at layout time,
    // which is more robust against configuration change race conditions.
    BoxWithConstraints(modifier = modifier
        .fillMaxSize()
        .zIndex(100f) // Ensure FABs are always on top
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx().toInt() }
        val screenHeightPx = with(density) { maxHeight.toPx().toInt() }

        // Get the FAB size from preferences
        val fabSizeDp = prefsManager.getFabSizeDp()

        // Detect orientation from actual screen dimensions (more reliable)
        val isLandscape = screenWidthPx > screenHeightPx

        // Track all FAB positions for collision detection
        val fabPositions = remember { mutableStateMapOf<String, Pair<Float, Float>>() }

        // Log orientation change
        LaunchedEffect(screenWidthPx, screenHeightPx, isLandscape) {
            android.util.Log.d("FABOverlay", "=== RECOMPOSITION === isLandscape=$isLandscape, screenWidth=$screenWidthPx, screenHeight=$screenHeightPx, fabCount=${fabs.size}")
        }

        fabs.filter { it.visible }.forEach { fabConfig ->
            // Use landscape positions if available and device is in landscape
            val effectiveDefaultX = if (isLandscape && fabConfig.defaultLandscapeX != null) {
                fabConfig.defaultLandscapeX
            } else {
                fabConfig.defaultX
            }
            val effectiveDefaultY = if (isLandscape && fabConfig.defaultLandscapeY != null) {
                fabConfig.defaultLandscapeY
            } else {
                fabConfig.defaultY
            }

            DraggableFab(
                name = fabConfig.id,
                prefsManager = prefsManager,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                fabSizeDp = fabSizeDp,
                scope = fabConfig.scope,
                defaultX = effectiveDefaultX,
                defaultY = effectiveDefaultY,
                isLandscape = isLandscape,
                visible = fabConfig.visible,
                onClick = fabConfig.onClick,
                containerColor = fabConfig.containerColor,
                contentColor = fabConfig.contentColor,
                marginPx = marginPx,
                allFabPositions = fabPositions,
                onPositionChanged = { x, y ->
                    fabPositions[fabConfig.id] = Pair(x, y)
                },
                content = {
                    Icon(
                        imageVector = fabConfig.icon,
                        contentDescription = fabConfig.contentDescription,
                        tint = if (fabConfig.enabled) {
                            LocalContentColor.current
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        }
                    )
                }
            )
        }
    }
}

/**
 * Standard FAB configurations for Map Viewer
 */
object MapViewerFABs {
    @Composable
    fun create(
        onCenterOnPosition: () -> Unit,
        onLayerSelection: () -> Unit,
        onOverlaySelection: () -> Unit,
        onAddMilitarySymbol: () -> Unit,
        onMarkerRouteManagement: () -> Unit,
        onLockScreen: () -> Unit,
        onToggleMapRotation: () -> Unit,
        onToggleRotationGesture: () -> Unit,
        onDrawingTools: () -> Unit,
        onResetFabPositions: () -> Unit,
        onDataPadOpen: () -> Unit,
        onQuickAccessOpen: () -> Unit,
        onTacticalUnitsOpen: () -> Unit = {},
        isConnected: Boolean = false,
        isScreenLocked: Boolean = false,
        mapRotationMode: Int = 0,
        rotationGestureEnabled: Boolean = true,
        isDrawingMode: Boolean = false,
        repositoriesReady: Boolean = false,
        pendingSymbolPlacement: Any? = null,
        datapadEnabled: Boolean = false,
        containerColorConnected: Color,
        containerColorDisconnected: Color,
        containerColorSecondary: Color,
        containerColorTertiary: Color,
        containerColorPrimary: Color,
        containerColorSurface: Color
    ): List<FABConfig> {
        // Use responsive utilities instead of raw dp checks
        val windowSize = rememberWindowSize()
        val isTablet = windowSize.isTablet

        // On smartphones, position DataPad and QuickAccess FABs higher to avoid flight instruments
        val datapadY = if (isTablet) 0.80f else 0.55f
        val quickAccessY = if (isTablet) 0.85f else 0.60f

        return listOf(
        FABConfig(
            id = "map_center",
            icon = Icons.Default.MyLocation,
            contentDescription = stringResource(R.string.fab_cd_center_on_position),
            onClick = onCenterOnPosition,
            containerColor = if (isConnected) containerColorConnected else containerColorDisconnected,
            defaultX = 0.97f,
            defaultY = 0.05f,
            defaultLandscapeX = 0.97f,
            defaultLandscapeY = 0.05f,
            scope = "map"
        ),
        FABConfig(
            id = "map_layers",
            icon = Icons.Default.Layers,
            contentDescription = stringResource(R.string.map_layers),
            onClick = onLayerSelection,
            containerColor = containerColorSecondary,
            defaultX = 0.95f,
            defaultY = 0.10f,
            defaultLandscapeX = 0.95f,
            defaultLandscapeY = 0.15f,
            scope = "map"
        ),
        FABConfig(
            id = "map_overlays",
            icon = Icons.Default.Flight,
            contentDescription = stringResource(R.string.map_overlays),
            onClick = onOverlaySelection,
            containerColor = containerColorSecondary,
            defaultX = 0.95f,
            defaultY = 0.15f,
            defaultLandscapeX = 0.95f,
            defaultLandscapeY = 0.25f,
            scope = "map"
        ),
        FABConfig(
            id = "tactical_units",
            icon = Icons.Default.TrackChanges,
            contentDescription = "Tactical Units",
            onClick = onTacticalUnitsOpen,
            visible = datapadEnabled,
            containerColor = if (isConnected) containerColorConnected else containerColorTertiary,
            defaultX = 0.95f,
            defaultY = 0.20f,
            defaultLandscapeX = 0.95f,
            defaultLandscapeY = 0.35f,
            scope = "map"
        ),
        FABConfig(
            id = "map_add_symbol",
            icon = Icons.Default.Add,
            contentDescription = stringResource(R.string.fab_cd_add_military_symbol),
            onClick = onAddMilitarySymbol,
            containerColor = if (pendingSymbolPlacement != null) containerColorPrimary
                         else if (repositoriesReady) containerColorTertiary
                         else containerColorSurface,
            enabled = repositoriesReady,
            defaultX = 0.95f,
            defaultY = 0.25f,
            defaultLandscapeX = 0.95f,
            defaultLandscapeY = 0.45f,
            scope = "map"
        ),
        FABConfig(
            id = "map_marker_route",
            icon = Icons.AutoMirrored.Filled.List,
            contentDescription = stringResource(R.string.fab_cd_markers_routes),
            onClick = onMarkerRouteManagement,
            containerColor = if (repositoriesReady) containerColorTertiary else containerColorSurface,
            enabled = repositoriesReady,
            defaultX = 0.95f,
            defaultY = 0.30f,
            defaultLandscapeX = 0.95f,
            defaultLandscapeY = 0.55f,
            scope = "map"
        ),
        FABConfig(
            id = "map_screen_lock",
            icon = if (isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = if (isScreenLocked) stringResource(R.string.cd_unlock_screen) else stringResource(R.string.cd_lock_screen),
            onClick = onLockScreen,
            containerColor = if (isScreenLocked) containerColorPrimary else containerColorSurface,
            defaultX = 0.95f,
            defaultY = 0.35f,
            defaultLandscapeX = 0.95f,
            defaultLandscapeY = 0.65f,
            scope = "map"
        ),
        FABConfig(
            id = "map_rotate",
            icon = if (mapRotationMode == 1) Icons.Default.Flight else Icons.Default.Explore,
            contentDescription = stringResource(R.string.fab_cd_toggle_map_rotation),
            onClick = onToggleMapRotation,
            containerColor = if (mapRotationMode == 1) containerColorPrimary else containerColorSurface,
            defaultX = 0.95f,
            defaultY = 0.40f,
            defaultLandscapeX = 0.95f,
            defaultLandscapeY = 0.75f,
            scope = "map"
        ),
        FABConfig(
            id = "map_rotation_gesture",
            icon = Icons.AutoMirrored.Filled.RotateRight,
            contentDescription = stringResource(R.string.fab_cd_toggle_rotation_gesture),
            onClick = onToggleRotationGesture,
            containerColor = if (rotationGestureEnabled) containerColorPrimary else containerColorSurface,
            defaultX = 0.95f,
            defaultY = 0.45f,
            defaultLandscapeX = 0.92f,
            defaultLandscapeY = 0.60f,
            scope = "map"
        ),
        FABConfig(
            id = "map_drawing",
            icon = Icons.Default.Edit,
            contentDescription = stringResource(R.string.map_drawing_tools_title),
            onClick = onDrawingTools,
            containerColor = if (isDrawingMode) containerColorPrimary else containerColorTertiary,
            defaultX = 0.95f,
            defaultY = 0.50f,
            defaultLandscapeX = 0.92f,
            defaultLandscapeY = 0.70f,
            scope = "map"
        ),
        FABConfig(
            id = "datapad",
            icon = Icons.Default.Flight,
            contentDescription = stringResource(R.string.datapad_title),
            onClick = onDataPadOpen,
            visible = datapadEnabled,
            containerColor = containerColorTertiary,
            defaultX = 0.95f,
            defaultY = datapadY,
            defaultLandscapeX = 0.95f,
            defaultLandscapeY = 0.05f,
            scope = "map"
        ),
        FABConfig(
            id = "quick_access",
            icon = Icons.AutoMirrored.Filled.NoteAdd,
            contentDescription = stringResource(R.string.quick_access_title),
            onClick = onQuickAccessOpen,
            containerColor = containerColorPrimary,
            defaultX = 0.95f,
            defaultY = quickAccessY,
            defaultLandscapeX = 0.92f,
            defaultLandscapeY = 0.80f,
            scope = "map"
        )
        )
    }
}

/**
 * Standard FAB configurations for Quick Tab Switcher
 */
object QuickTabSwitcherFABs {
    @Composable
    fun create(
        onQuickTabSwitch: () -> Unit,
        containerColor: Color
    ): List<FABConfig> = listOf(
        FABConfig(
            id = "quick_tab_switch",
            icon = Icons.Default.History,
            contentDescription = stringResource(R.string.fab_cd_quick_tab_switch),
            onClick = onQuickTabSwitch,
            containerColor = containerColor,
            defaultX = 0.9f,
            defaultY = 0.85f,
            defaultLandscapeX = 0.95f,
            defaultLandscapeY = 0.90f
        )
    )
}

/**
 * Standard FAB configurations for Menu/Navigation
 */
object MenuFABs {
    @Composable
    fun create(
        onMenuOpen: () -> Unit,
        containerColor: Color
    ): List<FABConfig> = listOf(
        FABConfig(
            id = "menu",
            icon = Icons.Default.Menu,
            contentDescription = stringResource(R.string.fab_cd_open_menu),
            onClick = onMenuOpen,
            containerColor = containerColor,
            defaultX = 0.05f,
            defaultY = 0.1f,
            defaultLandscapeX = 0.03f,
            defaultLandscapeY = 0.05f
        )
    )
}

/**
 * Standard FAB configurations for Quick Access/Notes
 */
object QuickAccessFABs {
    @Composable
    fun create(
        onQuickAccessOpen: () -> Unit,
        containerColor: Color
    ): List<FABConfig> = listOf(
        FABConfig(
            id = "quick_access",
            icon = Icons.AutoMirrored.Filled.Note,
            contentDescription = stringResource(R.string.quick_access_title),
            onClick = onQuickAccessOpen,
            containerColor = containerColor,
            defaultX = 0.9f,
            defaultY = 0.9f,
            defaultLandscapeX = 0.95f,
            defaultLandscapeY = 0.92f
        )
    )
}

/**
 * Standard FAB configurations for DataPad
 */
object DataPadFABs {
    @Composable
    fun create(
        onDataPadOpen: () -> Unit,
        containerColor: Color,
        visible: Boolean = true
    ): List<FABConfig> = listOf(
        FABConfig(
            id = "datapad",
            icon = Icons.Default.Tablet,
            contentDescription = stringResource(R.string.datapad_title),
            onClick = onDataPadOpen,
            containerColor = containerColor,
            visible = visible,
            defaultX = 0.05f,
            defaultY = 0.9f,
            defaultLandscapeX = 0.03f,
            defaultLandscapeY = 0.90f
        )
    )
}

/**
 * Standard FAB configurations for PDF Viewer
 */
object PdfViewerFABs {
    @Composable
    fun create(
        onZoomReset: () -> Unit,
        onMenuOpen: (() -> Unit)?,
        onDataPadOpen: () -> Unit,
        onQuickAccessOpen: () -> Unit,
        zoomResetVisible: Boolean,
        datapadEnabled: Boolean,
        containerColorPrimary: Color,
        containerColorTertiary: Color
    ): List<FABConfig> = buildList {
        // Zoom Reset FAB (conditionally visible)
        add(
            FABConfig(
                id = "zoom_reset",
                icon = Icons.Default.CenterFocusWeak,
                contentDescription = stringResource(R.string.fab_cd_reset_zoom),
                onClick = onZoomReset,
                visible = zoomResetVisible,
                defaultX = 0.95f,
                defaultY = 0.05f,
                defaultLandscapeX = 0.95f,
                defaultLandscapeY = 0.05f
            )
        )

        // Menu FAB
        if (onMenuOpen != null) {
            add(
                FABConfig(
                    id = "menu",
                    icon = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.fab_cd_file_list),
                    onClick = onMenuOpen,
                    defaultX = 0.95f,
                    defaultY = 0.10f,
                    defaultLandscapeX = 0.95f,
                    defaultLandscapeY = 0.15f
                )
            )
        }

        // DataPad FAB
        add(
            FABConfig(
                id = "datapad",
                icon = Icons.Default.Flight,
                contentDescription = stringResource(R.string.datapad_title),
                onClick = onDataPadOpen,
                visible = datapadEnabled,
                containerColor = containerColorTertiary,
                defaultX = 0.95f,
                defaultY = 0.80f,
                defaultLandscapeX = 0.95f,
                defaultLandscapeY = 0.80f,
                scope = "pdf"
            )
        )

        // Quick Access FAB
        add(
            FABConfig(
                id = "quick_access",
                icon = Icons.AutoMirrored.Filled.NoteAdd,
                contentDescription = stringResource(R.string.quick_access_title),
                onClick = onQuickAccessOpen,
                containerColor = containerColorPrimary,
                defaultX = 0.95f,
                defaultY = 0.85f,
                defaultLandscapeX = 0.95f,
                defaultLandscapeY = 0.90f,
                scope = "pdf"
            )
        )
    }
}

/**
 * Standard FAB configurations for Markdown Viewer
 */
object MarkdownViewerFABs {
    @Composable
    fun create(
        onMenuOpen: (() -> Unit)?,
        onDataPadOpen: () -> Unit,
        onQuickAccessOpen: () -> Unit,
        datapadEnabled: Boolean,
        containerColorPrimary: Color,
        containerColorTertiary: Color
    ): List<FABConfig> = buildList {
        // Menu FAB
        if (onMenuOpen != null) {
            add(
                FABConfig(
                    id = "menu",
                    icon = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.fab_cd_menu),
                    onClick = onMenuOpen,
                    defaultX = 0.95f,
                    defaultY = 0.05f,
                    defaultLandscapeX = 0.95f,
                    defaultLandscapeY = 0.05f
                )
            )
        }

        // DataPad FAB
        add(
            FABConfig(
                id = "datapad",
                icon = Icons.Default.Flight,
                contentDescription = stringResource(R.string.datapad_title),
                onClick = onDataPadOpen,
                visible = datapadEnabled,
                containerColor = containerColorTertiary,
                defaultX = 0.95f,
                defaultY = 0.80f,
                defaultLandscapeX = 0.95f,
                defaultLandscapeY = 0.80f,
                scope = "markdown"
            )
        )

        // Quick Access FAB
        add(
            FABConfig(
                id = "quick_access",
                icon = Icons.AutoMirrored.Filled.NoteAdd,
                contentDescription = stringResource(R.string.quick_access_title),
                onClick = onQuickAccessOpen,
                containerColor = containerColorPrimary,
                defaultX = 0.95f,
                defaultY = 0.85f,
                defaultLandscapeX = 0.95f,
                defaultLandscapeY = 0.90f,
                scope = "markdown"
            )
        )
    }
}

/**
 * Standard FAB configurations for Internal File Viewer
 */
object InternalFileViewerFABs {
    @Composable
    fun create(
        onMenuOpen: () -> Unit,
        onDataPadOpen: () -> Unit,
        onQuickAccessOpen: () -> Unit,
        datapadEnabled: Boolean,
        containerColorPrimary: Color,
        containerColorTertiary: Color
    ): List<FABConfig> = listOf(
        FABConfig(
            id = "menu",
            icon = Icons.Default.Menu,
            contentDescription = stringResource(R.string.fab_cd_file_list),
            onClick = onMenuOpen,
            defaultX = 0.95f,
            defaultY = 0.05f,
            defaultLandscapeX = 0.95f,
            defaultLandscapeY = 0.05f
        ),
        FABConfig(
            id = "datapad",
            icon = Icons.Default.Flight,
            contentDescription = stringResource(R.string.datapad_title),
            onClick = onDataPadOpen,
            visible = datapadEnabled,
            containerColor = containerColorTertiary,
            defaultX = 0.95f,
            defaultY = 0.80f,
            defaultLandscapeX = 0.95f,
            defaultLandscapeY = 0.80f,
            scope = "internal_file_viewer"
        ),
        FABConfig(
            id = "quick_access",
            icon = Icons.AutoMirrored.Filled.NoteAdd,
            contentDescription = stringResource(R.string.quick_access_title),
            onClick = onQuickAccessOpen,
            containerColor = containerColorPrimary,
            defaultX = 0.95f,
            defaultY = 0.85f,
            defaultLandscapeX = 0.95f,
            defaultLandscapeY = 0.90f
        )
    )
}

/**
 * Standard FAB configurations for Internal Files Screen (browser)
 */
object InternalFilesScreenFABs {
    @Composable
    fun create(
        onDataPadOpen: () -> Unit,
        onQuickAccessOpen: () -> Unit,
        datapadEnabled: Boolean,
        containerColorPrimary: Color,
        containerColorTertiary: Color
    ): List<FABConfig> = listOf(
        FABConfig(
            id = "datapad",
            icon = Icons.Default.Flight,
            contentDescription = stringResource(R.string.datapad_title),
            onClick = onDataPadOpen,
            visible = datapadEnabled,
            containerColor = containerColorTertiary,
            defaultX = 0.95f,
            defaultY = 0.80f,
            defaultLandscapeX = 0.95f,
            defaultLandscapeY = 0.80f,
            scope = "internal_files"
        ),
        FABConfig(
            id = "quick_access",
            icon = Icons.AutoMirrored.Filled.NoteAdd,
            contentDescription = stringResource(R.string.quick_access_title),
            onClick = onQuickAccessOpen,
            containerColor = containerColorPrimary,
            defaultX = 0.95f,
            defaultY = 0.85f,
            defaultLandscapeX = 0.95f,
            defaultLandscapeY = 0.90f,
            scope = "internal_files"
        )
    )
}
