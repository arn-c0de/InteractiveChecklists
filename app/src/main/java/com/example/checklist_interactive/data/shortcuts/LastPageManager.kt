package com.example.checklist_interactive.data.shortcuts

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLDecoder

/**
 * Repräsentiert die zuletzt geöffnete Seite einer Datei
 */
@Serializable
data class LastPageRecord(
    val filePath: String,
    val pageNumber: Int,
    val lastAccessedAt: Long = System.currentTimeMillis()
)

/**
 * Verwaltet die zuletzt geöffneten Seiten für PDFs
 */
class LastPageManager(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val lastPagesFile: File
        get() = File(context.filesDir, "last_pages.json")

    /**
     * Lädt alle gespeicherten letzten Seiten
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
     * Speichert alle letzten Seiten
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
     * Speichert die zuletzt geöffnete Seite für eine Datei
     */
    fun saveLastPage(filePath: String, pageNumber: Int) {
        val lastPages = loadLastPages().toMutableList()

        val norm = normalizePath(filePath)
        // Entferne vorhandenen Eintrag für diese Datei
        lastPages.removeAll { it.filePath == norm }

        // Füge neuen Eintrag hinzu
        lastPages.add(LastPageRecord(norm, pageNumber))

        saveLastPages(lastPages)
    }

    /**
     * Lädt die zuletzt geöffnete Seite für eine Datei
     * Gibt 0 zurück, wenn keine Seite gespeichert ist
     */
    fun getLastPage(filePath: String): Int {
        val norm = normalizePath(filePath)
        return loadLastPages()
            .find { it.filePath == norm }
            ?.pageNumber ?: 0
    }

    /**
     * Löscht den Eintrag für eine Datei
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
     * Löscht alle Einträge, die älter als die angegebene Anzahl von Tagen sind
     */
    fun clearOldEntries(daysToKeep: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        val lastPages = loadLastPages().filter { it.lastAccessedAt > cutoffTime }
        saveLastPages(lastPages)
    }
}
