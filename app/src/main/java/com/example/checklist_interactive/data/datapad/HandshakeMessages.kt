package com.example.checklist_interactive.data.datapad

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Handshake protocol messages for ECDH key exchange
 */

/**
 * Base interface for all handshake messages
 */
sealed interface HandshakeMessage {
    val type: String
    val timestamp: Long
}

/**
 * ClientHello: Initial message from Android app to DCS server
 * Contains device identity and public key for ECDH
 */
@Serializable
data class ClientHello(
    @SerialName("type")
    override val type: String = "ClientHello",
    
    @SerialName("version")
    val version: String = "1.0",
    
    @SerialName("deviceId")
    val deviceId: String,
    
    @SerialName("deviceName")
    val deviceName: String,
    
    @SerialName("publicKey")
    val publicKey: String, // Base64 encoded EC public key
    
    @SerialName("timestamp")
    override val timestamp: Long = System.currentTimeMillis(),
    
    @SerialName("capabilities")
    val capabilities: List<String> = listOf("receive", "send_commands")
) : HandshakeMessage

/**
 * ServerHello: Response from DCS server to Android app
 * Contains server public key and authorization status
 */
@Serializable
data class ServerHello(
    @SerialName("type")
    override val type: String = "ServerHello",

    @SerialName("version")
    val version: String = "1.0",

    @SerialName("sessionId")
    val sessionId: String,

    @SerialName("publicKey")
    val publicKey: String, // Base64 encoded EC public key

    @SerialName("timestamp")
    override val timestamp: Long = System.currentTimeMillis(),

    @SerialName("authorized")
    val authorized: Boolean,

    @SerialName("aircraft")
    val aircraft: String? = null,

    @SerialName("message")
    val message: String? = null,

    @SerialName("serverHmac")
    val serverHmac: String? = null,  // For mutual authentication

    @SerialName("salt")
    val salt: String? = null  // Base64 encoded random salt for HKDF (32 bytes)
) : HandshakeMessage

/**
 * KeyConfirm: Confirmation from Android app that session key is derived
 * Contains HMAC to prove possession of the shared secret
 */
@Serializable
data class KeyConfirm(
    @SerialName("type")
    override val type: String = "KeyConfirm",
    
    @SerialName("sessionId")
    val sessionId: String,
    
    @SerialName("hmac")
    val hmac: String, // Base64 encoded HMAC-SHA256 of session key
    
    @SerialName("timestamp")
    override val timestamp: Long = System.currentTimeMillis()
) : HandshakeMessage

/**
 * Ack: Final acknowledgment from DCS server
 * Confirms that session is established and ready
 */
@Serializable
data class Ack(
    @SerialName("type")
    override val type: String = "Ack",
    
    @SerialName("sessionId")
    val sessionId: String,
    
    @SerialName("status")
    val status: String, // "ready", "error", etc.
    
    @SerialName("timestamp")
    override val timestamp: Long = System.currentTimeMillis(),
    
    @SerialName("message")
    val message: String? = null
) : HandshakeMessage

/**
 * HandshakeError: Error response during handshake
 */
@Serializable
data class HandshakeError(
    @SerialName("type")
    override val type: String = "Error",
    
    @SerialName("error")
    val error: String,
    
    @SerialName("message")
    val message: String,
    
    @SerialName("timestamp")
    override val timestamp: Long = System.currentTimeMillis(),
    
    @SerialName("sessionId")
    val sessionId: String? = null
) : HandshakeMessage

/**
 * Session information stored after successful handshake
 */
data class SessionInfo(
    val sessionId: String,
    val sessionKey: javax.crypto.SecretKey,
    val establishedAt: Long,
    val serverPublicKey: java.security.PublicKey,
    val aircraft: String?
)
