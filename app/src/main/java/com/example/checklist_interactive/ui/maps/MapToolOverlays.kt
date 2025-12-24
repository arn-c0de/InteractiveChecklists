package com.example.checklist_interactive.ui.maps

import android.graphics.Paint
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Overlay

/**
 * MapToolOverlays - All map overlay implementations for compass, range rings, and MGRS grid
 * 
 * This file contains all custom overlay classes used in MapViewer:
 * - CompassOverlay: Fixed-size compass with cardinal directions and degree ticks
 * - HeadingSpeedLineOverlay: Yellow line showing aircraft heading and speed
 * - RangeRingsOverlay: Concentric circles for distance estimation
 * - MgrsGridOverlay: Military Grid Reference System overlay
 */

/**
 * Fixed-size compass overlay drawn centered on given GeoPoint (scales with zoom, not speed)
 */
class CompassOverlay : Overlay() {
    var center: GeoPoint? = null
    var heading: Float = 0f
    private val paint = Paint().apply {
        isAntiAlias = true
        // more transparent red for a subtler compass
        color = android.graphics.Color.argb(0x66, 0xFF, 0x44, 0x44)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        // slightly translucent labels
        color = android.graphics.Color.argb(0xCC, 0xFF, 0xFF, 0xFF)
        textSize = 32f
    }
    private val circlePaint = Paint(paint).apply {
        style = Paint.Style.STROKE
        strokeWidth = paint.strokeWidth
    }
    private val smallTickPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        strokeWidth = 1f
    }
    private val majorTickPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        strokeWidth = 2f
    }
    private val labelSize = Paint(textPaint).apply {
        textSize = 18f
    }
    private val headingPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.YELLOW
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val headingTextPaint = Paint(textPaint).apply {
        color = android.graphics.Color.YELLOW
        textSize = 24f
    }
    private val headingTextShadowPaint = Paint(headingTextPaint).apply {
        color = android.graphics.Color.argb(0xCC, 0, 0, 0)
    }

    override fun draw(canvas: android.graphics.Canvas?, mapView: org.osmdroid.views.MapView?, shadow: Boolean) {
        if (shadow) return
        val mv = mapView ?: return
        val c = center ?: return
        val proj = mv.projection
        val centerPt = Point()
        proj.toPixels(c, centerPt)

        // Fixed radius based on zoom level (NOT speed)
        val zoomLevel = mv.zoomLevelDouble
        // Original base (40..240) scaled up ~2.2x (a bit smaller than before)
        val baseRadius = ((40f + (zoomLevel.toFloat() - 8f) * 20f) * 2.2f).coerceIn(88f, 528f)

        // outer circle
        canvas?.drawCircle(centerPt.x.toFloat(), centerPt.y.toFloat(), baseRadius, circlePaint)

        // Cardinal radial lines and labels (N, E, S, W)
        val cardinals = listOf(0, 90, 180, 270)
        val labelMap = mapOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")
        for (angle in cardinals) {
            val rad = Math.toRadians(angle.toDouble())
            val dx = (Math.sin(rad) * baseRadius).toFloat()
            val dy = (-Math.cos(rad) * baseRadius).toFloat()
            // full line across the circle
            canvas?.drawLine(centerPt.x - dx, centerPt.y - dy, centerPt.x + dx, centerPt.y + dy, paint)
            // label slightly beyond the ring
            val lx = centerPt.x + (dx * 1.12f)
            val ly = centerPt.y + (dy * 1.12f) + (textPaint.textSize / 3)
            canvas?.drawText(labelMap[angle] ?: "", lx, ly, textPaint)
        }

        // Degree ticks around outer ring
        // - Minor ticks every 5° (small)
        // - Major ticks every 30° (long)
        // - Numeric labels at 0°, 90°, 180°, 270°
        
        // Minor ticks (every 5° excluding the major tick positions)
        for (a in 0 until 360 step 5) {
            if (a % 30 == 0) continue // skip majors
            val rad = Math.toRadians(a.toDouble())
            val dx = (Math.sin(rad) * baseRadius).toFloat()
            val dy = (-Math.cos(rad) * baseRadius).toFloat()
            val innerX = centerPt.x + (dx * 0.985f)
            val innerY = centerPt.y + (dy * 0.985f)
            val outerX = centerPt.x + dx
            val outerY = centerPt.y + dy
            canvas?.drawLine(innerX, innerY, outerX, outerY, smallTickPaint)
        }

        // Major ticks and numeric labels (every 30°; label every 90°)
        for (a in 0 until 360 step 30) {
            val rad = Math.toRadians(a.toDouble())
            val dx = (Math.sin(rad) * baseRadius).toFloat()
            val dy = (-Math.cos(rad) * baseRadius).toFloat()
            val innerX = centerPt.x + (dx * 0.92f)
            val innerY = centerPt.y + (dy * 0.92f)
            val outerX = centerPt.x + dx
            val outerY = centerPt.y + dy
            canvas?.drawLine(innerX, innerY, outerX, outerY, majorTickPaint)

            if (a % 90 == 0) {
                val label = String.format(mapView.context.getString(com.example.checklist_interactive.R.string.map_label_degrees), a)
                val lx = centerPt.x + (dx * 0.8f)
                val ly = centerPt.y + (dy * 0.8f)
                canvas?.drawText(label, lx, ly, labelSize)
            }
        }

        // Heading indicator (just a small marker on the compass ring, NOT a line)
        val headingNorm = (((heading % 360) + 360) % 360).toInt()
        val radH = Math.toRadians(headingNorm.toDouble())
        val dxH = (Math.sin(radH) * baseRadius).toFloat()
        val dyH = (-Math.cos(radH) * baseRadius).toFloat()
        // Draw a small circle marker at the heading position on the ring
        // Draw a small circle marker at the heading position
        canvas?.drawCircle(centerPt.x + dxH, centerPt.y + dyH, 8f, headingPaint)
        // Outline for visibility
        canvas?.drawCircle(centerPt.x + dxH, centerPt.y + dyH, 8f, outlinePaint)
        
        // Label with heading near the marker
        val label = String.format(mapView.context.getString(com.example.checklist_interactive.R.string.map_label_hdg_degrees), headingNorm)
        val labelX = centerPt.x + (dxH * 1.25f)
        val labelY = centerPt.y + (dyH * 1.25f)
        // Draw shadow for readability
        canvas?.drawText(label, labelX + 2f, labelY + 2f, headingTextShadowPaint)
        canvas?.drawText(label, labelX, labelY, headingTextPaint)
    }
}

