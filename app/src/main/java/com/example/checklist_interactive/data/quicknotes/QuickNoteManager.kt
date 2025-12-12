package com.example.checklist_interactive.data.quicknotes

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a linked document in quick notes
 */
data class LinkedDocument(
    val id: String,
    val filePath: String,
    val fileName: String,
    val pageNumber: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents a single quick note tab
 */
data class QuickNote(
    val id: String,
    val title: String = "",
    val content: String = "",
    val linkedDocuments: List<LinkedDocument> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Manager for quick notes that can be accessed from anywhere in the app
 */
class QuickNoteManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("quick_notes", Context.MODE_PRIVATE)

    // MutableStateFlows initialized empty/default, then loaded in init
    private val _notes = MutableStateFlow<List<QuickNote>>(emptyList())
    val notes: StateFlow<List<QuickNote>> = _notes.asStateFlow()

    private val _activeNoteId = MutableStateFlow<String?>(null)
    val activeNoteId: StateFlow<String?> = _activeNoteId.asStateFlow()

    private val _noteContent = MutableStateFlow("")
    val noteContent: StateFlow<String> = _noteContent.asStateFlow()

    private val _linkedDocuments = MutableStateFlow<List<LinkedDocument>>(emptyList())
    val linkedDocuments: StateFlow<List<LinkedDocument>> = _linkedDocuments.asStateFlow()

    init {
        // Load notes and handle migration if needed
        val loadedNotes = loadNotesInternal()

        // Wenn keine Notizen existieren, erstelle eine Default-Notiz
        val notesToUse = if (loadedNotes.isEmpty()) {
            val defaultNote = QuickNote(
                id = "note_${System.currentTimeMillis()}",
                title = "Meine Notizen",
                content = ""
            )
            listOf(defaultNote)
        } else {
            loadedNotes
        }

        _notes.value = notesToUse
        val loadedActiveId = loadActiveNoteId() ?: notesToUse.firstOrNull()?.id
        _activeNoteId.value = loadedActiveId
        _noteContent.value = notesToUse.firstOrNull { it.id == loadedActiveId }?.content ?: ""
        _linkedDocuments.value = notesToUse.firstOrNull { it.id == loadedActiveId }?.linkedDocuments ?: emptyList()

        // Speichere Notizen wenn sie neu erstellt wurden oder migriert wurden
        if (prefs.getString(NOTES_KEY, null) == null) {
            saveNotes(notesToUse)
            loadedActiveId?.let { prefs.edit().putString(ACTIVE_NOTE_KEY, it).apply() }
        }
    }

    // Loads the active note id from shared preferences
    private fun loadActiveNoteId(): String? {
        return prefs.getString(ACTIVE_NOTE_KEY, null)
    }

    private fun loadActiveNoteContent(): String {
        // if no notes exist, ensure we have at least one
        val notes = _notes.value
        val activeId = loadActiveNoteId()
        val note = if (notes.isNotEmpty()) notes.firstOrNull { it.id == activeId } ?: notes.first() else null
        return note?.content ?: ""
    }

    private fun loadActiveNoteLinkedDocuments(): List<LinkedDocument> {
        val activeId = loadActiveNoteId()
        val activeNote: JSONObject? = activeId?.let { id ->
            try {
                val json = prefs.getString(NOTES_KEY, "[]") ?: "[]"
                val array = JSONArray(json)
                (0 until array.length())
                    .map { i: Int -> array.getJSONObject(i) }
                    .firstOrNull { obj -> obj.optString("id") == id }
            } catch (e: Exception) {
                null
            }
        }
        // If active note JSON found, parse its linkedDocuments; otherwise fallback to migration path
        return if (activeNote != null && activeNote.has("linkedDocuments")) {
            try {
                val list = mutableListOf<LinkedDocument>()
                val larr = activeNote.getJSONArray("linkedDocuments")
                for (i in 0 until larr.length()) {
                    val obj = larr.getJSONObject(i)
                    list.add(LinkedDocument(
                        id = obj.getString("id"),
                        filePath = obj.getString("filePath"),
                        fileName = obj.getString("fileName"),
                        pageNumber = if (obj.has("pageNumber")) obj.getInt("pageNumber") else null,
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    ))
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            // Last resort, try parse old style linked docs
            val json = prefs.getString(LINKED_DOCS_KEY, "[]") ?: "[]"
            try {
                val array = JSONArray(json)
                (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    LinkedDocument(
                        id = obj.getString("id"),
                        filePath = obj.getString("filePath"),
                        fileName = obj.getString("fileName"),
                        pageNumber = if (obj.has("pageNumber")) obj.getInt("pageNumber") else null,
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun saveNoteContent(noteId: String? = null, content: String) {
        val id = noteId ?: _activeNoteId.value
        val current = _notes.value.toMutableList()
        val idx = current.indexOfFirst { note: QuickNote -> note.id == id }
        if (idx >= 0) {
            val note = current[idx]
            current[idx] = note.copy(content = content)
            saveNotes(current)
            if (_activeNoteId.value == id) _noteContent.value = content
        }
    }

    // Backwards-compatible wrapper for existing callers
    fun saveNote(content: String) {
        saveNoteContent(null, content)
    }

    fun clearActiveNote() {
        val id = _activeNoteId.value
        if (id != null) saveNoteContent(id, "")
    }

    // Backwards-compatible wrapper
    fun clearNote() { clearActiveNote() }

    fun addLinkedDocument(filePath: String, fileName: String, pageNumber: Int? = null, noteId: String? = null) {
        val id = "${filePath}_${pageNumber ?: 0}_${System.currentTimeMillis()}"
        val targetId = noteId ?: _activeNoteId.value
        if (targetId == null) return

        val current = _notes.value.toMutableList()
        val idx = current.indexOfFirst { note: QuickNote -> note.id == targetId }
        if (idx < 0) return

        val note = current[idx]
        val docs = note.linkedDocuments.toMutableList()
        docs.removeAll { it.filePath == filePath && it.pageNumber == pageNumber }
        docs.add(0, LinkedDocument(id = id, filePath = filePath, fileName = fileName, pageNumber = pageNumber))
        current[idx] = note.copy(linkedDocuments = docs)
        saveNotes(current)
        if (_activeNoteId.value == targetId) _linkedDocuments.value = docs
    }

    fun removeLinkedDocument(id: String, noteId: String? = null) {
        val targetId = noteId ?: _activeNoteId.value
        if (targetId == null) return

        val current = _notes.value.toMutableList()
        val idx = current.indexOfFirst { note: QuickNote -> note.id == targetId }
        if (idx < 0) return

        val note = current[idx]
        val docs = note.linkedDocuments.toMutableList()
        docs.removeAll { it.id == id }
        current[idx] = note.copy(linkedDocuments = docs)
        saveNotes(current)
        if (_activeNoteId.value == targetId) _linkedDocuments.value = docs
    }

    // Backwards-compatible overload
    fun removeLinkedDocument(id: String) { removeLinkedDocument(id, null) }

    fun clearActiveNoteLinkedDocuments() {
        val id = _activeNoteId.value ?: return
        val current = _notes.value.toMutableList()
        val idx = current.indexOfFirst { note: QuickNote -> note.id == id }
        if (idx < 0) return
        val note = current[idx]
        current[idx] = note.copy(linkedDocuments = emptyList())
        saveNotes(current)
        _linkedDocuments.value = emptyList()
    }

    // Backwards-compatible wrapper
    fun clearLinkedDocuments() { clearActiveNoteLinkedDocuments() }

    private fun saveNotes(notes: List<QuickNote>) {
        val array = JSONArray()
        notes.forEach { note ->
            val obj = JSONObject().apply {
                put("id", note.id)
                put("title", note.title)
                put("content", note.content)
                put("timestamp", note.timestamp)
                val lad = JSONArray()
                note.linkedDocuments.forEach { doc ->
                    val dobj = JSONObject().apply {
                        put("id", doc.id)
                        put("filePath", doc.filePath)
                        put("fileName", doc.fileName)
                        doc.pageNumber?.let { put("pageNumber", it) }
                        put("timestamp", doc.timestamp)
                    }
                    lad.put(dobj)
                }
                put("linkedDocuments", lad)
            }
            array.put(obj)
        }
        prefs.edit().putString(NOTES_KEY, array.toString()).apply()
        _notes.value = notes
    }

    // Internal loader for notes, does not call saveNotes to avoid NPE during construction
    private fun loadNotesInternal(): List<QuickNote> {
        // Try load new format
        val json = prefs.getString(NOTES_KEY, null)
        if (json != null) {
            return try {
                val array = JSONArray(json)
                (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    val linkedDocs = mutableListOf<LinkedDocument>()
                    if (obj.has("linkedDocuments")) {
                        val la = obj.getJSONArray("linkedDocuments")
                        (0 until la.length()).forEach { j ->
                            val d = la.getJSONObject(j)
                            linkedDocs.add(LinkedDocument(
                                id = d.getString("id"),
                                filePath = d.getString("filePath"),
                                fileName = d.getString("fileName"),
                                pageNumber = if (d.has("pageNumber")) d.getInt("pageNumber") else null,
                                timestamp = d.optLong("timestamp", System.currentTimeMillis())
                            ))
                        }
                    }
                    QuickNote(
                        id = obj.getString("id"),
                        title = obj.optString("title", ""),
                        content = obj.optString("content", ""),
                        linkedDocuments = linkedDocs,
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        // Backwards-compatibility: if old single-note keys exist, migrate (but don't save yet)
        val oldContent = prefs.getString(NOTE_KEY, null)
        val oldLinked = prefs.getString(LINKED_DOCS_KEY, null)
        if (oldContent != null || oldLinked != null) {
            val linked = try {
                val arr = JSONArray(oldLinked ?: "[]")
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    LinkedDocument(
                        id = obj.getString("id"),
                        filePath = obj.getString("filePath"),
                        fileName = obj.getString("fileName"),
                        pageNumber = if (obj.has("pageNumber")) obj.getInt("pageNumber") else null,
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                }
            } catch (e: Exception) { emptyList() }

            val note = QuickNote(id = "note_${System.currentTimeMillis()}", title = "Notes", content = oldContent ?: "", linkedDocuments = linked)
            return listOf(note)
        }

        // No notes at all: return empty
        return emptyList()
    }

    fun addNote(title: String = "New Note", content: String = "", setActive: Boolean = true): String {
        val id = "note_${System.currentTimeMillis()}"
        val current = _notes.value.toMutableList()
        current.add(0, QuickNote(id = id, title = title, content = content))
        saveNotes(current)
        if (setActive) setActiveNote(id)
        return id
    }

    fun removeNote(id: String) {
        val current = _notes.value.toMutableList()
        current.removeAll { it.id == id }
        saveNotes(current)
        if (_activeNoteId.value == id) _activeNoteId.value = _notes.value.firstOrNull()?.id
    }

    fun renameNote(id: String, newTitle: String) {
        val current = _notes.value.toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val note = current[idx]
            current[idx] = note.copy(title = newTitle)
            saveNotes(current)
        }
    }

    fun setActiveNote(id: String?) {
        if (id == null) {
            _activeNoteId.value = _notes.value.firstOrNull()?.id
            _noteContent.value = loadActiveNoteContent()
            _linkedDocuments.value = loadActiveNoteLinkedDocuments()
            prefs.edit().remove(ACTIVE_NOTE_KEY).apply()
            return
        }
        val exists = _notes.value.any { it.id == id }
        if (!exists) return
        _activeNoteId.value = id
        prefs.edit().putString(ACTIVE_NOTE_KEY, id).apply()
        _noteContent.value = loadActiveNoteContent()
        _linkedDocuments.value = loadActiveNoteLinkedDocuments()
    }

    fun clearAllNotes() {
        saveNotes(emptyList())
        prefs.edit().remove(ACTIVE_NOTE_KEY).apply()
        _activeNoteId.value = null
        _noteContent.value = ""
        _linkedDocuments.value = emptyList()
    }

    companion object {
        private const val NOTE_KEY = "quick_note_content"
        private const val LINKED_DOCS_KEY = "linked_documents"
        private const val NOTES_KEY = "quick_notes_list"
        private const val ACTIVE_NOTE_KEY = "active_quick_note_id"
    }
}
