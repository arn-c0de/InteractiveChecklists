package com.example.checklist_interactive.ui.maps

import android.graphics.Color

/**
 * AA (Anti-Aircraft) System range specification
 * Contains detection and engagement ranges for various AA systems
 */
data class AASystemSpec(
    val name: String,                    // System name (e.g., "SA-11 Buk", "Patriot")
    val displayName: String,             // Display name for UI
    val detectionRangeKm: Double,        // Detection range in kilometers
    val engagementRangeKm: Double,       // Max engagement range in kilometers
    val minEngagementRangeKm: Double = 0.0, // Min engagement range (dead zone)
    val maxAltitudeM: Double,            // Max engagement altitude in meters
    val minAltitudeM: Double = 0.0,      // Min engagement altitude in meters
    val searchRadarRangeKm: Double? = null, // Search radar range (if different from detection)
    val trackRadarRangeKm: Double? = null,  // Track radar range
    val category: String,                // "Long-range", "Medium-range", "Short-range", "MANPADS"
    val notes: String = ""               // Additional notes
)

/**
 * AA Range Rings configuration for map display
 */
data class AARangeRing(
    val radiusKm: Double,
    val color: Int,
    val label: String,
    val strokeWidth: Float = 3f,
    val isDashed: Boolean = false
)

/**
 * Complete AA system database with all major AA systems in DCS World
 */
object AARangeDatabase {

