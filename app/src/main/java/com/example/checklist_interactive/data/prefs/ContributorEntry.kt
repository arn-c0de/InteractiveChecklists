package com.example.checklist_interactive.data.prefs

import kotlinx.serialization.Serializable

@Serializable
data class ContributorEntry(
    val name: String,
    val website: String? = null,
    val role: String? = null,
    // ISO date string (YYYY-MM-DD) when the contribution was made or added
    val date: String? = null
)
