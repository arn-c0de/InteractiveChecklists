package com.example.checklist_interactive.ui.maps.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.R
import com.example.checklist_interactive.data.tactical.LocationEntity
import com.example.checklist_interactive.data.tactical.RunwayEntity
import com.example.checklist_interactive.data.tactical.TacticalDatabase
import com.example.checklist_interactive.ui.maps.MapActionBus
import com.example.checklist_interactive.ui.maps.marker.LocationEditDialog
import org.json.JSONObject
import java.time.Instant
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope

private fun formatLatLon(lat: Double, lon: Double): String {
    val latPrefix = if (lat >= 0) "N" else "S"
    val lonPrefix = if (lon >= 0) "E" else "W"
    val latAbs = String.format(java.util.Locale.getDefault(), "%.6f", kotlin.math.abs(lat))
    val lonAbs = String.format(java.util.Locale.getDefault(), "%.6f", kotlin.math.abs(lon))
    return "$latPrefix $latAbs, $lonPrefix $lonAbs"
}

private fun extractHeadingFromLocation(location: com.example.checklist_interactive.data.tactical.LocationEntity): Double? {
    location.metadata?.let { meta ->
        try {
            val obj = JSONObject(meta)
            if (obj.has("heading") && !obj.isNull("heading")) {
                val v = obj.optDouble("heading")
                if (!v.isNaN()) return v
                val s = obj.optString("heading", "")
                s.toDoubleOrNull()?.let { return it }
            }
        } catch (_: Exception) {
        }
    }
    location.description.takeIf { it.isNotEmpty() }?.let { desc ->
        val regex = Regex("(?i)\\b(?:hdg|heading)\\s*[:=]?\\s*([0-9]{1,3}(?:\\.[0-9]+)?)\\b")
        val m = regex.find(desc)
        if (m != null) return m.groupValues[1].toDoubleOrNull()
    }
    location.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
        val regex2 = Regex("(?i)\\bheading=([0-9]{1,3}(?:\\.[0-9]+)?)\\b")
        val m2 = regex2.find(tags)
        if (m2 != null) return m2.groupValues[1].toDoubleOrNull()
    }
    return null
}

/**
 * Single marker navigation and details display
 * Handles individual marker navigation, details view, and quick actions
 */

/**
 * Marker details content with navigation options
 * Displays comprehensive marker information and provides action buttons for
 * navigation, editing, centering, and deletion.
 */
