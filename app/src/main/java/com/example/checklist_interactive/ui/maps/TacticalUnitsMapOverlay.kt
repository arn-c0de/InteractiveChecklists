package com.example.checklist_interactive.ui.maps

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.checklist_interactive.data.tactical.LocationEntity
import com.example.checklist_interactive.data.tactical.TacticalUnitEntity
import com.example.checklist_interactive.data.tactical.TacticalUnitsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

private const val TAG = "TacticalUnitsOverlay"

/**
 * Custom overlay for displaying tactical units with:
 * 1. Circle marker with letter - text rotates to stay readable, circle stays fixed
 * 2. Heading arrow - always points in absolute heading direction, independent of map rotation
 */
class TacticalUnitsMapOverlay(
    private val context: Context,
    private val repository: TacticalUnitsRepository,
    private val showLiveOnlyFlow: StateFlow<Boolean>,
    private val isEntityTrackingEnabledFlow: StateFlow<Boolean>,
    private val updateIntervalSecondsFlow: StateFlow<Float>,
    private val getMapView: () -> MapView?,
    private val onUnitClick: (TacticalUnitEntity) -> Unit
) : Overlay() {

    private val units = mutableMapOf<Int, TacticalUnitEntity>()
    private val unitArrows = mutableMapOf<Int, Bitmap>() // Arrows for each unit with matching color

    private var updateJob: Job? = null
    private var dataCollectorJob: Job? = null

    // Paint for debug info
    private val debugPaint = Paint().apply {
        color = Color.WHITE
        textSize = 12f
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    /**
     * Start tracking tactical units
     */
    fun startTracking(scope: CoroutineScope) {
        stopTracking()

        Log.d(TAG, "📡 Starting tactical units tracking")

        var lastUnits: List<TacticalUnitEntity> = emptyList()

        // Job 1: Collect latest units from database
        dataCollectorJob = scope.launch {
            combine(
                showLiveOnlyFlow,
                isEntityTrackingEnabledFlow,
                repository.getAllActiveUnits(),
                repository.getLiveUnits()
            ) { showLiveOnly: Boolean, isTrackingEnabled: Boolean, allUnits: List<TacticalUnitEntity>, liveUnits: List<TacticalUnitEntity> ->
                if (!isTrackingEnabled) allUnits
                else if (showLiveOnly) liveUnits
                else allUnits
            }.collect { newUnits: List<TacticalUnitEntity> ->
                Log.d(TAG, "📡 Database units updated: ${newUnits.size} units collected")
                lastUnits = newUnits

                // Update units map immediately - remove deleted units
                val currentUnitIds = newUnits.map { it.id }.toSet()
                val toRemove = units.keys.filter { it !in currentUnitIds }
                toRemove.forEach { unitId: Int ->
                    units.remove(unitId)
                    unitArrows.remove(unitId)
                }

                // Update existing units and add new ones
                newUnits.forEach { unit: TacticalUnitEntity ->
                    updateUnit(unit)
                }
            }
        }

        // Job 2: Update map at user-defined interval
        updateJob = scope.launch {
            // Wait for initial data
            var retryCount = 0
            Log.d(TAG, "📡 Waiting for initial tactical units data...")
            while (lastUnits.isEmpty() && retryCount < 50 && isActive) {
                delay(100)
                retryCount++
            }

            if (lastUnits.isEmpty()) {
                Log.w(TAG, "📡 No tactical units data after 5 seconds wait")
            } else {
                Log.d(TAG, "📡 Initial data loaded: ${lastUnits.size} units")
            }

            while (isActive) {
                val updateIntervalSeconds = updateIntervalSecondsFlow.value

                if (lastUnits.isNotEmpty()) {
                    Log.d(TAG, "📡 Updating ${lastUnits.size} tactical units on map (interval: ${updateIntervalSeconds}s)")

                    // Trigger map redraw
                    withContext(Dispatchers.Main) {
                        getMapView()?.invalidate()
                    }
                }

                // Wait for next update cycle
                delay((updateIntervalSeconds * 1000f).toLong())
            }
        }
    }

    /**
     * Stop tracking tactical units
     */
    fun stopTracking() {
        Log.d(TAG, "📡 Stopping tactical units tracking")
        updateJob?.cancel()
        dataCollectorJob?.cancel()
        units.clear()
        unitArrows.clear()
    }

    /**
     * Update unit data (called from database observer)
     */
    fun updateUnit(unit: TacticalUnitEntity) {
        units[unit.id] = unit

        // Create arrow if needed (circle is now drawn directly, no icon needed)
        if (unit.id !in unitArrows) {
            val coalitionColor = getCoalitionColor(unit.coalition)
            unitArrows[unit.id] = createArrowBitmap(coalitionColor)
        }
    }

    /**
     * Remove unit
     */
    fun removeUnit(unitId: Int) {
        units.remove(unitId)
        unitArrows.remove(unitId)
    }

    /**
     * Get coalition color
     */
    private fun getCoalitionColor(coalition: Int): Int {
        return when (coalition) {
            0 -> Color.parseColor("#999999") // Neutral (gray)
            1 -> Color.parseColor("#FF4444") // Red (hostile)
            2 -> Color.parseColor("#00A8FF") // Blue (friendly)
            else -> Color.parseColor("#FFFF80") // Unknown (yellow)
        }
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        val projection = mapView.projection
        val mapRotation = mapView.mapOrientation // Map rotation in degrees

        units.values.forEach { unit ->
            val geoPoint = GeoPoint(unit.latitude, unit.longitude)
            val screenPoint = projection.toPixels(geoPoint, null)
            
            // For OSMDroid Overlay: projection.toPixels() already gives correct position
            // DO NOT manually rotate - that's only for external Compose overlays!

            // Draw heading arrow FIRST (points in absolute map heading direction)
            unit.heading?.let { heading ->
                // Arrow must point in true map direction, compensating for map rotation
                drawHeadingArrow(canvas, screenPoint, unit.id, heading, mapRotation)
            }

            // Draw circle marker with letter SECOND
            // Circle doesn't rotate, letter stays readable
            drawCircleMarker(canvas, screenPoint, unit, mapRotation)
        }
    }

    /**
     * Draw circle marker with category letter
     * Circle stays in map orientation, letter counter-rotates to stay readable
     */
    private fun drawCircleMarker(canvas: Canvas, point: Point, unit: TacticalUnitEntity, mapRotation: Float) {
        val coalitionColor = getCoalitionColor(unit.coalition)
        val size = 48f
        val center = size / 2f
        val radius = size / 3f

        // Paint for filled circle
        val fillPaint = Paint().apply {
            isAntiAlias = true
            color = coalitionColor
            style = Paint.Style.FILL
        }

        // Paint for white border
        val strokePaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        canvas.save()
        
        // Translate to marker position
        canvas.translate(point.x.toFloat(), point.y.toFloat())
        
        // NO rotation for circle - it stays with map orientation

        // Draw filled circle
        canvas.drawCircle(0f, 0f, radius, fillPaint)
        canvas.drawCircle(0f, 0f, radius, strokePaint)

        // NOW counter-rotate ONLY for text to keep it readable
        canvas.rotate(-mapRotation)

        // Draw category letter
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 18f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val categoryLetter = when (unit.category.lowercase()) {
            "aircraft" -> "A"
            "helicopter" -> "H"
            "ground" -> "G"
            "ship" -> "S"
            "structure" -> "B" // Building
            "weapon" -> "W"
            else -> "U" // Unknown
        }

        // Center text vertically (account for text baseline)
        val textBounds = Rect()
        textPaint.getTextBounds(categoryLetter, 0, categoryLetter.length, textBounds)
        val textY = -textBounds.exactCenterY()

        canvas.drawText(categoryLetter, 0f, textY, textPaint)

        canvas.restore()
    }

    /**
     * Draw heading arrow that points in TRUE map direction
     * Arrow rotates with heading, stays fixed relative to map (not screen)
     */
    private fun drawHeadingArrow(canvas: Canvas, point: Point, unitId: Int, heading: Double, mapRotation: Float) {
        val arrowBitmap = unitArrows[unitId] ?: return

        canvas.save()

        // Translate to marker position
        canvas.translate(point.x.toFloat(), point.y.toFloat())

        // Rotate to heading on map - NO compensation for map rotation
        // heading = 0° means North on the map
        // When map rotates, arrow rotates with it to maintain true map direction
        canvas.rotate(heading.toFloat())

        // Draw arrow pointing up (will be rotated to heading direction)
        val halfWidth = arrowBitmap.width / 2f
        val halfHeight = arrowBitmap.height / 2f
        canvas.drawBitmap(arrowBitmap, -halfWidth, -halfHeight, null)

        canvas.restore()
    }

    /**
     * Create arrow bitmap for heading indicator with coalition color
     */
    private fun createArrowBitmap(color: Int): Bitmap {
        // Slightly larger arrow for better visibility
        val width = 44
        val height = 66
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            isAntiAlias = true
            this.color = color // Use coalition color
            style = Paint.Style.FILL
            strokeWidth = 2.5f
        }

        val strokePaint = Paint().apply {
            isAntiAlias = true
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }

        // Create arrow path pointing up — keep the tip, remove the long rear shaft (short stub only)
        val path = Path().apply {
            val tipY = 8f // slightly lower tip for a smaller point
            val headBaseY = height * 0.45f
            val stubBottomY = height * 0.62f // short stub under the head

            moveTo(width / 2f, tipY) // Arrow tip (top)
            lineTo(width * 0.82f, headBaseY) // Right outer (narrower)
            lineTo(width * 0.62f, headBaseY) // Right inner
            lineTo(width * 0.62f, stubBottomY) // Short stub bottom right
            lineTo(width * 0.38f, stubBottomY) // Short stub bottom left
            lineTo(width * 0.38f, headBaseY) // Left inner
            lineTo(width * 0.18f, headBaseY) // Left outer (narrower)
            close()
        }

        // Draw filled arrow
        canvas.drawPath(path, paint)

        // Draw white outline
        canvas.drawPath(path, strokePaint)

        return bitmap
    }

    override fun onSingleTapConfirmed(e: android.view.MotionEvent, mapView: MapView): Boolean {
        val projection = mapView.projection
        val touchPoint = Point(e.x.toInt(), e.y.toInt())

        // Find nearest unit within tap threshold
        val threshold = 50 // pixels
        var nearestUnit: TacticalUnitEntity? = null
        var nearestDistance = Float.MAX_VALUE

        units.values.forEach { unit ->
            val geoPoint = GeoPoint(unit.latitude, unit.longitude)
            val screenPoint = projection.toPixels(geoPoint, null)

            val dx = screenPoint.x - touchPoint.x
            val dy = screenPoint.y - touchPoint.y
            val distance = kotlin.math.sqrt((dx * dx + dy * dy).toFloat())

            if (distance < threshold && distance < nearestDistance) {
                nearestDistance = distance
                nearestUnit = unit
            }
        }

        nearestUnit?.let { unit ->
            onUnitClick(unit)
            return true
        }

        return false
    }
}
