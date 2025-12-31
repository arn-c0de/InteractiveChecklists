package com.example.checklist_interactive.ui.checklist.markdown

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.checklist_interactive.R
import com.example.checklist_interactive.data.quicknotes.QuickNoteManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.checklist_interactive.data.checklist.Checklist
import com.example.checklist_interactive.data.prefs.PreferencesManager
import com.example.checklist_interactive.data.checklist.ChecklistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.SharedPreferences
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * MarkdownViewer - Composable for displaying markdown files with checkbox support
 *
 * @param assetPath path to the markdown file in the assets
 * @param checklist Optional: checklist with checkbox states
 * @param onCheckboxChange Callback when a checkbox is changed
 */
@Composable
fun MarkdownViewer(
    assetPath: String,
    checklist: Checklist? = null,
    onCheckboxChange: ((itemId: String, checked: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    isInternalFile: Boolean = false,
    prefsManager: PreferencesManager? = null,
    forceExpandAll: Boolean? = null,
    markdownContentOverride: String? = null,
    resetTrigger: Int = 0
) {
    val context = LocalContext.current
    val providedNoteManager = com.example.checklist_interactive.ui.quickaccess.LocalQuickNoteManager.current
    val noteManager = providedNoteManager ?: remember { QuickNoteManager(context) }
    val callsign by noteManager.callsign.collectAsState()
    var markdownContent by remember { mutableStateOf(markdownContentOverride ?: "") }
    // Derived display content with callsign substitution (do not mutate the raw markdown)
    val displayMarkdownContent by remember(markdownContent, callsign) {
        mutableStateOf(
            if (callsign.isBlank()) markdownContent
            else markdownContent.replace(Regex("\\[Callsign\\]", RegexOption.IGNORE_CASE), callsign)
        )
    }
    var isLoading by remember { mutableStateOf(markdownContentOverride == null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load markdown file only when no override is present
    LaunchedEffect(assetPath, isInternalFile, markdownContentOverride) {
        if (markdownContentOverride != null) {
            markdownContent = markdownContentOverride
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null
        try {
            val content = if (isInternalFile) {
                loadMarkdownFromFile(assetPath)
            } else {
                loadMarkdownFromAssets(context, assetPath)
            }
            markdownContent = content
        } catch (e: Exception) {
            errorMessage = context.getString(R.string.checklist_error_loading, e.message ?: "")
        } finally {
            isLoading = false
        }
    }

    val effectivePrefs = prefsManager ?: remember { PreferencesManager(context) }
    val initialFontSize = effectivePrefs.getMarkdownFontSize()
    var fontSizeState by remember { mutableStateOf(initialFontSize) }

    // Expand/Collapse all sections state
    val initialExpandState = forceExpandAll ?: effectivePrefs.areMarkdownSectionsExpandedByDefault()
    var expandAllState by remember(forceExpandAll) { mutableStateOf(initialExpandState) }

    // Update expandAllState when forceExpandAll changes
    LaunchedEffect(forceExpandAll) {
        if (forceExpandAll != null) {
            expandAllState = forceExpandAll
        }
    }

    // Listen for preference changes and update font size in real time
    DisposableEffect(effectivePrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            val updatedFontSize = effectivePrefs.getMarkdownFontSize()
            if (updatedFontSize != fontSizeState) fontSizeState = updatedFontSize
        }
        effectivePrefs.registerOnChangeListener(listener)
        onDispose {
            effectivePrefs.unregisterOnChangeListener(listener)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: stringResource(R.string.checklist_error_unknown),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            else -> {
                // Check if the checklist has items
                val hasItems = checklist?.sections?.any { it.items.isNotEmpty() } == true
                android.util.Log.d("MarkdownViewer", "hasItems=$hasItems, checklist=${checklist != null}, onCheckboxChange=${onCheckboxChange != null}")
                if (checklist != null) {
                    android.util.Log.d("MarkdownViewer", "checklist sections: ${checklist.sections.size}, total items: ${checklist.sections.sumOf { it.items.size }}")
                }
                if (hasItems && checklist != null && onCheckboxChange != null) {
                    // Interactive view with checkboxes
                    android.util.Log.d("MarkdownViewer", "Using InteractiveMarkdownView, resetTrigger=$resetTrigger, expandAll=$expandAllState")
                    InteractiveMarkdownView(
                        markdownContent = displayMarkdownContent,
                        checklist = checklist,
                        onCheckboxChange = onCheckboxChange,
                        bodyFontSize = fontSizeState,
                        expandAll = expandAllState,
                        resetTrigger = resetTrigger
                    )
                } else {
                    // Simple markdown view without interaction (or no checkboxes found)
                    android.util.Log.d("MarkdownViewer", "Using SimpleMarkdownView, resetTrigger=$resetTrigger, expandAll=$expandAllState")
                    SimpleMarkdownView(
                        markdownContent = displayMarkdownContent,
                        assetId = assetPath,
                        bodyFontSize = fontSizeState,
                        expandAll = expandAllState,
                        onCheckboxChange = onCheckboxChange,
                        resetTrigger = resetTrigger
                    )
                }
            }
        }
    }
}

/**
 * Data class for a Markdown section with ### heading
 */
private data class MarkdownSection(
    val heading: String,
    val content: List<String>
) 

/**
 * Simple markdown view - renders basic Markdown
 * Groups content between ### headings into containers
 */
@Composable
private fun SimpleMarkdownView(
    markdownContent: String,
    assetId: String,
    bodyFontSize: Int,
    expandAll: Boolean = false,
    onCheckboxChange: ((itemId: String, checked: Boolean) -> Unit)? = null,
    resetTrigger: Int = 0
) {
    val scrollState = rememberScrollState()

    // Parse markdown into sections
    val sections = parseMarkdownSections(markdownContent) 

    // State for expanded sections - key is the section index
    // Remember per assetId so each file gets its own expand/collapse state
    val expandedSections = remember(assetId) { mutableStateMapOf<Int, Boolean>() }

    // State for checkbox lines per section-line index (Pair<sectionIndex,lineIndex> -> checked)
    // Remember per assetId so each file keeps a separate checkbox state
    val checkboxStates = remember(assetId) { mutableStateMapOf<Pair<Int, Int>, Boolean>() }

    // Helper to identify a checkbox line
    val isCheckboxLine: (String) -> Boolean = { it.trim().startsWith("- [") }

    // Get context and repository for loading saved states
    val context = LocalContext.current
    val repository = remember { com.example.checklist_interactive.data.checklist.ChecklistRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    // Initialize expand/checkbox states when content changes
    LaunchedEffect(expandAll, markdownContent, assetId) {
        sections.indices.forEach { index ->
            if (sections[index].content.any { it.trim().startsWith("- [") }) {
                expandedSections[index] = expandAll
            }
        }

        // Load saved checkbox states from repository
        val savedStates = repository.getChecklistState(assetId)
        android.util.Log.d("MarkdownViewer", "Loaded saved states for $assetId: ${savedStates.keys.size} entries")

        sections.forEachIndexed { index, section ->
            section.content.forEachIndexed { lineIndex, line ->
                if (isCheckboxLine(line)) {
                    val key = Pair(index, lineIndex)
                    val syntheticId = "$assetId-simple-$index-$lineIndex"
                    // Restore from saved state if available, otherwise use markdown default
                    val defaultChecked = line.trim().startsWith("- [x]") || line.trim().startsWith("- [X]")
                    checkboxStates[key] = savedStates[syntheticId] ?: defaultChecked
                    if (savedStates.containsKey(syntheticId)) {
                        android.util.Log.d("MarkdownViewer", "Restored $syntheticId = ${checkboxStates[key]}")
                    }
                }
            }
        }
    }

    // Listen for reset trigger to clear local checkbox states and reinitialize them to defaults from content
    LaunchedEffect(resetTrigger, assetId) {
        if (resetTrigger > 0) {
            checkboxStates.clear()
            sections.forEachIndexed { secIndex, sec ->
                sec.content.forEachIndexed { lineIndex, line ->
                    if (isCheckboxLine(line)) {
                        val key = Pair(secIndex, lineIndex)
                        checkboxStates[key] = line.trim().startsWith("- [x]") || line.trim().startsWith("- [X]")
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        sections.forEachIndexed { index, section ->
            when {
                // Main heading (# or ##) - if it contains checkboxes, show as Card
                section.heading.startsWith("# ") || section.heading.startsWith("## ") -> {
                    val hasCheckboxes = section.content.any { it.trim().startsWith("- [") }
                    if (hasCheckboxes) {
                        val isExpanded = expandedSections[index] ?: false // Default: collapsed

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                // Clickable header with icon
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedSections[index] = !isExpanded }
                                        .padding(bottom = if (isExpanded) 8.dp else 0.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Count checked and total checkboxes in this section using the checkbox state map
                                    val totalCheckboxes = section.content.count { line -> isCheckboxLine(line) }
                                    val checkedCheckboxes = section.content.mapIndexed { lidx, line ->
                                        if (isCheckboxLine(line)) checkboxStates[Pair(index, lidx)] ?: (line.trim().startsWith("- [x]") || line.trim().startsWith("- [X]")) else false
                                    }.count { it }
                                    val countText = if (totalCheckboxes > 0) stringResource(R.string.markdown_checkbox_count_format, checkedCheckboxes, totalCheckboxes) else ""

                                    val isAllChecked = totalCheckboxes > 0 && checkedCheckboxes == totalCheckboxes
                                    Text(
                                        text = section.heading.substring(3) + countText,
                                        style = if (isAllChecked) MaterialTheme.typography.headlineMedium.copy(textDecoration = TextDecoration.LineThrough) else MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = stringResource(if (isExpanded) R.string.markdown_collapse else R.string.markdown_expand)
                                    )
                                }

                                // Render section content only when expanded
                                if (isExpanded) {
                                    section.content.forEachIndexed { lineIndex, line ->
                                        if (isCheckboxLine(line)) {
                                            val key = Pair(index, lineIndex)
                                            val checked = checkboxStates[key] ?: false
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.Start
                                                ) {
                                                    Checkbox(
                                                        checked = checked,
                                                        onCheckedChange = { newChecked ->
                                                            checkboxStates[key] = newChecked
                                                            val syntheticId = "$assetId-simple-$index-$lineIndex"
                                                            onCheckboxChange?.invoke(syntheticId, newChecked)
                                                            // Persist directly if no external handler (e.g., no ViewModel)
                                                            if (onCheckboxChange == null) {
                                                                android.util.Log.d("MarkdownViewer", "Saving $syntheticId=$newChecked for $assetId (no handler)")
                                                                // launch a coroutine to save
                                                                coroutineScope.launch {
                                                                    repository.saveChecklistItemState(assetId, syntheticId, newChecked)
                                                                }
                                                            }

                                                            // If all checkboxes in this section are now checked, collapse the container
                                                            val totalCheckboxesInSection = section.content.count { it.trim().startsWith("- [") }
                                                            if (totalCheckboxesInSection > 0) {
                                                                val checkedCount = section.content.mapIndexed { lidx, line ->
                                                                    if (line.trim().startsWith("- [")) checkboxStates[Pair(index, lidx)] ?: (line.trim().startsWith("- [x]") || line.trim().startsWith("- [X]")) else false
                                                                }.count { it }
                                                                if (checkedCount == totalCheckboxesInSection) {
                                                                    android.util.Log.d("MarkdownViewer", "Auto-closing simple section index=$index for $assetId (all checked)")
                                                                    expandedSections[index] = false
                                                                }
                                                            }
                                                        }
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = parseInlineMarkdown(line.trim().substring(5).trim(), bodyFontSize),
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontSize = bodyFontSize.sp,
                                                            textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
                                                        ),
                                                        modifier = Modifier
                                                            .align(androidx.compose.ui.Alignment.CenterVertically)
                                                            .clickable(role = Role.Checkbox) {
                                                                val newChecked = !checked
                                                                checkboxStates[key] = newChecked
                                                                val syntheticId = "$assetId-simple-$index-$lineIndex"
                                                                onCheckboxChange?.invoke(syntheticId, newChecked)
                                                                if (onCheckboxChange == null) {
                                                                    coroutineScope.launch {
                                                                        repository.saveChecklistItemState(assetId, syntheticId, newChecked)
                                                                    }
                                                                }

                                                                val totalCheckboxes = section.content.count { it.trim().startsWith("- [") }
                                                                if (totalCheckboxes > 0) {
                                                                    val checkedCount = section.content.mapIndexed { lidx, line ->
                                                                        if (line.trim().startsWith("- [")) checkboxStates[Pair(index, lidx)] ?: (line.trim().startsWith("- [x]") || line.trim().startsWith("- [X]")) else false
                                                                    }.count { it }
                                                                    if (checkedCount == totalCheckboxes) {
                                                                        android.util.Log.d("MarkdownViewer", "Auto-closing simple section index=$index for $assetId (all checked)")
                                                                        expandedSections[index] = false
                                                                    }
                                                                }
                                                            }
                                                    )
                                                }
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                                )
                                            }
                                        } else {
                                            RenderMarkdownLine(line, bodyFontSize)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Render heading
                        RenderMarkdownLine(section.heading, bodyFontSize)

                        // Render content
                        section.content.forEachIndexed { lineIndex, line ->
                            if (isCheckboxLine(line)) {
                                val key = Pair(index, lineIndex)
                                val checked = checkboxStates[key] ?: false
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { newChecked ->
                                                checkboxStates[key] = newChecked
                                                val syntheticId = "$assetId-simple-$index-$lineIndex"
                                                onCheckboxChange?.invoke(syntheticId, newChecked)
                                                if (onCheckboxChange == null) {
                                                    coroutineScope.launch {
                                                        repository.saveChecklistItemState(assetId, syntheticId, newChecked)
                                                    }
                                                }

                                                // If all checkboxes in this subsection are now checked, collapse the subsection container
                                                val totalCheckboxesInSection = section.content.count { it.trim().startsWith("- [") }
                                                if (totalCheckboxesInSection > 0) {
                                                    val checkedCount = section.content.mapIndexed { lidx, line ->
                                                        if (line.trim().startsWith("- [")) checkboxStates[Pair(index, lidx)] ?: (line.trim().startsWith("- [x]") || line.trim().startsWith("- [X]")) else false
                                                    }.count { it }
                                                    if (checkedCount == totalCheckboxesInSection) {
                                                        android.util.Log.d("MarkdownViewer", "Auto-closing simple subsection index=$index for $assetId (all checked)")
                                                        expandedSections[index] = false
                                                    }
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = parseInlineMarkdown(line.trim().substring(5).trim(), bodyFontSize),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = bodyFontSize.sp,
                                                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
                                            ),
                                            modifier = Modifier
                                                .align(androidx.compose.ui.Alignment.CenterVertically)
                                                .clickable(role = Role.Checkbox) {
                                                    val newChecked = !checked
                                                    checkboxStates[key] = newChecked
                                                    val syntheticId = "$assetId-simple-$index-$lineIndex"
                                                    onCheckboxChange?.invoke(syntheticId, newChecked)
                                                    if (onCheckboxChange == null) {
                                                        coroutineScope.launch {
                                                            repository.saveChecklistItemState(assetId, syntheticId, newChecked)
                                                        }
                                                    }

                                                    val totalCheckboxes = section.content.count { it.trim().startsWith("- [") }
                                                    if (totalCheckboxes > 0) {
                                                        val checkedCount = section.content.mapIndexed { lidx, line ->
                                                            if (line.trim().startsWith("- [")) checkboxStates[Pair(index, lidx)] ?: (line.trim().startsWith("- [x]") || line.trim().startsWith("- [X]")) else false
                                                        }.count { it }
                                                        if (checkedCount == totalCheckboxes) {
                                                            android.util.Log.d("MarkdownViewer", "Auto-closing simple section index=$index for $assetId (all checked)")
                                                            expandedSections[index] = false
                                                        }
                                                    }
                                                }
                                        )
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                    )
                                }
                            } else {
                                RenderMarkdownLine(line, bodyFontSize)
                            }
                        }
                    }
                }
                // ### heading - group into Card with collapsible function
                section.heading.startsWith("### ") -> {
                    val isExpanded = expandedSections[index] ?: false // Default: collapsed

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            // Clickable header with icon
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedSections[index] = !isExpanded }
                                    .padding(bottom = if (isExpanded) 8.dp else 0.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Count checked and total checkboxes in this section using the checkbox state map
                                val totalCheckboxes = section.content.count { line -> isCheckboxLine(line) }
                                val checkedCheckboxes = section.content.mapIndexed { lidx, line ->
                                    if (isCheckboxLine(line)) checkboxStates[Pair(index, lidx)] ?: (line.trim().startsWith("- [x]") || line.trim().startsWith("- [X]")) else false
                                }.count { it }
                                val countText = if (totalCheckboxes > 0) " | $checkedCheckboxes/$totalCheckboxes" else ""

                                val isAllCheckedSub = totalCheckboxes > 0 && checkedCheckboxes == totalCheckboxes
                                Text(
                                    text = section.heading.substring(4) + countText,
                                    style = if (isAllCheckedSub) MaterialTheme.typography.headlineSmall.copy(textDecoration = TextDecoration.LineThrough) else MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = stringResource(if (isExpanded) R.string.markdown_collapse else R.string.markdown_expand)
                                )
                            }

                            // Render section content only when expanded
                            if (isExpanded) {
                                section.content.forEachIndexed { lineIndex, line ->
                                    if (isCheckboxLine(line)) {
                                        val key = Pair(index, lineIndex)
                                        val checked = checkboxStates[key] ?: false
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(modifier = Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                                                Checkbox(
                                                    checked = checked,
                                                    onCheckedChange = { newChecked ->
                                                        checkboxStates[key] = newChecked
                                                        val syntheticId = "$assetId-simple-$index-$lineIndex"
                                                        onCheckboxChange?.invoke(syntheticId, newChecked)
                                                        if (onCheckboxChange == null) {
                                                            android.util.Log.d("MarkdownViewer", "Saving $syntheticId=$newChecked for $assetId (no handler)")
                                                            android.util.Log.d("MarkdownViewer", "Saving $syntheticId=$newChecked for $assetId (no handler)")
                                                            coroutineScope.launch {
                                                                repository.saveChecklistItemState(assetId, syntheticId, newChecked)
                                                            }
                                                        }
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = parseInlineMarkdown(line.trim().substring(5).trim(), bodyFontSize),
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = bodyFontSize.sp,
                                                        textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
                                                    ),
                                                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically)
                                                )
                                            }
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                            )
                                        }
                                    } else {
                                        RenderMarkdownLine(line, bodyFontSize)
                                    }
                                }
                            }
                        }
                    }
                }
                // Content without heading
                else -> {
                    section.content.forEachIndexed { lineIndex, line ->
                        if (isCheckboxLine(line)) {
                            val key = Pair(index, lineIndex)
                            val checked = checkboxStates[key] ?: false
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { newChecked ->
                                            checkboxStates[key] = newChecked
                                            val syntheticId = "$assetId-simple-$index-$lineIndex"
                                            onCheckboxChange?.invoke(syntheticId, newChecked)
                                            if (onCheckboxChange == null) {
                                                android.util.Log.d("MarkdownViewer", "Saving $syntheticId=$newChecked for $assetId (no handler)")
                                                coroutineScope.launch {
                                                    repository.saveChecklistItemState(assetId, syntheticId, newChecked)
                                                }
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = parseInlineMarkdown(line.trim().substring(5).trim(), bodyFontSize),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = bodyFontSize.sp),
                                        modifier = Modifier
                                            .align(androidx.compose.ui.Alignment.CenterVertically)
                                            .clickable(role = Role.Checkbox) {
                                                val newChecked = !checked
                                                checkboxStates[key] = newChecked
                                                val syntheticId = "$assetId-simple-$index-$lineIndex"
                                                onCheckboxChange?.invoke(syntheticId, newChecked)
                                                if (onCheckboxChange == null) {
                                                    coroutineScope.launch {
                                                        repository.saveChecklistItemState(assetId, syntheticId, newChecked)
                                                    }
                                                }

                                                val totalCheckboxes = section.content.count { it.trim().startsWith("- [") }
                                                if (totalCheckboxes > 0) {
                                                    val checkedCount = section.content.mapIndexed { lidx, line ->
                                                        if (line.trim().startsWith("- [")) checkboxStates[Pair(index, lidx)] ?: (line.trim().startsWith("- [x]") || line.trim().startsWith("- [X]")) else false
                                                    }.count { it }
                                                    if (checkedCount == totalCheckboxes) {
                                                        android.util.Log.d("MarkdownViewer", "Auto-closing simple section index=$index for $assetId (all checked)")
                                                        expandedSections[index] = false
                                                    }
                                                }
                                            }
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                )
                            }
                        } else {
                            RenderMarkdownLine(line, bodyFontSize)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders a single markdown line
 */
@Composable
private fun RenderMarkdownLine(line: String, bodyFontSize: Int) {
    when {
        line.startsWith("# ") -> {
            Text(
                text = parseInlineMarkdown(line.substring(2), bodyFontSize),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        line.startsWith("## ") -> {
            Text(
                text = parseInlineMarkdown(line.substring(3), bodyFontSize),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        line.startsWith("### ") -> {
            Text(
                text = parseInlineMarkdown(line.substring(4), bodyFontSize),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        line.trim().startsWith("- [ ]") || line.trim().startsWith("- [x]") || line.trim().startsWith("- [X]") -> {
            // Checkbox-Item
            val isChecked = line.trim().startsWith("- [x]") || line.trim().startsWith("- [X]")
            val text = line.trim().substring(5).trim()
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = null, // Read-only in simple view
                        enabled = false
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = parseInlineMarkdown(text, bodyFontSize),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = bodyFontSize.sp,
                            textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically)
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            }
        }
        line.trim().startsWith("- ") -> {
            // Normal list item
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(stringResource(R.string.markdown_bullet_point), style = MaterialTheme.typography.bodyMedium.copy(fontSize = bodyFontSize.sp))
                Text(
                    text = parseInlineMarkdown(line.trim().substring(2), bodyFontSize),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = bodyFontSize.sp)
                )
            }
        }
        line.trim().startsWith("> ") -> {
            // Blockquote
            val quoted = line.trim().substring(2)
            Card(
                modifier = Modifier.padding(vertical = 2.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.05f))
            ) {
                Text(
                    text = parseInlineMarkdown(quoted, bodyFontSize),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = (bodyFontSize - 1).sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        line.isNotBlank() -> {
            Text(
                text = parseInlineMarkdown(line, bodyFontSize),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = bodyFontSize.sp),
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        else -> {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * Parses markdown content into sections based on headings
 */
private fun parseMarkdownSections(markdownContent: String): List<MarkdownSection> {
    val lines = markdownContent.lines()
    val sections = mutableListOf<MarkdownSection>()
    var currentHeading = ""
    var currentContent = mutableListOf<String>()

    lines.forEach { line ->
        when {
            // New heading found
            line.startsWith("# ") || line.startsWith("## ") || line.startsWith("### ") -> {
                // Save previous section if present
                if (currentHeading.isNotEmpty() || currentContent.isNotEmpty()) {
                    sections.add(MarkdownSection(currentHeading, currentContent.toList()))
                }
                // Start new section
                currentHeading = line
                currentContent = mutableListOf()
            }
            // Normal content
            else -> {
                currentContent.add(line)
            }
        }
    }

    // Add last section
    if (currentHeading.isNotEmpty() || currentContent.isNotEmpty()) {
        sections.add(MarkdownSection(currentHeading, currentContent.toList()))
    }

    return sections
}

/**
 * Interactive markdown view with checkbox support
 * Shows checkboxes based on the checklist status
 * Groups ### sections into containers
 */
@Composable
private fun InteractiveMarkdownView(
    markdownContent: String,
    checklist: Checklist,
    onCheckboxChange: (itemId: String, checked: Boolean) -> Unit
    , bodyFontSize: Int
    , expandAll: Boolean = false,
    resetTrigger: Int = 0
) {
    val scrollState = rememberScrollState()

    // State for expanded sections - key is the section title
    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }

    // When expandAll changes, update all sections with items
    LaunchedEffect(expandAll) {
        checklist.sections.forEach { section ->
            if (section.title.isNotEmpty() && section.items.isNotEmpty()) {
                expandedSections[section.title] = expandAll
            }
        }
    }

    // Auto-collapse: if all items of a section are checked, automatically collapse the section
    LaunchedEffect(checklist) {
        checklist.sections.forEach { section ->
            val isExpanded = expandedSections[section.title] ?: false
            if (isExpanded && section.items.isNotEmpty()) {
                val allChecked = section.items.all { it.isChecked }
                if (allChecked) {
                    android.util.Log.d("MarkdownViewer", "Auto-closing section ${section.title} because all items are checked")
                    expandedSections[section.title] = false
                }
            }
        }
    }

    // Note: Do not reset collapsed/expanded section states on resetTrigger
    // Reset trigger should only reset checkbox states, not UI collapse/expand state.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Show checklist title as main heading
        if (checklist.title.isNotEmpty()) {
            Text(
                text = checklist.title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Sections with checkboxes and headings - grouped in Cards
        checklist.sections.forEach { section ->
            // Section heading (## level) - without Card
            if (section.title.isNotEmpty() && section.title != checklist.title) {
                // Check if it is a ### heading (sub-section)
                val isSubSection = section.title.length > 3 && !section.title.contains("Case") && !section.title.contains("Recovery")

                if (section.items.isNotEmpty()) {
                    val isExpanded = expandedSections[section.title] ?: false // Default: collapsed

                    // Section with items - show as collapsible Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            // Clickable header with icon
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedSections[section.title] = !isExpanded }
                                    .padding(bottom = if (isExpanded) 8.dp else 0.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Count checked and total checkboxes in this section
                                val totalCheckboxes = section.items.size
                                val checkedCheckboxes = section.items.count { it.isChecked }
                                val countText = if (totalCheckboxes > 0) stringResource(R.string.markdown_checkbox_count_format, checkedCheckboxes, totalCheckboxes) else ""

                                val isAllCheckedInteractive = totalCheckboxes > 0 && checkedCheckboxes == totalCheckboxes
                                Text(
                                    text = section.title + countText,
                                    style = if (isAllCheckedInteractive) MaterialTheme.typography.headlineSmall.copy(textDecoration = TextDecoration.LineThrough) else MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = stringResource(if (isExpanded) R.string.markdown_collapse else R.string.markdown_expand)
                                )
                            }

                            // Checkbox-Items in dieser Section nur wenn expanded
                            if (isExpanded) {
                                section.items.forEach { item ->
                                    ChecklistItemRow(
                                        item = item,
                                        onCheckboxChange = onCheckboxChange,
                                        bodyFontSize = bodyFontSize
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ## heading - without Card
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )

                    // Checkbox-Items in dieser Section
                    section.items.forEach { item ->
                        ChecklistItemRow(
                            item = item,
                            onCheckboxChange = onCheckboxChange,
                            bodyFontSize = bodyFontSize
                        )
                    }
                }
            } else {
                // No heading or main title
                section.items.forEach { item ->
                    ChecklistItemRow(
                        item = item,
                        onCheckboxChange = onCheckboxChange,
                        bodyFontSize = bodyFontSize
                    )
                }
            }
        }
    }
}

/**
 * Single checkbox row for a checklist item
 */
@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    onCheckboxChange: (itemId: String, checked: Boolean) -> Unit
    , bodyFontSize: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = { isChecked ->
                    onCheckboxChange(item.id, isChecked)
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = parseInlineMarkdown(item.text, bodyFontSize),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = bodyFontSize.sp,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                ),
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.CenterVertically)
                    .clickable(role = Role.Checkbox) { onCheckboxChange(item.id, !item.isChecked) }
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    }
}

/**
 * Loads markdown content from assets
 */
private suspend fun loadMarkdownFromAssets(context: Context, assetPath: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream = context.assets.open(assetPath)
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.use { it.readText() }
        } catch (e: Exception) {
            throw Exception("File not found: $assetPath", e)
        }
    }
} 

/**
 * Loads markdown content from an internal file
 */
private suspend fun loadMarkdownFromFile(filePath: String): String {
    return withContext(Dispatchers.IO) {
        try {
            java.io.File(filePath).readText()
        } catch (e: Exception) {
            throw Exception("File not found: $filePath", e)
        }
    }
}