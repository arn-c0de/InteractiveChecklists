package com.example.checklist_interactive.ui.checklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.data.checklist.Checklist
import com.example.checklist_interactive.data.checklist.ChecklistItem
import com.example.checklist_interactive.data.checklist.ChecklistSection
import com.example.checklist_interactive.data.checklist.AssetBrowser
import com.example.checklist_interactive.data.checklist.AssetNode

import androidx.compose.ui.platform.LocalContext
import com.example.checklist_interactive.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(viewModel: ChecklistViewModel, checklistId: String, onBack: (() -> Unit)? = null, onExport: ((String) -> Unit)? = null, assetBrowser: AssetBrowser? = null, onOpenFile: ((String) -> Unit)? = null, onThemeChange: ((Boolean) -> Unit)? = null, initialIsDark: Boolean = false) {
    val checklistState by viewModel.checklistState.collectAsState()
    val checklist = checklistState

    var showFolderPopup by remember { mutableStateOf(false) }
    var popupPath by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isDarkTheme by remember { mutableStateOf(initialIsDark) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = checklist.title) }, navigationIcon = {
                Row {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                        IconButton(onClick = {
                            showFolderPopup = !showFolderPopup
                            if (showFolderPopup) popupPath = ""
                        }) {
                            Icon(Icons.Default.Folder, contentDescription = LocalContext.current.getString(R.string.open_folders))
                        }
                    }
                    if (showFolderPopup && assetBrowser != null) {
                        val popupNodes = remember(popupPath) { assetBrowser.list(popupPath) }
                        AlertDialog(onDismissRequest = { showFolderPopup = false }, title = { Text(popupPath) }, text = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    IconButton(onClick = {
                                        if (popupPath.isNotEmpty()) {
                                            val segments = popupPath.split('/')
                                            val parent = segments.dropLast(1).joinToString("/")
                                            popupPath = if (parent.isEmpty()) "" else parent
                                        }
                                    }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = LocalContext.current.getString(R.string.up))
                                    }
                                    Text(popupPath.ifBlank { "Assets" }, modifier = Modifier.padding(start = 8.dp))
                                }
                                Divider()
                                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                    items(popupNodes) { node: AssetNode ->
                                        ListItem(headlineContent = { Text(node.name) }, leadingContent = {
                                            if (node.isDirectory) Icon(Icons.Default.Folder, contentDescription = "folder")
                                            else Icon(Icons.Default.InsertDriveFile, contentDescription = "file")
                                        }, modifier = Modifier.clickable {
                                            if (node.isDirectory) {
                                                popupPath = node.path
                                            } else {
                                                onOpenFile?.invoke(node.path)
                                                showFolderPopup = false
                                            }
                                        })
                                    }
                                }
                            }
                        }, confirmButton = {
                            TextButton(onClick = {
                                onOpenFile?.invoke(popupPath)
                                showFolderPopup = false
                            }) { Text(LocalContext.current.getString(R.string.open)) }
                        }, dismissButton = {
                            TextButton(onClick = { showFolderPopup = false }) { Text(LocalContext.current.getString(R.string.cancel)) }
                        })
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(onClick = { onBack?.invoke() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = LocalContext.current.getString(R.string.back))
                    }
                }
            }, actions = {
                IconButton(onClick = { viewModel.resetChecklist() }) {
                    Icon(Icons.Default.Refresh, contentDescription = LocalContext.current.getString(R.string.reset))
                }
                IconButton(onClick = {
                    val md = viewModel.exportChecklistMarkdown()
                    if (md != null) onExport?.invoke(md)
                }) {
                    Icon(Icons.Default.Share, contentDescription = LocalContext.current.getString(R.string.export))
                }
                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(Icons.Default.Settings, contentDescription = LocalContext.current.getString(R.string.settings))
                }
            })
        }
    ) { padding ->
        val c: Checklist = checklist
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(c.sections) { section: ChecklistSection ->
                    ChecklistSectionComposable(section = section, checklistId = c.id, viewModel = viewModel)
                }
            }
        
    }
    ThemeSettingsDialog(open = showSettingsDialog, isDark = isDarkTheme, onDismiss = { showSettingsDialog = false }) { toggled ->
        isDarkTheme = toggled
        onThemeChange?.invoke(toggled)
    }
}

@Composable
fun ChecklistSectionComposable(section: ChecklistSection, checklistId: String, viewModel: ChecklistViewModel) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text(text = section.title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
            section.items.forEach { item: ChecklistItem ->
                ChecklistItemComposable(item = item, checklistId = checklistId, viewModel = viewModel)
        }
    }
}

@Composable
fun ChecklistItemComposable(item: ChecklistItem, checklistId: String, viewModel: ChecklistViewModel) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { viewModel.onCheckboxChange(item.id, !item.isChecked) }
        .padding(4.dp), horizontalArrangement = Arrangement.Start) {
        Checkbox(checked = item.isChecked, onCheckedChange = { checked -> viewModel.onCheckboxChange(item.id, checked) })
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = parseInlineMarkdown(item.text, 16), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None)
    }
}