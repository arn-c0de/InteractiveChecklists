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
import androidx.compose.ui.graphics.Color
import com.example.checklist_interactive.data.quicknotes.QuickNoteManager
import java.net.URLDecoder
import java.net.URLEncoder

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
    var hasChanges by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var showAdvancedEditor by remember { mutableStateOf(false) }
    var newTextInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }

    // Sync currentNote with savedNote from manager
    LaunchedEffect(savedNote) {
        if (!hasChanges) {
            currentNote = savedNote
        }
    }

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
                            val annotatedString = buildAnnotatedStringWithLinks(currentNote)
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
