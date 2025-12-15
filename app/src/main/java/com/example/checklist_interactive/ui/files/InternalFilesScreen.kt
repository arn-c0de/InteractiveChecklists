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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor
import com.example.checklist_interactive.ui.common.DraggableFab
import com.example.checklist_interactive.data.files.FileInfo
import com.example.checklist_interactive.data.files.InternalFileManager
import com.example.checklist_interactive.data.prefs.PreferencesManager
import com.example.checklist_interactive.data.shortcuts.ShortcutManager
import com.example.checklist_interactive.data.shortcuts.PageShortcut
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.automirrored.filled.ViewList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.checklist_interactive.R
import androidx.compose.ui.graphics.Color
import com.example.checklist_interactive.ui.tags.FileTagEditorDialog
import com.example.checklist_interactive.ui.tags.TagFilterBar
import com.example.checklist_interactive.ui.tags.TagChips
import com.example.checklist_interactive.ui.quickaccess.QuickAccessSheet

/**
 * Main screen for internal file management
 * Shows files categorized by folder
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
    
    // Load all top-level folders from assets dynamically
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
    
    // Load all aircraft subfolders from the Checklists folder (case-insensitive, dynamic)
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
    // Combine all top-level folders, aircraft folders, and already imported categories
    val assetAircraftsLower = remember(assetAircrafts, assetTopLevelFolders, fileManager) { 
        (assetTopLevelFolders + assetAircrafts + fileManager.getCategories()).map { it.lowercase() }.toSet() 
    }
    var groupedFiles by remember { mutableStateOf<Map<String, List<FileInfo>>>(emptyMap()) }
    var folderTree by remember { mutableStateOf<List<InternalFileManager.FolderNode>>(emptyList()) }
    // Cache of enriched files by path to avoid repeated enrichWithTags calls
    var enrichedFileMap by remember { mutableStateOf<Map<String, FileInfo>>(emptyMap()) }
    var shortcuts by remember { mutableStateOf<List<PageShortcut>>(emptyList()) }
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
    var allUsedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoadingTags by remember { mutableStateOf(false) }
    var isLoadingFiles by remember { mutableStateOf(true) }
    
    val coroutineScope = rememberCoroutineScope()

    // Quick Access state
    var showQuickAccess by remember { mutableStateOf(false) }
    // Search state
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<FileInfo>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    // Perform a simple search across displayName, path and tags (IO-bound)
    fun performSearch(query: String) {
        coroutineScope.launch {
            isSearching = true
            val q = query.trim().lowercase()
            val results = withContext(Dispatchers.IO) {
                // Use the enriched map when available to include tags
                val allFiles = (groupedFiles.values.flatten()).map { enrichedFileMap[it.path] ?: it }
                if (q.isEmpty()) return@withContext allFiles
                allFiles.filter { file ->
                    file.displayName.lowercase().contains(q) ||
                    file.path.lowercase().contains(q) ||
                    file.tags.any { tag -> tag.lowercase().contains(q) }
                }
            }
            searchResults = results
            isSearching = false
        }
    }

    // When search dialog opens, show all files initially (empty query => all files)
    LaunchedEffect(showSearchDialog) {
        if (showSearchDialog) performSearch("")
    }
    // Display mode: list or grid for files (persisted)
    var isGridView by remember { mutableStateOf(prefsManager.isGridViewEnabled()) }

    

    // Suspended function to refresh and enrich files with tags (IO-heavy - keep suspended)
    suspend fun refreshFilesWithTags() {
        android.util.Log.i("InternalFiles", "refreshFilesWithTags: start")
        val rawGroupedFiles = withContext(Dispatchers.IO) { fileManager.getAllFilesGrouped() }
        // Build a single flat list of unique files to enrich once
        val allFiles = rawGroupedFiles.values.flatten().distinctBy { it.path }
        val enrichedAll = withContext(Dispatchers.IO) { fileManager.enrichWithTags(allFiles) }
        val newEnrichedMap = enrichedAll.associateBy { it.path }
        val newGrouped = rawGroupedFiles.mapValues { (_, files) ->
            val enrichedFiles = files.map { newEnrichedMap[it.path] ?: it }
            if (selectedTagFilters.isEmpty()) {
                enrichedFiles
            } else if (tagFilterMode == "all") {
                enrichedFiles.filter { file -> selectedTagFilters.all { tag -> file.tags.contains(tag) } }
            } else {
                enrichedFiles.filter { file -> file.tags.any { tag -> selectedTagFilters.contains(tag) } }
            }
        }.filterValues { it.isNotEmpty() }
        enrichedFileMap = newEnrichedMap
        android.util.Log.i("InternalFiles", "refreshFilesWithTags: enriched ${enrichedFileMap.size} files, grouped ${newGrouped.size} categories")
        if (newGrouped != groupedFiles) groupedFiles = newGrouped
        
        val rawTree = withContext(Dispatchers.IO) {
            fileManager.getFolderTree().map { node -> filterAircraftChildren(node, assetAircraftsLower, prefsManager) }
        }
        val newTree = withContext(Dispatchers.IO) { rawTree.map { node -> enrichNodeWithTags(node, enrichedFileMap, selectedTagFilters, tagFilterMode) } }
        if (newTree != folderTree) folderTree = newTree
        android.util.Log.i("InternalFiles", "refreshFilesWithTags: tree nodes ${folderTree.size}")
        android.util.Log.i("InternalFiles", "refreshFilesWithTags: done")
    }

    
    
    // Load tags asynchronously on background thread
    LaunchedEffect(refreshTrigger) {
        isLoadingTags = true
        allUsedTags = withContext(Dispatchers.IO) {
            tagManager.getAllUsedTags()
        }
        isLoadingTags = false
    }
    
    // Initial load of files (async)
    LaunchedEffect(Unit) {
        isLoadingFiles = true
        // Ensure tag manager is initialized on IO thread first
        withContext(Dispatchers.IO) {
            try {
                tagManager.initializeIfNeeded()
            } catch (_: Exception) { }
        }
        // Refresh files (suspending, performs IO internally)
        refreshFilesWithTags()
        // Load shortcuts after files are loaded
        shortcuts = withContext(Dispatchers.IO) { shortcutManager.loadShortcuts() }
        isLoadingFiles = false
    }
    

    // Expanded state for each category + shortcuts
    val expandedCategories = remember { mutableStateMapOf<String, Boolean>() }

    // Seed expandedCategories from groupedFiles and folderTree when they become available
    LaunchedEffect(groupedFiles, folderTree) {
        groupedFiles.keys.forEach { category ->
            if (!expandedCategories.containsKey(category)) {
                expandedCategories[category] = prefsManager.isCategoryExpanded(category) ?: false
            }
        }
        folderTree.forEach { node ->
            if (!expandedCategories.containsKey(node.relativePath)) {
                expandedCategories[node.relativePath] = prefsManager.isCategoryExpanded(node.relativePath) ?: false
            }
            fun seedChildren(n: InternalFileManager.FolderNode) {
                n.children.forEach {
                    if (!expandedCategories.containsKey(it.relativePath)) {
                        expandedCategories[it.relativePath] = prefsManager.isCategoryExpanded(it.relativePath) ?: false
                    }
                    seedChildren(it)
                }
            }
            seedChildren(node)
        }
        if (!expandedCategories.containsKey("shortcuts")) {
            expandedCategories["shortcuts"] = prefsManager.isCategoryExpanded("shortcuts") ?: false
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
                    // Refresh file list and filter folder tree by visibility
                    coroutineScope.launch {
                        isLoadingFiles = true
                        refreshFilesWithTags()
                        onRefresh()
                        isLoadingFiles = false
                    }
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
                        coroutineScope.launch {
                            isLoadingFiles = true
                            refreshFilesWithTags()
                            onRefresh()
                            isLoadingFiles = false
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = context.getString(R.string.refresh))
                    }
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = context.getString(R.string.toggle_dark_mode)
                        )
                    }
                    // Toggle between list and grid view for files
                    IconButton(onClick = {
                        isGridView = !isGridView
                        prefsManager.setGridViewEnabled(isGridView)
                    }) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.ViewModule,
                            contentDescription = if (isGridView) "List view" else "Grid view"
                        )
                    }
                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Search files")
                    }
                    IconButton(onClick = onShowSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = { /* moved to overlay */ }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // (Draggable FAB moved to render last so it stays on top)

            if (isLoadingFiles) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (folderTree.isEmpty() && shortcuts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                    modifier = Modifier.fillMaxSize()
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
                                        // Refresh and compute folder expansions off the UI thread
                                        coroutineScope.launch {
                                            refreshFilesWithTags()
                                            val nodesToExpand = withContext(Dispatchers.IO) {
                                                // Compute which folder nodes contain matching files using cached enrichedFileMap
                                                fun nodeHasMatchingFiles(node: InternalFileManager.FolderNode): Boolean {
                                                    val enriched = node.files.map { enrichedFileMap[it.path] ?: it }
                                                    val fileMatches = enriched.any { file ->
                                                        if (selectedTagFilters.isEmpty()) return@any false
                                                        if (tagFilterMode == "all") {
                                                            selectedTagFilters.all { tag -> file.tags.contains(tag) }
                                                        } else {
                                                            file.tags.any { t -> selectedTagFilters.contains(t) }
                                                        }
                                                    }
                                                    if (fileMatches) return true
                                                    return node.children.any { child -> nodeHasMatchingFiles(child) }
                                                }
                                                val matching = mutableListOf<String>()
                                                folderTree.forEach { rootNode ->
                                                    if (rootNode.name.equals("Checklists", ignoreCase = true)) {
                                                        rootNode.children.forEach { aircraftNode ->
                                                            if (!prefsManager.isAircraftVisible(aircraftNode.name)) return@forEach
                                                            if (nodeHasMatchingFiles(aircraftNode)) matching.add(aircraftNode.relativePath)
                                                        }
                                                    } else {
                                                        if (nodeHasMatchingFiles(rootNode)) matching.add(rootNode.relativePath)
                                                    }
                                            }
                                            matching
                                        }
                                        // Apply expansion on UI thread
                                        withContext(Dispatchers.Main) {
                                            nodesToExpand.forEach { expandPath ->
                                                var p = expandPath
                                                while (p.isNotBlank()) {
                                                    expandedCategories[p] = true
                                                    p = p.substringBeforeLast('/', "")
                                                }
                                            }
                                        }
                                    }

                                    // Expand folders that contain files matching the selected tags,
                                    // but do not expand aircraft folders that are deselected in settings.
                                    fun nodeHasMatchingFiles(node: InternalFileManager.FolderNode): Boolean {
                                        // Check files in this node (use cached enriched files)
                                        val enriched = node.files.map { enrichedFileMap[it.path] ?: it }
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
                                    coroutineScope.launch { refreshFilesWithTags() }
                                },
                                onFilterModeChange = { mode ->
                                    tagFilterMode = mode
                                    prefsManager.setTagFilterMode(mode)
                                    coroutineScope.launch { refreshFilesWithTags() }
                                },
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                
                // Shortcuts category (always first)
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
                            , isGridView = isGridView
                        )
                    }
                }
            }

            // Draggable FAB overlay (rendered last so it stays on top)
            // Compute screen/fab dimensions here so variables are in scope
            val configuration = LocalConfiguration.current
            val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.roundToPx() }
            val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.roundToPx() }
            val fabSizePx = with(LocalDensity.current) { 56.dp.roundToPx() }

            DraggableFab(
                name = "quick_access",
                prefsManager = prefsManager,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                fabSizePx = fabSizePx,
                defaultX = 1.0f,
                defaultY = 0.9f,
                visible = true,
                onClick = { showQuickAccess = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                content = {
                    Icon(
                        Icons.AutoMirrored.Filled.NoteAdd,
                        contentDescription = "Quick access",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            )

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

    // Search Dialog
    if (showSearchDialog) {
        SearchDialog(
            query = searchQuery,
            onQueryChange = { q ->
                searchQuery = q
                performSearch(q)
            },
            isSearching = isSearching,
            results = searchResults,
            onSelect = { file ->
                onFileOpen(file)
                showSearchDialog = false
                searchQuery = ""
                searchResults = emptyList()
            },
            onDismiss = {
                showSearchDialog = false
                searchQuery = ""
                searchResults = emptyList()
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
                            coroutineScope.launch {
                                isLoadingFiles = true
                                refreshFilesWithTags()
                                onRefresh()
                                isLoadingFiles = false
                            }
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
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    shortcutToRename = null
                }) {
                    Text("Cancel")
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
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            // Convert absolute path to relative path
                            val relativePath = fileManager.getRelativePath(file.path)
                            tagManager.setTagsForFile(relativePath, newTags)
                        }
                        refreshFilesWithTags()
                    }
                }
                showTagEditor = false
                fileToEditTags = null
            }
        )
    }

    // Quick Access Bottom Sheet - central notes without document context
    if (showQuickAccess) {
        QuickAccessSheet(
            onDismiss = { showQuickAccess = false },
            onOpenDocument = onOpenLinkedDocument
            // No currentDocument parameter = central notes without pin functionality
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
            // Update persisted UI preferences when they change elsewhere
            isGridView = prefsManager.isGridViewEnabled()
            coroutineScope.launch {
                refreshFilesWithTags()
                shortcuts = shortcutManager.loadShortcuts()
            }
        }
        prefsManager.registerOnChangeListener(listener)
        onDispose { prefsManager.unregisterOnChangeListener(listener) }
    }
}

