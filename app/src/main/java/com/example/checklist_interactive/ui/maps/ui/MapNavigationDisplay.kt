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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
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
import android.util.Log
import org.json.JSONObject
import kotlinx.coroutines.delay

/**
 * Extract heading from location metadata, description, or tags
 */
private fun extractHeadingFromLocation(location: LocationEntity?): Double? {
    if (location == null) return null
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
    roundedPatternCorners: Boolean,
    onRoundedPatternCornersChange: (Boolean) -> Unit,
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
    saveNavigationState: () -> Unit,
    // Manual landing pattern state (for markers without runways)
    enableManualLandingPattern: Boolean,
    onEnableManualLandingPatternChange: (Boolean) -> Unit,
    manualLandingHeading: String,
    onManualLandingHeadingChange: (String) -> Unit,
    showManualHeadingError: Boolean,
    onShowManualHeadingErrorChange: (Boolean) -> Unit,
    // Airspace display state
    showAirspaceCircles: Boolean,
    onShowAirspaceCirclesChange: (Boolean) -> Unit,
    enabledAirspaceClasses: Set<String>,
    onEnabledAirspaceClassesChange: (Set<String>) -> Unit,
    airspaceFillTransparency: Float,
    onAirspaceFillTransparencyChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isSmallScreen = configuration.screenWidthDp < 600

    // Debug log for manual landing pattern changes
    LaunchedEffect(enableManualLandingPattern) {
        Log.d("MapNavigationDisplay", "manualLanding toggled: $enableManualLandingPattern")
    }
    
    // Persistent height fraction (0.0 to 1.0 of screen height)
    val prefs = context.getSharedPreferences("map_navigation_prefs", Context.MODE_PRIVATE)
    val KEY_HEIGHT_FRACTION = "nav_display_height_fraction"
    val savedFraction = prefs.getFloat(KEY_HEIGHT_FRACTION, 0.35f)
    val heightMin = 0.15f
    // Reduce max height on smartphones to keep more screen visible
    val heightMax = if (isSmallScreen) 0.5f else 0.7f
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

    // Persist showNavigationDetails state across app restarts
    LaunchedEffect(showNavigationDetails) {
        prefs.edit().putBoolean("show_navigation_details", showNavigationDetails).apply()
    }

    // Manual landing details collapse state (hoisted so other controls can sync it)
    var showManualLandingDetails by rememberSaveable { mutableStateOf(showNavigationDetails) }

    // Auto-update heading modes
    var autoUpdateMarkerHeading by rememberSaveable { mutableStateOf(false) }
    var autoUpdateCarrierHeading by rememberSaveable { mutableStateOf(false) }

    // Auto-update heading when mode is active and marker heading changes
    LaunchedEffect(
        autoUpdateMarkerHeading,
        autoUpdateCarrierHeading,
        originalAirportTarget?.latitude,
        originalAirportTarget?.longitude,
        originalAirportTarget?.metadata
    ) {
        val markerHeading = extractHeadingFromLocation(originalAirportTarget)
        if (markerHeading != null) {
            when {
                autoUpdateMarkerHeading -> {
                    val normalizedHeading = ((markerHeading % 360 + 360) % 360)
                    onManualLandingHeadingChange(String.format("%.0f", normalizedHeading))
                    onShowManualHeadingErrorChange(false)
                }
                autoUpdateCarrierHeading -> {
                    val carrierHeading = ((markerHeading - 8.0) % 360 + 360) % 360
                    onManualLandingHeadingChange(String.format("%.0f", carrierHeading))
                    onShowManualHeadingErrorChange(false)
                }
            }
        }
    }

    // Persist opacity when changed
    LaunchedEffect(cardOpacity) {
        prefs.edit().putFloat(KEY_OPACITY, cardOpacity).apply()
    }

    // Sync manual landing collapse with the navigation panel collapse/expand
    LaunchedEffect(showNavigationDetails) {
        if (!showNavigationDetails) {
            // When the navigation panel collapses, only collapse UI details - keep pattern active!
            showManualLandingDetails = false
            onShowPatternDetailsChange(false)
            Log.d("MapNavigationDisplay", "Navigation collapsed -> collapsing UI details only, keeping pattern active")
        } else if (showNavigationDetails && showRunwayApproach) {
            // If the panel expands while landing is active, expand manual landing details
            showManualLandingDetails = true
        }
    }

    // When landing mode toggles, expand or collapse related UI
    // NOTE: We no longer auto-enable pattern here - let user control it independently
    LaunchedEffect(showRunwayApproach) {
        if (showRunwayApproach) {
            onShowNavigationDetailsChange(true)
            showManualLandingDetails = true
            // Don't force pattern on - let user decide
            // onShowTrafficPatternChange(true)
            // onShowPatternDetailsChange(true)
            Log.d("MapNavigationDisplay", "Landing enabled -> expanding UI details (pattern controlled by user)")
        } else {
            showManualLandingDetails = false
        }
    }

    val cardHeightDp = (configuration.screenHeightDp.toFloat() * heightFraction).dp

    // Limit card width on smartphones to avoid overlapping with FAB buttons
    val maxCardWidth = if (isSmallScreen) 320.dp else 500.dp

    if (activeNavigationTarget != null) {
        Box(modifier = modifier
            .widthIn(max = maxCardWidth)
            .zIndex(101f) // Ensure card is above FAB buttons (which are at zIndex 100f)
        ) {
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
                        .padding(if (isSmallScreen) 6.dp else 12.dp)
                        .clickable { onShowNavigationDetailsChange(!showNavigationDetails); saveNavigationState() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.map_nav_route_to,
                                    activeNavigationTarget?.name
                                        ?.replace(Regex(" PATTERN \\d+"), "")
                                        ?.replace(Regex(" RWY \\d+"), "") ?: ""),
                                style = if (isSmallScreen) MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp) else MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                softWrap = false
                            )
                            // Show PATTERN or RWY indicator when active (fixed, doesn't blink)
                            selectedRunwayHeading?.let { hdg ->
                                val indicatorText = if (showTrafficPattern) {
                                    "PATTERN ${String.format("%02d", hdg.toInt() / 10)}"
                                } else if (showRunwayApproach) {
                                    "RWY ${String.format("%02d", hdg.toInt() / 10)}"
                                } else {
                                    null
                                }
                                indicatorText?.let {
                                    Text(
                                        text = it,
                                        style = if (isSmallScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = if (isSmallScreen) 4.dp else 8.dp)
                                    )
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(if (isSmallScreen) 6.dp else 16.dp),
                            modifier = Modifier.padding(top = if (isSmallScreen) 1.dp else 4.dp)
                        ) {
                            navigationDistanceNm?.let { dist ->
                                Text(
                                    text = stringResource(R.string.map_nav_dist_nm, dist),
                                    style = if (isSmallScreen) MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp) else MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            navigationHeading?.let { hdg ->
                                Text(
                                    text = stringResource(R.string.map_nav_hdg, hdg),
                                    style = if (isSmallScreen) MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp) else MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        // Show marker altitude in second row (meters and feet)
                        activeNavigationTarget?.elevationM?.let { elevation ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(if (isSmallScreen) 6.dp else 16.dp),
                                modifier = Modifier.padding(top = if (isSmallScreen) 1.dp else 2.dp)
                            ) {
                                Text(
                                    text = "${String.format("%.0f", elevation)}m",
                                    style = if (isSmallScreen) MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp) else MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "${String.format("%.0f", elevation * 3.28084)}ft",
                                    style = if (isSmallScreen) MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp) else MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        // Show final runway heading when runway selected
                        selectedRunwayHeading?.let { rwyHdg ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(if (isSmallScreen) 4.dp else 8.dp),
                                modifier = Modifier.padding(top = if (isSmallScreen) 2.dp else 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.map_nav_final),
                                    style = if (isSmallScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = stringResource(R.string.map_nav_rwy, rwyHdg),
                                    style = if (isSmallScreen) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 1,
                                    softWrap = false
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
                                        style = if (isSmallScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(if (isSmallScreen) 4.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Airspace settings dialog state
                        var showAirspaceSettings by remember { mutableStateOf(false) }
                        
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

                        Column(verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 2.dp else 4.dp)) {
                            // Pattern altitude row (P:)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (isSmallScreen) 3.dp else 6.dp)) {
                                Text(
                                    text = stringResource(R.string.map_nav_pattern_alt, patternAltMslCompact),
                                    style = if (isSmallScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )

                                // compact indicator with black background
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = androidx.compose.ui.graphics.Color.Black,
                                            shape = MaterialTheme.shapes.extraSmall
                                        )
                                        .padding(horizontal = if (isSmallScreen) 3.dp else 4.dp, vertical = if (isSmallScreen) 1.dp else 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (diffCompact != null) {
                                        val absDiff = kotlin.math.abs(diffCompact)
                                        when {
                                            absDiff <= smallTolerance -> {
                                                Text(
                                                    text = "≈", 
                                                    color = androidx.compose.ui.graphics.Color(0xFF00C853),
                                                    fontSize = if (isSmallScreen) 10.sp else 12.sp
                                                )
                                            }
                                            else -> {
                                                val col = if (absDiff <= warningTol) androidx.compose.ui.graphics.Color(0xFFFFA000) else androidx.compose.ui.graphics.Color(0xFFD50000)
                                                if (diffCompact < 0) {
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowUp, 
                                                        contentDescription = stringResource(R.string.map_nav_alt_climb), 
                                                        tint = col,
                                                        modifier = Modifier.size(if (isSmallScreen) 14.dp else 18.dp)
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowDown, 
                                                        contentDescription = stringResource(R.string.map_nav_alt_descend), 
                                                        tint = col,
                                                        modifier = Modifier.size(if (isSmallScreen) 14.dp else 18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "?", 
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontSize = if (isSmallScreen) 10.sp else 12.sp
                                        )
                                    }
                                }

                                Text(
                                    text = "C:${currentAltCompactDisplay}",
                                    style = if (isSmallScreen) MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp) else MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            // Marker altitude row (M:) - only if marker has altitude
                            activeNavigationTarget?.elevationM?.let { markerAltM ->
                                val markerAltFt = markerAltM * 3.28084
                                val markerPlayerDiff = if (currentAltCompact.isNaN()) null else currentAltCompact - markerAltFt
                                val markerTolerance = 100.0

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (isSmallScreen) 3.dp else 6.dp)) {
                                    Text(
                                        text = "M:${markerAltFt.toInt()}ft",
                                        style = if (isSmallScreen) MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp) else MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false
                                    )

                                    // Marker altitude indicator with black background
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = androidx.compose.ui.graphics.Color.Black,
                                                shape = MaterialTheme.shapes.extraSmall
                                            )
                                            .padding(horizontal = if (isSmallScreen) 3.dp else 4.dp, vertical = if (isSmallScreen) 1.dp else 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (markerPlayerDiff != null) {
                                            val absDiff = kotlin.math.abs(markerPlayerDiff)
                                            when {
                                                absDiff <= markerTolerance -> {
                                                    Text(
                                                        text = "→", 
                                                        color = androidx.compose.ui.graphics.Color(0xFF00C853), 
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = if (isSmallScreen) 10.sp else 12.sp
                                                    )
                                                }
                                                markerPlayerDiff < 0 -> {
                                                    val col = if (absDiff <= 500.0) androidx.compose.ui.graphics.Color(0xFFFFA000) else androidx.compose.ui.graphics.Color(0xFFD50000)
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowUp, 
                                                        contentDescription = "Climb to marker", 
                                                        tint = col, 
                                                        modifier = Modifier.size(if (isSmallScreen) 12.dp else 16.dp)
                                                    )
                                                }
                                                else -> {
                                                    val col = if (absDiff <= 500.0) androidx.compose.ui.graphics.Color(0xFFFFA000) else androidx.compose.ui.graphics.Color(0xFFD50000)
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowDown, 
                                                        contentDescription = "Descend to marker", 
                                                        tint = col, 
                                                        modifier = Modifier.size(if (isSmallScreen) 12.dp else 16.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = "?", 
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                fontSize = if (isSmallScreen) 10.sp else 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Toggle expand/collapse button
                        IconButton(
                            onClick = { onShowNavigationDetailsChange(!showNavigationDetails); saveNavigationState() },
                            modifier = Modifier.size(if (isSmallScreen) 32.dp else 48.dp)
                        ) {
                            Icon(
                                imageVector = if (showNavigationDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showNavigationDetails) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(if (isSmallScreen) 18.dp else 24.dp)
                            )
                        }

                        // Land button (show always, allows manual pattern for markers without runways)
                        FilledTonalIconButton(
                            onClick = {
                                val newVal = !showRunwayApproach
                                Log.d("MapNavigationDisplay", "Land clicked: newVal=$newVal targetRunwaysEmpty=${targetRunways.isEmpty()} showNavigationDetails=$showNavigationDetails enableManual=$enableManualLandingPattern")
                                onShowRunwayApproachChange(newVal)
                                // Ensure details are expanded so user sees the heading UI
                                if (newVal) {
                                    onShowNavigationDetailsChange(true)
                                    // Set originalAirportTarget to current navigation target if not already set
                                    if (originalAirportTarget == null && activeNavigationTarget != null) {
                                        onOriginalAirportTargetChange(activeNavigationTarget)
                                        Log.d("MapNavigationDisplay", "Set originalAirportTarget to ${activeNavigationTarget?.name}")
                                    }
                                    // Expand manual/pattern sections but DON'T force pattern on
                                    showManualLandingDetails = true
                                    // Let user toggle pattern independently
                                    // onShowTrafficPatternChange(true)
                                    // onShowPatternDetailsChange(true)
                                } else {
                                    // When landing is disabled, also disable pattern
                                    showManualLandingDetails = false
                                    onShowTrafficPatternChange(false)
                                    onShowPatternDetailsChange(false)
                                }
                                // If there are no runways, enable manual landing pattern immediately
                                if (newVal && targetRunways.isEmpty()) {
                                    Log.d("MapNavigationDisplay", "Enabling manual landing pattern (no runways)")
                                    onEnableManualLandingPatternChange(true)
                                }
                                if (!newVal) {
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
                            ),
                            modifier = Modifier.size(if (isSmallScreen) 32.dp else 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlightLand,
                                contentDescription = stringResource(R.string.map_nav_approach_button),
                                modifier = Modifier.size(if (isSmallScreen) 18.dp else 24.dp)
                            )
                        }

                        // Airspace button (show/hide airspace circles) - supports long-press for settings
                        var airspaceButtonPressStartTime by remember { mutableLongStateOf(0L) }

                        Box(
                            modifier = Modifier
                                .size(if (isSmallScreen) 32.dp else 48.dp)
                                .background(
                                    color = if (showAirspaceCircles)
                                        MaterialTheme.colorScheme.tertiary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape
                                )
                                .pointerInput(showAirspaceCircles) {
                                    detectTapGestures(
                                        onPress = {
                                            airspaceButtonPressStartTime = System.currentTimeMillis()
                                            val released = tryAwaitRelease()
                                            if (released) {
                                                val pressDuration = System.currentTimeMillis() - airspaceButtonPressStartTime
                                                if (pressDuration < 500) {
                                                    // Short press - toggle airspace
                                                    // Capture current state value at the moment of the press
                                                    val currentState = showAirspaceCircles
                                                    val newState = !currentState
                                                    Log.d("MapNavigationDisplay", "🌐 Airspace button pressed - toggling from $currentState to $newState")
                                                    onShowAirspaceCirclesChange(newState)
                                                    saveNavigationState()
                                                } else {
                                                    // Long press - show settings
                                                    Log.d("MapNavigationDisplay", "🌐 Airspace button long-pressed - showing settings")
                                                    showAirspaceSettings = true
                                                }
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Airspace",
                                tint = if (showAirspaceCircles)
                                    MaterialTheme.colorScheme.onTertiary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(if (isSmallScreen) 18.dp else 24.dp)
                            )
                        }

                        // Airspace settings dialog
                        if (showAirspaceSettings) {
                            AirspaceSettingsDialog(
                                enabledAirspaces = enabledAirspaceClasses,
                                fillTransparency = airspaceFillTransparency,
                                onDismiss = { showAirspaceSettings = false },
                                onSave = { newEnabled, newTransparency ->
                                    onEnabledAirspaceClassesChange(newEnabled)
                                    onAirspaceFillTransparencyChange(newTransparency)
                                    showAirspaceSettings = false
                                    saveNavigationState()
                                }
                            )
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
                            },
                            modifier = Modifier.size(if (isSmallScreen) 32.dp else 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.map_nav_cancel_button),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(if (isSmallScreen) 18.dp else 24.dp)
                            )
                        }
                    }
                }

                // Collapsible details section (scrollable)
                AnimatedVisibility(
                    visible = showNavigationDetails || enableManualLandingPattern,
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
                                // Only show these elements when expanded
                                if (showNavigationDetails) {
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
                                        // Pattern button - consume pointer events to prevent click-through
                                        Button(
                                            onClick = {
                                                val newPatternState = !showTrafficPattern
                                                android.util.Log.d("MapNavigationDisplay", "Pattern button clicked: current=$showTrafficPattern, new=$newPatternState")
                                                onShowTrafficPatternChange(newPatternState)
                                                // Only expand pattern details when enabling, not when disabling
                                                if (newPatternState) {
                                                    onShowPatternDetailsChange(true)
                                                }
                                                // Don't collapse showPatternDetails when disabling - let it stay expanded for runway info
                                                saveNavigationState()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (showTrafficPattern) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                contentColor = if (showTrafficPattern) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            ),
                                            modifier = Modifier
                                                .height(28.dp)
                                                .pointerInput(Unit) {
                                                    // Consume all pointer events to prevent click-through to map
                                                    detectTapGestures(onTap = { /* Handled by onClick */ })
                                                },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.map_nav_pattern_button),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 11.sp
                                            )
                                        }

                                        // Rounded corners toggle button
                                        Button(
                                            onClick = {
                                                val newState = !roundedPatternCorners
                                                onRoundedPatternCornersChange(newState)
                                                saveNavigationState()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (roundedPatternCorners) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                contentColor = if (roundedPatternCorners) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            ),
                                            modifier = Modifier
                                                .height(28.dp)
                                                .pointerInput(Unit) {
                                                    detectTapGestures(onTap = { /* Handled by onClick */ })
                                                },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                        ) {
                                            Text(
                                                text = "⤴",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 14.sp
                                            )
                                        }

                                        Box {
                                            FilterChip(
                                                selected = false,
                                                onClick = { expanded = !expanded },
                                                label = {
                                                    Text(
                                                        text = stringResource(R.string.map_nav_final_dist_nm, finalApproachDistanceNm.let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() }),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1,
                                                        softWrap = false
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
                                        
                                        // Extract real runway names from database (e.g., "09/27" -> "09" and "27")
                                        // Only calculate from heading if runway name is not available
                                        val runwayNames = runway.name?.split("/")?.map { it.trim() } ?: emptyList()
                                        val runwayName1 = runwayNames.getOrNull(0) ?: (heading1 / 10).toString().padStart(2, '0')
                                        val runwayName2 = runwayNames.getOrNull(1) ?: (heading2 / 10).toString().padStart(2, '0')

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
                                                            name = context.getString(R.string.map_nav_approach_target_name, target.name, runwayName1),
                                                            latitude = endpoint.latitude,
                                                            longitude = endpoint.longitude
                                                        )
                                                        onActiveNavigationTargetChange(approachTarget)
                                                    }
                                                },
                                                label = {
                                                    Text(
                                                        text = stringResource(R.string.map_nav_rwy_chip, runwayName1, heading1),
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
                                                            name = context.getString(R.string.map_nav_approach_target_name, target.name, runwayName2),
                                                            latitude = endpoint.latitude,
                                                            longitude = endpoint.longitude
                                                        )
                                                        onActiveNavigationTargetChange(approachTarget)
                                                    }
                                                },
                                                label = {
                                                    Text(
                                                        text = stringResource(R.string.map_nav_rwy_chip, runwayName2, heading2),
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

                                    // Pattern altitude selector
                                    var showAltitudeEditDialog by remember { mutableStateOf(false) }
                                    val runwayElevationFt = (originalAirportTarget?.elevationM?.times(3.28084))?.toInt() ?: 0
                                    val patternAltAgl = customPatternAltitudeAglFt ?: patternSize.patternAltitudeAglFt
                                    val patternAltMsl = patternAltAgl + runwayElevationFt

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.map_nav_pattern_alt_msl, patternAltMsl),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )

                                        FilterChip(
                                            selected = false,
                                            onClick = { showAltitudeEditDialog = true },
                                            label = {
                                                Text(
                                                    text = "$patternAltAgl ft AGL ($patternAltMsl ft MSL)" + if (customPatternAltitudeAglFt != null) " ✎" else "",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            modifier = Modifier.height(28.dp)
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
                                                    PatternSize.HUGE -> 4.0
                                                    PatternSize.GIGANTIC -> 6.0
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
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                                } // End showNavigationDetails

                                Spacer(modifier = Modifier.height(8.dp))
                                // Runways moved into Pattern Configuration above
                                // (previously displayed when approach mode was active)
                            }
                        }

                        // Manual Landing Pattern (for markers without runways - tactical units, carriers, etc.)
                        if (showRunwayApproach && targetRunways.isEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                // Only show manual landing details when expanded
                                if (showNavigationDetails) {
                                    // Header with collapse button
                                    Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showManualLandingDetails = !showManualLandingDetails },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.map_landing_pattern),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Pattern button (only visible when expanded)
                                        if (showNavigationDetails) {
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
                                        }

                                        // Collapse/expand button
                                        IconButton(
                                            onClick = { showManualLandingDetails = !showManualLandingDetails },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (showManualLandingDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (showManualLandingDetails) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                // Collapsible content
                                AnimatedVisibility(
                                    visible = showManualLandingDetails,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column {
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Final approach distance dropdown
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

                                            var expanded by remember { mutableStateOf(false) }
                                            val distances = listOf(2.5, 5.0, 10.0, 15.0, 25.0)

                                            Box {
                                                FilterChip(
                                                    selected = false,
                                                    onClick = { expanded = !expanded },
                                                    label = {
                                                        Text(
                                                            text = stringResource(R.string.map_nav_final_dist_nm, finalApproachDistanceNm.let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() }),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            maxLines = 1,
                                                            softWrap = false
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

                                                Spacer(modifier = Modifier.height(8.dp))

                                        // Heading input field with auto-update buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            // Heading input field (1/3 width)
                                            androidx.compose.material3.OutlinedTextField(
                                                value = manualLandingHeading,
                                                onValueChange = { newValue ->
                                                    onManualLandingHeadingChange(newValue)
                                                    onShowManualHeadingErrorChange(false)
                                                },
                                                label = { Text(stringResource(R.string.map_landing_heading)) },
                                                placeholder = { Text("000") },
                                                singleLine = true,
                                                isError = showManualHeadingError,
                                                supportingText = if (showManualHeadingError) {
                                                    { Text(stringResource(R.string.map_landing_heading_error)) }
                                                } else {
                                                    { Text(stringResource(R.string.map_landing_heading_hint)) }
                                                },
                                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                                ),
                                                trailingIcon = {
                                                    Text(
                                                        text = "°",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                },
                                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = MaterialTheme.colorScheme.onErrorContainer,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f),
                                                    focusedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                                                    unfocusedLabelColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                                                    cursorColor = MaterialTheme.colorScheme.onErrorContainer
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )

                                            // Column for the two toggle buttons (2/3 width)
                                            Column(
                                                modifier = Modifier.weight(2f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                // Auto-update heading toggle button
                                                // Track position and metadata changes for live updates
                                                val markerHeading = remember(
                                                    originalAirportTarget,
                                                    originalAirportTarget?.latitude,
                                                    originalAirportTarget?.longitude,
                                                    originalAirportTarget?.metadata
                                                ) {
                                                    extractHeadingFromLocation(originalAirportTarget)
                                                }
                                                FilterChip(
                                                    selected = autoUpdateMarkerHeading,
                                                    onClick = {
                                                        if (markerHeading != null) {
                                                            autoUpdateMarkerHeading = !autoUpdateMarkerHeading
                                                            if (autoUpdateMarkerHeading) {
                                                                // Deactivate carrier mode
                                                                autoUpdateCarrierHeading = false
                                                                // Set heading immediately
                                                                val normalizedHeading = ((markerHeading % 360 + 360) % 360)
                                                                onManualLandingHeadingChange(String.format("%.0f", normalizedHeading))
                                                                onShowManualHeadingErrorChange(false)
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    enabled = markerHeading != null,
                                                    label = {
                                                        Text(
                                                            text = if (markerHeading != null) {
                                                                if (autoUpdateMarkerHeading) {
                                                                    "🔴 LIVE HDG: ${String.format("%.0f°", ((markerHeading % 360 + 360) % 360))}"
                                                                } else {
                                                                    "→ Marker HDG (${String.format("%.0f°", ((markerHeading % 360 + 360) % 360))})"
                                                                }
                                                            } else {
                                                                "→ Marker HDG (N/A)"
                                                            },
                                                            fontSize = 12.sp
                                                        )
                                                    },
                                                    leadingIcon = if (autoUpdateMarkerHeading) {
                                                        { Icon(Icons.Default.FlightLand, contentDescription = null) }
                                                    } else null
                                                )

                                                // Carrier landing toggle button (-8 degrees)
                                                FilterChip(
                                                    selected = autoUpdateCarrierHeading,
                                                    onClick = {
                                                        if (markerHeading != null) {
                                                            autoUpdateCarrierHeading = !autoUpdateCarrierHeading
                                                            if (autoUpdateCarrierHeading) {
                                                                // Deactivate marker mode
                                                                autoUpdateMarkerHeading = false
                                                                // Set carrier heading immediately
                                                                val carrierHeading = ((markerHeading - 8.0) % 360 + 360) % 360
                                                                onManualLandingHeadingChange(String.format("%.0f", carrierHeading))
                                                                onShowManualHeadingErrorChange(false)
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    enabled = markerHeading != null,
                                                    label = {
                                                        Text(
                                                            text = if (markerHeading != null) {
                                                                val carrierHdg = ((markerHeading - 8.0) % 360 + 360) % 360
                                                                if (autoUpdateCarrierHeading) {
                                                                    "🔴 CARRIER: ${String.format("%.0f°", carrierHdg)}"
                                                                } else {
                                                                    "⚓ Carrier (-8°): ${String.format("%.0f°", carrierHdg)}"
                                                                }
                                                            } else {
                                                                "⚓ Carrier (-8°)"
                                                            },
                                                            fontSize = 12.sp
                                                        )
                                                    },
                                                    leadingIcon = if (autoUpdateCarrierHeading) {
                                                        { Icon(Icons.Default.FlightLand, contentDescription = null) }
                                                    } else null
                                                )
                                            }
                                        }

                                // Apply button (validate and set heading)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val heading = manualLandingHeading.toDoubleOrNull()
                                        Log.d("MapNavigationDisplay", "Apply clicked: heading=$heading, valid=${heading != null && heading >= 0.0 && heading <= 360.0}, originalTarget=${originalAirportTarget?.name}")
                                        if (heading != null && heading >= 0.0 && heading <= 360.0) {
                                            onSelectedRunwayHeadingChange(heading)
                                            Log.d("MapNavigationDisplay", "Heading set to $heading")
                                            
                                            // Calculate approach endpoint like runway approach
                                            val target = originalAirportTarget
                                            if (target != null) {
                                                val distanceMeters = finalApproachDistanceNm * 1852.0
                                                val rad = Math.toRadians(heading)
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
                                                    name = context.getString(R.string.map_nav_approach_target_name, target.name, heading.toInt()),
                                                    latitude = endpoint.latitude,
                                                    longitude = endpoint.longitude
                                                )
                                                onActiveNavigationTargetChange(approachTarget)
                                                Log.d("MapNavigationDisplay", "Approach target set: ${approachTarget.name} at ${endpoint.latitude},${endpoint.longitude}")
                                            } else {
                                                Log.d("MapNavigationDisplay", "No original airport target - cannot calculate approach endpoint")
                                            }
                                            
                                            onShowRunwayApproachChange(true)
                                            onShowManualHeadingErrorChange(false)
                                            saveNavigationState()
                                            Log.d("MapNavigationDisplay", "Navigation state saved")
                                        } else {
                                            onShowManualHeadingErrorChange(true)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(Icons.Default.FlightLand, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.map_nav_apply_heading))
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

                                    // Collapsible altitude threshold settings
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
                                                        label = { Text(stringResource(R.string.map_nav_level_tolerance_ft)) },
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
                                                                onPatternAltitudeWarningToleranceFtChange(intVal.toDouble())
                                                                saveNavigationState()
                                                            }
                                                        },
                                                        label = { Text(stringResource(R.string.map_nav_warn_tolerance_ft)) },
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
                                                    PatternSize.HUGE -> 4.0
                                                    PatternSize.GIGANTIC -> 6.0
                                                }
                                                val turnMultiplier = if (patternDirection == PatternDirection.LEFT_HAND) -1 else 1
                                                val runwayHeading = selectedRunwayHeading ?: manualLandingHeading.toDoubleOrNull() ?: 0.0

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

                                                val downwindLengthNm = 2.0 / 1852.0 + patternFinalDistanceNm + (0.5 * sizeScale) // Default 2km runway length
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
                                            }
                                        }
                                    }
                                        }
                                    } // End AnimatedVisibility (manual landing details)
                                }
                                } // End showNavigationDetails
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
                                .padding(horizontal = 12.dp, vertical = 4.dp)
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
                            .height(24.dp)
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
    // Start with an empty input so the user can immediately type a new value
    var altitudeText by remember { mutableStateOf("") }
    var isValid by remember { mutableStateOf(false) }

    // Focus and keyboard handling so the field is focused and numeric keyboard opened when dialog appears
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        // Small delay ensures the dialog is fully composed before requesting focus
        delay(100L)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

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
                                    modifier = Modifier.focusRequester(focusRequester),
                                    label = { Text(stringResource(R.string.map_nav_edit_pattern_alt_hint)) },
                    // Only show an error state once the user has typed something invalid
                    isError = altitudeText.isNotBlank() && !isValid,
                    supportingText = {
                        if (altitudeText.isNotBlank() && !isValid) {
                            Text(stringResource(R.string.map_nav_edit_pattern_alt_error), color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(stringResource(R.string.map_nav_edit_pattern_alt_info))
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
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

/**
 * Airspace settings dialog - allows selecting which airspace classes to display
 */
@Composable
fun AirspaceSettingsDialog(
    enabledAirspaces: Set<String>,
    fillTransparency: Float,
    onDismiss: () -> Unit,
    onSave: (Set<String>, Float) -> Unit
) {
    var selectedAirspaces by remember { mutableStateOf(enabledAirspaces) }
    var transparency by remember { mutableStateOf(fillTransparency) }

    // Available airspace classes
    val airspaceOptions = listOf(
        "CLASS_D" to "Class D (CTR) - 5 NM",
        "CLASS_C_CTR" to "Class C (CTR) - 5 NM",
        "CLASS_C_TMA" to "Class C (TMA) - 15 NM",
        "CLASS_B" to "Class B - 30 NM",
        "CLASS_E" to "Class E - 10 NM",
        "CLASS_G" to "Class G - 8 NM"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Airspace Display Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Select airspace classes to display:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                airspaceOptions.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedAirspaces = if (selectedAirspaces.contains(key)) {
                                    selectedAirspaces - key
                                } else {
                                    selectedAirspaces + key
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedAirspaces.contains(key),
                            onCheckedChange = { checked ->
                                selectedAirspaces = if (checked) {
                                    selectedAirspaces + key
                                } else {
                                    selectedAirspaces - key
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Transparency slider
                Text(
                    text = "Background Transparency:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${(transparency * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "(Borders remain visible)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Slider(
                    value = transparency,
                    onValueChange = { transparency = it },
                    valueRange = 0.05f..0.5f,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Text(
                    text = "Lower = More transparent (better map visibility)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedAirspaces, transparency) }) {
                Text(stringResource(R.string.action_save))
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
 * Marker Pattern Settings Dialog
 * Allows configuring traffic pattern for any marker (not just active navigation target)
 */
@Composable
fun MarkerPatternSettingsDialog(
    location: LocationEntity,
    runways: List<RunwayEntity>,
    currentState: com.example.checklist_interactive.ui.maps.MapViewerState.MarkerPatternState,
    onDismiss: () -> Unit,
    onSave: (com.example.checklist_interactive.ui.maps.MapViewerState.MarkerPatternState) -> Unit
) {
    // Load last settings from currentState
    var patternSize by remember { mutableStateOf(currentState.patternSize) }
    var patternDirection by remember { mutableStateOf(currentState.patternDirection) }
    var finalDistanceNm by remember { mutableStateOf(currentState.finalDistanceNm) }
    var roundedCorners by remember { mutableStateOf(currentState.roundedCorners) }
    var customAltitudeAglFt by remember { mutableStateOf(currentState.customAltitudeAglFt) }
    var selectedRunwayIndex by remember { mutableStateOf(currentState.selectedRunwayIndex) }
    var manualHeading by remember { mutableStateOf(currentState.manualHeading) }
    var showManualHeadingError by remember { mutableStateOf(false) }
    
    val hasRunways = runways.isNotEmpty()
    val isATCT = location.markerType == "atct" || location.markerType == "tactical"
    val markerHeading = extractHeadingFromLocation(location)
    
    // Reset to default function
    val resetToDefaults = {
        patternSize = PatternSize.NORMAL
        patternDirection = PatternDirection.LEFT_HAND
        finalDistanceNm = 1.0
        roundedCorners = false
        customAltitudeAglFt = null
        selectedRunwayIndex = null
        manualHeading = ""
        showManualHeadingError = false
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text(stringResource(R.string.map_pattern_settings_title))
                Text(
                    text = location.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Runway/Heading selection section
                if (hasRunways) {
                    Text(
                        text = stringResource(R.string.map_nav_select_runway),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Runway selection chips - show both directions for each runway
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        runways.forEachIndexed { index, runway ->
                            // Parse runway name to get both directions (e.g., "09/27" -> "09" and "27")
                            val runwayNames = runway.name.split("/").map { it.trim() }
                            val runwayHeading = runway.headingDeg ?: extractRunwayHeading(runway.name) ?: 0.0
                            
                            // Direction 1 (e.g., "09")
                            if (runwayNames.isNotEmpty()) {
                                val direction1Index = index * 2
                                val heading1 = runwayHeading.toInt()
                                FilterChip(
                                    selected = selectedRunwayIndex == direction1Index,
                                    onClick = { 
                                        selectedRunwayIndex = direction1Index
                                        manualHeading = "" // Clear manual heading when runway selected
                                    },
                                    label = { Text("${runwayNames[0]} (${String.format("%03d", heading1)}°)") }
                                )
                            }
                            
                            // Direction 2 (e.g., "27")
                            if (runwayNames.size > 1) {
                                val direction2Index = index * 2 + 1
                                val heading2 = ((runwayHeading + 180.0) % 360).toInt()
                                FilterChip(
                                    selected = selectedRunwayIndex == direction2Index,
                                    onClick = { 
                                        selectedRunwayIndex = direction2Index
                                        manualHeading = "" // Clear manual heading when runway selected
                                    },
                                    label = { Text("${runwayNames[1]} (${String.format("%03d", heading2)}°)") }
                                )
                            }
                        }
                    }
                } else if (isATCT && markerHeading != null) {
                    // ATCT marker with heading - show auto-update options
                    Text(
                        text = stringResource(R.string.map_nav_landing_heading),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    OutlinedTextField(
                        value = manualHeading,
                        onValueChange = { 
                            manualHeading = it
                            showManualHeadingError = it.toDoubleOrNull() == null && it.isNotEmpty()
                        },
                        label = { Text(stringResource(R.string.map_nav_manual_heading_hint)) },
                        isError = showManualHeadingError,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                    )
                } else {
                    // Generic marker - manual heading input
                    Text(
                        text = stringResource(R.string.map_nav_landing_heading),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    OutlinedTextField(
                        value = manualHeading,
                        onValueChange = { 
                            manualHeading = it
                            showManualHeadingError = it.toDoubleOrNull() == null && it.isNotEmpty()
                        },
                        label = { Text(stringResource(R.string.map_nav_manual_heading_hint)) },
                        isError = showManualHeadingError,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                    )
                }
                
                HorizontalDivider()
                
                // Pattern Size
                Text(
                    text = stringResource(R.string.map_nav_pattern_size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PatternSize.values().forEach { size ->
                        FilterChip(
                            selected = patternSize == size,
                            onClick = { patternSize = size },
                            label = { Text(size.displayName) }
                        )
                    }
                }
                
                // Pattern Direction
                Text(
                    text = stringResource(R.string.map_nav_pattern_direction),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = patternDirection == PatternDirection.LEFT_HAND,
                        onClick = { patternDirection = PatternDirection.LEFT_HAND },
                        label = { Text(stringResource(R.string.map_nav_left_pattern)) }
                    )
                    FilterChip(
                        selected = patternDirection == PatternDirection.RIGHT_HAND,
                        onClick = { patternDirection = PatternDirection.RIGHT_HAND },
                        label = { Text(stringResource(R.string.map_nav_right_pattern)) }
                    )
                }
                
                // Final Approach Distance
                Text(
                    text = stringResource(R.string.map_nav_final_distance),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format("%.1f NM", finalDistanceNm),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = finalDistanceNm.toFloat(),
                    onValueChange = { finalDistanceNm = it.toDouble() },
                    valueRange = 0.5f..10.0f,
                    steps = 18
                )
                
                // Rounded Corners
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.map_nav_rounded_corners),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = roundedCorners,
                        onCheckedChange = { roundedCorners = it }
                    )
                }
                
                // Custom Altitude
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.map_nav_pattern_altitude),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = customAltitudeAglFt?.let { "$it ft AGL" } ?: stringResource(R.string.map_nav_use_default),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { customAltitudeAglFt = if (customAltitudeAglFt != null) null else 1200 }) {
                        Text(if (customAltitudeAglFt != null) stringResource(R.string.map_nav_reset_to_default) else stringResource(R.string.map_nav_customize))
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Reset to Default button
                OutlinedButton(
                    onClick = { resetToDefaults() }
                ) {
                    Text(stringResource(R.string.map_nav_reset_to_default))
                }
                
                // Save button
                TextButton(
                    onClick = {
                        val newState = com.example.checklist_interactive.ui.maps.MapViewerState.MarkerPatternState(
                            enabled = true,
                            patternSize = patternSize,
                            patternDirection = patternDirection,
                            finalDistanceNm = finalDistanceNm,
                            roundedCorners = roundedCorners,
                            customAltitudeAglFt = customAltitudeAglFt,
                            selectedRunwayIndex = selectedRunwayIndex,
                            manualHeading = manualHeading,
                            showManualHeadingError = showManualHeadingError
                        )
                        onSave(newState)
                    }
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