    // Main database of AA systems
    private val systems = listOf(
        // === RUSSIAN / SOVIET SYSTEMS ===

        // Long-range SAM systems
        AASystemSpec(
            name = "SA-10",
            displayName = "SA-10 Grumble (S-300PS)",
            detectionRangeKm = 150.0,
            engagementRangeKm = 75.0,
            minEngagementRangeKm = 5.0,
            maxAltitudeM = 25000.0,
            minAltitudeM = 25.0,
            searchRadarRangeKm = 150.0,
            trackRadarRangeKm = 100.0,
            category = "Long-range",
            notes = "S-300PS - Lethal long-range system"
        ),
        AASystemSpec(
            name = "SA-20",
            displayName = "SA-20 Gargoyle (S-300PMU)",
            detectionRangeKm = 200.0,
            engagementRangeKm = 90.0,
            minEngagementRangeKm = 5.0,
            maxAltitudeM = 27000.0,
            minAltitudeM = 10.0,
            searchRadarRangeKm = 200.0,
            trackRadarRangeKm = 120.0,
            category = "Long-range",
            notes = "S-300PMU - Advanced long-range SAM"
        ),
        AASystemSpec(
            name = "SA-23",
            displayName = "SA-23 Gladiator (S-300VM)",
            detectionRangeKm = 250.0,
            engagementRangeKm = 200.0,
            minEngagementRangeKm = 6.0,
            maxAltitudeM = 30000.0,
            minAltitudeM = 25.0,
            searchRadarRangeKm = 250.0,
            category = "Long-range",
            notes = "S-300VM - Anti-ballistic/ABM capable"
        ),

        // Medium-range SAM systems
        AASystemSpec(
            name = "SA-11",
            displayName = "SA-11 Gadfly (Buk-M1)",
            detectionRangeKm = 85.0,
            engagementRangeKm = 35.0,
            minEngagementRangeKm = 3.0,
            maxAltitudeM = 22000.0,
            minAltitudeM = 15.0,
            searchRadarRangeKm = 85.0,
            trackRadarRangeKm = 42.0,
            category = "Medium-range",
            notes = "Buk-M1 - Mobile medium-range SAM (9M38 missiles)"
        ),
        AASystemSpec(
            name = "SA-17",
            displayName = "SA-17 Grizzly (Buk-M2)",
            detectionRangeKm = 120.0,
            engagementRangeKm = 45.0,
            minEngagementRangeKm = 3.0,
            maxAltitudeM = 25000.0,
            minAltitudeM = 10.0,
            searchRadarRangeKm = 120.0,
            trackRadarRangeKm = 50.0,
            category = "Medium-range",
            notes = "Buk-M2 - Upgraded Buk system"
        ),
        AASystemSpec(
            name = "SA-6",
            displayName = "SA-6 Gainful (Kub)",
            detectionRangeKm = 75.0,
            engagementRangeKm = 24.0,
            minEngagementRangeKm = 4.0,
            maxAltitudeM = 12000.0,
            minAltitudeM = 100.0,
            searchRadarRangeKm = 75.0,
            category = "Medium-range",
            notes = "Kub - Older medium-range SAM"
        ),

        // Short-range SAM systems
        AASystemSpec(
            name = "SA-15",
            displayName = "SA-15 Gauntlet (Tor-M1)",
            detectionRangeKm = 25.0,
            engagementRangeKm = 12.0,
            minEngagementRangeKm = 1.5,
            maxAltitudeM = 6000.0,
            minAltitudeM = 10.0,
            searchRadarRangeKm = 25.0,
            trackRadarRangeKm = 18.0,
            category = "Short-range",
            notes = "Tor-M1 - Point defense SAM (9M330 missiles)"
        ),
        AASystemSpec(
            name = "SA-19",
            displayName = "SA-19 Grison (Tunguska)",
            detectionRangeKm = 18.0,
            engagementRangeKm = 8.0,
            minEngagementRangeKm = 0.2,
            maxAltitudeM = 3500.0,
            minAltitudeM = 0.0,
            searchRadarRangeKm = 18.0,
            category = "Short-range",
            notes = "Tunguska - Combined gun/missile SPAAG"
        ),
        AASystemSpec(
            name = "SA-8",
            displayName = "SA-8 Gecko (Osa)",
            detectionRangeKm = 30.0,
            engagementRangeKm = 10.0,
            minEngagementRangeKm = 1.5,
            maxAltitudeM = 5000.0,
            minAltitudeM = 25.0,
            searchRadarRangeKm = 30.0,
            category = "Short-range",
            notes = "Osa - Mobile short-range SAM"
        ),
        AASystemSpec(
            name = "SA-13",
            displayName = "SA-13 Gopher (Strela-10)",
            detectionRangeKm = 10.0,
            engagementRangeKm = 5.0,
            minEngagementRangeKm = 0.8,
            maxAltitudeM = 3500.0,
            minAltitudeM = 25.0,
            searchRadarRangeKm = 10.0,
            category = "Short-range",
            notes = "Strela-10 - IR-guided mobile SAM"
        ),

        // MANPADS
        AASystemSpec(
            name = "SA-9",
            displayName = "SA-9 Gaskin (Strela-1)",
            detectionRangeKm = 8.0,
            engagementRangeKm = 4.2,
            minEngagementRangeKm = 0.8,
            maxAltitudeM = 3500.0,
            minAltitudeM = 50.0,
            category = "MANPADS",
            notes = "Strela-1 - Mobile IR SAM (9M31 missiles)"
        ),
        AASystemSpec(
            name = "SA-18",
            displayName = "SA-18 Grouse (Igla)",
            detectionRangeKm = 5.2,
            engagementRangeKm = 5.2,
            minEngagementRangeKm = 0.5,
            maxAltitudeM = 3500.0,
            minAltitudeM = 10.0,
            category = "MANPADS",
            notes = "Igla - Man-portable IR SAM"
        ),
        AASystemSpec(
            name = "SA-7",
            displayName = "SA-7 Grail (Strela-2)",
            detectionRangeKm = 4.2,
            engagementRangeKm = 4.2,
            minEngagementRangeKm = 0.8,
            maxAltitudeM = 2300.0,
            minAltitudeM = 50.0,
            category = "MANPADS",
            notes = "Strela-2 - First-gen MANPADS"
        ),

        // === NATO / WESTERN SYSTEMS ===

        // Long-range SAM
        AASystemSpec(
            name = "MIM-104",
            displayName = "MIM-104 Patriot",
            detectionRangeKm = 170.0,
            engagementRangeKm = 100.0,
            minEngagementRangeKm = 3.0,
            maxAltitudeM = 24000.0,
            minAltitudeM = 60.0,
            searchRadarRangeKm = 170.0,
            trackRadarRangeKm = 100.0,
            category = "Long-range",
            notes = "Patriot - Advanced long-range SAM system"
        ),

        // Medium-range SAM
        AASystemSpec(
            name = "NASAMS",
            displayName = "NASAMS (AIM-120)",
            detectionRangeKm = 120.0,
            engagementRangeKm = 25.0,
            minEngagementRangeKm = 1.0,
            maxAltitudeM = 20000.0,
            minAltitudeM = 30.0,
            searchRadarRangeKm = 120.0,
            category = "Medium-range",
            notes = "NASAMS - Uses AIM-120 AMRAAM missiles"
        ),
        AASystemSpec(
            name = "MIM-23",
            displayName = "MIM-23 HAWK",
            detectionRangeKm = 100.0,
            engagementRangeKm = 40.0,
            minEngagementRangeKm = 2.5,
            maxAltitudeM = 18000.0,
            minAltitudeM = 60.0,
            searchRadarRangeKm = 100.0,
            category = "Medium-range",
            notes = "HAWK - Legacy medium-range SAM"
        ),

        // Short-range SAM
        AASystemSpec(
            name = "Roland",
            displayName = "Roland SAM",
            detectionRangeKm = 18.0,
            engagementRangeKm = 6.3,
            minEngagementRangeKm = 0.5,
            maxAltitudeM = 5500.0,
            minAltitudeM = 15.0,
            searchRadarRangeKm = 18.0,
            category = "Short-range",
            notes = "Roland - Franco-German short-range SAM"
        ),
        AASystemSpec(
            name = "Rapier",
            displayName = "Rapier FSC",
            detectionRangeKm = 15.0,
            engagementRangeKm = 6.8,
            minEngagementRangeKm = 0.5,
            maxAltitudeM = 3000.0,
            minAltitudeM = 10.0,
            searchRadarRangeKm = 15.0,
            category = "Short-range",
            notes = "Rapier - British short-range SAM"
        ),
        AASystemSpec(
            name = "Avenger",
            displayName = "Avenger (Stinger)",
            detectionRangeKm = 10.0,
            engagementRangeKm = 4.8,
            minEngagementRangeKm = 0.2,
            maxAltitudeM = 3800.0,
            minAltitudeM = 10.0,
            category = "Short-range",
            notes = "Avenger - Mobile Stinger SAM platform"
        ),

        // MANPADS
        AASystemSpec(
            name = "FIM-92",
            displayName = "FIM-92 Stinger",
            detectionRangeKm = 4.8,
            engagementRangeKm = 4.8,
            minEngagementRangeKm = 0.2,
            maxAltitudeM = 3800.0,
            minAltitudeM = 10.0,
            category = "MANPADS",
            notes = "Stinger - US MANPADS"
        ),
        AASystemSpec(
            name = "Mistral",
            displayName = "Mistral MANPADS",
            detectionRangeKm = 6.0,
            engagementRangeKm = 6.0,
            minEngagementRangeKm = 0.5,
            maxAltitudeM = 3000.0,
            minAltitudeM = 10.0,
            category = "MANPADS",
            notes = "Mistral - French MANPADS"
        ),

        // === AAA (Anti-Aircraft Artillery) ===
        AASystemSpec(
            name = "ZSU-23-4",
            displayName = "ZSU-23-4 Shilka",
            detectionRangeKm = 20.0,
            engagementRangeKm = 2.5,
            minEngagementRangeKm = 0.0,
            maxAltitudeM = 1500.0,
            minAltitudeM = 0.0,
            searchRadarRangeKm = 20.0,
            category = "AAA",
            notes = "Shilka - 4x 23mm radar-guided SPAAG"
        ),
        AASystemSpec(
            name = "2S6",
            displayName = "2S6 Tunguska (Gun)",
            detectionRangeKm = 18.0,
            engagementRangeKm = 4.0,
            minEngagementRangeKm = 0.0,
            maxAltitudeM = 3500.0,
            minAltitudeM = 0.0,
            searchRadarRangeKm = 18.0,
            category = "AAA",
            notes = "Tunguska 2x 30mm guns (shorter range than missiles)"
        ),
        AASystemSpec(
            name = "Gepard",
            displayName = "Gepard SPAAG",
            detectionRangeKm = 15.0,
            engagementRangeKm = 3.5,
            minEngagementRangeKm = 0.0,
            maxAltitudeM = 3500.0,
            minAltitudeM = 0.0,
            searchRadarRangeKm = 15.0,
            category = "AAA",
            notes = "Gepard - 2x 35mm radar-guided SPAAG"
        ),
        AASystemSpec(
            name = "Vulcan",
            displayName = "M163 Vulcan",
            detectionRangeKm = 5.0,
            engagementRangeKm = 1.2,
            minEngagementRangeKm = 0.0,
            maxAltitudeM = 1200.0,
            minAltitudeM = 0.0,
            category = "AAA",
            notes = "Vulcan - 20mm Gatling gun SPAAG"
        ),
        AASystemSpec(
            name = "ZU-23",
            displayName = "ZU-23 (Towed)",
            detectionRangeKm = 2.5,
            engagementRangeKm = 2.5,
            minEngagementRangeKm = 0.0,
            maxAltitudeM = 1500.0,
            minAltitudeM = 0.0,
            category = "AAA",
            notes = "ZU-23 - Towed 23mm AAA"
        )
    )

