package com.example.checklist_interactive.data.datapad

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Flight data model matching the UDP JSON stream format
 */
@Serializable
data class FlightData(
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
    
    @SerialName("timestamp")
    val timestamp: String = "",
    
    @SerialName("unitID")
    val unitID: String = "N/A",
    
    @SerialName("lua_version")
    val luaVersion: String = "",
    
    @SerialName("streamer_version")
    val streamerVersion: String = ""
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
