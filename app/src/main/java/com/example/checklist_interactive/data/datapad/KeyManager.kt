package com.example.checklist_interactive.data.datapad

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.*
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Manages ECDH key pairs and session key derivation for secure DataPad communication
 * Uses Android KeyStore for secure key storage
 */
class KeyManager(private val context: Context) {
    companion object {
        private const val TAG = "KeyManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "datapad_device_key"
        private const val EC_CURVE = "secp256r1" // NIST P-256
        
        // HKDF parameters
        private const val HKDF_INFO = "DataPad-Session-Key"
        private const val AES_KEY_SIZE = 32 // 256 bits
    }
    
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }
    
    /**
     * Get or create a persistent device key pair stored in Android KeyStore
     * This key pair is used to identify the device and derive session keys
     */
    fun getOrCreateDeviceKeyPair(): KeyPair {
        return try {
            // Try to load existing key
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
            val publicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey
            
            if (privateKey != null && publicKey != null) {
                Log.d(TAG, "Loaded existing device key pair from KeyStore")
                return KeyPair(publicKey, privateKey)
            }
            
            // Generate new key pair if none exists
            generateDeviceKeyPair()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading device key pair, generating new one: ${e.message}")
            generateDeviceKeyPair()
        }
    }
    
    /**
     * Generate a new EC key pair in Android KeyStore
     */
    private fun generateDeviceKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_AGREE_KEY
        ).apply {
            setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
            setUserAuthenticationRequired(false) // No biometric required
            setRandomizedEncryptionRequired(true)
        }.build()
        
        keyPairGenerator.initialize(parameterSpec)
        val keyPair = keyPairGenerator.generateKeyPair()
        
        Log.d(TAG, "Generated new device key pair in KeyStore")
        return keyPair
    }
    
    /**
     * Derive an AES-256 session key from the peer's public key using ECDH
     * @param peerPublicKey The other party's EC public key
     * @return A 256-bit AES key derived from the shared secret
     */
    fun deriveSessionKey(peerPublicKey: PublicKey): SecretKey {
        try {
            val deviceKeyPair = getOrCreateDeviceKeyPair()
            
            // Perform ECDH key agreement
            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(deviceKeyPair.private)
            keyAgreement.doPhase(peerPublicKey, true)
            
            val sharedSecret = keyAgreement.generateSecret()
            Log.d(TAG, "ECDH shared secret generated (${sharedSecret.size} bytes)")
            
            // Derive AES key using HKDF-SHA256
            val aesKeyBytes = hkdfSha256(sharedSecret, HKDF_INFO.toByteArray(), AES_KEY_SIZE)
            
            return SecretKeySpec(aesKeyBytes, "AES")
        } catch (e: Exception) {
            Log.e(TAG, "Error deriving session key: ${e.message}", e)
            throw SecurityException("Failed to derive session key", e)
        }
    }
    
    /**
     * Get a unique device identifier based on the public key fingerprint
     * @return A 32-character hex string derived from the public key
     */
    fun getDeviceId(): String {
        val publicKey = getOrCreateDeviceKeyPair().public
        val encoded = publicKey.encoded
        
        // SHA-256 hash of the public key
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(encoded)
        
        // Take first 16 bytes and convert to hex
        return hash.take(16).joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Export the device's public key as a Base64 string for transmission
     */
    fun exportPublicKey(): String {
        val publicKey = getOrCreateDeviceKeyPair().public
        return Base64.getEncoder().encodeToString(publicKey.encoded)
    }
    
    /**
     * Import a peer's public key from Base64 string
     */
    fun importPublicKey(base64Key: String): PublicKey {
        val keyBytes = Base64.getDecoder().decode(base64Key)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(keySpec)
    }
    
    /**
     * HKDF-SHA256 key derivation function (simplified implementation)
     * @param inputKeyMaterial The shared secret from ECDH
     * @param info Context and application specific information
     * @param outputLength Desired output key length in bytes
     * @return Derived key material
     */
    private fun hkdfSha256(inputKeyMaterial: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        // Step 1: Extract (using HMAC-SHA256 with salt=zeros)
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val salt = ByteArray(32) // All zeros as salt
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(inputKeyMaterial)
        
        // Step 2: Expand
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val okm = ByteArray(outputLength)
        var t = ByteArray(0)
        var offset = 0
        var i = 1
        
        while (offset < outputLength) {
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            
            val copyLength = minOf(t.size, outputLength - offset)
            System.arraycopy(t, 0, okm, offset, copyLength)
            offset += copyLength
            i++
        }
        
        return okm
    }
    
    /**
     * Reset the device key (for testing or security purposes)
     */
    fun resetDeviceKey() {
        try {
            keyStore.deleteEntry(KEY_ALIAS)
            Log.d(TAG, "Device key pair deleted from KeyStore")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting device key: ${e.message}")
        }
    }
}
