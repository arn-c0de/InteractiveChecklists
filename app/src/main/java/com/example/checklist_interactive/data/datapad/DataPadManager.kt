package com.example.checklist_interactive.data.datapad

import android.content.Context
import android.util.Log
import com.example.checklist_interactive.data.tactical.TacticalDatabase
import com.example.checklist_interactive.data.tactical.TacticalUnitEntity
import com.example.checklist_interactive.data.tactical.TacticalUnitHistoryEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import java.net.InetAddress
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

/**
 * Manages UDP reception of live flight data and provides state for UI consumption
 */
class DataPadManager(private val context: Context) {
    companion object {
        private const val TAG = "DataPadManager"
        private const val DEFAULT_UDP_PORT = 5010
        private const val BUFFER_SIZE = 65536  // Increased from 4096 to support large tactical unit payloads (max UDP size)
        private const val SOCKET_TIMEOUT_MS = 1000

        private const val PREFS_NAME = "datapad_settings"
        private const val KEY_UDP_PORT = "udp_port"
        private const val KEY_BIND_IP = "bind_ip"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DEVICE_NAME = "device_name"
        // Persist last transient reception state (true if receiver was running when app exited)
        private const val KEY_LAST_RUNNING = "last_running"
        // Enable/disable entity tracking (tactical units)
        private const val KEY_ENTITY_TRACKING_ENABLED = "entity_tracking_enabled"
        // Tactical units map update interval in seconds
        private const val KEY_TACTICAL_UNITS_MAP_UPDATE_INTERVAL = "tactical_units_map_update_interval"
        private const val KEY_TACTICAL_UNITS_SHOW_LIVE_ONLY = "tactical_units_show_live_only"
        private const val KEY_SHOW_TACTICAL_UNITS_ON_MAP = "show_tactical_units_on_map"

        // Handshake timeout
        private const val HANDSHAKE_TIMEOUT_MS = 10000L
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Tactical database for storing tracked units
    private val tacticalDb by lazy { TacticalDatabase.getInstance(context) }

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

    // Runtime running state (separate from persisted enabled setting)
    // Initialize from persisted last-running preference so reception-off can persist across restarts
    private val _isRunning = MutableStateFlow(prefs.getBoolean(KEY_LAST_RUNNING, false))
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _lastUpdateTime = MutableStateFlow<Long?>(null)
    val lastUpdateTime: StateFlow<Long?> = _lastUpdateTime.asStateFlow()
    
    private val _deviceIpAddress = MutableStateFlow<String>("Unknown")
    val deviceIpAddress: StateFlow<String> = _deviceIpAddress.asStateFlow()
    
    private val _udpPort = MutableStateFlow(prefs.getInt(KEY_UDP_PORT, DEFAULT_UDP_PORT))
    val udpPort: StateFlow<Int> = _udpPort.asStateFlow()
    
    private val _bindIp = MutableStateFlow(prefs.getString(KEY_BIND_IP, "") ?: "")
    val bindIp: StateFlow<String> = _bindIp.asStateFlow()

    private val _deviceName = MutableStateFlow(prefs.getString(KEY_DEVICE_NAME, "Android Tablet") ?: "Android Tablet")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _serverIp = MutableStateFlow(prefs.getString("server_ip", "") ?: "")
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()

    private val _handshakeStatus = MutableStateFlow<String?>(null)
    val handshakeStatus: StateFlow<String?> = _handshakeStatus.asStateFlow()

    // Entity tracking enabled/disabled
    private val _isEntityTrackingEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENTITY_TRACKING_ENABLED, false))
    val isEntityTrackingEnabled: StateFlow<Boolean> = _isEntityTrackingEnabled.asStateFlow()
    
    // Tactical units map update interval (seconds) - how often to redraw markers on map
    private val _tacticalUnitsMapUpdateInterval = MutableStateFlow(prefs.getFloat(KEY_TACTICAL_UNITS_MAP_UPDATE_INTERVAL, 2.0f))
    val tacticalUnitsMapUpdateInterval: StateFlow<Float> = _tacticalUnitsMapUpdateInterval.asStateFlow()
    
    // Tactical units live filter - only show units seen in last 10 seconds
    private val _tacticalUnitsShowLiveOnly = MutableStateFlow(prefs.getBoolean(KEY_TACTICAL_UNITS_SHOW_LIVE_ONLY, false))
    val tacticalUnitsShowLiveOnly: StateFlow<Boolean> = _tacticalUnitsShowLiveOnly.asStateFlow()

    // Tactical units map visibility - show/hide units on map (independent from entity tracking)
    private val _showTacticalUnitsOnMap = MutableStateFlow(prefs.getBoolean(KEY_SHOW_TACTICAL_UNITS_ON_MAP, true))
    val showTacticalUnitsOnMap: StateFlow<Boolean> = _showTacticalUnitsOnMap.asStateFlow()

    // Connection health tracking (for heartbeat monitoring)
    enum class ConnectionHealth {
        DISCONNECTED,  // No connection
        HEALTHY,       // Data received recently (green)
        WARNING,       // No message for ~30s (yellow)
        STALE,         // No message for 35+ seconds (red)
    }
    
    private val _connectionHealth = MutableStateFlow(ConnectionHealth.DISCONNECTED)
    val connectionHealth: StateFlow<ConnectionHealth> = _connectionHealth.asStateFlow()
    
    // Expose last heartbeat timestamp so UI can show when the last heartbeat arrived
    private val _lastHeartbeatTime = MutableStateFlow<Long?>(null)
    val lastHeartbeatTime: StateFlow<Long?> = _lastHeartbeatTime.asStateFlow()

    private var lastMessageReceivedTime: Long = 0L
    private var healthCheckJob: Job? = null
    private var statsLoggingJob: Job? = null
    private var unitsProcessorJob: Job? = null
    private val unitsChannel = kotlinx.coroutines.channels.Channel<List<NearbyUnit>>(capacity = kotlinx.coroutines.channels.Channel.CONFLATED)
    private val HEARTBEAT_WARNING_MS = 30000L // 30 seconds - show yellow
    private val HEARTBEAT_TIMEOUT_MS = 35000L  // 35 seconds - show red

    // Interarrival time tracking for performance diagnostics
    private var lastPacketTime: Long = 0L
    private val interarrivalTimes = mutableListOf<Long>()  // Store last N interarrival times
    private val MAX_INTERARRIVAL_SAMPLES = 100  // Rolling window size
    private val _minInterarrivalMs = MutableStateFlow<Long?>(null)
    val minInterarrivalMs: StateFlow<Long?> = _minInterarrivalMs.asStateFlow()
    private val _maxInterarrivalMs = MutableStateFlow<Long?>(null)
    val maxInterarrivalMs: StateFlow<Long?> = _maxInterarrivalMs.asStateFlow()
    private val _avgInterarrivalMs = MutableStateFlow<Long?>(null)
    val avgInterarrivalMs: StateFlow<Long?> = _avgInterarrivalMs.asStateFlow()

    // Log statistics (rate limiting) - accumulate counts and log summary every 5 seconds
    @Volatile private var logStatsUdpPacketsReceived = 0
    @Volatile private var logStatsFlightDataReceived = 0
    @Volatile private var logStatsTacticalUnitsProcessed = 0
    private val LOG_SUMMARY_INTERVAL_MS = 5000L // 5 seconds

    // ECDH components
    private val keyManager = KeyManager(context)
    private var encryptionProvider: EcdhEncryption? = null
    private var currentSession: SessionInfo? = null
    private val pendingHandshakeResponses = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<HandshakeMessage>>()
    private val handshakeLock = kotlinx.coroutines.sync.Mutex()

    private var udpSocket: DatagramSocket? = null
    private var receiveJob: Job? = null
    private var handshakeJob: Job? = null
    private var isStarted = false

    // Restore last transient running state if the user had reception running previously
    init {
        // Update device IP address
        _deviceIpAddress.value = getLocalIpAddress()

        // Ensure device key pair exists for ECDH
        try {
            keyManager.getOrCreateDeviceKeyPair()
            Log.i(TAG, "Device key pair ensured for ECDH mode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate/ensure device key pair: ${e.message}", e)
        }

        val lastRunning = prefs.getBoolean(KEY_LAST_RUNNING, false)
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        udpLogD("Init: KEY_LAST_RUNNING=$lastRunning, KEY_ENABLED=$enabled")

        // Only auto-start if user previously had reception running AND the Settings-enabled flag is set
        if (lastRunning && enabled) {
            udpLogD("Restoring running receiver on init")
            scope.launch {
                startInternal(ignoreEnabledCheck = true)
            }
        } else {
            // Ensure runtime state matches persisted preference
            _isRunning.value = false
        }
    }

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
     * Track packet interarrival time and update statistics
     * Excludes heartbeats and extreme outliers for realistic statistics
     */
    private fun trackInterarrival(isHeartbeat: Boolean = false) {
        val now = System.currentTimeMillis()
        if (lastPacketTime > 0L) {
            val interarrival = now - lastPacketTime
            
            // Only track normal data packets (exclude heartbeats and extreme gaps > 5s)
            // Heartbeats arrive every ~30s and would skew statistics
            if (!isHeartbeat && interarrival < 5000) {
                synchronized(interarrivalTimes) {
                    interarrivalTimes.add(interarrival)
                    // Keep only last MAX_INTERARRIVAL_SAMPLES
                    if (interarrivalTimes.size > MAX_INTERARRIVAL_SAMPLES) {
                        interarrivalTimes.removeAt(0)
                    }
                    // Update statistics
                    if (interarrivalTimes.isNotEmpty()) {
                        _minInterarrivalMs.value = interarrivalTimes.minOrNull()
                        _maxInterarrivalMs.value = interarrivalTimes.maxOrNull()
                        _avgInterarrivalMs.value = (interarrivalTimes.average().toLong())
                    }
                }
            }
            
            if (interarrival > 200) {  // Log gaps > 200ms
                udpLogD("Packet gap: ${interarrival}ms${if (isHeartbeat) " (heartbeat)" else ""}")
            }
        }
        lastPacketTime = now
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
    /**
     * Internal start helper. If ignoreEnabledCheck==true this will start the receiver
     * regardless of the persisted "enabled" preference (used for a transient connect).
     */
    private fun startInternal(ignoreEnabledCheck: Boolean = false) {
        if (isStarted) return
        if (!ignoreEnabledCheck && !_isEnabled.value) {
            // Receiver is disabled by user - do not start
            udpLogD("Start requested but receiver is disabled")
            return
        }

        isStarted = true
        _isRunning.value = true

        receiveJob = scope.launch {
            try {
                // Initialize socket FIRST (needed for handshake)
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

                // Initialize encryption provider after ECDH handshake
                encryptionProvider = null // Will be set after handshake

                // Get and log device IP
                val deviceIp = getLocalIpAddress()
                _deviceIpAddress.value = deviceIp
                udpLogD("Device IP address: $deviceIp")

                // Log network info
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                // Use NetworkCapabilities on modern APIs to avoid deprecated NetworkInfo
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val activeNet = cm.activeNetwork
                    val nc = cm.getNetworkCapabilities(activeNet)
                    val connected = nc != null && nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val transport = when {
                        nc?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
                        nc?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CELLULAR"
                        nc?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETHERNET"
                        nc?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH) == true -> "BLUETOOTH"
                        else -> "UNKNOWN"
                    }
                    udpLogD("Active network: $transport, connected: $connected")
                } else {
                    val activeNetwork = cm.activeNetworkInfo
                    udpLogD("Active network: ${activeNetwork?.typeName}, connected: ${activeNetwork?.isConnected}")
                }

                udpLogD("Socket local address: ${udpSocket?.localAddress?.hostAddress}")
                udpLogD("Socket local port: ${udpSocket?.localPort}")
                udpLogD("Waiting for UDP packets on ${if (_bindIp.value.isNotEmpty()) _bindIp.value else deviceIp}:${_udpPort.value}...")

                // Start health check monitoring job
                startHealthCheckJob()

                // Start statistics logging job (logs summary every 5 seconds)
                startStatsLoggingJob()

                // Start sequential units processor job (prevents race conditions)
                startUnitsProcessorJob()

                // Perform ECDH handshake in background (concurrently with receive loop)
                handshakeJob = scope.launch {
                    try {
                        // Abort early if receiver was disabled while scheduling
                        if (!_isEnabled.value && !ignoreEnabledCheck) {
                            udpLogD("Handshake aborted: receiver disabled")
                            return@launch
                        }

                        _handshakeStatus.value = "Initiating handshake..."
                        val success = performHandshake()

                        if (!_isEnabled.value && !ignoreEnabledCheck) {
                            // If user disabled receiver during handshake, ensure we stop and clear state
                            udpLogD("Handshake aborted after running: receiver disabled")
                            stop()
                            return@launch
                        }

                        if (!success) {
                            _handshakeStatus.value = "Handshake failed"
                            udpLogE("Handshake failed, stopping")
                            stop()
                        } else {
                            _handshakeStatus.value = "Handshake successful"
                        }
                    } catch (e: Exception) {
                        _handshakeStatus.value = "Handshake error: ${e.message}"
                        udpLogE("Handshake error: ${e.message}", e)
                        stop()
                    } finally {
                        // Clear reference when done
                        handshakeJob = null
                    }
                }

                val buffer = ByteArray(BUFFER_SIZE)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    try {
                        udpSocket?.receive(packet)
                        val receivedData = packet.data.copyOfRange(0, packet.length)
                        val senderIp = packet.address.hostAddress
                        
                        // Note: trackInterarrival will be called from handleIncomingMessage
                        // after we know if it's a heartbeat or not

                        // Increment counter (summary logged in timer job)
                        logStatsUdpPacketsReceived++

                        // Filter out packets from own IP address (broadcast echoes)
                        if (senderIp == deviceIp) {
                            udpLogD("Ignoring packet from own IP address ($senderIp)")
                            continue
                        }

                        // Handle all incoming messages through a unified handler
                        handleIncomingMessage(receivedData, packet.address, packet.port)

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
        // Set isRunning to false FIRST so UI updates immediately
        _isRunning.value = false
        isStarted = false
        receiveJob?.cancel()
        receiveJob = null
        healthCheckJob?.cancel()
        healthCheckJob = null
        statsLoggingJob?.cancel()
        statsLoggingJob = null
        unitsProcessorJob?.cancel()
        unitsProcessorJob = null
        // Note: We don't close the channel so it can be reused on restart
        _connectionHealth.value = ConnectionHealth.DISCONNECTED
        // Cancel any in-progress handshake job as well
        handshakeJob?.cancel()
        handshakeJob = null
        udpSocket?.close()
        udpSocket = null
        _isConnected.value = false
        _handshakeStatus.value = null
        udpLogD("UDP socket closed, isRunning=$_isRunning")
    }

    /**
     * Connect (start) the receiver transiently without changing persisted settings.
     * This is used by the in-popup power button to start/stop the reception session
     * while leaving the Settings 'enabled' preference untouched.
     */
    fun connect() {
        // Persist that user started reception so it can be restored on next app launch
        prefs.edit().putBoolean(KEY_LAST_RUNNING, true).apply()
        startInternal(ignoreEnabledCheck = true)
    }

    /**
     * Disconnect (stop) the receiver transiently without changing persisted settings.
     */
    fun disconnect() {
        // Persist that user stopped reception so it remains off after restart
        prefs.edit().putBoolean(KEY_LAST_RUNNING, false).apply()
        stop()
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        stop()
        scope.cancel()
    }
    
    /**
     * Start periodic health check to monitor connection status
     * Checks every 5 seconds if messages are still being received
     */
    private fun startHealthCheckJob() {
        healthCheckJob?.cancel()
        lastMessageReceivedTime = System.currentTimeMillis()
        _connectionHealth.value = ConnectionHealth.HEALTHY
        
        healthCheckJob = scope.launch {
            while (isActive) {
                delay(5000) // Check every 5 seconds
                
                val timeSinceLastMessage = System.currentTimeMillis() - lastMessageReceivedTime
                
                _connectionHealth.value = when {
                    timeSinceLastMessage > HEARTBEAT_TIMEOUT_MS -> {
                        udpLogD("⚠️ Connection STALE: No message for ${timeSinceLastMessage / 1000}s")
                        ConnectionHealth.STALE
                    }
                    timeSinceLastMessage > HEARTBEAT_WARNING_MS -> {
                        udpLogD("⚠️ Connection WARNING: No message for ${timeSinceLastMessage / 1000}s")
                        ConnectionHealth.WARNING
                    }
                    else -> {
                        ConnectionHealth.HEALTHY
                    }
                }
            }
        }
    }

    /**
     * Start periodic statistics logging job
     * Logs summary every 5 seconds without blocking the receive loop
     */
    private fun startStatsLoggingJob() {
        statsLoggingJob?.cancel()

        statsLoggingJob = scope.launch {
            while (isActive) {
                delay(LOG_SUMMARY_INTERVAL_MS)

                // Read and reset counters atomically
                val udpPackets = logStatsUdpPacketsReceived
                val flightData = logStatsFlightDataReceived
                val tacticalUnits = logStatsTacticalUnitsProcessed

                logStatsUdpPacketsReceived = 0
                logStatsFlightDataReceived = 0
                logStatsTacticalUnitsProcessed = 0

                // Log summary if there was any activity
                if (udpPackets > 0 || flightData > 0 || tacticalUnits > 0) {
                    udpLogD("📊 5s Summary: UDP packets=${udpPackets}, Flight data=${flightData}, Tactical units=${tacticalUnits}")
                }
            }
        }
    }

    /**
     * Start sequential units processor job
     * Processes tactical units one batch at a time to prevent database race conditions
     */
    private fun startUnitsProcessorJob() {
        unitsProcessorJob?.cancel()

        unitsProcessorJob = scope.launch {
            for (units in unitsChannel) {
                try {
                    processNearbyUnits(units)
                } catch (e: Exception) {
                    udpLogE("Failed to process units batch: ${e.message}", e)
                }
            }
        }
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
     * Enable or disable the UDP receiver. When disabled, the socket is closed and no UDP logs will be emitted.
     */
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _isEnabled.value = enabled
        if (!enabled) {
            // When explicitly disabling via settings, also clear the last-running flag
            prefs.edit().putBoolean(KEY_LAST_RUNNING, false).apply()
        }
        if (enabled) start() else stop()
    }

    fun toggleEnabled() {
        setEnabled(!_isEnabled.value)
    }

    /**
     * Enable or disable entity tracking (tactical units)
     * When disabled, nearbyUnits data will not be processed or stored in the database
     */
    fun setEntityTrackingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENTITY_TRACKING_ENABLED, enabled).apply()
        _isEntityTrackingEnabled.value = enabled
        udpLogD("Entity tracking ${if (enabled) "enabled" else "disabled"}")
        
        // Restart connection to perform new handshake with updated entity tracking status
        if (isStarted) {
            udpLogD("Restarting connection to update entity tracking preference...")
            restart()
        }
    }

    fun toggleEntityTracking() {
        setEntityTrackingEnabled(!_isEntityTrackingEnabled.value)
    }
    
    /**
     * Set tactical units map update interval (how often markers are redrawn on map)
     * @param intervalSeconds Interval in seconds (0.5 to 10.0)
     */
    fun setTacticalUnitsMapUpdateInterval(intervalSeconds: Float) {
        val clamped = intervalSeconds.coerceIn(0.5f, 10.0f)
        prefs.edit().putFloat(KEY_TACTICAL_UNITS_MAP_UPDATE_INTERVAL, clamped).apply()
        _tacticalUnitsMapUpdateInterval.value = clamped
        udpLogD("Tactical units map update interval set to ${clamped}s")
    }
    
    /**
     * Set tactical units live filter (only show units seen in last 10 seconds)
     */
    fun setTacticalUnitsShowLiveOnly(liveOnly: Boolean) {
        prefs.edit().putBoolean(KEY_TACTICAL_UNITS_SHOW_LIVE_ONLY, liveOnly).apply()
        _tacticalUnitsShowLiveOnly.value = liveOnly
        udpLogD("Tactical units live filter ${if (liveOnly) "enabled" else "disabled"}")
    }

    /**
     * Show or hide tactical units on map
     * This is independent from entity tracking - units are still tracked in DB when hidden
     */
    fun setShowTacticalUnitsOnMap(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_TACTICAL_UNITS_ON_MAP, show).apply()
        _showTacticalUnitsOnMap.value = show
        udpLogD("Tactical units map visibility ${if (show) "enabled" else "disabled"}")
    }

    fun toggleTacticalUnitsOnMap() {
        setShowTacticalUnitsOnMap(!_showTacticalUnitsOnMap.value)
    }

    /**
     * Update device name for handshake identification
     */
    fun updateDeviceName(newName: String) {
        prefs.edit().putString(KEY_DEVICE_NAME, newName).apply()
        _deviceName.value = newName
    }

    /**
     * Set server IP address for unicast handshake (more secure than broadcast)
     * @param serverIp Server IP address (empty = use broadcast)
     */
    fun updateServerIp(serverIp: String) {
        prefs.edit().putString("server_ip", serverIp).apply()
        _serverIp.value = serverIp
        if (isStarted) {
            restart()
        }
    }
    
    /**
     * Restart the UDP socket with new settings
     */
    private fun restart() {
        stop()
        start()
    }

    /**
     * Public start wrapper to honor persisted enabled pref by default
     */
    fun start() {
        startInternal(ignoreEnabledCheck = false)
    }
    
    // ========== ECDH Handshake Methods ==========
    
    /**
     * Perform ECDH handshake with DCS server
     * 
     * SECURITY NOTE: Handshake messages are sent in plaintext (standard practice for ECDH)
     * - Passive observers can see: device ID, device name, public keys
     * - Flight data remains encrypted after handshake completes
     * - MITM attacks mitigated by server whitelist and HMAC verification
     * 
     * @return true if handshake successful, false otherwise
     */
    private suspend fun performHandshake(): Boolean = withContext(Dispatchers.IO) {
        return@withContext handshakeLock.withLock {
            try {
                udpLogD("🔐 Starting ECDH handshake...")
                Log.i(TAG, "⚠️ Handshake messages visible on network (standard for ECDH)")
                
                // Clear any old pending responses
                pendingHandshakeResponses.clear()
                
                // Step 1: Send ClientHello
                val clientHello = ClientHello(
                    deviceId = keyManager.getDeviceId(),
                    deviceName = _deviceName.value,
                    publicKey = keyManager.exportPublicKey(),
                    entityTrackingEnabled = _isEntityTrackingEnabled.value
                )
                
                udpLogD("📡 Entity tracking ${if (_isEntityTrackingEnabled.value) "ENABLED" else "DISABLED"} in handshake")
                
                // SECURITY: Prefer unicast over broadcast for server discovery
                val serverAddress = when {
                    // 1st priority: Explicit server IP (most secure)
                    _serverIp.value.isNotEmpty() -> {
                        udpLogD("🎯 Using unicast to server: ${_serverIp.value}")
                        InetAddress.getByName(_serverIp.value)
                    }
                    // 2nd priority: Bind IP (if specified)
                    _bindIp.value.isNotEmpty() -> {
                        udpLogD("🎯 Using bind IP as server: ${_bindIp.value}")
                        InetAddress.getByName(_bindIp.value)
                    }
                    // Fallback: Broadcast discovery (less secure, info leakage)
                    else -> {
                        udpLogD("⚠️ Using broadcast discovery (255.255.255.255)")
                        InetAddress.getByName("255.255.255.255")
                    }
                }
                
                sendHandshakeMessage(clientHello, serverAddress)
                udpLogD("📤 Sent ClientHello to ${serverAddress.hostAddress}")
                
                // Step 2: Wait for ServerHello
                val serverHelloDeferred = CompletableDeferred<HandshakeMessage>()
                pendingHandshakeResponses["ServerHello"] = serverHelloDeferred

                val serverHelloMsg = withTimeout(HANDSHAKE_TIMEOUT_MS) {
                    serverHelloDeferred.await()
                }
                val serverHello = serverHelloMsg as? ServerHello ?: run {
                    udpLogE("Unexpected handshake response type: ${'$'}{serverHelloMsg::class.simpleName}")
                    return@withLock false
                }
            
            if (!serverHello.authorized) {
                udpLogE("❌ Server rejected handshake: ${serverHello.message}")
                return@withLock false
            }
            
            udpLogD("📥 Received ServerHello, authorized: ${serverHello.authorized}")

            // Step 3: Derive session key with server-provided salt
            val serverPublicKey = keyManager.importPublicKey(serverHello.publicKey)
            val salt = serverHello.salt?.let {
                try {
                    Base64.getDecoder().decode(it)
                } catch (e: Exception) {
                    udpLogE("Failed to decode salt: ${e.message}")
                    null
                }
            }
            val sessionKey = keyManager.deriveSessionKey(serverPublicKey, salt)
            
            // SECURITY: Verify server knows the session key (mutual authentication)
            // Server sends HMAC of "server_{sessionId}" with the derived session key
            if (serverHello.serverHmac != null) {
                val expectedData = "server_${serverHello.sessionId}".toByteArray(Charsets.UTF_8)
                val expectedHmac = computeHmac(sessionKey.encoded, expectedData)
                val receivedHmac = try {
                    Base64.getDecoder().decode(serverHello.serverHmac)
                } catch (e: Exception) {
                    udpLogE("Failed to decode serverHmac: ${e.message}")
                    _handshakeStatus.value = "Error: Invalid server HMAC format"
                    return@withLock false
                }
                
                if (!expectedHmac.contentEquals(receivedHmac)) {
                    udpLogE("Server HMAC verification FAILED - possible MITM attack!")
                    _handshakeStatus.value = "Error: Server authentication failed"
                    return@withLock false
                }
                udpLogD("✅ Server HMAC verified - mutual authentication successful")
            } else {
                udpLogD("⚠️ No serverHmac received - mutual authentication skipped")
            }
            
            // Store session info (but don't update encryption provider yet)
            currentSession = SessionInfo(
                sessionId = serverHello.sessionId,
                sessionKey = sessionKey,
                establishedAt = System.currentTimeMillis(),
                serverPublicKey = serverPublicKey,
                aircraft = serverHello.aircraft
            )

            udpLogD("🔑 Session key derived")

            // Step 4: Send KeyConfirm with HMAC proof
            val hmac = computeHmac(sessionKey.encoded, serverHello.sessionId.toByteArray())
            val keyConfirm = KeyConfirm(
                sessionId = serverHello.sessionId,
                hmac = Base64.getEncoder().encodeToString(hmac)
            )

            sendHandshakeMessage(keyConfirm, serverAddress)
            udpLogD("📤 Sent KeyConfirm")

            // Step 5: Wait for Ack (still encrypted with PSK)
            val ackDeferred = CompletableDeferred<HandshakeMessage>()
            pendingHandshakeResponses["Ack"] = ackDeferred

            val ackMsg = withTimeout(HANDSHAKE_TIMEOUT_MS) {
                ackDeferred.await()
            }
            val ack = ackMsg as? Ack ?: run {
                udpLogE("Unexpected handshake response type: ${'$'}{ackMsg::class.simpleName}")
                return@withLock false
            }

            if (ack.status != "ready") {
                udpLogE("❌ Server not ready: ${ack.message}")
                currentSession = null
                encryptionProvider = null
                return@withLock false
            }

            // Verify Ack matches our session
            if (ack.sessionId != serverHello.sessionId) {
                udpLogE("❌ Session ID mismatch in Ack")
                currentSession = null
                encryptionProvider = null
                return@withLock false
            }

            // NOW switch to session key encryption (after handshake complete)
            encryptionProvider = EcdhEncryption(sessionKey)
            udpLogD("✅ Handshake complete! Session: ${serverHello.sessionId}")
            udpLogD("🔒 Now using session key for encryption")
            true
            
        } catch (e: TimeoutCancellationException) {
            udpLogE("⏱️ Handshake timeout - no response from server")
            currentSession = null
            encryptionProvider = null
            false
        } catch (e: Exception) {
            udpLogE("❌ Handshake error: ${e.message}", e)
            currentSession = null
            encryptionProvider = null
            false
        } finally {
            // Don't clear immediately - responses might still arrive
            scope.launch {
                delay(1000)
                pendingHandshakeResponses.clear()
            }
        }
        }
    }
    
    /**
     * Send a handshake message via UDP (PLAINTEXT - no PSK encryption for proper ECDH)
     */
    private fun sendHandshakeMessage(message: HandshakeMessage, address: InetAddress) {
        try {
            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true  // Include fields with default values
            }

            val jsonString = when (message) {
                is ClientHello -> json.encodeToString(message)
                is KeyConfirm -> json.encodeToString(message)
                else -> throw IllegalArgumentException("Unknown handshake message type")
            }
            
            // Send handshake messages in PLAINTEXT (no PSK encryption)
            // This ensures proper ECDH with Perfect Forward Secrecy
            val plainData = jsonString.toByteArray(Charsets.UTF_8)
            
            val packet = DatagramPacket(
                plainData,
                plainData.size,
                address,
                _udpPort.value
            )
            
            udpSocket?.send(packet)
            udpLogD("📤 Sent handshake message: ${message::class.simpleName} [PLAINTEXT]")
        } catch (e: Exception) {
            udpLogE("Error sending handshake message: ${e.message}", e)
        }
    }
    
    /**
     * Handle incoming UDP messages and route to appropriate handler
     * Handshake messages are received in PLAINTEXT
     */
    private fun handleIncomingMessage(data: ByteArray, address: InetAddress, port: Int) {
        val senderIp = address.hostAddress
        
        // Try parsing as PLAINTEXT (for handshake) first
        try {
            val plaintextMessage = String(data, Charsets.UTF_8)
            if (plaintextMessage.contains("ServerHello")) {
                val serverHello = json.decodeFromString<ServerHello>(plaintextMessage)
                udpLogD("📥 Received ServerHello from $senderIp, completing handshake deferred")
                pendingHandshakeResponses["ServerHello"]?.complete(serverHello)
                return
            }
            if (plaintextMessage.contains("\"Ack\"")) {
                val ack = json.decodeFromString<Ack>(plaintextMessage)
                udpLogD("📥 Received Ack from $senderIp, completing handshake deferred")
                pendingHandshakeResponses["Ack"]?.complete(ack)
                return
            }
            if (plaintextMessage.contains("\"Error\"")) {
                val error = json.decodeFromString<HandshakeError>(plaintextMessage)
                udpLogE("Server error from $senderIp: ${error.error} - ${error.message}")
                return
            }
        } catch (e: Exception) {
            // Not a valid plaintext handshake message, proceed to decryption
        }

        // If not a handshake message, assume it's encrypted flight data
        val provider = encryptionProvider
        if (provider == null) {
            udpLogD("⚠️ Ignoring packet from $senderIp - no encryption provider yet (handshake in progress?)")
            return
        }
        
        val decryptedData = provider.decrypt(data)

        if (decryptedData != null) {
            val message = String(decryptedData, Charsets.UTF_8)
            // Decryption successful - count in stats (logged in summary)

            try {
                val flightData = json.decodeFromString<FlightData>(message)
                if (currentSession == null) {
                    udpLogE("Received flight data from $senderIp but no active session - rejecting")
                } else {
                    // Update last message received time for health monitoring
                    lastMessageReceivedTime = System.currentTimeMillis()
                    
                    // Check if this is a heartbeat message
                    if (flightData.type == "heartbeat") {
                        trackInterarrival(isHeartbeat = true)
                        udpLogD("💓 Received heartbeat from $senderIp: ${flightData.message}")
                        // Update connection health but don't update flight data
                        _connectionHealth.value = ConnectionHealth.HEALTHY
                        _lastHeartbeatTime.value = System.currentTimeMillis()
                    } else {
                        trackInterarrival(isHeartbeat = false)
                        // Normal flight data
                        _flightData.value = flightData
                        _lastUpdateTime.value = System.currentTimeMillis()
                        _isConnected.value = true
                        _connectionHealth.value = ConnectionHealth.HEALTHY
                        // Also update heartbeat timestamp for normal messages (treated as activity)
                        _lastHeartbeatTime.value = System.currentTimeMillis()

                        // Process nearby units if present AND entity tracking is enabled
                        if (_isEntityTrackingEnabled.value) {
                            flightData.nearbyUnits?.let { units ->
                                // Send to sequential processor (CONFLATED channel keeps only latest)
                                unitsChannel.trySend(units)
                            }
                        }

                        // Increment counter instead of logging each flight data packet
                        logStatsFlightDataReceived++
                    }
                }
            } catch (e: Exception) {
                udpLogE("Failed to parse flight data from $senderIp: ${e.message}", e)
                udpLogE("Raw message: $message")
            }
        } else {
            udpLogE("❌ Failed to decrypt packet from $senderIp - rejecting (encryption enforced)")
        }
    }
    
    /**
     * Compute HMAC-SHA256 for key confirmation
     */
    private fun computeHmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key, "HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(data)
    }
    
    /**
     * Get current session info (null if no session)
     */
    fun getCurrentSession(): SessionInfo? = currentSession
    
    /**
     * Get device ID for display in UI
     */
    fun getDeviceId(): String = keyManager.getDeviceId()

    /**
     * Export device public key as Base64 string (for adding to server's authorized list)
     */
    fun getPublicKey(): String = keyManager.exportPublicKey()
    
    /**
     * Process nearby units from DCS and store in tactical database
     * - New units: INSERT + History entry
     * - Known units: UPDATE position + History entry
     * - Missing units: Mark as inactive (isActive=0)
     */
    private suspend fun processNearbyUnits(units: List<NearbyUnit>) = withContext(Dispatchers.IO) {
        try {
            val now = Instant.now().toString()
            val dao = tacticalDb.tacticalUnitsDao()
            val historyDao = tacticalDb.tacticalUnitHistoryDao()
            
            // Track which DCS IDs are present in this update
            val currentDcsIds = units.map { it.dcsId }.toSet()
            
            // Process each received unit
            for (unit in units) {
                val existing = dao.getUnitByDcsId(unit.dcsId)
                
                if (existing != null) {
                    // Unit exists - update position and category
                    dao.updateUnitPosition(
                        dcsId = unit.dcsId,
                        latitude = unit.latitude,
                        longitude = unit.longitude,
                        altitude = unit.altitude,
                        heading = unit.heading,
                        speed = unit.speed,
                        distance = unit.distance,
                        bearing = unit.bearing,
                        health = unit.health,
                        category = unit.category,
                        lastSeenAt = now,
                        lastUpdateAt = now
                    )

                    // Add history entry (non-critical, ignore errors)
                    try {
                        historyDao.insertHistory(
                            TacticalUnitHistoryEntity(
                                unitId = existing.id,
                                latitude = unit.latitude,
                                longitude = unit.longitude,
                                altitude = unit.altitude,
                                heading = unit.heading,
                                speed = unit.speed,
                                timestamp = now
                            )
                        )
                    } catch (e: Exception) {
                        // Ignore history insert errors (e.g., foreign key violations during race conditions)
                    }
                } else {
                    // New unit - insert
                    val newId = dao.insertUnit(
                        TacticalUnitEntity(
                            dcsId = unit.dcsId,
                            name = unit.name,
                            type = unit.type,
                            category = unit.category,
                            coalition = unit.coalition,
                            latitude = unit.latitude,
                            longitude = unit.longitude,
                            altitude = unit.altitude,
                            heading = unit.heading,
                            speed = unit.speed,
                            distance = unit.distance,
                            bearing = unit.bearing,
                            health = unit.health,
                            country = unit.country,
                            groupName = unit.group,
                            pilotName = unit.pilot,
                            isActive = 1,
                            firstSeenAt = now,
                            lastSeenAt = now,
                            lastUpdateAt = now
                        )
                    )

                    // Add initial history entry (non-critical, ignore errors)
                    try {
                        historyDao.insertHistory(
                            TacticalUnitHistoryEntity(
                                unitId = newId.toInt(),
                                latitude = unit.latitude,
                                longitude = unit.longitude,
                                altitude = unit.altitude,
                                heading = unit.heading,
                                speed = unit.speed,
                                timestamp = now
                            )
                        )
                    } catch (e: Exception) {
                        // Ignore history insert errors (e.g., foreign key violations during race conditions)
                    }
                }
            }
            
            // Mark units that are no longer visible as inactive
            // (Units not in currentDcsIds but still marked as active in DB)
            // This is done by getting all active units and checking if they're in the update
            // We do this in a background job to avoid blocking
            // For now, we'll mark ALL active units that aren't in the update as inactive
            // A more sophisticated approach would track "last seen" time and only mark as inactive after a timeout

            // Increment counter instead of logging each batch
            logStatsTacticalUnitsProcessed += units.size
        } catch (e: Exception) {
            udpLogE("Failed to process nearby units: ${e.message}", e)
        }
    }
}
