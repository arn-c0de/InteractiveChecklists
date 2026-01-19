package com.example.checklist_interactive.ui.maps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.example.checklist_interactive.R
import com.example.checklist_interactive.data.tactical.LocationEntity

/**
 * Overlay displaying nearest airports sorted by distance
 * Appears as a sidebar/panel on the map view
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearestAirportsOverlay(
    visible: Boolean,
    airports: List<AirportWithDistance>,
    useNauticalMiles: Boolean,
    isLoading: Boolean = false,
    onClose: () -> Unit,
    onAirportClick: (LocationEntity) -> Unit,
    onCenterOnAirport: (LocationEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val panelWidthFraction = if (isLandscape) 0.35f else 0.85f

    // Use a Dialog so the overlay is rendered above the MapView (AndroidView)
    if (visible) {
        // Log to confirm Dialog entry
        androidx.compose.runtime.LaunchedEffect(Unit) {
            android.util.Log.d("NearestAirportsOverlay", "🛬 Showing Dialog overlay (visible=true)")
        }

        androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f))
                    .clickable(onClick = onClose)
            ) {
                // Panel with entrance animation
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                    exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(panelWidthFraction)
                            .align(Alignment.CenterEnd)
                            .clickable(enabled = false, onClick = {}), // Prevent clicks from passing through
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            // Header
                            NearestAirportsHeader(
                                airportCount = airports.size,
                                onClose = onClose
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Content
                            if (isLoading) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else if (airports.isEmpty()) {
                                NearestAirportsEmptyState()
                            } else {
                                NearestAirportsList(
                                    airports = airports,
                                    useNauticalMiles = useNauticalMiles,
                                    onAirportClick = onAirportClick,
                                    onCenterOnAirport = onCenterOnAirport
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Header section with title and close button
 */
@Composable
private fun NearestAirportsHeader(
    airportCount: Int,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Nearest Airports Header" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.nearest_airports_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (airportCount > 0) {
                Text(
                    text = stringResource(R.string.nearest_airports_count, airportCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.semantics {
                contentDescription = "Close nearest airports list"
            }
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_close)
            )
        }
    }
}

/**
 * Empty state when no airports are found
 */
@Composable
private fun NearestAirportsEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "No airports found" },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Flight,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.nearest_airports_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * List of nearest airports
 */
@Composable
private fun NearestAirportsList(
    airports: List<AirportWithDistance>,
    useNauticalMiles: Boolean,
    onAirportClick: (LocationEntity) -> Unit,
    onCenterOnAirport: (LocationEntity) -> Unit
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "List of ${airports.size} nearest airports" }
    ) {
        items(
            items = airports,
            key = { it.location.id }
        ) { airport ->
            NearestAirportCard(
                airport = airport,
                useNauticalMiles = useNauticalMiles,
                onAirportClick = onAirportClick,
                onCenterOnAirport = onCenterOnAirport
            )
        }
    }
}

/**
 * Individual airport card in the list
 */
@Composable
private fun NearestAirportCard(
    airport: AirportWithDistance,
    useNauticalMiles: Boolean,
    onAirportClick: (LocationEntity) -> Unit,
    onCenterOnAirport: (LocationEntity) -> Unit
) {
    val location = airport.location

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAirportClick(location) }
            .semantics {
                contentDescription = "Airport ${location.name}, " +
                    "distance ${airport.getFormattedDistance(useNauticalMiles)}, " +
                    "bearing ${airport.getFormattedBearing()}"
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Airport name and ICAO
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = location.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        location.icao?.let { icao ->
                            Text(
                                text = icao,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        location.iata?.let { iata ->
                            if (iata != location.icao) {
                                Text(
                                    text = "($iata)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Center button
                IconButton(
                    onClick = { onCenterOnAirport(location) },
                    modifier = Modifier.semantics {
                        contentDescription = "Center map on ${location.name}"
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = stringResource(R.string.map_center_on_map),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Distance and bearing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Distance
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.nearest_airports_distance_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = airport.getFormattedDistance(useNauticalMiles),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Bearing
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.nearest_airports_bearing_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = airport.getFormattedBearing(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Runway info (if available)
            airport.getRunwaySummary()?.let { runwayInfo ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = runwayInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Elevation (if available)
            location.elevationM?.let { elevation ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.nearest_airports_elevation,
                        elevation.toInt(),
                        (elevation * 3.28084).toInt()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
