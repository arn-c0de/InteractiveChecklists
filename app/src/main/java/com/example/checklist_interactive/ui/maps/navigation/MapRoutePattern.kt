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
 * NOTE: sizes increased slightly for more realistic military patterns; EXTRA_LARGE and even larger
 * presets (HUGE, GIGANTIC) are available for large-scale or military training patterns.
 */
enum class PatternSize(val displayName: String, val downwindDistanceNm: Double, val patternAltitudeAglFt: Int) {
    NORMAL("Normal", 0.75, 1200),
    MEDIUM("Medium", 1.0, 1400),
    LARGE("Large", 1.5, 1800),
    VERY_LARGE("Very Large", 2.0, 2200),
    EXTRA_LARGE("Extra Large", 3.0, 3000),
    HUGE("Huge", 4.5, 4500),
    GIGANTIC("Gigantic", 6.0, 9000);
    
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
        finalDistanceNm: Double = 1.0,
        roundedCorners: Boolean = false
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
            PatternSize.HUGE -> 4.0
            PatternSize.GIGANTIC -> 6.0
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
        
        // Apply corner smoothing if enabled
        return if (roundedCorners) {
            smoothCorners(points, patternSize)
        } else {
            points
        }
    }
    
    /**
     * Generate traffic pattern with corner information for labels
     * Returns triple of (smoothed points, original corner positions, straight-segment headings)
     */
    fun generateTrafficPatternWithCorners(
        runwayThreshold: GeoPoint,
        runwayHeading: Double,
        runwayLengthMeters: Double,
        patternSize: PatternSize = PatternSize.NORMAL,
        direction: PatternDirection = PatternDirection.LEFT_HAND,
        finalDistanceNm: Double = 1.0,
        roundedCorners: Boolean = false
    ): Triple<List<GeoPoint>, Map<String, GeoPoint>, Map<String, Double>> {
        val points = mutableListOf<GeoPoint>()
        
        // [Copy the entire pattern generation logic here - same as generateTrafficPattern]
        val sizeScale = when (patternSize) {
            PatternSize.NORMAL -> 1.25
            PatternSize.MEDIUM -> 1.5
            PatternSize.LARGE -> 1.75
            PatternSize.VERY_LARGE -> 2.25
            PatternSize.EXTRA_LARGE -> 3.0
            PatternSize.HUGE -> 4.0
            PatternSize.GIGANTIC -> 6.0
        }

        val downwindDistanceMeters = patternSize.downwindDistanceNm * 1852.0 * sizeScale
        val baseDistanceMeters = downwindDistanceMeters
        val turnMultiplier = if (direction == PatternDirection.LEFT_HAND) -1.0 else 1.0
        
        points.add(runwayThreshold)
        val departureEndDistance = runwayLengthMeters + (0.5 * 1852.0 * sizeScale)
        val departureEnd = calculateDestination(runwayThreshold, runwayHeading, departureEndDistance)
        points.add(departureEnd)
        
        val crosswindTurnDistance = 0.3 * 1852.0 * sizeScale
        val crosswindTurnPoint = calculateDestination(departureEnd, runwayHeading, crosswindTurnDistance)
        points.add(crosswindTurnPoint)
        
        val crosswindHeading = normalizeHeading(runwayHeading + (90.0 * turnMultiplier))
        val downwindEntryPoint = calculateDestination(crosswindTurnPoint, crosswindHeading, downwindDistanceMeters)
        points.add(downwindEntryPoint)
        
        val downwindHeading = normalizeHeading(runwayHeading + 180.0)
        val downwindLength = runwayLengthMeters + (finalDistanceNm * 1852.0) + (0.5 * 1852.0 * sizeScale)
        val downwindMidpoint = calculateDestination(downwindEntryPoint, downwindHeading, downwindLength / 2)
        points.add(downwindMidpoint)
        
        val downwindAbeam = calculateDestination(downwindEntryPoint, downwindHeading, downwindLength)
        points.add(downwindAbeam)
        
        val baseExtension = (0.3 + (finalDistanceNm * 0.2)) * 1852.0 * sizeScale
        val baseTurnPoint = calculateDestination(downwindAbeam, downwindHeading, baseExtension)
        points.add(baseTurnPoint)
        
        val baseHeading = normalizeHeading(runwayHeading + (270.0 * turnMultiplier))
        val basePoint = calculateDestination(baseTurnPoint, baseHeading, baseDistanceMeters)
        points.add(basePoint)
        
        val finalDistance = finalDistanceNm * 1852.0
        val finalPoint = calculateDestination(runwayThreshold, normalizeHeading(runwayHeading + 180.0), finalDistance)
        points.add(finalPoint)
        
        val shortFinalDistance = (finalDistanceNm * 0.2) * 1852.0
        val shortFinalPoint = calculateDestination(runwayThreshold, normalizeHeading(runwayHeading + 180.0), shortFinalDistance)
        points.add(shortFinalPoint)
        
        points.add(runwayThreshold)
        
        // Store original corner positions and headings before smoothing
        val corners = mapOf(
            "departure" to points[1],
            "crosswind" to calculateDestination(points[2], calculateBearing(points[2], points[3]), calculateDistance(points[2], points[3]) / 2.0),
            "downwind" to points[3],
            "base" to points[6],
            "final" to (calculateLineIntersection(points[6], points[7], points[0], points[8]) ?: points[8])
        )
        
        // Store original straight-segment headings (before rounding)
        val headings = mapOf(
            "departure" to runwayHeading,
            "crosswind" to crosswindHeading,
            "downwind" to downwindHeading,
            "base" to baseHeading,
            "final" to runwayHeading  // Final approach is aligned with runway
        )
        
        val finalPoints = if (roundedCorners) {
            smoothCorners(points, patternSize)
        } else {
            points
        }
        
        return Triple(finalPoints, corners, headings)
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
     * Works with both smoothed and non-smoothed patterns
     * 
     * @param points Pattern points (may be smoothed)
     * @param cornerPositions Optional map of original corner positions (for smoothed patterns)
     * @param segmentHeadings Optional map of original segment headings (for smoothed patterns)
     */
    fun generatePatternLabels(
        points: List<GeoPoint>,
        direction: PatternDirection,
        runwayHeading: Double,
        patternSize: PatternSize = PatternSize.NORMAL,
        runwayElevationFt: Int = 0,
        customAltitudeAglFt: Int? = null,
        cornerPositions: Map<String, GeoPoint>? = null,
        segmentHeadings: Map<String, Double>? = null
    ): List<Pair<GeoPoint, String>> {
        // Use corner positions if provided (for smoothed patterns), otherwise use indices
        val useFallback = cornerPositions == null || points.size < 11
        
        if (useFallback && points.size < 11) return emptyList()

        // For non-smoothed patterns, calculate from indices
        val departurePoint = cornerPositions?.get("departure") ?: points.getOrNull(1) ?: return emptyList()
        val crosswindPoint = cornerPositions?.get("crosswind") ?: run {
            if (points.size > 3) {
                val distMeters = calculateDistance(points[2], points[3])
                val bearing = calculateBearing(points[2], points[3])
                calculateDestination(points[2], bearing, distMeters / 2.0)
            } else return emptyList()
        }
        val downwindPoint = cornerPositions?.get("downwind") ?: points.getOrNull(3) ?: return emptyList()
        val basePoint = cornerPositions?.get("base") ?: points.getOrNull(6) ?: return emptyList()
        val finalPoint = cornerPositions?.get("final") ?: run {
            if (points.size > 8) {
                calculateLineIntersection(points[6], points[7], points[0], points[8]) ?: points[8]
            } else return emptyList()
        }

        // Calculate distances to NEXT waypoint (distance remaining to fly to next turn/threshold)
        // DEPARTURE: heading to DOWNWIND entry (display heading only)
        // CROSSWIND: no distance shown (it's a turn, not a straight leg to fly)
        // DOWNWIND: distance to BASE turn
        val downwindDist = calculateDistance(downwindPoint, basePoint) / 1852.0
        // BASE: distance to FINAL turn
        val baseDist = calculateDistance(basePoint, finalPoint) / 1852.0
        // FINAL: distance to runway threshold (marker center)
        val finalDist = calculateDistance(finalPoint, points.getOrNull(0) ?: finalPoint) / 1852.0

        // Use provided headings if available (for smoothed patterns), otherwise calculate from points
        // Compute CROSSWIND heading first
        val crosswindHdg = (segmentHeadings?.get("crosswind") ?: 
            if (points.size > 3) calculateBearing(points[2], points[3]) else 0.0).toInt()
        // DEPARTURE always uses the same heading as CROSSWIND (perpendicular turn from runway)
        val departureHdg = crosswindHdg
        val downwindHdg = (segmentHeadings?.get("downwind") ?: 
            if (points.size > 5) calculateBearing(points[3], points[5]) else 0.0).toInt()
        val baseHdg = (segmentHeadings?.get("base") ?: 
            if (points.size > 7) calculateBearing(points[6], points[7]) else 0.0).toInt()
        val finalHdg = (segmentHeadings?.get("final") ?: 
            if (points.size > 10) calculateBearing(points[9], points[10]) else 0.0).toInt()

        val altAglFt = customAltitudeAglFt ?: patternSize.patternAltitudeAglFt
        val altMslFt = altAglFt + runwayElevationFt

        return listOf(
            departurePoint to String.format("DEPARTURE\nHDG %03d°", departureHdg),
            crosswindPoint to String.format("CROSSWIND\nHDG %03d°", crosswindHdg),
            downwindPoint to String.format("DOWNWIND\nHDG %03d°\n%.1f NM\n%d ft MSL (%d AGL)", downwindHdg, downwindDist, altMslFt, altAglFt),
            basePoint to String.format("BASE\nHDG %03d°\n%.1f NM", baseHdg, baseDist),
            finalPoint to String.format("FINAL\nHDG %03d°\n%.1f NM", finalHdg, finalDist)
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
     * Smooth corners of a pattern by adding interpolated points at turns
     * Uses a simple arc approximation for smooth flyable corners
     * 
     * @param points Original pattern points
     * @param patternSize Pattern size determines smoothing radius (larger = more rounded)
     * @return Smoothed pattern with additional points at corners
     */
    private fun smoothCorners(points: List<GeoPoint>, patternSize: PatternSize): List<GeoPoint> {
        if (points.size < 3) return points
        
        // Scale smoothing radius based on pattern size
        // Larger patterns get progressively more rounding for smoother, easier turns
        val smoothingRadius = when (patternSize) {
            PatternSize.NORMAL -> 0.12       // 12% - tighter turns
            PatternSize.MEDIUM -> 0.15       // 15% - moderate
            PatternSize.LARGE -> 0.18        // 18% - smoother
            PatternSize.VERY_LARGE -> 0.22   // 22% - very smooth
            PatternSize.EXTRA_LARGE -> 0.28  // 28% - wide, gentle turns
            PatternSize.HUGE -> 0.34         // 34% - very wide
            PatternSize.GIGANTIC -> 0.42     // 42% - extremely wide
        }
        
        // Allow larger upper clamp for giant patterns
        val radius = smoothingRadius.coerceIn(0.05, 0.6) // Safety clamp
        
        // Calculate a fixed smoothing distance based on pattern size (in meters)
        // This ensures all corners are rounded equally
        val fixedSmoothDist = when (patternSize) {
            PatternSize.NORMAL -> 200.0       // 200m radius
            PatternSize.MEDIUM -> 300.0       // 300m radius
            PatternSize.LARGE -> 400.0        // 400m radius
            PatternSize.VERY_LARGE -> 550.0   // 550m radius
            PatternSize.EXTRA_LARGE -> 750.0  // 750m radius
            PatternSize.HUGE -> 1000.0        // 1km radius
            PatternSize.GIGANTIC -> 1500.0    // 1.5km radius
        }
        
        val smoothed = mutableListOf<GeoPoint>()
        smoothed.add(points.first()) // Keep first point (runway threshold)
        
        for (i in 1 until points.size - 1) {
            val prev = points[i - 1]
            val current = points[i]
            val next = points[i + 1]
            
            // Calculate distances to adjacent points
            val distToPrev = calculateDistance(current, prev)
            val distToNext = calculateDistance(current, next)
            
            // Skip smoothing if segments are too short for the fixed smoothing distance
            if (distToPrev < fixedSmoothDist * 1.5 || distToNext < fixedSmoothDist * 1.5) {
                smoothed.add(current)
                continue
            }
            
            // Use fixed smoothing distance for consistent rounding on all corners
            val smoothDist = fixedSmoothDist
            
            // Calculate bearings
            val bearingFromPrev = calculateBearing(prev, current)
            val bearingToNext = calculateBearing(current, next)
            
            // Create approach and exit points before/after corner
            val approachPoint = calculateDestination(
                current,
                normalizeHeading(bearingFromPrev + 180.0),
                smoothDist
            )
            val exitPoint = calculateDestination(
                current,
                bearingToNext,
                smoothDist
            )
            
            // Add approach point
            smoothed.add(approachPoint)
            
            // Add arc points for smooth corner (3-5 points depending on turn angle)
            val turnAngle = abs(normalizeHeading(bearingToNext - bearingFromPrev))
            val arcPoints = when {
                turnAngle > 60 -> 5 // Sharp turn
                turnAngle > 30 -> 3 // Medium turn
                else -> 2 // Gentle turn
            }
            
            for (j in 1..arcPoints) {
                val t = j.toDouble() / (arcPoints + 1)
                // Simple linear interpolation (could be improved with true arc)
                val lat = approachPoint.latitude + (exitPoint.latitude - approachPoint.latitude) * t
                val lon = approachPoint.longitude + (exitPoint.longitude - approachPoint.longitude) * t
                smoothed.add(GeoPoint(lat, lon))
            }
            
            // Add exit point
            smoothed.add(exitPoint)
        }
        
        smoothed.add(points.last()) // Keep last point (runway threshold)
        
        return smoothed
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
    private val labels: List<Pair<GeoPoint, String>>,
    private var mapRotationDegrees: Float = 0f
) : org.osmdroid.views.overlay.Overlay() {

    /**
     * Update the map rotation angle (to be called when map rotation changes)
     */
    fun updateMapRotation(rotationDegrees: Float) {
        mapRotationDegrees = rotationDegrees
    }
    
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

            // Save canvas state and apply counter-rotation to keep text upright
            canvas.save()
            // Counter-rotate around the label position to keep text upright
            // The map rotation is negative, so we negate it to counter-rotate
            canvas.rotate(-mapRotationDegrees, point.x.toFloat(), point.y.toFloat())

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

            // Restore canvas state
            canvas.restore()
        }
    }
}
