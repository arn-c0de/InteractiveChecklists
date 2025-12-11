package com.example.checklist_interactive.ui.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.checklist_interactive.data.checklist.Checklist
import com.example.checklist_interactive.data.checklist.ChecklistRepository
import com.example.checklist_interactive.data.checklist.MarkdownChecklistParser
import com.example.checklist_interactive.ui.checklist.ChecklistViewModel
import com.example.checklist_interactive.ui.checklist.ChecklistViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.checklist_interactive.data.prefs.PreferencesManager
import androidx.compose.ui.platform.LocalContext

/**
 * MarkdownViewerScreen - Screen-Komponente für die Markdown-Anzeige
 * Zeigt eine Markdown-Datei mit optionaler Checkbox-Interaktion an
 * 
 * @param assetPath Pfad zur Markdown-Datei in den Assets
 * @param checklist Optional: Checklist-Daten für Checkbox-Stati
 * @param onBack Navigation zurück
 * @param onCheckboxChange Callback wenn eine Checkbox geändert wird
 * @param onSettings Optional: Settings-Button-Handler
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownViewerScreen(
    assetPath: String,
    checklist: Checklist? = null,
    onBack: () -> Unit,
    onCheckboxChange: ((itemId: String, checked: Boolean) -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onShowFileList: (() -> Unit)? = null
) {
    // Extrahiere Dateinamen für den Titel
    val fileName = remember(assetPath) {
        assetPath.substringAfterLast('/').substringBeforeLast('.')
    }

    // Create optional checklist view model for persisting checkbox states
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }
    val checklistRepository = remember { ChecklistRepository(context) }
    val markdownContent = remember(assetPath) {
        try {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "" // Return empty on error
        }
    }
    val parsedChecklist = remember(markdownContent) { MarkdownChecklistParser().parse(assetPath, markdownContent) }
    val viewModel: ChecklistViewModel? = if (checklist != null) {
        viewModel(key = assetPath, factory = ChecklistViewModelFactory(checklistRepository, parsedChecklist))
    } else null
    val checklistState: Checklist? = viewModel?.let { vm ->
        vm.checklistState.collectAsState().value
    } ?: checklist

    // State für Expand/Collapse All
    var expandAllSections by remember { mutableStateOf(prefsManager.areMarkdownSectionsExpandedByDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Zurück"
                        )
                    }
                },
                actions = {
                    if (viewModel != null) {
                        IconButton(onClick = { viewModel.resetChecklist() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Checklist")
                        }
                    }
                    // Expand/Collapse All Button
                    IconButton(onClick = {
                        expandAllSections = !expandAllSections
                        prefsManager.setMarkdownSectionsExpandedByDefault(expandAllSections)
                    }) {
                        Icon(
                            imageVector = if (expandAllSections) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                            contentDescription = if (expandAllSections) "Alle einklappen" else "Alle ausklappen"
                        )
                    }
                    if (onSettings != null) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Einstellungen"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (onShowFileList != null) {
                FloatingActionButton(
                    onClick = onShowFileList,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Dateiliste")
                }
            }
        }
    ) { paddingValues ->
        // Use previously prepared context/prefs/viewmodel
        val checkedStateForViewer = checklistState
        val handler: ((itemId: String, checked: Boolean) -> Unit)? = viewModel?.let { it::onCheckboxChange } ?: onCheckboxChange

        MarkdownViewer(
            assetPath = assetPath,
            checklist = checkedStateForViewer,
            onCheckboxChange = handler,
            modifier = Modifier.padding(paddingValues),
            prefsManager = prefsManager
        )
    }
}
