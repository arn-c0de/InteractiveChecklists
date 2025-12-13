package com.example.checklist_interactive.data.quicknotes

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
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

    // Initialize database and repository lazily on IO to avoid blocking main thread
    private lateinit var database: QuickNoteDatabase
    private lateinit var repository: QuickNoteRepository

    // StateFlows for reactive UI updates
    private val _notes = MutableStateFlow<List<QuickNote>>(emptyList())
    val notes: StateFlow<List<QuickNote>> = _notes.asStateFlow()

    private val _notesSummary = MutableStateFlow<List<QuickNote>>(emptyList())
    val notesSummary: StateFlow<List<QuickNote>> = _notesSummary.asStateFlow()

    private val _activeNoteId = MutableStateFlow<String?>(null)
    val activeNoteId: StateFlow<String?> = _activeNoteId.asStateFlow()

    private val _noteContent = MutableStateFlow("")
    val noteContent: StateFlow<String> = _noteContent.asStateFlow()

    /**
     * Get note content as Flow for specific note ID
     */
    fun getNoteContentFlow(noteId: String): Flow<String?> {
        return if (::repository.isInitialized) {
            repository.getNoteContentFlow(noteId)
        } else {
            kotlinx.coroutines.flow.flowOf(null)
        }
    }

    // Radio/CALLSIGN state flows
    private val _callsign = MutableStateFlow("")
    val callsign: StateFlow<String> = _callsign.asStateFlow()

    private val _com1 = MutableStateFlow("")
    val com1: StateFlow<String> = _com1.asStateFlow()

    private val _com1Mode = MutableStateFlow("FM") // or "AM"
    val com1Mode: StateFlow<String> = _com1Mode.asStateFlow()

    private val _com2 = MutableStateFlow("")
    val com2: StateFlow<String> = _com2.asStateFlow()

    private val _com2Mode = MutableStateFlow("FM")
    val com2Mode: StateFlow<String> = _com2Mode.asStateFlow()

    private val _flightInfoExpanded = MutableStateFlow(false)
    val flightInfoExpanded: StateFlow<Boolean> = _flightInfoExpanded.asStateFlow()
    private val _flightStatus = MutableStateFlow("Idle")
    val flightStatus: StateFlow<String> = _flightStatus.asStateFlow()

    init {
        // Load active note id from preferences first to avoid race condition
        val initialActiveId = loadActiveNoteId()
        if (initialActiveId != null) {
            _activeNoteId.value = initialActiveId
        }

        // Start coroutine to initialize data
        scope.launch {
            // Initialize Room database and repository on IO
            database = QuickNoteDatabase.getDatabase(context)
            repository = QuickNoteRepository(database.quickNoteDao())
            try {
                // Check if migration is needed
                val notesCount = repository.getNotesCount()
                if (notesCount == 0) {
                    // No notes in database, try to migrate from SharedPreferences
                    migrateFromSharedPreferences()
                }

                // Observe note summaries from database for fast list display
                repository.getAllNoteSummaries().collect { notesList ->
                    _notesSummary.value = notesList
                    _notes.value = notesList

                    // If no active note is set, set the first one
                    if (_activeNoteId.value == null && notesList.isNotEmpty()) {
                        setActiveNote(notesList.first().id)
                    }

                    // Update active note content if it changed: fetch full content
                    val activeId = _activeNoteId.value
                    if (activeId != null) {
                        val fullNote = repository.getNoteByIdOnce(activeId)
                        if (fullNote != null) {
                            _noteContent.value = fullNote.content
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing QuickNoteManager", e)
            }
        }

        // Load callsign/com preferences and other small settings
        scope.launch {
            // load callsign/coms
            _callsign.value = prefs.getString(CALLSIGN_KEY, "") ?: ""
            _com1.value = prefs.getString(COM1_KEY, "") ?: ""
            _com1Mode.value = prefs.getString(COM1_MODE_KEY, "FM") ?: "FM"
            _com2.value = prefs.getString(COM2_KEY, "") ?: ""
            _com2Mode.value = prefs.getString(COM2_MODE_KEY, "FM") ?: "FM"
            _flightInfoExpanded.value = prefs.getBoolean(FLIGHT_INFO_EXPANDED_KEY, false)
            _flightStatus.value = prefs.getString(FLIGHT_STATUS_KEY, "Idle") ?: "Idle"
        }
    }

    fun saveFlightInfoExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(FLIGHT_INFO_EXPANDED_KEY, expanded).apply()
        _flightInfoExpanded.value = expanded
    }

    fun saveFlightStatus(status: String) {
        prefs.edit().putString(FLIGHT_STATUS_KEY, status).apply()
        _flightStatus.value = status
    }

    /**
     * Warm up the database connection and load initial data
     * Call this early (e.g., from MainActivity) to reduce perceived latency
     */
    fun warmUp() {
        scope.launch {
            try {
                // Trigger database initialization if not already done
                if (!::database.isInitialized) {
                    database = QuickNoteDatabase.getDatabase(context)
                    repository = QuickNoteRepository(database.quickNoteDao())
                }
                // Preload summaries to warm up cache
                repository.getNotesCount()
            } catch (e: Exception) {
                Log.e(TAG, "Error during warmup", e)
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

    // -------- CALLSIGN and COM persistence --------
    fun saveCallsign(value: String) {
        prefs.edit().putString(CALLSIGN_KEY, value).apply()
        _callsign.value = value
    }

    fun saveCom1(freq: String, mode: String = "FM") {
        val formatted = formatComFreq(freq)
        prefs.edit().putString(COM1_KEY, formatted).putString(COM1_MODE_KEY, mode).apply()
        _com1.value = formatted
        _com1Mode.value = mode
    }

    fun saveCom2(freq: String, mode: String = "FM") {
        val formatted = formatComFreq(freq)
        prefs.edit().putString(COM2_KEY, formatted).putString(COM2_MODE_KEY, mode).apply()
        _com2.value = formatted
        _com2Mode.value = mode
    }

    /**
     * Load the saved radio settings and return them in a map
     */
    fun loadRadioSettings(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        map[CALLSIGN_KEY] = prefs.getString(CALLSIGN_KEY, "") ?: ""
        map[COM1_KEY] = prefs.getString(COM1_KEY, "") ?: ""
        map[COM1_MODE_KEY] = prefs.getString(COM1_MODE_KEY, "FM") ?: "FM"
        map[COM2_KEY] = prefs.getString(COM2_KEY, "") ?: ""
        map[COM2_MODE_KEY] = prefs.getString(COM2_MODE_KEY, "FM") ?: "FM"
        return map
    }

    data class RadioSettings(
        val callsign: String,
        val com1: String,
        val com1Mode: String,
        val com2: String,
        val com2Mode: String,
        val flightStatus: String
    )

    fun getRadioSettings(): RadioSettings {
        return RadioSettings(
            callsign = prefs.getString(CALLSIGN_KEY, "") ?: "",
            com1 = prefs.getString(COM1_KEY, "") ?: "",
            com1Mode = prefs.getString(COM1_MODE_KEY, "FM") ?: "FM",
            com2 = prefs.getString(COM2_KEY, "") ?: "",
            com2Mode = prefs.getString(COM2_MODE_KEY, "FM") ?: "FM",
            flightStatus = prefs.getString(FLIGHT_STATUS_KEY, "Idle") ?: "Idle"
        )
    }

    private fun formatComFreq(freq: String): String {
        // Accept both comma and dot, format as 000,000 with 3 digits fractional
        val normalized = freq.replace('.', ',')
        // allow only digits and comma
        val filtered = normalized.filter { it.isDigit() || it == ',' }
        val parts = filtered.split(',')
        val whole = parts.getOrNull(0)?.padStart(3, '0') ?: "000"
        val fracRaw = parts.getOrNull(1) ?: "000"
        val frac = when {
            fracRaw.length >= 3 -> fracRaw.substring(0, 3)
            else -> fracRaw.padEnd(3, '0')
        }
        return "$whole,$frac"
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
                if (_activeNoteId.value == id) {
                    // Get current notes list before deletion
                    val notes = repository.getAllNotes().first()
                    val currentIndex = notes.indexOfFirst { it.id == id }

                    // Delete the note
                    repository.deleteNoteById(id)

                    // Select next note in order
                    val updatedNotes = repository.getAllNotes().first()
                    val nextNote = when {
                        updatedNotes.isEmpty() -> null
                        currentIndex < updatedNotes.size -> updatedNotes[currentIndex] // Next in line
                        else -> updatedNotes.lastOrNull() // Was last, select previous
                    }

                    _activeNoteId.value = nextNote?.id
                    saveActiveNoteId(_activeNoteId.value)

                    // Update content for new active note
                    if (nextNote != null) {
                        _noteContent.value = nextNote.content
                    } else {
                        _noteContent.value = ""
                    }
                } else {
                    // Not active note, just delete
                    repository.deleteNoteById(id)
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
        private const val CALLSIGN_KEY = "callsign"
        private const val COM1_KEY = "com1"
        private const val COM1_MODE_KEY = "com1_mode"
        private const val COM2_KEY = "com2"
        private const val COM2_MODE_KEY = "com2_mode"
        private const val FLIGHT_STATUS_KEY = "flight_status"
        private const val FLIGHT_INFO_EXPANDED_KEY = "flight_info_expanded"
    }
}
