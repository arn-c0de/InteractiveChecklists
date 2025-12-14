package com.example.checklist_interactive.data.files

import android.content.Context
import android.net.Uri
import com.example.checklist_interactive.data.tags.FileTagManager
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * File information
 */
data class FileInfo(
    val name: String,
    val displayName: String,
    val path: String,
    val category: String,
    val size: Long,
    val lastModified: Long,
    val extension: String,
    val isAsset: Boolean = false,
    val tags: Set<String> = emptySet()
)

/**
 * Manages the app's internal file system.
 * Creates folder structure and imports external files.
 */

class InternalFileManager(private val context: Context) {

    private val rootDir: File = File(context.filesDir, "documents")
    internal val tagManager: FileTagManager by lazy { FileTagManager(context) }

    init {
        // Create root directory if missing
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
        // Delete empty folders on startup (e.g., old dummy folders)
        deleteEmptyFolders(rootDir)
    }
    
    /**
     * Public method to get relative path for external access
     */
    fun getRelativePath(absolutePath: String): String {
        // Handle asset paths
        if (absolutePath.startsWith("asset://")) {
            return absolutePath.removePrefix("asset://").removePrefix("/").replace('\\', '/')
        }
        val file = File(absolutePath)
        return if (file.absolutePath.startsWith(rootDir.absolutePath)) {
            file.absolutePath.removePrefix(rootDir.absolutePath)
                .removePrefix(File.separator)
                .replace('\\', '/') // Normalize to forward slashes
        } else {
            // For other paths just normalize separators
            absolutePath.replace('\\', '/')
        }
    }
    
    /**
     * Enriches a FileInfo object with tags from the tag manager
     */
    fun enrichWithTags(fileInfo: FileInfo): FileInfo {
        // Convert absolute path to relative path from root directory
        val relativePath = getRelativePath(fileInfo.path)
        val tags = tagManager.getTagsForFile(relativePath)
        return fileInfo.copy(tags = tags)
    }
    
    /**
     * Enriches a list of FileInfo objects with tags
     */
    fun enrichWithTags(fileInfos: List<FileInfo>): List<FileInfo> {
        return fileInfos.map { enrichWithTags(it) }
    }
    

