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

/**
 * Manages UDP reception of live flight data and provides state for UI consumption
 */
class DataPadManager(private val context: Context) {
    companion object {
        private const val TAG = "DataPadManager"
        private const val UDP_PORT = 5010
        private const val BUFFER_SIZE = 4096
        private const val SOCKET_TIMEOUT_MS = 1000
    }

    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _flightData = MutableStateFlow<FlightData?>(null)
    val flightData: StateFlow<FlightData?> = _flightData.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _lastUpdateTime = MutableStateFlow<Long?>(null)
    val lastUpdateTime: StateFlow<Long?> = _lastUpdateTime.asStateFlow()
    
    private val _deviceIpAddress = MutableStateFlow<String>("Unknown")
    val deviceIpAddress: StateFlow<String> = _deviceIpAddress.asStateFlow()
    
    private var udpSocket: DatagramSocket? = null
    private var receiveJob: Job? = null
    private var isStarted = false
    
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
        isStarted = true
        
        receiveJob = scope.launch {
            try {
                // Get and log device IP
                val deviceIp = getLocalIpAddress()
                _deviceIpAddress.value = deviceIp
                Log.d(TAG, "Device IP address: $deviceIp")
                
                // Log network info
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val activeNetwork = cm.activeNetworkInfo
                Log.d(TAG, "Active network: ${activeNetwork?.typeName}, connected: ${activeNetwork?.isConnected}")
                
                udpSocket = DatagramSocket(UDP_PORT).apply {
                    soTimeout = SOCKET_TIMEOUT_MS
                    reuseAddress = true
                    broadcast = true
                }
                Log.d(TAG, "UDP socket opened on port $UDP_PORT")
                Log.d(TAG, "Socket local address: ${udpSocket?.localAddress?.hostAddress}")
                Log.d(TAG, "Socket local port: ${udpSocket?.localPort}")
                Log.d(TAG, "Waiting for UDP packets on $deviceIp:$UDP_PORT...")
                
                val buffer = ByteArray(BUFFER_SIZE)
                val packet = DatagramPacket(buffer, buffer.size)
                
                while (isActive) {
                    try {
                        udpSocket?.receive(packet)
                        val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        Log.d(TAG, "UDP packet received (${packet.length} bytes): ${message.take(200)}")
                        
                        try {
                            val data = json.decodeFromString<FlightData>(message)
                            _flightData.value = data
                            _lastUpdateTime.value = System.currentTimeMillis()
                            _isConnected.value = true
                            Log.d(TAG, "Received flight data: ${data.aircraft} at ${data.altitude}m")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse flight data: ${e.message}", e)
                            Log.e(TAG, "Raw message: $message")
                        }
                        
                    } catch (e: SocketTimeoutException) {
                        // Timeout is normal, check connection status
                        val lastUpdate = _lastUpdateTime.value
                        if (lastUpdate != null && System.currentTimeMillis() - lastUpdate > 5000) {
                            _isConnected.value = false
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Error receiving UDP packet: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open UDP socket: ${e.message}")
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
        Log.d(TAG, "UDP socket closed")
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        stop()
        scope.cancel()
    }
}
