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
    private val highestCounter = AtomicLong(-1)
    private val WINDOW: Long = 10000L // sliding window size for replay protection

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
     * - Validates sender prefix (must be the remote sender id)
     * - Uses sliding-window replay protection (bounded memory)
     * @return true if nonce is valid (not replayed), false otherwise
     */
    fun validateNonce(nonce: ByteArray, maxAge: Long = 10000): Boolean {
        if (nonce.size != 12) return false

        // Validate sender prefix: incoming nonce must be from the OTHER party
        val senderByte = nonce[0]
        val expectedSender: Byte = if (senderId == SENDER_ID_CLIENT) SENDER_ID_SERVER else SENDER_ID_CLIENT
        if (senderByte != expectedSender) {
            android.util.Log.e("GcmNonceGenerator", "Invalid nonce sender id: $senderByte - expected $expectedSender")
            return false
        }

        // Extract counter from nonce
        val buffer = ByteBuffer.wrap(nonce)
        buffer.position(4) // Skip sender ID and reserved bytes
        val receivedCounter = buffer.long

        synchronized(receivedCounters) {
            val highest = highestCounter.get()

            // Reject counters that are far in the past (stale/too old)
            if (highest >= 0 && receivedCounter <= (highest - WINDOW)) {
                android.util.Log.w("GcmNonceGenerator", "Stale nonce detected (too old): $receivedCounter (highest: $highest)")
                return false
            }

            // Check for replay
            if (receivedCounter in receivedCounters) {
                android.util.Log.w("GcmNonceGenerator", "Replay attack detected! Counter: $receivedCounter")
                return false
            }

            // Add to seen set and update highest
            receivedCounters.add(receivedCounter)
            if (receivedCounter > highest) {
                highestCounter.set(receivedCounter)
            }

            // Cleanup old counters (keep only sliding window)
            val minAllowed = kotlin.math.max(0L, highestCounter.get() - WINDOW)
            val toRemove = receivedCounters.filter { it < minAllowed }
            if (toRemove.isNotEmpty()) {
                receivedCounters.removeAll(toRemove.toSet())
            }
        }

        return true
    }
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