/**
 * Heading speed line overlay: yellow line from center showing heading, length based on speed
 */
class HeadingSpeedLineOverlay : Overlay() {
    var center: GeoPoint? = null
    var heading: Float = 0f
    var speedKts: Double = 0.0
    
    private val linePaint = Paint().apply {
        // Slightly transparent yellow for subtler visual
        color = android.graphics.Color.argb(0xCC, 0xFF, 0xFF, 0x00)
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        textSize = 30f
    }
    private val bgPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(0xCC, 0, 0, 0)
        style = Paint.Style.FILL
    }

    override fun draw(canvas: android.graphics.Canvas?, mapView: org.osmdroid.views.MapView?, shadow: Boolean) {
        if (shadow || canvas == null || mapView == null) return
        val c = center ?: return

        val screenPt = Point()
        val projection = mapView.projection
        projection.toPixels(c, screenPt)

        val cx = screenPt.x.toFloat()
        val cy = screenPt.y.toFloat()

        // Defensive speed handling to avoid NaN/Infinite lengths
        val safeSpeed = if (speedKts.isFinite()) speedKts.coerceIn(0.0, 1000.0) else 0.0
        val speedFactor = (safeSpeed / 300.0).coerceIn(0.0, 1.0)
        // Original was 50 + 150*factor, scale up by 2.5x (half of previous 5x), clamp to safe pixel range
        var lineLength = ((50f + 150f * speedFactor.toFloat()) * 2.5f).coerceIn(50f, 1000f)
        if (!lineLength.isFinite()) lineLength = 200f

        // Validate heading
        if (!heading.isFinite()) return
        val headingRad = Math.toRadians(heading.toDouble())
        var endX = cx + lineLength * Math.sin(headingRad).toFloat()
        var endY = cy - lineLength * Math.cos(headingRad).toFloat()

        // Defensive check: ensure endpoints are finite and within reasonable bounds
        if (!endX.isFinite() || !endY.isFinite()) {
            // fallback to capped length
            lineLength = 200f
            endX = cx + lineLength * Math.sin(headingRad).toFloat()
            endY = cy - lineLength * Math.cos(headingRad).toFloat()
        }

        // Draw the heading line
        canvas.drawLine(cx, cy, endX, endY, linePaint)

        // Draw speed label at the tip of the line (only if speed is finite)
        if (safeSpeed.isFinite()) {
            try {
                val speedText = "%.0f kts".format(safeSpeed)
                val textBounds = android.graphics.Rect()
                textPaint.getTextBounds(speedText, 0, speedText.length, textBounds)
                
                val labelX = endX - textBounds.width() / 2f
                val labelY = endY - 10f
                
                // Draw background
                canvas.drawRect(
                    labelX - 6f,
                    labelY - textBounds.height() - 4f,
                    labelX + textBounds.width() + 6f,
                    labelY + 4f,
                    bgPaint
                )
                
                canvas.drawText(speedText, labelX, labelY, textPaint)
            } catch (_: Throwable) {
                // Ignore formatting errors
            }
        }
    }
}