    /**
     * Deletes empty folders recursively (excluding PDF/MD/Markdown files) under the given directory
     */
    private fun deleteEmptyFolders(dir: File) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                deleteEmptyFolders(file)
                val hasSupportedFiles = file.listFiles()?.any {
                    it.isFile && (it.extension.equals("pdf", true) || it.extension.equals("md", true) || it.extension.equals("markdown", true))
                } == true
                val hasSubDirs = file.listFiles()?.any { it.isDirectory } == true
                if (!hasSupportedFiles && !hasSubDirs) {
                    file.delete()
                }
            }
        }
    }

    /**
     * Returns the internal root path as a String
     */
    fun getInternalRootPath(): String {
        return rootDir.absolutePath
    }

    /**
     * Ensures that the root directory exists
     */
    private fun ensureDirectoryStructure() {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }

    /**
     * Returns all categories (folders in the root directory)
     */
    fun getCategories(): List<String> {
        ensureDirectoryStructure()
        val internal = rootDir.listFiles()
            ?.filter { dir -> dir.isDirectory }
            ?.map { it.name }
            ?: emptyList()

        val assetTop = try {
            context.assets.list("")?.filter { entry ->
                try {
                    val arr = context.assets.list(entry)
                    arr != null && arr.isNotEmpty()
                } catch (e: Exception) { false }
            }?.toList() ?: emptyList()
        } catch (e: Exception) { emptyList() }

        return (internal + assetTop).distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    }

    /**
     * Checks recursively whether a folder or its subfolders contain supported files
     */
    private fun hasFilesRecursive(dir: File): Boolean {
        dir.listFiles()?.forEach { file ->
            if (file.isFile && (file.extension.equals("pdf", true) || file.extension.equals("md", true) || file.extension.equals("markdown", true))) {
                return true
            }
            if (file.isDirectory && hasFilesRecursive(file)) {
                return true
            }
        }
        return false
    }

    /**
     * Creates a new category
     */
    fun createCategory(categoryName: String): Boolean {
        val categoryDir = File(rootDir, categoryName)
        return if (!categoryDir.exists()) {
            categoryDir.mkdirs()
        } else {
            false
        }
    }

    /**
     * Deletes a category and all contained files
     */
    fun deleteCategory(categoryName: String): Boolean {
        val categoryDir = File(rootDir, categoryName)
        return categoryDir.deleteRecursively()
    }

    /**
     * Returns all files in a category
     */
    fun getFilesInCategory(category: String): List<FileInfo> {
        val results = mutableListOf<FileInfo>()

        // Internal files
        val categoryDir = File(rootDir, category)
        if (categoryDir.exists() && categoryDir.isDirectory) {
            categoryDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val ext = file.extension
                    if (ext == "pdf" || ext == "md" || ext == "markdown") {
                        results.add(
                            FileInfo(
                                name = file.name,
                                displayName = file.nameWithoutExtension,
                                path = file.absolutePath,
                                category = category,
                                size = file.length(),
                                lastModified = file.lastModified(),
                                extension = file.extension,
                                isAsset = false
                            )
                        )
                    }
                }
            }
        }

        // Asset files (case-insensitive match)
        findAssetPathForCategory(category)?.let { assetPath ->
            results.addAll(listAssetFiles(assetPath, category))
        }

        return results.sortedBy { it.displayName.lowercase() }
    }

    /**
     * Returns all files grouped by categories
     */
    fun getAllFilesGrouped(): Map<String, List<FileInfo>> {
        return getCategories().associateWith { category ->
            getFilesInCategory(category)
        }.filterValues { it.isNotEmpty() }
    }

    /**
     * Returns all category paths including nested relative paths under rootDir
     */
    fun getAllCategoryPaths(): List<String> {
        val nodes = getFolderTree()
        val result = mutableListOf<String>()
        fun collect(node: FolderNode) {
            result.add(node.relativePath)
            node.children.forEach { collect(it) }
        }
        nodes.forEach { collect(it) }
        return result
    }

    // Find the actual asset path that matches the category path, performing case-insensitive matching
    private fun findAssetPathForCategory(category: String): String? {
        if (category.isBlank()) return null
        val allowedAssetFolders = setOf("checklists", "handbooks", "radiocommunication")
        val parts = category.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty() || !allowedAssetFolders.contains(parts[0].lowercase())) return null
        var currentPath = parts[0]
        var found = true
        for (i in 1 until parts.size) {
            val listParent = try { context.assets.list(currentPath) ?: emptyArray() } catch (e: Exception) { emptyArray() }
            val match = listParent.firstOrNull { it.equals(parts[i], ignoreCase = true) }
            if (match == null) { found = false; break }
            currentPath = "$currentPath/$match"
        }
        return if (found) currentPath else null
    }

    // Recursively lists supported files from assets under assetPath and maps to FileInfo with isAsset=true
    private fun listAssetFiles(assetPath: String, category: String): List<FileInfo> {
        val allowedAssetFolders = setOf("checklists", "handbooks", "radiocommunication")
        val topLevel = assetPath.split('/').firstOrNull()?.lowercase() ?: ""
        if (!allowedAssetFolders.contains(topLevel)) return emptyList()
        val results = mutableListOf<FileInfo>()
        try {
            val list = context.assets.list(assetPath) ?: emptyArray()
            for (entry in list) {
                val childPath = if (assetPath.isEmpty()) entry else "$assetPath/$entry"
                val sub = try { context.assets.list(childPath) ?: emptyArray() } catch (e: Exception) { emptyArray() }
                if (sub.isNotEmpty()) {
                    results.addAll(listAssetFiles(childPath, category))
                } else {
                    val lower = entry.lowercase()
                    if (lower.endsWith(".pdf") || lower.endsWith(".md") || lower.endsWith(".markdown")) {
                        results.add(
                            FileInfo(
                                name = entry,
                                displayName = entry.substringBeforeLast('.'),
                                path = "asset://$childPath",
                                category = category,
                                size = 0L,
                                lastModified = 0L,
                                extension = entry.substringAfterLast('.', ""),
                                isAsset = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return results
    }

    /**
     * Represents a folder / category node with nested subfolders and files
     */
    data class FolderNode(
        val name: String,
        val relativePath: String, // relative to rootDir, e.g., "checklists" or "checklists/F-16_Viper"
        val children: List<FolderNode> = emptyList(),
        val files: List<FileInfo> = emptyList()
    )

    /**
     * Returns the recursive folder tree of the internal root directory
     */
    /**
     * Returns the recursive folder tree of the internal root directory
     * Shows ALL folders from internal storage (including empty ones)
     */
    fun getFolderTree(): List<FolderNode> {
        ensureDirectoryStructure()
        val internalNodes = rootDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { buildFolderNode(it, it.relativeTo(rootDir).path.replace('\\', '/')) }
            ?: emptyList()

        // Build asset folder nodes - only from allowed top-level folders
        val allowedAssetFolders = setOf("checklists", "handbooks", "radiocommunication")
        val assetNodes = try {
            context.assets.list("")?.filter { entry ->
                // Only process allowed top-level folders
                if (!allowedAssetFolders.contains(entry.lowercase())) return@filter false
                // Check if it's a directory (has children)
                try { context.assets.list(entry)?.isNotEmpty() == true } catch (e: Exception) { false }
            }?.map { top -> buildAssetFolderNode(top, top) } ?: emptyList()
        } catch (e: Exception) { emptyList() }

        // Merge internal and asset nodes by relativePath (case-insensitive)
        val merged = mutableMapOf<String, FolderNode>()
        fun keyOf(path: String) = path.replace('\\', '/').lowercase()

        (internalNodes + assetNodes).forEach { node ->
            val k = keyOf(node.relativePath)
            val existing = merged[k]
            merged[k] = if (existing == null) node else mergeFolderNodes(existing, node)
        }
        return merged.values.sortedBy { it.name.lowercase() }
    }

    private fun buildFolderNode(dir: File, relativePath: String): FolderNode {
        val children = dir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { buildFolderNode(it, File(relativePath, it.name).path.replace('\\', '/')) }
            ?: emptyList()

        val files = dir.listFiles()
            ?.filter { it.isFile && (it.extension == "pdf" || it.extension == "md" || it.extension == "markdown") }
            ?.map { file ->
                FileInfo(
                    name = file.name,
                    displayName = file.nameWithoutExtension,
                    path = file.absolutePath,
                    category = relativePath.replace('\\', '/'),
                    size = file.length(),
                    lastModified = file.lastModified(),
                    extension = file.extension
                )
            }
            ?.sortedBy { it.displayName.lowercase() }
            ?: emptyList()

        return FolderNode(
            name = dir.name,
            relativePath = relativePath,
            children = children,
            files = files
        )
    }

    // Build a FolderNode from assets
    private fun buildAssetFolderNode(assetPath: String, relativePath: String): FolderNode {
        // Define allowed top-level asset folders
        val allowedAssetFolders = setOf("checklists", "handbooks", "radiocommunication")
        
        // Check if we should skip this folder (only at top level)
        if (!assetPath.contains("/") && !allowedAssetFolders.contains(assetPath.lowercase())) {
            return FolderNode(name = assetPath, relativePath = relativePath, children = emptyList(), files = emptyList())
        }
        
        val children = try {
            context.assets.list(assetPath)?.filter { entry ->
                try { context.assets.list("$assetPath/$entry")?.isNotEmpty() == true } catch (e: Exception) { false }
            }?.map { child ->
                val childRel = if (relativePath.isEmpty()) child else "$relativePath/$child"
                buildAssetFolderNode("$assetPath/$child", childRel)
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }

        val files = try {
            context.assets.list(assetPath)?.filter { entry ->
                val l = entry.lowercase()
                l.endsWith(".pdf") || l.endsWith(".md") || l.endsWith(".markdown")
            }?.map { fileName ->
                val fullAssetPath = if (assetPath.isEmpty()) fileName else "$assetPath/$fileName"
                FileInfo(
                    name = fileName,
                    displayName = fileName.substringBeforeLast('.'),
                    path = "asset://$fullAssetPath",
                    category = relativePath.replace('\\', '/'),
                    size = 0L,
                    lastModified = 0L,
                    extension = fileName.substringAfterLast('.', ""),
                    isAsset = true
                )
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }

        val nodeName = assetPath.substringAfterLast('/')
        return FolderNode(
            name = nodeName,
            relativePath = relativePath.replace('\\', '/'),
            children = children,
            files = files.sortedBy { it.displayName.lowercase() }
        )
    }

    private fun mergeFolderNodes(a: FolderNode, b: FolderNode): FolderNode {
        // Merge children
        val childrenMap = mutableMapOf<String, FolderNode>()
        (a.children + b.children).forEach { child ->
            val k = child.relativePath.replace('\\', '/').lowercase()
            val existing = childrenMap[k]
            childrenMap[k] = if (existing == null) child else mergeFolderNodes(existing, child)
        }
        // Merge files, prefer internal (a) over asset (b)
        val filesMap = mutableMapOf<String, FileInfo>()
        (a.files + b.files).forEach { f -> if (!filesMap.containsKey(f.name)) filesMap[f.name] = f }
        return FolderNode(name = a.name.ifBlank { b.name }, relativePath = a.relativePath.ifBlank { b.relativePath }, children = childrenMap.values.sortedBy { it.name.lowercase() }, files = filesMap.values.sortedBy { it.displayName.lowercase() })
    }

    /**
     * Imports a file from an external Uri into a category
     */
    fun importFile(uri: Uri, category: String, fileName: String? = null): Result<FileInfo> {
        return try {
            val categoryDir = File(rootDir, category)
            if (!categoryDir.exists()) {
                categoryDir.mkdirs()
            }

            // Extract filename or use provided name
            val originalName = fileName ?: getFileNameFromUri(uri) ?: "imported_${System.currentTimeMillis()}"

            // Check if file extension is PDF or MD
            val extension = originalName.substringAfterLast('.', "").lowercase()
            if (extension !in listOf("pdf", "md", "markdown")) {
                return Result.failure(Exception("Only PDF and Markdown files are supported"))
            }

            // Zieldatei erstellen
            val destFile = File(categoryDir, originalName)

            // If file already exists, generate a new name
            val finalFile = if (destFile.exists()) {
                val baseName = originalName.substringBeforeLast('.')
                val ext = originalName.substringAfterLast('.')
                File(categoryDir, "${baseName}_${System.currentTimeMillis()}.$ext")
            } else {
                destFile
            }

            // Datei kopieren
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(finalFile).use { output ->
                    input.copyTo(output)
                }
            }

            val fileInfo = FileInfo(
                name = finalFile.name,
                displayName = finalFile.nameWithoutExtension,
                path = finalFile.absolutePath,
                category = category,
                size = finalFile.length(),
                lastModified = finalFile.lastModified(),
                extension = finalFile.extension
            )

            Result.success(fileInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a file
     */
    fun deleteFile(filePath: String): Boolean {
        val file = File(filePath)
        return file.exists() && file.delete()
    }

    /**
     * Verschiebt eine Datei in eine andere Kategorie
     */
    fun moveFile(filePath: String, newCategory: String): Result<FileInfo> {
        return try {
            val sourceFile = File(filePath)
            if (!sourceFile.exists()) {
                return Result.failure(Exception("File not found"))
            }

            val categoryDir = File(rootDir, newCategory)
            if (!categoryDir.exists()) {
                categoryDir.mkdirs()
            }

            val destFile = File(categoryDir, sourceFile.name)

            // If destination file exists, error
            if (destFile.exists()) {
                return Result.failure(Exception("File already exists in this category"))
            }

            // Datei verschieben
            if (sourceFile.renameTo(destFile)) {
                // Update tags with new path
                val oldRelativePath = getRelativePath(sourceFile.absolutePath)
                val newRelativePath = getRelativePath(destFile.absolutePath)
                tagManager.updateFilePath(oldRelativePath, newRelativePath)
                
                val fileInfo = FileInfo(
                    name = destFile.name,
                    displayName = destFile.nameWithoutExtension,
                    path = destFile.absolutePath,
                    category = newCategory,
                    size = destFile.length(),
                    lastModified = destFile.lastModified(),
                    extension = destFile.extension
                )
                Result.success(fileInfo)
            } else {
                Result.failure(Exception("File could not be moved"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Benennt eine Datei um
     */
    fun renameFile(filePath: String, newName: String): Result<FileInfo> {
        return try {
            val sourceFile = File(filePath)
            if (!sourceFile.exists()) {
                return Result.failure(Exception("File not found"))
            }

            val extension = sourceFile.extension
            val newFileName = if (newName.endsWith(".$extension")) newName else "$newName.$extension"
            val destFile = File(sourceFile.parent, newFileName)

            if (destFile.exists()) {
                return Result.failure(Exception("A file with this name already exists"))
            }

            if (sourceFile.renameTo(destFile)) {
                // Update tags with new path
                val oldRelativePath = getRelativePath(sourceFile.absolutePath)
                val newRelativePath = getRelativePath(destFile.absolutePath)
                tagManager.updateFilePath(oldRelativePath, newRelativePath)
                
                val category = sourceFile.parentFile?.name ?: "unknown"
                val fileInfo = FileInfo(
                    name = destFile.name,
                    displayName = destFile.nameWithoutExtension,
                    path = destFile.absolutePath,
                    category = category,
                    size = destFile.length(),
                    lastModified = destFile.lastModified(),
                    extension = destFile.extension
                )
                Result.success(fileInfo)
            } else {
                Result.failure(Exception("File could not be renamed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Hilfsfunktion um Dateinamen aus Uri zu extrahieren
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    /**
     * Returns the FileInfo object for a given path
     */
    fun getFile(filePath: String): File? {
        val file = File(filePath)
        return if (file.exists() && file.isFile) file else null
    }

    /**
     * Imports a file bundled in the app assets into a category
     * Example assetPath: "checklists/OFS-FA18C-Checklist-v2.pdf"
     */
    fun importAssetFile(assetPath: String, category: String): Result<FileInfo> {
        return try {
            val categoryDir = File(rootDir, category)
            if (!categoryDir.exists()) {
                categoryDir.mkdirs()
            }

            val fileName = assetPath.substringAfterLast('/')
            val destFile = File(categoryDir, fileName)

            // If file already exists, return an error
            if (destFile.exists()) {
                return Result.failure(Exception("File already exists in this category"))
            }

            context.assets.open(assetPath).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            val fileInfo = FileInfo(
                name = destFile.name,
                displayName = destFile.nameWithoutExtension,
                path = destFile.absolutePath,
                category = category,
                size = destFile.length(),
                lastModified = destFile.lastModified(),
                extension = destFile.extension
            )

            Result.success(fileInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Kopiert rekursiv alle unterstützten Checklists (pdf/md) aus den App-Assets ins interne Root-Verzeichnis.
     * Behält die komplette Ordnerstruktur bei.
     * Returns number of imported files.
     */
    fun importAllBundledAssets(rootAssetPath: String = ""): Int {
        var imported = 0

        // Cleanup potential stale/duplicated internal files that may have been restored
        // by OS backup or left from previous imports. We remove internal files whose
        // base names conflict with bundled asset names to avoid duplicates after rename.
        try {
            val assetNames = collectAllAssetBaseNames(rootAssetPath)
            val removed = cleanupConflictingInternalFiles(assetNames)
            if (removed > 0) {
                // proceed silently; UI refresh will show updated list
            }
        } catch (e: Exception) {
            // ignore cleanup errors
        }
        
        // Define allowed top-level asset folders to import
        val allowedAssetFolders = setOf("checklists", "handbooks", "radiocommunication", "charts")
        
        try {
            fun walker(path: String, relativePath: String, isTopLevel: Boolean = false) {
                val list = try {
                    context.assets.list(path) ?: emptyArray()
                } catch (e: Exception) {
                    emptyArray()
                }
                if (list.isEmpty()) return
                for (child in list) {
                    // Skip unwanted top-level folders
                    if (isTopLevel && !allowedAssetFolders.contains(child.lowercase())) {
                        continue
                    }
                    
                    val childPath = if (path.isEmpty()) child else "$path/$child"
                    // Check if it's a directory by trying to list children
                    val sub = try { context.assets.list(childPath) ?: emptyArray() } catch (e: Exception) { emptyArray() }
                    if (sub.isNotEmpty()) {
                        // It's a directory - recurse
                        val nextRel = if (relativePath.isEmpty()) child.lowercase() else "$relativePath/${child.lowercase()}"
                        walker(childPath, nextRel, false)
                    } else {
                        // It's a file - check if it's a supported type
                        if (child.lowercase().endsWith(".pdf") || child.lowercase().endsWith(".md") || child.lowercase().endsWith(".markdown")) {
                            try {
                                // Create destination directory with full path structure
                                val destDir = if (relativePath.isEmpty()) rootDir else File(rootDir, relativePath)
                                if (!destDir.exists()) {
                                    destDir.mkdirs()
                                }
                                
                                val destFile = File(destDir, child)
                                if (!destFile.exists()) {
                                    // Copy from assets to internal storage
                                    context.assets.open(childPath).use { input ->
                                        java.io.FileOutputStream(destFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    imported++
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
            walker(rootAssetPath, "", true)
        } catch (e: Exception) {
            // ignore, return count
        }
        return imported
    }

    // Collect base filenames (without extension) of all supported asset files under rootAssetPath
    private fun collectAllAssetBaseNames(rootAssetPath: String = ""): List<String> {
        val names = mutableListOf<String>()
        try {
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
                            names.add(child.substringBeforeLast('.'))
                        }
                    }
                }
            }
            walker(rootAssetPath)
        } catch (e: Exception) {
            // ignore
        }
        return names
    }

    // Remove internal files whose base name conflicts with any asset base name.
    // Heuristic: consider a conflict when either name contains the other (case-insensitive).
    private fun cleanupConflictingInternalFiles(assetBaseNames: List<String>): Int {
        if (assetBaseNames.isEmpty()) return 0
        var removed = 0
        rootDir.walkTopDown().forEach { file ->
            try {
                if (!file.isFile) return@forEach
                val ext = file.extension.lowercase()
                if (ext != "pdf" && ext != "md" && ext != "markdown") return@forEach
                val internalBase = file.nameWithoutExtension
                val conflict = assetBaseNames.any { assetBase ->
                    assetBase.equals(internalBase, ignoreCase = true) ||
                    assetBase.contains(internalBase, ignoreCase = true) ||
                    internalBase.contains(assetBase, ignoreCase = true)
                }
                if (conflict) {
                    if (file.delete()) removed++
                }
            } catch (e: Exception) {
                // ignore per-file errors
            }
        }
        return removed
    }

    /**
     * Löscht alle Dateien und Ordner unter dem internen Root (Reset), behält Root-Ordner selbst bei.
     */
    fun wipeInternalRoot() {
        rootDir.listFiles()?.forEach { it.deleteRecursively() }
        ensureDirectoryStructure()
    }
    
    /**
     * Importiert alle Dateien aus dem externen Imports/ Ordner automatisch.
     * Jeder Unterordner wird zu einer Kategorie.
     * Returns Anzahl der importierten Dateien.
     */
    fun importFromExternalImportsFolder(): Int {
        var imported = 0
        try {
            // 1. Prüfe im app-spezifischen Verzeichnis: Android/data/<package>/files/Imports/
            context.getExternalFilesDir(null)?.let { appFiles ->
                val importsFolder = File(appFiles, "Imports")
                if (importsFolder.exists() && importsFolder.isDirectory) {
                    imported += importFromFolder(importsFolder)
                }
            }
            
            // 2. Prüfe im Download-Ordner: /storage/emulated/0/Download/Imports/
            try {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val downloadImports = File(downloadsDir, "Imports")
                if (downloadImports.exists() && downloadImports.isDirectory) {
                    imported += importFromFolder(downloadImports)
                }
            } catch (e: Exception) {
                // Ignore wenn kein Zugriff
            }
            
            // 3. Prüfe im Root des externen Speichers: /storage/emulated/0/Imports/
            try {
                val externalStorage = android.os.Environment.getExternalStorageDirectory()
                val rootImports = File(externalStorage, "Imports")
                if (rootImports.exists() && rootImports.isDirectory) {
                    imported += importFromFolder(rootImports)
                }
            } catch (e: Exception) {
                // Ignore wenn kein Zugriff
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return imported
    }
    
    /**
     * Importiert alle Dateien aus einem Ordner rekursiv.
     * Unterordner werden zu Kategorien.
     */
    private fun importFromFolder(folder: File): Int {
        var imported = 0
        try {
            fun walker(currentFolder: File, relativePath: String) {
                currentFolder.listFiles()?.forEach { child ->
                    if (child.isDirectory) {
                        val nextRel = if (relativePath.isEmpty()) child.name.lowercase() else "$relativePath/${child.name.lowercase()}"
                        walker(child, nextRel)
                    } else if (child.isFile) {
                        val ext = child.extension.lowercase()
                        if (ext in listOf("pdf", "md", "markdown")) {
                            try {
                                // Zielverzeichnis in INTERNAL rootDir anlegen: rootDir/<relativePath>
                                val destDir = if (relativePath.isEmpty()) rootDir else File(rootDir, relativePath)
                                if (!destDir.exists()) {
                                    destDir.mkdirs()
                                }

                                val destFile = File(destDir, child.name)
                                if (!destFile.exists()) {
                                    child.copyTo(destFile, overwrite = false)
                                    imported++
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
            walker(folder, "")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return imported
    }
}
