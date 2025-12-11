package com.example.checklist_interactive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.documentfile.provider.DocumentFile
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.DisposableEffect
import android.os.FileObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.checklist_interactive.ui.theme.ChecklistInteractiveTheme
import com.example.checklist_interactive.data.prefs.PreferencesManager
import com.example.checklist_interactive.data.files.InternalFileManager
import com.example.checklist_interactive.data.files.FileInfo
import com.example.checklist_interactive.ui.files.InternalFilesScreen
import com.example.checklist_interactive.ui.files.InternalFileViewer
import com.example.checklist_interactive.data.checklist.ChecklistRepository
import com.example.checklist_interactive.data.shortcuts.PageShortcut
import com.example.checklist_interactive.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Permission checks and SAF fallback are handled inside the Compose UI via
        // rememberLauncherForActivityResult and the in-composition logic.
        enableEdgeToEdge()
        setContent {
            val prefsManager = remember { PreferencesManager(this@MainActivity) }
            var isDarkTheme by remember { mutableStateOf(prefsManager.isDarkModeEnabled()) }
            val toggleTheme: () -> Unit = {
                isDarkTheme = !isDarkTheme
                prefsManager.setDarkModeEnabled(isDarkTheme)
            }
            ChecklistInteractiveTheme(darkTheme = isDarkTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var openFile by remember { mutableStateOf<FileInfo?>(null) }
                    var openPage by remember { mutableStateOf(0) }
                    var showFileList by remember { mutableStateOf(false) }
                    var showSettings by remember { mutableStateOf(false) }
                    var refreshTrigger by remember { mutableStateOf(0) }

                    val fileManager = remember { InternalFileManager(this@MainActivity) }
                    val repository = remember { ChecklistRepository(this@MainActivity) }
                    val scope = rememberCoroutineScope()
                    val context = this@MainActivity

                    // Compose-aware permission & SAF pickers
                    var showImportDialog by remember { mutableStateOf(false) }

                    // Open multiple documents (user selects multiple files to import)
                    val pickMultipleDocumentsLauncher = rememberLauncherForActivityResult(
                        OpenMultipleDocuments()
                    ) { uris ->
                        uris?.forEach { uri ->
                            fileManager.importFile(uri, "imports")
                        }
                        // trigger refresh
                        refreshTrigger++
                    }

                    // Open a document tree (folder) and import all supported files
                    val pickDocumentTreeLauncher = rememberLauncherForActivityResult(
                        OpenDocumentTree()
                    ) { treeUri ->
                        treeUri?.let { uri ->
                            // Save the folder URI in preferences
                            prefsManager.setImportFolderUri(uri.toString())
                            
                            // Persist URI permission temporarily for SAF access
                            try {
                                context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (e: Exception) {
                                // ignore
                            }
                            val docFile = DocumentFile.fromTreeUri(context, uri)
                            docFile?.let { traverseAndImportDocumentFiles(it, fileManager) }
                            refreshTrigger++
                        }
                    }

                    // Request multiple permissions: media & external storage fallbacks
                    val requestMultiplePermissionsLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { results ->
                        // If any permission granted, try to import again
                        val anyGranted = results.values.any { it }
                        if (anyGranted) {
                            // perform import again
                            val importedAgain = fileManager.importFromExternalImportsFolder()
                            if (importedAgain == 0) {
                                // Offer fallback to SAF
                                if (!prefsManager.hasShownImportDialog()) {
                                    showImportDialog = true
                                    prefsManager.setImportDialogShown(true)
                                }
                                prefsManager.setFirstLaunchComplete()
                            }
                        } else {
                            // No permission granted: show fallback dialog
                            if (!prefsManager.hasShownImportDialog()) {
                                showImportDialog = true
                                prefsManager.setImportDialogShown(true)
                            }
                            prefsManager.setFirstLaunchComplete()
                        }
                    }

                    // Auto-open last file on startup
                    LaunchedEffect(Unit) {
                        // 1. Importiere Dateien aus externem Imports/ Ordner
                        // Check permissions and request if needed
                        val permissionsToRequest = mutableListOf<String>()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
                            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
                            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
                            // READ_MEDIA_DOCUMENTS may not exist on older SDKs; use string
                            permissionsToRequest.add("android.permission.READ_MEDIA_DOCUMENTS")
                        } else {
                            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }

                        val missing = permissionsToRequest.filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }

                        if (missing.isNotEmpty()) {
                            requestMultiplePermissionsLauncher.launch(missing.toTypedArray())
                        }
                        val importedFromExternal = fileManager.importFromExternalImportsFolder()
                        
                        // 2. Wenn keine Dateien vorhanden, importiere gebündelte Assets
                        val allCategories = fileManager.getCategories()
                        var hasAnyFiles = false
                        allCategories.forEach { category ->
                            if (fileManager.getFilesInCategory(category).isNotEmpty()) {
                                hasAnyFiles = true
                            }
                        }
                        
                        if (!hasAnyFiles) {
                            // Copy all bundled assets from assets/ into internal storage
                            // This includes Checklists/ and radiocommunication/ folders with full structure
                            val imported = fileManager.importAllBundledAssets("")
                        }
                        
                        // 3. Versuche letzte geöffnete Datei zu laden
                        val lastFilePath = repository.getLastOpenedFile()
                        if (!lastFilePath.isNullOrEmpty()) {
                            // Try to find file in internal storage
                            val allFiles = fileManager.getAllFilesGrouped().values.flatten()
                            val lastFile = allFiles.find { it.path == lastFilePath }
                            if (lastFile != null) {
                                openFile = lastFile
                            }
                        }
                        
                        // Always trigger refresh after startup to show latest data
                        refreshTrigger++
                        
                        // Show import dialog only on first launch if no files
                        if (!prefsManager.hasShownImportDialog() && prefsManager.isFirstLaunch() && importedFromExternal == 0 && fileManager.getAllFilesGrouped().values.flatten().isEmpty()) {
                            showImportDialog = true
                            prefsManager.setImportDialogShown(true)
                            prefsManager.setFirstLaunchComplete()
                        }
                    }

                    // Re-import when the app resumes: watch for changes in external Imports folder
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                val importedAgain = fileManager.importFromExternalImportsFolder()
                                if (importedAgain > 0) {
                                    refreshTrigger++
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    // Observe internal storage rootDir for changes and refresh UI automatically
                    DisposableEffect(Unit) {
                        val rootPath = fileManager.getInternalRootPath()
                        val observer = object : FileObserver(rootPath, FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM or FileObserver.MODIFY) {
                            override fun onEvent(event: Int, path: String?) {
                                // Only react to events that are files with supported extensions
                                if (path == null) return
                                val p = path.lowercase()
                                if (p.endsWith(".pdf") || p.endsWith(".md") || p.endsWith(".markdown")) {
                                    refreshTrigger++
                                }
                            }
                        }
                        observer.startWatching()
                        onDispose { observer.stopWatching() }
                    }

                    // Offer the SAF import dialog when needed
                    if (showImportDialog) {
                        AlertDialog(
                            onDismissRequest = { showImportDialog = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    // Launch multiple-document picker for pdf/md files
                                    pickMultipleDocumentsLauncher.launch(arrayOf("application/pdf", "text/markdown", "text/plain"))
                                    showImportDialog = false
                                }) {
                                    Text("Pick files")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    // Launch folder picker
                                    pickDocumentTreeLauncher.launch(null)
                                    showImportDialog = false
                                }) {
                                    Text("Pick folder")
                                }
                            },
                            title = { Text("Import files from device") },
                            text = { Text("No files were imported automatically. Would you like to pick files or a folder to import?\n(Recommended for PDFs and markdown files) ") }
                        )
                    }

                    when {
                        showSettings -> {
                            // Settings screen
                            SettingsScreen(
                                prefsManager = prefsManager,
                                fileManager = fileManager,
                                onBack = { showSettings = false },
                                onRequestFolderPicker = {
                                    pickDocumentTreeLauncher.launch(null)
                                }
                            )
                        }
                        openFile != null && !showFileList -> {
                            // File viewer
                            InternalFileViewer(
                                fileInfo = openFile!!,
                                initialPage = openPage,
                                onBack = {
                                    openFile = null
                                    openPage = 0
                                    scope.launch {
                                        repository.saveLastOpenedFile("")
                                    }
                                },
                                onShowFileList = {
                                    showFileList = true
                                }
                                ,
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = toggleTheme
                            )
                        }
                        else -> {
                            // File list (home screen or overlay)
                            InternalFilesScreen(
                                fileManager = fileManager,
                                onFileOpen = { fileInfo ->
                                    openFile = fileInfo
                                    openPage = 0
                                    showFileList = false
                                    scope.launch {
                                        repository.saveLastOpenedFile(fileInfo.path)
                                    }
                                },
                                onShortcutOpen = { shortcut: PageShortcut ->
                                    // Finde die Datei im File Manager
                                    val allFiles = fileManager.getAllFilesGrouped().values.flatten()
                                    val targetFile = allFiles.find { it.path == shortcut.filePath }
                                    if (targetFile != null) {
                                        openFile = targetFile
                                        openPage = shortcut.pageNumber
                                        showFileList = false
                                        scope.launch {
                                            repository.saveLastOpenedFile(targetFile.path)
                                        }
                                    }
                                },
                                onRefresh = {
                                    refreshTrigger++
                                }
                                ,
                                refreshTrigger = refreshTrigger,
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = toggleTheme,
                                onShowSettings = { showSettings = true }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun traverseAndImportDocumentFiles(docFile: DocumentFile, fileManager: InternalFileManager) {
    docFile.listFiles().forEach { child ->
        if (child.isDirectory) {
            traverseAndImportDocumentFiles(child, fileManager)
        } else {
            val name = child.name ?: return@forEach
            if (name.lowercase().endsWith(".pdf") || name.lowercase().endsWith(".md") || name.lowercase().endsWith(".markdown")) {
                try {
                    fileManager.importFile(child.uri, "imports")
                } catch (e: Exception) {
                    // ignore individual import errors
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ChecklistInteractiveTheme {
        Greeting("Android")
    }
}