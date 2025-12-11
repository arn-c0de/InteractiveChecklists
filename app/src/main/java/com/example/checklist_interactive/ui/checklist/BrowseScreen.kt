package com.example.checklist_interactive.ui.checklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
// removed DropdownMenu imports; using AlertDialog for folder popup
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.data.checklist.AssetBrowser
import com.example.checklist_interactive.data.checklist.AssetNode
import com.example.checklist_interactive.ui.checklist.ChecklistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    assetBrowser: AssetBrowser,
    startPath: String = "",
    onOpenFile: (assetPath: String) -> Unit,
    onBackToHome: (() -> Unit)? = null
    ,
    onThemeChange: ((isDark: Boolean) -> Unit)? = null,
    initialIsDark: Boolean = false
) {
    var currentPath by remember { mutableStateOf(startPath) }
    val nodes = remember(currentPath) { assetBrowser.list(currentPath) }
    val breadcrumb by remember(currentPath) {
        mutableStateOf(currentPath.split('/'))
    }

    var showFolderPopup by remember { mutableStateOf(false) }
    var popupPath by remember { mutableStateOf(currentPath) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isDarkTheme by remember { mutableStateOf(initialIsDark) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentPath) },
                navigationIcon = {
                    Row {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                            IconButton(onClick = {
                                showFolderPopup = !showFolderPopup
                                if (showFolderPopup) popupPath = currentPath
                            }) {
                                Icon(Icons.Default.Folder, contentDescription = "Open folders")
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                                        IconButton(onClick = {
                            if (startPath.isNotEmpty() && currentPath == startPath) {
                                onBackToHome?.invoke()
                            } else {
                                val segments = currentPath.split('/')
                                val parent = segments.dropLast(1).joinToString("/")
                                currentPath = if (parent.isEmpty()) startPath else parent
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Path: ")
                breadcrumb.forEachIndexed { index, segment ->
                    val isLast = index == breadcrumb.size - 1
                    Text(text = segment, modifier = Modifier.padding(horizontal = 4.dp))
                    if (!isLast) Text("/")
                }
            }
            Divider()
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(nodes) { node ->
                    BrowseListItem(node = node, onClick = { n ->
                        if (n.isDirectory) {
                            currentPath = n.path
                        } else {
                            onOpenFile(n.path)
                        }
                    })
                }
            }
        }
    }
    
    // Folder popup dialog
    if (showFolderPopup) {
        popupPath = popupPath.takeIf { it.isNotEmpty() } ?: startPath
        val popupNodes = remember(popupPath) { assetBrowser.list(popupPath) }
        AlertDialog(
            onDismissRequest = { showFolderPopup = false },
            title = { Text(popupPath.ifBlank { "Assets" }) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = {
                            if (popupPath != startPath) {
                                val segments = popupPath.split('/')
                                val parent = segments.dropLast(1).joinToString("/")
                                popupPath = if (parent.isEmpty()) startPath else parent
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Up")
                        }
                        Text(popupPath, modifier = Modifier.padding(start = 8.dp))
                    }
                    Divider()
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(popupNodes) { node ->
                            ListItem(
                                headlineContent = { Text(node.name) },
                                leadingContent = {
                                    if (node.isDirectory) Icon(Icons.Default.Folder, contentDescription = "folder")
                                    else Icon(Icons.Default.InsertDriveFile, contentDescription = "file")
                                },
                                modifier = Modifier.clickable {
                                    if (node.isDirectory) {
                                        popupPath = node.path
                                    } else {
                                        onOpenFile(node.path)
                                        showFolderPopup = false
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    currentPath = popupPath
                    showFolderPopup = false
                }) { Text("Open") }
            },
            dismissButton = {
                TextButton(onClick = { showFolderPopup = false }) { Text("Cancel") }
            }
        )
    }
    
    ThemeSettingsDialog(open = showSettingsDialog, isDark = isDarkTheme, onDismiss = { showSettingsDialog = false }) { toggled ->
        isDarkTheme = toggled
        onThemeChange?.invoke(toggled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseListItem(node: AssetNode, onClick: (AssetNode) -> Unit) {
    val displayName = if (!node.isDirectory && node.name.endsWith(".pdf")) node.name.removeSuffix(".pdf") else node.name
    ListItem(
        headlineContent = { Text(displayName) },
        leadingContent = {
            if (node.isDirectory) Icon(Icons.Default.Folder, contentDescription = "folder")
            else Icon(Icons.Default.InsertDriveFile, contentDescription = "file")
        },
        modifier = Modifier.clickable { onClick(node) }
    )
}

// Settings dialog outside of the main composable
@Composable
fun ThemeSettingsDialog(open: Boolean, isDark: Boolean, onDismiss: () -> Unit, onChange: (Boolean) -> Unit) {
    if (!open) return

    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Dark mode")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(checked = isDark, onCheckedChange = onChange)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
