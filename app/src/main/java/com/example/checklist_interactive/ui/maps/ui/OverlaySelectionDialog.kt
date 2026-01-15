package com.example.checklist_interactive.ui.maps.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.R
import com.example.checklist_interactive.ui.maps.BorderEpoch
import com.example.checklist_interactive.ui.maps.CountryBordersOverlay
import com.example.checklist_interactive.ui.common.rememberWindowSize
import com.example.checklist_interactive.ui.common.rememberResponsiveDimensions

/**
 * Dialog for managing map overlay visibility settings
 *
 * Controls:
 * - Compass overlay
 * - Range rings with distance configuration
 * - MGRS grid overlay
 * - Flight path tracking and recording
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlaySelectionDialog(
    compassEnabled: Boolean,
    rangeRingsEnabled: Boolean,
    rangeRingsMaxNm: Int,
    mgrsGridEnabled: Boolean,
    countryBordersEnabled: Boolean,
    borderEpoch: BorderEpoch,
    flightInstrumentsEnabled: Boolean,
    markerLabelsEnabled: Boolean,
    flightPathEnabled: Boolean,
    flightPathRecording: Boolean,
    flightPathPointCount: Int,
    flightPathIntervalSeconds: Int,
    onDismiss: () -> Unit,
    onToggleCompass: (Boolean) -> Unit,
    onToggleRangeRings: (Boolean) -> Unit,
    onChangeRangeRingsMaxNm: (Int) -> Unit,
    onToggleMgrsGrid: (Boolean) -> Unit,
    onToggleCountryBorders: (Boolean) -> Unit,
    onChangeBorderEpoch: (BorderEpoch) -> Unit,
    onToggleFlightInstruments: (Boolean) -> Unit,
    onToggleMarkerLabels: (Boolean) -> Unit,
    onToggleFlightPath: (Boolean) -> Unit,
    onStartTracking: () -> Unit,
    onPauseTracking: () -> Unit,
    onClearFlightPath: () -> Unit,
    onChangeFlightPathInterval: (Int) -> Unit
) {
    val windowSize = rememberWindowSize()

    // Responsive dialog width based on device class
    val maxDialogWidth = when {
        windowSize.widthDp < 360 -> 350.dp   // Very small phones
        windowSize.widthDp < 600 -> 450.dp   // Phones
        windowSize.widthDp < 840 -> 600.dp   // Medium tablets
        else -> 700.dp                        // Large tablets
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_overlays_dialog_title)) },
        modifier = Modifier.widthIn(max = maxDialogWidth),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.map_compass), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.map_compass_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = compassEnabled, onCheckedChange = onToggleCompass)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.map_range_rings), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.map_range_rings_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = rangeRingsEnabled, onCheckedChange = onToggleRangeRings)
                }

                if (rangeRingsEnabled) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(stringResource(R.string.map_range_rings_max_label, rangeRingsMaxNm), style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = rangeRingsMaxNm.toFloat(),
                            onValueChange = { onChangeRangeRingsMaxNm(it.toInt()) },
                            valueRange = 1f..500f,
                            steps = 499,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(stringResource(R.string.map_range_rings_max_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.map_overlay_mgrs_grid), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.map_overlay_mgrs_grid_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = mgrsGridEnabled, onCheckedChange = onToggleMgrsGrid)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.map_overlay_country_borders), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.map_overlay_country_borders_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = countryBordersEnabled, onCheckedChange = onToggleCountryBorders)
                }

                // Border Epoch Dropdown (shown when country borders are enabled)
                if (countryBordersEnabled) {
                    val context = LocalContext.current
                    val availableEpochs = remember { CountryBordersOverlay.getAvailableEpochs(context) }
                    var epochDropdownExpanded by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.map_overlay_border_epoch_label),
                            style = MaterialTheme.typography.bodySmall
                        )
                        ExposedDropdownMenuBox(
                            expanded = epochDropdownExpanded,
                            onExpandedChange = { epochDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = borderEpoch.displayName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = epochDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = epochDropdownExpanded,
                                onDismissRequest = { epochDropdownExpanded = false }
                            ) {
                                availableEpochs.forEach { epoch ->
                                    DropdownMenuItem(
                                        text = { Text(epoch.displayName) },
                                        onClick = {
                                            onChangeBorderEpoch(epoch)
                                            epochDropdownExpanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.map_overlay_border_epoch_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.map_overlay_flight_instruments), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.map_overlay_flight_instruments_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = flightInstrumentsEnabled, onCheckedChange = onToggleFlightInstruments)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.map_marker_labels_toggle), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.map_marker_labels_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = markerLabelsEnabled, onCheckedChange = onToggleMarkerLabels)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Flight Path Section
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.map_overlay_flight_path), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.map_overlay_flight_path_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (flightPathEnabled && flightPathPointCount > 0) {
                            Text(
                                stringResource(R.string.map_overlay_flight_path_points, flightPathPointCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Switch(checked = flightPathEnabled, onCheckedChange = onToggleFlightPath)
                }

                // Recording interval slider (shown when path recording is enabled)
                if (flightPathEnabled) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.map_overlay_flight_path_interval_desc, flightPathIntervalSeconds),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("1s", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                            Slider(
                                value = flightPathIntervalSeconds.toFloat(),
                                onValueChange = { onChangeFlightPathInterval(it.toInt()) },
                                valueRange = 1f..60f,
                                steps = 58,
                                modifier = Modifier.weight(1f)
                            )
                            Text("60s", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(1, 2, 5, 10, 30, 60).forEach { seconds ->
                                TextButton(
                                    onClick = { onChangeFlightPathInterval(seconds) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("${seconds}s", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // Start/Pause Tracking Buttons
                if (flightPathEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onStartTracking,
                            enabled = !flightPathRecording,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.map_overlay_flight_path_start))
                        }
                        OutlinedButton(
                            onClick = onPauseTracking,
                            enabled = flightPathRecording,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.map_overlay_flight_path_pause))
                        }
                    }
                }

                // Clear Path Button (shown when path exists)
                if (flightPathPointCount > 0) {
                    OutlinedButton(
                        onClick = onClearFlightPath,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.map_overlay_flight_path_clear))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}
