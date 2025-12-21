package com.example.checklist_interactive.ui.maps

import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import kotlin.math.*

/**
 * MapRoutePattern - Traffic Pattern and Route Pattern Generator
 * 
 * Generates realistic aviation traffic patterns (circuits) and other route patterns
 * for navigation and training purposes.
 * 
 * Features:
 * - Standard traffic pattern (left/right hand)
 * - Configurable pattern size (Normal, Medium, Large, Very Large)
 * - Runway-specific pattern generation
 * - Modular design for future pattern types
 */

/**
 * Pattern size presets
 */
enum class PatternSize(val displayName: String, val downwindDistanceNm: Double, val patternAltitudeFt: Int) {
    NORMAL("Normal", 0.5, 1000),
    MEDIUM("Medium", 0.75, 1000),
    LARGE("Large", 1.0, 1200),
    VERY_LARGE("Very Large", 1.5, 1500);
    
    companion object {
        fun fromOrdinal(ordinal: Int): PatternSize = values().getOrNull(ordinal) ?: NORMAL
    }
}

/**
 * Pattern direction (circuit direction)
 */
enum class PatternDirection {
    LEFT_HAND,   // Standard - turns to the left
    RIGHT_HAND   // Non-standard - turns to the right
}

/**
 * Traffic pattern legs
 */
enum class PatternLeg {
    DEPARTURE,   // Initial climb along runway heading
    CROSSWIND,   // 90° turn perpendicular to runway
    DOWNWIND,    // Parallel to runway, opposite direction
    BASE,        // 90° turn perpendicular to runway
    FINAL        // Aligned with runway for landing
}

/**
 * Traffic pattern generator
 */
object TrafficPatternGenerator {
    
    /**
     * Generate a complete traffic pattern around a runway
     * 
     * @param runwayThreshold GeoPoint of the runway threshold (start of landing runway)
     * @param runwayHeading Runway heading in degrees (0-360)
     * @param runwayLengthMeters Length of runway in meters
     * @param patternSize Size preset for the pattern
     * @param direction Left or right hand pattern
     * @return List of GeoPoints forming the complete pattern
     */
    fun generateTrafficPattern(
        runwayThreshold: GeoPoint,
        runwayHeading: Double,
        runwayLengthMeters: Double,
        patternSize: PatternSize = PatternSize.NORMAL,
        direction: PatternDirection = PatternDirection.LEFT_HAND,
        finalDistanceNm: Double = 1.0
    ): List<GeoPoint> {
        
        val points = mutableListOf<GeoPoint>()
        
        // Scale factor for longitudinal distances based on size preset
        val sizeScale = when (patternSize) {
            PatternSize.NORMAL -> 1.0
            PatternSize.MEDIUM -> 1.25
            PatternSize.LARGE -> 1.5
            PatternSize.VERY_LARGE -> 2.0
        }

        // Lateral downwind distance (NM -> meters) and base uses same lateral distance
        val downwindDistanceMeters = patternSize.downwindDistanceNm * 1852.0 * sizeScale
        val baseDistanceMeters = downwindDistanceMeters
        
        // Determine turn direction multiplier (left = -1, right = +1)
        val turnMultiplier = if (direction == PatternDirection.LEFT_HAND) -1.0 else 1.0
        
        // 1. Departure point (runway threshold)
        points.add(runwayThreshold)
        
        // 2. Climb-out point (extend past runway end; scaled by size)
        val departureEndDistance = runwayLengthMeters + (0.5 * 1852.0 * sizeScale)
        val departureEnd = calculateDestination(runwayThreshold, runwayHeading, departureEndDistance)
        points.add(departureEnd)
        
        // 3. Crosswind turn point (continue another scaled distance, then turn)
        val crosswindTurnDistance = 0.3 * 1852.0 * sizeScale
        val crosswindTurnPoint = calculateDestination(departureEnd, runwayHeading, crosswindTurnDistance)
        points.add(crosswindTurnPoint)
        
        // 4. Downwind entry point (after 90° turn and flying perpendicular)
        val crosswindHeading = normalizeHeading(runwayHeading + (90.0 * turnMultiplier))
        val downwindEntryPoint = calculateDestination(crosswindTurnPoint, crosswindHeading, downwindDistanceMeters)
        points.add(downwindEntryPoint)
        
        // 5. Downwind midpoint (parallel to runway, opposite direction)
        // Extend downwind based on final approach distance to keep pattern proportional
        val downwindHeading = normalizeHeading(runwayHeading + 180.0)
        val downwindLength = runwayLengthMeters + (finalDistanceNm * 1852.0) + (0.5 * 1852.0 * sizeScale) // Match final distance + extra for pattern spacing
        val downwindMidpoint = calculateDestination(downwindEntryPoint, downwindHeading, downwindLength / 2)
        points.add(downwindMidpoint)
        
        // 6. Downwind abeam threshold (opposite the landing threshold)
        val downwindAbeam = calculateDestination(downwindEntryPoint, downwindHeading, downwindLength)
        points.add(downwindAbeam)
        
        // 7. Base turn point (continue past abeam, then turn onto base)
        // Scale base extension with final distance to keep pattern square
        val baseExtension = (0.3 + (finalDistanceNm * 0.2)) * 1852.0 * sizeScale
        val baseTurnPoint = calculateDestination(downwindAbeam, downwindHeading, baseExtension)
        points.add(baseTurnPoint)
        
        // 8. Base leg point (perpendicular to runway, approaching final)
        val baseHeading = normalizeHeading(runwayHeading + (270.0 * turnMultiplier))
        val basePoint = calculateDestination(baseTurnPoint, baseHeading, baseDistanceMeters)
        points.add(basePoint)
        
        // 9. Final approach point (turn onto runway heading)
        // Position final approach to intercept extended centerline (user-configurable)
        val finalDistance = finalDistanceNm * 1852.0 // NM to meters
        val finalPoint = calculateDestination(runwayThreshold, normalizeHeading(runwayHeading + 180.0), finalDistance)
        points.add(finalPoint)
        
        // 10. Short final (closer to threshold) - proportional to final distance
        val shortFinalDistance = (finalDistanceNm * 0.2) * 1852.0 // 20% of final distance
        val shortFinalPoint = calculateDestination(runwayThreshold, normalizeHeading(runwayHeading + 180.0), shortFinalDistance)
        points.add(shortFinalPoint)
        
        // 11. Threshold (landing point)
        points.add(runwayThreshold)
        
        return points
    }
    
