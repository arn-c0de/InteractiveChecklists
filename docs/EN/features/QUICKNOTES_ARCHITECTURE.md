# QuickNotes Architecture - Developer Guide

Updated-VERSION=1.0.3

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Components](#components)
4. [Database Schema](#database-schema)
5. [Data Flow](#data-flow)
6. [Usage Examples](#usage-examples)
7. [Migration Guide](#migration-guide)
8. [Best Practices](#best-practices)
9. [Troubleshooting](#troubleshooting)

---

## Overview

The QuickNotes feature provides a comprehensive note-taking system integrated throughout the application. As of version 1.0.4, it uses a modern architecture based on **Room Database**, **Repository Pattern**, and **Kotlin Coroutines**.

### Key Features
- ✅ Multiple notes support with tabs
- ✅ Real-time search functionality
- ✅ Markdown link support for cross-document references
- ✅ Auto-save with 2-second delay
- ✅ Persistent storage with Room Database
- ✅ Reactive UI updates with StateFlow
- ✅ Material Design 3 UI

---

## Architecture

The QuickNotes system follows **Clean Architecture** principles with clear separation of concerns:

```
┌─────────────────────────────────────────┐
│           UI Layer (Compose)            │
│  QuickAccessSheet.kt                    │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│      Manager Layer (StateFlow)          │
│  QuickNoteManager.kt                    │
│  - Manages StateFlows                   │
│  - Handles business logic               │
│  - Coordinates UI & Repository          │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│     Repository Layer (Data Access)      │
│  QuickNoteRepository.kt                 │
│  - Abstracts data source                │
│  - Error handling                       │
│  - Transforms entities ↔ domain models  │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│      Data Layer (Room Database)         │
│  QuickNoteDatabase.kt                   │
│  QuickNoteDao.kt                        │
│  QuickNoteEntity.kt                     │
└─────────────────────────────────────────┘
```

---

## Components

### 1. QuickNoteManager.kt
**Location:** `data/quicknotes/QuickNoteManager.kt`

**Purpose:** Central manager for note operations. Provides StateFlows for reactive UI updates.

**Key Responsibilities:**
- Manages active note state
- Coordinates between UI and Repository
- Handles data migration from SharedPreferences
- Provides backward-compatible API

**Public API:**
```kotlin
class QuickNoteManager(context: Context) {
    // StateFlows for reactive UI
    val notes: StateFlow<List<QuickNote>>
    val activeNoteId: StateFlow<String?>
    val noteContent: StateFlow<String>

    // Note operations
    fun addNote(title: String, content: String, setActive: Boolean): String
    fun removeNote(id: String)
    fun renameNote(id: String, newTitle: String)
    fun setActiveNote(id: String?)

    // Content operations
    fun saveNoteContent(noteId: String?, content: String)
    fun clearActiveNote()

    // Search
    fun searchNotes(query: String)
    fun getSearchResults(query: String): Flow<List<QuickNote>>
}
```

### 2. QuickNoteRepository.kt
**Location:** `data/quicknotes/QuickNoteRepository.kt`

**Purpose:** Abstracts data access and provides clean API for CRUD operations.

**Key Features:**
- Flow-based reactive queries
- Result-based error handling
- Entity ↔ Domain model transformation
- Comprehensive logging

**Public API:**
```kotlin
class QuickNoteRepository(dao: QuickNoteDao) {
    fun getAllNotes(): Flow<List<QuickNote>>
    fun getNoteById(noteId: String): Flow<QuickNote?>
    suspend fun getNoteByIdOnce(noteId: String): QuickNote?
    fun searchNotes(query: String): Flow<List<QuickNote>>

    suspend fun insertNote(note: QuickNote): Result<Unit>
    suspend fun updateNoteContent(noteId: String, content: String): Result<Unit>
    suspend fun updateNoteTitle(noteId: String, title: String): Result<Unit>
    suspend fun deleteNoteById(noteId: String): Result<Unit>
}
```

### 3. QuickNoteDao.kt
**Location:** `data/quicknotes/QuickNoteDao.kt`

**Purpose:** Room DAO interface for database operations.

**Key Queries:**
```kotlin
@Dao
interface QuickNoteDao {
    @Query("SELECT * FROM quick_notes ORDER BY lastModified DESC")
    fun getAllNotes(): Flow<List<QuickNoteEntity>>

    @Query("SELECT * FROM quick_notes WHERE id = :noteId")
    fun getNoteById(noteId: String): Flow<QuickNoteEntity?>

    @Query("""
        SELECT * FROM quick_notes
        WHERE title LIKE '%' || :query || '%'
        OR content LIKE '%' || :query || '%'
        ORDER BY lastModified DESC
    """)
    fun searchNotes(query: String): Flow<List<QuickNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: QuickNoteEntity)

    @Update
    suspend fun updateNote(note: QuickNoteEntity)

    @Delete
    suspend fun deleteNote(note: QuickNoteEntity)
}
```

### 4. QuickNoteEntity.kt
**Location:** `data/quicknotes/QuickNoteEntity.kt`

**Purpose:** Room entity and domain model definitions.

**Schema:**
```kotlin
@Entity(tableName = "quick_notes")
data class QuickNoteEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val content: String,
    val linkedDocuments: List<LinkedDocumentEntity>, // Kept for migration
    val timestamp: Long,
    val lastModified: Long = System.currentTimeMillis()
)

// Domain model
data class QuickNote(
    val id: String,
    val title: String = "",
    val content: String = "",
    val linkedDocuments: List<LinkedDocument> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
```

### 5. QuickAccessSheet.kt
**Location:** `ui/quickaccess/QuickAccessSheet.kt`

**Purpose:** Material Design 3 UI for note editing and management.

**Features:**
- Horizontal scrollable note tabs with FilterChips
- Search bar (appears when multiple notes exist)
- Dual editing mode (Normal/Markdown)
- Auto-save with status indicator
- Clickable markdown links
- Quick text input field

---

## Database Schema

### Table: `quick_notes`

| Column          | Type   | Constraints    | Description                    |
|-----------------|--------|----------------|--------------------------------|
| id              | String | PRIMARY KEY    | Unique note identifier         |
| title           | String |                | Note title                     |
| content         | String |                | Note content (supports markdown) |
| linkedDocuments | String | JSON           | Deprecated - kept for migration|
| timestamp       | Long   |                | Creation timestamp             |
| lastModified    | Long   |                | Last modification timestamp    |

**Indexes:**
- Primary key on `id`
- Implicit index on `lastModified` (used in ORDER BY)

---

## Data Flow

### 1. Creating a Note

```
User clicks "Add Note"
        ↓
QuickAccessSheet calls noteManager.addNote()
        ↓
QuickNoteManager creates QuickNote with unique ID
        ↓
Repository.insertNote() converts to QuickNoteEntity
        ↓
DAO.insertNote() writes to Room Database
        ↓
Room emits updated list via Flow
        ↓
Repository transforms entities to domain models
        ↓
QuickNoteManager updates StateFlows
        ↓
QuickAccessSheet recomposes with new note
```

### 2. Saving Note Content (Auto-save)

```
User types in note editor
        ↓
Local state updates (hasChanges = true)
        ↓
LaunchedEffect delays 2 seconds
        ↓
noteManager.saveNoteContent() called
        ↓
Repository.updateNoteContent() updates database
        ↓
Room emits updated note via Flow
        ↓
UI updates with saved state
```

### 3. Searching Notes

```
User types in search bar
        ↓
searchQuery state updates
        ↓
LazyRow filters notes locally OR
        ↓
noteManager.searchNotes(query) triggers database search
        ↓
DAO searches with LIKE query
        ↓
Results flow back through Repository → Manager → UI
```

---

## Usage Examples

### Basic Usage in Compose UI

```kotlin
@Composable
fun MyScreen() {
    val context = LocalContext.current
    val noteManager = remember { QuickNoteManager(context) }

    // Observe notes
    val notes by noteManager.notes.collectAsState()
    val activeContent by noteManager.noteContent.collectAsState()

    // Create new note
    Button(onClick = {
        noteManager.addNote(title = "My Note", content = "Hello World")
    }) {
        Text("New Note")
    }

    // Save content
    TextField(
        value = activeContent,
        onValueChange = { newContent ->
            noteManager.saveNoteContent(content = newContent)
        }
    )
}
```

### Programmatic Note Operations

```kotlin
val noteManager = QuickNoteManager(context)

// Create a note
val noteId = noteManager.addNote(
    title = "Meeting Notes",
    content = "Discussed project requirements",
    setActive = true
)

// Update content
noteManager.saveNoteContent(
    noteId = noteId,
    content = "Updated meeting notes\n- Action item 1\n- Action item 2"
)

// Rename note
noteManager.renameNote(noteId, "Q1 Meeting Notes")

// Search notes
noteManager.searchNotes("meeting")
noteManager.getSearchResults("meeting").collect { results ->
    println("Found ${results.size} notes")
}

// Delete note
noteManager.removeNote(noteId)
```

### Adding Document Links

```kotlin
// Add markdown link to active note
noteManager.addLinkedDocument(
    filePath = "/storage/emulated/0/document.pdf",
    fileName = "Project Requirements.pdf",
    pageNumber = 5
)

// This creates markdown link in note content:
// 📎 [Project Requirements.pdf](internal://open?file=/storage/...&page=5)
```

---

## Migration Guide

### From SharedPreferences (v1.0.3) to Room (v1.0.4)

Migration happens **automatically** on first launch:

1. **Manager checks** if Room database is empty
2. **If empty**, loads notes from SharedPreferences
3. **Inserts** all notes into Room database
4. **Clears** old SharedPreferences keys
5. **Continues** with Room as single source of truth

**Old SharedPreferences Keys:**
- `quick_note_content` (single note content)
- `linked_documents` (deprecated linked docs)
- `quick_notes_list` (multiple notes JSON)
- `active_quick_note_id` (active note ID - kept for active state)

**Migration Code:**
```kotlin
// In QuickNoteManager.init
val notesCount = repository.getNotesCount()
if (notesCount == 0) {
    migrateFromSharedPreferences()
}
```

---

## Best Practices

### 1. StateFlow Observation
Always observe StateFlows in Composables:
```kotlin
val notes by noteManager.notes.collectAsState()
// NOT: noteManager.notes.value (won't recompose)
```

### 2. Coroutine Scope
Manager handles coroutines internally - no need to launch:
```kotlin
// GOOD
noteManager.saveNote(content)

// UNNECESSARY
viewModelScope.launch {
    noteManager.saveNote(content)
}
```

### 3. Error Handling
Repository returns `Result<Unit>` for operations:
```kotlin
suspend fun saveNoteWithErrorHandling(note: QuickNote) {
    repository.insertNote(note)
        .onSuccess { /* Success UI */ }
        .onFailure { error -> /* Show error */ }
}
```

### 4. Memory Management
QuickNoteManager uses application context internally. Safe to create multiple instances, but prefer singleton:
```kotlin
// In ViewModel or Application class
val noteManager = QuickNoteManager(applicationContext)
```

### 5. Testing
Use repository for unit tests, manager for integration tests:
```kotlin
@Test
fun `repository inserts note correctly`() = runTest {
    val note = QuickNote(id = "test", content = "Test")
    repository.insertNote(note)
    val result = repository.getNoteByIdOnce("test")
    assertEquals("Test", result?.content)
}
```

---

## Troubleshooting

### Issue: Notes not saving
**Symptoms:** Changes lost on app restart
**Causes:**
- Database not initialized
- Migration failed
- Permissions issue

**Solution:**
1. Check Logcat for `QuickNoteManager` or `QuickNoteRepository` errors
2. Verify database file exists: `adb shell ls /data/data/com.example.checklist_interactive/databases/`
3. Clear app data and retry

### Issue: Search not working
**Symptoms:** Search returns no results despite matching content
**Causes:**
- Case sensitivity issues
- LIKE query not matching

**Solution:**
```kotlin
// DAO uses case-insensitive search:
WHERE title LIKE '%' || :query || '%'
// Ensure query is not pre-escaped
```

### Issue: UI not updating
**Symptoms:** Changes not reflected in UI
**Causes:**
- Not collecting StateFlow
- Using `.value` instead of `collectAsState()`

**Solution:**
```kotlin
// WRONG
val notes = noteManager.notes.value

// CORRECT
val notes by noteManager.notes.collectAsState()
```

### Issue: Migration not happening
**Symptoms:** Old notes missing after update
**Causes:**
- Database already exists from previous install
- SharedPreferences keys already cleared

**Solution:**
1. Check migration logs: `adb logcat -s QuickNoteManager:D`
2. Manually trigger migration (testing only):
```kotlin
// Clear Room database
context.deleteDatabase("quick_notes_database")
// Reinstall app
```

---

## Advanced Topics

### Custom Database Queries

Add custom DAO methods for specific use cases:

```kotlin
@Query("""
    SELECT * FROM quick_notes
    WHERE timestamp > :since
    ORDER BY lastModified DESC
    LIMIT :limit
""")
fun getRecentNotes(since: Long, limit: Int): Flow<List<QuickNoteEntity>>
```

### Export/Import Notes

```kotlin
suspend fun exportNotes(): String {
    val notes = repository.getAllNotes().first()
    return Json.encodeToString(notes)
}

suspend fun importNotes(json: String) {
    val notes = Json.decodeFromString<List<QuickNote>>(json)
    repository.insertNotes(notes)
}
```

### Note Templates

```kotlin
fun createNoteFromTemplate(template: NoteTemplate): String {
    return noteManager.addNote(
        title = template.title,
        content = template.content,
        setActive = true
    )
}
```

---

## Performance Considerations

### Database Operations
- ✅ All database operations are async (suspend functions)
- ✅ Flow-based queries update automatically
- ✅ Indexed searches for optimal performance
- ⚠️ LIKE queries can be slow on very large datasets (>10,000 notes)

### UI Rendering
- ✅ LazyRow for horizontal scrolling (better than Row)
- ✅ FilterChips instead of heavy button layouts
- ✅ Conditional rendering (hide search if only 1 note)
- ⚠️ ClickableText with long notes can impact scroll performance

### Memory Usage
- ✅ StateFlows only hold current state
- ✅ Room manages database connections efficiently
- ⚠️ Loading all notes at once (consider pagination for >1000 notes)

---

## Future Enhancements

Planned features for future versions:

1. **Note Categories/Tags** - Organize notes into categories
2. **Rich Text Editor** - WYSIWYG markdown editing
3. **Note Sharing** - Export/import individual notes
4. **Encryption** - Optional note encryption with user password
5. **Cloud Sync** - Sync notes across devices
6. **Voice Notes** - Audio recording and transcription
7. **Attachments** - Embed images and files
8. **Version History** - Track note revisions

---

## References

- [Room Database Documentation](https://developer.android.com/training/data-storage/room)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [StateFlow Documentation](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/)
- [Material Design 3](https://m3.material.io/)
- [Repository Pattern](https://developer.android.com/topic/architecture/data-layer)

---

**Last Updated:** 2025-12-12
**Version:** 1.0.4
**Author:** Development Team