@Composable
fun MarkerDetailsContent(
    location: LocationEntity,
    runways: List<RunwayEntity>,
    onClose: () -> Unit,
    onSetRoute: (LocationEntity) -> Unit = {},
    onEdit: (LocationEntity) -> Unit = {},
    onDelete: (Int) -> Unit = {},
    onCenter: (LocationEntity) -> Unit = {}
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // Header with location name and coordinates
        MarkerDetailsHeader(location)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Info grid (two columns) — expanded to include tactical/admin metadata, source and tags
        MarkerDetailsInfoGrid(location, runways)
        
        // Description
        if (location.description.isNotEmpty()) {
            Text(
                text = location.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // Frequencies
        MarkerDetailsFrequencies(location)
        
        // Runways
        MarkerDetailsRunways(runways)
        
        // Action buttons
        MarkerDetailsActionButtons(
            location = location,
            onSetRoute = onSetRoute,
            onEditClick = { showEditDialog = true },
            onMoveClick = {
                MapActionBus.requestMove(location.id)
                onClose()
            },
            onCenterClick = {
                onCenter(location)
                onClose()
            },
            onDeleteClick = { showDeleteConfirm = true },
            onClose = onClose
        )
    }
    
    // Edit dialog
    if (showEditDialog) {
        LocationEditDialog(
            location = location,
            runways = runways,
            onDismiss = { showEditDialog = false },
            onSave = { updatedLocation ->
                onEdit(updatedLocation)
                showEditDialog = false
            },
            onSaveRunways = { updatedRunways ->
                saveRunwaysToDatabase(context, location, runways, updatedRunways)
            }
        )
    }
    
    // Delete confirmation
    if (showDeleteConfirm) {
        MarkerDeleteConfirmationDialog(
            markerName = location.name,
            onConfirm = {
                onDelete(location.id)
                showDeleteConfirm = false
                onClose()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

/**
 * Header section with marker name and coordinates
 */
@Composable
private fun MarkerDetailsHeader(location: LocationEntity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { com.example.checklist_interactive.data.tactical.TacticalUnitsRepository(context) }
    val configuration = LocalConfiguration.current
    val isSmallScreen = configuration.screenWidthDp < 600
    
    // Extract tactical unit ID and highlight status from metadata
    val (tacticalUnitId, initialHighlightState) = remember(location.metadata) {
        try {
            val metadata = location.metadata?.let { org.json.JSONObject(it) }
            val unitId = metadata?.optInt("tactical_unit_id", -1) ?: -1
            val isHighlighted = metadata?.optInt("is_highlighted", 0) ?: 0
            Pair(unitId, isHighlighted == 1)
        } catch (_: Exception) {
            Pair(-1, false)
        }
    }
    
    var isHighlighted by remember { mutableStateOf(initialHighlightState) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = location.name,
                style = if (isSmallScreen) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = if (isSmallScreen) 2 else Int.MAX_VALUE
            )
        
        // Last Seen display for tactical units (directly under name)
        location.updatedAt?.let { lastSeenStr ->
            Spacer(modifier = Modifier.height(4.dp))
            
            // Update current time every second for live timer
            var currentTime by remember { mutableStateOf(Instant.now()) }
            
            LaunchedEffect(Unit) {
                while (isActive) {
                    currentTime = Instant.now()
                    kotlinx.coroutines.delay(1000L)
                }
            }
            
            // Load string resources
            val strLastSeen = stringResource(R.string.last_seen_label)
            val strSecondsFormat = stringResource(R.string.tactical_time_seconds)
            val strMinutesFormat = stringResource(R.string.tactical_time_minutes)
            val strHoursFormat = stringResource(R.string.tactical_time_hours)
            val strUnknown = stringResource(R.string.tactical_time_unknown)
            val strNA = stringResource(R.string.na_label)
            
            val (timeAgoText, secondsAgo) = try {
                val lastSeenTime = Instant.parse(lastSeenStr)
                val seconds = (currentTime.epochSecond - lastSeenTime.epochSecond)
                val text = when {
                    seconds < 60 -> String.format(java.util.Locale.getDefault(), strSecondsFormat, seconds)
                    seconds < 3600 -> String.format(java.util.Locale.getDefault(), strMinutesFormat, seconds / 60)
                    else -> String.format(java.util.Locale.getDefault(), strHoursFormat, seconds / 3600)
                }
                Pair(text, seconds)
            } catch (_: Exception) {
                Pair(strNA, Long.MAX_VALUE)
            }
            
            // Color coding based on age
            val textColor = when {
                secondsAgo < 30 -> MaterialTheme.colorScheme.primary
                secondsAgo < 300 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            }
            
            Text(
                text = "$strLastSeen $timeAgoText",
                style = if (isSmallScreen) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
        }
        
        // Highlight button for tactical units
        if (tacticalUnitId > 0) {
            IconButton(
                onClick = {
                    isHighlighted = !isHighlighted
                    scope.launch {
                        repository.toggleUnitHighlight(tacticalUnitId, isHighlighted)
                    }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isHighlighted) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isHighlighted) "Remove highlight" else "Highlight on map",
                    tint = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(if (isSmallScreen) 2.dp else 4.dp))
        Text(
            text = formatLatLon(location.latitude, location.longitude),
            style = if (isSmallScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Show altitude if available
        location.elevationM?.let { elevation ->
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Alt: ${String.format(java.util.Locale.getDefault(), "%.0f", elevation)}m (${String.format(java.util.Locale.getDefault(), "%.0f", elevation * 3.28084)}ft)",
                style = if (isSmallScreen) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Show heading below altitude if present
        val markerHdg = extractHeadingFromLocation(location)
        markerHdg?.let { hdg ->
            Spacer(modifier = Modifier.height(if (isSmallScreen) 2.dp else 4.dp))
            Text(
                text = "${stringResource(R.string.heading_label)}: ${String.format(java.util.Locale.getDefault(), "%.0f°", (((hdg % 360) + 360) % 360))}",
                style = if (isSmallScreen) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Info grid displaying marker metadata
 */
@Composable
private fun MarkerDetailsInfoGrid(location: LocationEntity, runways: List<RunwayEntity>) {
    val infoItems = remember(location, runways) {
        buildMarkerInfoItems(location, runways)
    }
    val configuration = LocalConfiguration.current
    val isSmallScreen = configuration.screenWidthDp < 600
    val columnsPerRow = if (isSmallScreen) 1 else 2
    
    if (infoItems.isNotEmpty()) {
        Column {
            infoItems.chunked(columnsPerRow).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (k, v) ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = k,
                                style = if (isSmallScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = v,
                                style = if (isSmallScreen) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = if (isSmallScreen) 1 else Int.MAX_VALUE,
                                softWrap = true
                            )
                        }
                    }
                    if (row.size == 1 && columnsPerRow == 2) Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Build list of information items for display
 */
private fun buildMarkerInfoItems(
    location: LocationEntity,
    runways: List<RunwayEntity>
): List<Pair<String, String>> {
    return mutableListOf<Pair<String, String>>().apply {
        // Basic identifiers
        location.markerType?.takeIf { it.isNotEmpty() }?.let {
            add("Type" to it.replace('_', ' '))
        }
        location.icao?.takeIf { it.isNotEmpty() }?.let { add("ICAO" to it) }
        location.iata?.takeIf { it.isNotEmpty() }?.let { add("IATA" to it) }
        
        // DCS Map identifier
        location.map?.takeIf { it.isNotEmpty() }?.let { add("Map" to it) }
        
        // Geography / admin
        location.country?.takeIf { it.isNotEmpty() }?.let { add("Country" to it) }
        location.region?.takeIf { it.isNotEmpty() }?.let { add("Region" to it) }
        location.timezone?.takeIf { it.isNotEmpty() }?.let { add("Timezone" to it) }
        
        // Elevation
        location.elevationM?.let { add("Elevation" to "${it} m") }
        
        // Tactical / unit fields (if present)
        location.unitType?.takeIf { it.isNotEmpty() }?.let { add("Unit" to it) }
        location.threatLevel?.let { add("Threat" to it.toString()) }
        location.strength?.let { add("Strength" to it.toString()) }
        
        // Source and verification
        location.source?.takeIf { it.isNotEmpty() }?.let { add("Source" to it) }
        location.verified?.let { add("Verified" to if (it == 1) "yes" else "no") }
        location.lastVerifiedAt?.takeIf { it.isNotEmpty() }?.let { add("Verified at" to it) }
        
        // Tags (try JSON array, fallback to comma-separated string)
        location.tags?.takeIf { it.isNotEmpty() }?.let { rawTags ->
            val parsed = try {
                val arr = org.json.JSONArray(rawTags)
                (0 until arr.length()).map { i -> arr.optString(i) }.filter { it.isNotEmpty() }
            } catch (_: Exception) {
                rawTags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            }
            if (parsed.isNotEmpty()) add("Tags" to parsed.joinToString(", "))
        }
        
        // Runways count if provided by the caller
        if (runways.isNotEmpty()) add("Runways" to runways.size.toString())
    }
}

/**
 * Frequencies section with chips or text display
 */
@Composable
private fun MarkerDetailsFrequencies(location: LocationEntity) {
    val configuration = LocalConfiguration.current
    val isSmallScreen = configuration.screenWidthDp < 600
    val freqs = remember(location.frequencies) {
        try {
            val f = location.frequencies?.trim()
            if (f != null && f.startsWith("{")) {
                val obj = JSONObject(f)
                val keys = obj.keys().asSequence().toList()
                keys.map { k -> k to obj.optString(k) }
            } else emptyList()
        } catch (_: Exception) {
            emptyList<Pair<String, String>>()
        }
    }
    
    if (freqs.isNotEmpty() || (!location.frequencies.isNullOrEmpty())) {
        Text(
            text = "Frequencies",
            style = if (isSmallScreen) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        
        if (freqs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                freqs.forEach { (k, v) ->
                    AssistChip(
                        onClick = { /*noop*/ },
                        label = { 
                            Text(
                                text = "$k: $v",
                                style = if (isSmallScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
                            ) 
                        }
                    )
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = location.frequencies ?: "",
                    modifier = Modifier.padding(if (isSmallScreen) 6.dp else 8.dp),
                    fontFamily = FontFamily.Monospace,
                    style = if (isSmallScreen) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Runways section with compact cards
 */
@Composable
private fun MarkerDetailsRunways(runways: List<RunwayEntity>) {
    val configuration = LocalConfiguration.current
    val isSmallScreen = configuration.screenWidthDp < 600
    
    if (runways.isNotEmpty()) {
        Text(
            text = "Runways",
            style = if (isSmallScreen) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 6.dp else 8.dp)) {
            runways.forEach { rw ->
                RunwayCard(runway = rw, isSmallScreen = isSmallScreen)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Individual runway card
 */
@Composable
private fun RunwayCard(runway: RunwayEntity, isSmallScreen: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSmallScreen) 1.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(if (isSmallScreen) 8.dp else 12.dp)) {
            // Name and length row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = runway.name,
                    fontWeight = FontWeight.Bold,
                    style = if (isSmallScreen) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
                )
                val length = runway.lengthM?.toString() ?: runway.lengthFt?.toString() ?: "?"
                Text(
                    text = "${length}m",
                    style = if (isSmallScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(if (isSmallScreen) 4.dp else 6.dp))
            
            // Surface and heading - always horizontal
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = runway.surface ?: "?",
                    style = if (isSmallScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "HDG ${runway.headingDeg?.let { String.format("%.0f", it) } ?: "?"}°",
                    style = if (isSmallScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // ILS and Lighting chips - only if present
            if (!runway.ilsFrequency.isNullOrEmpty() || runway.hasLighting == 1) {
                Spacer(modifier = Modifier.height(if (isSmallScreen) 4.dp else 6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    if (!runway.ilsFrequency.isNullOrEmpty()) {
                        AssistChip(
                            onClick = { /*noop*/ },
                            label = { 
                                Text(
                                    text = "ILS ${runway.ilsFrequency}",
                                    style = if (isSmallScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
                                ) 
                            }
                        )
                    }
                    
                    if (runway.hasLighting == 1) {
                        AssistChip(
                            onClick = { /*noop*/ },
                            label = { 
                                Text(
                                    text = "Lights",
                                    style = if (isSmallScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
                                ) 
                            }
                        )
                    }
                }
            }
            
            // Notes
            if (!runway.notes.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(if (isSmallScreen) 4.dp else 8.dp))
                Text(
                    text = runway.notes ?: "",
                    style = if (isSmallScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isSmallScreen) 2 else Int.MAX_VALUE
                )
            }
        }
    }
}

/**
 * Action buttons section
 */
@Composable
private fun MarkerDetailsActionButtons(
    location: LocationEntity,
    onSetRoute: (LocationEntity) -> Unit,
    onEditClick: () -> Unit,
    onMoveClick: () -> Unit,
    onCenterClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onClose: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isSmallScreen = configuration.screenWidthDp < 600
    
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Set Route button (prominent)
        Button(
            onClick = {
                android.util.Log.d("MarkerDetailsButtons", "🚀 Set Route button clicked: id=${location.id}, source=${location.source}, name=${location.name}")
                onSetRoute(location)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            contentPadding = if (isSmallScreen) PaddingValues(horizontal = 12.dp, vertical = 8.dp) else ButtonDefaults.ContentPadding
        ) {
            Icon(Icons.Default.Flight, contentDescription = null, modifier = Modifier.size(if (isSmallScreen) 18.dp else 24.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.map_set_route), style = if (isSmallScreen) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge)
        }
        
        // Edit / Move / Center / Delete buttons - vertical on small screens, horizontal on large
        if (isSmallScreen) {
            // Vertical layout for small screens
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onEditClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.map_action_edit), style = MaterialTheme.typography.labelMedium)
                }
                
                OutlinedButton(
                    onClick = onCenterClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.map_center_on_map), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.map_action_center), style = MaterialTheme.typography.labelMedium)
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (location.isStatic != 1) {
                    OutlinedButton(
                        onClick = onMoveClick,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.OpenWith, contentDescription = stringResource(R.string.map_move_marker), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.map_action_move), style = MaterialTheme.typography.labelMedium)
                    }
                }
                
                OutlinedButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            // Horizontal layout for larger screens
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Edit button
                OutlinedButton(
                    onClick = onEditClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.map_action_edit))
                }
                
                // Move button (only for non-static markers)
                if (location.isStatic != 1) {
                    OutlinedButton(
                        onClick = onMoveClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenWith, contentDescription = stringResource(R.string.map_move_marker))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.map_action_move))
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                
                // Center button
                OutlinedButton(
                    onClick = onCenterClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.map_center_on_map))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.map_action_center))
                }
                
                // Delete button
                OutlinedButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
        
        // Back button
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = if (isSmallScreen) PaddingValues(horizontal = 12.dp, vertical = 8.dp) else ButtonDefaults.ContentPadding
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back), modifier = Modifier.size(if (isSmallScreen) 18.dp else 24.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.action_back), style = if (isSmallScreen) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * Delete confirmation dialog
 */
@Composable
private fun MarkerDeleteConfirmationDialog(
    markerName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_delete_marker_title)) },
        text = { Text(stringResource(R.string.map_delete_marker_confirm, markerName)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * Save runways to database
 */
private suspend fun saveRunwaysToDatabase(
    context: android.content.Context,
    location: LocationEntity,
    existingRunways: List<RunwayEntity>,
    updatedRunways: List<RunwayEntity>
) {
    val td = TacticalDatabase.getInstance(context, useExternalPath = false)
    val dao = td.runwayDao()
    
    // Determine existing and updated ids
    val existingIds = existingRunways.mapNotNull { it.id }.toSet()
    val updatedIds = updatedRunways.mapNotNull { it.id }.toSet()
    val toDelete = existingIds - updatedIds
    
    // Delete removed runways
    toDelete.forEach { id -> dao.deleteRunwayById(id) }
    
    // Insert or update runways
    updatedRunways.forEach { rw ->
        if ((rw.id ?: 0) == 0) {
            // Insert new runway and ensure locationId is set
            val insertRw = rw.copy(locationId = location.id ?: 0)
            dao.insertRunway(insertRw)
        } else {
            dao.updateRunway(rw)
        }
    }
}
