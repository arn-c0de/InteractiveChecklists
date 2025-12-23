
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
import com.example.checklist_interactive.ui.datapad.LocalDataPadManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.flow.collectLatest
import android.app.Application

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

    // Map DB reimport confirmation
    var showMapDbConfirm by remember { mutableStateOf(false) }
    var backupBeforeWipe by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val vm: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(
            LocalContext.current.applicationContext as Application,
            prefsManager,
            fileManager
        )
    )
    val isImporting by vm.isImporting.collectAsState()
    val isClearingMapCache by vm.isClearingMapCache.collectAsState()
    val isMapImporting by vm.isMapImporting.collectAsState()
    val mapWipePreview by vm.mapWipePreview.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Load preview when dialog is shown
    LaunchedEffect(showMapDbConfirm) {
        if (showMapDbConfirm) {
            vm.loadMapWipePreview()
        }
    }

    LaunchedEffect(vm) {
        vm.snackbarMessages.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Restart the app when ViewModel requests it after map DB reimport
    LaunchedEffect(vm) {
        vm.requestRestart.collectLatest {
            // Let user see the snackbar or message briefly, then attempt a gentle relaunch
            try {
                snackbarHostState.showSnackbar(context.getString(R.string.msg_map_db_reimported))
            } catch (_: Exception) {}
            kotlinx.coroutines.delay(800)
            try {
                val activity = context as? android.app.Activity
                val pmIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                pmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                pmIntent?.putExtra("__restarted_by", "map_reimport")
                context.startActivity(pmIntent)
                // finishAffinity closes all activities in task but keeps process; this is a gentler restart
                activity?.finishAffinity()
            } catch (e: Exception) {
                // fallback: attempt process exit if gentle restart fails
                try {
                    Runtime.getRuntime().exit(0)
                } catch (_: Exception) {
                    // ignore
                }
            }
        }
    }

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
    val tagJsonContent by vm.tagJsonContent.collectAsState("")
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
    var contributors by remember { mutableStateOf(listOf<ContributorEntry>()) }
    LaunchedEffect(Unit) {
        try {
            val assetManager = context.assets
            val jsonStr = assetManager.open("contributors.json").bufferedReader().use { it.readText() }
            contributors = Json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(ContributorEntry.serializer()),
                jsonStr
            )
        } catch (e: Exception) {
            contributors = emptyList()
        }
    }

    // Collapsible section states (kept local to each section to reduce recompositions)

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

    // Import/clear logic moved to SettingsViewModel (vm). Use vm.reimport(), vm.wipeAndReimport(), vm.clearMapCache() and observe vm.snackbarMessages for feedback.

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    onReimport = { vm.reimport() },
                    showWipeConfirm = showWipeConfirm,
                    onShowWipeConfirm = { showWipeConfirm = it },
                    onWipeAndReimport = { vm.wipeAndReimport() },
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
                                // Reset all FAB positions (global) — user action in Settings should clear everything
                                prefsManager.resetFabPositions(null)
                                uiScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_fab_positions_restored)) }
                            }) {
                                Text(stringResource(R.string.settings_reset_fab_positions))
                            }
                        }
                    }
                }
            }

            // === FAB Size ===
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_fab_size),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_fab_size_explain),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        var currentFabSize by remember { mutableStateOf(prefsManager.getFabSize()) }
                        val fabSizes = listOf(
                            "small" to stringResource(R.string.settings_fab_size_small),
                            "medium" to stringResource(R.string.settings_fab_size_medium),
                            "large" to stringResource(R.string.settings_fab_size_large)
                        )
                        
                        Column {
                            fabSizes.forEach { (size, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentFabSize = size
                                            prefsManager.setFabSize(size)
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = currentFabSize == size,
                                        onClick = {
                                            currentFabSize = size
                                            prefsManager.setFabSize(size)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(label)
                                }
                            }
                        }
                    }
                }
            }

            // === Map Cache & Map Database ===
            item {
                Spacer(modifier = Modifier.height(12.dp))

                // Local expanded states (reduce top-level recompositions)
                var mapCacheExpanded by remember { mutableStateOf(false) }
                var mapDbExpanded by remember { mutableStateOf(false) }

                // Shared rotation states for headers
                val mapCacheRotation by animateFloatAsState(targetValue = if (mapCacheExpanded) 180f else 0f)
                val mapDbRotation by animateFloatAsState(targetValue = if (mapDbExpanded) 180f else 0f)

                BoxWithConstraints {
                    val isNarrow = maxWidth < 560.dp

                    if (isNarrow) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Map Cache card
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

                            // Map DB card
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { mapDbExpanded = !mapDbExpanded },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_wipe_map_db_title),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (mapDbExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                                            modifier = Modifier.rotate(mapDbRotation)
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = mapDbExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column {
                                            // Auto-load versions when expanded
                                            LaunchedEffect(mapDbExpanded) {
                                                if (mapDbExpanded) vm.loadDbVersions()
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = stringResource(R.string.settings_wipe_map_db_explain),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))

                                            // DB version row
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                val assetV by vm.assetDbVersion.collectAsState()
                                                val installedV by vm.installedDbVersion.collectAsState()
                                                Text(text = stringResource(R.string.settings_internal_db_version, installedV), style = MaterialTheme.typography.bodySmall)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(text = stringResource(R.string.settings_asset_db_version, assetV), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Spacer(modifier = Modifier.weight(1f))
                                                IconButton(onClick = { vm.loadDbVersions() }) { Icon(Icons.Default.Refresh, contentDescription = null) }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = { showMapDbConfirm = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled = !isMapImporting,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            ) {
                                                if (isMapImporting) {
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
                                                    Text(stringResource(R.string.settings_wipe_map_db_button))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            // Map Cache card
                            Card(modifier = Modifier.weight(1f)) {
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

                            // Map DB card
                            Card(modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { mapDbExpanded = !mapDbExpanded },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_wipe_map_db_title),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (mapDbExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                                            modifier = Modifier.rotate(mapDbRotation)
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = mapDbExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column {
                                            // Auto-load versions when expanded
                                            LaunchedEffect(mapDbExpanded) {
                                                if (mapDbExpanded) vm.loadDbVersions()
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = stringResource(R.string.settings_wipe_map_db_explain),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))

                                            // DB version row
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                val assetV by vm.assetDbVersion.collectAsState()
                                                val installedV by vm.installedDbVersion.collectAsState()
                                                Text(text = stringResource(R.string.settings_internal_db_version, installedV), style = MaterialTheme.typography.bodySmall)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(text = stringResource(R.string.settings_asset_db_version, assetV), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Spacer(modifier = Modifier.weight(1f))
                                                IconButton(onClick = { vm.loadDbVersions() }) { Icon(Icons.Default.Refresh, contentDescription = null) }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = { showMapDbConfirm = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled = !isMapImporting,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            ) {
                                                if (isMapImporting) {
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
                                                    Text(stringResource(R.string.settings_wipe_map_db_button))
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

                        val languages = listOf(
                            "en" to stringResource(R.string.language_english),
                            "de" to stringResource(R.string.language_german),
                            "es" to stringResource(R.string.language_spanish)
                        )
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
                var contributorsExpanded by remember { mutableStateOf(false) }
                val contributorsRotation by animateFloatAsState(targetValue = if (contributorsExpanded) 180f else 0f)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { contributorsExpanded = !contributorsExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.contributors_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (contributorsExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                                modifier = Modifier.rotate(contributorsRotation)
                            )
                        }
                        AnimatedVisibility(
                            visible = contributorsExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
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

                                                if (!entry.date.isNullOrBlank()) {
                                                    Text(
                                                        text = stringResource(R.string.contributor_date, entry.date),
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
                var sourcesExpanded by remember { mutableStateOf(false) }
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
                var tagsExpanded by remember { mutableStateOf(false) }
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
                                        // Load tags and display JSON via ViewModel
                                        vm.loadInternalTagsToJson()
                                        showTagJsonDialog = true
                                    }) {
                                        Text(stringResource(R.string.settings_view_internal_tag_json))
                                    }
                                    OutlinedButton(onClick = {
                                        // Show asset default file as JSON via ViewModel
                                        vm.loadDefaultTagsAssetToTagJson()
                                        showTagJsonDialog = true
                                    }) {
                                        Text(stringResource(R.string.settings_view_default_asset_json))
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                    vm.importTagsFromAsset()
                                    tagReloadKey++
                                    onFilesRefreshed()
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.settings_import_reload_tags))
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                // Tag statistics (loaded off main thread)
                                var tagStats by remember { mutableStateOf(Pair(0, 0)) }
                                LaunchedEffect(tagReloadKey) {
                                    val result = withContext(Dispatchers.IO) {
                                        try {
                                            val tags = fileManager.tagManager.loadFileTags()
                                            val uniqueTags = tags.flatMap { it.tags }.toSet()
                                            val filesWithTags = tags.count { it.tags.isNotEmpty() }
                                            Pair(uniqueTags.size, filesWithTags)
                                        } catch (e: Exception) {
                                            Pair(0, 0)
                                        }
                                    }
                                    tagStats = result
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
                var markdownExpanded by remember { mutableStateOf(false) }
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

            // === DataPad Enable/Disable ===
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.datapad_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                val dataPadManager = LocalDataPadManager.current
                val dpEnabled by dataPadManager.isEnabled.collectAsState()

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = stringResource(R.string.datapad_title), style = MaterialTheme.typography.titleMedium)
                            Text(text = stringResource(R.string.datapad_settings_description), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = dpEnabled, onCheckedChange = { dataPadManager.setEnabled(it) })
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
                        items(availableAircrafts, key = { it }) { aircraft ->
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
                    uiScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_copied_to_clipboard)) }
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
                    uiScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_copied_to_clipboard)) }
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
                    uiScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_copied_to_clipboard)) }
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
                    vm.clearMapCache()
                }) { Text(stringResource(R.string.action_yes)) }
            },
            dismissButton = {
                TextButton({ showClearMapCacheConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    // Map DB wipe & re-import confirmation
    if (showMapDbConfirm) {
        AlertDialog(
            onDismissRequest = { showMapDbConfirm = false },
            title = { Text(stringResource(R.string.settings_wipe_map_db_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_wipe_map_db_confirm_message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = backupBeforeWipe, onCheckedChange = { backupBeforeWipe = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_wipe_map_db_explain), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Markers that will be affected (${mapWipePreview.size}):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.heightIn(min = 80.dp, max = 220.dp).fillMaxWidth()) {
                        if (mapWipePreview.isEmpty()) {
                            Text(stringResource(R.string.common_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LazyColumn {
                                items(mapWipePreview) { m ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = m.name, modifier = Modifier.weight(1f))
                                        Text(text = m.markerType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { vm.loadMapWipePreview() }) { Text(stringResource(R.string.action_refresh)) }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    showMapDbConfirm = false
                    vm.wipeAndReimportMapDatabase(backupBeforeWipe)
                }) { Text(stringResource(R.string.action_yes)) }
            },
            dismissButton = {
                TextButton({ showMapDbConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
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