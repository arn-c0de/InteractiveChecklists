package com.example.checklist_interactive.data.prefs

import kotlinx.serialization.Serializable

@Serializable
data class SourceEntry(
    val name: String,
    val website: String? = null,
    val license: String? = null,
    val author: String? = null,
    val usage: String? = null,
    val artifacts: List<String>? = null,
    val included_file: String? = null,
    val notes: String? = null
)
