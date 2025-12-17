
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.R
import androidx.compose.ui.text.font.FontWeight
import com.example.checklist_interactive.data.prefs.PreferencesManager
import com.example.checklist_interactive.data.files.InternalFileManager
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.checklist_interactive.data.prefs.SourceEntry
import org.osmdroid.config.Configuration
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

    // Map cache clear state
    var showClearMapCacheConfirm by remember { mutableStateOf(false) }
    var isClearingMapCache by remember { mutableStateOf(false) }

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
    var sources by remember { mutableStateOf(listOf<SourceEntry>()) }
    LaunchedEffect(Unit) {
        try {
            val assetManager = context.assets
            val jsonStr = assetManager.open("document_sources.json").bufferedReader().use { it.readText() }
            sources = Json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(SourceEntry.serializer()),
                jsonStr
            )
        } catch (e: Exception) {
            sources = emptyList()
        }
    }

    // Contributors
    var showContributorsJsonDialog by remember { mutableStateOf(false) }
    var contributorsJsonContent by remember { mutableStateOf("") }
    val contributors = remember { androidx.compose.runtime.mutableStateListOf<ContributorEntry>().apply { addAll(prefsManager.getContributors()) } }

    // Collapsible section states
    var tagsExpanded by remember { mutableStateOf(false) }
    var markdownExpanded by remember { mutableStateOf(false) }
    var sourcesExpanded by remember { mutableStateOf(false) }
    var mapCacheExpanded by remember { mutableStateOf(false) }

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
                    Toast.makeText(context, context.resources.getQuantityString(R.plurals.imported_files, imported, imported), Toast.LENGTH_SHORT).show()
                    onFilesRefreshed()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
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
                    Toast.makeText(context, context.resources.getQuantityString(R.plurals.wiped_imported_files, imported, imported), Toast.LENGTH_SHORT).show()
                    onFilesRefreshed()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.wipe_import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            } finally {
                isImporting = false
            }
        }
    }

    // Clear map cache (osmdroid) — deletes the osmdroid cache directory
    fun handleClearMapCache() {
        coroutineScope.launch {
            isClearingMapCache = true
            try {
                withContext(Dispatchers.IO) {
                    try {
                        val basePathFile = Configuration.getInstance().osmdroidBasePath
                        if (basePathFile != null && basePathFile.exists()) {
                            // Delete the osmdroid base directory recursively
                            basePathFile.deleteRecursively()
                        }
                    } catch (e: Exception) {
                        throw e
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.msg_map_cache_cleared), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.msg_map_cache_clear_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            } finally {
                isClearingMapCache = false
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.settings_title))
                        val displayVersion = softwareVersion.ifBlank { "—" }
                        Text(
                            text = stringResource(R.string.settings_version, displayVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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

            // === Viewer Layout Reset ===
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_viewer_layout),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_fab_positions_explain),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                prefsManager.resetPdfViewerLayout()
                                Toast.makeText(context, context.getString(R.string.msg_fab_positions_restored), Toast.LENGTH_SHORT).show()
                            }) {
                                Text(stringResource(R.string.settings_reset_fab_positions))
                            }
                        }
                    }
                }
            }

            // === Map Cache ===
            item {
                Spacer(modifier = Modifier.height(12.dp))
                val mapCacheRotation by animateFloatAsState(targetValue = if (mapCacheExpanded) 180f else 0f)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { mapCacheExpanded = !mapCacheExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_clear_map_cache),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (mapCacheExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                                modifier = Modifier.rotate(mapCacheRotation)
                            )
                        }

                        AnimatedVisibility(
                            visible = mapCacheExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_clear_map_cache_explain),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { showClearMapCacheConfirm = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isClearingMapCache,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    if (isClearingMapCache) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.settings_processing))
                                        }
                                    } else {
                                        Text(stringResource(R.string.settings_clear_map_cache))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // === App Language ===
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_language_label),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val languages = listOf("en" to stringResource(R.string.language_english), "de" to stringResource(R.string.language_german))
                        var expandedLang by remember { mutableStateOf(false) }
                        var selectedLang by remember { mutableStateOf(prefsManager.getAppLanguage()) }
                        val selectedLabel = languages.find { it.first == selectedLang }?.second ?: stringResource(R.string.language_english)

                        Box {
                            OutlinedTextField(
                                value = selectedLabel,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { expandedLang = !expandedLang }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(expanded = expandedLang, onDismissRequest = { expandedLang = false }) {
                                languages.forEach { (code, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            if (code != selectedLang) {
                                                prefsManager.setAppLanguage(code)
                                                selectedLang = code
                                                expandedLang = false
                                                // Recreate activity to apply new locale
                                                (context as? android.app.Activity)?.recreate()
                                            } else {
                                                expandedLang = false
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // === Contributors ===
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.contributors_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.contributors_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.contributors_explain),
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
                                    contributorsJsonContent = context.getString(R.string.error_encoding_contributors, e.message ?: "")
                                }
                                showContributorsJsonDialog = true
                            }) {
                                Text(stringResource(R.string.settings_view_contributors_json))
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
                                                text = stringResource(R.string.role_label, entry.role),
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
                    text = stringResource(R.string.document_sources_title),
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
                                text = stringResource(R.string.document_sources_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (sourcesExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
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
                                    text = stringResource(R.string.document_sources_explain),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
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
                                                        text = stringResource(R.string.license_label, entry.license),
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
                    text = stringResource(R.string.settings_visibility),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_select_visible_aircraft),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_aircraft_visibility_explain),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { showAircraftDialog = true }) {
                                Text(stringResource(R.string.settings_select_visible_aircraft))
                            }
                            OutlinedButton(onClick = { showResetConfirm = true }) {
                                Text(stringResource(R.string.settings_reset_to_defaults))
                            }
                        }
                    }
                }
            }

            // === Tags ===
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.tags_title),
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
                                text = stringResource(R.string.settings_view_internal_tag_json),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (tagsExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
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
                                    text = stringResource(R.string.settings_view_internal_tag_json_explain),
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
                                            tagJsonContent = context.getString(R.string.error_loading_tags, e.message ?: "")
                                        }
                                        showTagJsonDialog = true
                                    }) {
                                        Text(stringResource(R.string.settings_view_internal_tag_json))
                                    }
                                    OutlinedButton(onClick = {
                                        // Show asset default file as JSON if present
                                        try {
                                            val stream = context.assets.open("file_tags.json")
                                            val content = stream.bufferedReader().use { it.readText() }
                                            tagJsonContent = content
                                        } catch (e: Exception) {
                                            tagJsonContent = context.getString(R.string.no_default_tags_asset)
                                        }
                                        showTagJsonDialog = true
                                    }) {
                                        Text(stringResource(R.string.settings_view_default_asset_json))
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
                                        Toast.makeText(context, context.getString(R.string.msg_tags_imported), Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, context.getString(R.string.import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                                    }
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.settings_import_reload_tags))
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
                                    text = stringResource(R.string.total_unique_tags, tagStats.first),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = stringResource(R.string.files_with_tags, tagStats.second),
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
                    text = stringResource(R.string.markdown_title),
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
                                text = stringResource(R.string.settings_markdown_font_size),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (markdownExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
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
                                            Text(stringResource(R.string.settings_font_size, size))
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
            title = { Text(stringResource(R.string.settings_aircraft_dialog_title)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { selectedSet = availableAircrafts.toSet() }) {
                            Text(stringResource(R.string.settings_aircraft_select_all))
                        }
                        TextButton(onClick = { selectedSet = emptySet() }) {
                            Text(stringResource(R.string.settings_aircraft_select_none))
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
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton({ showAircraftDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showTagJsonDialog) {
        AlertDialog(
            onDismissRequest = { showTagJsonDialog = false },
            title = { Text(stringResource(R.string.settings_tag_json_title)) },
            text = {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 420.dp)
                    .verticalScroll(rememberScrollState())) {
                    Text(
                        text = tagJsonContent.ifBlank { stringResource(R.string.common_empty) },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTagJsonDialog = false }) { Text(stringResource(R.string.action_close)) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    // Copy to clipboard
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    val clip = android.content.ClipData.newPlainText(context.getString(R.string.clipboard_tags_json_label), tagJsonContent)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, context.getString(R.string.msg_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.action_copy)) }
            }
        )
    }

    if (showSourcesJsonDialog) {
        AlertDialog(
            onDismissRequest = { showSourcesJsonDialog = false },
            title = { Text(stringResource(R.string.settings_sources_json_title)) },
            text = {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 420.dp)
                    .verticalScroll(rememberScrollState())) {
                    Text(
                        text = sourcesJsonContent.ifBlank { stringResource(R.string.common_empty) },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourcesJsonDialog = false }) { Text(stringResource(R.string.action_close)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    val clip = android.content.ClipData.newPlainText(context.getString(R.string.clipboard_sources_json_label), sourcesJsonContent)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, context.getString(R.string.msg_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.action_copy)) }
            }
        )
    }

    if (showContributorsJsonDialog) {
        AlertDialog(
            onDismissRequest = { showContributorsJsonDialog = false },
            title = { Text(stringResource(R.string.settings_contributors_json_title)) },
            text = {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 420.dp)
                    .verticalScroll(rememberScrollState())) {
                    Text(
                        text = contributorsJsonContent.ifBlank { stringResource(R.string.common_empty) },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showContributorsJsonDialog = false }) { Text(stringResource(R.string.action_close)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    val clip = android.content.ClipData.newPlainText(context.getString(R.string.clipboard_contributors_json_label), contributorsJsonContent)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, context.getString(R.string.msg_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.action_copy)) }
            }
        )
    }

    // Clear Map Cache confirmation
    if (showClearMapCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearMapCacheConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_map_cache_title)) },
            text = { Text(stringResource(R.string.settings_clear_map_cache_message)) },
            confirmButton = {
                TextButton({
                    showClearMapCacheConfirm = false
                    handleClearMapCache()
                }) { Text(stringResource(R.string.action_yes)) }
            },
            dismissButton = {
                TextButton({ showClearMapCacheConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    // === Reset Visibility Confirmation ===
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.settings_reset_visibility_title)) },
            text = { Text(stringResource(R.string.settings_reset_visibility_message)) },
            confirmButton = {
                TextButton({
                    prefsManager.resetVisibleAircrafts()
                    visibilityRefreshKey++
                    showResetConfirm = false
                }) { Text(stringResource(R.string.action_reset)) }
            },
            dismissButton = {
                TextButton({ showResetConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
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
                    text = stringResource(R.string.settings_import_title),
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
                                Text(stringResource(R.string.settings_external_import_folder), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = if (currentFolderUri != null) stringResource(R.string.folder_selected) else stringResource(R.string.no_folder_selected_tap),
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
                            Text(stringResource(R.string.settings_remove_import_folder))
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
                                Text(stringResource(R.string.settings_importing))
                            }
                        } else {
                            Text(stringResource(R.string.settings_reimport_bundled_assets))
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
                                    Text(stringResource(R.string.settings_processing))
                                }
                            } else {
                                Text(stringResource(R.string.settings_reset_bundled_imports))
                            }
                        }

                        if (showWipeConfirm) {
                            AlertDialog(
                                onDismissRequest = { onShowWipeConfirm(false) },
                                title = { Text(stringResource(R.string.settings_reset_bundled_imports_title)) },
                                text = {
                                    Text(stringResource(R.string.settings_reset_bundled_imports_message))
                                },
                                confirmButton = {
                                    TextButton({
                                        onShowWipeConfirm(false)
                                        onWipeAndReimport()
                                    }) { Text(stringResource(R.string.action_yes)) }
                                },
                                dismissButton = {
                                    TextButton({ onShowWipeConfirm(false) }) { Text(stringResource(R.string.action_cancel)) }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.settings_import_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.settings_import_help),
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