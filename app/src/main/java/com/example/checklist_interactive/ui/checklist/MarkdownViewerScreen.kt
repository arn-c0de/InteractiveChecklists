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
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.*
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.checklist_interactive.ui.datapad.LocalDataPadManager
import com.example.checklist_interactive.ui.datapad.DataPadPopup
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import androidx.compose.foundation.layout.offset
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
import com.example.checklist_interactive.ui.common.DraggableFab
import com.example.checklist_interactive.ui.common.FABOverlay
import com.example.checklist_interactive.ui.common.MarkdownViewerFABs
import androidx.compose.material.icons.filled.Link
import androidx.compose.ui.res.stringResource
import com.example.checklist_interactive.R

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
        val parsed = MarkdownChecklistParser().parse(
            id = assetPath,
            markdown = markdownContent,
            context = context,
            defaultSectionTitle = context.getString(R.string.parser_default_general),
            defaultChecklistTitle = context.getString(R.string.parser_default_checklist)
        )
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
    var showDataPad by remember { mutableStateOf(false) }
    val datapadManager = LocalDataPadManager.current
    val datapadEnabled by datapadManager.isEnabled.collectAsState()
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
                            contentDescription = stringResource(R.string.action_back),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel?.resetChecklist()
                        resetTrigger += 1
                    }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_reset_checklist), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = {
                        quickNoteManager.addMarkdownLink(
                            filePath = assetPath,
                            fileName = fileName,
                            pageNumber = null
                        )
                    }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Link, contentDescription = stringResource(R.string.cd_link_to_quick_note), modifier = Modifier.size(20.dp))
                    }
                    // Quick access: tap opens sheet, long-press resets persisted FAB positions
                    Box(modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { showQuickAccess = true },
                            onLongPress = {
                                prefsManager.resetFabPositions("markdown")
                                android.widget.Toast.makeText(context, context.getString(R.string.msg_fab_positions_restored), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }) {
                        IconButton(onClick = { showQuickAccess = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = stringResource(R.string.cd_quick_access), modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = {
                        expandAllSections = !expandAllSections
                        prefsManager.setMarkdownSectionsExpandedByDefault(expandAllSections)
                    }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = if (expandAllSections) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                            contentDescription = stringResource(if (expandAllSections) R.string.cd_collapse_all else R.string.cd_expand_all),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (onSettings != null) {
                        IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.cd_settings),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                modifier = Modifier.height(48.dp)
            )
        },
        floatingActionButton = { /* moved to overlay */ }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Use previously prepared context/prefs/viewmodel
            val checkedStateForViewer = checklistState
            val handler: ((itemId: String, checked: Boolean) -> Unit)? = viewModel?.let { vm ->
                { itemId: String, checked: Boolean -> vm.onCheckboxChange(itemId, checked) }
            } ?: onCheckboxChange

            MarkdownViewer(
                assetPath = assetPath,
                checklist = checkedStateForViewer,
                onCheckboxChange = handler,
                modifier = Modifier.fillMaxSize(),
                prefsManager = prefsManager,
                forceExpandAll = expandAllSections,
                markdownContentOverride = markdownContent,
                resetTrigger = resetTrigger
            )

            // Draggable FABs
            val configuration = LocalConfiguration.current
            val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.roundToPx() }
            val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.roundToPx() }
            // Adjust height to account for Scaffold padding so FAB offsets are computed relative to the content area
            val topPadPx = with(LocalDensity.current) { paddingValues.calculateTopPadding().roundToPx() }
            val bottomPadPx = with(LocalDensity.current) { paddingValues.calculateBottomPadding().roundToPx() }
            val effectiveScreenHeightPx = (screenHeightPx - topPadPx - bottomPadPx).coerceAtLeast(1)

            val datapadManager = LocalDataPadManager.current
            val datapadEnabled by datapadManager.isEnabled.collectAsState()

            FABOverlay(
                prefsManager = prefsManager,
                screenWidthPx = screenWidthPx,
                screenHeightPx = effectiveScreenHeightPx,
                fabs = MarkdownViewerFABs.create(
                    onMenuOpen = onShowFileList,
                    onDataPadOpen = { if (datapadEnabled) showDataPad = true },
                    onQuickAccessOpen = { showQuickAccess = true },
                    datapadEnabled = datapadEnabled,
                    containerColorPrimary = MaterialTheme.colorScheme.primaryContainer,
                    containerColorTertiary = MaterialTheme.colorScheme.tertiaryContainer
                )
            )
        }
    }

    // DataPad Popup
    if (showDataPad && datapadEnabled) {
        DataPadPopup(onDismiss = { showDataPad = false })
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