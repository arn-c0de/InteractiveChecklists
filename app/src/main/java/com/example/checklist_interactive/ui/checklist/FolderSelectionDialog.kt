package com.example.checklist_interactive.ui.checklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.data.checklist.AssetBrowser
import com.example.checklist_interactive.data.checklist.AssetNode

/**
 * Dialog zur Auswahl eines Root-Ordners aus den Assets
 */
@Composable
fun FolderSelectionDialog(
    assetBrowser: AssetBrowser,
    initialPath: String = "",
    onDismiss: () -> Unit,
    onFolderSelected: (String) -> Unit
) {
    var currentPath by remember { mutableStateOf(initialPath) }
    val nodes = remember(currentPath) {
        assetBrowser.list(currentPath).filter { it.isDirectory }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ordner auswählen") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentPath.isNotEmpty()) {
                        IconButton(onClick = {
                            val segments = currentPath.split('/')
                            currentPath = segments.dropLast(1).joinToString("/")
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                    Text(
                        text = if (currentPath.isEmpty()) "Root" else currentPath,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Ordnerliste
                if (nodes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Keine Unterordner gefunden",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(nodes) { node ->
                            ListItem(
                                headlineContent = { Text(node.name) },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier.clickable {
                                    currentPath = node.path
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onFolderSelected(currentPath.ifEmpty { "checklists" })
                }
            ) {
                Text("Diesen Ordner wählen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
