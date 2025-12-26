package com.example.checklist_interactive

import android.content.Context

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import java.io.File
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
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
import com.example.checklist_interactive.data.tabs.TabManager
import com.example.checklist_interactive.ui.tabs.TabbedDocumentViewer
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import android.content.res.Configuration
import java.util.Locale

class MainActivity : ComponentActivity() {
        companion object {
            const val SOFTWARE_VERSION = "1.0.17"
        }

        val softwareVersion = SOFTWARE_VERSION

    // Singletons used by Compose and lifecycle methods so we can persist state reliably
    private val globalPrefsManager by lazy { com.example.checklist_interactive.data.prefs.PreferencesManager(this) }
    private val globalTabManager by lazy { com.example.checklist_interactive.data.tabs.TabManager(this) }
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(updateLocale(newBase))
    }
    
    private fun updateLocale(context: Context): Context {
        val prefsManager = PreferencesManager(context)
        val languageCode = prefsManager.getAppLanguage()
        val locale = when {
            languageCode.contains("-") -> Locale.forLanguageTag(languageCode) // 处理 "zh-CN"
            languageCode.contains("_") -> {
                val parts = languageCode.split("_")
                if (parts.size > 1) Locale(parts[0], parts[1]) else Locale(languageCode) // 处理 "zh_CN"
            }
            else -> Locale(languageCode) 
        }
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return context.createConfigurationContext(config)
    }
    
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply fullscreen immediately BEFORE any UI setup
        applyFullscreenSettings()

        // Permission checks and SAF fallback are handled inside the Compose UI via
        // rememberLauncherForActivityResult and the in-composition logic.
        enableEdgeToEdge()

        // Pre-restore tab state synchronously before Compose to avoid pager initial-page flicker
        val preFileManager = InternalFileManager(this)
        val allFilesPre = preFileManager.getAllFilesGrouped().values.flatten()
        globalTabManager.restoreTabsFromPaths { path ->
            allFilesPre.find { it.path == path }
        }
        globalTabManager.loadHistoryFromPreferences()

        // If no tabs restored, try to open last opened file (read synchronously)
        val sp = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lastFilePathSync = sp.getString("last_opened_file", null)
        if (globalTabManager.openTabs.value.isEmpty() && !lastFilePathSync.isNullOrEmpty()) {
            val lastFile = allFilesPre.find { it.path == lastFilePathSync }
            if (lastFile != null) {
                val idx = globalTabManager.openTab(lastFile)
                globalTabManager.switchToTab(idx)
            }
        }

        setContent {
            // Maintain fullscreen during composition
            androidx.compose.runtime.SideEffect {
                applyFullscreenSettings()
            }
            val prefsManager = remember { globalPrefsManager }
            var isDarkTheme by remember { mutableStateOf(prefsManager.isDarkModeEnabled()) }
            val toggleTheme: () -> Unit = {
                isDarkTheme = !isDarkTheme
                prefsManager.setDarkModeEnabled(isDarkTheme)
            }
            // State hoisted for back button handling and tab management
            val tabManager = remember { globalTabManager }
            val openTabs by tabManager.openTabs.collectAsState()
            val activeTabIndex by tabManager.activeTabIndex.collectAsState()
            // Restore last main page preference (0=file list, 1=tabs/viewer)
            var showFileList by remember { mutableStateOf(prefsManager.getLastMainPage() == 0) }
            var showSettings by remember { mutableStateOf(false) }
                    var showTacticalUnits by remember { mutableStateOf(false) }
                    var isScreenLocked by remember { mutableStateOf(prefsManager.isScreenLocked()) }
                    val backHandlerEnabled = openTabs.isNotEmpty() || showSettings || showTacticalUnits

            // Handle Android back button: return to file list instead of closing app
            BackHandler(enabled = backHandlerEnabled) {
                if (showTacticalUnits) {
                    showTacticalUnits = false
                } else if (showSettings) {
                    showSettings = false
                } else if (openTabs.isNotEmpty()) {
                    // Close current tab or go to file list if last tab
                    if (openTabs.size == 1) {
                        tabManager.closeAllTabs()
                        showFileList = true
                    } else {
                        tabManager.closeTab(activeTabIndex)
                    }
                }
            }
            val quickNoteManager = remember { 
                com.example.checklist_interactive.data.quicknotes.QuickNoteManager(this@MainActivity).also {
                    it.warmUp()
                }
            }
            val dataPadManager = remember {
                com.example.checklist_interactive.data.datapad.DataPadManager(this@MainActivity).also {
                    it.start()
                }
            }
            
            // Database update manager - checks for new DB version on startup
            val databaseUpdateManager = remember {
                com.example.checklist_interactive.data.tactical.DatabaseUpdateManager(this@MainActivity).also {
                    it.checkForDatabaseUpdate()
                }
            }
            
            // Clean up DataPadManager on disposal
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose {
                    dataPadManager.cleanup()
                }
            }
            
            androidx.compose.runtime.CompositionLocalProvider(
                com.example.checklist_interactive.ui.quickaccess.LocalQuickNoteManager provides quickNoteManager,
                com.example.checklist_interactive.ui.datapad.LocalDataPadManager provides dataPadManager
            ) {
                ChecklistInteractiveTheme(darkTheme = isDarkTheme) {

                Scaffold(modifier = Modifier.fillMaxSize(), topBar = { FlightMiniStatusBar(noteManager = quickNoteManager) }) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                    var refreshTrigger by remember { mutableStateOf(0) }

                    // Database update dialog (shows when new DB version detected)
                    if (databaseUpdateManager.showUpdateDialog.value) {
                        com.example.checklist_interactive.ui.tactical.DatabaseUpdateDialog(
                            assetVersion = databaseUpdateManager.assetDbVersion.value,
                            currentVersion = databaseUpdateManager.currentDbVersion.value,
                            onMerge = {
                                databaseUpdateManager.importDatabaseMerge {
                                    // Refresh UI after merge
                                    refreshTrigger++
                                }
                            },
                            onClean = {
                                databaseUpdateManager.importDatabaseClean {
                                    // Refresh UI after clean import
                                    refreshTrigger++
                                }
                            },
                            onDismiss = {
                                databaseUpdateManager.dismissDialog()
                            }
                        )
                    }
                    // reuse pre-created instances so state is consistent and restoration happened already
                    val fileManager = remember { preFileManager }
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

                    // Auto-open last file on startup and restore tabs
                    LaunchedEffect(Unit) {
                        // 1. Import files from external Imports/ folder
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
                        
                        // Always trigger refresh after startup to show latest data
                        refreshTrigger++
                        
                        // 3. Tab restoration is handled before composition to avoid UI flicker.
                        
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
                        val rootFile = File(rootPath)
                        val observer = object : FileObserver(rootFile, FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM or FileObserver.MODIFY) {
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
                                    Text(stringResource(R.string.dialog_pick_files))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    // Launch folder picker
                                    pickDocumentTreeLauncher.launch(null)
                                    showImportDialog = false
                                }) {
                                    Text(stringResource(R.string.dialog_pick_folder))
                                }
                            },
                            title = { Text(stringResource(R.string.dialog_import_files)) },
                            text = { Text(stringResource(R.string.dialog_import_files_message)) }
                        )
                    }

                    // Horizontal pager for swipe navigation between file list and tabs
                    val mainPagerState = rememberPagerState(
                        initialPage = if (showFileList) 0 else 1,
                        pageCount = { 2 }
                    )
                    
                    // Sync pager state with showFileList
                    LaunchedEffect(mainPagerState.currentPage) {
                        showFileList = (mainPagerState.currentPage == 0)
                    }
                    
                    LaunchedEffect(showFileList) {
                        if (showFileList && mainPagerState.currentPage != 0) {
                            mainPagerState.animateScrollToPage(0)
                        } else if (!showFileList && mainPagerState.currentPage != 1) {
                            mainPagerState.animateScrollToPage(1)
                        }
                        // Persist user's last visible main page: 0=file list, 1=tabs/viewer
                        prefsManager.setLastMainPage(if (showFileList) 0 else 1)
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
                        else -> {
                            // Main pager with file list and tab viewer
                            HorizontalPager(
                                state = mainPagerState,
                                modifier = Modifier.fillMaxSize(),
                                userScrollEnabled = !(isScreenLocked && openTabs.getOrNull(activeTabIndex)?.content is TabManager.TabContent.MapTab && mainPagerState.currentPage == 1)
                            ) { page ->
                                when (page) {
                                    0 -> {
                                        // File list (My Files)
                                        InternalFilesScreen(
                                            fileManager = fileManager,
                                            onFileOpen = { fileInfo ->
                                                val idx = tabManager.openTab(fileInfo)
                                                tabManager.switchToTab(idx)
                                                showFileList = false
                                                // Ensure focus after pager transition (workaround for compose race)
                                                scope.launch {
                                                    kotlinx.coroutines.delay(100L)
                                                    tabManager.switchToTab(idx)
                                                    repository.saveLastOpenedFile(fileInfo.path)
                                                }
                                            },
                                            onShortcutOpen = { shortcut: PageShortcut ->
                                                val allFiles = fileManager.getAllFilesGrouped().values.flatten()
                                                val targetFile = allFiles.find { it.path == shortcut.filePath }
                                                if (targetFile != null) {
                                                    val idx = tabManager.openTab(targetFile, shortcut.pageNumber)
                                                    tabManager.switchToTab(idx)
                                                    showFileList = false
                                                    scope.launch {
                                                        kotlinx.coroutines.delay(100L)
                                                        tabManager.switchToTab(idx)
                                                        repository.saveLastOpenedFile(targetFile.path)
                                                    }
                                                }
                                            },
                                            onRefresh = {
                                                fileManager.importAllBundledAssets("")
                                                refreshTrigger++
                                            },
                                            refreshTrigger = refreshTrigger,
                                            isDarkTheme = isDarkTheme,
                                            onToggleTheme = toggleTheme,
                                            onShowSettings = { showSettings = true },
                                            onOpenLinkedDocument = { filePath, pageNumber ->
                                                val allFiles = fileManager.getAllFilesGrouped().values.flatten()
                                                val targetFile = allFiles.find { it.path == filePath || it.path.endsWith(filePath) }
                                                if (targetFile != null) {
                                                    val idx = tabManager.openTab(targetFile, pageNumber ?: 0)
                                                    tabManager.switchToTab(idx)
                                                    showFileList = false
                                                    scope.launch {
                                                        kotlinx.coroutines.delay(100L)
                                                        tabManager.switchToTab(idx)
                                                        repository.saveLastOpenedFile(targetFile.path)
                                                    }
                                                }
                                            },
                                            onOpenMap = {
                                                // Open aviation map tab
                                                val idx = tabManager.openMapTab()
                                                tabManager.switchToTab(idx)
                                                showFileList = false
                                            }
                                        )
                                    }
                                    1 -> {
                                        // Tabbed document viewer
                                        TabbedDocumentViewer(
                                tabs = openTabs,
                                activeTabIndex = activeTabIndex,
                                isScreenLocked = isScreenLocked,
                                onTabChanged = { index ->
                                    tabManager.switchToTab(index)
                                    val tab = openTabs.getOrNull(index)
                                    if (tab != null) {
                                        scope.launch {
                                            repository.saveLastOpenedFile(tab.fileInfo.path)
                                        }
                                    }
                                },
                                onTabClosed = { index ->
                                    val removed = tabManager.closeTab(index)
                                    if (removed != null) {
                                        scope.launch {
                                            val currentActive = tabManager.getActiveTab()?.fileInfo?.path ?: ""
                                            repository.saveLastOpenedFile(currentActive)
                                        }
                                    }
                                },
                                onNewTab = {
                                    // Show file list to select new file
                                    showFileList = true
                                },
                                onTabsReordered = { from, to ->
                                    tabManager.moveTab(from, to)
                                },
                                onInternalFileViewerOpen = { showFileList = true },
                                onSettings = { showSettings = true }
                            ) { tabInfo ->
                                // Render appropriate viewer based on tab content type
                                when (tabInfo.content) {                                    is TabManager.TabContent.MapTab -> {
                                        // Render aviation map viewer
                                        com.example.checklist_interactive.ui.maps.MapViewer(
                                            isScreenLocked = isScreenLocked,
                                            onLockScreen = {
                                                isScreenLocked = !isScreenLocked
                                                prefsManager.setScreenLocked(isScreenLocked)
                                            },
                                            onTacticalUnitsOpen = {
                                                showTacticalUnits = true
                                            }
                                        )
                                    }
                                    is TabManager.TabContent.DocumentTab -> {
                                        // Render the document viewer for each tab
                                        InternalFileViewer(
                                            fileInfo = tabInfo.fileInfo,
                                            initialPage = tabInfo.pageNumber,
                                            onBack = {
                                                // Close current tab or show file list if last tab
                                                if (openTabs.size == 1) {
                                                    tabManager.closeAllTabs()
                                                    showFileList = true
                                                    scope.launch {
                                                        // Clear last opened file when all tabs closed
                                                        repository.saveLastOpenedFile("")
                                                    }
                                                } else {
                                                    val removed = tabManager.closeTab(activeTabIndex)
                                                    if (removed != null) {
                                                        scope.launch {
                                                            val currentActive = tabManager.getActiveTab()?.fileInfo?.path ?: ""
                                                            repository.saveLastOpenedFile(currentActive)
                                                        }
                                                    }
                                                }
                                            },
                                            onShowFileList = {
                                                showFileList = true
                                            },
                                            isDarkTheme = isDarkTheme,
                                            onToggleTheme = toggleTheme,
                                            onOpenLinkedDocument = { filePath, pageNumber ->
                                                // Open linked document in new tab
                                                val allFiles = fileManager.getAllFilesGrouped().values.flatten()
                                                val targetFile = allFiles.find { file ->
                                                    file.path == filePath ||
                                                    file.path.endsWith(filePath) ||
                                                    filePath.endsWith(file.path) ||
                                                    file.path.replace("asset://", "") == filePath ||
                                                    filePath.replace("asset://", "") == file.path.replace("asset://", "")
                                                }
                                                if (targetFile != null) {
                                                    val idx = tabManager.openTab(targetFile, pageNumber ?: -1)
                                                    tabManager.switchToTab(idx)
                                                    showFileList = false
                                                    scope.launch {
                                                        kotlinx.coroutines.delay(100L)
                                                        tabManager.switchToTab(idx)
                                                        repository.saveLastOpenedFile(targetFile.path)
                                                    }
                                                } else {
                                                    android.util.Log.e("MainActivity", "Linked document not found: $filePath")
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

                    // Tactical Units Popup Overlay (shown over map when active)
                    if (showTacticalUnits) {
                        com.example.checklist_interactive.ui.tactical.TacticalUnitsListScreen(
                            onNavigateBack = { showTacticalUnits = false },
                            onUnitClick = { unit ->
                                // Optional: Center map on unit location
                                // For now, just close the list
                                showTacticalUnits = false
                            }
                        )
                    }
                    }
                }
                }


            }
        }
    }

    private fun applyFullscreenSettings() {
        // Enable immersive fullscreen mode using modern WindowInsetsController API
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyFullscreenSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        applyFullscreenSettings()
    }

    override fun onPause() {
        super.onPause()

        // Persist tab state and history synchronously so a restart will restore the same UI.
        try {
            globalTabManager.persistAll()
        } catch (e: Exception) {
            // ignore
        }

        // Persist last opened file immediately (blocking commit) so it is available at next startup
        val currentActive = globalTabManager.getActiveTab()?.fileInfo?.path ?: ""
        try {
            val sp = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            sp.edit().putString("last_opened_file", currentActive).commit()
        } catch (e: Exception) {
            // fallback: ignore
        }

        // Persist last main page: if we have open tabs we assume the user was in tab view
        try {
            globalPrefsManager.setLastMainPage(if (globalTabManager.openTabs.value.isNotEmpty()) 1 else 0)
        } catch (e: Exception) {
            // ignore
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
        text = stringResource(R.string.greeting_hello, name),
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ChecklistInteractiveTheme {
        Greeting(stringResource(R.string.greeting_default_name))
    }
}