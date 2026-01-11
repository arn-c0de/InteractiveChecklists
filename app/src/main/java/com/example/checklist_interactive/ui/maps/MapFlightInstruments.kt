package com.example.checklist_interactive.ui.maps

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.example.checklist_interactive.R
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue

/**
 * MapFlightInstruments - Flight instruments panel overlay for map view
 *
 * Displays aviation instruments with real-time DataPad data:
 * - Attitude Indicator (Artificial Horizon)
 * - Turn and Slip Indicator
 * - Vertical Speed Indicator
 * - Airspeed Indicator
 *
 * Instruments are arranged horizontally at the bottom center of the screen.
 * The panel automatically expands as more instruments are added.
 */
@Composable
fun MapFlightInstruments(
    modifier: Modifier = Modifier,
    pitch: Double = 0.0,
    bank: Double = 0.0,
    verticalSpeed: Double? = null,
    airspeed: Double? = null,
    altitude: Double? = null,
    terrainElevation: Double? = null,
    heading: Double? = null,
    angleOfAttack: Double? = null,
    gLoad: Double? = null,
    fuelRemaining: Double? = null,
    fuelTotal: Double? = null,
    mach: Double? = null,
    engineRpmLeft: Double? = null,
    engineRpmRight: Double? = null,
    windSpeed: Double? = null,
    windDirection: Double? = null,
    flareCount: Int? = null,
    chaffCount: Int? = null,
    enabled: Boolean = true,
    dataAvailable: Boolean = true
) {
    // Debug logging removed to reduce log spam

    if (!enabled) return

    // Get screen configuration for responsive sizing
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // Calculate responsive instrument sizes
    // Primary row: 3 instruments (120dp + 220dp + 120dp = 460dp) + spacing (2 * 12dp = 24dp) = 484dp
    // Secondary row: 7 instruments (7 * 64dp = 448dp) + spacing (6 * 8dp = 48dp) = 496dp
    // We need to fit the wider of these (496dp) into available width with padding

    val horizontalPadding = 10.dp
    val availableWidth = screenWidth - (horizontalPadding * 2)

    // Calculate scale factor based on secondary row (wider row)
    val secondaryRowWidth = (64 * 7 + 8 * 6).dp
    val primaryRowWidth = (120 + 220 + 120 + 12 * 2).dp
    val maxRequiredWidth = maxOf(secondaryRowWidth, primaryRowWidth)

    val scaleFactor = (availableWidth / maxRequiredWidth).coerceIn(0.4f, 1.0f)

    // Calculate scaled sizes
    val primarySmallSize = (120.dp * scaleFactor).coerceAtLeast(60.dp)
    val primaryLargeSize = (220.dp * scaleFactor).coerceAtLeast(110.dp)
    val secondarySize = (64.dp * scaleFactor).coerceAtLeast(32.dp)
    val primarySpacing = (12.dp * scaleFactor).coerceAtLeast(6.dp)
    val secondarySpacing = (8.dp * scaleFactor).coerceAtLeast(4.dp)

    // Horizontal panel at bottom center
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .padding(bottom = 12.dp)
                .wrapContentSize(),
            tonalElevation = 4.dp,
            shape = MaterialTheme.shapes.large,
            color = Color.Transparent // make outer panel transparent so instrument circles stand out
        ) {
            // Use a Box so we can overlay a "NO DATA" indicator when no flight data exists
            Box {
                Column(
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Primary flight instruments (top row)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(primarySpacing),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Airspeed Indicator
                        AirspeedIndicator(
                            airspeed = airspeed ?: 0.0,
                            mach = mach,
                            size = primarySmallSize
                        )

                        // Attitude Indicator (larger, centered) with HUD Altitude and Speed Overlays
                        AttitudeIndicator(
                            pitch = pitch,
                            bank = bank,
                            size = primaryLargeSize,
                            altitude = altitude,
                            terrainElevation = terrainElevation,
                            verticalSpeed = verticalSpeed,
                            airspeed = airspeed
                        )

                        // Vertical Speed Indicator
                        VerticalSpeedIndicator(
                            verticalSpeed = verticalSpeed ?: 0.0,
                            size = primarySmallSize
                        )
                    }

                    // Secondary instruments (bottom row - compact)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(secondarySpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Heading Indicator
                        HeadingIndicator(
                            heading = heading ?: 0.0,
                            size = secondarySize
                        )

                        // Angle of Attack
                        AoAIndicator(
                            aoa = angleOfAttack ?: 0.0,
                            size = secondarySize
                        )

                        // G-Meter (left side)
                        GMeterIndicator(
                            gLoad = gLoad ?: 1.0,
                            size = secondarySize
                        )

                        // Engine RPM Indicator (center-right)
                        EngineRPMIndicator(
                            rpmLeft = engineRpmLeft ?: 0.0,
                            rpmRight = engineRpmRight,
                            size = secondarySize
                        )

                        // Fuel Indicator (right side)
                        FuelIndicator(
                            fuelRemaining = fuelRemaining ?: 0.0,
                            fuelTotal = fuelTotal ?: 1.0,
                            size = secondarySize
                        )

                        // Wind Indicator
                        WindIndicator(
                            windSpeed = windSpeed ?: 0.0,
                            windDirection = windDirection ?: 0.0,
                            size = secondarySize
                        )

                        // Countermeasures Indicator
                        CountermeasuresIndicator(
                            flareCount = flareCount ?: 0,
                            chaffCount = chaffCount ?: 0,
                            size = secondarySize
                        )
                    }
                }

                if (!dataAvailable) {
                    // Semi-transparent overlay with a small notice to indicate lack of live data
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color(0x88000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = stringResource(R.string.map_flight_instrument_no_data), color = Color.Yellow, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Wrapper that makes an instrument draggable
 */
/**
 * Attitude Indicator (Artificial Horizon)
 * Shows pitch and bank angles (expects values in degrees)
 * With integrated HUD altitude and speed display overlays
 */
@Composable
fun AttitudeIndicator(
    pitch: Double, // in degrees
    bank: Double, // in degrees
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    altitude: Double? = null,
    terrainElevation: Double? = null,
    verticalSpeed: Double? = null,
    airspeed: Double? = null
) {
    // Debug log for visibility
    // Debug logging removed to reduce log spam

    // Calculate HUD overlay sizes proportional to attitude indicator size
    // Base size is 220dp, overlays are 70dp x 160dp and 70dp x 70dp
    val overlayScale = (size / 220.dp).coerceIn(0.5f, 1.0f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape) // Clip everything inside to circle shape
        ) {
            // Attitude Indicator (circular, clipped)
            Box(
                modifier = Modifier
                    .size(size)
                    .background(Color(0xFF1A1A1A))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = this.size.width / 2
                    val centerY = this.size.height / 2
                    val radius = this.size.minDimension / 2

                    // Rotate canvas for bank angle
                    rotate(-bank.toFloat(), Offset(centerX, centerY)) {
                        // Sky (blue) - upper half
                        val pitchOffset = (pitch.toFloat() * radius * 0.02f).coerceIn(-radius * 2, radius * 2)

                        drawRect(
                            color = Color(0xFF4A90E2),
                            topLeft = Offset(0f, centerY + pitchOffset - radius * 2),
                            size = Size(this.size.width, radius * 2)
                        )

                        // Ground (brown) - lower half
                        drawRect(
                            color = Color(0xFF8B4513),
                            topLeft = Offset(0f, centerY + pitchOffset),
                            size = Size(this.size.width, radius * 2)
                        )

                        // Horizon line
                        drawLine(
                            color = Color.White,
                            start = Offset(0f, centerY + pitchOffset),
                            end = Offset(this.size.width, centerY + pitchOffset),
                            strokeWidth = 3f
                        )

                        // Pitch ladder
                        drawPitchLadder(centerX, centerY, pitchOffset, radius)
                    }

                    // Bank angle indicator (fixed reference)
                    drawBankIndicator(centerX, centerY, radius, bank)

                    // Aircraft symbol (fixed in center)
                    drawAircraftSymbol(centerX, centerY, radius * 0.6f)
                }
            }

            // HUD Altitude Overlay (right side) - positioned OVER the attitude indicator
            HUDAltitudeOverlay(
                altitude = altitude ?: 0.0,
                terrainElevation = terrainElevation,
                verticalSpeed = verticalSpeed,
                scale = overlayScale,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp * overlayScale)
            )

            // HUD Speed Overlay (top left) - positioned OVER the attitude indicator
            HUDSpeedOverlay(
                airspeed = airspeed ?: 0.0,
                scale = overlayScale,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 4.dp * overlayScale, top = 20.dp * overlayScale)
            )
        }

        Text(
            text = stringResource(R.string.map_flight_instrument_attitude),
            fontSize = 10.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * HUD-Style Altitude Overlay for Attitude Indicator
 * Displays altitude tape on left side of artificial horizon
 */
@Composable
fun HUDAltitudeOverlay(
    altitude: Double,
    terrainElevation: Double?,
    verticalSpeed: Double?,
    modifier: Modifier = Modifier,
    scale: Float = 1.0f
) {
    // Ground height and warning height settings (persisted in SharedPreferences)
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("map_flight_instrument_prefs", Context.MODE_PRIVATE)
    var showSettingsDialog by remember { mutableStateOf(false) }
    var groundHeight by remember { mutableStateOf(prefs.getFloat("hud_ground_height", 0f).toDouble()) } // Ground elevation in feet
    var warningHeight by remember { mutableStateOf(prefs.getFloat("hud_warning_height", 500f).toDouble()) } // Warning threshold in feet AGL

    // Convert meters to feet
    val altFeet by remember(altitude) {
        derivedStateOf { (altitude * 3.28084).coerceIn(-1000.0, 99999.0) }
    }

    val aglFeet by remember(altitude, terrainElevation, groundHeight) {
        derivedStateOf {
            if (terrainElevation != null) {
                ((altitude - terrainElevation) * 3.28084).coerceIn(-1000.0, 99999.0)
            } else {
                // Use manual ground height if no terrain data
                (altFeet - groundHeight).coerceIn(-1000.0, 99999.0)
            }
        }
    }

    val vsFpm by remember(verticalSpeed) {
        derivedStateOf {
            ((verticalSpeed ?: 0.0) * 196.85).coerceIn(-15000.0, 15000.0)
        }
    }

    // Predicted altitude in 6 seconds
    val trendAltitude by remember(altFeet, vsFpm) {
        derivedStateOf {
            (altFeet + (vsFpm / 60.0 * 6.0)).coerceIn(-1000.0, 99999.0)
        }
    }

    // Color coding based on AGL
    val aglColor by remember(aglFeet) {
        derivedStateOf {
            when {
                aglFeet < 100 -> Color(0xFFFF0000) // Red: Critical
                aglFeet < 500 -> Color(0xFFFFAA00) // Amber: Caution
                aglFeet < 1000 -> Color(0xFFFFFF00) // Yellow: Warning
                else -> Color(0xFF00FF00) // Green: Safe
            }
        }
    }

    // Cached Paint objects with scaled text sizes
    val tapePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(0x99, 0x0A, 0x0A, 0x0A) // More transparent
            style = android.graphics.Paint.Style.FILL
        }
    }

    val textPaint = remember(scale) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(0xFF, 0x00, 0xFF, 0x00)
            textSize = 22f * scale
            textAlign = android.graphics.Paint.Align.RIGHT
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    val textShadowPaint = remember(scale) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(0xAA, 0x00, 0x00, 0x00)
            textSize = 22f * scale
            textAlign = android.graphics.Paint.Align.RIGHT
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    Box(
        modifier = modifier
            .size(width = 70.dp * scale, height = 160.dp * scale)
    ) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .clickable { showSettingsDialog = true }
        ) {
            val width = this.size.width
            val height = this.size.height
            val centerY = height / 2f

            if (!altFeet.isFinite() || !aglFeet.isFinite()) return@Canvas

            // Draw tape background
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRoundRect(
                    0f,
                    0f,
                    width,
                    height,
                    8f,
                    8f,
                    tapePaint
                )
            }

            // Draw altitude tick marks
            val altitudeRange = 600f // Show ±300ft range
            val pixelsPerFoot = height / altitudeRange
            val tickInterval = 100
            val minorTickInterval = 50

            val minAlt = (altFeet - 300).toInt()
            val maxAlt = (altFeet + 300).toInt()

            drawIntoCanvas { canvas ->
                // Draw ticks and labels
                for (alt in ((minAlt / minorTickInterval) * minorTickInterval)..(maxAlt / minorTickInterval) * minorTickInterval step minorTickInterval) {
                    val yPos = centerY - ((alt - altFeet) * pixelsPerFoot).toFloat()

                    if (yPos < 0 || yPos > height) continue

                    val isMajorTick = alt % tickInterval == 0
                    val tickLength = if (isMajorTick) width * 0.4f else width * 0.2f

                    // Tick line
                    val tickPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = if (isMajorTick) android.graphics.Color.argb(0xFF, 0x00, 0xFF, 0x00)
                                else android.graphics.Color.argb(0x88, 0x00, 0xFF, 0x00)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = if (isMajorTick) 2f else 1f
                    }

                    canvas.nativeCanvas.drawLine(
                        width - tickLength,
                        yPos,
                        width,
                        yPos,
                        tickPaint
                    )

                    // Major tick labels
                    if (isMajorTick && abs(yPos - centerY) > 25f) {
                        val label = (alt / 100).toString()
                        // Shadow
                        canvas.nativeCanvas.drawText(
                            label,
                            width - tickLength - 3f,
                            yPos + 7f,
                            textShadowPaint
                        )
                        // Text
                        canvas.nativeCanvas.drawText(
                            label,
                            width - tickLength - 4f,
                            yPos + 6f,
                            textPaint
                        )
                    }
                }
            }

            // Draw trend vector
            if (abs(vsFpm) > 100) {
                val trendY = centerY - ((trendAltitude - altFeet) * pixelsPerFoot).toFloat()
                val trendColor = if (vsFpm > 0) Color(0xFF00FF00) else Color(0xFFFF6600)

                // Trend line
                drawLine(
                    color = trendColor.copy(alpha = 0.4f),
                    start = Offset(width * 0.3f - 1f, centerY),
                    end = Offset(width * 0.3f - 1f, trendY.coerceIn(10f, height - 10f)),
                    strokeWidth = 6f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                drawLine(
                    color = trendColor,
                    start = Offset(width * 0.3f, centerY),
                    end = Offset(width * 0.3f, trendY.coerceIn(10f, height - 10f)),
                    strokeWidth = 2f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                // Arrow head
                val arrowY = trendY.coerceIn(10f, height - 10f)
                val arrowSize = 5f
                val arrowPath = Path().apply {
                    if (vsFpm > 0) {
                        moveTo(width * 0.3f, arrowY)
                        lineTo(width * 0.3f - arrowSize, arrowY + arrowSize)
                        lineTo(width * 0.3f + arrowSize, arrowY + arrowSize)
                    } else {
                        moveTo(width * 0.3f, arrowY)
                        lineTo(width * 0.3f - arrowSize, arrowY - arrowSize)
                        lineTo(width * 0.3f + arrowSize, arrowY - arrowSize)
                    }
                    close()
                }
                drawPath(arrowPath, trendColor)
            }

            // Draw current altitude box (clickable area handled by modifier)
            drawIntoCanvas { canvas ->
                val boxHeight = 45f * scale
                val boxWidth = width * 0.95f
                val boxLeft = width - boxWidth
                val boxTop = centerY - boxHeight / 2f

                // Box background
                val boxPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.argb(0xEE, 0x00, 0x00, 0x00)
                    style = android.graphics.Paint.Style.FILL
                }

                canvas.nativeCanvas.drawRoundRect(
                    boxLeft,
                    boxTop,
                    width,
                    boxTop + boxHeight,
                    4f * scale,
                    4f * scale,
                    boxPaint
                )

                // Box border with AGL color
                val boxStrokePaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = aglColor.value.toInt()
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 2f * scale
                }

                canvas.nativeCanvas.drawRoundRect(
                    boxLeft,
                    boxTop,
                    width,
                    boxTop + boxHeight,
                    4f * scale,
                    4f * scale,
                    boxStrokePaint
                )

                // Altitude text - color changes based on warning height
                val altTextColor = when {
                    aglFeet < warningHeight * 0.5 -> android.graphics.Color.argb(0xFF, 0xFF, 0x00, 0x00) // Red when below 50% of warning height
                    aglFeet < warningHeight -> android.graphics.Color.argb(0xFF, 0xFF, 0xFF, 0x00) // Yellow when below warning height
                    else -> android.graphics.Color.argb(0xFF, 0x00, 0xFF, 0x00) // Green when above warning height
                }

                val altText = String.format("%05d", altFeet.toInt())
                val altTextPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = altTextColor
                    textSize = 26f * scale
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                }

                canvas.nativeCanvas.drawText(
                    altText,
                    boxLeft + boxWidth / 2f,
                    centerY + 10f * scale,
                    altTextPaint
                )
            }

            // Draw vertical speed at top (left-aligned to avoid clipping)
            drawIntoCanvas { canvas ->
                val vsColor = when {
                    vsFpm > 500 -> android.graphics.Color.argb(0xFF, 0x00, 0xFF, 0x00) // Green for climbing
                    vsFpm < -500 -> android.graphics.Color.argb(0xFF, 0xFF, 0x66, 0x00) // Orange for descending
                    else -> android.graphics.Color.argb(0xFF, 0xFF, 0xFF, 0xFF) // White for near level
                }

                val vsPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = vsColor
                    textSize = 18f * scale
                    textAlign = android.graphics.Paint.Align.LEFT
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                }

                val vsInt = vsFpm.roundToInt()
                val vsText = if (vsInt > 0) "+$vsInt" else "$vsInt"
                canvas.nativeCanvas.drawText(vsText, 8f * scale, 20f * scale, vsPaint)
            }

            // Draw AGL readout at bottom
            if (terrainElevation != null) {
                drawIntoCanvas { canvas ->
                    val aglText = "${aglFeet.toInt()}"
                    val aglPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = aglColor.value.toInt()
                        textSize = 20f * scale
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                    }

                    canvas.nativeCanvas.drawText(
                        aglText,
                        width / 2f,
                        height - 12f * scale,
                        aglPaint
                    )
                }
            }
        }

        // Settings dialog for ground height and warning height
        if (showSettingsDialog) {
            var groundHeightInput by remember { mutableStateOf(groundHeight.toInt().toString()) }
            var warningHeightInput by remember { mutableStateOf(warningHeight.toInt().toString()) }

            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text(stringResource(R.string.map_flight_instrument_altitude_settings_title)) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = groundHeightInput,
                            onValueChange = { groundHeightInput = it },
                            label = { Text(stringResource(R.string.map_flight_instrument_ground_height_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused && groundHeightInput == groundHeight.toInt().toString()) {
                                        groundHeightInput = ""
                                    }
                                }
                        )

                        OutlinedTextField(
                            value = warningHeightInput,
                            onValueChange = { warningHeightInput = it },
                            label = { Text(stringResource(R.string.map_flight_instrument_warning_height_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused && warningHeightInput == warningHeight.toInt().toString()) {
                                        warningHeightInput = ""
                                    }
                                }
                        )

                        Text(
                            text = stringResource(R.string.map_flight_instrument_warning_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            groundHeight = groundHeightInput.toDoubleOrNull() ?: groundHeight
                            warningHeight = warningHeightInput.toDoubleOrNull() ?: warningHeight
                            // Persist to SharedPreferences
                            prefs.edit()
                                .putFloat("hud_ground_height", groundHeight.toFloat())
                                .putFloat("hud_warning_height", warningHeight.toFloat())
                                .apply()
                            showSettingsDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}

/**
 * HUD-Style Speed Overlay for Attitude Indicator
 * Displays airspeed in km/h and miles on top left of artificial horizon
 */
@Composable
fun HUDSpeedOverlay(
    airspeed: Double, // in m/s
    modifier: Modifier = Modifier,
    scale: Float = 1.0f
) {
    // Convert m/s to km/h and knots
    val speedKmh by remember(airspeed) {
        derivedStateOf { (airspeed * 3.6).coerceIn(0.0, 9999.0) }
    }

    // Show airspeed in knots rather than mph
    val speedKts by remember(airspeed) {
        derivedStateOf { (airspeed * 1.943844).coerceIn(0.0, 9999.0) }
    }

    // Cached Paint objects with scaled text sizes
    val bgPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(0x99, 0x0A, 0x0A, 0x0A) // More transparent
            style = android.graphics.Paint.Style.FILL
        }
    }

    val kmhTextPaint = remember(scale) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(0xFF, 0x00, 0xFF, 0x00)
            textSize = 24f * scale
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
    }

    val ktsTextPaint = remember(scale) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(0xFF, 0x00, 0xFF, 0xFF) // Cyan
            textSize = 18f * scale
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
    }

    val labelPaint = remember(scale) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(0xAA, 0xFF, 0xFF, 0xFF)
            textSize = 12f * scale
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    Box(
        modifier = modifier
            .size(width = 70.dp * scale, height = 70.dp * scale)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = this.size.width
            val height = this.size.height

            if (!speedKmh.isFinite() || !speedKts.isFinite()) return@Canvas

            // Draw background with rounded corners
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRoundRect(
                    0f,
                    0f,
                    width,
                    height,
                    8f * scale,
                    8f * scale,
                    bgPaint
                )
            }

            // Draw km/h value
            drawIntoCanvas { canvas ->
                val kmhText = speedKmh.toInt().toString()
                canvas.nativeCanvas.drawText(
                    kmhText,
                    width / 2f,
                    height / 2f - 5f * scale,
                    kmhTextPaint
                )

                // km/h label
                canvas.nativeCanvas.drawText(
                    "km/h",
                    width / 2f,
                    height / 2f + 10f * scale,
                    labelPaint
                )

                // kts value (smaller, below)
                val ktsText = speedKts.toInt().toString()
                canvas.nativeCanvas.drawText(
                    ktsText,
                    width / 2f,
                    height / 2f + 28f * scale,
                    ktsTextPaint
                )

                // kts label (knots)
                canvas.nativeCanvas.drawText(
                    "kts",
                    width / 2f,
                    height / 2f + 40f * scale,
                    labelPaint
                )
            }
        }
    }
}