// Top-level helper to enrich folder nodes with tags (must be outside composable for visibility)
private fun enrichNodeWithTags(
    node: InternalFileManager.FolderNode,
    enrichedMap: Map<String, FileInfo>,
    selectedTagFilters: Set<String>,
    tagFilterMode: String
): InternalFileManager.FolderNode {
    // Helper to apply tag filters
    fun applyTagFilters(files: List<FileInfo>): List<FileInfo> {
        if (selectedTagFilters.isEmpty()) return files
        val enrichedFiles = files.map { enrichedMap[it.path] ?: it }
        return if (tagFilterMode == "all") {
            enrichedFiles.filter { file -> selectedTagFilters.all { tag -> file.tags.contains(tag) } }
        } else {
            enrichedFiles.filter { file -> file.tags.any { tag -> selectedTagFilters.contains(tag) } }
        }
    }
    val enrichedFiles = applyTagFilters(node.files).map { enrichedMap[it.path] ?: it }
    val enrichedChildren = node.children.map { enrichNodeWithTags(it, enrichedMap, selectedTagFilters, tagFilterMode) }
    return node.copy(files = enrichedFiles, children = enrichedChildren)
}

/**
 * Recursively filter aircraft subfolders based on visibility settings.
 * All top-level folders (Checklists, Handbooks, radiocommunication, etc.) are always shown.
 * Only aircraft subfolders under "Checklists" are filtered by visibility.
 */
