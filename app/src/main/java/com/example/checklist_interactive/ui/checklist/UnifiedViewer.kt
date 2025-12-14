package com.example.checklist_interactive.ui.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.checklist_interactive.data.checklist.Checklist
import androidx.compose.ui.platform.LocalContext
import com.example.checklist_interactive.data.prefs.PreferencesManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.checklist_interactive.data.checklist.ChecklistRepository
import com.example.checklist_interactive.data.checklist.MarkdownChecklistParser
import com.example.checklist_interactive.ui.checklist.ChecklistViewModel
import com.example.checklist_interactive.ui.checklist.ChecklistViewModelFactory
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.checklist_interactive.R

/**
 * UnifiedViewer - Universal viewer that automatically chooses between PDF and Markdown
 *
 * Supports:
 * - .pdf files (if PDF library available)
 * - .md, .markdown files
 *
 * @param assetPath path to the file in assets
 * @param checklist Optional: Checklist data for checkbox interactions
 * @param onCheckboxChange Callback for checkbox changes
 * @param modifier Modifier for layout
 */
@Composable
fun UnifiedViewer(
    assetPath: String,
    checklist: Checklist? = null,
    onCheckboxChange: ((itemId: String, checked: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val fileExtension = assetPath.substringAfterLast('.', "").lowercase()
    
    val context = LocalContext.current
    when (fileExtension) {
        "md", "markdown" -> {
            // Use Markdown viewer
            val prefsManager = remember { PreferencesManager(context) }
            if (checklist != null) {
                // For interactive checklists, create a viewmodel so states persist
                val checklistRepository = remember { ChecklistRepository(context) }
                val markdownContent = remember(assetPath) {
                    try {
                        context.assets.open(assetPath).bufferedReader().use { it.readText() }
                    } catch (e: Exception) {
                        "" // Return empty on error
                    }
                }
                val parsedChecklist = remember(markdownContent) { MarkdownChecklistParser().parse(assetPath, markdownContent) }
                val viewModel: ChecklistViewModel = viewModel(key = assetPath, factory = ChecklistViewModelFactory(checklistRepository, parsedChecklist))
                val checklistState by viewModel.checklistState.collectAsState()
                MarkdownViewer(
                    assetPath = assetPath,
                    checklist = checklistState,
                    onCheckboxChange = viewModel::onCheckboxChange,
                    modifier = modifier,
                    prefsManager = prefsManager,

                )
            } else {
                MarkdownViewer(
                    assetPath = assetPath,
                    checklist = checklist,
                    onCheckboxChange = onCheckboxChange,
                    modifier = modifier,
                    prefsManager = prefsManager,

                )
            }
        }
        "pdf" -> {
            // PDF viewer is used in UnifiedViewerScreen
            // (PdfViewer requires a TopBar and navigation)
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = LocalContext.current.getString(R.string.use_unified_viewer_for_pdf),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
        else -> {
            // Unbekanntes Format
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = LocalContext.current.getString(R.string.unsupported_file_format, fileExtension),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * UnifiedViewerScreen - Screen wrapper for UnifiedViewer
 * Automatically shows the appropriate viewer based on file type
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedViewerScreen(
    assetPath: String,
    checklist: Checklist? = null,
    onBack: () -> Unit,
    onCheckboxChange: ((itemId: String, checked: Boolean) -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onShowFileList: (() -> Unit)? = null
) {
    val fileName = assetPath.substringAfterLast('/').substringBeforeLast('.')
    val fileExtension = assetPath.substringAfterLast('.', "").lowercase()
    
    // For Markdown files we use the MarkdownViewerScreen directly
    // (it already has TopBar and navigation)
    when (fileExtension) {
        "md", "markdown" -> {
            MarkdownViewerScreen(
                assetPath = assetPath,
                checklist = checklist,
                onBack = onBack,
                onCheckboxChange = onCheckboxChange,
                onSettings = onSettings,
                onShowFileList = onShowFileList
            )
        }
        "pdf" -> {
            // Use PDF viewer
            PdfViewer(
                pdfPath = assetPath,
                title = fileName,
                onBack = onBack,
                onShowFileList = onShowFileList
            )
        }
        else -> {
            // Unknown format
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(fileName) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back"
                                )
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Unsupported file format: .$fileExtension\nSupported: .md, .markdown",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