/**
 * Draw pitch ladder with degree markings
 */
private fun DrawScope.drawPitchLadder(
    centerX: Float,
    centerY: Float,
    pitchOffset: Float,
    radius: Float
) {
    val pitchStep = 10.0
    val pixelsPerDegree = radius * 0.02f

    for (angle in -90..90 step pitchStep.toInt()) {
        if (angle == 0) continue

        val y = centerY + pitchOffset - (angle * pixelsPerDegree)
        val lineWidth = if (angle % 20 == 0) radius * 0.5f else radius * 0.3f

        // Left line
        drawLine(
            color = Color.White,
            start = Offset(centerX - lineWidth / 2, y),
            end = Offset(centerX - lineWidth / 4, y),
            strokeWidth = 2f
        )

        // Right line
        drawLine(
            color = Color.White,
            start = Offset(centerX + lineWidth / 4, y),
            end = Offset(centerX + lineWidth / 2, y),
            strokeWidth = 2f
        )
    }
}

/**
 * Draw bank angle indicator at top
 */
private fun DrawScope.drawBankIndicator(
    centerX: Float,
    centerY: Float,
    radius: Float,
    bank: Double
) {
    // Bank scale arcs
    val arcRadius = radius * 0.85f
    val bankAngles = listOf(-60, -45, -30, -20, -10, 0, 10, 20, 30, 45, 60)

    bankAngles.forEach { angle ->
        val rad = Math.toRadians(angle.toDouble() - 90)
        val lineLength = if (angle % 30 == 0) 15f else 10f
        val startX = centerX + (arcRadius * cos(rad)).toFloat()
        val startY = centerY + (arcRadius * sin(rad)).toFloat()
        val endX = centerX + ((arcRadius - lineLength) * cos(rad)).toFloat()
        val endY = centerY + ((arcRadius - lineLength) * sin(rad)).toFloat()

        drawLine(
            color = Color.White,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 2f
        )
    }

    // Bank pointer (yellow triangle)
    val pointerRad = Math.toRadians(-bank - 90)
    val pointerRadius = radius * 0.75f
    val pointerX = centerX + (pointerRadius * cos(pointerRad)).toFloat()
    val pointerY = centerY + (pointerRadius * sin(pointerRad)).toFloat()

    val path = Path().apply {
        moveTo(pointerX, pointerY)
        lineTo(pointerX - 8f, pointerY - 12f)
        lineTo(pointerX + 8f, pointerY - 12f)
        close()
    }

    rotate(bank.toFloat(), Offset(centerX, centerY)) {
        drawPath(path, color = Color.Yellow, style = Fill)
    }
}

