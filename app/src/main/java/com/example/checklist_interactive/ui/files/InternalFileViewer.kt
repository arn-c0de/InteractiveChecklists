package com.example.checklist_interactive.ui.files

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.offset
import com.example.checklist_interactive.data.files.FileInfo
import com.example.checklist_interactive.ui.checklist.MarkdownViewer
import com.example.checklist_interactive.ui.checklist.PdfViewer
import com.example.checklist_interactive.ui.quickaccess.QuickAccessSheet
import com.example.checklist_interactive.data.quicknotes.QuickNoteManager

import androidx.compose.material.icons.filled.Refresh
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.checklist_interactive.data.checklist.ChecklistRepository
import com.example.checklist_interactive.data.prefs.PreferencesManager
import com.example.checklist_interactive.data.checklist.MarkdownChecklistParser
import com.example.checklist_interactive.ui.checklist.ChecklistViewModel
import com.example.checklist_interactive.ui.checklist.ChecklistViewModelFactory
import com.example.checklist_interactive.ui.common.DraggableFab
import java.io.File
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.checklist_interactive.R

/**
 * Viewer for internal files (MD and PDF)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalFileViewer(
    fileInfo: FileInfo,
    initialPage: Int = -1,
    onBack: () -> Unit,
    onShowFileList: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenLinkedDocument: ((filePath: String, pageNumber: Int?) -> Unit)? = null
) {
    val context = LocalContext.current

    when (fileInfo.extension.lowercase()) {
        "pdf" -> {
            val prefsManager = remember { PreferencesManager(context) }
            val lastPage = remember(fileInfo.path) { prefsManager.getInt("pdf_last_page_${fileInfo.path}", com.example.checklist_interactive.data.shortcuts.LastPageManager(context).getLastPage(fileInfo.path)) }
            var currentPage by remember(fileInfo.path) { mutableStateOf(lastPage) }

            // Update currentPage when initialPage changes (e.g., by link click)
            // initialPage = -1 means "not set, use lastPage"
            androidx.compose.runtime.LaunchedEffect(initialPage) {
                if (initialPage >= 0) {
                    currentPage = initialPage
                }
            }

            val isAsset = fileInfo.isAsset || fileInfo.path.startsWith("asset://")
            val assetPath = if (isAsset) fileInfo.path.removePrefix("asset://") else fileInfo.path

            val pdfPath by androidx.compose.runtime.produceState<String?>(initialValue = null, key1 = fileInfo.path) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val start = System.currentTimeMillis()
                    val result = if (isAsset) {
                        val safeName = assetPath.replace('/', '_')
                        val tmp = File(context.cacheDir, "asset_$safeName")
                        try {
                            if (!tmp.exists()) {
                                context.assets.open(assetPath).use { input ->
                                    tmp.outputStream().use { output -> input.copyTo(output) }
                                }
                            }
                            tmp.absolutePath
                        } catch (e: Exception) {
                            // fallback to path (might fail)
                            null
                        }
                    } else {
                        fileInfo.path
                    }
                    val elapsed = System.currentTimeMillis() - start
                    android.util.Log.i("InternalFileViewer", "pdf asset load for ${fileInfo.path} took ${elapsed}ms")
                    result
                }
            }

            val currentPdfPath = pdfPath
            if (currentPdfPath == null) {
                // Loading indicator while asset copy happens
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
            PdfViewer(
                pdfPath = currentPdfPath,
                title = fileInfo.displayName,
                onBack = {
                    prefsManager.setInt("pdf_last_page_${fileInfo.path}", currentPage)
                    // Also save into LastPageManager so other viewers using that storage see the same last page
                    com.example.checklist_interactive.data.shortcuts.LastPageManager(context).saveLastPage(fileInfo.path, currentPage)
                    onBack()
                },
                onShowFileList = onShowFileList,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                isInternalFile = true,
                initialPage = currentPage,
                onPageChange = { page -> currentPage = page },
                onOpenLinkedDocument = onOpenLinkedDocument,
                documentId = fileInfo.path
            )
            }
        }
            "md", "markdown" -> {
            val prefsManager = remember { PreferencesManager(context) }
            val checklistRepository = remember { ChecklistRepository(context) }
            val providedNoteManager = com.example.checklist_interactive.ui.quickaccess.LocalQuickNoteManager.current
            val quickNoteManager = providedNoteManager ?: remember { QuickNoteManager(context) }
            val isAsset = fileInfo.isAsset || fileInfo.path.startsWith("asset://")
            val assetPath = if (isAsset) fileInfo.path.removePrefix("asset://") else fileInfo.path
            val markdownContent by androidx.compose.runtime.produceState(initialValue = "", key1 = fileInfo.path) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val start = System.currentTimeMillis()
                    try {
                        if (isAsset) context.assets.open(assetPath).bufferedReader().use { it.readText() }
                        else java.io.File(fileInfo.path).readText()
                    } catch (e: Exception) {
                        ""
                    } finally {
                        val elapsed = System.currentTimeMillis() - start
                        android.util.Log.i("InternalFileViewer", "markdown load for ${fileInfo.path} took ${elapsed}ms")
                    }
                }
            }
            val parsedChecklist = remember(markdownContent) {
                MarkdownChecklistParser().parse(fileInfo.path, markdownContent)
            }

            val viewModel: ChecklistViewModel = viewModel(
                key = fileInfo.path,
                factory = ChecklistViewModelFactory(checklistRepository, parsedChecklist)
            )

            val checklistState by viewModel.checklistState.collectAsState()

            val displayAssetAsInternal = !isAsset

            // State for Expand/Collapse All
            var expandAllSections by remember { mutableStateOf(prefsManager.areMarkdownSectionsExpandedByDefault()) }
            var showQuickAccess by remember { mutableStateOf(false) }
            var resetTrigger by remember { mutableStateOf(0) }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { 
                            Text(
                                fileInfo.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            ) 
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack, 
                                    contentDescription = LocalContext.current.getString(R.string.back),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                viewModel.resetChecklist()
                                resetTrigger += 1
                            }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset Checklist", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = {
                                expandAllSections = !expandAllSections
                                prefsManager.setMarkdownSectionsExpandedByDefault(expandAllSections)
                            }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    imageVector = if (expandAllSections) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                    contentDescription = if (expandAllSections) "Collapse all" else "Expand all",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = onToggleTheme, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = LocalContext.current.getString(R.string.toggle_dark_mode),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        modifier = Modifier.height(48.dp)
                    )
                },
                floatingActionButton = { /* moved to overlay */ }
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    MarkdownViewer(
                        assetPath = assetPath,
                        checklist = checklistState,
                        onCheckboxChange = viewModel::onCheckboxChange,
                        modifier = Modifier.fillMaxSize(),
                        isInternalFile = !isAsset,
                        prefsManager = prefsManager,
                        forceExpandAll = expandAllSections,
                        markdownContentOverride = markdownContent,
                        resetTrigger = resetTrigger
                    )

                    // Draggable FABs
                    val configuration = LocalConfiguration.current
                    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.roundToPx() }
                    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.roundToPx() }
                    val fabSizePx = with(LocalDensity.current) { 56.dp.roundToPx() }

                    DraggableFab(
                        name = "menu",
                        prefsManager = prefsManager,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                        fabSizePx = fabSizePx,
                        defaultX = 1.0f,
                        defaultY = 0.8f,
                        visible = true,
                        onClick = onShowFileList,
                        content = { Icon(Icons.Default.Menu, contentDescription = context.getString(R.string.file_list)) }
                    )

                    DraggableFab(
                        name = "quick_access",
                        prefsManager = prefsManager,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                        fabSizePx = fabSizePx,
                        defaultX = 1.0f,
                        defaultY = 0.9f,
                        visible = true,
                        onClick = { showQuickAccess = true },
                        content = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "Quick access") }
                    )
                }
            }

            // Quick Access Bottom Sheet
            if (showQuickAccess) {
                QuickAccessSheet(
                    onDismiss = { showQuickAccess = false },
                    currentDocumentPath = fileInfo.path,
                    currentDocumentName = fileInfo.displayName,
                    onOpenDocument = onOpenLinkedDocument
                )
            }
        }
        else -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(fileInfo.displayName) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = LocalContext.current.getString(R.string.back))
                            }
                        },
                        actions = {
                            IconButton(onClick = onToggleTheme) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = LocalContext.current.getString(R.string.toggle_dark_mode)
                                )
                            }
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = LocalContext.current.getString(R.string.unsupported_file_format, fileInfo.extension),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
