package com.example.checklist_interactive.ui.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.checklist_interactive.data.checklist.Checklist
import androidx.compose.ui.platform.LocalContext
import com.example.checklist_interactive.R

/**
 * UnifiedViewer - Universeller Viewer der automatisch zwischen PDF und Markdown wählt
 * 
 * Unterstützt:
 * - .pdf Dateien (wenn PDF-Bibliothek verfügbar)
 * - .md, .markdown Dateien
 * 
 * @param assetPath Pfad zur Datei in den Assets
 * @param checklist Optional: Checklist-Daten für Checkbox-Interaktion
 * @param onCheckboxChange Callback für Checkbox-Änderungen
 * @param modifier Modifier für das Layout
 */
@Composable
fun UnifiedViewer(
    assetPath: String,
    checklist: Checklist? = null,
    onCheckboxChange: ((itemId: String, checked: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val fileExtension = assetPath.substringAfterLast('.', "").lowercase()
    
    when (fileExtension) {
        "md", "markdown" -> {
            // Markdown-Viewer verwenden
            MarkdownViewer(
                assetPath = assetPath,
                checklist = checklist,
                onCheckboxChange = onCheckboxChange,
                modifier = modifier
            )
        }
        "pdf" -> {
            // PDF-Viewer wird in UnifiedViewerScreen verwendet
            // (PdfViewer benötigt eine TopBar und Navigation)
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
 * UnifiedViewerScreen - Screen-Wrapper für den UnifiedViewer
 * Zeigt automatisch den passenden Viewer basierend auf dem Dateityp
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
    
    // Für Markdown-Dateien verwenden wir den MarkdownViewerScreen direkt
    // (dieser hat bereits TopBar und Navigation)
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
            // PDF-Viewer verwenden
            PdfViewer(
                pdfPath = assetPath,
                title = fileName,
                onBack = onBack,
                onShowFileList = onShowFileList
            )
        }
        else -> {
            // Unbekanntes Format
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(fileName) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Zurück"
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
                        text = "Nicht unterstütztes Dateiformat: .$fileExtension\nUnterstützt: .md, .markdown",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