/**
 * Range rings overlay: concentric circles around center to estimate distances (1,2,5 NM)
 */
class RangeRingsOverlay : Overlay() {
    var center: GeoPoint? = null
    // current heading (used to place exact heading label on outermost ring)
    var heading: Float = 0f
    // current speed in knots; used to scale the heading radial length
    var speedKts: Double = 0.0
    // max radius in NM; default 5
    var maxNm: Int = 5
    private val paint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(0x99, 0x22, 0x88, 0xFF)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        textSize = 22f
    }
    private val tickPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(0xCC, 0xFF, 0xFF, 0xFF)
        strokeWidth = 2f
    }
    private val smallText = Paint(textPaint).apply {
        textSize = 18f
    }
    private val headingPaint = Paint(textPaint).apply {
        color = android.graphics.Color.YELLOW
        textSize = textPaint.textSize + 2f
    }
    private val headingLinePaint = Paint(paint).apply {
        color = android.graphics.Color.argb(0xCC, 0xFF, 0xFF, 0x00)
        strokeWidth = 4f
    }

    private fun generateDistancesMeters(): List<Double> {
        // Generate sequence 1,2,5,10,20,50,100,200,... up to maxNm
        val bases = listOf(1, 2, 5)
        val resultNm = mutableListOf<Int>()
        var multiplier = 1
        while (true) {
            var addedAny = false
            for (b in bases) {
                val value = b * multiplier
                if (value <= maxNm) {
                    resultNm.add(value)
                    addedAny = true
                }
            }
            if (!addedAny) break
            multiplier *= 10
        }
        // ensure unique and sorted
        val finalNm = resultNm.distinct().sorted()
        return finalNm.map { it * 1852.0 }
    }

    override fun draw(canvas: android.graphics.Canvas?, mapView: org.osmdroid.views.MapView?, shadow: Boolean) {
        if (shadow) return
        val mv = mapView ?: return
        val c = center ?: return
        val proj = mv.projection
        val centerPt = Point()
        proj.toPixels(c, centerPt)

        val latRad = Math.toRadians(c.latitude)

        val distances = generateDistancesMeters()

        var outerRadiusPx = 0f
        distances.forEachIndexed { idx, meters ->
            // convert meters to degrees longitude delta at this latitude
            val deltaLon = meters / (111319.9 * Math.cos(latRad))
            val edge = GeoPoint(c.latitude, c.longitude + deltaLon)
            val edgePt = Point()
            proj.toPixels(edge, edgePt)
            val radiusPx = kotlin.math.hypot((edgePt.x - centerPt.x).toDouble(), (edgePt.y - centerPt.y).toDouble()).toFloat()
            canvas?.drawCircle(centerPt.x.toFloat(), centerPt.y.toFloat(), radiusPx, paint)
            // label at rightmost point
            val nm = (meters / 1852.0).toInt()
            val label = String.format(mapView.context.getString(com.example.checklist_interactive.R.string.map_label_nm), nm)
            canvas?.drawText(label, centerPt.x + radiusPx + 6f, centerPt.y.toFloat() - 6f - (idx * 18), textPaint)
            if (idx == distances.lastIndex) outerRadiusPx = radiusPx
        }

        if (outerRadiusPx > 0f) {
            // Draw cardinal radial lines through the outermost ring and label them
            val cardinals = listOf(0, 90, 180, 270)
            val labelMap = mapOf(0 to "N", 90 to "O", 180 to "S", 270 to "W")
            
            for (angle in cardinals) {
                val rad = Math.toRadians(angle.toDouble())
                val dx = (Math.sin(rad) * outerRadiusPx).toFloat()
                val dy = (-Math.cos(rad) * outerRadiusPx).toFloat()
                canvas?.drawLine(centerPt.x.toFloat(), centerPt.y.toFloat(), centerPt.x + dx, centerPt.y + dy, tickPaint)
                val label = labelMap[angle] ?: ""
                canvas?.drawText(label, centerPt.x + (dx * 1.1f), centerPt.y + (dy * 1.1f), smallText)
            }

            // Degree ticks around outer ring (every 30°, label every 60°)
            for (a in 0 until 360 step 30) {
                val rad = Math.toRadians(a.toDouble())
                val dx = (Math.sin(rad) * outerRadiusPx).toFloat()
                val dy = (-Math.cos(rad) * outerRadiusPx).toFloat()
                val innerX = centerPt.x + (dx * 0.95f)
                val innerY = centerPt.y + (dy * 0.95f)
                canvas?.drawLine(innerX, innerY, centerPt.x + dx, centerPt.y + dy, tickPaint)
                
                if (a % 60 == 0 && a !in cardinals) {
                    val label = String.format(mapView.context.getString(com.example.checklist_interactive.R.string.map_label_degrees), a)
                    canvas?.drawText(label, centerPt.x + (dx * 1.12f), centerPt.y + (dy * 1.12f), smallText)
                }
            }

            // Draw exact heading label and heading radial line on the outermost ring (length scaled by speed)
            val headingNorm = (((heading % 360) + 360) % 360).toInt()
            val hRad = Math.toRadians(headingNorm.toDouble())

            // Scale heading radial by speed: small when stationary, longer with speed
            val minFactor = 0.05f
            val maxFactor = 1.0f
            val maxSpeed = 300.0 // knots
            var scaleFactor = (minFactor + ((speedKts.coerceIn(0.0, maxSpeed) / maxSpeed) * (maxFactor - minFactor))).toFloat()
            if (!scaleFactor.isFinite() || scaleFactor.isNaN()) scaleFactor = minFactor
            // Compute heading radial scaled by speed; cap it to a configurable maximum so it never becomes too long.
            // maxHeadingRatio defines the maximum fraction of the outer ring the heading radial may occupy.
            // Also clamp to an absolute pixel limit for very large rings.
            val headingRadiusRaw = outerRadiusPx * scaleFactor * 1f
            val minHeadingPx = 12f
            val maxHeadingRatio = 0.6f // at most 60% of outer ring
            val maxHeadingAbsPx = 300f // absolute cap in pixels
            val maxHeadingPx = kotlin.math.min(outerRadiusPx * maxHeadingRatio, maxHeadingAbsPx)
            val headingRadius = headingRadiusRaw.coerceIn(minHeadingPx, maxHeadingPx)

            val hx = centerPt.x + (Math.sin(hRad) * headingRadius).toFloat()
            val hy = centerPt.y + (-Math.cos(hRad) * headingRadius).toFloat()
            val headingLabel = String.format(mapView.context.getString(com.example.checklist_interactive.R.string.map_label_degrees), headingNorm)
            // Draw a highlighted radial for heading (slightly transparent)
            canvas?.drawLine(centerPt.x.toFloat(), centerPt.y.toFloat(), hx, hy, headingLinePaint)

            // Draw heading label
            canvas?.drawText(headingLabel, hx, hy - 6f, headingPaint)
        }
    }
}

