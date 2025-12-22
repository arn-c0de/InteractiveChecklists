package com.example.checklist_interactive.ui.maps.drawing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.sqrt

/**
 * Drawing overlay for the map
 * Renders strokes on top of the MapView
 * Always renders saved strokes, but only accepts input when drawing mode is active
 */
@Composable
fun MapDrawingOverlay(
    mapView: MapView?,
    drawingState: MapDrawingState,
    strokes: List<MapDrawingStroke>,
    currentStroke: MapDrawingStroke?,
    onStrokeComplete: (MapDrawingStroke) -> Unit,
    onStrokesErased: (List<MapDrawingStroke>) -> Unit,
    onLongPress: ((Offset) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var pointerPosition by remember { mutableStateOf<Offset?>(null) }
    val currentGeoPoints = remember { mutableStateListOf<GeoPoint>() }
    var showRadialMenu by remember { mutableStateOf(false) }
    var radialMenuPosition by remember { mutableStateOf(Offset.Zero) }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Canvas for rendering strokes (always visible)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    // Only enable input when drawing mode is active
                    if (drawingState.isDrawingMode) {
                        Modifier.pointerInput(drawingState, mapView) {
                            val currentMapView = mapView
                            if (currentMapView == null) return@pointerInput
                            
                            // Detect long press for radial menu
                            detectTapGestures(
                                onLongPress = { offset ->
                                    onLongPress?.invoke(offset)
                                }
                            )
                        }.pointerInput(drawingState, mapView, showRadialMenu) {
                            val currentMapView = mapView
                            if (currentMapView == null) return@pointerInput
                            
                            detectDragGestures(
                    onDragStart = { offset ->
                        pointerPosition = offset
                        currentGeoPoints.clear()
                        // Consume the event to prevent map panning
                        
                        if (drawingState.isEraseMode) {
                            // Start erasing
                        } else {
                            // Start drawing
                            try {
                                val projection = currentMapView.projection
                                val geoPoint = projection.fromPixels(offset.x.toInt(), offset.y.toInt()) as? GeoPoint
                                geoPoint?.let { currentGeoPoints.add(it) }
                            } catch (e: Exception) {
                                android.util.Log.e("MapDrawingOverlay", "Error converting to GeoPoint", e)
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume() // Consume event to prevent map panning
                        val offset = change.position
                        pointerPosition = offset
                        
                        if (drawingState.isEraseMode) {
                            // Erase strokes near pointer
                            val toErase = mutableListOf<MapDrawingStroke>()
                            val projection = currentMapView.projection
                            
                            strokes.forEach { stroke ->
                                // Check if any point in the stroke is within erase radius
                                val shouldErase = stroke.geoPoints.any { geoPoint ->
                                    val screenPoint = projection.toPixels(geoPoint, null)
                                    val dx = screenPoint.x - offset.x
                                    val dy = screenPoint.y - offset.y
                                    val distance = sqrt(dx * dx + dy * dy)
                                    distance <= drawingState.eraseRadius
                                }
                                if (shouldErase) {
                                    toErase.add(stroke)
                                }
                            }
                            
                            if (toErase.isNotEmpty()) {
                                onStrokesErased(toErase)
                            }
                        } else {
                            // Continue drawing
                            try {
                                val projection = currentMapView.projection
                                val geoPoint = projection.fromPixels(offset.x.toInt(), offset.y.toInt()) as? GeoPoint
                                geoPoint?.let { 
                                    // Add point if it's far enough from the last one (to avoid too many points)
                                    val lastPoint = currentGeoPoints.lastOrNull()
                                    if (lastPoint == null || lastPoint.distanceToAsDouble(it) > 0.00001) {
                                        currentGeoPoints.add(it)
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MapDrawingOverlay", "Error adding drawing point", e)
                            }
                        }
                    },
                    onDragEnd = {
                        pointerPosition = null
                        
                        if (!drawingState.isEraseMode && currentGeoPoints.size >= 2) {
                            // Create and save stroke
                            val stroke = MapDrawingStroke(
                                geoPoints = currentGeoPoints.toList(),
                                color = drawingState.selectedColor,
                                strokeWidth = drawingState.strokeWidth,
                                brushType = drawingState.brushType,
                                isHighlight = drawingState.brushType == MapBrushType.Marker
                            )
                            onStrokeComplete(stroke)
                        }
                        
                        currentGeoPoints.clear()
                    }
                )
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            val projection = mapView?.projection ?: return@Canvas
        
        // Draw all saved strokes
        strokes.forEach { stroke ->
            if (stroke.geoPoints.size < 2) return@forEach
            
            val path = Path()
            stroke.geoPoints.forEachIndexed { index, geoPoint ->
                val screenPoint = projection.toPixels(geoPoint, null)
                val offset = Offset(screenPoint.x.toFloat(), screenPoint.y.toFloat())
                
                if (index == 0) {
                    path.moveTo(offset.x, offset.y)
                } else {
                    path.lineTo(offset.x, offset.y)
                }
            }
            
            // Determine stroke properties
            val strokeWidth = if (stroke.isHighlight) {
                stroke.strokeWidth * 5f
            } else {
                stroke.strokeWidth
            }
            
            val strokeAlpha = if (stroke.isHighlight) 0.4f else 1f
            
            drawPath(
                path = path,
                color = stroke.color,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                ),
                alpha = strokeAlpha
            )
        }
            
            // Draw current stroke being drawn (only in drawing mode)
            if (drawingState.isDrawingMode && currentGeoPoints.size >= 2 && !drawingState.isEraseMode) {
            val path = Path()
            currentGeoPoints.forEachIndexed { index, geoPoint ->
                val screenPoint = projection.toPixels(geoPoint, null)
                val offset = Offset(screenPoint.x.toFloat(), screenPoint.y.toFloat())
                
                if (index == 0) {
                    path.moveTo(offset.x, offset.y)
                } else {
                    path.lineTo(offset.x, offset.y)
                }
            }
            
            val strokeWidth = if (drawingState.brushType == MapBrushType.Marker) {
                drawingState.strokeWidth * 5f
            } else {
                drawingState.strokeWidth
            }
            
            val strokeAlpha = if (drawingState.brushType == MapBrushType.Marker) 0.4f else 1f
            
            drawPath(
                path = path,
                color = drawingState.selectedColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                ),
                alpha = strokeAlpha
            )
        }
            
            // Draw cursor (only in drawing mode)
            if (drawingState.isDrawingMode) {
                pointerPosition?.let { pos ->
                    if (drawingState.isEraseMode) {
                // Eraser cursor
                drawCircle(
                    color = Color.Red.copy(alpha = 0.3f),
                    radius = drawingState.eraseRadius,
                    center = pos,
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = Color.Red.copy(alpha = 0.1f),
                    radius = drawingState.eraseRadius,
                    center = pos
                )
            } else {
                // Brush cursor
                val brushRadius = if (drawingState.brushType == MapBrushType.Marker) {
                    drawingState.strokeWidth * 2.5f
                } else {
                    drawingState.strokeWidth / 2f
                }
                
                drawCircle(
                    color = drawingState.selectedColor.copy(alpha = 0.5f),
                    radius = brushRadius,
                    center = pos,
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = drawingState.selectedColor.copy(alpha = if (drawingState.brushType == MapBrushType.Marker) 0.3f else 0.2f),
                    radius = brushRadius,
                        center = pos
                    )
                    }
                }
            }
        }
    }
}
