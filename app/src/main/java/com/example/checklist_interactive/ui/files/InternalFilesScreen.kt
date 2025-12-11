package com.example.checklist_interactive.ui.files

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.data.files.FileInfo
import com.example.checklist_interactive.data.files.InternalFileManager
import com.example.checklist_interactive.data.prefs.PreferencesManager
import com.example.checklist_interactive.data.shortcuts.ShortcutManager
import com.example.checklist_interactive.data.shortcuts.PageShortcut
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings

import com.example.checklist_interactive.R
import androidx.compose.ui.graphics.Color

/**
 * Hauptscreen für interne Dateiverwaltung
 * Zeigt kategorisiert alle Dateien an
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalFilesScreen(
    fileManager: InternalFileManager,
    onFileOpen: (FileInfo) -> Unit,
    onShortcutOpen: (PageShortcut) -> Unit,
    onRefresh: () -> Unit,
    refreshTrigger: Int,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onShowSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }
    val shortcutManager = remember { ShortcutManager(context) }
    
    val assetAircrafts = remember {
        context.assets.list("Checklists")?.filter { entry ->
            try {
                val arr = context.assets.list("Checklists/$entry")
                arr != null && arr.isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }?.toList() ?: emptyList()
    }
    // Keep a lowercase set for matching folder nodes which may be lowercased on import
    val assetAircraftsLower = remember(assetAircrafts, fileManager) { (assetAircrafts + fileManager.getCategories()).map { it.lowercase() }.toSet() }
    var groupedFiles by remember { mutableStateOf(fileManager.getAllFilesGrouped()) }
    var folderTree by remember { mutableStateOf(fileManager.getFolderTree()) }
    var shortcuts by remember { mutableStateOf(shortcutManager.loadShortcuts()) }
    var showImportDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var fileToDelete by remember { mutableStateOf<FileInfo?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var shortcutToRename by remember { mutableStateOf<PageShortcut?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    
    // Expanded state für jede Kategorie + Shortcuts
    val expandedCategories = remember {
        mutableStateMapOf<String, Boolean>().apply {
            groupedFiles.keys.forEach { category ->
                put(category, prefsManager.isCategoryExpanded(category) ?: false)
            }
            // Also seed expanded state from folderTree nodes
            folderTree.forEach { node ->
                put(node.relativePath, prefsManager.isCategoryExpanded(node.relativePath) ?: false)
                // include nested children
                fun seedChildren(n: InternalFileManager.FolderNode) {
                    n.children.forEach {
                        put(it.relativePath, prefsManager.isCategoryExpanded(it.relativePath) ?: false)
                        seedChildren(it)
                    }
                }
                seedChildren(node)
            }
            put("shortcuts", prefsManager.isCategoryExpanded("shortcuts") ?: false)
        }
    }

    // Import Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            selectedCategory?.let { category ->
                val result = fileManager.importFile(selectedUri, category)
                result.onSuccess {
                    // Refresh file list and folder tree
                    groupedFiles = fileManager.getAllFilesGrouped()
                    // Filter folderTree based on visible aircraft preferences
                    folderTree = fileManager.getFolderTree().filter { node ->
                        // If the node is a known bundled aircraft folder, check prefs; otherwise always show
                        if (assetAircraftsLower.contains(node.name.lowercase())) {
                            prefsManager.isAircraftVisible(node.name)
                        } else {
                            true
                        }
                    }
                    onRefresh()
                }.onFailure { error ->
                    // TODO: Show error to user
                }
            }
        }
        showImportDialog = false
        selectedCategory = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.title_my_files)) },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = context.getString(R.string.import_file))
                    }
                    IconButton(onClick = {
                        groupedFiles = fileManager.getAllFilesGrouped()
                        folderTree = fileManager.getFolderTree().filter { node ->
                            if (assetAircraftsLower.contains(node.name.lowercase())) {
                                prefsManager.isAircraftVisible(node.name)
                            } else {
                                true
                            }
                        }
                        onRefresh()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = context.getString(R.string.refresh))
                    }
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = context.getString(R.string.toggle_dark_mode)
                        )
                    }
                    IconButton(onClick = onShowSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        if (folderTree.isEmpty() && shortcuts.isEmpty()) {
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
                        context.getString(R.string.no_files),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.import_file))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Shortcuts-Kategorie (immer zuerst)
                if (shortcuts.isNotEmpty()) {
                    item {
                        ShortcutsHeader(
                            shortcutCount = shortcuts.size,
                            isExpanded = expandedCategories["shortcuts"] ?: true,
                            onToggleExpanded = {
                                val newState = !(expandedCategories["shortcuts"] ?: true)
                                expandedCategories["shortcuts"] = newState
                                prefsManager.setCategoryExpanded("shortcuts", newState)
                            }
                        )
                    }
                    
                    if (expandedCategories["shortcuts"] == true) {
                        items(shortcuts) { shortcut ->
                            ShortcutListItem(
                                shortcut = shortcut,
                                onClick = { onShortcutOpen(shortcut) },
                                onDelete = {
                                    shortcutManager.deleteShortcut(shortcut.id)
                                    shortcuts = shortcutManager.loadShortcuts()
                                },
                                onRename = {
                                    shortcutToRename = shortcut
                                    renameText = shortcut.name
                                    showRenameDialog = true
                                }
                            )
                        }
                    }
                }
                
                // Show folder tree once (it contains all categories and nested folders)
                item {
                    Column {
                        FolderTree(
                            nodes = folderTree,
                            expanded = expandedCategories,
                            onToggleExpanded = { key, newState ->
                                expandedCategories[key] = newState
                                prefsManager.setCategoryExpanded(key, newState)
                            },
                            onFileOpen = onFileOpen,
                            onDelete = { file ->
                                fileToDelete = file
                                showDeleteConfirm = true
                            },
                            level = 0
                        )
                    }
                }
            }
        }
    }

    // Import Dialog
    if (showImportDialog) {
        CategorySelectionDialog(
            categories = fileManager.getAllCategoryPaths(),
            selectedCategory = selectedCategory,
            onCategorySelected = { category ->
                selectedCategory = category
                importLauncher.launch("*/*")
            },
            onDismiss = {
                showImportDialog = false
                selectedCategory = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm && fileToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                fileToDelete = null
            },
            title = { Text(context.getString(R.string.delete_file)) },
            text = { Text(context.getString(R.string.delete_confirm, fileToDelete?.displayName ?: "")) },
            confirmButton = {
                        TextButton(
                    onClick = {
                        fileToDelete?.let { file ->
                            fileManager.deleteFile(file.path)
                            groupedFiles = fileManager.getAllFilesGrouped()
                                    folderTree = fileManager.getFolderTree()
                            onRefresh()
                        }
                        showDeleteConfirm = false
                        fileToDelete = null
                    }
                ) {
                    Text(context.getString(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    fileToDelete = null
                }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
    
    // Rename Shortcut Dialog
    if (showRenameDialog && shortcutToRename != null) {
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                shortcutToRename = null
            },
            title = { Text("Shortcut umbenennen") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            shortcutToRename?.let { shortcut ->
                                shortcutManager.renameShortcut(shortcut.id, renameText)
                                shortcuts = shortcutManager.loadShortcuts()
                            }
                        }
                        showRenameDialog = false
                        shortcutToRename = null
                    }
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    shortcutToRename = null
                }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // When parent changes refreshTrigger, update contents
    LaunchedEffect(refreshTrigger) {
        groupedFiles = fileManager.getAllFilesGrouped()
        folderTree = fileManager.getFolderTree().filter { node ->
            if (assetAircraftsLower.contains(node.name.lowercase())) {
                prefsManager.isAircraftVisible(node.name)
            } else {
                true
            }
        }
        shortcuts = shortcutManager.loadShortcuts()
    }

    // Listen for preference changes and update filtering when visible aircrafts change
    DisposableEffect(prefsManager) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            groupedFiles = fileManager.getAllFilesGrouped()
            folderTree = fileManager.getFolderTree().filter { node ->
                // Use lowercase set for matching to handle imported/lowercased nodes
                if (assetAircraftsLower.contains(node.name.lowercase())) {
                    prefsManager.isAircraftVisible(node.name)
                } else {
                    true
                }
            }
            shortcuts = shortcutManager.loadShortcuts()
        }
        prefsManager.registerOnChangeListener(listener)
        onDispose { prefsManager.unregisterOnChangeListener(listener) }
    }
}

