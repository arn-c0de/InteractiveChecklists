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
        private const val KEY_PRE_SHARED_KEY = "pre_shared_key"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_USE_ECDH = "use_ecdh"
        private const val KEY_DEVICE_NAME = "device_name"
        
        // Default Pre-Shared Key (32 bytes for AES-256)
        private const val DEFAULT_PRE_SHARED_KEY = "DCS_DataPad_Secret_Key_32BYTES!!"
        
        // Handshake timeout
        private const val HANDSHAKE_TIMEOUT_MS = 10000L
        
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
    
    private val _useEcdh = MutableStateFlow(prefs.getBoolean(KEY_USE_ECDH, false))
    val useEcdh: StateFlow<Boolean> = _useEcdh.asStateFlow()
    
    private val _deviceName = MutableStateFlow(prefs.getString(KEY_DEVICE_NAME, "Android Tablet") ?: "Android Tablet")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _serverIp = MutableStateFlow(prefs.getString("server_ip", "") ?: "")
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()

    private val _handshakeStatus = MutableStateFlow<String?>(null)
    val handshakeStatus: StateFlow<String?> = _handshakeStatus.asStateFlow()
    
    private var udpSocket: DatagramSocket? = null
    private var receiveJob: Job? = null
    private var isStarted = false
    
    // ECDH components
    private val keyManager = KeyManager(context)
    private var encryptionProvider: EncryptionProvider? = null
    private var currentSession: SessionInfo? = null
    private val pendingHandshakeResponses = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<HandshakeMessage>>()
    private val handshakeLock = kotlinx.coroutines.sync.Mutex()

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
                
                // Initialize encryption provider based on mode
                encryptionProvider = if (_useEcdh.value) {
                    null // Will be set after handshake
                } else {
                    PskEncryption(_preSharedKey.value)
                }

                // Get and log device IP
                val deviceIp = getLocalIpAddress()
                _deviceIpAddress.value = deviceIp
                udpLogD("Device IP address: $deviceIp")
                
                // Log network info
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val activeNetwork = cm.activeNetworkInfo
                udpLogD("Active network: ${activeNetwork?.typeName}, connected: ${activeNetwork?.isConnected}")
                
                udpLogD("Socket local address: ${udpSocket?.localAddress?.hostAddress}")
                udpLogD("Socket local port: ${udpSocket?.localPort}")
                udpLogD("Waiting for UDP packets on ${if (_bindIp.value.isNotEmpty()) _bindIp.value else deviceIp}:${_udpPort.value}...")

                // If ECDH mode, perform handshake in background (concurrently with receive loop)
                if (_useEcdh.value) {
                    scope.launch {
                        try {
                            _handshakeStatus.value = "Initiating handshake..."
                            val success = performHandshake()
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
                        }
                    }
                }

                val buffer = ByteArray(BUFFER_SIZE)
                val packet = DatagramPacket(buffer, buffer.size)
                
                while (isActive) {
                    try {
                        udpSocket?.receive(packet)
                        val receivedData = packet.data.copyOfRange(0, packet.length)
                        udpLogD("UDP packet received (${packet.length} bytes) from ${packet.address.hostAddress}:${packet.port}")

                        // SECURITY FIX: Only accept plaintext handshake messages DURING handshake phase
                        // CRITICAL: After session is established, ALL messages MUST be encrypted
                        // Note: encryptionProvider is set AFTER Ack is received (line 570), not when currentSession is set
                        val isHandshakePhase = _useEcdh.value && encryptionProvider == null

                        if (isHandshakePhase) {
                            // ONLY during ECDH handshake: Try to parse as PLAINTEXT handshake message
                            // Handshake messages are NEVER encrypted for proper ECDH Perfect Forward Secrecy
                            try {
                                val plaintextMessage = String(receivedData, Charsets.UTF_8)
                                if (plaintextMessage.contains("\"type\":") &&
                                    (plaintextMessage.contains("ServerHello") ||
                                     plaintextMessage.contains("Ack") ||
                                     plaintextMessage.contains("Error"))) {
                                    // This is a handshake response - handle as PLAINTEXT
                                    udpLogD("📥 Received handshake message (PLAINTEXT): ${plaintextMessage.take(100)}")
                                    handleIncomingMessage(plaintextMessage, packet.address, packet.port)
                                    continue // Skip decryption for handshake messages
                                }
                            } catch (e: Exception) {
                                // Not a valid UTF-8 string, probably encrypted data - continue to decryption
                            }
                        }

                        // SECURITY FIX: Enforce encryption - NO plaintext fallback!
                        // Use the configured encryption provider (PSK or ECDH session key)
                        val provider = encryptionProvider
                        val decryptedData = if (provider != null) {
                            // Use configured encryption provider
                            provider.decrypt(receivedData)
                        } else if (!_useEcdh.value) {
                            // ONLY in PSK mode: fallback to PSK if no provider set
                            val keyBytes = _preSharedKey.value.toByteArray(Charsets.UTF_8)
                            decryptPayload(receivedData, keyBytes)
                        } else {
                            // ECDH mode but no session key: REJECT
                            null
                        }

                        if (decryptedData != null) {
                            val message = String(decryptedData, Charsets.UTF_8)
                            udpLogD("Decrypted message: ${message.take(200)}")

                            // Parse as flight data
                            try {
                                val data = json.decodeFromString<FlightData>(message)

                                // If ECDH mode, verify we have active session
                                if (_useEcdh.value && currentSession == null) {
                                    udpLogE("Received flight data but no active session - rejecting")
                                } else {
                                    _flightData.value = data
                                    _lastUpdateTime.value = System.currentTimeMillis()
                                    _isConnected.value = true
                                    currentSession?.let {
                                        udpLogD("✅ Received encrypted flight data: ${data.aircraft} at ${data.altitude}m (session: ${it.sessionId.take(8)}...)")
                                    } ?: udpLogD("✅ Received encrypted flight data: ${data.aircraft} at ${data.altitude}m")
                                }
                            } catch (e: Exception) {
                                udpLogE("Failed to parse flight data: ${e.message}", e)
                                udpLogE("Raw message: $message")
                            }
                        } else {
                            // SECURITY: Decryption failed - REJECT packet (no plaintext fallback!)
                            udpLogE("❌ Failed to decrypt packet - rejecting (encryption enforced)")
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
     * Update device name for handshake identification
     */
    fun updateDeviceName(newName: String) {
        prefs.edit().putString(KEY_DEVICE_NAME, newName).apply()
        _deviceName.value = newName
    }
    
    /**
     * Enable or disable ECDH handshake mode
     */
    fun setUseEcdh(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_ECDH, enabled).apply()
        _useEcdh.value = enabled
        if (isStarted) {
            restart()
        }
    }

    /**
     * Set server IP address for unicast handshake (more secure than broadcast)
     * @param serverIp Server IP address (empty = use broadcast)
     */
    fun updateServerIp(serverIp: String) {
        prefs.edit().putString("server_ip", serverIp).apply()
        _serverIp.value = serverIp
        if (isStarted && _useEcdh.value) {
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
    
    // ========== ECDH Handshake Methods ==========
    
    /**
     * Perform ECDH handshake with DCS server
     * @return true if handshake successful, false otherwise
     */
    private suspend fun performHandshake(): Boolean = withContext(Dispatchers.IO) {
        return@withContext handshakeLock.withLock {
            try {
                udpLogD("🔐 Starting ECDH handshake...")
                
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
                val serverHelloDeferred = CompletableDeferred<ServerHello>()
                pendingHandshakeResponses["ServerHello"] = serverHelloDeferred as CompletableDeferred<HandshakeMessage>
                
                val serverHello = withTimeout(HANDSHAKE_TIMEOUT_MS) {
                    serverHelloDeferred.await()
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
            val ackDeferred = CompletableDeferred<Ack>()
            pendingHandshakeResponses["Ack"] = ackDeferred as CompletableDeferred<HandshakeMessage>

            val ack = withTimeout(HANDSHAKE_TIMEOUT_MS) {
                ackDeferred.await()
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
     * Handshake messages are received in PLAINTEXT (no PSK encryption)
     */
    private fun handleIncomingMessage(message: String, address: InetAddress, port: Int) {
        try {
            // Try to parse as different message types (handle both compact and formatted JSON)
            // Note: Handshake messages (ServerHello, Ack) are in PLAINTEXT
            when {
                message.contains("ServerHello") -> {
                    val serverHello = json.decodeFromString<ServerHello>(message)
                    udpLogD("📥 Received ServerHello, completing handshake deferred")
                    pendingHandshakeResponses["ServerHello"]?.complete(serverHello)
                }
                message.contains("\"Ack\"") -> {
                    val ack = json.decodeFromString<Ack>(message)
                    udpLogD("📥 Received Ack, completing handshake deferred")
                    pendingHandshakeResponses["Ack"]?.complete(ack)
                }
                message.contains("\"Error\"") -> {
                    val error = json.decodeFromString<HandshakeError>(message)
                    udpLogE("Server error: ${error.error} - ${error.message}")
                }
            }
        } catch (e: Exception) {
            udpLogE("Error handling incoming message: ${e.message}", e)
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
}
