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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.checklist_interactive.R
import com.example.checklist_interactive.data.datapad.DataPadManager
import com.example.checklist_interactive.ui.datapad.LocalDataPadManager
import com.example.checklist_interactive.data.tactical.TacticalUnitEntity
import com.example.checklist_interactive.data.tactical.TacticalUnitsRepository
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TacticalUnitsListScreen(
    onNavigateBack: () -> Unit,
    onUnitClick: ((TacticalUnitEntity) -> Unit)? = null,
    onCenterOnMap: ((latitude: Double, longitude: Double) -> Unit)? = null
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

    val isEntityTrackingEnabled by dataPadManager.isEntityTrackingEnabled.collectAsState()
    val mapUpdateInterval by dataPadManager.tacticalUnitsMapUpdateInterval.collectAsState()
    val showTacticalUnitsOnMap by dataPadManager.showTacticalUnitsOnMap.collectAsState()

    val units by viewModel.units.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val stats by viewModel.stats.collectAsState()
    
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCleanupMenu by remember { mutableStateOf(false) }
    
    // Opacity control (persistent)
    val prefs = context.getSharedPreferences("tactical_units_prefs", android.content.Context.MODE_PRIVATE)
    val KEY_OPACITY = "tactical_units_opacity"
    val savedOpacity = prefs.getFloat(KEY_OPACITY, 1.0f)
    var dialogOpacity by rememberSaveable { mutableStateOf(savedOpacity.coerceIn(0.25f, 1.0f)) }
    var showOpacitySlider by remember { mutableStateOf(false) }

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
                
                // Live filter toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = if (uiState.showLiveOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.tactical_live_only),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = { viewModel.toggleFilterDialog() }) {
                            Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.tactical_advanced_filters))
                        }
                    }
                    Switch(
                        checked = uiState.showLiveOnly,
                        onCheckedChange = { viewModel.setShowLiveOnly(it) }
                    )
                }
                
                // Search bar (compact)
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(vertical = 4.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    placeholder = { Text(stringResource(R.string.tactical_search_placeholder), style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear), modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true
                )
                
                // Statistics card with entity tracking toggle and map update interval
                StatsCard(
                    stats = stats,
                    isEntityTrackingEnabled = isEntityTrackingEnabled,
                    onToggleEntityTracking = { dataPadManager.toggleEntityTracking() },
                    showTacticalUnitsOnMap = showTacticalUnitsOnMap,
                    onToggleMapVisibility = { dataPadManager.toggleTacticalUnitsOnMap() },
                    mapUpdateInterval = mapUpdateInterval,
                    onMapUpdateIntervalChange = { dataPadManager.setTacticalUnitsMapUpdateInterval(it) },
                    selectedCoalitions = uiState.selectedCoalitions,
                    onToggleCoalition = { viewModel.toggleCoalition(it) },
                    selectedCategories = uiState.selectedCategories,
                    onToggleCategory = { viewModel.toggleCategory(it) }
                )
                
                // Units list
                if (units.isEmpty()) {
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
                            items = units,
                            key = { it.id }
                        ) { unit ->
                            UnitCard(
                                unit = unit,
                                onClick = { onUnitClick?.invoke(unit) },
                                onCenterOnMap = onCenterOnMap,
                                getCoalitionName = { viewModel.getCoalitionName(it) },
                                getCategoryDisplayName = { viewModel.getCategoryDisplayName(it) }
                            )
                        }
                    }
                }
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
    onToggleCategory: (String) -> Unit = {}
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
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (isSelected) 1f else 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                color = Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
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
                    
                    // Center on Map button
                    if (onCenterOnMap != null) {
                        Button(
                            onClick = { onCenterOnMap(unit.latitude, unit.longitude) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.center_on_map))
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
                listOf("aircraft", "helicopter", "ground", "ship", "structure", "weapon").forEach { category ->
                    val categoryLabel = when (category) {
                        "aircraft" -> stringResource(R.string.category_aircraft)
                        "helicopter" -> stringResource(R.string.category_heli)
                        "ground" -> stringResource(R.string.category_ground)
                        "ship" -> stringResource(R.string.category_ship)
                        "structure" -> stringResource(R.string.category_structure)
                        "weapon" -> stringResource(R.string.category_weapon)
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
