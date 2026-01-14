package com.example.checklist_interactive.ui.maps

import android.graphics.*
import android.util.Log
import com.example.checklist_interactive.data.tactical.TacticalUnitEntity
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs

private const val TAG = "AARangeRingsOverlay"

/**
 * Overlay for displaying AA (Anti-Aircraft) range rings around ground units
 * Shows detection and engagement ranges with labels and configurable visibility
 * Supports multiple AA units simultaneously
 */
class AARangeRingsOverlay(
    private val isEnabled: () -> Boolean,
    private val getAAUnits: () -> List<TacticalUnitEntity>,
    private val getFillTransparency: () -> Float,
    private val getShowAllAARange: () -> Boolean,
    private val getUnitRangeVisibility: (Int) -> Boolean // Check if specific unit has range visible
) : Overlay() {

    private val circlePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val labelBackgroundPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#E5000000") // Semi-transparent black (90% opacity)
        style = Paint.Style.FILL
    }

    private val labelBorderPaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val labelTextPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 13f
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    private val labelSubTextPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#FFCCCC") // Light red
        textSize = 10f
        textAlign = Paint.Align.LEFT
    }

    private val textBounds = Rect()

    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        if (canvas == null || mapView == null || shadow) return
        if (!isEnabled()) return

        val aaUnits = getAAUnits()
        if (aaUnits.isEmpty()) return

        // Get current map bounds to only draw visible units
        val boundingBox = mapView.boundingBox
        val minLat = boundingBox.latSouth
        val maxLat = boundingBox.latNorth
        val minLon = boundingBox.lonWest
        val maxLon = boundingBox.lonEast

        canvas.save()

        try {
            // Draw range rings for each AA unit
            for (aaUnit in aaUnits) {
                // Skip units outside visible bounds (with buffer for range rings)
                // Add ~50km buffer to account for large range rings
                val bufferDegrees = 0.5 // ~50km latitude buffer
                if (aaUnit.latitude < minLat - bufferDegrees || aaUnit.latitude > maxLat + bufferDegrees ||
                    aaUnit.longitude < minLon - bufferDegrees || aaUnit.longitude > maxLon + bufferDegrees) {
                    continue
                }

                // Check if this unit should show range rings
                val showAllAA = getShowAllAARange()
                val showThisUnit = getUnitRangeVisibility(aaUnit.id)

                if (showAllAA || showThisUnit) {
                    drawRangeRingsForUnit(canvas, mapView, aaUnit)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing AA range rings", e)
        } finally {
            canvas.restore()
        }
    }

    private fun drawRangeRingsForUnit(
        canvas: Canvas,
        mapView: MapView,
        aaUnit: TacticalUnitEntity
    ) {
        try {
            // Get AA system specification by unit name/type
            val aaSystem = AARangeDatabase.getSystemByUnitType(aaUnit.name)
                ?: AARangeDatabase.getSystemByUnitType(aaUnit.type)

            if (aaSystem == null) {
                Log.d(TAG, "No AA system spec found for unit: ${aaUnit.name} (type: ${aaUnit.type})")
                return
            }

            val centerGeoPoint = GeoPoint(aaUnit.latitude, aaUnit.longitude)
            val centerScreenPoint = mapView.projection.toPixels(centerGeoPoint, null)

            // Get range rings for this system
            val rangeRings = AARangeDatabase.getRangeRingsForSystem(aaSystem)

            if (rangeRings.isEmpty()) {
                return
            }

            // Pre-calculate all ring circle data
            data class RingCircle(
                val ring: AARangeRing,
                val radiusMeters: Double,
                val radiusPixels: Float,
                val labelAngle: Double,
                val labelScreenX: Float,
                val labelScreenY: Float
            )

            val ringCircles = rangeRings.map { ring ->
                val radiusMeters = ring.radiusKm * 1000.0 // km to meters
                val edgeGeoPoint = GeoPoint(aaUnit.latitude, aaUnit.longitude)
                    .destinationPoint(radiusMeters, 90.0) // Point to the east
                val edgeScreenPoint = mapView.projection.toPixels(edgeGeoPoint, null)
                val radiusPixels = abs(edgeScreenPoint.x - centerScreenPoint.x).toFloat()

                val labelAngle = 135.0 // Top-right for labels
                val labelGeoPoint = GeoPoint(aaUnit.latitude, aaUnit.longitude)
                    .destinationPoint(radiusMeters * 0.90, labelAngle)
                val labelScreenPoint = mapView.projection.toPixels(labelGeoPoint, null)

                RingCircle(
                    ring = ring,
                    radiusMeters = radiusMeters,
                    radiusPixels = radiusPixels,
                    labelAngle = labelAngle,
                    labelScreenX = labelScreenPoint.x.toFloat(),
                    labelScreenY = labelScreenPoint.y.toFloat()
                )
            }

            // PHASE 1: Draw all fills with masking (largest to smallest) for ring/donut effect
            val transparency = getFillTransparency().coerceIn(0.05f, 0.3f) // Lower max for AA rings
            for (i in ringCircles.indices) {
                val circle = ringCircles[i]

                // Create a layer for this ring
                val layerId = canvas.saveLayer(null, null)

                // Draw the filled circle
                val baseColor = circle.ring.color
                val adjustedColor = Color.argb(
                    (transparency * 255).toInt(),
                    Color.red(baseColor),
                    Color.green(baseColor),
                    Color.blue(baseColor)
                )
                fillPaint.color = adjustedColor
                fillPaint.xfermode = null
                canvas.drawCircle(
                    centerScreenPoint.x.toFloat(),
                    centerScreenPoint.y.toFloat(),
                    circle.radiusPixels,
                    fillPaint
                )

                // Mask out all smaller circles (all circles after this one)
                fillPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                for (j in (i + 1) until ringCircles.size) {
                    val innerCircle = ringCircles[j]
                    canvas.drawCircle(
                        centerScreenPoint.x.toFloat(),
                        centerScreenPoint.y.toFloat(),
                        innerCircle.radiusPixels,
                        fillPaint
                    )
                }

                // Restore the layer
                fillPaint.xfermode = null
                canvas.restoreToCount(layerId)
            }

            // PHASE 2: Draw all borders
            for (circle in ringCircles.reversed()) {
                circlePaint.color = circle.ring.color
                circlePaint.strokeWidth = circle.ring.strokeWidth

                // Use dash pattern if specified
                circlePaint.pathEffect = if (circle.ring.isDashed) {
                    DashPathEffect(floatArrayOf(15f, 10f), 0f)
                } else {
                    null
                }

                canvas.drawCircle(
                    centerScreenPoint.x.toFloat(),
                    centerScreenPoint.y.toFloat(),
                    circle.radiusPixels,
                    circlePaint
                )
            }

            // PHASE 3: Draw unit label at center
            drawUnitLabel(
                canvas,
                centerScreenPoint.x.toFloat(),
                centerScreenPoint.y.toFloat(),
                aaSystem,
                aaUnit
            )

            // PHASE 4: Draw range labels for each ring
            for (circle in ringCircles) {
                drawRangeLabel(
                    canvas,
                    circle.labelScreenX,
                    circle.labelScreenY,
                    circle.ring
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing range rings for unit ${aaUnit.name}", e)
        }
    }

    private fun drawUnitLabel(
        canvas: Canvas,
        x: Float,
        y: Float,
        aaSystem: AASystemSpec,
        aaUnit: TacticalUnitEntity
    ) {
        val nameText = aaSystem.displayName
        val rangeText = "Rng: ${aaSystem.engagementRangeKm.toInt()}km"
        val altText = "Alt: ${(aaSystem.maxAltitudeM / 1000).toInt()}k"

        // Calculate dimensions
        labelTextPaint.getTextBounds(nameText, 0, nameText.length, textBounds)
        val nameWidth = textBounds.width().toFloat()
        val nameHeight = textBounds.height().toFloat()

        labelSubTextPaint.getTextBounds(rangeText, 0, rangeText.length, textBounds)
        val rangeWidth = textBounds.width().toFloat()
        val rangeHeight = textBounds.height().toFloat()

        labelSubTextPaint.getTextBounds(altText, 0, altText.length, textBounds)
        val altWidth = textBounds.width().toFloat()

        val maxWidth = maxOf(nameWidth, rangeWidth, altWidth)
        val padding = 6f
        val lineSpacing = 2f
        val totalHeight = nameHeight + lineSpacing + rangeHeight + lineSpacing + rangeHeight

        val labelWidth = maxWidth + (padding * 2)
        val labelHeight = totalHeight + (padding * 2)

        // Center label on unit position
        val labelX = x - labelWidth / 2f
        val labelY = y - labelHeight / 2f

        // Draw background with red border
        val rectF = RectF(labelX, labelY, labelX + labelWidth, labelY + labelHeight)
        canvas.drawRoundRect(rectF, 4f, 4f, labelBackgroundPaint)
        canvas.drawRoundRect(rectF, 4f, 4f, labelBorderPaint)

        // Draw text
        var textY = labelY + padding + nameHeight
        canvas.drawText(nameText, labelX + padding, textY, labelTextPaint)

        textY += rangeHeight + lineSpacing
        canvas.drawText(rangeText, labelX + padding, textY, labelSubTextPaint)

        textY += rangeHeight + lineSpacing
        canvas.drawText(altText, labelX + padding, textY, labelSubTextPaint)
    }

    private fun drawRangeLabel(canvas: Canvas, x: Float, y: Float, ring: AARangeRing) {
        val labelText = "${ring.radiusKm.toInt()} km"

        // Calculate dimensions
        labelSubTextPaint.getTextBounds(labelText, 0, labelText.length, textBounds)
        val textWidth = textBounds.width().toFloat()
        val textHeight = textBounds.height().toFloat()

        val padding = 4f
        val labelWidth = textWidth + (padding * 2)
        val labelHeight = textHeight + (padding * 2)

        // Draw small background
        val rectF = RectF(x, y, x + labelWidth, y + labelHeight)

        // Use ring color for label background (with more opacity)
        val labelBg = Paint().apply {
            isAntiAlias = true
            color = Color.argb(230, Color.red(ring.color), Color.green(ring.color), Color.blue(ring.color))
            style = Paint.Style.FILL
        }

        canvas.drawRoundRect(rectF, 3f, 3f, labelBg)

        // Draw text
        val textY = y + padding + textHeight

        // Use white text for better contrast
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 11f
            textAlign = Paint.Align.LEFT
            isFakeBoldText = true
        }

        canvas.drawText(labelText, x + padding, textY, textPaint)
    }
}