    /**
     * Generate pattern polyline with styling
     */
    fun createPatternPolyline(
        points: List<GeoPoint>,
        color: Int = 0xFF00FF00.toInt(), // Green
        width: Float = 4f
    ): Polyline {
        return Polyline().apply {
            setPoints(points)
            outlinePaint.color = color
            outlinePaint.strokeWidth = width
            outlinePaint.style = android.graphics.Paint.Style.STROKE
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
            // Dashed line for pattern
            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
        }
    }
    
    /**
     * Generate pattern leg labels for overlay with distances and headings
     */
    fun generatePatternLabels(
        points: List<GeoPoint>,
        direction: PatternDirection,
        runwayHeading: Double
    ): List<Pair<GeoPoint, String>> {
        if (points.size < 11) return emptyList()
        
        // Calculate headings for each leg
        val turnMultiplier = if (direction == PatternDirection.LEFT_HAND) -1.0 else 1.0
        val departureHdg = normalizeHeading(runwayHeading).toInt()
        val crosswindHdg = normalizeHeading(runwayHeading + (90.0 * turnMultiplier)).toInt()
        val downwindHdg = normalizeHeading(runwayHeading + 180.0).toInt()
        val baseHdg = normalizeHeading(runwayHeading + (270.0 * turnMultiplier)).toInt()
        val finalHdg = normalizeHeading(runwayHeading).toInt()
        
        // Calculate distances for each leg in NM
        val departureDist = calculateDistance(points[0], points[1]) / 1852.0
        val crosswindDist = calculateDistance(points[2], points[3]) / 1852.0
        val downwindDist = calculateDistance(points[3], points[6]) / 1852.0
        val baseDist = calculateDistance(points[7], points[8]) / 1852.0
        val finalDist = calculateDistance(points[9], points[10]) / 1852.0
        
        return listOf(
            points[1] to String.format("DEPARTURE\nHDG %03d°\n%.1f NM", departureHdg, departureDist),
            points[3] to String.format("CROSSWIND\nHDG %03d°\n%.1f NM", crosswindHdg, crosswindDist),
            points[5] to String.format("DOWNWIND\nHDG %03d°\n%.1f NM", downwindHdg, downwindDist),
            points[7] to String.format("BASE\nHDG %03d°\n%.1f NM", baseHdg, baseDist),
            points[9] to String.format("FINAL\nHDG %03d°\n%.1f NM", finalHdg, finalDist)
        )
    }
    
