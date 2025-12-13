package com.example.checklist_interactive.ui.quickaccess

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import com.example.checklist_interactive.data.quicknotes.QuickNoteManager
import java.net.URLDecoder
import java.net.URLEncoder

// Replace [Callsign] placeholders (case-insensitive) with provided callsign value when rendering.
private fun expandCallsignPlaceholder(content: String, callsign: String): String {
    if (callsign.isBlank()) return content
    val regex = Regex("\\[Callsign\\]", RegexOption.IGNORE_CASE)
    return content.replace(regex, callsign)
}

/**
 * Builds annotated string with clickable internal links
 */
private fun buildAnnotatedStringWithLinks(content: String): AnnotatedString {
    val regex = Regex("\\[([^]]+)]\\(internal://open\\?file=([^)&]+)(?:&page=(\\d+))?\\)")
    val builder = AnnotatedString.Builder()
    var last = 0

    regex.findAll(content).forEach { match ->
        val range = match.range
        if (range.first > last) {
            builder.append(content.substring(last, range.first))
        }

        val label = match.groups[1]?.value ?: ""
        val fileEnc = match.groups[2]?.value ?: ""
        val page = match.groups[3]?.value ?: ""
        val annotationValue = "$fileEnc|$page"

        val startIndex = builder.length
        builder.append(label)
        val endIndex = builder.length

        builder.addStringAnnotation(
            tag = "OPEN_LINK",
            annotation = annotationValue,
            start = startIndex,
            end = endIndex
        )
        builder.addStyle(
            SpanStyle(
                color = Color(0xFF0066CC),
                textDecoration = TextDecoration.Underline
            ),
            startIndex,
            endIndex
        )
        last = range.last + 1
    }

    if (last < content.length) {
        builder.append(content.substring(last))
    }

    return builder.toAnnotatedString()
}

