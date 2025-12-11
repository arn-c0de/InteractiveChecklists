package com.example.checklist_interactive.data.checklist

import android.content.Context

/**
 * Utility to browse assets in app/src/main/assets/checklists. It returns immediate children
 * under a directory but can be used recursively by calling list() with subfolders.
 */
class AssetBrowser(private val context: Context) {
    private val assetManager = context.assets

    fun list(path: String): List<AssetNode> {
        // Normalize path to avoid leading/trailing slashes
        val normalized = path.trim().trimStart('/').trimEnd('/')
        val results = mutableListOf<AssetNode>()
        try {
            val children = assetManager.list(normalized) ?: emptyArray()
            for (child in children) {
                // Build full path
                val childPath = if (normalized.isEmpty()) child else "$normalized/$child"
                // Try to list the child - if it has children, it's a directory
                val sub = assetManager.list(childPath) ?: emptyArray()
                val isDir = sub.isNotEmpty()
                if (isDir) {
                    results.add(AssetNode(name = child, path = childPath, isDirectory = true))
                } else {
                    // Accept PDF and Markdown files
                    if (child.endsWith(".pdf") || child.endsWith(".md") || child.endsWith(".markdown")) {
                        results.add(AssetNode(name = child, path = childPath, isDirectory = false))
                    }
                }
            }
        } catch (e: Exception) {
            // If list fails, try to see if the path is a file: then return empty list
            // or fallback to empty
        }
        return results.sortedWith(compareByDescending<AssetNode> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /**
     * Scannt rekursiv alle Dateien im angegebenen Pfad und gruppiert sie nach Ordnernamen.
     * @return Map mit Ordnername -> Liste von Dateien in diesem Ordner
     */
    fun scanAllFilesGrouped(rootPath: String): Map<String, List<AssetNode>> {
        val grouped = mutableMapOf<String, MutableList<AssetNode>>()
        scanRecursively(rootPath, rootPath, grouped)
        return grouped.mapValues { it.value.sortedBy { node -> node.name.lowercase() } }
    }

    private fun scanRecursively(
        rootPath: String,
        currentPath: String,
        grouped: MutableMap<String, MutableList<AssetNode>>
    ) {
        try {
            val normalized = currentPath.trim().trimStart('/').trimEnd('/')
            val children = assetManager.list(normalized) ?: emptyArray()

            for (child in children) {
                val childPath = if (normalized.isEmpty()) child else "$normalized/$child"
                val sub = assetManager.list(childPath) ?: emptyArray()
                val isDir = sub.isNotEmpty()

                if (isDir) {
                    // Rekursiv in Unterordner gehen
                    scanRecursively(rootPath, childPath, grouped)
                } else {
                    // Datei gefunden
                    if (child.endsWith(".pdf") || child.endsWith(".md") || child.endsWith(".markdown")) {
                        // Ordnername extrahieren (relativ zum Root)
                        val folderName = if (normalized == rootPath || normalized.isEmpty()) {
                            "Root"
                        } else {
                            normalized.removePrefix("$rootPath/").substringBeforeLast('/').ifEmpty {
                                normalized.removePrefix("$rootPath/")
                            }
                        }

                        grouped.getOrPut(folderName) { mutableListOf() }
                            .add(AssetNode(name = child, path = childPath, isDirectory = false))
                    }
                }
            }
        } catch (e: Exception) {
            // Fehler ignorieren
        }
    }
}
