package com.example.checklist_interactive

import com.example.checklist_interactive.ui.maps.GeoUtils
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

/**
 * Unit tests for GeoUtils geographic calculations
 */
class GeoUtilsTest {

    companion object {
        // Tolerance for floating point comparisons
        private const val DISTANCE_TOLERANCE_KM = 1.0  // ±1 km
        private const val BEARING_TOLERANCE_DEG = 1.0  // ±1 degree

        // Well-known airports for testing
        // Frankfurt Airport (EDDF)
        private const val EDDF_LAT = 50.0379
        private const val EDDF_LON = 8.5622

        // Munich Airport (EDDM)
        private const val EDDM_LAT = 48.3538
        private const val EDDM_LON = 11.7861

        // London Heathrow (EGLL)
        private const val EGLL_LAT = 51.4700
        private const val EGLL_LON = -0.4543

        // New York JFK (KJFK)
        private const val KJFK_LAT = 40.6413
        private const val KJFK_LON = -73.7781
    }

    @Test
    fun testCalculateDistance_frankfurtToMunich() {
        // Known distance: approximately 299 km
        val distance = GeoUtils.calculateDistance(
            EDDF_LAT, EDDF_LON,
            EDDM_LAT, EDDM_LON
        )

        assertEquals(299.0, distance, DISTANCE_TOLERANCE_KM)
    }

    @Test
    fun testCalculateDistance_frankfurtToLondon() {
        // Known distance: approximately 637 km
        val distance = GeoUtils.calculateDistance(
            EDDF_LAT, EDDF_LON,
            EGLL_LAT, EGLL_LON
        )

        assertEquals(637.0, distance, DISTANCE_TOLERANCE_KM)
    }

    @Test
    fun testCalculateDistance_londonToNewYork() {
        // Known distance: approximately 5567 km
        val distance = GeoUtils.calculateDistance(
            EGLL_LAT, EGLL_LON,
            KJFK_LAT, KJFK_LON
        )

        assertEquals(5567.0, distance, 10.0) // Larger tolerance for long distances
    }

