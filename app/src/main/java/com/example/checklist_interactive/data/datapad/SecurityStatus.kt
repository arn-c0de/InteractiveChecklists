package com.example.checklist_interactive.data.datapad

/**
 * Represents the security status of the DataPad connection (ECDH only)
 */
sealed class SecurityStatus {
    /** Using ECDH with secure key exchange */
    object EcdhSecure : SecurityStatus()

    /** Error state with message */
    data class Error(val message: String) : SecurityStatus()

    /** Returns true if the current status is considered secure */
    fun isSecure(): Boolean = when (this) {
        is EcdhSecure -> true
        is Error -> false
    }

    /** Returns a user-friendly description of the security status */
    fun getDescription(): String = when (this) {
        is EcdhSecure -> "Secure (ECDH encryption)"
        is Error -> "Error: $message"
    }

    /** Returns security recommendation for the user */
    fun getRecommendation(): String? = when (this) {
        is Error -> "Fix the security configuration error before connecting"
        else -> null
    }
}
