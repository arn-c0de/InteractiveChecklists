package com.example.checklist_interactive.data.prefs

import kotlinx.serialization.Serializable

@Serializable
data class SourceEntry(
    val name: String,
    val website: String? = null,
    val license: String? = null,
    val author: String? = null
)
