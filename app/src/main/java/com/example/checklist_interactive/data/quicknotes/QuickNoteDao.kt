package com.example.checklist_interactive.data.quicknotes

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for QuickNotes
 * Provides reactive queries using Flow for real-time updates
 */
@Dao
interface QuickNoteDao {
    /**
     * Get all notes ordered by last modified timestamp
     */
    @Query("SELECT * FROM quick_notes ORDER BY lastModified DESC")
    fun getAllNotes(): Flow<List<QuickNoteEntity>>

    /**
     * Get note summaries (id, title, lastModified only) for fast list display
     */
    @Query("SELECT id, title, '' as content, '[]' as linkedDocuments, drawing, timestamp, lastModified FROM quick_notes ORDER BY lastModified DESC")
    fun getAllNoteSummaries(): Flow<List<QuickNoteEntity>>

    /**
     * Get a specific note by ID
     */
    @Query("SELECT * FROM quick_notes WHERE id = :noteId")
    fun getNoteById(noteId: String): Flow<QuickNoteEntity?>

    /**
     * Get a specific note by ID (suspend function for one-time read)
     */
    @Query("SELECT * FROM quick_notes WHERE id = :noteId")
    suspend fun getNoteByIdOnce(noteId: String): QuickNoteEntity?

    /**
     * Get note content only by ID as Flow
     */
    @Query("SELECT content FROM quick_notes WHERE id = :noteId")
    fun getNoteContent(noteId: String): Flow<String?>

    /**
     * Search notes by title or content
     */
    @Query("""
        SELECT * FROM quick_notes
        WHERE title LIKE '%' || :query || '%'
        OR content LIKE '%' || :query || '%'
        ORDER BY lastModified DESC
    """)
    fun searchNotes(query: String): Flow<List<QuickNoteEntity>>

    /**
     * Insert a new note
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: QuickNoteEntity)

    /**
     * Insert multiple notes
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<QuickNoteEntity>)

    /**
     * Update an existing note
     */
    @Update
    suspend fun updateNote(note: QuickNoteEntity)

    /**
     * Delete a note
     */
    @Delete
    suspend fun deleteNote(note: QuickNoteEntity)

    /**
     * Delete a note by ID
     */
    @Query("DELETE FROM quick_notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: String)

    /**
     * Delete all notes
     */
    @Query("DELETE FROM quick_notes")
    suspend fun deleteAllNotes()

    /**
     * Get count of all notes
     */
    @Query("SELECT COUNT(*) FROM quick_notes")
    suspend fun getNotesCount(): Int

    /**
     * Update note content
     */
    @Query("UPDATE quick_notes SET content = :content, lastModified = :lastModified WHERE id = :noteId")
    suspend fun updateNoteContent(noteId: String, content: String, lastModified: Long)

    /**
     * Get drawing JSON for a note (strokes serialized as JSON)
     */
    @Query("SELECT drawing FROM quick_notes WHERE id = :noteId")
    fun getNoteDrawing(noteId: String): Flow<String?>

    /**
     * Update drawing JSON for a note
     */
    @Query("UPDATE quick_notes SET drawing = :drawing, lastModified = :lastModified WHERE id = :noteId")
    suspend fun updateNoteDrawing(noteId: String, drawing: String?, lastModified: Long)

    /**
     * Update note title
     */
    @Query("UPDATE quick_notes SET title = :title, lastModified = :lastModified WHERE id = :noteId")
    suspend fun updateNoteTitle(noteId: String, title: String, lastModified: Long)
}
