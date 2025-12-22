package com.example.checklist_interactive.ui.maps.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.checklist_interactive.data.datapad.FlightData
import com.example.checklist_interactive.ui.maps.MapViewerState
import com.example.checklist_interactive.ui.maps.navigation.PatternDirection
import com.example.checklist_interactive.ui.maps.navigation.PatternSize
import org.osmdroid.util.GeoPoint

/**
 * Active Navigation Display - Shows route information and runway approach options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapNavigationDisplay(
    mapState: MapViewerState,
    flightData: FlightData?,
    modifier: Modifier = Modifier
) {
    if (mapState.activeNavigationTarget != null) {
        Card(
            modifier = modifier
                .widthIn(max = 500.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clickable { mapState.showNavigationDetails = !mapState.showNavigationDetails; mapState.saveNavigationState() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ROUTE TO: ${mapState.activeNavigationTarget?.name ?: ""}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            mapState.navigationDistanceNm?.let { dist ->
                                Text(
                                    text = "${String.format("%.1f", dist)} NM",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            mapState.navigationHeading?.let { hdg ->
                                Text(
                                    text = "HDG ${String.format("%03.0f", hdg)}°",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        // Show final runway heading when runway selected
                        mapState.selectedRunwayHeading?.let { rwyHdg ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = "FINAL:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "RWY ${String.format("%03.0f", rwyHdg)}°",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )

                                // Calculate and display distance to airport
                                val airport = mapState.originalAirportTarget
                                if (airport != null && flightData != null &&
                                    flightData.latitude != 0.0 && flightData.longitude != 0.0) {
                                    val airportPos = GeoPoint(airport.latitude, airport.longitude)
                                    val playerPos = GeoPoint(flightData.latitude, flightData.longitude)
                                    val distanceMeters = playerPos.distanceToAsDouble(airportPos)
                                    val distanceNm = distanceMeters / 1852.0
                                    Text(
                                        text = "${String.format("%.1f", distanceNm)} NM",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Compact pattern altitude + current altitude + indicator (visible when collapsed)
                        val patternAltCompact = mapState.patternSize.patternAltitudeFt
                        val currentAltMetersCompact = flightData?.altitude?.let { it.toDouble() } ?: Double.NaN
                        val currentAltCompact = if (currentAltMetersCompact.isNaN()) Double.NaN else currentAltMetersCompact * 3.28084 // convert m -> ft
                        val currentAltCompactDisplay = if (currentAltCompact.isNaN()) "n/a" else String.format("%d ft", currentAltCompact.toInt())
                        val diffCompact = if (currentAltCompact.isNaN()) null else currentAltCompact - patternAltCompact
                        val smallTolerance = mapState.patternAltitudeSmallToleranceFt
                        val warningTol = mapState.patternAltitudeWarningToleranceFt

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "P:${patternAltCompact}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            // compact indicator
                            if (diffCompact != null) {
                                val absDiff = kotlin.math.abs(diffCompact)
                                when {
                                    absDiff <= smallTolerance -> {
                                        Text(text = "≈", color = androidx.compose.ui.graphics.Color(0xFF00C853))
                                    }
                                    else -> {
                                        val col = if (absDiff <= warningTol) androidx.compose.ui.graphics.Color(0xFFFFA000) else androidx.compose.ui.graphics.Color(0xFFD50000)
                                        if (diffCompact < 0) {
                                            Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Need to climb", tint = col)
                                        } else {
                                            Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Need to descend", tint = col)
                                        }
                                    }
                                }
                            } else {
                                Text(text = "?", color = MaterialTheme.colorScheme.onErrorContainer)
                            }

                            Text(
                                text = "C:${currentAltCompactDisplay}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                            )
                        }

                        // Toggle expand/collapse button
                        IconButton(
                            onClick = { mapState.showNavigationDetails = !mapState.showNavigationDetails; mapState.saveNavigationState() }
                        ) {
                            Icon(
                                imageVector = if (mapState.showNavigationDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (mapState.showNavigationDetails) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        // Land button (only show if target has runways)
                        if (mapState.targetRunways.isNotEmpty()) {
                            FilledTonalIconButton(
                                onClick = {
                                    mapState.showRunwayApproach = !mapState.showRunwayApproach
                                    if (!mapState.showRunwayApproach) {
                                        mapState.selectedRunwayIndex = null
                                        mapState.selectedRunwayHeading = null
                                        mapState.selectedRunway = null
                                    }
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (mapState.showRunwayApproach)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (mapState.showRunwayApproach)
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlightLand,
                                    contentDescription = "Landing approach"
                                )
                            }
                        }

                        // Cancel button
                        IconButton(
                            onClick = {
                                mapState.activeNavigationTarget = null
                                mapState.originalAirportTarget = null
                                mapState.showRunwayApproach = false
                                mapState.selectedRunwayIndex = null
                                mapState.selectedRunwayHeading = null
                                mapState.selectedRunway = null
                                mapState.saveNavigationState()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel navigation",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Collapsible details section
                AnimatedVisibility(
                    visible = mapState.showNavigationDetails,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Runway selection (when approach mode active)
                        if (mapState.showRunwayApproach && mapState.targetRunways.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SELECT RUNWAY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )

                            // Final approach distance dropdown
                            var expanded by remember { mutableStateOf(false) }
                            val distances = listOf(2.5, 5.0, 10.0, 15.0, 25.0)

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Pattern button
                                Button(
                                    onClick = { mapState.showTrafficPattern = !mapState.showTrafficPattern },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (mapState.showTrafficPattern) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        contentColor = if (mapState.showTrafficPattern) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = "PATTERN",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp
                                    )
                                }

                                Box {
                                    FilterChip(
                                        selected = false,
                                        onClick = { expanded = !expanded },
                                        label = {
                                            Text(
                                                text = "${mapState.finalApproachDistanceNm.let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() }} NM",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.height(28.dp)
                                    )

                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        distances.forEach { dist ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = "${if (dist == dist.toInt().toDouble()) dist.toInt().toString() else dist.toString()} NM Final",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                },
                                                onClick = {
                                                    mapState.finalApproachDistanceNm = dist
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Pattern configuration (when pattern mode active)
                        if (mapState.showTrafficPattern) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "PATTERN CONFIGURATION",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Pattern size selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Size:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )

                                var sizeExpanded by remember { mutableStateOf(false) }
                                Box {
                                    FilterChip(
                                        selected = false,
                                        onClick = { sizeExpanded = !sizeExpanded },
                                        label = {
                                            Text(
                                                text = mapState.patternSize.displayName,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = if (sizeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.height(28.dp)
                                    )

                                    DropdownMenu(
                                        expanded = sizeExpanded,
                                        onDismissRequest = { sizeExpanded = false }
                                    ) {
                                        PatternSize.values().forEach { size ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(
                                                            text = size.displayName,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "${size.downwindDistanceNm} NM • ${size.patternAltitudeFt} ft",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    mapState.patternSize = size
                                                    sizeExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Pattern direction selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Direction:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = mapState.patternDirection == PatternDirection.LEFT_HAND,
                                        onClick = { mapState.patternDirection = PatternDirection.LEFT_HAND },
                                        label = {
                                            Text(
                                                text = "Left",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        modifier = Modifier.height(28.dp)
                                    )

                                    FilterChip(
                                        selected = mapState.patternDirection == PatternDirection.RIGHT_HAND,
                                        onClick = { mapState.patternDirection = PatternDirection.RIGHT_HAND },
                                        label = {
                                            Text(
                                                text = "Right",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Final approach distance selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Final Length:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )

                                var finalExpanded by remember { mutableStateOf(false) }
                                val finalDistances = listOf(0.5, 1.0, 2.0, 3.0, 5.0, 7.0, 10.0)

                                Box {
                                    FilterChip(
                                        selected = false,
                                        onClick = { finalExpanded = !finalExpanded },
                                        label = {
                                            Text(
                                                text = "${if (mapState.patternFinalDistanceNm == mapState.patternFinalDistanceNm.toInt().toDouble()) mapState.patternFinalDistanceNm.toInt().toString() else mapState.patternFinalDistanceNm.toString()} NM",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = if (finalExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.height(28.dp)
                                    )

                                    DropdownMenu(
                                        expanded = finalExpanded,
                                        onDismissRequest = { finalExpanded = false }
                                    ) {
                                        finalDistances.forEach { dist ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(
                                                            text = "${if (dist == dist.toInt().toDouble()) dist.toInt().toString() else dist.toString()} NM Final",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = when {
                                                                dist <= 1.0 -> "Short - Quick pattern"
                                                                dist <= 3.0 -> "Medium - Standard"
                                                                dist <= 5.0 -> "Long - More time"
                                                                else -> "Very Long - Training"
                                                            },
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    mapState.patternFinalDistanceNm = dist
                                                    finalExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Collapsible settings container (default collapsed)
                            var showAltThresholdSettings by remember { mutableStateOf(false) }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showAltThresholdSettings = !showAltThresholdSettings }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Altitude Indicator Settings",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )

                                        IconButton(onClick = { showAltThresholdSettings = !showAltThresholdSettings }) {
                                            Icon(
                                                imageVector = if (showAltThresholdSettings) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (showAltThresholdSettings) "Collapse" else "Expand"
                                            )
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = showAltThresholdSettings,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            var smallTolText by remember { mutableStateOf(mapState.patternAltitudeSmallToleranceFt.toInt().toString()) }
                                            var warnTolText by remember { mutableStateOf(mapState.patternAltitudeWarningToleranceFt.toInt().toString()) }

                                            OutlinedTextField(
                                                value = smallTolText,
                                                onValueChange = { v ->
                                                    smallTolText = v.filter { it.isDigit() }
                                                    val intVal = smallTolText.toIntOrNull()
                                                    if (intVal != null) {
                                                        mapState.patternAltitudeSmallToleranceFt = intVal.toDouble()
                                                        mapState.saveNavigationState()
                                                    }
                                                },
                                                label = { Text("Level tol (ft)") },
                                                singleLine = true,
                                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                                modifier = Modifier.weight(1f)
                                            )

                                            OutlinedTextField(
                                                value = warnTolText,
                                                onValueChange = { v ->
                                                    warnTolText = v.filter { it.isDigit() }
                                                    val intVal = warnTolText.toIntOrNull()
                                                    if (intVal != null) {
                                                        mapState.patternAltitudeWarningToleranceFt = intVal.toDouble()
                                                        mapState.saveNavigationState()
                                                    }
                                                },
                                                label = { Text("Warn tol (ft)") },
                                                singleLine = true,
                                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Pattern details header with collapsible body
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pattern Details",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(onClick = { mapState.showPatternDetails = !mapState.showPatternDetails }) {
                                    Icon(
                                        imageVector = if (mapState.showPatternDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (mapState.showPatternDetails) "Collapse" else "Expand"
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = mapState.showPatternDetails,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Use same multipliers as the generator to keep UI and calculations consistent
                                        val sizeScale = when (mapState.patternSize) {
                                            PatternSize.NORMAL -> 1.25
                                            PatternSize.MEDIUM -> 1.5
                                            PatternSize.LARGE -> 1.75
                                            PatternSize.VERY_LARGE -> 2.25
                                            PatternSize.EXTRA_LARGE -> 3.0
                                        }
                                        val turnMultiplier = if (mapState.patternDirection == PatternDirection.LEFT_HAND) -1 else 1
                                        val selectedRwy = mapState.selectedRunway
                                        val runwayHeading = (selectedRwy?.headingDeg ?: extractRunwayHeading(selectedRwy?.name ?: "") ?: 0.0)

                                        // Compute canonical headings for each leg using runwayHeading and turn direction
                                        val departureHdg = normalizeHeading(runwayHeading)
                                        val crosswindHdg = normalizeHeading(runwayHeading + (90.0 * turnMultiplier))
                                        val downwindHdg = normalizeHeading(runwayHeading + 180.0)
                                        val baseLegHdg = normalizeHeading(downwindHdg + (90.0 * turnMultiplier))
                                        val finalHdg = normalizeHeading(runwayHeading)

                                        val departureHdgInt = departureHdg.toInt()
                                        val crosswindHdgInt = crosswindHdg.toInt()
                                        val downwindHdgInt = downwindHdg.toInt()
                                        val baseLegHdgInt = baseLegHdg.toInt()
                                        val finalHdgInt = finalHdg.toInt()

                                        Text(
                                            text = "• Departure: HDG ${String.format("%03d", departureHdgInt)}° • ${String.format("%.1f", 0.5 * sizeScale)} NM",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Text(
                                            text = "• Crosswind: HDG ${String.format("%03d", crosswindHdgInt)}° • ${String.format("%.1f", mapState.patternSize.downwindDistanceNm * sizeScale)} NM",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        val downwindLengthNm = (selectedRwy?.lengthM?.toDouble() ?: 2000.0) / 1852.0 + mapState.patternFinalDistanceNm + (0.5 * sizeScale)
                                        Text(
                                            text = "• Downwind: HDG ${String.format("%03d", downwindHdgInt)}° • ${String.format("%.1f", downwindLengthNm)} NM",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        val baseExtensionNm = (0.3 + (mapState.patternFinalDistanceNm * 0.2)) * sizeScale
                                        Text(
                                            text = "• Base: HDG ${String.format("%03d", baseLegHdgInt)}° • ${String.format("%.1f", mapState.patternSize.downwindDistanceNm * sizeScale)} NM (turn at ${String.format("%.1f", baseExtensionNm)} NM)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Text(
                                            text = "• Final: HDG ${String.format("%03d", finalHdgInt)}° • ${String.format("%.1f", mapState.patternFinalDistanceNm)} NM",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        // Pattern settings: size, direction, final distance, and show toggle
                                        Spacer(modifier = Modifier.height(6.dp))

                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                // Pattern size dropdown
                                                var sizeExpanded by remember { mutableStateOf(false) }
                                                ExposedDropdownMenuBox(
                                                    expanded = sizeExpanded,
                                                    onExpandedChange = { sizeExpanded = !sizeExpanded },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    OutlinedTextField(
                                                        value = mapState.patternSize.displayName,
                                                        onValueChange = {},
                                                        readOnly = true,
                                                        label = { Text("Pattern Size") },
                                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sizeExpanded) },
                                                        modifier = Modifier.menuAnchor()
                                                    )
                                                    ExposedDropdownMenu(
                                                        expanded = sizeExpanded,
                                                        onDismissRequest = { sizeExpanded = false }
                                                    ) {
                                                        PatternSize.values().forEach { size ->
                                                            DropdownMenuItem(
                                                                text = { Text(size.displayName) },
                                                                onClick = {
                                                                    mapState.patternSize = size
                                                                    mapState.saveNavigationState()
                                                                    sizeExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                // Pattern direction buttons
                                                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Button(
                                                        onClick = {
                                                            mapState.patternDirection = PatternDirection.LEFT_HAND
                                                            mapState.saveNavigationState()
                                                        },
                                                        colors = if (mapState.patternDirection == PatternDirection.LEFT_HAND) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.buttonColors()
                                                    ) {
                                                        Text("Left")
                                                    }
                                                    Button(
                                                        onClick = {
                                                            mapState.patternDirection = PatternDirection.RIGHT_HAND
                                                            mapState.saveNavigationState()
                                                        },
                                                        colors = if (mapState.patternDirection == PatternDirection.RIGHT_HAND) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.buttonColors()
                                                    ) {
                                                        Text("Right")
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                var finalDistText by remember { mutableStateOf(String.format("%.1f", mapState.patternFinalDistanceNm)) }
                                                OutlinedTextField(
                                                    value = finalDistText,
                                                    onValueChange = { v ->
                                                        val filtered = v.filter { it.isDigit() || it == '.' }
                                                        finalDistText = filtered
                                                        val d = filtered.toDoubleOrNull()
                                                        if (d != null) {
                                                            mapState.patternFinalDistanceNm = d
                                                            mapState.saveNavigationState()
                                                        }
                                                    },
                                                    label = { Text("Final dist (NM)") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(text = "Show pattern")
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Switch(checked = mapState.showTrafficPattern, onCheckedChange = {
                                                        mapState.showTrafficPattern = it
                                                        mapState.saveNavigationState()
                                                    })
                                                }
                                            }
                                        }

                                        // Show pattern altitude and current altitude with indicator
                                        val patternAlt = mapState.patternSize.patternAltitudeFt
                                        val currentAltMeters = flightData?.altitude?.let { it.toDouble() } ?: Double.NaN
                                        val currentAlt = if (currentAltMeters.isNaN()) Double.NaN else currentAltMeters * 3.28084 // convert m -> ft
                                        val currentAltDisplay = if (currentAlt.isNaN()) "n/a" else String.format("%d ft", currentAlt.toInt())
                                        val diff = if (currentAlt.isNaN()) null else currentAlt - patternAlt
                                        val smallTolerance = mapState.patternAltitudeSmallToleranceFt
                                        val warningTol = mapState.patternAltitudeWarningToleranceFt

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Start
                                        ) {
                                            Text(
                                                text = "Pattern Altitude: $patternAlt ft AGL",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // Indicator between pattern and current altitude
                                            if (diff != null) {
                                                val absDiff = kotlin.math.abs(diff)
                                                when {
                                                    // Within small tolerance -> show green '≈'
                                                    absDiff <= smallTolerance -> {
                                                        Text(
                                                            text = "≈",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = androidx.compose.ui.graphics.Color(0xFF00C853)
                                                        )
                                                    }
                                                    else -> {
                                                        // Use yellow when within warningTol, red when beyond
                                                        val col = if (absDiff <= warningTol) androidx.compose.ui.graphics.Color(0xFFFFA000) else androidx.compose.ui.graphics.Color(0xFFD50000)
                                                        if (diff < 0) {
                                                            // Below pattern -> show UP arrow
                                                            Icon(
                                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                                contentDescription = "Need to climb",
                                                                tint = col
                                                            )
                                                        } else {
                                                            // Above pattern -> show DOWN arrow
                                                            Icon(
                                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                                contentDescription = "Need to descend",
                                                                tint = col
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                Text(
                                                    text = "?",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Text(
                                                text = "Current: $currentAltDisplay",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        mapState.targetRunways.forEachIndexed { index, runway ->
                            val baseHeading = runway.headingDeg ?: extractRunwayHeading(runway.name) ?: 0.0
                            val heading1 = baseHeading.toInt()
                            val heading2 = ((heading1 + 180) % 360)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Direction 1
                                FilterChip(
                                    selected = mapState.selectedRunwayIndex == (index * 2),
                                    onClick = {
                                        // When switching from Direction 2 to Direction 1, flip pattern direction
                                        if (mapState.selectedRunwayIndex == (index * 2 + 1)) {
                                            mapState.patternDirection = if (mapState.patternDirection == PatternDirection.LEFT_HAND) {
                                                PatternDirection.RIGHT_HAND
                                            } else {
                                                PatternDirection.LEFT_HAND
                                            }
                                        }
                                        mapState.selectedRunwayIndex = index * 2
                                        mapState.selectedRunway = runway
                                        // Store the runway heading for display
                                        val calcHeading = runway.headingDeg ?: extractRunwayHeading(runway.name) ?: 0.0
                                        mapState.selectedRunwayHeading = calcHeading
                                        // Create route to approach endpoint
                                        val target = mapState.originalAirportTarget
                                        if (target != null) {
                                            val distanceMeters = mapState.finalApproachDistanceNm * 1852.0
                                            val rad = Math.toRadians(calcHeading)
                                            val lat1 = Math.toRadians(target.latitude)
                                            val lon1 = Math.toRadians(target.longitude)
                                            val dLat = distanceMeters * Math.cos(rad) / 6371000.0
                                            val dLon = distanceMeters * Math.sin(rad) / (6371000.0 * Math.cos(lat1))
                                            val endLat = lat1 + dLat
                                            val endLon = lon1 + dLon
                                            val endpoint = GeoPoint(Math.toDegrees(endLat), Math.toDegrees(endLon))

                                            // Update navigation to approach endpoint (red line will auto-update)
                                            // Create temporary target at endpoint
                                            val approachTarget = target.copy(
                                                id = -1,
                                                name = "${target.name} RWY ${String.format("%02d", heading1 / 10)}",
                                                latitude = endpoint.latitude,
                                                longitude = endpoint.longitude
                                            )
                                            mapState.activeNavigationTarget = approachTarget
                                            // Keep runway approach lines visible (don't set showRunwayApproach = false)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = "RWY ${String.format("%02d", heading1 / 10)} (${heading1}°)",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary
                                    )
                                )

                                // Direction 2
                                FilterChip(
                                    selected = mapState.selectedRunwayIndex == (index * 2 + 1),
                                    onClick = {
                                        // When switching from Direction 1 to Direction 2, flip pattern direction
                                        if (mapState.selectedRunwayIndex == (index * 2)) {
                                            mapState.patternDirection = if (mapState.patternDirection == PatternDirection.LEFT_HAND) {
                                                PatternDirection.RIGHT_HAND
                                            } else {
                                                PatternDirection.LEFT_HAND
                                            }
                                        }
                                        mapState.selectedRunwayIndex = index * 2 + 1
                                        mapState.selectedRunway = runway
                                        // Store the opposite runway heading for display
                                        val calcHeading = runway.headingDeg ?: extractRunwayHeading(runway.name) ?: 0.0
                                        val oppositeHeading = (calcHeading + 180) % 360
                                        mapState.selectedRunwayHeading = oppositeHeading
                                        // Create route to opposite approach endpoint
                                        val target = mapState.originalAirportTarget
                                        if (target != null) {
                                            val distanceMeters = mapState.finalApproachDistanceNm * 1852.0
                                            val heading2Rad = Math.toRadians(oppositeHeading)
                                            val lat1 = Math.toRadians(target.latitude)
                                            val lon1 = Math.toRadians(target.longitude)
                                            val dLat = distanceMeters * Math.cos(heading2Rad) / 6371000.0
                                            val dLon = distanceMeters * Math.sin(heading2Rad) / (6371000.0 * Math.cos(lat1))
                                            val endLat = lat1 + dLat
                                            val endLon = lon1 + dLon
                                            val endpoint = GeoPoint(Math.toDegrees(endLat), Math.toDegrees(endLon))

                                            val approachTarget = target.copy(
                                                id = -1,
                                                name = "${target.name} RWY ${String.format("%02d", heading2 / 10)}",
                                                latitude = endpoint.latitude,
                                                longitude = endpoint.longitude
                                            )
                                            mapState.activeNavigationTarget = approachTarget
                                            // Keep runway approach lines visible (don't set showRunwayApproach = false)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = "RWY ${String.format("%02d", heading2 / 10)} (${heading2}°)",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                        }
                    }
                    }
                }
            }
        }
    }
}

/**
 * Helper function to extract runway heading from name
 */
private fun extractRunwayHeading(runwayName: String?): Double? {
    if (runwayName == null) return null
    // Extract first 2 digits from runway name (e.g., "09L" -> 09, "27R" -> 27)
    val headingStr = runwayName.take(2)
    return headingStr.toIntOrNull()?.let { it * 10.0 }
}

/**
 * Normalize heading to 0..360
 */
private fun normalizeHeading(heading: Double): Double {
    var h = heading % 360.0
    if (h < 0) h += 360.0
    return h
}
