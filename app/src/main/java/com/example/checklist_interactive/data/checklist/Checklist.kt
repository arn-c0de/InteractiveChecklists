package com.example.checklist_interactive.data.checklist

data class Checklist(
    val id: String,
    val title: String,
    val sections: List<ChecklistSection>
)

data class ChecklistSection(
    val title: String,
    val items: List<ChecklistItem>
)

data class ChecklistItem(
    val id: String,
    val text: String,
    val indent: Int = 0,
    val isChecked: Boolean = false,
    val isTask: Boolean = true
)
