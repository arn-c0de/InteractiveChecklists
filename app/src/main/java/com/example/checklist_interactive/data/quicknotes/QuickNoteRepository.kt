package com.example.checklist_interactive.data.quicknotes

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for QuickNotes
 * Abstracts the data layer and provides a clean API for data access
 * Handles data operations and error handling
 */
class QuickNoteRepository(private val quickNoteDao: QuickNoteDao) {

    companion object {
        private const val TAG = "QuickNoteRepository"
    }

    /**
     * Get all notes as a Flow
     */
    fun getAllNotes(): Flow<List<QuickNote>> {
        return quickNoteDao.getAllNotes().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get a specific note by ID
     */
    fun getNoteById(noteId: String): Flow<QuickNote?> {
        return quickNoteDao.getNoteById(noteId).map { it?.toDomain() }
    }

    /**
     * Get a specific note by ID (one-time read)
     */
    suspend fun getNoteByIdOnce(noteId: String): QuickNote? {
        return try {
            quickNoteDao.getNoteByIdOnce(noteId)?.toDomain()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting note by ID: $noteId", e)
            null
        }
    }

    /**
     * Search notes by query string
     */
    fun searchNotes(query: String): Flow<List<QuickNote>> {
        return quickNoteDao.searchNotes(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Insert a new note
     */
    suspend fun insertNote(note: QuickNote): Result<Unit> {
        return try {
            quickNoteDao.insertNote(note.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting note: ${note.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Insert multiple notes (useful for migration)
     */
    suspend fun insertNotes(notes: List<QuickNote>): Result<Unit> {
        return try {
            quickNoteDao.insertNotes(notes.map { it.toEntity() })
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting multiple notes", e)
            Result.failure(e)
        }
    }

    /**
     * Update an existing note
     */
    suspend fun updateNote(note: QuickNote): Result<Unit> {
        return try {
            val entity = note.toEntity().copy(lastModified = System.currentTimeMillis())
            quickNoteDao.updateNote(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating note: ${note.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Update note content only
     */
    suspend fun updateNoteContent(noteId: String, content: String): Result<Unit> {
        return try {
            quickNoteDao.updateNoteContent(noteId, content, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating note content: $noteId", e)
            Result.failure(e)
        }
    }

    /**
     * Update note title only
     */
    suspend fun updateNoteTitle(noteId: String, title: String): Result<Unit> {
        return try {
            quickNoteDao.updateNoteTitle(noteId, title, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating note title: $noteId", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a note
     */
    suspend fun deleteNote(note: QuickNote): Result<Unit> {
        return try {
            quickNoteDao.deleteNote(note.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting note: ${note.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a note by ID
     */
    suspend fun deleteNoteById(noteId: String): Result<Unit> {
        return try {
            quickNoteDao.deleteNoteById(noteId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting note by ID: $noteId", e)
            Result.failure(e)
        }
    }

    /**
     * Delete all notes
     */
    suspend fun deleteAllNotes(): Result<Unit> {
        return try {
            quickNoteDao.deleteAllNotes()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all notes", e)
            Result.failure(e)
        }
    }

    /**
     * Get the count of all notes
     */
    suspend fun getNotesCount(): Int {
        return try {
            quickNoteDao.getNotesCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting notes count", e)
            0
        }
    }
}
