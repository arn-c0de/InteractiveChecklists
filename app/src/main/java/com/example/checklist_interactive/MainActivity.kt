package com.example.checklist_interactive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
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
import androidx.compose.foundation.layout.Box
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
import com.example.checklist_interactive.ui.quickaccess.FlightMiniStatusBar
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler

class MainActivity : ComponentActivity() {
        companion object {
            const val SOFTWARE_VERSION = "1.0.5"
        }

        val softwareVersion = SOFTWARE_VERSION
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Permission checks and SAF fallback are handled inside the Compose UI via
        // rememberLauncherForActivityResult and the in-composition logic.
        enableEdgeToEdge()

        // Enable immersive full-screen for the activity: hide Android status/navigation bars and allow transient reveal by swipe
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Also set compatibility flags for older APIs and ensure the window stays fullscreen
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContent {
            val prefsManager = remember { PreferencesManager(this@MainActivity) }
            var isDarkTheme by remember { mutableStateOf(prefsManager.isDarkModeEnabled()) }
            val toggleTheme: () -> Unit = {
                isDarkTheme = !isDarkTheme
                prefsManager.setDarkModeEnabled(isDarkTheme)
            }
            // State hoisted for back button handling
            var openFile by remember { mutableStateOf<FileInfo?>(null) }
            var openPage by remember { mutableStateOf(-1) }
            var showFileList by remember { mutableStateOf(false) }
            var showSettings by remember { mutableStateOf(false) }
            val backHandlerEnabled = openFile != null || showSettings

            // Handle Android back button: return to file list instead of closing app
            BackHandler(enabled = backHandlerEnabled) {
                if (showSettings) {
                    showSettings = false
                } else if (openFile != null) {
                    openFile = null
                    openPage = 0
                    showFileList = true
                }
            }
            val quickNoteManager = remember { 
                com.example.checklist_interactive.data.quicknotes.QuickNoteManager(this@MainActivity).also {
                    it.warmUp()
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(
                com.example.checklist_interactive.ui.quickaccess.LocalQuickNoteManager provides quickNoteManager
            ) {
                ChecklistInteractiveTheme(darkTheme = isDarkTheme) {

                Scaffold(modifier = Modifier.fillMaxSize(), topBar = { FlightMiniStatusBar(noteManager = quickNoteManager) }) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
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
                        
                        // 2. Check if app was updated and import new assets automatically
                        val lastImportedVersion = prefsManager.getLastImportedVersion()
                        val currentVersion = SOFTWARE_VERSION
                        
                        if (lastImportedVersion != currentVersion) {
                            // App was updated or first launch - import all bundled assets
                            // This will only copy new files that don't exist yet
                            val imported = fileManager.importAllBundledAssets("")
                            prefsManager.setLastImportedVersion(currentVersion)
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
                                softwareVersion = softwareVersion,
                                onFilesRefreshed = { refreshTrigger++ }
                            )
                        }
                        openFile != null && !showFileList -> {
                            // File viewer
                            InternalFileViewer(
                                fileInfo = openFile!!,
                                initialPage = openPage,
                                onBack = {
                                    openFile = null
                                    openPage = -1
                                    scope.launch {
                                        repository.saveLastOpenedFile("")
                                    }
                                },
                                onShowFileList = {
                                    showFileList = true
                                }
                                ,
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = toggleTheme,
                                onOpenLinkedDocument = { filePath, pageNumber ->
                                    // Finde die Datei im File Manager
                                    // Unterstütze verschiedene Pfadformate: absolute Pfade, relative Pfade, asset:// Pfade
                                    val allFiles = fileManager.getAllFilesGrouped().values.flatten()
                                    val targetFile = allFiles.find { file ->
                                        file.path == filePath ||
                                        file.path.endsWith(filePath) ||
                                        filePath.endsWith(file.path) ||
                                        file.path.replace("asset://", "") == filePath ||
                                        filePath.replace("asset://", "") == file.path.replace("asset://", "")
                                    }
                                    if (targetFile != null) {
                                        openFile = targetFile
                                        openPage = pageNumber ?: -1
                                        showFileList = false
                                        scope.launch {
                                            repository.saveLastOpenedFile(targetFile.path)
                                        }
                                    } else {
                                        // Debug: Datei nicht gefunden
                                        android.util.Log.e("MainActivity", "Linked document not found: $filePath")
                                        allFiles.forEach { file ->
                                            android.util.Log.d("MainActivity", "Available file: ${file.path}")
                                        }
                                    }
                                }
                            )
                        }
                        else -> {
                            // File list (home screen or overlay)
                            InternalFilesScreen(
                                fileManager = fileManager,
                                onFileOpen = { fileInfo ->
                                    openFile = fileInfo
                                    openPage = -1  // -1 = use last saved page
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
                                    // Re-import bundled assets (copy new/changed files) and refresh view
                                    fileManager.importAllBundledAssets("")
                                    refreshTrigger++
                                }
                                ,
                                refreshTrigger = refreshTrigger,
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = toggleTheme,
                                onShowSettings = { showSettings = true },
                                onOpenLinkedDocument = { filePath, pageNumber ->
                                    // Find the file in the file manager and open it
                                    val allFiles = fileManager.getAllFilesGrouped().values.flatten()
                                    val targetFile = allFiles.find { it.path == filePath || it.path.endsWith(filePath) }
                                    if (targetFile != null) {
                                        openFile = targetFile
                                        openPage = pageNumber ?: 0
                                        showFileList = false
                                        scope.launch {
                                            repository.saveLastOpenedFile(targetFile.path)
                                        }
                                    }
                                }
                            )
                        }
                    }
                    }
                }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            try {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } catch (_: Throwable) {
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
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