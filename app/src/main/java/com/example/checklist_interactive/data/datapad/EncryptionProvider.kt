package com.example.checklist_interactive.data.datapad

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Interface for encryption providers
 * Allows switching between PSK and ECDH encryption
 */
interface EncryptionProvider {
    /**
     * Encrypt data using the provider's encryption method
     * @param data Plain data to encrypt
     * @return Encrypted data (format depends on implementation)
     */
    fun encrypt(data: ByteArray): ByteArray

    /**
     * Decrypt data using the provider's decryption method
     * @param encryptedData Encrypted data to decrypt
     * @return Decrypted plain data, or null if decryption fails
     */
    fun decrypt(encryptedData: ByteArray): ByteArray?

    /**
     * Get the encryption method name for logging
     */
    fun getMethod(): String
}

/**
 * Nonce generator for AES-GCM that prevents nonce reuse
 * Uses counter-based nonces with sender ID prefix
 * Format: [sender_id:1][reserved:3][counter:8] = 12 bytes
 */
class GcmNonceGenerator(private val senderId: Byte) {
    companion object {
        const val SENDER_ID_CLIENT: Byte = 0x00
        const val SENDER_ID_SERVER: Byte = 0x01
    }

    // Thread-safe counter
    private val counter = AtomicLong(0)
    private val receivedCounters = mutableSetOf<Long>()

    /**
     * Generate next nonce with monotonic counter
     * Thread-safe via AtomicLong
     */
    fun generateNonce(): ByteArray {
        val count = counter.incrementAndGet()
        if (count == Long.MAX_VALUE) {
            throw SecurityException("Nonce counter exhausted - re-key required!")
        }

        return ByteBuffer.allocate(12).apply {
            put(senderId)           // Byte 0: Sender ID
            put(0)                  // Bytes 1-3: Reserved (zeros)
            put(0)
            put(0)
            putLong(count)          // Bytes 4-11: Counter
        }.array()
    }

    /**
     * Validate received nonce to detect replay attacks
     * @return true if nonce is valid (not replayed), false otherwise
     */
    fun validateNonce(nonce: ByteArray, maxAge: Long = 10000): Boolean {
        if (nonce.size != 12) return false

        // Extract counter from nonce
        val buffer = ByteBuffer.wrap(nonce)
        buffer.position(4) // Skip sender ID and reserved bytes
        val receivedCounter = buffer.long

        synchronized(receivedCounters) {
            // Check for replay
            if (receivedCounter in receivedCounters) {
                android.util.Log.w("GcmNonceGenerator", "Replay attack detected! Counter: $receivedCounter")
                return false
            }

            // Add to seen set
            receivedCounters.add(receivedCounter)

            // Cleanup old counters (prevent memory leak)
            if (receivedCounters.size > maxAge) {
                val toRemove = receivedCounters.sorted().take((maxAge / 2).toInt())
                receivedCounters.removeAll(toRemove.toSet())
            }
        }

        return true
    }
}

/**
 * PSK-based AES-GCM encryption (legacy method)
 * Uses a pre-shared 256-bit key for encryption/decryption
 * NOW WITH COUNTER-BASED NONCES (prevents nonce collision)
 */
class PskEncryption(private val preSharedKey: String) : EncryptionProvider {
    companion object {
        private const val TAG = "PskEncryption"
        private const val NONCE_SIZE = 12 // 96 bits for GCM
        private const val TAG_SIZE = 128 // 128-bit authentication tag
    }

    private val keySpec: SecretKeySpec = SecretKeySpec(
        preSharedKey.toByteArray(Charsets.UTF_8),
        "AES"
    )

    // Counter-based nonce generator (CLIENT side)
    private val nonceGenerator = GcmNonceGenerator(GcmNonceGenerator.SENDER_ID_CLIENT)

    override fun encrypt(data: ByteArray): ByteArray {
        // Generate counter-based nonce (NO COLLISION POSSIBLE)
        val nonce = nonceGenerator.generateNonce()

        // Initialize AES-GCM cipher
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_SIZE, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        // Encrypt data
        val ciphertext = cipher.doFinal(data)

        // Return: nonce (12 bytes) + ciphertext + tag (16 bytes)
        return nonce + ciphertext
    }

    override fun decrypt(encryptedData: ByteArray): ByteArray? {
        return try {
            if (encryptedData.size < NONCE_SIZE + 16) { // 12 (nonce) + 16 (tag) minimum
                return null
            }

            // Extract nonce (first 12 bytes)
            val nonce = encryptedData.copyOfRange(0, NONCE_SIZE)

            // SECURITY: Validate nonce to prevent replay attacks
            if (!nonceGenerator.validateNonce(nonce)) {
                android.util.Log.e(TAG, "❌ Replay attack detected - rejecting message")
                return null
            }

            // Extract ciphertext + tag (remaining bytes)
            val ciphertext = encryptedData.copyOfRange(NONCE_SIZE, encryptedData.size)

            // Initialize AES-GCM cipher
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(TAG_SIZE, nonce)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

            // Decrypt and verify
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Decryption failed: ${e.message}")
            null
        }
    }

    override fun getMethod(): String = "PSK-AES-GCM-CTR"
}

/**
 * ECDH-based AES-GCM encryption
 * Uses a session key derived from ECDH key exchange
 * NOW WITH COUNTER-BASED NONCES (prevents nonce collision)
 */
class EcdhEncryption(private val sessionKey: SecretKey) : EncryptionProvider {
    companion object {
        private const val TAG = "EcdhEncryption"
        private const val NONCE_SIZE = 12 // 96 bits for GCM
        private const val TAG_SIZE = 128 // 128-bit authentication tag
    }

    // Counter-based nonce generator (CLIENT side)
    private val nonceGenerator = GcmNonceGenerator(GcmNonceGenerator.SENDER_ID_CLIENT)

    override fun encrypt(data: ByteArray): ByteArray {
        // Generate counter-based nonce (NO COLLISION POSSIBLE)
        val nonce = nonceGenerator.generateNonce()

        // Initialize AES-GCM cipher
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_SIZE, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey, gcmSpec)

        // Encrypt data
        val ciphertext = cipher.doFinal(data)

        // Return: nonce (12 bytes) + ciphertext + tag (16 bytes)
        return nonce + ciphertext
    }

    override fun decrypt(encryptedData: ByteArray): ByteArray? {
        return try {
            if (encryptedData.size < NONCE_SIZE + 16) { // 12 (nonce) + 16 (tag) minimum
                return null
            }

            // Extract nonce (first 12 bytes)
            val nonce = encryptedData.copyOfRange(0, NONCE_SIZE)

            // SECURITY: Validate nonce to prevent replay attacks
            if (!nonceGenerator.validateNonce(nonce)) {
                android.util.Log.e(TAG, "❌ Replay attack detected - rejecting message")
                return null
            }

            // Extract ciphertext + tag (remaining bytes)
            val ciphertext = encryptedData.copyOfRange(NONCE_SIZE, encryptedData.size)

            // Initialize AES-GCM cipher
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(TAG_SIZE, nonce)
            cipher.init(Cipher.DECRYPT_MODE, sessionKey, gcmSpec)

            // Decrypt and verify
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Decryption failed: ${e.message}")
            null
        }
    }

    override fun getMethod(): String = "ECDH-AES-GCM-CTR"
}
