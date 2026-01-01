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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import android.graphics.PointF
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
    
    // Track map changes to force canvas redraw when map moves/zooms/rotates
    var mapInvalidationKey by remember { mutableStateOf(0L) }
    
    // Listen to ALL map events to trigger canvas recomposition
    DisposableEffect(mapView) {
        val currentMapView = mapView ?: return@DisposableEffect onDispose {}
        
        // Listener for scroll and zoom events
        val mapListener = object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                mapInvalidationKey = System.currentTimeMillis()
                return true
            }
            
            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                mapInvalidationKey = System.currentTimeMillis()
                return true
            }
        }
        
        currentMapView.addMapListener(mapListener)
        
        // Additional listener specifically for rotation
        val rotationGestureOverlay = currentMapView.overlayManager.firstOrNull { 
            it is org.osmdroid.views.overlay.gestures.RotationGestureOverlay 
        } as? org.osmdroid.views.overlay.gestures.RotationGestureOverlay
        
        // Force invalidation on every frame when map transforms (rotation, pan, zoom) might be active
        // by observing mapOrientation, map center and zoom level changes
        val invalidationRunnable = object : Runnable {
            private var lastOrientation = currentMapView.mapOrientation
            private var lastCenter = currentMapView.mapCenter?.let { Pair(it.latitude, it.longitude) }
            private var lastZoom = currentMapView.zoomLevelDouble

            override fun run() {
                val currentOrientation = currentMapView.mapOrientation
                val center = currentMapView.mapCenter
                val currentCenter = center?.let { Pair(it.latitude, it.longitude) }
                val currentZoom = currentMapView.zoomLevelDouble

                if (currentOrientation != lastOrientation || currentCenter != lastCenter || currentZoom != lastZoom) {
                    mapInvalidationKey = System.currentTimeMillis()
                    lastOrientation = currentOrientation
                    lastCenter = currentCenter
                    lastZoom = currentZoom
                }
                currentMapView.postDelayed(this, 16) // ~60fps
            }
        }
        
        currentMapView.post(invalidationRunnable)
        
        onDispose {
            currentMapView.removeMapListener(mapListener)
            currentMapView.removeCallbacks(invalidationRunnable)
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Canvas for rendering strokes (always visible)
        // Trigger redraw when map moves (mapInvalidationKey) OR when strokes list changes
        // Note: strokes is a mutableStateListOf, so changes will automatically trigger recomposition
        val redrawTrigger = mapInvalidationKey to strokes.size // Combine both triggers
        key(redrawTrigger) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                .then(
                    // Only enable input when drawing mode is active
                    if (drawingState.isDrawingMode) {
                        Modifier
                            // LongPress detection with detectTapGestures (simpler and more reliable)
                            .pointerInput(drawingState) {
                                detectTapGestures(
                                    onLongPress = { offset ->
                                        android.util.Log.d("MapDrawingOverlay", "LongPress detected at (${offset.x}, ${offset.y})")
                                        onLongPress?.invoke(offset)
                                    }
                                )
                            }
                            // Drawing/Erasing gestures
                            .pointerInput(drawingState, mapView) {
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
                                // MapView may be offset within the parent, convert pointer position to MapView-local coords
                                val mapLeft = currentMapView.left.toFloat()
                                val mapTop = currentMapView.top.toFloat()
                                val px = offset.x - mapLeft
                                val py = offset.y - mapTop
                                // inverse-rotate around map center to convert overlay point into projection's (north-up) pixel space
                                val centerPoint = projection.toPixels(currentMapView.mapCenter, null)
                                val angleRadInv = Math.toRadians(-currentMapView.mapOrientation.toDouble())
                                val cosAInv = kotlin.math.cos(angleRadInv)
                                val sinAInv = kotlin.math.sin(angleRadInv)
                                val dx = px - centerPoint.x
                                val dy = py - centerPoint.y
                                val unrotX = dx * cosAInv - dy * sinAInv + centerPoint.x
                                val unrotY = dx * sinAInv + dy * cosAInv + centerPoint.y
                                val geoPoint = projection.fromPixels(unrotX.toInt(), unrotY.toInt()) as? GeoPoint
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
                        
                        val mapLeft = currentMapView.left.toFloat()
                        val mapTop = currentMapView.top.toFloat()
                        
                        if (drawingState.isEraseMode) {
                            // Erase strokes near pointer
                            val toErase = mutableListOf<MapDrawingStroke>()
                            val projection = currentMapView.projection
                            
                            strokes.forEach { stroke ->
                                // Check if any point in the stroke is within erase radius
                                val shouldErase = stroke.geoPoints.any { geoPoint ->
                                    val screenPoint = projection.toPixels(geoPoint, null)
                                    // rotate around map center then translate to overlay coords
                                    val centerPoint = projection.toPixels(currentMapView.mapCenter, null)
                                    val angleRad = Math.toRadians(currentMapView.mapOrientation.toDouble())
                                    val cosA = kotlin.math.cos(angleRad)
                                    val sinA = kotlin.math.sin(angleRad)
                                    val dx0 = screenPoint.x - centerPoint.x
                                    val dy0 = screenPoint.y - centerPoint.y
                                    val rotX = dx0 * cosA - dy0 * sinA + centerPoint.x
                                    val rotY = dx0 * sinA + dy0 * cosA + centerPoint.y
                                    val sx = rotX + mapLeft
                                    val sy = rotY + mapTop
                                    val dx = sx - offset.x
                                    val dy = sy - offset.y
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
                                val px = (change.position.x - mapLeft)
                                val py = (change.position.y - mapTop)
                                val centerPoint = projection.toPixels(currentMapView.mapCenter, null)
                                val angleRadInv = Math.toRadians(-currentMapView.mapOrientation.toDouble())
                                val cosAInv = kotlin.math.cos(angleRadInv)
                                val sinAInv = kotlin.math.sin(angleRadInv)
                                val dx = px - centerPoint.x
                                val dy = py - centerPoint.y
                                val unrotX = dx * cosAInv - dy * sinAInv + centerPoint.x
                                val unrotY = dx * sinAInv + dy * cosAInv + centerPoint.y
                                val geoPoint = projection.fromPixels(unrotX.toInt(), unrotY.toInt()) as? GeoPoint
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
            // CRITICAL: Get fresh projection on EVERY draw to handle map rotation/zoom/pan
            // The projection object contains the current map transformation state
            val currentMapView = mapView
            if (currentMapView == null) {
                android.util.Log.w("MapDrawingOverlay", "Canvas draw: mapView is null, cannot render ${strokes.size} strokes")
                return@Canvas
            }
            val projection = currentMapView.projection
            // MapView may be offset in the parent; account for its left/top when translating
            val mapLeft = currentMapView.left.toFloat()
            val mapTop = currentMapView.top.toFloat()
        
        // Draw all saved strokes
        android.util.Log.d("MapDrawingOverlay", "Drawing ${strokes.size} strokes on canvas; mapCenter=${currentMapView.mapCenter?.latitude},${currentMapView.mapCenter?.longitude}, mapOrientation=${currentMapView.mapOrientation}")
        strokes.forEach { stroke ->
            if (stroke.geoPoints.isNotEmpty()) {
                val sample = stroke.geoPoints.take(3).map { "(lat=${it.latitude},lon=${it.longitude})" }.joinToString(",")
                android.util.Log.d("MapDrawingOverlay", "  Stroke id=${stroke.id}, pts=${stroke.geoPoints.size}, sample=$sample, strokeWidth=${stroke.strokeWidth}, isHighlight=${stroke.isHighlight}")
            }
            if (stroke.geoPoints.size < 2) return@forEach
            
            val path = Path()
            stroke.geoPoints.forEachIndexed { index, geoPoint ->
                // Convert GeoPoint to screen coordinates using current projection (relative to MapView)
                val screenPoint = projection.toPixels(geoPoint, null)
                // Rotate around map center by current map orientation, then translate to overlay coords
                val centerPoint = projection.toPixels(currentMapView.mapCenter, null)
                val angleRad = Math.toRadians(currentMapView.mapOrientation.toDouble())
                val cosA = kotlin.math.cos(angleRad)
                val sinA = kotlin.math.sin(angleRad)
                val dx = screenPoint.x - centerPoint.x
                val dy = screenPoint.y - centerPoint.y
                val rotX = dx * cosA - dy * sinA + centerPoint.x
                val rotY = dx * sinA + dy * cosA + centerPoint.y
                val offset = Offset(rotX.toFloat() + mapLeft, rotY.toFloat() + mapTop)
                
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
                // rotate around map center
                val centerPoint = projection.toPixels(currentMapView.mapCenter, null)
                val angleRad = Math.toRadians(currentMapView.mapOrientation.toDouble())
                val cosA = kotlin.math.cos(angleRad)
                val sinA = kotlin.math.sin(angleRad)
                val dx = screenPoint.x - centerPoint.x
                val dy = screenPoint.y - centerPoint.y
                val rotX = dx * cosA - dy * sinA + centerPoint.x
                val rotY = dx * sinA + dy * cosA + centerPoint.y
                val offset = Offset(rotX.toFloat() + mapLeft, rotY.toFloat() + mapTop)
                
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
        } // end of key(mapInvalidationKey)
    }
}
