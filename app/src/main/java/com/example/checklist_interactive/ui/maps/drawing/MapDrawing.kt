package com.example.checklist_interactive.ui.maps.drawing

import androidx.compose.ui.graphics.Color
import org.osmdroid.util.GeoPoint

/**
 * Map drawing brush types
 */
enum class MapBrushType {
    Pen,      // Regular pen/pencil
    Marker,   // Highlighter/marker (semi-transparent, thicker)
    Special   // Special brush with custom effects
}

/**
 * In-memory representation of a map drawing stroke
 * Points are stored as GeoPoints (lat/lon) for map rendering
 */
data class MapDrawingStroke(
    val id: Int = 0,
    val geoPoints: List<GeoPoint>,
    val color: Color,
    val strokeWidth: Float,
    val brushType: MapBrushType,
    val isHighlight: Boolean = false,
    val mapRegion: String? = null
) {
    /**
     * Convert to database entity format
     */
    fun toEntity(createdAt: String, modifiedAt: String): com.example.checklist_interactive.data.tactical.MapDrawingEntity {
        // Convert GeoPoints to JSON array of [lat, lon] pairs
        val pointsJson = buildString {
            append("[")
            geoPoints.forEachIndexed { index, point ->
                if (index > 0) append(",")
                // Use proper JSON number format with controlled precision
                append("[%.8f,%.8f]".format(point.latitude, point.longitude))
            }
            append("]")
        }
        
        return com.example.checklist_interactive.data.tactical.MapDrawingEntity(
            id = if (id == 0) 0 else id,  // Let DB auto-generate if 0
            mapRegion = mapRegion,
            color = color.value.toLong(),
            strokeWidth = strokeWidth,
            brushType = brushType.name.lowercase(),
            points = pointsJson,
            isHighlight = if (isHighlight) 1 else 0,
            createdAt = createdAt,
            modifiedAt = modifiedAt
        )
    }
    
    companion object {
        /**
         * Create from database entity
         */
        fun fromEntity(entity: com.example.checklist_interactive.data.tactical.MapDrawingEntity): MapDrawingStroke {
            // Parse JSON array of [lat, lon] pairs
            val geoPoints = mutableListOf<GeoPoint>()
            try {
                val json = org.json.JSONArray(entity.points)
                for (i in 0 until json.length()) {
                    val point = json.getJSONArray(i)
                    val lat = point.getDouble(0)
                    val lon = point.getDouble(1)
                    geoPoints.add(GeoPoint(lat, lon))
                }
            } catch (e: Exception) {
                android.util.Log.e("MapDrawingStroke", "Failed to parse points JSON", e)
            }
            
            val brushType = when (entity.brushType.lowercase()) {
                "marker" -> MapBrushType.Marker
                "special" -> MapBrushType.Special
                else -> MapBrushType.Pen
            }
            
            return MapDrawingStroke(
                id = entity.id,
                geoPoints = geoPoints,
                color = Color(entity.color.toULong()),
                strokeWidth = entity.strokeWidth,
                brushType = brushType,
                isHighlight = entity.isHighlight == 1,
                mapRegion = entity.mapRegion
            )
        }
    }
}

/**
 * Drawing tool state for the map
 */
data class MapDrawingState(
    val isDrawingMode: Boolean = false,
    val brushType: MapBrushType = MapBrushType.Pen,
    val selectedColor: Color = Color.Red,
    val strokeWidth: Float = 4f,
    val isEraseMode: Boolean = false,
    val eraseRadius: Float = 50f
)
