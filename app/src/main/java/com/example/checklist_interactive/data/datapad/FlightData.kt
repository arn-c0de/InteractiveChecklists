package com.example.checklist_interactive.data.datapad

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Flight data model matching the UDP JSON stream format
 * Extended with tactical kneepad telemetry fields
 */
@Serializable
data class FlightData(
    // === Message Type (for heartbeat detection) ===
    @SerialName("type")
    val type: String? = null,  // "heartbeat" for keep-alive messages, null for normal data
    
    @SerialName("message")
    val message: String? = null,  // Optional message from server
    
    // === Basic Identity ===
    @SerialName("aircraft")
    val aircraft: String = "",
    
    @SerialName("unitName")
    val unitName: String = "",
    
    @SerialName("coalition")
    val coalition: String = "",
    
    @SerialName("country")
    val country: Int = 0,
    
    @SerialName("group")
    val group: String = "",
    
    // === Flight Parameters ===
    @SerialName("alt")
    val altitude: Double = 0.0,
    
    @SerialName("terrainElevation")
    val terrainElevation: Double? = null,
    
    @SerialName("heading")
    val heading: Double = 0.0,
    
    @SerialName("pitch")
    val pitch: Double = 0.0,
    
    @SerialName("bank")
    val bank: Double = 0.0,
    
    @SerialName("lat")
    val latitude: Double = 0.0,
    
    @SerialName("long")
    val longitude: Double = 0.0,
    
    @SerialName("pos")
    val position: Position? = null,
    
    // === Speed & Vertical ===
    @SerialName("groundSpeed")
    val groundSpeed: Double? = null,
    
    @SerialName("indicatedAirspeed")
    val indicatedAirspeed: Double? = null,
    
    @SerialName("trueAirspeed")
    val trueAirspeed: Double? = null,
    
    @SerialName("verticalSpeed")
    val verticalSpeed: Double? = null,
    
    @SerialName("mach")
    val mach: Double? = null,
    
    // === Fuel ===
    @SerialName("fuel")
    val fuel: FuelData? = null,
    
    // === Angle of Attack & G-Load ===
    @SerialName("angleOfAttack")
    val angleOfAttack: Double? = null,
    
    @SerialName("gLoad")
    val gLoad: GLoadData? = null,
    
    // === Aircraft Mass ===
    @SerialName("aircraftMass")
    val aircraftMass: AircraftMassData? = null,
    
    // === Engine Data ===
    @SerialName("engines")
    val engines: EngineData? = null,
    
    // === Flight Controls & Trim ===
    @SerialName("flightControls")
    val flightControls: FlightControlsData? = null,
    
    // === Mechanical (Gear, Flaps, etc.) ===
    @SerialName("mechanical")
    val mechanical: MechanicalData? = null,
    
    @SerialName("weightOnWheels")
    val weightOnWheels: Boolean = false,
    
    // === Lights ===
    @SerialName("lights")
    val lights: LightsData? = null,
    
    // === Mission Time ===
    @SerialName("missionTime")
    val missionTime: Double? = null,
    
    // === Systems Status ===
    @SerialName("systems")
    val systems: SystemsData? = null,
    
    // === Nearby Units ===
    @SerialName("nearbyUnits")
    val nearbyUnits: List<NearbyUnit>? = null,
    
    // === Navigation & Waypoints ===
    @SerialName("waypoint")
    val waypoint: WaypointData? = null,
    
    @SerialName("flightPlan")
    val flightPlan: FlightPlanData? = null,
    
    // === Weapons & Stores ===
    @SerialName("weapons")
    val weapons: WeaponsData? = null,
    
    // === EW / RWR / Threats ===
    @SerialName("rwr")
    val rwr: RwrData? = null,
    
    @SerialName("radar")
    val radar: RadarData? = null,
    
    @SerialName("countermeasures")
    val countermeasures: CountermeasuresData? = null,
    
    // === Avionics & Systems ===
    @SerialName("autopilot")
    val autopilot: AutopilotData? = null,
    
    @SerialName("transponder")
    val transponder: TransponderData? = null,
    
    @SerialName("radios")
    val radios: RadiosData? = null,
    
    @SerialName("warnings")
    val warnings: WarningsData? = null,
    
    // === Environmental ===
    @SerialName("environment")
    val environment: EnvironmentData? = null,
    
    // === Existing Status Flags ===
    @SerialName("isHuman")
    val isHuman: Boolean = false,
    
    @SerialName("born")
    val born: Boolean = false,
    
    @SerialName("aiOn")
    val aiOn: Boolean = false,
    
    @SerialName("radarActive")
    val radarActive: Boolean = false,
    
    @SerialName("jamming")
    val jamming: Boolean = false,
    
    @SerialName("irJamming")
    val irJamming: Boolean = false,
    
    @SerialName("invisible")
    val invisible: Boolean = false,
    
    // === Metadata ===
    @SerialName("timestamp")
    val timestamp: String = "",
    
    @SerialName("unitID")
    val unitID: String = "N/A",
    
    @SerialName("lua_version")
    val luaVersion: String = "",
    
    @SerialName("streamer_version")
    val streamerVersion: String = "",
    
    @SerialName("dataAge")
    val dataAge: Double? = null,
    
    @SerialName("updateRate")
    val updateRate: Double? = null
)

