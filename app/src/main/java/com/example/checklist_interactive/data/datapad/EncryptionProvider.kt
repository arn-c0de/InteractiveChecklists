package com.example.checklist_interactive.data.datapad

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

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
 * PSK-based AES-GCM encryption (legacy method)
 * Uses a pre-shared 256-bit key for encryption/decryption
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
    
    override fun encrypt(data: ByteArray): ByteArray {
        // Generate random nonce
        val nonce = ByteArray(NONCE_SIZE)
        java.security.SecureRandom().nextBytes(nonce)
        
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
    
    override fun getMethod(): String = "PSK-AES-GCM"
}

/**
 * ECDH-based AES-GCM encryption
 * Uses a session key derived from ECDH key exchange
 */
class EcdhEncryption(private val sessionKey: SecretKey) : EncryptionProvider {
    companion object {
        private const val TAG = "EcdhEncryption"
        private const val NONCE_SIZE = 12 // 96 bits for GCM
        private const val TAG_SIZE = 128 // 128-bit authentication tag
    }
    
    override fun encrypt(data: ByteArray): ByteArray {
        // Generate random nonce
        val nonce = ByteArray(NONCE_SIZE)
        java.security.SecureRandom().nextBytes(nonce)
        
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
    
    override fun getMethod(): String = "ECDH-AES-GCM"
}