/**
 * Quick Access Bottom Sheet with modern note-taking functionality
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAccessSheet(
    onDismiss: () -> Unit,
    onOpenDocument: ((filePath: String, pageNumber: Int?) -> Unit)? = null,
    currentDocumentPath: String? = null,
    currentDocumentName: String? = null,
    currentPageNumber: Int? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val noteManager = remember { QuickNoteManager(context) }

    // State flows
    val notes by noteManager.notes.collectAsState()
    val activeNoteId by noteManager.activeNoteId.collectAsState()
    val savedNote by noteManager.noteContent.collectAsState()

    // Local UI state
    var currentNote by remember { mutableStateOf("") }
    // Radio toolbar state
    val callsignFlow by noteManager.callsign.collectAsState()
    val com1Flow by noteManager.com1.collectAsState()
    val com1ModeFlow by noteManager.com1Mode.collectAsState()
    val com2Flow by noteManager.com2.collectAsState()
    val com2ModeFlow by noteManager.com2Mode.collectAsState()
    val flightStatusFlow by noteManager.flightStatus.collectAsState()
    var callsign by remember { mutableStateOf("") }
    var com1 by remember { mutableStateOf("") }
    var com1Mode by remember { mutableStateOf("FM") }
    var com2 by remember { mutableStateOf("") }
    var com2Mode by remember { mutableStateOf("FM") }
    var flightStatus by remember { mutableStateOf("") }
    var hasChanges by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var showAdvancedEditor by remember { mutableStateOf(false) }
    var newTextInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    // Flight info expanded state
    val flightExpandedFlow by noteManager.flightInfoExpanded.collectAsState()
    var flightExpanded by remember { mutableStateOf(flightExpandedFlow) }

    // Sync currentNote with savedNote from manager
    LaunchedEffect(savedNote) {
        if (!hasChanges) {
            currentNote = savedNote
        }
    }

    // Initialize toolbar state from flows
    LaunchedEffect(callsignFlow, com1Flow, com1ModeFlow, com2Flow, com2ModeFlow, flightStatusFlow) {
        callsign = callsignFlow
        com1 = com1Flow
        com1Mode = com1ModeFlow
        com2 = com2Flow
        com2Mode = com2ModeFlow
        flightStatus = flightStatusFlow
    }

    // Auto-save callsign when it changes (debounced via LaunchedEffect cancellations)
    LaunchedEffect(callsign) {
        if (callsign != callsignFlow) {
            kotlinx.coroutines.delay(600L)
            noteManager.saveCallsign(callsign)
        }
    }

    // Auto-save COM1 and mode when either changes
    LaunchedEffect(com1, com1Mode) {
        if (com1 != com1Flow || com1Mode != com1ModeFlow) {
            kotlinx.coroutines.delay(600L)
            noteManager.saveCom1(com1, com1Mode)
        }
    }

    // Auto-save COM2 and mode when either changes
    LaunchedEffect(com2, com2Mode) {
        if (com2 != com2Flow || com2Mode != com2ModeFlow) {
            kotlinx.coroutines.delay(600L)
            noteManager.saveCom2(com2, com2Mode)
        }
    }

    // Auto-save flight status
    LaunchedEffect(flightStatus) {
        if (flightStatus != flightStatusFlow) {
            kotlinx.coroutines.delay(600L)
            noteManager.saveFlightStatus(flightStatus)
        }
    }
    LaunchedEffect(flightExpandedFlow) { flightExpanded = flightExpandedFlow }

    // Auto-save after 2 seconds of inactivity
    LaunchedEffect(currentNote, hasChanges) {
        if (hasChanges) {
            kotlinx.coroutines.delay(2000L)
            noteManager.saveNote(currentNote)
            hasChanges = false
        }
    }

    // Save on dispose
    DisposableEffect(Unit) {
        onDispose {
            if (hasChanges) {
                noteManager.saveNote(currentNote)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (hasChanges) {
                noteManager.saveNote(currentNote)
            }
            onDismiss()
        },
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header with title and actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Article,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Schnellnotizen",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    // Search button
                    if (notes.size > 1) {
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(
                                if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Suchen"
                            )
                        }
                    }

                    // Pin button (only if document is open)
                    if (currentDocumentPath != null && currentDocumentName != null) {
                        FilledTonalIconButton(
                            onClick = {
                                val uri = "internal://open?file=${URLEncoder.encode(currentDocumentPath, "UTF-8")}${
                                    if (currentPageNumber != null) "&page=${currentPageNumber + 1}" else ""
                                }"
                                val label = if (currentPageNumber != null) "${currentDocumentName} (S. ${currentPageNumber + 1})" else currentDocumentName
                                val linkText = "\n📌 [$label]($uri)\n"
                                val newContent = currentNote + linkText
                                currentNote = newContent
                                hasChanges = true
                            }
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = "Dokument anheften")
                        }
                    }
                }
            }

            // Search bar
            if (showSearchBar) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    placeholder = { Text("Notizen durchsuchen...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Löschen")
                            }
                        }
                    },
                    singleLine = true
                )
            }

            // Flight Info header + Radio / Callsign toolbar (small)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Flight Info", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = {
                    flightExpanded = !flightExpanded
                    noteManager.saveFlightInfoExpanded(flightExpanded)
                }) {
                    Icon(
                        imageVector = if (flightExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (flightExpanded) "Einklappen" else "Ausklappen"
                    )
                }
            }

            // Compact view when collapsed
            AnimatedVisibility(visible = !flightExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "${callsign.ifBlank { "-" }}  | Status: ${flightStatus.ifBlank { "Idle" }}  | COM1: ${com1.ifBlank { "-" }} ${com1Mode}  | COM2: ${com2.ifBlank { "-" }} ${com2Mode}", style = MaterialTheme.typography.bodyMedium)
                    Row {
                        IconButton(onClick = { flightExpanded = true; noteManager.saveFlightInfoExpanded(true) }) {
                            Icon(Icons.Default.ExpandMore, contentDescription = "Expand")
                        }
                    }
                }
            }

            AnimatedVisibility(visible = flightExpanded) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top row: CALLSIGN
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = callsign,
                                    onValueChange = { callsign = it },
                                    label = { Text("CALLSIGN") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                // Flight status dropdown using shared component
                                FlightStatusDropdown(
                                    currentStatus = flightStatus,
                                    onStatusChange = { flightStatus = it },
                                    compact = false
                                )
                            }
                    }

                    // Second row: COM1 and COM2 side-by-side with their modes and the action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // COM1 column
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = com1,
                                onValueChange = { com1 = it },
                                label = { Text("COM1") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(140.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(selected = com1Mode == "FM", onClick = { com1Mode = "FM" }, label = { Text("FM") })
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(selected = com1Mode == "AM", onClick = { com1Mode = "AM" }, label = { Text("AM") })
                        }

                        // COM2 column
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = com2,
                                onValueChange = { com2 = it },
                                label = { Text("COM2") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(140.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(selected = com2Mode == "FM", onClick = { com2Mode = "FM" }, label = { Text("FM") })
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(selected = com2Mode == "AM", onClick = { com2Mode = "AM" }, label = { Text("AM") })
                        }

                        // no manual save/load buttons (auto-save/auto-load enabled)
                    }
                }
            }

            // Note tabs (horizontal scrollable)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Filter notes based on search query
                val filteredNotes = if (searchQuery.isBlank()) {
                    notes
                } else {
                    notes.filter { note ->
                        note.title.contains(searchQuery, ignoreCase = true) ||
                        note.content.contains(searchQuery, ignoreCase = true)
                    }
                }

                items(filteredNotes) { note ->
                    FilterChip(
                        selected = note.id == activeNoteId,
                        onClick = { noteManager.setActiveNote(note.id) },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (note.title.isNotBlank()) note.title else "Unbenannt",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (note.id == activeNoteId) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                renameTargetId = note.id
                                                renameText = note.title
                                                showRenameDialog = true
                                            }
                                    )
                                }
                            }
                        },
                        trailingIcon = if (note.id == activeNoteId && notes.size > 1) {
                            {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Löschen",
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { noteManager.removeNote(note.id) },
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else null
                    )
                }

                // Add note button
                item {
                    FilterChip(
                        selected = false,
                        onClick = {
                            val id = noteManager.addNote(title = "Neue Notiz")
                            noteManager.setActiveNote(id)
                        },
                        label = { Text("Neu") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Neue Notiz",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            // Rename dialog
            if (showRenameDialog) {
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    icon = {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    },
                    title = { Text("Notiz umbenennen") },
                    text = {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text("Titel") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        FilledTonalButton(
                            onClick = {
                                renameTargetId?.let {
                                    noteManager.renameNote(it, renameText)
                                }
                                showRenameDialog = false
                            }
                        ) {
                            Text("Speichern")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = false }) {
                            Text("Abbrechen")
                        }
                    }
                )
            }

            // Editor mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showAdvancedEditor) "Markdown Editor" else "Notiz",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = !showAdvancedEditor,
                        onClick = { showAdvancedEditor = false },
                        label = { Text("Normal", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = showAdvancedEditor,
                        onClick = { showAdvancedEditor = true },
                        label = { Text("Markdown", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Note editor card
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            ) {
                if (showAdvancedEditor) {
                    // Advanced markdown editor
                    OutlinedTextField(
                        value = currentNote,
                        onValueChange = {
                            currentNote = it
                            hasChanges = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 300.dp, max = 500.dp),
                        placeholder = {
                            Text(
                                "Markdown Editor\n\n" +
                                "Links: [Text](internal://open?file=...)\n" +
                                "**Fett**, *Kursiv*",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                } else {
                    // Normal mode with clickable links
                    Column(modifier = Modifier.fillMaxWidth()) {
                            if (currentNote.isNotEmpty()) {
                            val displayNote = expandCallsignPlaceholder(currentNote, callsign)
                            val annotatedString = buildAnnotatedStringWithLinks(displayNote)
                            @Suppress("DEPRECATION")
                            ClickableText(
                                text = annotatedString,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 200.dp, max = 400.dp)
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState()),
                                onClick = { offset ->
                                    annotatedString.getStringAnnotations("OPEN_LINK", offset, offset)
                                        .firstOrNull()?.let { annotation ->
                                            val parts = annotation.item.split("|")
                                            val filePath = URLDecoder.decode(
                                                parts.getOrNull(0) ?: "",
                                                "UTF-8"
                                            )
                                            // Normalize clicked page number to 0-based index (allow stored links to be 1-based)
                                            val rawPage = parts.getOrNull(1)?.toIntOrNull()
                                            val pageNumber = rawPage?.let { if (it > 0) it - 1 else it }

                                            onOpenDocument?.invoke(filePath, pageNumber)
                                            onDismiss()
                                        }
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }

                        // Quick add text field
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newTextInput,
                                onValueChange = { newTextInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Text hinzufügen...") },
                                maxLines = 4,
                                textStyle = MaterialTheme.typography.bodyMedium
                            )

                            FilledTonalIconButton(
                                onClick = {
                                    if (newTextInput.isNotEmpty()) {
                                        currentNote = if (currentNote.isEmpty()) {
                                            newTextInput
                                        } else {
                                            "$currentNote\n\n$newTextInput"
                                        }
                                        newTextInput = ""
                                        hasChanges = true
                                    }
                                },
                                enabled = newTextInput.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Hinzufügen")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        currentNote = ""
                        hasChanges = true
                        noteManager.clearNote()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = currentNote.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Löschen")
                }

                Button(
                    onClick = {
                        noteManager.saveNote(currentNote)
                        hasChanges = false
                    },
                    modifier = Modifier.weight(1f),
                    enabled = hasChanges
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Speichern")
                }
            }

            // Status indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    hasChanges -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Nicht gespeichert",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    currentNote.isNotEmpty() -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Gespeichert",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                // Note count
                if (notes.isNotEmpty()) {
                    Text(
                        text = "${notes.size} ${if (notes.size == 1) "Notiz" else "Notizen"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
