package com.example.checklist_interactive.data.shortcuts

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLDecoder

/**
 * Represents the last opened page of a file
 */
@Serializable
data class LastPageRecord(
    val filePath: String,
    val pageNumber: Int,
    val lastAccessedAt: Long = System.currentTimeMillis()
)

/**
 * Manages last-opened pages for PDFs
 */
class LastPageManager(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val lastPagesFile: File
        get() = File(context.filesDir, "last_pages.json")

    /**
     * Loads all stored last-page entries
     */
    private fun loadLastPages(): List<LastPageRecord> {
        return try {
            if (!lastPagesFile.exists()) {
                return emptyList()
            }
            val jsonString = lastPagesFile.readText()
            json.decodeFromString<List<LastPageRecord>>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Saves all last-page entries
     */
    private fun saveLastPages(lastPages: List<LastPageRecord>) {
        try {
            val jsonString = json.encodeToString(lastPages)
            lastPagesFile.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Saves the last opened page for a file
     */
    fun saveLastPage(filePath: String, pageNumber: Int) {
        val lastPages = loadLastPages().toMutableList()

        val norm = normalizePath(filePath)
        // Remove any existing entry for this file
        lastPages.removeAll { it.filePath == norm }

        // Add new entry
        lastPages.add(LastPageRecord(norm, pageNumber))

        saveLastPages(lastPages)
    }

    /**
     * Loads the last opened page for a file
     * Returns 0 if no page is stored
     */
    fun getLastPage(filePath: String): Int {
        val norm = normalizePath(filePath)
        return loadLastPages()
            .find { it.filePath == norm }
            ?.pageNumber ?: 0
    }

    /**
     * Deletes the entry for a file
     */
    fun clearLastPage(filePath: String) {
        val lastPages = loadLastPages().toMutableList()
        val norm = normalizePath(filePath)
        lastPages.removeAll { it.filePath == norm }
        saveLastPages(lastPages)
    }

    private fun normalizePath(path: String): String {
        var p = path
        try {
            p = URLDecoder.decode(p, "UTF-8")
        } catch (_: Exception) {}
        if (p.startsWith("asset://")) p = p.removePrefix("asset://")
        return p.trim()
    }

    /**
     * Clears all entries older than the specified number of days
     */
    fun clearOldEntries(daysToKeep: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        val lastPages = loadLastPages().filter { it.lastAccessedAt > cutoffTime }
        saveLastPages(lastPages)
    }
}
