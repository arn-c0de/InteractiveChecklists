package com.example.checklist_interactive.ui.maps

import com.example.checklist_interactive.data.tactical.LocationEntity
import com.example.checklist_interactive.data.tactical.RunwayEntity

/**
 * Data class representing an airport with calculated distance and bearing
 * from the player's current position
 */
data class AirportWithDistance(
    val location: LocationEntity,
    val distanceKm: Double,
    val distanceNm: Double,
    val bearing: Double,
    val runways: List<RunwayEntity> = emptyList()
) {
    /**
     * Get primary runway info (longest runway)
     */
    val primaryRunway: RunwayEntity? = runways.maxByOrNull { it.lengthM ?: it.lengthFt ?: 0 }

    /**
     * Get formatted distance string based on user preference
     */
    fun getFormattedDistance(useNauticalMiles: Boolean): String {
        return if (useNauticalMiles) {
            String.format("%.1f nm", distanceNm)
        } else {
            String.format("%.1f km", distanceKm)
        }
    }

    /**
     * Get formatted bearing string
     */
    fun getFormattedBearing(): String {
        return String.format("%03.0f°", bearing)
    }

    /**
     * Get runway summary string (e.g., "2 runways, longest: 09/27 3000m")
     */
    fun getRunwaySummary(): String? {
        if (runways.isEmpty()) return null

        val count = runways.size
        val longest = primaryRunway ?: return "$count runway${if (count > 1) "s" else ""}"
        val length = longest.lengthM ?: longest.lengthFt ?: 0

        return "$count runway${if (count > 1) "s" else ""}, longest: ${longest.name} ${length}m"
    }
}

/**
 * Calculate nearest airports from a list of location markers
 * Filters for airport markers and calculates distance/bearing from player position
 */
object NearestAirportsCalculator {

    /**
     * Calculate nearest airports from current position
     *
     * @param allLocations All location markers from database
     * @param playerLat Player's current latitude
     * @param playerLon Player's current longitude
     * @param maxResults Maximum number of results to return
     * @param maxDistanceKm Maximum distance in kilometers (null = no limit)
     * @param runwaysMap Map of locationId to runways list
     * @return List of airports sorted by distance (nearest first)
     */
    fun calculateNearestAirports(
        allLocations: List<LocationEntity>,
        playerLat: Double,
        playerLon: Double,
        maxResults: Int = 10,
        maxDistanceKm: Double? = null,
        runwaysMap: Map<Int, List<RunwayEntity>> = emptyMap()
    ): List<AirportWithDistance> {
        // Filter for airport markers only
        val airports = allLocations.filter { it.markerType == "airport" }

        // Calculate distance and bearing for each airport
        val airportsWithDistance = airports.mapNotNull { airport ->
            try {
                val distanceKm = GeoUtils.calculateDistance(
                    playerLat, playerLon,
                    airport.latitude, airport.longitude
                )

                // Apply distance filter if specified
                if (maxDistanceKm != null && distanceKm > maxDistanceKm) {
                    return@mapNotNull null
                }

                val distanceNm = distanceKm * 0.539957 // km to nautical miles
                val bearing = GeoUtils.calculateBearing(
                    playerLat, playerLon,
                    airport.latitude, airport.longitude
                )

                val runways = runwaysMap[airport.id] ?: emptyList()

                AirportWithDistance(
                    location = airport,
                    distanceKm = distanceKm,
                    distanceNm = distanceNm,
                    bearing = bearing,
                    runways = runways
                )
            } catch (e: Exception) {
                android.util.Log.e("NearestAirports", "Error calculating distance for ${airport.name}", e)
                null
            }
        }

        // Sort by distance (nearest first) and limit results
        return airportsWithDistance
            .sortedBy { it.distanceKm }
            .take(maxResults)
    }
}
