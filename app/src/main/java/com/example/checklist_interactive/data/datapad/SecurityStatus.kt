package com.example.checklist_interactive.data.datapad

/**
 * Represents the security status of the DataPad connection
 */
sealed class SecurityStatus {
    /** PSK not yet initialized */
    object Uninitialized : SecurityStatus()
    
    /** Using ECDH with secure key exchange */
    object EcdhSecure : SecurityStatus()
    
    /** Using user-configured custom PSK */
    object PskConfigured : SecurityStatus()
    
    /** Using auto-generated random PSK (secure but not user-controlled) */
    object PskRandomGenerated : SecurityStatus()
    
    /** No encryption enabled (INSECURE - should not be allowed) */
    object Unencrypted : SecurityStatus()
    
    /** Error state with message */
    data class Error(val message: String) : SecurityStatus()
    
    /** Returns true if the current status is considered secure */
    fun isSecure(): Boolean = when (this) {
        is EcdhSecure, is PskConfigured, is PskRandomGenerated -> true
        else -> false
    }
    
    /** Returns a user-friendly description of the security status */
    fun getDescription(): String = when (this) {
        is Uninitialized -> "Security not initialized"
        is EcdhSecure -> "Secure (ECDH encryption)"
        is PskConfigured -> "Secure (Custom PSK)"
        is PskRandomGenerated -> "Secure (Auto-generated PSK)"
        is Unencrypted -> "INSECURE - No encryption"
        is Error -> "Error: $message"
    }
    
    /** Returns security recommendation for the user */
    fun getRecommendation(): String? = when (this) {
        is PskRandomGenerated -> "Consider using ECDH mode for enhanced security, or set a custom PSK that matches your server"
        is Unencrypted -> "Enable encryption immediately - your data is being sent in plaintext"
        is Error -> "Fix the security configuration error before connecting"
        else -> null
    }
}
