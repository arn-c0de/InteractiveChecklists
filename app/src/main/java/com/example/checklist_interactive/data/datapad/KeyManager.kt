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
import java.security.interfaces.ECPublicKey
import java.security.spec.ECParameterSpec
import java.math.BigInteger
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
     * @param salt Optional salt for HKDF (recommended: 32 random bytes from server)
     * @return A 256-bit AES key derived from the shared secret
     */
    fun deriveSessionKey(peerPublicKey: PublicKey, salt: ByteArray? = null): SecretKey {
        try {
            val deviceKeyPair = getOrCreateDeviceKeyPair()

            // Perform ECDH key agreement
            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(deviceKeyPair.private)
            keyAgreement.doPhase(peerPublicKey, true)

            val sharedSecret = keyAgreement.generateSecret()
            Log.d(TAG, "ECDH shared secret generated (${sharedSecret.size} bytes)")

            // Derive AES key using HKDF-SHA256 with provided salt (or zeros if null)
            val actualSalt = salt ?: ByteArray(32)  // Default to zeros if no salt provided
            val aesKeyBytes = hkdfSha256(sharedSecret, actualSalt, HKDF_INFO.toByteArray(), AES_KEY_SIZE)

            if (salt != null) {
                Log.d(TAG, "✅ HKDF with random salt (${salt.size} bytes)")
            } else {
                Log.w(TAG, "⚠️ HKDF with zero salt (legacy mode)")
            }

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
     * Performs sanity and security checks:
     *  - must be an EC public key
     *  - curve parameters must match the device curve (secp256r1)
     *  - public point must lie on the curve (not infinity / invalid)
     */
    fun importPublicKey(base64Key: String): PublicKey {
        val keyBytes = Base64.getDecoder().decode(base64Key)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        val pub = keyFactory.generatePublic(keySpec)

        if (pub !is ECPublicKey) {
            throw IllegalArgumentException("Imported key is not an EC public key")
        }

        // Validate that the curve matches our device key (secp256r1)
        val devicePub = getOrCreateDeviceKeyPair().public as ECPublicKey
        val peerParams: ECParameterSpec = pub.params
        val deviceParams: ECParameterSpec = devicePub.params

        if (!ecParamsEqual(peerParams, deviceParams)) {
            throw IllegalArgumentException("EC public key curve parameters do not match expected curve")
        }

        // Verify that the public point lies on the curve: y^2 mod p == x^3 + a*x + b (mod p)
        val w = pub.w
        val curve = peerParams.curve
        val field = curve.field
        if (field !is java.security.spec.ECFieldFp) {
            throw IllegalArgumentException("Unsupported EC field type")
        }
        val p: BigInteger = (field as java.security.spec.ECFieldFp).p
        val x: BigInteger = w.affineX
        val y: BigInteger = w.affineY
        val a: BigInteger = curve.a
        val b: BigInteger = curve.b

        val left = y.multiply(y).mod(p)
        val right = x.multiply(x).multiply(x).add(a.multiply(x)).add(b).mod(p)

        if (left != right) {
            throw IllegalArgumentException("EC public key point is not on the expected curve")
        }

        return pub
    }

    /**
     * Compare two ECParameterSpec objects for equivalence (curve parameters + generator + order/cofactor)
     */
    private fun ecParamsEqual(a: ECParameterSpec, b: ECParameterSpec): Boolean {
        val ca = a.curve
        val cb = b.curve
        if (ca.field.fieldSize != cb.field.fieldSize) return false
        if (ca.a != cb.a || ca.b != cb.b) return false
        val ga = a.generator
        val gb = b.generator
        if (ga.affineX != gb.affineX || ga.affineY != gb.affineY) return false
        if (a.order != b.order || a.cofactor != b.cofactor) return false
        return true
    }
    
    /**
     * HKDF-SHA256 key derivation function (RFC 5869)
     * @param inputKeyMaterial The shared secret from ECDH
     * @param salt Random salt (should be 32 bytes from server)
     * @param info Context and application specific information
     * @param outputLength Desired output key length in bytes
     * @return Derived key material
     */
    private fun hkdfSha256(inputKeyMaterial: ByteArray, salt: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        // Step 1: Extract (using HMAC-SHA256 with provided salt)
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
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
