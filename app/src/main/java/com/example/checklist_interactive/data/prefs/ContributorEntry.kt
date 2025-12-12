package com.example.checklist_interactive.data.prefs

import kotlinx.serialization.Serializable

@Serializable
data class ContributorEntry(
    val name: String,
    val website: String? = null,
    val role: String? = null
)