@Serializable
data class Position(
    @SerialName("x")
    val x: Double = 0.0,
    
    @SerialName("y")
    val y: Double = 0.0,
    
    @SerialName("z")
    val z: Double = 0.0
)

@Serializable
data class FuelData(
    @SerialName("total")
    val total: Double = 0.0,
    
    @SerialName("remaining")
    val remaining: Double = 0.0,
    
    @SerialName("internal")
    val internal: Double? = null,
    
    @SerialName("external")
    val external: Double? = null,
    
    @SerialName("endurance")
    val endurance: Double? = null, // minutes
    
    @SerialName("fuelFlow")
    val fuelFlow: Double? = null // kg/s or lbs/s
)

@Serializable
data class WaypointData(
    @SerialName("current")
    val current: String? = null,
    
    @SerialName("distance")
    val distance: Double? = null, // meters or nm
    
    @SerialName("bearing")
    val bearing: Double? = null, // degrees
    
    @SerialName("eta")
    val eta: String? = null, // ISO timestamp or seconds
    
    @SerialName("etaSeconds")
    val etaSeconds: Double? = null
)

@Serializable
data class FlightPlanData(
    @SerialName("currentIndex")
    val currentIndex: Int? = null,
    
    @SerialName("totalWaypoints")
    val totalWaypoints: Int? = null,
    
    @SerialName("route")
    val route: String? = null
)

@Serializable
data class WeaponsData(
    @SerialName("masterArm")
    val masterArm: Boolean = false,
    
    @SerialName("selected")
    val selected: String? = null,
    
    @SerialName("stations")
    val stations: List<WeaponStation>? = null,
    
    @SerialName("totalCount")
    val totalCount: Int? = null
)

@Serializable
data class WeaponStation(
    @SerialName("station")
    val station: Int = 0,
    
    @SerialName("type")
    val type: Int = 0,
    
    @SerialName("count")
    val count: Int = 0
)

@Serializable
data class RwrData(
    @SerialName("contacts")
    val contacts: List<RwrContact>? = null,
    
    @SerialName("threatsDetected")
    val threatsDetected: Int = 0
)

@Serializable
data class RwrContact(
    @SerialName("id")
    val id: String = "",
    
    @SerialName("type")
    val type: String = "", // SAM, AAA, AI (air intercept)
    
    @SerialName("bearing")
    val bearing: Double? = null,
    
    @SerialName("azimuth")
    val azimuth: Double? = null,
    
    @SerialName("priority")
    val priority: Int = 0, // 0=low, 1=medium, 2=high, 3=critical
    
    @SerialName("locked")
    val locked: Boolean = false
)

@Serializable
data class RadarData(
    @SerialName("mode")
    val mode: String? = null, // RWS, TWS, STT, etc.
    
    @SerialName("range")
    val range: Double? = null,
    
    @SerialName("locked")
    val locked: Boolean = false,
    
    @SerialName("trackCount")
    val trackCount: Int = 0,
    
    @SerialName("azimuth")
    val azimuth: Double? = null,
    
    @SerialName("elevation")
    val elevation: Double? = null,
    
    @SerialName("scan")
    val scan: String? = null,
    
    @SerialName("tracks")
    val tracks: List<RadarTrack>? = null
)

