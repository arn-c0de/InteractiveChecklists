package com.example.checklist_interactive.data.quicknotes

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/**
 * Room entity for QuickNote
 */
@Entity(tableName = "quick_notes")
@TypeConverters(LinkedDocumentConverter::class)
data class QuickNoteEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val content: String,
    val linkedDocuments: List<LinkedDocumentEntity>,
    val timestamp: Long,
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Serializable representation of LinkedDocument for Room storage
 */
@Serializable
data class LinkedDocumentEntity(
    val id: String,
    val filePath: String,
    val fileName: String,
    val pageNumber: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Type converter for storing List<LinkedDocumentEntity> in Room
 */
class LinkedDocumentConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromLinkedDocuments(value: List<LinkedDocumentEntity>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toLinkedDocuments(value: String): List<LinkedDocumentEntity> {
        return try {
            json.decodeFromString<List<LinkedDocumentEntity>>(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Extension functions to convert between domain models and entities
 */
fun QuickNoteEntity.toDomain(): QuickNote {
    return QuickNote(
        id = id,
        title = title,
        content = content,
        linkedDocuments = linkedDocuments.map { it.toDomain() },
        timestamp = timestamp
    )
}

fun QuickNote.toEntity(): QuickNoteEntity {
    return QuickNoteEntity(
        id = id,
        title = title,
        content = content,
        linkedDocuments = linkedDocuments.map { it.toEntity() },
        timestamp = timestamp
    )
}

fun LinkedDocumentEntity.toDomain(): LinkedDocument {
    return LinkedDocument(
        id = id,
        filePath = filePath,
        fileName = fileName,
        pageNumber = pageNumber,
        timestamp = timestamp
    )
}

fun LinkedDocument.toEntity(): LinkedDocumentEntity {
    return LinkedDocumentEntity(
        id = id,
        filePath = filePath,
        fileName = fileName,
        pageNumber = pageNumber,
        timestamp = timestamp
    )
}
