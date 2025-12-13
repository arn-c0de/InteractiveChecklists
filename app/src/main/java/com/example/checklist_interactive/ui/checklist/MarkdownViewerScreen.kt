package com.example.checklist_interactive.ui.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.example.checklist_interactive.ui.quickaccess.QuickAccessSheet
import com.example.checklist_interactive.data.quicknotes.QuickNoteManager
import androidx.compose.material.icons.filled.Link

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
    val providedNoteManager = com.example.checklist_interactive.ui.quickaccess.LocalQuickNoteManager.current
    val quickNoteManager = providedNoteManager ?: remember { QuickNoteManager(context) }
    val markdownContent = remember(assetPath) {
        try {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "" // Return empty on error
        }
    }
    val parsedChecklist = remember(markdownContent) {
        val parsed = MarkdownChecklistParser().parse(assetPath, markdownContent)
        android.util.Log.d("MarkdownViewerScreen", "Parsed checklist: sections=${parsed.sections.size}, items=${parsed.sections.sumOf { it.items.size }}")
        parsed
    }
    val hasCheckboxes = remember(parsedChecklist) {
        val result = parsedChecklist.sections.any { it.items.isNotEmpty() }
        android.util.Log.d("MarkdownViewerScreen", "hasCheckboxes=$result")
        result
    }
    val viewModel: ChecklistViewModel? = if (hasCheckboxes) {
        android.util.Log.d("MarkdownViewerScreen", "Creating ViewModel for $assetPath")
        viewModel(key = assetPath, factory = ChecklistViewModelFactory(checklistRepository, parsedChecklist))
    } else {
        android.util.Log.d("MarkdownViewerScreen", "No ViewModel created (no checkboxes)")
        null
    }
    val checklistState: Checklist? = viewModel?.let { vm ->
        val state = vm.checklistState.collectAsState().value
        android.util.Log.d("MarkdownViewerScreen", "checklistState from ViewModel: ${state.sections.size} sections")
        state
    } ?: checklist

    // State für Expand/Collapse All
    var expandAllSections by remember { mutableStateOf(prefsManager.areMarkdownSectionsExpandedByDefault()) }
    var showQuickAccess by remember { mutableStateOf(false) }
    var resetTrigger by remember { mutableStateOf(0) }

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
                    IconButton(onClick = {
                        viewModel?.resetChecklist()
                        resetTrigger += 1
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Checklist")
                    }
                    // Link zu Schnellnotiz
                    IconButton(onClick = {
                        quickNoteManager.addLinkedDocument(
                            filePath = assetPath,
                            fileName = fileName,
                            pageNumber = null
                        )
                    }) {
                        Icon(Icons.Default.Link, contentDescription = "Zu Schnellnotiz verlinken")
                    }
                    // Quick Access shortcut (visible in top bar)
                    IconButton(onClick = { showQuickAccess = true }) {
                        Icon(Icons.Default.NoteAdd, contentDescription = "Schnellzugriff")
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
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.End
            ) {
                // Quick Access FAB - immer sichtbar
                FloatingActionButton(
                    onClick = { showQuickAccess = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = "Schnellzugriff")
                }

                // Menu FAB - nur wenn onShowFileList gesetzt ist
                if (onShowFileList != null) {
                    FloatingActionButton(
                        onClick = onShowFileList,
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Dateiliste")
                    }
                }
            }
        }
    ) { paddingValues ->
        // Use previously prepared context/prefs/viewmodel
        val checkedStateForViewer = checklistState
        val handler: ((itemId: String, checked: Boolean) -> Unit)? = viewModel?.let { vm ->
            { itemId: String, checked: Boolean -> vm.onCheckboxChange(itemId, checked) }
        } ?: onCheckboxChange

        MarkdownViewer(
            assetPath = assetPath,
            checklist = checkedStateForViewer,
            onCheckboxChange = handler,
            modifier = Modifier.padding(paddingValues),
            prefsManager = prefsManager,
            forceExpandAll = expandAllSections,
            markdownContentOverride = markdownContent,
            resetTrigger = resetTrigger
        )
    }

    // Quick Access Bottom Sheet
    if (showQuickAccess) {
        QuickAccessSheet(
            onDismiss = { showQuickAccess = false },
            currentDocumentPath = assetPath,
            currentDocumentName = fileName
        )
    }
}