/**
 * Draw fixed aircraft symbol in center
 */
private fun DrawScope.drawAircraftSymbol(centerX: Float, centerY: Float, width: Float) {
    val thickness = 4f
    val wingHalfWidth = width / 2
    val fuselageHeight = width * 0.15f

    // Wings (horizontal line)
    drawLine(
        color = Color.Yellow,
        start = Offset(centerX - wingHalfWidth, centerY),
        end = Offset(centerX + wingHalfWidth, centerY),
        strokeWidth = thickness
    )

    // Center dot
    drawCircle(
        color = Color.Yellow,
        radius = 6f,
        center = Offset(centerX, centerY)
    )

    // Fuselage (small vertical line below)
    drawLine(
        color = Color.Yellow,
        start = Offset(centerX, centerY),
        end = Offset(centerX, centerY + fuselageHeight),
        strokeWidth = thickness
    )
}


/**
 * Airspeed Indicator
 * Shows indicated airspeed in knots with optional Mach display
 */
@Composable
fun AirspeedIndicator(
    airspeed: Double,
    mach: Double? = null,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    // Debug logging removed to reduce log spam

    val textPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = 24f
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    val machPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.CYAN
            textSize = 14f
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    val machFormat = stringResource(R.string.map_flight_instrument_mach_format)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = this.size.width / 2
                val centerY = this.size.height / 2
                val radius = this.size.minDimension / 2

                // Convert m/s to knots (1 m/s = 1.9438 knots)
                val speedKts = (airspeed * 1.9438).coerceIn(0.0, 600.0)

                // Draw speed tape background
                drawCircle(
                    color = Color(0xFF2A2A2A),
                    radius = radius * 0.9f,
                    center = Offset(centerX, centerY)
                )

                // Draw speed arcs and ticks
                for (speed in 0..600 step 20) {
                    val angle = (speed / 600.0) * 270.0 - 135.0 // -135° to +135°
                    val rad = Math.toRadians(angle)
                    val tickLength = if (speed % 100 == 0) 15f else if (speed % 50 == 0) 10f else 6f
                    val innerRadius = radius * 0.85f
                    val outerRadius = radius * 0.85f - tickLength

                    val startX = centerX + (innerRadius * cos(rad)).toFloat()
                    val startY = centerY + (innerRadius * sin(rad)).toFloat()
                    val endX = centerX + (outerRadius * cos(rad)).toFloat()
                    val endY = centerY + (outerRadius * sin(rad)).toFloat()

                    drawLine(
                        color = Color.White,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (speed % 100 == 0) 3f else 2f
                    )
                }

                // Draw needle
                val needleAngle = (speedKts / 600.0) * 270.0 - 135.0
                val needleRad = Math.toRadians(needleAngle)
                val needleLength = radius * 0.7f
                val needleEndX = centerX + (needleLength * cos(needleRad)).toFloat()
                val needleEndY = centerY + (needleLength * sin(needleRad)).toFloat()

                // Needle shadow
                drawLine(
                    color = Color.Black.copy(alpha = 0.5f),
                    start = Offset(centerX + 2f, centerY + 2f),
                    end = Offset(needleEndX + 2f, needleEndY + 2f),
                    strokeWidth = 5f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                // Needle
                drawLine(
                    color = Color.Yellow,
                    start = Offset(centerX, centerY),
                    end = Offset(needleEndX, needleEndY),
                    strokeWidth = 4f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                // Center hub
                drawCircle(
                    color = Color(0xFF444444),
                    radius = 8f,
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3f,
                    center = Offset(centerX, centerY)
                )

                // Digital readout
                val speedText = "${speedKts.toInt()}"
                drawContext.canvas.nativeCanvas.drawText(
                    speedText,
                    centerX,
                    centerY + radius * 0.5f,
                    textPaint
                )

                // Mach number (if available and > 0.3)
                if (mach != null && mach > 0.3) {
                    val machText = String.format(machFormat, mach)
                    drawContext.canvas.nativeCanvas.drawText(
                        machText,
                        centerX,
                        centerY + radius * 0.7f,
                        machPaint
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.map_flight_instrument_airspeed),
            fontSize = 10.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Vertical Speed Indicator (Variometer)
 * Shows rate of climb/descent in feet per minute
 */
@Composable
fun VerticalSpeedIndicator(
    verticalSpeed: Double,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    // Debug logging removed to reduce log spam

    val textPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 20f
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = this.size.width / 2
                val centerY = this.size.height / 2
                val radius = this.size.minDimension / 2

                // Convert m/s to feet per minute (1 m/s = 196.85 ft/min)
                val vsFpm = (verticalSpeed * 196.85).coerceIn(-15000.0, 15000.0) // expanded range to support higher climb/descent rates

                // Draw background
                drawCircle(
                    color = Color(0xFF2A2A2A),
                    radius = radius * 0.9f,
                    center = Offset(centerX, centerY)
                )

                // Draw VSI scale
                val scaleValues = listOf(-15000, -10000, -5000, -2000, 0, 2000, 5000, 10000, 15000)
                scaleValues.forEach { value ->
                    val angle = (value / 15000.0) * 135.0 // -135° to +135° (using ±15000 fpm)
                    val rad = Math.toRadians(angle + 90.0) // Rotate 90° so 0 is at top
                    val tickLength = if (value == 0 || Math.abs(value) == 5000 || Math.abs(value) == 15000) 15f else 10f
                    val innerRadius = radius * 0.85f
                    val outerRadius = radius * 0.85f - tickLength

                    val startX = centerX + (innerRadius * cos(rad)).toFloat()
                    val startY = centerY + (innerRadius * sin(rad)).toFloat()
                    val endX = centerX + (outerRadius * cos(rad)).toFloat()
                    val endY = centerY + (outerRadius * sin(rad)).toFloat()

                    drawLine(
                        color = if (value == 0) Color.White else Color.Gray,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (value == 0) 3f else 2f
                    )
                }

                // Draw needle
                val needleAngle = (vsFpm / 15000.0) * 135.0 + 90.0
                val needleRad = Math.toRadians(needleAngle)
                val needleLength = radius * 0.7f
                val needleEndX = centerX + (needleLength * cos(needleRad)).toFloat()
                val needleEndY = centerY + (needleLength * sin(needleRad)).toFloat()

                // Needle color based on direction
                val needleColor = when {
                    vsFpm > 100 -> Color.Green
                    vsFpm < -100 -> Color.Red
                    else -> Color.White
                }

                // Needle shadow
                drawLine(
                    color = Color.Black.copy(alpha = 0.5f),
                    start = Offset(centerX + 2f, centerY + 2f),
                    end = Offset(needleEndX + 2f, needleEndY + 2f),
                    strokeWidth = 5f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                // Needle
                drawLine(
                    color = needleColor,
                    start = Offset(centerX, centerY),
                    end = Offset(needleEndX, needleEndY),
                    strokeWidth = 4f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                // Center hub
                drawCircle(
                    color = Color(0xFF444444),
                    radius = 8f,
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3f,
                    center = Offset(centerX, centerY)
                )

                // Digital readout
                val vsInt = vsFpm.roundToInt()
                val vsText = if (vsInt > 0) "+$vsInt" else "$vsInt"
                textPaint.color = when {
                    vsFpm > 100 -> android.graphics.Color.GREEN
                    vsFpm < -100 -> android.graphics.Color.RED
                    else -> android.graphics.Color.WHITE
                }
                drawContext.canvas.nativeCanvas.drawText(
                    vsText,
                    centerX,
                    centerY + radius * 0.5f,
                    textPaint
                )
            }
        }

        Text(
            text = stringResource(R.string.map_flight_instrument_vert_speed),
            fontSize = 10.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Altimeter Indicator
 * Shows altitude MSL (top) and AGL (bottom) in feet
 */
@Composable
fun AltimeterIndicator(
    altitude: Double,
    terrainElevation: Double? = null,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    // Convert meters to feet (1m = 3.28084ft)
    val altFeet = (altitude * 3.28084).toInt()
    // Calculate AGL: if terrain elevation is available, subtract it from altitude
    val aglFeet = if (terrainElevation != null) {
        ((altitude - terrainElevation) * 3.28084).toInt()
    } else {
        altFeet // Fallback: show MSL if terrain unknown
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // ALT (MSL)
                Text(
                    text = "$altFeet",
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.map_flight_instrument_alt),
                    fontSize = 8.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(2.dp))

                // AGL (Above Ground Level)
                Text(
                    text = "$aglFeet",
                    fontSize = 14.sp,
                    color = Color.Yellow,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.map_flight_instrument_agl),
                    fontSize = 8.sp,
                    color = Color.Gray
                )
            }
        }
        Text(
            text = stringResource(R.string.map_flight_instrument_alt),
            fontSize = 8.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Heading Indicator (Compass)
 * Shows heading in degrees
 */
@Composable
fun HeadingIndicator(
    heading: Double,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    // Convert radians to degrees if needed
    val headingDeg = if (heading > 6.28) heading else Math.toDegrees(heading)
    val normalizedHeading = ((headingDeg % 360) + 360) % 360

    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 20f
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    val textPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.CYAN
            textSize = 16f
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    val cardinalN = stringResource(R.string.map_flight_instrument_cardinal_n)
    val cardinalE = stringResource(R.string.map_flight_instrument_cardinal_e)
    val cardinalS = stringResource(R.string.map_flight_instrument_cardinal_s)
    val cardinalW = stringResource(R.string.map_flight_instrument_cardinal_w)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = this.size.width / 2
                val centerY = this.size.height / 2
                val radius = this.size.minDimension / 2

                // Draw compass rose
                val cardinals = listOf(cardinalN, cardinalE, cardinalS, cardinalW)
                for (i in 0 until 4) {
                    val angle = i * 90.0
                    val rad = Math.toRadians(angle - normalizedHeading - 90)
                    // Pull the cardinal labels slightly inward so they don't clash with the numeric readout
                    val textRadius = radius * 0.55f
                    val x = centerX + (textRadius * cos(rad)).toFloat()
                    val y = centerY + (textRadius * sin(rad)).toFloat()

                    paint.color = if (i == 0) android.graphics.Color.RED else android.graphics.Color.WHITE
                    // smaller vertical nudge so the labels sit neatly on the ring without overlapping the heading number
                    drawContext.canvas.nativeCanvas.drawText(
                        cardinals[i],
                        x,
                        y + 4f,
                        paint
                    )
                }

                // Digital readout (placed clearly below the compass and centered)
                val hdgText = "${normalizedHeading.toInt()}°"
                // Nudged slightly higher to sit closer to the compass but avoid overlapping the 'S'
                drawContext.canvas.nativeCanvas.drawText(
                    hdgText,
                    centerX,
                    centerY + radius * 0.90f + 2f,
                    textPaint
                )
            }
        }
        Text(
            text = stringResource(R.string.map_flight_instrument_hdg),
            fontSize = 8.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Angle of Attack Indicator
 * Shows AoA in degrees
 */
@Composable
fun AoAIndicator(
    aoa: Double,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    // Color based on AoA value
    val aoaColor = when {
        aoa > 20.0 -> Color.Red
        aoa > 15.0 -> Color.Yellow
        aoa < -5.0 -> Color.Cyan
        else -> Color.Green
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${String.format("%.1f", aoa)}°",
                    fontSize = 18.sp,
                    color = aoaColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.map_flight_instrument_aoa),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
        Text(
            text = stringResource(R.string.map_flight_instrument_aoa),
            fontSize = 8.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * G-Meter Indicator
 * Shows G-load
 */
@Composable
fun GMeterIndicator(
    gLoad: Double,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    // Color based on G value
    val gColor = when {
        gLoad > 7.0 -> Color.Red
        gLoad > 5.0 -> Color.Yellow
        gLoad < 0.0 -> Color.Cyan
        else -> Color.White
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = String.format("%.1f", gLoad),
                    fontSize = 20.sp,
                    color = gColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.map_flight_instrument_g_load_label),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
        Text(
            text = stringResource(R.string.map_flight_instrument_g_load),
            fontSize = 8.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Fuel Indicator
 * Shows fuel remaining as percentage
 */
@Composable
fun FuelIndicator(
    fuelRemaining: Double,
    fuelTotal: Double?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    // Compute percent only if we have a valid total > 0
    val fuelPercent: Int? = if (fuelTotal != null && fuelTotal > 0.0) {
        ((fuelRemaining / fuelTotal) * 100).toInt()
    } else {
        null
    }

    // Color based on fuel level; unknown -> neutral gray
    val fuelColor = when {
        fuelPercent == null -> Color.LightGray
        fuelPercent < 15 -> Color.Red
        fuelPercent < 30 -> Color.Yellow
        else -> Color.Green
    }

    val fuelOfKg = stringResource(R.string.map_flight_instrument_fuel_of_kg, fuelTotal?.toInt() ?: 0)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Show percentage when total is known, otherwise show remaining (absolute) or "--"
                Text(
                    text = if (fuelPercent != null) "${fuelPercent}%" else if (fuelRemaining > 0.0) "${fuelRemaining.toInt()}" else stringResource(R.string.map_flight_instrument_fuel_placeholder),
                    fontSize = 18.sp,
                    color = fuelColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (fuelTotal != null && fuelTotal > 0.0) fuelOfKg else "",
                    fontSize = 9.sp,
                    color = Color.Gray
                )
            }
        }
        Text(
            text = stringResource(R.string.map_flight_instrument_fuel),
            fontSize = 8.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Engine RPM Indicator
 * Shows engine RPM with warning zones (yellow >90%, red >100%)
 * Supports single or dual engine display
 */
@Composable
fun EngineRPMIndicator(
    rpmLeft: Double,
    rpmRight: Double? = null,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    // Color based on RPM level (assuming percentage 0-100+)
    val rpmColorLeft = when {
        rpmLeft > 100.0 -> Color.Red
        rpmLeft > 90.0 -> Color.Yellow
        else -> Color.Green
    }

    val rpmColorRight = rpmRight?.let {
        when {
            it > 100.0 -> Color.Red
            it > 90.0 -> Color.Yellow
            else -> Color.Green
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = this.size.width / 2
                val centerY = this.size.height / 2
                val radius = this.size.minDimension / 2

                // Draw RPM arc scale (0-110%)
                // Green zone: 0-90°, Yellow: 90-100°, Red: 100-110°
                val startAngle = 135f
                val sweepAngle = 270f

                // Green zone (0-90%)
                drawArc(
                    color = Color.Green.copy(alpha = 0.3f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * 0.818f, // 90% of 110%
                    useCenter = false,
                    topLeft = Offset(centerX - radius * 0.8f, centerY - radius * 0.8f),
                    size = Size(radius * 1.6f, radius * 1.6f),
                    style = Stroke(width = 8f)
                )

                // Yellow zone (90-100%)
                drawArc(
                    color = Color.Yellow.copy(alpha = 0.3f),
                    startAngle = startAngle + sweepAngle * 0.818f,
                    sweepAngle = sweepAngle * 0.091f, // 10% of 110%
                    useCenter = false,
                    topLeft = Offset(centerX - radius * 0.8f, centerY - radius * 0.8f),
                    size = Size(radius * 1.6f, radius * 1.6f),
                    style = Stroke(width = 8f)
                )

                // Red zone (100-110%)
                drawArc(
                    color = Color.Red.copy(alpha = 0.3f),
                    startAngle = startAngle + sweepAngle * 0.909f,
                    sweepAngle = sweepAngle * 0.091f, // 10% of 110%
                    useCenter = false,
                    topLeft = Offset(centerX - radius * 0.8f, centerY - radius * 0.8f),
                    size = Size(radius * 1.6f, radius * 1.6f),
                    style = Stroke(width = 8f)
                )

                // Draw needle for left engine (or single engine)
                val rpmAngle = startAngle + (rpmLeft.coerceIn(0.0, 110.0) / 110.0) * sweepAngle
                val rpmRad = Math.toRadians(rpmAngle.toDouble())
                val needleLength = radius * 0.65f
                val needleEndX = centerX + (needleLength * cos(rpmRad)).toFloat()
                val needleEndY = centerY + (needleLength * sin(rpmRad)).toFloat()

                drawLine(
                    color = rpmColorLeft,
                    start = Offset(centerX, centerY),
                    end = Offset(needleEndX, needleEndY),
                    strokeWidth = 3f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                // If dual engine, draw second needle slightly offset
                if (rpmRight != null) {
                    val rpmAngle2 = startAngle + (rpmRight.coerceIn(0.0, 110.0) / 110.0) * sweepAngle
                    val rpmRad2 = Math.toRadians(rpmAngle2)
                    val needleEndX2 = centerX + (needleLength * cos(rpmRad2)).toFloat()
                    val needleEndY2 = centerY + (needleLength * sin(rpmRad2)).toFloat()

                    drawLine(
                        color = rpmColorRight ?: Color.Gray,
                        start = Offset(centerX, centerY),
                        end = Offset(needleEndX2, needleEndY2),
                        strokeWidth = 2f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }

                // Center hub
                drawCircle(
                    color = Color(0xFF444444),
                    radius = 6f,
                    center = Offset(centerX, centerY)
                )
            }

            // Digital readout at bottom
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${rpmLeft.toInt()}%",
                    fontSize = 12.sp,
                    color = rpmColorLeft,
                    fontWeight = FontWeight.Bold
                )
                if (rpmRight != null) {
                    Text(
                        text = "${rpmRight.toInt()}%",
                        fontSize = 10.sp,
                        color = rpmColorRight ?: Color.Gray
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.map_flight_instrument_rpm),
            fontSize = 8.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Wind Indicator
 * Shows wind speed and direction as an arrow/compass
 */
@Composable
fun WindIndicator(
    windSpeed: Double,
    windDirection: Double,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    // Convert wind speed from m/s to knots
    val windKts = (windSpeed * 1.9438).toInt()
    val cardinalN = stringResource(R.string.map_flight_instrument_cardinal_n)
    val cardinalE = stringResource(R.string.map_flight_instrument_cardinal_e)
    val cardinalS = stringResource(R.string.map_flight_instrument_cardinal_s)
    val cardinalW = stringResource(R.string.map_flight_instrument_cardinal_w)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = this.size.width / 2
                val centerY = this.size.height / 2
                val radius = this.size.minDimension / 2

                // Draw compass ring
                drawCircle(
                    color = Color(0xFF2A2A2A),
                    radius = radius * 0.85f,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 2f)
                )

                // Draw cardinal directions
                val cardinals = listOf(cardinalN, cardinalE, cardinalS, cardinalW)
                for (i in 0 until 4) {
                    val angle = i * 90.0 - 90.0
                    val rad = Math.toRadians(angle)
                    val textRadius = radius * 0.65f
                    val x = centerX + (textRadius * cos(rad)).toFloat()
                    val y = centerY + (textRadius * sin(rad)).toFloat()

                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = android.graphics.Color.GRAY
                        textSize = 12f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        cardinals[i],
                        x,
                        y + 4f,
                        paint
                    )
                }

                // Draw wind arrow
                val windRad = Math.toRadians(windDirection - 90.0)
                val arrowLength = radius * 0.5f
                val arrowEndX = centerX + (arrowLength * cos(windRad)).toFloat()
                val arrowEndY = centerY + (arrowLength * sin(windRad)).toFloat()

                // Arrow shaft
                drawLine(
                    color = Color.Cyan,
                    start = Offset(centerX, centerY),
                    end = Offset(arrowEndX, arrowEndY),
                    strokeWidth = 3f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                // Arrow head
                val arrowHeadSize = 8f
                val arrowAngle1 = windRad + Math.toRadians(150.0)
                val arrowAngle2 = windRad - Math.toRadians(150.0)

                drawLine(
                    color = Color.Cyan,
                    start = Offset(arrowEndX, arrowEndY),
                    end = Offset(
                        arrowEndX + (arrowHeadSize * cos(arrowAngle1)).toFloat(),
                        arrowEndY + (arrowHeadSize * sin(arrowAngle1)).toFloat()
                    ),
                    strokeWidth = 3f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                drawLine(
                    color = Color.Cyan,
                    start = Offset(arrowEndX, arrowEndY),
                    end = Offset(
                        arrowEndX + (arrowHeadSize * cos(arrowAngle2)).toFloat(),
                        arrowEndY + (arrowHeadSize * sin(arrowAngle2)).toFloat()
                    ),
                    strokeWidth = 3f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }

            // Wind speed readout at bottom
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$windKts${stringResource(R.string.map_flight_instrument_wind_unit_kts)}",
                    fontSize = 11.sp,
                    color = Color.Cyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${windDirection.toInt()}°",
                    fontSize = 9.sp,
                    color = Color.Gray
                )
            }
        }
        Text(
            text = stringResource(R.string.map_flight_instrument_wind),
            fontSize = 8.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Countermeasures Indicator
 * Shows remaining flares and chaff counts
 */
@Composable
fun CountermeasuresIndicator(
    flareCount: Int,
    chaffCount: Int,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(4.dp)
            ) {
                // Flares (hot)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🔥",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "$flareCount",
                        fontSize = 14.sp,
                        color = if (flareCount < 10) Color.Red else if (flareCount < 20) Color.Yellow else Color.Green,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Chaff (metallic)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "⚡",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "$chaffCount",
                        fontSize = 14.sp,
                        color = if (chaffCount < 10) Color.Red else if (chaffCount < 20) Color.Yellow else Color.Green,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.map_flight_instrument_cmds),
            fontSize = 8.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Enhanced 3D Altitude Indicator with HUD-style display
 * Combines tape-style altimeter with terrain awareness, trend vectors, and 3D visual effects
 *
 * Features:
 * - Vertical scrolling altitude tape (glass cockpit PFD standard)
 * - 3D depth effects with shadows and glows (HUD-style)
 * - Terrain elevation overlay with AGL visualization
 * - Vertical speed trend vector
 * - Color-coded altitude zones (green/yellow/red based on AGL)
 * - Performance optimized with cached Paint objects
 */
@Composable
fun EnhancedAltitudeIndicator(
    altitude: Double,
    terrainElevation: Double? = null,
    verticalSpeed: Double? = null,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    showTerrainProfile: Boolean = true,
    showTrendVector: Boolean = true
) {
    // Convert meters to feet
    val altFeet by remember(altitude) {
        derivedStateOf { (altitude * 3.28084).coerceIn(-1000.0, 99999.0) }
    }

    val aglFeet by remember(altitude, terrainElevation) {
        derivedStateOf {
            if (terrainElevation != null) {
                ((altitude - terrainElevation) * 3.28084).coerceIn(-1000.0, 99999.0)
            } else {
                altFeet
            }
        }
    }

    val vsFpm by remember(verticalSpeed) {
        derivedStateOf {
            ((verticalSpeed ?: 0.0) * 196.85).coerceIn(-15000.0, 15000.0)
        }
    }

    // Predicted altitude in 6 seconds (standard trend vector)
    val trendAltitude by remember(altFeet, vsFpm) {
        derivedStateOf {
            (altFeet + (vsFpm / 60.0 * 6.0)).coerceIn(-1000.0, 99999.0)
        }
    }

    // Color coding based on AGL (aviation standards)
    val aglColor by remember(aglFeet) {
        derivedStateOf {
            when {
                aglFeet < 100 -> Color(0xFFFF0000) // Red: Critical
                aglFeet < 500 -> Color(0xFFFFAA00) // Amber: Caution
                aglFeet < 1000 -> Color(0xFFFFFF00) // Yellow: Warning
                else -> Color(0xFF00FF00) // Green: Safe (HUD standard color)
            }
        }
    }

    // Cached Paint objects for performance
    val tapePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(0xCC, 0x1A, 0x1A, 0x1A)
            style = android.graphics.Paint.Style.FILL
        }
    }

    val tapeStrokePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(0xFF, 0x00, 0xFF, 0x00) // HUD green
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f
        }
    }

    val textPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(0xFF, 0x00, 0xFF, 0x00) // HUD green
            textSize = 24f
            textAlign = android.graphics.Paint.Align.RIGHT
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    val textShadowPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(0xAA, 0x00, 0x00, 0x00)
            textSize = 24f
            textAlign = android.graphics.Paint.Align.RIGHT
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    val smallTextPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(0xFF, 0x00, 0xFF, 0xFF) // Cyan for secondary info
            textSize = 16f
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    val centerBoxPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(0xEE, 0x00, 0x00, 0x00)
            style = android.graphics.Paint.Style.FILL
        }
    }

    val centerBoxStrokePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f
        }
    }

    val terrainPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(width = size * 1.2f, height = size * 2f)
                .background(Color(0xFF0A0A0A))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = this.size.width
                val height = this.size.height
                val centerY = height / 2f
                val tapeWidth = width * 0.6f
                val tapeLeft = width - tapeWidth

                // Safe values check
                if (!altFeet.isFinite() || !aglFeet.isFinite()) return@Canvas

                // Draw altitude tape background with 3D depth effect
                drawIntoCanvas { canvas ->
                    // Shadow for depth
                    val shadowPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = android.graphics.Color.argb(0x80, 0x00, 0x00, 0x00)
                        maskFilter = android.graphics.BlurMaskFilter(8f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.drawRect(
                        tapeLeft + 4f,
                        4f,
                        width + 4f,
                        height + 4f,
                        shadowPaint
                    )

                    // Tape background
                    canvas.nativeCanvas.drawRect(
                        tapeLeft,
                        0f,
                        width,
                        height,
                        tapePaint
                    )

                    // Tape border
                    canvas.nativeCanvas.drawRect(
                        tapeLeft,
                        0f,
                        width,
                        height,
                        tapeStrokePaint
                    )
                }

                // Draw altitude tick marks and numbers
                val altitudeRange = 1000f // Show ±500ft range
                val pixelsPerFoot = height / altitudeRange
                val tickInterval = 100 // Major ticks every 100ft
                val minorTickInterval = 20 // Minor ticks every 20ft

                val minAlt = (altFeet - 500).toInt()
                val maxAlt = (altFeet + 500).toInt()

                drawIntoCanvas { canvas ->
                    // Draw ticks
                    for (alt in ((minAlt / minorTickInterval) * minorTickInterval)..(maxAlt / minorTickInterval) * minorTickInterval step minorTickInterval) {
                        val yPos = centerY - ((alt - altFeet) * pixelsPerFoot).toFloat()

                        if (yPos < 0 || yPos > height) continue

                        val isMajorTick = alt % tickInterval == 0
                        val tickLength = if (isMajorTick) tapeWidth * 0.3f else tapeWidth * 0.15f

                        // Tick line
                        val tickPaint = if (isMajorTick) tapeStrokePaint else android.graphics.Paint().apply {
                            isAntiAlias = true
                            color = android.graphics.Color.argb(0x88, 0x00, 0xFF, 0x00)
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 1f
                        }

                        canvas.nativeCanvas.drawLine(
                            width - tickLength,
                            yPos,
                            width,
                            yPos,
                            tickPaint
                        )

                        // Major tick labels
                        if (isMajorTick && abs(yPos - centerY) > 40f) {
                            val label = (alt / 100).toString()
                            // Shadow
                            canvas.nativeCanvas.drawText(
                                label,
                                width - tickLength - 8f,
                                yPos + 10f,
                                textShadowPaint
                            )
                            // Text
                            canvas.nativeCanvas.drawText(
                                label,
                                width - tickLength - 10f,
                                yPos + 8f,
                                textPaint
                            )
                        }
                    }
                }

                // Draw trend vector (6-second altitude prediction)
                if (showTrendVector && abs(vsFpm) > 100) {
                    val trendY = centerY - ((trendAltitude - altFeet) * pixelsPerFoot).toFloat()
                    val trendColor = if (vsFpm > 0) Color(0xFF00FF00) else Color(0xFFFF6600)

                    // Trend arrow with glow effect
                    drawLine(
                        color = trendColor.copy(alpha = 0.3f),
                        start = Offset(width - tapeWidth * 0.5f - 2f, centerY),
                        end = Offset(width - tapeWidth * 0.5f - 2f, trendY.coerceIn(20f, height - 20f)),
                        strokeWidth = 8f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    drawLine(
                        color = trendColor,
                        start = Offset(width - tapeWidth * 0.5f, centerY),
                        end = Offset(width - tapeWidth * 0.5f, trendY.coerceIn(20f, height - 20f)),
                        strokeWidth = 3f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )

                    // Trend arrow head
                    val arrowY = trendY.coerceIn(20f, height - 20f)
                    val arrowSize = 8f
                    val arrowPath = Path().apply {
                        if (vsFpm > 0) {
                            moveTo(width - tapeWidth * 0.5f, arrowY)
                            lineTo(width - tapeWidth * 0.5f - arrowSize, arrowY + arrowSize)
                            lineTo(width - tapeWidth * 0.5f + arrowSize, arrowY + arrowSize)
                        } else {
                            moveTo(width - tapeWidth * 0.5f, arrowY)
                            lineTo(width - tapeWidth * 0.5f - arrowSize, arrowY - arrowSize)
                            lineTo(width - tapeWidth * 0.5f + arrowSize, arrowY - arrowSize)
                        }
                        close()
                    }
                    drawPath(arrowPath, trendColor)
                }

                // Draw center altitude readout box (current altitude)
                drawIntoCanvas { canvas ->
                    val boxHeight = 50f
                    val boxWidth = tapeWidth * 1.3f
                    val boxLeft = width - boxWidth
                    val boxTop = centerY - boxHeight / 2f

                    // Box shadow for 3D depth
                    val boxShadowPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = android.graphics.Color.argb(0xAA, 0x00, 0x00, 0x00)
                        maskFilter = android.graphics.BlurMaskFilter(6f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.drawRoundRect(
                        boxLeft + 3f,
                        boxTop + 3f,
                        width + 3f,
                        boxTop + boxHeight + 3f,
                        8f,
                        8f,
                        boxShadowPaint
                    )

                    // Box background
                    canvas.nativeCanvas.drawRoundRect(
                        boxLeft,
                        boxTop,
                        width,
                        boxTop + boxHeight,
                        8f,
                        8f,
                        centerBoxPaint
                    )

                    // Box border with AGL-based color
                    centerBoxStrokePaint.color = aglColor.value.toInt()
                    canvas.nativeCanvas.drawRoundRect(
                        boxLeft,
                        boxTop,
                        width,
                        boxTop + boxHeight,
                        8f,
                        8f,
                        centerBoxStrokePaint
                    )

                    // Current altitude text
                    val altText = String.format("%05d", altFeet.toInt())
                    val altTextPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = android.graphics.Color.argb(0xFF, 0x00, 0xFF, 0x00)
                        textSize = 32f
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                    }

                    // Text shadow
                    val altTextShadowPaint = android.graphics.Paint(altTextPaint).apply {
                        color = android.graphics.Color.argb(0xDD, 0x00, 0x00, 0x00)
                    }

                    canvas.nativeCanvas.drawText(
                        altText,
                        boxLeft + boxWidth / 2f + 1f,
                        centerY + 12f,
                        altTextShadowPaint
                    )
                    canvas.nativeCanvas.drawText(
                        altText,
                        boxLeft + boxWidth / 2f,
                        centerY + 11f,
                        altTextPaint
                    )
                }

                // Draw terrain elevation bar (if available)
                if (showTerrainProfile && terrainElevation != null) {
                    val terrainFeet = (terrainElevation * 3.28084).coerceIn(-1000.0, 99999.0)
                    val terrainY = centerY - ((terrainFeet - altFeet) * pixelsPerFoot).toFloat()

                    if (terrainY > 0 && terrainY < height) {
                        // Terrain baseline
                        terrainPaint.color = android.graphics.Color.argb(0x88, 0x8B, 0x45, 0x13) // Semi-transparent brown
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawRect(
                                0f,
                                terrainY,
                                tapeLeft,
                                height,
                                terrainPaint
                            )
                        }

                        // Terrain line
                        drawLine(
                            color = Color(0xFF8B4513),
                            start = Offset(0f, terrainY),
                            end = Offset(tapeLeft, terrainY),
                            strokeWidth = 2f
                        )
                    }

                    // AGL digital readout at bottom
                    drawIntoCanvas { canvas ->
                        val aglText = "${aglFeet.toInt()} AGL"
                        val aglPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            color = aglColor.value.toInt()
                            textSize = 20f
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                        }

                        // Background box for AGL
                        val aglBoxPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            color = android.graphics.Color.argb(0xDD, 0x00, 0x00, 0x00)
                            style = android.graphics.Paint.Style.FILL
                        }

                        val textBounds = android.graphics.Rect()
                        aglPaint.getTextBounds(aglText, 0, aglText.length, textBounds)

                        val aglBoxY = height - 35f
                        canvas.nativeCanvas.drawRoundRect(
                            width / 2f - textBounds.width() / 2f - 8f,
                            aglBoxY - textBounds.height() - 4f,
                            width / 2f + textBounds.width() / 2f + 8f,
                            aglBoxY + 4f,
                            4f,
                            4f,
                            aglBoxPaint
                        )

                        canvas.nativeCanvas.drawText(
                            aglText,
                            width / 2f,
                            aglBoxY,
                            aglPaint
                        )
                    }
                }

                // Draw "ALT" label at top
                drawIntoCanvas { canvas ->
                    val labelPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = android.graphics.Color.argb(0xFF, 0x00, 0xFF, 0x00)
                        textSize = 16f
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                    }
                    canvas.nativeCanvas.drawText("ALT", width / 2f, 20f, labelPaint)
                }
            }
        }

        Text(
            text = stringResource(R.string.map_flight_instrument_alt),
            fontSize = 8.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}


