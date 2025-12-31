package com.example.checklist_interactive.ui.checklist.pdf

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages caching of parsed PDF outlines to avoid re-parsing on every document open.
 * Cache entries are invalidated when the source file is modified.
 */
class PdfOutlineCacheManager(context: Context) {

    private val TAG = "PdfOutlineCache"
    private val prefs: SharedPreferences = context.getSharedPreferences("pdf_outline_cache", Context.MODE_PRIVATE)

    /**
     * Retrieves cached outline for a PDF file if available and not stale.
     * Returns null if no valid cache exists.
     */
    fun getCachedOutline(pdfFile: File): List<PdfOutlineItem>? {
        try {
            val cacheKey = getCacheKey(pdfFile)
            val cachedJson = prefs.getString(cacheKey, null) ?: return null

            val cacheData = JSONObject(cachedJson)
            val cachedTimestamp = cacheData.getLong("timestamp")
            val currentTimestamp = pdfFile.lastModified()

            // Invalidate cache if file was modified
            if (cachedTimestamp != currentTimestamp) {
                Log.d(TAG, "Cache invalidated for ${pdfFile.name} (file modified)")
                removeCachedOutline(pdfFile)
                return null
            }

            val outlineArray = cacheData.getJSONArray("outline")
            val outlineItems = mutableListOf<PdfOutlineItem>()

            for (i in 0 until outlineArray.length()) {
                val item = outlineArray.getJSONObject(i)
                outlineItems.add(
                    PdfOutlineItem(
                        title = item.getString("title"),
                        pageNumber = item.getInt("pageNumber"),
                        level = item.getInt("level")
                    )
                )
            }

            Log.d(TAG, "Cache hit for ${pdfFile.name} (${outlineItems.size} items)")
            return outlineItems

        } catch (e: Exception) {
            Log.e(TAG, "Error reading cache for ${pdfFile.name}: ${e.message}", e)
            return null
        }
    }

    /**
     * Stores parsed outline in cache with file's current modification timestamp.
     */
    fun cacheOutline(pdfFile: File, outline: List<PdfOutlineItem>) {
        try {
            val cacheKey = getCacheKey(pdfFile)
            val cacheData = JSONObject()
            cacheData.put("timestamp", pdfFile.lastModified())

            val outlineArray = JSONArray()
            outline.forEach { item ->
                val itemJson = JSONObject()
                itemJson.put("title", item.title)
                itemJson.put("pageNumber", item.pageNumber)
                itemJson.put("level", item.level)
                outlineArray.put(itemJson)
            }
            cacheData.put("outline", outlineArray)

            prefs.edit().putString(cacheKey, cacheData.toString()).apply()
            Log.d(TAG, "Cached ${outline.size} outline items for ${pdfFile.name}")

        } catch (e: Exception) {
            Log.e(TAG, "Error caching outline for ${pdfFile.name}: ${e.message}", e)
        }
    }

    /**
     * Removes cached outline for a specific file.
     */
    fun removeCachedOutline(pdfFile: File) {
        val cacheKey = getCacheKey(pdfFile)
        prefs.edit().remove(cacheKey).apply()
    }

    /**
     * Clears all cached outlines.
     */
    fun clearAllCache() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Cleared all cached outlines")
    }

    /**
     * Returns the number of cached entries.
     */
    fun getCacheSize(): Int {
        return prefs.all.size
    }

    /**
     * Generates a unique cache key for a PDF file using its absolute path hash.
     */
    private fun getCacheKey(pdfFile: File): String {
        // Use file path hash to create a stable key
        return "outline_${pdfFile.absolutePath.hashCode()}"
    }
}
