package com.example.checklist_interactive.data.shortcuts

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Represents a shortcut to a specific page in a file
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
 * Manages shortcuts to file pages
 */
class ShortcutManager(private val context: Context) {
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val shortcutsFile: File
        get() = File(context.filesDir, "page_shortcuts.json")
    
    /**
     * Loads all shortcuts
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
     * Saves all shortcuts
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
     * Creates a new shortcut
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
     * Deletes a shortcut
     */
    fun deleteShortcut(id: String) {
        val shortcuts = loadShortcuts().toMutableList()
        shortcuts.removeAll { it.id == id }
        saveShortcuts(shortcuts)
    }
    
    /**
     * Renames a shortcut
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
     * Updates the highlighted status of a shortcut
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
     * Returns all shortcuts for a given file
     */
    fun getShortcutsForFile(filePath: String): List<PageShortcut> {
        return loadShortcuts().filter { it.filePath == filePath }
    }
}
