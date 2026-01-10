package com.example.checklist_interactive.ui.maps

import android.content.Context
import android.graphics.Paint
import android.util.Log
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Overlay
import org.json.JSONArray
import org.json.JSONObject

/**
 * MapBorders - Country border overlays with support for different historical epochs
 * 
 * Supported epochs:
 * - MODERN: Current international borders
 * - COLD_WAR: Cold War era borders (1947-1991) - includes divided Germany, Yugoslavia, USSR, etc.
 * - WW2: World War II borders (1939-1945)
 * - WW1: World War I borders (1914-1918)
 * - PRE_WW1: Pre-WWI borders (1900)
 */

/**
 * Historical epoch for border display
 */
enum class BorderEpoch(val displayName: String, val fileName: String) {
    MODERN("Modern (2020+)", "borders/ne_110m_admin_0_countries.geojson"),
    COLD_WAR("Cold War (1947-1991)", "borders/borders_cold_war.geojson"),
    WW2("World War II (1939-1945)", "borders/borders_ww2.geojson"),
    WW1("World War I (1914-1918)", "borders/borders_ww1.geojson"),
    PRE_WW1("Pre-WWI (1900)", "borders/borders_pre_ww1.geojson")
}

/**
 * Country Borders overlay: displays political/administrative boundaries from Natural Earth data
 * Supports different historical epochs with viewport filtering for performance
 */
