package com.example.checklist_interactive.ui.files

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.data.files.FileInfo
import com.example.checklist_interactive.ui.checklist.MarkdownViewer
import com.example.checklist_interactive.ui.checklist.PdfViewer

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
    initialPage: Int = 0,
    onBack: () -> Unit,
    onShowFileList: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current

    when (fileInfo.extension.lowercase()) {
        "pdf" -> {
            val prefsManager = remember { PreferencesManager(context) }
            val lastPage = remember(fileInfo.path) { prefsManager.getInt("pdf_last_page_${fileInfo.path}", 0) }
            var currentPage by remember { mutableStateOf(lastPage) }
            PdfViewer(
                pdfPath = fileInfo.path,
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
                onPageChange = { page -> currentPage = page }
            )
        }
            "md", "markdown" -> {
            val prefsManager = remember { PreferencesManager(context) }
            val checklistRepository = remember { ChecklistRepository(context) }
            val markdownContent = remember(fileInfo.path) {
                try {
                    File(fileInfo.path).readText()
                } catch (e: Exception) {
                    "" // Return empty on error
                }
            }
            val parsedChecklist = remember(markdownContent) {
                MarkdownChecklistParser().parse(fileInfo.path, markdownContent)
            }

            val viewModel: ChecklistViewModel = viewModel(
                factory = ChecklistViewModelFactory(checklistRepository, parsedChecklist)
            )

            val checklistState by viewModel.checklistState.collectAsState()

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
                    FloatingActionButton(
                        onClick = onShowFileList,
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = LocalContext.current.getString(R.string.file_list))
                    }
                }
            ) { padding ->
                MarkdownViewer(
                    assetPath = fileInfo.path,
                    checklist = checklistState,
                    onCheckboxChange = viewModel::onCheckboxChange,
                    modifier = Modifier.padding(padding),
                    isInternalFile = true,
                    prefsManager = prefsManager
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
