package com.example.checklist_interactive.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
    screenWidthPx: Int,
    screenHeightPx: Int,
    marginPx: Int = 0,
    modifier: Modifier = Modifier
) {
    // Get the FAB size from preferences
    val fabSizeDp = prefsManager.getFabSizeDp()
    val density = LocalDensity.current
    val fabSizePx = with(density) { fabSizeDp.dp.toPx().toInt() }

    Box(modifier = modifier.fillMaxSize()) {
        fabs.filter { it.visible }.forEach { fabConfig ->
            DraggableFab(
                name = fabConfig.id,
                prefsManager = prefsManager,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                fabSizeDp = fabSizeDp,
                scope = fabConfig.scope,
                defaultX = fabConfig.defaultX,
                defaultY = fabConfig.defaultY,
                visible = fabConfig.visible,
                onClick = fabConfig.onClick,
                containerColor = fabConfig.containerColor,
                contentColor = fabConfig.contentColor,
                marginPx = marginPx,
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
    fun create(
        onCenterOnPosition: () -> Unit,
        onLayerSelection: () -> Unit,
        onOverlaySelection: () -> Unit,
        onAddMilitarySymbol: () -> Unit,
        onMarkerRouteManagement: () -> Unit,
        onLockScreen: () -> Unit,
        onToggleMapRotation: () -> Unit,
        onDrawingTools: () -> Unit,
        onResetFabPositions: () -> Unit,
        onDataPadOpen: () -> Unit,
        onQuickAccessOpen: () -> Unit,
        isConnected: Boolean = false,
        isScreenLocked: Boolean = false,
        mapRotationMode: Int = 0,
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
    ): List<FABConfig> = listOf(
        FABConfig(
            id = "map_center",
            icon = Icons.Default.MyLocation,
            contentDescription = "Center on position",
            onClick = onCenterOnPosition,
            containerColor = if (isConnected) containerColorConnected else containerColorDisconnected,
            defaultX = 0.95f,
            defaultY = 0.05f,
            scope = "map"
        ),
        FABConfig(
            id = "map_layers",
            icon = Icons.Default.Layers,
            contentDescription = "Map layers",
            onClick = onLayerSelection,
            containerColor = containerColorSecondary,
            defaultX = 0.95f,
            defaultY = 0.10f,
            scope = "map"
        ),
        FABConfig(
            id = "map_overlays",
            icon = Icons.Default.Flight,
            contentDescription = "Map overlays",
            onClick = onOverlaySelection,
            containerColor = containerColorSecondary,
            defaultX = 0.95f,
            defaultY = 0.15f,
            scope = "map"
        ),
        FABConfig(
            id = "map_add_symbol",
            icon = Icons.Default.Add,
            contentDescription = "Add Military Symbol",
            onClick = onAddMilitarySymbol,
            containerColor = if (pendingSymbolPlacement != null) containerColorPrimary 
                         else if (repositoriesReady) containerColorTertiary 
                         else containerColorSurface,
            enabled = repositoriesReady,
            defaultX = 0.95f,
            defaultY = 0.20f,
            scope = "map"
        ),
        FABConfig(
            id = "map_marker_route",
            icon = Icons.Default.List,
            contentDescription = "Markers & Routes",
            onClick = onMarkerRouteManagement,
            containerColor = if (repositoriesReady) containerColorTertiary else containerColorSurface,
            enabled = repositoriesReady,
            defaultX = 0.95f,
            defaultY = 0.25f,
            scope = "map"
        ),
        FABConfig(
            id = "map_screen_lock",
            icon = if (isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = if (isScreenLocked) "Unlock screen" else "Lock screen",
            onClick = onLockScreen,
            containerColor = if (isScreenLocked) containerColorPrimary else containerColorSurface,
            defaultX = 0.95f,
            defaultY = 0.30f,
            scope = "map"
        ),
        FABConfig(
            id = "map_rotate",
            icon = if (mapRotationMode == 1) Icons.Default.Flight else Icons.Default.Explore,
            contentDescription = "Toggle map rotation",
            onClick = onToggleMapRotation,
            containerColor = if (mapRotationMode == 1) containerColorPrimary else containerColorSurface,
            defaultX = 0.95f,
            defaultY = 0.35f,
            scope = "map"
        ),
        FABConfig(
            id = "map_drawing",
            icon = Icons.Default.Edit,
            contentDescription = "Drawing Tools",
            onClick = onDrawingTools,
            containerColor = if (isDrawingMode) containerColorPrimary else containerColorTertiary,
            defaultX = 0.95f,
            defaultY = 0.40f,
            scope = "map"
        ),
        FABConfig(
            id = "datapad",
            icon = Icons.Default.Flight,
            contentDescription = "DataPad",
            onClick = onDataPadOpen,
            visible = datapadEnabled,
            containerColor = containerColorTertiary,
            defaultX = 0.95f,
            defaultY = 0.80f,
            scope = "map"
        ),
        FABConfig(
            id = "quick_access",
            icon = Icons.AutoMirrored.Filled.NoteAdd,
            contentDescription = "Quick Access",
            onClick = onQuickAccessOpen,
            containerColor = containerColorPrimary,
            defaultX = 0.95f,
            defaultY = 0.85f,
            scope = "map"
        )
    )
}

/**
 * Standard FAB configurations for Quick Tab Switcher
 */
object QuickTabSwitcherFABs {
    fun create(
        onQuickTabSwitch: () -> Unit,
        containerColor: Color
    ): List<FABConfig> = listOf(
        FABConfig(
            id = "quick_tab_switch",
            icon = Icons.Default.History,
            contentDescription = "Quick tab switch",
            onClick = onQuickTabSwitch,
            containerColor = containerColor,
            defaultX = 0.9f,
            defaultY = 0.85f
        )
    )
}

/**
 * Standard FAB configurations for Menu/Navigation
 */
object MenuFABs {
    fun create(
        onMenuOpen: () -> Unit,
        containerColor: Color
    ): List<FABConfig> = listOf(
        FABConfig(
            id = "menu",
            icon = Icons.Default.Menu,
            contentDescription = "Open menu",
            onClick = onMenuOpen,
            containerColor = containerColor,
            defaultX = 0.05f,
            defaultY = 0.1f
        )
    )
}

/**
 * Standard FAB configurations for Quick Access/Notes
 */
object QuickAccessFABs {
    fun create(
        onQuickAccessOpen: () -> Unit,
        containerColor: Color
    ): List<FABConfig> = listOf(
        FABConfig(
            id = "quick_access",
            icon = Icons.Default.Note,
            contentDescription = "Quick access",
            onClick = onQuickAccessOpen,
            containerColor = containerColor,
            defaultX = 0.9f,
            defaultY = 0.9f
        )
    )
}

/**
 * Standard FAB configurations for DataPad
 */
object DataPadFABs {
    fun create(
        onDataPadOpen: () -> Unit,
        containerColor: Color,
        visible: Boolean = true
    ): List<FABConfig> = listOf(
        FABConfig(
            id = "datapad",
            icon = Icons.Default.Tablet,
            contentDescription = "DataPad",
            onClick = onDataPadOpen,
            containerColor = containerColor,
            visible = visible,
            defaultX = 0.05f,
            defaultY = 0.9f
        )
    )
}

/**
 * Standard FAB configurations for PDF Viewer
 */
object PdfViewerFABs {
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
                contentDescription = "Reset Zoom",
                onClick = onZoomReset,
                visible = zoomResetVisible,
                defaultX = 0.95f,
                defaultY = 0.05f
            )
        )

        // Menu FAB
        if (onMenuOpen != null) {
            add(
                FABConfig(
                    id = "menu",
                    icon = Icons.Default.Menu,
                    contentDescription = "File List",
                    onClick = onMenuOpen,
                    defaultX = 0.95f,
                    defaultY = 0.10f
                )
            )
        }

        // DataPad FAB
        add(
            FABConfig(
                id = "datapad",
                icon = Icons.Default.Flight,
                contentDescription = "DataPad",
                onClick = onDataPadOpen,
                visible = datapadEnabled,
                containerColor = containerColorTertiary,
                defaultX = 0.95f,
                defaultY = 0.80f,
                scope = "pdf"
            )
        )

        // Quick Access FAB
        add(
            FABConfig(
                id = "quick_access",
                icon = Icons.AutoMirrored.Filled.NoteAdd,
                contentDescription = "Quick Access",
                onClick = onQuickAccessOpen,
                containerColor = containerColorPrimary,
                defaultX = 0.95f,
                defaultY = 0.85f,
                scope = "pdf"
            )
        )
    }
}

/**
 * Standard FAB configurations for Markdown Viewer
 */
object MarkdownViewerFABs {
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
                    contentDescription = "Menu",
                    onClick = onMenuOpen,
                    defaultX = 0.95f,
                    defaultY = 0.05f
                )
            )
        }

        // DataPad FAB
        add(
            FABConfig(
                id = "datapad",
                icon = Icons.Default.Flight,
                contentDescription = "DataPad",
                onClick = onDataPadOpen,
                visible = datapadEnabled,
                containerColor = containerColorTertiary,
                defaultX = 0.95f,
                defaultY = 0.80f,
                scope = "markdown"
            )
        )

        // Quick Access FAB
        add(
            FABConfig(
                id = "quick_access",
                icon = Icons.AutoMirrored.Filled.NoteAdd,
                contentDescription = "Quick Access",
                onClick = onQuickAccessOpen,
                containerColor = containerColorPrimary,
                defaultX = 0.95f,
                defaultY = 0.85f,
                scope = "markdown"
            )
        )
    }
}

