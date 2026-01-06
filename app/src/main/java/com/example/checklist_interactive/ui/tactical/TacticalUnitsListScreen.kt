@file:OptIn(ExperimentalLayoutApi::class)
package com.example.checklist_interactive.ui.tactical

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.checklist_interactive.R
import com.example.checklist_interactive.data.datapad.DataPadManager
import com.example.checklist_interactive.ui.datapad.LocalDataPadManager
import com.example.checklist_interactive.data.tactical.TacticalUnitEntity
import com.example.checklist_interactive.data.tactical.TacticalUnitsRepository
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.text.KeyboardOptions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                onClearFilters = { viewModel.clearFilters() }
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
                onClearFilters = { viewModel.clearFilters() }
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactSettingsControls(
    isEntityTrackingEnabled: Boolean,
    onToggleEntityTracking: () -> Unit,
    showTacticalUnitsOnMap: Boolean,
    onToggleMapVisibility: () -> Unit,
    mapUpdateInterval: Float,
    onMapUpdateIntervalChange: (Float) -> Unit,
    hideTimeoutMinutes: Float,
    onHideTimeoutMinutesChange: (Float) -> Unit,
    tacticalAutoSort: Boolean,
    onToggleAutoSort: () -> Unit,
    showHiddenUnits: Boolean,
    onToggleShowHiddenUnits: () -> Unit,
    showLiveOnly: Boolean,
    onToggleLiveOnly: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            // Main toggles in a grid (2 columns)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                maxItemsInEachRow = 2
            ) {
                CompactToggleChip(
                    label = "Track",
                    icon = if (isEntityTrackingEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    checked = isEntityTrackingEnabled,
                    onClick = onToggleEntityTracking,
                    modifier = Modifier.weight(1f)
                )
                CompactToggleChip(
                    label = "Map",
                    icon = if (showTacticalUnitsOnMap) Icons.Default.Map else Icons.Default.LocationOff,
                    checked = showTacticalUnitsOnMap,
                    onClick = onToggleMapVisibility,
                    modifier = Modifier.weight(1f)
                )
                CompactToggleChip(
                    label = "Live",
                    icon = Icons.Default.FilterList,
                    checked = showLiveOnly,
                    onClick = onToggleLiveOnly,
                    modifier = Modifier.weight(1f)
                )
                CompactToggleChip(
                    label = "Sort",
                    icon = if (tacticalAutoSort) Icons.Default.Sort else Icons.Default.SortByAlpha,
                    checked = tacticalAutoSort,
                    onClick = onToggleAutoSort,
                    modifier = Modifier.weight(1f)
                )
                CompactToggleChip(
                    label = "Hidden",
                    icon = if (showHiddenUnits) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    checked = showHiddenUnits,
                    onClick = onToggleShowHiddenUnits,
                    modifier = Modifier.weight(1f)
                )
            }

            // Map update interval (only if map is visible)
            if (showTacticalUnitsOnMap) {
                var showIntervalDialog by remember { mutableStateOf(false) }
                
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tactical_interval),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = String.format("%.1fs", mapUpdateInterval),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { showIntervalDialog = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                
                if (showIntervalDialog) {
                    var inputText by remember { mutableStateOf(String.format("%.1f", mapUpdateInterval)) }
                    AlertDialog(
                        onDismissRequest = { showIntervalDialog = false },
                        title = { Text(stringResource(R.string.tactical_interval)) },
                        text = {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                label = { Text("Seconds (0.5-10.0)") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    inputText.toFloatOrNull()?.let { value ->
                                        val clamped = value.coerceIn(0.5f, 10.0f)
                                        onMapUpdateIntervalChange(clamped)
                                    }
                                    showIntervalDialog = false
                                }
                            ) {
                                Text(stringResource(R.string.action_ok))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showIntervalDialog = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    )
                }
                Slider(
                    value = mapUpdateInterval,
                    onValueChange = onMapUpdateIntervalChange,
                    valueRange = 0.5f..10.0f,
                    steps = 18,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )
            }
            
            // Hide timeout slider
            var showHideTimeoutDialog by remember { mutableStateOf(false) }
            
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tactical_hide_after),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = stringResource(R.string.tactical_hide_after_format, hideTimeoutMinutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { showHideTimeoutDialog = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            
            if (showHideTimeoutDialog) {
                var inputText by remember { mutableStateOf(String.format("%.0f", hideTimeoutMinutes)) }
                AlertDialog(
                    onDismissRequest = { showHideTimeoutDialog = false },
                    title = { Text(stringResource(R.string.tactical_hide_after)) },
                    text = {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            label = { Text("Minutes (1-120)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                inputText.toFloatOrNull()?.let { value ->
                                    val clamped = value.coerceIn(1.0f, 120.0f)
                                    onHideTimeoutMinutesChange(clamped)
                                }
                                showHideTimeoutDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.action_ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showHideTimeoutDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
            Slider(
                value = hideTimeoutMinutes,
                onValueChange = onHideTimeoutMinutesChange,
                valueRange = 1.0f..120.0f,
                steps = 118,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )
        }
    }
}

/**
 * Always-visible stats and filters section
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactStatsAndFilters(
    stats: UnitStatistics,
    selectedCoalitions: Set<Int>,
    onToggleCoalition: (Int) -> Unit,
    selectedCategories: Set<String>,
    onToggleCategory: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            // Coalition badges (compact, 3 in a row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CoalitionBadge(
                    name = "N",
                    count = stats.neutralCount,
                    color = Color(0xFF999999),
                    coalitionId = 0,
                    isSelected = selectedCoalitions.contains(0),
                    onClick = { onToggleCoalition(0) }
                )
                CoalitionBadge(
                    name = "R",
                    count = stats.redCount,
                    color = Color(0xFFE53935),
                    coalitionId = 1,
                    isSelected = selectedCoalitions.contains(1),
                    onClick = { onToggleCoalition(1) }
                )
                CoalitionBadge(
                    name = "B",
                    count = stats.blueCount,
                    color = Color(0xFF1E88E5),
                    coalitionId = 2,
                    isSelected = selectedCoalitions.contains(2),
                    onClick = { onToggleCoalition(2) }
                )
            }

            // Category chips (compact, horizontal scroll) with total badge at start
            Spacer(modifier = Modifier.height(2.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                maxItemsInEachRow = 10
            ) {
                TotalBadge(count = stats.totalActive, modifier = Modifier.padding(end = 2.dp))
                if (stats.aircraftCount > 0) CompactCategoryChip(
                    label = "Air: ${stats.aircraftCount}",
                    isSelected = selectedCategories.contains("aircraft"),
                    onClick = { onToggleCategory("aircraft") }
                )
                if (stats.helicopterCount > 0) CompactCategoryChip(
                    label = "Heli: ${stats.helicopterCount}",
                    isSelected = selectedCategories.contains("helicopter"),
                    onClick = { onToggleCategory("helicopter") }
                )
                if (stats.groundCount > 0) CompactCategoryChip(
                    label = "Gnd: ${stats.groundCount}",
                    isSelected = selectedCategories.contains("ground"),
                    onClick = { onToggleCategory("ground") }
                )
                if (stats.shipCount > 0) CompactCategoryChip(
                    label = "Ship: ${stats.shipCount}",
                    isSelected = selectedCategories.contains("ship"),
                    onClick = { onToggleCategory("ship") }
                )
                if (stats.structureCount > 0) CompactCategoryChip(
                    label = "Str: ${stats.structureCount}",
                    isSelected = selectedCategories.contains("structure"),
                    onClick = { onToggleCategory("structure") }
                )
                if (stats.weaponCount > 0) CompactCategoryChip(
                    label = "Wpn: ${stats.weaponCount}",
                    isSelected = selectedCategories.contains("weapon"),
                    onClick = { onToggleCategory("weapon") }
                )
                if (stats.countermeasureCount > 0) CompactCategoryChip(
                    label = "CM: ${stats.countermeasureCount}",
                    isSelected = selectedCategories.contains("countermeasure"),
                    onClick = { onToggleCategory("countermeasure") }
                )
            }
        }
    }
}

@Composable
private fun CompactToggleChip(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(32.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun CompactCategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .clickable(onClick = onClick)
            .height(28.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun TotalBadge(count: Int, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .height(28.dp)
            .padding(start = 2.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
@Composable
private fun StatsCard(
    stats: UnitStatistics,
    isEntityTrackingEnabled: Boolean,
    onToggleEntityTracking: () -> Unit,
    showTacticalUnitsOnMap: Boolean,
    onToggleMapVisibility: () -> Unit,
    mapUpdateInterval: Float = 2.0f,
    onMapUpdateIntervalChange: (Float) -> Unit = {},
    selectedCoalitions: Set<Int> = emptySet(),
    onToggleCoalition: (Int) -> Unit = {},
    selectedCategories: Set<String> = emptySet(),
    onToggleCategory: (String) -> Unit = {},
    tacticalAutoSort: Boolean = true,
    onToggleAutoSort: () -> Unit = {},
    showHiddenUnits: Boolean = false,
    onToggleShowHiddenUnits: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            var showVisibilityTooltip by remember { mutableStateOf(false) }
            // Entity Tracking Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleEntityTracking)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isEntityTrackingEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = if (isEntityTrackingEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.tactical_entity_tracking),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isEntityTrackingEnabled) stringResource(R.string.tactical_receiving) else stringResource(R.string.tactical_tracking_disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isEntityTrackingEnabled,
                    onCheckedChange = { onToggleEntityTracking() }
                )
            }

            // Map Visibility Toggle (independent of entity tracking - can show historical data)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleMapVisibility)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (showTacticalUnitsOnMap) Icons.Default.Map else Icons.Default.LocationOff,
                        contentDescription = null,
                        tint = if (showTacticalUnitsOnMap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.tactical_unit_visibility_on_map),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = when {
                                !showTacticalUnitsOnMap -> stringResource(R.string.tactical_units_hidden_on_map)
                                isEntityTrackingEnabled -> stringResource(R.string.tactical_units_visible_on_map)
                                else -> stringResource(R.string.tactical_units_visible_on_map_history)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { showVisibilityTooltip = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.tactical_unit_visibility_on_map),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Switch(
                    checked = showTacticalUnitsOnMap,
                    onCheckedChange = { onToggleMapVisibility() }
                )
            
            if (showVisibilityTooltip) {
                AlertDialog(
                    onDismissRequest = { showVisibilityTooltip = false },
                    title = { Text(stringResource(R.string.tactical_unit_visibility_on_map)) },
                    text = { Text(stringResource(R.string.tactical_unit_visibility_on_map_history_tooltip)) },
                    confirmButton = {
                        TextButton(onClick = { showVisibilityTooltip = false }) {
                            Text(stringResource(R.string.action_ok))
                        }
                    }
                )
            }
            }

            // Automatic list sorting (placement under map visibility)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleAutoSort)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (tacticalAutoSort) Icons.Default.Sort else Icons.Default.SortByAlpha,
                        contentDescription = null,
                        tint = if (tacticalAutoSort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.tactical_auto_sort),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (tacticalAutoSort) stringResource(R.string.tactical_auto_sort_enabled) else stringResource(R.string.tactical_auto_sort_disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = tacticalAutoSort,
                    onCheckedChange = { onToggleAutoSort() }
                )
            }

            // Show Hidden Units Toggle
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleShowHiddenUnits)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (showHiddenUnits) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = if (showHiddenUnits) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.tactical_show_hidden_units),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (showHiddenUnits) stringResource(R.string.tactical_hidden_units_visible) else stringResource(R.string.tactical_hidden_units_hidden),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = showHiddenUnits,
                    onCheckedChange = { onToggleShowHiddenUnits() }
                )
            }

            // Map Update Interval Slider (only show when map visibility is enabled)
            if (showTacticalUnitsOnMap) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.tactical_map_update_interval),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = String.format("%.1fs", mapUpdateInterval),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Slider(
                        value = mapUpdateInterval,
                        onValueChange = onMapUpdateIntervalChange,
                        valueRange = 0.5f..10.0f,
                        steps = 18, // 0.5, 1.0, 1.5, 2.0, ..., 10.0
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(
                        text = stringResource(R.string.tactical_map_update_explain),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(R.string.tactical_active_units_label, stats.totalActive),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Coalition stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CoalitionBadge(
                    name = stringResource(R.string.tactical_coalition_neutral),
                    count = stats.neutralCount,
                    color = Color(0xFF999999),
                    coalitionId = 0,
                    isSelected = selectedCoalitions.contains(0),
                    onClick = { onToggleCoalition(0) }
                )
                CoalitionBadge(
                    name = stringResource(R.string.tactical_coalition_red),
                    count = stats.redCount,
                    color = Color(0xFFE53935),
                    coalitionId = 1,
                    isSelected = selectedCoalitions.contains(1),
                    onClick = { onToggleCoalition(1) }
                )
                CoalitionBadge(
                    name = stringResource(R.string.tactical_coalition_blue),
                    count = stats.blueCount,
                    color = Color(0xFF1E88E5),
                    coalitionId = 2,
                    isSelected = selectedCoalitions.contains(2),
                    onClick = { onToggleCoalition(2) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Category stats (wrap to multiple rows if needed)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (stats.aircraftCount > 0) CategoryChip(
                    label = stringResource(R.string.category_aircraft),
                    count = stats.aircraftCount,
                    isSelected = selectedCategories.contains("aircraft"),
                    onClick = { onToggleCategory("aircraft") }
                )
                if (stats.helicopterCount > 0) CategoryChip(
                    label = stringResource(R.string.category_heli),
                    count = stats.helicopterCount,
                    isSelected = selectedCategories.contains("helicopter"),
                    onClick = { onToggleCategory("helicopter") }
                )
                if (stats.groundCount > 0) CategoryChip(
                    label = stringResource(R.string.category_ground),
                    count = stats.groundCount,
                    isSelected = selectedCategories.contains("ground"),
                    onClick = { onToggleCategory("ground") }
                )
                if (stats.shipCount > 0) CategoryChip(
                    label = stringResource(R.string.category_ship),
                    count = stats.shipCount,
                    isSelected = selectedCategories.contains("ship"),
                    onClick = { onToggleCategory("ship") }
                )
                if (stats.structureCount > 0) CategoryChip(
                    label = stringResource(R.string.category_structure),
                    count = stats.structureCount,
                    isSelected = selectedCategories.contains("structure"),
                    onClick = { onToggleCategory("structure") }
                )
                if (stats.weaponCount > 0) CategoryChip(
                    label = stringResource(R.string.category_weapon),
                    count = stats.weaponCount,
                    isSelected = selectedCategories.contains("weapon"),
                    onClick = { onToggleCategory("weapon") }
                )
            }
        }
    }
}

@Composable
private fun CoalitionBadge(
    name: String,
    count: Int,
    color: Color,
    coalitionId: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (isSelected) 1f else 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun CategoryChip(
    label: String,
    count: Int,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = "$label: $count",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun UnitCard(
    unit: TacticalUnitEntity,
    onClick: () -> Unit,
    onCenterOnMap: ((latitude: Double, longitude: Double) -> Unit)?,
    onShowDetails: ((TacticalUnitEntity) -> Unit)?,
    getCoalitionName: (Int) -> String,
    getCategoryDisplayName: (String) -> String
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = unit.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = unit.type,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Coalition badge
                    CoalitionIndicator(unit.coalition, getCoalitionName)
                    
                    // Expand/collapse icon
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Category and status
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = getCategoryDisplayName(unit.category),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                
                if (unit.isActive == 1) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = stringResource(R.string.status_active),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = stringResource(R.string.status_lost_contact),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Distance and bearing
            if (unit.distance != null && unit.bearing != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "${stringResource(R.string.unit_dist_label)} ${(unit.distance / 1000.0).format(1)} km",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${stringResource(R.string.unit_brg_label)} ${unit.bearing.toInt()}°",
                        style = MaterialTheme.typography.bodySmall
                    )
                    unit.speed?.let { speed ->
                        Text(
                            text = "${stringResource(R.string.unit_spd_label)} ${speed.toInt()} m/s",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // Last seen timestamp with live updates
            Spacer(modifier = Modifier.height(4.dp))
            var currentTime by remember { mutableStateOf(java.time.Instant.now()) }
            
            // Update current time every second for live timer
            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(1000)
                    currentTime = java.time.Instant.now()
                }
            }
            
            // Load string resources outside try-catch (composables can't be in try-catch)
            val strSeconds = stringResource(R.string.tactical_time_seconds, 0)
            val strMinutes = stringResource(R.string.tactical_time_minutes, 0)
            val strHours = stringResource(R.string.tactical_time_hours, 0)
            val strUnknown = stringResource(R.string.tactical_time_unknown)
            
            val (timeAgoText, secondsAgo) = try {
                val lastSeen = java.time.Instant.parse(unit.lastSeenAt)
                val seconds = java.time.Duration.between(lastSeen, currentTime).seconds
                val text = when {
                    seconds < 60 -> strSeconds.replace("0", seconds.toString())
                    seconds < 3600 -> strMinutes.replace("0", (seconds / 60).toString())
                    else -> strHours.replace("0", (seconds / 3600).toString())
                }
                Pair(text, seconds)
            } catch (_: Exception) {
                Pair(strUnknown, Long.MAX_VALUE)
            }
            Text(
                text = "${stringResource(R.string.unit_last_seen_label)} $timeAgoText",
                style = MaterialTheme.typography.bodySmall,
                color = if (secondsAgo < 10) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (secondsAgo < 10) FontWeight.Bold else FontWeight.Normal
            )
            
            // Group info
            unit.groupName?.let { group ->
                if (group.isNotEmpty()) {
                    Text(
                        text = "${stringResource(R.string.unit_group_label)} $group",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Pilot info
            unit.pilotName?.let { pilot ->
                if (pilot.isNotEmpty()) {
                    Text(
                        text = "${stringResource(R.string.pilot_label)} $pilot",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Expanded details
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Divider()
                    
                    // Coordinates
                    Text(
                        text = stringResource(R.string.coords_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.lat_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format("%.6f°", unit.latitude),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.lon_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format("%.6f°", unit.longitude),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.alt_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = unit.altitude?.let { "${it.toInt()} ft" } ?: stringResource(R.string.na_label),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    // Health bar
                    unit.health?.let { health ->
                        Text(
                            text = stringResource(R.string.unit_health_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = health.toFloat().coerceIn(0f, 1f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                color = when {
                                    health > 0.7 -> Color(0xFF4CAF50) // Green
                                    health > 0.3 -> Color(0xFFFFA726) // Orange
                                    else -> Color(0xFFE53935) // Red
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = "${(health * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    health > 0.7 -> Color(0xFF4CAF50)
                                    health > 0.3 -> Color(0xFFFFA726)
                                    else -> Color(0xFFE53935)
                                }
                            )
                        }
                    }

                    // Navigation data
                    if (unit.heading != null || unit.speed != null || unit.distance != null) {
                        Text(
                            text = stringResource(R.string.navigation_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            unit.heading?.let {
                                Column {
                                    Text(
                                        text = stringResource(R.string.heading_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${it.toInt()}°",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            unit.speed?.let {
                                Column {
                                    Text(
                                        text = stringResource(R.string.speed_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${it.toInt()} m/s",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            unit.distance?.let {
                                Column {
                                    Text(
                                        text = stringResource(R.string.distance_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = String.format("%.2f km", it / 1000.0),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            unit.bearing?.let {
                                Column {
                                    Text(
                                        text = stringResource(R.string.bearing_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${it.toInt()}°",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                    
                    // Timestamps
                    Text(
                        text = stringResource(R.string.tracking_info_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.first_seen_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = try {
                                    val instant = java.time.Instant.parse(unit.firstSeenAt)
                                    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                                        .withZone(ZoneId.systemDefault())
                                    formatter.format(instant)
                                } catch (_: Exception) { stringResource(R.string.na_label) },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.last_seen_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = try {
                                    val instant = java.time.Instant.parse(unit.lastSeenAt)
                                    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                                        .withZone(ZoneId.systemDefault())
                                    formatter.format(instant)
                                } catch (_: Exception) { stringResource(R.string.na_label) },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.dcs_id_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = unit.dcsId.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    
                    // Pilot info
                    unit.pilotName?.let { pilot ->
                        if (pilot.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.pilot_info_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${stringResource(R.string.pilot_label)} $pilot",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    // Action buttons - side by side at half size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        onCenterOnMap?.let {
                            Button(
                                onClick = { it(unit.latitude, unit.longitude) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.center_on_map),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        
                        onShowDetails?.let {
                            OutlinedButton(
                                onClick = { it(unit) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.show_details),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoalitionIndicator(coalition: Int, getCoalitionName: (Int) -> String) {
    val color = when (coalition) {
        0 -> Color(0xFF999999) // Neutral
        1 -> Color(0xFFE53935) // Red
        2 -> Color(0xFF1E88E5) // Blue
        else -> Color.Gray
    }
    
    Surface(
        shape = CircleShape,
        color = color,
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = getCoalitionName(coalition).first().toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun FilterDialog(
    uiState: TacticalUnitsUiState,
    onDismiss: () -> Unit,
    onToggleCategory: (String) -> Unit,
    onToggleCoalition: (Int) -> Unit,
    onToggleActiveOnly: (Boolean) -> Unit,
    onClearFilters: () -> Unit
) {
    val dataPadManager = LocalDataPadManager.current
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("tactical_units_prefs", android.content.Context.MODE_PRIVATE)
    
    // Persistent collapsed/expanded state for each section
    val KEY_FILTERS_EXPANDED = "settings_filters_expanded"
    val KEY_HIGHLIGHTS_EXPANDED = "settings_highlights_expanded"
    
    var filtersExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_FILTERS_EXPANDED, true)) }
    var highlightsExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_HIGHLIGHTS_EXPANDED, true)) }
    
    // Persist when changed
    LaunchedEffect(filtersExpanded) {
        prefs.edit().putBoolean(KEY_FILTERS_EXPANDED, filtersExpanded).apply()
    }
    LaunchedEffect(highlightsExpanded) {
        prefs.edit().putBoolean(KEY_HIGHLIGHTS_EXPANDED, highlightsExpanded).apply()
    }
    
    // Collect auto-highlight states
    val autoHighlightAll by dataPadManager.autoHighlightAll.collectAsState()
    val autoHighlightAircraft by dataPadManager.autoHighlightAircraft.collectAsState()
    val autoHighlightHelicopter by dataPadManager.autoHighlightHelicopter.collectAsState()
    val autoHighlightGround by dataPadManager.autoHighlightGround.collectAsState()
    val autoHighlightShip by dataPadManager.autoHighlightShip.collectAsState()
    val autoHighlightStructure by dataPadManager.autoHighlightStructure.collectAsState()
    val autoHighlightWeapon by dataPadManager.autoHighlightWeapon.collectAsState()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tactical_settings_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // === FILTERS SECTION (Collapsible) ===
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // Section header with expand/collapse button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { filtersExpanded = !filtersExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.filter_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (filtersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (filtersExpanded) "Collapse" else "Expand"
                            )
                        }
                        
                        // Collapsible content
                        AnimatedVisibility(visible = filtersExpanded) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = stringResource(R.string.filter_by_coalition),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = uiState.selectedCoalitions.contains(0),
                                        onClick = { onToggleCoalition(0) },
                                        label = { Text(stringResource(R.string.coalition_neutral)) }
                                    )
                                    FilterChip(
                                        selected = uiState.selectedCoalitions.contains(1),
                                        onClick = { onToggleCoalition(1) },
                                        label = { Text(stringResource(R.string.coalition_red)) }
                                    )
                                    FilterChip(
                                        selected = uiState.selectedCoalitions.contains(2),
                                        onClick = { onToggleCoalition(2) },
                                        label = { Text(stringResource(R.string.coalition_blue)) }
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = stringResource(R.string.filter_by_category),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilterChip(
                                        selected = uiState.selectedCategories.contains("aircraft"),
                                        onClick = { onToggleCategory("aircraft") },
                                        label = { Text(stringResource(R.string.category_aircraft)) }
                                    )
                                    FilterChip(
                                        selected = uiState.selectedCategories.contains("helicopter"),
                                        onClick = { onToggleCategory("helicopter") },
                                        label = { Text(stringResource(R.string.category_helicopter)) }
                                    )
                                    FilterChip(
                                        selected = uiState.selectedCategories.contains("ground"),
                                        onClick = { onToggleCategory("ground") },
                                        label = { Text(stringResource(R.string.category_ground)) }
                                    )
                                    FilterChip(
                                        selected = uiState.selectedCategories.contains("ship"),
                                        onClick = { onToggleCategory("ship") },
                                        label = { Text(stringResource(R.string.category_ship)) }
                                    )
                                    FilterChip(
                                        selected = uiState.selectedCategories.contains("structure"),
                                        onClick = { onToggleCategory("structure") },
                                        label = { Text(stringResource(R.string.category_structure)) }
                                    )
                                    FilterChip(
                                        selected = uiState.selectedCategories.contains("weapon"),
                                        onClick = { onToggleCategory("weapon") },
                                        label = { Text(stringResource(R.string.category_weapon)) }
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.filter_active_only),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Switch(
                                        checked = uiState.showActiveOnly,
                                        onCheckedChange = onToggleActiveOnly
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // === AUTO-HIGHLIGHT SECTION (Collapsible) ===
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // Section header with expand/collapse button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { highlightsExpanded = !highlightsExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.auto_highlight_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (highlightsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (highlightsExpanded) "Collapse" else "Expand"
                            )
                        }
                        
                        // Collapsible content
                        AnimatedVisibility(visible = highlightsExpanded) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = stringResource(R.string.auto_highlight_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Highlight All toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { dataPadManager.toggleAutoHighlightAll() },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = if (autoHighlightAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stringResource(R.string.auto_highlight_all),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (autoHighlightAll) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                    Switch(
                                        checked = autoHighlightAll,
                                        onCheckedChange = { dataPadManager.setAutoHighlightAll(it) }
                                    )
                                }
                                
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                
                                Text(
                                    text = stringResource(R.string.auto_highlight_by_category),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // Category-specific highlights
                                HighlightToggleRow(
                                    icon = Icons.Default.Flight,
                                    label = stringResource(R.string.category_aircraft),
                                    checked = autoHighlightAircraft,
                                    enabled = !autoHighlightAll,
                                    onCheckedChange = { dataPadManager.setAutoHighlightAircraft(it) }
                                )
                                HighlightToggleRow(
                                    icon = Icons.Default.Flight,
                                    label = stringResource(R.string.category_helicopter),
                                    checked = autoHighlightHelicopter,
                                    enabled = !autoHighlightAll,
                                    onCheckedChange = { dataPadManager.setAutoHighlightHelicopter(it) }
                                )
                                HighlightToggleRow(
                                    icon = Icons.Default.DirectionsCar,
                                    label = stringResource(R.string.category_ground),
                                    checked = autoHighlightGround,
                                    enabled = !autoHighlightAll,
                                    onCheckedChange = { dataPadManager.setAutoHighlightGround(it) }
                                )
                                HighlightToggleRow(
                                    icon = Icons.Default.DirectionsBoat,
                                    label = stringResource(R.string.category_ship),
                                    checked = autoHighlightShip,
                                    enabled = !autoHighlightAll,
                                    onCheckedChange = { dataPadManager.setAutoHighlightShip(it) }
                                )
                                HighlightToggleRow(
                                    icon = Icons.Default.Home,
                                    label = stringResource(R.string.category_structure),
                                    checked = autoHighlightStructure,
                                    enabled = !autoHighlightAll,
                                    onCheckedChange = { dataPadManager.setAutoHighlightStructure(it) }
                                )
                                HighlightToggleRow(
                                    icon = Icons.Default.GpsFixed,
                                    label = stringResource(R.string.category_weapon),
                                    checked = autoHighlightWeapon,
                                    enabled = !autoHighlightAll,
                                    onCheckedChange = { dataPadManager.setAutoHighlightWeapon(it) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onClearFilters()
                onDismiss()
            }) {
                Text(stringResource(R.string.clear_filters))
            }
        }
    )
}

@Composable
private fun HighlightToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (checked && enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (checked && enabled) FontWeight.Bold else FontWeight.Normal
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun OldFilterDialog(
    uiState: TacticalUnitsUiState,
    onDismiss: () -> Unit,
    onToggleCategory: (String) -> Unit,
    onToggleCoalition: (Int) -> Unit,
    onToggleActiveOnly: (Boolean) -> Unit,
    onClearFilters: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_title)) },
        text = {
            Column {
                // Active/Inactive toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.filter_show_active_only))
                    Switch(
                        checked = uiState.showActiveOnly,
                        onCheckedChange = onToggleActiveOnly
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Categories
                Text(stringResource(R.string.filter_categories_title), style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                listOf("aircraft", "helicopter", "ground", "ship", "structure", "weapon", "countermeasure").forEach { category ->
                    val categoryLabel = when (category) {
                        "aircraft" -> stringResource(R.string.category_aircraft)
                        "helicopter" -> stringResource(R.string.category_heli)
                        "ground" -> stringResource(R.string.category_ground)
                        "ship" -> stringResource(R.string.category_ship)
                        "structure" -> stringResource(R.string.category_structure)
                        "weapon" -> stringResource(R.string.category_weapon)
                        "countermeasure" -> "Countermeasures (CM)"
                        else -> category.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleCategory(category) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = uiState.selectedCategories.isEmpty() || uiState.selectedCategories.contains(category),
                            onCheckedChange = { onToggleCategory(category) }
                        )
                        Text(categoryLabel)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Coalitions
                Text(stringResource(R.string.filter_coalitions_title), style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                listOf(0 to stringResource(R.string.tactical_coalition_neutral), 1 to stringResource(R.string.tactical_coalition_red), 2 to stringResource(R.string.tactical_coalition_blue)).forEach { (coalition, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleCoalition(coalition) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = uiState.selectedCoalitions.isEmpty() || uiState.selectedCoalitions.contains(coalition),
                            onCheckedChange = { onToggleCoalition(coalition) }
                        )
                        Text(name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tactical_done))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onClearFilters()
                onDismiss()
            }) {
                Text(stringResource(R.string.action_clear))
            }
        }
    )
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = stringResource(R.string.empty_no_units_found),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.empty_start_datapad_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private fun Double.format(decimals: Int): String {
    return "%.${decimals}f".format(this)
}