class CountryBordersOverlay(
    private val context: Context,
    private var epoch: BorderEpoch = BorderEpoch.MODERN
) : Overlay() {
    
    private val borderPaint = Paint().apply {
        color = android.graphics.Color.argb(200, 255, 100, 0) // Orange with transparency
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    companion object {
        private const val TAG = "CountryBordersOverlay"
        
        // Cache for each epoch's border data
        private val borderDataCache = mutableMapOf<BorderEpoch, List<CountryBorder>?>()
        private val loadingEpochs = mutableSetOf<BorderEpoch>()

        data class CountryBorder(
            val name: String,
            val boundaries: List<List<GeoPoint>>,
            val minLat: Double,
            val maxLat: Double,
            val minLon: Double,
            val maxLon: Double
        )

        /**
         * Load border data for a specific epoch from assets
         */
        fun loadBorderData(context: Context, epoch: BorderEpoch) {
            if (borderDataCache.containsKey(epoch) || loadingEpochs.contains(epoch)) return
            
            loadingEpochs.add(epoch)

            try {
                val inputStream = try {
                    context.assets.open(epoch.fileName)
                } catch (e: Exception) {
                    Log.w(TAG, "Border file ${epoch.fileName} not found, falling back to modern borders")
                    // Fallback to modern borders if epoch file doesn't exist
                    context.assets.open(BorderEpoch.MODERN.fileName)
                }
                
                val json = inputStream.bufferedReader().use { it.readText() }
                val rootObject = JSONObject(json)
                val features = rootObject.getJSONArray("features")

                val borders = mutableListOf<CountryBorder>()

                for (i in 0 until features.length()) {
                    try {
                        val feature = features.getJSONObject(i)
                        val properties = feature.optJSONObject("properties")
                        val name = properties?.optString("NAME", "Unknown") ?: "Unknown"
                        val geometry = feature.getJSONObject("geometry")
                        val type = geometry.getString("type")
                        val coordinates = geometry.getJSONArray("coordinates")

                        val boundaries = mutableListOf<List<GeoPoint>>()
                        var minLat = Double.MAX_VALUE
                        var maxLat = -Double.MAX_VALUE
                        var minLon = Double.MAX_VALUE
                        var maxLon = -Double.MAX_VALUE

                        when (type) {
                            "Polygon" -> {
                                val boundary = parsePolygon(coordinates)
                                if (boundary.isNotEmpty()) {
                                    boundaries.add(boundary)
                                    boundary.forEach { point ->
                                        minLat = minOf(minLat, point.latitude)
                                        maxLat = maxOf(maxLat, point.latitude)
                                        minLon = minOf(minLon, point.longitude)
                                        maxLon = maxOf(maxLon, point.longitude)
                                    }
                                }
                            }
                            "MultiPolygon" -> {
                                for (j in 0 until coordinates.length()) {
                                    val polygon = coordinates.getJSONArray(j)
                                    val boundary = parsePolygon(polygon)
                                    if (boundary.isNotEmpty()) {
                                        boundaries.add(boundary)
                                        boundary.forEach { point ->
                                            minLat = minOf(minLat, point.latitude)
                                            maxLat = maxOf(maxLat, point.latitude)
                                            minLon = minOf(minLon, point.longitude)
                                            maxLon = maxOf(maxLon, point.longitude)
                                        }
                                    }
                                }
                            }
                        }

                        if (boundaries.isNotEmpty()) {
                            borders.add(CountryBorder(name, boundaries, minLat, maxLat, minLon, maxLon))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse feature at index $i for epoch ${epoch.displayName}", e)
                    }
                }

                borderDataCache[epoch] = borders
                Log.d(TAG, "Loaded ${borders.size} countries for epoch ${epoch.displayName}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load border data for epoch ${epoch.displayName}", e)
                borderDataCache[epoch] = null
            } finally {
                loadingEpochs.remove(epoch)
            }
        }

        private fun parsePolygon(coordinates: JSONArray): List<GeoPoint> {
            // First ring is the outer boundary
            if (coordinates.length() == 0) return emptyList()
            val ring = coordinates.getJSONArray(0)
            val points = mutableListOf<GeoPoint>()

            for (i in 0 until ring.length()) {
                try {
                    val coord = ring.getJSONArray(i)
                    if (coord.length() >= 2) {
                        val lon = coord.getDouble(0)
                        val lat = coord.getDouble(1)
                        points.add(GeoPoint(lat, lon))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse coordinate at index $i", e)
                }
            }
            return points
        }

        /**
         * Clear all cached border data (useful for memory management)
         */
        fun clearCache() {
            borderDataCache.clear()
        }

        /**
         * Clear cache for a specific epoch
         */
        fun clearCache(epoch: BorderEpoch) {
            borderDataCache.remove(epoch)
        }

        /**
         * Check which border epochs have available GeoJSON files
         */
        fun getAvailableEpochs(context: Context): List<BorderEpoch> {
            return BorderEpoch.values().filter { epoch ->
                try {
                    context.assets.open(epoch.fileName).use { true }
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    /**
     * Change the displayed epoch and trigger reload
     */
    fun setEpoch(newEpoch: BorderEpoch) {
        if (epoch != newEpoch) {
            epoch = newEpoch
            loadBorderData(context, epoch)
        }
    }

    /**
     * Get the current epoch
     */
    fun getEpoch(): BorderEpoch = epoch

    override fun draw(canvas: android.graphics.Canvas?, mapView: org.osmdroid.views.MapView?, shadow: Boolean) {
        if (shadow || canvas == null || mapView == null) return

        // Load data if not already loaded
        if (!borderDataCache.containsKey(epoch) && !loadingEpochs.contains(epoch)) {
            loadBorderData(context, epoch)
            return // Wait for next frame
        }

        val data = borderDataCache[epoch] ?: return
        val projection = mapView.projection
        val boundingBox = projection.boundingBox

        // Get canvas dimensions for clipping
        val canvasWidth = canvas.width
        val canvasHeight = canvas.height
        val margin = 1000f // Pixels outside viewport before clipping

        // Filter and draw only visible country borders
        for (country in data) {
            // Quick bounding box check for performance
            if (country.maxLat < boundingBox.latSouth ||
                country.minLat > boundingBox.latNorth ||
                country.maxLon < boundingBox.lonWest ||
                country.minLon > boundingBox.lonEast) {
                continue
            }

            // Draw all boundaries for this country
            for (boundary in country.boundaries) {
                if (boundary.isEmpty()) continue

                val path = android.graphics.Path()
                var firstPoint = true
                var anyPointVisible = false

                for (point in boundary) {
                    val screenPoint = android.graphics.Point()
                    projection.toPixels(point, screenPoint)

                    // Check if point is within extended viewport (with margin)
                    if (screenPoint.x >= -margin && screenPoint.x <= canvasWidth + margin &&
                        screenPoint.y >= -margin && screenPoint.y <= canvasHeight + margin) {
                        anyPointVisible = true
                    }

                    if (firstPoint) {
                        path.moveTo(screenPoint.x.toFloat(), screenPoint.y.toFloat())
                        firstPoint = false
                    } else {
                        path.lineTo(screenPoint.x.toFloat(), screenPoint.y.toFloat())
                    }
                }

                // Only draw if at least one point is visible
                if (anyPointVisible) {
                    canvas.drawPath(path, borderPaint)
                }
            }
        }
    }

    /**
     * Preload border data in the background
     */
    fun preload() {
        loadBorderData(context, epoch)
    }
}
