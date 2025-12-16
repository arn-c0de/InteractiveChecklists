package com.example.checklist_interactive.data.datapad

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages UDP reception of live flight data and provides state for UI consumption
 */
class DataPadManager(private val context: Context) {
    companion object {
        private const val TAG = "DataPadManager"
        private const val DEFAULT_UDP_PORT = 5010
        private const val BUFFER_SIZE = 4096
        private const val SOCKET_TIMEOUT_MS = 1000
        
        private const val PREFS_NAME = "datapad_settings"
        private const val KEY_UDP_PORT = "udp_port"
        private const val KEY_BIND_IP = "bind_ip"
        private const val KEY_PRE_SHARED_KEY = "pre_shared_key"
        private const val KEY_ENABLED = "enabled"
        
        // Default Pre-Shared Key (32 bytes for AES-256)
        private const val DEFAULT_PRE_SHARED_KEY = "DCS_DataPad_Secret_Key_32BYTES!!"
        
        /**
         * Decrypt AES-GCM encrypted data
         * Format: nonce (12 bytes) + ciphertext + tag (16 bytes)
         */
        private fun decryptPayload(encryptedData: ByteArray, key: ByteArray): ByteArray? {
            return try {
                if (encryptedData.size < 28) { // 12 (nonce) + 16 (tag) minimum
                    Log.e(TAG, "Encrypted data too short: ${encryptedData.size} bytes")
                    return null
                }
                
                // Extract nonce (first 12 bytes)
                val nonce = encryptedData.copyOfRange(0, 12)
                // Extract ciphertext + tag (remaining bytes)
                val ciphertext = encryptedData.copyOfRange(12, encryptedData.size)
                
                // Initialize AES-GCM cipher
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val keySpec = SecretKeySpec(key, "AES")
                val gcmSpec = GCMParameterSpec(128, nonce) // 128-bit authentication tag
                
                cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
                cipher.doFinal(ciphertext)
            } catch (e: Exception) {
                Log.e(TAG, "Decryption failed: ${e.message}", e)
                null
            }
        }
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _flightData = MutableStateFlow<FlightData?>(null)
    val flightData: StateFlow<FlightData?> = _flightData.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Whether the UDP receiver is enabled (persisted)
    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    private val _lastUpdateTime = MutableStateFlow<Long?>(null)
    val lastUpdateTime: StateFlow<Long?> = _lastUpdateTime.asStateFlow()
    
    private val _deviceIpAddress = MutableStateFlow<String>("Unknown")
    val deviceIpAddress: StateFlow<String> = _deviceIpAddress.asStateFlow()
    
    private val _udpPort = MutableStateFlow(prefs.getInt(KEY_UDP_PORT, DEFAULT_UDP_PORT))
    val udpPort: StateFlow<Int> = _udpPort.asStateFlow()
    
    private val _bindIp = MutableStateFlow(prefs.getString(KEY_BIND_IP, "") ?: "")
    val bindIp: StateFlow<String> = _bindIp.asStateFlow()
    
    private val _preSharedKey = MutableStateFlow(prefs.getString(KEY_PRE_SHARED_KEY, DEFAULT_PRE_SHARED_KEY) ?: DEFAULT_PRE_SHARED_KEY)
    val preSharedKey: StateFlow<String> = _preSharedKey.asStateFlow()
    
    private var udpSocket: DatagramSocket? = null
    private var receiveJob: Job? = null
    private var isStarted = false

    // Helper functions to gate UDP-related logs when disabled
    private fun udpLogD(message: String) {
        if (_isEnabled.value) Log.d(TAG, message)
    }

    private fun udpLogE(message: String, throwable: Throwable? = null) {
        if (_isEnabled.value) {
            if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
        }
    }
    
    /**
     * Get the device's local IP address
     */
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress ?: "Unknown"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address: ${e.message}")
        }
        return "Unknown"
    }

    /**
     * Start listening for UDP packets on the configured port
     */
    fun start() {
        if (isStarted) return
        if (!_isEnabled.value) {
            // Receiver is disabled by user - do not start
            udpLogD("Start requested but receiver is disabled")
            return
        }
        isStarted = true
        
        receiveJob = scope.launch {
            try {
                // Get and log device IP
                val deviceIp = getLocalIpAddress()
                _deviceIpAddress.value = deviceIp
                udpLogD("Device IP address: $deviceIp")
                
                // Log network info
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val activeNetwork = cm.activeNetworkInfo
                udpLogD("Active network: ${activeNetwork?.typeName}, connected: ${activeNetwork?.isConnected}")
                
                val port = _udpPort.value
                val bindAddress = _bindIp.value
                
                udpSocket = if (bindAddress.isNotEmpty()) {
                    DatagramSocket(port, java.net.InetAddress.getByName(bindAddress))
                } else {
                    DatagramSocket(port)
                }.apply {
                    soTimeout = SOCKET_TIMEOUT_MS
                    reuseAddress = true
                    broadcast = true
                }
                udpLogD("UDP socket opened on ${if (bindAddress.isNotEmpty()) bindAddress else "0.0.0.0"}:$port")
                udpLogD("Socket local address: ${udpSocket?.localAddress?.hostAddress}")
                udpLogD("Socket local port: ${udpSocket?.localPort}")
                udpLogD("Waiting for UDP packets on ${if (bindAddress.isNotEmpty()) bindAddress else deviceIp}:${_udpPort.value}...")
                
                val buffer = ByteArray(BUFFER_SIZE)
                val packet = DatagramPacket(buffer, buffer.size)
                
                while (isActive) {
                    try {
                        udpSocket?.receive(packet)
                        val receivedData = packet.data.copyOfRange(0, packet.length)
                        udpLogD("UDP packet received (${packet.length} bytes)")
                        
                        // Try to decrypt the data
                        val keyBytes = _preSharedKey.value.toByteArray(Charsets.UTF_8)
                        val decryptedData = decryptPayload(receivedData, keyBytes)
                        
                        if (decryptedData != null) {
                            val message = String(decryptedData, Charsets.UTF_8)
                            udpLogD("Decrypted message: ${message.take(200)}")
                            
                            try {
                                val data = json.decodeFromString<FlightData>(message)
                                _flightData.value = data
                                _lastUpdateTime.value = System.currentTimeMillis()
                                _isConnected.value = true
                                udpLogD("✅ Received encrypted flight data: ${data.aircraft} at ${data.altitude}m")
                            } catch (e: Exception) {
                                udpLogE("Failed to parse flight data: ${e.message}", e)
                                udpLogE("Raw message: $message")
                            }
                        } else {
                            udpLogE("❌ Failed to decrypt packet - check Pre-Shared Key!")
                        }
                        
                    } catch (e: SocketTimeoutException) {
                        // Timeout is normal, check connection status
                        val lastUpdate = _lastUpdateTime.value
                        if (lastUpdate != null && System.currentTimeMillis() - lastUpdate > 5000) {
                            _isConnected.value = false
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            udpLogE("Error receiving UDP packet: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                udpLogE("Failed to open UDP socket: ${e.message}")
                _isConnected.value = false
            } finally {
                udpSocket?.close()
                udpSocket = null
            }
        }
    }

    /**
     * Stop listening for UDP packets
     */
    fun stop() {
        isStarted = false
        receiveJob?.cancel()
        receiveJob = null
        udpSocket?.close()
        udpSocket = null
        _isConnected.value = false
        udpLogD("UDP socket closed")
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        stop()
        scope.cancel()
    }
    
    /**
     * Update UDP port and restart socket if running
     */
    fun updatePort(newPort: Int) {
        if (newPort in 1024..65535) {
            prefs.edit().putInt(KEY_UDP_PORT, newPort).apply()
            _udpPort.value = newPort
            if (isStarted) {
                restart()
            }
        }
    }
    
    /**
     * Update bind IP address (empty = all interfaces)
     */
    fun updateBindIp(newIp: String) {
        prefs.edit().putString(KEY_BIND_IP, newIp).apply()
        _bindIp.value = newIp
        if (isStarted) {
            restart()
        }
    }
    
    /**
     * Update Pre-Shared Key for AES-GCM decryption
     */
    fun updatePreSharedKey(newKey: String) {
        if (newKey.length == 32 || newKey.isEmpty()) {
            val finalKey = newKey.ifEmpty { DEFAULT_PRE_SHARED_KEY }
            prefs.edit().putString(KEY_PRE_SHARED_KEY, finalKey).apply()
            _preSharedKey.value = finalKey
        }
    }

    /**
     * Enable or disable the UDP receiver. When disabled, the socket is closed and no UDP logs will be emitted.
     */
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _isEnabled.value = enabled
        if (enabled) start() else stop()
    }

    fun toggleEnabled() {
        setEnabled(!_isEnabled.value)
    }
    
    /**
     * Restart the UDP socket with new settings
     */
    private fun restart() {
        stop()
        start()
    }
}
