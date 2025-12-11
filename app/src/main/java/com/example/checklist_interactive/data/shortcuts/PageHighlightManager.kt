package com.example.checklist_interactive.data.shortcuts

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Repräsentiert ein Seiten-Highlight (gesamte Seite ist markiert)
 */
@Serializable
data class PageHighlight(
    val filePath: String,
    val pageNumber: Int,
    val color: Long = 0xFFFFFF00, // Default: Gelb
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Verwaltet Seiten-Highlights
 */
class PageHighlightManager(private val context: Context) {
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val highlightsFile: File
        get() = File(context.filesDir, "page_highlights.json")
    
    /**
     * Lädt alle Highlights
     */
    fun loadHighlights(): List<PageHighlight> {
        return try {
            if (!highlightsFile.exists()) {
                return emptyList()
            }
            val jsonString = highlightsFile.readText()
            json.decodeFromString<List<PageHighlight>>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Speichert alle Highlights
     */
    private fun saveHighlights(highlights: List<PageHighlight>) {
        try {
            val jsonString = json.encodeToString(highlights)
            highlightsFile.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Fügt ein Highlight hinzu oder entfernt es, wenn es bereits existiert
     */
    fun togglePageHighlight(filePath: String, pageNumber: Int, color: Long = 0xFFFFFF00): Boolean {
        val highlights = loadHighlights().toMutableList()
        val existing = highlights.find { 
            it.filePath == filePath && it.pageNumber == pageNumber 
        }
        
        return if (existing != null) {
            // Entfernen
            highlights.removeAll { it.filePath == filePath && it.pageNumber == pageNumber }
            saveHighlights(highlights)
            false
        } else {
            // Hinzufügen
            highlights.add(PageHighlight(filePath, pageNumber, color))
            saveHighlights(highlights)
            true
        }
    }
    
    /**
     * Prüft ob eine Seite highlighted ist
     */
    fun isPageHighlighted(filePath: String, pageNumber: Int): Boolean {
        return loadHighlights().any { 
            it.filePath == filePath && it.pageNumber == pageNumber 
        }
    }
    
    /**
     * Gibt alle Highlights für eine Datei zurück
     */
    fun getHighlightsForFile(filePath: String): List<PageHighlight> {
        return loadHighlights().filter { it.filePath == filePath }
    }
    
    /**
     * Löscht alle Highlights für eine Datei
     */
    fun clearHighlightsForFile(filePath: String) {
        val highlights = loadHighlights().toMutableList()
        highlights.removeAll { it.filePath == filePath }
        saveHighlights(highlights)
    }
}
