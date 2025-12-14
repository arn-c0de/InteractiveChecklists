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
    
    // Cache for loaded tags - retained for the app session
    @Volatile
    private var tagsCache: List<FileTag>? = null
    // Map caches for fast lookups (normalized path -> tags) and name -> tags
    @Volatile
    private var tagsCacheMap: Map<String, Set<String>>? = null
    @Volatile
    private var tagsByNameMap: Map<String, Set<String>>? = null
    private val cacheLock = Any()

    @Volatile
    private var initialized = false

    init {
        // Ensure tags file exists; but avoid expensive sync on the UI thread.
        try {
            if (!tagsFile.exists()) {
                context.assets.open("file_tags.json").use { input ->
                    tagsFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
        } catch (e: Exception) {
            try {
                if (!tagsFile.exists()) {
                    tagsFile.writeText("[]")
                }
            } catch (_: Exception) { }
        }
    }

    // Synchronize asset-provided tags and add heuristic tags for asset files missing explicit tags
    private fun syncAssetTagsAndHeuristics() {
        // 1) Read asset file_tags.json if present
        val assetTags = try {
            context.assets.open("file_tags.json").use { input ->
                val s = input.readBytes().toString(Charsets.UTF_8)
                if (s.isBlank()) emptyList<FileTag>() else json.decodeFromString<List<FileTag>>(s)
            }
        } catch (e: Exception) {
            emptyList()
        }

        // Load current internal tags into a mutable map for quick updates
        val internal = loadFileTags().associateBy { normalizePath(it.filePath) }.toMutableMap()

        // Merge asset tags: union of tag sets, do not remove user tags
        for (a in assetTags) {
            val key = normalizePath(a.filePath)
            val existing = internal[key]
            if (existing != null) {
                val union = (existing.tags + a.tags).toSet()
                if (union != existing.tags) {
                    // Preserve the stored filePath string (absolute/internal path), keep unioned tags
                    internal[key] = FileTag(existing.filePath, union)
                }
            } else {
                internal[key] = FileTag(a.filePath, a.tags)
            }
        }

        // 2) Walk assets and add heuristic tags for files not covered by assetTags
        fun walker(path: String) {
            val list = try { context.assets.list(path) ?: emptyArray() } catch (e: Exception) { emptyArray() }
            if (list.isEmpty()) return
            for (child in list) {
                val childPath = if (path.isEmpty()) child else "$path/$child"
                val sub = try { context.assets.list(childPath) ?: emptyArray() } catch (e: Exception) { emptyArray() }
                if (sub.isNotEmpty()) {
                    walker(childPath)
                } else {
                    val lower = child.lowercase()
                    if (lower.endsWith(".pdf") || lower.endsWith(".md") || lower.endsWith(".markdown")) {
                        val assetPath = "asset://$childPath"
                        val key = normalizePath(assetPath)
                        if (internal.containsKey(key)) continue // already present
                        // derive heuristic tags from filename
                        val heurTags = heuristicTagsFromName(child)
                        if (heurTags.isNotEmpty()) {
                            internal[key] = FileTag(assetPath, heurTags)
                        } else {
                            // Ensure asset is present in tags file even if no tags assigned yet
                            internal[key] = FileTag(assetPath, emptySet())
                        }
                    }
                }
            }
        }

        walker("")

        // 3) Persist merged map back to internal tags file (replace fully)
        saveFileTags(internal.values.toList())
    }

    /**
     * Initialize the manager asynchronously if not already done.
     * This will perform the asset merge and build caches on IO thread.
     */
    suspend fun initializeIfNeeded() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            // perform initialization work on caller thread (expected to be IO dispatcher)
            try {
                syncAssetTagsAndHeuristics()
            } catch (_: Exception) {
                // ignore
            }
            // pre-load tags into memory
            loadFileTags()
            initialized = true
        }
    }

    // Simple heuristics to derive tags from filename
    private fun heuristicTagsFromName(fileName: String): Set<String> {
        val name = fileName.lowercase()
        val tags = mutableSetOf<String>()
        if (name.contains("start") || name.contains("startup")) tags.add("startup")
        if (name.contains("takeoff")) tags.add("takeoff")
        if (name.contains("landing")) tags.add("landing")
        if (name.contains("taxi")) tags.add("taxi")
        if (name.contains("shutdown")) tags.add("postflight")
        if (name.contains("combat") || name.contains("air_to_air") || name.contains("air_to_ground") || name.contains("air-to-air") || name.contains("air-to-ground")) tags.add("combat")
        if (name.contains("carrier")) tags.add("carrier")
        if (name.contains("refu" ) || name.contains("refuelling")) tags.add("fuel")
        if (name.contains("nav") || name.contains("navigation")) tags.add("navigation")
        if (name.contains("comm" ) || name.contains("radio")) tags.add("communications")
        return tags
    }
    
    /**
     * Loads all file tags from storage
     * Uses in-memory cache for session - only loads from disk once
     */
    fun loadFileTags(): List<FileTag> {
        // Fast path: return cached data if available
        tagsCache?.let { return it }
        
        // Slow path: load from disk and cache
        synchronized(cacheLock) {
            // Double-check after acquiring lock
            tagsCache?.let { return it }
            
            val loaded = try {
                if (!tagsFile.exists()) {
                    emptyList()
                } else {
                    val jsonString = tagsFile.readText()
                    if (jsonString.isBlank()) {
                        emptyList()
                    } else {
                        json.decodeFromString<List<FileTag>>(jsonString)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
            
            tagsCache = loaded
            // Also build fast lookup maps
            tagsCacheMap = loaded.associate { normalizePath(it.filePath) to it.tags }
            tagsByNameMap = loaded.groupBy { normalizePath(it.filePath).substringAfterLast('/') }
                .mapValues { it.value.flatMap { ft -> ft.tags }.toSet() }
            return loaded
        }
    }
    
    /**
     * Invalidates the cache - call this after modifying tags
     */
    private fun invalidateCache() {
        synchronized(cacheLock) {
            tagsCache = null
            tagsCacheMap = null
            tagsByNameMap = null
        }
    }
    
    /**
     * Saves all file tags to storage
     */
    private fun saveFileTags(tags: List<FileTag>) {
        try {
            val jsonString = json.encodeToString(tags)
            tagsFile.writeText(jsonString)
            // Update cache with new data
            synchronized(cacheLock) {
                tagsCache = tags
                tagsCacheMap = tags.associate { normalizePath(it.filePath) to it.tags }
                tagsByNameMap = tags.groupBy { normalizePath(it.filePath).substringAfterLast('/') }
                    .mapValues { it.value.flatMap { ft -> ft.tags }.toSet() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Gets tags for a specific file
     */
    fun getTagsForFile(filePath: String): Set<String> {
        val normalized = normalizePath(filePath)
        // Fast path: try the maps
        loadFileTags() // ensure maps are loaded
        tagsCacheMap?.get(normalized)?.let { return it }
        val fileName = normalized.substringAfterLast('/')
        val byName = tagsByNameMap?.get(fileName)
        if (!byName.isNullOrEmpty()) return byName

        // Fallback: try suffix match but using cached map keys for faster scan
        val lowerNormalized = normalized.lowercase()
        val suffixMatches = tagsCacheMap?.entries?.asSequence()
            ?.filter { (k, _) -> k.lowercase().endsWith("/${lowerNormalized}") || k.lowercase().endsWith(lowerNormalized) }
            ?.flatMap { it.value.asSequence() }
            ?.toSet() ?: emptySet()
        return suffixMatches
    }
    
    /**
     * Sets tags for a specific file
     */
    fun setTagsForFile(filePath: String, tags: Set<String>) {
        val normalized = normalizePath(filePath)
        val allTags = loadFileTags().toMutableList()
        val existingIndex = allTags.indexOfFirst { normalizePath(it.filePath) == normalized }
        
        if (tags.isEmpty()) {
            // Remove the entry if no tags
            if (existingIndex != -1) {
                allTags.removeAt(existingIndex)
            }
        } else {
            // Update or add the entry
            val fileTag = FileTag(normalized, tags)
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
        loadFileTags()
        return tagsCacheMap?.values?.flatMap { it.asSequence() }?.toSet() ?: emptySet()
    }
    
    /**
     * Gets all files that have a specific tag
     */
    fun getFilesWithTag(tag: String): List<String> {
        loadFileTags()
        return tagsCacheMap?.entries?.filter { it.value.contains(tag) }?.map { it.key } ?: emptyList()
    }
    
    /**
     * Gets all files that have ANY of the specified tags
     */
    fun getFilesWithAnyTag(tags: Set<String>): List<String> {
        if (tags.isEmpty()) return emptyList()
        loadFileTags()
        return tagsCacheMap?.entries?.filter { entry -> entry.value.any { it in tags } }?.map { it.key } ?: emptyList()
    }
    
    /**
     * Gets all files that have ALL of the specified tags
     */
    fun getFilesWithAllTags(tags: Set<String>): List<String> {
        if (tags.isEmpty()) return emptyList()
        loadFileTags()
        return tagsCacheMap?.entries?.filter { entry -> tags.all { it in entry.value } }?.map { it.key } ?: emptyList()
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

    private fun normalizePath(path: String): String {
        var p = path
        // Strip asset scheme
        if (p.startsWith("asset://")) p = p.removePrefix("asset://")
        return p.replace('\\', '/').trimStart('/')
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
            "carrier",
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