@Serializable
data class RadarTrack(
    @SerialName("id")
    val id: Int = 0,
    
    @SerialName("range")
    val range: Double? = null,
    
    @SerialName("azimuth")
    val azimuth: Double? = null,
    
    @SerialName("elevation")
    val elevation: Double? = null,
    
    @SerialName("locked")
    val locked: Boolean = false
)

@Serializable
data class CountermeasuresData(
    @SerialName("chaffCount")
    val chaffCount: Int = 0,
    
    @SerialName("flareCount")
    val flareCount: Int = 0,
    
    @SerialName("dispenserMode")
    val dispenserMode: String? = null // AUTO, MANUAL, OFF
)

@Serializable
data class AutopilotData(
    @SerialName("enabled")
    val enabled: Boolean = false,
    
    @SerialName("mode")
    val mode: String? = null, // ALT HOLD, HDG, NAV, etc.
    
    @SerialName("flightDirector")
    val flightDirector: Boolean = false
)

@Serializable
data class TransponderData(
    @SerialName("code")
    val code: String? = null, // 4-digit squawk
    
    @SerialName("mode")
    val mode: String? = null, // OFF, STBY, ON, ALT
    
    @SerialName("ident")
    val ident: Boolean = false
)

@Serializable
data class RadiosData(
    @SerialName("com1")
    val com1: Double? = null, // MHz
    
    @SerialName("com2")
    val com2: Double? = null,
    
    @SerialName("guard")
    val guard: Boolean = false,
    
    @SerialName("activeFreq")
    val activeFreq: Double? = null
)

@Serializable
data class WarningsData(
    @SerialName("masterCaution")
    val masterCaution: Boolean = false,
    
    @SerialName("masterWarning")
    val masterWarning: Boolean = false,
    
    @SerialName("faults")
    val faults: List<String>? = null, // fault codes
    
    @SerialName("alerts")
    val alerts: List<String>? = null
)

@Serializable
data class EnvironmentData(
    @SerialName("windDirection")
    val windDirection: Double? = null, // degrees
    
    @SerialName("windSpeed")
    val windSpeed: Double? = null, // m/s or knots
    
    @SerialName("temperature")
    val temperature: Double? = null, // celsius
    
    @SerialName("pressure")
    val pressure: Double? = null, // hPa or inHg
    
    @SerialName("visibility")
    val visibility: Double? = null, // meters or nm
    
    @SerialName("clouds")
    val clouds: String? = null
)

@Serializable
data class GLoadData(
    @SerialName("x")
    val x: Double = 0.0,
    
    @SerialName("y")
    val y: Double = 0.0,
    
    @SerialName("z")
    val z: Double = 0.0
)

@Serializable
data class AircraftMassData(
    @SerialName("total")
    val total: Double? = null, // kg
    
    @SerialName("empty")
    val empty: Double? = null,
    
    @SerialName("payload")
    val payload: Double? = null
)

@Serializable
data class EngineData(
    @SerialName("rpm")
    val rpm: EngineRpmData? = null,
    
    @SerialName("egt")
    val egt: EngineEgtData? = null,
    
    @SerialName("throttle")
    val throttle: Double? = null,
    
    @SerialName("afterburner")
    val afterburner: Boolean = false
)

@Serializable
data class EngineRpmData(
    @SerialName("left")
    val left: Double? = null,
    
    @SerialName("right")
    val right: Double? = null
)

@Serializable
data class EngineEgtData(
    @SerialName("left")
    val left: Double? = null,
    
    @SerialName("right")
    val right: Double? = null
)

@Serializable
data class FlightControlsData(
    @SerialName("pitch")
    val pitch: Double? = null,
    
    @SerialName("roll")
    val roll: Double? = null,
    
    @SerialName("yaw")
    val yaw: Double? = null,
    
    @SerialName("trimPitch")
    val trimPitch: Double? = null,
    
    @SerialName("trimRoll")
    val trimRoll: Double? = null,
    
    @SerialName("trimYaw")
    val trimYaw: Double? = null
)

