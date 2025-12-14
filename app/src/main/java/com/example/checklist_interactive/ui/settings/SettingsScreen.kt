
package com.example.checklist_interactive.ui.settings

import android.widget.Toast
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.example.checklist_interactive.data.prefs.PreferencesManager
import com.example.checklist_interactive.data.files.InternalFileManager
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.checklist_interactive.data.prefs.SourceEntry
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontFamily
import com.example.checklist_interactive.data.tags.FileTag
import com.example.checklist_interactive.data.prefs.ContributorEntry

// The list of available aircraft folders is loaded from the assets/Checklists directory

// Shared Json instance to avoid redundant creation
private val json = Json { prettyPrint = true }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefsManager: PreferencesManager,
    fileManager: InternalFileManager,
    onBack: () -> Unit,
    softwareVersion: String,
    onFilesRefreshed: () -> Unit = {}
) {
    var importExpanded by remember { mutableStateOf(false) }
    var currentFolderUri by remember { mutableStateOf(prefsManager.getImportFolderUri()) }
    var showWipeConfirm by remember { mutableStateOf(false) }

    var showAircraftDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var visibilityRefreshKey by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    // Load available aircraft from assets/Checklists (subfolders)
    var availableAircrafts by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(context) {
        val list = try {
            context.assets.list("Checklists")
                ?.filter { it.isNotBlank() && !it.contains('.') }
                ?.sorted() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        availableAircrafts = list
    }
    val rotation by animateFloatAsState(targetValue = if (importExpanded) 180f else 0f)

    var showTagJsonDialog by remember { mutableStateOf(false) }
    var tagJsonContent by remember { mutableStateOf("") }
    var tagReloadKey by remember { mutableStateOf(0) }
    
    var showSourcesJsonDialog by remember { mutableStateOf(false) }
    var sourcesJsonContent by remember { mutableStateOf("") }
    val sources = remember { androidx.compose.runtime.mutableStateListOf<SourceEntry>().apply { addAll(prefsManager.getDocumentSources()) } }

    // Contributors
    var showContributorsJsonDialog by remember { mutableStateOf(false) }
    var contributorsJsonContent by remember { mutableStateOf("") }
    val contributors = remember { androidx.compose.runtime.mutableStateListOf<ContributorEntry>().apply { addAll(prefsManager.getContributors()) } }

    // Collapsible section states
    var tagsExpanded by remember { mutableStateOf(false) }
    var markdownExpanded by remember { mutableStateOf(false) }
    var sourcesExpanded by remember { mutableStateOf(false) }

    // Launcher for folder picker (SAF)
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            prefsManager.setImportFolderUri(it.toString())
            currentFolderUri = it.toString()
        }
    }

    fun handleReimport() {
        coroutineScope.launch {
            isImporting = true
            try {
                val imported = withContext(Dispatchers.IO) {
                    fileManager.importAllBundledAssets("")
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Imported $imported files", Toast.LENGTH_SHORT).show()
                    onFilesRefreshed()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                isImporting = false
            }
        }
    }

    fun handleWipeAndReimport() {
        coroutineScope.launch {
            isImporting = true
            try {
                val imported = withContext(Dispatchers.IO) {
                    fileManager.wipeInternalRoot()
                    fileManager.importAllBundledAssets("")
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Wiped and imported $imported files", Toast.LENGTH_SHORT).show()
                    onFilesRefreshed()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Wipe/Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                isImporting = false
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Settings")
                        Text(
                            text = "Version: $softwareVersion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // === Import Settings Section ===
            item {
                ImportSettingsSection(
                    importExpanded = importExpanded,
                    onExpandToggle = { importExpanded = !importExpanded },
                    rotation = rotation,
                    currentFolderUri = currentFolderUri,
                    onRequestFolderPicker = { folderPicker.launch(null) },
                    onRemoveImportFolder = {
                        prefsManager.setImportFolderUri(null)
                        currentFolderUri = null
                    },
                    onReimport = { handleReimport() },
                    showWipeConfirm = showWipeConfirm,
                    onShowWipeConfirm = { showWipeConfirm = it },
                    onWipeAndReimport = { handleWipeAndReimport() },
                    isImporting = isImporting
                )
            }

            // === Contributors ===
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Contributors",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Contributors",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add contributors. Each person can have a website and an optional role.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                try {
                                    val jsonString = json.encodeToString(
                                        kotlinx.serialization.builtins.ListSerializer(ContributorEntry.serializer()),
                                        contributors
                                    )
                                    contributorsJsonContent = jsonString
                                } catch (e: Exception) {
                                    contributorsJsonContent = "Error encoding contributors: ${e.message}"
                                }
                                showContributorsJsonDialog = true
                            }) {
                                Text("View contributors JSON")
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Column {
                            contributors.forEachIndexed { idx, entry ->
                                Column(modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(entry.name, fontWeight = FontWeight.Bold)
                                        if (!entry.website.isNullOrBlank()) {
                                            Text(
                                                text = entry.website,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.clickable {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW)
                                                        intent.data = android.net.Uri.parse(entry.website)
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) { }
                                                }
                                            )
                                        }
                                        if (!entry.role.isNullOrBlank()) {
                                            Text(
                                                text = "Role: ${entry.role}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            // === Document Sources ===
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Document Sources",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                val sourcesRotation by animateFloatAsState(targetValue = if (sourcesExpanded) 180f else 0f)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { sourcesExpanded = !sourcesExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Document Sources",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (sourcesExpanded) "Collapse" else "Expand",
                                modifier = Modifier.rotate(sourcesRotation)
                            )
                        }
                        AnimatedVisibility(
                            visible = sourcesExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Sources for documents. Each source can have a website and a license.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(onClick = {
                                        try {
                                            val jsonString = json.encodeToString(
                                                kotlinx.serialization.builtins.ListSerializer(SourceEntry.serializer()),
                                                sources
                                            )
                                            sourcesJsonContent = jsonString
                                        } catch (e: Exception) {
                                            sourcesJsonContent = "Error encoding sources: ${'$'}{e.message}"
                                        }
                                        showSourcesJsonDialog = true
                                    }) {
                                        Text("View sources JSON")
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Column {
                                    sources.forEachIndexed { idx, entry ->
                                        Column(modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Text(entry.name, fontWeight = FontWeight.Bold)
                                                if (!entry.website.isNullOrBlank()) {
                                                    Text(
                                                        text = entry.website,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.clickable {
                                                            try {
                                                                val intent = Intent(Intent.ACTION_VIEW)
                                                                intent.data = android.net.Uri.parse(entry.website)
                                                                context.startActivity(intent)
                                                            } catch (_: Exception) { }
                                                        }
                                                    )
                                                }
                                                if (!entry.license.isNullOrBlank()) {
                                                    Text(
                                                        text = "License: ${entry.license}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // === Aircraft Visibility ===
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Visibility",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Aircraft visibility",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Choose which bundled aircraft categories are visible in My Files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { showAircraftDialog = true }) {
                                Text("Select visible aircraft")
                            }
                            OutlinedButton(onClick = { showResetConfirm = true }) {
                                Text("Reset to defaults")
                            }
                        }
                    }
                }
            }

            // === Tags ===
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Tags",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                val tagsRotation by animateFloatAsState(targetValue = if (tagsExpanded) 180f else 0f)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tagsExpanded = !tagsExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Internal tags JSON",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (tagsExpanded) "Collapse" else "Expand",
                                modifier = Modifier.rotate(tagsRotation)
                            )
                        }
                        AnimatedVisibility(
                            visible = tagsExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "View or copy the internal tag JSON currently stored by the app.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(onClick = {
                                        // Load tags and display JSON
                                        try {
                                            val tags = fileManager.tagManager.loadFileTags()
                                            val jsonStr = json.encodeToString(
                                                kotlinx.serialization.builtins.ListSerializer(FileTag.serializer()),
                                                tags
                                            )
                                            tagJsonContent = jsonStr
                                        } catch (e: Exception) {
                                            tagJsonContent = "Error loading tags: ${e.message}"
                                        }
                                        showTagJsonDialog = true
                                    }) {
                                        Text("View internal tag JSON")
                                    }
                                    OutlinedButton(onClick = {
                                        // Show asset default file as JSON if present
                                        try {
                                            val stream = context.assets.open("file_tags.json")
                                            val content = stream.bufferedReader().use { it.readText() }
                                            tagJsonContent = content
                                        } catch (e: Exception) {
                                            tagJsonContent = "No default tags asset found."
                                        }
                                        showTagJsonDialog = true
                                    }) {
                                        Text("View default asset JSON")
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                    // Import and reload tags from asset file_tags.json
                                    try {
                                        // Overwrite internal file_tags.json with asset version
                                        context.assets.open("file_tags.json").use { input ->
                                            val outFile = java.io.File(context.filesDir, "file_tags.json")
                                            outFile.outputStream().use { output -> input.copyTo(output) }
                                        }
                                        tagReloadKey++
                                        onFilesRefreshed()
                                        Toast.makeText(context, "Tags imported and reloaded!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Import & Reload Tags")
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                // Tag statistics
                                val tagStats = remember(tagReloadKey) {
                                    val tags = fileManager.tagManager.loadFileTags()
                                    val uniqueTags = tags.flatMap { it.tags }.toSet()
                                    val filesWithTags = tags.count { it.tags.isNotEmpty() }
                                    Pair(uniqueTags.size, filesWithTags)
                                }
                                Text(
                                    text = "Total unique tags: ${tagStats.first}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Files with at least one tag: ${tagStats.second}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // === Markdown Settings ===
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Markdown",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                val markdownRotation by animateFloatAsState(targetValue = if (markdownExpanded) 180f else 0f)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { markdownExpanded = !markdownExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Markdown font size",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (markdownExpanded) "Collapse" else "Expand",
                                modifier = Modifier.rotate(markdownRotation)
                            )
                        }
                        AnimatedVisibility(
                            visible = markdownExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))

                                var currentFontSize by remember { mutableStateOf(prefsManager.getMarkdownFontSize()) }
                                val sizes = listOf(14, 16, 18, 20, 22)

                                Column {
                                    sizes.forEach { size ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    prefsManager.setMarkdownFontSize(size)
                                                    currentFontSize = size
                                                }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = size == currentFontSize,
                                                onClick = {
                                                    prefsManager.setMarkdownFontSize(size)
                                                    currentFontSize = size
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("$size sp")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // === Aircraft Selection Dialog ===
    if (showAircraftDialog) {
        val storedVisible = prefsManager.getVisibleAircrafts()
        var selectedSet by remember(visibilityRefreshKey, availableAircrafts) {
            mutableStateOf(storedVisible?.toSet() ?: availableAircrafts.toSet())
        }

        AlertDialog(
            onDismissRequest = { showAircraftDialog = false },
            title = { Text("Select visible aircraft") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { selectedSet = availableAircrafts.toSet() }) {
                            Text("Select all")
                        }
                        TextButton(onClick = { selectedSet = emptySet() }) {
                            Text("Select none")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn {
                        items(availableAircrafts) { aircraft ->
                            val displayName = aircraft.replace('_', ' ')
                            val checked = selectedSet.contains(aircraft)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSet = if (checked) {
                                            selectedSet - aircraft
                                        } else {
                                            selectedSet + aircraft
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedSet = if (isChecked) {
                                            selectedSet + aircraft
                                        } else {
                                            selectedSet - aircraft
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(displayName)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    prefsManager.setVisibleAircrafts(selectedSet)
                    visibilityRefreshKey++
                    showAircraftDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton({ showAircraftDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showTagJsonDialog) {
        AlertDialog(
            onDismissRequest = { showTagJsonDialog = false },
            title = { Text("Tag JSON") },
            text = {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 420.dp)
                    .verticalScroll(rememberScrollState())) {
                    Text(
                        text = tagJsonContent.ifBlank { "(empty)" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTagJsonDialog = false }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    // Copy to clipboard
                    try {
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        val clip = android.content.ClipData.newPlainText("tags_json", tagJsonContent)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { }
                }) { Text("Copy") }
            }
        )
    }

    if (showSourcesJsonDialog) {
        AlertDialog(
            onDismissRequest = { showSourcesJsonDialog = false },
            title = { Text("Sources JSON") },
            text = {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 420.dp)
                    .verticalScroll(rememberScrollState())) {
                    Text(
                        text = sourcesJsonContent.ifBlank { "(empty)" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourcesJsonDialog = false }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = {
                    try {
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        val clip = android.content.ClipData.newPlainText("sources_json", sourcesJsonContent)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { }
                }) { Text("Copy") }
            }
        )
    }

    if (showContributorsJsonDialog) {
        AlertDialog(
            onDismissRequest = { showContributorsJsonDialog = false },
            title = { Text("Contributors JSON") },
            text = {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 420.dp)
                    .verticalScroll(rememberScrollState())) {
                    Text(
                        text = contributorsJsonContent.ifBlank { "(empty)" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showContributorsJsonDialog = false }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = {
                    try {
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        val clip = android.content.ClipData.newPlainText("contributors_json", contributorsJsonContent)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { }
                }) { Text("Copy") }
            }
        )
    }

    // === Reset Visibility Confirmation ===
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset visibility to defaults") },
            text = { Text("This will show all bundled aircraft categories. Continue?") },
            confirmButton = {
                TextButton({
                    prefsManager.resetVisibleAircrafts()
                    visibilityRefreshKey++
                    showResetConfirm = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton({ showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// Reusable Import Settings Section
@Composable
private fun ImportSettingsSection(
    importExpanded: Boolean,
    onExpandToggle: () -> Unit,
    rotation: Float,
    currentFolderUri: String?,
    onRequestFolderPicker: () -> Unit,
    onRemoveImportFolder: () -> Unit,
    onReimport: () -> Unit,
    showWipeConfirm: Boolean,
    onShowWipeConfirm: (Boolean) -> Unit,
    onWipeAndReimport: () -> Unit,
    isImporting: Boolean = false
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandToggle() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Import Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation)
                )
            }

            AnimatedVisibility(
                visible = importExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    // Folder Picker Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRequestFolderPicker() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (currentFolderUri != null) Icons.Default.FolderOpen else Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("External Import Folder", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = if (currentFolderUri != null) "Folder selected" else "No folder selected – tap to choose",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (currentFolderUri != null) {
                        Button(
                            onClick = onRemoveImportFolder,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text("Remove Import Folder")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Button(
                        onClick = onReimport,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isImporting
                    ) {
                        if (isImporting) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Importing...")
                            }
                        } else {
                            Text("Re-import bundled assets")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column {
                        Button(
                            onClick = { onShowWipeConfirm(true) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isImporting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            if (isImporting) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Processing...")
                                }
                            } else {
                                Text("Reset bundled imports (wipe + reimport)")
                            }
                        }

                        if (showWipeConfirm) {
                            AlertDialog(
                                onDismissRequest = { onShowWipeConfirm(false) },
                                title = { Text("Reset bundled imports") },
                                text = {
                                    Text("This will delete all files from internal storage and re-import bundled assets. Continue?")
                                },
                                confirmButton = {
                                    TextButton({
                                        onShowWipeConfirm(false)
                                        onWipeAndReimport()
                                    }) { Text("Yes") }
                                },
                                dismissButton = {
                                    TextButton({ onShowWipeConfirm(false) }) { Text("Cancel") }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Import Settings Help",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Select an external folder to automatically import PDF and Markdown files from. The app remembers this location and checks for new files on startup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}