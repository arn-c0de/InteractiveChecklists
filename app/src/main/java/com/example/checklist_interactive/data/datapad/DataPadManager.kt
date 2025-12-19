package com.example.checklist_interactive.data.datapad

import android.content.Context
import android.util.Log
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
        private const val BUFFER_SIZE = 4096
        private const val SOCKET_TIMEOUT_MS = 1000

        private const val PREFS_NAME = "datapad_settings"
        private const val KEY_UDP_PORT = "udp_port"
        private const val KEY_BIND_IP = "bind_ip"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DEVICE_NAME = "device_name"
        // Persist last transient reception state (true if receiver was running when app exited)
        private const val KEY_LAST_RUNNING = "last_running"

        // Handshake timeout
        private const val HANDSHAKE_TIMEOUT_MS = 10000L
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
                        udpLogD("UDP packet received (${packet.length} bytes) from ${packet.address.hostAddress}:${packet.port}")

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
                    publicKey = keyManager.exportPublicKey()
                )
                
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
        // Try parsing as PLAINTEXT (for handshake) first
        try {
            val plaintextMessage = String(data, Charsets.UTF_8)
            if (plaintextMessage.contains("ServerHello")) {
                val serverHello = json.decodeFromString<ServerHello>(plaintextMessage)
                udpLogD("📥 Received ServerHello, completing handshake deferred")
                pendingHandshakeResponses["ServerHello"]?.complete(serverHello)
                return
            }
            if (plaintextMessage.contains("\"Ack\"")) {
                val ack = json.decodeFromString<Ack>(plaintextMessage)
                udpLogD("📥 Received Ack, completing handshake deferred")
                pendingHandshakeResponses["Ack"]?.complete(ack)
                return
            }
            if (plaintextMessage.contains("\"Error\"")) {
                val error = json.decodeFromString<HandshakeError>(plaintextMessage)
                udpLogE("Server error: ${error.error} - ${error.message}")
                return
            }
        } catch (e: Exception) {
            // Not a valid plaintext handshake message, proceed to decryption
        }

        // If not a handshake message, assume it's encrypted flight data
        val provider = encryptionProvider
        val decryptedData = provider?.decrypt(data)

        if (decryptedData != null) {
            val message = String(decryptedData, Charsets.UTF_8)
            val method = provider.getMethod()
            udpLogD("Received decrypted message (length=${message.length} bytes, method=$method)")

            try {
                val flightData = json.decodeFromString<FlightData>(message)
                if (currentSession == null) {
                    udpLogE("Received flight data but no active session - rejecting")
                } else {
                    _flightData.value = flightData
                    _lastUpdateTime.value = System.currentTimeMillis()
                    _isConnected.value = true
                    currentSession?.let {
                        udpLogD("✅ Received encrypted flight data: ${flightData.aircraft} at ${flightData.altitude}m (session: ${it.sessionId.take(8)}...)")
                    } ?: udpLogD("✅ Received encrypted flight data: ${flightData.aircraft} at ${flightData.altitude}m")
                }
            } catch (e: Exception) {
                udpLogE("Failed to parse flight data: ${e.message}", e)
                udpLogE("Raw message: $message")
            }
        } else {
            udpLogE("❌ Failed to decrypt packet - rejecting (encryption enforced)")
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
}
