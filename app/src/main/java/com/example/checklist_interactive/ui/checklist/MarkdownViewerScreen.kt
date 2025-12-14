package com.example.checklist_interactive.ui.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.automirrored.filled.NoteAdd
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
 * MarkdownViewerScreen - component for displaying Markdown files
 * Shows a markdown file with optional checkbox interaction
 *
 * @param assetPath path to the markdown file in assets
 * @param checklist Optional: checklist data for checkbox states
 * @param onBack Navigation back
 * @param onCheckboxChange Callback when a checkbox is toggled
 * @param onSettings Optional: Settings button handler
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
    // Extract filename for the title
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

    // State for Expand/Collapse All
    var expandAllSections by remember { mutableStateOf(prefsManager.areMarkdownSectionsExpandedByDefault()) }
    var showQuickAccess by remember { mutableStateOf(false) }
    var resetTrigger by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel?.resetChecklist()
                        resetTrigger += 1
                    }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Checklist", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = {
                        quickNoteManager.addLinkedDocument(
                            filePath = assetPath,
                            fileName = fileName,
                            pageNumber = null
                        )
                    }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Link, contentDescription = "Link to quick note", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { showQuickAccess = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "Quick access", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = {
                        expandAllSections = !expandAllSections
                        prefsManager.setMarkdownSectionsExpandedByDefault(expandAllSections)
                    }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = if (expandAllSections) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                            contentDescription = if (expandAllSections) "Collapse all" else "Expand all",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (onSettings != null) {
                        IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                modifier = Modifier.height(48.dp)
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.End
            ) {
                // Quick Access FAB - always visible
                FloatingActionButton(
                    onClick = { showQuickAccess = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "Quick access")
                }

                // Menu FAB - only if onShowFileList is set
                if (onShowFileList != null) {
                    FloatingActionButton(
                        onClick = onShowFileList,
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "File list")
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