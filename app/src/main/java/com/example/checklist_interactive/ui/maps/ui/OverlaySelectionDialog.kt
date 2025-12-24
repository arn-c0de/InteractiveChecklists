package com.example.checklist_interactive.ui.maps.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.R

/**
 * Dialog for managing map overlay visibility settings
 *
 * Controls:
 * - Compass overlay
 * - Range rings with distance configuration
 * - MGRS grid overlay
 */
@Composable
fun OverlaySelectionDialog(
    compassEnabled: Boolean,
    rangeRingsEnabled: Boolean,
    rangeRingsMaxNm: Int,
    mgrsGridEnabled: Boolean,
    flightInstrumentsEnabled: Boolean,
    onDismiss: () -> Unit,
    onToggleCompass: (Boolean) -> Unit,
    onToggleRangeRings: (Boolean) -> Unit,
    onChangeRangeRingsMaxNm: (Int) -> Unit,
    onToggleMgrsGrid: (Boolean) -> Unit,
    onToggleFlightInstruments: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_overlays_dialog_title)) },
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
                        Text(stringResource(R.string.map_overlay_flight_instruments), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.map_overlay_flight_instruments_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = flightInstrumentsEnabled, onCheckedChange = onToggleFlightInstruments)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}
