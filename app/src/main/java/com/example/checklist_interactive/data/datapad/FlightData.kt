package com.example.checklist_interactive.data.datapad

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Flight data model matching the UDP JSON stream format
 * Extended with tactical kneepad telemetry fields
 */
@Serializable
data class FlightData(
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
    val bearing: Double = 0.0,
    
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
    val trackCount: Int = 0
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
