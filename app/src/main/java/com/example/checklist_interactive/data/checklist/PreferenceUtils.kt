package com.example.checklist_interactive.data.checklist

import java.security.MessageDigest

object PreferenceUtils {
    fun getPreferenceNameForChecklist(checklistId: String): String {
        val safeId = hashString(checklistId)
        return "checklist_state_$safeId"
    }

    private fun hashString(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
