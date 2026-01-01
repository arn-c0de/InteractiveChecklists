package com.example.checklist_interactive.data.datapad

import android.util.Log
import java.security.MessageDigest
import java.util.Base64

/**
 * Proof-of-Work solver for handshake DoS protection
 */
class ProofOfWorkSolver {
    companion object {
        private const val TAG = "ProofOfWorkSolver"
        
        /**
         * Solve proof-of-work challenge
         * Finds a nonce such that SHA-256(challenge + nonce) has 'difficulty' leading zero bits
         */
        fun solve(challengeBase64: String, difficulty: Int): String? {
            try {
                val challenge = Base64.getDecoder().decode(challengeBase64)
                val digest = MessageDigest.getInstance("SHA-256")
                
                val startTime = System.currentTimeMillis()
                var nonce = 0
                val maxAttempts = 1_000_000 // Prevent infinite loop
                
                while (nonce < maxAttempts) {
                    // Compute hash of challenge + nonce
                    digest.reset()
                    digest.update(challenge)
                    digest.update(nonce.toString().toByteArray())
                    val hash = digest.digest()
                    
                    // Check if hash has required number of leading zero bits
                    if (countLeadingZeroBits(hash) >= difficulty) {
                        val elapsed = System.currentTimeMillis() - startTime
                        Log.d(TAG, "✅ PoW solved in ${elapsed}ms (nonce: $nonce, difficulty: $difficulty)")
                        return nonce.toString()
                    }
                    
                    nonce++
                }
                
                Log.e(TAG, "Failed to solve PoW after $maxAttempts attempts")
                return null
            } catch (e: Exception) {
                Log.e(TAG, "PoW solve error: ${e.message}")
                return null
            }
        }
        
        /**
         * Count leading zero bits in a byte array
         */
        private fun countLeadingZeroBits(data: ByteArray): Int {
            var count = 0
            for (byte in data) {
                if (byte.toInt() == 0) {
                    count += 8
                } else {
                    // Count leading zeros in this byte
                    var b = byte.toInt() and 0xFF
                    while ((b and 0x80) == 0) {
                        count++
                        b = b shl 1
                    }
                    break
                }
            }
            return count
        }
    }
}
