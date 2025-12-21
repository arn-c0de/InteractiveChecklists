package com.example.checklist_interactive.ui.maps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.checklist_interactive.data.prefs.PreferencesManager
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * MapFlightInstruments - Draggable flight instruments overlay for map view
 * 
 * Displays aviation instruments with real-time DataPad data:
 * - Attitude Indicator (Artificial Horizon)
 * - Turn and Slip Indicator
 * 
 * Each instrument is individually draggable and positions are persisted.
 */
@Composable
fun MapFlightInstruments(
    modifier: Modifier = Modifier,
    pitch: Double = 0.0,
    bank: Double = 0.0,
    turnRate: Double = 0.0,
    slip: Double = 0.0,
    enabled: Boolean = true
) {
    if (!enabled) return

    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }
    
    // Load saved positions or use defaults
    val (attitudeX, setAttitudeX) = remember { 
        mutableStateOf(prefsManager.getInstrumentPosition("attitude_x") ?: 16f) 
    }
    val (attitudeY, setAttitudeY) = remember { 
        mutableStateOf(prefsManager.getInstrumentPosition("attitude_y") ?: 500f) 
    }
    val (turnSlipX, setTurnSlipX) = remember { 
        mutableStateOf(prefsManager.getInstrumentPosition("turnslip_x") ?: 200f) 
    }
    val (turnSlipY, setTurnSlipY) = remember { 
        mutableStateOf(prefsManager.getInstrumentPosition("turnslip_y") ?: 500f) 
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Attitude Indicator
        DraggableInstrument(
            x = attitudeX,
            y = attitudeY,
            onPositionChange = { dx, dy ->
                val newX = (attitudeX + dx).coerceIn(0f, 1000f)
                val newY = (attitudeY + dy).coerceIn(0f, 1000f)
                setAttitudeX(newX)
                setAttitudeY(newY)
                prefsManager.saveInstrumentPosition("attitude_x", newX)
                prefsManager.saveInstrumentPosition("attitude_y", newY)
            }
        ) {
            AttitudeIndicator(
                pitch = pitch,
                bank = bank,
                size = 140.dp
            )
        }

        // Turn and Slip Indicator
        DraggableInstrument(
            x = turnSlipX,
            y = turnSlipY,
            onPositionChange = { dx, dy ->
                val newX = (turnSlipX + dx).coerceIn(0f, 1000f)
                val newY = (turnSlipY + dy).coerceIn(0f, 1000f)
                setTurnSlipX(newX)
                setTurnSlipY(newY)
                prefsManager.saveInstrumentPosition("turnslip_x", newX)
                prefsManager.saveInstrumentPosition("turnslip_y", newY)
            }
        ) {
            TurnAndSlipIndicator(
                turnRate = turnRate,
                slip = slip,
                size = 140.dp
            )
        }
    }
}

/**
 * Wrapper that makes an instrument draggable
 */
@Composable
private fun DraggableInstrument(
    x: Float,
    y: Float,
    onPositionChange: (Float, Float) -> Unit,
    content: @Composable () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current

    Box(
        modifier = Modifier
            .offset(x.dp, y.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    // consume the pointer event to avoid other gesture interference
                    // NOTE: `consumeAllChanges()` is not available in older Compose versions; use `consume()`
                    change.consume()

                    // Convert pixel drag amounts to dp units
                    val dxDp = with(density) { dragAmount.x.toDp().value }
                    val dyDp = with(density) { dragAmount.y.toDp().value }

                    onPositionChange(dxDp, dyDp)
                }
            }
    ) {
        content()
    }
}

/**
 * Attitude Indicator (Artificial Horizon)
 * Shows pitch and bank angles
 */
@Composable
fun AttitudeIndicator(
    pitch: Double,
    bank: Double,
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