@Composable
private fun FolderTree(
    nodes: List<InternalFileManager.FolderNode>,
    expanded: MutableMap<String, Boolean>,
    onToggleExpanded: (String, Boolean) -> Unit,
    onFileOpen: (FileInfo) -> Unit,
    onDelete: (FileInfo) -> Unit,
    level: Int
) {
    nodes.forEach { node ->
        FolderNodeItem(
            node = node,
            expanded = expanded,
            onToggleExpanded = onToggleExpanded,
            onFileOpen = onFileOpen,
            onDelete = onDelete,
            level = level
        )
    }
}

private fun countFiles(node: InternalFileManager.FolderNode): Int {
    var count = node.files.size
    node.children.forEach { child ->
        count += countFiles(child)
    }
    return count
}

@Composable
private fun FolderNodeItem(
    node: InternalFileManager.FolderNode,
    expanded: MutableMap<String, Boolean>,
    onToggleExpanded: (String, Boolean) -> Unit,
    onFileOpen: (FileInfo) -> Unit,
    onDelete: (FileInfo) -> Unit,
    level: Int
) {
    val key = node.relativePath
    val isExpanded = expanded[key] ?: true
    val fileCount = countFiles(node)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpanded(key, !isExpanded) },
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width((level * 12).dp))
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = node.name.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            val localContext = LocalContext.current
            Text(
                text = "$fileCount ${if (fileCount == 1) localContext.getString(R.string.file) else localContext.getString(R.string.files)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { onToggleExpanded(key, !isExpanded) }) {
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) localContext.getString(R.string.collapse) else localContext.getString(R.string.expand)
                )
            }
        }
    }

    if (isExpanded) {
        // Show files for this node
        node.files.forEach { file ->
            FileListItem(file = file, onClick = { onFileOpen(file) }, onDelete = { onDelete(file) })
        }

        // Recurse into children nodes
        node.children.forEach { child ->
            FolderNodeItem(
                node = child,
                expanded = expanded,
                onToggleExpanded = onToggleExpanded,
                onFileOpen = onFileOpen,
                onDelete = onDelete,
                level = level + 1
            )
        }
    }
}

