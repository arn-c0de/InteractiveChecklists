package com.example.checklist_interactive.ui.maps.overlays

import android.content.Context
import android.graphics.*
import android.util.Log
import com.example.checklist_interactive.data.tactical.LocationEntity
import com.example.checklist_interactive.data.tactical.LocationRepository
import com.example.checklist_interactive.data.tactical.RunwayEntity
import com.example.checklist_interactive.data.tactical.TacticalDatabase
import com.example.checklist_interactive.ui.common.calculateMapOverlayScale
import com.example.checklist_interactive.ui.common.getScaledTextSize
import com.example.checklist_interactive.ui.common.getScaledStrokeWidth
import com.example.checklist_interactive.ui.common.getScaledLabelOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

private const val TAG = "AirportLabelsOverlay"

/**
 * Custom overlay for displaying airport marker labels with:
 * 1. Airport name
 * 2. Available runway designators (e.g., "05/27")
 * 
 * Only shows labels for static airport markers from the database,
 * not for tactical/live markers.
 */
class AirportMarkerLabelsOverlay(
    private val context: Context,
    private val locationRepository: LocationRepository,
    private val database: TacticalDatabase,
    private val getVisibleMaps: () -> List<String>,
    private val isEnabled: () -> Boolean,
    private val getMapView: () -> MapView?
) : Overlay() {

    private val airports = mutableListOf<AirportLabel>()
    private var updateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Calculate overlay scale once during initialization
    private val overlayScale = calculateMapOverlayScale(context)

    // Device scale factor based on screen width (helps small phones show larger labels)
    private fun deviceScale(): Float {
        val metrics = context.resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        return when {
            // Stronger scaling for very small phones
            widthDp <= 360f -> 1.40f
            // Moderate scaling for medium-small phones
            widthDp <= 420f -> 1.25f
            // Slight increase for others to keep labels comfortably readable
            else -> 1.05f
        }
    }

    // Paint for label background
    private val labelBackgroundPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#CC000000") // Semi-transparent black
        style = Paint.Style.FILL
    }

    // Paint for label border
    private val labelBorderPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = getScaledStrokeWidth(2f, context)
    }

    // Paint for label text
    private val labelTextPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = getScaledTextSize(16f, context)
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    // Paint for runway text (smaller, less bold)
    private val runwayTextPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#BBBBBB") // Light gray
        textSize = getScaledTextSize(13f, context)
        textAlign = Paint.Align.LEFT
        isFakeBoldText = false
    }

    private val textBounds = Rect()

    data class AirportLabel(
        val location: LocationEntity,
        val runwayNames: String // e.g., "05/27, 13L/31R"
    )

    init {
        startDataCollection()
    }

    private fun startDataCollection() {
        updateJob?.cancel()
        updateJob = scope.launch {
            try {
                // Collect airport markers and their runways
                locationRepository.getAllLocations().collect { locations ->
                    Log.d(TAG, "Processing ${locations.size} total locations")
                    
                    val visibleMaps = getVisibleMaps()
                    Log.d(TAG, "Visible maps: $visibleMaps")
                    
                    // Count by type
                    val airportCount = locations.count { it.markerType == "airport" }
                    Log.d(TAG, "Found $airportCount airports")
                    
                    // Filter to only airport markers that match visible maps
                    val airportMarkers = locations.filter { loc ->
                        val isAirport = loc.markerType == "airport"
                        val mapMatch = visibleMaps.isEmpty() || visibleMaps.any { map -> 
                            map.equals(loc.map ?: "Unknown", ignoreCase = true) 
                        }
                        
                        isAirport && mapMatch
                    }
                    
                    Log.d(TAG, "Filtered to ${airportMarkers.size} airports matching criteria")

                    // Get runways for each airport
                    val labels = airportMarkers.map { airport ->
                        try {
                            val runways = database.runwayDao().getRunwaysByLocationSync(airport.id)
                            val runwayNames = runways.joinToString(", ") { runway -> runway.name }
                            AirportLabel(airport, runwayNames)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to get runways for ${airport.name}: ${e.message}")
                            AirportLabel(airport, "")
                        }
                    }

                    airports.clear()
                    airports.addAll(labels)
                    Log.d(TAG, "Loaded ${labels.size} airport labels (${airportMarkers.size} airports total)")
                    
                    // Trigger redraw on main thread
                    withContext(Dispatchers.Main) {
                        getMapView()?.invalidate()
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Data collection cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting airport data", e)
            }
        }
    }

    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        if (canvas == null || mapView == null || shadow) return
        if (!isEnabled()) return

        val airportCount = airports.size
        if (airportCount == 0) {
            return
        }

        // Compute device-based scale so small phones get slightly larger labels
        val rawScaleFactor = overlayScale * deviceScale()

        // Enforce a stronger minimum scale on phones so small devices show significantly larger text
        val metrics = context.resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        val minScaleForPhone = if (widthDp <= 360f) 1.6f else if (widthDp <= 420f) 1.4f else 1.05f
        val scaleFactor = maxOf(rawScaleFactor, minScaleForPhone)

        // Debug logging to help verify scale on device (remove after verification)
        Log.d(TAG, "scaleFactor=$scaleFactor raw=$rawScaleFactor overlayScale=$overlayScale widthDp=$widthDp")

        // Apply scale to paints dynamically (keeps initial paint configs but adjusts sizes for current device)
        labelTextPaint.textSize = getScaledTextSize(16f, context) * scaleFactor
        runwayTextPaint.textSize = getScaledTextSize(13f, context) * scaleFactor
        labelBorderPaint.strokeWidth = getScaledStrokeWidth(2f, context) * scaleFactor

        canvas.save()

        try {
            // Get current map bounds to only draw visible labels
            val boundingBox = mapView.boundingBox
            val minLat = boundingBox.latSouth
            val maxLat = boundingBox.latNorth
            val minLon = boundingBox.lonWest
            val maxLon = boundingBox.lonEast

            for (airportLabel in airports) {
                val location = airportLabel.location

                // Skip if outside visible bounds
                if (location.latitude < minLat || location.latitude > maxLat ||
                    location.longitude < minLon || location.longitude > maxLon) {
                    continue
                }

                val geoPoint = GeoPoint(location.latitude, location.longitude)
                val screenPoint = mapView.projection.toPixels(geoPoint, null)

                // Calculate label dimensions
                val nameText = location.name
                val runwayText = airportLabel.runwayNames

                labelTextPaint.getTextBounds(nameText, 0, nameText.length, textBounds)
                val nameWidth = textBounds.width().toFloat()
                val nameHeight = textBounds.height().toFloat()

                var maxWidth = nameWidth
                // vertical measurements include scaled inner spacing
                var totalHeight = nameHeight + (8f * scaleFactor)

                val hasRunways = runwayText.isNotEmpty()
                var runwayWidth = 0f
                var runwayHeight = 0f

                if (hasRunways) {
                    runwayTextPaint.getTextBounds(runwayText, 0, runwayText.length, textBounds)
                    runwayWidth = textBounds.width().toFloat()
                    runwayHeight = textBounds.height().toFloat()
                    maxWidth = maxOf(maxWidth, runwayWidth)
                    totalHeight += runwayHeight + (4f * scaleFactor)
                }

                // Position label below and to the right of the marker (with responsive offset)
                val labelX = screenPoint.x.toFloat() + (getScaledLabelOffset(20f, context) * scaleFactor)
                val labelY = screenPoint.y.toFloat() + (getScaledLabelOffset(10f, context) * scaleFactor)

                val padding = 8f * scaleFactor
                val labelWidth = maxWidth + (padding * 2)
                val labelHeight = totalHeight + (padding * 2)

                // Draw background with border
                val cornerRadius = 6f * scaleFactor
                val rectF = RectF(labelX, labelY, labelX + labelWidth, labelY + labelHeight)
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, labelBackgroundPaint)
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, labelBorderPaint)

                // Draw airport name (top line)
                var textY = labelY + padding + nameHeight
                canvas.drawText(nameText, labelX + padding, textY, labelTextPaint)

                // Draw runway names (bottom line) if available
                if (hasRunways) {
                    textY += runwayHeight + (4f * scaleFactor)
                    canvas.drawText(runwayText, labelX + padding, textY, runwayTextPaint)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing airport labels", e)
        } finally {
            canvas.restore()
        }
    }

    fun cleanup() {
        updateJob?.cancel()
        scope.cancel()
        airports.clear()
    }
}