private fun filterAircraftChildren(
    node: InternalFileManager.FolderNode,
    assetAircraftsLower: Set<String>,
    prefsManager: PreferencesManager
): InternalFileManager.FolderNode {
    // If the current node is "Checklists", filter aircraft subfolders by visibility
    return if (node.name.equals("Checklists", ignoreCase = true)) {
        // Filter the children: keep only visible aircraft folders
        val filteredChildren = node.children.filter { child ->
            if (assetAircraftsLower.contains(child.name.lowercase())) {
                prefsManager.isAircraftVisible(child.name)
            } else {
                true // Always show non-aircraft folders
            }
        }.map { child ->
            // Apply recursively to subfolders
            filterAircraftChildren(child, assetAircraftsLower, prefsManager)
        }
        node.copy(children = filteredChildren)
    } else {
        // For all other top-level folders (Handbooks, radiocommunication, etc.): 
        // Keep ALL children; apply filtering only recursively
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
    level: Int,
    isGridView: Boolean
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
            ,
            isGridView = isGridView
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
    ,
    isGridView: Boolean
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
        if (isGridView) {
            val columns = 3
            val rows = node.files.chunked(columns)
            rows.forEach { rowFiles ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowFiles.forEach { file ->
                        Box(modifier = Modifier
                            .weight(1f)
                            .padding(8.dp)) {
                            FileGridItem(
                                file = file,
                                onClick = { onFileOpen(file) },
                                onDelete = { onDelete(file) },
                                onEditTags = { onEditTags(file) }
                            )
                        }
                    }
                    if (rowFiles.size < columns) {
                        repeat(columns - rowFiles.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            node.files.forEach { file ->
                FileListItem(
                    file = file,
                    onClick = { onFileOpen(file) },
                    onDelete = { onDelete(file) },
                    onEditTags = { onEditTags(file) }
                )
            }
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
                level = level + 1,
                isGridView = isGridView
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
                "${shortcut.fileName} • Page ${shortcut.pageNumber + 1}",
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
                        contentDescription = "Rename",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
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
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
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
private fun FileGridItem(
    file: FileInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEditTags: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileIcon = when (file.extension.lowercase()) {
        "pdf" -> Icons.Default.PictureAsPdf
        "md", "markdown" -> Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
    val fileColor = when (file.extension.lowercase()) {
        "pdf" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(fileIcon, contentDescription = null, tint = fileColor, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                file.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (file.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                TagChips(tags = file.tags, maxVisible = 2)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row {
                IconButton(onClick = onEditTags) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit tags", tint = MaterialTheme.colorScheme.primary)
                }
                if (!file.isAsset) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = LocalContext.current.getString(R.string.delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    results: List<FileInfo>,
    onSelect: (FileInfo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search files") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { onQueryChange(it); /* performSearch is called from parent */ },
                    label = { Text("Search") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (isSearching) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
                        items(results) { file ->
                            ListItem(
                                headlineContent = { Text(file.displayName) },
                                supportingContent = {
                                    Column {
                                        Text(file.path, style = MaterialTheme.typography.labelSmall)
                                        if (file.tags.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            TagChips(tags = file.tags, maxVisible = 4)
                                        }
                                    }
                                },
                                leadingContent = {
                                    Icon(
                                        when (file.extension.lowercase()) {
                                            "pdf" -> Icons.Default.PictureAsPdf
                                            "md", "markdown" -> Icons.Default.Description
                                            else -> Icons.AutoMirrored.Filled.InsertDriveFile
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier.clickable {
                                    onSelect(file)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
        title = { Text("Select category") },
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
                Text("Cancel")
            }
        }
    )
}
