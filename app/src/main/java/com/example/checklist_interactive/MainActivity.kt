package com.example.checklist_interactive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Berechtigung wurde erteilt oder abgelehnt
        // App funktioniert auch ohne, aber kann dann nicht aus Imports/ laden
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Frage nach Berechtigung für externen Speicherzugriff
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // READ_MEDIA_DOCUMENTS is not available as a defined constant on all SDKs.
            // Use the permission name string to avoid unresolved reference during compilation
            // on SDKs where the constant might not yet be defined.
            val readMediaDocumentsPerm = "android.permission.READ_MEDIA_DOCUMENTS"
            if (ContextCompat.checkSelfPermission(
                    this,
                    readMediaDocumentsPerm
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(readMediaDocumentsPerm)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
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
                    var refreshTrigger by remember { mutableStateOf(0) }

                    val fileManager = remember { InternalFileManager(this@MainActivity) }
                    val repository = remember { ChecklistRepository(this@MainActivity) }
                    val scope = rememberCoroutineScope()

                    // Auto-open last file on startup
                    LaunchedEffect(Unit) {
                        // 1. Importiere Dateien aus externem Imports/ Ordner
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
                            // Copy all bundled assets under assets/checklists into internal storage
                            val imported = fileManager.importAllBundledAssets("checklists")
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
                    }

                    when {
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
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = toggleTheme
                            )
                        }
                    }
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