package com.example.checklist_interactive.ui.maps

import kotlin.math.*

/**
 * Geographic utility functions for distance and bearing calculations
 * Uses the Haversine formula for great circle distances
 */
object GeoUtils {
    private const val EARTH_RADIUS_KM = 6371.0
    private const val EARTH_RADIUS_NM = 3440.065 // nautical miles

    /**
     * Calculate great circle distance between two points using Haversine formula
     *
     * @param lat1 Latitude of first point (degrees)
     * @param lon1 Longitude of first point (degrees)
     * @param lat2 Latitude of second point (degrees)
     * @param lon2 Longitude of second point (degrees)
     * @return Distance in kilometers
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)

        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        val a = sin(dLat / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2).pow(2)
        val c = 2 * asin(sqrt(a))

        return EARTH_RADIUS_KM * c
    }

    /**
     * Calculate great circle distance in nautical miles
     *
     * @param lat1 Latitude of first point (degrees)
     * @param lon1 Longitude of first point (degrees)
     * @param lat2 Latitude of second point (degrees)
     * @param lon2 Longitude of second point (degrees)
     * @return Distance in nautical miles
     */
    fun calculateDistanceNM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return calculateDistance(lat1, lon1, lat2, lon2) * 0.539957 // km to nm conversion
    }

    /**
     * Calculate initial bearing from point 1 to point 2
     *
     * @param lat1 Latitude of first point (degrees)
     * @param lon1 Longitude of first point (degrees)
     * @param lat2 Latitude of second point (degrees)
     * @param lon2 Longitude of second point (degrees)
     * @return Bearing in degrees (0-360, where 0 is north, 90 is east, etc.)
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)

        val dLon = lon2Rad - lon1Rad

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

        val bearingRad = atan2(y, x)
        val bearingDeg = Math.toDegrees(bearingRad)

        // Normalize to 0-360
        return (bearingDeg + 360) % 360
    }

    /**
     * Format bearing as 3-digit string with degree symbol (e.g., "045°", "180°", "270°")
     */
    fun formatBearing(bearing: Double): String {
        return String.format("%03.0f°", bearing)
    }

    /**
     * Format distance with appropriate unit and precision
     *
     * @param distanceKm Distance in kilometers
     * @param useNauticalMiles If true, convert to nautical miles
     * @return Formatted distance string (e.g., "12.5 km", "6.8 nm")
     */
    fun formatDistance(distanceKm: Double, useNauticalMiles: Boolean = false): String {
        return if (useNauticalMiles) {
            val distanceNm = distanceKm * 0.539957
            String.format("%.1f nm", distanceNm)
        } else {
            String.format("%.1f km", distanceKm)
        }
    }

    /**
     * Calculate destination point given distance and bearing from start point
     * Uses the direct geodetic problem formula
     *
     * @param lat Starting latitude (degrees)
     * @param lon Starting longitude (degrees)
     * @param distanceKm Distance in kilometers
     * @param bearingDeg Bearing in degrees
     * @return Pair of (latitude, longitude) of destination point
     */
    fun calculateDestination(lat: Double, lon: Double, distanceKm: Double, bearingDeg: Double): Pair<Double, Double> {
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val bearingRad = Math.toRadians(bearingDeg)
        val angularDistance = distanceKm / EARTH_RADIUS_KM

        val lat2Rad = asin(
            sin(latRad) * cos(angularDistance) +
            cos(latRad) * sin(angularDistance) * cos(bearingRad)
        )

        val lon2Rad = lonRad + atan2(
            sin(bearingRad) * sin(angularDistance) * cos(latRad),
            cos(angularDistance) - sin(latRad) * sin(lat2Rad)
        )

        return Pair(Math.toDegrees(lat2Rad), Math.toDegrees(lon2Rad))
    }
}