    /**
     * Calculate destination point given start point, bearing, and distance
     * 
     * @param start Starting GeoPoint
     * @param bearing Bearing in degrees (0-360)
     * @param distanceMeters Distance in meters
     * @return Destination GeoPoint
     */
    private fun calculateDestination(start: GeoPoint, bearing: Double, distanceMeters: Double): GeoPoint {
        val earthRadius = 6371000.0 // Earth radius in meters
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        val bearingRad = Math.toRadians(bearing)
        val angularDistance = distanceMeters / earthRadius
        
        val lat2 = asin(
            sin(lat1) * cos(angularDistance) +
            cos(lat1) * sin(angularDistance) * cos(bearingRad)
        )
        
        val lon2 = lon1 + atan2(
            sin(bearingRad) * sin(angularDistance) * cos(lat1),
            cos(angularDistance) - sin(lat1) * sin(lat2)
        )
        
        return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }
    
    /**
     * Normalize heading to 0-360 range
     */
    private fun normalizeHeading(heading: Double): Double {
        var h = heading % 360.0
        if (h < 0) h += 360.0
        return h
    }
    
    /**
     * Calculate distance between two GeoPoints in meters
     */
    fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val earthRadius = 6371000.0
        val lat1 = Math.toRadians(point1.latitude)
        val lon1 = Math.toRadians(point1.longitude)
        val lat2 = Math.toRadians(point2.latitude)
        val lon2 = Math.toRadians(point2.longitude)
        
        val dLat = lat2 - lat1
        val dLon = lon2 - lon1
        
        val a = sin(dLat / 2).pow(2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * Calculate bearing between two GeoPoints in degrees
     */
    fun calculateBearing(point1: GeoPoint, point2: GeoPoint): Double {
        val lat1 = Math.toRadians(point1.latitude)
        val lon1 = Math.toRadians(point1.longitude)
        val lat2 = Math.toRadians(point2.latitude)
        val lon2 = Math.toRadians(point2.longitude)
        
        val dLon = lon2 - lon1
        
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        
        val bearing = Math.toDegrees(atan2(y, x))
        return normalizeHeading(bearing)
    }
}

/**
 * Overlay for displaying pattern leg labels
 */
class PatternLabelOverlay(
    private val labels: List<Pair<GeoPoint, String>>
) : org.osmdroid.views.overlay.Overlay() {
    
    private val textPaint = android.graphics.Paint().apply {
        color = 0xFF00FF00.toInt() // Green
        textSize = 28f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        style = android.graphics.Paint.Style.FILL
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    
    private val textPaintSmall = android.graphics.Paint().apply {
        color = 0xFFFFFFFF.toInt() // White for details
        textSize = 22f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        style = android.graphics.Paint.Style.FILL
        typeface = android.graphics.Typeface.MONOSPACE
    }
    
    private val bgPaint = android.graphics.Paint().apply {
        color = 0xAA000000.toInt() // Semi-transparent black
        style = android.graphics.Paint.Style.FILL
    }
    
    override fun draw(canvas: android.graphics.Canvas?, mapView: org.osmdroid.views.MapView?, shadow: Boolean) {
        if (shadow || canvas == null || mapView == null) return
        
        val projection = mapView.projection
        
        labels.forEach { (geoPoint, label) ->
            val point = projection.toPixels(geoPoint, null)
            val lines = label.split("\n")
            
            // Calculate total height and max width for background
            var maxWidth = 0f
            var totalHeight = 0f
            val lineHeights = mutableListOf<Float>()
            
            lines.forEachIndexed { index, line ->
                val paint = if (index == 0) textPaint else textPaintSmall
                val bounds = android.graphics.Rect()
                paint.getTextBounds(line, 0, line.length, bounds)
                maxWidth = maxOf(maxWidth, bounds.width().toFloat())
                val lineHeight = bounds.height().toFloat() + 4f
                lineHeights.add(lineHeight)
                totalHeight += lineHeight
            }
            
            val padding = 10f
            val bgRect = android.graphics.RectF(
                point.x - maxWidth / 2f - padding,
                point.y - totalHeight / 2f - padding,
                point.x + maxWidth / 2f + padding,
                point.y + totalHeight / 2f + padding
            )
            canvas.drawRoundRect(bgRect, 8f, 8f, bgPaint)
            
            // Draw each line
            var currentY = point.y.toFloat() - totalHeight / 2f
            lines.forEachIndexed { index, line ->
                val paint = if (index == 0) textPaint else textPaintSmall
                currentY += lineHeights[index]
                canvas.drawText(line, point.x.toFloat(), currentY, paint)
            }
        }
    }
}
