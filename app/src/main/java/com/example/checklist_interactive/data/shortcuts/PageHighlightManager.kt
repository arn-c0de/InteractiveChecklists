package com.example.checklist_interactive.data.shortcuts

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLDecoder

/**
 * Represents a page highlight (whole page is marked)
 */
@Serializable
data class PageHighlight(
    val filePath: String,
    val pageNumber: Int,
    val color: Long = 0xFFFFFF00, // Default: Yellow
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Manages page highlights
 */
class PageHighlightManager(private val context: Context) {
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val highlightsFile: File
        get() = File(context.filesDir, "page_highlights.json")
    
    /**
     * Loads all highlights
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
     * Saves all highlights
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
     * Adds a highlight or removes it if it already exists
     */
    fun togglePageHighlight(filePath: String, pageNumber: Int, color: Long = 0xFFFFFF00): Boolean {
        val highlights = loadHighlights().toMutableList()
        val norm = normalizePath(filePath)
        val existing = highlights.find {
            it.filePath == norm && it.pageNumber == pageNumber
        }
        
        return if (existing != null) {
            // Remove
            highlights.removeAll { it.filePath == filePath && it.pageNumber == pageNumber }
            saveHighlights(highlights)
            false
        } else {
            // Add
            highlights.add(PageHighlight(norm, pageNumber, color))
            saveHighlights(highlights)
            true
        }
    }
    
    /**
     * Checks if a page is highlighted
     */
    fun isPageHighlighted(filePath: String, pageNumber: Int): Boolean {
        val norm = normalizePath(filePath)
        return loadHighlights().any {
            it.filePath == norm && it.pageNumber == pageNumber
        }
    }
    
    /**
     * Returns all highlights for a file
     */
    fun getHighlightsForFile(filePath: String): List<PageHighlight> {
        val norm = normalizePath(filePath)
        return loadHighlights().filter { it.filePath == norm }
    }
    
    /**
     * Clears all highlights for a file
     */
    fun clearHighlightsForFile(filePath: String) {
        val highlights = loadHighlights().toMutableList()
        val norm = normalizePath(filePath)
        highlights.removeAll { it.filePath == norm }
        saveHighlights(highlights)
    }

    private fun normalizePath(path: String): String {
        var p = path
        try {
            p = URLDecoder.decode(p, "UTF-8")
        } catch (_: Exception) {}
        if (p.startsWith("asset://")) p = p.removePrefix("asset://")
        return p.trim()
    }
}
