package com.example.checklist_interactive.data.quicknotes

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a single quick note
 */
data class QuickNote(
    val id: String,
    val title: String = "",
    val content: String = "",
    val linkedDocuments: List<LinkedDocument> = emptyList(), // Kept for backward compatibility during migration
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents a linked document (deprecated - replaced by markdown links in content)
 * Kept only for backward compatibility during migration
 */
@Deprecated("Use markdown links in note content instead")
data class LinkedDocument(
    val id: String,
    val filePath: String,
    val fileName: String,
    val pageNumber: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Manager for quick notes that can be accessed from anywhere in the app
 * Uses Room database with Repository pattern for efficient data management
 */
class QuickNoteManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("quick_notes", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Initialize database and repository
    private val database = QuickNoteDatabase.getDatabase(context)
    private val repository = QuickNoteRepository(database.quickNoteDao())

    // StateFlows for reactive UI updates
    private val _notes = MutableStateFlow<List<QuickNote>>(emptyList())
    val notes: StateFlow<List<QuickNote>> = _notes.asStateFlow()

    private val _activeNoteId = MutableStateFlow<String?>(null)
    val activeNoteId: StateFlow<String?> = _activeNoteId.asStateFlow()

    private val _noteContent = MutableStateFlow("")
    val noteContent: StateFlow<String> = _noteContent.asStateFlow()

    init {
        // Start coroutine to initialize data
        scope.launch {
            try {
                // Check if migration is needed
                val notesCount = repository.getNotesCount()
                if (notesCount == 0) {
                    // No notes in database, try to migrate from SharedPreferences
                    migrateFromSharedPreferences()
                }

                // Observe notes from database
                repository.getAllNotes().collect { notesList ->
                    _notes.value = notesList

                    // If no active note is set, set the first one
                    if (_activeNoteId.value == null && notesList.isNotEmpty()) {
                        setActiveNote(notesList.first().id)
                    }

                    // Update active note content if it changed
                    val activeId = _activeNoteId.value
                    if (activeId != null) {
                        val activeNote = notesList.find { it.id == activeId }
                        if (activeNote != null) {
                            _noteContent.value = activeNote.content
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing QuickNoteManager", e)
            }
        }

        // Load active note ID from preferences
        scope.launch {
            val activeId = loadActiveNoteId()
            if (activeId != null) {
                _activeNoteId.value = activeId
            }
        }
    }

    /**
     * Migrate data from SharedPreferences to Room database
     */
    private suspend fun migrateFromSharedPreferences() {
        try {
            val migratedNotes = loadNotesFromSharedPreferences()

            if (migratedNotes.isNotEmpty()) {
                Log.d(TAG, "Migrating ${migratedNotes.size} notes from SharedPreferences to Room")
                repository.insertNotes(migratedNotes)

                // Clear old SharedPreferences data after successful migration
                prefs.edit().apply {
                    remove(NOTE_KEY)
                    remove(LINKED_DOCS_KEY)
                    remove(NOTES_KEY)
                }.apply()

                Log.d(TAG, "Migration completed successfully")
            } else {
                // No old data, create a default note
                val defaultNote = QuickNote(
                    id = "note_${System.currentTimeMillis()}",
                    title = "Meine Notizen",
                    content = ""
                )
                repository.insertNote(defaultNote)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during migration", e)
        }
    }

    /**
     * Load notes from SharedPreferences (for migration)
     */
    private fun loadNotesFromSharedPreferences(): List<QuickNote> {
        // Try load new format
        val json = prefs.getString(NOTES_KEY, null)
        if (json != null) {
            return try {
                val array = JSONArray(json)
                (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    QuickNote(
                        id = obj.getString("id"),
                        title = obj.optString("title", ""),
                        content = obj.optString("content", ""),
                        linkedDocuments = emptyList(), // Clear legacy linked documents
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading notes from SharedPreferences", e)
                emptyList()
            }
        }

        // Try old single-note format
        val oldContent = prefs.getString(NOTE_KEY, null)
        if (oldContent != null) {
            val note = QuickNote(
                id = "note_${System.currentTimeMillis()}",
                title = "Notizen",
                content = oldContent,
                linkedDocuments = emptyList()
            )
            return listOf(note)
        }

        return emptyList()
    }

    private fun loadActiveNoteId(): String? {
        return prefs.getString(ACTIVE_NOTE_KEY, null)
    }

    /**
     * Save note content
     */
    fun saveNoteContent(noteId: String? = null, content: String) {
        scope.launch {
            try {
                val id = noteId ?: _activeNoteId.value
                if (id != null) {
                    repository.updateNoteContent(id, content)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving note content", e)
            }
        }
    }

    /**
     * Backwards-compatible wrapper for existing callers
     */
    fun saveNote(content: String) {
        saveNoteContent(null, content)
    }

    /**
     * Clear active note content
     */
    fun clearActiveNote() {
        val id = _activeNoteId.value
        if (id != null) saveNoteContent(id, "")
    }

    /**
     * Backwards-compatible wrapper
     */
    fun clearNote() {
        clearActiveNote()
    }

    /**
     * Add a linked document to the active note (deprecated - now adds markdown link to content)
     * Kept for backward compatibility with existing UI code
     */
    @Deprecated("Use markdown links directly in note content", ReplaceWith("addMarkdownLink(filePath, fileName, pageNumber)"))
    fun addLinkedDocument(
        filePath: String,
        fileName: String,
        pageNumber: Int? = null,
        noteId: String? = null
    ) {
        scope.launch {
            try {
                val targetId = noteId ?: _activeNoteId.value
                if (targetId != null) {
                    val note = repository.getNoteByIdOnce(targetId)
                    if (note != null) {
                        // Create markdown link instead of using LinkedDocuments
                        val encodedPath = java.net.URLEncoder.encode(filePath, "UTF-8")
                        val pageParam = if (pageNumber != null) "&page=${pageNumber + 1}" else ""
                        val label = if (pageNumber != null) "$fileName (S. ${pageNumber + 1})" else fileName
                        val markdownLink = "\n📎 [$label](internal://open?file=$encodedPath$pageParam)\n"

                        val newContent = note.content + markdownLink
                        repository.updateNoteContent(targetId, newContent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding linked document", e)
            }
        }
    }

    /**
     * Add a new note
     */
    fun addNote(title: String = "New Note", content: String = "", setActive: Boolean = true): String {
        val id = "note_${System.currentTimeMillis()}"
        scope.launch {
            try {
                val note = QuickNote(id = id, title = title, content = content)
                repository.insertNote(note)
                if (setActive) {
                    setActiveNote(id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding note", e)
            }
        }
        return id
    }

    /**
     * Remove a note
     */
    fun removeNote(id: String) {
        scope.launch {
            try {
                repository.deleteNoteById(id)
                if (_activeNoteId.value == id) {
                    val notes = repository.getAllNotes().first()
                    _activeNoteId.value = notes.firstOrNull()?.id
                    saveActiveNoteId(_activeNoteId.value)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing note", e)
            }
        }
    }

    /**
     * Rename a note
     */
    fun renameNote(id: String, newTitle: String) {
        scope.launch {
            try {
                repository.updateNoteTitle(id, newTitle)
            } catch (e: Exception) {
                Log.e(TAG, "Error renaming note", e)
            }
        }
    }

    /**
     * Set active note
     */
    fun setActiveNote(id: String?) {
        scope.launch {
            try {
                if (id == null) {
                    val notes = repository.getAllNotes().first()
                    _activeNoteId.value = notes.firstOrNull()?.id
                } else {
                    val note = repository.getNoteByIdOnce(id)
                    if (note != null) {
                        _activeNoteId.value = id
                        _noteContent.value = note.content
                    }
                }
                saveActiveNoteId(_activeNoteId.value)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting active note", e)
            }
        }
    }

    private fun saveActiveNoteId(id: String?) {
        if (id == null) {
            prefs.edit().remove(ACTIVE_NOTE_KEY).apply()
        } else {
            prefs.edit().putString(ACTIVE_NOTE_KEY, id).apply()
        }
    }

    /**
     * Clear all notes
     */
    fun clearAllNotes() {
        scope.launch {
            try {
                repository.deleteAllNotes()
                _activeNoteId.value = null
                _noteContent.value = ""
                prefs.edit().remove(ACTIVE_NOTE_KEY).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing all notes", e)
            }
        }
    }

    /**
     * Search notes by query
     */
    fun searchNotes(query: String) {
        scope.launch {
            try {
                repository.searchNotes(query).collect { /* Results observed via notes StateFlow */ }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching notes", e)
            }
        }
    }

    /**
     * Get search results as a Flow
     */
    fun getSearchResults(query: String) = repository.searchNotes(query)

    companion object {
        private const val TAG = "QuickNoteManager"
        private const val NOTE_KEY = "quick_note_content"
        private const val LINKED_DOCS_KEY = "linked_documents"
        private const val NOTES_KEY = "quick_notes_list"
        private const val ACTIVE_NOTE_KEY = "active_quick_note_id"
    }
}