/**
 * Standard FAB configurations for Internal File Viewer
 */
object InternalFileViewerFABs {
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
            contentDescription = "File List",
            onClick = onMenuOpen,
            defaultX = 0.95f,
            defaultY = 0.05f
        ),
        FABConfig(
            id = "datapad",
            icon = Icons.Default.Flight,
            contentDescription = "DataPad",
            onClick = onDataPadOpen,
            visible = datapadEnabled,
            containerColor = containerColorTertiary,
            defaultX = 0.95f,
            defaultY = 0.80f,
            scope = "internal_file_viewer"
        ),
        FABConfig(
            id = "quick_access",
            icon = Icons.AutoMirrored.Filled.NoteAdd,
            contentDescription = "Quick Access",
            onClick = onQuickAccessOpen,
            containerColor = containerColorPrimary,
            defaultX = 0.95f,
            defaultY = 0.85f
        )
    )
}

/**
 * Standard FAB configurations for Internal Files Screen (browser)
 */
object InternalFilesScreenFABs {
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
            contentDescription = "DataPad",
            onClick = onDataPadOpen,
            visible = datapadEnabled,
            containerColor = containerColorTertiary,
            defaultX = 0.95f,
            defaultY = 0.80f,
            scope = "internal_files"
        ),
        FABConfig(
            id = "quick_access",
            icon = Icons.AutoMirrored.Filled.NoteAdd,
            contentDescription = "Quick Access",
            onClick = onQuickAccessOpen,
            containerColor = containerColorPrimary,
            defaultX = 0.95f,
            defaultY = 0.85f,
            scope = "internal_files"
        )
    )
}