    /**
     * Get system specification by name (case-insensitive partial match)
     */
    fun getSystemByName(name: String): AASystemSpec? {
        val normalized = name.lowercase()
        // Try exact match first
        systems.firstOrNull { it.name.lowercase() == normalized }?.let { return it }
        // Try display name match
        systems.firstOrNull { it.displayName.lowercase() == normalized }?.let { return it }
        // Try partial match
        return systems.firstOrNull {
            it.name.lowercase().contains(normalized) ||
            it.displayName.lowercase().contains(normalized) ||
            normalized.contains(it.name.lowercase())
        }
    }

    /**
     * Get system by DCS unit type name (handles various formats)
     */
    fun getSystemByUnitType(unitType: String): AASystemSpec? {
        val normalized = unitType.lowercase()

        // Direct mappings for common DCS unit types
        val mappings = mapOf(
            "s-300ps" to "SA-10",
            "s-300pmu" to "SA-20",
            "sa-10" to "SA-10",
            "sa-20" to "SA-20",
            "buk" to "SA-11",
            "sa-11" to "SA-11",
            "sa-17" to "SA-17",
            "tor" to "SA-15",
            "sa-15" to "SA-15",
            "tunguska" to "SA-19",
            "sa-19" to "SA-19",
            "strela-10" to "SA-13",
            "sa-13" to "SA-13",
            "strela-1" to "SA-9",
            "sa-9" to "SA-9",
            "igla" to "SA-18",
            "sa-18" to "SA-18",
            "patriot" to "MIM-104",
            "hawk" to "MIM-23",
            "nasams" to "NASAMS",
            "roland" to "Roland",
            "rapier" to "Rapier",
            "avenger" to "Avenger",
            "stinger" to "FIM-92",
            "shilka" to "ZSU-23-4",
            "zsu-23-4" to "ZSU-23-4",
            "gepard" to "Gepard",
            "vulcan" to "Vulcan",
            "zu-23" to "ZU-23"
        )

        // Try direct mapping
        mappings[normalized]?.let { systemName ->
            return systems.firstOrNull { it.name == systemName }
        }

        // Try partial match in mappings keys
        mappings.entries.firstOrNull { (key, _) ->
            normalized.contains(key) || key.contains(normalized)
        }?.let { (_, systemName) ->
            return systems.firstOrNull { it.name == systemName }
        }

        // Fallback to name search
        return getSystemByName(unitType)
    }

