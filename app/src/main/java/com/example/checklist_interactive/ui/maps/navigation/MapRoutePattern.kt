package com.example.checklist_interactive.ui.maps.navigation

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
 *
 * NOTE: sizes increased slightly for more realistic military patterns; an EXTRA_LARGE
 * preset is available for military operations (larger lateral spacing and higher pattern altitude).
 */
enum class PatternSize(val displayName: String, val downwindDistanceNm: Double, val patternAltitudeFt: Int) {
    NORMAL("Normal", 0.75, 1200),
    MEDIUM("Medium", 1.0, 1400),
    LARGE("Large", 1.5, 1800),
    VERY_LARGE("Very Large", 2.0, 2200),
    EXTRA_LARGE("Extra Large", 3.0, 3000);
    
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
        // Increased multipliers for larger presets (EXTRA_LARGE used for military / high-alt patterns)
        val sizeScale = when (patternSize) {
            PatternSize.NORMAL -> 1.25
            PatternSize.MEDIUM -> 1.5
            PatternSize.LARGE -> 1.75
            PatternSize.VERY_LARGE -> 2.25
            PatternSize.EXTRA_LARGE -> 3.0
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
        runwayHeading: Double,
        patternSize: PatternSize = PatternSize.NORMAL
    ): List<Pair<GeoPoint, String>> {
        if (points.size < 11) return emptyList()

        // Distances for each leg in NM (use the actual leg endpoints)
        val departureDist = calculateDistance(points[0], points[1]) / 1852.0
        val crosswindDist = calculateDistance(points[2], points[3]) / 1852.0
        val downwindDist = calculateDistance(points[3], points[5]) / 1852.0
        val baseDist = calculateDistance(points[6], points[7]) / 1852.0
        val finalDist = calculateDistance(points[8], points[10]) / 1852.0

        // Compute headings for each leg based on actual points (more robust when points are transformed)
        val departureHdg = calculateBearing(points[0], points[1]).toInt()
        val crosswindHdg = calculateBearing(points[2], points[3]).toInt()
        val downwindHdg = calculateBearing(points[3], points[5]).toInt()
        // Base leg heading is the heading flown from base turn point to base point
        val baseHdg = calculateBearing(points[6], points[7]).toInt()
        val finalHdg = calculateBearing(points[9], points[10]).toInt()
        // Compute midpoints where needed
        val crosswindMid = run {
            val distMeters = calculateDistance(points[2], points[3])
            val bearing = calculateBearing(points[2], points[3])
            calculateDestination(points[2], bearing, distMeters / 2.0)
        }
        // DOWNWIND label at the corner (entry point) between Crosswind and Downwind
        val downwindCorner = points[3] // downwindEntryPoint

        // Determine the true Base->Final corner by intersecting the Base leg (points[6]->points[7])
        // with the Final approach line (runway threshold -> points[8]). If lines are parallel fall back to points[8].
        // points[0] is the runway threshold (first point in the pattern)
        val intersection = calculateLineIntersection(points[6], points[7], points[0], points[8])
        val finalCorner = intersection ?: points[8]
        // Use the computed corner directly for the FINAL label so it sits at the turn point between Base and Final
        val finalLabelPoint = finalCorner

        val altFt = patternSize.patternAltitudeFt

        return listOf(
            // DEPARTURE at end of climb-out
            points[1] to String.format("DEPARTURE\nHDG %03d°\n%.1f NM", departureHdg, departureDist),
            // CROSSWIND shown at midpoint of the crosswind leg
            crosswindMid to String.format("CROSSWIND\nHDG %03d°\n%.1f NM", crosswindHdg, crosswindDist),
            // DOWNWIND shown at the corner between Crosswind and Downwind (includes pattern altitude)
            downwindCorner to String.format("DOWNWIND\nHDG %03d°\n%.1f NM\nALT %d ft", downwindHdg, downwindDist, altFt),
            // BASE at the base-turn corner
            points[6] to String.format("BASE\nHDG %03d°\n%.1f NM", baseHdg, baseDist),
            // FINAL offset a little from the corner to avoid overlapping the final leg
            finalLabelPoint to String.format("FINAL\nHDG %03d°\n%.1f NM", finalHdg, finalDist)
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

    /**
     * Reflect a point across a line passing through `origin` with heading `lineBearing`.
     * Reflection formula: newBearing = 2*lineBearing - originalBearing (keeps distance).
     */
    fun reflectPointAcrossLine(origin: GeoPoint, lineBearing: Double, point: GeoPoint): GeoPoint {
        val d = calculateDistance(origin, point)
        val originalBearing = calculateBearing(origin, point)
        val reflectedBearing = normalizeHeading(2 * lineBearing - originalBearing)
        return calculateDestination(origin, reflectedBearing, d)
    }

    /**
     * Rotate a point 180° around `center` (equivalent to mirroring both axes)
     */
    fun rotate180Around(center: GeoPoint, point: GeoPoint): GeoPoint {
        val d = calculateDistance(center, point)
        val b = calculateBearing(center, point)
        return calculateDestination(center, normalizeHeading(b + 180.0), d)
    }

    /**
     * Apply reflection across runway centerline (horizontal axis) to all points
     */
    fun reflectAcrossRunwayCenterline(points: List<GeoPoint>, runwayThreshold: GeoPoint, runwayBearing: Double): List<GeoPoint> {
        return points.map { p -> reflectPointAcrossLine(runwayThreshold, runwayBearing, p) }
    }

    /**
     * Apply reflection across a line parallel to runway (lateral axis) to all points
     * This is used when switching pattern direction (LEFT_HAND ↔ RIGHT_HAND)
     * Swaps left/right sides while preserving runway alignment (departure/final stay correct)
     */
    fun reflectAcrossRunwayParallel(points: List<GeoPoint>, runwayThreshold: GeoPoint, runwayBearing: Double): List<GeoPoint> {
        // Reflect across a line PARALLEL to the runway (same bearing)
        // This swaps lateral positions (left ↔ right) without swapping longitudinal positions (departure ↔ final)
        return points.map { p -> reflectPointAcrossLine(runwayThreshold, runwayBearing, p) }
    }

    /**
     * Rotate all points 180° around a given center
     */
    fun rotatePoints180(points: List<GeoPoint>, center: GeoPoint): List<GeoPoint> {
        return points.map { p -> rotate180Around(center, p) }
    }

    /**
     * Calculate intersection of two lines (p1->p2) and (p3->p4) using a simple local equirectangular projection.
     * Returns null if lines are parallel.
     */
    private fun calculateLineIntersection(p1: GeoPoint, p2: GeoPoint, p3: GeoPoint, p4: GeoPoint): GeoPoint? {
        // Use p1 as origin for local projection
        val originLat = Math.toRadians(p1.latitude)
        val originLon = Math.toRadians(p1.longitude)
        val r = 6371000.0

        fun toXY(pt: GeoPoint): Pair<Double, Double> {
            val lat = Math.toRadians(pt.latitude)
            val lon = Math.toRadians(pt.longitude)
            val x = r * (lon - originLon) * cos(originLat)
            val y = r * (lat - originLat)
            return Pair(x, y)
        }

        val (x1, y1) = toXY(p1)
        val (x2, y2) = toXY(p2)
        val (x3, y3) = toXY(p3)
        val (x4, y4) = toXY(p4)

        val denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (abs(denom) < 1e-6) return null // parallel or nearly parallel

        val px = ((x1*y2 - y1*x2)*(x3 - x4) - (x1 - x2)*(x3*y4 - y3*x4)) / denom
        val py = ((x1*y2 - y1*x2)*(y3 - y4) - (y1 - y2)*(x3*y4 - y3*x4)) / denom

        // Convert back to lat/lon
        val lat = Math.toDegrees(originLat + (py / r))
        val lon = Math.toDegrees(originLon + (px / (r * cos(originLat))))
        return GeoPoint(lat, lon)
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
