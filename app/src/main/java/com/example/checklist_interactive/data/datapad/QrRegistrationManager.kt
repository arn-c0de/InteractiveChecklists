package com.example.checklist_interactive.data.datapad

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * QR-Code based device registration manager
 * Handles automatic device registration via QR-code scanning
 */

/**
 * Registration token payload (from QR code)
 */
@Serializable
data class RegistrationTokenPayload(
    val type: String = "datapad_registration",
    val version: String = "1.0",
    val token: String,
    val server: String,
    val port: Int,
    val expires: Long,
    val permissions: List<String> = listOf("receive", "send_commands")
)

/**
 * DeviceRegistration request message
 */
@Serializable
data class DeviceRegistration(
    val type: String = "DeviceRegistration",
    val registrationToken: String,
    val deviceId: String,
    val deviceName: String,
    val publicKey: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Registration response from server
 */
@Serializable
data class RegistrationResponse(
    val type: String,
    val error: String? = null,
    val message: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val permissions: List<String>? = null,
    val timestamp: Long
)

/**
 * Registration result
 */
sealed class RegistrationResult {
    data class Success(val deviceId: String, val deviceName: String, val permissions: List<String>) : RegistrationResult()
    data class Error(val error: String, val message: String) : RegistrationResult()
    object Timeout : RegistrationResult()
}

/**
 * Manages QR-code based device registration
 */
class QrRegistrationManager(
    private val context: Context,
    private val keyManager: KeyManager
) {
    companion object {
        private const val TAG = "QrRegistrationManager"
        private const val REGISTRATION_TIMEOUT_MS = 10000L // 10 seconds
        private const val BUFFER_SIZE = 8192
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Parse QR code data into registration token payload
     * @param qrData Raw QR code string (JSON)
     * @return Parsed RegistrationTokenPayload or null if invalid
     */
    fun parseQrCode(qrData: String): RegistrationTokenPayload? {
        return try {
            val payload = json.decodeFromString<RegistrationTokenPayload>(qrData)
            
            // Validate payload
            if (payload.type != "datapad_registration") {
                Log.w(TAG, "Invalid QR code type: ${payload.type}")
                return null
            }
            
            if (payload.version != "1.0") {
                Log.w(TAG, "Unsupported QR code version: ${payload.version}")
                return null
            }
            
            // Check if token is expired
            if (System.currentTimeMillis() / 1000 > payload.expires) {
                Log.w(TAG, "QR code token expired")
                return null
            }
            
            Log.i(TAG, "✅ Valid QR code: server=${payload.server}:${payload.port}")
            payload
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse QR code: ${e.message}")
            null
        }
    }
    
    /**
     * Register device with server using registration token
     * @param token Registration token from QR code
     * @param deviceName User-friendly device name
     * @return RegistrationResult indicating success or failure
     */
    suspend fun registerDevice(
        token: RegistrationTokenPayload,
        deviceName: String
    ): RegistrationResult = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        
        try {
            // Get device key pair
            val keyPair = keyManager.getOrCreateDeviceKeyPair()
            val deviceId = keyManager.getDeviceId()
            val publicKeyB64 = keyManager.exportPublicKey()
            
            Log.i(TAG, "📱 Registering device: $deviceId ($deviceName)")
            Log.i(TAG, "🌐 Server: ${token.server}:${token.port}")
            
            // Create registration request
            val request = DeviceRegistration(
                registrationToken = token.token,
                deviceId = deviceId,
                deviceName = deviceName,
                publicKey = publicKeyB64
            )
            
            val requestJson = json.encodeToString(request)
            val requestBytes = requestJson.toByteArray()
            
            // Send registration request to handshake port
            socket = DatagramSocket()
            socket.soTimeout = REGISTRATION_TIMEOUT_MS.toInt()
            
            val serverAddress = InetAddress.getByName(token.server)
            val packet = DatagramPacket(
                requestBytes,
                requestBytes.size,
                serverAddress,
                token.port
            )
            
            Log.d(TAG, "📤 Sending registration request...")
            socket.send(packet)
            
            // Wait for response
            val responseBuffer = ByteArray(BUFFER_SIZE)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            
            Log.d(TAG, "⏳ Waiting for registration response...")
            socket.receive(responsePacket)
            
            val responseJson = String(responsePacket.data, 0, responsePacket.length)
            val response = json.decodeFromString<RegistrationResponse>(responseJson)
            
            Log.d(TAG, "📥 Received response: ${response.type}")
            
            when (response.type) {
                "RegistrationSuccess" -> {
                    Log.i(TAG, "✅ Registration successful!")
                    RegistrationResult.Success(
                        deviceId = response.deviceId ?: deviceId,
                        deviceName = response.deviceName ?: deviceName,
                        permissions = response.permissions ?: token.permissions
                    )
                }
                "RegistrationError" -> {
                    Log.w(TAG, "❌ Registration failed: ${response.message}")
                    RegistrationResult.Error(
                        error = response.error ?: "Unknown",
                        message = response.message ?: "Registration failed"
                    )
                }
                else -> {
                    Log.w(TAG, "❌ Unknown response type: ${response.type}")
                    RegistrationResult.Error(
                        error = "UnknownResponse",
                        message = "Unexpected response from server"
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "❌ Registration timeout")
            RegistrationResult.Timeout
        } catch (e: Exception) {
            Log.e(TAG, "❌ Registration failed: ${e.message}", e)
            RegistrationResult.Error(
                error = "NetworkError",
                message = e.message ?: "Network error during registration"
            )
        } finally {
            socket?.close()
        }
    }
    
    /**
     * Apply registration token settings to DataPadManager
     * @param token Registration token from QR code
     * @param dataPadManager DataPadManager instance to update
     */
    fun applyTokenSettings(token: RegistrationTokenPayload, dataPadManager: DataPadManager) {
        Log.i(TAG, "⚙️ Applying token settings to DataPadManager")
        
        // Update server IP and port
        dataPadManager.updateServerIp(token.server)
        dataPadManager.updatePort(token.port)
        
        Log.i(TAG, "✅ Settings applied: ${token.server}:${token.port}")
    }
    
    /**
     * Complete registration flow: parse QR code, register device, apply settings
     * @param qrData Raw QR code string
     * @param deviceName User-friendly device name
     * @param dataPadManager DataPadManager instance to update
     * @return RegistrationResult indicating success or failure
     */
    suspend fun completeRegistration(
        qrData: String,
        deviceName: String,
        dataPadManager: DataPadManager
    ): RegistrationResult {
        // Parse QR code
        val token = parseQrCode(qrData) ?: return RegistrationResult.Error(
            error = "InvalidQrCode",
            message = "QR code is invalid or expired"
        )
        
        // Register device with server
        val result = registerDevice(token, deviceName)
        
        // If successful, apply settings
        if (result is RegistrationResult.Success) {
            applyTokenSettings(token, dataPadManager)
        }
        
        return result
    }
}
