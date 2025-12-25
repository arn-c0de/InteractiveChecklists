package com.example.checklist_interactive.ui.tactical

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.checklist_interactive.data.datapad.DataPadManager
import com.example.checklist_interactive.ui.datapad.LocalDataPadManager
import com.example.checklist_interactive.data.tactical.TacticalUnitEntity
import com.example.checklist_interactive.data.tactical.TacticalUnitsRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
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
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tactical Units") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Filter button
                    IconButton(onClick = { viewModel.toggleFilterDialog() }) {
                        Badge(
                            containerColor = if (uiState.selectedCategories.isNotEmpty() || uiState.selectedCoalitions.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            }
                        ) {
                            Icon(Icons.Default.FilterList, "Filter")
                        }
                    }

                    // Cleanup menu
                    Box {
                        IconButton(onClick = { showCleanupMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More")
                        }
                        DropdownMenu(
                            expanded = showCleanupMenu,
                            onDismissRequest = { showCleanupMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete old inactive (7d)") },
                                onClick = {
                                    viewModel.deleteOldInactiveUnits(7)
                                    showCleanupMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete old history (14d)") },
                                onClick = {
                                    viewModel.deleteOldHistory(14)
                                    showCleanupMenu = false
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Delete all units", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showDeleteConfirm = true
                                    showCleanupMenu = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Live filter toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = if (uiState.showLiveOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Live Units Only",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (uiState.showLiveOnly) "Showing units seen in last 10s" else "Showing all units",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = uiState.showLiveOnly,
                    onCheckedChange = { viewModel.toggleLiveOnly() }
                )
            }
            
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search by name or group...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, "Clear")
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
                onMapUpdateIntervalChange = { dataPadManager.setTacticalUnitsMapUpdateInterval(it) }
            )
            
            // Units list
            if (units.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
            title = { Text("Delete All Units?") },
            text = { Text("This will delete all tracked units and their history. This cannot be undone.") },
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
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
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
    onMapUpdateIntervalChange: (Float) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
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
                            text = "Entity Tracking",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isEntityTrackingEnabled) "Receiving tactical units" else "Tracking disabled",
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

            // Map Visibility Toggle (only show when entity tracking is enabled)
            if (isEntityTrackingEnabled) {
                Spacer(modifier = Modifier.height(12.dp))

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
                                text = "Unit Visibility on Map",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (showTacticalUnitsOnMap) "Units visible on map" else "Units hidden on map",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = showTacticalUnitsOnMap,
                        onCheckedChange = { onToggleMapVisibility() }
                    )
                }
            }

            // Map Update Interval Slider (only show when entity tracking and map visibility are enabled)
            if (isEntityTrackingEnabled && showTacticalUnitsOnMap) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Map Update Interval",
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
                        text = "Lower = smoother movement, higher = better performance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text(
                text = "Active Units: ${stats.totalActive}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Coalition stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CoalitionBadge("Neutral", stats.neutralCount, Color(0xFF999999))
                CoalitionBadge("Red", stats.redCount, Color(0xFFE53935))
                CoalitionBadge("Blue", stats.blueCount, Color(0xFF1E88E5))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Category stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (stats.aircraftCount > 0) CategoryChip("Aircraft", stats.aircraftCount)
                if (stats.helicopterCount > 0) CategoryChip("Heli", stats.helicopterCount)
                if (stats.groundCount > 0) CategoryChip("Ground", stats.groundCount)
                if (stats.shipCount > 0) CategoryChip("Ship", stats.shipCount)
            }
        }
    }
}

@Composable
private fun CoalitionBadge(name: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryChip(label: String, count: Int) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = "$label: $count",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall
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
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
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
                            text = "ACTIVE",
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
                            text = "LOST CONTACT",
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
                        text = "Dist: ${(unit.distance / 1000.0).format(1)} km",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Brg: ${unit.bearing.toInt()}°",
                        style = MaterialTheme.typography.bodySmall
                    )
                    unit.speed?.let { speed ->
                        Text(
                            text = "Spd: ${speed.toInt()} m/s",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // Last seen timestamp
            Spacer(modifier = Modifier.height(4.dp))
            val (timeAgoText, secondsAgo) = try {
                val lastSeen = java.time.Instant.parse(unit.lastSeenAt)
                val now = java.time.Instant.now()
                val seconds = java.time.Duration.between(lastSeen, now).seconds
                val text = when {
                    seconds < 60 -> "${seconds}s ago"
                    seconds < 3600 -> "${seconds / 60}m ago"
                    else -> "${seconds / 3600}h ago"
                }
                Pair(text, seconds)
            } catch (_: Exception) {
                Pair("Unknown", Long.MAX_VALUE)
            }
            Text(
                text = "Last seen: $timeAgoText",
                style = MaterialTheme.typography.bodySmall,
                color = if (secondsAgo < 10) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (secondsAgo < 10) FontWeight.Bold else FontWeight.Normal
            )
            
            // Group info
            unit.groupName?.let { group ->
                if (group.isNotEmpty()) {
                    Text(
                        text = "Group: $group",
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
                        text = "Coordinates",
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
                                text = "Latitude",
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
                                text = "Longitude",
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
                                text = "Altitude",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = unit.altitude?.let { "${it.toInt()} ft" } ?: "N/A",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    // Navigation data
                    if (unit.heading != null || unit.speed != null || unit.distance != null) {
                        Text(
                            text = "Navigation",
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
                                        text = "Heading",
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
                                        text = "Speed",
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
                                        text = "Distance",
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
                                        text = "Bearing",
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
                        text = "Tracking Info",
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
                                text = "First Seen:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = try {
                                    val instant = java.time.Instant.parse(unit.firstSeenAt)
                                    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                                        .withZone(ZoneId.systemDefault())
                                    formatter.format(instant)
                                } catch (_: Exception) { "N/A" },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Last Seen:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = try {
                                    val instant = java.time.Instant.parse(unit.lastSeenAt)
                                    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                                        .withZone(ZoneId.systemDefault())
                                    formatter.format(instant)
                                } catch (_: Exception) { "N/A" },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "DCS ID:",
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
                                text = "Pilot Info",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Pilot: $pilot",
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
                            Text("Center on Map")
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
        title = { Text("Filter Units") },
        text = {
            Column {
                // Active/Inactive toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show active only")
                    Switch(
                        checked = uiState.showActiveOnly,
                        onCheckedChange = onToggleActiveOnly
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Categories
                Text("Categories", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                listOf("aircraft", "helicopter", "ground", "ship", "structure", "weapon").forEach { category ->
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
                        Text(category.capitalize())
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Coalitions
                Text("Coalitions", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                listOf(0 to "Neutral", 1 to "Red", 2 to "Blue").forEach { (coalition, name) ->
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
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onClearFilters()
                onDismiss()
            }) {
                Text("Clear")
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
                text = "No units found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Start DCS with DataPad enabled to track units",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// Extension function for Double formatting
private fun Double.format(decimals: Int): String {
    return "%.${decimals}f".format(this)
}
