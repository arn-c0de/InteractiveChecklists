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
import androidx.compose.material3.LocalContentColor
import com.example.checklist_interactive.data.files.FileInfo
import com.example.checklist_interactive.data.files.InternalFileManager
import com.example.checklist_interactive.data.prefs.PreferencesManager
import com.example.checklist_interactive.data.shortcuts.ShortcutManager
import com.example.checklist_interactive.data.shortcuts.PageShortcut
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.NoteAdd

import com.example.checklist_interactive.R
import androidx.compose.ui.graphics.Color
import com.example.checklist_interactive.ui.tags.FileTagEditorDialog
import com.example.checklist_interactive.ui.tags.TagFilterBar
import com.example.checklist_interactive.ui.tags.TagChips
import com.example.checklist_interactive.ui.quickaccess.QuickAccessSheet

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
    onShowSettings: () -> Unit = {},
    onOpenLinkedDocument: ((filePath: String, pageNumber: Int?) -> Unit)? = null
) {
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }
    val shortcutManager = remember { ShortcutManager(context) }
    
    // Lade alle Top-Level-Ordner aus assets dynamisch
    val assetTopLevelFolders = remember {
        try {
            context.assets.list("")?.filter { entry ->
                try {
                    val arr = context.assets.list(entry)
                    arr != null && arr.isNotEmpty()
                } catch (e: Exception) {
                    false
                }
            }?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Lade alle Aircraft-Unterordner aus dem Checklists-Ordner (case-insensitive, dynamisch)
    val assetAircrafts = remember {
        try {
            val rootEntries = context.assets.list("") ?: emptyArray()
            val checklistsFolder = rootEntries.firstOrNull { it.equals("Checklists", ignoreCase = true) }
            if (checklistsFolder != null) {
                context.assets.list(checklistsFolder)?.filter { entry ->
                    try {
                        val arr = context.assets.list("$checklistsFolder/$entry")
                        arr != null && arr.isNotEmpty()
                    } catch (e: Exception) {
                        false
                    }
                }?.toList() ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Keep a lowercase set for matching folder nodes which may be lowercased on import
    // Kombiniere alle Top-Level-Ordner, Aircraft-Ordner und bereits importierte Kategorien
    val assetAircraftsLower = remember(assetAircrafts, assetTopLevelFolders, fileManager) { 
        (assetTopLevelFolders + assetAircrafts + fileManager.getCategories()).map { it.lowercase() }.toSet() 
    }
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
    
    // Tag-related state
    val tagManager = remember { fileManager.tagManager }
    var fileToEditTags by remember { mutableStateOf<FileInfo?>(null) }
    var showTagEditor by remember { mutableStateOf(false) }
    var showTagFilter by remember { mutableStateOf(false) }
    var selectedTagFilters by remember { mutableStateOf(prefsManager.getActiveTagFilters()) }
    var tagFilterMode by remember { mutableStateOf(prefsManager.getTagFilterMode()) }
    val allUsedTags = remember(refreshTrigger) { tagManager.getAllUsedTags() }

    // Quick Access state
    var showQuickAccess by remember { mutableStateOf(false) }
    
    // Function to refresh and enrich files with tags
    fun refreshFilesWithTags() {
        val rawGroupedFiles = fileManager.getAllFilesGrouped()
        groupedFiles = rawGroupedFiles.mapValues { (_, files) ->
            val enrichedFiles = fileManager.enrichWithTags(files)
            if (selectedTagFilters.isEmpty()) {
                enrichedFiles
            } else if (tagFilterMode == "all") {
                enrichedFiles.filter { file -> selectedTagFilters.all { tag -> file.tags.contains(tag) } }
            } else {
                enrichedFiles.filter { file -> file.tags.any { tag -> selectedTagFilters.contains(tag) } }
            }
        }.filterValues { it.isNotEmpty() }
        
        val rawTree = fileManager.getFolderTree().map { node ->
            filterAircraftChildren(node, assetAircraftsLower, prefsManager)
        }
        folderTree = rawTree.map { node -> enrichNodeWithTags(node, fileManager, selectedTagFilters, tagFilterMode) }
    }
    
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
                    // Refresh file list und filter folderTree nach Sichtbarkeit
                    refreshFilesWithTags()
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
                    // Tag filter toggle
                    IconButton(onClick = { showTagFilter = !showTagFilter }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter by tags",
                            tint = if (selectedTagFilters.isNotEmpty())
                                MaterialTheme.colorScheme.primary
                            else
                                LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = context.getString(R.string.import_file))
                    }
                    IconButton(onClick = {
                        refreshFilesWithTags()
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
                    Icon(
                        Icons.Default.NoteAdd,
                        contentDescription = "Schnellzugriff"
                    )
                }

                // Import FAB - immer an fester Position
                FloatingActionButton(
                    onClick = { showImportDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = context.getString(R.string.import_file))
                }
            }
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
                // Tag filter bar
                if (showTagFilter && allUsedTags.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            TagFilterBar(
                                availableTags = allUsedTags,
                                selectedTags = selectedTagFilters,
                                filterMode = tagFilterMode,
                                onTagToggle = { tag ->
                                    selectedTagFilters = if (selectedTagFilters.contains(tag)) {
                                        selectedTagFilters - tag
                                    } else {
                                        selectedTagFilters + tag
                                    }
                                    prefsManager.setActiveTagFilters(selectedTagFilters)
                                        refreshFilesWithTags()

                                    // Expand folders that contain files matching the selected tags,
                                    // but do not expand aircraft folders that are deselected in settings.
                                    fun nodeHasMatchingFiles(node: InternalFileManager.FolderNode): Boolean {
                                        // Check files in this node
                                        val enriched = fileManager.enrichWithTags(node.files)
                                        val fileMatches = enriched.any { file ->
                                            if (selectedTagFilters.isEmpty()) return@any false
                                            if (tagFilterMode == "all") {
                                                selectedTagFilters.all { tag -> file.tags.contains(tag) }
                                            } else {
                                                file.tags.any { t -> selectedTagFilters.contains(t) }
                                            }
                                        }
                                        if (fileMatches) return true
                                        // Check children recursively
                                        return node.children.any { child -> nodeHasMatchingFiles(child) }
                                    }

                                    fun expandAncestors(path: String) {
                                        var p = path
                                        while (p.isNotBlank()) {
                                            expandedCategories[p] = true
                                            p = p.substringBeforeLast('/', "")
                                        }
                                    }

                                    if (selectedTagFilters.isNotEmpty()) {
                                        // Iterate top-level nodes and expand matches
                                        folderTree.forEach { rootNode ->
                                            if (rootNode.name.equals("Checklists", ignoreCase = true)) {
                                                // Only consider visible aircraft folders
                                                rootNode.children.forEach { aircraftNode ->
                                                    if (!prefsManager.isAircraftVisible(aircraftNode.name)) return@forEach
                                                    if (nodeHasMatchingFiles(aircraftNode)) {
                                                        expandAncestors(aircraftNode.relativePath)
                                                    }
                                                }
                                            } else {
                                                if (nodeHasMatchingFiles(rootNode)) {
                                                    expandAncestors(rootNode.relativePath)
                                                }
                                            }
                                        }
                                    }
                                },
                                onClearAll = {
                                    selectedTagFilters = emptySet()
                                    prefsManager.clearTagFilters()
                                    refreshFilesWithTags()
                                },
                                onFilterModeChange = { mode ->
                                    tagFilterMode = mode
                                    prefsManager.setTagFilterMode(mode)
                                    refreshFilesWithTags()
                                },
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                
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
                            },                            onEditTags = { file ->
                                fileToEditTags = file
                                showTagEditor = true
                            },                            level = 0
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
                            // Convert to relative path for tag removal
                            val relativePath = fileManager.getRelativePath(file.path)
                            tagManager.removeFileFromTags(relativePath)
                            refreshFilesWithTags()
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
    
    // Tag Editor Dialog
    if (showTagEditor && fileToEditTags != null) {
        FileTagEditorDialog(
            fileName = fileToEditTags!!.displayName,
            currentTags = fileToEditTags!!.tags,
            allUsedTags = allUsedTags,
            onDismiss = {
                showTagEditor = false
                fileToEditTags = null
            },
            onSave = { newTags ->
                fileToEditTags?.let { file ->
                    // Convert absolute path to relative path
                    val relativePath = fileManager.getRelativePath(file.path)
                    tagManager.setTagsForFile(relativePath, newTags)
                    refreshFilesWithTags()
                }
                showTagEditor = false
                fileToEditTags = null
            }
        )
    }

    // Quick Access Bottom Sheet - zentrale Notizen ohne Dokument-Kontext
    if (showQuickAccess) {
        QuickAccessSheet(
            onDismiss = { showQuickAccess = false },
            onOpenDocument = onOpenLinkedDocument
            // Keine currentDocument-Parameter = zentrale Notizen ohne Pin-Funktion
        )
    }

    // When parent changes refreshTrigger, update contents
    LaunchedEffect(refreshTrigger) {
        refreshFilesWithTags()
        shortcuts = shortcutManager.loadShortcuts()
    }

    // Listen for preference changes and update filtering when visible aircrafts change
    DisposableEffect(prefsManager) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            refreshFilesWithTags()
            shortcuts = shortcutManager.loadShortcuts()
        }
        prefsManager.registerOnChangeListener(listener)
        onDispose { prefsManager.unregisterOnChangeListener(listener) }
    }
}

// Top-level helper to enrich folder nodes with tags (must be outside composable for visibility)
private fun enrichNodeWithTags(
    node: InternalFileManager.FolderNode,
    fileManager: InternalFileManager,
    selectedTagFilters: Set<String>,
    tagFilterMode: String
): InternalFileManager.FolderNode {
    // Helper to apply tag filters
    fun applyTagFilters(files: List<FileInfo>): List<FileInfo> {
        if (selectedTagFilters.isEmpty()) return files
        val enrichedFiles = fileManager.enrichWithTags(files)
        return if (tagFilterMode == "all") {
            enrichedFiles.filter { file -> selectedTagFilters.all { tag -> file.tags.contains(tag) } }
        } else {
            enrichedFiles.filter { file -> file.tags.any { tag -> selectedTagFilters.contains(tag) } }
        }
    }
    val enrichedFiles = fileManager.enrichWithTags(applyTagFilters(node.files))
    val enrichedChildren = node.children.map { enrichNodeWithTags(it, fileManager, selectedTagFilters, tagFilterMode) }
    return node.copy(files = enrichedFiles, children = enrichedChildren)
}

/**
 * Filtert rekursiv Aircraft-Unterordner basierend auf Sichtbarkeits-Einstellungen.
 * Alle Top-Level-Ordner (Checklists, Handbooks, radiocommunication, etc.) werden immer angezeigt.
 * Nur Aircraft-Unterordner innerhalb von "Checklists" werden nach Sichtbarkeit gefiltert.
 */
private fun filterAircraftChildren(
    node: InternalFileManager.FolderNode,
    assetAircraftsLower: Set<String>,
    prefsManager: PreferencesManager
): InternalFileManager.FolderNode {
    // Wenn der aktuelle Knoten "Checklists" ist, filtere Aircraft-Unterordner nach Sichtbarkeit
    return if (node.name.equals("Checklists", ignoreCase = true)) {
        // Filtere die Kinder: Nur sichtbare Aircraft-Ordner behalten
        val filteredChildren = node.children.filter { child ->
            if (assetAircraftsLower.contains(child.name.lowercase())) {
                prefsManager.isAircraftVisible(child.name)
            } else {
                true // Nicht-Aircraft-Ordner immer anzeigen
            }
        }.map { child ->
            // Rekursiv für Unterordner anwenden
            filterAircraftChildren(child, assetAircraftsLower, prefsManager)
        }
        node.copy(children = filteredChildren)
    } else {
        // Für alle anderen Top-Level-Ordner (Handbooks, radiocommunication, etc.): 
        // Behalte ALLE Kinder, wende Filterung nur rekursiv an
        val filteredChildren = node.children.map { child ->
            filterAircraftChildren(child, assetAircraftsLower, prefsManager)
        }
        node.copy(children = filteredChildren)
    }
}

@Composable
private fun FolderTree(
    nodes: List<InternalFileManager.FolderNode>,
    expanded: MutableMap<String, Boolean>,
    onToggleExpanded: (String, Boolean) -> Unit,
    onFileOpen: (FileInfo) -> Unit,
    onDelete: (FileInfo) -> Unit,
    onEditTags: (FileInfo) -> Unit,
    level: Int
) {
    nodes.forEach { node ->
        FolderNodeItem(
            node = node,
            expanded = expanded,
            onToggleExpanded = onToggleExpanded,
            onFileOpen = onFileOpen,
            onDelete = onDelete,
            onEditTags = onEditTags,
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
    onEditTags: (FileInfo) -> Unit,
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
                text = node.name.replace('_', ' '),
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
            FileListItem(
                file = file, 
                onClick = { onFileOpen(file) }, 
                onDelete = { onDelete(file) },
                onEditTags = { onEditTags(file) }
            )
        }

        // Recurse into children nodes
        node.children.forEach { child ->
            FolderNodeItem(
                node = child,
                expanded = expanded,
                onToggleExpanded = onToggleExpanded,
                onFileOpen = onFileOpen,
                onDelete = onDelete,
                onEditTags = onEditTags,
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
                text = category.replace('/', ' ').replace('_', ' '),
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
    onDelete: () -> Unit,
    onEditTags: () -> Unit
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

    Column(modifier = Modifier.clickable(onClick = onClick)) {
        ListItem(
            headlineContent = { Text(file.displayName) },
            supportingContent = {
                Column {
                    Text(
                        file.extension.uppercase(),
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (file.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TagChips(tags = file.tags, maxVisible = 3)
                    }
                }
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
                    IconButton(onClick = onEditTags) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit tags",
                            tint = if (file.tags.isNotEmpty()) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.outline
                        )
                    }
                    if (!file.isAsset) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = LocalContext.current.getString(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        )
        HorizontalDivider()
    }
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
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(categories) { category ->
                    val displayName = category.replace('/', ' ').replace('_', ' ')
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
