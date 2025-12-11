package com.example.checklist_interactive.data.files

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Verwaltet das interne Dateisystem der App.
 * Erstellt Ordnerstruktur und importiert externe Dateien.
 */
class InternalFileManager(private val context: Context) {

    companion object {
        // Vordefinierte Kategorien
        val DEFAULT_CATEGORIES = listOf(
            "checklists",
            "comms",
            "charts",
            "procedures",
            "manuals"
        )
    }

    private val rootDir: File = File(context.filesDir, "documents")

    init {
        // Erstelle Root-Verzeichnis und Standardkategorien
        ensureDirectoryStructure()
    }

    /**
     * Stellt sicher, dass die Ordnerstruktur existiert
     */
    private fun ensureDirectoryStructure() {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }

        DEFAULT_CATEGORIES.forEach { category ->
            val categoryDir = File(rootDir, category)
            if (!categoryDir.exists()) {
                categoryDir.mkdirs()
            }
        }
    }

    /**
     * Gibt alle Kategorien zurück (Ordner im Root-Verzeichnis)
     */
    fun getCategories(): List<String> {
        ensureDirectoryStructure()
        return rootDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sorted()
            ?: DEFAULT_CATEGORIES
    }

    /**
     * Erstellt eine neue Kategorie
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
     * Löscht eine Kategorie und alle darin enthaltenen Dateien
     */
    fun deleteCategory(categoryName: String): Boolean {
        if (DEFAULT_CATEGORIES.contains(categoryName)) {
            // Schütze Standard-Kategorien
            return false
        }
        val categoryDir = File(rootDir, categoryName)
        return categoryDir.deleteRecursively()
    }

    /**
     * Gibt alle Dateien in einer Kategorie zurück
     */
    fun getFilesInCategory(category: String): List<FileInfo> {
        val categoryDir = File(rootDir, category)
        if (!categoryDir.exists() || !categoryDir.isDirectory) {
            return emptyList()
        }

        return categoryDir.listFiles()
            ?.filter { it.isFile && (it.extension == "pdf" || it.extension == "md" || it.extension == "markdown") }
            ?.map { file ->
                FileInfo(
                    name = file.name,
                    displayName = file.nameWithoutExtension,
                    path = file.absolutePath,
                    category = category,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    extension = file.extension
                )
            }
            ?.sortedBy { it.displayName.lowercase() }
            ?: emptyList()
    }

    /**
     * Gibt alle Dateien gruppiert nach Kategorien zurück
     */
    fun getAllFilesGrouped(): Map<String, List<FileInfo>> {
        return getCategories().associateWith { category ->
            getFilesInCategory(category)
        }.filterValues { it.isNotEmpty() }
    }

    /**
     * Importiert eine Datei aus einem externen Uri in eine Kategorie
     */
    fun importFile(uri: Uri, category: String, fileName: String? = null): Result<FileInfo> {
        return try {
            val categoryDir = File(rootDir, category)
            if (!categoryDir.exists()) {
                categoryDir.mkdirs()
            }

            // Dateiname extrahieren oder bereitgestellten Namen verwenden
            val originalName = fileName ?: getFileNameFromUri(uri) ?: "imported_${System.currentTimeMillis()}"

            // Prüfen ob Dateiendung PDF oder MD ist
            val extension = originalName.substringAfterLast('.', "").lowercase()
            if (extension !in listOf("pdf", "md", "markdown")) {
                return Result.failure(Exception("Nur PDF und Markdown Dateien werden unterstützt"))
            }

            // Zieldatei erstellen
            val destFile = File(categoryDir, originalName)

            // Wenn Datei bereits existiert, neuen Namen generieren
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
     * Löscht eine Datei
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
                return Result.failure(Exception("Datei nicht gefunden"))
            }

            val categoryDir = File(rootDir, newCategory)
            if (!categoryDir.exists()) {
                categoryDir.mkdirs()
            }

            val destFile = File(categoryDir, sourceFile.name)

            // Wenn Zieldatei existiert, Fehler
            if (destFile.exists()) {
                return Result.failure(Exception("Datei existiert bereits in dieser Kategorie"))
            }

            // Datei verschieben
            if (sourceFile.renameTo(destFile)) {
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
                Result.failure(Exception("Datei konnte nicht verschoben werden"))
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
                return Result.failure(Exception("Datei nicht gefunden"))
            }

            val extension = sourceFile.extension
            val newFileName = if (newName.endsWith(".$extension")) newName else "$newName.$extension"
            val destFile = File(sourceFile.parent, newFileName)

            if (destFile.exists()) {
                return Result.failure(Exception("Eine Datei mit diesem Namen existiert bereits"))
            }

            if (sourceFile.renameTo(destFile)) {
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
                Result.failure(Exception("Datei konnte nicht umbenannt werden"))
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
     * Gibt Datei-Objekt für einen Pfad zurück
     */
    fun getFile(filePath: String): File? {
        val file = File(filePath)
        return if (file.exists() && file.isFile) file else null
    }

    /**
     * Importiert eine Datei, die gebündelt in den App-Assets liegt, in eine Kategorie
     * Beispiel assetPath: "checklists/OFS-FA18C-Checklist-v2.pdf"
     */
    fun importAssetFile(assetPath: String, category: String): Result<FileInfo> {
        return try {
            val categoryDir = File(rootDir, category)
            if (!categoryDir.exists()) {
                categoryDir.mkdirs()
            }

            val fileName = assetPath.substringAfterLast('/')
            val destFile = File(categoryDir, fileName)

            // Wenn Datei bereits existiert, gib einen Fehler zurück
            if (destFile.exists()) {
                return Result.failure(Exception("Datei existiert bereits in dieser Kategorie"))
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
     * Returns number of imported files.
     */
    fun importAllBundledAssets(rootAssetPath: String = "checklists"): Int {
        var imported = 0
        try {
            fun walker(path: String) {
                val list = try {
                    context.assets.list(path) ?: emptyArray()
                } catch (e: Exception) {
                    emptyArray()
                }
                if (list.isEmpty()) return
                for (child in list) {
                    val childPath = if (path.isEmpty()) child else "$path/$child"
                    // Check if it's a directory by trying to list children
                    val sub = try { context.assets.list(childPath) ?: emptyArray() } catch (e: Exception) { emptyArray() }
                    if (sub.isNotEmpty()) {
                        walker(childPath)
                    } else {
                        // only copy pdf/md/markdown
                        if (child.lowercase().endsWith(".pdf") || child.lowercase().endsWith(".md") || child.lowercase().endsWith(".markdown")) {
                            // decide category: use top-level folder (e.g., "checklists") or folder that directly contains the file
                            val segments = childPath.split('/')
                            val category = if (segments.size > 1) segments[segments.size - 2] else rootAssetPath
                            val assetRelative = childPath
                            val res = importAssetFile(assetRelative, category)
                            if (res.isSuccess) imported++
                        }
                    }
                }
            }
            walker(rootAssetPath)
        } catch (e: Exception) {
            // ignore, return count
        }
        return imported
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
            folder.listFiles()?.forEach { categoryFolder ->
                if (categoryFolder.isDirectory) {
                    val categoryName = categoryFolder.name.lowercase()
                    
                    // Erstelle Kategorie falls noch nicht vorhanden
                    createCategory(categoryName)
                    
                    // Importiere alle Dateien in dieser Kategorie
                    categoryFolder.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val extension = file.extension.lowercase()
                            if (extension in listOf("pdf", "md", "markdown")) {
                                // Prüfe ob Datei schon existiert
                                val destFile = File(File(rootDir, categoryName), file.name)
                                if (!destFile.exists()) {
                                    try {
                                        file.copyTo(destFile, overwrite = false)
                                        imported++
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return imported
    }
}

/**
 * Datei-Informationen
 */
data class FileInfo(
    val name: String,
    val displayName: String,
    val path: String,
    val category: String,
    val size: Long,
    val lastModified: Long,
    val extension: String
)
