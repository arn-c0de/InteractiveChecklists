package com.example.checklist_interactive.ui.checklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.data.checklist.AssetBrowser
import com.example.checklist_interactive.data.checklist.AssetNode

/**
 * Categorized file view - shows all files grouped by folders
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorizedFilesScreen(
    assetBrowser: AssetBrowser,
    rootFolder: String,
    onFileSelect: (String) -> Unit,
    onClose: () -> Unit,
    onChangeRootFolder: () -> Unit
) {
    val groupedFiles = remember(rootFolder) {
        assetBrowser.scanAllFilesGrouped(rootFolder)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Files in: $rootFolder") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onChangeRootFolder) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Change folder")
                    }
                }
            )
        }
    ) { padding ->
        if (groupedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No files found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onChangeRootFolder) {
                        Text("Select another folder")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                groupedFiles.forEach { (category, files) ->
                    item {
                        // Kategorie-Header
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "${files.size} ${if (files.size == 1) "Datei" else "Dateien"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(files) { file ->
                        CategorizedFileItem(
                            file = file,
                            onClick = { onFileSelect(file.path) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorizedFileItem(
    file: AssetNode,
    onClick: () -> Unit
) {
    val displayName = file.name
        .removeSuffix(".pdf")
        .removeSuffix(".md")
        .removeSuffix(".markdown")

    val fileIcon = when {
        file.name.endsWith(".pdf") -> Icons.Default.PictureAsPdf
        file.name.endsWith(".md") || file.name.endsWith(".markdown") -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }

    val fileColor = when {
        file.name.endsWith(".pdf") -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    ListItem(
        headlineContent = { Text(displayName) },
        supportingContent = {
            Text(
                file.name.substringAfterLast('.').uppercase(),
                style = MaterialTheme.typography.labelSmall
            )
        },
        leadingContent = {
            Icon(
                fileIcon,
                contentDescription = null,
                tint = fileColor
            )
        },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}
