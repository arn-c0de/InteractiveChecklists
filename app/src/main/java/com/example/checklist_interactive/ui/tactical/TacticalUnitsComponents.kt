@file:OptIn(ExperimentalLayoutApi::class)
package com.example.checklist_interactive.ui.tactical

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.R
import com.example.checklist_interactive.data.tactical.TacticalUnitEntity
import com.example.checklist_interactive.ui.datapad.LocalDataPadManager
import java.time.format.DateTimeFormatter

/**
 * Compact settings controls (collapsible)
 */
@Composable
fun CompactSettingsControls(
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
                                label = { Text(stringResource(R.string.tactical_interval)) },
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
                                        onMapUpdateIntervalChange(value.coerceIn(0.5f, 10.0f))
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
                            label = { Text(stringResource(R.string.tactical_hide_after)) },
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
                                    onHideTimeoutMinutesChange(value.coerceIn(1.0f, 120.0f))
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
@Composable
fun CompactStatsAndFilters(
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
fun StatsCard(
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
                        steps = 18,
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

            // Category stats
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
fun CompactToggleChip(
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
fun CompactCategoryChip(
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
fun TotalBadge(count: Int, modifier: Modifier = Modifier) {
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
fun CoalitionBadge(
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
fun CategoryChip(
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
fun UnitCard(
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
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = unit.type,
                        style = MaterialTheme.typography.bodySmall,
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
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(24.dp)
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
                        text = stringResource(R.string.unit_dist_label) + " ${unit.distance.format(1)}km",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.unit_brg_label) + " ${unit.bearing.format(0)}°",
                        style = MaterialTheme.typography.bodySmall
                    )
                    unit.speed?.let { speed ->
                        Text(
                            text = stringResource(R.string.unit_spd_label) + " ${speed.format(1)}m/s",
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
                        text = stringResource(R.string.unit_group_label, group),
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
                        text = stringResource(R.string.pilot_label) + " $pilot",
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
                    HorizontalDivider()
                    
                    // Coordinates
                    Text(
                        text = stringResource(R.string.coords_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.lat_label) + ": ${unit.latitude.format(6)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = stringResource(R.string.lon_label) + ": ${unit.longitude.format(6)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            unit.altitude?.let { alt ->
                                Text(
                                    text = stringResource(R.string.alt_label) + ": ${alt.format(1)}m",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        // Center on map button
                        onCenterOnMap?.let { handler ->
                            IconButton(
                                onClick = { handler(unit.latitude, unit.longitude) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = stringResource(R.string.center_on_map),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    // Health bar
                    unit.health?.let { health ->
                        Column {
                            Text(
                                text = stringResource(R.string.unit_health_label) + ": ${(health * 100).format(1)}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                            LinearProgressIndicator(
                                progress = health.toFloat().coerceIn(0f, 1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = when {
                                    health > 0.75 -> Color(0xFF4CAF50)
                                    health > 0.50 -> Color(0xFFFFC107)
                                    health > 0.25 -> Color(0xFFFF9800)
                                    else -> Color(0xFFF44336)
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }

                    // Navigation data
                    if (unit.heading != null || unit.speed != null || unit.distance != null) {
                        HorizontalDivider()
                        Text(
                            text = stringResource(R.string.navigation_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        
                        unit.heading?.let { heading ->
                            Text(
                                text = stringResource(R.string.heading_label) + ": ${heading.format(0)}°",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        unit.speed?.let { speed ->
                            Text(
                                text = stringResource(R.string.speed_label) + ": ${speed.format(1)}m/s",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        unit.distance?.let { distance ->
                            Text(
                                text = stringResource(R.string.unit_dist_label) + " ${distance.format(1)}km",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        unit.bearing?.let { bearing ->
                            Text(
                                text = stringResource(R.string.unit_brg_label) + " ${bearing.format(0)}°",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    // Timestamps
                    Text(
                        text = stringResource(R.string.tracking_info_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") }
                        val firstSeen = remember(unit.firstSeenAt) {
                            runCatching {
                                java.time.Instant.parse(unit.firstSeenAt)
                                    .atZone(java.time.ZoneId.systemDefault())
                            }.getOrNull()
                        }
                        val lastSeen = remember(unit.lastSeenAt) {
                            runCatching {
                                java.time.Instant.parse(unit.lastSeenAt)
                                    .atZone(java.time.ZoneId.systemDefault())
                            }.getOrNull()
                        }
                        
                        if (firstSeen != null && lastSeen != null) {
                            Text(
                                text = stringResource(R.string.first_seen_label) + " " + formatter.format(firstSeen),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = stringResource(R.string.last_seen_label) + " " + formatter.format(lastSeen),
                                style = MaterialTheme.typography.bodySmall
                            )
                            
                            // Time alive
                            val duration = java.time.Duration.between(firstSeen, lastSeen)
                            val hours = duration.toHours()
                            val minutes = duration.toMinutes() % 60
                            val seconds = duration.seconds % 60
                            
                            val timeAliveText = when {
                                hours > 0 -> "Time alive: ${hours}h ${minutes}m"
                                minutes > 0 -> "Time alive: ${minutes}m ${seconds}s"
                                else -> "Time alive: ${seconds}s"
                            }
                            
                            Text(
                                text = timeAliveText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.tactical_time_unknown),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    // Pilot info
                    unit.pilotName?.let { pilot ->
                        if (pilot.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                text = stringResource(R.string.pilot_info_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = pilot,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    // Action buttons - side by side at half size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        onCenterOnMap?.let { handler ->
                            Button(
                                onClick = { handler(unit.latitude, unit.longitude) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.center_on_map),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        
                        onShowDetails?.let { handler ->
                            OutlinedButton(
                                onClick = { handler(unit) },
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
                                    style = MaterialTheme.typography.labelSmall
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
fun CoalitionIndicator(coalition: Int, getCoalitionName: (Int) -> String) {
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
fun FilterDialog(
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
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // Header with collapse/expand button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { filtersExpanded = !filtersExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.filter_title),
                                style = MaterialTheme.typography.titleSmall,
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
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                
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
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Categories
                                Text(
                                    text = stringResource(R.string.filter_categories_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                listOf("aircraft", "helicopter", "ground", "ship", "structure", "weapon", "countermeasure").forEach { category ->
                                    val categoryLabel = when (category) {
                                        "aircraft" -> stringResource(R.string.category_aircraft)
                                        "helicopter" -> stringResource(R.string.category_heli)
                                        "ground" -> stringResource(R.string.category_ground)
                                        "ship" -> stringResource(R.string.category_ship)
                                        "structure" -> stringResource(R.string.category_structure)
                                        "weapon" -> stringResource(R.string.category_weapon)
                                        "countermeasure" -> "Countermeasure"
                                        else -> category
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(categoryLabel, style = MaterialTheme.typography.bodySmall)
                                        Checkbox(
                                            checked = uiState.selectedCategories.contains(category),
                                            onCheckedChange = { onToggleCategory(category) }
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Coalitions
                                Text(
                                    text = stringResource(R.string.filter_coalitions_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                listOf(
                                    0 to stringResource(R.string.tactical_coalition_neutral),
                                    1 to stringResource(R.string.tactical_coalition_red),
                                    2 to stringResource(R.string.tactical_coalition_blue)
                                ).forEach { (coalition, name) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(name, style = MaterialTheme.typography.bodySmall)
                                        Checkbox(
                                            checked = uiState.selectedCoalitions.contains(coalition),
                                            onCheckedChange = { onToggleCoalition(coalition) }
                                        )
                                    }
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
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // Header with collapse/expand button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { highlightsExpanded = !highlightsExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.auto_highlight_title),
                                style = MaterialTheme.typography.titleSmall,
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
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                
                                Text(
                                    text = stringResource(R.string.auto_highlight_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Master toggle
                                HighlightToggleRow(
                                    icon = Icons.Default.Star,
                                    label = stringResource(R.string.auto_highlight_all),
                                    checked = autoHighlightAll,
                                    enabled = true,
                                    onCheckedChange = { dataPadManager.toggleAutoHighlightAll() }
                                )
                                
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                
                                // Individual category toggles (disabled when master is off)
                                HighlightToggleRow(
                                    icon = Icons.Default.Flight,
                                    label = stringResource(R.string.category_aircraft),
                                    checked = autoHighlightAircraft,
                                    enabled = autoHighlightAll,
                                    onCheckedChange = { dataPadManager.toggleAutoHighlightAircraft() }
                                )
                                
                                HighlightToggleRow(
                                    icon = Icons.Default.FlightTakeoff,
                                    label = stringResource(R.string.category_heli),
                                    checked = autoHighlightHelicopter,
                                    enabled = autoHighlightAll,
                                    onCheckedChange = { dataPadManager.toggleAutoHighlightHelicopter() }
                                )
                                
                                HighlightToggleRow(
                                    icon = Icons.Default.DirectionsCar,
                                    label = stringResource(R.string.category_ground),
                                    checked = autoHighlightGround,
                                    enabled = autoHighlightAll,
                                    onCheckedChange = { dataPadManager.toggleAutoHighlightGround() }
                                )
                                
                                HighlightToggleRow(
                                    icon = Icons.Default.DirectionsBoat,
                                    label = stringResource(R.string.category_ship),
                                    checked = autoHighlightShip,
                                    enabled = autoHighlightAll,
                                    onCheckedChange = { dataPadManager.toggleAutoHighlightShip() }
                                )
                                
                                HighlightToggleRow(
                                    icon = Icons.Default.Business,
                                    label = stringResource(R.string.category_structure),
                                    checked = autoHighlightStructure,
                                    enabled = autoHighlightAll,
                                    onCheckedChange = { dataPadManager.toggleAutoHighlightStructure() }
                                )
                                
                                HighlightToggleRow(
                                    icon = Icons.Default.Repartition,
                                    label = stringResource(R.string.category_weapon),
                                    checked = autoHighlightWeapon,
                                    enabled = autoHighlightAll,
                                    onCheckedChange = { dataPadManager.toggleAutoHighlightWeapon() }
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
fun HighlightToggleRow(
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
fun EmptyState() {
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

fun Double.format(decimals: Int): String {
    return "%.${decimals}f".format(this)
}