    /**
     * Get all systems in a category
     */
    fun getSystemsByCategory(category: String): List<AASystemSpec> {
        return systems.filter { it.category.equals(category, ignoreCase = true) }
    }

    /**
     * Get all available categories
     */
    fun getAllCategories(): List<String> {
        return systems.map { it.category }.distinct().sorted()
    }

    /**
     * Get all systems
     */
    fun getAllSystems(): List<AASystemSpec> = systems

    /**
     * Generate range rings for a system
     */
    fun getRangeRingsForSystem(system: AASystemSpec): List<AARangeRing> {
        val rings = mutableListOf<AARangeRing>()

        // Detection range ring (outermost, dashed, light red)
        rings.add(AARangeRing(
            radiusKm = system.detectionRangeKm,
            color = Color.parseColor("#88FF4444"), // Light red, semi-transparent
            label = "${system.displayName} - Detection (${system.detectionRangeKm.toInt()} km)",
            strokeWidth = 2f,
            isDashed = true
        ))

        // Engagement range ring (solid, red)
        rings.add(AARangeRing(
            radiusKm = system.engagementRangeKm,
            color = Color.parseColor("#DDFF0000"), // Bright red, more opaque
            label = "${system.displayName} - Max Range (${system.engagementRangeKm.toInt()} km)",
            strokeWidth = 4f,
            isDashed = false
        ))

        // Minimum engagement range (dead zone) if present
        if (system.minEngagementRangeKm > 0.5) {
            rings.add(AARangeRing(
                radiusKm = system.minEngagementRangeKm,
                color = Color.parseColor("#AAFFAA00"), // Amber, semi-transparent
                label = "${system.displayName} - Min Range (${system.minEngagementRangeKm} km)",
                strokeWidth = 2f,
                isDashed = true
            ))
        }

        return rings.sortedByDescending { it.radiusKm } // Largest first for drawing order
    }
}
