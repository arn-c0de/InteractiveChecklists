package com.example.checklist_interactive.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.data.prefs.PreferencesManager
import com.example.checklist_interactive.data.files.InternalFileManager
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefsManager: PreferencesManager,
    fileManager: InternalFileManager,
    onBack: () -> Unit,
    onRequestFolderPicker: () -> Unit,
    softwareVersion: String
) {
    val context = LocalContext.current
    var currentFolderUri by remember { mutableStateOf(prefsManager.getImportFolderUri()) }
    var showAircraftDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val assetAircrafts = remember { context.assets.list("Checklists")?.toList() ?: emptyList() }
    val internalCategories = remember { fileManager.getCategories() }
    val aircraftList = remember(assetAircrafts, internalCategories) { (assetAircrafts + internalCategories).distinctBy { it.lowercase() } }
    val availableAircrafts = remember(aircraftList) { aircraftList }
    
    // State to trigger refresh when visibility changes
    var visibilityRefreshKey by remember { mutableStateOf(0) }
    
    // Update when preference changes
    LaunchedEffect(Unit) {
        currentFolderUri = prefsManager.getImportFolderUri()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Import Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
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
                            Text(
                                text = "External Import Folder",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (currentFolderUri != null) {
                                    "Folder selected"
                                } else {
                                    "No folder selected - tap to choose"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            item {
                if (currentFolderUri != null) {
                    Button(
                        onClick = {
                            prefsManager.setImportFolderUri(null)
                            currentFolderUri = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Remove Import Folder")
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Import Settings Help",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Select an external folder to automatically import PDF and Markdown files from. The app will remember this location and check for new files on startup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Choose which bundled aircraft categories are visible in My Files. If an aircraft is not owned, you can hide it here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { showAircraftDialog = true }) {
                                Text("Select visible aircrafts")
                            }
                            OutlinedButton(onClick = { showResetConfirm = true }) {
                                Text("Reset to defaults")
                            }
                        }
                    }
                }
            }

            // Aircraft visibility dialog
            item {
                if (showAircraftDialog) {
                    var selectedSet by remember(visibilityRefreshKey) { mutableStateOf(
                        prefsManager.getVisibleAircrafts().let { s -> if (s.isEmpty()) availableAircrafts.toSet() else s.toSet() }
                    ) }

                    AlertDialog(
                    onDismissRequest = { showAircraftDialog = false },
                    title = { Text("Select visible aircrafts") },
                    text = {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                                    val displayName = aircraft.replace('_', ' ').replaceFirstChar { it.uppercase() }
                                    val checked = selectedSet.contains(aircraft)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedSet = selectedSet.toMutableSet().apply {
                                                    if (checked) remove(aircraft) else add(aircraft)
                                                }.toSet()
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { new ->
                                                selectedSet = selectedSet.toMutableSet().apply {
                                                    if (new) add(aircraft) else remove(aircraft)
                                                }.toSet()
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(displayName, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            prefsManager.setVisibleAircrafts(selectedSet)
                            visibilityRefreshKey++ // Trigger refresh
                            showAircraftDialog = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAircraftDialog = false }) { Text("Cancel") }
                    }
                    )
                }
            }

            item {
            if (showResetConfirm) {
                AlertDialog(
                    onDismissRequest = { showResetConfirm = false },
                    title = { Text("Reset visibility to defaults") },
                    text = { Text("This will show all bundled/internal aircraft categories in My Files. Continue?") },
                    confirmButton = {
                        TextButton(onClick = {
                            prefsManager.resetVisibleAircrafts()
                            visibilityRefreshKey++ // Trigger refresh
                            showResetConfirm = false
                        }) { Text("Reset") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
                    }
                )
            }
            }

            // Markdown settings
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Markdown font size",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
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
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (size == currentFontSize),
                                        onClick = {
                                            prefsManager.setMarkdownFontSize(size)
                                            currentFontSize = size
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "$size sp", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
