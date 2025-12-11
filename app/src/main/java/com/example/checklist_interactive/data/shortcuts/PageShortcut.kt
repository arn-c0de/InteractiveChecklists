package com.example.checklist_interactive.data.shortcuts

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Repräsentiert einen Shortcut zu einer bestimmten Seite in einer Datei
 */
@Serializable
data class PageShortcut(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val filePath: String,
    val pageNumber: Int,
    val isHighlighted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val fileName: String
        get() = File(filePath).nameWithoutExtension
}

/**
 * Verwaltet Shortcuts zu Dateiseiten
 */
class ShortcutManager(private val context: Context) {
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val shortcutsFile: File
        get() = File(context.filesDir, "page_shortcuts.json")
    
    /**
     * Lädt alle Shortcuts
     */
    fun loadShortcuts(): List<PageShortcut> {
        return try {
            if (!shortcutsFile.exists()) {
                return emptyList()
            }
            val jsonString = shortcutsFile.readText()
            json.decodeFromString<List<PageShortcut>>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Speichert alle Shortcuts
     */
    private fun saveShortcuts(shortcuts: List<PageShortcut>) {
        try {
            val jsonString = json.encodeToString(shortcuts)
            shortcutsFile.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Erstellt einen neuen Shortcut
     */
    fun createShortcut(
        name: String,
        filePath: String,
        pageNumber: Int,
        isHighlighted: Boolean = false
    ): PageShortcut {
        val shortcuts = loadShortcuts().toMutableList()
        val shortcut = PageShortcut(
            name = name,
            filePath = filePath,
            pageNumber = pageNumber,
            isHighlighted = isHighlighted
        )
        shortcuts.add(shortcut)
        saveShortcuts(shortcuts)
        return shortcut
    }
    
    /**
     * Löscht einen Shortcut
     */
    fun deleteShortcut(id: String) {
        val shortcuts = loadShortcuts().toMutableList()
        shortcuts.removeAll { it.id == id }
        saveShortcuts(shortcuts)
    }
    
    /**
     * Benennt einen Shortcut um
     */
    fun renameShortcut(id: String, newName: String): Boolean {
        val shortcuts = loadShortcuts().toMutableList()
        val index = shortcuts.indexOfFirst { it.id == id }
        if (index >= 0) {
            shortcuts[index] = shortcuts[index].copy(name = newName)
            saveShortcuts(shortcuts)
            return true
        }
        return false
    }
    
    /**
     * Aktualisiert den Highlighted-Status eines Shortcuts
     */
    fun updateHighlightStatus(id: String, isHighlighted: Boolean) {
        val shortcuts = loadShortcuts().toMutableList()
        val index = shortcuts.indexOfFirst { it.id == id }
        if (index >= 0) {
            shortcuts[index] = shortcuts[index].copy(isHighlighted = isHighlighted)
            saveShortcuts(shortcuts)
        }
    }
    
    /**
     * Gibt alle Shortcuts für eine bestimmte Datei zurück
     */
    fun getShortcutsForFile(filePath: String): List<PageShortcut> {
        return loadShortcuts().filter { it.filePath == filePath }
    }
}