@Composable
private fun CategoryHeader(
    category: String,
    fileCount: Int,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onImport: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpanded() },
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
                text = category.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            val localContext = LocalContext.current
            Text(
                text = "$fileCount ${if (fileCount == 1) localContext.getString(R.string.file) else localContext.getString(R.string.files)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { 
                onImport()
            }) {
                Icon(Icons.Default.Add, contentDescription = localContext.getString(R.string.add_file))
            }
            IconButton(onClick = onToggleExpanded) {
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) localContext.getString(R.string.collapse) else localContext.getString(R.string.expand)
                )
            }
        }
    }
}

@Composable
private fun ShortcutsHeader(
    shortcutCount: Int,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Shortcuts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$shortcutCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onToggleExpanded) {
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Zuklappen" else "Aufklappen"
                )
            }
        }
    }
}

@Composable
private fun ShortcutListItem(
    shortcut: PageShortcut,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    ListItem(
        headlineContent = { Text(shortcut.name) },
        supportingContent = {
            Text(
                "${shortcut.fileName} • Seite ${shortcut.pageNumber + 1}",
                style = MaterialTheme.typography.labelSmall
            )
        },
        leadingContent = {
            Icon(
                Icons.Default.Bookmark,
                contentDescription = null,
                tint = if (shortcut.isHighlighted) Color.Yellow else MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onRename) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Umbenennen",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Löschen",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}

@Composable
private fun FileListItem(
    file: FileInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val fileIcon = when (file.extension.lowercase()) {
        "pdf" -> Icons.Default.PictureAsPdf
        "md", "markdown" -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }

    val fileColor = when (file.extension.lowercase()) {
        "pdf" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    ListItem(
        headlineContent = { Text(file.displayName) },
        supportingContent = {
            Text(
                file.extension.uppercase(),
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
            Row {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = LocalContext.current.getString(R.string.delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}

@Composable
private fun CategorySelectionDialog(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kategorie wählen") },
        text = {
            LazyColumn {
                items(categories) { category ->
                    val displayName = category.replace('/', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }
                    ListItem(
                        headlineContent = { Text(displayName) },
                        leadingContent = {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable {
                            onCategorySelected(category)
                        }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
