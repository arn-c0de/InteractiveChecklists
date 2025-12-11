package com.example.checklist_interactive.data.tags

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Represents tags assigned to a file
 */
@Serializable
data class FileTag(
    val filePath: String,
    val tags: Set<String> = emptySet()
)

/**
 * Manages tags for files (PDFs and Markdown files)
 * Tags like "startup", "landing", "combat", "emergency", etc.
 */
class FileTagManager(private val context: Context) {
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val tagsFile: File
        get() = File(context.filesDir, "file_tags.json")

    init {
        // On first run, if the internal tags file does not exist, try to copy a default one from assets.
        try {
            if (!tagsFile.exists()) {
                context.assets.open("file_tags.json").use { input ->
                    tagsFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
        } catch (e: Exception) {
            // If there is no asset or another error occurs, ensure the file exists with an empty array
            try {
                if (!tagsFile.exists()) {
                    tagsFile.writeText("[]")
                }
            } catch (_: Exception) { }
        }
    }
    
    /**
     * Loads all file tags from storage
     */
    fun loadFileTags(): List<FileTag> {
        return try {
            if (!tagsFile.exists()) {
                return emptyList()
            }
            val jsonString = tagsFile.readText()
            if (jsonString.isBlank()) {
                return emptyList()
            }
            json.decodeFromString<List<FileTag>>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Saves all file tags to storage
     */
    private fun saveFileTags(tags: List<FileTag>) {
        try {
            val jsonString = json.encodeToString(tags)
            tagsFile.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Gets tags for a specific file
     */
    fun getTagsForFile(filePath: String): Set<String> {
        return loadFileTags().find { it.filePath == filePath }?.tags ?: emptySet()
    }
    
    /**
     * Sets tags for a specific file
     */
    fun setTagsForFile(filePath: String, tags: Set<String>) {
        val allTags = loadFileTags().toMutableList()
        val existingIndex = allTags.indexOfFirst { it.filePath == filePath }
        
        if (tags.isEmpty()) {
            // Remove the entry if no tags
            if (existingIndex != -1) {
                allTags.removeAt(existingIndex)
            }
        } else {
            // Update or add the entry
            val fileTag = FileTag(filePath, tags)
            if (existingIndex != -1) {
                allTags[existingIndex] = fileTag
            } else {
                allTags.add(fileTag)
            }
        }
        
        saveFileTags(allTags)
    }
    
    /**
     * Adds a tag to a file
     */
    fun addTagToFile(filePath: String, tag: String) {
        val currentTags = getTagsForFile(filePath).toMutableSet()
        currentTags.add(tag)
        setTagsForFile(filePath, currentTags)
    }
    
    /**
     * Removes a tag from a file
     */
    fun removeTagFromFile(filePath: String, tag: String) {
        val currentTags = getTagsForFile(filePath).toMutableSet()
        currentTags.remove(tag)
        setTagsForFile(filePath, currentTags)
    }
    
    /**
     * Gets all unique tags used across all files
     */
    fun getAllUsedTags(): Set<String> {
        return loadFileTags().flatMap { it.tags }.toSet()
    }
    
    /**
     * Gets all files that have a specific tag
     */
    fun getFilesWithTag(tag: String): List<String> {
        return loadFileTags()
            .filter { it.tags.contains(tag) }
            .map { it.filePath }
    }
    
    /**
     * Gets all files that have ANY of the specified tags
     */
    fun getFilesWithAnyTag(tags: Set<String>): List<String> {
        if (tags.isEmpty()) return emptyList()
        return loadFileTags()
            .filter { fileTag -> fileTag.tags.any { it in tags } }
            .map { it.filePath }
    }
    
    /**
     * Gets all files that have ALL of the specified tags
     */
    fun getFilesWithAllTags(tags: Set<String>): List<String> {
        if (tags.isEmpty()) return emptyList()
        return loadFileTags()
            .filter { fileTag -> tags.all { it in fileTag.tags } }
            .map { it.filePath }
    }
    
    /**
     * Removes all tags associated with a file (useful when file is deleted)
     */
    fun removeFileFromTags(filePath: String) {
        setTagsForFile(filePath, emptySet())
    }
    
    /**
     * Updates file path in tags (useful when file is renamed or moved)
     */
    fun updateFilePath(oldPath: String, newPath: String) {
        val tags = getTagsForFile(oldPath)
        if (tags.isNotEmpty()) {
            removeFileFromTags(oldPath)
            setTagsForFile(newPath, tags)
        }
    }
    
    /**
     * Predefined/suggested tags that can be used
     */
    companion object {
        val SUGGESTED_TAGS = listOf(
            "startup",
            "landing",
            "combat",
            "emergency",
            "normal",
            "abnormal",
            "takeoff",
            "approach",
            "taxi",
            "preflight",
            "postflight",
            "systems",
            "weapons",
            "navigation",
            "communications",
            "fuel",
            "electrical",
            "hydraulic",
            "important",
            "reference",
            "training"
        ).sorted()
    }
}