@Serializable
data class MechanicalData(
    @SerialName("gear")
    val gear: GearData? = null,
    
    @SerialName("flaps")
    val flaps: Double? = null, // 0.0 to 1.0
    
    @SerialName("speedbrake")
    val speedbrake: Double? = null,
    
    @SerialName("canopy")
    val canopy: Double? = null,
    
    @SerialName("hook")
    val hook: Double? = null,
    
    @SerialName("wheelBrake")
    val wheelBrake: Double? = null,
    
    @SerialName("noseGearSteeringEnabled")
    val noseGearSteeringEnabled: Boolean? = null
)

@Serializable
data class GearData(
    @SerialName("nose")
    val nose: Double? = null, // 0.0=up, 1.0=down
    
    @SerialName("left")
    val left: Double? = null,
    
    @SerialName("right")
    val right: Double? = null
)

@Serializable
data class LightsData(
    @SerialName("landing")
    val landing: Double? = null,
    
    @SerialName("taxi")
    val taxi: Double? = null,
    
    @SerialName("navigation")
    val navigation: Double? = null,
    
    @SerialName("strobe")
    val strobe: Double? = null,
    
    @SerialName("formation")
    val formation: Double? = null
)

@Serializable
data class SystemsData(
    @SerialName("electrical")
    val electrical: String? = null,
    
    @SerialName("hydraulic")
    val hydraulic: String? = null,
    
    @SerialName("apuOn")
    val apuOn: Boolean? = null,
    
    @SerialName("generatorOn")
    val generatorOn: Boolean? = null
)

/**
 * Custom serializer for coalition field that handles both string and integer formats
 * Legacy format: "Enemies", "Allies", "Neutral"
 * New format: 0, 1, 2
 */
object CoalitionSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Coalition", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        return try {
            // Try to decode as integer first (new format)
            decoder.decodeInt()
        } catch (e: Exception) {
            // If that fails, try as string (legacy format)
            try {
                val str = decoder.decodeString()
                when (str.lowercase()) {
                    "neutral", "neutrals" -> 0
                    "red", "enemies" -> 1
                    "blue", "allies" -> 2
                    else -> str.toIntOrNull() ?: 0
                }
            } catch (e2: Exception) {
                0 // Default to neutral on any error
            }
        }
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }
}

/**
 * Custom serializer for type field that handles both integer and string formats
 * DCS may send type as integer (unit type ID) or string (unit type name)
 */
object UnitTypeSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UnitType", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        return try {
            // Try string first
            decoder.decodeString()
        } catch (e: Exception) {
            // If that fails, decode as int and convert to string
            try {
                decoder.decodeInt().toString()
            } catch (e2: Exception) {
                "Unknown"
            }
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

@Serializable
data class NearbyUnit(
    @SerialName("dcsId")
    val dcsId: String = "",

    @SerialName("name")
    val name: String = "",

    @Serializable(with = UnitTypeSerializer::class)
    @SerialName("type")
    val type: String = "",  // Can be integer (unit type ID) or string (unit type name)

    @SerialName("category")
    val category: String = "",  // aircraft, helicopter, ground, ship, structure, weapon

    @Serializable(with = CoalitionSerializer::class)
    @SerialName("coalition")
    val coalition: Int = 0,  // 0=Neutral, 1=Red, 2=Blue (or "Enemies"/"Allies" in legacy format)

    @SerialName("latitude")
    val latitude: Double = 0.0,

    @SerialName("longitude")
    val longitude: Double = 0.0,

    @SerialName("altitude")
    val altitude: Double = 0.0,

    @SerialName("heading")
    val heading: Double? = null,

    @SerialName("speed")
    val speed: Double? = null,

    @SerialName("distance")
    val distance: Double? = null,  // meters

    @SerialName("bearing")
    val bearing: Double? = null,  // 0-360 degrees

    @SerialName("country")
    val country: Int? = null,

    @SerialName("group")
    val group: String? = null,

    @SerialName("pilot")
    val pilot: String? = null
)
