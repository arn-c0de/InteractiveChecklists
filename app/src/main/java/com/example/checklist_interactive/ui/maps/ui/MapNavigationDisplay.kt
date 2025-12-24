package com.example.checklist_interactive.ui.maps.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.checklist_interactive.data.datapad.FlightData
import com.example.checklist_interactive.data.tactical.LocationEntity
import com.example.checklist_interactive.data.tactical.RunwayEntity
import com.example.checklist_interactive.ui.maps.MapViewerState
import com.example.checklist_interactive.ui.maps.navigation.PatternDirection
import com.example.checklist_interactive.ui.maps.navigation.PatternSize
import com.example.checklist_interactive.R
import org.osmdroid.util.GeoPoint
import kotlin.math.abs

/**
 * Active Navigation Display - Shows route information and runway approach options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapNavigationDisplay(
    flightData: FlightData?,
    modifier: Modifier = Modifier,
    activeNavigationTarget: LocationEntity?,
    navigationDistanceNm: Double?,
    navigationHeading: Double?,
    showNavigationDetails: Boolean,
    onShowNavigationDetailsChange: (Boolean) -> Unit,
    selectedRunwayHeading: Double?,
    originalAirportTarget: LocationEntity?,
    targetRunways: List<RunwayEntity>,
    showRunwayApproach: Boolean,
    onShowRunwayApproachChange: (Boolean) -> Unit,
    finalApproachDistanceNm: Double,
    onFinalApproachDistanceNmChange: (Double) -> Unit,
    showTrafficPattern: Boolean,
    onShowTrafficPatternChange: (Boolean) -> Unit,
    patternSize: PatternSize,
    onPatternSizeChange: (PatternSize) -> Unit,
    patternDirection: PatternDirection,
    onPatternDirectionChange: (PatternDirection) -> Unit,
    patternFinalDistanceNm: Double,
    onPatternFinalDistanceNmChange: (Double) -> Unit,
    selectedRunwayIndex: Int?,
    onSelectedRunwayIndexChange: (Int?) -> Unit,
    /* Pattern details visibility (hoisted) */
    showPatternDetails: Boolean,
    onShowPatternDetailsChange: (Boolean) -> Unit,
    selectedRunway: RunwayEntity?,
    onSelectedRunwayChange: (RunwayEntity?) -> Unit,
    onActiveNavigationTargetChange: (LocationEntity?) -> Unit,
    onOriginalAirportTargetChange: (LocationEntity?) -> Unit,
    onSelectedRunwayHeadingChange: (Double?) -> Unit,
    customPatternAltitudeAglFt: Int?,
    onCustomPatternAltitudeAglFtChange: (Int?) -> Unit,
    patternAltitudeSmallToleranceFt: Double,
    onPatternAltitudeSmallToleranceFtChange: (Double) -> Unit,
    patternAltitudeWarningToleranceFt: Double,
    onPatternAltitudeWarningToleranceFtChange: (Double) -> Unit,
    saveNavigationState: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    // Persistent height fraction (0.0 to 1.0 of screen height)
    val prefs = context.getSharedPreferences("map_navigation_prefs", Context.MODE_PRIVATE)
    val KEY_HEIGHT_FRACTION = "nav_display_height_fraction"
    val savedFraction = prefs.getFloat(KEY_HEIGHT_FRACTION, 0.35f)
    val heightMin = 0.15f
    val heightMax = 0.7f
    var heightFraction by rememberSaveable { mutableStateOf(savedFraction.coerceIn(heightMin, heightMax)) }
    
    // Persist height when changed
    LaunchedEffect(heightFraction) {
        prefs.edit().putFloat(KEY_HEIGHT_FRACTION, heightFraction).apply()
    }
    
    // Persistent opacity
    val KEY_OPACITY = "nav_display_opacity"
    val savedOpacity = prefs.getFloat(KEY_OPACITY, 1.0f)
    var cardOpacity by rememberSaveable { mutableStateOf(savedOpacity.coerceIn(0.25f, 1.0f)) }
    var showOpacitySlider by remember { mutableStateOf(false) }
    
    // Persist opacity when changed
    LaunchedEffect(cardOpacity) {
        prefs.edit().putFloat(KEY_OPACITY, cardOpacity).apply()
    }
    
    val cardHeightDp = (configuration.screenHeightDp.toFloat() * heightFraction).dp
    
    if (activeNavigationTarget != null) {
        Box(modifier = modifier.widthIn(max = 500.dp)) {
            Card(
                modifier = Modifier
                    // When details are visible, use the persisted height as a maximum so the card can shrink if content is smaller; otherwise let the card wrap to content
                    .then(if (showNavigationDetails) Modifier.heightIn(max = cardHeightDp) else Modifier.wrapContentHeight())
                    .fillMaxWidth()
                    .animateContentSize(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = cardOpacity)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                // Don't force the column to fill the card's max size so the card can shrink when collapsed
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Fixed header (always visible, non-scrollable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clickable { onShowNavigationDetailsChange(!showNavigationDetails); saveNavigationState() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.map_nav_route_to, activeNavigationTarget?.name ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            navigationDistanceNm?.let { dist ->
                                Text(
                                    text = stringResource(R.string.map_nav_dist_nm, dist),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            navigationHeading?.let { hdg ->
                                Text(
                                    text = stringResource(R.string.map_nav_hdg, hdg),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        // Show final runway heading when runway selected
                        selectedRunwayHeading?.let { rwyHdg ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.map_nav_final),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = stringResource(R.string.map_nav_rwy, rwyHdg),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )

                                // Calculate and display distance to airport
                                val airport = originalAirportTarget
                                if (airport != null && flightData != null &&
                                    flightData.latitude != 0.0 && flightData.longitude != 0.0) {
                                    val airportPos = GeoPoint(airport.latitude, airport.longitude)
                                    val playerPos = GeoPoint(flightData.latitude, flightData.longitude)
                                    val distanceMeters = playerPos.distanceToAsDouble(airportPos)
                                    val distanceNm = distanceMeters / 1852.0
                                    Text(
                                        text = stringResource(R.string.map_nav_dist_nm, distanceNm),
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
                        val runwayElevationFt = (originalAirportTarget?.elevationM?.times(3.28084))?.toInt() ?: 0
                        val patternAltAglCompact = customPatternAltitudeAglFt ?: patternSize.patternAltitudeAglFt
                        val patternAltMslCompact = patternAltAglCompact + runwayElevationFt
                        val currentAltMetersCompact = flightData?.altitude?.let { it.toDouble() } ?: Double.NaN
                        val currentAltCompact = if (currentAltMetersCompact.isNaN()) Double.NaN else currentAltMetersCompact * 3.28084 // convert m -> ft
                        val currentAltCompactDisplay = if (currentAltCompact.isNaN()) stringResource(R.string.map_nav_current_alt_unavailable) else stringResource(R.string.map_nav_current_alt_ft, currentAltCompact.toInt())
                        val diffCompact = if (currentAltCompact.isNaN()) null else currentAltCompact - patternAltMslCompact
                        val smallTolerance = patternAltitudeSmallToleranceFt
                        val warningTol = patternAltitudeWarningToleranceFt

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.map_nav_pattern_alt, patternAltMslCompact),
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
                                            Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.map_nav_alt_climb), tint = col)
                                        } else {
                                            Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.map_nav_alt_descend), tint = col)
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
                            onClick = { onShowNavigationDetailsChange(!showNavigationDetails); saveNavigationState() }
                        ) {
                            Icon(
                                imageVector = if (showNavigationDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showNavigationDetails) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        // Land button (only show if target has runways)
                        if (targetRunways.isNotEmpty()) {
                            FilledTonalIconButton(
                                onClick = {
                                    onShowRunwayApproachChange(!showRunwayApproach)
                                    if (!showRunwayApproach) {
                                        onSelectedRunwayIndexChange(null)
                                        onSelectedRunwayHeadingChange(null)
                                        onSelectedRunwayChange(null)
                                    }
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (showRunwayApproach)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (showRunwayApproach)
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlightLand,
                                    contentDescription = stringResource(R.string.map_nav_approach_button)
                                )
                            }
                        }

                        // Cancel button
                        IconButton(
                            onClick = {
                                onActiveNavigationTargetChange(null)
                                onOriginalAirportTargetChange(null)
                                onShowRunwayApproachChange(false)
                                // Also hide any traffic pattern overlays when cancelling navigation
                                onShowTrafficPatternChange(false)
                                onSelectedRunwayIndexChange(null)
                                onSelectedRunwayHeadingChange(null)
                                onSelectedRunwayChange(null)
                                saveNavigationState()
                            }
                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = stringResource(R.string.map_nav_cancel_button),
                                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                                            )                        }
                    }
                }

                // Collapsible details section (scrollable)
                AnimatedVisibility(
                    visible = showNavigationDetails,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Runway selection (when approach mode active)
                        if (showRunwayApproach && targetRunways.isNotEmpty()) {
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
                                        text = stringResource(R.string.map_nav_select_runway),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
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
                                            onClick = { onShowTrafficPatternChange(!showTrafficPattern) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (showTrafficPattern) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                contentColor = if (showTrafficPattern) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            ),
                                            modifier = Modifier.height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.map_nav_pattern_button),
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
                                                        text = stringResource(R.string.map_nav_final_dist_nm, finalApproachDistanceNm.let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() }),
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
                                                            onFinalApproachDistanceNmChange(dist)
                                                            expanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Runway selection (moved up so available without opening pattern config)
                                if (targetRunways.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Runway chips
                                    targetRunways.forEachIndexed { index, runway ->
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
                                                selected = selectedRunwayIndex == (index * 2),
                                                onClick = {
                                                    // When switching from Direction 2 to Direction 1, flip pattern direction
                                                    if (selectedRunwayIndex == (index * 2 + 1)) {
                                                        onPatternDirectionChange(if (patternDirection == PatternDirection.LEFT_HAND) {
                                                            PatternDirection.RIGHT_HAND
                                                        } else {
                                                            PatternDirection.LEFT_HAND
                                                        })
                                                    }
                                                    onSelectedRunwayIndexChange(index * 2)
                                                    onSelectedRunwayChange(runway)
                                                    // Store the runway heading for display
                                                    val calcHeading = runway.headingDeg ?: extractRunwayHeading(runway.name) ?: 0.0
                                                    onSelectedRunwayHeadingChange(calcHeading)
                                                    // Create route to approach endpoint
                                                    val target = originalAirportTarget
                                                    if (target != null) {
                                                        val distanceMeters = finalApproachDistanceNm * 1852.0
                                                        val rad = Math.toRadians(calcHeading)
                                                        val lat1 = Math.toRadians(target.latitude)
                                                        val lon1 = Math.toRadians(target.longitude)
                                                        val dLat = distanceMeters * Math.cos(rad) / 6371000.0
                                                        val dLon = distanceMeters * Math.sin(rad) / (6371000.0 * Math.cos(lat1))
                                                        val endLat = lat1 + dLat
                                                        val endLon = lon1 + dLon
                                                        val endpoint = GeoPoint(Math.toDegrees(endLat), Math.toDegrees(endLon))

                                                        // Update navigation to approach endpoint (red line will auto-update)
                                                        val approachTarget = target.copy(
                                                            id = -1,
                                                            name = context.getString(R.string.map_nav_approach_target_name, target.name, heading1 / 10),
                                                            latitude = endpoint.latitude,
                                                            longitude = endpoint.longitude
                                                        )
                                                        onActiveNavigationTargetChange(approachTarget)
                                                    }
                                                },
                                                label = {
                                                    Text(
                                                        text = stringResource(R.string.map_nav_rwy_chip, heading1 / 10, heading1),
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
                                                selected = selectedRunwayIndex == (index * 2 + 1),
                                                onClick = {
                                                    // When switching from Direction 1 to Direction 2, flip pattern direction
                                                    if (selectedRunwayIndex == (index * 2)) {
                                                        onPatternDirectionChange(if (patternDirection == PatternDirection.LEFT_HAND) {
                                                            PatternDirection.RIGHT_HAND
                                                        } else {
                                                            PatternDirection.LEFT_HAND
                                                        })
                                                    }
                                                    onSelectedRunwayIndexChange(index * 2 + 1)
                                                    onSelectedRunwayChange(runway)
                                                    val calcHeading = runway.headingDeg ?: extractRunwayHeading(runway.name) ?: 0.0
                                                    val oppositeHeading = (calcHeading + 180) % 360
                                                    onSelectedRunwayHeadingChange(oppositeHeading)
                                                    val target = originalAirportTarget
                                                    if (target != null) {
                                                        val distanceMeters = finalApproachDistanceNm * 1852.0
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
                                                            name = context.getString(R.string.map_nav_approach_target_name, target.name, heading2 / 10),
                                                            latitude = endpoint.latitude,
                                                            longitude = endpoint.longitude
                                                        )
                                                        onActiveNavigationTargetChange(approachTarget)
                                                    }
                                                },
                                                label = {
                                                    Text(
                                                        text = stringResource(R.string.map_nav_rwy_chip, heading2 / 10, heading2),
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

                                // Pattern configuration (when pattern mode active)
                                if (showTrafficPattern) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = stringResource(R.string.map_nav_pattern_config),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Runway selection moved up above to avoid duplication
                                    

                                    // Pattern size selector
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.map_nav_pattern_size),
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
                                                        text = patternSize.displayName,
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
                                                                    text = stringResource(R.string.map_nav_pattern_size_desc, size.downwindDistanceNm, size.patternAltitudeAglFt),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            onPatternSizeChange(size)
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
                                            text = stringResource(R.string.map_nav_pattern_direction),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(
                                                selected = patternDirection == PatternDirection.LEFT_HAND,
                                                onClick = { onPatternDirectionChange(PatternDirection.LEFT_HAND) },
                                                label = {
                                                    Text(
                                                        text = "Left",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                },
                                                modifier = Modifier.height(28.dp)
                                            )

                                            FilterChip(
                                                selected = patternDirection == PatternDirection.RIGHT_HAND,
                                                onClick = { onPatternDirectionChange(PatternDirection.RIGHT_HAND) },
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
                                            text = stringResource(R.string.map_nav_final_length),
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
                                                        text = stringResource(R.string.map_nav_pattern_final_dist_nm, patternFinalDistanceNm.let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() }),
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
                                                                    text = stringResource(R.string.map_nav_final_dist_nm, if (dist == dist.toInt().toDouble()) dist.toInt().toString() else dist.toString()),
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                                Text(
                                                                    text = when {
                                                                        dist <= 1.0 -> stringResource(R.string.map_nav_pattern_final_desc_short)
                                                                        dist <= 3.0 -> stringResource(R.string.map_nav_pattern_final_desc_medium)
                                                                        dist <= 5.0 -> stringResource(R.string.map_nav_pattern_final_desc_long)
                                                                        else -> stringResource(R.string.map_nav_pattern_final_desc_very_long)
                                                                    },
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            onPatternFinalDistanceNmChange(dist)
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
                                                    text = stringResource(R.string.map_nav_alt_indicator_settings),
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
                                                    var smallTolText by remember { mutableStateOf(patternAltitudeSmallToleranceFt.toInt().toString()) }
                                                    var warnTolText by remember { mutableStateOf(patternAltitudeWarningToleranceFt.toInt().toString()) }

                                                    OutlinedTextField(
                                                        value = smallTolText,
                                                        onValueChange = { v ->
                                                            smallTolText = v.filter { it.isDigit() }
                                                            val intVal = smallTolText.toIntOrNull()
                                                            if (intVal != null) {
                                                                onPatternAltitudeSmallToleranceFtChange(intVal.toDouble())
                                                                saveNavigationState()
                                                            }
                                                        },
                                                                                                                    label = { Text(stringResource(R.string.map_nav_level_tolerance_ft)) },                                                        singleLine = true,
                                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                                        modifier = Modifier.weight(1f)
                                                    )

                                                    OutlinedTextField(
                                                        value = warnTolText,
                                                        onValueChange = { v ->
                                                            warnTolText = v.filter { it.isDigit() }
                                                            val intVal = warnTolText.toIntOrNull()
                                                            if (intVal != null) {
                                                                onPatternAltitudeWarningToleranceFtChange(intVal.toDouble())
                                                                saveNavigationState()
                                                            }
                                                        },
                                                                                                                    label = { Text(stringResource(R.string.map_nav_warn_tolerance_ft)) },                                                        singleLine = true,
                                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Pattern details header with collapsible body (visibility hoisted to parent state)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.map_nav_pattern_details),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        IconButton(onClick = { onShowPatternDetailsChange(!showPatternDetails) }) {
                                            Icon(
                                                imageVector = if (showPatternDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (showPatternDetails) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand)
                                            )
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = showPatternDetails,
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
                                                val sizeScale = when (patternSize) {
                                                    PatternSize.NORMAL -> 1.25
                                                    PatternSize.MEDIUM -> 1.5
                                                    PatternSize.LARGE -> 1.75
                                                    PatternSize.VERY_LARGE -> 2.25
                                                    PatternSize.EXTRA_LARGE -> 3.0
                                                }
                                                val turnMultiplier = if (patternDirection == PatternDirection.LEFT_HAND) -1 else 1
                                                val selectedRwy = selectedRunway
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
                                                    text = stringResource(R.string.map_nav_pattern_leg_departure, departureHdgInt, (0.5 * sizeScale).toFloat()),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                Text(
                                                    text = stringResource(R.string.map_nav_pattern_leg_crosswind, crosswindHdgInt, (patternSize.downwindDistanceNm * sizeScale).toFloat()),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                val downwindLengthNm = (selectedRwy?.lengthM?.toDouble() ?: 2000.0) / 1852.0 + patternFinalDistanceNm + (0.5 * sizeScale)
                                                Text(
                                                    text = stringResource(R.string.map_nav_pattern_leg_downwind, downwindHdgInt, downwindLengthNm.toFloat()),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                val baseExtensionNm = (0.3 + (patternFinalDistanceNm * 0.2)) * sizeScale
                                                Text(
                                                    text = stringResource(R.string.map_nav_pattern_leg_base, baseLegHdgInt, (patternSize.downwindDistanceNm * sizeScale).toFloat(), baseExtensionNm.toFloat()),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                Text(
                                                    text = stringResource(R.string.map_nav_pattern_leg_final, finalHdgInt, patternFinalDistanceNm.toFloat()),
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
                                                                value = patternSize.displayName,
                                                                onValueChange = {},
                                                                readOnly = true,
                                                                label = { Text(stringResource(R.string.map_nav_pattern_size_label)) },
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
                                                                            onPatternSizeChange(size)
                                                                            saveNavigationState()
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
                                                                    onPatternDirectionChange(PatternDirection.LEFT_HAND)
                                                                    saveNavigationState()
                                                                },
                                                                colors = if (patternDirection == PatternDirection.LEFT_HAND) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.buttonColors()
                                                            ) {
                                                                Text(stringResource(R.string.map_nav_pattern_dir_left))
                                                            }
                                                            Button(
                                                                onClick = {
                                                                    onPatternDirectionChange(PatternDirection.RIGHT_HAND)
                                                                    saveNavigationState()
                                                                },
                                                                colors = if (patternDirection == PatternDirection.RIGHT_HAND) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.buttonColors()
                                                            ) {
                                                                Text(stringResource(R.string.map_nav_pattern_dir_right))
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        var finalDistText by remember { mutableStateOf(String.format("%.1f", patternFinalDistanceNm)) }
                                                        OutlinedTextField(
                                                            value = finalDistText,
                                                            onValueChange = { v ->
                                                                val filtered = v.filter { it.isDigit() || it == '.' }
                                                                finalDistText = filtered
                                                                val d = filtered.toDoubleOrNull()
                                                                if (d != null) {
                                                                    onPatternFinalDistanceNmChange(d)
                                                                    saveNavigationState()
                                                                }
                                                            },
                                                            label = { Text(stringResource(R.string.map_nav_final_dist_nm, "")) },
                                                            singleLine = true,
                                                            modifier = Modifier.weight(1f)
                                                        )

                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(text = stringResource(R.string.map_nav_show_pattern))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Switch(checked = showTrafficPattern, onCheckedChange = {
                                                                onShowTrafficPatternChange(it)
                                                                saveNavigationState()
                                                            })
                                                        }
                                                    }
                                                }

                                                // Show pattern altitude and current altitude with indicator
                                                var showAltitudeEditDialog by remember { mutableStateOf(false) }
                                                val runwayElevationFt = (originalAirportTarget?.elevationM?.times(3.28084))?.toInt() ?: 0
                                                val patternAltAgl = customPatternAltitudeAglFt ?: patternSize.patternAltitudeAglFt
                                                val patternAltMsl = patternAltAgl + runwayElevationFt
                                                val currentAltMeters = flightData?.altitude?.let { it.toDouble() } ?: Double.NaN
                                                val currentAlt = if (currentAltMeters.isNaN()) Double.NaN else currentAltMeters * 3.28084 // convert m -> ft
                                                val currentAltDisplay = if (currentAlt.isNaN()) "n/a" else String.format("%d ft", currentAlt.toInt())
                                                val diff = if (currentAlt.isNaN()) null else currentAlt - patternAltMsl
                                                val smallTolerance = patternAltitudeSmallToleranceFt
                                                val warningTol = patternAltitudeWarningToleranceFt

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Start
                                                ) {
                                                    Column(
                                                        modifier = Modifier.clickable { showAltitudeEditDialog = true }
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.map_nav_pattern_alt_msl, patternAltMsl),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Text(
                                                            text = stringResource(R.string.map_nav_pattern_alt_agl, patternAltAgl, runwayElevationFt, if (customPatternAltitudeAglFt != null) stringResource(R.string.map_nav_pattern_alt_custom_suffix) else ""),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                
                                                // Altitude edit dialog
                                                if (showAltitudeEditDialog) {
                                                    PatternAltitudeEditDialog(
                                                        currentAltitudeAgl = patternAltAgl,
                                                        onDismiss = { showAltitudeEditDialog = false },
                                                        onSave = { newAltitude ->
                                                            onCustomPatternAltitudeAglFtChange(newAltitude)
                                                            saveNavigationState()
                                                        },
                                                        onReset = {
                                                            onCustomPatternAltitudeAglFtChange(null)
                                                            saveNavigationState()
                                                        }
                                                    )
                                                }

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
                                                                        contentDescription = stringResource(R.string.map_nav_alt_climb),
                                                                        tint = col
                                                                    )
                                                                } else {
                                                                    // Above pattern -> show DOWN arrow
                                                                    Icon(
                                                                        imageVector = Icons.Default.KeyboardArrowDown,
                                                                        contentDescription = stringResource(R.string.map_nav_alt_descend),
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
                                                        text = stringResource(R.string.map_nav_current_alt_label, currentAltDisplay),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                // Runways moved into Pattern Configuration above
                                // (previously displayed when approach mode was active)
                            }
                        }
                    }
                }
                    } // End AnimatedVisibility (scrollable details section)
                    
                    // Opacity slider (collapsible, fixed at bottom)
                    AnimatedVisibility(visible = showOpacitySlider) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.map_nav_transparency),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Slider(
                                value = cardOpacity,
                                onValueChange = { cardOpacity = it },
                                valueRange = 0.25f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                    
                    // Drag handle at bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val dragDp = with(density) { dragAmount.y.toDp() }
                                    val screenHeightDp = configuration.screenHeightDp.dp
                                    val deltaFraction = dragDp / screenHeightDp
                                    heightFraction = (heightFraction + deltaFraction).coerceIn(heightMin, heightMax)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = stringResource(R.string.map_nav_resize_handle),
                                tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)
                            )

                            // Circular percentage badge showing current opacity (click to toggle slider)
                            val opacityPercent = (cardOpacity * 100).toInt()
                            Surface(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { showOpacitySlider = !showOpacitySlider },
                                shape = CircleShape,
                                tonalElevation = 0.dp,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.06f)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = stringResource(R.string.map_nav_opacity_percent, opacityPercent),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                            }
                        }
                    }
                } // End outer Column
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

