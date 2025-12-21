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
    turnRate: Double = 0.0,
    slip: Double = 0.0,
    verticalSpeed: Double? = null,
    airspeed: Double? = null,
    enabled: Boolean = true
) {
    // Log when the instruments composable is active and whenever data changes
    LaunchedEffect(enabled, pitch, bank, turnRate, slip, verticalSpeed, airspeed) {
        Log.d("MapFlightInstruments", "composed enabled=$enabled pitch=$pitch bank=$bank turnRate=$turnRate slip=$slip vs=$verticalSpeed ias=$airspeed")
    }

    if (!enabled) return

    // Horizontal panel at bottom center
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .wrapContentSize(),
            tonalElevation = 8.dp,
            shape = MaterialTheme.shapes.large,
            color = Color(0xCC000000) // Semi-transparent black background
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Airspeed Indicator
                AirspeedIndicator(
                    airspeed = airspeed ?: 0.0,
                    size = 120.dp
                )

                // Attitude Indicator
                AttitudeIndicator(
                    pitch = pitch,
                    bank = bank,
                    size = 120.dp
                )

                // Vertical Speed Indicator
                VerticalSpeedIndicator(
                    verticalSpeed = verticalSpeed ?: 0.0,
                    size = 120.dp
                )

                // Turn and Slip Indicator
                TurnAndSlipIndicator(
                    turnRate = turnRate,
                    slip = slip,
                    size = 120.dp
                )
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
 * Turn and Slip Indicator
 * Shows turn rate and slip/skid
 */
@Composable
fun TurnAndSlipIndicator(
    turnRate: Double,
    slip: Double,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(turnRate, slip) {
        Log.d("TurnAndSlipIndicator", "turnRate=$turnRate slip=$slip")
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
                
                // Turn coordinator (upper half)
                drawTurnCoordinator(centerX, centerY * 0.6f, radius * 0.7f, turnRate)
                
                // Slip indicator (lower half)
                drawSlipIndicator(centerX, centerY * 1.4f, radius * 0.8f, slip)
            }
        }
        
        Text(
            text = "TURN & SLIP",
            fontSize = 10.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Draw turn coordinator (miniature aircraft tilting)
 */
private fun DrawScope.drawTurnCoordinator(
    centerX: Float,
    centerY: Float,
    size: Float,
    turnRate: Double
) {
    // Standard rate turn is 3°/sec, max deflection at 6°/sec
    val maxDeflection = 30.0
    val deflection = (turnRate * 5).coerceIn(-maxDeflection, maxDeflection)
    
    // Reference marks (L and R)
    drawLine(
        color = Color.White,
        start = Offset(centerX - size * 0.8f, centerY),
        end = Offset(centerX - size * 0.7f, centerY),
        strokeWidth = 2f
    )
    drawLine(
        color = Color.White,
        start = Offset(centerX + size * 0.7f, centerY),
        end = Offset(centerX + size * 0.8f, centerY),
        strokeWidth = 2f
    )
    
    // Miniature aircraft
    rotate(deflection.toFloat(), Offset(centerX, centerY)) {
        // Wings
        drawLine(
            color = Color.White,
            start = Offset(centerX - size * 0.5f, centerY),
            end = Offset(centerX + size * 0.5f, centerY),
            strokeWidth = 3f
        )
        // Fuselage
        drawLine(
            color = Color.White,
            start = Offset(centerX, centerY - size * 0.15f),
            end = Offset(centerX, centerY + size * 0.3f),
            strokeWidth = 3f
        )
    }
}

/**
 * Draw slip/skid indicator (ball in tube)
 */
private fun DrawScope.drawSlipIndicator(
    centerX: Float,
    centerY: Float,
    width: Float,
    slip: Double
) {
    val tubeWidth = width
    val tubeHeight = 20f
    val ballRadius = 8f
    
    // Tube outline
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(centerX - tubeWidth / 2, centerY - tubeHeight / 2),
        size = Size(tubeWidth, tubeHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(tubeHeight / 2, tubeHeight / 2),
        style = Stroke(width = 2f)
    )
    
    // Reference marks
    val markWidth = 2f
    drawLine(
        color = Color.White,
        start = Offset(centerX - markWidth, centerY - tubeHeight * 0.7f),
        end = Offset(centerX - markWidth, centerY + tubeHeight * 0.7f),
        strokeWidth = 2f
    )
    drawLine(
        color = Color.White,
        start = Offset(centerX + markWidth, centerY - tubeHeight * 0.7f),
        end = Offset(centerX + markWidth, centerY + tubeHeight * 0.7f),
        strokeWidth = 2f
    )
    
    // Ball (moves with slip, clamped to tube)
    val maxDeflection = tubeWidth / 2 - ballRadius - 4f
    val ballOffset = (slip * 20).coerceIn(-maxDeflection.toDouble(), maxDeflection.toDouble()).toFloat()
    
    drawCircle(
        color = Color.White,
        radius = ballRadius,
        center = Offset(centerX + ballOffset, centerY)
    )
    
    // Shading for 3D effect
    drawCircle(
        color = Color.White.copy(alpha = 0.3f),
        radius = ballRadius * 0.6f,
        center = Offset(centerX + ballOffset - 2f, centerY - 2f)
    )
}

/**
 * Airspeed Indicator
 * Shows indicated airspeed in knots
 */
@Composable
fun AirspeedIndicator(
    airspeed: Double,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(airspeed) {
        Log.d("AirspeedIndicator", "airspeed=$airspeed")
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
                val textPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.WHITE
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText(
                    speedText,
                    centerX,
                    centerY + radius * 0.5f,
                    textPaint
                )
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
                val textPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = when {
                        vsFpm > 100 -> android.graphics.Color.GREEN
                        vsFpm < -100 -> android.graphics.Color.RED
                        else -> android.graphics.Color.WHITE
                    }
                    textSize = 20f
                    textAlign = android.graphics.Paint.Align.CENTER
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


