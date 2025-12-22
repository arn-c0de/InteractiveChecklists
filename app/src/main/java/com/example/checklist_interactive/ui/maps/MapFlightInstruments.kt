package com.example.checklist_interactive.ui.maps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import android.util.Log

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
    // Log when the instruments composable is active and whenever data changes
    LaunchedEffect(enabled, pitch, bank, verticalSpeed, airspeed, altitude, heading, angleOfAttack, gLoad, dataAvailable, fuelRemaining, fuelTotal) {
        Log.d("MapFlightInstruments", "composed enabled=$enabled dataAvailable=$dataAvailable pitch=$pitch bank=$bank vs=$verticalSpeed ias=$airspeed alt=$altitude hdg=$heading aoa=$angleOfAttack g=$gLoad rpm_l=$engineRpmLeft rpm_r=$engineRpmRight wind=${windSpeed}@${windDirection}° flares=$flareCount chaff=$chaffCount fuelRem=$fuelRemaining fuelTot=$fuelTotal")
    }

    if (!enabled) return

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
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Primary flight instruments (top row)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Airspeed Indicator
                        AirspeedIndicator(
                            airspeed = airspeed ?: 0.0,
                            mach = mach,
                            size = 120.dp
                        )

                        // Attitude Indicator (larger, centered)
                        AttitudeIndicator(
                            pitch = pitch,
                            bank = bank,
                            size = 160.dp
                        )

                        // Vertical Speed Indicator
                        VerticalSpeedIndicator(
                            verticalSpeed = verticalSpeed ?: 0.0,
                            size = 120.dp
                        )
                    }

                    // Secondary instruments (bottom row - compact)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Heading Indicator
                        HeadingIndicator(
                            heading = heading ?: 0.0,
                            size = 64.dp
                        )

                        // Angle of Attack
                        AoAIndicator(
                            aoa = angleOfAttack ?: 0.0,
                            size = 64.dp
                        )

                        // G-Meter (left side)
                        GMeterIndicator(
                            gLoad = gLoad ?: 1.0,
                            size = 64.dp
                        )

                        // Altimeter (center-left)
                        AltimeterIndicator(
                            altitude = altitude ?: 0.0,
                            terrainElevation = terrainElevation,
                            size = 64.dp
                        )

                        // Engine RPM Indicator (center-right)
                        EngineRPMIndicator(
                            rpmLeft = engineRpmLeft ?: 0.0,
                            rpmRight = engineRpmRight,
                            size = 64.dp
                        )

                        // Fuel Indicator (right side)
                        FuelIndicator(
                            fuelRemaining = fuelRemaining ?: 0.0,
                            fuelTotal = fuelTotal ?: 1.0,
                            size = 64.dp
                        )

                        // Wind Indicator
                        WindIndicator(
                            windSpeed = windSpeed ?: 0.0,
                            windDirection = windDirection ?: 0.0,
                            size = 64.dp
                        )

                        // Countermeasures Indicator
                        CountermeasuresIndicator(
                            flareCount = flareCount ?: 0,
                            chaffCount = chaffCount ?: 0,
                            size = 64.dp
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
                        Text(text = "NO DATA", color = Color.Yellow, fontSize = 12.sp)
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
 */
@Composable
fun AttitudeIndicator(
    pitch: Double, // in degrees
    bank: Double, // in degrees
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    // Debug log for visibility
    LaunchedEffect(pitch, bank) {
        Log.d("AttitudeIndicator", "pitch=${pitch}° bank=${bank}°")
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
                    drawPitchLadder(centerX, centerY, pitchOffset, radius, pitch)
                }
                
                // Bank angle indicator (fixed reference)
                drawBankIndicator(centerX, centerY, radius, bank)
                
                // Aircraft symbol (fixed in center)
                drawAircraftSymbol(centerX, centerY, radius * 0.6f)
            }
        }
        
        Text(
            text = "ATTITUDE",
            fontSize = 10.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Draw pitch ladder with degree markings
 */
private fun DrawScope.drawPitchLadder(
    centerX: Float,
    centerY: Float,
    pitchOffset: Float,
    radius: Float,
    pitch: Double
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
        drawPath(path, color = Color.Yellow, style = androidx.compose.ui.graphics.drawscope.Fill)
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
    LaunchedEffect(airspeed, mach) {
        Log.d("AirspeedIndicator", "airspeed=$airspeed mach=$mach")
    }

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
                    val machText = "M${String.format("%.2f", mach)}"
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
            text = "AIRSPEED",
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
    LaunchedEffect(verticalSpeed) {
        Log.d("VerticalSpeedIndicator", "verticalSpeed=$verticalSpeed")
    }

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
                val vsFpm = (verticalSpeed * 196.85).coerceIn(-6000.0, 6000.0)

                // Draw background
                drawCircle(
                    color = Color(0xFF2A2A2A),
                    radius = radius * 0.9f,
                    center = Offset(centerX, centerY)
                )

                // Draw VSI scale
                val scaleValues = listOf(-6000, -4000, -2000, -1000, 0, 1000, 2000, 4000, 6000)
                scaleValues.forEach { value ->
                    val angle = (value / 6000.0) * 135.0 // -135° to +135°
                    val rad = Math.toRadians(angle + 90.0) // Rotate 90° so 0 is at top
                    val tickLength = if (value == 0 || Math.abs(value) == 2000 || Math.abs(value) == 6000) 15f else 10f
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
                val needleAngle = (vsFpm / 6000.0) * 135.0 + 90.0
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
                val vsText = if (vsFpm >= 0) "+${vsFpm.toInt()}" else "${vsFpm.toInt()}"
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
            text = "VERT SPEED",
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
                    text = "${altFeet}",
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = "ALT",
                    fontSize = 8.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // AGL (Above Ground Level)
                Text(
                    text = "${aglFeet}",
                    fontSize = 14.sp,
                    color = Color.Yellow,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = "AGL",
                    fontSize = 8.sp,
                    color = Color.Gray
                )
            }
        }
        Text(
            text = "ALT",
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
                val cardinals = listOf("N", "E", "S", "W")
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
            text = "HDG",
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = "AoA",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
        Text(
            text = "AoA",
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
                    text = "${String.format("%.1f", gLoad)}",
                    fontSize = 20.sp,
                    color = gColor,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = "G",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
        Text(
            text = "G-LOAD",
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
                    text = if (fuelPercent != null) "${fuelPercent}%" else if (fuelRemaining > 0.0) "${fuelRemaining.toInt()}" else "--",
                    fontSize = 18.sp,
                    color = fuelColor,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = if (fuelTotal != null && fuelTotal > 0.0) "of ${fuelTotal.toInt()}kg" else "",
                    fontSize = 9.sp,
                    color = Color.Gray
                )
            }
        }
        Text(
            text = "FUEL",
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
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
            text = "RPM",
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
                val cardinals = listOf("N", "E", "S", "W")
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
                    text = "${windKts}kt",
                    fontSize = 11.sp,
                    color = Color.Cyan,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = "${windDirection.toInt()}°",
                    fontSize = 9.sp,
                    color = Color.Gray
                )
            }
        }
        Text(
            text = "WIND",
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
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
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
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }
        Text(
            text = "CMDS",
            fontSize = 8.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}


