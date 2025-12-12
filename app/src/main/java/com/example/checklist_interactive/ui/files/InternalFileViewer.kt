package com.example.checklist_interactive.ui.files

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import java.io.File
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.checklist_interactive.R

/**
 * Viewer für interne Dateien (MD und PDF)
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
            val lastPage = remember(fileInfo.path) { prefsManager.getInt("pdf_last_page_${fileInfo.path}", 0) }
            var currentPage by remember(fileInfo.path) { mutableStateOf(lastPage) }

            // Aktualisiere currentPage wenn initialPage sich ändert (z.B. durch Link-Klick)
            // initialPage = -1 bedeutet "nicht gesetzt, verwende lastPage"
            androidx.compose.runtime.LaunchedEffect(initialPage) {
                if (initialPage >= 0) {
                    currentPage = initialPage
                }
            }

            val isAsset = fileInfo.isAsset || fileInfo.path.startsWith("asset://")
            val pdfPath = if (isAsset) {
                val assetPath = fileInfo.path.removePrefix("asset://")
                // Copy asset to cache once
                val safeName = assetPath.replace('/', '_')
                val tmp = File(context.cacheDir, "asset_$safeName")
                if (!tmp.exists()) {
                    try {
                        context.assets.open(assetPath).use { input ->
                            tmp.outputStream().use { output -> input.copyTo(output) }
                        }
                    } catch (e: Exception) {
                        // fallback: try without copying (will fail later)
                    }
                }
                tmp.absolutePath
            } else {
                fileInfo.path
            }

            PdfViewer(
                pdfPath = pdfPath,
                title = fileInfo.displayName,
                onBack = {
                    prefsManager.setInt("pdf_last_page_${fileInfo.path}", currentPage)
                    onBack()
                },
                onShowFileList = onShowFileList,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                isInternalFile = true,
                initialPage = currentPage,
                onPageChange = { page -> currentPage = page },
                onOpenLinkedDocument = onOpenLinkedDocument
            )
        }
            "md", "markdown" -> {
            val prefsManager = remember { PreferencesManager(context) }
            val checklistRepository = remember { ChecklistRepository(context) }
            val quickNoteManager = remember { QuickNoteManager(context) }
            val isAsset = fileInfo.isAsset || fileInfo.path.startsWith("asset://")
            val assetPath = if (isAsset) fileInfo.path.removePrefix("asset://") else fileInfo.path
            val markdownContent = remember(fileInfo.path) {
                try {
                    if (isAsset) {
                        context.assets.open(assetPath).bufferedReader().use { it.readText() }
                    } else {
                        File(fileInfo.path).readText()
                    }
                } catch (e: Exception) {
                    "" // Return empty on error
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

            // State für Expand/Collapse All
            var expandAllSections by remember { mutableStateOf(prefsManager.areMarkdownSectionsExpandedByDefault()) }
            var showQuickAccess by remember { mutableStateOf(false) }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(fileInfo.displayName) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = LocalContext.current.getString(R.string.back))
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.resetChecklist() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset Checklist")
                            }
                            // Expand/Collapse All Button
                            IconButton(onClick = {
                                expandAllSections = !expandAllSections
                                prefsManager.setMarkdownSectionsExpandedByDefault(expandAllSections)
                            }) {
                                Icon(
                                    imageVector = if (expandAllSections) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                    contentDescription = if (expandAllSections) "Alle einklappen" else "Alle ausklappen"
                                )
                            }
                            IconButton(onClick = onToggleTheme) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = LocalContext.current.getString(R.string.toggle_dark_mode)
                                )
                            }
                        }
                    )
                },
                floatingActionButton = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        // Quick Access FAB - immer sichtbar an fester Position
                        FloatingActionButton(
                            onClick = { showQuickAccess = true },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(Icons.Default.NoteAdd, contentDescription = "Schnellzugriff")
                        }

                        // Menu FAB - immer an fester Position
                        FloatingActionButton(
                            onClick = onShowFileList,
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = LocalContext.current.getString(R.string.file_list))
                        }
                    }
                }
            ) { padding ->
                MarkdownViewer(
                    assetPath = assetPath,
                    checklist = checklistState,
                    onCheckboxChange = viewModel::onCheckboxChange,
                    modifier = Modifier.padding(padding),
                    isInternalFile = !isAsset,
                    prefsManager = prefsManager
                )
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
                                Icon(Icons.Default.ArrowBack, contentDescription = LocalContext.current.getString(R.string.back))
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
