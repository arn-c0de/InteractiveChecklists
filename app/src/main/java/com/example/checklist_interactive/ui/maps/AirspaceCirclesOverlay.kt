package com.example.checklist_interactive.ui.maps

import android.graphics.*
import android.util.Log
import com.example.checklist_interactive.data.tactical.LocationEntity
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.*

private const val TAG = "AirspaceCirclesOverlay"

/**
 * ICAO Airspace class definition with typical dimensions
 */
data class AirspaceClass(
    val name: String,
    val displayName: String,
    val radiusNm: Double,
    val lowerAgl: String,
    val upperAgl: String,
    val rules: String,
    val color: Int,
    val isLayered: Boolean = false
)

/**
 * Overlay for displaying airspace circles around airports
 * Shows ICAO airspace classes with labels and configurable visibility
 */
class AirspaceCirclesOverlay(
    private val isEnabled: () -> Boolean,
    private val getTargetAirport: () -> LocationEntity?,
    private val getEnabledAirspaces: () -> Set<String>,
    private val getFillTransparency: () -> Float
) : Overlay() {

    // ICAO airspace class definitions
    private val airspaceClasses = listOf(
        AirspaceClass(
            name = "CLASS_D",
            displayName = "Class D (CTR)",
            radiusNm = 5.0,
            lowerAgl = "SFC",
            upperAgl = "2500 ft",
            rules = "Radio: Required | VFR/IFR: With clearance",
            color = Color.parseColor("#4000BFFF") // Blue
        ),
        AirspaceClass(
            name = "CLASS_C_CTR",
            displayName = "Class C (CTR)",
            radiusNm = 5.0,
            lowerAgl = "SFC",
            upperAgl = "2500 ft",
            rules = "Radio: Required | VFR/IFR: With clearance",
            color = Color.parseColor("#40FF00FF") // Magenta
        ),
        AirspaceClass(
            name = "CLASS_C_TMA",
            displayName = "Class C (TMA)",
            radiusNm = 15.0,
            lowerAgl = "1500 ft",
            upperAgl = "FL100",
            rules = "Radio: Required | VFR/IFR: With clearance",
            color = Color.parseColor("#40FF00FF"), // Magenta
            isLayered = true
        ),
        AirspaceClass(
            name = "CLASS_B",
            displayName = "Class B",
            radiusNm = 30.0,
            lowerAgl = "SFC",
            upperAgl = "FL180",
            rules = "Radio: Required | VFR: Explicit clearance",
            color = Color.parseColor("#400000FF") // Dark Blue
        ),
        AirspaceClass(
            name = "CLASS_E",
            displayName = "Class E",
            radiusNm = 10.0,
            lowerAgl = "700 ft",
            upperAgl = "FL195",
            rules = "Radio: IFR only | VFR: No clearance",
            color = Color.parseColor("#40808080") // Gray
        ),
        AirspaceClass(
            name = "CLASS_G",
            displayName = "Class G",
            radiusNm = 8.0,
            lowerAgl = "SFC",
            upperAgl = "700 ft",
            rules = "Uncontrolled | No radio required",
            color = Color.parseColor("#4000FF00") // Green
        )
    )

    private val circlePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val labelBackgroundPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#DD000000") // Semi-transparent black
        style = Paint.Style.FILL
    }

    private val labelBorderPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val labelTextPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 14f
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    private val labelSubTextPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#CCCCCC")
        textSize = 11f
        textAlign = Paint.Align.LEFT
    }

    private val textBounds = Rect()

    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        if (canvas == null || mapView == null || shadow) return
        if (!isEnabled()) return

        val airport = getTargetAirport() ?: return
        val enabledAirspaces = getEnabledAirspaces()
        if (enabledAirspaces.isEmpty()) return

        canvas.save()

        try {
            val centerGeoPoint = GeoPoint(airport.latitude, airport.longitude)
            val centerScreenPoint = mapView.projection.toPixels(centerGeoPoint, null)

            // Sort enabled airspaces by radius (largest first) so smaller ones appear on top
            val sortedAirspaces = airspaceClasses
                .filter { enabledAirspaces.contains(it.name) }
                .sortedByDescending { it.radiusNm }

            if (sortedAirspaces.isEmpty()) {
                Log.w(TAG, "No airspaces to draw (enabled: $enabledAirspaces)")
                return
            }

            // Pre-calculate all airspace circle data
            data class AirspaceCircle(
                val airspace: AirspaceClass,
                val radiusMeters: Double,
                val radiusPixels: Float,
                val labelAngle: Double,
                val labelScreenX: Float,
                val labelScreenY: Float
            )

            val airspaceCircles = sortedAirspaces.map { airspace ->
                val radiusMeters = airspace.radiusNm * 1852.0 // 1 NM = 1852 meters
                val edgeGeoPoint = GeoPoint(airport.latitude, airport.longitude)
                    .destinationPoint(radiusMeters, 90.0) // Point to the east
                val edgeScreenPoint = mapView.projection.toPixels(edgeGeoPoint, null)
                val radiusPixels = abs(edgeScreenPoint.x - centerScreenPoint.x).toFloat()

                val labelAngle = 45.0
                val labelGeoPoint = GeoPoint(airport.latitude, airport.longitude)
                    .destinationPoint(radiusMeters * 0.85, labelAngle)
                val labelScreenPoint = mapView.projection.toPixels(labelGeoPoint, null)

                AirspaceCircle(
                    airspace = airspace,
                    radiusMeters = radiusMeters,
                    radiusPixels = radiusPixels,
                    labelAngle = labelAngle,
                    labelScreenX = labelScreenPoint.x.toFloat(),
                    labelScreenY = labelScreenPoint.y.toFloat()
                )
            }

            // PHASE 1: Draw all fills with masking (largest to smallest)
            // Each circle masks out all smaller circles to create ring/donut effect
            val transparency = getFillTransparency().coerceIn(0.05f, 0.5f)
            for (i in airspaceCircles.indices) {
                val circle = airspaceCircles[i]

                // Create a layer for this airspace circle
                val layerId = canvas.saveLayer(null, null)

                // Draw the filled circle
                val baseColor = circle.airspace.color
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

                // Mask out all smaller circles (all circles after this one in the sorted list)
                fillPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                for (j in (i + 1) until airspaceCircles.size) {
                    val innerCircle = airspaceCircles[j]
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

            // PHASE 2: Draw all borders with offset for same-radius airspaces
            // Group by radius to detect overlaps
            val radiusGroups = airspaceCircles.groupBy { it.radiusPixels }

            for (circle in airspaceCircles.reversed()) {
                val sameRadiusCount = radiusGroups[circle.radiusPixels]?.size ?: 1
                val indexInGroup = radiusGroups[circle.radiusPixels]?.indexOf(circle) ?: 0

                // For overlapping airspaces, offset the radius slightly or use different patterns
                val radiusOffset = if (sameRadiusCount > 1) {
                    // Offset by stroke width to make both borders visible
                    indexInGroup * 8f
                } else {
                    0f
                }

                circlePaint.color = adjustAlpha(circle.airspace.color, 255)
                circlePaint.strokeWidth = 5f

                // Use different dash patterns for overlapping airspaces
                circlePaint.pathEffect = if (circle.airspace.isLayered) {
                    DashPathEffect(floatArrayOf(15f, 8f), 0f)
                } else if (sameRadiusCount > 1 && indexInGroup > 0) {
                    // Second airspace at same radius uses dash pattern
                    DashPathEffect(floatArrayOf(10f, 10f), 0f)
                } else {
                    null
                }

                canvas.drawCircle(
                    centerScreenPoint.x.toFloat(),
                    centerScreenPoint.y.toFloat(),
                    circle.radiusPixels + radiusOffset,
                    circlePaint
                )
            }

            // PHASE 3: Draw all labels
            for (circle in airspaceCircles) {
                drawAirspaceLabel(
                    canvas,
                    circle.labelScreenX,
                    circle.labelScreenY,
                    circle.airspace
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing airspace circles", e)
        } finally {
            canvas.restore()
        }
    }

    private fun drawAirspaceLabel(canvas: Canvas, x: Float, y: Float, airspace: AirspaceClass) {
        val nameText = airspace.displayName
        val altText = "${airspace.lowerAgl} - ${airspace.upperAgl}"
        val rulesText = airspace.rules

        // Calculate dimensions
        labelTextPaint.getTextBounds(nameText, 0, nameText.length, textBounds)
        val nameWidth = textBounds.width().toFloat()
        val nameHeight = textBounds.height().toFloat()

        labelSubTextPaint.getTextBounds(altText, 0, altText.length, textBounds)
        val altWidth = textBounds.width().toFloat()
        val altHeight = textBounds.height().toFloat()

        labelSubTextPaint.getTextBounds(rulesText, 0, rulesText.length, textBounds)
        val rulesWidth = textBounds.width().toFloat()
        val rulesHeight = textBounds.height().toFloat()

        val maxWidth = maxOf(nameWidth, altWidth, rulesWidth)
        val padding = 6f
        val lineSpacing = 3f
        val totalHeight = nameHeight + lineSpacing + altHeight + lineSpacing + rulesHeight

        val labelWidth = maxWidth + (padding * 2)
        val labelHeight = totalHeight + (padding * 2)

        // Draw background with border
        val rectF = RectF(x, y, x + labelWidth, y + labelHeight)
        canvas.drawRoundRect(rectF, 4f, 4f, labelBackgroundPaint)
        canvas.drawRoundRect(rectF, 4f, 4f, labelBorderPaint)

        // Draw text
        var textY = y + padding + nameHeight
        canvas.drawText(nameText, x + padding, textY, labelTextPaint)

        textY += altHeight + lineSpacing
        canvas.drawText(altText, x + padding, textY, labelSubTextPaint)

        textY += rulesHeight + lineSpacing
        canvas.drawText(rulesText, x + padding, textY, labelSubTextPaint)
    }

    private fun adjustAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    /**
     * Get all available airspace classes for configuration
     */
    fun getAvailableAirspaces(): List<AirspaceClass> = airspaceClasses
}
