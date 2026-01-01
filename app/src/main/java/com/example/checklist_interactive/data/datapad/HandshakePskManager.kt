package com.example.checklist_interactive.data.datapad

import android.content.Context
import android.util.Log
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * Manages pre-shared keys for handshake encryption
 */
class HandshakePskManager(private val context: Context) {
    companion object {
        private const val TAG = "HandshakePskManager"
        private const val PREFS_NAME = "handshake_psk"
        private const val KEY_PSK = "psk_key"
        private const val KEY_EXPIRES = "psk_expires"
        private const val KEY_SERVER_IP = "psk_server_ip"
        private const val NONCE_SIZE = 12
        private const val TAG_SIZE = 128
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Store a PSK received from QR code
     */
    fun storePsk(serverIp: String, pskBase64: String, expiresAt: Long): Boolean {
        return try {
            // Validate PSK format (should be 32 bytes base64 encoded)
            val pskBytes = Base64.getDecoder().decode(pskBase64)
            if (pskBytes.size != 32) {
                Log.e(TAG, "Invalid PSK size: ${pskBytes.size} (expected 32)")
                return false
            }
            
            prefs.edit()
                .putString(KEY_PSK, pskBase64)
                .putLong(KEY_EXPIRES, expiresAt)
                .putString(KEY_SERVER_IP, serverIp)
                .apply()
            
            Log.i(TAG, "✅ Stored PSK for $serverIp (expires: $expiresAt)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store PSK: ${e.message}")
            false
        }
    }
    
    /**
     * Get current PSK if valid
     */
    fun getPsk(serverIp: String): ByteArray? {
        try {
            val storedIp = prefs.getString(KEY_SERVER_IP, null)
            val pskBase64 = prefs.getString(KEY_PSK, null)
            val expiresAt = prefs.getLong(KEY_EXPIRES, 0)
            
            if (storedIp != serverIp) {
                Log.w(TAG, "PSK is for different server: $storedIp (expected: $serverIp)")
                return null
            }
            
            if (System.currentTimeMillis() > expiresAt) {
                Log.w(TAG, "PSK expired at $expiresAt")
                clearPsk()
                return null
            }
            
            if (pskBase64 == null) {
                return null
            }
            
            return Base64.getDecoder().decode(pskBase64)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get PSK: ${e.message}")
            return null
        }
    }
    
    /**
     * Check if we have a valid PSK for the server
     */
    fun hasPsk(serverIp: String): Boolean {
        return getPsk(serverIp) != null
    }
    
    /**
     * Clear stored PSK
     */
    fun clearPsk() {
        prefs.edit().clear().apply()
        Log.i(TAG, "🗑️ Cleared PSK")
    }
    
    /**
     * Encrypt handshake message with PSK
     */
    fun encryptHandshake(plaintext: ByteArray, psk: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_SIZE)
        SecureRandom().nextBytes(nonce)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(psk, "AES")
        val gcmSpec = GCMParameterSpec(TAG_SIZE, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        
        val ciphertext = cipher.doFinal(plaintext)
        
        // Return: nonce (12 bytes) + ciphertext + tag (16 bytes)
        return nonce + ciphertext
    }
    
    /**
     * Decrypt handshake message with PSK
     */
    fun decryptHandshake(encryptedData: ByteArray, psk: ByteArray): ByteArray? {
        return try {
            if (encryptedData.size < NONCE_SIZE + 16) {
                Log.e(TAG, "Encrypted data too short")
                return null
            }
            
            val nonce = encryptedData.copyOfRange(0, NONCE_SIZE)
            val ciphertext = encryptedData.copyOfRange(NONCE_SIZE, encryptedData.size)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(psk, "AES")
            val gcmSpec = GCMParameterSpec(TAG_SIZE, nonce)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: ${e.message}")
            null
        }
    }
}
