package com.example.checklist_interactive.data.quicknotes

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Room entity for QuickNote
 */
@Entity(tableName = "quick_notes")
data class QuickNoteEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val content: String,
    // JSON serialized strokes (nullable) — stores strokes as JSON for simple persistence
    val drawing: String? = null,
    val timestamp: Long,
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Extension functions to convert between domain models and entities
 */
fun QuickNoteEntity.toDomain(): QuickNote {
    return QuickNote(
        id = id,
        title = title,
        content = content,
        drawing = drawing,
        timestamp = timestamp
    )
}

fun QuickNote.toEntity(): QuickNoteEntity {
    return QuickNoteEntity(
        id = id,
        title = title,
        content = content,
        drawing = drawing,
        timestamp = timestamp
    )
}