/**
 * MGRS Grid overlay: draws Military Grid Reference System grid lines in red with transparency
 */
class MgrsGridOverlay : Overlay() {
    private val gridPaint = Paint().apply {
        color = android.graphics.Color.argb(128, 255, 0, 0) // Red with 50% transparency
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = android.graphics.Color.argb(200, 255, 0, 0) // Red with 78% transparency
        textSize = 24f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val bgPaint = Paint().apply {
        color = android.graphics.Color.argb(180, 0, 0, 0) // Black background for text
        style = Paint.Style.FILL
    }

    override fun draw(canvas: android.graphics.Canvas?, mapView: org.osmdroid.views.MapView?, shadow: Boolean) {
        if (canvas == null || mapView == null || shadow) return

        val projection = mapView.projection
        val boundingBox = projection.boundingBox

        // Calculate grid spacing based on zoom level
        val zoom = mapView.zoomLevelDouble
        val gridSpacing = when {
            zoom >= 15 -> 0.001 // ~100m
            zoom >= 13 -> 0.01  // ~1km
            zoom >= 10 -> 0.1   // ~10km
            else -> 1.0         // ~100km
        }

        // Draw vertical lines (longitude)
        var lon = Math.floor(boundingBox.lonWest / gridSpacing) * gridSpacing
        while (lon <= boundingBox.lonEast) {
            val topPoint = projection.toPixels(GeoPoint(boundingBox.latNorth, lon), null)
            val bottomPoint = projection.toPixels(GeoPoint(boundingBox.latSouth, lon), null)
            canvas.drawLine(
                topPoint.x.toFloat(),
                topPoint.y.toFloat(),
                bottomPoint.x.toFloat(),
                bottomPoint.y.toFloat(),
                gridPaint
            )

            // Draw label at top
            val label = formatMgrsCoordinate(lon, true)
            val textX = topPoint.x.toFloat()
            val textY = topPoint.y.toFloat() + 30f
            val textBounds = android.graphics.Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            canvas.drawRect(
                textX - textBounds.width() / 2f - 4f,
                textY - textBounds.height() - 4f,
                textX + textBounds.width() / 2f + 4f,
                textY + 4f,
                bgPaint
            )
            canvas.drawText(label, textX, textY, textPaint)

            lon += gridSpacing
        }

        // Draw horizontal lines (latitude)
        var lat = Math.floor(boundingBox.latSouth / gridSpacing) * gridSpacing
        while (lat <= boundingBox.latNorth) {
            val leftPoint = projection.toPixels(GeoPoint(lat, boundingBox.lonWest), null)
            val rightPoint = projection.toPixels(GeoPoint(lat, boundingBox.lonEast), null)
            canvas.drawLine(
                leftPoint.x.toFloat(),
                leftPoint.y.toFloat(),
                rightPoint.x.toFloat(),
                rightPoint.y.toFloat(),
                gridPaint
            )

            // Draw label at left
            val label = formatMgrsCoordinate(lat, false)
            val textX = leftPoint.x.toFloat() + 50f
            val textY = leftPoint.y.toFloat()
            val textBounds = android.graphics.Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            canvas.drawRect(
                textX - textBounds.width() / 2f - 4f,
                textY - textBounds.height() / 2f - 4f,
                textX + textBounds.width() / 2f + 4f,
                textY + textBounds.height() / 2f + 4f,
                bgPaint
            )
            canvas.drawText(label, textX, textY + textBounds.height() / 2f, textPaint)

            lat += gridSpacing
        }
    }
    
    private fun formatMgrsCoordinate(value: Double, isLongitude: Boolean): String {
        val degrees = Math.abs(value).toInt()
        val minutes = ((Math.abs(value) - degrees) * 60).toInt()
        val direction = if (isLongitude) {
            if (value >= 0) "E" else "W"
        } else {
            if (value >= 0) "N" else "S"
        }
        return String.format("%d\u00b0%02d'%s", degrees, minutes, direction)
    }
}
