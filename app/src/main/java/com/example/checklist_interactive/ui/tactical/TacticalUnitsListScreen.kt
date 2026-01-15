@file:OptIn(ExperimentalLayoutApi::class)
package com.example.checklist_interactive.ui.tactical

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.checklist_interactive.R
import com.example.checklist_interactive.ui.datapad.LocalDataPadManager
import com.example.checklist_interactive.data.tactical.TacticalUnitEntity
import com.example.checklist_interactive.data.tactical.TacticalUnitsRepository
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

/**
 * Reusable Tactical Units content (without the overlay wrapper)
 * This can be used both in standalone popup and as a tab in MapObjects sheet
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TacticalUnitsContent(
    viewModel: TacticalUnitsViewModel,
    onUnitClick: ((TacticalUnitEntity) -> Unit)? = null,
    onCenterOnMap: ((latitude: Double, longitude: Double) -> Unit)? = null,
    onShowDetails: ((TacticalUnitEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dataPadManager = LocalDataPadManager.current

    val isEntityTrackingEnabled by dataPadManager.isEntityTrackingEnabled.collectAsState()
    val mapUpdateInterval by dataPadManager.tacticalUnitsMapUpdateInterval.collectAsState()
    val showTacticalUnitsOnMap by dataPadManager.showTacticalUnitsOnMap.collectAsState()
    val tacticalAutoSort by dataPadManager.tacticalUnitsAutoSort.collectAsState()
    val hideTimeoutMinutes by dataPadManager.tacticalUnitsHideTimeoutMinutes.collectAsState()

    val units by viewModel.units.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val stats by viewModel.stats.collectAsState()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCleanupMenu by remember { mutableStateOf(false) }

    // Persisted collapsed/expanded state for settings
    val contextForSettings = LocalContext.current
    val prefsSettings = contextForSettings.getSharedPreferences("tactical_units_prefs", android.content.Context.MODE_PRIVATE)
    val KEY_SETTINGS_EXPANDED = "tactical_settings_expanded"
    var settingsExpanded by rememberSaveable { mutableStateOf(prefsSettings.getBoolean(KEY_SETTINGS_EXPANDED, true)) }

    // Persist when changed
    LaunchedEffect(settingsExpanded) {
        prefsSettings.edit().putBoolean(KEY_SETTINGS_EXPANDED, settingsExpanded).apply()
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Compact header with search and controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search bar (compact)
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp),
                textStyle = MaterialTheme.typography.labelSmall,
                placeholder = { Text("Search", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(14.dp)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Clear, null, modifier = Modifier.size(12.dp))
                        }
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Total units badge (compact) - left of filter button
            TotalBadge(count = stats.totalActive, modifier = Modifier.padding(end = 6.dp))

            // Settings expand/collapse button (more prominent)
            IconButton(
                onClick = { settingsExpanded = !settingsExpanded },
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (settingsExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (settingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (settingsExpanded) "Collapse settings" else "Expand settings",
                    modifier = Modifier.size(20.dp),
                    tint = if (settingsExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Filter button
            IconButton(onClick = { viewModel.toggleFilterDialog() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp))
            }

            // Cleanup menu
            Box {
                IconButton(onClick = { showCleanupMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = showCleanupMenu,
                    onDismissRequest = { showCleanupMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Clear All Highlights", style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            showCleanupMenu = false
                            viewModel.clearAllHighlights()
                        },
                        leadingIcon = { Icon(Icons.Default.HighlightOff, null, modifier = Modifier.size(16.dp)) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tactical_unhide_all_units), style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            showCleanupMenu = false
                            viewModel.unhideAllUnits()
                        },
                        leadingIcon = { Icon(Icons.Default.Visibility, null, modifier = Modifier.size(16.dp)) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tactical_delete_inactive), style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            showCleanupMenu = false
                            viewModel.deleteInactiveUnits()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tactical_delete_old), style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            showCleanupMenu = false
                            viewModel.deleteOldUnits(3600)
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tactical_delete_all), style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            showCleanupMenu = false
                            showDeleteConfirm = true
                        },
                        leadingIcon = { Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }

        // Compact settings grid - collapsible controls
        AnimatedVisibility(
            visible = settingsExpanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CompactSettingsControls(
                isEntityTrackingEnabled = isEntityTrackingEnabled,
                onToggleEntityTracking = { dataPadManager.toggleEntityTracking() },
                showTacticalUnitsOnMap = showTacticalUnitsOnMap,
                onToggleMapVisibility = { dataPadManager.toggleTacticalUnitsOnMap() },
                mapUpdateInterval = mapUpdateInterval,
                onMapUpdateIntervalChange = { dataPadManager.setTacticalUnitsMapUpdateInterval(it) },
                hideTimeoutMinutes = hideTimeoutMinutes,
                onHideTimeoutMinutesChange = { dataPadManager.setTacticalUnitsHideTimeoutMinutes(it) },
                tacticalAutoSort = tacticalAutoSort,
                onToggleAutoSort = { dataPadManager.toggleTacticalUnitsAutoSort() },
                showHiddenUnits = uiState.showHiddenUnits,
                onToggleShowHiddenUnits = { viewModel.toggleShowHiddenUnits() },
                showLiveOnly = uiState.showLiveOnly,
                onToggleLiveOnly = { viewModel.setShowLiveOnly(!uiState.showLiveOnly) }
            )
        }

        // Always-visible stats and filters
        CompactStatsAndFilters(
            stats = stats,
            selectedCoalitions = uiState.selectedCoalitions,
            onToggleCoalition = { viewModel.toggleCoalition(it) },
            selectedCategories = uiState.selectedCategories,
            onToggleCategory = { viewModel.toggleCategory(it) }
        )

        // Units list
        val displayUnits = if (tacticalAutoSort) units else units.sortedBy { it.id }

        if (displayUnits.isEmpty()) {
            EmptyState()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(
                    items = displayUnits,
                    key = { it.id }
                ) { unit ->
                    UnitCard(
                        unit = unit,
                        onClick = { onUnitClick?.invoke(unit) },
                        onCenterOnMap = onCenterOnMap,
                        onShowDetails = onShowDetails,
                        getCoalitionName = { viewModel.getCoalitionName(it) },
                        getCategoryDisplayName = { viewModel.getCategoryDisplayName(it) }
                    )
                }
            }
        }

        // Filter Dialog
        if (uiState.showFilterDialog) {
            FilterDialog(
                uiState = uiState,
                onDismiss = { viewModel.toggleFilterDialog() },
                onToggleCategory = { viewModel.toggleCategory(it) },
                onToggleCoalition = { viewModel.toggleCoalition(it) },
                onToggleActiveOnly = { viewModel.setShowActiveOnly(it) },
                onClearFilters = { viewModel.clearFilters() },
                onClearAllHighlights = { viewModel.clearAllHighlights() }
            )
        }

        // Delete confirmation dialog
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(R.string.tactical_delete_all_confirm_title)) },
                text = { Text(stringResource(R.string.tactical_delete_all_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteAllUnits()
                            showDeleteConfirm = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TacticalUnitsListScreen(
    onNavigateBack: () -> Unit,
    onUnitClick: ((TacticalUnitEntity) -> Unit)? = null,
    onCenterOnMap: ((latitude: Double, longitude: Double) -> Unit)? = null,
    onShowDetails: ((TacticalUnitEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val repository = remember { TacticalUnitsRepository(context) }

    // Use the same DataPadManager instance from CompositionLocal (shared with MapViewer)
    val dataPadManager = LocalDataPadManager.current

    val viewModel: TacticalUnitsViewModel = viewModel(
        factory = TacticalUnitsViewModelFactory(
            context.applicationContext as Application,
            repository,
            dataPadManager
        )
    )

    // Opacity control (persistent)
    val prefs = context.getSharedPreferences("tactical_units_prefs", android.content.Context.MODE_PRIVATE)
    val KEY_OPACITY = "tactical_units_opacity"
    val savedOpacity = prefs.getFloat(KEY_OPACITY, 1.0f)
    var dialogOpacity by rememberSaveable { mutableStateOf(savedOpacity.coerceIn(0.25f, 1.0f)) }
    var showOpacitySlider by remember { mutableStateOf(false) }

    // State variables for dialogs
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCleanupMenu by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()

    // Persist opacity when changed
    LaunchedEffect(dialogOpacity) {
        prefs.edit().putFloat(KEY_OPACITY, dialogOpacity).apply()
    }

    // Box overlay instead of Dialog so map remains visible and active in background
    // Clicking outside the popup closes it
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Background scrim - clicking here closes the popup
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    onNavigateBack() // Close popup when clicking outside
                }
        )

        // Popup content - clicks don't propagate to background
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = dialogOpacity),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                // Header with close button and opacity control
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tactical_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Opacity control button (smaller)
                        IconButton(onClick = { showOpacitySlider = !showOpacitySlider }) {
                            Icon(
                                imageVector = if (showOpacitySlider) Icons.Default.Close else Icons.Default.Visibility,
                                contentDescription = stringResource(R.string.tactical_adjust_opacity),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // More options menu
                        Box {
                            IconButton(onClick = { showCleanupMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.tactical_options), modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(
                                expanded = showCleanupMenu,
                                onDismissRequest = { showCleanupMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Clear All Highlights") },
                                    onClick = {
                                        showCleanupMenu = false
                                        viewModel.clearAllHighlights()
                                    },
                                    leadingIcon = { Icon(Icons.Default.HighlightOff, null) }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.tactical_unhide_all_units)) },
                                    onClick = {
                                        showCleanupMenu = false
                                        viewModel.unhideAllUnits()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Visibility, null) }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.tactical_delete_inactive)) },
                                    onClick = {
                                        showCleanupMenu = false
                                        viewModel.deleteInactiveUnits()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.tactical_delete_old)) },
                                    onClick = {
                                        showCleanupMenu = false
                                        viewModel.deleteOldUnits(3600)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.tactical_delete_all)) },
                                    onClick = {
                                        showCleanupMenu = false
                                        showDeleteConfirm = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.DeleteForever, null) }
                                )
                            }
                        }
                        
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close), modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Opacity slider (animated visibility)
                AnimatedVisibility(
                    visible = showOpacitySlider,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tactical_opacity_label, (dialogOpacity * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = dialogOpacity,
                            onValueChange = { dialogOpacity = it },
                            valueRange = 0.25f..1.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Use the reusable TacticalUnitsContent composable
                TacticalUnitsContent(
                    viewModel = viewModel,
                    onUnitClick = onUnitClick,
                    onCenterOnMap = onCenterOnMap,
                    onShowDetails = onShowDetails,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Filter Dialog (inside the Box)
        if (uiState.showFilterDialog) {
            FilterDialog(
                uiState = uiState,
                onDismiss = { viewModel.toggleFilterDialog() },
                onToggleCategory = { viewModel.toggleCategory(it) },
                onToggleCoalition = { viewModel.toggleCoalition(it) },
                onToggleActiveOnly = { viewModel.setShowActiveOnly(it) },
                onClearFilters = { viewModel.clearFilters() },
                onClearAllHighlights = { viewModel.clearAllHighlights() }
            )
        }

        // Delete confirmation dialog (inside the Box)
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(R.string.tactical_delete_all_confirm_title)) },
                text = { Text(stringResource(R.string.tactical_delete_all_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteAllUnits()
                            showDeleteConfirm = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}

/**
 * Compact settings controls (collapsible)
 */


/**
 * Always-visible stats and filters section
 */






