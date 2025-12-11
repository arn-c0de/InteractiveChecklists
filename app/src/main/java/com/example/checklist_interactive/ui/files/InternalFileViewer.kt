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
    when (fileInfo.extension.lowercase()) {
        "pdf" -> {
            PdfViewer(
                pdfPath = fileInfo.path,
                title = fileInfo.displayName,
                onBack = onBack,
                onShowFileList = onShowFileList,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                isInternalFile = true,
                initialPage = initialPage
            )
        }
        "md", "markdown" -> {
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
                    checklist = null,
                    onCheckboxChange = null,
                    modifier = Modifier.padding(padding),
                    isInternalFile = true
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