/**
 * Dialog for editing custom pattern altitude (AGL)
 */
@Composable
fun PatternAltitudeEditDialog(
    currentAltitudeAgl: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onReset: () -> Unit = {}
) {
    var altitudeText by remember { mutableStateOf(currentAltitudeAgl.toString()) }
    var isValid by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_nav_edit_pattern_alt_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.map_nav_edit_pattern_alt_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = altitudeText,
                                    onValueChange = {
                                        altitudeText = it.filter { ch -> ch.isDigit() }
                                        isValid = altitudeText.toIntOrNull()?.let { alt -> alt in 500..5000 } ?: false
                                    },
                                    label = { Text(stringResource(R.string.map_nav_edit_pattern_alt_hint)) },                    isError = !isValid,
                    supportingText = {
                        if (!isValid) {
                            Text(stringResource(R.string.map_nav_edit_pattern_alt_error), color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(stringResource(R.string.map_nav_edit_pattern_alt_info))
                        }
                    },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        onReset()
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.map_nav_reset_to_default))
                }
                TextButton(
                    onClick = {
                        altitudeText.toIntOrNull()?.let { altitude ->
                            if (altitude in 500..5000) {
                                onSave(altitude)
                                onDismiss()
                            }
                        }
                    },
                    enabled = isValid
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