    @Test
    fun testCalculateDistance_sameLocation() {
        // Distance from a point to itself should be 0
        val distance = GeoUtils.calculateDistance(
            EDDF_LAT, EDDF_LON,
            EDDF_LAT, EDDF_LON
        )

        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun testCalculateDistance_shortDistance() {
        // Test very short distance (1 km apart approximately)
        val lat1 = 50.0
        val lon1 = 10.0
        val lat2 = 50.009 // Roughly 1 km north
        val lon2 = 10.0

        val distance = GeoUtils.calculateDistance(lat1, lon1, lat2, lon2)

        assertTrue("Distance should be approximately 1 km", abs(distance - 1.0) < 0.1)
    }

    @Test
    fun testCalculateDistanceNM_frankfurtToMunich() {
        // Known distance: approximately 161 nautical miles
        val distanceNm = GeoUtils.calculateDistanceNM(
            EDDF_LAT, EDDF_LON,
            EDDM_LAT, EDDM_LON
        )

        assertEquals(161.0, distanceNm, 1.0)
    }

    @Test
    fun testCalculateBearing_northToSouth() {
        // Bearing from north to south should be approximately 180°
        val bearing = GeoUtils.calculateBearing(
            50.0, 10.0,  // North
            49.0, 10.0   // South
        )

        assertEquals(180.0, bearing, BEARING_TOLERANCE_DEG)
    }

    @Test
    fun testCalculateBearing_southToNorth() {
        // Bearing from south to north should be approximately 0° (360°)
        val bearing = GeoUtils.calculateBearing(
            49.0, 10.0,  // South
            50.0, 10.0   // North
        )

        assertEquals(0.0, bearing, BEARING_TOLERANCE_DEG)
    }

    @Test
    fun testCalculateBearing_westToEast() {
        // Bearing from west to east should be approximately 90°
        val bearing = GeoUtils.calculateBearing(
            50.0, 9.0,   // West
            50.0, 10.0   // East
        )

        assertEquals(90.0, bearing, BEARING_TOLERANCE_DEG)
    }

    @Test
    fun testCalculateBearing_eastToWest() {
        // Bearing from east to west should be approximately 270°
        val bearing = GeoUtils.calculateBearing(
            50.0, 10.0,  // East
            50.0, 9.0    // West
        )

        assertEquals(270.0, bearing, BEARING_TOLERANCE_DEG)
    }

    @Test
    fun testCalculateBearing_frankfurtToMunich() {
        // Frankfurt to Munich is roughly southeast (around 115°)
        val bearing = GeoUtils.calculateBearing(
            EDDF_LAT, EDDF_LON,
            EDDM_LAT, EDDM_LON
        )

        // Verify it's in the southeast quadrant (90° to 180°)
        assertTrue("Bearing should be southeast", bearing > 90 && bearing < 180)
    }

    @Test
    fun testCalculateBearing_range0to360() {
        // Test that bearing is always in range [0, 360)
        val testCases = listOf(
            Triple(0.0, 0.0, 1.0, 1.0),      // Northeast
            Triple(0.0, 0.0, -1.0, 1.0),     // Southeast
            Triple(0.0, 0.0, -1.0, -1.0),    // Southwest
            Triple(0.0, 0.0, 1.0, -1.0)      // Northwest
        )

        testCases.forEach { (lat1, lon1, lat2, lon2) ->
            val bearing = GeoUtils.calculateBearing(lat1, lon1, lat2, lon2)
            assertTrue("Bearing must be >= 0", bearing >= 0.0)
            assertTrue("Bearing must be < 360", bearing < 360.0)
        }
    }

    @Test
    fun testFormatBearing() {
        assertEquals("000°", GeoUtils.formatBearing(0.0))
        assertEquals("090°", GeoUtils.formatBearing(90.0))
        assertEquals("180°", GeoUtils.formatBearing(180.0))
        assertEquals("270°", GeoUtils.formatBearing(270.0))
        assertEquals("359°", GeoUtils.formatBearing(359.4))
        assertEquals("045°", GeoUtils.formatBearing(45.2))
    }

    @Test
    fun testFormatDistance_kilometers() {
        assertEquals("10.5 km", GeoUtils.formatDistance(10.5, useNauticalMiles = false))
        assertEquals("0.0 km", GeoUtils.formatDistance(0.0, useNauticalMiles = false))
        assertEquals("1000.0 km", GeoUtils.formatDistance(1000.0, useNauticalMiles = false))
    }

    @Test
    fun testFormatDistance_nauticalMiles() {
        // 10 km = approximately 5.4 nautical miles
        val formatted = GeoUtils.formatDistance(10.0, useNauticalMiles = true)
        assertTrue("Should contain 'nm'", formatted.contains("nm"))
        assertTrue("Should be approximately 5.4", formatted.contains("5.4"))
    }

    @Test
    fun testCalculateDestination_north() {
        // Move 100 km north from Frankfurt
        val (destLat, destLon) = GeoUtils.calculateDestination(
            EDDF_LAT, EDDF_LON,
            distanceKm = 100.0,
            bearingDeg = 0.0
        )

        // Latitude should increase, longitude should stay roughly same
        assertTrue("Latitude should increase", destLat > EDDF_LAT)
        assertEquals(EDDF_LON, destLon, 0.1)
    }

    @Test
    fun testCalculateDestination_east() {
        // Move 100 km east from Frankfurt
        val (destLat, destLon) = GeoUtils.calculateDestination(
            EDDF_LAT, EDDF_LON,
            distanceKm = 100.0,
            bearingDeg = 90.0
        )

        // Longitude should increase, latitude should stay roughly same
        assertTrue("Longitude should increase", destLon > EDDF_LON)
        assertEquals(EDDF_LAT, destLat, 0.1)
    }

    @Test
    fun testCalculateDestination_roundTrip() {
        // Move 100 km in one direction, then 100 km back
        val (lat1, lon1) = GeoUtils.calculateDestination(
            EDDF_LAT, EDDF_LON,
            distanceKm = 100.0,
            bearingDeg = 45.0
        )

        val (lat2, lon2) = GeoUtils.calculateDestination(
            lat1, lon1,
            distanceKm = 100.0,
            bearingDeg = 225.0  // Opposite direction (45 + 180)
        )

        // Should be back near original position
        assertEquals(EDDF_LAT, lat2, 0.01)
        assertEquals(EDDF_LON, lon2, 0.01)
    }

    @Test
    fun testCalculateDestination_consistency() {
        // Calculate destination, then verify distance and bearing
        val distanceKm = 50.0
        val bearingDeg = 120.0

        val (destLat, destLon) = GeoUtils.calculateDestination(
            EDDF_LAT, EDDF_LON,
            distanceKm,
            bearingDeg
        )

        // Verify distance
        val calculatedDistance = GeoUtils.calculateDistance(
            EDDF_LAT, EDDF_LON,
            destLat, destLon
        )
        assertEquals(distanceKm, calculatedDistance, 0.1)

        // Verify bearing
        val calculatedBearing = GeoUtils.calculateBearing(
            EDDF_LAT, EDDF_LON,
            destLat, destLon
        )
        assertEquals(bearingDeg, calculatedBearing, 1.0)
    }

    @Test
    fun testSymmetry_distanceIsCommutative() {
        // Distance from A to B should equal distance from B to A
        val d1 = GeoUtils.calculateDistance(EDDF_LAT, EDDF_LON, EDDM_LAT, EDDM_LON)
        val d2 = GeoUtils.calculateDistance(EDDM_LAT, EDDM_LON, EDDF_LAT, EDDF_LON)

        assertEquals(d1, d2, 0.001)
    }

    @Test
    fun testBearingReciprocalProperty() {
        // Bearing from A to B + 180° should approximately equal bearing from B to A
        val bearingAtoB = GeoUtils.calculateBearing(EDDF_LAT, EDDF_LON, EDDM_LAT, EDDM_LON)
        val bearingBtoA = GeoUtils.calculateBearing(EDDM_LAT, EDDM_LON, EDDF_LAT, EDDF_LON)

        val reciprocal = (bearingAtoB + 180.0) % 360.0

        // Allow some tolerance for spherical geometry effects
        val diff = abs(reciprocal - bearingBtoA)
        val normalizedDiff = if (diff > 180) 360 - diff else diff

        assertTrue("Bearings should be reciprocal", normalizedDiff < 5.0)
    }
}
